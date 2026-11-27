import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ChatMessageDTO } from '@/services/chat.types'
import { ChatThread } from './ChatThread'

if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {}

const S = '22222222-2222-2222-2222-222222222222'
const messages: ChatMessageDTO[] = [
  { id: 'u', sessionId: S, role: 'USER', content: 'Explique', createAt: '2026-11-27T12:00:00Z' },
  {
    id: 'a',
    sessionId: S,
    role: 'ASSISTANT',
    content: 'Resposta parcial',
    reasoning: 'Analisando o pedido\nConsultando o contexto',
    createAt: '2026-11-27T12:00:01Z',
  },
]

describe('ChatThread — reasoning incremental', () => {
  it('mostra o bloco de pensamento aberto durante a geração, sem metadata enganosa', () => {
    render(
      <ChatThread
        messages={messages}
        generating
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
        onStarter={vi.fn()}
      />
    )

    expect(screen.getByText('Raciocínio do modelo')).toBeTruthy()
    expect(screen.getByText(/Analisando o pedido/)).toBeTruthy()
    expect(screen.getByText(/Consultando o contexto/)).toBeTruthy()
    expect(screen.getByText('Resposta parcial')).toBeTruthy()
    expect(screen.queryByText(/etapas/)).toBeNull()
  })

  it('sem reasoning não renderiza o bloco', () => {
    render(
      <ChatThread
        messages={messages.map((message) => ({ ...message, reasoning: undefined }))}
        onLoadOlder={vi.fn()}
        onRetry={vi.fn()}
        onStarter={vi.fn()}
      />
    )
    expect(screen.queryByText('Raciocínio do modelo')).toBeNull()
  })
})
