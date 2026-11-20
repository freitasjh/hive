import type { Meta, StoryObj } from "@storybook/react-vite"

import { MfaSetupInvite } from "./MfaSetupInvite"

/**
 * Convite opcional pós-login — nunca modal: a overview permanece interativa.
 * Estados aplicáveis: default, focus (anel do Button via teclado), desktop,
 * mobile 375px e dispensado (Pular → o pai desmonta o componente).
 * Não há loading/error/empty aqui: o componente é puramente informativo e
 * delega o QR/secret ao MfaSetupDialog existente.
 */
const meta = {
  title: "Auth/MfaSetupInvite",
  component: MfaSetupInvite,
  parameters: { layout: "fullscreen" },
  args: {
    onConfigure: () => undefined,
    onSkip: () => undefined,
  },
} satisfies Meta<typeof MfaSetupInvite>

export default meta
type Story = StoryObj<typeof meta>

/** Estado default sobre fundo de overview; sem overlay e sem focus trap. */
export const Default: Story = {
  decorators: [
    (Story) => (
      <div className="relative min-h-svh bg-background p-6 text-foreground">
        <h1 className="text-xl font-bold tracking-tight">Visão geral do Hive</h1>
        <p className="mt-1 text-xs text-muted-foreground">Conteúdo de fundo permanece utilizável.</p>
        <Story />
      </div>
    ),
  ],
}

/** Dark: superfície de card + borda primary/30, sem cor hardcoded. */
export const Dark: Story = {
  decorators: [
    (Story) => (
      <div className="dark">
        <div className="relative min-h-svh bg-background p-6 text-foreground">
          <h1 className="text-xl font-bold tracking-tight">Visão geral do Hive</h1>
          <Story />
        </div>
      </div>
    ),
  ],
}

/** 375px: convite cabe na viewport sem cobrir a tela inteira. */
export const MobileNarrow: Story = {
  decorators: [
    (Story) => (
      <div className="relative h-[720px] w-[375px] max-w-full bg-background p-4 text-foreground">
        <h1 className="text-xl font-bold tracking-tight">Visão geral do Hive</h1>
        <Story />
      </div>
    ),
  ],
}

/** Dispensado: "Pular" desmonta o convite; nenhum estado sobrevive ao reload. */
export const Dismissed: Story = {
  render: () => <></>,
}
