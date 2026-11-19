"use client"

import { useCallback, useEffect, useState } from 'react'
import { promptService } from '@/services/prompt.service'
import type { PromptStatus } from '@/services/admin.types'
import { apiErrorMessage } from '../error-message'
import { useAdminI18n } from '../i18n'
import type { PromptEditorActions, PromptEditorState } from '../state'
import { PromptEditorView } from './PromptEditorView'

export interface PromptEditorContainerProps {
  promptId: string | null
  onSaved: () => void
  onBack: () => void
}

function blankState(promptId: string | null): PromptEditorState {
  return {
    isEdit: Boolean(promptId),
    promptId,
    name: '',
    code: '',
    description: '',
    tags: [],
    tagsInput: '',
    template: '',
    status: 'DRAFT',
    temperature: 0.7,
    topP: 0.9,
    maxTokens: 2048,
    loading: Boolean(promptId),
    saving: false,
    error: null,
    validationError: null,
  }
}

export function PromptEditorContainer({ promptId, onSaved, onBack }: PromptEditorContainerProps) {
  const { t } = useAdminI18n()
  const [state, setState] = useState<PromptEditorState>(() => blankState(promptId))

  const load = useCallback(async () => {
    if (!promptId) {
      setState(blankState(null))
      return
    }
    setState(blankState(promptId))
    try {
      const dto = await promptService.getById(promptId)
      setState({
        isEdit: true,
        promptId,
        name: dto.name,
        code: dto.code,
        description: dto.description ?? '',
        tags: dto.tags ?? [],
        tagsInput: '',
        template: dto.template,
        status: dto.status,
        temperature: dto.inferenceParams?.temperature ?? null,
        topP: dto.inferenceParams?.topP ?? null,
        maxTokens: dto.inferenceParams?.maxTokens ?? null,
        loading: false,
        saving: false,
        error: null,
        validationError: null,
      })
    } catch (error) {
      setState((current) => ({
        ...current,
        loading: false,
        error: apiErrorMessage(error, t('admin.editor.loadError')),
      }))
    }
  }, [promptId, t])

  useEffect(() => {
    void load()
  }, [load])

  const actions: PromptEditorActions = {
    onNameChange(value) {
      setState((current) => ({ ...current, name: value, validationError: null }))
    },
    onCodeChange(value) {
      setState((current) => ({ ...current, code: value, validationError: null }))
    },
    onDescriptionChange(value) {
      setState((current) => ({ ...current, description: value }))
    },
    onTagsInputChange(value) {
      setState((current) => ({ ...current, tagsInput: value }))
    },
    onAddTag() {
      const tag = state.tagsInput.trim()
      if (!tag) return
      if (state.tags.length >= 20) {
        setState((current) => ({ ...current, validationError: t('admin.editor.maxTags') }))
        return
      }
      setState((current) => ({
        ...current,
        tags: current.tags.includes(tag) ? current.tags : [...current.tags, tag],
        tagsInput: '',
      }))
    },
    onRemoveTag(index) {
      setState((current) => ({ ...current, tags: current.tags.filter((_, position) => position !== index) }))
    },
    onTemplateChange(value) {
      setState((current) => ({ ...current, template: value, validationError: null }))
    },
    onStatusChange(status: PromptStatus) {
      setState((current) => ({ ...current, status }))
    },
    onTemperatureChange(value) {
      setState((current) => ({ ...current, temperature: value }))
    },
    onTopPChange(value) {
      setState((current) => ({ ...current, topP: value }))
    },
    onMaxTokensChange(value) {
      setState((current) => ({ ...current, maxTokens: value }))
    },
    async onSave() {
      if (!state.name.trim() || !state.code.trim() || !state.template.trim()) {
        setState((current) => ({ ...current, validationError: t('admin.editor.validation') }))
        return
      }
      const payload = {
        name: state.name.trim(),
        description: state.description.trim() || null,
        tags: state.tags.length ? state.tags : null,
        template: state.template,
        inferenceParams: {
          temperature: state.temperature,
          topP: state.topP,
          maxTokens: state.maxTokens,
        },
        status: state.status,
      }
      setState((current) => ({ ...current, saving: true, error: null }))
      try {
        if (state.isEdit && state.promptId) {
          await promptService.update(state.promptId, payload)
        } else {
          await promptService.create({ ...payload, code: state.code.trim() })
        }
        setState((current) => ({ ...current, saving: false, validationError: null }))
        onSaved()
      } catch (error) {
        setState((current) => ({
          ...current,
          saving: false,
          error: apiErrorMessage(error, t('admin.editor.saveError')),
        }))
      }
    },
    onBack,
    onRetry() {
      void load()
    },
  }

  return <PromptEditorView state={state} actions={actions} />
}
