"use client"

import { useCallback, useEffect, useRef, useState } from 'react'
import { agentAdminService } from '@/services/agent-admin.service'
import type { AiProvider } from '@/services/admin.types'
import { apiErrorMessage } from '../error-message'
import { useAdminI18n } from '../i18n'
import type { LlmConfigActions, LlmConfigState } from '../state'
import { isRemoteProvider, VENDOR_CATALOG } from '../vendor-catalog'
import { LlmConfigView } from './LlmConfigView'

const initialState: LlmConfigState = {
  provider: null,
  model: '',
  configured: false,
  loading: true,
  saving: false,
  testing: false,
  loadError: null,
  saveError: null,
  testResult: null,
  remoteModels: [],
  remoteLoading: false,
  remoteError: null,
  notice: null,
}

export function LlmConfigContainer() {
  const { t } = useAdminI18n()
  const [state, setState] = useState<LlmConfigState>(initialState)
  const reqRef = useRef(0)

  const fetchRemoteModels = useCallback(
    async (provider: AiProvider, refresh = false, resetModel = false) => {
      const seq = ++reqRef.current
      setState((current) => ({ ...current, remoteLoading: true, remoteError: null }))
      try {
        const response = await agentAdminService.listProviderModels(provider, refresh)
        if (seq !== reqRef.current) return
        const models = response.models ?? []
        setState((current) => ({
          ...current,
          remoteModels: models,
          remoteLoading: false,
          model: resetModel ? (models[0] ?? VENDOR_CATALOG[provider].defaultModel) : current.model,
        }))
      } catch (error) {
        if (seq !== reqRef.current) return
        setState((current) => ({
          ...current,
          remoteError: apiErrorMessage(error, t('admin.llm.loadError')),
          remoteModels: current.remoteModels.length
            ? current.remoteModels
            : [...VENDOR_CATALOG[provider].models],
          remoteLoading: false,
          model: resetModel ? VENDOR_CATALOG[provider].defaultModel : current.model,
        }))
      }
    },
    [t]
  )

  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, loadError: null }))
    try {
      const config = await agentAdminService.getLlmConfig()
      if (config) {
        const provider = config.provider
        setState((current) => ({
          ...current,
          loading: false,
          configured: true,
          provider,
          model: config.model,
        }))
        if (isRemoteProvider(provider)) void fetchRemoteModels(provider, false, false)
      } else {
        setState((current) => ({ ...current, loading: false, configured: false }))
      }
    } catch (error) {
      setState((current) => ({
        ...current,
        loading: false,
        loadError: apiErrorMessage(error, t('admin.llm.loadError')),
      }))
    }
  }, [fetchRemoteModels, t])

  useEffect(() => {
    void load()
  }, [load])

  const actions: LlmConfigActions = {
    onProviderChange(provider) {
      setState((current) => ({
        ...current,
        provider,
        model: provider ? VENDOR_CATALOG[provider].defaultModel : '',
        testResult: null,
        remoteModels: [],
        remoteError: null,
        remoteLoading: false,
      }))
      if (provider && isRemoteProvider(provider)) void fetchRemoteModels(provider, false, true)
    },
    onModelChange(model) {
      setState((current) => ({ ...current, model }))
    },
    async onSave() {
      if (!state.provider) return
      setState((current) => ({ ...current, saving: true, saveError: null }))
      try {
        const saved = await agentAdminService.saveLlmConfig({
          provider: state.provider as AiProvider,
          model: state.model.trim(),
        })
        setState((current) => ({
          ...current,
          saving: false,
          configured: true,
          provider: saved.provider,
          model: saved.model,
          notice: t('admin.llm.saved', { provider: saved.provider, model: saved.model }),
        }))
      } catch (error) {
        setState((current) => ({
          ...current,
          saving: false,
          saveError: apiErrorMessage(error, t('admin.llm.saveError')),
        }))
      }
    },
    async onTest() {
      if (!state.provider) return
      setState((current) => ({ ...current, testing: true, testResult: null, saveError: null }))
      try {
        const result = await agentAdminService.validate({
          provider: state.provider as AiProvider,
          model: state.model.trim(),
        })
        setState((current) => ({ ...current, testing: false, testResult: result }))
      } catch (error) {
        setState((current) => ({
          ...current,
          testing: false,
          saveError: apiErrorMessage(error, t('admin.llm.testError')),
        }))
      }
    },
    onRefreshModels() {
      if (state.provider && isRemoteProvider(state.provider)) {
        void fetchRemoteModels(state.provider, true, false)
      }
    },
    onRetry() {
      void load()
    },
  }

  return <LlmConfigView state={state} actions={actions} />
}
