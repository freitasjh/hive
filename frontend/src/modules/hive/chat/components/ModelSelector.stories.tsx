import type { Meta, StoryObj } from "@storybook/react-vite"
import type { ProviderCatalogEntry } from "@/services/chat.types"
import { ModelSelector } from "./ModelSelector"

const catalog: ProviderCatalogEntry[] = [
  { provider: "OPENAI", models: ["gpt-4o-mini", "gpt-4o"], defaultModel: "gpt-4o-mini", configured: true },
  {
    provider: "OPENCODE_ZEN",
    models: ["claude-sonnet-4-5"],
    defaultModel: "claude-sonnet-4-5",
    configured: true,
  },
  { provider: "GEMINI", models: ["gemini-2.0-flash"], defaultModel: "gemini-2.0-flash", configured: false },
]

const meta = {
  title: "Hive/Chat/ModelSelector",
  component: ModelSelector,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  args: {
    catalog: [...catalog],
    value: null,
    baseline: { provider: "OPENAI", model: "gpt-4o-mini" },
    onChange: () => {},
  },
} satisfies Meta<typeof ModelSelector>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Explicit: Story = {
  args: { value: { provider: "OPENCODE_ZEN", model: "claude-sonnet-4-5" } },
}
export const Loading: Story = { args: { loading: true } }
export const Disabled: Story = { args: { disabled: true } }
export const WithoutCatalog: Story = { args: { catalog: [], error: "Catálogo de modelos indisponível" } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
