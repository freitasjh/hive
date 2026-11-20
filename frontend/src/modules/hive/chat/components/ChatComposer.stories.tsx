import type { Meta, StoryObj } from "@storybook/react-vite"
import { ChatComposer } from "./ChatComposer"

const meta = {
  title: "Hive/Chat/ChatComposer",
  component: ChatComposer,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  args: { value: "", onChange: () => {}, onSend: () => {} },
} satisfies Meta<typeof ChatComposer>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Typing: Story = { args: { value: "Resuma este texto" } }
export const Sending: Story = { args: { value: "Resuma este texto", sending: true, disabled: true } }
export const Disabled: Story = { args: { disabled: true } }
export const Warning: Story = { args: { value: "oi", warning: "Escolha um prompt antes de enviar." } }
export const Success: Story = { args: { value: "" } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
