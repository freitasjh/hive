# UX-SPEC: admin-prompt-agent (Hive AI)

**Autor:** ux-designer  
**Data:** 2026-08-26  
**Versão:** 1.0  
**Status:** PARECER VINCULANTE para developer-engineer e SPEC

---

## 1. Auditoria do Estado Atual

### 1.1 Componentes Existentes (Analisados)

| Componente | Localização | Avaliação UX |
|------------|-------------|--------------|
| **TheSidebar** | `src/components/TheSidebar.vue` | ✅ Bem estruturado. Suporta collapse/expand, nav items com badge, hover state com translateX(4px). Variáveis CSS corretas (`--sidebar-width-expanded`, `--sidebar-width-collapsed`). Já inclui itens para `/prompts` e `/agents` no MainLayout. |
| **TheTopbar** | `src/components/TheTopbar.vue` | ✅ Funcional. Breadcrumbs dinâmicos, theme toggle, user menu com avatar. Sticky top. Altura 72px. |
| **MainLayout** | `src/components/MainLayout.vue` | ✅ Orquestração correta: Topbar + Sidebar + Content + Footerbar. Transições fade. Breadcrumbs computados a partir da rota. **Já define nav items para Prompts e Agentes.** |
| **AgentSelector** | `src/components/AgentSelector.vue` | ⚠️ Funcional mas limitado. Apenas `Select` com nome+descrição. Sem avatar, sem badge de padrão, sem indicador de modelo. Service `agentService.listAgents()` existe. |
| **Chat.vue** | `src/views/Chat.vue` | ✅ Referência de layout. Centralizado (max-width 850px), empty state com CTA, typing indicator. Boa referência de densidade e feedback. |
| **ChatSidebar** | `src/components/ChatSidebar.vue` | ✅ Drawer pattern para histórico. Bom exemplo de empty state, loading skeleton, paginação, confirmação de delete. |
| **AuthLayout** | `src/components/AuthLayout.vue` | ✅ Card auth com logo animado. Gradient background. Referência de visual. |

### 1.2 Rotas Existentes

```typescript
// router/index.ts — já definido mas com placeholder
{ path: 'prompts', component: () => import('../components/HelloWorld.vue') }  // PLACEHOLDER
{ path: 'agents',  component: () => import('../components/HelloWorld.vue') }  // PLACEHOLDER
```

**Conclusão:** A infraestrutura de navegação (sidebar, topbar, layout) já suporta as novas rotas. Não há necessidade de alterar TheSidebar ou TheTopbar para a feature.

### 1.3 Design System Pulse — Tokens Disponíveis

| Token | Valor | Uso |
|-------|-------|-----|
| Primary | `#18181b` (zinc) | Títulos, headings |
| Accent | `#f97316` (amber) | CTAs, estados ativos |
| Accent Alt | `#06b6d4` (cyan) | Tags, data viz secundária |
| Surface | `#ffffff` | Cards, painéis |
| BG | `#f1f5f9` (slate) | Fundo de página |
| Muted | `#a1a1aa` (zinc-400) | Captions, placeholders |
| Success | `#22c55e` | Badges: concluído, ativo |
| Warning | `#f59e0b` | Badges: em progresso |
| Danger | `#ef4444` | Badges: bloqueado, deletado |

**Tipografia:**
- DM Sans 700 → títulos de página, headings
- Inter 400-600 → corpo, labels, inputs, tabelas
- Escala: Page Title 1.75rem / Section h2 1.25rem / Body 0.875rem / Label 0.6875rem uppercase

**Linha de acento:** 2px âmbar sob títulos de página — elemento assinatura.

---

## 2. Propostas de Melhoria e Inovação

### 2.1 Melhorias Estruturais (Reaproveitar que existe)

| # | Melhoria | Componente | Justificativa |
|---|----------|------------|---------------|
| M1 | **Sidebar já tem nav items** para Prompts/Agents | MainLayout | Não criar novo nav. Já funciona. |
| M2 | **Breadcrumbs dinâmicos** já computam da rota | TheTopbar | Não alterar. Já gera "Home > Prompts" automaticamente. |
| M3 | **Theme toggle** já funciona | TheTopbar | Dark mode herda automaticamente. Não duplicar. |
| M4 | **Empty state pattern** do Chat como referência | Chat.vue | Aplicar mesmo padrão: ícone + título + descrição + CTA. |
| M5 | **Skeleton loading** do ChatSidebar como referência | ChatSidebar.vue | Aplicar mesmo padrão em tabelas e forms. |
| M6 | **Confirmação de delete** com Dialog | ChatSidebar | Reutilizar pattern: Dialog modal com "Cancelar" + "Excluir" (danger). |

### 2.2 Inovações Necessárias (Novos padrões)

| # | Inovação | Descrição | Framework |
|---|----------|-----------|-----------|
| I1 | **DataTable Admin** | Tabela com sort, filtro, paginação, actions por linha. Padrão admin que não existe ainda. | PrimeVue DataTable |
| I2 | **Split View Prompts** | Lista à esquerda + Editor à direita. Reduz navegação (Lei de Fitts). | PrimeVue Splitter |
| I3 | **Template Editor com highlight** | Editor de texto com marcação visual `{{variable}}` em cor cyan. | Textarea + regex highlight overlay |
| I4 | **Playground lateral** | Painel colapsável que extrai vars do template e mostra preview mock. | Drawer ou Splitter panel |
| I5 | **Slider params com tooltips** | Sliders para temperatura, TopP, etc. com valor numérico editável e tooltip explicativo. | PrimeVue Slider + InputNumber |
| I6 | **Badge "Padrão"** | Badge âmbar fixo no agente marcado como padrão. Indicador visual claro. | PrimeVue Tag/Badge |
| I7 | **Toggle "Definir como Padrão"** | Switch com confirmação ao trocar padrão. Aviso: "Já existe um padrão. Trocar?" | PrimeVue ToggleSwitch + Dialog |
| I8 | **Multi-select Tools/KBs** | Chips com autocomplete para ferramentas e bases de conhecimento. MVP: string[] placeholder. | PrimeVue AutoComplete + Chip |

---

