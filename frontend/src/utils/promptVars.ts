export const VAR_PATTERN = /\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}/g

export function extractVars(template: string): string[] {
  if (!template || !template.includes('{{')) return []
  const seen = new Set<string>()
  const result: string[] = []
  const re = new RegExp(VAR_PATTERN.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(template)) !== null) {
    const name = m[1]
    if (!seen.has(name)) {
      seen.add(name)
      result.push(name)
    }
  }
  return result
}

export function validateTemplate(template: string): boolean {
  if (!template) return false
  if (!template.includes('{{') && !template.includes('}}')) return true
  // if contains {{ but extractVars empty => invalid token present
  if (template.includes('{{')) {
    const vars = extractVars(template)
    // check for any {{...}} that didn't match canonical pattern
    const anyMustache = /\{\{[^}]*\}\}/g
    const allMatches = template.match(anyMustache) ?? []
    if (allMatches.length > 0 && vars.length === 0) return false
    // if number of mustaches > vars, some invalid
    // Count valid replacements vs total mustaches
    const validCount = (template.match(new RegExp(VAR_PATTERN.source, 'g')) ?? []).length
    if (allMatches.length !== validCount) return false
  }
  return true
}

export function renderPreview(template: string, values: Record<string, string>): string {
  if (!template) return ''
  return template.replace(new RegExp(VAR_PATTERN.source, 'g'), (_match, varName: string) => {
    const v = values[varName]
    return v !== undefined && v !== '' ? v : `{{${varName}}}`
  })
}
