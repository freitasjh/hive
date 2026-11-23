# HANDOFF — avaliação pelo `architect`

**Data:** 2026-11-21 · **Origem:**Pair Programmer / dev-planner + deepsearch · **Destino:** agente `architect` (`.agents/agents/architect/AGENT.md`).

## 1. Inventário exato do disco

Existe em `.spec/prd/public-chat-api/`:

| Arquivo | Conteúdo |
|---|---|
| `EXPLORATION.md` | varredura da API pública, divergências D1–D6, fontes |
| `CONSTRAINTS.md` | restrições técnicas/contrato/processo, débitos herdados, premissas confirmadas (§7) |
| `DESIGN-DECISIONS.md` | 4 decisões com alternativas e trade-off + pré-condição springdoc |
| `ARCHITECTURE.md` | fronteiras, modelo alvo por frente, fluxos, evidência mínima |
| `ADR-INDEX.md` | ledger de 5 ADRs **candidatos** — não é o índice canônico |
| `HANDOFF.md` | este documento |

**Não existe:** `PRD.md`, `RESEARCH.md`, diretório `ADRs/`, qualquer `ADR.md`, qualquer código de produto. O pedido do operador mencionou "ADR e PRD criado" — regresso factual: foram criados artefatos de estudo e planejamento; ADR e PRD formais ainda não.

## 2. Barreira de esteira (primeira decisão do arquiteto)

`.agents/rules/prd-adr-pipeline.md:9-16`: layout é `.spec/prd/{prd-id}/PRD.md` (16 seções, §16 = aprovação humana) e `.spec/prd/{prd-id}/ADRs/ADR-00N-{slug}/ADR.md`; **nenhum `ADRs/**` nasce antes de §16 preenchido**. Exceção de `:43-45` (ADR sem PRD) exige "arquitetural sem valor de produto novo" — não se aplica: limite por tenant e UI admin são valor de produto. Índice canônico exige colunas `order`, `id`, `title`, `status`, `depends_on`, `dir`, `branch`, `merge` (`:20-27`).

Pergunta ao arquiteto: gerar `PRD.md` com §16 para aprovação, ou registrar exceção explícita?

## 3. Pendências para avaliação

### 3.1 Pré-condição bloqueante
springdoc `2.8.14` fora da matriz oficial para Spring Boot `4.1.0` (FAQ: `4.1.x` → `3.1.x`–`3.2.x`). `v3.2.0` é a linha 4.1; `v3.2.1` roda em 4.1, mas traz swagger-core 3.0/Jackson 3 e rename de artefatos. Gate: `GET /v3/api-docs` com paths `/v1/public/**` e schemas não vazios. Decidir se o ADR 1 é só anotação ou upgrade de dependência.

### 3.2 ADRs candidatos (sequência proposta)

| # | Tema | Depende de |
|---|---|---|
| 1 | Contrato tipado + OpenAPI da API pública | pré-condição springdoc |
| 2 | Centralizar conversão/system + trace (K6/K11) | 1 |
| 3 | UI admin de credenciais sobre `client_credentials` | 1 |
| 4 | Cota por tenant (100 req/min compartilhado) + headers de rate limit | 3 |
| 5 | Fronteira `agent/api` × `agent/impl` (K3) | 2 |

### 3.3 Decisões já-fixadas pelo operador (2026-11-21)

1. `client_id` **não** é por tenant: é globalmente único (`V1__public_api_client.sql:28`); tenant vem da linha; um tenant pode ter N clients com labels distintos (`:29`).
2. Limite de chat: **por tenant compartilhado** — um contador por tenant, clients competindo pelo mesmo budget, default **100 req/min**.
3. Origem do limite: **migration em `publicapi_db`**. `RedisQuotaGuard.checkTenant` já existe (`RedisQuotaGuard.java:114,158`), sem chamador de produção; entra no lugar de `checkClient` no chat (`PublicChatServiceImpl.java:135`).
4. `public_api_client.rate_limit_per_minute` (default 60) fica obsoleto no chat; não remover coluna sem ADR de limpeza.
5. Token endpoint mantém 30/120/30 próprios (`PublicAuthControllerV1.java:81`); os 100 não se aplicam ali.
6. Crédito (budget consumível) **fora** deste PRD, registrado como débito; RPM e crédito são dimensões distintas.

