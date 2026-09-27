# UX Review — Assistant (fluxo completo)

> Parecer de UX/visual. ** vinculante** para o `developer-engineer`.
> Sem código. Base visual: `docs/design/DESIGN_SYSTEM.md` (PrimeVue Aura, Deep Indigo + Amber, `.p-dark`).
> Stack: Vue 3 `<script setup lang="ts">` + PrimeVue 4.

---

## 0. Conclusão executiva

O loop elementar funciona. O que não funciona é o **ciclo de vida em volta dele**: o produto mudou de
"Agent" para "Assistant" há 2 dias e a interface ainda fala a língua do produto antigo. Há 4 achados
🔴 que não são polimento — são promessas que a tela faz e o produto não cumpre (anexo, voz, busca no
seletor, "Configurações"), e 1 lacuna estrutural (não existe estado de primeira-vez).

Ordem de implementação recomendada: **F1 (prontidão) → F2 (seletor) → F3 (navegação) → F4 (achados)**.
F1 destrava a primeira impressão; F2 é o que fecha o P2 registrado; F3 é o que faz F1 ser
descobrível; F4 é dívida de consistência.

---

## 1. `PromptSelector` — teto de 50 e ausência de busca

### 1.1 Diagnóstico (evidência)

- `PromptSelector.vue:93` pede `size: 50`. `PromptTemplateControllerV1.java:70-72` rejeita `size > 50`
  com 400. O teto é do contrato do servidor, não uma escolha de UI.
- `PromptSelector.vue:23` já liga `filter` do PrimeVue `Select`. Esse filtro é **client-side sobre os 50
  já carregados**. Acima de 50 itens, ele **parece** buscar e nunca encontra o #51. É pior que não
  ter busca: cria confiança falsa. → 🔴
- O backend **já suporta busca**: `GET /v1/prompt-templates?page&size&q&status`, com
  `findByTenantIdWithSearch(tenantId, q, pageable)`
  (`PromptTemplateRepository:37-41`) e ramo com status (`PromptTemplateServiceImpl:197-206`).
  **Zero trabalho de backend.** O serviço do frontend (`prompt.service.ts:34-40`) já aceita `q`.
- `frontend/src/composables/useDebouncedSearch.ts` já existe e está **sem uso**.

### 1.2 Opção recomendada

**Busca com digitação (lazy) contra o servidor + fuga para a tela de Prompts. Sem paginação no seletor.**

Justificativa:
- Paginação dentro de um dropdown é navegação que quebra o contexto (anti-pattern
  `modal-vs-navigation`): o usuário abre o seletor, vira página, perde o que já leu, e a página 2 só
  aparece se ele rolar até o fim. Custa mais atrito do que resolve.
- Com `q=` no servidor, o seletor deixa de ter teto: a lista carregada deixa de ser "o universo" e
  vira "os recentes". Isso também dá um significado honesto ao `size=50` — 50 *recentes*, não 50 *de 50*.
- A tela de Prompts continua sendo o caminho de manutenção (listar tudo, ordenar, arquivar), e o
  seletor passa a ter um link explícito para lá. Nenhum dos dois vira o único caminho.

### 1.3 Comportamento por estado

O seletor tem **dois modos**, e o usuário precisa saber em qual está:

| Modo | Gatilho | Conteúdo do dropdown |
|---|---|---|
| **Recentes** | Abre sem digitação | 50 mais recentes. Cabeçalho: `Prompts recentes` |
| **Busca** | ≥ 2 caracteres digitados | `size=20`, `q=<termo>`. Cabeçalho: `Resultados da busca` |

| Estado | Comportamento visual |
|---|---|
| **Carregando (abre)** | Skeleton de altura do campo (1 linha, `border-radius: 12px`). **Comportamento atual está correto** — manter. Não usar spinner no trigger. |
| **Carregando (busca)** | As 20 opções antigas permanecem visíveis, esmaecidas (`opacity: 0.5`). Sem skeleton: esqueleto dentro de um dropdown aberto causa salto de layout e esconde a lista. `aria-busy="true"` no container. |
| **Vazio (sem nenhum prompt no tenant)** | Estado próprio, **não** uma mensagem inline. Ver §2.3 — não é um erro do seletor, é falta de setup. Nada de `Select` renderizado. |
| **Busca sem resultado** | Linha no dropdown: `Nenhum prompt para "assunto"` + abaixo, 2 ações: `Criar prompt "assunto"` (→ `/prompts/new`) e `Ver todos os prompts` (→ `/prompts`). Nunca `0 resultados`. Anti-pattern `Search/No Results`: dead end é proibido. |
| **Muitos resultados (> 20 no filtro)** | Rodapé fixo no dropdown, sempre presente: `Ver todos os N prompts →`. É o que torna o teto **legível** em vez de silencioso. `N` vem de `totalElements` da resposta. |
| **Muitos prompts no tenant, sem busca** | Idem: rodapé `Ver todos os N prompts →`. O usuário descobre que existem mais, em vez de inferir que não existem. |
| **Erro de carga inicial** | **Comportamento atual é 🔴 e precisa mudar.** Hoje o `Select` inteiro é substituído por `Message` vermelha. Se há sessão aberta, o usuário **perde de vista qual prompt está respondendo** — a conversa segue com um prompt invisível. Novo: manter o `Select` renderizado e desabilitado, com uma `Message` de erro **acima** do campo e um `Button` text `Tentar novamente` ao lado. O valor selecionado continua visível. |
| **Erro durante a busca** | Não substitui a lista. Mantém as 20 opções, mostra `Não foi possível buscar. Tentar novamente` como **rodapé** do dropdown, com o texto anterior ainda na caixa. |
| **Sessão aberta (locked)** | Ver §1.4. |
| **Selecionado** | Valor renderizado: ícone `pi-file-edit` + nome. Adicionar `pi-lock` quando travado. |

