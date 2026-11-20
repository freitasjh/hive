import type { Meta, StoryObj } from "@storybook/react-vite"

import { MfaVerifyView } from "./MfaVerifyView"

/** Cada estado da tela de desafio fora do shell (login/MFA não usam AtlasAdminTemplate). */
const meta = {
  title: "Templates/MFA/Verify",
  component: MfaVerifyView,
  parameters: { layout: "fullscreen" },
  args: {
    mode: "code",
    code: "",
    status: "idle",
    errorKey: null,
    remainingAttempts: 5,
    secondsLeft: 120,
    onModeChange: () => {},
    onCodeChange: () => {},
    onSubmit: () => {},
    onBack: () => {},
  },
} satisfies Meta<typeof MfaVerifyView>

export default meta
type Story = StoryObj<typeof meta>

/** Estado inicial: campo vazio, 5 tentativas e contagem cheia. */
export const Default: Story = {}

/** Código digitado, botão habilitado. */
export const Filled: Story = {
  args: { code: "123456" },
}

/** Submissão em andamento. */
export const Pending: Story = {
  args: { code: "123456", status: "pending" },
}

/** Código inválido: erro em aria-live e tentativas decrementadas. */
export const Error: Story = {
  args: {
    code: "000000",
    status: "error",
    errorKey: "auth.mfa.error.invalid",
    remainingAttempts: 4,
  },
}

/** 5 erros: bloqueio orientado. */
export const TooManyAttempts: Story = {
  args: {
    status: "error",
    errorKey: "auth.mfa.error.tooMany",
    remainingAttempts: 0,
  },
}

/** Desafio expirado. */
export const Expired: Story = {
  args: { secondsLeft: 0 },
}

/** Aba de recuperação. */
export const Recovery: Story = {
  args: { mode: "recovery", remainingAttempts: 5 },
}

/** Verificação concluída antes do redirect. */
export const Success: Story = {
  args: { status: "success" },
}

/** Viewport 375px: campo e ações utilizáveis sem overflow. */
export const Mobile: Story = {
  decorators: [
    (Story) => (
      <div className="mx-auto w-[375px] max-w-full bg-background text-foreground">
        <Story />
      </div>
    ),
  ],
}
