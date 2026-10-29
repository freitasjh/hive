# ADR-014 — API pública de chat: superfície HTTP stateless no formato OpenAI para integração interna

- **Status:** Aceito (2026-09-27) — **revisado em 2026-09-27 por 3 pareceres** (`security-engineer`,
  `product-manager`, `architect`: APROVADO COM CONDIÇÕES). **P1 (tripwire) e P2 (módulo novo)
  RATIFICADAS pelo usuário**, junto com as decisões D1–D8 da revisão — ver §"Revisão de 2026-09-27".
  As 10 decisões numeradas foram fechadas pelo usuário.
- **Data:** 2026-09-27
- **Contexto:** Hive AI (Java 21 + Spring Boot 4.1.0 + LangChain4j 1.19.0 + PostgreSQL 16 + Redis) ·
  produto é o **Assistant** (ADR-013): chat simples, prompt escolhido por conversa, sem agente, sem
  tools, sem base de conhecimento, não-agentico
- **Decisores:** usuário (escopo, premissas de produto, 10 decisões numeradas + D1–D8 da revisão)
  + product-manager (BYOK descartado, sem billing, sem metering) + architect (desenho, decisões
  técnicas) · **security-engineer consultado em 2026-09-27** (parecer APROVADO COM CONDIÇÕES, 10
  blockers endereçados nas decisões D1–D8) · **ux-designer NÃO exigido** — superfície headless,
  decisão D6
- **Supersede:** nada. É **aditivo** — nenhuma decisão anterior é revertida
- **Refina:** [ADR-012](ADR-012-Chat-Turn-Gateway-vs-AI-Services.md) — confirma que o **gatilho 5 não
  dispara** e registra a API pública como a **primeira implementadora da porta que não é agente**.
  Complementa [ADR-013](ADR-013-Assistant-Sem-Rag-Sem-Agente.md), cujo §"O que este ADR não decide"
  nomeava esta feature como pendente: *"APIs públicas — modelo de autenticação e cota, indefinido.
  É a próxima decisão de produto."* Este ADR é essa decisão.

---

## Contexto

O ERP e o sistema B2B da própria organização precisam conversar com o Assistant usando prompts que
**eles mesmos criam na plataforma**, e precisam chamar isso por HTTP, do jeito que um sistema externo
chama.

O produto interno tem hoje uma superfície que não serve: `/hive/v1/chat` exige um `User` autenticado
(`ChatControllerV1:66-70` lança `AccessDeniedException` se o principal não for `User`), exige
`@HasPermission("CHAT_SEND")` de um espaço de nomes de permissões de **usuário**, e **cria
`chat_session`** quando não recebe `sessionId` (`ChatServiceImpl:143-151`). Uma integração máquina-a-máquina
não tem usuário, não tem sessão, e não deveria ter: o histórico da conversa é do consumidor, e
recriá-lo no servidor a cada chamada seria storing duas vezes a mesma verdade.

O `product-manager` validou três premissas que **removem** a maior parte do escopo que uma API
pública normalmente carrega: o ERP é da mesma organização, os prompts são do cliente (não nossos, o
que elimina risco de extração de prompt), e **BYOK foi descartado** — o ERP não manda chave, usa
`tenant_llm_config`, que já existe e já funciona. Não há billing, não há metering, não há plano de
preço. **Não há produto vendido.**

Duas perguntas de discovery mudaram o desenho antes de qualquer código, e ambas estão registradas
neste ADR porque o erro nelas é o tipo que se repete:

1. O que o ERP precisa **além** de `chat/completions`? Resposta: um endereço de token, um `expires_in`,
   um identificador de turno para correlacionar log, a **versão efetiva do prompt** no corpo da
   resposta, e códigos de erro que ele consiga ramificar. Tudo isso é barato de adicionar agora e
   caro de adicionar depois — **versionamento de contrato é por path, não por header** (D14), e
   `/hive/v1/public/v1/chat/completions` carrega `/v1` duas vezes **de propósito**: o SDK OpenAI monta
   `{base_url}/chat/completions` e espera o `/v1` no `base_url`. Sem ele, quebra-se a compatibilidade
   com o cliente, não se resolve.
2. A ordem em que as checagens acontecem. Ver "Consequência 2" — é a ordem, não a allowlist, que mata
   a enumeração.

---

## A premissa que governa o resto

> **Primeira parte, não produto vendido.** É MVP para testes de integração. O ERP é da mesma
> organização.

Tudo abaixo decorre disso, e é útil declarar porque é o que impede o próximo de "endurecer" algo sem
motivo:

| Consequência | Por quê |
|---|---|
| Sem billing, sem metering, sem plano | Nada é cobrado, nada precisa ser contado para cobrar |
| Sem auto-serviço de provisionamento | O admin opera; o consumidor é uma integração interna |
| Sem SLA contratual, sem versionamento de política | Sem contrato com terceiro, sem suporte a depreciação |
| Sem catálogo de prompts, sem discovery | Os códigos válidos chegam no ato do provisionamento |
| Segredo do cliente **não** é responsabilidade nossa | BYOK descartado: não guardamos chave de terceiro, não ciframos chave de terceiro, e um item inteiro de compliance desaparece |
| Cota é para **proteger o provider**, não para cobrar | Ninguém paga por chamada |

O risco que resta sem billing é o de **custo e disponibilidade**: uma integração com bug em loop pode
consumir o orçamento do provider ou derrubar a taxa de erro. É exatamente isso que a cota (decisão 8) e
o `MAX_TOKENS` do prompt tratam. **Cota por integração não é metering** — é proteção de recurso
compartilhado, e a diferença importa para a conversa com o cliente.

---

## Decisão

1. **`POST /hive/v1/public/auth/token`** — `clientId` + `clientSecret` → access token curto, OAuth 2.0
   `client_credentials`. O segredo longo aparece **só** nesse endpoint. `grant_type` é obrigatório e
   validado; `scope` é ignorado e intersectado com as concessões da credencial. Sem refresh token — RFC
   6749 §4.4.3 não emite em `client_credentials`. TTL 10 min, por credencial.
2. **`POST /hive/v1/public/v1/chat/completions`** — Bearer, formato OpenAI, **stateless**. **Não cria
   `chat_session`.** O histórico é do consumidor, reenviado a cada chamada.
3. **Request** `{ model, prompt_code, messages[], user? }`. **Resposta** no formato OpenAI **mais**
   `prompt_code` no topo **mais** bloco `hive:{}` em `metadata` (turn id, versão efetiva, fingerprint
   do texto, pin mode, provider, modelo, latência, tenant).
4. **Allowlist de `prompt_code` por credencial.** Sem endpoint de busca. A resposta **não** lista
   códigos — seria discovery, e discovery foi recusado.
5. **BYOK descartado.** O ERP não manda chave. Provider e modelo são o default do tenant
   (`tenant_llm_config`, ADR-013 §Decisão 3). Remove: armazenamento de segredo de terceiro, cifrar e
   decifrar BYOK, e um item inteiro de compliance.
