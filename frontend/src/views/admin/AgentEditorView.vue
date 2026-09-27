<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import ToggleSwitch from 'primevue/toggleswitch'
import Accordion from 'primevue/accordion'
import AccordionPanel from 'primevue/accordionpanel'
import AccordionHeader from 'primevue/accordionheader'
import AccordionContent from 'primevue/accordioncontent'
import AutoComplete from 'primevue/autocomplete'
import Chip from 'primevue/chip'
import Dialog from 'primevue/dialog'
import Card from 'primevue/card'
import Toast from 'primevue/toast'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import InferenceParamsSliders from '../../components/admin/InferenceParamsSliders.vue'
import ProviderSelect from '../../components/ProviderSelect.vue'
import ConnectionTester from '../../components/ConnectionTester.vue'
import { agentAdminService } from '../../services/agent-admin.service'
import { promptService } from '../../services/prompt.service'
import { setBreadcrumbLabel } from '../../utils/breadcrumbOverride'
import { VENDOR_CATALOG, LGPD_OPENCODE_MESSAGE, toApiError, type AiProvider, type ValidateResponse } from '../../types/agent-admin'
import { useRemoteModels } from '../../composables/useRemoteModels'
import type { PromptTemplateDTO } from '../../types/prompt'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const isEdit = computed(() => !!route.params.id)
const id = computed(() => route.params.id as string | undefined)

const name = ref('')
const avatarUrl = ref('')
const description = ref('')
const systemPrompt = ref('')
const promptTemplateId = ref<string | null>(null)
const promptOptions = ref<PromptTemplateDTO[]>([])
const provider = ref<AiProvider>('OPENAI')
const model = ref(VENDOR_CATALOG.OPENAI.defaultModel)
const modelTouched = ref(false)
const baseUrl = ref('')
const temperature = ref<number | null>(0.7)
const topP = ref<number | null>(0.9)
const maxTokens = ref<number | null>(2048)
const tools = ref<string[]>([])
const isDefault = ref(false)
const active = ref(true)
const originalIsDefault = ref(false)
const saving = ref(false)
const loading = ref(false)
const showDefaultDialog = ref(false)
const pendingDefault = ref(false)
const hasExistingDefault = ref(false)
const agentNameLoaded = ref<string | null>(null)

const credentialConfigured = ref<Map<AiProvider, boolean>>(new Map())
const credentialsLoaded = ref(false)
const lastTest = ref<ValidateResponse | { ok: false; reason: string } | null>(null)

const isMigrated = computed(() => isEdit.value && provider.value === 'OPENAI_COMPATIBLE')
const providerMeta = computed(() => VENDOR_CATALOG[provider.value])
const keyConfigured = computed(() => credentialConfigured.value.get(provider.value) ?? null)
const isExternalProvider = computed(() => provider.value === 'OPENCODE_GO' || provider.value === 'OPENCODE_ZEN')

const remote = useRemoteModels(provider)

/**
 * Catálogo bruto do provedor: remotos vêm do discovery, locais do catálogo estático.
 * Única fonte de dados do campo "Modelo".
 */
const modelCatalog = computed<string[]>(() =>
  remote.isRemote.value ? remote.models.value : providerMeta.value.models,
)

/**
 * Única lista ligada ao AutoComplete via `:suggestions`.
 *
 * O PrimeVue 4 **só abre o painel** quando a referência do array `suggestions` muda
 * enquanto `searching === true` (ver `AutoComplete.vue` → watcher `suggestions()`,
 * chamado a partir de `search()` no `onDropdownClick`/`onInput`). Se o handler de
 * `@complete` escrevesse em outro array, o watcher nunca dispararia, `overlayVisible`
 * ficaria `false` e o panel nunca seria criado no DOM.
 *
 * Por isso `onModelSearch` escreve AQUI, e nunca em `remote.models` (read-only do composable).
 */
const modelSuggestions = ref<string[]>([...VENDOR_CATALOG.OPENAI.models])

watch(
  modelCatalog,
  (list) => {
    modelSuggestions.value = [...list]
  },
  { immediate: true },
)

const chipModels = computed(() => modelCatalog.value.slice(0, 3))

const offCatalog = computed(() => {
  if (!model.value.trim() || !modelTouched.value) return false
  const list = modelCatalog.value
  if (!list.length) return false
  return !list.includes(model.value.trim())
})

function onProviderChange(next: AiProvider) {
  provider.value = next
  model.value = VENDOR_CATALOG[next].defaultModel
  modelTouched.value = false
  lastTest.value = null
  if (next === 'OLLAMA') {
    baseUrl.value = baseUrl.value || VENDOR_CATALOG.OLLAMA.defaultBaseUrl || ''
  } else {
    baseUrl.value = ''
  }
}

