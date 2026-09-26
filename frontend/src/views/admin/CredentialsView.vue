<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'
import Message from 'primevue/message'
import { agentAdminService } from '../../services/agent-admin.service'
import {
  VENDOR_CATALOG,
  VENDOR_ORDER,
  LGPD_OPENCODE_MESSAGE,
  acceptsTenantBaseUrl,
  maskKey,
  type AiProvider,
  type CredentialStatus,
} from '../../types/agent-admin'

const toast = useToast()

const loading = ref(false)
const statuses = ref<CredentialStatus[]>([])
const statusByProvider = computed(() => {
  const map = new Map<AiProvider, CredentialStatus>()
  for (const s of statuses.value) map.set(s.provider, s)
  return map
})

const editing = ref<AiProvider | null>(null)
const showDialog = ref(false)
const apiKey = ref('')
const baseUrl = ref('')
const saving = ref(false)

const deleteTarget = ref<AiProvider | null>(null)
const showDeleteConfirm = ref(false)
const deleting = ref(false)

const editingMeta = computed(() => (editing.value ? VENDOR_CATALOG[editing.value] : null))
const editingIsExternal = computed(() => editing.value === 'OPENCODE_GO' || editing.value === 'OPENCODE_ZEN')
/** Só os providers da allowlist têm base URL do tenant; nos demais a URL fixa é apenas exibição. */
const editingAcceptsBaseUrl = computed(() => editing.value !== null && acceptsTenantBaseUrl(editing.value))

const prefixError = computed(() => {
  if (!editing.value || !apiKey.value) return null
  const meta = VENDOR_CATALOG[editing.value]
  if (!meta.requiresKey) return null
  if (meta.keyPrefix && !apiKey.value.startsWith(meta.keyPrefix)) {
    return `A chave deve começar com "${meta.keyPrefix}"`
  }
  return null
})

const canSave = computed(() => !prefixError.value && !saving.value)

async function load() {
  loading.value = true
  try {
    statuses.value = await agentAdminService.listCredentials()
  } catch {
    toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar credenciais', life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openEdit(provider: AiProvider) {
  editing.value = provider
  apiKey.value = ''
  // Só popula o estado editável quando a baseUrl é do tenant; para GO/Zen a URL fixa do vendor
  // é informação readonly e não tem representação editável nem valor de payload.
  baseUrl.value = acceptsTenantBaseUrl(provider)
    ? statusByProvider.value.get(provider)?.baseUrl ?? VENDOR_CATALOG[provider].defaultBaseUrl ?? ''
    : ''
  showDialog.value = true
}

function closeDialog() {
  showDialog.value = false
  editing.value = null
  apiKey.value = ''
  baseUrl.value = ''
}

async function saveCredential() {
  if (!editing.value || !canSave.value) return
  saving.value = true
  try {
    const payload: { apiKey?: string; baseUrl?: string } = {}
    if (apiKey.value) payload.apiKey = apiKey.value
    if (editingAcceptsBaseUrl.value && baseUrl.value.trim()) payload.baseUrl = baseUrl.value.trim()
    await agentAdminService.upsertCredential(editing.value, payload)
    toast.add({ severity: 'success', summary: 'Salva', detail: 'Credencial atualizada', life: 2000 })
    closeDialog()
    await load()
  } catch (err: unknown) {
    const e = err as { status?: number; detail?: string }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.detail ?? 'Falha ao salvar credencial', life: 4000 })
  } finally {
    saving.value = false
  }
}

