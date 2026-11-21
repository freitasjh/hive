---
prd_id: langchain4j-ai-services
adr_id: ADR-005
unit: legacy
status: todo
title: "Congelar fronteira de módulos, teto de maxTokens e débito da aresta"
data: 2026-11-21
decisor: architect
---

# ADR-005 — Congelar fronteira de módulos, teto de maxTokens e débito da aresta

> Unidade de decisão: 1 ADR = 1 branch = 1 PR (merge humano). Decisão apenas.

- **Decisão**: manter `ChatTurnGateway` em `agent/api` como porta única, `agent/impl` como adaptador e `publicapi/impl` como 2º chamador; registrar a aresta `publicapi/impl → agent/impl` como **débito separado, fora deste PRD**. Definir teto único de geração de `maxTokens` no gateway (`min(requested, 4096)`), preservando a validação `[256,4096]` do DTO e separando "teto de geração" de "teto de requisição".
- **Motivo**: responder #2 (complemento), #7, #9 e #10 (`PRD.md:186,191,193,194`) com evidência de código e pareceres, sem inventar regra de negócio nem aprofundar acoplamento.
- **Alternativas descartadas**: mover `CredentialResolver`/`ProviderResolution`/`isModelNotFound` para `agent/api` **neste PRD** — fonte: Brain `arquitetura/projetos/hive/arquitetura/api-publica-chat/fechamento-d5-d7-d3` D5 — 2026-09-27 (reforma estrutural, fora do teto de 5 e sem valor de produto novo).
- **depende-de**: ADR-001, ADR-002
- **Interface congelada**: porta em `agent/api`; aresta congelada como débito; clamp no gateway; DTO fail-fast; MFA intocável.
- **Fora desta decisão**: cota/ocupação (K12), migração da aresta, ciclo MFA.
- **Consequências se estiver errado**: teto no gateway pode divergir da semântica por provider se a fábrica mudar — IT de caracterização por provider mitiga.
- **Status**: todo

## Decisão detalhada

**Pergunta #2 e #9 — porta e aresta.**
**Veredito #2 (complemento ao ADR-002)**: sim, a porta permanece. Brain `fechamento-d5-d7-d3` D5 — 2026-09-27 fixa: **porta em `agent/api`, adaptador em `agent/impl`**; `ProviderResolution` não pode ir para `agent/api` sem criar ciclo `agent/api → agent/impl` porque depende de `CredentialResolver` (`agent/impl`). **Veredito #9: a aresta `publicapi/impl → agent/impl` fica como débito separado, fora deste PRD.** Evidência: `publicapi/impl/pom.xml:33-37`, `PublicChatServiceImpl.java:9-10`; o parecer `parecer-consolidado-2026-09-27` classifica essa aresta como pior que a aresta `agent → security` que P2 evita; `TECH-BRIEF.md:97,249`. Nenhuma alternativa A–D a elimina por si mesma (`RESEARCH.md:215`). Resolvê-la exige a reforma D5 acima, que não é pré-condição das 7 regras nem da adoção de AiService. Nenhum ADR deste lote aprofunda a aresta: ADR-001 usa apenas `agent/api/util/ProviderMessages`.

**Pergunta #7 — "maxTokens tem teto único e onde mora?" (K8).**
**Veredito: sim — no gateway.** Hoje: `InferenceParamsDTO.java:9-19` valida `maxTokens ∈ [256,4096]`, `temperature ∈ [0,2]`, `topP ∈ [0,1]`; `LangChain4jTurnGateway.java:66-82` repassa sem clamp; a fábrica aplica por provider (`LangChain4jClientFactory.java:116-124` OpenAI, `:138-144` OpenCode, `:154-159` Ollama `numPredict`, `:170-176` Gemini `maxOutputTokens`, `:187-193` Anthropic com null = default 1024). O parecer de 2026-09-27 afirmava que o gateway "pode clampar `maxTokens`" — **divergência histórica, não fato atual** (`TECH-BRIEF.md:156`, K8). Decisão: `effectiveMaxTokens = min(requested, 4096)` aplicado no gateway antes da fábrica; o DTO mantém a validação `[256,4096]` como fail-fast. Justificativa de arquitetura (com impacto de produto, sujeito a revisão do `product-manager` em novo ADR): a fábrica atende 5 providers com semânticas divergentes, então clamp por provider é disperso; o gateway é a fronteira comum aos 2 chamadores e é onde a política de turno vive (Brain D5). Parâmetros por chamada em Ai Services são suportados — Afirmação — `ChatRequestParameters` por chamada — https://docs.langchain4j.dev/tutorials/ai-services — 2026-11-21 (**não versionada**; existência da classe confirmada por import em `AiServices.java` 1.19.0, `TECH-BRIEF.md:161`) — o que permite o clamp sem mudar a fábrica.
**Não confundir**: `PublicChatServiceImpl.java:114-131` limita **número de mensagens e caracteres do request** (`m=200`, `c=32000`, `t=256000`, claim `plim`) — "teto de requisição", regra distinta que permanece em `publicapi`.

