package br.com.systec.hive.security.impl.filter;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.RefreshTokenData;
import br.com.systec.hive.security.api.service.AuthenticationService;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private static final List<String> DOCS_PREFIXES = List.of("/scalar", "/v3/api-docs");

    private final SecurityTokenService securityTokenService;
    private final AuthenticationService authenticationService;
    private final TokenCacheService tokenCacheService;
    private final UserService userService;

    public SecurityFilter(SecurityTokenService securityTokenService, AuthenticationService authenticationService,
                          TokenCacheService tokenCacheService, UserService userService) {
        this.securityTokenService = securityTokenService;
        this.authenticationService = authenticationService;
        this.tokenCacheService = tokenCacheService;
        this.userService = userService;
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
            } else if (!isDocsRequest(request) || !authenticateWithRefreshCookie(request, response, filterChain)) {
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

    private boolean isDocsRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        return DOCS_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /** Cookie de refresh autentica apenas a documentação; nunca aciona rotação nem expõe o token na URL. */
    private boolean authenticateWithRefreshCookie(HttpServletRequest request, HttpServletResponse response,
                                                   FilterChain filterChain) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
            }
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }

        Optional<RefreshTokenData> found = tokenCacheService.findRefreshTokenDataByToken(refreshToken);
        if (found.isEmpty()) {
            return false;
        }
        RefreshTokenData data = found.get();
        if (data.expiresAt() != null && data.expiresAt().isBefore(Instant.now())) {
            return false;
        }
        String clientIp = clientIp(request);
        if (data.remoteIp() != null && !sameClientIp(data.remoteIp(), clientIp)) {
            log.warn("Cookie de refresh da documentação com IP divergente");
            return false;
        }

        User user;
        try {
            user = userService.findById(data.userId());
        } catch (RuntimeException e) {
            log.debug("Cookie de refresh da documentação referencia usuário inexistente", e);
            return false;
        }
        if (user == null || !data.tenantId().equals(user.getTenantId())) {
            return false;
        }

        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.runWithTenant(data.tenantId(), () -> {
            filterChain.doFilter(request, response);
            return null;
        });
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    /** Compara por bytes; representações distintas do mesmo loopback valem como o mesmo cliente. Falha fecha. */
    private boolean sameClientIp(String storedIp, String clientIp) {
        try {
            InetAddress stored = InetAddress.getByName(storedIp);
            InetAddress client = InetAddress.getByName(clientIp);
            if (stored.isLoopbackAddress() && client.isLoopbackAddress()) {
                return true;
            }
            return Arrays.equals(stored.getAddress(), client.getAddress());
        } catch (UnknownHostException e) {
            return false;
        }
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
