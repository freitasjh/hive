import type { Meta, StoryObj } from "@storybook/react-vite"
import { ChatMarkdown } from "./ChatMarkdown"

const meta = {
  title: "Hive/Chat/ChatMarkdown",
  component: ChatMarkdown,
  parameters: { layout: "padded", a11y: { test: "error" } },
  tags: ["autodocs"],
  decorators: [(Story) => <div className="max-w-2xl rounded-xl bg-muted/30 p-4"><Story /></div>],
  args: { content: "Resposta simples em **negrito** com `código inline`." },
} satisfies Meta<typeof ChatMarkdown>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}
export const Fence: Story = {
  args: {
    content: "Use assim:\n\n```ts\nconst dobro = (n: number) => n * 2\nconsole.log(dobro(21))\n```\n\nPronto.",
  },
}
export const UnknownLanguage: Story = { args: { content: "```texto\nlinha sem gramática\n```" } }
export const Streaming: Story = { args: { content: "```ts\nconst x = 1\n", isAnimating: true } }
export const Empty: Story = { args: { content: "" } }
export const Hover: Story = {
  decorators: [(Story) => <div className="hover:bg-accent"><Story /></div>],
}
export const Focus: Story = {
  decorators: [(Story) => <div className="focus-within:ring-2 focus-within:ring-ring"><Story /></div>],
}
