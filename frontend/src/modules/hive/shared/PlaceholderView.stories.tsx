import type { Meta, StoryObj } from "@storybook/react-vite"
import { PlaceholderView } from "./PlaceholderView"

const meta = {
  title: "Hive/PlaceholderView",
  component: PlaceholderView,
  args: {
    view: "kanban",
    onBack: () => {},
  },
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PlaceholderView>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {}

export const Dark: Story = {
  decorators: [
    (Story) => (
      <div className="dark bg-background text-foreground">
        <Story />
      </div>
    ),
  ],
}
