import type { Meta, StoryObj } from "@storybook/react-vite"
import { PromptEditorView } from "./PromptEditorView"
import type { PromptEditorActions, PromptEditorState } from "../state"

const actions: PromptEditorActions = {
  onNameChange: () => {},
  onCodeChange: () => {},
  onDescriptionChange: () => {},
  onTagsInputChange: () => {},
  onAddTag: () => {},
  onRemoveTag: () => {},
  onTemplateChange: () => {},
  onStatusChange: () => {},
  onTemperatureChange: () => {},
  onTopPChange: () => {},
  onMaxTokensChange: () => {},
  onSave: () => {},
  onBack: () => {},
  onRetry: () => {},
}

const state: PromptEditorState = {
  isEdit: false,
  promptId: null,
  name: "Saudação",
  code: "greeting-v1",
  description: "Prompt inicial",
  tags: ["chat"],
  tagsInput: "",
  template: "Olá {{nome}}, bem-vindo ao {{produto}}",
  status: "DRAFT",
  temperature: 0.7,
  topP: 0.9,
  maxTokens: 2048,
  loading: false,
  saving: false,
  error: null,
  validationError: null,
}

const meta = {
  title: "Hive/Admin/PromptEditor",
  component: PromptEditorView,
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
  tags: ["autodocs"],
} satisfies Meta<typeof PromptEditorView>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = { args: { state, actions } }
export const Loading: Story = { args: { state: { ...state, loading: true }, actions } }
export const Error: Story = { args: { state: { ...state, error: "Falha ao carregar prompt." }, actions } }
export const Empty: Story = {
  args: { state: { ...state, name: "", code: "", tags: [], template: "" }, actions },
}
export const Disabled: Story = { args: { state: { ...state, isEdit: true, saving: true }, actions } }
export const Active: Story = {
  args: { state: { ...state, isEdit: true, promptId: "11111111-1111-1111-1111-111111111111" }, actions },
}
export const Success: Story = {
  args: { state: { ...state, validationError: null }, actions },
}
export const Validation: Story = {
  args: {
    state: {
      ...state,
      name: "",
      code: "",
      template: "",
      validationError: "Preencha nome, code e template",
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
