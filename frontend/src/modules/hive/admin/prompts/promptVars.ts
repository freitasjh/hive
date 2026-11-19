/** Porta exata de bkp/front_bkp/src/utils/promptVars.ts (preview local, sem chamada de LLM). */

export const VAR_PATTERN = /\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}/g

export function extractVars(template: string): string[] {
  if (!template || !template.includes("{{")) return []
  const seen = new Set<string>()
  const result: string[] = []
  const re = new RegExp(VAR_PATTERN.source, "g")
  let match: RegExpExecArray | null
  while ((match = re.exec(template)) !== null) {
    const name = match[1]
    if (!seen.has(name)) {
      seen.add(name)
      result.push(name)
    }
  }
  return result
}

export function renderPreview(template: string, values: Record<string, string>): string {
  if (!template) return ""
  return template.replace(new RegExp(VAR_PATTERN.source, "g"), (_match, varName: string) => {
    const value = values[varName]
    return value !== undefined && value !== "" ? value : `{{${varName}}}`
  })
}
