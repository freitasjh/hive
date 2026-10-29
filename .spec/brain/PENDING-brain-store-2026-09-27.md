# PENDING brain_store — marco do Assistant validado ponta a ponta

**Data:** 2026-09-27 · `brain_ping` → HTTP 410 (5a vez seguida)

Destino quando o Brain voltar:
- `layer=arquitetura`, `path=hive/decisoes/assistant-loop-validado-end-to-end`, `scope=projetos`
- tags: hive, mvp, validacao, produto, aprendizado, e2e, escopo

---

# Marco: loop do Assistant validado ponta a ponta com provider real

**Status:** MVP do loop elementar **validado com modelo de verdade**.

## O que foi validado

Cadastrar credencial → configurar default do tenant (`/settings/llm`) → cadastrar prompt
template → abrir chat → escolher prompt → enviar mensagem → **resposta do modelo real**.

Fluxo completo funcionando. Sem provider real, isto nunca tinha rodado desde a mudança de
escopo: ate aqui a geracao era coberta por IT com Testcontainers e um 502 com chave falsa.

## Por que isto importa mais que qualquer codigo

A decisao de remover o RAG veio do diagnostico: *"estamos dando um passo maior que a perna,
principalmente que ainda nao temos um MVP bem estruturado com assistent"*. Este marco fecha a
divida que aquela decisao comprou.

**A licao que ele paga, e que vale mais que a decisao:**

> Construir a segunda abstracao antes do primeiro loop estar fechado custa caro mesmo quando a
> segunda esta correta. O RAG estava funcional, testado, com E2E real, revisado e documentado por
> 3 ADRs. E mesmo assim era o lugar errado — nao porque era ruim, mas porque **nao era o que ainda
> nao existia**.

O sintoma que antecede essa classe de erro: **otimizacao de camada de infraestrutura enquanto o
loop de produto esta aberto.** A conta nao aparece em metrica de qualidade — RAG passando em
tudo, 700 testes verdes, 3 ADRs. Aparece em produto que nao e usavel.

## Regra que decorre

**Antes de construir a segunda capacidade, exercite a primeira em ambiente real, por um humano,
no browser.** Nao por IT, nao por curl, nao por mock.

Caso concreto deste projeto: o `PromptSelector` pedia `size=100` contra um servidor que responde
400 para `size>50` — **400 em toda montagem da tela**, invisivel a 145 testes porque o mock aceita
qualquer numero. So o browser real viu.

**Rede de seguranca estrutural tambem morre com o codigo que ela protegia.** O
`HardcodedSimilarityCutOffGuard` protegia contra hardcodar threshold de similaridade; o unico
decidedor de threshold morreu, e o guard ficou orfao em `src/main` protegendo um erro que nao pode
mais ocorrer. Guard sem consumidor e orcamento de manutencao de uma condicao que ninguem mais impoe.

## O que este marco destrava

- **API publica** — agora ha um loop interno funcionando para expor. A pergunta de autenticacao e
  cota (que ficou aberta) agora tem o que proteger.
- **RAG de novo, se um dia fizer sentido** — com gatilho explicito e o loop ja validado, em vez de
  como segunda abstracao adivinhada.

## Pendente

- **Rotacionar `OPENCODE_GO`**: a chave antiga foi exposta em conversa. O fluxo acima cadastrou
  credencial nova — **confirmar que a antiga foi revogada e nao so contornada**.
- **P1 de RBAC** (`.spec/tech-debts/security-rbac/`): todo usuario autenticado e superusuario, RBAC
  decorativo. Relevante agora que existe produto usavel.


---
---

# [2] Lição — o teste que não pega regressão

**Data:** 2026-09-27 · `brain_ping` → HTTP 410 (6a vez seguida)
**Destino:** `layer=regras`, `path=hive/eng/frontend/teste-que-nao-pega-regressao`, `scope=projetos`
tags: eng/frontend, teste, regressao, vitest, primevue, jsdom

## O caso

Para provar que o filtro client-side do PrimeVue estava neutralizado no `PromptSelector`, escrevi
um teste que chamava o handler do componente e inspecionava o `computed` de `options`:

```ts
vm.onFilter({ value: 'auditoria' })
expect(vm.options.every(o => o.searchTerm === 'auditoria')).toBe(true)
```

**Passou — e passou também com a neutralização removida.** O reviewer rodou a suíte contra uma cópia
do componente sem `:filterFields` (o PrimeVue caía no fallback `searchFields=[optionLabel]=['name']`,
o bug original) e viu **19 passed (19)** com o dropdown mostrando 1 de 5.

