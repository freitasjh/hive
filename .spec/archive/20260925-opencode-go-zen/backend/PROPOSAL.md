# Change Proposal: OpenCode GO/Zen + Provider Custom

> **ID**: `opencode-go-zen` · **Status**: `Under Review` · **Author**: orchestrator (Fase 1) · **Date**: 2026-09-25
> **Source**: triagem Fase 0 (architect + PM) + decisão usuário (B: enums GO/ZEN)

## 1. Problem Statement
- **Contexto**: agentes presos a 4 vendors fixos; `OPENAI_COMPATIBLE` escondido da criação; sem GO/Zen; modelo digitado no escuro → 400/502.
- **Impacto**: sem acesso a custo menor (GO $10/mês) nem gateway próprio; catálogo estático desatualiza.
- **Evidência**: `VendorCatalog` estático; `hiddenFromCreate=true`; endpoints `/v1/models` GO/Zen públicos verificados (200).

## 2. Proposed Solution
**Resumo**: enums `OPENCODE_GO` + `OPENCODE_ZEN` (baseUrls fixas, credencial por tenant) + `OPENAI_COMPATIBLE` promovido a "Custom" + discovery `GET /v1/providers/{go,zen}/models` (filtrado chat/completions, cache 10min) + headers Go (UA + `x-opencode-session`) + teste de conexão mandatório + LGPD banners.
**Abordagem**: zero DDL (VARCHAR sem CHECK); denylist curada responses/messages; pinned fetch (padrão `KbPinnedHttpClient`); rate limit chat novo + log de uso; 422 `MODEL_UNAVAILABLE` sem fallback silencioso.
**Descartados**: presets sem enum (usuário escolheu enums); cron refresh (manual); metering/caps (fase 2); protocols responses/messages (OUT).

## 3. Scope
### In Scope
- [ ] `AiProvider.OPENCODE_GO/ZEN` + `VendorCatalog` (presets, denylist) + `CredentialPolicy/Resolver` (env names, prefix skip) + factory branches (OpenAi + baseUrl + headers)
- [ ] `OpencodeDiscoveryService` + `GET /v1/providers/{go,zen}/models` (auth, rate-limit, cache, evict) + `POST /validate` estendido
- [ ] Custom first-class (`hiddenFromCreate=false`, label, baseUrl editável) + CGNAT block + https não-loopback + pin no call
- [ ] Rate limit chat por tenant + log tokens + `key_version` increment + 422 `MODEL_UNAVAILABLE`
- [ ] FE: `VENDOR_ORDER`, metadata (costHint, readOnlyBaseUrl, remoteModels, lgpdWarning), `useRemoteModels`, dropdown remoto + staleness, CredentialsView GO/ZEN/custom, badge stale, banners LGPD
- [ ] Testes: units + ITs (discovery, IDOR, SSRF custom, stale 422) + FE specs + harness GO/custom
- [ ] SPEC seção providers + ADR
### Out of Scope
- [ ] `/responses` e `/messages`, cron, metering/cotas/bloqueio por uso, override de chave por agente, Grok/Mistral nativos

## 4. Architecture Impact
| Aspecto | Impacto | ADR? |
|---|---|---|
| Backend (`provider/*`, `agent`) | Enum + discovery + headers + rate limit + custom guards | Sim |
| Frontend (select, editor, credenciais) | 7 vendors, dropdown remoto, banners | Não (UX-SPEC) |
| Banco | Zero DDL | Não |
| Multi-tenancy | Linhas `(tenant,GO/ZEN)` isoladas | Não |
| Segurança | SSRF custom, LGPD, session header, custo Zen | Sim (security) |
| Eventos | Nenhum | Não |

## 5. Delta Preview
| Documento | Tipo | Seção |
|---|---|---|
| `docs/product/SYSTEM_SPECIFICATION.md` | MODIFIED | §4.3 (GO/ZEN/custom/discovery) |
| `docs/adr/` | ADDED | `ADR-011-Opencode-Go-Zen.md` |

## 6. Risks
| Risco | Prob | Impacto | Mitigação |
|---|---|---|---|
| Lista remota instável | Alta | Médio | Cache + badge + 422 legível, nunca fallback |
| Zen sem teto (fatura) | Média | Alto | Copy + rate limit chat + log uso (caps fase 2) |
| SSRF custom | Baixa | Alto | Guard + CGNAT + https + pin (R2) |
| Privacidade (prompts p/ terceiro) | Certo | Médio | Banners LGPD (R3) |

## 7. Success Criteria
- [ ] Agente GO e custom respondem 200 em harness; model removido → 422 legível (zero 500); descoberta lista ≥1 filtrado; suites verdes; zero regressão `CredentialIT`.

## 8. Completion Metadata — —/—/—/—
