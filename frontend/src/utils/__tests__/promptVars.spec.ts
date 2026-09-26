import { describe, it, expect } from 'vitest'
import { extractVars, validateTemplate, renderPreview } from '../promptVars'

describe('extractVars', () => {
  it('extracts single var', () => {
    expect(extractVars('Olá {{nome}}')).toEqual(['nome'])
  })
  it('dedups and preserves order', () => {
    expect(extractVars('{{nome}} {{tarefa}} {{nome}}')).toEqual(['nome', 'tarefa'])
  })
  it('trims spaces', () => {
    expect(extractVars('{{ nome }}')).toEqual(['nome'])
  })
  it('returns empty when no vars', () => {
    expect(extractVars('Olá mundo')).toEqual([])
  })
  it('ignores invalid pattern', () => {
    expect(extractVars('{{1bad}}')).toEqual([])
    expect(extractVars('{{}}')).toEqual([])
    expect(extractVars('{{var com espaço}}')).toEqual([])
  })
  it('handles underscore start', () => {
    expect(extractVars('{{_var1}}')).toEqual(['_var1'])
  })
})

describe('validateTemplate', () => {
  it('valid template returns true', () => {
    expect(validateTemplate('Olá {{nome}}')).toBe(true)
  })
  it('invalid var returns false', () => {
    expect(validateTemplate('{{1bad}}')).toBe(false)
  })
  it('no mustache returns true', () => {
    expect(validateTemplate('plain text')).toBe(true)
  })
})

describe('renderPreview', () => {
  it('replaces vars', () => {
    expect(renderPreview('Olá {{nome}}', { nome: 'João' })).toBe('Olá João')
  })
  it('keeps placeholder when missing', () => {
    expect(renderPreview('{{nome}} {{tarefa}}', { nome: 'A' })).toBe('A {{tarefa}}')
  })
})