Por quê: eu chamava o método do componente e lia o estado do componente. O teste afirmava o invariante
que eu escrevi no código, sobre o código. Tautologia.

## A regra

> **Um teste de comportamento dirige o comportamento. Se ele chama o método do componente e lê o
> estado do componente, ele testa a implementação, não o efeito.**

O que expõe o efeito é a **entrada real** (o input que o usuário digita) e a **saída visível** (o que
está no DOM). No meio fica a biblioteca: o PrimeVue que decide filtrar, o filtro do browser, o overlay
teleportado.

```ts
const input = document.body.querySelector('input[role="searchbox"]')
input.value = 'fiscal'
input.dispatchEvent(new Event('input', { bubbles: true }))
expect(document.body.querySelectorAll('.p-select-overlay li[role="option"]').length).toBe(5)
```

## Prova de que o teste tem dentes

Escrever não basta: **tirar o conserto e ver o teste morrer**.

| build | resultado |
|-------|-----------|
| correto | 23 passed |
| sem `:filterFields` (neutralização removida) | **3 failed** — "expected 1 to be 5", "expected 1 to be 2" |
| sem o fix do vazamento de loading | **1 failed** — "expected 'pending' to be 'idle'" |

Teste que não morre quando o conserto sai não é barreira: é decoração.

## O detalhe que quase deixou passar de novo

A primeira fixture tinha 5 resultados **com o termo no `name`**. Com a neutralização removida, o filtro
padrão ainda casava — então o teste passou com o bug de volta. **A fixture precisa discriminar**: o
termo tem que casar o que o *servidor* casa (`name`, `code` OU `description`) e **não** casar o que o
`filter` padrão casa (`name` só).

## jsdom: duas armadilhas que escondem o dropdown inteiro

1. **VTU monta destacado.** Sem `attachTo: document.body`, `document.querySelector` não enxerga a raiz
   do componente — só o overlay teleportado. O teste passa a consultar duas metades diferentes do
   mesmo componente e produz asserções que não descrevem nada.
2. **Overlay vaza entre testes.** O Portal do PrimeVue escreve em `document.body` e o VTU não
   desmonta sozinho: o painel do teste anterior continua no DOM do seguinte e consultas por
   `data-testid` acham o painel errado. `afterEach`: `wrapper.unmount()` **antes** de
   `document.body.innerHTML = ''`.

## Mesma raiz, segunda ocorrência no mesmo dia

`unwrapPage` lia `page.totalElements`, mas o Spring 3+ **aninha** em `page.page.totalElements`. Os
testes mockavam a forma achatada (a antiga), então passavam; o servidor manda a outra. Resultado: o
rodapé "Ver todos os N prompts" **nunca aparecia** em produção, porque `N` caía no comprimento da
página (50 == 50). Achado **só no H3**, com o backend no ar.

> **Regra:** todo teste que depende da **forma de um payload externo** precisa de mock colado de uma
> resposta real (curl / DevTools / request gravada), nunca de memória.

## Checklist para o próximo teste de UI

- [ ] Dirige a entrada real (input, clique), não o handler do componente?
- [ ] Afirma a saída visível (DOM, contagem de nós), não o `computed`/`ref`?
- [ ] A fixture **discrimina** entre o comportamento certo e o errado?
- [ ] O mock de payload externo foi copiado de uma resposta real?
- [ ] O teste **morre** quando eu tiro o conserto? (rode e reporte)
- [ ] Os wrappers estão `attachTo: document.body` e desmontados no `afterEach`?

---

# [3] Lição — estado de carregamento que mente

**Data:** 2026-09-27 · `brain_ping` → HTTP 410 (6a vez seguida)
**Destino:** `layer=regras`, `path=hive/eng/frontend/estado-de-carregamento-que-mente`, `scope=projetos`
tags: eng/frontend, ux, estado-de-carregamento, acessibilidade, mentira, design-system

## O caso

No seletor, `mode` era promovido para `'search'` **na tecla**, mas `searchResults` só era populado
400ms (debounce) + RTT depois. O cabeçalho decidia ausência por `searchResults.length === 0` — que é
o estado *inicial*, não o resultado.

Snapshot no instante da digitação (`q=auditoria`, nada tinha chegado):

```
Resultados da busca | Nenhum prompt para "auditoria" | Criar prompt "auditoria"
| Ver todos os prompts | No results found | No available options | No selected item
```

