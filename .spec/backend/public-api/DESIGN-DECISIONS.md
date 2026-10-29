# DESIGN-DECISIONS — API pública de chat

- **Fase:** dev-planner 2 (Desafie)
- **Data:** 2026-09-27
- **Postura:** as 10 decisões fechadas são respeitadas. Onde a medição **contradiz o motivo declarado**
  de uma delas, isso está escrito com o número do achado e uma correção proposta — sem reabrir a
  decisão.

---

## 1. Confronto A/B/C — onde a API pública mora

### 1.1 (A) Proposta: `/hive/v1/public/**` com cadeia de segurança própria — **ADOTADA**

| Prós | Contras medidos |
|---|---|
| A allowlist e a cadeia não se misturam: token público não alcança endpoint interno *por ausência de rota naquele chain* | Custo: 1 `SecurityFilterChain` + 1 `PublicApiSecurityFilter` + 1 advice de erro + 1 limiter |
| Formato de erro OpenAI e formato interno (RFC 7807 `SNAKE_UPPER`) convivem sem disputa de handler | Duas tabelas de grant que só a rota pública usa |
| `aud` distinto impede o replay cruzado entre painel e API pública | 3 tabelas novas + 1 histórico Flyway novo |
| O duplo `/v1` é o que o SDK OpenAI espera (§`EXPLORATION.md §4.6`) | Um módulo Maven novo (`publicapi`) com 4 submódulos |

**Veredito:** é a única das três que **fecha a lacuna de endpoint** que o briefing identificou, e
fecha por construção em vez de por lista.

### 1.2 (B) Dentro do `/v1` existente, reaproveitando `ChatControllerV1` — **REJEITADA**

Argumento decisivo, medido: `ChatControllerV1:66-70` faz
`if (auth.getPrincipal() instanceof User user) { return user.getId(); }` e, se não for `User`,
lança `AccessDeniedException`. Um principal `client_credentials` **não é** um `User`. E
`@HasPermission("CHAT_SEND")` sobre uma credencial pública seria uma concessão a mais numa lista que
já concede 6 a qualquer usuário.

Três problemas que se multiplicam:

1. **A permissão de chat e a permissão de administração administrativa se misturam no mesmo espaço de nomes.**
   `CHAT_SEND` é o que o ERP precisa; `AGENT_MANAGE` é o que o ERP **não** pode ter. Enquanto ambas
   vivem na mesma lista hardcoded, "conceder `CHAT_SEND` ao ERP" é "conceder as 6 ao ERP".
2. **O contrato diverge.** O interno devolve `ChatResponseDTO` (`sessionId`, `message`, `role`,
   `timestamp`); o público devolve `chat.completion` + `metadata.hive`. Uma resposta que é OpenAI
   *e* interna é duas respostas.
3. **Stateless vs `chat_session` diverge.** `ChatServiceImpl.chat()` **cria sessão** quando
   `sessionId == null` e `promptTemplateId != null` (`:143-151`). "Stateless" e "reaproveitando o
   controller" são incompatíveis por construção.

**O que (B) tem de certo, e que foi aproveitado:** a existência de `HasPermission` + `HasPermissionAspect`
+ uma porta `ChatTurnGateway` com um método. É a **moldura**, não o caminho.

### 1.3 (C) ERP chamando o gateway direto, sem HTTP — **REJEITADA**

O ERP é outro sistema. "Chamar o gateway direto" significaria library compartilhada, build
compartilhado, versão acoplada entre ERP e Hive, e um `ChatTurnGateway` no classpath de um processo
que não é o nosso. O resultado seria: sem boundary de tenant (`@Filter` depende de
`TenantContext` + `TenantAspect`, que é servlet-scoped), sem fronteira de rede para o
`tenant_llm_config`, sem token, sem cota, e **sem log** estruturado próprio. Tudo que P-a/P-b/P-c
existem para entregar seria desmontado.

Além disso: o ERP é da **mesma organização** e o contrato é HTTP desde o começo (o `client_credentials`
existe justamente para ser um boundary). Voltar para library embutida é o caminho **mais caro** de
reverter depois.

