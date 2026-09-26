package br.com.systec.hive.security.impl.filter;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.security.api.service.AuthenticationService;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private final SecurityTokenService securityTokenService;
    private final AuthenticationService authenticationService;

    public SecurityFilter(SecurityTokenService securityTokenService, AuthenticationService authenticationService) {
        this.securityTokenService = securityTokenService;
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws BaseException {
        try {
            log.warn("@@@ Iniciando filtro de segurança");
            String bearerToken = recoverToken(request);

            if (bearerToken != null) {
                securityTokenService.validateTokenRevoke(bearerToken);
                var username = securityTokenService.getSubject(bearerToken);
                var userDetails = authenticationService.loadUserByUsername(username);
                Collection<? extends GrantedAuthority> authorities = resolveAuthorities(bearerToken, userDetails);
                var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                TenantContext.runWithTenant(securityTokenService.getTenantId(bearerToken), () -> {
                    filterChain.doFilter(request, response);
                    return null;
                });
            } else {
                filterChain.doFilter(request, response);
            }
        } catch (BaseException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Erro ao tentar fazer o SecurityFilter", e);
            throw new SecurityException(e);
        } finally {
            log.warn("@@@ Finalizando o secuirty service");
            SecurityContextHolder.clearContext();
        }
    }

    private String recoverToken(HttpServletRequest request){
        String token = request.getHeader("Authorization");

        if(token == null || !token.startsWith("Bearer ")){
            return null;
        }

        return token.replace("Bearer ", "");
    }

    private Collection<? extends GrantedAuthority> resolveAuthorities(String bearerToken, org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            DecodedJWT decoded = JWT.decode(bearerToken);
            List<String> permissions = decoded.getClaim("permissions").asList(String.class);
            if (permissions != null && !permissions.isEmpty()) {
                List<GrantedAuthority> authorities = new ArrayList<>();
                for (String p : permissions) {
                    authorities.add(new SimpleGrantedAuthority(p));
                }
                if (userDetails.getAuthorities() != null) {
                    authorities.addAll((Collection<? extends GrantedAuthority>) userDetails.getAuthorities());
                }
                return authorities;
            }
        } catch (Exception e) {
            log.debug("Failed to extract permissions from JWT, falling back to UserDetails authorities", e);
        }
        return userDetails.getAuthorities();
    }
}
