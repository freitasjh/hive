# VALIDATION — API pública de chat

- **Fase:** dev-planner 5 (Valide)
- **Data:** 2026-09-27

---

## 1. Consistência — os 6 artefatos fecham entre si?

| Verificação | Resultado |
|---|---|
| As 10 decisões fechadas aparecem com o mesmo número em todos os artefatos? | ✅ `EXPLORATION §2` ↔ `DESIGN-DECISIONS §3` ↔ `ARCHITECTURE` ↔ `PLAN §5` |
| Alguma decisão reaberta? | ❌ **Nenhuma.** A decisão 5 foi **corrigida na promessa**, não na decisão: continua `PINNED`/`FOLLOW_ACTIVE`, com semântica e códigos de erro explícitos |
| Alguma restrição de código ignorada? | ✅ Todas as 10 de `CONSTRAINTS.md §1` têm task: `ddl-auto`→3.4 · `repair()`→3.2 · jar→3.3 · `--enable-preview`/`ScopedValue`→3.7/4.2 · `@Filter`→3.5/3.4 · `TurnReply`→9.5 · `ChatServiceImpl`→1.x · envelope→8.3/9.8 · AES→3.6 · quota→7.x |
| Todo o fora-de-escopo respeitado? | ✅ RAG, tools, memória, anexos, voz, streaming, billing, metering, auto-serviço, SDK, marketplace — nenhum aparece como item. **Auto-serviço** é explicitamente cortado em `PLAN §3` |
| RBAC de usuário não foi pago? | ✅ Só o grant model (`public_api_client_grant`, 1 tabela, 1 permissão). `.spec/tech-debts/security-rbac/` P1-1 continua aberto e declarado |
| O desenho serve o cliente agente? | ✅ `DESIGN-DECISIONS §2.3` — ele é o **segundo chamador** da mesma porta, que é o que a porta valida. O que já nasce pronto está tabelado em `PLAN §4` |
| Cobertura decisão → task | ✅ 18/18 |
| Dívidas registradas, não corrigidas? | ✅ `CONSTRAINTS.md §5` — 7 itens, cada um com o efeito nesta feature |

### 1.1 As 2 contradições com o briefing, e o que cada uma custou

| # | O briefing diz | O código diz | Correção proposta | Custo |
|---|---|---|---|---|
| **1** | "A credencial fixa a VERSÃO… para que resolver latest não mude o ERP **silenciosamente**" | `update()` **muta a linha in-place** e incrementa o contador. **Não existe snapshot.** `PINNED` pane a integração a cada edição e nunca devolve o texto antigo | `PINNED` = **asserção de conformidade** (tripwire), documentado como tal; 4º erro `prompt_version_not_found`; `FOLLOW_ACTIVE` ganha `prompt_version` + `prompt_fingerprint` na resposta. O snapshot real é dívida, condicionada a mudar o editor da UI | ~10 linhas + 1 IT. **0 migration** |
| **2** | "A API pública pode ser a [primeira] segunda instância de `ChatTurnGateway`" | `LangChain4jTurnGateway` já decide 5 coisas que a porta existe para centralizar. Uma 2ª impl as **reimplementa** e faz `MODEL_UNAVAILABLE` divergir entre os caminhos. O gatilho 5 do ADR-012 **não** dispara | A API pública é o **2º chamador**, não a 2ª impl. O gatilho 5 do ADR-012 é registrado como **não disparado**, com motivo | 0 linhas a menos. **Ganha** coerência de diagnóstico |

E **um gate que o briefing não listou** (P-d):

| # | Gate | Onde | Custo |
|---|---|---|---|
| **3** | `SecurityFilter` chama `loadUserByUsername` **incondicionalmente**. Um `sub` = `clientId` não é um `users.username` → `UsernameNotFoundException` → lançado para fora do `FilterChainProxy` → **500**, não 401. Nenhum controller é alcançado | `EXPLORATION §4.2` · `DESIGN-DECISIONS D2` | 1 `SecurityFilterChain` + 1 filter. **E fecha a lacuna de endpoint por construção** |