6. **Só `ACTIVE` é chamável.** `DRAFT` e `ARCHIVED` → `prompt_not_active`, erro distinguível, não 400
   genérico. A unicidade de um ACTIVE por `(tenant_id, code)` **já é do banco**
   (`uk_prompt_template_active_version`, índice único parcial em `WHERE status = 'ACTIVE'`), não é
   regra de aplicação.
7. **Um `clientId` por integração**, com label humano ("produto X / produção"). Revogação cirúrgica:
   derrubar um produto não derruba os outros.
8. **Cota é dado da credencial**, não constante de código. 10/min é número de humano numa tela, não de
   integração. Teto adicional por tenant.
9. **Sem SSE na v1.** `stream: true` devolve erro explícito, **não** silêncio — o ERP ficaria esperando
   tokens que nunca chegam.
10. **Códigos válidos chegam ao consumidor no ato do provisionamento**, sem discovery.

⚠️ **O segredo longo é `client_secret`, e ele é `SHA-256` no storage** — não `AesGcmAttributeConverter`.
Ver "Decisões que parecem detalhe".

---

## Premissas adotadas — ⚠️ NÃO ratificadas pelo usuário

As 10 decisões acima foram fechadas. As 2 premissas abaixo e as 4 respostas de discovery **não** foram:
foram adotadas com a premissa explicitada, e estão aqui porque cada uma muda o contrato ou o esforço.

### P1 — `PINNED` é **tripwire**, não snapshot ✅ **RATIFICADO pelo usuário (2026-09-27)**

A credencial declara a versão esperada do prompt. Se a versão não bater, vem
`prompt_version_not_found`. **`PINNED` detecta drift; não previne.**

