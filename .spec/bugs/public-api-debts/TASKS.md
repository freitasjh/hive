# TASKS — Débitos da API pública (pós-PRD)

**Data:** 2026-11-23 · **Origem:** PRD `public-chat-api` arquivado em `.spec/archive/20261123-prd-public-chat-api/` · **Fase:** itens 1–3 `BugCompleted`; itens 4–6 em registro/insumo.

>Este documento é a análise (R3). Implementação só após HITL B1→B2 e reprodução real (R1). Itens 4–6 não são bugfix de código: são decisão, validação externa e insumo de produto.

## 1. N+1 na listagem admin de clients

- **Tipo:** bugfix de performance · **Responsável:** backend-engineer · **Prioridade:** P1
- **Causa (arquivo:linha):** `PublicApiClientAdminServiceImpl.java:96-104` — `list()` chama `fill()` por entidade; `fill()` faz `findByClientRef` para grants e outro para prompts. Resultado: 1+2N queries (41 com `size=20`, 201 com `size=100`). Registrado no `CODE-REVIEW.md` da ADR-003 como débito não bloqueante.
- **Solução:** buscar grants e prompts em lote com `findByClientRefIn(refs)`, agrupar por `clientRef` e montar o DTO sem consulta por item. `@EntityGraph` não se aplica (associações por `clientRef`, sem `@OneToMany`).
- **Arquivos previstos:** `PublicApiClientAdminServiceImpl`, repositórios de grant/prompt, `PublicApiClientAdminIT`.
- **Impacto:** apenas listagem admin; teto de 100 itens; sem mudança de contrato HTTP.
- **Testes:** IT asserindo contagem de queries ou exercício do limite (definir na implementação); suíte completa.
- **Riscos:** mudança de ordenação/agrupamento; perda de grants/prompts se o agrupamento errar.
- **R1 — reproduzido:** Hibernate Statistics em Postgres real — **42 queries** com `size=20` e **202** com `size=100` (`2N+2`) antes do fix; **4 queries** depois. `PublicApiClientAdminQueryIT:23-25`.

## 2. IT de omissão de headers `X-RateLimit-*` em fail-open

- **Tipo:** bugfix de cobertura · **Responsável:** backend-engineer · **Prioridade:** P2
- **Causa (arquivo:linha):** `RedisQuotaGuardTest:121-132` prova `measured=false` no guard, mas nenhum IT afirma que a **resposta HTTP** omite `X-RateLimit-Limit/Remaining/Reset` quando Redis cai. Débito registrado no `CODE-REVIEW.md` da ADR-004.
- **Solução:** estender IT de cota para simular falha do Redis e asserir ausência dos três headers, preservando `Retry-After` quando aplicável.
- **Arquivos previstos:** IT de cota em `backend/integration-test`.
- **Impacto:** nenhum comportamento de produção muda; só evidência.
- **Testes:** IT novo + suíte completa.
- **Riscos:** falha do Redis mal simulada pode mascarar o caminho real.
- **R1 — reproduzido:** nenhum IT cobria a omissão; headers reais capturados sem `X-RateLimit-*` no sucesso e na negação (`retry-after=7`). `PublicApiQuotaFailOpenIT:29-31`.

## 3. IT de staleness do `plim.r`

- **Tipo:** bugfix de cobertura · **Responsável:** backend-engineer · **Prioridade:** P2
- **Causa (arquivo:linha):** `PublicClientTokenIssuer.java:118-121` congela `plim.r` no JWT; `PublicChatServiceImpl.java:140-147` consome o snapshot. A ADR-004 aceitou staleness ≤ TTL, mas não há IT provando que token antigo mantém o limite anterior até reemissão. Débito do `CODE-REVIEW.md` da ADR-004.
- **Solução:** IT emitindo token com limite X, alterar `chat_requests_per_minute` para Y, provar que o token antigo ainda usa X e que token novo usa Y.
- **Arquivos previstos:** `PublicApiTenantQuotaIT` ou IT de emissão de token.
- **Impacto:** nenhum comportamento muda; só evidência do contrato aceito.
- **Testes:** IT novo + suíte completa.
- **Riscos:** teste dependente de TTL/ordem de emissão; isolar com tenant novo.
- **R1 — reproduzido:** nenhum IT cobria; token antigo `plim.r=250`, tenant alterado para `1`, antigo segue `250` e novo `1`. `PublicApiPlimStalenessIT:20`.

## 4. SEC-02 — 401 na cadeia humana

- **Tipo:** decisão humana / segurança · **Responsável:** operador + `security-engineer` (parecer)
- **Evidência:** Brain `sessoes/hive/2026-10-29` — "SEC-02 401 cadeia humana (pendente decisão)". Fora do PRD por decisão do arquiteto.
- **Entrega desta esteira:** registrar a pergunta (qual código/resposta esperada para a chain humana), impactos e opções. **Não implementar** sem decisão explícita.
- **Testes:** N/A nesta etapa.
- **Riscos:** decidir por palpite pode alterar contrato de autenticação fora do escopo.

## 5. Gate externo ERP (VALIDATION §4)

- **Tipo:** validação externa manual · **Responsável:** operador/QA externo
- **Evidência:** Brain `sessoes/hive/2026-10-29` — "Gate externo ERP (VALIDATION §4) manual".
- **Entrega desta esteira:** checklist do que o ERP precisa validar (emissão de token, chat completions, erros 429/400, headers), sem afirmar execução.
- **Testes:** N/A nesta etapa.
- **Riscos:** marcar como concluído sem execução real — proibido (regra de instalação verificada aplicável por analogia).

## 6. Crédito/budget e K12

- **Tipo:** decisão de produto · **Responsável:** `product-manager`
- **Evidência:** PRD §2/§14 — crédito fora de escopo; K12 = retry 2× + read timeout 120 s não cobertos pela cota de 100 req/min. Brain `arquitetura/.../decisoes/api-publica-chat`.
- **Entrega desta esteira:** insumo com as opções (custo por token, budget recarregável, limite por ocupação) e a pergunta de negócio. **Não escolher** sem product-manager.
- **Testes:** N/A nesta etapa.
- **Riscos:** transformar débito de produto em bugfix de código sem decisão.

---

## Portões

- **R1 (Harness-first):** concluído para itens 1–3 — evidência real registrada acima, antes da edição.
- **R2:** encerrar portas por `lsof -ti :PORTA` → conferir dono → `kill` por PID; nunca `pkill -f`.
- **R3:** esta análise; comandos e paths devem vir de `harness.config.json`/`AGENTS.md`, não de literal inventado.
- **R4:** `brain_store` obrigatório ao fechar cada bug.
- **B1→B2:** aprovado pelo operador em 2026-11-23; implementação dos itens 1–3 concluída.
- **H6:** suíte 0 falhas, novos ITs verdes, `code-reviewer` sem 🔴, Brain registrado.
