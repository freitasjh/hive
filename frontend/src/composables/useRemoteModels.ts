import { computed, ref, watch } from 'vue'
import { agentAdminService } from '../services/agent-admin.service'
import { VENDOR_CATALOG, toApiError, type AiProvider } from '../types/agent-admin'

const STALE_MS = 5 * 60 * 1000

export function isRemoteProvider(provider: AiProvider): boolean {
  return provider === 'OPENCODE_GO' || provider === 'OPENCODE_ZEN'
}

export function useRemoteModels(providerRef: { value: AiProvider }) {
  const models = ref<string[]>([])
  const filteredOut = ref<{ id: string; reason: string }[]>([])
  const cachedAt = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const lastProvider = ref<AiProvider | null>(null)

  const isRemote = computed(() => isRemoteProvider(providerRef.value))

  const stale = computed(() => {
    if (!cachedAt.value) return false
    return Date.now() - new Date(cachedAt.value).getTime() > STALE_MS
  })

  const staleMinutes = computed(() => {
    if (!cachedAt.value) return 0
    return Math.max(1, Math.floor((Date.now() - new Date(cachedAt.value).getTime()) / 60000))
  })

  async function fetchModels(refresh = false) {
    const provider = providerRef.value
    if (!isRemoteProvider(provider)) {
      models.value = [...VENDOR_CATALOG[provider].models]
      filteredOut.value = []
      cachedAt.value = null
      error.value = null
      lastProvider.value = provider
      return
    }
    loading.value = true
    error.value = null
    try {
      const res = await agentAdminService.listProviderModels(provider, refresh)
      models.value = res.models
      filteredOut.value = res.filteredOut ?? []
      cachedAt.value = res.cachedAt
      lastProvider.value = provider
    } catch (e) {
      const apiErr = toApiError(e)
      error.value = apiErr.detail ?? 'Falha ao carregar modelos remotos.'
      if (!models.value.length) {
        models.value = [...VENDOR_CATALOG[provider].models]
      }
    } finally {
      loading.value = false
    }
  }

  watch(
    () => providerRef.value,
    () => fetchModels(false),
    { immediate: true },
  )

  return {
    models,
    filteredOut,
    cachedAt,
    loading,
    error,
    stale,
    staleMinutes,
    isRemote,
    fetchModels,
    refresh: () => fetchModels(true),
  }
}
