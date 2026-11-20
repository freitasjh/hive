package br.com.systec.hive.security.impl.service;

import jakarta.servlet.http.HttpServletRequest;

/** Contexto de rede da requisição, usado no binding do challenge e no refresh. */
public final class ClientContext {

    private ClientContext() {
    }

    /** IP real considerando proxy reverso — mesmo critério de SecurityTokenServiceImpl. */
    public static String remoteIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwarded.split(",")[0].trim();
    }

    public static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