**Lição do ADR-013 aplicada:** "construir a segunda capacidade antes do primeiro loop estar fechado
custa caro mesmo quando a segunda está correta". (C) é a segunda capacidade (integração) desenhada
antes de o primeiro loop (conversa stateless) existir. Não é opção.

### 1.4 A quarta opção que o briefing não listou, e é a que eu recomendo sobre (A)

**Módulo Maven novo `publicapi`** (`api` + `impl` + `dbupdate` + `web`), em vez de espalhar a feature
pelos módulos existentes. Não é uma alternativa a (A) — é a **forma de (A)** que a topologia do
projeto pede.

O grafo medido hoje:

```
agent/api   → hive-commons-api, hive-provider-api
agent/impl  → hive-commons-impl, hive-commons-api, hive-agent-api,
              hive-identity-api, hive-provider-api, hive-tenant-api, hive-provider-langchain4j
security/api→ hive-commons-api
security/impl→ hive-commons-api, hive-commons-impl, hive-security-api, hive-identity-api
tenant/api  → hive-commons-api, hive-provider-api
```

`agent` **não** depende de `security`. `security` **não** depende de `agent`. São brothers.

| Onde a credencial vive | Edge necessária | Veredito |
|---|---|---|
| `security` (novo `dbupdate`) + chat em `agent/web` | **`agent/web → security/api`** — nova aresta entre brothers | 🟡 Aceitável, mas `agent` passa a carregar preocupação de autenticação de terceiro |
| `identity` (já tem `dbupdate`) | `identity` vira dono de identidade de **máquina** — é `identity` de **humano** | 🔴 Errado conceitualmente |
| `agent` (já tem `dbupdate`) | sem edge nova, mas credencial de autenticação dentro do módulo de chat | 🔴 Pior: mistura boundary de segurança com boundary de conversa |
| **`publicapi` (novo)** | **`publicapi → {agent-api, security-api, tenant-api, provider-api, commons-*}`** — só arestas **de saída**, nenhuma para dentro | ✅ |

`publicapi` é um bounded context legítimo: superfície de integração externa, com **dados próprios**
(credencial, concessões, allowlist), **esquema de auth próprio** (`client_credentials`, `aud` próprio)
e **envelope de erro próprio** (OpenAI). Ele **compõe** `agent` e `security` em vez de algum deles
compor o outro. E é reversível: apagar o módulo inteiro remove a feature sem tocar em nenhum outro.

**Custo honesto:** 4 `pom.xml` novos, 1 `@Configuration` Flyway, 1 `pom` pai. E o bootstrap tem que
depender de `hive-publicapi-impl` e `hive-publicapi-web`.

✅ **Ratificada pelo usuário (D5 da revisão).** `ux-designer` **não** entra na lista de obrigatórios
desta feature (D6): superfície headless, sem tela. O gatilho de volta está nomeado no PLAN.

---

## 2. 🗡️ Confronto do briefing: "a API pública pode ser a segunda implementação de `ChatTurnGateway`"

> *Briefing:* "`ChatTurnGateway` existe para receber uma segunda implementação (ADR-012, gatilho:
> 'existir a primeira tool'). **A API pública pode ser a primeira.**"

**Eu argumento contra.** E o motivo não é "não valida a porta" — é que a segunda implementação seria
uma **cópia de comportamento**, e cópia do que a porta existe para centralizar é pior do que a
alternativa.

### 2.1 A porta não precisa de segunda implementação — ela precisa de **segundo chamador**

`LangChain4jTurnGateway` (179 linhas) possui exatamente estas decisões:

| O que ele decide | Linha | Um `PublicApiTurnGateway` teria que… |
|---|---|---|
| `SystemMessage(template)` antes de `conversation` | `buildMessages` | **reimplementar** a precedência de system prompt |
| Regra de "modelo removido" (404 + corpo JSON citando o modelo; 404 + HTML = `baseUrl` sem `/v1`) | `isModelNotFound` | **reimplementar o vocabulário HTTP do transporte** |
| Retry via `LlmRetryPolicy` | `:70` | **reimplementar a política de retry** |
| Guarda de resposta vazia (→ `EMPTY_RESPONSE`, não exceção) | `:100-105` | **reimplementar** |
| `apiKey` mascarado em `toString()` | `TurnRequest` | **reimplementar** |

