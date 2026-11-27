import { act, renderHook, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { buildChatPayload } from "@/services/chat.service"
import { useChatController } from "./use-chat-controller"

const P = "11111111-1111-1111-1111-111111111111"
const S = "22222222-2222-2222-2222-222222222222"

const mocks = vi.hoisted(() => ({
  send: vi.fn(),
  promptList: vi.fn(),
  listSessions: vi.fn(),
  getMessages: vi.fn(),
  getSession: vi.fn(),
  deleteSession: vi.fn(),
  getProviderCatalog: vi.fn(),
  getLlmConfig: vi.fn(),
}))

vi.mock(import("@/services/chat.service"), async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/chat.service")>()
  // Sem streamMessage em runtime: exercita o fallback sendMessage (feature flag).
  return { ...actual, chatService: { sendMessage: mocks.send } as unknown as typeof actual.chatService }
})
vi.mock("@/services/prompt.service", () => ({ promptService: { list: mocks.promptList } }))
vi.mock("@/services/session.service", () => ({
  sessionService: {
    listSessions: mocks.listSessions,
    getMessages: mocks.getMessages,
    getSession: mocks.getSession,
    deleteSession: mocks.deleteSession,
    updateSession: vi.fn(),
    createSession: vi.fn(),
  },
}))
vi.mock("@/services/agent-admin.service", () => ({
  agentAdminService: {
    getProviderCatalog: mocks.getProviderCatalog,
    getLlmConfig: mocks.getLlmConfig,
  },
}))

const prompt = { id: P, name: "Saudação", code: "greeting", template: "Olá", status: "ACTIVE" }
const session = {
  id: S,
  userId: "u",
  promptTemplateId: P,
  title: "Conversa",
  createAt: "",
  updateAt: "",
}
const answer = {
  sessionId: S,
  role: "ASSISTANT",
  message: "Resposta",
  timestamp: "2026-11-20T12:01:00Z",
  provider: "OPENAI",
  model: "gpt-4o",
}
const catalog = [
  { provider: "OPENAI", models: ["gpt-4o-mini", "gpt-4o"], defaultModel: "gpt-4o-mini", configured: true },
  { provider: "GEMINI", models: ["gemini-2.0-flash"], defaultModel: "gemini-2.0-flash", configured: false },
]

