package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.exceptions.UserNotFoundException;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.RefreshTokenData;
import br.com.systec.hive.security.api.exceptions.*;
import br.com.systec.hive.security.api.exceptions.SecurityException;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SecurityTokenServiceImpl implements SecurityTokenService {
    private static final Logger log = LoggerFactory.getLogger(SecurityTokenServiceImpl.class);
    public static final String TENANT_CLAIM = "tenant-id";

    private static final String ISSUE = "hive.api";
    private static final String AUDIENCE = "hive.web";

    @Value("${api.security.token.secret}")
    private String secret;

    private final UserService userService;
    private final TokenCacheService tokenCacheService;
    private final jakarta.servlet.http.HttpServletRequest request;

    public SecurityTokenServiceImpl(UserService userService, TokenCacheService tokenCacheService, jakarta.servlet.http.HttpServletRequest request) {
        this.userService = userService;
        this.tokenCacheService = tokenCacheService;
        this.request = request;
    }

    @Override
    @Transactional
    public LoginAuthenticate generateNewAccessTokenByRefreshToken() throws BaseException {
        try {
            // Extrai o tenant do cabeçalho da requisição (ex: X-Tenant-ID) ou do JWT decodificado
            // Para manter o isolamento multi-tenant, o tenantId é mandatório.
            String tenantIdStr = request.getHeader("X-Tenant-ID");
            String refreshToken = getRefreshToken();

            if (tenantIdStr == null || tenantIdStr.isBlank()) {
                throw new BaseException("Tenant ID não informado na requisição");
            }

            UUID tenantId = UUID.fromString(tenantIdStr);

            // Busca os dados do Refresh Token no Redis
            RefreshTokenData oldTokenData = tokenCacheService.getRefreshTokenData(tenantId, refreshToken)
                    .orElseThrow(RefreshTokenNotFoundException::new);

            // Validação de Segurança Robustez (IP e User-Agent)
            String currentIp = getClientIp(request);
//            String currentUserAgent = request.getHeader("User-Agent");

            // Se o IP ou User-Agent mudarem drasticamente, pode ser um sequestro de sessão
            if (oldTokenData.remoteIp() != null && !oldTokenData.remoteIp().equals(currentIp)) {
                log.error("Possível sequestro de sessão detectado! IP original: {}, IP atual: {}", 
                        oldTokenData.remoteIp(), currentIp);
                
                // Invalida o token imediatamente por segurança
                tokenCacheService.revokeRefreshToken(tenantId, refreshToken);
                throw new SecurityException("Acesso negado: contexto de segurança alterado");
            }

            // Busca o usuário para gerar o novo par
            User user = userService.findById(oldTokenData.userId());
            if (user == null) {
                throw new UserNotFoundException();
            }

            // EFETUA A ROTAÇÃO (Invalidar o antigo e gerar novo)
            tokenCacheService.revokeRefreshToken(tenantId, refreshToken);
            
            // Opcional: Adiciona o JTI antigo à blacklist para garantir que o Access Token original 
            // não seja mais usado se o Refresh já rodou
            tokenCacheService.addToBlacklist(tenantId, oldTokenData.jti(), Duration.ofMinutes(15));

            log.info("Efetuando rotação de token para o usuário: {} no tenant: {}", user.getId(), tenantId);
            return generateTokenAndRefreshTokenByUser(user);

        } catch (BaseException e) {
            log.error("Erro no refresh token: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao processar refresh token", e);
            throw new BaseException("Erro ao processar refresh token", e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public LoginAuthenticate generateTokenAndRefreshToken(Authentication authentication) throws SecurityException {
        try {
            User user = (User) authentication.getPrincipal();
            return generateTokenAndRefreshTokenByUser(user);
        } catch (BaseException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Ocorreu um erro ao tentar gerar os tokens", e);
            throw new SecurityException(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public String getSubject(String tokenJWT) throws SecurityException {
        try {
            DecodedJWT decodedJWT = decodeAndVerify(tokenJWT);
            return decodedJWT.getSubject();
        } catch (TokenExpiredException e) {
            throw new TokenOpusfinancialExpiredException(e);
        } catch (Exception e) {
            throw new BaseException(e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public void validateTokenRevoke(String tokenJWT) throws BaseException {
        try {
            DecodedJWT decodedJWT = decodeAndVerify(tokenJWT);
            String jti = decodedJWT.getId();
            UUID tenantId = UUID.fromString(decodedJWT.getClaim(TENANT_CLAIM).asString());

            if (tokenCacheService.isBlacklisted(tenantId, jti)) {
                log.warn("Tentativa de uso de token revogado: {}", jti);
                throw new TokenRevokeException();
            }
        } catch (TokenExpiredException e) {
            throw new TokenOpusfinancialExpiredException();
        } catch (Exception e) {
            log.error("Erro ao validar revogação de token", e);
            throw new BaseException(e.getMessage(), e);
        }
    }

    private DecodedJWT decodeAndVerify(String tokenJWT) {
        return JWT.require(getAlgorithm())
                .withIssuer(ISSUE)
                .withAudience(AUDIENCE)
                .build()
                .verify(tokenJWT);
    }

    private String generateToken(User user, String jti) {
        List<String> permissions = List.of("PROMPT_MANAGE", "PROMPT_ADMIN", "AGENT_MANAGE", "AGENT_ADMIN", "CHAT_SEND", "CHAT_SESSION_MANAGE", "KNOWLEDGE_MANAGE", "KNOWLEDGE_READ");
        return JWT.create()
                .withIssuer(ISSUE)
                .withSubject(user.getUsername())
                .withAudience(AUDIENCE)
                .withIssuedAt(new Date(System.currentTimeMillis()))
                .withExpiresAt(generateDateExpired())
                .withClaim(TENANT_CLAIM, user.getTenantId().toString())
                .withClaim("permissions", permissions)
                .withJWTId(jti)
                .sign(getAlgorithm());
    }

    private String generateRefreshTokenValue() throws BaseException {
        try {
            byte[] bytes = new byte[64];
            new SecureRandom().nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BaseException(e);
        }
    }

    private Algorithm getAlgorithm() {
        return Algorithm.HMAC256(secret);
    }

    private Instant generateDateExpired() {
        return LocalDateTime.now().plusMinutes(15).toInstant(ZoneOffset.of("-03:00"));
    }

    private LoginAuthenticate generateTokenAndRefreshTokenByUser(User user) throws BaseException {
        try {
            String jti = UUID.randomUUID().toString();
            String accessToken = generateToken(user, jti);
            String refreshTokenValue = generateRefreshTokenValue();

            // Configura os metadados para o cache
            Instant now = Instant.now();
            Duration refreshTtl = Duration.ofDays(14);
            Instant expiresAt = now.plus(refreshTtl);

            // Captura metadados para segurança robusta (OWASP)
            String remoteIp = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");

            RefreshTokenData cacheData = new RefreshTokenData(
                    user.getId(),
                    user.getTenantId(),
                    jti,
                    now,
                    expiresAt,
                    remoteIp,
                    userAgent
            );

            // Salva no Redis
            tokenCacheService.saveRefreshToken(refreshTokenValue, cacheData, refreshTtl);

            String profileName = ""; // Pode ser expandido conforme Roles forem implementadas

            return new LoginAuthenticate(user.getId(), accessToken, "Bearer", profileName, refreshTokenValue);
        } catch (Exception e) {
            log.error("Erro ao gerar par de tokens para o usuário: {}", user.getId(), e);
            throw new TokenGenerationFailedException(e);
        }
    }

    /**
     * Captura o IP real do cliente, mesmo atrás de Proxys/Load Balancers.
     */
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public UUID getTenantId(String token) throws SecurityException {
        try {
            DecodedJWT decodedJWT = decodeAndVerify(token);
            String tenantId = decodedJWT.getClaim(TENANT_CLAIM).asString();
            return UUID.fromString(tenantId);
        } catch (Exception e) {
            log.error("Erro ao extrair tenantId do token", e);
            throw new SecurityException("Erro ao extrair tenantId do token", e);
        }
    }

    private String getRefreshToken() {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        if (refreshToken == null) {
            throw new RefreshTokenNotFoundException();
        }

        return refreshToken;
    }
}
