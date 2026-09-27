<template>
  <div class="prompt-selector">
    <!-- Erro ACIMA do campo, nunca no lugar dele. Uma conversa aberta continua respondida por um
         prompt, e esconder o campo some justamente com o dado que o usuário precisa ver. -->
    <Message v-if="error" severity="error" :closable="false" class="m-0 mb-2">
      {{ error }}
      <Button label="Tentar novamente" text size="small" class="ml-2" @click="() => load()" />
    </Message>

    <div v-if="loading" class="flex flex-column gap-2">
      <Skeleton height="2.5rem" borderRadius="12px" />
    </div>
    <Message v-else-if="prompts.length === 0" severity="info" :closable="false" class="m-0">
      Nenhum prompt criado ainda. Crie um em <strong>Prompts</strong> para começar a conversar.
    </Message>
    <Select
      v-else
      v-tooltip.top="disabled ? LOCKED_EXPLANATION : null"
      :modelValue="modelValue"
      :options="options"
      optionLabel="name"
      optionValue="id"
      placeholder="Escolha um prompt"
      class="w-full"
      :class="{ 'is-searching': hasStaleList }"
      :aria-busy="isSearchPending"
      :disabled="disabled || loading"
      :loading="loading"
      filter
      :filterFields="['searchTerm']"
      filterMatchMode="contains"
      filterPlaceholder="Busque por nome, código ou descrição"
      :resetFilterOnHide="false"
      :filterMessage="SILENT_LIVE_REGION"
      :emptyFilterMessage="SILENT_LIVE_REGION"
      :emptyMessage="SILENT_LIVE_REGION"
      :emptySelectionMessage="SILENT_LIVE_REGION"
      data-testid="prompt-selector"
      @filter="onFilter"
      @update:modelValue="onSelect"
    >
      <template #header>
        <div class="selector-header">
          <span class="selector-mode">{{ modeLabel }}</span>

          <!-- A única afirmação sobre o resultado da busca. Ela só fala depois que a resposta
               chegou: durante o pedido vira "Buscando…", e dizer "nenhum prompt" antes de a
               resposta existir seria a mesma busca que mente, com mais confiança. -->
          <span class="selector-status" :class="{ 'is-pending': isSearchPending }">
            <i v-if="isSearchPending" class="pi pi-spin pi-spinner text-xs"></i>
            {{ statusText }}
          </span>

          <div v-if="showsNoResults" class="selector-actions">
            <Button
              :label="createPromptLabel"
              icon="pi pi-plus"
              text
              size="small"
              data-testid="create-prompt-action"
              @click="goToPrompts('/prompts/new')"
            />
            <Button label="Ver todos os prompts" text size="small" @click="goToPrompts('/prompts')" />
          </div>
        </div>
      </template>

      <template #value="slotProps">
        <div v-if="slotProps.value" class="flex align-items-center gap-2">
          <i class="pi pi-file-edit text-primary"></i>
          <span>{{ nameOf(slotProps.value) }}</span>
          <i
            v-if="disabled"
            class="pi pi-lock text-surface-500"
            :title="LOCKED_EXPLANATION"
            :aria-label="LOCKED_EXPLANATION"
          ></i>
        </div>
        <span v-else class="text-surface-500">Escolha um prompt</span>
      </template>

      <template #option="slotProps">
        <div class="flex flex-column option-anatomy">
          <div class="flex align-items-center justify-content-between gap-2">
            <span class="font-semibold text-sm">{{ slotProps.option.name }}</span>
            <!-- ACTIVE em 50 itens é ruído; ARCHIVED é informação crítica — o usuário está escolhendo
                 um template arquivado sem saber. -->
            <Tag
              v-if="slotProps.option.status && slotProps.option.status !== 'ACTIVE'"
              :value="slotProps.option.status"
              :severity="slotProps.option.status === 'ARCHIVED' ? 'secondary' : 'warn'"
              class="option-status"
            />
          </div>
          <div class="flex align-items-baseline gap-2 option-subline">
            <span class="font-mono text-xs">{{ slotProps.option.code }}</span>
            <span
              v-if="slotProps.option.description"
              class="text-xs text-surface-500 option-description"
              :title="slotProps.option.description"
            >{{ slotProps.option.description }}</span>
          </div>
        </div>
      </template>

      <!-- O PrimeVue escreve "nenhum resultado encontrado" aqui. Um dead end é proibido: quem não
           achou precisa poder criar ou ver todos, e quem não achou NÃO é a mesma coisa que a lista
           estar vazia. -->
      <template #emptyfilter><span class="hidden"></span></template>

      <template #footer>
        <div v-if="mode === 'search' && searchError" class="selector-footer selector-footer-error">
          <span>{{ statusText }}</span>
          <Button label="Tentar novamente" text size="small" @click="retrySearch" />
        </div>
        <div
          v-else-if="totalInView > shownCount"
          class="selector-footer"
          role="button"
          tabindex="0"
          data-testid="see-all-prompts"
          @click="goToPrompts('/prompts')"
          @keydown.enter="goToPrompts('/prompts')"
        >
          Ver todos os {{ totalInView }} prompts <i class="pi pi-arrow-right text-xs"></i>
        </div>
      </template>
    </Select>

    <!--
      Uma única região viva para o seletor, fora do cabeçalho visual.

      O PrimeVue publica três `role="status"` próprios — contagem do filtro, "sem opções" e
      "item selecionado" — com o texto do locale, que é **inglês** neste projeto, e nenhuma delas
      sabe que existe uma requisição em andamento: elas diriam "nenhum resultado encontrado" nos
      400ms em que a resposta ainda não chegou. Reduzi as três a um espaço (a prop é `||` contra o
      locale, então string vazia não silencia) e deixo esta região dizer a única coisa que importa,
      em pt-BR, uma vez por mudança de estado.
    -->
    <span class="visually-hidden" role="status" aria-live="polite" data-testid="selector-status">{{ statusText }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * The prompt a new conversation is answered by.
 *
 * It replaces the agent selector, and the difference is the point: an agent was a saved bundle of
 * prompt plus model plus limits, so choosing one in the chat also chose the model. The model is now
 * the tenant's, and the prompt is the only thing a conversation picks. Locked once a session exists,
 * because the session keeps the prompt it was opened with.
 *
 * Two modes, and the user has to know which one they are in:
 *
 * - **Recentes** — opens with no term typed. The 50 most recent. 50 is the server's hard ceiling
 *   (`PromptTemplateControllerV1` answers 400 above it), not a UI choice.
 * - **Busca** — from the 2nd character typed, `q` goes to the server: `GET /v1/prompt-templates`
 *   already filters by name, code and description, and returns `totalElements`.
 *
 * Why the server and not the `filter` prop PrimeVue offers: that filter runs over whatever is
 * already loaded, so with 51+ prompts the user types, the filter runs, and prompt #51 never appears.
 * A search that lies is worse than no search. Here the client-side filter is neutralised on purpose
 * — every option carries `searchTerm`, the exact term that produced it, and `filterFields` points at
 * that field, so the match is `term contains term` and nothing is ever narrowed locally. The
 * visible list is always exactly what the server returned.
 */
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { promptService } from '../services/prompt.service'
import { useDebouncedSearch } from '../composables/useDebouncedSearch'
import type { Page, PromptTemplateDTO } from '../types/prompt'

const props = defineProps<{
  modelValue: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const router = useRouter()

/** `PromptTemplateControllerV1` rejeita `size > 50` com 400. Este é o teto, não um palpite. */
const MAX_PROMPTS_IN_SELECTOR = 50
/** A busca devolve o suficiente para escolher; o resto fica a um clique, no rodapé. */
const SEARCH_PAGE_SIZE = 20
/** Abaixo de 2 caracteres a busca não é busca, é ruído — e 1 letra derruba a lista inteira. */
const MIN_SEARCH_LENGTH = 2
/**
 * Timeout da busca. Sem ele um GET pendurado deixa "Buscando…" e a lista esmaecida **para sempre**,
 * que é a única fase que ainda pode ficar presa. O padrão do axios é esperar indefinidamente — e o
 * fato de o `validate` precisar de `{timeout: 20000}` individual prova que o default é ∞. 10s é
 * folgado para um GET de 20 itens e curto o bastante para o usuário ter uma resposta.
 */
const SEARCH_TIMEOUT_MS = 10000

/**
 * Por que o campo está travado. Fica em uma constante só, e não em duas strings parecidas: a regra de
 * prompt imutável por sessão é invisível hoje, e um cadeado sem frase ao lado é só um ícone a menos.
 */
const LOCKED_EXPLANATION = 'O prompt fica fixo nesta conversa. Para usar outro, inicie uma nova conversa.'

/**
 * Sobrescreve as live regions do PrimeVue (contagem do filtro, "sem opções", "sem seleção") sem
 * anunciá-las. São **quatro**: as três primeiras caem no locale, que é **inglês** neste projeto, e a
 * quarta (`emptySelectionMessage`) só aparece quando nada está selecionado — que é exatamente o
 * estado inicial do seletor, ou seja, o primeiro que o usuário encontra.
 *
 * A prop é `props || locale.x`, então `''` **não** silencia: cairia no locale. Um espaço é
 * verdadeiro e não é fala. A única região que anuncia é a do componente, em pt-BR, uma vez.
 */
const SILENT_LIVE_REGION = ' '

type SelectorMode = 'recent' | 'search'

/**
 * Fase da busca. Existe porque "não achou nada" e "ainda não perguntou" são coisas diferentes, e
 * coladas elas o seletor afirma ausência antes de a resposta chegar — que é a busca que mente.
 *
 * - `idle`    — nada em andamento (recentes, ou termo abaixo do mínimo)
 * - `pending` — o usuário pediu e a resposta não chegou: vale o que está na tela, esmaecido
 * - `settled` — a resposta chegou; a lista e o total dela são o que o usuário está vendo
 */
type SearchPhase = 'idle' | 'pending' | 'settled'

/** O DTO mais o termo que o trouxe: o campo que o filtro client-side casa, e sempre casa. */
type SelectorOption = PromptTemplateDTO & { searchTerm: string }

const prompts = ref<PromptTemplateDTO[]>([])
const searchResults = ref<PromptTemplateDTO[]>([])
const totalPrompts = ref(0)
const totalSearchResults = ref(0)
const loading = ref(false)
const searchPhase = ref<SearchPhase>('idle')
const error = ref<string | null>(null)
const searchError = ref<string | null>(null)
const mode = ref<SelectorMode>('recent')

/**
 * `useDebouncedSearch` morava no projeto sem consumidor. Aqui ele vira o que sempre devia ter sido:
 * 400ms antes de gastar uma requisição com o que o usuário ainda está digitando.
 *
 * `query` é a fonte da verdade do termo — o mesmo ref que o componente usa para carimbar cada opção
 * com `searchTerm`. Importante que seja o MESMO ref: o invariante de "o filtro local nunca descarta
 * nada" depende de a opção carregar exatamente o termo que a produziu, e um segundo ref atrasado
 * quebraria isso.
 */
const { query: searchTerm, debounced } = useDebouncedSearch(400)

/** Nome de todo prompt já visto, para o valor selecionado continuar legível. */
const knownNames = ref(new Map<string, string>())

const trimmedSearch = computed(() => searchTerm.value.trim())

const createPromptLabel = computed(() => `Criar prompt "${trimmedSearch.value}"`)

const shownCount = computed(() => (mode.value === 'search' ? searchResults.value.length : prompts.value.length))

/**
 * O `N` do rodapé é o total do modo em que o usuário está. Na busca ele é o total *da busca* — dizer
 * "ver todos os 137" para quem acabou de filtrar por um termo que casou 4 seria uma contagem de outro
 * universo, e o rodapé existe justamente para tornar o teto legível.
 */
const totalInView = computed(() => (mode.value === 'search' ? totalSearchResults.value : totalPrompts.value))

const visibleList = computed(() => (mode.value === 'search' ? searchResults.value : prompts.value))

const options = computed<SelectorOption[]>(() =>
  visibleList.value.map(p => ({ ...p, searchTerm: searchTerm.value }))
)

/**
 * "O pedido está em andamento" cobre a janela inteira — os 400ms de debounce e a ida ao servidor.
 * Ligar isso só ao request faria a a11y e o esmaecimento começarem 400ms depois da tecla, que é
 * justamente a janela em que a lista na tela já não responde à pergunta do usuário.
 */
const isSearchPending = computed(() => mode.value === 'search' && searchPhase.value === 'pending')

/** Só esmaece o que existe: sem lista anterior, esmaecer nada é não esmaecer nada. */
const hasStaleList = computed(() => isSearchPending.value && options.value.length > 0)

/**
 * Ausência só é afirmada com a resposta na mão, sem erro, e com a lista realmente vazia. Qualquer
 * uma das três condições a menos devolve `false` — e o que aparece no lugar é "Buscando…" ou o
 * rodapé de erro, nunca "não achou".
 */
const showsNoResults = computed(
  () => mode.value === 'search' && searchPhase.value === 'settled' && !searchError.value && searchResults.value.length === 0
)

const modeLabel = computed(() => (mode.value === 'search' ? 'Resultados da busca' : 'Prompts recentes'))

/**
 * A frase que o usuário lê e o leitor de tela ouve — o mesmo texto nos dois, um lugar só.
 * No modo Recentes fica vazia: não há consulta para announce, e anunciar o rótodo do modo seria
 * repetir o que a lista já diz.
 */
const statusText = computed(() => {
  if (mode.value !== 'search') return ''
  if (isSearchPending.value) return 'Buscando…'
  if (searchError.value) return 'Não foi possível buscar.'
  if (searchResults.value.length === 0) return `Nenhum prompt para "${trimmedSearch.value}"`
  return searchResults.value.length === 1 ? '1 resultado' : `${searchResults.value.length} resultados`
})

function nameOf(id: string): string {
  return knownNames.value.get(id) ?? id
}

function onSelect(value: string | null) {
  emit('update:modelValue', value)
}

function rememberNames(list: PromptTemplateDTO[]) {
  for (const p of list) knownNames.value.set(p.id, p.name)
}

function goToPrompts(path: string) {
  router.push(path)
}

/** Geração da busca em andamento. Só a geração atual pode escrever estado. */
let searchSeq = 0

/**
 * Volta para o modo Recentes e cancela a busca em andamento.
 *
 * O `searchSeq++` invalida a resposta que ainda está no ar — e o `searchPhase = 'idle'` é o que
 * fecha a janela de carregamento. Sem ele, a resposta invalidada cai no `finally` com um `seq` velho,
 * não limpa o estado, e os Recentes ficam esmaecidos e anunciados como "ocupado" para sempre.
 */
function goBackToRecent() {
  mode.value = 'recent'
  searchPhase.value = 'idle'
  searchError.value = null
  searchSeq++
}

function onFilter(event: { value: string }) {
  searchTerm.value = event.value ?? ''
  if (trimmedSearch.value.length < MIN_SEARCH_LENGTH) {
    goBackToRecent()
    return
  }
  // A fase vira `pending` já na tecla, não quando o request sai: entre a tecla e a resposta a lista
  // na tela é de outra pergunta, e a UI tem que dizer isso durante os 400ms também.
  mode.value = 'search'
  searchPhase.value = 'pending'
  searchError.value = null
}

function applyPage(page: Page<PromptTemplateDTO>, into: SelectorMode) {
  const content = page.content ?? []
  rememberNames(content)
  if (into === 'search') {
    searchResults.value = content
    totalSearchResults.value = page.totalElements ?? content.length
  } else {
    prompts.value = content
    totalPrompts.value = page.totalElements ?? content.length
  }
}

/**
 * Executa uma busca. É o **único** lugar que começa uma busca — tanto o debounce quanto o
 * "Tentar novamente" chegam por aqui.
 *
 * Por isso o estado inicial da busca mora AQUI e não em `onFilter`: `retrySearch` chama este
 * método direto, sem passar por `onFilter`, e quando a limpeza do erro ficava só em `onFilter` o
 * retry bem-sucedido deixava o rodapé de erro na tela e a live region continuando anunciando
 * "Não foi possível buscar". Auditoria do mesmo formato: a fase também precisa ficar `pending`
 * aqui, senão durante o retry a lista não esmaece e `aria-busy` fica `false` com um request de
 * verdade em andamento — o mesmo descaso que a fase existe para evitar.
 *
 * O `seq` protege contra duas buscas cruzando no caminho: só a geração corrente escreve estado.
 */
async function runSearch(term: string) {
  const seq = ++searchSeq
  searchPhase.value = 'pending'
  searchError.value = null
  try {
    const page = await promptService.list({ page: 0, size: SEARCH_PAGE_SIZE, q: term, timeoutMs: SEARCH_TIMEOUT_MS })
    // Duas buscas podem cruzar no caminho. A que chegou depois é a que vale; a anterior, não.
    if (seq !== searchSeq) return
    applyPage(page, 'search')
    searchPhase.value = 'settled'
  } catch (e: unknown) {
    if (seq !== searchSeq) return
    // A lista anterior fica no lugar: uma busca que falhou não deve apagar o que já estava à vista.
    searchError.value = e instanceof Error ? e.message : 'Erro ao buscar prompts'
    // `settled` e não `pending`: nada está em andamento. E como `searchError` está setado,
    // `showsNoResults` é falso — o dropdown mostra a lista anterior e o rodapé de erro.
    searchPhase.value = 'settled'
  }
}

function retrySearch() {
  const term = trimmedSearch.value
  if (term.length < MIN_SEARCH_LENGTH) return
  runSearch(term)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    // 50, o MÁXIMO que o contrato do servidor aceita (size > 50 → 400), e não a página default de
    // 20: um seletor que esconde o prompt que o usuário quer atrás de uma paginação que a tela não
    // oferece é um seletor quebrado. Acima de 50 prompts o tenant recebe os 50 mais recentes — o
    // rodapé diz quantos existem no total, e a busca server-side alcança qualquer um deles.
    const page = await promptService.list({ page: 0, size: MAX_PROMPTS_IN_SELECTOR })
    applyPage(page, 'recent')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Erro ao carregar prompts'
  } finally {
    loading.value = false
  }
}

watch(debounced, (term) => {
  if (mode.value !== 'search') return
  if (term.trim().length < MIN_SEARCH_LENGTH) return
  runSearch(term.trim())
})

onMounted(load)

// Trocar de sessão libera o seletor; o pai desmarca o id e recarrega a lista para não deixar um
// template de conversa anterior marcado numa conversa nova.
watch(() => props.disabled, (isDisabled) => {
  if (isDisabled) load()
})

defineExpose({
  load,
  prompts,
  options,
  onFilter,
  mode,
  searchPhase,
  isSearchPending,
  hasStaleList,
  statusText,
  showsNoResults,
  runSearch,
  retrySearch,
})
</script>

<style scoped>
.prompt-selector :deep(.p-select) {
  border-radius: 12px;
  border-color: var(--p-content-border-color);
}
.prompt-selector :deep(.p-select:not(.p-disabled):hover) {
  /* `primary-400` é token de dark mode; em light o primário é indigo-900 e o hover sumia. */
  border-color: var(--p-primary-color);
}
.prompt-selector :deep(.p-select-overlay) {
  min-width: 26rem;
  max-width: 34rem;
}
/* Durante a busca as opções anteriores ficam visíveis e esmaecidas: o que o usuário já leu não
   some da frente dele enquanto o servidor responde. Só liga quando existe lista para esmaecer. */
.is-searching :deep(.p-select-list) {
  opacity: 0.5;
}

/* A região viva precisa existir para leitores de tela e não existir para o olho. O PrimeFlex
   deste projeto não traz `.sr-only`, então a classe é daqui — mesma técnica do
   `p-hidden-accessible` do PrimeVue. */
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* O cabeçalho distingue o modo: sem ele o usuário não sabe se está vendo os recentes ou o que a
   busca devolveu, e as duas listas pareciam a mesma coisa. */
.selector-header {
  padding: 0.5rem 0.75rem 0.375rem;
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  border-bottom: 1px solid var(--p-content-border-color);
}
.selector-mode {
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--p-surface-500);
}
.selector-status {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.875rem;
  color: var(--p-text-color);
}
/* "Buscando…" é a única frase que o cabeçalho pode dizer antes da resposta: ela não afirma nada
   sobre o resultado, então não mente. */
.selector-status.is-pending {
  color: var(--p-surface-500);
}
.selector-actions {
  display: flex;
  gap: 0.25rem;
  flex-wrap: wrap;
}
.selector-actions :deep(.p-button) {
  padding: 0.25rem 0.5rem;
}

/* O rodapé é um alvo, não uma linha de texto: é ele que torna o teto de 50 legível. */
.selector-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-height: 44px;
  padding: 0.5rem 0.75rem;
  border-top: 1px solid var(--p-content-border-color);
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--p-primary-color);
  cursor: pointer;
  background: transparent;
  border-left: 0;
  border-right: 0;
  border-bottom: 0;
  width: 100%;
}
.selector-footer:hover {
  background: var(--p-surface-100);
}
.selector-footer-error {
  color: var(--p-text-color);
  justify-content: space-between;
}

.option-anatomy {
  gap: 0.125rem;
  min-width: 0;
}
.option-subline {
  gap: 0.375rem;
  min-width: 0;
  color: var(--p-surface-500);
}
.option-description {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.option-status {
  flex-shrink: 0;
}
</style>
