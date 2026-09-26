# Bugfix — Login retorna 500 com credenciais inválidas (login-500)

> Workflow: Bugfix Fast-Track — Fase B1 Análise
> Severidade: 🟠 Alta (segurança/contrato; sem perda de dados)
> Camada: **Backend**
> Módulo: `security` (api/impl/web) + `commons/api-tools` (handler)
> Data: 2026-09-24
> Status: **✅ Concluído (B1→B2→B3→B4)** — 2026-09-24
> Gates: B1 análise aprovada · B2 implementado · B3 review APPROVED (0 🔴) + fixes A1/A4/S1-S4 verdes · B4 suite verde (BE unit+IT 6/6, FE 93/93)
> Débitos follow-up: DT-1 RefreshTokenNotFoundException sem httpStatus · DT-2 SecurityFilter JDK SecurityException (ver Brain `regras/projetos/hive/debitos/auth-security-2026-09-24`)

---

## Bug

`POST /hive/v1/auth/login` com credenciais inválidas retorna **500 `INTERNAL_ERROR`** em vez de 401.

Reproduzido ✅ (harness R1, backend UP, 3 cenários):

| Cenário | Request | Status atual |
|---|---|---|
| Usuário inexistente | `{"username":"noexiste_xyz","password":"errada123"}` | 500 `{"code":"INTERNAL_ERROR","detail":"Ocorreu um erro inesperado...","instance":"/hive/v1/auth/login"}` |
| Usuário real + senha errada | `{"username":"teste.user","password":"senha_errada_123"}` | 500 (idem) |
| Payload vazio | `{"username":"","password":""}` | 500 (idem) |

**Esperado:** 401 com corpo RFC 7807 (`code=INVALID_CREDENTIALS`, mensagem genérica "Credenciais inválidas", sem distinguir usuário/senha — anti-enumeração, A07). O próprio `LoginControllerV1` documenta só `200` + `403` (doc incompleta: falta `401`).

---

## Causa Raiz

| # | Arquivo:linha | Causa |
|---|---|---|
| 1 | `backend/security/impl/.../service/LoginServiceImpl.java:29,38-40` | `catch (Exception e)` engole `BadCredentialsException`/`InternalAuthenticationServiceException` e relança **`java.lang.SecurityException`** (JDK, sem import — `LoginService.java:8`, `SecurityTokenService.java:11,15,19` idem). Ponto central: afeta ambos os cenários. |
| 2 | `backend/security/impl/.../service/AuthenticationServiceImpl.java:29-30` | `catch (Exception) → new BaseException("Erro ao tentar encontrar o usuario", e)` destrói a semântica: `UserNotFoundException extends ObjectNotFoundException` (teria handler 404 dedicado em `ControllerExceptionHandler.java:65`) vira `BaseException` com `httpStatus=null` (construtor `BaseException(String,Throwable)` não seta — `BaseException.java:35-37`). |
| 3 | `backend/commons/api-tools/.../exceptions/ControllerExceptionHandler.java` | Inventário: **nenhum handler** para `java.lang.SecurityException`, `AuthenticationException`, `BadCredentialsException`, `InternalAuthenticationServiceException`, nem para a `SecurityException` de domínio (`security/api/.../exceptions/SecurityException.java:7`, estende `BaseException` sem `httpStatus` → handler `BaseException` `:46` cai em 500). JDK `SecurityException` é `RuntimeException` → handler genérico `:120` → **500**. |

Cadeia (usuário inexistente): `LoginControllerV1.java:57` → `LoginServiceImpl.java:33` (`authenticationManager.authenticate()` throws `InternalAuthenticationServiceException`, caused by `AuthenticationServiceImpl.java:30` `BaseException`, caused by `UserServiceImpl.java:66` `UserNotFoundException`) → `LoginServiceImpl.java:38-40` relança JDK `SecurityException` → handler `RuntimeException` `:120-125` → 500.

**Gap de testes:** `LoginServiceImplTest` só cobre happy path + refresh; zero teste de login inválido. Nenhum `*IT.java` de login (só `SmokeIT`).

---

## Solução Proposta (B2)

1. `AuthenticationServiceImpl.loadUserByUsername`: não embrulhar em `BaseException`; relançar `UsernameNotFoundException` (contrato Spring `UserDetailsService`) — preserva `hideUserNotFoundExceptions` do `DaoAuthenticationProvider` (vira `BadCredentialsException`, anti-enumeração ✅ A01/A07).
2. `LoginServiceImpl.login` + interfaces (`LoginService`, `SecurityTokenService`): trocar JDK `SecurityException` pela `SecurityException` de domínio com `httpStatus=UNAUTHORIZED` (ou exceção dedicada `InvalidCredentialsException extends BaseException`, 401); tratar `BadCredentialsException`/`InternalAuthenticationServiceException`/`AuthenticationException` explicitamente → 401 com mensagem genérica. `catch (Exception)` genérico fica só como fallback 500 real.
3. `ControllerExceptionHandler`: adicionar `@ExceptionHandler(AuthenticationException.class)` → 401 RFC 7807 (`code=INVALID_CREDENTIALS`); respeitar `getHttpStatus()` da `SecurityException` de domínio; atualizar `@ApiResponse` do `LoginControllerV1` (401 em vez de 403 para login).
4. **Impacto/outros fluxos:** `refresh-token` (`SecurityTokenServiceImpl` lança `SecurityException` de domínio sem httpStatus → hoje também 500; ganha 401/403 coerente); `SecurityFilter.java:68` (lança `SecurityException` no filtro, fora do `@RestControllerAdvice` — comportamento inalterado); `@HasPermission` 403 (`AccessDeniedException` handler `:134`) intacto.

---

## Testes Necessários

- **Unitários:** `LoginServiceImplTest.when_login_with_invalidCredentials_then_throw401` (mock `authenticate` → `BadCredentialsException`, assert exceção de domínio com `httpStatus=401`); `AuthenticationServiceImplTest.when_loadUserByUsername_with_unknownUser_then_throwUsernameNotFound`.
- **Integração** (novo `LoginIT.java` em `backend/integration-test/`, base `WebIntegrationTest` + `DatabaseCleaner`, sem `JwtTestHelper` — rota pública): usuário inexistente → 401; senha errada → 401 (`INVALID_CREDENTIALS`, sem vazar existência); login válido → 200; `Content-Type: application/problem+json`.
- **Regressão:** suite existente (`mvn test` + `mvn verify -pl integration-test`).

---

## Riscos

- Mensagem 401 deve ser **genérica única** (nunca distinguir usuário/senha) — enumeração de usuários (A07).
- Trocar exceção na interface (`throws`) quebra callers/mocks — varrer `catch (SecurityException)` / `throws SecurityException` (ex.: `SecurityFilter`, `SecurityTokenServiceImpl.java:261` usa JDK `SecurityException` — uniformizar com cuidado).
- `BaseException(String,Throwable)` com `httpStatus=null` é armadilha recorrente (handler cai em 500) — considerar default no getter (fora deste bugfix; registrar como melhoria).

---

## Evidências

- Log backend: `/tmp/opencode/hive-backend2.log` (linhas 260, 554, 818)
- Harness: backend UP durante reprodução, DOWN após (R2 ✅); Postgres `postgres-pgvector` + Redis `hive-redis` usados; nenhum código alterado em B1.
