import { fireEvent, render, screen, within } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import type { AiProvider } from "@/services/admin.types"
import type { ProviderCatalogEntry } from "@/services/chat.types"
import { ChatMarkdown } from "./ChatMarkdown"
import { ChatThread } from "./ChatThread"
import { ModelSelector } from "./ModelSelector"

const catalog: ProviderCatalogEntry[] = [
  { provider: "OPENAI", models: ["gpt-4o-mini", "gpt-4o"], defaultModel: "gpt-4o-mini", configured: true },
  {
    provider: "OPENCODE_ZEN",
    models: ["claude-sonnet-4-5"],
    defaultModel: "claude-sonnet-4-5",
    configured: true,
  },
  { provider: "GEMINI", models: ["gemini-2.0-flash"], defaultModel: "gemini-2.0-flash", configured: false },
]

const msg = (id: string, role: string, content: string) => ({
  id,
  sessionId: "s",
  role,
  content,
  createAt: "2026-11-20T12:00:00Z",
})

const threadBase = {
  onLoadOlder: () => {},
  onRetry: () => {},
  onStarter: () => {},
}

describe("ChatMarkdown — fence roteado para CodeBlock", () => {
  it("renderiza fence como CodeBlock com line numbers e botão de copiar", () => {
    const { container } = render(
      <ChatMarkdown content={'Antes\n\n```ts\nconst x = 1\nconst y = 2\n```\n\nDepois'} />
    )
    const block = container.querySelector('[data-slot="code-block"]')
    expect(block).toBeTruthy()
    expect(container.querySelector('[data-slot="code-block-content"][data-numbered]')).toBeTruthy()
    expect(container.querySelector('[data-slot="code-block-line"]')).toBeTruthy()
    const copy = container.querySelector('[data-slot="code-block-copy-button"]')
    expect(copy).toBeTruthy()
    expect(copy?.getAttribute("aria-label")).toMatch(/copiar/i)
    expect(block?.textContent).toContain("const x = 1")
    expect(screen.getByText("Antes")).toBeTruthy()
    expect(screen.getByText("Depois")).toBeTruthy()
  })

  it("código inline permanece inline (sem data-block / sem CodeBlock)", () => {
    const { container } = render(<ChatMarkdown content={"Use `npm test` agora."} />)
    expect(container.querySelector('[data-slot="code-block"]')).toBeNull()
    const inline = screen.getByText("npm test")
    expect(inline.tagName).toBe("CODE")
  })

  it("linguagem desconhecida continua renderizando o código em texto", () => {
    const { container } = render(<ChatMarkdown content={"```foobar\nlinha única\n```"} />)
    expect(container.querySelector('[data-slot="code-block"]')).toBeTruthy()
    expect(container.textContent).toContain("linha única")
  })
})

describe("ChatThread — AI Elements de scroll e mensagem", () => {
  it("usa MessageScroller com viewport, âncora na fala da pessoa e botão de jump traduzido", () => {
    const { container } = render(
      <ChatThread
        {...threadBase}
        messages={[msg("u1", "USER", "pergunta"), msg("a1", "ASSISTANT", "resposta")]}
      />
    )
    expect(container.querySelector('[data-slot="message-scroller"]')).toBeTruthy()
    const viewport = container.querySelector('[data-slot="message-scroller-viewport"]')
    expect(viewport).toBeTruthy()
    expect(viewport?.getAttribute("aria-label")).toMatch(/mensagens/i)
    expect(container.querySelector('[role="log"]')).toBeTruthy()

    const anchors = container.querySelectorAll('[data-slot="message-scroller-item"]')
    expect(anchors).toHaveLength(2)
    expect(anchors[0].getAttribute("data-scroll-anchor")).toBe("true")
    expect(anchors[1].getAttribute("data-scroll-anchor")).toBe("false")
    expect(anchors[0].getAttribute("data-message-id")).toBe("u1")

    const jump = container.querySelector('[data-slot="message-scroller-button"]')
    expect(jump).toBeTruthy()
    expect(within(jump as HTMLElement).getByText(/ir para o fim/i)).toBeTruthy()
  })

  it("pessoa usa Bubble end/muted e assistente usa Markdown com ações no MessageFooter", () => {
    const onRetry = vi.fn()
    const { container } = render(
      <ChatThread
        {...threadBase}
        onRetry={onRetry}
        messages={[msg("u1", "USER", "pergunta"), msg("a1", "ASSISTANT", "**resposta**")]}
      />
    )
    const bubble = container.querySelector('[data-slot="bubble"]')
    expect(bubble?.getAttribute("data-align")).toBe("end")
    expect(bubble?.getAttribute("data-variant")).toBe("muted")
    expect(container.querySelector('[data-slot="message"][data-align="end"]')).toBeTruthy()
    expect(container.querySelector('[data-slot="message-footer"]')).toBeTruthy()
    expect(container.querySelector('[data-slot="code-block"]')).toBeNull()
    expect(screen.getByText("resposta")).toBeTruthy()
  })

  it("mantém testids e papéis ARIA legados", () => {
    render(
      <ChatThread
        {...threadBase}
        messages={[]}
        loading
        generating
        retryable
        error="Falha"
        starterPrompts={["Oi"]}
      />
    )
    expect(screen.getByTestId("chat-thread")).toBeTruthy()
    expect(screen.getByTestId("thread-loading")).toBeTruthy()
    expect(screen.getByRole("progressbar")).toBeTruthy()
  })
})

describe("ModelSelector — catálogo por provedor com opção padrão", () => {
  it("lista padrão do tenant e grupos por provedor, fechando ao escolher", () => {
    const onChange = vi.fn()
    render(
      <ModelSelector
        catalog={catalog}
        value={null}
        baseline={{ provider: "OPENAI", model: "gpt-4o-mini" }}
        onChange={onChange}
      />
    )
    const trigger = screen.getByTestId("model-select")
    expect(trigger.getAttribute("aria-label")).toMatch(/modelo/i)
    fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false })
    expect(screen.getByTestId("model-select-default")).toBeTruthy()
    expect(screen.getAllByRole("menuitemradio").length).toBeGreaterThanOrEqual(4)
    expect(screen.getByTestId("model-option-OPENAI-0")).toBeTruthy()
    expect(screen.getByTestId("model-option-OPENCODE_ZEN-0")).toBeTruthy()
    expect(screen.getByTestId("model-option-GEMINI-0")).toBeTruthy()
    expect(screen.getByText("OpenAI")).toBeTruthy()
    expect(screen.getByText("OpenCode Zen")).toBeTruthy()
    expect(screen.getByText(/sem credencial/i)).toBeTruthy()

    fireEvent.click(screen.getByTestId("model-option-OPENCODE_ZEN-0"))
    expect(onChange).toHaveBeenCalledWith({ provider: "OPENCODE_ZEN", model: "claude-sonnet-4-5" })
  })

  it("mostra o modelo efetivo quando não há seleção explícita", () => {
    render(
      <ModelSelector
        catalog={[]}
        value={null}
        baseline={{ provider: "OPENAI" as AiProvider, model: "gpt-4o-mini" }}
        onChange={vi.fn()}
      />
    )
    expect(screen.getByTestId("model-select").textContent).toContain("gpt-4o-mini")
  })
})