/** Filtra sempre a partir do catálogo bruto, nunca da lista já filtrada (backspace funciona). */
function onModelSearch(e: { query: string }) {
  const q = e.query.trim().toLowerCase()
  modelSuggestions.value = q
    ? modelCatalog.value.filter((m) => m.toLowerCase().includes(q))
    : [...modelCatalog.value]
}

function onModelBlur() {
  modelTouched.value = true
}

const modelError = computed(() => {
  if (!modelTouched.value) return null
  if (!model.value.trim()) return 'Modelo obrigatório'
  return null
})

const toolsSuggestions = ref<string[]>(['web_search', 'code_exec', 'file_read', 'api_call'])

async function loadPrompts(q?: string) {
  try {
    const res = await promptService.list({ page: 0, size: 20, q })
    promptOptions.value = res.content
  } catch { /* ignore */ }
}

async function loadCredentialStatus() {
  try {
    const list = await agentAdminService.listCredentials()
    credentialConfigured.value = new Map(list.map((s) => [s.provider, s.configured]))
    credentialsLoaded.value = true
  } catch { /* badge oculto; link permanece */ }
}

onUnmounted(() => setBreadcrumbLabel(null))

onMounted(async () => {
  loadPrompts()
  loadCredentialStatus()
  try {
    await agentAdminService.getDefault()
    hasExistingDefault.value = true
  } catch {
    hasExistingDefault.value = false
  }
  if (isEdit.value && id.value) {
    loading.value = true
    try {
      const dto = await agentAdminService.getById(id.value)
      name.value = dto.name
      agentNameLoaded.value = dto.name
      setBreadcrumbLabel(dto.name)
      avatarUrl.value = dto.avatarUrl ?? ''
      description.value = dto.description ?? ''
      systemPrompt.value = dto.systemPrompt ?? ''
      promptTemplateId.value = dto.promptTemplateId ?? null
      provider.value = dto.provider
      model.value = dto.model
      temperature.value = dto.temperature ?? null
      topP.value = dto.topP ?? null
      maxTokens.value = dto.maxTokens ?? null
      tools.value = dto.tools ?? []
      isDefault.value = dto.isDefault
      originalIsDefault.value = dto.isDefault
      active.value = dto.active
      if (dto.promptTemplateId) {
        const p = await promptService.getById(dto.promptTemplateId).catch(() => null)
        if (p && !promptOptions.value.find(x => x.id === p.id)) promptOptions.value.unshift(p)
      }
    } catch {
      toast.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar agente', life: 3000 })
    } finally {
      loading.value = false
    }
  }
})

function onDefaultToggle(val: boolean) {
  if (val && !originalIsDefault.value && hasExistingDefault.value) {
    pendingDefault.value = true
    showDefaultDialog.value = true
    return
  }
  if (val && !originalIsDefault.value && !hasExistingDefault.value) {
    isDefault.value = true
    return
  }
  isDefault.value = val
}

async function confirmDefault() {
  isDefault.value = true
  showDefaultDialog.value = false
  pendingDefault.value = false
  hasExistingDefault.value = true
  await save()
}

function cancelDefault() {
  showDefaultDialog.value = false
  pendingDefault.value = false
  isDefault.value = originalIsDefault.value
}

function isValidUrl(s: string) {
  if (!s) return true
  return /^https?:\/\/.+/.test(s)
}

function onTested(result: ValidateResponse | { ok: false; reason: string }) {
  lastTest.value = result
}

