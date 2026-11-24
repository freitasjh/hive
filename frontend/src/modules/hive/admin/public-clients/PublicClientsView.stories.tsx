import type { Meta, StoryObj } from "@storybook/react-vite"
import type { PublicApiClient } from "@/services/admin.types"
import { emptyPublicClientForm } from "./form"
import { PublicClientsView } from "./PublicClientsView"
import type { OneShotSecret, PublicClientsActions, PublicClientsState } from "../state"

const client: PublicApiClient = {
  clientId: "cli-123",
  label: "Chat do site",
  tenantId: "tenant-abc",
  grants: ["PUBLIC_CHAT_COMPLETE"],
  prompts: [{ promptCode: "greeting", pinMode: "PINNED", pinnedVersion: 3 }],
  token_ttl_seconds: 900,
  max_messages: 20,
  max_message_chars: 4000,
  max_total_chars: 20000,
  revoked_at: null,
  expires_at: null,
  previous_secret_expires_at: "2999-01-01T00:00:00Z",
}

const actions: PublicClientsActions = {
  onQueryChange: () => {},
  onPageChange: () => {},
  onRetry: () => {},
  onOpenCreate: () => {},
  onCloseCreate: () => {},
  onFormChange: () => {},
  onToggleGrant: () => {},
  onPromptChange: () => {},
  onAddPrompt: () => {},
  onRemovePrompt: () => {},
  onCreate: () => {},
  onRotate: () => {},
  onAskRevoke: () => {},
  onCancelRevoke: () => {},
  onConfirmRevoke: () => {},
  onDismissSecret: () => {},
}

const state: PublicClientsState = {
  items: [client],
  total: 1,
  page: 0,
  size: 20,
  q: "",
  loading: false,
  error: null,
  actionError: null,
  notice: null,
  createOpen: false,
  form: emptyPublicClientForm,
  validationError: null,
  creating: false,
  revokeTarget: null,
  revoking: false,
  rotatingClientId: null,
}

const oneShot: OneShotSecret = {
  secret: "sk-exemplo-one-shot",
  scope: "create",
  clientId: "cli-123",
  label: "Chat do site",
  previousSecretExpiresAt: null,
}

const meta = {
  title: "Hive/Admin/PublicClients",
  component: PublicClientsView,
  parameters: { layout: "fullscreen", a11y: { test: "error" } },
  tags: ["autodocs"],
} satisfies Meta<typeof PublicClientsView>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = { args: { state, actions, oneShot: null } }
export const Active: Story = {
  args: { state: { ...state, createOpen: true }, actions, oneShot: null },
}
export const CreateWithGrant: Story = {
  args: {
    state: {
      ...state,
      createOpen: true,
      form: { ...emptyPublicClientForm, grants: ["PUBLIC_CHAT_COMPLETE"] },
    },
    actions,
    oneShot: null,
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
export const Disabled: Story = {
  args: {
    state: { ...state, items: [{ ...client, revoked_at: "2026-11-22T00:00:00Z" }] },
    actions,
    oneShot: null,
  },
}
export const Loading: Story = {
  args: { state: { ...state, loading: true }, actions, oneShot: null },
}
export const Error: Story = {
  args: { state: { ...state, error: "Falha ao carregar os clients da API pública." }, actions, oneShot: null },
}
export const Empty: Story = {
  args: { state: { ...state, items: [], total: 0 }, actions, oneShot: null },
}
export const Success: Story = {
  args: { state: { ...state, notice: "Client criado." }, actions, oneShot: null },
}
export const SecretOneShot: Story = { args: { state, actions, oneShot } }
export const RevokeConfirm: Story = {
  args: { state: { ...state, revokeTarget: client }, actions, oneShot: null },
}
