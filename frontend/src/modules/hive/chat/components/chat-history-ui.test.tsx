import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import type { ChatMessageDTO, ChatSessionDTO } from "@/services/chat.types"
import { ChatThread } from "./ChatThread"
import { SessionSidebar } from "./SessionSidebar"

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

const mocks = vi.hoisted(() => ({
  scrollToMessage: vi.fn(),
  scrollToEnd: vi.fn(),
  scrollToStart: vi.fn(),
}))

vi.mock("@/components/shadcn/ui/message-scroller", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/shadcn/ui/message-scroller")>()
  return {
    ...actual,
    useMessageScroller: () => ({
      scrollToMessage: mocks.scrollToMessage,
      scrollToEnd: mocks.scrollToEnd,
      scrollToStart: mocks.scrollToStart,
    }),
  }
})

const S = "22222222-2222-2222-2222-222222222222"
const localDay = (reference: Date, days: number) =>
  new Date(reference.getFullYear(), reference.getMonth(), reference.getDate() - days, 12).toISOString()

const session = (overrides: Partial<ChatSessionDTO> = {}): ChatSessionDTO => ({
  id: S,
  userId: "u",
  promptTemplateId: "p",
  title: "Relatório mensal",
  createAt: localDay(new Date(), 0),
  updateAt: localDay(new Date(), 0),
  ...overrides,
})

const message: ChatMessageDTO = {
  id: "m-42",
  sessionId: S,
  role: "ASSISTANT",
  content: "Resumo do relatório trimestral",
  createAt: "2026-11-20T12:00:00Z",
}

const base = {
  selectedId: null as string | null,
  page: 0,
  totalPages: 1,
  onSelect: vi.fn(),
  onNew: vi.fn(),
  onDelete: vi.fn(),
  onRename: vi.fn(),
  onJumpToMessage: vi.fn(),
  onQueryChange: vi.fn(),
  onPageChange: vi.fn(),
}

describe("SessionSidebar — busca local", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem("hive_locale", "pt-BR")
  })

  it("filtra sessões por título e mostra a limitação da busca", () => {
    render(
      <SessionSidebar
        {...base}
        sessions={[session(), session({ id: S + "-2", title: "Checklist de viagem" })]}
        query="check"
      />
    )
    expect(screen.getByText(/checklist de viagem/i)).toBeTruthy()
    expect(screen.queryByText(/relatório mensal/i)).toBeNull()
    expect(screen.getByText(/busca local/i)).toBeTruthy()
    expect(screen.getByTestId("session-search")).toBeTruthy()
  })

  it("usa o resultado filtrado do controller quando fornecido", () => {
    render(
      <SessionSidebar
        {...base}
        sessions={[session(), session({ id: S + "-2", title: "Checklist de viagem" })]}
        filteredSessions={[session({ title: "Somente este" })]}
        query="qualquer"
      />
    )
    expect(screen.getByText("Somente este")).toBeTruthy()
    expect(screen.queryByText(/checklist de viagem/i)).toBeNull()
  })

  it("exibe mensagens carregadas que casam e salta para o messageId", () => {
    const onJumpToMessage = vi.fn()
    render(
      <SessionSidebar
        {...base}
        sessions={[session()]}
        query="resumo"
        matchingMessages={[message]}
        onJumpToMessage={onJumpToMessage}
      />
    )
    expect(screen.getByText(/mensagens encontradas/i)).toBeTruthy()
    const result = screen.getByTestId("session-message-result")
    expect(result.textContent).toContain("Resumo do relatório trimestral")
    fireEvent.click(result)
    expect(onJumpToMessage).toHaveBeenCalledWith("m-42")
  })

  it("estado vazio de busca é explícito e traduzido", () => {
    render(<SessionSidebar {...base} sessions={[session()]} query="sem resultado" />)
    expect(screen.getByText(/nenhum resultado para esta busca/i)).toBeTruthy()
  })
})

