import type { Meta, StoryObj } from "@storybook/react-vite"
import type { ChatSessionDTO } from "@/services/chat.types"
import { SessionSidebar } from "./SessionSidebar"

const sessions: ChatSessionDTO[] = Array.from({ length: 3 }, (_, index) => ({
  id: `22222222-2222-2222-2222-22222222222${index}`,
  userId: "u",
  promptTemplateId: "11111111-1111-1111-1111-111111111111",
  title: `Conversa ${index + 1}`,
  createAt: "2026-11-19T12:00:00Z",
  updateAt: "2026-11-19T12:00:00Z",
}))

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