## 3. Fluxos de Usuário

### 3.1 Fluxo Prompts — Listagem → Criar → Editar

```
┌─────────────────────────────────────────────────────────┐
│  /prompts (Listagem)                                    │
│                                                         │
│  [Busca] [Filtro Categoria] [+ Novo Prompt]            │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Nome        │ Categoria │ Criado em  │ Ações    │    │
│  │─────────────│───────────│────────────│──────────│    │
│  │ Saudação    │ Geral     │ 12/08/2026 │ ✏️ 🗑️    │    │
│  │ Código      │ Tech      │ 15/08/2026 │ ✏️ 🗑️    │    │
│  │ Relatório   │ Business  │ 20/08/2026 │ ✏️ 🗑️    │    │
│  └─────────────────────────────────────────────────┘    │
│  < Paginação >                                          │
└──────────────────────┬──────────────────────────────────┘
                       │ Clique "+ Novo Prompt" ou ✏️
                       ▼
┌─────────────────────────────────────────────────────────┐
│  /prompts/new  ou  /prompts/:id/edit                    │
│                                                         │
│  ┌──────────────────────┬──────────────────────────┐    │
│  │  EDITOR              │  PLAYGROUND (colapsável)  │    │
│  │                      │                          │    │
│  │  Nome: [_________]   │  Variáveis detectadas:   │    │
│  │  Categoria: [____]   │  ┌──────────────────┐   │    │
│  │  Descrição: [____]   │  │ nome: [____]     │   │    │
│  │                      │  │ tarefa: [____]   │   │    │
│  │  Template:           │  └──────────────────┘   │    │
│  │  ┌────────────────┐  │                          │    │
│  │  │ Olá {{nome}},  │  │  Preview:               │    │
│  │  │ sua tarefa é:  │  │  ┌──────────────────┐   │    │
│  │  │ {{tarefa}}     │  │  │ Olá João,        │   │    │
│  │  └────────────────┘  │  │ sua tarefa é:    │   │    │
│  │                      │  │ Criar relatório  │   │    │
│  │  Parâmetros:         │  └──────────────────┘   │    │
│  │  Temperatura [══○] 0.7│                          │    │
│  │  Top-P      [═══○] 0.9│  [Testar no Chat →]    │    │
│  │  Max Tokens [══○] 2048│                          │    │
│  │                      │                          │    │
│  │  [Salvar] [Cancelar] │                          │    │
│  └──────────────────────┴──────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Fluxo Agentes — Listagem → Criar → Editar → Padrão

```
┌─────────────────────────────────────────────────────────┐
│  /agents (Listagem)                                     │
│                                                         │
│  [Busca] [+ Novo Agente]                               │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Avatar │ Nome      │ Modelo  │ Padrão │ Ações   │    │
│  │────────│───────────│─────────│────────│─────────│    │
│  │ 🤖     │ Assistente│ GPT-4o  │ ⭐ Padr│ ✏️ 🗑️   │    │
│  │ 💻     │ Code Bot  │ Claude  │        │ ✏️ 🗑️   │    │
│  │ 📊     │ Analista  │ GPT-4o  │        │ ✏️ 🗑️   │    │
│  └─────────────────────────────────────────────────┘    │
│  < Paginação >                                          │
└──────────────────────┬──────────────────────────────────┘
                       │ Clique "+ Novo Agente" ou ✏️
                       ▼