Cada uma dessas é uma **decisão** que o ADR-012 diz, textualmente, que a porta existe para ter num
lugar só: *"A abstração do turno é a porta. ... o gateway mantém o prompt, a chamada ao provider, a
política de retry, a guarda de resposta vazia e a regra de modelo-não-encontrado."*

Uma segunda implementação com a mesma comportamento copia cinco decisões. O resultado é que
`MODEL_UNAVAILABLE` deixa de concordar entre os dois caminhos — e **a concordância entre os dois é
justamente o que a porta compra**. `ChatServiceImpl:227` usa
`LangChain4jTurnGateway.isModelNotFound(failure)` (estático) justamente para que a classificação não
dependa de qual instância respondeu.

### 2.2 O gatilho 5 do ADR-012 **não** dispara

Gatilho 5, textual: *"`ChatTurnGateway` ganhar uma **segunda implementação real** (não um mock de
teste). A segunda implementação é o que valida a porta; um mock não valida."*

Uma classe nova no mesmo comportamento **não é** "uma segunda implementação" no sentido do gatilho —
é a mesma implementação com outro nome. O gatilho 5 continua **não disparado**, e o ADR-012 deve
registrar isso explicitamente, com o motivo, para que o próximo leitor não conclua que a porta
"falhou em ser validada".

O que **realmente** valida uma porta, e o que esta feature entrega: **um segundo consumidor real da
mesma porta, com o mesmo comportamento e o mesmo diagnóstico.** Isso é melhor que a segunda
implementação para o objetivo do ADR-012 (que era concentrar a decisão), e não exige a duplicação.

Nenhum dos outros 4 gatilhos dispara: não há tool (1), o split TX1/TX2 continua (2), o histórico é
nosso e limitado (3), a versão continua 1.19.0 (4).

### 2.3 O briefing está certo na **premissa** e errado no **alvo**: 4 de 6 componentes são mesmo implementações paralelas

A afirmação "4 de 6 componentes são implementações paralelas" é verdadeira, e vale — só que em
outros lugares. Mapeadas:

| # | Componente | Onde a API pública **reusa** | O que é **paralelo** | A paralela se justifica? |
|---|---|---|---|---|
| 1 | `ChatTurnGateway` | **100%** — mesma instância | nada | ❌ **NÃO.** Ver §2.1. Reusar é o que preserva o diagnóstico |
| 2 | `LangChain4jTurnGateway` | 100% (via 1) | nada | ❌ NÃO |
| 3 | `ChatServiceImpl` | **nada** (§`CONSTRAINTS.md §1.7`) | o *compositor* de sessão | ✅ **SIM**, mas não como cópia: extrair `ProviderResolution` + `TurnOutcomeMapper` (§4) |
| 4 | `SecurityFilter` | nada | `PublicApiSecurityFilter` | ✅ **SIM** — principal não-humano não passa por `loadUserByUsername`. É a **defesa em profundidade** que fecha o `AGENT_MANAGE` |
| 5 | `SecurityTokenServiceImpl.generateToken` | `decodeAndVerify` (privado, verificar extração) | emissor de token público | ✅ **SIM** — lista de permissões hardcoded vs concessões persistidas é a **P-a** |
| 6 | `ControllerExceptionHandler` | nada | envelope OpenAI | ✅ **SIM** — RFC 7807 `SNAKE_UPPER` vs OpenAI `snake_lower` são formatos genuinamente distintos |
| 7 | `SlidingWindowRateLimiter` (memória) | **algoritmo/forma**, não estado | `RedisSlidingWindowRateLimiter` | ✅ **SIM** — estado compartilhado vs por-JVM é uma diferença real, não duplicação |

**A regra que eu adoto, e que generaliza para o cliente agente (fase 2):**

