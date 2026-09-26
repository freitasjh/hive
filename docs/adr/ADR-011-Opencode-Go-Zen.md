# ADR-011 — OpenCode GO/Zen + Provider Custom

- **Status:** Aceito (Fase 3, Batch 2/2 — `opencode-go-zen`, 2026-09-25) · **Data:** 2026-09-25
- **Decisores:** usuário (enums GO/ZEN) + architect + PM + ux + security

## Contexto
Tenants presos a 4 vendors; GO ($10/mês) e Zen (pay-as-you-go) expõem `/v1/models` público sem protocolo por modelo; custom restrito a migração.

## Decisão
1. Enums `OPENCODE_GO`/`OPENCODE_ZEN` (baseUrls fixas, zero DDL); custom = `OPENAI_COMPATIBLE` first-class.
2. Discovery manual `GET /v1/providers/{go,zen}/models` (pinned, sem redirect, 512KB, denylist responses/messages, cache 10min, evict em MODEL_UNAVAILABLE).
3. Headers Go (`customHeaders` na factory: `User-Agent: hive/1.0` + `x-opencode-session` = UUID da chat_session, sem input path do cliente; verificado E2E); teste mandatório; sem fallback (422); rate limit chat + log uso; LGPD visível; caps fase 2.

## Alternativas
| Alternativa | Rejeição |
|---|---|
| Presets sem enum | Usuário escolheu enums (credenciais separadas, multi-gateway) |
| Cron refresh | Tráfego/falha silenciosa; manual + staleness |
| Metering/caps MVP | Fase 2 |
| responses/messages | OUT (outro protocolo/cliente) |

## Consequências
Positivas: custo menor, gateways próprios, catálogo vivo. Negativas: lista instável (badge+422), Zen sem teto (rate limit+log; caps fase 2), privacidade terceiro (banners).
