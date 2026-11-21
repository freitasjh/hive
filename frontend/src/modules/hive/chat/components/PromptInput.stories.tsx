import type { Meta, StoryObj } from "@storybook/react-vite"
import { PromptInput } from "./PromptInput"

const meta = {
  title: "Hive/Chat/PromptInput",
  component: PromptInput,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  args: {
    value: "",
    onChange: () => {},
    onSubmit: () => {},
    label: "Mensagem para o assistente",
    placeholder: "Escreva sua mensagem…",
    hint: "Enter envia · Shift+Enter quebra linha",
    disclaimer: "O assistente pode cometer erros. Confira informações importantes.",
    sendLabel: "Enviar",
    sendingLabel: "Enviando…",
    modelSlot: <button type="button" className="min-h-9 rounded-md border px-3 text-xs">Padrão do tenant</button>,
  },
} satisfies Meta<typeof PromptInput>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Typing: Story = { args: { value: "Resuma este texto" } }
export const Empty: Story = { args: { value: "" } }
export const Sending: Story = { args: { value: "Resuma este texto", sending: true, disabled: true } }
export const Disabled: Story = { args: { value: "Resuma este texto", disabled: true } }
export const Warning: Story = { args: { value: "oi", warning: "Escolha um prompt antes de enviar." } }
export const Error: Story = { args: { value: "oi", warning: "Não foi possível enviar a mensagem." } }
export const Success: Story = { args: { value: "" } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
