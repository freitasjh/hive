import type { Meta, StoryObj } from "@storybook/react-vite"
import type { PromptTemplateDTO } from "@/services/admin.types"
import { PromptSelector } from "./PromptSelector"

const prompt: PromptTemplateDTO = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Saudação",
  code: "greeting",
  template: "Olá {{nome}}",
  status: "ACTIVE",
  version: 1,
  createAt: "2026-01-01T00:00:00Z",
  updateAt: "2026-01-01T00:00:00Z",
}

const meta = {
  title: "Hive/Chat/PromptSelector",
  component: PromptSelector,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  args: { prompts: [prompt], value: null, onChange: () => {} },
} satisfies Meta<typeof PromptSelector>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Selected: Story = { args: { value: prompt.id } }
export const Loading: Story = { args: { loading: true } }
export const Error: Story = { args: { error: "Não foi possível carregar os prompts." } }
export const Empty: Story = { args: { prompts: [] } }
export const Disabled: Story = { args: { disabled: true, value: prompt.id } }
export const Locked: Story = { args: { locked: true, value: prompt.id } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
