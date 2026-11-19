"use client"

import { useMemo, useState } from "react"
import { ArrowLeft, ChevronDown, ChevronUp, Copy, Plus, X } from "lucide-react"
import { Button } from "@/components/shadcn/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/shadcn/ui/card"
import { Input } from "@/components/shadcn/ui/input"
import { Label } from "@/components/shadcn/ui/label"
import type { PromptStatus } from "@/services/admin.types"
import { ErrorBanner, LoadingBlock, NoticeBanner } from "../components/AdminStates"
import { useAdminI18n } from "../i18n"
import type { PromptEditorActions, PromptEditorState } from "../state"
import { extractVars, renderPreview } from "./promptVars"

export interface PromptEditorViewProps {
  state: PromptEditorState
  actions: PromptEditorActions
}

const STATUSES: readonly PromptStatus[] = ["DRAFT", "ACTIVE", "ARCHIVED"]

/** Controle nativo estilizado com tokens — permite teclado e value sem depender de portal. */
function StatusSelect({
  value,
  onChange,
  disabled,
}: {
  value: PromptStatus
  onChange: (status: PromptStatus) => void
  disabled?: boolean
}) {
  const { t } = useAdminI18n()
  return (
    <div className="relative">
      <select
        id="prompt-status"
        name="status"
        data-testid="select-status"
        value={value}
        disabled={disabled}
        aria-label={t("admin.common.status")}
        onChange={(event) => onChange(event.target.value as PromptStatus)}
        className="h-10 w-full appearance-none rounded-md border border-input bg-transparent px-3 pr-8 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50 dark:bg-input/30"
      >
        {STATUSES.map((status) => (
          <option key={status} value={status}>
            {status}
          </option>
        ))}
      </select>
      <ChevronDown className="pointer-events-none absolute right-3 top-3 size-4 text-muted-foreground" aria-hidden="true" />
    </div>
  )
}

