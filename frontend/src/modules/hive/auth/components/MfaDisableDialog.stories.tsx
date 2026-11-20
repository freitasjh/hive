import type { Meta, StoryObj } from "@storybook/react-vite"

import { MfaDisableDialog } from "./MfaDisableDialog"

const meta = {
  title: "Templates/MFA/Disable",
  component: MfaDisableDialog,
  args: {
    open: true,
    onOpenChange: () => {},
    onSuccess: () => {},
  },
} satisfies Meta<typeof MfaDisableDialog>

export default meta
type Story = StoryObj<typeof meta>

/** Estado inicial: campo de senha vazio, ação desabilitada. */
export const Default: Story = {}

/** Modal fechado (estado de repouso no menu do usuário). */
export const Closed: Story = {
  args: { open: false },
}
