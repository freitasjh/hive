import type { Meta, StoryObj } from "@storybook/react-vite"
import { LlmConfigView } from "./LlmConfigView"
import type { LlmConfigActions, LlmConfigState } from "../state"

const actions: LlmConfigActions = {
  onProviderChange: () => {},
  onModelChange: () => {},
  onSave: () => {},
  onTest: () => {},
  onRefreshModels: () => {},
  onRetry: () => {},
}

const state: LlmConfigState = {
  provider: "OPENAI",
  model: "gpt-4o-mini",
  configured: true,
  loading: false,
  saving: false,
  testing: false,
  loadError: null,
  saveError: null,
  testResult: null,
  remoteModels: [],
  remoteLoading: false,
  remoteError: null,
  notice: null,
}

const meta = {
  title: "Hive/Admin/LlmConfig",
  component: LlmConfigView,
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
  tags: ["autodocs"],
} satisfies Meta<typeof LlmConfigView>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = { args: { state, actions } }
export const Loading: Story = { args: { state: { ...state, loading: true }, actions } }
export const Error: Story = {
  args: { state: { ...state, loadError: "Falha ao carregar a configuração do tenant." }, actions },
}
export const Empty: Story = {
  args: { state: { ...state, provider: null, model: "", configured: false }, actions },
}
export const Disabled: Story = {
  args: { state: { ...state, saving: true, remoteLoading: true }, actions },
}
export const Active: Story = {
  args: {
    state: {
      ...state,
      provider: "OPENCODE_GO",
      model: "mimo-v2.5",
      remoteModels: ["mimo-v2.5", "mimo-v2.5-preview"],
    },
    actions,
  },
}
export const Success: Story = {
  args: {
    state: {
      ...state,
      testResult: { ok: true, provider: "OPENAI", model: "gpt-4o-mini", latencyMs: 42, reason: null },
      notice: "Configuração salva: OPENAI / gpt-4o-mini.",
    },
    actions,
  },
}
export const Hover: Story = {
  ...Default,
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  ...Default,
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
