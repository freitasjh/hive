# VERIFY — mfa-onboarding-opcional (fechamento 2026-11-26)

Feature sem ADR na esteira v2 (implementada em `feature/login-tela-mfa` antes da adoção do layout por ADR). Este arquivo é o artefato de fechamento P5, com as 3 dimensões do `verify-gate.md`.

## Evidência de origem

- `.spec/agents/workflow-state.pending.json:13-23` — `code_review_status: passed`, review `APPROVED`, 0 críticos, 3 lots aprovados; `harness_status: passed`.
- `harness_note` — backend 501 testes + `MfaLoginFlowIT` 4/4; frontend 280 testes + build 0; convite não-modal (`MfaSetupInvite.tsx`); desafio com `tenantId`, verify/recovery com `X-Tenant-ID`.
- Dívidas 🔵 conhecidas: tautologia em `LoginServiceImpl:87`, `@JsonInclude` em `LoginResponseDTO`, teto 50 prompts, `X-Forwarded-For`, reautenticação recente no setup.

## Dimensão 1 — Completeness ✅

- Sem `TASKS.md`/`SPEC.md` desta feature (legado fora do layout v2) — **N/A justificado**.
- Substituto: 3 lots de review aprovados, `must_fix_before_done: []` no estado pendente.
- Zero TODO/FIXME novo introduzido pelo fechamento (artefato somente `.md`).

## Dimensão 2 — Correctness ✅ (com exceção registrada)

- Build frontend: 0 erros (`harness_note`).
- Suíte: 501 testes backend + 4/4 IT + 280 testes frontend, 0 falhas.
- `MfaLoginFlowIT` cobre os 3 cenários: login sem MFA (convite), com MFA (desafio), verify/recovery.
- **Exceção**: H3 greenfield (wipe + re-cadastro pela UI, 12 passos, Network/Console limpos) **não executado** — perfil `chrome-devtools` bloqueado por outro agente na época. Registrado em `workflow-state.json.overrides` + dívida no Brain. Override não apaga a falha; permanece como passivo de verificação.

## Dimensão 3 — Coherence ✅

- Review `APPROVED` com 0 críticos/0 🟠/0 🟡; veredito de parecer, âncora B lendo `code_review_status: passed`.
- Escopo congelado: `backend/security/`, `frontend/src/modules/hive/auth/components/MfaSetupInvite.tsx`.
- Arquivos de `publicapi`/frontend da outra sessão ficam **fora** deste fechamento (não comitados, conforme `harness_note`).

## Resultado

```text
VERIFY STATUS: APPROVED ✅ (com override H3)

Bloqueadores: nenhum novo.
Observações:
- H3 real pendente — executar antes de qualquer release que dependa de MFA.
- Dívidas 🔵 listadas acima permanecem no backlog.
- workflow-state.json root deve ser atualizado por delegação a papel de implementação.
```
