import { readFileSync } from "node:fs"
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"
import type { PromptTemplateDTO } from "@/services/admin.types"
import type { ChatSessionDTO } from "@/services/chat.types"
import { PromptInput } from "./PromptInput"
import { PromptSelector } from "./PromptSelector"
import { SessionSidebar } from "./SessionSidebar"

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
// jsdom não implementa Pointer Capture; o Radix Select usa ao abrir o popup.
if (!HTMLElement.prototype.hasPointerCapture) {
  HTMLElement.prototype.hasPointerCapture = () => false
  HTMLElement.prototype.setPointerCapture = () => {}
  HTMLElement.prototype.releasePointerCapture = () => {}
}

const TOKENS_CSS = readFileSync("src/tokens-shadcn.css", "utf8")

const prompt: PromptTemplateDTO = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Saudação",
  code: "greeting",
  template: "Olá {{nome}}",
  status: "ACTIVE",
  version: 1,
  createAt: "",
  updateAt: "",
}

const session = (id: string, title: string): ChatSessionDTO => ({
  id,
  title,
  userId: "u",
  promptTemplateId: "p",
  createAt: "2026-11-20T12:00:00Z",
  updateAt: "2026-11-20T12:00:00Z",
})

const sidebarBase = {
  selectedId: null as string | null,
  page: 0,
  totalPages: 1,
  onSelect: vi.fn(),
  onNew: vi.fn(),
  onDelete: vi.fn(),
  onPageChange: vi.fn(),
}

describe("color-scheme — popup escuro em dark", () => {
  it(":root declara color-scheme light e .dark declara color-scheme dark", () => {
    expect(TOKENS_CSS).toMatch(/:root\s*\{[^}]*color-scheme:\s*light/s)
    expect(TOKENS_CSS).toMatch(/\.dark\s*\{[^}]*color-scheme:\s*dark/s)
  })

  it("html.dark propaga color-scheme dark via cascata CSS", () => {
    const style = document.createElement("style")
    style.textContent = ":root{color-scheme:light}.dark{color-scheme:dark}"
    document.head.append(style)
    document.documentElement.classList.add("dark")
    try {
      expect(getComputedStyle(document.documentElement).colorScheme).toBe("dark")
    } finally {
      document.documentElement.classList.remove("dark")
      style.remove()
    }
  })
})

describe("PromptSelector — popup shadcn em vez de select nativo", () => {
  it("trigger é combobox (button) e o popup é o popover do shadcn", async () => {
    render(<PromptSelector prompts={[prompt]} value={null} onChange={vi.fn()} />)
    const trigger = screen.getByTestId("prompt-select")
    expect(trigger.tagName).toBe("BUTTON")
    expect(trigger.getAttribute("role")).toBe("combobox")

    fireEvent.keyDown(trigger, { key: "Enter" })
    const listbox = await screen.findByRole("listbox")
    expect(listbox.getAttribute("data-slot")).toBe("select-content")
    expect(within(listbox).getByRole("option", { name: "Saudação" })).toBeTruthy()
  })

  it("preserva aria-label, aria-busy e disabled do contrato antigo", () => {
    const { rerender } = render(
      <PromptSelector prompts={[prompt]} value={null} loading onChange={vi.fn()} />
    )
    const trigger = screen.getByTestId("prompt-select") as HTMLButtonElement
    expect(trigger.getAttribute("aria-label")).toBe("Prompt")
    expect(trigger.getAttribute("aria-busy")).toBe("true")
    expect(trigger.disabled).toBe(true)

    rerender(<PromptSelector prompts={[prompt]} value={prompt.id} locked onChange={vi.fn()} />)
    expect((screen.getByTestId("prompt-select") as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/travado nesta conversa/i)).toBeTruthy()
  })
})

