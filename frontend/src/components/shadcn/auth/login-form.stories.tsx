import type { Meta, StoryObj } from "@storybook/react-vite"

import { LoginForm } from "./login-form"

const meta = {
  title: "Templates/LoginForm",
  component: LoginForm,
  parameters: {
    layout: "centered",
    a11y: { test: "error" },
  },
  tags: ["autodocs"],
  args: {
    onSubmit: () => {},
  },
  decorators: [
    (Story) => (
      <div className="w-[380px] max-w-full rounded-2xl border border-border/70 bg-card p-6 text-foreground">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof LoginForm>

export default meta
type Story = StoryObj<typeof meta>

/** Estado inicial: campos vazios, botão habilitado. */
export const Default: Story = {}

/** Formulário vazio — estado inicial sem valores persistidos. */
export const Empty: Story = {}

/** Foco no primeiro campo (navegação por teclado). */
export const Focus: Story = {
  play: async ({ canvasElement }) => {
    canvasElement.querySelector<HTMLInputElement>("#username")?.focus()
  },
}

/** Hover no botão de envio. */
export const Hover: Story = {
  play: async ({ canvasElement }) => {
    canvasElement.querySelector<HTMLButtonElement>('button[type="submit"]')?.focus()
  },
}

/** Estado ativo: usuário preenchido, senha pendente. */
export const Active: Story = {
  play: async ({ canvasElement }) => {
    const username = canvasElement.querySelector<HTMLInputElement>("#username")
    if (username) {
      username.value = "freitasjh"
      username.dispatchEvent(new Event("input", { bubbles: true }))
    }
  },
}

/** Campos e botão desabilitados. */
export const Disabled: Story = {
  args: { disabled: true },
}

/** Envio em andamento: aria-busy e rótulo de envio. */
export const Loading: Story = {
  args: { isPending: true },
}

/** Credenciais inválidas: erro semântico sem payload. */
export const ErrorInvalid: Story = {
  args: { errorKey: "auth.login.error.invalid" },
}

/** Falha de rede: erro semântico sem payload. */
export const ErrorNetwork: Story = {
  args: { errorKey: "auth.login.error.network" },
}

/** Envio concluído: formulário volta ao estado interativo. */
export const Success: Story = {
  play: async ({ canvasElement }) => {
    const username = canvasElement.querySelector<HTMLInputElement>("#username")
    const password = canvasElement.querySelector<HTMLInputElement>("#password")
    if (username) {
      username.value = "freitasjh"
      username.dispatchEvent(new Event("input", { bubbles: true }))
    }
    if (password) {
      password.value = "opaco"
      password.dispatchEvent(new Event("input", { bubbles: true }))
    }
    canvasElement.querySelector<HTMLFormElement>("form")?.requestSubmit()
  },
}
