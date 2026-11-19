import type { Meta, StoryObj } from "@storybook/react-vite"
import { HiveOverviewView } from "./HiveOverviewView"

const meta = {
  title: "Hive/Overview",
  component: HiveOverviewView,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof HiveOverviewView>

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