> **Reusar o algoritmo e a decisão. Paralelizar a superfície de política.**
>
> `ChatTurnGateway`, a classificação de falha e o token **verificador** são **um** por sistema: duas
> implementações significa dois diagnósticos que discordam. `SecurityFilter`, o envelope de erro, o
> emissor de token e o *armazenamento* de cota são **paralelos por natureza**: são política, e
> política legitimately diverge.

Isso responde também ao "o desenho deve servir ao cliente agente, que é o próximo passo": o cliente
agente é um **segundo chamador** da mesma porta, exatamente como a API pública. Ele **não** precisa de
segunda implementação, e **não** vai precisar. O que ele vai precisar que já existe: porta
estável, grant model, cota por identidade, e um envelope de erro. **É por isso que ele é barato depois
desta.**

### 2.4 Então, o que a API **deve** reusar de `ChatServiceImpl` em vez de forkar?

A pergunta do briefing tem uma resposta incomoda, e ela é o achado mais acionável de todo o
planejamento:

> **Nenhuma linha, e isso é o motivo de `ChatServiceImpl` estar acoplado demais. O plano precisa dizer
> isso — e o plano diz.**

A decomposição medida (`CONSTRAINTS.md §1.7`) é: três responsabilidades soldadas em `chat()` —
(1) resolver o prompt de uma sessão, (2) rodar o turno, (3) persistir e traçar. A (2) já foi extraída
(é a porta). A (1) e a (3) são **inherently** session-scoped.

Ou seja: **não há nada a forkar, e nada a lamentar.** O que existe é um acoplamento real e nomeado,
em dois lugares específicos, e **ambos têm correção barata e sem mudança de comportamento**:

| Acoplamento | Onde | Correção |
|---|---|---|
| O serviço lê **contexto de segurança ambiente** (`:123` `TenantContext`, `:127` `SecurityUtils.currentUserId()`) | 2 linhas | Extrair `ProviderResolution` e `TurnOutcomeMapper` deixa `chat()` com **zero** leitura de ambiente; o tenant e a identidade chegam por parâmetro, como já chegam em `chat(request, tenantId, userId)` |
| **Duas decisões de política** estão *inline* dentro do método que também faz transação: `:176-186` (provider/credencial) e `:203-246` (os 3 status) | ~80 linhas | Extrair para 2 colaboradores com 1 método cada |

**Isso é a mesma jogada do ADR-012, um nível acima.** O ADR-012 extraiu "o que não tem dono" do
serviço para a porta; o que falta extrair é "o que tem dono, mas mora no lugar errado porque o
método também faz TX". E o resultado é duplo: a API pública reusa, e `ChatServiceImpl` encolhe de 345
para ~265.

⚠️ **A armadilha do ADR-013, aplicada aqui:** `ChatServiceImpl` foi validado por **execução**, não por
mock — `systemBlockChars=5028` estável em três rodadas de E2E foi a prova de que a trace mede. Se essa
extração for feita "só para a API pública", o caminho interno muda e a única evidência que sustenta
`ChatServiceImpl` é um teste com mock. **Gate: o batch de refactor não pode ser o mesmo batch que
entrega a API.** Ele vem antes (ver `PLAN.md`), passa pela suíte, e a entrega da API vem depois.
Se alguém inverter, o ADR-013 já avisa o que acontece.

---

## 3. Decisões de projeto (ADRs candidatos)