**Pergunta #10 — restrição MFA.**
**Veredito: confirmado.** Nenhuma decisão deste ADR — nem de qualquer outro deste PRD — toca `security/**/Mfa*` nem `identity/**/Mfa*`. Registro: `PRD.md:118,177,194`, `TECH-BRIEF.md:56`, `workflow-state.json:2-6`, `ADR-INDEX.md` (seção "Confirmação MFA").

**K12 (débito registrado, não resolvido)**: retry 2× + read timeout 120 s (`LangChain4jClientFactory.java:33`, `LlmRetryPolicy.java:31-35`) contra cota 60/min (`PublicChatServiceImpl.java:134`); cota limita taxa, não ocupação (`TECH-BRIEF.md:258`; Brain `parecer-consolidado-2026-09-27`). Sem solução por palpite.

## depende-de

ADR-001 (baseline da conversão/ordem) e ADR-002 (porta + proxy definidos).

## Interface congelada

1. **Porta**: `ChatTurnGateway` em `agent/api`, 1 método, exatamente 2 chamadores (`ChatTurnGateway.java:29-36`; `TECH-BRIEF.md:78,87,97`). Adaptador em `agent/impl`; nenhum chamador invoca o adaptador diretamente.
2. **Aresta congelada como débito**: a dependência `publicapi/impl → agent/impl` (`publicapi/impl/pom.xml:33-37`) permanece **inalterada** neste PRD e é explicitamente registrada para ADR futuro fora do teto de 5. Nenhum ADR deste lote a aprofunda; `publicapi/impl` continua consumindo apenas `agent/api` para a conversão centralizada (ADR-001). Não mover `CredentialResolver`/`ProviderResolution`/`isModelNotFound` aqui.
3. **Teto de geração**: `MAX_TOKENS_CEILING = 4096`, aplicado no gateway como `min(requested, 4096)` antes da chamada à fábrica (`LangChain4jTurnGateway.java:66-82`). Nenhum clamp novo é introduzido na fábrica; as semânticas por provider permanecem (`LangChain4jClientFactory.java:116-193`).
4. **Validação de entrada**: faixa `[256,4096]` permanece no DTO (`InferenceParamsDTO.java:9-19`) como fail-fast; valores abaixo de 256 continuam sendo 400, não clampados para cima.
5. **Teto de requisição**: `m=200`, `c=32000`, `t=256000` permanecem em `publicapi` (`PublicChatServiceImpl.java:114-131`), nominalmente distintos do teto de geração.
6. **MFA (intocável)**: zero mudanças em `backend/security/**/Mfa*` e `backend/identity/**/Mfa*`; verificação por `git diff --stat` nesses paths (gate H6).

## Alternativas consideradas (com fonte)

- **Resolver a aresta neste PRD**: REJEITADA — exige mover `CredentialResolver`/`ProviderResolution`/`isModelNotFound` para `agent/api` (Brain `fechamento-d5-d7-d3` D5 — 2026-09-27), reforma estrutural sem valor de produto novo e fora do teto de 5.
- **Clamp na fábrica**: REJEITADO — 5 providers com semânticas divergentes (`numPredict`, `maxOutputTokens`, null = 1024; `LangChain4jClientFactory.java:154-193`).
- **Clamp só no DTO**: REJEITADO — o caminho público não passa necessariamente pelo mesmo DTO de UI; o gateway é a fronteira comum aos dois chamadores.
- **Herdar a afirmação antiga de que o gateway já clamparia**: REJEITADO — `LangChain4jTurnGateway.java:66-82` não contém `Math.min` (`TECH-BRIEF.md:156`, K8).

## Consequências e riscos K tratados

- **K3**: registrado como débito separado, não resolvido — decisão explícita (pergunta #9).
- **K8**: mitigado — teto único definido e localizado; divergência histórica reconciliada com o código.
- **K12**: registrado, não resolvido (sem palpite).
- **K9/K10**: não tratados aqui (ADR-003 e ADR-002, respectivamente).
- **Se estiver errado**: teto no gateway pode divergir se um provider mudar sua semântica de `maxTokens` — IT por provider mitiga; reversibilidade média via novo ADR.

## Como verificar (IT de caracterização)

1. IT de teto: request com `maxTokens = 9999` → gateway envia 4096 à fábrica; request com `maxTokens = 100` → DTO responde 400 (congela itens 3 e 4).
2. IT de não divergência: `publicapi` mantém o mesmo diagnóstico `model-not-found` que o chat humano (`TECH-BRIEF.md:291(d)`), sem depender de mudança na aresta congelada.
3. IT de teto de requisição: limites `m/c/t` do claim `plim` continuam em `PublicChatServiceImpl.java:114-131` e não são confundidos com geração.
4. Verificação estática: nenhuma alteração em `publicapi/impl/pom.xml:33-37` nem em `PublicChatServiceImpl.java:9-10`.
5. Portão: suíte completa 0 falhas; `git diff --stat` com zero linhas em paths MFA (pergunta #10).
