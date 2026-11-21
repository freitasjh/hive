import type { Meta, StoryObj } from "@storybook/react-vite"
import type { ChatMessageDTO, ChatSessionDTO } from "@/services/chat.types"
import { SessionSidebar } from "./SessionSidebar"

const localDay = (days: number) =>
  new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() - days, 12).toISOString()

const sessions: ChatSessionDTO[] = Array.from({ length: 3 }, (_, index) => ({
  id: `22222222-2222-2222-2222-22222222222${index}`,
  userId: "u",
  promptTemplateId: "11111111-1111-1111-1111-111111111111",
  title: `Conversa ${index + 1}`,
  createAt: localDay(0),
  updateAt: localDay(0),
}))

const datedSessions: ChatSessionDTO[] = [
  { ...sessions[0], title: "Sessão de hoje", updateAt: localDay(0) },
  { ...sessions[1], title: "Sessão de ontem", updateAt: localDay(1) },
  { ...sessions[2], title: "Sessão da semana", updateAt: localDay(4) },
  { ...sessions[0], id: sessions[0].id + "-old", title: "Sessão antiga", updateAt: localDay(12) },
]

const matchingMessages: ChatMessageDTO[] = [
  { id: "m-1", sessionId: sessions[0].id, role: "ASSISTANT", content: "Resumo do relatório trimestral", createAt: localDay(0) },
  { id: "m-2", sessionId: sessions[0].id, role: "USER", content: "Resuma este texto", createAt: localDay(0) },
]

const meta = {
  title: "Hive/Chat/SessionSidebar",
  component: SessionSidebar,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  decorators: [(Story) => <div className="h-[26rem] w-72"><Story /></div>],
  args: {
    sessions: [],
    selectedId: null,
    page: 0,
    totalPages: 1,
    onSelect: () => {},
    onNew: () => {},
    onDelete: () => {},
    onRename: async () => true,
    onJumpToMessage: () => {},
    onQueryChange: () => {},
    onPageChange: () => {},
  },
} satisfies Meta<typeof SessionSidebar>

export default meta
type Story = StoryObj<typeof meta>

export const Empty: Story = {}
export const Default: Story = { args: { sessions, selectedId: sessions[0].id } }
export const Loading: Story = { args: { loading: true } }
export const Error: Story = { args: { error: "Não foi possível carregar as conversas." } }
export const Paginated: Story = { args: { sessions, totalPages: 4, page: 1 } }
export const Disabled: Story = { args: { sessions, loading: true } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}

export const Search: Story = {
  args: { sessions: datedSessions, query: "semana" },
}
export const DateGroups: Story = {
  args: { sessions: datedSessions },
}
export const SearchMessages: Story = {
  args: { sessions, query: "resumo", matchingMessages },
}
export const SearchEmpty: Story = {
  args: { sessions, query: "sem resultado" },
}
export const RenameDialog: Story = {
  args: { sessions },
  play: async ({ canvasElement }) => {
    const trigger = canvasElement.querySelector('[data-testid="session-rename"]') as HTMLButtonElement | null
    trigger?.click()
  },
}
export const DeleteConfirmation: Story = {
  args: { sessions },
  play: async ({ canvasElement }) => {
    const trigger = canvasElement.querySelector('[data-testid="session-delete"]') as HTMLButtonElement | null
    trigger?.click()
  },
}
