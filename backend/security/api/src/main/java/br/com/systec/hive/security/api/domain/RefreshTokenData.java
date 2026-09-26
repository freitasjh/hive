package br.com.systec.hive.security.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa os dados do Refresh Token armazenados no cache (Redis).
 * Utiliza Java 21 Records para imutabilidade e clareza.
 *
 * @param userId Identificador do usuário dono do token.
 * @param tenantId Identificador do inquilino (Multi-tenancy).
 * @param jti JWT ID (tokenId) associado ao Access Token original.
 * @param createdAt Instante de criação do token.
 * @param expiresAt Instante de expiração do token no cache.
 * @param remoteIp IP que originou a criação do token (Segurança).
 * @param userAgent User-Agent que originou a criação do token (Segurança).
 */
public record RefreshTokenData(
    UUID userId,
    UUID tenantId,
    String jti,
    Instant createdAt,
    Instant expiresAt,
    String remoteIp,
    String userAgent
) {
}
