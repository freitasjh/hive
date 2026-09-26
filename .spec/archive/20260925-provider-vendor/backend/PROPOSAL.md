# Change Proposal: Provider como Vendor Real (LangChain4j único)

> **ID**: `provider-vendor`
> **Status**: `Under Review`
> **Author**: orchestrator (Fase 1 SDD)
> **Date**: 2026-09-25
> **Source**: triagem Fase 0 (architect + product-manager) + decisão do usuário (refactor, LangChain4j único)

---

## 1. Problem Statement

- **Contexto**: cadastro/edição de agentes (`AgentEditorView.vue` ↔ `AgentControllerV1`) trata **framework** (`AiProviderType: SPRING_AI | LANGCHAIN4J`) como "provider" e mistura vendor+modelo em `LLMModel (OLLHAMA/OPENAI/OPENCODE/GEMINI)`.
- **Impacto atual**: UI permite salvar agentes que quebram em runtime — `OPENAI`/`GEMINI` caem em `UnsupportedOperationException` → 502 (`LangChain4jClientFactory` só instancia OLLAMA + OPENCODE); default da UI (`SPRING_AI` + `OPENAI`) cria agente quebrado; `SpringAiProvider` é stub que retorna texto fake persistido como mensagem do assistant (corrompe histórico); sem campo de apiKey/baseUrl; apiKey real estava hardcoded no código (removida em 2026-09-25, rotação pendente no provedor).
- **Evidência**: inventário architect Fase 0 (30+ arquivos), `ChatServiceImpl:119-132`, `LangChain4jClientFactory:13-25`, `AgentEditorView.vue:39-40,56-66`, `AdminViews.spec.ts:140-175`.

## 2. Proposed Solution

**Resumo**: provider passa a ser o vendor real de IA (`OPENAI | GEMINI | ANTHROPIC | OLLAMA | OPENAI_COMPATIBLE`), `model` vira string validada por catálogo, LangChain4j vira framework único (módulo `provider/spring-ai` removido), credenciais ficam no nível do tenant (tabela `provider_credential`, AES-256-GCM), e o cadastro ganha UX nova (seção "Provedor de IA", teste de conexão, chave mascarada).

**Abordagem**:
- Reaproveitar colunas `agent.provider_type`/`llm_model` (reescrita de valores via Flyway V9, zero DDL estrutural).
- `LangChain4jClientFactory` vira único builder (`switch` por vendor; `OPENAI_COMPATIBLE`/XAI via `OpenAiChatModel` + baseUrl); novas deps no BOM: `langchain4j-google-ai-gemini`, `langchain4j-anthropic` 1.19.0.
- Novo endpoint `POST /v1/agents/validate` (teste de conexão sem persistir) + CRUD `GET/PUT/DELETE /v1/provider-credentials` (GET mascarado).
- Nova tela `CredentialsView` (`/settings/providers`) + `AgentEditorView` sem apiKey (badge de status da chave do tenant).
- Alternativas descartadas: manter dual-framework (stub corrompe dados); enum de modelos fechado (recorrência do débito); apiKey por agente (usuário escolheu global por tenant — decisão 3b).

## 3. Scope

### In Scope
- [ ] Novo enum `AiProvider` (vendor) + remoção de `AiProviderType`; `LLMModel` → `model: String` + catálogo por vendor (MVP: OPENAI/GEMINI/ANTHROPIC/OLLAMA; OPENAI_COMPATIBLE só como destino de migração)
- [ ] `LangChain4jClientFactory` por vendor + remoção completa de `provider/spring-ai` (poms + factory + FE)
- [ ] Tabela `provider_credential` + `CredentialResolver` (tenant → credential → env fallback) + CRUD mascarado
- [ ] Flyway V9 migração de dados (mapeamento por `llm_model`; `OPENCODE` → `OPENAI_COMPATIBLE` + baseUrl gateway)
- [ ] `POST /v1/agents/validate` + tela `CredentialsView` + refactor `AgentEditorView` (decisões ux-designer vinculantes)
- [ ] Testes: unitários (factory, resolver, validators) + ITs (`AgentProviderIT`, `CredentialIT`, `ValidateIT`) + FE (novos componentes + specs atualizadas)
- [ ] SPEC §4.2 (`SYSTEM_SPECIFICATION.md`) + ADR-009