### 1.4 Seletor travado por sessão

Hoje `:disabled="!!sessionId"` sem nenhuma explicação. O usuário não sabe se está quebrado ou travado,
e a regra RN-PT2 (prompt imutável por sessão) é invisível.

- Ícone `pi-lock` à direita do valor selecionado, `aria-label="Prompt fixo nesta conversa"`.
- `Tooltip` no campo: *"O prompt fica fixo nesta conversa. Para usar outro, inicie uma nova conversa."*
- **Não** usar `severity="secondary"` esmaecido sem explicação. Desabilitado + cadeado + tooltip é
  affordance; desabilitado sozinho é bug.

### 1.5 Anatomia da opção (com 50 itens, `code` é o discriminador)

`PromptsView` mostra `name`, `code`, `status`, `tags`, `createAt`. O seletor mostra só `name` +
`description`. Com 50 itens isso não basta.

Layout da opção, 2 linhas, hierarquia por peso (não por cor):

```
┌──────────────────────────────────────────────┐
│ Resumir documento            [ACTIVE]        │  ← nome: font-semibold, text-sm
│ summarize-meeting · Relatórios               │  ← code em mono + description, text-xs, text-surface-500
└──────────────────────────────────────────────┘
```

- `name` — `text-sm font-semibold`, `--p-text-color`
- `code` — `font-mono text-xs`, `--p-surface-500` (monoespaçado é o que distingue um identificador de
  um título)
- `description` — `text-xs text-truncate`, **1 linha** com ellipsis, `title` para o texto completo
  (regra `Truncation`: truncar com ellipsis + expandir, nunca cortar layout)
- `status` — `Tag` apenas quando **≠ ACTIVE** (DRAFT / ARCHIVED). Mostrar `ACTIVE` em todas as 50 é
  ruído (Hick); mostrar `ARCHIVED` é informação crítica — o usuário está escolhendo um template
  arquivado sem saber.
- Item selecionado: `pi pi-check` à direita, `color: var(--p-primary-color)`. O PrimeVue já faz pelo
  `highlight`, mas **sem** check fica ambíguo em lista com descrição.

### 1.6 Acessibilidade do seletor

- Filtro com ≥ 2 chars: também aceitar busca por `code` (o backend já faz — ver query do repository).
- Teclado: `↑`/`↓` navegam, `Enter` seleciona, `Esc` fecha **preservando a busca digitada**,
  `Backspace` com campo vazio volta ao modo Recentes.
- `aria-live="polite"` no cabeçalho do dropdown anunciando `12 resultados` / `Nenhum resultado`.
- Alvos: o campo e o rodapé do dropdown ≥ 44px de altura. O rodapé `Ver todos` hoje seria uma linha
  de texto — **não** pode ser.

---

## 2. Estados vazios e de primeira-vez

### 2.1 Resposta direta: é um **gate**, não um erro

Credencial ausente não pode ser erro. Mas também não pode ser fingido: o chat não pode parecer
pronto e falhar no clique. As duas coisas que hoje estão erradas são:

1. Não há **nenhuma** detecção preventiva — o app sabe que falta tudo e deixa o usuário descobrir
   enviando uma mensagem.
2. Quando o erro chega, ele vira **toast** (some em 8s) ou **desculpa genérica**. Uma tarefa de setup
   não é um evento de 8 segundos.

**Regra:** o chat faz um **pre-flight** no mount. Faltando pré-requisito, o estado de setup aparece
**no lugar da conversa vazia** — não como erro, não como toast.

### 2.2 Sequência de primeira-vez (ordem causal, não alfabética)

A ordem não é "o que falta" — é **o que precisa ser decidido antes do quê**:

```
1. Provedor e modelo   → qual empresa responde, qual modelo      (define a chave necessária)
2. Credencial          → a chave daquele provedor específico      (não existe sem o passo 1)
3. Prompt template     → a intenção da conversa                  (independe de infra)
```

O passo 1 antes do 2 porque o usuário **não sabe qual chave colar** antes de escolher o provedor. Na
tela de credenciais, o provider escolhido em `/settings/llm` precisa estar **pré-selecionado** e
visível. Hoje não está: o usuário sai de "Modelo do tenant" remembering `OPENCODE_GO`, chega em
"Provedores", e a lista é alfabética com 5 vendors.

> **Requisito de integração:** `/settings/providers?provider=<AI_PROVIDER>` — deep-link por query
> param, abrindo o diálogo de edição do vendor. Não é conceito novo, é deep-linking
> (regra `deep-linking`).

### 2.3 O painel de prontidão (substitui a ilustração de boas-vindas enquanto incompleto)

Aparece **somente** quando `sessionId == null && messages.length == 0`. Se há sessão no
`localStorage` com histórico, o usuário está voltando — o painel **não** aparece. Regra de onboarding:
nada de tutorial forçado, nada de tour (anti-pattern `Onboarding/User Freedom`).

```
┌───────────────────────────────────────────────────────────────┐
│  Para conversar, este tenant precisa de três coisas.           │  ← text-sm, text-surface-500
│                                                               │
│  ○  Provedor e modelo      Qual modelo responde pelas         │  ← hórus vazio = pendente
│     conversas deste tenant.                                    │     linha 2: por quê importa
│     [ Configurar ]  → /settings/llm                           │  ← CTA primário da linha
│                                                               │
│  ○  Credencial             A chave de API do provedor          │
│     escolhido.                                                │     botão **text** +
│     [ Adicionar chave ]  → /settings/providers?provider=…     │     seta no label
│                                                               │
│  ○  Prompt template        A intenção que guia a resposta      │
│     desta conversa.                                           │
│     [ Criar prompt ]  → /prompts/new                          │
│                                                               │
│  ─────────────────────────────────────────────────────────────│
│  O chat é liberado quando os três estiverem prontos.           │  ← text-xs, text-surface-500
└───────────────────────────────────────────────────────────────┘
```