> **A premissa original estava errada, e foi refutada.** A decisão foi apresentada (no briefing) como
> *"a credencial fixa a versão do prompt, para que resolver para latest não mude o comportamento do ERP
> silenciosamente a cada edição"*. O diagnóstico estava certo. O remédio **não entregava a garantia
> prometida**, e a medição está em `PromptTemplateServiceImpl:82-88`:
>
> ```
> 82:  promptTemplate.setVersion(existing.getVersion() + 1);
> 85:  mapper.updateEntity(existing, promptTemplate);   // ← in-place, MESMA linha
> 87:  existing.setVersion(promptTemplate.getVersion());
> ```
>
> `update()` **muta a linha existente** e incrementa o contador; `status` é preservado. Editar um
> template ACTIVE **não cria versão nova** — a linha deixa de estar na versão N e passa a estar na N+1.
> Não existe tabela de histórico. Consequência direta: **fixar a versão N e o cliente fazer um `PUT`
> pane a integração, e a versão N nunca mais existe** — o ERP **nunca** recebe o prompt antigo.
>
> Congelar de verdade exige tornar o template imutável-on-activate ("editar = criar rascunho novo +
> ativar"), o que **muda o editor de prompt existente**, que hoje faz `PUT` in-place. Isso é batch
> próprio, fora deste ADR.

O que o ADR decide é a **semântica honesta**: `PINNED` é uma **asserção de conformidade** — "esta
integração roda exatamente na versão N; se o prompt se mover, ela é avisada". Isso é útil, é
implementável hoje, e o custo é zero migration.

O lado positivo da mesma medição: como só `ACTIVE` é chamável (decisão 6) e há um ACTIVE único por
code, **`FOLLOW_ACTIVE` não é drift silencioso** — é troca visível. O que a torna visível é o eco da
versão na resposta, e por isso `prompt_version` e `prompt_fingerprint` (`SHA-256` do texto efetivo) são
obrigatórios em `metadata.hive{}`. Com eco, seguir o publicado é legítimo e mais simples que fixar.

**Se o usuário exigir snapshot de verdade, este ADR precisa ser reaberto** — não é um detalhe de
implementação, é uma mudança de schema e de comportamento do editor.

### P2 — módulo Maven novo `publicapi` ✅ **RATIFICADO pelo usuário (D5 da revisão)**

4 submódulos (`api`/`impl`/`dbupdate`/`web`) + 1 `pom` pai + 1 `@Configuration` Flyway, para **não**
criar a aresta `agent → security`. Motivo: `agent` e `security` são **brothers** hoje — `agent/api`
depende de `commons-api` e `provider-api`, `security/api` de `commons-api`, e nenhum dos dois do
outro. Hospedar a credencial em `security` e o controller de chat em `agent/web` funciona e é mais
barato em contagem de arquivos, ao custo de uma aresta nova entre brothers. A recomendação é o módulo
novo; a decisão é do usuário, porque é uma escolha de **esforço**, não de arquitetura.

### As 4 respostas de discovery, com premissa adotada

| # | Pergunta | Premissa adotada |
|---|---|---|
| 1 | `role: "system"` no `messages[]` é aceito? | **REJEITADO com 400 `system_role_not_allowed`** (D3 da revisão, 3/3 pareceres + usuário). `buildMessages:121-124` põe o template do cliente **primeiro**; aceitar-e-sobrescrever deixaria o system do ERP chegar por último. Por ser rejeição, **não há override a declarar** — a IT falsificável afirma a **ausência** de efeito (sem `metadata.hive`, sem turno registrado) |
| 2 | O transcript é persistido? | **Não.** Só metadados em **log** (D4): `ChatTrace` ganha `tenantId` + `turnId` + `clientId` no escopo do **Batch 1B** (~15 linhas, zero migration). Premissa: log do ambiente **coletado e pesquisável** por `clientId`/`turnId` depois de 1 dia — confirmada pelo usuário. Reversão: vira **tabela** (+1 migration, retenção, LGPD) |
| 3 | Limite de mensagens por requisição? | **Dado da credencial** (D2), não constante de código: `max_messages` 200 / `max_message_chars` 32000 / `max_total_chars` 256000, reclamados no claim `plim`. **Recusa explícita, nunca truncamento:** 400 `too_many_messages`/`request_too_large`; 413 só para bytes, decidido no filtro por `Content-Length` antes do Jackson (teto 1 MiB — JSON escapa non-ASCII como `\uXXXX`, 6 bytes/char) |
| 4 | `client_secret` no header ou no body? | **`client_secret_basic`** no header `Authorization` (RFC 6749 §2.3.1 — evita que access log registre o body) |

### Onde o architect discorda

Registradas aqui e não só no retorno, porque as duas mudam o contrato e o próximo implementador
precisa saber que elas foram contestadas.

**(RESOLVIDO — D2) "Sem limite de mensagens; o teto é do proxy" — o architect discordou, e a revisão
fechou com a forma dele:** teto no servidor, **dado da credencial** (não constante), recusa explícita
(400/413), nunca truncamento silencioso. Registro do argumento original, que segue valendo:**

O ERP chama o Hive **direto**. Não há proxy no caminho descrito, e um teto que não existe no caminho
real não é um teto. Além disso, três consequências que o argumento "o proxy cuida" não cobre:

- **Custo de provider.** `LlmRetryPolicy` **retenta**. Um payload sem teto, retido, é o pior
  combinação de custo e erro do desenho. E `max_tokens` do prompt é do **cliente** (é o prompt dele), então o
  teto de geração não é nosso.
- **A superfície é externa e o chamador é uma máquina.** Um bug no ERP que manda o histórico em loop
  não tem judgement para se corrigir. O cap interno do chat (`findLast20`, 4000 chars) existe porque
  um humano erra; contra uma máquina o erro assume a forma de laço.
- **Janela de contexto do modelo.** Acima de um certo tamanho o provider recusa, e a recusa chega como
  erro do provider, muito depois de onde a falha foi cometida — a mesma armadilha que o ADR-013
  registra para o `max_tokens` ausente de `chat_session`.

**Meio caminho proposto, se a decisão for manter o MVP sem teto:** teto **generoso** no servidor
(por exemplo 200 mensagens, 32 KB por `content`) com erro explícito `413`/`400 too_many_messages` em
vez de truncamento silencioso, e teto fino onde o proxy existir. Truncar em silêncio é pior que
recusar: o ERP recebe 200 OK com metade da conversa e não sabe.

**(RESOLVIDO — D4) "Metadados persistidos" — a formulação era ambígua, e a diferença importava.**

"O transcript não é persistido, só metadados" é compatível com `ChatTrace`, que **já** registra
`prompt_template_id`, versão efetiva, tokens, latência e status — **e não texto**. Fechado: os
metadados vão para a **linha de log** que `LoggingChatTraceRecorder` já escreve, sem tabela nova e sem
escrita no hot path — **com 3 condições** (a 3ª foi acrescentada na revisão): (1) `ChatTrace` ganha
`tenantId` + `turnId` + `clientId` (Batch 1B); (2) o `turn_id` que o ERP recebe casa com a linha de
log; (3) o log do ambiente é **coletado e pesquisável** após 1 dia — confirmado pelo usuário em
2026-09-27. Se qualquer condição cair, vira **tabela** (+1 migration, retenção, LGPD).

---

## Revisão de 2026-09-27 — 3 pareceres, 8 decisões fechadas

`security-engineer`, `product-manager` e `architect` revisaram este ADR e os 6 artefatos de
planejamento (2.708 linhas, **zero código**). Veredito unânime: **APROVADO COM CONDIÇÕES** — 10
blockers (B1–B10), 6 contradições documentais, 16 omissões que quebravam o desenho. As correções
viraram 8 decisões fechadas (**D1–D8**), mais a ratificação das premissas P1/P2 e o re-measure do
baseline (Batch 0). Registro completo: `workflow-state.json → public_api_chat_review` e
`.spec/backend/public-api/VALIDATION.md §6`.

| # | Decisão | Quem fechou | Efeito |
|---|---|---|---|
| D1 | Default de `pin_mode` = **`FOLLOW_ACTIVE`**; `PINNED` só onde o contrato exigir | product-manager + security-engineer, **arbitrado pelo usuário** | A divergência (PM queria `PINNED`) foi desfeita pela própria condição do PM: *"1 integração, prompt do mesmo time que faz o deploy"* → `FOLLOW_ACTIVE`. Provisionamento **sempre informa** `pin_mode` explicitamente; o `DEFAULT` do DDL só atende insert fora do runner |
| D2 | Teto de mensagens = **dado da credencial** (`plim` no claim) | product-manager + architect | 400 `too_many_messages`/`request_too_large`, **nunca** truncamento; 413 só para bytes no filtro (1 MiB) |
| D3 | `role: "system"` → **400** | 3/3 + usuário | IT falsificável (ausência de `metadata.hive` + turno não registrado) |
| D4 | Metadados em **log**; `ChatTrace` ganha `tenantId`/`turnId`/`clientId` no **Batch 1B** | product-manager | 3 condições; reversão para tabela se o log do ambiente não for pesquisável |
| D5 | Política do turno: porta e função pura em `agent/api`, adaptador em `agent/impl`; `isModelNotFound` sobe; `LangChain4jTurnGateway` **é tocado** | architect | P2 ratificada; zero aresta `impl → impl`; zero query nova |
| D6 | Provisionamento por **`CommandLineRunner`** (tela cortada) | product-manager | `ux-designer` **não** exigido; gatilho para voltar nomeado |
| D7 | `FilterRegistrationBean.setEnabled(false)`; `SecurityFilter.java` com 0 linhas alteradas | architect + security-engineer | `shouldNotFilter` rejeitado; IT de sentinel + teste estático |
| D8 | Exceções de segurança **registradas**; 4 correções baratas em código | security-engineer | Chave separada (`api.security.public.token.secret`), fail-fast de perfil, springdoc em prod, `typ`/`kid` |

**Esforço reestimado pelo architect após as correções: 52–66 h** (o PLAN dizia 32–44 h). Entram:
correções documentais, `ModelNotFound` movido, `FilterRegistrationBean` + 3 ITs de cadeia, IT de
cota-vs-allowlist + claim. Sai: `TurnContext` (não existe).

**Divergência que segue sem arbitragem: SEC-02** — o `security-engineer` propõe o fix do 401 na
cadeia humana no `SecurityFilter` (arquivo que a Conformidade promete não tocar); o `architect` não
se pronunciou. Fica registrada, não decidida.

---

## Consequência 1 — O gate de segurança P-d, e por que a correção resolve dois problemas

Este é o achado mais importante do planejamento, e ele **não estava no briefing**.

`SecurityFilter.java:28` é `@Component` — **auto-registrado no container para `/*`, fora do
`FilterChainProxy`** (B1). E um token `aud=hive.public` falha **antes** de qualquer lookup de usuário:
`SecurityFilter:49` chama `validateTokenRevoke` → `decodeAndVerify:167-171` exige `aud=hive.web` →
`BaseException` relançada em `:63` → **HTTP 500, não 401**. Nenhum controller é alcançado, e o
sintoma mente: diz "erro interno" onde o integrador diria "credencial inválida".

**A causa original foi apontada errada no planejamento e corrigida na revisão (B2):**
`loadUserByUsername:51` é **inalcançável** nesse caminho — a validação de audiência morre antes. Ele
segue sendo um defeito latente (um token `aud=hive.web` forjado com `sub` de não-usuário chegaria lá),
mas **não é a causa do 500 da rota pública**. registrar a causa errada é o que faz o próximo corrigir
o lugar errado.

E a correção **resolve dois problemas que não são o mesmo**:

1. **Tecnicamente**, é a razão de a cadeia ser **separada** e não um ramo por claim — e a correção
   é **D7**: `FilterRegistrationBean<SecurityFilter>` com `setEnabled(false)` em `SecurityConfig`,
   mais nada. **`SecurityFilter.java` fica com zero linhas alteradas**; o duplo registro
   (`@Component` + bean do container) é desfeito por 1 `@Bean`. `shouldNotFilter` foi rejeitado:
   criaria um 2º lugar com a lista de paths, divergindo em silêncio do `SecurityConfig`.
2. **Em segurança**, a cadeia separada é o que **fecha o `AGENT_MANAGE` por ausência de rota** —
   ver Consequência 2.

> **E isso é o ponto que merece ser registrado.** O `product-manager`*pôs uma restrição explícita:
> **não pagar a matriz de papéis de usuário (RBAC completo) para destravar isto.** A restrição foi
> honrada — e por um caminho que ele não podia prever. Nenhum papel de usuário foi criado, nenhuma
> matriz foi paga, e `.spec/tech-debts/security-rbac/TASKS.md` P1-1 continua aberto. A superfície
> pública é fechada porque **o filter chain dela não contém rota interna alguma**, e não porque uma
> lista de permissões foi enxuta. Uma lista é um invólucro que o próximo endpoint esquece de envolver;
> ausência de rota não depende de disciplina.

Complemento barato e frequentemente esquecido: **`aud` distinto** (`hive.public` vs `hive.web`, hoje em
`SecurityTokenServiceImpl:45`). Mesmo segredo HMAC, mesmo `iss` — e ainda assim um token do painel web
não é aceito na API pública, e vice-versa.

---

## Consequência 2 — A allowlist **não é um controle de acesso**. É uma lista.

> Isto está escrito para que o próximo leitor não confunda as duas coisas, porque a confusão é
> exatamente o que reabriria o furo.

A allowlist controla **qual prompt** o consumidor pode chamar. Ela **não** controla **quais endpoints**
ele pode alcançar. São coisas diferentes, e a segunda não está resolvida pela primeira.

O fato medido: `SecurityTokenServiceImpl:182` concede **6 permissões a qualquer usuário que se
autentique** —

```
182:  List<String> permissions = List.of("PROMPT_MANAGE", "PROMPT_ADMIN", "AGENT_MANAGE",
                                         "AGENT_ADMIN", "CHAT_SEND", "CHAT_SESSION_MANAGE");
```

— e são as mesmas 6 de `SecurityConstants.DEFAULT_PERMISSIONS`, que é **só dado de exibição**, e as
mesmas 6 de `RoleControllerV1`, que também é só exibição. **Não existe modelo de dados de papéis**:
nenhuma entidade, nenhuma tabela, nenhum repositório ligado a role/permission/grant.

Um token com essas 6 permissões alcança `AGENT_MANAGE`, que em `/v1/provider-credential` e
`/v1/tenant/llm-config` significa **trocar a chave de API do provider e o modelo default do tenant**.
Ou seja: uma credencial de integração poderia reconfigurar a qual provider o tenant fala, e com que
modelo — a partir de um endpoint cujo nome é "chat".

**Quem fecha isso é a cadeia separada** (`/v1/public/**` casa numa `SecurityFilterChain` própria, e a
cadeia humana declara `.requestMatchers("/v1/public/**").denyAll()` explicitamente). Não existe
`/v1/provider-credential` sob a cadeia pública, então não há o que alcançar. **A allowlist não
participa dessa defesa.**

A allowlist resolve um problema **diferente** e real: enumeração cross-tenant. Ela é verificada
**antes de qualquer leitura de `prompt_template`**, e um `prompt_code` fora da allowlist devolve o
**mesmo** código de erro exista ou não no tenant. Inverter essa ordem transforma 403-vs-404 em oráculo
de existência **dentro** do tenant. Os passos 7 e 8 do gate de conexão (`VALIDATION.md §4.2`) existem
para provar exatamente isso, e a IT correspondente é a que **não pode ser cortada**.

---

## Consequência 3 — Reuso, não fork — e o que isso custou

`ChatServiceImpl` tem 345 linhas e 9 dependências de construtor. Decomposto por responsabilidade:

| Trecho | Responsabilidade | Reusável num fluxo stateless? |
|---|---|---|
| `:123`, `:127` | Lê `TenantContext` e `SecurityUtils.currentUserId()` do **contexto ambiente** | ❌ é a trava — um chamador não-humano não tem `userId` |
| `:143-168` | Cria ou carrega `chat_session` | ❌ stateless |
| `:170-174` | `resolvePromptTemplate(sessionId, …)` | ❌ scopado por sessão |
| **`:176-186`** | `tenantLlmConfigService.require` → `VendorCatalog.validate` → `credentialResolver.resolve` | ✅ **decisão sem sessão** |
| `:188-194` | TX1: persiste a mensagem do user | ❌ persiste |
| `:200-201` | `turnGateway.execute(new TurnRequest(...))` | ✅ **é a porta** |
| **`:203-246`** | Os 3 ramos de status + `FailureClassifier` + evict de discovery + 422/502 | ✅ **decisão sem sessão** |
| `:248-252` | trace + TX2 | ❌ persiste |
| `:328-344` | `toProviderMessages` — a conversão para o tipo do provider | ✅ **é a mesma conversão, com outra fonte** |

**Nenhuma linha é reusada como está, e nenhuma precisa ser.** O que sai de lá é **decisão que mora
no lugar errado porque o método também faz transação**, e o recorte final (D5 da revisão) é:

| # | Collaborator | Forma final |
|---|---|---|
| 1 | `ProviderResolution` | `agent/impl`, 1 método. (`ProviderResolution` **não** sobe para `agent/api`: `CredentialResolver` está em `agent/impl` e `agent/api → agent/impl` é ciclo Maven) |
| 2 | `TurnOutcomeMapper` | `agent/impl`, 1 método |
| 3 | `toProviderMessages` | **estático em `agent/api/util/ProviderMessages`** — a porta e a função pura vivem no `api`; o adaptador, no `impl` |
| 4 | `isModelNotFound` + `ModelNotFound` | **movem para `agent/api`** — `LangChain4jTurnGateway` **é tocado** (emenda ratificada pelo usuário), 1 checkbox de Conformidade |

`~72` é a região **medida**; `~28` é o código que muda. A estimativa "~110 linhas" do planejamento
confundia região com diff, e foi essa confusão que gerou a divergência de esforço entre os 3
pareceres (32–44 h vs 46–58 h).

E `LangChain4jTurnGateway` (179 linhas) **já decide cinco coisas** que a porta existe para centralizar:
a precedência do `SystemMessage` sobre a conversa, a regra de "modelo removido" (404 com corpo JSON
citando o modelo **é** modelo removido; 404 com corpo HTML **é** `baseUrl` sem `/v1`, e não pode virar
422 enganoso), a política de retry, a guarda de resposta vazia, e o `apiKey` mascarado no `toString()`.
Uma segunda implementação as **reimplementa as cinco** — e `MODEL_UNAVAILABLE` passaria a divergir
entre os dois caminhos, que é a concordância que a porta compra.

---

## Por que o gatilho 5 do ADR-012 **NÃO** dispara

O gatilho 5 do [ADR-012](ADR-012-Chat-Turn-Gateway-vs-AI-Services.md), textual: *"`ChatTurnGateway`
ganhar uma **segunda implementação real** (não um mock de teste). A segunda implementação é o que
valida a porta; um mock não valida."*

**A API pública não é uma segunda implementação. Ela é a mesma implementação, servindo um segundo
consumidor.** Três razões, e a última é a que resolve:

1. `LangChain4jTurnGateway` **não tem** `ToolProvider`, **não tem** loop de tool-calling, e **não tem**
   `AiServices`. A porta não ganhou nenhuma forma nova.
2. A API pública não é um agente. É o mesmo turno, sem `chat_session`, com o histórico vindo do request
   em vez do banco.
3. Uma classe nova com comportamento idêntico **não é** "segunda implementação" no sentido do
   gatilho — é a mesma implementação com outro nome, e ela reimplementaria as cinco decisões acima.

> **Registrado explicitamente porque é o tipo de conclusão que se re-litiga.** O ADR-012 tem 5 gatilhos
> e este é o que a leitura apressada marcaria como disparado. Os outros 4 também não: não há tool
> (1), o split TX1/TX2 permanece (2), o histórico é nosso e limitado (3), a versão continua 1.19.0
> (4).

O que **valida** a porta, e é o que esta API entrega, é melhor do que a segunda implementação para o
objetivo do ADR-012 (que era concentrar a decisão): um **segundo consumidor real, com o mesmo
diagnóstico**. E é também por isso que o **cliente agente**, que é o próximo passo declarado, sai
barato: ele é o terceiro consumidor da mesma porta, e não vai precisar de segunda implementação.

**A regra que generaliza, e que serve ao cliente agente:** *reusar o algoritmo e a decisão;
paralelizar a superfície de política.* São 4 implementações paralelas legítimas nesta feature —
`SecurityFilter`, envelope de erro, emissor de token e armazenamento de cota — porque são **política**,
e política diverge legitimamente. Zero paralelas em `ChatTurnGateway`, porque ali são **decisão**.

---

## Decisões técnicas que parecem detalhe e não são

| Decisão | Por que não é detalhe |
|---|---|
| **`client_secret` com SHA-256, não `AesGcmAttributeConverter`** | Segredo precisa ser **verificável**, não recuperável. Cifrar cria duas metades do mesmo segredo no mesmo processo e no mesmo banco, e acopla o ciclo de vida de um segredo rotacionado **por integração** ao de uma master key que **não** é rotacionada por integração. Trocar a master key inutilizaria também os clientes dos clientes. Precedente copiado: `SecurityTokenServiceImpl:195-208` — a **primitiva** (SHA-256 de segredo de alta entropia), **não** a escolha de devolver o hash ao cliente, que ali é válida porque o refresh é curto e rotaciona a cada uso |
| **Flyway do módulo novo sem `repair()`** | `repair()` realinha checksum de migrations **já aplicadas** e **não executa SQL**. Medido com Flyway 10.10.0: série nova colidindo em número de versão → `migrationsExecuted = 0`, `Successfully validated N migrations`, **boot verde com o schema antigo**. É o que transforma erro barulhento em mentira silenciosa. O módulo novo tem histórico que **nasce vazio** — não há checksum para realinhar. A ausência de `repair()` é deliberada e é a **oposta** dos outros três módulos: faz divergência futura morrer **alto e verboso**, que é o comportamento correto para um histórico que cabe na cabeça de uma pessoa |
| **Cota nasce em Redis** | Redis já é **produção**, não item de stack: `RedisConfig` declara `RedisTemplate` e `StringRedisTemplate`, e `TokenCacheServiceImpl` já usa `hive:auth:refresh:{tenantId}:{token}` e `hive:auth:blacklist:{tenantId}:{jti}` com TTL. `ChatRateLimiter` é `ConcurrentHashMap<UUID, Deque<Instant>>` **em memória, por JVM, sem eviction** — sob N instâncias o limite efetivo é `10N/min`. O sintoma de que já é hora: a cota deixou de ser número de humano e passou a ser **dado da credencial**. Contar por nó nessa altura é **bug de correção**, não imprecisão. Algoritmo: sliding-window log em `SORTED SET` com **um** `EVAL` de Lua, membro com **sufixo aleatório** (senão dois requests no mesmo microssegundo geram membro idêntico e o Redis trata duplicata como **update** → subcontagem). O `ChatRateLimiter` em memória **não é tocado** — continua servindo `/v1/chat` |
| **`client_id` globalmente único**, não por tenant | O endpoint de token roda **antes** de existir `TenantContext` — `TenantAspect` engole `TenantNotFountException` e **segue com o `@Filter` do Hibernate desligado**. Único-por-tenant seria ambíguo, e o tenant viria do request, que é a direção de confiança errada. `clientId` é o identificador; o tenant é dado do servidor, lido da linha |
| **`tenant-id` no token, e com esse nome** | `SecurityTokenServiceImpl:42` — `TENANT_CLAIM = "tenant-id"`, não `tenantId`. Divergir quebraria `getTenantId(token)` e `validateTokenRevoke`, e o claim é a **âncora do `@Filter`** |
| **Envelope de erro local ao controller público** | `ControllerExceptionHandler` é `@RestControllerAdvice` sem qualifier e **já tem** `@ExceptionHandler(Exception.class)`; ele deriva `code` do **nome da classe** em SNAKE_UPPER e responde `application/problem+json`. O contrato público pede `snake_lower` no formato OpenAI. Duas peças capturando `Exception` com `@Order` é precedência de handler — sutil, silenciosa quando erra, e o sintoma é um envelope errado em produção. Handler local é explícito |
| **`model` é asserção, não seletor** | O SDK OpenAI exige o campo e proxies compatíveis o ignoram. Ausente → default do tenant; presente e **diferente** → `400 unknown_model`. Transforma um campo obrigatório e inútil em **sanidade check de configuração**: se o ERP aponta para o Hive errado, descobre no primeiro request |

---

## Sequenciamento inegociável

Cada item existe porque a ordem invertida produz um bug específico, e nenhum deles é negociável por
conveniência de sprint.

| Ordem | Batch | Bug evitado pela ordem |
|---|---|---|
| **1º** | Extração de `ProviderResolution` + `TurnOutcomeMapper` + `toProviderMessages` compartilhado | **Fork.** Escrita contra o `ChatServiceImpl` monolítico, a API pública **força** a cópia — e a extração depois vira refactor em cima de código já duplicado, que é a forma mais cara de fazer as duas coisas. ⚠️ E em **batch próprio**, com gate de `systemBlockChars` igual ao baseline: se o refactor e a API forem o mesmo batch, a evidência que sustenta `ChatServiceImpl` passa a ser só mock, e é assim que o `PromptSelector` com `size=100` contra servidor que corta em 50 passou por 145 testes e quebrou 400 em toda montagem |
| **2º** | P-a (grant model persistido) + P-b (`tenantId` no token) + P-c (ciclo de vida do segredo) | **O primeiro token emitido é um superusuário.** Não existe "só um endpoint de teste": o primeiro token já é o breach. Sem P-a não há registro do que revogar, auditar ou testar 403; sem P-b não há âncora de tenant e a leitura é cross-tenant; sem P-c o segredo vive em claro |
| **3º** | `SecurityFilterChain` separada, **antes** de qualquer endpoint público | **O token público alcança `/v1/provider-credential`** e troca a chave de API e o modelo default do tenant. Ver Consequência 2 |
| **4º** | **Batch 1B** — `ChatTrace` ganha `tenantId` + `turnId` + `clientId` (D4) | Trace da rota pública **não atribuível**: o `turn_id` que o ERP recebe não casa com linha de log nenhuma. ~15 linhas, zero migration, nenhum teste existente quebrado |
| **5º** | Os dois endpoints | — |

A regra geral, que vale para o próximo: **nenhuma emissão de credencial antes do seu modelo de
concessão, e nenhuma rota antes da fronteira que a contém.**

---

## Alternativas consideradas

| Alternativa | Decisão | Motivo |
|---|---|---|
| **Dentro do `/v1` existente, reaproveitando `ChatControllerV1`** | Rejeitada | `ChatControllerV1:66-70` lança `AccessDeniedException` se o principal não for `User`, e um token de máquina não é. Além disso `CHAT_SEND` e `AGENT_MANAGE` dividem o mesmo espaço de nomes, o contrato diverge (`ChatResponseDTO` vs `chat.completion`), e `ChatServiceImpl:143-151` **cria sessão** quando não recebe `sessionId` — "stateless" e "reaproveitar o controller" são incompatíveis por construção. O que se aproveitou foi a **moldura**: `@HasPermission` + `HasPermissionAspect` + a porta |
| **ERP chamando `ChatTurnGateway` direto, sem HTTP** | Rejeitada | Library compartilhada e versão acoplada entre ERP e Hive; sem fronteira de tenant (`@Filter` depende de `TenantContext` + `TenantAspect`, que é servlet-scoped), sem fronteira de rede, sem token, sem cota, sem log próprio — e desmontaria tudo que P-a/P-b/P-c entregam. É também a **segunda capacidade desenhada antes do primeiro loop**, que é a lição do ADR-013 |
| **Credencial em `security` (novo `dbupdate`) + controller em `agent/web`** | Alternativa à P2 | Mais barato em contagem de arquivos; custa a aresta `agent → security` entre brothers. Recomendação do architect é o módulo novo; **decisão do usuário** (P2) |
| **Credencial em `identity`** (já tem `dbupdate`) | Rejeitada | `identity` é dono da identidade **humana**; uma credencial de máquina é outro conceito |
| **Credencial em `agent`** (já tem `dbupdate`) | Rejeitada | Mistura a fronteira de segurança com a fronteira de conversa |
| **BYOK — o ERP manda a chave do provider** | **Rejeitada pelo product-manager** | O ERP é da mesma organização; guardar a chave dele é passivo sem contrapartida. Remove armazenamento de segredo de terceiro, cifrar/decifrar, e um item de compliance |
| **Segunda implementação de `ChatTurnGateway` para a API pública** | Rejeitada | Ver "Por que o gatilho 5 do ADR-012 não dispara". Reimplementaria as cinco decisões que a porta existe para centralizar |
| **Snapshot real do prompt (tabela de histórico)** | **Adiada, e P1 depende disso** | Exige tornar o template imutável-on-activate, o que muda o editor existente. Batch próprio |
| **Pagar a matriz de papéis de usuário (RBAC)** | **Rejeitada por restrição explícita do product-manager** | Honrada — e a lacuna real (endpoint, não permissão) foi fechada por separação de cadeia, que é mais forte. P1 de `.spec/tech-debts/security-rbac/` continua aberto e **não** é este ADR que paga |
| **Endpoint de busca de prompts** | Rejeitada | Seria discovery, e mataria a propriedade de que códigos válidos chegam no provisionamento |
| **SSE na v1** | Adiada | Fase 2. Na v1, `stream: true` é erro explícito |

---

## A lição

> **A premissa que pareceu óbvia estava errada, e nada na métrica do projeto seria acusá-la.**

A decisão de versionamento entrou no briefing com confiança e com justificativa técnica
(`PromptTemplateServiceImpl:82` incrementa `version` a cada update, então resolver para latest muda o
comportamento silenciosamente). O diagnóstico era exato. O **remédio** não entregava a garantia
prometida, e nenhuma métrica do projeto teria accusations: a suíte ficaria verde, os testes passariam,
e a integração quebraria **em produção, semanas depois, na primeira edição de prompt** — que é
exatamente quando ninguém está olhando.

O padrão, escrito para ser reaplicado:

> **Quando uma decisão de produto é apresentada como óbvia — e é o tipo de decisão que não aparece em
> métrica nenhuma — ela exige verificação empírica antes de virar ADR.** "Óbvio" é uma pista de que
> ninguém olhou o código, não de que está certo.

Isto é a **terceira** ocorrência da mesma família neste repositório, e as três são o mesmo erro de
forma:

| # | Caso | Forma | Como o defeito se manifesta |
|---|---|---|---|
| 1 | `max(version)` do Flyway | contador que **parece** número e era lido como texto | `repair()` realinha checksum, roda **zero** migrations e reporta **sucesso** — boot verde com schema antigo |
| 2 | `res.data as Page<T>` | tipo declarado que o servidor **não** garante (`totalElements` vem aninhado em `page`) | dado chega na forma errada, o cast compila, e o defeito só aparece no consumo — `frontend/src/services/session.service.ts:15` documenta a armadilha |
| 3 | `version` do prompt (este ADR) | contador que **parece** snapshot e é um mutador in-place | pin por versão **pane** a integração e nunca devolve o texto antigo |

A regra de detecção é a mesma nos três: **um valor que parece algo não é, e o consumidor é
incondicional.** `UNIQUE` que não protege NULL, `ORDER BY version DESC` que traz NULL primeiro
(`NULLS FIRST` no PostgreSQL), `totalElements` que não está onde o tipo diz, `version` que não é
imutável. Em todos, o banco, o servidor e o tipo **aceitam** o valor e **nenhum reclama**.

⚠️ **Corolário sobre a premissa 1 (o `system` do cliente):** a solução inicial — aceitar e
sobrescrever declarando em `metadata.hive{}` — **era** um override silencioso por construção. A
revisão fechou **rejeição** (400, D3): um override declarado ainda deixa o consumidor achando que o
system dele foi honrado, e a declaração vive onde ele não olha. Rejeitar é a única forma de não
mentir — e a prova é falsificável (a IT afirma a **ausência** de `metadata.hive` e de turno
registrado).

---

## O que este ADR não decide

- **Cliente agente.** É o próximo passo declarado, e o desenho **serve a ele** — ele é o terceiro
  consumidor da mesma porta, não uma segunda implementação. O que já nasce pronto: grant model por
  identidade, cota por identidade, envelope de erro parametrizável. O que **não** nasce: sessão
  persistente, tools, memória. Gatilho do ADR-013: primeira tool.
- **SSE / streaming** — fase 2.
- **Billing e metering** — não existe produto vendido.
- **Provisionamento auto-serviço** — o admin opera.
- **SDK** — o ERP usa o SDK OpenAI que já tem, porque o contrato é o formato OpenAI. É o ponto do
  desenho: **compatibilidade é o SDK de graça**.
- **Catálogo / marketplace de prompts.**
- **RBAC de usuário** — dívida pré-existente, explicitamente fora do escopo.
- **Snapshot real de prompt** — depende de mudar o editor; ver P1.
- **`/actuator/**` e `/v3/**` em `permitAll`** — o enquadramento original ("adjacente, não afetado")
  era **meio verdade** (correção do `security-engineer`): certo para `/actuator/**`, **falso para
  `/v3/**`** — `springdoc-openapi-starter-webmvc-scalar` é dependência **COMPILE** de
  `bootstrap/pom.xml:53-57`, então `/v3/api-docs` está no jar de prod e publica o schema de **todos**
  os endpoints internos, inclusive `POST /hive/v1/provider-credential`. Correção barata em código
  (D8): `springdoc.api-docs.enabled=false` em prod + `management.endpoints.web.exposure.include`
  explícito + IT.

---

## Gatilhos de reavaliação

1. **Existir a primeira tool** (gatilho 1 e 4 do ADR-012, reaberto por aqui) — a porta ganha um método a
   mais, e a superfície pública precisa de uma permissão nova.
2. **Um segundo cliente externo, de outra organização** — hoje as premissas são de **primeira parte**.
   Aí entra consortium question, e talvez multipartid.
3. **O ERP querer transcript persistido** — hoje é decisão 2 (stateless) e premissa 2 da discovery.
   Persistir reabre `chat_session`, reabre a cota por sessão, e reabre privacidade.
4. **O usuário exigir snapshot de prompt** (P1) — muda schema e muda o editor. Reabre este ADR.
5. **O usuário ratificar ou rejeitar P2** (módulo novo vs aresta `agent → security`) — muda esforço,
   não arquitetura.
6. **Surgir necessidade de usage para cobrança** — hoje sem metering porque não há produto vendido.
7. **Cota virar unidade de conta em vez de proteção** — aí o sliding-window em Redis já está no lugar;
   falta a parte de metering, que é decisão de produto, não de engenharia.

---

## Conformidade

- [ ] Nenhum endpoint público exposto **antes** de P-a, P-b e P-c verdes
- [ ] `SecurityFilter.java` **inalterado** (0 linhas); o duplo registro é corrigido por 1 `@Bean`
      (`FilterRegistrationBean.setEnabled(false)`) em `SecurityConfig` — D7
- [ ] `SecurityTokenServiceImpl` **inalterado** (`:182` continua hardcoded, e o P1 de
      `security-rbac` segue aberto por decisão)
- [ ] `LangChain4jTurnGateway` **intacto exceto `isModelNotFound`**, que muda para `agent/api`
      (emenda ratificada — D5). Sem a emenda, ou o gateway fica intacto e D3 vira mentira, ou a regra
      é copiada
- [ ] Nenhuma linha de `ChatServiceImpl` copiada; extração validada por `git diff` das linhas
      load-bearing + E2E com `systemBlockChars` igual ao baseline
- [ ] `ChatSessionEntity`, `ChatMessageEntity` e `PromptTemplateEntity` **inalterados** — a API
      pública não escreve nenhuma das três
- [ ] `provider_credential` continua sendo do módulo `agent` e **não** é tocado (ADR-013 §"`provider_credential` fica no módulo `agent`")
- [ ] IT de superfície: token público em `/v1/prompt-templates`, `/v1/provider-credential`,
      `/v1/chat` e `/v1/tenant/llm-config` → **401 (nunca 200, nunca 500)**, e token humano na API
      pública → **401**; mais IT de **caracterização** do 500 atual (documenta o defeito antes de
      corrigir) e IT de **sentinel** (corpo não-MVC que falha antes do handler)
- [ ] IT de anti-enumeração: `prompt_code` fora da allowlist devolve o **mesmo** código exista ou não
- [ ] IT de versionamento: `PINNED` numa versão + `PUT` no template → `409 prompt_version_not_found`
- [ ] IT de `system` **falsificável**: `role: "system"` → 400 **e** a resposta não tem
      `metadata.hive` **e** nenhum turno é registrado (a ausência é a prova)
- [ ] Teto: `max_messages`/`chars` da credencial no claim `plim`; acima → 400
      `too_many_messages`/`request_too_large`; corpo > 1 MiB → **413 no filtro** (antes do Jackson);
      `max_tokens` do provider com clamp; **nunca** truncamento silencioso
- [ ] Envelope: handler local com **catch-all** (`HttpMessageNotReadable`, `MethodArgumentNotValid`,
      405, `DataIntegrityViolation`) — **nunca** `application/problem+json` na rota pública
- [ ] `typ` no JWT; **chave separada** `api.security.public.token.secret` (HMAC compartilhado entre
      token humano e de máquina é correção barata, não exceção); `jti` na blacklist com escritor
- [ ] `client_secret` nunca em log, nunca em `toString()`, nunca em resposta de erro; comparação em
      tempo constante
- [ ] `Cache-Control: no-store` no token (RFC 6749 §5.1)
- [ ] `system` **rejeitado** com 400 (D3) — não há override a declarar
- [ ] Flyway do módulo novo **sem** `repair()`, e `mvn clean install -DskipTests` antes de subir
      qualquer migration
- [ ] `security-engineer` consultado em 2026-09-27 — **APROVADO COM CONDIÇÕES**, 10 blockers
      endereçados em D1–D8. **ux-designer não exigido** (D6: superfície headless; gatilho para voltar:
      2 credenciais de produção, ou provisionamento por alguém sem acesso ao ambiente)
- [ ] Exceções ao `security-standard.md` **registradas, não escondidas**: HMAC256 simétrico =
      exceção aceita (gatilho: 2º cliente externo); TLS = prova do edge, débito de ops; demais
      correções baratas em código (chave separada, fail-fast de perfil, springdoc em prod)

✅ **A dívida de parecer foi paga em 2026-09-27.** Os 3 pareceres (security-engineer, product-manager,
architect) são registrados em `workflow-state.json → public_api_chat_review`; os blockers B1–B10 e as
contradições documentais foram endereçados nas decisões D1–D8. **Uma divergência segue sem
arbitragem: SEC-02** (401 na cadeia humana — o `security-engineer` propõe o fix no `SecurityFilter`,
o PLAN 6.3 diz "não mexer em `SecurityTokenServiceImpl`"; o `architect` não se pronunciou).

---

## Referências

- **ADRs:** [ADR-012](ADR-012-Chat-Turn-Gateway-vs-AI-Services.md) (porta do turno; gatilho 5
  **não disparado** — ver seção própria) · [ADR-013](ADR-013-Assistant-Sem-Rag-Sem-Agente.md) (§Regra 1:
  exercitar a primeira capacidade em ambiente real antes da segunda; §"O que este ADR não decide":
  nomeia esta feature como pendente) · [ADR-011](ADR-011-Opencode-Go-Zen.md) (`x-opencode-session` =
  UUID da `chat_session` — relevante para o gate R2 do plano: `sessionId` nulo nesse caminho precisa
  de verificação antes) · [ADR-010](ADR-010-KB-RAG-pgvector-manual.md) e
  [ADR-009](ADR_009_LangChain4j_Vendor_Provider.md) (supercedido; referido aqui só pela armadilha do
  `repair()` e pelo `Async` com `TenantContext` explícito)
- **Artefatos de planejamento** (`.spec/backend/public-api/`, 2.083 linhas, 6 fases do `dev-planner`):
  [`EXPLORATION.md`](../../.spec/backend/public-api/EXPLORATION.md) (9 perguntas de discovery, 7 achados)
  · [`CONSTRAINTS.md`](../../.spec/backend/public-api/CONSTRAINTS.md) (10 restrições medidas, compliance)
  · [`DESIGN-DECISIONS.md`](../../.spec/backend/public-api/DESIGN-DECISIONS.md) (confronto A/B/C, 18
  decisões, argumento contra a premissa do briefing) ·
  [`ARCHITECTURE.md`](../../.spec/backend/public-api/ARCHITECTURE.md) (3 tabelas, contrato do token,
  contrato HTTP, 4 erros) · [`PLAN.md`](../../.spec/backend/public-api/PLAN.md) (13 batches, 54 tasks,
  ~32–44 h) · [`VALIDATION.md`](../../.spec/backend/public-api/VALIDATION.md) (gate de conexão com o
  ERP, 10 passos)
- **Código (verificado por leitura, não por resumo):**
  `SecurityFilter.java:51` e `:66` · `SecurityTokenServiceImpl:42`, `:45`, `:182`, `:195-208` ·
  `SecurityConfig.java:44`, `:48` · `AuthenticationServiceImpl` ·
  `ChatServiceImpl:123`, `:127`, `:143-151`, `:170-174`, **`:176-186`**, `:200-201`,
  **`:203-246`**, `:248-252`, `:328-344` (345 linhas) · `LangChain4jTurnGateway` (179 linhas),
  `isModelNotFound` · `TurnRequest` / `TurnReply` / `ChatTurnGateway` · `ChatTrace` /
  `ChatTraceRecorder` · `PromptTemplateServiceImpl:82-88`, `activateVersion`, `deactivateVersion` ·
  `PromptTemplateRepository` (`findByTenantIdAndCodeAndVersion` **já existe**) ·
  `PromptTemplateControllerV1:64` · `ChatControllerV1:66-70` · `ChatRateLimiter` +
  `SlidingWindowRateLimiter` · `AesGcmAttributeConverter` + `ProviderMasterKey` ·
  `TenantContext` (`ScopedValue`) · `TenantAspect` · `AbstractEntity` (`@Filter`) ·
  `ControllerExceptionHandler` · `RestControllerTool` · `TokenCacheServiceImpl` + `RedisConfig` ·
  `LlmClientFactory.createClientWithSession`
- **Migrations:** `agent/V2__create_prompt_template.sql` (`uk_prompt_template_tenant_code_version`,
  `uk_prompt_template_active_version`, `chk_prompt_template_version`) ·
  `agent/V3__create_provider_credential.sql` · `agent/V4__create_chat_session.sql` ·
  `agent/V5__create_chat_message.sql` · `tenant/V3__tenant_llm_config.sql` · `AgentDbUpdateConfig`,
  `UserDbUpdateConfig`, `TenantDbUpdateConfig` · próxima versão livre por módulo: `agent` **V6**,
  `identity` **V4**, `tenant` **V4**, `publicapi` **V1**
- **Dívidas que este ADR não paga e que ele toca:**
  `.spec/tech-debts/security-rbac/TASKS.md` P1-1 (RBAC decorativo — **aberto**) ·
  `.spec/tech-debts/prompt-read-permission/TASKS.md` (**confirmado irrelevante** para esta API: o
  caminho novo resolve o template por `code` em serviço, dentro do `@Filter`, e não passa por
  `PromptTemplateControllerV1:64`) · `.spec/tech-debts/unwrap-data-empty-body/TASKS.md` (a
  terceira ocorrência da família da lição) · `/actuator/**` em `permitAll`
- **Commits do estado que este ADR descreve:** `7cbf54d` (migrations do agent reescritas, 12 → 4) ·
  `bee9839` (ADR-013) · `15eb7f1` (Batch 3) · `ab5cda1` (E2E com provider real) · `6599a0b` (Batch 2 —
  Assistant) · `48f36d6` (Batch 1 — remoção do RAG) · `400963e` (ADR-012) · `901ae25` (costuras do
  turno) · tag `pre-removal-rag-agent`
- **Brain (registro secundário, nunca fonte primária):**
  `arquitetura/projetos/hive/decisoes/api-publica-chat` ·
  `regras/global/api-m2m-roteiros` (5 padrões reutilizáveis: filter chain separado, versão só
  congela se imutável, extrair decisão antes de forkar, segredo de alta entropia, cota distribuída)
- **Fontes externas consultadas no planejamento:** OAuth 2.0 `client_credentials` (RFC 6749 §2.3.1,
  §4.4.3, §5.1) · *Implementing OAuth 2.0 Client Credentials Flow* (2026-05) — `client_secret_basic`
  como recomendação de indústria · *Securing M2M tokens in B2B SaaS* (2025-05) — TTL 5–15 min ·
  *Designing API Authentication for B2B SaaS* (2026-04) — **segredo de alta entropia é SHA-256, não
  bcrypt** · *Build 5 Rate Limiters with Redis* (redis.io) — sliding-window log em `SORTED SET` + Lua
  atômico · Vast.ai OpenAI-compatible guide — `model` é obrigatório no SDK e ignorado pelo proxy ·
  *OAuth 2.0 Client Credentials Misuse in Public Apps* (2025-06) — o grant é para cliente
  **confidencial**; o ERP é
