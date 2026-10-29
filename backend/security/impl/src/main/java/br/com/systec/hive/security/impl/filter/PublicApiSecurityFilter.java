package br.com.systec.hive.security.impl.filter;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PublicApiSecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PublicApiSecurityFilter.class);

    private static final String JTI_BLACKLIST_KEY = "hive:public:revoked-jti";

    private final PublicClientTokenIssuer tokenIssuer;
    private final RedisTemplate<String, Object> redisTemplate;

    public PublicApiSecurityFilter(PublicClientTokenIssuer tokenIssuer, RedisTemplate<String, Object> redisTemplate) {
        this.tokenIssuer = tokenIssuer;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.contains("/v1/public/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String bearerToken = recoverToken(request);
        if (bearerToken == null) {
            writeUnauthorized(response);
            return;
        }

        UUID tenantId = null;
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        String subject = null;

        try {
            DecodedJWT decoded = tokenIssuer.decodeAndVerify(bearerToken);

            String jti = decoded.getId();
            if (jti != null) {
                Boolean blacklisted = redisTemplate.opsForSet().isMember(JTI_BLACKLIST_KEY, jti);
                if (Boolean.TRUE.equals(blacklisted)) {
                    writeUnauthorized(response);
                    return;
                }
            }

            String tenantIdStr = decoded.getClaim("tenant-id").asString();
            tenantId = UUID.fromString(tenantIdStr);

            List<String> permissions = decoded.getClaim("permissions").asList(String.class);
            if (permissions != null) {
                for (String p : permissions) {
                    authorities.add(new SimpleGrantedAuthority(p));
                }
            }
            subject = decoded.getSubject();

            try {
                request.setAttribute("publicapi.clientId", subject);
                request.setAttribute("publicapi.pcode", decoded.getClaim("pcode").asList(String.class));
                Object plock = decoded.getClaim("plock").asMap();
                request.setAttribute("publicapi.plock", plock);
                Object plim = decoded.getClaim("plim").asMap();
                request.setAttribute("publicapi.plim", plim);
            } catch (Exception ignored) {
            }

            var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.debug("Public API token rejected: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }

        try {
            final UUID tid = tenantId;
            TenantContext.runWithTenant(tid, () -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (Exception e) {
            if (e instanceof ServletException) {
                throw (ServletException) e;
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new ServletException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String recoverToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return null;
        }
        return token.substring(7);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"message\":\"Unauthorized\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}");
    }
}