É literalmente o defeito que o batch existia para matar — **busca que mente** — e ainda por cima
oferecia "Criar prompt" antes de saber: quem clicasse saía do chat para criar algo que talvez já
existisse. E o comentário no CSS prometia "o que o usuário já leu não some", verdadeiro da 2ª busca
em diante e **falso na 1ª** (não há lista anterior).

## A regra

> **Ausência é uma afirmação sobre uma resposta. Sem resposta, o estado correto é "não sei".**

"Lista vazia", "carregando" e "não achou" são três fatos diferentes. Colados, um vira mentira — e o
pior não é a mentira visual, é a **ação** que ela oferece: um estado de carregamento que oferece
"Criar" convida o usuário a criar.

## A máquina de fases que resolveu

```ts
type SearchPhase = 'idle' | 'pending' | 'settled'
```

| fase | quando | o que a UI pode afirmar |
|------|--------|------------------------|
| `idle` | nada em andamento; termo < 2 chars | nada (só "Prompts recentes") |
| `pending` | tecla >= 2 chars, **incluindo os 400ms de debounce** | só "Buscando…" — nunca ausência |
| `settled` | a resposta chegou | contagem, ou "Nenhum prompt para …" + ações |

Ausência atrás de três condições, todas necessárias:
`mode==='search' && phase==='settled' && !searchError && results.length===0`.

Ganho de estrutura: **o vazamento de loading ficou impossível**, porque a fase só é escrita pela
geração corrente da requisição, e voltar a menos de 2 caracteres é transição explícita
(`goBackToRecent()`), não um `seq++` esquecido.

## A regra irmã, do mesmo batch

> **Ligue a acessibilidade e o esmaecimento ao estado da INTERFACE, não ao estado da REQUISIÇÃO.**

`aria-busy` e a classe de esmaecimento estavam ligados a `searchLoading`, que só vira `true` **depois**
do debounce — a UI mudava 400ms antes de a a11y acompanhar. Entre a tecla e a resposta a lista na tela
já é de outra pergunta, e a UI tem que dizer isso na janela inteira.

## Esmaecer é diferente de esvaziar

O parecer pedia "as 20 opções antigas esmaecidas durante o carregamento". Esmaecer faz sentido **da
2ª busca em diante**. Na 1ª não há o que esmaecer, e o certo é estado de carregamento explícito, não
lista vazia com "carregando" em cima. Daí duas grandezas **separadas**:

```ts
const isSearchPending = computed(() => mode === 'search' && searchPhase === 'pending')  // a11y
const hasStaleList    = computed(() => isSearchPending.value && options.value.length > 0)  // esmaecimento
```

`hasStaleList` herda de `isSearchPending`. Uma fórmula só não dá para as duas.

## Acessibilidade: uma live region, e ela é a do projeto