### Out of Scope
- [ ] Billing/cotas/rate-limit por chave, rotação automática, multi-chave/fallback por agente, BYOK por usuário final, playground comparativo (fase 2)
- [ ] Grok/Mistral nativos (fase 2 via `OPENAI_COMPATIBLE`)
- [ ] Rotação da chave vazada no provedor (usuário faz em paralelo — fora do repo)

## 4. Architecture Impact

| Aspecto | Impacto | ADR Necessário? |
|---------|---------|-----------------|
| Backend (`provider/api,langchain4j`, `agent/*`, `commons/api-tools`) | Enum novo, factory re-keyada, `spring-ai` removido, `CredentialResolver`, 2 controllers novos, handler cobre 422 | Sim (ADR-009) |
| Frontend (`AgentEditorView`, `CredentialsView` nova, `ProviderSelect`/`ApiKeyInput`/`ConnectionTester` novos, `agent-admin.service.ts`, types, specs) | Refactor seção LLM + tela nova + máscara de chave | Não (segue UX-SPEC) |
| Banco (migrations) | Nova tabela `provider_credential` (módulo a definir na SPEC) + V9 `UPDATE` dados `agent` | Sim (ADR-009) |
| Multi-tenancy | Credential isolada por `(tenant, provider)` + UNIQUE + FK CASCADE; teste IDOR obrigatório | Não (padrão existente) |
| Segurança / RBAC | `@HasPermission(AGENT_MANAGE)` nos novos endpoints; AES-256-GCM; GET mascarado; auditoria PUT/DELETE; SSRF em baseUrl (allowlist/IP privado) | Sim (security-review Fase 1) |
| Eventos | Nenhum | Não |

## 5. Delta Preview

| Documento Alvo | Tipo de Mudança | Seção Afetada |
|----------------|-----------------|---------------|
| `docs/product/SYSTEM_SPECIFICATION.md` | MODIFIED | §4.2 (admin prompts/agents: novo enum vendor, credenciais, validate) |
| `docs/adr/` | ADDED | `ADR_009_LangChain4j_Vendor_Provider.md` |
| `AGENTS.md` | MODIFIED | Seção provider/LangChain4j (se existir menção a spring-ai) |

## 6. Risk Assessment

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Agentes legados quebram pós-deploy (`SPRING_AI` sem mapeamento) | Média | Alto | V9 mapeia por `llm_model` (nunca por `provider_type`); audit counts antes; dual-read/janela + rollback |
| Chave tenant ausente → chat 422 em runtime | Média | Médio | Badge "faltante ⚠️" no editor + `needsKey`; env fallback documentado |
| SSRF via baseUrl (Ollama/compatible) | Baixa | Alto | Allowlist + bloqueio IP privado/RFC1918 + metadata IP (security-review) |
| Vazamento de chave (log/response) | Baixa | Alto | GET mascarado (`configured`+`last4`); nunca logar; IT anti-exposição |
| Release parcial BE/FE (defaults divergentes) | Média | Médio | Release único; specs FE atualizadas juntas (`AdminViews.spec`) |

## 7. Success Criteria

- [ ] Agente `OPENAI/GEMINI` criado na UI chama provider real com sucesso (zero 502 `UnsupportedOperationException`)
- [ ] Agentes legados migrados conversam sem reconfiguração (tabela de migração 100% mapeada)
- [ ] `GET /v1/provider-credentials` nunca expõe ciphertext; zero apiKey em logs (audit)
- [ ] `mvn clean install` + `mvn verify -pl integration-test` + `npm run test` + `npm run build` verdes
- [ ] Harness E2E: cadastro → teste conexão → chat 1º turno OK em < 3min

## 8. Completion Metadata

- **Archived on**: —
- **Duration**: —
- **Archive path**: —
- **Files changed**: —