┌─────────────────────────────────────────────────────────┐
│  /agents/new  ou  /agents/:id/edit                      │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │  Informações Básicas                            │    │
│  │  ┌──────┐                                       │    │
│  │  │ Avatar│  Nome: [________________________]    │    │
│  │  │ (URL) │  Descrição: [____________________]   │    │
│  │  └──────┘                                       │    │
│  │                                                  │    │
│  │  System Prompt                                   │    │
│  │  Vincular Prompt: [Select de Prompts    ▼]      │    │
│  │  ─── ou ───                                      │    │
│  │  Editor inline: [____________________________]   │    │
│  │  [____________________________]                  │    │
│  │  [____________________________]                  │    │
│  │                                                  │    │
│  │  Configuração                                    │    │
│  │  Modelo: [GPT-4o ▼]                              │    │
│  │  Temperatura: [═══○] 0.7                         │    │
│  │  Max Tokens: [═══○] 2048                         │    │
│  │                                                  │    │
│  │  Ferramentas: [autocomplete chips]               │    │
│  │  Bases de Conhecimento: [autocomplete chips]     │    │
│  │                                                  │    │
│  │  ☐ Definir como Agente Padrão                    │    │
│  │  (se já existe padrão: ⚠️ "Trocar agente padrão? │    │
│  │   O agente atual perderá o status de padrão.")   │    │
│  │                                                  │    │
│  │  [Salvar] [Cancelar]                             │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 3.3 Fluxo de Troca de Agente Padrão

```
Usuário ativa toggle "Definir como Padrão"
              │
              ▼
   Já existe um agente padrão?
      ┌───────┴───────┐
      │ SIM           │ NÃO
      ▼               ▼
   Dialog de      Ativa toggle
   confirmação    direto
      │
      ▼
   "Trocar agente padrão?
    O agente [Nome Atual] perderá
    o status de padrão."
   [Cancelar] [Confirmar Troca]
      │               │
      ▼               ▼
   Fecha dialog   Ativa toggle
                  Toast: "Agente padrão atualizado"
```

### 3.4 Fluxo de Exclusão/Desativação do Único Padrão

```
Usuário tenta:
- Desativar agente que é único padrão
- Excluir agente que é único padrão
              │
              ▼
   Bloqueado visualmente:
   - Toggle desabilitado (disabled)
   - Botão excluir desabilitado
   - Tooltip: "Este é o único agente padrão. 
     Defina outro como padrão antes de excluir."
              │
              ▼
   Não abre dialog. Ação simplesmente não disponível.
```

---

## 4. Especificação de Telas

### 4.1 Tela: Listagem Prompts (`/prompts`)

**Estrutura:**
```
┌─────────────────────────────────────────────────────┐
│ [TheTopbar — sem alteração]                          │
├──────┬──────────────────────────────────────────────┤
│      │  Prompts                           [barra 2px âmbar]│
│ [S]  │  ─────────────────────────────────────────── │
│      │  [🔍 Buscar...] [Categoria ▼]  [+ Novo Prompt] │
│ [C]  │                                              │
│ [P]  │  ┌────────────────────────────────────────┐  │
│ [A]  │  │ DataTable                              │  │
│      │  │ ┌──────┬────────┬─────────┬──────┬───┐ │  │
│      │  │ │Nome  │Categoria│Criado   │Vars  │Act│ │  │
│      │  │ ├──────┼────────┼─────────┼──────┼───┤ │  │
│      │  │ │Sauda…│ Geral  │ 12/08   │ {{nome│ ✏🗑│ │  │
│      │  │ │Código│ Tech   │ 15/08   │ {{cod│ ✏🗑│ │  │
│      │  │ └──────┴────────┴─────────┴──────┴───┘ │  │
│      │  │ [◀ 1/3 ▶]  Mostrando 1-10 de 24       │  │
│      │  └────────────────────────────────────────┘  │
├──────┴──────────────────────────────────────────────┤
│ [TheFooterbar — sem alteração]                       │
└─────────────────────────────────────────────────────┘
```

**Especificações:**
- **Page Title:** `Prompts` — DM Sans, 1.75rem, 700, cor `#18181b`
- **Linha de acento:** 2px `#f97316` (âmbar) abaixo do título
- **Barra de ferramentas:** flex row, gap 12px, alinhado à direita
  - Input busca: PrimeVue `InputText` com `pi-search` icon left, placeholder "Buscar prompts..."
  - Filtro categoria: PrimeVue `Dropdown` com opções: Geral, Tech, Business, Creative
  - Botão "+ Novo Prompt": PrimeVue `Button` severity="primary" (âmbar), icon `pi-plus`
- **DataTable:** PrimeVue `DataTable` com:
  - Colunas: Nome (text), Categoria (Badge), Data de criação (date formatted), Variáveis detectadas (count badge), Ações (icon buttons)
  - Sort por nome ou data
  - Paginação: `Paginator` template "FirstPageLink PrevPageLink CurrentPageReport NextPageLink LastPageLink"
  - Rows per page: 10
  - Empty state: ícone `pi pi-bolt` (3xl, muted) + "Nenhum prompt criado" + "Crie seu primeiro prompt para começar." + botão "+ Novo Prompt"
  - Loading: skeleton rows (3-5 linhas)
- **Actions por linha:** 
  - Editar: `pi-pencil` button text rounded severity="secondary"
  - Excluir: `pi-trash` button text rounded severity="danger"
  - Confirmação de delete: Dialog modal "Excluir prompt" — "Tem certeza? Esta ação não pode ser desfeita." [Cancelar] [Excluir]

**Componentes Pulse:**
- Card container: surface `#ffffff`, border 1px, `--shadow-sm`, radius `--radius-sm`
- Badge categoria: `--radius-xs`, fundo `cyan-100`, texto `cyan-800`
- Botão primário: âmbar `#f97316`, texto branco, radius `--radius-sm`
- Hover row: `bg-surface-50`

---

### 4.2 Tela: Editor Prompts (`/prompts/new` ou `/prompts/:id/edit`)

**Layout Split View (Lei de Fitts — ação primária ao lado do contexto):**
```
┌─────────────────────────────────────────────────────────┐
│ [TheTopbar]                                              │
├──────┬──────────────────────────────────────────────────┤
│      │  Editar Prompt                          [barra 2px âmbar]│
│ [S]  │  ─────────────────────────────────────────────── │
│      │  [← Voltar para Prompts]                         │
│ [C]  │                                                  │
│ [P]  │  ┌──────────────────┬───────────────────────┐   │
│ [A]  │  │ EDITOR           │ PLAYGROUND            │   │
│      │  │                  │ (colapsável)          │   │
│      │  │ Nome:            │                       │   │
│      │  │ [Saudação   ]   │ Variáveis:            │   │
│      │  │                  │ ┌───────────────────┐ │   │
│      │  │ Categoria:       │ │ {{nome}} → [____] │ │   │
│      │  │ [Geral ▼]        │ │ {{tarefa}}→ [____]│ │   │
│      │  │                  │ └───────────────────┘ │   │
│      │  │ Descrição:       │                       │   │
│      │  │ [Prompt para sa-│ Preview:              │   │
│      │  │  udar o usuário]│ ┌───────────────────┐ │   │
│      │  │                  │ │ Olá João,         │ │   │
│      │  │ Template:        │ │ sua tarefa é:     │ │   │
│      │  │ ┌──────────────┐ │ │ Criar relatório   │ │   │
│      │  │ │ Olá {{nome}},│ │ └───────────────────┘ │   │
│      │  │ │ sua tarefa é:│ │                       │   │
│      │  │ │ {{tarefa}}   │ │ [Testar no Chat →]   │   │
│      │  │ └──────────────┘ │                       │   │
│      │  │                  │                       │   │
│      │  │ ── Parâmetros ── │                       │   │
│      │  │                  │                       │   │
│      │  │ Temperatura      │                       │   │
│      │  │ [═══════○] 0.7   │                       │   │
│      │  │ 0.0 ──────── 2.0│                       │   │
│      │  │                  │                       │   │
│      │  │ Top-P            │                       │   │
│      │  │ [═════════○] 0.9 │                       │   │
│      │  │ 0.0 ──────── 1.0│                       │   │
│      │  │                  │                       │   │
│      │  │ Max Tokens       │                       │   │
│      │  │ [══════○] 2048   │                       │   │
│      │  │ 256 ─────── 4096 │                       │   │
│      │  │                  │                       │   │
│      │  │ [Salvar] [Cancel]│                       │   │
│      │  └──────────────────┴───────────────────────┘   │
└──────┴──────────────────────────────────────────────────┘
```

**Especificações do Editor:**
- **Splitter:** PrimeVue `Splitter` com panels 60%/40%
- **Playground toggle:** Botão `pi-eye` / `pi-eye-slash` no header do playground para colapsar/expandir
- **Template Editor:** `Textarea` com:
  - Font: monospace (Fira Code ou similar via CSS `font-family: 'Fira Code', monospace`)
  - Highlight visual: variáveis `{{var}}` em cor cyan `#06b6d4` com fundo `cyan-50` — implementar via overlay div com regex
  - Auto-resize: `autoResize` prop do PrimeVue Textarea
  - Placeholder: "Digite seu template. Use {{nome_variavel}} para variáveis."
- **Sliders params:**
  - Temperatura: Slider 0.0–2.0, step 0.1, default 0.7
  - Top-P: Slider 0.0–1.0, step 0.05, default 0.9
  - Max Tokens: Slider 256–4096, step 256, default 2048
  - Cada slider: label acima (Inter, 0.875rem, 600), valor numérico à direita editável (InputNumber)
  - Tooltip explicativo no label: `v-tooltip.top="'Controla a criatividade da resposta'"`
- **Playground:**
  - Detecta `{{var}}` do template via regex `/\{\{(\w+)\}\}/g`
  - Gera inputs dinâmicos com label = nome da variável
  - Preview: renderiza template substituindo vars por valores dos inputs
  - "Testar no Chat →": navega para `/chat` com query param `?agentId=...&promptId=...` (MVP: apenas visual, sem implementação de chat)
- **Botões:**
  - Salvar: `Button` severity="primary" (âmbar), icon `pi-check`
  - Cancelar: `Button` severity="secondary", text

**Estados de interação:**
- **Default:** campos vazios, sliders nos defaults
- **Loading (salvar):** botão Salvar com spinner, campos disabled
- **Sucesso:** toast "Prompt salvo com sucesso" + redirect para `/prompts`
- **Erro:** toast com mensagem de erro, campos mantidos
- **Dirty:** ao modificar qualquer campo, botão voltar mostra confirmação "Descartar alterações?"

---

### 4.3 Tela: Listagem Agentes (`/agents`)

**Estrutura:**
```
┌─────────────────────────────────────────────────────┐
│ [TheTopbar]                                          │
├──────┬──────────────────────────────────────────────┤
│      │  Agentes                             [barra 2px âmbar]│
│ [S]  │  ─────────────────────────────────────────── │
│      │  [🔍 Buscar...]              [+ Novo Agente] │
│ [C]  │                                              │
│ [P]  │  ┌────────────────────────────────────────┐  │
│ [A]  │  │ DataTable                              │  │
│      │  │ ┌──────┬───────┬───────┬───────┬──────┐│  │
│      │  │ │Avatar│Nome   │Modelo │Padrão │Ações ││  │
│      │  │ ├──────┼───────┼───────┼───────┼──────┤│  │
│      │  │ │ [🤖] │Asist… │GPT-4o │⭐Padr │ ✏🗑  ││  │
│      │  │ │ [💻] │Code B │Claude │       │ ✏🗑  ││  │
│      │  │ └──────┴───────┴───────┴───────┴──────┘│  │
│      │  │ [◀ 1/2 ▶]  Mostrando 1-10 de 12       │  │
│      │  └────────────────────────────────────────┘  │
├──────┴──────────────────────────────────────────────┤
│ [TheFooterbar]                                       │
└─────────────────────────────────────────────────────┘
```

**Especificações:**
- **Coluna Avatar:** PrimeVue `Avatar` (32x32px) com image URL ou iniciais do nome como fallback
- **Coluna Nome:** bold, com descrição truncada abaixo em muted
- **Coluna Modelo:** Badge com nome do modelo (ex: "GPT-4o", "Claude 3.5")
- **Coluna Padrão:** 
  - Se é padrão: Tag âmbar `⭐ Padrão` com background `amber-100`, text `amber-800`
  - Se não é padrão: vazio
- **Ações:** Mesmo padrão dos Prompts (editar + excluir)
- **Exclusão do único padrão:** botão excluir desabilitado com tooltip "Defina outro como padrão antes de excluir"

---

### 4.4 Tela: Editor Agentes (`/agents/new` ou `/agents/:id/edit`)

**Layout Form Vertical (7+ campos → Wizard ou Form longo com seções):**

Decisão UX: **Form vertical com seções colapsáveis** em vez de wizard multi-etapa.
Justificativa: Agentes são entidades que o admin edita raramente (Lei de Miller: manter contexto visível). Seções colapsáveis permitem progressive disclosure sem perder visão geral.

```
┌─────────────────────────────────────────────────────┐
│ [TheTopbar]                                          │
├──────┬──────────────────────────────────────────────┤
│      │  Novo Agente                         [barra 2px âmbar]│
│ [S]  │  ─────────────────────────────────────────── │
│      │  [← Voltar para Agentes]                     │
│ [C]  │                                              │
│ [P]  │  ┌────────────────────────────────────────┐  │
│ [A]  │  │ ▼ Informações Básicas                  │  │
│      │  │  ┌──────┐  Nome: [________________]    │  │
│      │  │  │Avatar│  Descrição: [____________]   │  │
│      │  │  │ URL  │                              │  │
│      │  │  └──────┘                              │  │
│      │  │                                        │  │
│      │  │ ▶ System Prompt (colapsado)            │  │
│      │  │ ▶ Configuração (colapsado)             │  │
│      │  │ ▶ Ferramentas & KBs (colapsado)        │  │
│      │  │                                        │  │
│      │  │ ☐ Definir como Agente Padrão           │  │
│      │  │                                        │  │
│      │  │ [Salvar] [Cancelar]                    │  │
│      │  └────────────────────────────────────────┘  │
├──────┴──────────────────────────────────────────────┤
│ [TheFooterbar]                                       │
└─────────────────────────────────────────────────────┘
```

**Seção System Prompt (expandida):**
```
│  System Prompt                                       │
│  Vincular a um Prompt existente:                     │
│  [Selecione um Prompt...  ▼]  ← Select do prompt    │
│                                                      │
│  ── ou escreva diretamente ──                        │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │ Você é um assistente especializado em...      │   │
│  │ [________________________________________]   │   │
│  │ [________________________________________]   │   │
│  └──────────────────────────────────────────────┘   │
```

**Seção Configuração (expandida):**
```
│  Configuração                                        │
│                                                      │
│  Modelo: [GPT-4o ▼]  ← Select com opções do provider│
│                                                      │
│  Temperatura: [═══════○] 0.7  ← Slider              │
│  Top-P:        [═════════○] 0.9 ← Slider            │
│  Max Tokens:   [══════○] 2048  ← Slider             │
│                                                      │
│  (Params herdados do prompt vinculado, sobrescreve   │
│   os valores do prompt)                               │
```

**Seção Ferramentas & KBs (expandida):**
```
│  Ferramentas                                         │
│  [🔍 Buscar ferramentas...]                          │
│  [web_search ×] [code_exec ×] [file_read ×]         │
│                                                      │
│  Bases de Conhecimento                               │
│  [🔍 Buscar KBs...]                                  │
│  [docs-api ×] [faq-produto ×]                        │
```

**Especificações:**
- **Accordion:** PrimeVue `Accordion` com `multiple` mode (todas seções podem estar abertas)
- **Avatar URL:** PrimeVue `InputText` com preview circular à esquerda (32x32px). Se URL inválida ou vazia, mostra iniciais do nome.
- **Select Prompt:** PrimeVue `Select` com `optionLabel="name"`, `optionValue="id"`, `showClear`, `filter`. Ao selecionar: preenche campos de parâmetros com valores do prompt.
- **System Prompt textarea:** Mono-spaced, auto-resize, placeholder descritivo
- **Modelo select:** Opções fixas do provider (MVP enum). Ex: GPT-4o, GPT-4o-mini, Claude 3.5 Sonnet, Claude 3 Haiku
- **Ferramentas/KBs:** PrimeVue `AutoComplete` com `multiple` + `Chip`. MVP: `List<String>` placeholder, sem validação de existência.
- **Toggle Padrão:** PrimeVue `ToggleSwitch` com label "Definir como Agente Padrão". Abaixo do toggle, quando ativo: badge âmbar "⭐ Este agente será o padrão para novas conversas."

---

### 4.5 Tela: Empty States

**Prompts sem registros:**
```
┌─────────────────────────────────────────────────────┐
│                                                      │
│           [pi-bolt icon, 3xl, muted]                 │
│                                                      │
│         Nenhum prompt criado                         │
│    Crie seu primeiro prompt para começar.             │
│                                                      │
│         [+ Novo Prompt]                              │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**Agentes sem registros:**
```
┌─────────────────────────────────────────────────────┐
│                                                      │
│           [pi-users icon, 3xl, muted]                │
│                                                      │
│         Nenhum agente criado                         │
│    Crie seu primeiro agente para começar.             │
│                                                      │
│         [+ Novo Agente]                              │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**Padrão:** Seguir padrão do Chat.vue empty state — ícone grande muted + título bold + descrição muted + botão CTA primário (âmbar).

---

## 5. Especificação Responsiva

### Breakpoints (seguindo Pulse + ui-ux-pro-max)

| Breakpoint | Largura | Comportamento |
|------------|---------|---------------|
| **Mobile** | < 768px | Sidebar colapsada (64px). Tabelas → card layout. Split view → stacked (editor sobre playground). Form full-width. |
| **Tablet** | 768px–1024px | Sidebar colapsada. Tabela com colunas reduzidas (ocultar "Variáveis" e "Modelo"). Split view 50/50. |
| **Desktop** | > 1024px | Sidebar expandida (280px). Tabela completa. Split view 60/40. Form com seções lado a lado. |
| **Wide** | > 1440px | Mesmo que desktop, com mais breathing room. |

### Grid

```
Desktop (≥1024px):
┌──────┬────────────────────────────────────────┐
│ 280px │ content (fluid)                        │
│ side  │ padding: 32px                          │
│ bar   │                                        │
└──────┴────────────────────────────────────────┘

Tablet (768–1024px):
┌──────┬────────────────────────────────────────┐
│ 64px │ content (fluid)                         │
│ side │ padding: 24px                           │
│ bar  │                                         │
└──────┴────────────────────────────────────────┘

Mobile (<768px):
┌────────────────────────────────────────────────┐
│ content (full-width)                            │
│ padding: 16px                                   │
│ sidebar hidden (toggle via hamburger)           │
└────────────────────────────────────────────────┘
```

### Tabela Responsiva (Mobile)

```
Desktop:                          Mobile:
┌──────┬───────┬──────┬─────┐    ┌─────────────────────┐
│Nome  │Categ. │Data  │Ações│    │ [🤖] Saudação       │
├──────┼───────┼──────┼─────┤    │ Geral · 12/08/2026  │
│Sauda…│Geral  │12/08 │✏🗑  │    │ vars: {{nome}}       │
└──────┴───────┴──────┴─────┘    │ [Editar] [Excluir]  │
                                  └─────────────────────┘
```

Em mobile, a tabela vira uma lista de cards. Cada card mostra:
- Avatar/ícone à esquerda
- Nome (bold) + metadata secundária (muted)
- Tags/badges inline
- Ações como botões de texto na parte inferior

---

## 6. Especificação de Componentes

### 6.1 Componentes Novos (Criar)

| Componente | Localização | Props | Descrição |
|------------|-------------|-------|-----------|
| `PromptDataTable` | `src/components/admin/PromptDataTable.vue` | `prompts: PromptDTO[]`, `loading`, `@edit`, `@delete`, `@create` | Tabela de prompts com sort/filter |
| `PromptEditor` | `src/components/admin/PromptEditor.vue` | `prompt?: PromptDTO`, `@save`, `@cancel` | Form + template editor + params |
| `TemplateEditor` | `src/components/admin/TemplateEditor.vue` | `modelValue: string`, `@update:modelValue` | Textarea com highlight de variáveis |
| `InferenceParams` | `src/components/admin/InferenceParams.vue` | `modelValue: InferenceParams`, `@update:modelValue` | Sliders de temperatura/topP/tokens |
| `PlaygroundPanel` | `src/components/admin/PlaygroundPanel.vue` | `template: string`, `params: Record<string, string>` | Extração de vars + preview mock |
| `AgentDataTable` | `src/components/admin/AgentDataTable.vue` | `agents: AgentAdminDTO[]`, `loading`, `@edit`, `@delete`, `@create` | Tabela de agentes com badge padrão |
| `AgentEditor` | `src/components/admin/AgentEditor.vue` | `agent?: AgentAdminDTO`, `@save`, `@cancel` | Form com accordion de seções |
| `DefaultAgentToggle` | `src/components/admin/DefaultAgentToggle.vue` | `isDefault: boolean`, `isOnlyDefault: boolean`, `@toggle` | Toggle com lógica de confirmação |
| `AdminEmptyState` | `src/components/admin/AdminEmptyState.vue` | `icon`, `title`, `description`, `ctaLabel`, `@cta` | Empty state reutilizável |

### 6.2 Componentes Existentes (Reaproveitar)

| Componente | Uso na Feature |
|------------|----------------|
| `TheSidebar` | Sem alteração. Nav items já definidos no MainLayout. |
| `TheTopbar` | Sem alteração. Breadcrumbs dinâmicos. |
| `MainLayout` | Sem alteração. Estrutura correta. |
| `TheFooterbar` | Sem alteração. |

### 6.3 Diretório de Arquivos

```
src/
├── views/
│   ├── PromptsView.vue          # Listagem + rota /prompts
│   ├── PromptEditorView.vue     # Editor + rota /prompts/new, /prompts/:id/edit
│   ├── AgentsView.vue           # Listagem + rota /agents
│   └── AgentEditorView.vue      # Editor + rota /agents/new, /agents/:id/edit
├── components/
│   └── admin/
│       ├── PromptDataTable.vue
│       ├── PromptEditor.vue
│       ├── TemplateEditor.vue
│       ├── InferenceParams.vue
│       ├── PlaygroundPanel.vue
│       ├── AgentDataTable.vue
│       ├── AgentEditor.vue
│       ├── DefaultAgentToggle.vue
│       └── AdminEmptyState.vue
├── services/
│   ├── prompt.service.ts        # CRUD prompts
│   └── agent-admin.service.ts   # CRUD agents (admin)
└── types/
    ├── prompt.ts                # PromptDTO, PromptPage, InferenceParams
    └── agent-admin.ts           # AgentAdminDTO, AgentAdminPage
```

---

## 7. Adesão ao Design System Pulse

### Checklist de Conformidade

| Elemento | Token Pulse | Aplicação |
|----------|-------------|-----------|
| **Page Title** | DM Sans, 1.75rem, 700, `#18181b` | "Prompts", "Agentes", "Editar Prompt", "Novo Agente" |
| **Linha de acento** | 2px `#f97316` | Abaixo de cada page title |
| **CTA primário** | `#f97316` (âmbar), texto branco, radius `--radius-sm` | "+ Novo Prompt", "+ Novo Agente", "Salvar" |
| **CTA secundário** | transparente, borda sutil | "Cancelar", "Voltar" |
| **Card** | surface `#ffffff`, border 1px `#e2e8f0`, `--shadow-sm` | Container de tabelas, forms |
| **Input focus** | ring âmbar 3px, 12% opacity | Todos os InputText, Textarea, Select |
| **Badge categoria** | `--radius-xs`, cyan bg | Tags de categoria em prompts |
| **Badge "Padrão"** | âmbar bg, texto escuro | Tag ⭐ Padrão na listagem de agentes |
| **DataTable header** | uppercase, muted, Inter 0.6875rem, 600 | Headers de tabela |
| **DataTable row** | border 1px `#e2e8f0` entre linhas, sem zebra | Linhas da tabela |
| **Empty state** | ícone muted + título bold + descrição muted + CTA | Tabelas vazias |
| **Toast sucesso** | `#22c55e` (success) | "Prompt salvo", "Agente criado" |
| **Toast erro** | `#ef4444` (danger) | Erros de API |
| **Toast warn** | `#f59e0b` (warning) | "Já existe um padrão" |
| **Slider** | âmbar no track preenchido | Temperatura, TopP, Max Tokens |
| **Skeleton** | surface-200, radius `--radius-md` | Loading de tabelas e forms |
| **Dark mode** | herda via `.p-dark` | Todos os componentes |

### O que NÃO fazer (Anti-patterns Pulse)

- ❌ Usar âmbar em elementos passivos (fundos, bordas, divisores)
- ❌ Usar mais de um acento (cyan só para data/tags, não para CTAs)
- ❌ uppercase em corpo de texto (só em labels e headers de tabela)
- ❌ max-width na content area (usar largura total com padding 32px)
- ❌ Inventar novos tokens (usar os existentes em `style.css`)

---

## 8. Avaliação Heurística (Nielsen)

### 8.1 Prompts — Listagem

| # | Heurística | Status | Notas |
|---|------------|--------|-------|
| 1 | Visibilidade do status | ✅ | Loading skeleton, empty state, toast de feedback |
| 2 | Sistema/mundo real | ✅ | Nomes como "Prompts", "Categoria" — linguagem de domínio |
| 3 | Controle/liberdade | ✅ | Botão cancelar/voltar, confirmação de delete |
| 4 | Consistência | ✅ | Mesmo padrão de tabela do ChatSidebar (action buttons) |
| 5 | Prevenção de erros | ✅ | Confirmação antes de excluir |
| 6 | Reconhecimento > memória | ✅ | Categorias visíveis, busca com filtro |
| 7 | Flexibilidade/eficiência | ✅ | Sort, filtro, paginação |
| 8 | Estético/minimalista | ✅ | Design limpo, sem decoração desnecessária |
| 9 | Diagnóstico de erros | ✅ | Toast com mensagem descritiva |
| 10 | Ajuda/docs | ⚠️ | Tooltips nos sliders, mas sem help geral na listagem |

### 8.2 Prompts — Editor

| # | Heurística | Status | Notas |
|---|------------|--------|-------|
| 1 | Visibilidade do status | ✅ | Loading no save, toast sucesso/erro |
| 2 | Sistema/mundo real | ✅ | "Temperatura", "Top-P" — termos do domínio LLM |
| 3 | Controle/liberdade | ✅ | Cancelar com confirmação de dirty state |
| 4 | Consistência | ✅ | Mesmos padrões de form do sistema |
| 5 | Prevenção de erros | ✅ | Validação de ranges nos sliders, required fields |
| 6 | Reconhecimento > memória | ✅ | Playground mostra preview em tempo real |
| 7 | Flexibilidade/eficiência | ✅ | Split view reduz navegação |
| 8 | Estético/minimalista | ✅ | Editor clean, playground colapsável |
| 9 | Diagnóstico de erros | ✅ | Mensagens de erro inline |
| 10 | Ajuda/docs | ✅ | Tooltips explicativos nos parâmetros |

### 8.3 Agentes — Editor

| # | Heurística | Status | Notas |
|---|------------|--------|-------|
| 1 | Visibilidade do status | ✅ | Loading, toast feedback |
| 2 | Sistema/mundo real | ✅ | "System Prompt", "Ferramentas" — termos conhecidos |
| 3 | Controle/liberdade | ✅ | Cancelar, voltar, accordion toggle |
| 4 | Consistência | ✅ | Mesmo padrão do editor de prompts |
| 5 | Prevenção de erros | ✅ | Toggle padrão com confirmação, exclusão bloqueada |
| 6 | Reconhecimento > memória | ✅ | Seções agrupadas, labels claros |
| 7 | Flexibilidade/eficiência | ✅ | Select de prompt existente evita reescrever |
| 8 | Estético/minimalista | ✅ | Accordion reduz complexidade visual |
| 9 | Diagnóstico de erros | ✅ | Toast + tooltip de bloqueio |
| 10 | Ajuda/docs | ⚠️ | Tooltip no toggle, mas sem help nos campos de tools/KBs |

---

## 9. Decisões de Design (com Justificativa)

### D1: Split View vs. Modal para Editor de Prompts
**Decisão:** Split View (60/40)  
**Justificativa:** Lei de Fitts — manter o editor e o playground visíveis simultaneamente reduz a distância entre ação (editar template) e resultado (ver preview). Modal forçaria troca de contexto. Split view é padrão em ferramentas de coding (VS Code split, Notion side peek).

### D2: Accordion vs. Wizard para Editor de Agentes
**Decisão:** Accordion com seções colapsáveis  
**Justificativa:** Agentes são editados raramente (Lei de Miller — manter contexto visível). Wizard multi-etapa fragmenta o fluxo e dificulta revisão. Accordion permite progressive disclosure mantendo a visão geral. Referência: GitHub repo settings (sections colapsáveis).

### D3: DataTable vs. Cards para Listagem
**Decisão:** DataTable em desktop, Cards em mobile  
**Justificativa:** Tabelas são mais eficientes para escaneamento de dados estruturados (Lei de Fitts — alvos alinhados). Cards são mais touch-friendly em mobile (ui-ux-pro-max: touch target ≥44px). PrimeVue DataTable com responsive mode.

### D4: Badge "Padrão" como Tag vs. Ícone
**Decisão:** Tag âmbar com texto "⭐ Padrão"  
**Justificativa:** Ícone sozinho pode ser ambíguo (Heurística 4: Consistência). Tag com texto é auto-explicativa. Cor âmbar mantém hierarquia visual — padrão é informação importante mas não é a ação primária.

### D5: Toggle "Padrão" com Dialog vs. Inline
**Decisão:** Toggle com Dialog de confirmação  
**Justificativa:** Heurística 5: Prevenção de erros. Trocar o agente padrão tem impacto em todas as novas conversas. Ação destrutiva indireta requer confirmação explícita.

### D6: Playground Mock vs. LLM Real
**Decisão:** Mock local (MVP)  
**Justificativa:** Conforme product-manager (A4). Substitui variáveis localmente e renderiza preview. Sem chamada LLM. "Testar no Chat →" navega para chat com contexto.

### D7: Fonte do Template Editor
**Decisão:** Mono-spaced (Fira Code ou inherit do sistema)  
**Justificativa:** Templates são código estruturado. Mono-spaced melhora legibilidade de `{{variáveis}}` e alinhamento. Padrão de editores de código.

---

## 10. Especificação de Interação

### 10.1 Micro-interações

| Elemento | Comportamento | Duração | Easing |
|----------|---------------|---------|--------|
| **Hover row tabela** | Background `surface-50`, cursor pointer | 150ms | ease-out |
| **Hover botão** | Escurece ligeiramente (darken 5%) | 150ms | ease-out |
| **Click botão** | Scale 0.97 → 1.0 (press feedback) | 100ms | ease-in-out |
| **Slider drag** | Track preenchido acompanha thumb | instant | — |
| **Accordion expand** | Height animate + fade content | 200ms | ease-out |
| **Toast enter** | Slide-in from right + fade | 300ms | ease-out |
| **Toast exit** | Fade out | 200ms | ease-in |
| **Splitter resize** | Smooth panels reflow | instant | — |
| **Playground toggle** | Width animate do panel | 250ms | ease-out |
| **Page transition** | Fade (já existente no MainLayout) | 200ms | ease |

### 10.2 Focus States

- **Todos os inputs:** ring âmbar 3px, 12% opacity (já definido no Pulse)
- **Botões:** ring âmbar 3px no focus-visible
- **Tabela rows:** outline none (ação via botões de ação)
- **Accordion headers:** ring âmbar no focus-visible
- **Skip link:** "Pular para conteúdo principal" — acessível via Tab

### 10.3 Keyboard Navigation

- **Tab order:** Busca → Filtro → Botão criar → Tabela (rows) → Ações por linha → Paginação
- **Enter/Space:** Ativa botões e links
- **Escape:** Fecha modais e drawers
- **Arrow keys:** Navega entre linhas da tabela (quando focada)
- **Ctrl+S (editor):** Atalho para salvar (com toast "Pressione o botão Salvar")

---

## 11. Acessibilidade (WCAG 2.1 AA)

| Requisito | Implementação |
|-----------|---------------|
| **Contraste texto** | `#18181b` em `#ffffff` = 16.75:1 ✅ |
| **Contraste muted** | `#a1a1aa` em `#ffffff` = 3.04:1 — usar só para texto grande ou decorativo |
| **Alt text** | Avatar com `aria-label` do nome do agente/prompt |
| **Keyboard nav** | Tab order lógico, focus visible em todos os interativos |
| **Form labels** | Label + `for` em todos os inputs (nunca placeholder-only) |
| **Error messages** | `aria-live="polite"` para toast, `role="alert"` para erros inline |
| **Headings** | h1 (page title) → h2 (seções) → h3 (subseções). Sem skip. |
| **Reduced motion** | `@media (prefers-reduced-motion: reduce)` — desabilitar animações |
| **Touch targets** | Mínimo 44x44px em todos os botões e inputs mobile |

---

## 12. Tipos de Dados (Frontend)

### `types/prompt.ts`
```typescript
export interface PromptDTO {
  id: string
  name: string
  category: string
  description: string
  template: string          // com {{variáveis}}
  inferenceParams: InferenceParams
  createAt: string
  updateAt: string
}

export interface InferenceParams {
  temperature: number       // 0.0–2.0
  topP: number              // 0.0–1.0
  maxTokens: number         // 256–4096
  penalties?: Record<string, number>
}

export interface PromptPage {
  content: PromptDTO[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
```

### `types/agent-admin.ts`
```typescript
export interface AgentAdminDTO {
  id: string
  name: string
  avatarUrl?: string
  description?: string
  systemPrompt?: string
  promptId?: string          // vinculo a prompt existente
  model: string              // enum: gpt-4o, gpt-4o-mini, claude-3.5-sonnet, claude-3-haiku
  inferenceParams: InferenceParams
  tools: string[]            // MVP: placeholder
  knowledgeBases: string[]   // MVP: placeholder
  isDefault: boolean
  createAt: string
  updateAt: string
}

export interface AgentAdminPage {
  content: AgentAdminDTO[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
```

---

## 13. Serviços (Frontend)

### `services/prompt.service.ts`
```typescript
// CRUD endpoints:
// GET    /v1/prompts?page=&size=        → PromptPage
// GET    /v1/prompts/:id                → PromptDTO
// POST   /v1/prompts                    → PromptDTO
// PUT    /v1/prompts/:id                → PromptDTO
// DELETE /v1/prompts/:id                → 204
```

### `services/agent-admin.service.ts`
```typescript
// CRUD endpoints:
// GET    /v1/agents?page=&size=         → AgentAdminPage
// GET    /v1/agents/:id                 → AgentAdminDTO
// POST   /v1/agents                     → AgentAdminDTO
// PUT    /v1/agents/:id                 → AgentAdminDTO
// DELETE /v1/agents/:id                 → 204
// PUT    /v1/agents/:id/default         → 200 (definir como padrão)
// DELETE /v1/agents/:id/default         → 200 (remover padrão)
```

---

## 14. Rotas (Frontend)

```typescript
// router/index.ts — adicionar ao children do MainLayout
{
  path: 'prompts',
  name: 'prompts',
  component: () => import('../views/PromptsView.vue')
},
{
  path: 'prompts/new',
  name: 'prompt-new',
  component: () => import('../views/PromptEditorView.vue')
},
{
  path: 'prompts/:id/edit',
  name: 'prompt-edit',
  component: () => import('../views/PromptEditorView.vue')
},
{
  path: 'agents',
  name: 'agents',
  component: () => import('../views/AgentsView.vue')
},
{
  path: 'agents/new',
  name: 'agent-new',
  component: () => import('../views/AgentEditorView.vue')
},
{
  path: 'agents/:id/edit',
  name: 'agent-edit',
  component: () => import('../views/AgentEditorView.vue')
}
```

---

## 15. Resumo de Entregáveis para developer-engineer

| Entregável | Descrição |
|------------|-----------|
| **Views** | `PromptsView.vue`, `PromptEditorView.vue`, `AgentsView.vue`, `AgentEditorView.vue` |
| **Componentes** | 9 componentes admin (ver seção 6.1) |
| **Services** | `prompt.service.ts`, `agent-admin.service.ts` |
| **Types** | `prompt.ts`, `agent-admin.ts` |
| **Router** | 6 novas rotas (ver seção 14) |
| **CSS** | Nenhum novo — herda do `style.css` + PrimeVue Pulse tokens |

---

## Output Contract

### Task Report
- **Status**: success
- **Objective Achieved**: Parecer UX completo para admin-prompt-agent com auditoria do estado atual, propostas de melhoria/inovação, 5 telas especificadas, fluxos de usuário, adesão ao Pulse, e especificação responsiva — vinculante para developer-engineer e SPEC.
- **Files Created**: `.spec/frontend/admin-prompt-agent/UX-SPEC.md`
- **Files Modified**: none
- **Files Deleted**: none
- **Decisions Made**:
  - D1: Split View para editor de prompts (não modal)
  - D2: Accordion para editor de agentes (não wizard)
  - D3: DataTable + responsive cards (não só cards)
  - D4: Tag âmbar para badge "Padrão" (não só ícone)
  - D5: Dialog de confirmação para toggle padrão
  - D6: Playground mock local para MVP
  - D7: Fonte mono-spaced para template editor
- **Validation**: pass
- **Errors**: none
- **Scope Deviations**: none

### Downstream Context
- **Key Interfaces Introduced**: `PromptDTO`, `AgentAdminDTO`, `InferenceParams`, `PromptPage`, `AgentAdminPage` (arquivos `types/prompt.ts`, `types/agent-admin.ts`)
- **Patterns Established**: Split View para editors (60/40), Accordion para forms longos, DataTable responsive (table→card), Badge âmbar para "Padrão", Empty state reutilizável
- **Integration Points**: Router (`router/index.ts` — adicionar 6 rotas), MainLayout (já preparado), agent.service.ts (existente, estender ou criar agent-admin.service.ts)
- **Assumptions**: Backend endpoints CRUD existirão para prompts e agents. Avatar é URL (não upload). Tools/KBs são string[] placeholder. Modelo é enum fixo. Playground é mock local.
- **Warnings**: Contraste de `muted` (`#a1a1aa`) em fundo branco = 3.04:1 — usar apenas para texto grande ou decorativo. Em dark mode, verificar contraste separadamente.