O PrimeVue publica três `role="status"` próprios (contagem do filtro, "sem opções", "item
selecionado") com o texto do **locale, que aqui é inglês**, e nenhuma sabe que existe requisição em
andamento. Reduzi as três a uma região do projeto com `:filterMessage=" "`,
`:emptyFilterMessage=" "`, `:emptyMessage=" "`.

**Armadilha:** a prop é `props || locale.x`, então **string vazia NÃO silencia** — cai no locale. Um
espaço é verdadeiro e não é fala. E a região do projeto fica **fora** do cabeçalho visual, com o mesmo
texto que o usuário lê, para o leitor de tela não ouvir duas vezes nem em línguas diferentes.

## promoted a regra do Design System

`DESIGN_SYSTEM.md` §4 (novo) agora fixa: erros não se desculpam · causa+consequência+ação ·
mensagem de sistema nunca em `role="assistant"` · **carregamento nunca se confunde com vazio** ·
sem beco sem saída e sem saída sem destino.

## Checklist do próximo estado

- [ ] Existe uma fase que distingue "não sei" de "não tem"?
- [ ] A fase começa na **tecla**, não no request?
- [ ] Nenhuma ação criadora/destrutiva é oferecida antes da resposta?
- [ ] `aria-busy` e esmaecimento derivam do estado da UI, e são grandezas **separadas**?
- [ ] As regiões vivas da biblioteca foram silenciadas ou traduzidas?
- [ ] A contagem é anunciada **uma vez**, na língua do produto?


---
---

# [4] Lição — estado centralizado + dois caminhos de entrada

**Data:** 2026-09-27 (2ª rodada do code review) · `brain_ping` → HTTP 410 / timeout (7a vez)
**Destinos:**
- `layer=regras`, `path=hive/eng/frontend/estado-centralizado-e-entrada-dupla`, `scope=projetos`
tags: eng/frontend, ux, regressao, estado, entrada-dupla, compos, ambiente

## Corrigir um estado centralizado derruba o vizinho que ninguém olhou

Sintoma: busca falha → retry **sucede** → a lista nova aparece **com o rodapé de erro ainda na
tela** e a live region continua anunciando "Não foi possível buscar".

A causa **não** foi o `finally` que eu tinha removido (ele só fazia `searchLoading = false`; o
`searchError = null` estava no topo). A causa real:

> **`runSearch` tem DOIS caminhos de entrada** — o debounce (que passa por `onFilter`) e o
> `retrySearch` (que chama direto). O estado inicial da busca vivia em `onFilter`, então o caminho
> que **não passa por ele** não recebia o estado inicial.

E a auditoria pedida achou **um segundo bug do mesmo formato**: a fase `pending` também só era
setada por `onFilter`, então durante o retry a lista não esmaece e `aria-busy` fica `false` com um
request real em andamento. Invisível, e a mesma classe do bug de "aria-busy ligado ao request em vez
da UI" que eu tinha acabado de corrigir — **no caminho que ninguém exercita a mão**.

## A regra

> **Todo estado que começa uma operação mora no ponto onde a operação começa, não num chamador
> anterior do caminho feliz.**

E o corolário que a auditoria compra:

> **Ao remover um `finally`, a pergunta útil não é "o que o `finally` perdia?"** (ele não perdia
> nada) e sim **"quais caminhos chegam aqui, e todos recebem o estado inicial?"**

Como achar: listar **todos os chamadores** da função e, para cada um, o estado que ele sets e o que
falta. Um caminho que não passa pelo inicializador é um bug esperando o dia em que alguém exercitar
aquele botão. **Nenhum dos 26 testes exercitava o "Tentar novamente".**

## Prova

| build | resultado |
|-------|-----------|
| correto | 26 passed |
| sem `searchError = null` no topo do `runSearch` | **2 failed** |

O segundo teste ("loading treatment while a RETRY is in flight") foi escrito **depois** da
auditoria, e é o que pega o bug invisível.

## [5] Lição — `name:` de projeto compose é o nome do DIRETÓRIO

Dois clones do produto, mesmo `docker/`, mesmo nome de projeto. `up -d` reconcilia o **namespace
inteiro** e recriou o postgres de outro produto.

- `name: hive` resolve (1 linha por arquivo, Compose V2 honra).
- **Mas sozinho quebra o ambiente:** o compose deriva o nome do volume de `<projeto>_<volume>`, então
  o próximo `up -d` criaria `hive_postgres_data` **vazio** e o banco "desapareceria" — a terceira vez.
  Por isso os volumes ganharam `name: docker_postgres_data` **junto**. Trocar nome de projeto e nome
  de volume é **um** passo.
- **Mover a porta não resolve:** o outro clone continua com `5432:5432` e vai recriar o container na
  próxima vez que subir. A armadilha fica armada.
- O que salvou o dado foi o **volume**, não o container. E o que **escondeu** a origem foi o
  `container_name: postgres` confundido: ele **sai do namespace do projeto** (é global) e faz um
  incidente do produto A parecer ser do produto B.

**Antes de qualquer `docker compose up -d`:** `config | head -1` imprime o `name:` esperado? existe
outro clone com o mesmo nome? os volumes têm `name:` fixado? eu vou passar `--remove-orphans`?

Detalhe em `.spec/tech-debts/compose-project-name/TASKS.md`.

## [6] Lição — o `res.data as Page<T>` é uma afirmação sobre o servidor

O Spring Boot 3+ **aninha** o `totalElements`/`totalPages` dentro de `page`:

```json
{ "content": [...], "page": { "size": 20, "number": 0, "totalElements": 25, "totalPages": 2 } }
```

Um cast passivo entrega `undefined` nos dois. E `undefined > 1` é **falso** — o `ChatSidebar` nunca
renderiza o paginador, sem erro e sem aviso. Num tenant com 58 conversas o usuário via 20 e não
alcançava as outras 38: **achar conversa antiga é função principal de um produto de chat.**

A regra: **nenhum serviço faz cast de payload paginado; todos passam por `services/page.ts`.** E todo
teste de serviço que depende da forma do payload usa um body **colado de resposta real** (curl /
DevTools), nunca de memória — foi a forma achatada nos mocks que escondeu os dois bugs.

---


---
---

# [7] Lição — `max(version)` do Flyway devolve 9, e a verdade é 14

**Data:** 2026-09-27 (3ª rodada — correções de doc) · `brain_store` → HTTP 410 (8a vez)
**Destino:** `layer=regras`, `path=hive/eng/sql/max-version-do-flyway-mente`, `scope=projetos`
tags: sql, flyway, metrica, comparacao-lexicografica, registro, devops

## O fato

`flyway_schema_history_agent.version` é **`character varying`**, não numérico. Então:

```sql
SELECT max(version) FROM flyway_schema_history_agent;        -- devolve 9   ERRADO
SELECT max(version::numeric) FROM flyway_schema_history_agent;-- devolve 14  CERTO
SELECT version, description FROM flyway_schema_history_agent
  ORDER BY installed_rank DESC LIMIT 1;   -- 14 | agent delete cascade  (a mais confiavel)
```

Porque `'9' > '14'` em ordem de texto. E o erro é **silencioso e plausível**: `9` é um número de
migration que existe de verdade naquele histórico, então ninguém desconfia.

## Como isso chegou a um documento

Um registro de dívida técnica afirmava "versão aplicada: 9" e listava, com confiança, quais migrations
nunca rodaram. Estava errado em **dois** lugares pelo mesmo motivo: o número **e** a frase
("V10, V14 nunca aplicaram"), quando V10 e V14 **aplicaram** — V14 é justamente a que adicionou a
cascata que a V15 viria remover.

Num documento cuja função é dizer a alguém **qual é o estado do schema antes de rodar o SQL de
recuperação**, isso não é cosmético: a pessoa raciocina sobre um schema duas migrations atrás e
escreve o SQL errado.

**E o detalhe que fecha a lição: a checagem de aceite reproduziu o mesmo bug.** Rodei `max(version)`
para "confirmar" e obtive 9 — o número errado passando por verificação. Se eu tivesse confiado no
comando, teria publicado o 9 outra vez.

> **Nunca confie no número que uma consulta de agregação devolve sem olhar o tipo da coluna.**
> `max()`/`min()`/comparação em coluna `varchar` é ordenação de texto, e o resultado parece um número
> porque as strings parecem números.

## A família do mesmo bug — terceira ocorrência no mesmo batch

| onde | o que acontece |
|------|----------------|
| `res.data as Page<T>` | `totalElements`/`totalPages` do Spring 3+ vêm **aninhados** em `page`; o cast passivo entrega `undefined`, e `undefined > 1` é falso → o paginador nunca renderiza |
| `max(version)` do Flyway | `version` é `varchar`; `'9' > '14'` → o estado do schema parece anterior ao real |
| filtro client-side do PrimeVue | o `filter` roda sobre o que já está em memória e **parece** buscar sem alcançar o item #51 |

**O que as três têm em comum: nenhuma dá erro.** Cada uma devolve um valor *plausível* e errado, num
sistema que continua funcionando. Por isso só aparecem quando alguém **mede** o resultado em vez de
confiar nele — no navegador, no psql, ou com um teste que dirige a entrada real e afirma a saída
visível.

> **Toda métrica vinda do servidor, do Flyway ou de uma biblioteca é candidata a "valor plausível e
> errado".** Meça o valor, não a existência dele. E meça com a consulta **certa**: olhar o tipo da
> coluna é parte de medir.

## Checklist antes de publicar um número medido

- [ ] Qual é o **tipo** da coluna? (`information_schema.columns.data_type`)
- [ ] A ordenação é numérica ou lexicográfica?
- [ ] Existe uma forma **independente** de chegar ao mesmo número? (aqui: `order by installed_rank`)
- [ ] Se o número contradiz o que o código sugere, quem está mentindo — o código ou a consulta?

---

---

# [APPEND 2026-09-27 17:5x] Reescrita do histórico de migrations do módulo `agent`

> `brain_store` falhou com `HTTP 410` (endpoint do Brain MCP fora do ar — o mesmo erro do
> `brain_search` no início do batch). Registrado aqui para replay quando o MCP voltar.

## Contexto
12 migrations, das quais 4 existiam só para consertar as anteriores (`fix_*` ×2, a V10 que migrava
dado criado errado pela V2, a V14 que refazia a FK). O conceito `Agent` saiu do produto. Decisão do
usuário: reescrever o histórico para o schema nascer no formato final, sem migration de conserto.

## As 3 decisões investigadas

### (a) Versionamento de prompt — as colunas FICAM
`ADR_Prompt_Versioning.md` diz "não implementada". Verdade do MODELO do ADR (níveis, histórico de
transições, rollback); FALSO do que o schema e o código fazem. Versionamento inteiro em uso:
- `PromptTemplateEntity.version` (`nullable = false`)
- `PromptTemplateServiceImpl:50-51` (create → 1), `:82` (update → +1), `:105` (duplicar → latest+1)
- `PromptTemplateRepository:27,31,35` (`ORDER BY version DESC`), `:33` (`findByTenantIdAndCodeAndVersion`)
- `UNIQUE (tenant_id, code, version)` + índice parcial UNIQUE `(tenant_id, code) WHERE status='ACTIVE'`

**Lição: ADR marcado "não implementada" não prova schema morto. Verificar código + schema antes de
remover coluna.** A nota se autossabotava: reconhecia a coluna `version` e mesmo assim escrevia
"não existe versionamento de prompt no código".

### (b) Baseline (V1) — não existe arquivo, e reescrever é INVISÍVEL para o Flyway
`AgentDbUpdateConfig`: `baselineOnMigrate(true)`, `outOfOrder(true)`, e `flyway.repair()` antes do
`migrate()` (via `initMethod`). **`repair()` realinha checksums e descrições das migrations já
aplicadas.** Reescrever o histórico sem dropar o schema faz o Flyway realinhar o V2 antigo com o V2
novo, marcar as versions sumidas como `deleted`, e rodar **ZERO** migrations reportando sucesso.

**Consequência: dropar o schema do módulo é OBRIGATÓRIO.** Sem isso a reescrita é no-op silencioso.
Mesma família do `max(version)` que já enganou este projeto.

### (c) `provider_credential` fica no módulo `agent`
Entidade, service, repository, mapper, controller e criptografia estão todos em `agent/`. O módulo
`provider` **não tem `dbupdate`** (só `api` e `langchain4j`) — não tem como ser dono de tabela. Não
cita a tabela nenhuma vez. O nome é descritivo (credencial *de um* provider), não de módulo.

## Resultado
4 migrations, todas `CREATE` puras: `V2__create_prompt_template`, `V3__create_provider_credential`,
`V4__create_chat_session`, `V5__create_chat_message`. Zero `ALTER`/`DROP`/`fix_`/`drop_`.

## A lição de método: a premissa do diff estava errada
O critério de aceite esperava 3 diferenças de schema. **Todas as 3 já eram o estado final da série
antiga** — a V16 dropava `agent`, a V15 renomeava `agent_id` → `agent_id_legacy` e punha
`prompt_template_id` NOT NULL. A série antiga criava e destruía essas coisas; o fim já era o alvo.

Diff real: **2** diferenças.
1. `chat_session.agent_id_legacy` — não recriada (é o resto do "agent_id ausente")
2. `uniq_prompt_active_code_per_tenant` — índice parcial UNIQUE **duplicado**: a V7 recriou o que a V3
   já tinha, mesmo predicado `(tenant_id, code) WHERE status='ACTIVE'`. Colunas e predicado
   idênticos: só custava uma escrita extra por linha de `prompt_template`.

**Lição: compare o ESTADO FINAL da série antiga, não o caminho. E se o número de diferenças não bate
com o esperado, suspeite do critério de aceite antes de maquiar o resultado.**

## Backup e perda
`pg_dump` completo (custom, com dados) ANTES de dropar:
`.spec/snapshots/pre-rewrite-20260927-173430-full.dump`. Perdidos 58 `chat_session`, 130
`chat_message`, 1 `provider_credential` (credencial cifrada — restaurar exige recadastrar, a chave não
está no dump). `identity` e `tenant` intactos. Nenhum container removido; schema do módulo `agent`
derrubado por SQL. Resíduo do módulo `knowledge` (deletado em 2026-09-26) também saiu — foi
obrigatório, `agent_knowledge_base` tinha FK para `agent(id)`.

## Armadilha do Flyway
A V15 documentava que **nunca se escreva `$$` num arquivo de migration** — o parser de script do
Flyway corta o arquivo no delimitador, mesmo dentro de comentário. As 4 migrations novas **não usam
nenhum bloco dollar-quoted**, o que elimina a classe inteira do problema.

---

## PENDENTE 2 — correções documentais da API pública aplicadas (2026-09-27, ~23:59)

- **O quê:** 9 blocos de correção (D1–D8 + P6) aplicados nos 6 artefatos de `.spec/backend/public-api/`,
  no ADR-014 e em `workflow-state.json`. Criado `RUNBOOK-prompt-edition.md`.
- **Respostas do usuário:** P1 tripwire · P2 FOLLOW_ACTIVE (condição do PM) · P3 log pesquisável ·
  P4 gateway tocado · P5 SecurityFilter.java inalterado · P6 re-medir baseline.
- **Lição de processo (regra candidata, scope=projetos):** quando um parecer arbitra uma divergência,
  a pergunta que desempata deve ser formulada pelo **próprio autor da posição perdedora** — o PM
  escreveu a condição ("1 integração, mesmo time do deploy") que derrubou a própria proposta. Arbitragem
  externa sem condição declarada vira disputa de autoridade; com condição, vira medição.
- **Lição de doc (scope=projetos):** causa errada documentada é pior que ausência de causa — B2
  (`loadUserByUsername:51` inalcançável; falha real `validateTokenRevoke:49` → `aud`) quase fez o
  próximo corrigir o arquivo errado. Toda causa em ADR precisa do `file:line` do **caminho que
  executa**, não do que parece executar.
- **Resta:** SEC-02 sem arbitragem (401 na chain humana).

---

## PENDENTE 3 — baseline de suíte medido para `public-api-chat` (2026-10-09)

> `brain_ping`/`brain_store` **não foram tentados** — Brain MCP está FORA (HTTP 410 desde
> 2026-09-26). Registrado aqui para replay.

- **O quê:** BATCH 0 / task 0.1 da feature `public-api-chat`. Suite completa medida contra
  HEAD `e6d289e033efbeeb0fd1dbcd2fe6b9ffc4aabad8`. Resultado
  `.spec/backend/public-api/SUITE-BASELINE.md`.
- **Baseline canônico:** `371` unit / `75` IT / `189` frontend — 0 falhas, 0 erros, 0 skipped.
- **Destino quando o Brain voltar:**
  `layer=regras`, `path=hive/eng/testes/baseline-medido-vs-numero-de-planejamento`, `scope=projetos`
  tags: eng/testes, baseline, harness, h2.2, planejamento, metrica

### A lição

Os 3 artefatos de planejamento tinham **3 contagens diferentes** (376/76/158 · 505/118/222 ·
341/63/177) e **nenhuma era a real** (371/75/189). Nove números, nove errados, todos plausíveis.

> **Número de planejamento não é baseline.** Ele descreve o que o plano *acredita* que existe;
> baseline descreve o que a suíte *faz agora*. São grandezas diferentes e a segunda só existe
> depois de medida. Portão de entrada que adota número de plano como verde está afirmando verde
> sobre uma suíte que ninguém rodou.

### Achado correlato — a suíte NÃO depende do compose de dev

Os ITs rodam em `@ActiveProfiles("test")` com datasource e Redis injetados por
`@DynamicPropertySource` a partir de **Testcontainers** (`pgvector/pgvector:pg16` +
`redis:7-alpine`). O `docker-compose-vetor.yml` / `docker-compose-redis.yml` são necessários para
`spring-boot:run` e para H3 (browser real), **não** para a suíte. Subir compose para "passar a
suite" é trabalho na direção errada — e, neste batch, o `up` do Redis falhou com
`Conflict. container name "/hive-redis"` justamente porque o serviço **já estava no ar**.

### Armadilha de ambiente registrada (não tratada, não é falha de teste)

`backend/bootstrap/src/main/resources/application-dev.properties` está **sujo** na árvore
(modificação pré-existente de outro agente): datasource trocado de `localhost:5433`
(postgres/postgres) para `localhost:5432` (dev_user/dev_password). Não altera a suíte — o profile
`dev` não é carregado nos ITs — mas **quebrará `mvn spring-boot:run` no profile `dev`**, que
passará a apontar para o postgres de outro projeto, em `:5432`, com credenciais que o volume
`docker_postgres_data` pode não ter. Decisão do orquestrador.


---

## PENDENTE 4 — BATCH 1 `public-api-chat` (extração D4/D5) executado (2026-10-09)

> `brain_ping`/`brain_store` **não foram tentados** — Brain MCP está FORA (HTTP 410, 9a vez
> consecutiva). Registrado aqui para replay.

- **O quê:** tasks 1.1–1.8 do PLAN (`.spec/backend/public-api/PLAN.md`). Extração de
  `ProviderResolution` e `TurnOutcomeMapper`; `toProviderMessages` promovido a estático em
  `agent/api/util`; `isModelNotFound` + `ModelNotFound` (+ `MODEL_NOT_FOUND_PATTERN` +
  `isProviderModelErrorPayload`) mudados para `agent/api/util`; `PromptTemplateService.findByCodeAndVersion`.
- **Resultado:** `386` unit (baseline 371, +15) / `75` IT / `189` frontend — 0 falhas, 0 erros, 0 skip.
  E2E antes/depois com **igualdade byte a byte** do request ao provider (ver abaixo).
- **Destino quando o Brain voltar:**
  - `layer=arquitetura`, `path=hive/decisoes/public-api-batch1-extracao`, `scope=projetos`
    tags: hive, public-api, adr-014, refactor, d4, d5, gate-de-comportamento
  - `layer=regras`, `path=hive/eng/refactor/medir-o-fio-nao-o-trace`, `scope=projetos`
    tags: eng/refactor, e2e, prova-de-comportamento, proxy, telemetria

### A lição que o gate 1.6 comprou — meça o FIO, não o telemetria

O gate mandava comparar `systemBlockChars` (o que hoje é `promptChars` na linha de trace). O problema:
**essa métrica é da própria telemetria que estava sendo movida** — `recordTurn` saiu de
`ChatServiceImpl` e foi para `TurnOutcomeMapper`. Se a extração quebrasse a medição, a medida quebraria
junto e a prova diria "igual" sobre um prompt diferente.

A solução foi medir por fora: um proxy de captura (node:http, zero dependências) entre o backend e o
Ollama, com a credencial do provider apontando para `http://localhost:11500`. Ele encaminha intacto e
grava `sha256` + corpo de cada request. Assim a prova não depende de nenhuma linha do Hive.

```
antes: bytes=359 sha256=793153e1… promptSignature=471ef90d… systemChars=72 total=93
depois: bytes=359 sha256=793153e1… promptSignature=471ef90d… systemChars=72 total=93
trace: promptChars=93 promptMessages=2 historySize=1 maxTokens=512 temperature=0.7 (igual nos dois)
```

> **Regra: quando o refactor move a telemetria, a evidência de comportamento não pode ser a
> telemetria.** Meça o efeito no fio (request ao provider, DOM, arquivo gerado) ou por um caminho que
> o refactor não toca. Um teste que afirma o invariante pelo mesmo código que está sendo movido é
> tautologia — a 3a ocorrência da mesma família neste projeto (as outras duas: o filtro do PrimeVue e
> o `res.data as Page<T>`).

### Segunda lição — "igual ao baseline" sem métrica homônima exige tradução declarada

O gate falava de `systemBlockChars`, campo que **não existe mais** (morreu com o RAG). O sucessor é
`promptChars` (`ChatTrace` + `LoggingChatTraceRecorder:45`). Trocar a métrica sem dizer é publicar
"igual ao baseline" comparando grandezas diferentes. A tradução está declarada aqui e no relatório.

### Terceira — o método de prova por bloco

O gate pedia "git diff das linhas load-bearing mostra só movimentação". Em vez de ler o diff e
concordar, cada bloco foi extraído mecanicamente dos dois lados e diffado:

| bloco | resultado |
|---|---|
| 3 ramos do outcome (`ChatServiceImpl:203-246` → `TurnOutcomeMapper`) | idênticos, salvo `elapsedMs(startedAt)` → parâmetro `elapsedMs`, `isModelNotFound` pela nova casa (task 1.7) e reflow de 2 comentários/1 `log.error` |
| `recordTurn` + 4 helpers | idênticos (só reflow de assinatura) |
| `toProviderMessages` | idêntico (só `private static` → `public static`) |
| `isModelNotFound` / `isProviderModelErrorPayload` | idênticos (só visibilidade) |
| `MODEL_NOT_FOUND_PATTERN` | idêntico (só `static` → `private static`) |
| 3 leituras do provider (`ChatServiceImpl:176-186` → `ProviderResolution`) | idênticas (comentário virou javadoc) |

### Quarta — o que a extração decidiu, e o custo honesto

- `ChatServiceImpl` foi de 9 para 7 construtores e perdeu `recordTurn`, os 4 helpers e
  `toProviderMessages`. As linhas de log dos ramos de falha **mudaram de logger**
  (`ChatServiceImpl` → `TurnOutcomeMapper`); mensagem, nível e campos intactos. É o custo
  inevitável de mover um ramo em vez de copiá-lo.
- `chat(request, tenantId, userId)` já não lia ambiente — a task 1.4 era **preservar**, não criar.
  O que ela ganhou de prova: `when_chatIsDrivenByParameters_then_itReadsNothingFromTheEnvironment`,
  que roda um turno com `SecurityContextHolder` vazio.
- Achado para o batch 9 (não tratado aqui): o filtro de tenant do Hibernate
  (`TenantAspect` → `TenantContext.getCurrentTenant()`) **não** é satisfeito pelos parâmetros. Um
  chamador stateless da API pública precisa de `TenantContext.runWithTenant(...)` para o isolamento
  de banco, mesmo passando `tenantId` por argumento.
