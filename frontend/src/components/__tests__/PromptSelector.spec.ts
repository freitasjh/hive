import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PrimeVue from 'primevue/config'
import Tooltip from 'primevue/tooltip'
import PromptSelector from '../PromptSelector.vue'
import { promptService } from '../../services/prompt.service'

vi.mock('../../services/prompt.service', () => ({
  promptService: {
    list: vi.fn()
  },
  default: {
    list: vi.fn()
  }
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

interface FakePrompt {
  id: string
  name: string
  code: string
  description?: string
  status?: string
  /** Presente nas opções do seletor: é o termo que a trouxe, e o que o filtro local casa. */
  searchTerm?: string
}

function page(content: FakePrompt[], totalElements = content.length) {
  return { content, totalElements, totalPages: 1, number: 0, size: 50 }
}

/**
 * Todo wrapper fica **anexado ao `document.body`**.
 *
 * Sem `attachTo`, o vue-test-utils monta numa árvore solta e `document.querySelector` não enxerga a
 * raiz do componente — só o overlay teleportado. Aí o teste passa a consultar duas metades diferentes
 * do mesmo componente, e produz asserções que não descrevem nada. Com `attachTo`, o DOM do
 * componente e o do dropdown são a mesma árvore, como no browser.
 */
const mounted: Array<ReturnType<typeof mount>> = []

function mountSelector(props: Record<string, unknown> = {}) {
  const wrapper = mount(PromptSelector, {
    attachTo: document.body,
    props: { modelValue: null, ...props },
    global: {
      plugins: [PrimeVue],
      directives: { tooltip: Tooltip },
      mocks: { $router: { push } }
    }
  })
  mounted.push(wrapper)
  return wrapper
}

type SelectorVm = {
  onFilter: (e: { value: string }) => void
  mode: string
  searchPhase: string
  searchError: string | null
  isSearchPending: boolean
  hasStaleList: boolean
  statusText: string
  showsNoResults: boolean
  options: FakePrompt[]
  retrySearch: () => void
  load: () => Promise<void>
}

/** Espera o debounce de 400 ms do `useDebouncedSearch` e deixa a requisição resolver. */
async function settle(ms = 450) {
  await new Promise(resolve => setTimeout(resolve, ms))
  await flushPromises()
}

/** Espera só o relógio do debounce. */
async function waitDebounce(ms = 450) {
  await new Promise(resolve => setTimeout(resolve, ms))
  await flushPromises()
}

/**
 * Abre o painel. O dropdown do PrimeVue é teleportado para `document.body`, então nada dentro dele
 * existe no HTML do componente até estar aberto — e é dentro dele que mora o cabeçalho de modo, o
 * rodapé e as opções.
 */
async function openPanel(wrapper: ReturnType<typeof mountSelector>) {
  // Idempotente de propósito: clicar num painel já aberto o fecha, e o teste precisa poder olhar
  // para o mesmo painel duas vezes (antes e depois de uma busca).
  const alreadyOpen = document.body.querySelector('.p-select-overlay') !== null
  if (!alreadyOpen) {
    await wrapper.find('[data-testid="prompt-selector"]').trigger('click')
    await flushPromises()
  }
  return document.body
}

/**
 * Digita no input real do PrimeVue, e não numa chamada de método.
 *
 * Este é o ponto do arquivo. A versão anterior chamava `onFilter({ value })` direto e inspecionava
 * o `computed` de `options` — provava o invariante que eu escrevi no código contra o invariante que
 * eu escrevi no código, e passou verde **com a neutralização removida**. Dirigir o input de verdade
 * é o que faz o PrimeVue passar a lista por `FilterService.filter`; só aí o teste enxerga o efeito.
 */
async function typeInSearchBox(value: string) {
  const input = document.body.querySelector<HTMLInputElement>('input[role="searchbox"]')
  if (!input) throw new Error('input de busca do PrimeVue não encontrado — o painel está aberto?')
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

/**
 * Quantas opções o usuário está vendo de fato. Efeito, não estado interno.
 *
 * O `.p-select-empty-message` é EXCLUÍDO de propósito: o PrimeVue renderiza o `<li role="option">` do
 * `#emptyfilter` (o que a gente sobrescreve) com `role="option"` e sem conteúdo, e ele conta como
 * opção. Sem esta exclusão, um teste que só afirma contagem passaria com **1 opção real** — que é
 * exatamente o número do bug original. Por isso os testes que importam usam `visibleOptionNames()`
 * (conteúdo) junto com a contagem.
 */
function visibleOptionCount() {
  return document.body
    .querySelectorAll('.p-select-overlay li[role="option"]:not(.p-select-empty-message)')
    .length
}

function visibleOptionNames() {
  return Array.from(document.body.querySelectorAll('.p-select-overlay li[role="option"]'))
    .map(li => li.querySelector('.font-semibold')?.textContent?.trim() ?? '')
}

function panelText() {
  return document.body.querySelector('.p-select-overlay')?.textContent ?? ''
}

/** A classe que aciona o esmaecimento: o que a UI mostra, não o que o componente guarda. */
function listIsDimmed() {
  return document.querySelector('[data-testid="prompt-selector"].is-searching') !== null
}

describe('PromptSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    // Desmontar antes de limpar: o dropdown do PrimeVue é teleportado para `document.body` e o
    // vue-test-utils não desmonta sozinho. Sem isto o painel de um teste continua no DOM do
    // seguinte e as consultas por `data-testid` encontram o painel errado.
    while (mounted.length) {
      const wrapper = mounted.pop()
      wrapper?.unmount()
    }
    document.body.innerHTML = ''
  })

  it('lists the tenant prompts asking for the biggest page the server allows', async () => {
    // Um seletor que esconde o prompt que o usuário quer atrás de uma paginação que a tela não
    // oferece é um seletor quebrado. O teto é 50 porque `PromptTemplateControllerV1` responde 400
    // para size > 50 — pedir 100 daria 400 em toda montagem (achado no harness com browser real,
    // 2026-09-27; nenhum teste unitário pegaria, porque ambos aceitariam qualquer número).
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    mountSelector()
    await flushPromises()
    expect(promptService.list).toHaveBeenCalledWith({ page: 0, size: 50 })
  })

  it('emits the chosen prompt template id', async () => {
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    const wrapper = mountSelector()
    await flushPromises()
    ;(wrapper.vm as unknown as SelectorVm).options
    const vm = wrapper.vm as unknown as SelectorVm & { onSelect: (v: string | null) => void }
    vm.onSelect('p1')
    expect(wrapper.emitted('update:modelValue')).toEqual([['p1']])
  })

  it('resolves the name of a template that is not in the loaded page', async () => {
    // Uma sessão antiga pode apontar para um template que não veio na primeira página. Mostrar o id
    // cru é feio, mas silenciar o selector seria pior: o usuário não veria qual prompt está ativo.
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    const wrapper = mountSelector({ modelValue: 'p1' })
    await flushPromises()
    expect(wrapper.text()).toContain('Atendente')
  })

  it('says there is nothing to choose instead of rendering an empty dropdown', async () => {
    // Um Select vazio sem explicação parece um bug de carregamento.
    vi.mocked(promptService.list).mockResolvedValue(page([]) as never)
    const wrapper = mountSelector()
    await flushPromises()
    expect(wrapper.text()).toContain('Nenhum prompt criado ainda')
  })

  it('shows the error and reloads on retry', async () => {
    vi.mocked(promptService.list).mockRejectedValueOnce(new Error('500 do servidor'))
    const wrapper = mountSelector()
    await flushPromises()
    expect(wrapper.text()).toContain('500 do servidor')
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    await (wrapper.vm as unknown as SelectorVm).load()
    await flushPromises()
    expect(promptService.list).toHaveBeenCalledTimes(2)
  })

  it('keeps the field on screen when loading the recents fails, so an open conversation still shows its prompt', async () => {
    // O campo sumindo é o pior desfecho possível aqui: com uma sessão aberta, o usuário deixa de
    // ver qual prompt está respondendo e a conversa continua invisivelmente em outro prompt.
    vi.mocked(promptService.list)
      .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
      .mockRejectedValueOnce(new Error('500 do servidor'))
    const wrapper = mountSelector({ modelValue: 'p1' })
    await flushPromises()
    await (wrapper.vm as unknown as SelectorVm).load()
    await flushPromises()

    expect(wrapper.text()).toContain('500 do servidor')
    expect(wrapper.findComponent({ name: 'Select' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('Atendente')
  })

  it('passes disabled through, so a conversation with messages cannot be re-targeted', async () => {
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    const wrapper = mountSelector({ disabled: true })
    await flushPromises()
    const select = wrapper.findComponent({ name: 'Select' })
    expect(select.props('disabled')).toBe(true)
  })

  it('explains why the field is locked instead of only greying it out', async () => {
    // `:disabled="!!sessionId"` sem cadeado e sem motivo não é affordance, é bug: o usuário não sabe
    // se está quebrado ou travado, e a regra de prompt imutável por sessão fica invisível.
    vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
    const wrapper = mountSelector({ modelValue: 'p1', disabled: true })
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('pi-lock')
    expect(html).toContain('O prompt fica fixo nesta conversa. Para usar outro, inicie uma nova conversa.')
    // O Tooltip do PrimeVue só existe como binding; `data-pd-tooltip` prova que ele foi ligado.
    expect(html).toContain('data-pd-tooltip')
  })

  describe('busca com digitação', () => {
    it('sends the term to the server, so prompt #51 is reachable', async () => {
      // A regressão que motiva o componente existir: com 51+ prompts, o filtro client-side do
      // PrimeVue roda sobre os 50 já carregados, parece buscar e nunca encontra o #51. Busca que
      // mente é pior que nenhuma busca. O que prova a correção é o `q` indo ao servidor.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([{ id: 'p51', name: 'Auditoria', code: 'audit-51' }], 1) as never)

      const wrapper = mountSelector()
      await flushPromises()
      ;(wrapper.vm as unknown as SelectorVm).onFilter({ value: 'auditoria' })
      await settle()

      // `timeoutMs` vai junto: sem timeout, um GET pendurado deixa "Buscando…" preso para sempre.
      expect(promptService.list).toHaveBeenNthCalledWith(2, { page: 0, size: 20, q: 'auditoria', timeoutMs: 10000 })
      const vm = wrapper.vm as unknown as SelectorVm
      expect(vm.mode).toBe('search')
      expect(vm.options.map(o => o.id)).toEqual(['p51'])
    })

    it('waits 400 ms before asking, so half-typed words cost one request', async () => {
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      vm.onFilter({ value: 'at' })
      vm.onFilter({ value: 'ate' })
      vm.onFilter({ value: 'atendente' })

      expect(promptService.list).toHaveBeenCalledTimes(1)
      await settle()
      expect(promptService.list).toHaveBeenCalledTimes(2)
      expect(promptService.list).toHaveBeenLastCalledWith({ page: 0, size: 20, q: 'atendente', timeoutMs: 10000 })
    })

    it('does not search below two characters, which is noise and would empty the list', async () => {
      vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      vm.onFilter({ value: 'a' })
      await settle()
      expect(promptService.list).toHaveBeenCalledTimes(1)
      expect(vm.mode).toBe('recent')
    })

    it('goes back to the recents when the term drops below two characters', async () => {
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }]) as never)
        .mockResolvedValueOnce(page([{ id: 'p9', name: 'Auditoria', code: 'audit' }]) as never)
      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      vm.onFilter({ value: 'auditoria' })
      await settle()
      expect(vm.mode).toBe('search')
      vm.onFilter({ value: 'a' })
      expect(vm.mode).toBe('recent')
    })

    it('shows every option the server returned, typing in the real PrimeVue input', async () => {
      // 🔴 A BARREIRA DE TESTE DA NEUTRALIZAÇÃO. Este teste falha se `:filterFields` sai.
      //
      // A versão anterior dele chamava `onFilter` e olhava o `computed` de `options`: provava o
      // invariante que o código afirma sobre o código, e passou verde com a neutralização removida
      // (o PrimeVue caía em `searchFields=[optionLabel]=['name']` e mostrava 1 de 5). Aqui o input
      // real do PrimeVue é digitado, o componente passa a lista por `FilterService.filter`, e o que
      // se afirma é quantas opções estão visíveis no dropdown.
      // A fixture é feita para **discriminar**: o termo casa `description` e `code`, e NENHUM
      // `name`. O `q` do servidor acha os cinco por qualquer um dos três campos; o `filter`
      // padrão do PrimeVue, sem a neutralização, olha só `name` e apaga os cinco. Com `name`
      // contendo o termo o teste passaria com o bug de volta — foi exatamente o que aconteceu na
      // primeira versão deste teste.
      const serverResults = [
        { id: 'p51', name: 'Auditoria A', code: 'fiscal-a', description: 'Relatório fiscal trimestral' },
        { id: 'p52', name: 'Auditoria B', code: 'fiscal-b', description: 'Relatório fiscal anual' },
        { id: 'p53', name: 'Auditoria C', code: 'fiscal-c', description: 'Relatório fiscal mensal' },
        { id: 'p54', name: 'Auditoria D', code: 'fiscal-d', description: 'Relatório fiscal semanal' },
        { id: 'p55', name: 'Auditoria E', code: 'fiscal-e', description: 'Relatório fiscal diário' }
      ]
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page(serverResults, 5) as never)

      const wrapper = mountSelector()
      await flushPromises()
      await openPanel(wrapper)
      await typeInSearchBox('fiscal')
      await settle()

      expect(visibleOptionCount()).toBe(5)
      expect(visibleOptionNames()).toEqual(['Auditoria A', 'Auditoria B', 'Auditoria C', 'Auditoria D', 'Auditoria E'])
    })

    it('keeps every option visible when the typed term is a substring of the stamped one', async () => {
      // O termo é embutido NA opção, então a comparação é `termo-da-opção contém o que foi digitado`
      // (ver `FilterService.contains` em `@primevue/core/api`). Isso é o que torna a neutralização
      // robusta: mesmo um termo que não é substring do nome — e que o `filter` padrão eliminaria
      // inteiro — não apaga nada.
      const twoResults = page([
        { id: 'p51', name: 'Auditoria A', code: 'audit-a' },
        { id: 'p52', name: 'Auditoria B', code: 'audit-b' }
      ], 2)
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        // Default: este teste é sobre a neutralização, não sobre a contabilidade de mocks. Cada
        // termo novo re-consulta o servidor e recebe a MESMA lista — que é o cenário que interessa.
        .mockResolvedValue(twoResults as never)

      const wrapper = mountSelector()
      await flushPromises()
      await openPanel(wrapper)
      // Termos que NÃO casariam com nenhum campo do prompt — o bug original apagaria as duas opções.
      await typeInSearchBox('xyzqq')
      await settle()
      expect(visibleOptionCount()).toBe(2)

      await typeInSearchBox('uditoria')
      await settle()
      expect(visibleOptionCount()).toBe(2)
    })

    it('offers create-or-browse instead of a dead end when the search finds nothing', async () => {
      // "0 resultados" é beco sem saída. Quem não achou precisa poder criar o que procurava ou ver
      // a lista inteira — e nunca ver um número cru.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([], 0) as never)

      const wrapper = mountSelector()
      await flushPromises()
      ;(wrapper.vm as unknown as SelectorVm).onFilter({ value: 'fiscal' })
      await settle()

      const body = await openPanel(wrapper)
      expect(panelText()).toContain('Nenhum prompt para')
      expect(panelText()).toContain('fiscal')
      expect(body.querySelector('[data-testid="create-prompt-action"]')).not.toBeNull()
      expect(body.textContent).not.toContain('0 resultado')
      // O PrimeVue escreve "nenhum resultado encontrado" no slot dele; o texto do projeto nunca é
      // um número cru, porque número cru é beco sem saída.
      expect(body.textContent).not.toContain('Nenhum resultado encontrado')
    })

    it('keeps the options and reports the failure as a footer when the search fails', async () => {
      // Uma busca que falhou não pode apagar o que já estava à vista, nem virar o dropdown inteiro.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([{ id: 'p9', name: 'Auditoria', code: 'audit' }], 1) as never)
        .mockRejectedValueOnce(new Error('gateway estourou'))

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      vm.onFilter({ value: 'auditoria' })
      await settle()
      expect(vm.options.map(o => o.id)).toEqual(['p9'])

      vm.onFilter({ value: 'auditoriaFiscal' })
      await settle()
      // A lista continua no lugar...
      expect(vm.options.map(o => o.id)).toEqual(['p9'])
      // ...e a falha vira rodapé, não o dropdown inteiro.
      const body = await openPanel(wrapper)
      expect(body.textContent).toContain('Não foi possível buscar')
      expect(body.textContent).toContain('Tentar novamente')
    })

    it('makes the ceiling of 50 legible instead of silently truncating the list', async () => {
      // 50 é o teto do contrato do servidor. Sem rodapé, o usuário infere que existem exatamente 50.
      vi.mocked(promptService.list).mockResolvedValue(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 137) as never)
      const wrapper = mountSelector()
      await flushPromises()
      const body = await openPanel(wrapper)
      expect(body.querySelector('[data-testid="see-all-prompts"]')?.textContent).toContain('137')
    })

    it('does not declare absence while the first search is still in flight', async () => {
      // 🔴 A PRIMEIRA BUSCA NÃO PODE MENTIR. `searchResults` começa vazio, então "lista vazia" e
      // "nada achado" eram a mesma coisa: no instante da digitação o cabeçalho declarava
      // `Nenhum prompt para "auditoria"` e oferecia "Criar prompt" antes de a pergunta ter resposta.
      let resolveSearch: (v: unknown) => void = () => {}
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockImplementationOnce(() => new Promise(resolve => { resolveSearch = resolve as (v: unknown) => void }))

      const wrapper = mountSelector()
      await flushPromises()
      await openPanel(wrapper)
      await typeInSearchBox('auditoria')

      // Antes de o debounce vencer: modo já é busca, fase pendente, e nada é afirmado.
      let vm = wrapper.vm as unknown as SelectorVm
      expect(vm.mode).toBe('search')
      expect(vm.searchPhase).toBe('pending')
      expect(vm.isSearchPending).toBe(true)
      expect(vm.statusText).toBe('Buscando…')
      expect(panelText()).not.toContain('Nenhum prompt')
      expect(wrapper.find('[data-testid="create-prompt-action"]').exists()).toBe(false)

      // Depois do debounce, com a requisição ainda no ar: a mesma frase, e ainda sem afirmar nada.
      await waitDebounce()
      vm = wrapper.vm as unknown as SelectorVm
      expect(vm.searchPhase).toBe('pending')
      expect(vm.statusText).toBe('Buscando…')
      expect(panelText()).not.toContain('Nenhum prompt')
      expect(wrapper.find('[data-testid="create-prompt-action"]').exists()).toBe(false)
      // E a a11y conta que a região está ocupada durante a janela inteira, não só depois do request.
      expect(wrapper.find('[data-testid="prompt-selector"]').attributes('aria-busy')).toBe('true')

      // Só quando a resposta chega a ausência pode ser afirmada — e aí ela é verdadeira.
      resolveSearch(page([], 0) as unknown)
      await flushPromises()
      vm = wrapper.vm as unknown as SelectorVm
      expect(vm.searchPhase).toBe('settled')
      expect(vm.isSearchPending).toBe(false)
      expect(vm.statusText).toBe('Nenhum prompt para "auditoria"')
    })

    it('keeps the previous results dimmed while a later search is in flight, and never says "none"', async () => {
      // Da segunda busca em diante há lista anterior: ela fica visível e esmaecida (o parecer, §1.3)
      // e o cabeçalho diz que está buscando. O que não pode é virar "nenhum prompt" — a lista está
      // cheia, só é de outra pergunta.
      let resolveSearch: (v: unknown) => void = () => {}
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([
          { id: 'p51', name: 'Auditoria A', code: 'audit-a' },
          { id: 'p52', name: 'Auditoria B', code: 'audit-b' }
        ], 2) as never)
        .mockImplementationOnce(() => new Promise(resolve => { resolveSearch = resolve as (v: unknown) => void }))

      const wrapper = mountSelector()
      await flushPromises()
      await openPanel(wrapper)
      await typeInSearchBox('auditoria')
      await settle()
      expect(visibleOptionCount()).toBe(2)

      await typeInSearchBox('auditoria fiscal')
      await settle()

      expect(listIsDimmed()).toBe(true)
      expect(visibleOptionCount()).toBe(2)
      const vm = wrapper.vm as unknown as SelectorVm
      expect(vm.statusText).toBe('Buscando…')
      expect(panelText()).not.toContain('Nenhum prompt')
      expect(wrapper.find('[data-testid="create-prompt-action"]').exists()).toBe(false)

      resolveSearch(page([{ id: 'p53', name: 'Auditoria Fiscal A', code: 'audit-fisc' }], 1) as unknown)
      await flushPromises()
      expect(listIsDimmed()).toBe(false)
      expect(visibleOptionCount()).toBe(1)
    })

    it('does not leak the loading state when the term drops mid-request', async () => {
      // 🔴 `searchLoading` VAZAVA PARA TRUE PERMANENTE. O backspace invalidava a busca em voo
      // (`searchSeq++`) mas não fechava a janela; o `finally` só limpava com o `seq` corrente, que
      // depois da invalidação nunca mais voltava a ser verdade. Resultado: os Recentes ficavam
      // permanentemente em `opacity: 0.5` e o combobox anunciado como ocupado para sempre.
      let resolveSearch: (v: unknown) => void = () => {}
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockImplementationOnce(() => new Promise(resolve => { resolveSearch = resolve as (v: unknown) => void }))

      const wrapper = mountSelector()
      await flushPromises()
      await openPanel(wrapper)
      await typeInSearchBox('auditoria')
      await settle()

      // Backspace até abaixo do mínimo, com a requisição ainda no ar.
      await typeInSearchBox('a')
      await flushPromises()

      let vm = wrapper.vm as unknown as SelectorVm
      expect(vm.mode).toBe('recent')
      expect(vm.searchPhase).toBe('idle')
      expect(vm.isSearchPending).toBe(false)
      expect(listIsDimmed()).toBe(false)
      expect(wrapper.find('[data-testid="prompt-selector"]').attributes('aria-busy')).toBe('false')

      // Agora a resposta invalidada chega. Ela NÃO pode reescrever o estado.
      resolveSearch(page([{ id: 'p51', name: 'Auditoria A', code: 'audit-a' }], 1) as unknown)
      await flushPromises()
      await waitDebounce()

      vm = wrapper.vm as unknown as SelectorVm
      expect(vm.isSearchPending).toBe(false)
      expect(vm.searchPhase).toBe('idle')
      expect(listIsDimmed()).toBe(false)
      expect(wrapper.find('[data-testid="prompt-selector"]').attributes('aria-busy')).toBe('false')
      // E a lista voltou a ser a dos Recentes, não a da busca invalidada.
      expect(vm.mode).toBe('recent')
      expect(visibleOptionCount()).toBe(1)
      expect(visibleOptionNames()).toEqual(['Atendente'])
    })

    it('reads the ceiling from the real Spring page shape, not from the page length', async () => {
      // O `totalElements` do Spring 3+ vem aninhado em `page`. Quando o seletor lia `page.totalElements`
      // de um objeto achatado (que é o que os mocks usam), o `N` caía no comprimento da página, o rodapé
      // comparava 50 com 50 e **nunca aparecia** — o teto de 50 ficava silencioso, que é exatamente o
      // que o rodapé existe para impedir. Achado no H3 com o backend no ar.
      vi.mocked(promptService.list).mockResolvedValue({
        content: Array.from({ length: 50 }, (_, i) => ({ id: `p${i}`, name: `Prompt ${i}`, code: `code-${i}` })),
        totalElements: 56,
        totalPages: 2,
        number: 0,
        size: 50
      } as never)

      const wrapper = mountSelector()
      await flushPromises()
      const body = await openPanel(wrapper)

      expect(visibleOptionCount()).toBe(50)
      expect(body.querySelector('[data-testid="see-all-prompts"]')?.textContent).toContain('56')
    })

    it('clears the error on a successful retry: the footer goes and the live region stops apologising', async () => {
      // 🔴 REGRESSÃO. `runSearch` tem DOIS caminhos de entrada — o debounce (que passa por `onFilter`)
      // e o `retrySearch` (que chama direto). A limpeza do erro vivia só em `onFilter`, então um retry
      // bem-sucedido trazia a lista nova **com o rodapé de erro ainda na tela** e a live region
      // continuando anunciando "Não foi possível buscar". O conserto mora no `runSearch` porque ele é o
      // único ponto por onde uma busca começa.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([{ id: 'p9', name: 'Auditoria A', code: 'audit-a' }], 1) as never)
        .mockRejectedValueOnce(new Error('gateway estourou') as never)

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      await openPanel(wrapper)
      await typeInSearchBox('auditoria')
      await settle()
      expect(visibleOptionCount()).toBe(1)

      // Segunda busca falha.
      await typeInSearchBox('auditoria fiscal')
      await settle()
      let body = await openPanel(wrapper)
      expect(body.textContent).toContain('Não foi possível buscar')
      expect(vm.statusText).toBe('Não foi possível buscar.')

      // Retry, que agora SUCCEEDE.
      vi.mocked(promptService.list).mockResolvedValueOnce(
        page([
          { id: 'p11', name: 'Auditoria Fiscal A', code: 'audit-fisc-a' },
          { id: 'p12', name: 'Auditoria Fiscal B', code: 'audit-fisc-b' }
        ], 2) as never
      )
      await vm.retrySearch()
      await flushPromises()

      // O rodapé de erro sumiu...
      body = await openPanel(wrapper)
      expect(body.textContent).not.toContain('Não foi possível buscar')
      expect(wrapper.text()).not.toContain('Não foi possível buscar')
      // ...e a live region parou de anunciar falha.
      expect(vm.statusText).toBe('2 resultados')
      expect(vm.statusText).not.toContain('Não foi possível')
      // E os resultados novos estão lá.
      expect(visibleOptionCount()).toBe(2)
    })

    it('shows the loading treatment while a RETRY is in flight, not only while typing', async () => {
      // A mesma auditoria, na parte invisível: se o `pending` só é setado por `onFilter`, o retry
      // dispara com a fase `settled` — e a lista não esmaece e `aria-busy` fica `false` com um
      // request de verdade em andamento. O mesmo descaso que a fase existe para evitar, no caminho
      // que ninguém exercita a mão.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([{ id: 'p9', name: 'Auditoria A', code: 'audit-a' }], 1) as never)
        .mockRejectedValueOnce(new Error('gateway estourou') as never)

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      await openPanel(wrapper)
      await typeInSearchBox('auditoria')
      await settle()
      await typeInSearchBox('auditoria fiscal')
      await settle()
      expect(vm.searchError).not.toBeNull()
      expect(vm.isSearchPending).toBe(false)

      let resolveRetry: (v: unknown) => void = () => {}
      vi.mocked(promptService.list).mockImplementationOnce(
        () => new Promise(resolve => { resolveRetry = resolve as (v: unknown) => void })
      )
      vm.retrySearch()
      await flushPromises()

      expect(vm.isSearchPending).toBe(true)
      expect(listIsDimmed()).toBe(true)
      expect(wrapper.find('[data-testid="prompt-selector"]').attributes('aria-busy')).toBe('true')

      resolveRetry(page([{ id: 'p11', name: 'Auditoria Fiscal A', code: 'audit-fisc-a' }], 1) as unknown)
      await flushPromises()
      expect(vm.isSearchPending).toBe(false)
      expect(listIsDimmed()).toBe(false)
    })

    it('counts the search total, not the tenant total, in the footer', async () => {
      // Quem acabou de filtrar por "auditoria" e viu 4 de 4 não quer ler "ver todos os 137" — 137 é
      // a contagem de outro universo, e o rodapé existe para tornar o teto legível.
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 137) as never)
        .mockResolvedValueOnce(page([
          { id: 'p51', name: 'Auditoria A', code: 'audit-a' },
          { id: 'p52', name: 'Auditoria B', code: 'audit-b' }
        ], 4) as never)

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      vm.onFilter({ value: 'auditoria' })
      await settle()
      const body = await openPanel(wrapper)
      const footer = body.querySelector('[data-testid="see-all-prompts"]')?.textContent
      expect(footer).toContain('4')
      expect(footer).not.toContain('137')
    })

    it('distinguishes the two modes by header, so the user knows what they are looking at', async () => {
      vi.mocked(promptService.list)
        .mockResolvedValueOnce(page([{ id: 'p1', name: 'Atendente', code: 'atend' }], 51) as never)
        .mockResolvedValueOnce(page([{ id: 'p51', name: 'Auditoria', code: 'audit' }], 1) as never)

      const wrapper = mountSelector()
      await flushPromises()
      const vm = wrapper.vm as unknown as SelectorVm
      expect(vm.mode).toBe('recent')
      let body = await openPanel(wrapper)
      expect(body.textContent).toContain('Prompts recentes')

      vm.onFilter({ value: 'auditoria' })
      await settle()
      expect(vm.mode).toBe('search')
      body = await openPanel(wrapper)
      expect(body.textContent).toContain('Resultados da busca')
      // E nunca os dois no mesmo lugar: misturar "recentes" com resultado de busca faz o usuário
      // não saber o que está vendo.
      expect(body.textContent).not.toContain('Prompts recentes')
    })

    it('renders name, code, description and tags a non-ACTIVE status', async () => {
      // Com 50 itens, nomes parecidos ("Resumir", "Resumir documento") não se distinguem pelo nome.
      // O discriminador é o `code`; e escolher um ARCHIVED sem saber é escolher às cegas.
      vi.mocked(promptService.list).mockResolvedValue(page([
        { id: 'p1', name: 'Resumir documento', code: 'summarize-doc', description: 'Extrai os pontos principais', status: 'ACTIVE' },
        { id: 'p2', name: 'Resumir contrato', code: 'summarize-contract', description: 'Cláusulas e riscos', status: 'ARCHIVED' }
      ]) as never)
      const wrapper = mountSelector()
      await flushPromises()
      const body = await openPanel(wrapper)
      const html = body.innerHTML
      expect(html).toContain('summarize-contract')
      expect(html).toContain('summarize-doc')
      expect(html).toContain('ARCHIVED')
      // ACTIVE é o estado normal de quase tudo: tag em 50 itens é ruído (regra de Hick).
      expect(html).not.toContain('>ACTIVE<')
    })
  })
})
