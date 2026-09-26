package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.RefreshTokenData;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface para gerenciamento de cache de Tokens e Refresh Tokens no Redis.
 * Centraliza a lógica de revogação, validação e segurança (Blacklist).
 */
public interface TokenCacheService {

    /**
     * Persiste os dados do Refresh Token no cache.
     *
     * @param refreshToken O token de atualização opaco.
     * @param data         Dados associados ao token.
     * @param ttl          Tempo de vida do token (Time-to-Live).
     */
    void saveRefreshToken(String refreshToken, RefreshTokenData data, Duration ttl);

    /**
     * Recupera os dados do Refresh Token se ainda estiver ativo e não expirado.
     *
     * @param tenantId     Inquilino ao qual o token pertence.
     * @param refreshToken O token de atualização opaco.
     * @return Os dados do token, se encontrados.
     */
    Optional<RefreshTokenData> getRefreshTokenData(UUID tenantId, String refreshToken);

    /**
     * Remove o Refresh Token do cache (Revogação manual ou Logout).
     *
     * @param tenantId     Inquilino ao qual o token pertence.
     * @param refreshToken O token de atualização opaco.
     */
    void revokeRefreshToken(UUID tenantId, String refreshToken);

    /**
     * Adiciona um JWT ID (jti) à Blacklist para impedir o uso de Access Tokens válidos.
     * Geralmente usado durante o Logout ou quando um Refresh Token é rotacionado.
     *
     * @param tenantId Inquilino ao qual o token pertence.
     * @param jti      JWT ID (tokenId).
     * @param ttl      Tempo restante de validade do Access Token original.
     */
    void addToBlacklist(UUID tenantId, String jti, Duration ttl);

    /**
     * Verifica se um Access Token (via jti) está na Blacklist de revogação.
     *
     * @param tenantId Inquilino ao qual o token pertence.
     * @param jti      JWT ID (tokenId).
     * @return true se o token estiver revogado.
     */
    boolean isBlacklisted(UUID tenantId, String jti);

    /**
     * Revoga todos os Refresh Tokens de um usuário em um Tenant específico.
     * Utilizado para forçar o logout de todas as sessões em caso de incidente de segurança.
     *
     * @param tenantId Inquilino ao qual o usuário pertence.
     * @param userId   Identificador do usuário.
     */
    void revokeAllUserTokens(UUID tenantId, UUID userId);
}
