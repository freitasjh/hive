"use client"

import { useCallback, useEffect, useState } from "react"
import type { PublicApiClient } from "@/services/admin.types"
import { publicApiAdminService } from "@/services/public-api-admin.service"
import { useAdminI18n } from "../i18n"
import type {
  OneShotSecret,
  PublicClientPromptDraft,
  PublicClientsActions,
  PublicClientsFormState,
  PublicClientsState,
} from "../state"
import { publicClientsError } from "./error"
import { emptyPublicClientForm, toCreatePayload, validatePublicClientForm } from "./form"
import { PublicClientsView } from "./PublicClientsView"

const initialState: PublicClientsState = {
  items: [],
  total: 0,
  page: 0,
  size: 20,
  q: "",
  loading: true,
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

export function PublicClientsContainer() {
  const { t, locale } = useAdminI18n()
  const [state, setState] = useState<PublicClientsState>(initialState)
  /**
   * Secret plaintext one-shot: estado local isolado do estado da tela. Nunca entra
   * em `state.items`, localStorage, URL ou log; o dismiss apaga a referência.
   */
  const [oneShot, setOneShot] = useState<OneShotSecret | null>(null)

  const describe = useCallback(
    (error: unknown, fallback: Parameters<typeof publicClientsError>[2]) =>
      publicClientsError(error, { locale, t }, fallback),
    [locale, t]
  )

  const load = useCallback(
    async (page: number, size: number) => {
      setState((current) => ({ ...current, loading: true, error: null }))
      try {
        const result = await publicApiAdminService.list(page, size)
        setState((current) => ({
          ...current,
          items: result.content,
          total: result.totalElements,
          page: result.page,
          size: result.size,
          loading: false,
        }))
      } catch (error) {
        setState((current) => ({
          ...current,
          loading: false,
          error: describe(error, "admin.publicClients.loadError"),
        }))
      }
    },
    [describe]
  )

  useEffect(() => {
    void load(0, 20)
  }, [load])

  const actions: PublicClientsActions = {
    onQueryChange(value) {
      setState((current) => ({ ...current, q: value }))
    },
    onPageChange(page, size) {
      setState((current) => ({ ...current, page, size }))
      void load(page, size)
    },
    onRetry() {
      void load(state.page, state.size)
    },
    onOpenCreate() {
      setState((current) => ({
        ...current,
        createOpen: true,
        form: emptyPublicClientForm,
        validationError: null,
        actionError: null,
        notice: null,
      }))
    },
    onCloseCreate() {
      setState((current) => ({
        ...current,
        createOpen: false,
        form: emptyPublicClientForm,
        validationError: null,
      }))
    },
    onFormChange(patch: Partial<PublicClientsFormState>) {
      setState((current) => ({
        ...current,
        form: { ...current.form, ...patch },
        validationError: null,
      }))
    },
    onToggleGrant(grant, checked) {
      setState((current) => ({
        ...current,
        form: {
          ...current.form,
          grants: checked
            ? Array.from(new Set([...current.form.grants, grant]))
            : current.form.grants.filter((value) => value !== grant),
        },
        validationError: null,
      }))
    },
    onPromptChange(index: number, patch: Partial<PublicClientPromptDraft>) {
      setState((current) => ({
        ...current,
        form: {
          ...current.form,
          prompts: current.form.prompts.map((prompt, position) =>
            position === index ? { ...prompt, ...patch } : prompt
          ),
        },
        validationError: null,
      }))
    },
    onAddPrompt() {
      setState((current) => ({
        ...current,
        form: {
          ...current.form,
          prompts: [
            ...current.form.prompts,
            { promptCode: "", pinMode: "FOLLOW_ACTIVE", pinnedVersion: null },
          ],
        },
        validationError: null,
      }))
    },
    onRemovePrompt(index) {
      setState((current) => ({
        ...current,
        form: {
          ...current.form,
          prompts: current.form.prompts.filter((_, position) => position !== index),
        },
        validationError: null,
      }))
    },
    async onCreate() {
      const validationKey = validatePublicClientForm(state.form)
      if (validationKey) {
        setState((current) => ({ ...current, validationError: t(validationKey) }))
        return
      }
      const payload = toCreatePayload(state.form)
      setState((current) => ({ ...current, creating: true, validationError: null, actionError: null }))
      try {
        const result = await publicApiAdminService.create(payload)
        setOneShot({
          secret: result.secret,
          scope: "create",
          clientId: result.client.clientId,
          label: payload.label,
          previousSecretExpiresAt: null,
        })
        setState((current) => ({
          ...current,
          creating: false,
          createOpen: false,
          form: emptyPublicClientForm,
          notice: t("admin.publicClients.created"),
        }))
        await load(0, state.size)
      } catch (error) {
        setState((current) => ({
          ...current,
          creating: false,
          actionError: describe(error, "admin.publicClients.createError"),
        }))
      }
    },
    async onRotate(client: PublicApiClient) {
      setState((current) => ({ ...current, rotatingClientId: client.clientId, actionError: null, notice: null }))
      try {
        const result = await publicApiAdminService.rotate(client.clientId)
        setOneShot({
          secret: result.secret,
          scope: "rotate",
          clientId: result.clientId,
          label: client.label,
          previousSecretExpiresAt: result.previousSecretExpiresAt,
        })
        setState((current) => ({ ...current, rotatingClientId: null, notice: t("admin.publicClients.rotated") }))
        await load(state.page, state.size)
      } catch (error) {
        setState((current) => ({
          ...current,
          rotatingClientId: null,
          actionError: describe(error, "admin.publicClients.rotateError"),
        }))
      }
    },
    onAskRevoke(client) {
      setState((current) => ({ ...current, revokeTarget: client, actionError: null, notice: null }))
    },
    onCancelRevoke() {
      setState((current) => ({ ...current, revokeTarget: null }))
    },
    async onConfirmRevoke() {
      const target: PublicApiClient | null = state.revokeTarget
      if (!target) return
      setState((current) => ({ ...current, revoking: true, actionError: null }))
      try {
        await publicApiAdminService.revoke(target.clientId)
        setState((current) => ({
          ...current,
          revoking: false,
          revokeTarget: null,
          notice: t("admin.publicClients.revokedNotice"),
        }))
        await load(state.page, state.size)
      } catch (error) {
        setState((current) => ({
          ...current,
          revoking: false,
          actionError: describe(error, "admin.publicClients.revokeError"),
        }))
      }
    },
    onDismissSecret() {
      setOneShot(null)
    },
  }

  return <PublicClientsView state={state} actions={actions} oneShot={oneShot} />
}
