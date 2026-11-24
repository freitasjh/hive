package br.com.systec.hive.security.impl.filter;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.publicapi.api.service.PublicApiKeyAuthenticator;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Map;
import java.util.UUID;

public class PublicApiSecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PublicApiSecurityFilter.class);

    private static final String JTI_BLACKLIST_KEY = "hive:public:revoked-jti";
    private static final String KEY_PREFIX = "sk-";

    private final PublicClientTokenIssuer tokenIssuer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectProvider<PublicApiKeyAuthenticator> keyAuthenticator;

    public PublicApiSecurityFilter(PublicClientTokenIssuer tokenIssuer, RedisTemplate<String, Object> redisTemplate,
                                   ObjectProvider<PublicApiKeyAuthenticator> keyAuthenticator) {
        this.tokenIssuer = tokenIssuer;
        this.redisTemplate = redisTemplate;
        this.keyAuthenticator = keyAuthenticator;
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

        UUID tenantId;
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        String subject;

        try {
            if (bearerToken.startsWith(KEY_PREFIX)) {
                // ADR-001: prefixo exato e case-sensitive; NUNCA tenta JWT depois de ver sk-.
                if (bearerToken.length() == KEY_PREFIX.length()) {
                    writeUnauthorized(response);
                    return;
                }
                PublicApiKeyAuthenticator authenticator = keyAuthenticator.getIfAvailable();
                if (authenticator == null) {
                    // ADR-002 ainda não registrou o bean: fail-closed.
                    writeUnauthorized(response);
                    return;
                }
                PublicApiKeyAuthenticator.AuthenticatedKey key = authenticator.authenticate(bearerToken);
                if (key == null || key.tenantId() == null || key.clientId() == null) {
                    writeUnauthorized(response);
                    return;
                }
                tenantId = key.tenantId();
                subject = key.clientId();
                if (key.permissions() != null) {
                    for (String p : key.permissions()) {
                        authorities.add(new SimpleGrantedAuthority(p));
                    }
                }
                request.setAttribute("publicapi.clientId", subject);
                request.setAttribute("publicapi.pcode", key.pcode());
                request.setAttribute("publicapi.plock", key.plock());
                request.setAttribute("publicapi.plim", key.plim());
            } else {
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
            }

            var authentication = new UsernamePasswordAuthenticationToken(subject, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.debug("Public API credential rejected: {}", e.getMessage());
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
