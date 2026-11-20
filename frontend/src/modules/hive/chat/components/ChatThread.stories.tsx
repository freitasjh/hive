import type { Meta, StoryObj } from "@storybook/react-vite"
import type { ChatMessageDTO } from "@/services/chat.types"
import { STARTER_PROMPTS } from "../data/chat.data"
import { ChatThread } from "./ChatThread"

const messages: ChatMessageDTO[] = [
  { id: "1", sessionId: "s", role: "USER", content: "Resuma este texto", createAt: "2026-11-19T12:00:00Z" },
  { id: "2", sessionId: "s", role: "ASSISTANT", content: "Claro. Aqui vai o resumo…", createAt: "2026-11-19T12:01:00Z" },
]

const meta = {
  title: "Hive/Chat/ChatThread",
  component: ChatThread,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  decorators: [(Story) => <div className="flex h-[26rem] w-full max-w-3xl"><Story /></div>],
  args: { messages: [], onLoadOlder: () => {}, onRetry: () => {}, onStarter: () => {} },
} satisfies Meta<typeof ChatThread>

export default meta
type Story = StoryObj<typeof meta>

export const Empty: Story = { args: { starterPrompts: STARTER_PROMPTS } }
export const Starters: Story = { args: { starterPrompts: STARTER_PROMPTS, canUseStarter: true } }
export const Default: Story = { args: { messages } }
export const Loading: Story = { args: { loading: true } }
export const Error: Story = { args: { error: "Não foi possível carregar o histórico." } }
export const Generating: Story = { args: { messages, generating: true } }
export const Older: Story = { args: { messages, canLoadOlder: true } }
export const Retry: Story = { args: { messages, retryable: true } }
export const LongConversation: Story = {
  args: {
    messages: Array.from({ length: 12 }, (_, index) => ({
      id: `long-${index}`,
      sessionId: "s",
      role: index % 2 === 0 ? "USER" : "ASSISTANT",
      content: `Mensagem ${index + 1}: o thread deve rolar internamente mantendo o composer visível.`,
      createAt: "2026-11-19T12:00:00Z",
    })),
  },
}
export const Disabled: Story = { args: { messages: [], starterPrompts: STARTER_PROMPTS } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