describe("useChatController — catálogo, default e override de modelo", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    mocks.promptList.mockResolvedValue({ content: [prompt], totalElements: 1, totalPages: 1, number: 0 })
    mocks.listSessions.mockResolvedValue({ content: [session], totalElements: 1, totalPages: 1, number: 0 })
    mocks.getMessages.mockImplementation((_id: string, page = 0) =>
      Promise.resolve({ content: [], totalElements: 0, totalPages: 1, number: page })
    )
    mocks.getSession.mockResolvedValue(session)
    mocks.send.mockResolvedValue(answer)
    mocks.deleteSession.mockResolvedValue(undefined)
    mocks.getProviderCatalog.mockResolvedValue(catalog)
    mocks.getLlmConfig.mockResolvedValue({
      id: "cfg",
      tenantId: "t",
      provider: "OPENAI",
      model: "gpt-4o-mini",
      createAt: "",
      updateAt: "",
    })
  })

  it("carrega catálogo e começa no default do tenant", async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))

    expect(result.current.state.catalog).toEqual(catalog)
    expect(result.current.state.tenantDefault).toEqual({ provider: "OPENAI", model: "gpt-4o-mini" })
    expect(result.current.state.selectedModel).toBeNull()
  })

  it("envia provider/model no payload somente quando diferentes do default", async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))
    act(() => result.current.actions.selectPrompt(P))

    // diferente do default do tenant: override explícito no primeiro turno
    act(() => result.current.actions.selectModel({ provider: "OPENAI", model: "gpt-4o" }))
    act(() => result.current.actions.setInput("um"))
    await act(async () => {
      await result.current.actions.send()
    })
    expect(mocks.send).toHaveBeenLastCalledWith({
      promptTemplateId: P,
      message: "um",
      provider: "OPENAI",
      model: "gpt-4o",
    }, { provider: "OPENAI", model: "gpt-4o-mini" })
    let payload = buildChatPayload(mocks.send.mock.calls[0][0], result.current.state.tenantDefault)
    expect(payload).toMatchObject({ provider: "OPENAI", model: "gpt-4o" })

    // volta ao padrão: nenhum override no payload
    act(() => result.current.actions.selectModel(null))
    act(() => result.current.actions.setInput("dois"))
    await act(async () => {
      await result.current.actions.send()
    })
    expect(mocks.send).toHaveBeenLastCalledWith({
      promptTemplateId: P,
      sessionId: S,
      message: "dois",
    }, { provider: "OPENAI", model: "gpt-4o" })
    payload = buildChatPayload(mocks.send.mock.calls[1][0], result.current.state.tenantDefault)
    expect(payload).not.toHaveProperty("provider")
    expect(payload).not.toHaveProperty("model")
  })

  it("volta ao padrão ao selecionar a opção 'Padrão do tenant'", async () => {
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))
    act(() => result.current.actions.selectPrompt(P))
    act(() => result.current.actions.selectModel({ provider: "GEMINI", model: "gemini-2.0-flash" }))
    act(() => result.current.actions.selectModel(null))

    act(() => result.current.actions.setInput("oi"))
    await act(async () => {
      await result.current.actions.send()
    })
    expect(mocks.send).toHaveBeenLastCalledWith(
      { promptTemplateId: P, message: "oi" },
      { provider: "OPENAI", model: "gpt-4o-mini" }
    )
  })

  it("sessão existente nunca vaza override: troca no seletor volta ao efetivo", async () => {
    mocks.getSession.mockResolvedValue({
      ...session,
      provider: "OPENAI",
      model: "gpt-4o-mini",
    })
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))
    await act(async () => {
      await result.current.actions.selectSession(S)
    })

    act(() => result.current.actions.selectModel({ provider: "GEMINI", model: "gemini-2.0-flash" }))
    expect(result.current.state.selectedModel).toEqual({
      provider: "GEMINI",
      model: "gemini-2.0-flash",
    })

    act(() => result.current.actions.setInput("turno em sessão"))
    await act(async () => {
      await result.current.actions.send()
    })

    const sent = mocks.send.mock.calls[0][0]
    const wirePayload = buildChatPayload(sent, {
      provider: "OPENAI",
      model: "gpt-4o-mini",
    })
    expect(wirePayload).toEqual({ sessionId: S, message: "turno em sessão" })
    expect(wirePayload).not.toHaveProperty("provider")
    expect(wirePayload).not.toHaveProperty("model")
    expect(result.current.state.selectedModel).toBeNull()
    expect(result.current.state.notice).toMatch(/modelo travado/i)
  })

  it("buildChatPayload ignora override mesmo se chamado com sessionId e par divergente", () => {
    expect(
      buildChatPayload(
        {
          sessionId: S,
          message: "oi",
          provider: "GEMINI",
          model: "gemini-2.0-flash",
        },
        { provider: "OPENAI", model: "gpt-4o-mini" }
      )
    ).toEqual({ sessionId: S, message: "oi" })
  })

  it("abrir sessão pinta o seletor com o par efetivo dela", async () => {
    mocks.getSession.mockResolvedValue({
      ...session,
      provider: "GEMINI",
      model: "gemini-2.0-flash",
    })
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))

    await act(async () => {
      await result.current.actions.selectSession(S)
    })

    expect(result.current.state.effectiveModel).toEqual({
      provider: "GEMINI",
      model: "gemini-2.0-flash",
    })
    expect(result.current.state.selectedModel).toBeNull()
  })

  it("catalogo indisponível degrada sem quebrar o controller", async () => {
    mocks.getProviderCatalog.mockRejectedValueOnce(new Error("404"))
    const { result } = renderHook(() => useChatController())
    await waitFor(() => expect(result.current.state.catalogLoading).toBe(false))

    expect(result.current.state.catalog).toEqual([])
    expect(result.current.state.catalogError).toBeTruthy()
    expect(result.current.state.tenantDefault).toEqual({ provider: "OPENAI", model: "gpt-4o-mini" })
  })
})
