# Bug — `GET /auth/me` inexistente retorna 500 (auth-me-500)

> Workflow: Bugfix Fast-Track — Fase B1 concluída
> Severidade: 🟡 Média (contrato HTTP; máscara p/ anônimos via 401 do filtro)
> Camada: **Backend** (security/web + commons/api-tools) · Data: 2026-09-25
> Status: **B1 concluída — aguardando aprovação B1→B2**

## Bug
`GET /hive/v1/auth/me` + Bearer válido → **500** `{"code":"INTERNAL_ERROR",...}` em vez de 404. Reproduzido ✅ (backend UP; controle `/auth/permissions` → 200; sem token → 401). Mesmo defeito em `/hive/actuator/health` (permitAll, sem starter) → classe de bug, não caso isolado. Log: `/tmp/opencode/hive-authme500-backend3.log`.

## Causa raiz
`ControllerExceptionHandler.java:156` mapeia `NoHandlerFoundException` (legado — nunca lançado no Spring 6.1+ sem flag `throw-exception-if-no-handler-found`, ausente). Spring Boot 4.1/MVC 7 lança `NoResourceFoundException` (`No static resource v1/auth/me...`) → sem handler → `@ExceptionHandler(Exception)` (:40-45) → 500. Agravante: `UserAccountControllerV1.java:64-67` tem placeholder "para não retornar 500" — mesma classe, recorrência.

## Solução proposta (B2) — Opção A (recomendada)
Handler `NoResourceFoundException` → 404 RFC 7807 (`NOT_FOUND`, "Recurso não encontrado"), unificado ou não com o legado. 1 arquivo, risco mínimo; cobre todas as rotas inexistentes autenticadas.

## Alternativa B (produto — NÃO decidida)
Criar `GET /auth/me` real (perfil do logado). Possível origem do achado: frontend pode chamar `/auth/me` esperando 200. Recomendação: A agora + feature separada p/ B se PM confirmar.

## Testes necessários (IT)
1. Rota inexistente autenticada → 404 `NOT_FOUND` (nunca 500). 2. Sem token → 401 (contrato atual). 3. Opcional: caso nominal `/auth/me` → 404 problem+json.

## Riscos
Baixo (só intercepta exceção hoje 500). Médio (fora de escopo): outras exceções Spring 6.1+/7 sem handler (ex.: `MissingPathVariableException`) — sugerir auditoria em débito. Frontend pode chamar `/auth/me` esperando 200 → 404 limpo sinaliza implementar B ou remover chamada.

---

## B2/B3 resultado
- Handler unificado implementado + `NoResourceIT` (3 ITs). Review APPROVED (nota 🟢: `NoHandlerFoundException` é fallback defensivo — só dispara com `throw-exception-if-no-handler-found=true`; NÃO remover `NoResourceFoundException` do handler).