- **Sem ilustração, sem "Sua orquestração inteligente"** (ver F10). O painel é informativo, não decorativo.
- **Sem botão global de continuar.** Nada de wizard. O usuário faz o que quer, na ordem que quiser.
  Um único botão primário no painel seria uma mentira: não existe "próximo passo" único.
- Cada linha é independente. Concluir uma não muda as outras.
- **Token:** `border-1 border-dashed border-surface-200 rounded-2xl bg-surface-0` — o mesmo
  esqueleto de `AdminEmptyState.vue:15`. Consistência de estado vazio em todo o produto.
- Ícone de estado: `pi pi-circle` (pendente) preenchido, `pi pi-check-circle` (concluído) em
  `--p-primary-color`. **Nunca só cor** (regra `color-not-decorative-only`) — forma diferente.
- Linha concluída: colapsada, `opacity: 0.6`, sem CTA. Se as 3 estiverem prontas, o painel não
  renderiza e a boa-vinda volta.
- `max-width: 34rem`, centralizado. `aria-live="polite"` no container: cada linha que resolve é
  anunciada.

### 2.4 Pre-flight — o que checar, e onde cada bloqueio é detectado

Duas leituras, ambas já existentes, sem endpoint novo:

| Fonte | Chamada | Pergunta que responde |
|---|---|---|
| `GET /v1/tenant/llm-config` | já usada por `LlmConfigView.vue:165` | `null` → passo 1 pendente |
| `GET /v1/agent-admin/credentials` | já usada por `CredentialsView.vue:62` | o vendor do passo 1 está `configured`? → passo 2 |
| `GET /v1/prompt-templates?page=0&size=1` | `prompt.service.ts:34` | `totalElements === 0` → passo 3 |

> ⚠ **Armadilha de contrato — validar antes de codar o pre-flight.**
> `TenantLlmConfigControllerV1.java:56-60` responde `buildSuccessResponse(null)` quando não há config.
> `agent-admin.service.ts:24-28` (`unwrapData`) só retorna `null` se o wrapper serializar
> **`"data": null`**. Se o wrapper omitir o campo (`@JsonInclude(NON_NULL)`), `d.data === undefined` →
> o `unwrapData` devolve o **wrapper inteiro**, que é truthy — e `LlmConfigView.vue:167-172` marcaria
> um tenant sem configuração como `configured = true`, com `provider`/`model` `undefined`.
>
> **O pre-flight NÃO pode testar truthiness do objeto.** Testar a **presença do conteúdo**:
> `Boolean(config?.provider && config?.model)`. Isso é robustez de leitura, não contorno: um payload
> de 200 que não tem o que o cliente precisa é "ausente", e o painel trata os dois igual.
> Vale a mesma regra em `LlmConfigView.vue:167`.

- Só o passo 3 é problema do usuário. Passos 1 e 2 são **administração** — o painel deve indicar
  quem resolve, sem blame. Copy: `Um administrador precisa configurar…` quando o usuário não tem
  `AGENT_MANAGE`.
- Falha de rede no pre-flight **não** bloqueia o chat. Erro de leitura ≠ ausente. O painel só
  aparece com dadospositivos de ausência. Na dúvida, o chat abre normalmente e deixa o servidor
  recusar — que é o comportamento de hoje, e é aceitável.
- Rodar no mount, sem cache. 3 GETs baratos em 2 endpoints. Recarregar após o retorno de cada CTA
  (via `onActivated` do router ou `window` focus) resolve o caso de "configurei e voltei".

### 2.5 Sem credencial e o usuário tenta conversar

Três casos, três comportamentos distintos:

**a) App sabe que falta (pre-flight rodou).**
O composer fica **desabilitado** com motivo **inline e persistente**, não toast:

```
┌──────────────────────────────────────────────┐
│ ⚠ Falta a chave de API do provedor OpenAI.    │  ← Message severity="warn", persistent
│   [ Configurar agora ]                        │     (não toast, não some)
├──────────────────────────────────────────────┤
│  Escreva sua mensagem…              [ ➤ ]    │  ← composer desabilitado, opacity 0.5
└──────────────────────────────────────────────┘
```

O botão é um **convite**, não um erro: severidade `warn`, ícone de chave, verbo de ação
("Configurar agora"), e o caminho é único e conhecido. Resposta à pergunta: **convite**, e o
convite é um painel, não um toast.

**b) App não sabia (sessão do `localStorage`, config deletada depois, race).**
422 `TENANT_LLM_CONFIG_NOT_FOUND` ou 422 `CONFIG_MISSING` → **o painel de prontidão abre inline no
lugar da conversa**, com a linha culpada destacada e focada. **Não** toast, **não** a bolha
"Desculpe, ocorreu um erro".

- `TENANT_LLM_CONFIG_NOT_FOUND` → destaca a linha 1
- `CONFIG_MISSING` → destaca a linha 2, e o texto **nomeia o vendor** que falta
  (o backend já resolve o par tenant+vendor; o `provider` vem no detalhe)

Isso vale porque as duas condições são **ações administrativas distintas** e hoje colapsam no mesmo
`else` genérico (`Chat.vue:224-233`).

**c) Falha desconhecida.**
Só aí: toast `severity="error"` + **uma** mensagem de sistema no fluxo, visualmente distinta de uma
bolha do assistente (ver F3). Sem "Desculpe".

### 2.6 Correção de texto do backend (make-it-binding)

`TenantLlmConfigNotFoundException.java:27` diz `"...Configure em Configurações > Provedores..."`.
A tela que corrige o default de LLM é `/settings/llm` ("Modelo do tenant"). **"Provedores" é a tela
de credenciais** — o erro aponta para a tela errada, e para uma condição diferente.