async function save() {
  if (!name.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Validação', detail: 'Nome obrigatório', life: 3000 })
    return
  }
  modelTouched.value = true
  if (!model.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Validação', detail: 'Modelo obrigatório', life: 3000 })
    return
  }
  if (avatarUrl.value && !isValidUrl(avatarUrl.value)) {
    toast.add({ severity: 'warn', summary: 'Validação', detail: 'Avatar deve ser URL http(s)', life: 3000 })
    return
  }
  saving.value = true
  const payload = {
    name: name.value.trim(),
    avatarUrl: avatarUrl.value.trim() || null,
    description: description.value.trim() || null,
    systemPrompt: systemPrompt.value || null,
    promptTemplateId: promptTemplateId.value || null,
    provider: provider.value,
    model: model.value.trim(),
    temperature: temperature.value,
    topP: topP.value,
    maxTokens: maxTokens.value,
    tools: tools.value.length ? tools.value : null,
    isDefault: isDefault.value,
    active: active.value,
  }
  const testFailed = lastTest.value !== null && !lastTest.value.ok
  try {
    if (isEdit.value && id.value) {
      await agentAdminService.update(id.value, payload)
    } else {
      await agentAdminService.create(payload)
    }
    if (testFailed) {
      toast.add({ severity: 'warn', summary: 'Salvo com aviso', detail: 'Teste de conexão falhou — verifique chave e modelo.', life: 5000 })
    } else {
      toast.add({ severity: 'success', summary: isEdit.value ? 'Atualizado' : 'Criado', detail: isEdit.value ? 'Agente atualizado' : 'Agente criado', life: 2000 })
    }
    setBreadcrumbLabel(null)
    router.push('/agents')
  } catch (err: unknown) {
    const problem = toApiError(err)
    const detail = problem.detail ?? 'Falha ao salvar'
    if (problem.status === 409) {
      toast.add({ severity: 'warn', summary: 'Conflito', detail, life: 4000 })
    } else if (problem.status === 422) {
      toast.add({ severity: 'warn', summary: 'Não processável', detail, life: 4000 })
    } else {
      toast.add({ severity: 'error', summary: 'Erro', detail, life: 4000 })
    }
  } finally {
    saving.value = false
  }
}

function onPromptSearch(e: { query: string }) {
  loadPrompts(e.query)
}

const selectedPrompt = computed(() => promptOptions.value.find(p => p.id === promptTemplateId.value) ?? null)
</script>