describe("PromptInput — padrão oficial shadcn, sem anexos", () => {
  const base = {
    value: "oi",
    onChange: vi.fn(),
    onSubmit: vi.fn(),
    label: "Mensagem para o assistente",
    placeholder: "Escreva sua mensagem…",
    sendLabel: "Enviar",
    sendingLabel: "Enviando…",
  }

  it("mantém testids do composer e usa submit rounded-full com seta", () => {
    render(<PromptInput {...base} />)
    expect(screen.getByTestId("chat-form")).toBeTruthy()
    expect(screen.getByTestId("chat-input")).toBeTruthy()
    const send = screen.getByTestId("chat-send")
    expect(send.className).toContain("rounded-full")
    expect(send.querySelector("svg")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /anex|attach/i })).toBeNull()
    fireEvent.submit(screen.getByTestId("chat-form"))
    expect(base.onSubmit).toHaveBeenCalledTimes(1)
  })

  it("Enter envia, Shift+Enter quebra linha", () => {
    const onSubmit = vi.fn()
    render(<PromptInput {...base} onSubmit={onSubmit} />)
    const input = screen.getByTestId("chat-input")
    fireEvent.keyDown(input, { key: "Enter", shiftKey: false })
    expect(onSubmit).toHaveBeenCalledTimes(1)
    fireEvent.keyDown(input, { key: "Enter", shiftKey: true })
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it("Enter durante composição IME não envia (Enter normal ainda envia)", () => {
    const onSubmit = vi.fn()
    render(<PromptInput {...base} onSubmit={onSubmit} />)
    const input = screen.getByTestId("chat-input")

    // jsdom ignora `isComposing` no init do KeyboardEvent; definir na instância.
    const composingEnter = new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true })
    Object.defineProperty(composingEnter, "isComposing", { value: true })
    fireEvent(input, composingEnter)
    expect(onSubmit).not.toHaveBeenCalled()

    fireEvent.keyDown(input, { key: "Enter" })
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  it("positiona o seletor de modelo no addon block-end entre o texto e o submit", () => {
    const { container } = render(
      <PromptInput {...base} modelSlot={<button type="button" data-testid="model-select">Modelo</button>} />
    )
    const addon = container.querySelector('[data-align="block-end"]') as HTMLElement
    expect(addon).toBeTruthy()
    expect(addon.querySelector('[data-testid="model-select"]')).toBeTruthy()
    expect(addon.querySelector('[data-testid="chat-send"]')).toBeTruthy()
    expect(addon.querySelector('[data-testid="model-select"]')?.parentElement?.className).toContain("ml-auto")
  })
})

describe("SessionSidebar — histórico performático e acessível", () => {
  it("aplica content-visibility só nas linhas além do primeiro fold", () => {
    const sessions = Array.from({ length: 8 }, (_, index) => session(`s-${index}`, `Conversa ${index}`))
    const { container } = render(<SessionSidebar {...sidebarBase} sessions={sessions} />)
    const rows = container.querySelectorAll("li")
    expect(rows).toHaveLength(8)
    rows.forEach((row, index) => {
      if (index < 5) {
        expect(row.className).not.toContain("content-visibility")
      } else {
        expect(row.className).toContain("content-visibility:auto")
        expect(row.className).toContain("contain-intrinsic-size:auto_none_auto_40px")
      }
    })
  })

  it("linhas deferidas seguem alcançáveis por navegação de teclado", () => {
    const sessions = Array.from({ length: 8 }, (_, index) => session(`s-${index}`, `Conversa ${index}`))
    render(<SessionSidebar {...sidebarBase} sessions={sessions} />)
    const last = screen.getByRole("button", { name: "Conversa 7" })
    last.focus()
    expect(document.activeElement).toBe(last)
  })

  it("diálogos focam o campo de rename e a ação segura de delete", async () => {
    render(<SessionSidebar {...sidebarBase} sessions={[session("s-1", "Conversa 1")]} />)

    fireEvent.click(screen.getByRole("button", { name: /renomear conversa/i }))
    const renameDialog = await screen.findByRole("dialog")
    const field = within(renameDialog).getByLabelText(/título da conversa/i)
    await waitFor(() => expect(document.activeElement).toBe(field))

    fireEvent.click(within(renameDialog).getByRole("button", { name: /^cancelar$/i }))
    await waitFor(() => expect(screen.queryByTestId("session-rename-dialog")).toBeNull())

    fireEvent.click(screen.getByRole("button", { name: /remover conversa/i }))
    const deleteDialog = await screen.findByRole("dialog")
    const cancel = within(deleteDialog).getByRole("button", { name: /^cancelar$/i })
    await waitFor(() => expect(document.activeElement).toBe(cancel))
  })
})
