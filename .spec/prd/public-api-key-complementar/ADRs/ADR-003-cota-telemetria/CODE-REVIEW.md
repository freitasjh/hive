# CODE-REVIEW — ADR-003: Cota própria da key e telemetria preservada

**Data:** 2026-11-24 · **Branch:** `adr/public-api-key-complementar/03-cota-telemetria` (HEAD `84d7640`) · **Árvore:** sem commit no momento do veredito.

## Veredito

**APPROVED** — 0 🔴 · 0 🟠 · 0 🟡 · 0 🔵 · `must_fix_before_done: []`. `reviewer_session`: `ses_dffa34effffeZMrCoNwL2t4pqv`.

## Guardas verificadas

| # | Guarda | Resultado |
|---|---|---|
| 1 | V4: `key_chat_requests_per_minute INT NOT NULL DEFAULT 100`, `CHECK 1..10000`, backfill `COALESCE(cota do tenant V2, 100)` em Postgres real, sem `repair()`/`DROP`, V1/V2/V3 intactas | ✅ |
| 2 | `keyKey` = `hive:public:quota-key:{keyId}`, distinto de `quota-tenant`; `acquireKey` delega ao mesmo `acquireDecision`; Lua SHA `7f02dd6f433abdb0` idêntico; fail-open omite atributos/headers | ✅ |
| 3 | `AuthenticatedKey` com `keyId`/`keyChatRequestsPerMinute`; filtro seta atributos só no ramo `sk-`; serviço escolhe `acquireKey`/`acquireTenant`; stub da ADR-001 ganhou 2 argumentos sem mudar `plim`/contrato | ✅ |
| 4 | Emissor de JWT/`plim`, `ChatTurnGateway`, `TurnRequest`, `TurnReply`, `ChatTrace`, `TurnOutcomeMapper`, regras de prompt, V2, ADR-001/002, PRD/RESEARCH, arquivadas, MFA, estado e UI sem diff | ✅ |
| 5 | Headers derivados de `QuotaDecision.limit()` refletem 1 (JWT) e 2 (key) no mesmo tenant; `turnId`/`clientId` preenchidos nos dois caminhos; fail-open reexecutado | ✅ |
| 6 | 411 front + 530 backend + 152 ITs (+2), build 0, `git diff --check` limpo, `.flattened-pom.xml` sem diff, portas por PID sem `pkill -f`, R4 registrado, nenhum teste apagado/`@Disabled` | ✅ |

## Risco residual

O IT de backfill reaplica o `UPDATE` manualmente em vez de provar que a V4 popula uma linha pré-existente **no momento da migration**; o SQL foi inspecionado e está correto. Registrar como débito de cobertura, não bloqueante.

## Bloqueio de âncora

O bloqueio real é a âncora B do `gate.js` lendo `code_review_status`. Este artefato e o registro escopado no `workflow-state.json` tornam o veredito verificável sem alterar `current_phase` nem o registro da Fase 4 MFA.
