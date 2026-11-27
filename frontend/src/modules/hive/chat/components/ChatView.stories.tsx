import type { Meta, StoryObj } from "@storybook/react-vite"
import type { ChatSessionDTO } from "@/services/chat.types"
import type { ChatControllerState } from "../hooks/use-chat-controller"
import { ChatView } from "./ChatView"

const P = "11111111-1111-1111-1111-111111111111"
const S = "22222222-2222-2222-2222-222222222222"
const sessionsStub: ChatSessionDTO[] = [
  { id: S, userId: "u", promptTemplateId: P, title: "Conversa", createAt: "2026-11-19T12:00:00Z", updateAt: "2026-11-19T12:00:00Z" },
]

const state: ChatControllerState = {
  prompts: [{ id: P, name: "Saudação", code: "greeting", template: "Olá", status: "ACTIVE", version: 1, createAt: "", updateAt: "" }],
  promptsLoading: false,
  promptsError: null,
  selectedPromptTemplateId: null,
  sessions: sessionsStub,
  sessionsPage: 0,
  sessionsTotalPages: 3,
  sessionsLoading: false,
  sessionsError: null,
  sessionId: null,
  messages: [],
  messagesPage: 0,
  messagesTotalPages: 1,
  historyLoading: false,
  historyError: null,
  input: "",
  sending: false,
  sendError: null,
  notice: null,
  lastUserMessage: null,
  catalog: [
    { provider: "OPENAI", models: ["gpt-4o-mini", "gpt-4o"], defaultModel: "gpt-4o-mini", configured: true },
    { provider: "GEMINI", models: ["gemini-2.0-flash"], defaultModel: "gemini-2.0-flash", configured: false },
  ],
  catalogLoading: false,
  catalogError: null,
  tenantDefault: { provider: "OPENAI", model: "gpt-4o-mini" },
  selectedModel: null,
  effectiveModel: { provider: "OPENAI", model: "gpt-4o-mini" },
  query: "",
  filteredSessions: sessionsStub,
  matchingMessages: [],
  targetMessageId: null,
}

const actions = {
  selectPrompt: () => {},
  setInput: () => {},
  send: async () => {},
  useStarter: async () => {},
  selectSession: async () => {},
  newChat: () => {},
  removeSession: async () => {},
  goToSessionsPage: async () => {},
  loadOlderMessages: async () => {},
  retryLast: async () => {},
  dismissNotice: () => {},
  selectModel: () => {},
  setQuery: () => {},
  renameSession: async () => true,
  jumpToMessage: async () => {},
  clearTargetMessage: () => {},
}

const meta = {
  title: "Hive/Chat/ChatView",
  component: ChatView,
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
  tags: ["autodocs"],
  args: { state, actions },
} satisfies Meta<typeof ChatView>

export default meta
type Story = StoryObj<typeof meta>

export const Mobile: Story = {
  decorators: [(Story) => <div className="mx-auto w-[375px]"><Story /></div>],
}
export const Desktop: Story = {
  decorators: [(Story) => <div className="mx-auto w-[1280px]"><Story /></div>],
}
export const Compact: Story = {
  decorators: [(Story) => <div className="mx-auto w-[996px]"><Story /></div>],
}
export const Empty: Story = {}
export const Default: Story = {
  args: {
    state: {
      ...state,
      selectedPromptTemplateId: P,
      messages: [
        { id: "m1", sessionId: S, role: "USER", content: "Resuma este texto", createAt: "" },
        { id: "m2", sessionId: S, role: "ASSISTANT", content: "Resumo gerado a partir do prompt.", createAt: "" },
      ],
      sessionId: S,
      input: "E agora?",
    },
  },
}
export const Loading: Story = { args: { state: { ...state, promptsLoading: true, sessionsLoading: true } } }
export const Error: Story = {
  args: { state: { ...state, promptsError: "Não foi possível carregar os prompts.", sendError: "Não foi possível enviar a mensagem." } },
}
export const EmptySessions: Story = {
  args: { state: { ...state, sessions: [], filteredSessions: [], sessionsTotalPages: 1 } },
}
export const Disabled: Story = { args: { state: { ...state, sending: true, input: "oi" } } }
export const Streaming: Story = {
  decorators: [(Story) => <div className="mx-auto w-[375px]"><Story /></div>],
  args: {
    state: {
      ...state,
      sessionId: S,
      sending: true,
      messages: [
        { id: "m1", sessionId: S, role: "USER", content: "Resuma este texto", createAt: "" },
        {
          id: "m2",
          sessionId: S,
          role: "ASSISTANT",
          content: "O texto trata de",
          reasoning: "Mapeando o pedido\nSelecionando os pontos-chave",
          createAt: "",
        },
      ],
    },
  },
}
export const StreamError: Story = {
  decorators: [(Story) => <div className="mx-auto w-[1280px]"><Story /></div>],
  args: {
    state: {
      ...state,
      sessionId: S,
      sendError: "Muitas solicitações. Aguarde um momento e tente novamente.",
      messages: [
        { id: "m1", sessionId: S, role: "USER", content: "Resuma este texto", createAt: "" },
        { id: "m2", sessionId: S, role: "ASSISTANT", content: "", reasoning: "Mapeando o pedido", createAt: "" },
      ],
    },
  },
}
export const Success: Story = {
  args: {
    state: {
      ...state,
      sessionId: S,
      selectedPromptTemplateId: P,
      messages: [{ id: "m1", sessionId: S, role: "ASSISTANT", content: "Resposta recebida.", createAt: "" }],
    },
  },
}
export const Search: Story = {
  args: {
    state: {
      ...state,
      query: "resumo",
      messages: [{ id: "m1", sessionId: S, role: "ASSISTANT", content: "Resumo do relatório.", createAt: "" }],
      matchingMessages: [{ id: "m1", sessionId: S, role: "ASSISTANT", content: "Resumo do relatório.", createAt: "" }],
    },
  },
}
export const Warning: Story = {
  args: { state: { ...state, input: "oi", notice: "Escolha um prompt antes de enviar." } },
}
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