Duas correções, ambas necessárias:
1. **FE (autoritativa):** o `code` do erro decide **para onde** o CTA leva. O `detail` do servidor
   decide apenas **o que** o texto diz. O `LLM_CONFIG_NOT_FOUND_TITLE` (`chat-messages.ts:27`) já
   está certo.
2. **BE:** corrigir a string para `Configurações > Provedor e modelo`. Cuidado: 4 testes casam
   `"Configurações > Provedores"` por substring —
   `TenantLlmConfigNotFoundException` (a asserção é `hasMessageContaining("Configurações > Provedores")`,
   que **passa** com "Provedor e modelo"? Não: `"Provedor e modelo"` não contém `"Provedores"`.
   Verificar os 4 arquivos antes de mudar:
   `TenantLlmConfigServiceImplTest:66`, `ChatServiceImplTest:215`, `TenantLlmConfigIT:193`.

---

## 3. Navegação

### 3.1 Resposta: não, 4 itens no mesmo peso não é a IA certa

A navegação atual commite um erro de **custo**: dá peso de "ação 300× por dia" a "ação 1× por
tenant".

- **Chat** — 300+ vezes por semana, a única razão de o produto existir
- **Prompts** — centenas de vezes, e é a tela que dá sentido ao prompt escolhido
- **Provedores** — 1 vez, e por um administrador
- **Modelo do tenant** — 1 vez, e por um administrador. É a que **destrava o chat** e a menos
  descoberta. Engrenagem cinza no mesmo plano dos outros três.

Regra aplicável: `nav-hierarchy` (MD) — navegação primária e configuração precisam estar
**visualmente separadas**. E `Cost-Benefit in Navigation`: o peso visual de um item deve refletir
frequência × consequência.

O detalhe crítico: **a tela mais necessária está no mesmo plano dasadministration-only.** Se
`/settings/llm` morresse, o produto inteiro morre — e é a última da lista.

### 3.2 Navegação recomendada

```
┌──────────────────────────┐
│  ‹                       │  ← toggle (mantido)
│                          │
│  💬  Chat                │  ← primário
│  ⚡  Prompts             │  ← primário
│  ────────────────────────  ← divider 1px, --p-content-border-color
│  ⚙  Configurações    ›  │  ← grupo, chevron à direita
│  ────────────────────────  ← divider
│  📖  Documentação        │  ← rodapé (mantido)
└──────────────────────────┘
```

Expandido:

```
│  💬  Chat
│  ⚡  Prompts
│  ────────────────────
│  ⚙  Configurações    ⌄
│     🔑  Provedores e chaves     → /settings/providers
│     🤖  Provedor e modelo        → /settings/llm
```

### 3.3 Justificativa por item

| Decisão | Razão |
|---|---|
| 2 primários + 1 grupo | O menu de 4 itens iguais diz "estas 4 telas são equivalentes". Não são: 2 são conteúdo, 2 são infraestrutura. |
| **Grupo inline, não item de rodapé** | Colocar "Configurações" no rodapé (onde já vive "Documentação") *esconde* a tela que o §2 precisa que seja descoberta. Um botão `⚙` na topbar seria pior. O grupo inline mantém visível, remove a falsa paridade, e é o menor diff: reaproveita `.nav-item`, `router-link`, `activeSection`. |
| **Divider entre primários e grupo** | É a separação hierárquica (Lei de Prägnanz — proximity gera agrupamento). Sem o divider, 5 linhas com pesos diferentes é ilegível. |
| Chevron `›`/`⌄` no pai | Afordância de que o item é expansível. Sem chevron, parece link. |
| **Renomear "Modelo do tenant" → "Provedor e modelo"** | "tenant" é jargão de produto em menu de usuário final. O `h1` da tela (`LlmConfigView.vue:5`) **já** diz "Provedor e modelo do tenant" — o menu e a tela discordam. Alinhar pelo mesmo nome recupera a concordância (Nielsen #6: reconhecer em vez de lembrar). |
| **"Provedores" → "Provedores e chaves"** | "Provedores" sugere um catálogo de empresas. A tela é sobre chaves. O `h1` diz "Provedores de IA" e o subtítulo (`CredentialsView.vue:137`) já fala em chaves. |
| Ícone do grupo | `pi pi-cog` (mantido). **Preferir `pi pi-sliders-h`/`pi pi-wrench`** — `pi-cog` é o mesmo ícone do item morto "Configurações" da topbar e do botão morto do `ChatSidebar`; manter é ambíguo. Escolher **um** ícone e apagar os outros dois (F13, F14). |

### 3.4 Estados do grupo

| Estado | Comportamento |
|---|---|
| Fechado (padrão) | Chevron `pi-chevron-right`. Auto-expande se a rota atual é `/settings/*`. |
| Aberto | Chevron `pi-chevron-down`. Filho ativo com o mesmo tratamento de `.nav-item.active`. |
| Filho sem permissão | `AGENT_MANAGE` ausente → filho **desabilitado**, `pi-lock` + `Tooltip` "Requer permissão de administrador". **Não** redirect silencioso (ver F15). |
| Sidebar colapsada (68px) | Só o pai aparece, ícone `pi-cog`. Clicar no pai **expande a sidebar** e depois abre o grupo — não abrir um flyout sobre uma coluna de 68px. |
| Mobile (< 768px) | Mesmo comportamento de colapsada, via `sidebar-mobile`. Overlay já existe (`MainLayout.vue:117-121`). |

### 3.5 O que **não** muda

- **Nenhuma rota muda.** `/settings/providers` e `/settings/llm` continuam válidas e deep-linkáveis.
- **Breadcrumb não muda** (exceto o rótulo "Home" — F12).
- **Nenhum item sai do menu.** A restrição de permissão vira *desabilitado com motivo*, não
  *escondido* — esconder a tela de admin para o não-admin quebraria o §2.3.
- `TheSidebar` não ganha sub-componente novo: uma flag `group` no `NavItem` + `children` opcional,
  e o estado `expandedGroup` no próprio componente. A prop `items: NavItem[]` do
  `DESIGN_SYSTEM.md §2.3` **precisa ser atualizada junto** — o contrato documentado muda.

---

## 4. Achados de UX/visual

Severidade: 🔴 prejudica o uso · 🟡 atrapalha · 🟢 polimento

### 🔴

| # | Onde | Achado | Spec |
|---|---|---|---|
| **F1** | `ChatInput.vue:16-31` | Botão **anexar arquivo** (tooltip "Anexar arquivo") e **Comando de voz** (tooltip "Comando de voz"): sem handler, sem `disabled`, sem `aria-disabled`. Duas affordances mortas anunciando features deliberadamente removidas. `Chat.vue:168-171` ainda rejeita >4000 chars — o clipe sugere um caminho de upload que não existe. | **Remover ambos.** Não desabilitar, não esconder: um botão de clipe que não faz nada é pior que um botão de clipe que não existe. Se o produto quiser reanexar depois, ele volta — com backend. |
| **F2** | `ChatInput.vue:70-75` | Tags "Resumir documento / Analisar código / Gerar relatório / Explicar conceito". Hardcoded, ignoram o prompt selecionado, e auto-resposta (`@click="modelValue = suggest"`) — preenchem o campo com texto cuja resposta virá de um prompt que nada tem a ver. **Contradizem a única regra do produto** (você escolhe o prompt). São herança da era do Agent. | **Remover a fileira inteira.** Não há versão corrigível: qualquer tag fixa contradiz "o prompt é escolhido por você". Substituir por nada — o campo vazio com placeholder é a resposta honesta. |
| **F3** | `Chat.vue:229-233` | `"Desculpe, ocorreu um erro ao processar sua mensagem."` — viola a regra do Design System ("erros não se desculpam; explicam e indicam a correção") **e** é empurrada com `role: 'assistant'`, ou seja, **o modelo parece ter dito isso**. Um erro de sistema vestindo a máscara do assistente. | Mensagem de sistema com estilo próprio (ver §5.4), nunca com `role="assistant"`. Verbo factual + causa + caminho. |
| **F4** | `TenantLlmConfigNotFoundException.java:27` | A mensagem nomeia `Configurações > Provedores`; a tela que corrige é `/settings/llm` = "Modelo do tenant". **O erro aponta para a tela errada e para a condição errada.** `LlmConfigView.vue:6-9` afirma o oposto ("o erro diz exatamente esta tela") — a tela e o contrato estão desalinhados. | §2.6. |
| **F5** | `Chat.vue:224-233` | 422 `CONFIG_MISSING` (sem credencial) cai no `else` genérico → toast "Erro" + bolha de desculpa. Duas ações administrativas distintas colapsam no mesmo beco sem saída. O código existe e é distinto (`SYSTEM_SPECIFICATION.md:99`). | §2.5(b). Painel de prontidão, linha 2 destacada. |
| **F6** | `PromptSelector.vue:6-9` | Em erro de carga, o `Select` é **substituído** por `Message` vermelha. Com sessão aberta, o usuário perde de vista qual prompt responde. | §1.3 — manter o campo, erro acima. |
| **F7** | `PromptSelector.vue:23` | `filter` client-side sobre 50 itens: parece buscar, nunca acha o #51. Busca que mente. | §1.2 — busca server-side. |
| **F8** | `PromptSelector.vue:21` | Desabilitado quando há sessão, sem cadeado, sem tooltip, sem motivo. O usuário não sabe se está quebrado ou travado. | §1.4. |
| **F9** | `PromptSelector.vue:10-12` | "Nenhum prompt criado ainda. Crie um em **Prompts**" — "Prompts" é `<strong>`, não é link. Dead end. E o composer continua habilitado, inviting o envio que vai falhar. | §2.3 linha 3. |
| **F10** | `Chat.vue:42`, `PromptsView.vue:118`, `CredentialsView.vue:236`, `PromptsView.vue:81` | Copy da era do Agent: "orquestração inteligente", "acelerar seus fluxos de trabalho", "orquestrar agentes", "Agentes desse provedor falharão com 422", "Prompt em uso por agente". O produto foi renomeado há 2 dias; as palavras não foram. `PromptsView.vue:81` ainda contradiz o servidor, que hoje diz "em uso por uma sessão de chat" (`SYSTEM_SPECIFICATION.md` RN-PT1). | Reescrever os 4. O 409 deve usar o `detail` do servidor, como `Chat.vue:246-254` já faz corretamente. |
| **F11** | `Chat.vue` (ausente) | Não existe estado de primeira-vez. Tenant novo → conversa vazia + seletor vazio + envio → 422. | §2. |

### 🟡

| # | Onde | Achado | Spec |
|---|---|---|---|
| **F12** | `MainLayout.vue:54` | Breadcrumb raiz é `Home` → `/` → redirect para `/chat`. A trilha diz `Home > Chat` numa tela que **se chama** Chat, e `Home` não existe como destino. | Rótulo `Chat` (não `Home`) + ícone `pi-comments`, `to: '/chat'`. |
| **F13** | `TheTopbar.vue:45` | Item `Configurações` no menu do usuário **sem `command`** → item morto. | Apontar para o grupo: `command: () => router.push('/settings/llm')`, ou remover. Com F3 (§3.3) haverá **um** `⚙` no produto, não três. |
| **F14** | `ChatSidebar.vue:80` | Botão `Configurações` no rodapé do drawer, **sem handler** → item morto. Terceiro `Configurações` morto. | **Remover.** O drawer do chat é para histórico, não configuração. |
| **F15** | `MainLayout.vue:43-48` | Itens de nav não são filtrados por permissão, mas as rotas são (`router/index.ts:94-96`). Sem `AGENT_MANAGE`, clicar em "Modelo do tenant" → **redirect silencioso para o chat**, sem explicação. Pior que esconder. | §3.4 — desabilitado + cadeado + tooltip. |
| **F16** | `TheTopbar.vue:60-68` | Toggle de tema não persiste. `.p-dark` se perde no reload. O DS afirma dark mode via `.p-dark`; um toggle que reseta é bug, não preferência. | `localStorage('hive_theme')`, ler no boot antes do mount do PrimeVue. 🟢 escopo: 5 linhas. |
| **F17** | `TheTopbar.vue:137` vs `DESIGN_SYSTEM.md §1.3` | Topbar `72px` no CSS, token documentado `64px`. `Chat.vue:283` computa `max-height` com `var(--topbar-height)`. **Token e código discordam hoje.** | Escolher **72px** (o que está no DOM e o que o layout assume) e corrigir `DESIGN_SYSTEM.md`. Não mexer na matemática nesta passada. |
| **F18** | `TheTopbar.vue:85` | Breadcrumb `hidden md:block` → sem orientação nenhuma no mobile. | Manter oculto no mobile (a sidebar é a nav), **mas** o `h1` de cada tela já existe e é o suficiente. Não resolver. 🟢 |
| **F19** | `ChatInput.vue:45` | `Tag` clicável como controle: sem `role`, sem `tabindex`, sem handler de teclado. Mouse-only. | Sem objeto se F2 for aplicado (fileira removida). Se sobreviver: `role="button"` + `tabindex="0"` + Enter/Space. |
| **F20** | `Chat.vue:67` | `ChatInput :disabled="sending || loadingHistory"` — o composer inteiro (textarea + tags) desabilita durante o envio. O usuário não consegue deixar a próxima pergunta escrita. | Desabilitar **apenas o botão enviar**, não o textarea. O texto fica digitável durante o envio e é enviado ao terminar (ou descartado com aviso). Decisão de produto pequena — recomendo **permitir digitar, não enviar** nesta passada. |
| **F21** | `ChatInput.vue:36` | Botão enviar desabilitado com campo vazio, sem qualquer estado visual que diferencie "vazio" de "ocupado". | `severity="secondary"` + `opacity: 0.5` + `cursor: not-allowed` quando desabilitado; mantém `--p-primary` quando pronto. Só estado, sem texto. |
| **F22** | `ChatInput.vue:93-98` | `:focus-within { transform: translateY(-4px) }` — **o composer se move ao receber foco**. Shift de layout no controle mais usado da tela, mais `box-shadow` de `0.25` que escurece as bordas. E `prefers-reduced-motion` não é respeitado em lugar nenhum do projeto. | Trocar por **cor**: `border-color: var(--p-primary-color)` + sombra suave, sem transform. Adicionar `@media (prefers-reduced-motion: reduce) { * { animation: none; transition: none } }` global. |
| **F23** | `Chat.vue:172-176` | "Escolha um prompt" como toast de 3s, com o alvo a ~900px de distância no cabeçalho (Lei de Fitts). | Desabilitar o envio + `Message severity="warn"` **inline acima do composer**, persistente, com CTA `Escolher prompt` que foca o seletor. |
| **F24** | `Chat.vue:68-70` | Disclaimer "Hive AI pode cometer erros" em `text-surface-400` — abaixo de 4.5:1 sobre `--p-content-background` branco. | `text-surface-500` (ou `text-surface-600`). Mesma regra vale para `ChatSidebar.vue:22` (`Histórico Recente` em `text-surface-400`) e `ChatSidebar.vue:36`. |
| **F25** | `PromptSelector.vue:118-120` | Hover da borda em `--p-primary-400`. Em light mode o primário é `indigo-900`; `primary-400` é token de dark mode. Hover quase invisível no tema claro. | Usar `var(--p-primary-color)` com `opacity`/mistura, ou `--p-primary-300` validado contra `--p-content-background` nos dois temas. Validar com o medidor de contraste do DevTools. |
| **F26** | `TheSidebar.vue:98-101` | `transform: translateX(4px)` no hover de item de nav — **hover desloca layout** e desloca *para a direita* numa nav ancorada à esquerda (lê como "entrando"). Sem `prefers-reduced-motion`. | `background-color` + `color` apenas. Remover o transform. |
| **F27** | `TheSidebar.vue` / `TheTopbar.vue` | **Nenhum `:focus-visible`** em nenhum elemento interativo. `DESIGN_SYSTEM.md §3` exige explicitamente. Navegar por teclado no chat é uso real (Enter envia, Esc fecha). | Regra global: `:focus-visible { outline: 2px solid var(--p-primary-color); outline-offset: 2px; border-radius: 8px }`. |
| **F28** | `PromptSelector.vue:13-40` | A opção mostra `name` + `description`. Sem `code`, sem `status`, sem data. Com 50 itens e nomes parecidos ("Resumir", "Resumir documento", "Resumir contrato"), a distinção é por `description` — o campo menos estável dos três. | §1.5. |
| **F29** | `PromptSelector.vue:93` | Sem filtro de `status`: `DRAFT` e `ARCHIVED` são selecionáveis e indistinguíveis de `ACTIVE`. | §1.5 — `Tag` quando ≠ ACTIVE. |
| **F30** | `PromptsView.vue:104-105` | Busca só dispara em `Enter` ou no botão. `useDebouncedSearch` existe e não é usado. | Debounce 400ms. A tela tem paginação server-side, então o padrão de busca muda aqui e é o mesmo padrão do §1. |

### 🟢

| # | Onde | Achado |
|---|---|---|
| **F31** | `Chat.vue:38-40` | O bloco de boas-vindas é `bg-primary` com `rotate-12` + bolt amber. É o único uso de amber como **decoração** — o DS reserva amber para **ação/destaque**. A rotação de 12° num quadrado é arbitrária e não comunica nada. Substituir pelo painel §2.3 (quando incompleto) ou por um empty state `border-dashed` no padrão de `AdminEmptyState`. |
| **F32** | `ChatSidebar.vue:8` + `TheTopbar.vue:81` | "Hive AI" aparece **duas vezes na tela** (topbar + drawer). O drawer repete a marca para se posicionar. Aceitável; com F13/F14 o rodapé do drawer fica mais leve. |
| **F33** | `TheFooterbar` (`MainLayout.vue:138-140`) | Links "Documentação" e "Suporte" com `href="#"` — clicáveis, não vão a lugar. Enquanto forem `#`: `severity="secondary"` + `disabled`, nunca parecer clicáveis. |
| **F34** | `CredentialsView.vue:139` | Estado de carregamento é o texto `"Carregando..."` — único lugar do projeto sem skeleton, ao lado de um `<div>` com 5 cards que saltam no layout quando chegam. `Skeleton height="4rem" × 5` com o mesmo `rounded` dos cards. |
| **F35** | `CredentialsView.vue:164-169` | `value="Configurada ✅"` / `value="Faltante ⚠️"` — **emoji dentro de `Tag`** (anti-pattern `no-emoji-icons`; emoji é dependência de fonte e não respeita token). Ícones `pi-check-circle` / `pi-exclamation-triangle` + texto, ou só texto. |
| **F36** | `Chat.vue:59` | `"Hive está processando..."` — itálico + `text-xs` + "processando" sugere trabalho pesado. O que ocorre é uma chamada HTTP. `"Respondendo..."` é mais honesto e mais curto. |

---

## 5. Especificação transversal de componentes

### 5.1 Estados interativos (obrigatório em todo elemento novo)

`DESIGN_SYSTEM.md §3` já exige `:hover`, `:active`, `:focus-visible`. Especificação por componente:

| Componente | default | hover | active | focus-visible | disabled | loading |
|---|---|---|---|---|---|---|
| `.nav-item` (Sidebar) | `text-surface-600` | `bg-surface-100` + `text-surface-900`, **sem transform** | `bg-surface-200` | outline 2px `--p-primary-color` offset 2px | — | — |
| `.nav-item.active` | `bg: var(--p-primary-color)`, `color: var(--p-primary-contrast-color)` | `filter: brightness(1.08)` | `brightness(0.95)` | idem | — | — |
| Item de grupo | `pi-cog` + chevron `text-surface-400` | `color: var(--p-primary-color)` no chevron | — | outline | `opacity: 0.4` + `cursor: not-allowed` | — |
| Filho desabilitado | `opacity: 0.4` + `pi-lock` | **sem hover** | — | — | — | — |
| `Select` do prompt | `border-color: var(--p-content-border-color)` | `border-color: var(--p-primary-color)` (não `primary-400` — F25) | — | `:focus-visible` do PrimeVue **não removido** | `opacity: 0.5` + cadeado quando sessão | Skeleton no lugar do campo |
| Botão enviar | gradiente indigo + `pi-send` | `brightness(1.08)`, **sem `scale(1.05)`** (F22) | `brightness(0.95)` | outline 2px `--p-primary-color` | `severity="secondary"`, `opacity: 0.5`, `cursor: not-allowed` | `pi-spin pi-spinner`, `aria-busy` |
| Linha do painel de prontidão | `border-dashed border-surface-200` | `bg-surface-50` + borda sólida | — | outline no botão | linha concluída `opacity: 0.6` | `pi-spinner` no botão da CTA |
| Rodapé do dropdown | `text-sm text-primary` | `bg-surface-100` | — | outline, altura 44px | — | — |

### 5.2 Movimento

- Duração **150–300ms**. `ease-out` entrando, `ease-in` saindo. `exit` 60-70% do `enter`.
- Só `transform` e `opacity`. Nunca `width`/`height`/`top`/`left` — daí F22 e F26.
- `@media (prefers-reduced-motion: reduce)`: desabilitar `fade-in-up` (`Chat.vue:304`), `bounce`
  (`Chat.vue:298`) e `transition-all`. O typing-indicator vira um `pi-spinner` estático.
- Semanticidade: `fade-in-up` na entrada de mensagem está correto (contenido novo). No **typing
  indicator** está errado — movimento sem causa-efeito, é só enfeite. Manter, masReducer a amplitude.

### 5.3 Tokens (nenhum hex novo)

| Uso | Token |
|---|---|
| Fundo app | `var(--p-content-background)` |
| Card / painel / modal | `var(--p-surface-0)` |
| Borda | `var(--p-content-border-color)` |
| Texto primário | `var(--p-text-color)` |
| Texto secundário | `var(--p-surface-500)` |
| Ação primária | `var(--p-primary-color)` |
| Destaque de ação | `var(--p-highlight-background)` (amber) — **só** em CTA primário único e no estado "pendente" do painel |
| Erro | `var(--p-red-500)` / `Message severity="error"` |
| Aviso | `Message severity="warn"` — **não** é o mesmo que amber; o painel de prontidão usa `warn` para não competir com o CTA primário |

Regra dura: **um acento só** (amber). Nenhuma tela introduz uma segunda cor de destaque. Os
ícones de estado do painel usam **forma** (círculo vazio vs `pi-check-circle`), não cor — evita
competir com o amber do CTA.

### 5.4 Mensagem de erro no fluxo do chat (novo, para F3)

A bolha de hoje é `ChatMessage role="assistant"`. Um erro de sistema nesse papel é mentira. Novo
componente, `SystemNotice`, no fluxo:

```
┌──────────────────────────────────────────────────────┐
│ ⚠  Não foi possível enviar a mensagem.                │  ← ícone + weight 600
│    O modelo não respondeu a tempo. Sua mensagem não    │  ← causa, text-sm, text-surface-600
│    foi perdida — ela continua na caixa de texto.       │
│    [ Tentar de novo ]  [ Ver troubleshooting ]        │  ← só se houver ação
└──────────────────────────────────────────────────────┘
```

- Alinhada à **esquerda**, largura `min(560px, 100%)`, `bg-surface-0`, `border-1 border-surface-200`,
  `rounded-2xl` — **sem** o gradiente/raio assimétrico da bolha do assistente, para que o olho
  distinga mensagem do modelo de mensagem do sistema **sem ler**.
- Nunca `role="assistant"`. Nunca texto com "desculpe".
- Regra: **causa + consequência + ação**. Três partes, sempre. Sem as três, é toast genérico.
- `aria-live="assertive"`.

### 5.5 Responsivo

| Breakpoint | Especificação |
|---|---|
| **≥ 1024px** | Layout atual. `PromptSelector` 18rem no canto do cabeçalho. Nav com grupo. |
| **768–1023px** | Idem. PromptSelector 14rem. |
| **< 768px** | Sidebar vira overlay (mecânica existente, `MainLayout.vue:117-121`). `PromptSelector` **desce** para uma linha própria abaixo do `h1`, largura 100%. Painel de prontidão `padding: 1rem`, CTA full-width. Tabelas do admin → cards (fora de escopo, mas registrar). |
| **< 375px** | Título do painel sem `h1` duplicado. Botões do painel 100%. |
| **Alvos** | Tudo tocável ≥ 44px. Hoje: botão de histórico (`Chat.vue:15-23`, `text` + `rounded` ≈ 32px) e o toggle da sidebar (`w-3rem h-3rem` = 48px, ok) — **subir o de histórico**. |
| **Scroll** | `.chat-view` usa `max-height: calc(100vh - …)`. Em `dvh` (mobile) e com o teclado virtual aberto isso quebra. `min-h-dvh` e `overflow: hidden` no pai já isolam; verificar com o teclado virtual. |

---

## 6. O que NÃO mudar agora

Cada item com o motivo — para não inflar o escopo.

**Produto (fora por decisão, não por mérito)**
1. RAG, base de conhecimento, tools, memória longa, skills, agenticidade. Decisão registrada em
   `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`. **Não desenhar, não suggestir.**
2. Anexos e voz (F1, F2): remover, não construir.
3. Entidade "Assistant" / Prompt Library / prompt como recurso compartilhável entre conversas. O
   aggregate root é a sessão; o prompt é parâmetro do turno.

**UX — fora do escopo desta passada**
4. Renomear a permissão `AGENT_MANAGE` (dívida acknowledged na spec §4.2.1, batch próprio).
5. Tour/wizard de onboarding. O painel §2.3 é melhor e é pulável por construção.
6. Command palette / atalhos de teclado global.
7. Busca no histórico de conversas, renomear sessão, favoritos.
8. Playground / preview de prompt (existe no editor, não mexer).
9. Realocar `TheTopbar`/`TheFooterbar`/`AuthLayout`.
10. Re-trabalho do sistema de toast (continua PrimeVue `Toast`; o que muda é **quando não usar**).
11. Redesign do breadcrumb além do rótulo "Home" (F12).
12. Virtualização do dropdown. Com `size=20` na busca e 50 no modo recentes, 50 nós é aceitável —
    o limiar de virtualização (>50) não é atingido. Se um dia for, aí sim.
13. Validar/re-tunar contraste de toda a paleta nos dois temas. Só os pares que tocam esta
    mudança (F25, F24).
14. Modo de Selected Prompt indicator no meio da conversa (ver "P1 abaixo" — é backlog).

**Backlog registrado, não implementado**
15. **P1 — Selected Prompt indicator.** O seletor fica no cabeçalho, e o header rola junto com a
    conversa? Não: o header é fixo, mas em conversa longa o usuário **rola para baixo e perde de
    vista qual prompt está respondendo**. Especialmente porque o prompt é imutável por sessão e é
    a variável que mais muda a resposta. Uma linha compacta e sticky logo acima do composer
    (`pi-bolt` + nome + cadeado) resolve. **É a evolução natural do F8** e o próximo batch.
16. **P2 — indicador de "digitando" do modelo** (streaming). Depende de backend; não desenhar
    agora.
17. **P3 — `text-surface-400` → 500 em todo o projeto.** Regra de contraste, não deste fluxo.

**Fora do perfil desta revisão**
18. Auditoria de TypeScript, testes, estrutura de serviços. Não pedido.
19. Acessibilidade de tela por tela com leitor de tela real (o §5.1 é o que dá para fazer sem
    harness; o resto precisa do H3 do `harness-continuous.md`).

---

## 7. Rastreabilidade

| Pergunta | Seção | Achados que a fecham |
|---|---|---|
| **1. PromptSelector > 50** | §1 | F6, F7, F8, F28, F29 · 0 backend novo (`q=` existe) |
| **2. Estados vazios / primeira-vez** | §2 | F1, F2, F3, F4, F5, F9, F10, F11 |
| **3. Navegação** | §3 | F12, F13, F14, F15 |
| **4. O que incomodou** | §4 (40 achados) | — |

Ordem de implementação: **F11 → §2.3** (painel) → **F7 + §1.3** (busca) → **§3** (nav) → **F1, F2**
(remoções) → **F3, F4, F5** (erros) → **F6, F8, F9** (seletor) → **F10** (copy) → **F12–F30** por
lote.

`DESIGN_SYSTEM.md` precisa de atualização: `NavItem` (§2.3) ganha `group`/`children`;
`topbar.height` (§1.3) precisa bater com o CSS.