describe("SessionSidebar — agrupamento por data", () => {
  const reference = new Date()

  const datedSessions = [
    session({ id: "hoje", title: "Sessão A", updateAt: localDay(reference, 0) }),
    session({ id: "ontem", title: "Sessão B", updateAt: localDay(reference, 1) }),
    session({ id: "semana", title: "Sessão C", updateAt: localDay(reference, 4) }),
    session({ id: "antigo", title: "Sessão D", updateAt: localDay(reference, 9) }),
  ]

  it("pt-BR: Hoje / Ontem / Últimos 7 dias / Anteriores", () => {
    localStorage.setItem("hive_locale", "pt-BR")
    render(<SessionSidebar {...base} sessions={datedSessions} />)
    expect(screen.getByText("Hoje")).toBeTruthy()
    expect(screen.getByText("Ontem")).toBeTruthy()
    expect(screen.getByText("Últimos 7 dias")).toBeTruthy()
    expect(screen.getByText("Anteriores")).toBeTruthy()
  })

  it("en-US: Today / Yesterday / Last 7 days / Older", () => {
    localStorage.setItem("hive_locale", "en-US")
    render(<SessionSidebar {...base} sessions={datedSessions} />)
    expect(screen.getByText("Today")).toBeTruthy()
    expect(screen.getByText("Yesterday")).toBeTruthy()
    expect(screen.getByText("Last 7 days")).toBeTruthy()
    expect(screen.getByText("Older")).toBeTruthy()
  })
})

describe("SessionSidebar — ações destrutivas com confirmação", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.setItem("hive_locale", "pt-BR")
  })

  it("renomear abre diálogo e chama onRename com o novo título", async () => {
    const onRename = vi.fn().mockResolvedValue(true)
    render(<SessionSidebar {...base} sessions={[session()]} onRename={onRename} />)
    fireEvent.click(screen.getByRole("button", { name: /renomear conversa/i }))

    const dialog = await screen.findByRole("dialog")
    const field = within(dialog).getByLabelText(/título da conversa/i)
    fireEvent.change(field, { target: { value: "Novo título" } })
    fireEvent.click(within(dialog).getByRole("button", { name: /^salvar$/i }))

    await waitFor(() => expect(onRename).toHaveBeenCalledWith(S, "Novo título"))
  })

  it("nomes acessíveis de rename/delete identificam a conversa (sem botões idênticos)", () => {
    render(
      <SessionSidebar
        {...base}
        sessions={[
          session({ id: "s-a", title: "Sessão A" }),
          session({ id: "s-b", title: "Sessão B" }),
        ]}
      />
    )
    expect(screen.getByRole("button", { name: "Renomear conversa: Sessão A" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Renomear conversa: Sessão B" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Remover conversa: Sessão A" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Remover conversa: Sessão B" })).toBeTruthy()
    expect(screen.getAllByRole("button", { name: /^renomear conversa:/i })).toHaveLength(2)
    expect(screen.getAllByRole("button", { name: /^remover conversa:/i })).toHaveLength(2)
  })

  it("renomear com título vazio mantém o botão desabilitado", async () => {
    render(<SessionSidebar {...base} sessions={[session()]} />)
    fireEvent.click(screen.getByRole("button", { name: /renomear conversa/i }))
    const dialog = await screen.findByRole("dialog")
    fireEvent.change(within(dialog).getByLabelText(/título da conversa/i), { target: { value: "   " } })
    expect((within(dialog).getByRole("button", { name: /^salvar$/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("excluir exige confirmação antes de chamar onDelete", async () => {
    const onDelete = vi.fn()
    render(<SessionSidebar {...base} sessions={[session()]} onDelete={onDelete} />)
    fireEvent.click(screen.getByRole("button", { name: /remover conversa/i }))
    expect(onDelete).not.toHaveBeenCalled()

    const dialog = await screen.findByRole("dialog")
    expect(dialog.textContent).toContain("excluída permanentemente")
    fireEvent.click(within(dialog).getByRole("button", { name: /^excluir$/i }))
    expect(onDelete).toHaveBeenCalledWith(S)
  })
})

describe("ChatThread — salto para mensagem endereçável", () => {
  beforeEach(() => vi.clearAllMocks())

  const threadBase = {
    messages: [message],
    onLoadOlder: vi.fn(),
    onRetry: vi.fn(),
    onStarter: vi.fn(),
  }

  it("chama scrollToMessage quando o controller pede e limpa o alvo", async () => {
    const onTargetHandled = vi.fn()
    render(<ChatThread {...threadBase} targetMessageId="m-42" onTargetHandled={onTargetHandled} />)
    await waitFor(() =>
      expect(mocks.scrollToMessage).toHaveBeenCalledWith("m-42", expect.objectContaining({ align: "start" }))
    )
    expect(onTargetHandled).toHaveBeenCalledTimes(1)
  })

  it("não salta nem limpa o alvo enquanto a mensagem ainda não está carregada", () => {
    const onTargetHandled = vi.fn()
    render(<ChatThread {...threadBase} targetMessageId="ainda-nao-carregada" onTargetHandled={onTargetHandled} />)
    expect(mocks.scrollToMessage).not.toHaveBeenCalled()
    expect(onTargetHandled).not.toHaveBeenCalled()
  })
})