| # | Decisão | Alternativa rejeitada | Motivo |
|---|---|---|---|
| **D1** | Módulo novo `publicapi`, 4 submódulos, histórico Flyway próprio, **sem `repair()`** | (1) `security/dbupdate` + edge `agent→security`; (2) `identity`; (3) `agent` | Bounded context próprio; só arestas de saída; reversível apagando o módulo. Sem `repair()` porque o histórico nasce vazio — e a ausência faz divergência futura **morrer alto**, que é o correto (§`CONSTRAINTS.md §1.2`) |
| **D2** | **`SecurityFilterChain` separada** para `/v1/public/**`, com `denyAll()` do path público no chain humano | Ramo por claim dentro do `SecurityFilter` | Fecha a lacuna de endpoint **por ausência de rota**, não por lista. ⚠️ **Causa do 500 corrigida na revisão (B2):** não é `loadUserByUsername:51` (inalcançável) — é `validateTokenRevoke:49` → `decodeAndVerify` exigindo `aud=hive.web` → `BaseException` em `:63` → 500. E `SecurityFilter.java:28` é `@Component`: **auto-registrado fora do `FilterChainProxy`**, roda em `/v1/public/**` de qualquer forma (B1). Correção: `FilterRegistrationBean.setEnabled(false)` em `SecurityConfig` (D7) |
| **D3** | Reusar `LangChain4jTurnGateway`; a API pública é **segundo chamador**, não segunda implementação | `PublicApiTurnGateway` | §2.1/2.2. Preserva a concordância de diagnóstico entre os caminhos |
| **D4** | Extrair `ProviderResolution` + `TurnOutcomeMapper` de `ChatServiceImpl` **antes** de escrever a rota pública | Escrever a rota pública e deixar `ChatServiceImpl` intacto | Sem a extração, a rota pública **força** a cópia. Com ela, é aditiva. E `ChatServiceImpl` encolhe |
| **D5** | Cota pública em **Redis** (sliding-window log, `SORTED SET` + Lua atômico), keyed por `clientId`; teto por tenant em chave separada; `ChatRateLimiter` em memória **intocado** | Estender `SlidingWindowRateLimiter` em memória com `clientId` | §`EXPLORATION.md §4.5`. Redis já é produção (`TokenCacheServiceImpl`); `10N/min` sob N instâncias é bug de correção; `ConcurrentHashMap` sem eviction |
| **D6** | `client_secret` com **SHA-256** no storage, 256 bits aleatórios, exibido uma vez, rotação com janela de graça | `AesGcmAttributeConverter` (como o `provider_credential`) | Segredo precisa ser verificável, não recuperável; e a master key não deve governar o ciclo de vida de segredos de terceiros. Precedente: `SecurityTokenServiceImpl:195-208` (a **primitiva**, não a escolha de devolver o hash) |
| **D7** | `aud = "hive.public"` no token público, distinto de `aud = "hive.web"` | Reusar a audiência | Impede replay cruzado entre painel e API pública com a mesma assinatura HMAC e o mesmo segredo. Custo: uma constante e um branch no filtro |
| **D8** | `client_id` **globalmente único** (não por tenant) | Único por tenant | O endpoint de token roda **sem** filtro de tenant (`TenantAspect` engole `TenantNotFountException` e segue desligado). Único-por-tenant seria ambíguo, e o tenant viria do request — direção de confiança errada |
| **D9** | Envelope de erro **local ao controller público**, sem `@RestControllerAdvice` global novo | `@RestControllerAdvice(basePackages=…)` com `@Order` | `ControllerExceptionHandler` é `@RestControllerAdvice` sem qualifier e já tem `@ExceptionHandler(Exception.class)`; a precedência de handler nesse caso é sutil e um envelope errado em produção é um bug de contrato. Handler local é explícito |
| **D10** | `messages[]` **rejeita `role: "system"`** com erro distinguível | Aceitar e deixar o `system` wins | `buildMessages` põe o template do cliente **primeiro** e `addAll(conversation)` depois — um `system` no request chega por último e é a última instrução do modelo. Aceitar = o ERP sobrescreve o prompt que a plataforma na plataforma (decisão 3: o prompt é do cliente, na plataforma) |
| **D11** | `model` ausente → default do tenant; presente e ≠ modelo do tenant → `400 unknown_model` | Ignorar `model` (como faz vast.ai) | O SDK exige o campo. Aceitar qualquer valor (mesmo string vazia) joga fora o **único** check de sanidade que pega "o ERP aponta para o Hive errado" no primeiro request |
| **D12** | `stream:true` → erro explícito, não silêncio | Ignorar o campo | Decisão 9 sem SSE. Silêncio é o pior resultado: o ERP ficaria esperando tokens que nunca chegam |
| **D13** | `PINNED` = **asserção de conformidade** (tripwire), não snapshot; resposta carrega `prompt_version` + `prompt_fingerprint` | Congelar de verdade (template imutável-on-activate) | §`EXPLORATION.md §4.1`. O modelo atual **não tem snapshot** e criar um exige mudar o editor da UI — batch próprio. A decisão 6 + eco da versão já eliminam a mudança silenciosa |
| **D14** | Versionamento de contrato **por path**, não por header `Accept` | Header de versão | O SDK OpenAI monta o path; interceptar header antes do roteamento é complexidade sem ganho, já que o `/v1` está no path duas vezes |
| **D15** | Idempotency key **fora da v1** | `Idempotency-Key` na v1 | O fluxo é stateless e sem efeito colateral: reenviar a mesma requisição é seguro por construção. Uma chave de idempotência exigiria persistência (o que contradiz "sem `chat_session`") para resolver um problema que o desenho já resolveu. **Dívida registrada** |
| **D16** | Ordem de checagem: **401 → 429 → 403 allowlist → 404 → 409 → 400 → 422 → 502** | 404 antes do 403 | `prompt_not_in_allowlist` tem de responder **existindo ou não** o código. Inverter transforma o 403/404 em oráculo de existência **dentro do tenant** — que é o que a decisão 4 quer impedir |
| **D17** | `access_token` TTL público = **10 min**, sem refresh token | 15 min (do interno) / 30 min | OAuth 2.0: `client_credentials` **não** emite refresh token (RFC 6749 §4.4.3). Janela de exposição de token_commit: 5–15 min é a faixa recomendada para M2M |
| **D18** | Hot path **sem banco**: assinatura + `jti` blacklist (Redis) + `client` revogado (Redis) + allowlist do claim | Validar `clientId` contra o banco a cada request | Revogação imediata (Redis) sem o custo de um SELECT por request. Precedente do padrão: cache de 60s + invalidação imediata no Redis |
| **D19** | Default de `pin_mode` = **`FOLLOW_ACTIVE`**; provisionamento **sempre** informa `pin_mode` explicitamente | `PINNED` como default (posição do PM) | **Arbitrado pelo usuário pela própria condição do PM** (*"1 integração, prompt do mesmo time que faz o deploy"*). `PINNED` torna **todo PUT de rotina do prompt um 409** — risco de disponibilidade. `PINNED` segue disponível por credencial/código onde o contrato exigir |
| **D20** | Política do turno: porta e função pura em `agent/api` (`ProviderMessages` estático, `isModelNotFound`+`ModelNotFound`); adaptador em `agent/impl`; `LangChain4jTurnGateway` **é tocado** | Gateway intacto (regra copiada) ou tudo em `impl` | **Emenda ratificada pelo usuário.** Sem tocar o gateway, ou D3 vira mentira ou a regra é **copiada** — que é o que o ADR-012 existe para evitar. `ProviderResolution` fica no `impl`: `agent/api → agent/impl` é ciclo Maven |
| **D21** | Teto de payload = **dado da credencial** (3 colunas + claim `plim`); recusa explícita 400/413, **nunca** truncamento | Constante de código; ou "teto do proxy" (não existe proxy no caminho) | Número em código ninguém calibra sem deploy. O ERP chama o Hive **direto**, e `LlmRetryPolicy` **retenta** — payload sem teto × retry é a pior combinação de custo e erro |
| **D22** | Metadados de uso em **log**; `ChatTrace` ganha `tenantId`+`turnId`+`clientId` no **Batch 1B** | 4ª tabela no hot path | 3 condições (log pesquisável junto ao usuário — **verificada**); zero migration; reversão para tabela + LGPD se a condição cair |
| **D23** | Provisionamento por **`CommandLineRunner`** (tela 10.1 cortada); `ProvisioningPayloadBuilder` como código; `ux-designer` **não** exigido | Tela de provisionamento | Superfície headless não tem UX. Gatilho para voltar nomeado: 2 credenciais de produção, ou provisionamento por quem não tem acesso ao ambiente |