### 3.4 Pontos ainda em aberto no ADR 4

- **Staleness do limite:** claim `plim.r` é lido na emissão (TTL até 3600 s); alterar o limite só vale para tokens novos. Alternativa: ler a tabela a cada turno, com custo de DB por requisição.
- **Migration V2:** popular tenants já existentes com 100 req/min; definir `CHECK` (atual em `>= 1` para a coluna antiga) e nominalidade da coluna nova — não usar nome genérico que trave o modelo de crédito futuro.

### 3.5 Fora do escopo, mas registrar

K12 (custo de ocupação por retry 2× + read timeout 120 s vs cota), SEC-02 (401 na cadeia humana), gate externo ERP (VALIDATION §4), `application-dev.properties` sujo, Ai Services/`ChatMemoryStore` (riscos A8/A11/A12/A13/A15 do TECH-BRIEF).

## 4. Restrições Repassadas (PICCO confirmado)

- Somente `.md`; nenhum código-fonte, config, script ou migration criado/editado nesta etapa.
- Escrita restrita a `.spec/prd/**` (contrato do `architect`).
- Não tocar ciclo MFA nem `workflow-state.json`.
- Teto de 5 ADRs por PRD.
- Recomendação baseada em lib/API/versão exige URL + data; inferência sem fonte é rotulada.
- `architect` deve sincronizar Brain antes de decidir (`brain-context.md`), conhecer as notas `arquitetura/projetos/hive/decisoes/api-publica-chat` e `estudos/hive/public-chat-api-premissa-tenant-2026-11-21`, e registrar a decisão ao final.

## 5. Fontes consultadas (todas em 2026-11-21)

- `https://www.rfc-editor.org/rfc/rfc6749.html` — §2.3.1, §4.4, §5.1
- `https://platform.openai.com/docs/api-reference/chat/create`
- `https://developers.openai.com/api/docs/guides/error-codes`
- `https://developers.openai.com/api/docs/guides/rate-limits`
- `https://redis.io/learn/howtos/ratelimiting`
- `https://springdoc.org/faq.html`
- `https://springdoc.org/`
- `https://github.com/springdoc/springdoc-openapi/releases`
- `https://learn.openapis.org/best-practices.html`
- `.agents/rules/prd-adr-pipeline.md:9-27,43-45`
- `.spec/prd/langchain4j-ai-services/TECH-BRIEF.md:101-188,243-289`

## 6. Prompt pronto para colar no agente `architect`

```text
Avalie o pacote em .spec/prd/public-chat-api/ (EXPLORATION, CONSTRAINTS,
DESIGN-DECISIONS, ARCHITECTURE, ADR-INDEX, HANDOFF). Antes de qualquer
decisão, sincronize o Brain (brain-context.md) e leia as notas
arquitetura/projetos/hive/decisoes/api-publica-chat e
estudos/hive/public-chat-api-premissa-tenant-2026-11-21.

Entregue: (1) parecer sobre a barreira de esteira em
.agents/rules/prd-adr-pipeline.md:9-16 — PRD.md/§16 ausente, ADRs não criados;
(2) decisão sobre a pré-condição springdoc 2.8.14 × Spring Boot 4.1.0;
(3) validação ou correção das 5 decisões de DESIGN-DECISIONS.md, com atenção
ao novo modelo de cota por tenant (100 req/min compartilhado, migration em
publicapi_db, claim plim.r vs leitura por turno);
(4) os dois pontos em aberto do ADR 4 (staleness do claim e migration V2);
(5) blueprints no formato exigido pelo seu AGENT.md: system_overview,
component_diagram, api_contracts, data_model, trade_offs.

Restrições: somente .md em .spec/prd/**; não tocar MFA nem workflow-state.json;
não escrever código de produto; teto de 5 ADRs. Registre a decisão no Brain
e aponte o próximo passo da esteira.
```
