package br.com.systec.hive.integrationtest.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTestHelper {

    private static final String ISSUER = "hive.api";
    private static final String AUDIENCE = "hive.web";
    private static final String TENANT_CLAIM = "tenant-id";

    private static final List<String> DEFAULT_PERMISSIONS =
            List.of("PROMPT_MANAGE", "PROMPT_ADMIN", "AGENT_MANAGE", "AGENT_ADMIN", "CHAT_SEND", "CHAT_SESSION_MANAGE");

    @Value("${api.security.token.secret}")
    private String secret;

    public String generateToken(String username, UUID tenantId) {
        return generateToken(username, tenantId, DEFAULT_PERMISSIONS);
    }

    public String generateToken(String username, UUID tenantId, List<String> permissions) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withAudience(AUDIENCE)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .withClaim(TENANT_CLAIM, tenantId.toString())
                .withClaim("permissions", permissions)
                .withJWTId(UUID.randomUUID().toString())
                .sign(Algorithm.HMAC256(secret));
    }
}
