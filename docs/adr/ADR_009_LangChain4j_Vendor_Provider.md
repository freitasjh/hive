# ADR-009 — Framework único LangChain4j + provider como vendor real

- **Status:** Aceito (2026-09-25 — EPIC provider-vendor implementado: V9/V10 aplicadas, `provider/spring-ai` removido, `POST /v1/agents/validate` + credenciais por tenant no ar)

> ### ⚠️ Parcialmente superseded em 2026-09-27 — anotações, não reescrita
>
> A **decisão** deste ADR (LangChain4j como framework único, provider como vendor real, credencial
> por tenant) segue **inteira e em vigor**. O que mudou depois são fatos que ele registra como se
> ainda fossem o estado, e que o código já desmentiu:
>
> - **`AiProvider` tem 7 valores, não 5.** `OPENCODE_GO` e `OPENCODE_ZEN` entraram depois (ADR-011,
>   Aceito 2026-09-25). A lista de 5 deste texto é a allowlist do `tenant_llm_config` ampliada — a
>   verdade hoje está em `provider/api/.../AiProvider.java`.
> - **`POST /v1/agents/validate` não existe.** O probe pergunta a um **provedor** se um modelo
>   responde, então foi para a rota de provedor: **`POST /v1/providers/validate`**. A rota antiga
>   morreu com o conceito de agente (decisão registrada em **[ADR-013](ADR-013-Assistant-Sem-Rag-Sem-Agente.md)**).
> - **`AGENT_MANAGE`** guardava o editor de agente e hoje guarda trocar a chave de API e o modelo
>   padrão do tenant. Ver `.spec/tech-debts/security-rbac/TASKS.md` P1-2.
>
> O motivo de anotar em vez de reescrever é o de sempre: ADR é registro do raciocínio da época.
> Reescrever aqui apagaria a lição de que enum e rota mudam por conta de outras decisões.

- **Data:** 2026-09-25
- **Contexto:** Hive AI (Java 21 + Spring Boot 4.1.0 + LangChain4j 1.19.0 + Vue 3.5 + PrimeVue 4)
- **Decisores:** usuário + architect + product-manager + ux-designer

## Contexto

O enum legado de provider tratava frameworks como "provider" (um valor para Spring AI, outro para LangChain4j), enquanto o enum legado de modelos misturava vendor e nome de modelo. Consequências: `OPENAI`/`GEMINI` quebravam em runtime (502), o provider legado Spring AI era stub com resposta fake persistida, o default da UI criava agentes quebrados, sem campo de apiKey/baseUrl.

## Decisão

1. **Framework único: LangChain4j.** Remover o módulo `provider/spring-ai` por completo (poms `provider/`, `dependencies/`, `bootstrap/`, `agent/impl/` + factory + frontend).
2. **Provider = vendor real:** novo enum `AiProvider { OPENAI, GEMINI, ANTHROPIC, OLLAMA, OPENAI_COMPATIBLE }` (MVP aprovado 1a; Grok/Mistral nativos = fase 2 via compatible).
3. **`model: String`** validada por catálogo por vendor (enum legado de modelos removido do contrato).
4. **Credencial global por tenant** (decisão 3b): tabela `provider_credential(tenant_id, provider, ciphertext, key_version, base_url?)`, AES-256-GCM via `AttributeConverter`, chave mestra em env; `CredentialResolver` (tenant → credential → env fallback → 422 `CONFIG_MISSING`).
5. **Migração V9 por `llm_model`** (nunca por `provider_type`): `OLLHAMA→OLLAMA`, `OPENAI→OPENAI`, `GEMINI→GEMINI`, `OPENCODE→OPENAI_COMPATIBLE` + baseUrl gateway (decisão 2a). Reaproveitar colunas (zero DDL estrutural).
6. **Novo endpoint `POST /v1/agents/validate`** (teste de conexão sem persistir; sem `apiKey` no body — usa a do tenant) + CRUD mascarado `GET/PUT/DELETE /v1/provider-credentials`.

## Alternativas Consideradas

| Alternativa | Motivo da rejeição |
|---|---|
| Manter dual-framework | `spring-ai` sem dependências reais; stub corrompe histórico de chat |
| Enum de modelos fechado estendido | Recorrência do débito (modelos mudam semanalmente; enum gerou promessa quebrada) |
| apiKey por agente | Usuário escolheu global por tenant (rotação central; evolução futura: override por agente) |
| `XAI_GROK` como enum MVP | Fase 2 via `OPENAI_COMPATIBLE` + baseUrl (menos superfície) |
| baseUrl livre sem validação | Risco SSRF — exige allowlist + bloqueio de IP privado |

## Consequências

- **Positivas:** agentes OPENAI/GEMINI funcionam; zero 502; UI honesta; chaves fora do código e do banco em claro; remoção de liability (stub).
- **Negativas:** breaking change (release único BE+FE); migração de dados exige audit + janela; `provider_credential` exige gestão da chave mestra (env/KMS).
- **Segurança:** ver `security-review` Fase 1 da SPEC (STRIDE + OWASP); rotação da chave vazada (`LangChain4jClientFactory:25`, removida do código em 2026-09-25) pendente no provedor.

## Conformidade

- [ ] `.agents/rules/*` inalteradas (nenhum conflito; `backend-integration-tests.md` cobre novos ITs)
- [ ] SPEC `.spec/backend/provider-vendor/SPEC.md` detalha implementação
- [ ] Flyway: nova tabela (módulo a definir na SPEC) + V9 dados
