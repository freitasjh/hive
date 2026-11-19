import type { Meta, StoryObj } from "@storybook/react-vite"
import { PromptsView } from "./PromptsView"
import type { PromptsActions, PromptsState } from "../state"
import type { PromptTemplateDTO } from "@/services/admin.types"

const actions: PromptsActions = {
  onSearch: () => {},
  onQueryChange: () => {},
  onStatusChange: () => {},
  onPageChange: () => {},
  onNew: () => {},
  onEdit: () => {},
  onAskDelete: () => {},
  onCancelDelete: () => {},
  onConfirmDelete: () => {},
  onRetry: () => {},
}

const prompt: PromptTemplateDTO = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Saudação",
  code: "greeting-v1",
  description: null,
  tags: ["chat"],
  template: "Olá {{nome}}",
  params: null,
  inferenceParams: null,
  status: "ACTIVE",
  version: 1,
  createAt: "2026-01-01T00:00:00Z",
  updateAt: "2026-01-01T00:00:00Z",
}

const state: PromptsState = {
  items: [prompt],
  total: 1,
  page: 0,
  size: 20,
  q: "",
  status: null,
  loading: false,
  error: null,
  actionError: null,
  notice: null,
  deleteTarget: null,
  deleting: false,
}

const meta = {
  title: "Hive/Admin/Prompts",
  component: PromptsView,
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
  tags: ["autodocs"],
} satisfies Meta<typeof PromptsView>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = { args: { state, actions } }
export const Loading: Story = { args: { state: { ...state, loading: true }, actions } }
export const Error: Story = { args: { state: { ...state, error: "Falha ao carregar prompts." }, actions } }
export const Empty: Story = {
  args: { state: { ...state, items: [], total: 0 }, actions },
}
export const Disabled: Story = {
  args: { state: { ...state, deleteTarget: prompt, deleting: true }, actions },
}
export const Active: Story = {
  args: { state: { ...state, status: "ACTIVE" }, actions },
}
export const Success: Story = {
  args: { state: { ...state, notice: "Prompt removido." }, actions },
}
export const Hover: Story = {
  ...Default,
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  ...Default,
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