## 4. Extrações que precedem a feature (D4) — o recorte

**Recorte final (D5, revisão de 2026-09-27).** Dois colaboradores em `agent/impl`, uma função pura
e uma exceção que sobem para `agent/api`, e um método novo no serviço de prompt — **~72 = região
medida, ~28 = código que muda**, zero mudança de comportamento:

```
ProviderResolution            (de ChatServiceImpl:176-186)
  resolve(tenantId) → { AiProvider provider, String model, String apiKey, String baseUrl }
  - tenantLlmConfigService.require(tenantId)      → TenantLlmConfigNotFoundException (422)
  - VendorCatalog.validate(provider, model)      → IllegalArgumentException → 400
  - credentialResolver.resolve(tenantId, provider) → CONFIG_MISSING (422)

TurnOutcomeMapper             (de ChatServiceImpl:203-246)
  map(turnContext, TurnReply reply) → void   [lança, ou retorna a decisão]
  - EMPTY_RESPONSE → recordTurn(empty_model_response) + ModelReturnedEmptyException (422)
  - FAILED         → recordTurn(FailureClassifier.causeOf) + evict de discovery
                     + ModelUnavailableException (422) | ProviderException (502)
  - ANSWERED       → recordTurn(null)
```

```
toProviderMessages  → ESTÁTICO em agent/api/util/ProviderMessages        (D5.3)
isModelNotFound + ModelNotFound → agent/api                              (D5.4, gateway TOCADO)
PromptTemplateService.findByCodeAndVersion                                 (D5.4, 1 método)
```

