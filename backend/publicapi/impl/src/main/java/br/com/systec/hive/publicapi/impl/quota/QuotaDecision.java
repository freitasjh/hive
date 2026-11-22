package br.com.systec.hive.publicapi.impl.quota;

/**
 * ADR-004/QT04: decisão devolvida pelo guard sem alterar o algoritmo Lua.
 *
 * @param limit             limite efetivo aplicado
 * @param remaining         requisições restantes (0 na negação)
 * @param retryAfterSeconds segundos até liberar (0 quando permitido)
 * @param resetEpochSeconds epoch seconds do reset, derivado em Java
 * @param measured          {@code false} em fail-open — nesse caso os headers devem ser omitidos
 */
public record QuotaDecision(int limit, long remaining, long retryAfterSeconds,
                            long resetEpochSeconds, boolean measured) {
}