export function PromptEditorView({ state, actions }: PromptEditorViewProps) {
  const { t } = useAdminI18n()
  const [collapsed, setCollapsed] = useState(false)
  const [previewValues, setPreviewValues] = useState<Record<string, string>>({})
  const variables = useMemo(() => extractVars(state.template), [state.template])
  const rendered = useMemo(() => renderPreview(state.template, previewValues), [state.template, previewValues])

  async function copyPreview() {
    try {
      await navigator.clipboard.writeText(rendered)
      setPreviewValues((current) => ({ ...current }))
    } catch {
      setPreviewValues((current) => ({ ...current }))
    }
  }

  return (
    <section className="flex flex-col gap-5" aria-labelledby="admin-editor-title">
      <div className="flex items-center gap-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("admin.common.back")}
          data-testid="back-btn"
          onClick={actions.onBack}
        >
          <ArrowLeft aria-hidden="true" />
        </Button>
        <h1 id="admin-editor-title" className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
          {state.isEdit ? t("admin.editor.titleEdit") : t("admin.editor.titleNew")}
        </h1>
      </div>

      {state.error ? <ErrorBanner message={state.error} onRetry={actions.onRetry} testId="editor-error" /> : null}
      {state.loading ? <LoadingBlock testId="editor-loading" rows={6} /> : null}

      {!state.loading ? (
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
          <div className="lg:col-span-7">
            <Card>
              <CardContent className="flex flex-col gap-5">
                <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="prompt-name">{t("admin.editor.name")}</Label>
                    <Input
                      id="prompt-name"
                      name="name"
                      data-testid="input-name"
                      value={state.name}
                      onChange={(event) => actions.onNameChange(event.target.value)}
                      className="h-10 w-full"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="prompt-code">{t("admin.editor.code")}</Label>
                    <Input
                      id="prompt-code"
                      name="code"
                      data-testid="input-code"
                      value={state.code}
                      disabled={state.isEdit}
                      onChange={(event) => actions.onCodeChange(event.target.value)}
                      className="h-10 w-full font-mono"
                    />
                    {state.isEdit ? (
                      <small className="text-muted-foreground">{t("admin.editor.codeImmutable")}</small>
                    ) : null}
                  </div>
                  <div className="flex flex-col gap-1.5 sm:col-span-2">
                    <Label htmlFor="prompt-desc">{t("admin.common.description")}</Label>
                    <textarea
                      id="prompt-desc"
                      name="description"
                      data-testid="input-desc"
                      rows={2}
                      value={state.description}
                      placeholder={t("admin.editor.descriptionPlaceholder")}
                      onChange={(event) => actions.onDescriptionChange(event.target.value)}
                      className="flex min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30"
                    />
                  </div>
                  <div className="flex flex-col gap-2 sm:col-span-2">
                    <Label htmlFor="prompt-tag">{t("admin.common.tags")}</Label>
                    <div className="flex gap-2">
                      <Input
                        id="prompt-tag"
                        name="tags"
                        data-testid="input-tag"
                        value={state.tagsInput}
                        placeholder={t("admin.editor.tagPlaceholder")}
                        onChange={(event) => actions.onTagsInputChange(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            event.preventDefault()
                            actions.onAddTag()
                          }
                        }}
                        className="h-10 w-full"
                      />
                      <Button type="button" variant="outline" data-testid="add-tag-btn" onClick={actions.onAddTag}>
                        <Plus aria-hidden="true" />
                        {t("admin.editor.addTag")}
                      </Button>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {state.tags.map((tag, index) => (
                        <span
                          key={`${tag}-${index}`}
                          className="inline-flex items-center gap-1 rounded-full border border-border bg-muted/40 px-2 py-1 text-xs"
                        >
                          {tag}
                          <button
                            type="button"
                            data-testid={`remove-tag-${index}`}
                            aria-label={`${t("admin.common.delete")} ${tag}`}
                            onClick={() => actions.onRemoveTag(index)}
                            className="rounded-sm p-0.5 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
                          >
                            <X className="size-3" aria-hidden="true" />
                          </button>
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="prompt-status">{t("admin.common.status")}</Label>
                    <StatusSelect value={state.status} onChange={actions.onStatusChange} />
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="input-template">{t("admin.editor.template")}</Label>
                  <textarea
                    id="input-template"
                    name="template"
                    data-testid="input-template"
                    rows={10}
                    value={state.template}
                    onChange={(event) => actions.onTemplateChange(event.target.value)}
                    className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 font-mono text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30"
                  />
                </div>

                <div className="flex flex-col gap-3">
                  <p className="text-sm font-medium">{t("admin.editor.inference")}</p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    {([
                      ["temperature", t("admin.editor.temperature"), state.temperature, actions.onTemperatureChange, 0, 2, 0.1],
                      ["topP", t("admin.editor.topP"), state.topP, actions.onTopPChange, 0, 1, 0.05],
                      ["maxTokens", t("admin.editor.maxTokens"), state.maxTokens, actions.onMaxTokensChange, 256, 4096, 128],
                    ] as const).map(([key, label, value, onChange, min, max, step]) => (
                      <div key={key} className="flex flex-col gap-1.5">
                        <Label htmlFor={`param-${key}`}>{label}</Label>
                        <Input
                          id={`param-${key}`}
                          name={key}
                          type="number"
                          min={min}
                          max={max}
                          step={step}
                          value={value ?? ""}
                          onChange={(event) =>
                            onChange(event.target.value === "" ? null : Number(event.target.value))
                          }
                          className="h-10 w-full font-mono tabular-nums"
                        />
                      </div>
                    ))}
                  </div>
                </div>

                {state.validationError ? (
                  <NoticeBanner
                    message={state.validationError}
                    tone="warn"
                    testId="editor-validation-error"
                  />
                ) : null}

                <div className="flex justify-end gap-3">
                  <Button type="button" variant="ghost" onClick={actions.onBack}>
                    {t("admin.common.cancel")}
                  </Button>
                  <Button type="button" data-testid="save-btn" disabled={state.saving} onClick={actions.onSave}>
                    {state.saving ? t("admin.common.saving") : t("admin.common.save")}
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="lg:col-span-5">
            <Card className="h-full">
              <CardHeader className="flex-row items-center justify-between gap-2">
                <CardTitle>{t("admin.editor.playground")}</CardTitle>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-muted-foreground">{t("admin.editor.playgroundHint")}</span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-xs"
                    aria-label={collapsed ? t("admin.editor.expand") : t("admin.editor.collapse")}
                    data-testid="toggle-playground"
                    onClick={() => setCollapsed((current) => !current)}
                  >
                    {collapsed ? <ChevronDown aria-hidden="true" /> : <ChevronUp aria-hidden="true" />}
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {collapsed ? (
                  <p className="text-sm text-muted-foreground">{t("admin.editor.collapsed")}</p>
                ) : !state.template ? (
                  <NoticeBanner message={t("admin.editor.noTemplate")} tone="info" />
                ) : variables.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t("admin.editor.noVars")}</p>
                ) : (
                  <>
                    {variables.map((variable) => (
                      <div key={variable} className="flex flex-col gap-1.5">
                        <Label htmlFor={`var-${variable}`}>{variable}</Label>
                        <Input
                          id={`var-${variable}`}
                          name={`var-${variable}`}
                          data-testid={`var-input-${variable}`}
                          value={previewValues[variable] ?? ""}
                          placeholder={variable}
                          onChange={(event) =>
                            setPreviewValues((current) => ({ ...current, [variable]: event.target.value }))
                          }
                          className="h-10 w-full"
                        />
                      </div>
                    ))}
                    <div className="rounded-lg border border-border/70 bg-muted/40 p-3">
                      <div className="mb-2 flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-muted-foreground">
                          {t("admin.editor.preview")}
                        </span>
                        <Button
                          type="button"
                          variant="ghost"
                          size="xs"
                          data-testid="playground-copy"
                          onClick={() => void copyPreview()}
                        >
                          <Copy aria-hidden="true" />
                          {t("admin.editor.copy")}
                        </Button>
                      </div>
                      <pre
                        data-testid="playground-preview"
                        className="whitespace-pre-wrap break-words font-mono text-xs text-foreground"
                      >
                        {rendered || "—"}
                      </pre>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      ) : null}
    </section>
  )
}

/** Alias semântico para o nome usado pela tela legada. */
export const PromptEditorPanel = PromptEditorView