**Nenhuma das 3 exige reabrir decisão de produto.** As 3 são de engenharia, e cada uma tem
alternativa mais barata que a escolhida — que é justamente o motivo de estarem documentadas.

---

## 2. Riscos remanescentes

### 2.1 🔴 Alto

| # | Risco | Mitigação | Onde fica provado |
|---|---|---|---|
| V1 | **A 2ª cadeia permite vazamento de rota.** Um matcher mal escrito deixa `/v1/prompt-templates` alcançável pela chain pública | `denyAll` explícito do path público na chain humana + IT de 4 asserções (401 nas 4 rotas internas) | `PLAN` 6.4 |
| V2 | **A extração do batch 1 muda comportamento silenciosamente.** O único caminho é um `log.error` | Batch separado, `git diff` das linhas load-bearing, suíte, **E2E com `systemBlockChars` igual ao baseline** | `PLAN` 1.6 |
| V3 | **Ordem de checagem invertida** → 403/404 vira oráculo de existência de `prompt_code` no tenant | 403 **antes** do primeiro SELECT, e IT com `prompt_not_in_allowlist` para **code inexistente** | `PLAN` 9.3, 9.11 |
| V4 | **Emissão do primeiro token antes de P-a** → superusuário público | O `PLAN` não expõe nenhum endpoint antes dos batches 3–5 | `PLAN` §1 |

### 2.2 🟡 Médio

| # | Risco | Mitigação |
|---|---|---|
| V5 | `sessionId=null` vira header `"null"` no provider (GO/ZEN) | Gate R2 no batch 2, **antes** do batch 9. Decisão tomada com o código na mão |
| V6 | Cota em Redis = nova dependência do caminho de chat | fail-open + `log.error`; `ChatRateLimiter` em memória intocado para `/v1/chat` |
| V7 | `PUBLIC_CHAT_COMPLETE` vira a 4ª lista hardcoded | Sai de **linha de banco**; e **não** entra em `SecurityConstants.DEFAULT_PERMISSIONS` (que é exibição e tem teste de paridade com `LoginIT`) |
| V8 | O ERP reenvia o histórico inteiro e o custo de prompt cresce sem limite | Teto de 20 mensagens + 8000 chars por `content`, com erro `too_many_messages` |
| V9 | `user` virado de âncora de segurança por engano future | Documentado: `user` é correlação e log, **nunca** `@Filter`, nunca prompt, nunca confiável |
| V10 | Erro de provisionamento (`prompt_code` na allowlist que não existe) vira mistério | `404 prompt_not_found` com mensagem que diz "erro de provisionamento", e log `warn` com `clientId` |

### 2.3 🟢 Baixo / aceito

| # | Risco | Por que aceito |
|---|---|---|
| V11 | Sem idempotency key | Stateless e sem efeito colateral. Dívida registrada (D15) |
| V12 | Sem discovery | Decisão 10. Os códigos chegam no provisionamento e a resposta `hive{}` **não** lista |
| V13 | Sem version negotiation por header | Decisão 14. O `/v1` já está no path 2× |
| V14 | `public_api_client` é nome estreito para o cliente agente | Renomear agora é migração por antecipação. ADR-012 já registrou o custo de manter um nome que mente |
| V15 | `/actuator/**` em `permitAll` persiste | Adjacente, **não** afetado (a chain nova casa só `/v1/public/**`). Registrado como dívida |
| V16 | RBAC de usuário continua decorativo | Dívida pré-existente, explicitamente fora do escopo pelo PM |

---

## 3. O que este replanejamento mudou em relação ao ponto de partida

Três coisas, e vale registrar porque a mudança real foi de **premissa**, não de escopo:

1. **Saiu a "2ª implementação da porta"** e entrou o "2º chamador" — o que **remove** ~179 linhas de
   duplicação futura e mantém `MODEL_UNAVAILABLE` coerente entre os dois caminhos.
2. **Entrou um gate de segurança que não estava na lista** (o `loadUserByUsername` incondicional), e
   ele é justamente o que **permite não pagar o RBAC de usuário** — a restrição do PM é honrada
   porque a lacuna foi fechada por **separação de cadeia**, que é mais forte que lista.
3. **A decisão 5 mudou de significado, não de conteúdo.** Pinning continua; o que muda é a
   **expectativa** (tripwire, não snapshot) e o **diagnóstico** (4º erro). Sem isso, o ERP receberia
   um 409 sem código e não saberia se era bug dele, do cliente, ou manutenção de rotina.

---

## 4. 🔴 Gate de conexão com o ERP — como saber que funciona ponta a ponta

O H3 de `.agents/rules/harness-continuous.md` cobre o **re-cadastro pela UI**, que é o loop de
produto. Esta feature tem outro consumidor: **um sistema externo com segredo no CI**. O H3 não a
cobre, e o critério de "pronta" é outro.

**Regra que este batch segue, e que é a lição do ADR-013 §Regra 1:** antes de chamar de pronta,
**um humano exercita a integração em ambiente real, por fora do produto** — não por IT, não por
`curl` contra Testcontainers.

### 4.1 Pré-condições

- [ ] Suíte completa verde: `mvn clean install -DskipTests` → `mvn test` → `mvn verify -pl integration-test -Pintegration -am` (H1, 3 comandos)
- [ ] `npm run test` e `npm run build` verdes (o batch 10 toca a UI de provisionamento)
- [ ] `code_review.last_status == "passed"`, zero 🔴
- [ ] `tenant_llm_config` do tenant configurado, `provider_credential` com chave **real** (não a de dev)
- [ ] Prompt template criado, `code` = o esperado, `status = ACTIVE`
- [ ] Credencial provisionada com a allowlist e o modo de pin corretos

### 4.2 Os 10 passos do gate

| # | Passo | Passa se | Falha se |
|---|---|---|---|
| 1 | Provisiona a credencial e **guarda o `client_secret`** | resposta traz `clientId` + secret **uma vez** | secret reaparece em qualquer leitura posterior |
| 2 | Coloca `client_id`/`client_secret` no secret store do CI do ERP | CI lê de secret, não de arquivo versionado | secret em `application.properties`, no repo, ou em log |
| 3 | Troca o token com o **SDK OpenAI real** (não `curl`) apontando `base_url` = `https://host/hive/v1/public` | 200 com `access_token` + `expires_in` | qualquer ajuste manual de path |
| 4 | **Re-autentica depois de `expires_in`** | segundo token sem intervenção manual | 400/401, ou re-autentica a cada chamada (sintoma: 429 no `/auth/token`) |
| 5 | Chat com `prompt_code` permitido, `messages` multi-turn | 200, `choices[0].message.content` preenchido, `usage` presente | — |
| 6 | Confere `metadata.hive` | `prompt_version` e `prompt_fingerprint` presentes; `tenant_id` é o esperado | `hive{}` ausente, ou `prompt_version` ≠ provisionado |
| 7 | `prompt_code` **não** na allowlist | **403** `prompt_not_in_allowlist` | 404, 500, ou 200 |
| 8 | `prompt_code` na allowlist mas **inexistente** no tenant | **403** `prompt_not_in_allowlist` — **igual ao passo 7** | 404. *Esta é a prova de anti-enumeração* |
| 9 | Prompt fica `DRAFT` | **409** `prompt_not_active` | 400 genérico |
| 10 | Token público em rota interna: `GET /hive/v1/prompt-templates` e `POST /hive/v1/provider-credential` | **401** nos dois | 403 (vazou a chain) ou 200 (**breach**) |

