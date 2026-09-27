<template>
  <div class="flex flex-column gap-4 llm-config-view">
    <div class="flex align-items-center justify-content-between gap-3 flex-wrap">
      <div>
        <h1 class="text-2xl font-bold m-0 text-900">Provedor e modelo do tenant</h1>
        <p class="text-surface-500 m-0 mt-1">
          É o que responde por todas as conversas deste tenant. Não há fallback: sem esta configuração
          o chat recusa com um erro dizendo exatamente esta tela.
        </p>
      </div>
      <Button
        label="Testar conexão"
        icon="pi pi-plug"
        severity="secondary"
        outlined
        :loading="testing"
        :disabled="!canTest"
        @click="testConnection"
      />
    </div>

    <Message v-if="loadError" severity="error" :closable="false">{{ loadError }}</Message>

    <Card>
      <template #content>
        <div class="flex flex-column gap-4">
          <Message v-if="!configured" severity="info" :closable="false">
            Nenhum provedor e modelo definidos ainda. Escolha os dois abaixo e salve.
          </Message>

          <div class="flex flex-column gap-2">
            <label for="llm-provider" class="font-semibold text-sm">Provedor</label>
            <Select
              id="llm-provider"
              v-model="provider"
              :options="vendorOptions"
              optionLabel="label"
              optionValue="provider"
              placeholder="Escolha um provedor"
              class="w-full"
              :disabled="saving"
              data-testid="llm-provider"
              @change="onProviderChanged"
            >
              <template #option="slotProps">
                <div class="flex flex-column">
                  <span class="font-semibold text-sm">{{ slotProps.option.label }}</span>
                  <span class="text-xs text-surface-500">{{ slotProps.option.description }}</span>
                </div>
              </template>
            </Select>
          </div>

          <div class="flex flex-column gap-2">
            <label for="llm-model" class="font-semibold text-sm">Modelo</label>
            <InputText
              id="llm-model"
              v-model="model"
              class="w-full"
              :disabled="saving"
              placeholder="gpt-4o-mini"
              data-testid="llm-model"
            />
            <small class="text-surface-500">
              Catálogo de {{ meta?.label ?? '—' }}:
              <span v-if="catalogModels.length">{{ catalogModels.join(', ') }}</span>
              <span v-else>consulta o provedor</span>
            </small>
          </div>

          <Message v-if="testResult" :severity="testResult.ok ? 'success' : 'warn'" :closable="false">
            <span v-if="testResult.ok">Resposta em {{ testResult.latencyMs }} ms.</span>
            <span v-else>Falhou: {{ testResult.reason }}. A chave pode estar errada ou o modelo não existir.</span>
          </Message>

          <Message v-if="saveError" severity="error" :closable="false">{{ saveError }}</Message>

          <div class="flex align-items-center gap-2">
            <Button
              :label="configured ? 'Salvar' : 'Salvar e usar no chat'"
              icon="pi pi-check"
              :loading="saving"
              :disabled="!canSave"
              data-testid="llm-save"
              @click="save"
            />
            <Button
              v-if="configured"
              label="Ir para o chat"
              icon="pi pi-comments"
              text
              @click="goToChat"
            />
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup lang="ts">
/**
 * The tenant's single default LLM — the screen the `TENANT_LLM_CONFIG_NOT_FOUND` message sends the
 * user to. It is the one place where the model is chosen, because the model is a property of the
 * tenant: one row, one vendor, one model, for every conversation.
 *
 * It lives next to the provider credentials on purpose. Both are "what this tenant talks to and with
 * what secret", and splitting them across two admin sections is how a tenant ends up with a key
 * configured and no model, or a model and no key.
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { agentAdminService } from '../../services/agent-admin.service'
import { VENDOR_CATALOG, VENDOR_ORDER } from '../../types/agent-admin'
import type { AiProvider, TenantLlmConfig, ValidateResponse, VendorMeta } from '../../types/agent-admin'

const router = useRouter()
const toast = useToast()

const provider = ref<AiProvider | null>(null)
const model = ref('')
const configured = ref(false)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const loadError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const testResult = ref<ValidateResponse | null>(null)

/**
 * A ordem vem de `VENDOR_ORDER` e não da ordem das chaves do objeto: o usuário lê esta lista todo
 * dia e a ordem estável é parte do contrato. Passar o array de strings direto faria o PrimeVue
 * procurar `.label` numa string — daí a lista de `VendorMeta`.
 */
const vendorOptions = computed<VendorMeta[]>(() =>
  VENDOR_ORDER.map(p => VENDOR_CATALOG[p]).filter(Boolean)
)

const meta = computed(() => (provider.value ? VENDOR_CATALOG[provider.value] : null))
const catalogModels = computed(() => meta.value?.models ?? [])
const canSave = computed(() => !!(provider.value && model.value.trim()) && !saving.value && !loading.value)
const canTest = computed(() => !!(provider.value && model.value.trim()) && !testing.value)

/**
 * Switching vendor resets the model to that vendor's default, because the previous value almost
 * certainly is not in the new catalog — and `VendorCatalog.validate` on the server would reject it
 * with a message about a model the user never typed here.
 */
function onProviderChanged() {
  const next = provider.value ? VENDOR_CATALOG[provider.value] : null
  model.value = next?.defaultModel ?? ''
  testResult.value = null
}

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const config: TenantLlmConfig | null = await agentAdminService.getLlmConfig()
    if (config) {
      configured.value = true
      provider.value = config.provider
      model.value = config.model
    } else {
      configured.value = false
    }
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : 'Erro ao carregar a configuração do tenant'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!provider.value) return
  saving.value = true
  saveError.value = null
  try {
    const saved = await agentAdminService.saveLlmConfig({ provider: provider.value, model: model.value.trim() })
    configured.value = true
    provider.value = saved.provider
    model.value = saved.model
    toast.add({ severity: 'success', summary: 'Configuração salva', detail: `${saved.provider} / ${saved.model}`, life: 3000 })
  } catch (e: unknown) {
    saveError.value = e instanceof Error ? e.message : 'Não foi possível salvar a configuração'
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  if (!provider.value) return
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await agentAdminService.validate({
      provider: provider.value,
      model: model.value.trim(),
    })
  } catch (e: unknown) {
    saveError.value = e instanceof Error ? e.message : 'Não foi possível testar a conexão'
  } finally {
    testing.value = false
  }
}

function goToChat() {
  router.push('/chat')
}

onMounted(load)
</script>

<style scoped>
.llm-config-view {
  max-width: 46rem;
}
</style>