<template>
  <div class="agent-editor-view">
    <div class="flex align-items-center gap-3 mb-4">
      <Button icon="pi pi-arrow-left" text rounded aria-label="Voltar" @click="router.push('/agents')" data-testid="back-btn" />
      <h1 class="text-2xl font-bold m-0 text-900">{{ isEdit ? 'Editar Agente' : 'Novo Agente' }}</h1>
      <Tag v-if="isDefault" value="⭐ Padrão" severity="warn" class="ml-2" />
    </div>

    <div v-if="loading" class="text-surface-500">Carregando...</div>

    <Card v-else>
      <template #content>
        <Accordion :value="['0']" multiple>
          <!-- Básicas -->
          <AccordionPanel value="0">
            <AccordionHeader>Informações Básicas</AccordionHeader>
            <AccordionContent>
              <div class="grid">
                <div class="col-12 md:col-6 flex flex-column gap-1">
                  <label for="agent-name" class="text-sm font-medium">Nome *</label>
                  <InputText id="agent-name" v-model="name" placeholder="Assistente Padrão" data-testid="input-name" />
                </div>
                <div class="col-12 md:col-6 flex flex-column gap-1">
                  <label for="agent-avatar" class="text-sm font-medium">Avatar URL</label>
                  <InputText id="agent-avatar" v-model="avatarUrl" placeholder="https://cdn.example.com/avatar.png" data-testid="input-avatar" />
                </div>
                <div class="col-12 flex flex-column gap-1">
                  <label for="agent-desc" class="text-sm font-medium">Descrição</label>
                  <Textarea id="agent-desc" v-model="description" rows="2" placeholder="Descrição do agente" data-testid="input-desc" />
                </div>
              </div>
            </AccordionContent>
          </AccordionPanel>

          <!-- Provedor de IA -->
          <AccordionPanel value="1">
            <AccordionHeader>Provedor de IA</AccordionHeader>
            <AccordionContent>
              <Message v-if="isMigrated" severity="info" :closable="false" data-testid="migrated-banner" class="mb-3">
                Agente migrado (OPENCODE → Compatível OpenAI). O provedor está fixo; apenas o modelo pode ser ajustado.
              </Message>
              <Message v-if="provider === 'OLLAMA'" severity="info" :closable="false" data-testid="ollama-banner" class="mb-3">
                Ollama roda localmente, sem chave de API — restrinja a rede do endpoint.
              </Message>
              <Message
                v-if="isExternalProvider"
                severity="info"
                :closable="false"
                role="alert"
                data-testid="lgpd-banner"
                class="mb-3"
              >{{ LGPD_OPENCODE_MESSAGE }}</Message>

              <div v-if="isMigrated" class="flex flex-column gap-2" data-testid="migrated-provider-readonly">
                <span class="text-sm font-medium">Provedor</span>
                <span class="flex align-items-center gap-2">
                  <i :class="VENDOR_CATALOG.OPENAI_COMPATIBLE.icon" aria-hidden="true" />
                  <strong>{{ VENDOR_CATALOG.OPENAI_COMPATIBLE.label }}</strong>
                </span>
                <span class="text-sm text-surface-500">Modelo</span>
                <InputText v-model="model" data-testid="input-model-migrated" class="w-full" />
              </div>

              <div v-else class="flex flex-column gap-3">
                <ProviderSelect
                  :modelValue="provider"
                  :includeMigrated="isEdit && provider === 'OPENAI_COMPATIBLE'"
                  @update:modelValue="onProviderChange"
                />

                <div class="flex flex-column gap-1" aria-live="polite">
                  <label for="agent-model" class="text-sm font-medium">Modelo *</label>
                  <div v-if="remote.isRemote.value && remote.loading.value" data-testid="models-loading" aria-busy="true" class="flex flex-column gap-2">
                    <div class="p-skeleton h-2rem w-full" />
                    <small class="text-surface-500">Carregando modelos remotos...</small>
                  </div>
                  <template v-else>
                    <AutoComplete
                      v-model="model"
                      :suggestions="modelSuggestions"
                      @complete="onModelSearch"
                      @blur="onModelBlur"
                      placeholder="Selecione ou digite o modelo"
                      dropdown
                      completeOnFocus
                      class="w-full"
                      inputId="agent-model"
                      data-testid="input-model"
                    />
                  </template>
                  <small v-if="modelError" class="text-red-500" data-testid="model-error">{{ modelError }}</small>
                  <Tag
                    v-if="offCatalog"
                    value="Fora do catálogo — verifique o modelo"
                    severity="warn"
                    data-testid="model-offcatalog-badge"
                  />
                  <div
                    v-if="remote.isRemote.value && !remote.loading.value && !remote.models.value.length && !remote.error.value"
                    data-testid="models-empty"
                    class="flex align-items-center gap-2"
                  >
                    <small class="text-surface-500">Nenhum modelo remoto encontrado.</small>
                    <Button label="Recarregar" text size="small" data-testid="models-retry-btn" @click="remote.refresh()" />
                  </div>
                  <div
                    v-if="remote.isRemote.value && remote.error.value"
                    data-testid="models-error"
                    role="alert"
                    class="flex align-items-center gap-2"
                  >
                    <small class="text-red-500">{{ remote.error.value }}</small>
                    <Button label="Tentar novamente" text size="small" data-testid="models-retry-btn" @click="remote.refresh()" />
                  </div>
                  <div v-if="remote.isRemote.value && remote.stale.value" data-testid="models-stale" class="flex align-items-center gap-2">
                    <Tag :value="`Lista há ${remote.staleMinutes.value}min — pode estar desatualizada`" severity="warn" />
                    <Button label="Atualizar" text size="small" data-testid="models-refresh-btn" :loading="remote.loading.value" @click="remote.refresh()" />
                  </div>
                  <div class="flex flex-wrap gap-2 mt-1">
                    <Chip
                      v-for="m in chipModels"
                      :key="m"
                      :label="m"
                      class="cursor-pointer"
                      :data-testid="`model-chip-${m}`"
                      @click="model = m"
                    />
                  </div>
                </div>

                <div v-if="providerMeta.readOnlyBaseUrl && providerMeta.defaultBaseUrl" class="flex flex-column gap-1">
                  <label for="agent-base-url-ro" class="text-sm font-medium">Base URL (fixa do provedor)</label>
                  <InputText id="agent-base-url-ro" :modelValue="providerMeta.defaultBaseUrl" readonly data-testid="input-base-url-readonly" class="w-full font-mono text-sm" />
                </div>

                <div v-if="provider === 'OLLAMA' || (provider === 'OPENAI_COMPATIBLE' && !isMigrated)" class="flex flex-column gap-1">
                  <label for="agent-base-url" class="text-sm font-medium">Base URL</label>
                  <InputText id="agent-base-url" v-model="baseUrl" placeholder="http://localhost:11434" data-testid="input-base-url" />
                </div>

                <div class="flex align-items-center gap-2" data-testid="credential-badge">
                  <Tag
                    v-if="credentialsLoaded && keyConfigured === true && providerMeta.requiresKey"
                    value="Chave configurada ✅"
                    severity="success"
                  />
                  <Tag
                    v-else-if="credentialsLoaded && keyConfigured !== true && providerMeta.requiresKey"
                    value="Chave faltante ⚠️"
                    severity="warn"
                  />
                  <RouterLink v-if="providerMeta.requiresKey" to="/settings/providers" class="text-sm">
                    Gerenciar chaves
                  </RouterLink>
                </div>

                <ConnectionTester
                  :provider="provider"
                  :model="model"
                  :baseUrl="provider === 'OLLAMA' ? baseUrl : undefined"
                  @tested="onTested"
                />
              </div>
            </AccordionContent>
          </AccordionPanel>

          <!-- System Prompt -->
          <AccordionPanel value="2">
            <AccordionHeader>System Prompt</AccordionHeader>
            <AccordionContent>
              <div class="flex flex-column gap-3">
                <div class="flex flex-column gap-1">
                  <label class="text-sm font-medium">Vincular Prompt</label>
                  <AutoComplete
                    v-model="promptTemplateId"
                    :suggestions="promptOptions"
                    @complete="onPromptSearch"
                    optionLabel="name"
                    optionValue="id"
                    placeholder="Buscar prompt..."
                    dropdown
                    class="w-full"
                    data-testid="select-prompt"
                  >
                    <template #option="{ option }">
                      <div class="flex flex-column">
                        <span class="font-medium">{{ option.name }}</span>
                        <span class="text-xs text-surface-500">{{ option.code }}</span>
                      </div>
                    </template>
                  </AutoComplete>
                  <!-- simple select fallback -->
                  <select v-model="promptTemplateId" class="p-inputtext w-full mt-2" data-testid="select-prompt-fallback">
                    <option :value="null">— Nenhum —</option>
                    <option v-for="p in promptOptions" :key="p.id" :value="p.id">{{ p.name }} ({{ p.code }})</option>
                  </select>
                  <small v-if="selectedPrompt" class="text-surface-500">Herança: temperature/topP/maxTokens herdados se nulos</small>
                </div>
                <div class="flex flex-column gap-1">
                  <label for="agent-system" class="text-sm font-medium">System Prompt</label>
                  <Textarea id="agent-system" v-model="systemPrompt" rows="6" placeholder="Você é um assistente..." class="font-mono text-sm" data-testid="input-system" />
                </div>
              </div>
            </AccordionContent>
          </AccordionPanel>

          <!-- Config -->
          <AccordionPanel value="3">
            <AccordionHeader>Parâmetros de Inferência</AccordionHeader>
            <AccordionContent>
              <InferenceParamsSliders v-model:temperature="temperature" v-model:topP="topP" v-model:maxTokens="maxTokens" />
            </AccordionContent>
          </AccordionPanel>

          <!-- Tools/KBs -->
          <AccordionPanel value="4">
            <AccordionHeader>Tools & Knowledge Bases</AccordionHeader>
            <AccordionContent>
              <div class="grid">
                <div class="col-12 md:col-6 flex flex-column gap-2">
                  <label class="text-sm font-medium">Tools (max 20)</label>
                  <AutoComplete v-model="tools" multiple :suggestions="toolsSuggestions" placeholder="Digite e pressione Enter" class="w-full" data-testid="tools-input" />
                  <div class="flex flex-wrap gap-2">
                    <Chip v-for="(t,idx) in tools" :key="t" :label="t" removable @remove="tools.splice(idx,1)" />
                  </div>
                </div>
              </div>
            </AccordionContent>
          </AccordionPanel>

          <!-- Padrão -->
          <AccordionPanel value="5">
            <AccordionHeader>Padrão do Sistema</AccordionHeader>
            <AccordionContent>
              <div class="flex align-items-center gap-3">
                <ToggleSwitch :modelValue="isDefault" @update:modelValue="onDefaultToggle" data-testid="toggle-default" />
                <span class="font-medium">Definir como Agente Padrão do Sistema</span>
                <Tag v-if="isDefault" value="⭐ Padrão" severity="warn" />
              </div>
              <small class="text-surface-500 block mt-2">Apenas um agente pode ser padrão por tenant. Troca é atômica.</small>
              <div class="flex align-items-center gap-3 mt-3">
                <ToggleSwitch v-model="active" data-testid="toggle-active" :disabled="isDefault && originalIsDefault" v-tooltip.top="isDefault && originalIsDefault ? 'Defina outro como padrão antes de desativar.' : ''" />
                <span class="font-medium">Ativo</span>
              </div>
            </AccordionContent>
          </AccordionPanel>
        </Accordion>

        <div class="flex gap-3 justify-content-end mt-4 sticky bottom-0 surface-card p-2">
          <Button label="Cancelar" text @click="router.push('/agents')" />
          <Button :label="saving ? 'Salvando...' : 'Salvar'" :loading="saving" @click="save" data-testid="save-btn" />
        </div>
      </template>
    </Card>

    <Dialog v-model:visible="showDefaultDialog" modal header="Definir como padrão" :style="{ width: '28rem' }">
      <p>Já existe padrão. Trocar?</p>
      <template #footer>
        <Button label="Cancelar" text @click="cancelDefault" />
        <Button label="Confirmar" @click="confirmDefault" data-testid="confirm-default" />
      </template>
    </Dialog>

    <Toast />
  </div>
</template>