### 4.3 Os 3 cenários que os testes não pegam

| Cenário | Por que IT não pega | Passo |
|---|---|---|
| O SDK OpenAI monta o path certo | Um IT com MockMvc testa a URL que o dev **escreveu**, não a que o **SDK constrói** | 3 |
| O ERP re-autentica no ritmo certo | Nenhum IT sabe a frequência de chamada do ERP | 4 |
| `prompt_code` inexistente devolve o **mesmo** código que fora da allowlist | Precisaria de duas credenciais e dois prompts | 7+8 |

⚠️ **Os passos 7 e 8 juntos são o critério de aceite do achado nº 1.** Se divergirem, a allowlist
está conferindo **depois** do lookup, e a decisão 4 não está valendo — a API passa a contar quais
`prompt_code` existem no tenant.

⚠️ **O passo 10 é o critério de aceite do gate P-d e da decisão D2.** Se a chain vazar, o
`AGENT_MANAGE` que o briefing apontou está de volta, e nenhuma lista resolve.

### 4.4 O que este gate **não** cobre, e é dívida declarada

| Não coberto | Por quê | Dívida |
|---|---|---|
| Vazamento do `client_secret` em log do ERP | É observabilidade **deles** | due diligence do ERP |
| Carga / teste de saturação | É uma integração de teste, não produto | — |
| Rotação de segredo com o ERP no meio | Requer janela de graça orquestrada | exercise no batch 12 se o tempo permitir |
| Streaming | Fora de escopo (decisão 9) | fase 2 |
| Cliente agente | Fase 2 | — |

---

## 5. Handoff

**Para o `developer-engineer`, em ordem:**

1. Batch 0 — portão H2.2. **Se falhar, para.**
2. Batch 1 — extração D4, com o gate 1.6. **Não pular, e não fundir com o batch 9.**
3. Batches 2 → 12 na ordem de `PLAN.md §1`.

**Para o `sdd-compiler`:** os artefatos já cobrem EXPLORATION → CONSTRAINTS → DESIGN-DECISIONS →
ARCHITECTURE → PLAN. Faltam `SPEC.md` e `TASKS.md`, que são reexpressão em formato SDD, não conteúdo
novo. O `PLAN.md` já tem a ordem, o pré-requisito de segurança marcado e a rastreabilidade decisão →
task, então o `TASKS.md` é mecânico a partir dele.

**Para o usuário — 5 perguntas bloqueantes**, que estão em `EXPLORATION.md §3.1` (Q1–Q5) e
`§3.2` (Q6–Q9, com premissa já adotada). As que **travam** são:

| # | Pergunta | Premissa que adotei |
|---|---|---|
| **Q1** | `PINNED` como **tripwire** (notifica que o prompt se moveu) serve, ou você quer **snapshot** (o ERP continua no texto antigo)? | Tripwire. Snapshot exige mudar o editor da UI — batch próprio |
| **Q2** | `messages[]` pode conter `role: "system"`? | Não. Aceitar deixa o ERP sobrescrever o prompt do cliente |
| **Q3** | O ERP quer o transcript **persistido** no Hive? | Não. Stateless, zero persistência de conteúdo |
| **Q4** | O ERP reenvia quantas mensagens por chamada? | Teto de 20, com `too_many_messages` acima |
| **Q5** | `client_secret_basic` (header) ou `client_secret_post` (body)? | Ambos, `basic` preferido (RFC 6749 §2.3.1) |

**Uma decisão de esforço, não de arquitetura:** aprova-se criar o módulo Maven `publicapi` (4 poms,
1 config Flyway)? É a recomendação (D1) e evita a aresta `agent → security`, mas é o item com mais
arquivos novos do plano. A alternativa é 2 módulos existentes + 1 aresta nova entre brothers.

---

**Fim das 6 fases.** Nenhum código de implementação foi escrito.