function askDelete(provider: AiProvider) {
  deleteTarget.value = provider
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await agentAdminService.deleteCredential(deleteTarget.value)
    toast.add({ severity: 'success', summary: 'Removida', detail: 'Credencial removida', life: 2000 })
    showDeleteConfirm.value = false
    deleteTarget.value = null
    await load()
  } catch (err: unknown) {
    const e = err as { detail?: string }
    toast.add({ severity: 'error', summary: 'Erro', detail: e.detail ?? 'Falha ao remover', life: 4000 })
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="credentials-view">
    <div class="flex align-items-center gap-3 mb-4">
      <h1 class="text-2xl font-bold m-0 text-900">Provedores de IA</h1>
    </div>
    <p class="text-surface-500 mt-0">Gerencie as chaves de API por provedor. A chave nunca é exibida — apenas o status e os últimos 4 dígitos.</p>

    <div v-if="loading" class="text-surface-500">Carregando...</div>

    <div v-else class="flex flex-column gap-2">
      <div
        v-for="provider in VENDOR_ORDER"
        :key="provider"
        :data-testid="`cred-row-${provider}`"
        class="flex align-items-center gap-3 p-3 border-round surface-border border-1 surface-card"
      >
        <i :class="VENDOR_CATALOG[provider].icon" class="text-xl" aria-hidden="true" />
        <span class="flex flex-column flex-1">
          <span class="font-medium">{{ VENDOR_CATALOG[provider].label }}</span>
          <span class="text-xs text-surface-500" :data-testid="`cred-cost-${provider}`">{{ VENDOR_CATALOG[provider].costHint }}</span>
          <span
            v-if="VENDOR_CATALOG[provider].readOnlyBaseUrl && VENDOR_CATALOG[provider].defaultBaseUrl"
            class="text-xs text-surface-500 font-mono"
            :data-testid="`cred-baseurl-${provider}`"
          >{{ VENDOR_CATALOG[provider].defaultBaseUrl }}</span>
          <span class="text-sm text-surface-500">
            <span v-if="statusByProvider.get(provider)?.configured">
              {{ maskKey(statusByProvider.get(provider)?.last4) }}
            </span>
            <span v-else>Nenhuma chave cadastrada</span>
          </span>
        </span>
        <Tag
          v-if="statusByProvider.get(provider)?.configured"
          value="Configurada ✅"
          severity="success"
        />
        <Tag v-else value="Faltante ⚠️" severity="warn" />
        <Button
          icon="pi pi-pencil"
          text
          rounded
          aria-label="Editar credencial"
          :data-testid="`cred-edit-${provider}`"
          @click="openEdit(provider)"
        />
        <Button
          icon="pi pi-trash"
          text
          rounded
          severity="danger"
          aria-label="Remover credencial"
          :data-testid="`cred-delete-${provider}`"
          :disabled="!statusByProvider.get(provider)?.configured"
          @click="askDelete(provider)"
        />
      </div>
    </div>

    <Dialog v-model:visible="showDialog" modal header="Editar credencial" :style="{ width: '28rem' }" data-testid="cred-dialog">
      <div v-if="editingMeta" class="flex flex-column gap-3">
        <div class="font-medium">{{ editingMeta.label }}</div>
        <Message
          v-if="editingIsExternal"
          severity="info"
          :closable="false"
          role="alert"
          data-testid="cred-lgpd-banner"
        >{{ LGPD_OPENCODE_MESSAGE }}</Message>
        <Message v-if="!editingMeta.requiresKey" severity="info" :closable="false">
          Sem autenticação — restrinja a rede do endpoint.
        </Message>
        <div v-if="editingMeta.requiresKey" class="flex flex-column gap-1">
          <label for="cred-key" class="text-sm font-medium">Nova chave (vazio = manter atual)</label>
          <Password
            id="cred-key"
            v-model="apiKey"
            :placeholder="editingMeta.keyPlaceholder"
            :feedback="false"
            toggleMask
            class="w-full"
            data-testid="cred-key-input"
            :invalid="!!prefixError"
          />
          <small v-if="prefixError" class="text-red-500">{{ prefixError }}</small>
          <small v-else class="text-surface-500">Exibida como {{ maskKey('••••') }} — editar para alterar.</small>
        </div>
        <div v-if="editing && editingMeta.readOnlyBaseUrl" class="flex flex-column gap-1">
          <label for="cred-base-url-ro" class="text-sm font-medium">Base URL (fixa do provedor)</label>
          <InputText id="cred-base-url-ro" :modelValue="editingMeta.defaultBaseUrl ?? ''" readonly data-testid="cred-baseurl-readonly" class="w-full font-mono text-sm" />
          <small class="text-surface-500">Definida pelo provedor — não editável e não enviada no PUT.</small>
        </div>
        <div v-if="editingAcceptsBaseUrl" class="flex flex-column gap-1">
          <label for="cred-base-url" class="text-sm font-medium">Base URL</label>
          <InputText id="cred-base-url" v-model="baseUrl" placeholder="http://localhost:11434" data-testid="cred-baseurl-input" />
        </div>
      </div>
      <template #footer>
        <Button label="Cancelar" text @click="closeDialog" />
        <Button label="Salvar" :disabled="!canSave" :loading="saving" data-testid="cred-save-btn" @click="saveCredential" />
      </template>
    </Dialog>

    <Dialog v-model:visible="showDeleteConfirm" modal header="Remover credencial" :style="{ width: '26rem' }" data-testid="cred-delete-confirm">
      <p>Remover a chave de <strong>{{ deleteTarget ? VENDOR_CATALOG[deleteTarget].label : '' }}</strong>? Agentes desse provedor falharão com 422 até nova chave.</p>
      <template #footer>
        <Button label="Cancelar" text @click="showDeleteConfirm = false" />
        <Button label="Remover" severity="danger" :loading="deleting" data-testid="cred-delete-confirm-btn" @click="confirmDelete" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>
