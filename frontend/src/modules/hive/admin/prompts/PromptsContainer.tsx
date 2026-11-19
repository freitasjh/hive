"use client"

import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/services/api'
import { promptService } from '@/services/prompt.service'
import type { PromptStatus, PromptTemplateDTO } from '@/services/admin.types'
import { apiErrorMessage } from '../error-message'
import { useAdminI18n } from '../i18n'
import type { PromptsActions, PromptsState } from '../state'
import { PromptsView } from './PromptsView'

interface ListParams {
  page: number
  size: number
  q: string
  status: PromptStatus | null
}

const initialState: PromptsState = {
  items: [],
  total: 0,
  page: 0,
  size: 20,
  q: '',
  status: null,
  loading: true,
  error: null,
  actionError: null,
  notice: null,
  deleteTarget: null,
  deleting: false,
}

export interface PromptsContainerProps {
  onNew?: () => void
  onEdit?: (id: string) => void
}

export function PromptsContainer({ onNew, onEdit }: PromptsContainerProps = {}) {
  const { t } = useAdminI18n()
  const [state, setState] = useState<PromptsState>(initialState)
  const paramsRef = useRef<ListParams>({ page: 0, size: 20, q: '', status: null })

  const load = useCallback(
    async (overrides: Partial<ListParams> = {}) => {
      const params = { ...paramsRef.current, ...overrides }
      paramsRef.current = params
      setState((current) => ({ ...current, loading: true, error: null, actionError: null }))
      try {
        const page = await promptService.list({
          page: params.page,
          size: params.size,
          q: params.q || undefined,
          status: params.status ?? undefined,
        })
        setState((current) => ({
          ...current,
          items: page.content,
          total: page.totalElements,
          page: params.page,
          size: params.size,
          loading: false,
        }))
      } catch (error) {
        setState((current) => ({
          ...current,
          loading: false,
          error: apiErrorMessage(error, t('admin.prompts.loadError')),
        }))
      }
    },
    [t]
  )

  useEffect(() => {
    void load()
  }, [load])

  const actions: PromptsActions = {
    onQueryChange(value) {
      paramsRef.current = { ...paramsRef.current, q: value }
      setState((current) => ({ ...current, q: value }))
    },
    onSearch() {
      void load({ page: 0, q: paramsRef.current.q })
    },
    onStatusChange(status) {
      paramsRef.current = { ...paramsRef.current, status, page: 0 }
      setState((current) => ({ ...current, status, page: 0 }))
      void load({ status, page: 0 })
    },
    onPageChange(page, size) {
      paramsRef.current = { ...paramsRef.current, page, size }
      setState((current) => ({ ...current, page, size }))
      void load({ page, size })
    },
    onNew() {
      if (onNew) onNew()
      else window.dispatchEvent(new CustomEvent('hive:admin-open-prompt-editor', { detail: { id: null } }))
    },
    onEdit(id) {
      if (onEdit) onEdit(id)
      else window.dispatchEvent(new CustomEvent('hive:admin-open-prompt-editor', { detail: { id } }))
    },
    onAskDelete(row: PromptTemplateDTO) {
      setState((current) => ({ ...current, deleteTarget: row, actionError: null }))
    },
    onCancelDelete() {
      setState((current) => ({ ...current, deleteTarget: null }))
    },
    async onConfirmDelete() {
      const target = state.deleteTarget
      if (!target) return
      setState((current) => ({ ...current, deleting: true, actionError: null, notice: null }))
      try {
        await promptService.remove(target.id)
        setState((current) => ({ ...current, deleting: false, deleteTarget: null, notice: t('admin.prompts.removed') }))
        await load()
      } catch (error) {
        const inUse = error instanceof ApiError && error.status === 409
        setState((current) => ({
          ...current,
          deleting: false,
          actionError: inUse
            ? apiErrorMessage(error, t('admin.prompts.inUse'))
            : apiErrorMessage(error, t('admin.prompts.deleteError')),
        }))
      }
    },
    onRetry() {
      void load()
    },
  }

  return <PromptsView state={state} actions={actions} />
}