Efeito: `chat()` deixa de ler ambiente e passa a ter duas chamadas nomeadas no lugar da região
inline. **A porta, a trace, a classificação e as três classificações de erro ficam byte a byte
iguais** — a validação do batch é `git diff` das linhas load-bearing, mais a suíte, mais uma rodada
de E2E com `systemBlockChars` para provar que o prompt montado não mudou. ⚠️ `findActiveByCode` já
resolve `FOLLOW_ACTIVE`: **zero query nova**.

## 5. Riscos desta fase

| # | Risco | Prob. | Mitigação |
|---|---|---|---|
| R1 | O `refactor` de D4 mudar comportamento silenciosamente | **Média** | Batch **separado**, antes da feature. `git diff` das linhas load-bearing + suíte + E2E com `systemBlockChars` (precedente: `batch1_costuras_rag.metricas.baseline==refactor==correcoes: True`) |
| R2 | `sessionId = null` chegar ao provider como header `"null"` em GO/ZEN | **Média** | **Gate de verificação** no PLAN: ler a implementação de `createClientWithSession` antes de escrever a rota. Se não tolerar `null`, `TurnRequest` recebe um `sessionId` sintético (UUID por request) |
| R3 | Cota em Redis virar dependência dura do chat | Baixa | `fail-open` + log `error` (Q7) + o limiter em memória como piso degradado no caminho público |
| R4 | `client_credentials` usado num cliente não-confidencial | **Baixa** | O ERP é servidor com segredo em CI — é o caso de uso canônico. Registrar a restrição no doc do endpoint |
| R5 | A allowlist virar lista de 1 prompt e o ERP precisar de 50 | Baixa | Provisionamento por linha (`public_api_client_prompt`), sem migration — decisão 10 |
| R6 | `hasPermission` de `PUBLIC_CHAT_COMPLETE` conviver com as 6 literais sem virar a 4ª lista | **Média** | A concessão pública sai de **linha de banco**. As 6 literais continuam hardcoded até o RBAC de usuário ser pago (dívita) — e a nova permissão **não** entra em `SecurityConstants.DEFAULT_PERMISSIONS`, que é exibição |
| R7 | O gate H3 (E2E via UI) não cobrir esta feature | **Alta** | É esperado: o consumidor é externo ao browser. Substituído pelo **gate de conexão com o ERP** (`VALIDATION.md §4`), que é mais forte para esta feature |

---

**Próxima fase:** `ARCHITECTURE.md` — modelo de dados, contrato do token, contrato HTTP, os 4 erros.
