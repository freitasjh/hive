import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

const MODULES = [
  'tenant',
  'commons',
  'bootstrap',
  'identity',
  'security',
  'agent',
  'provider',
  'publicapi',
  'integration-test',
]

const STACK = [
  'Java 25',
  'Spring Boot 4.1.0',
  'PostgreSQL 16 + pgvector',
  'Redis',
  'RabbitMQ',
]

function overviewHeading() {
  return screen.getByRole('heading', { level: 1, name: 'Visão geral do Hive' })
}

describe('App', () => {
  it('monta o shell admin com visão inicial overview', () => {
    render(<App />)

    expect(overviewHeading()).toBeTruthy()
    expect(screen.getByRole('banner')).toBeTruthy()
    expect(
      screen.getByRole('navigation', { name: 'Navegação móvel inferior' })
    ).toBeTruthy()
    expect(screen.getByRole('button', { name: /Visão geral/ })).toBeTruthy()
  })

  it('exibe informações estáticas do Hive derivadas do repo', () => {
    render(<App />)

    expect(screen.getByText('HIVE AI')).toBeTruthy()
    expect(screen.getByText('Monolito modular')).toBeTruthy()
    expect(screen.getByText('9', { selector: 'span' })).toBeTruthy()
    expect(screen.getByText('5', { selector: 'span' })).toBeTruthy()

    for (const moduleName of MODULES) {
      expect(screen.getByText(moduleName)).toBeTruthy()
    }
    for (const technology of STACK) {
      expect(screen.getByText(technology)).toBeTruthy()
    }
  })

  it('navega para uma view rotulada e retorna à visão geral', () => {
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: /Atlas AI Copilot/ }))

    expect(screen.queryByRole('heading', { level: 1, name: 'Visão geral do Hive' })).toBeNull()
    expect(
      screen.getByRole('heading', { level: 1, name: 'Atlas AI Copilot' })
    ).toBeTruthy()
    expect(screen.getByText('Em preparação')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Voltar à visão geral' }))

    expect(overviewHeading()).toBeTruthy()
  })

  it('navega pela barra inferior móvel e mantém o shell acessível', () => {
    render(<App />)

    const mobileNav = screen.getByRole('navigation', {
      name: 'Navegação móvel inferior',
    })
    const transactions = screen.getAllByRole('button', { name: /Transações/i })

    fireEvent.click(transactions[transactions.length - 1])

    expect(
      screen.getByRole('heading', { level: 1, name: 'Transações' })
    ).toBeTruthy()
    expect(mobileNav).toBeTruthy()
  })
})
