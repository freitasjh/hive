import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { documentTitleFor } from "./document-title"

const ORIGINAL_LANG = document.documentElement.lang

function setLang(lang: string) {
  document.documentElement.lang = lang
}

describe("documentTitleFor", () => {
  beforeEach(() => {
    localStorage.removeItem("hive_locale")
  })

  afterEach(() => {
    document.documentElement.lang = ORIGINAL_LANG
    localStorage.removeItem("hive_locale")
  })

  it("rotas tabbed caem no fallback defensivo (dono do próprio título)", () => {
    setLang("en")
    expect(documentTitleFor("providers")).toBe("providers")
    expect(documentTitleFor("prompts")).toBe("prompts")
  })

  describe("precedência de locale", () => {
    it("hive_locale en-US sobrepõe lang pt-BR", () => {
      localStorage.setItem("hive_locale", "en-US")
      setLang("pt-BR")
      expect(documentTitleFor("overview")).toBe("Overview")
      expect(documentTitleFor("login")).toBe("Sign in")
    })

    it("hive_locale pt-BR sobrepõe lang en", () => {
      localStorage.setItem("hive_locale", "pt-BR")
      setLang("en")
      expect(documentTitleFor("mfa")).toBe("Verificação em duas etapas")
    })
  })

  describe("pt-BR (default)", () => {
    it("overview reusa o rótulo pt existente (VIEW_LABELS.overview)", () => {
      setLang("pt-BR")
      expect(documentTitleFor("overview")).toBe("Visão geral")
    })

    it("login reusa auth.document.title", () => {
      setLang("pt-BR")
      expect(documentTitleFor("login")).toBe("Entrar")
    })

    it("mfa reusa auth.mfa.document.title (mais descriptivo que VIEW_LABELS.mfa)", () => {
      setLang("pt-BR")
      expect(documentTitleFor("mfa")).toBe("Verificação em duas etapas")
    })

    it("chat reusa chat.document.title", () => {
      setLang("pt-BR")
      expect(documentTitleFor("chat")).toBe("Assistant")
    })

    it("sem lang explícita cai em pt-BR", () => {
      setLang("")
      expect(documentTitleFor("overview")).toBe("Visão geral")
      expect(documentTitleFor("mfa")).toBe("Verificação em duas etapas")
    })
  })

  describe("en", () => {
    it.each([
      ["overview", "Overview"],
      ["login", "Sign in"],
      ["mfa", "Two-step verification"],
      ["chat", "Assistant"],
    ] as const)("%s usa o título localizado em inglês", (view, expected) => {
      setLang("en")
      expect(documentTitleFor(view)).toBe(expected)
    })

    it.each(["en", "en-US", "en-GB"])("detecta lang %s como inglês", (lang) => {
      setLang(lang)
      expect(documentTitleFor("overview")).toBe("Overview")
    })

    it("nenhum título em inglês contém literal pt-Only", () => {
      setLang("en-US")
      for (const view of ["overview", "login", "mfa", "chat"] as const) {
        expect(documentTitleFor(view)).not.toMatch(/[áéíóúâêôãõç]/i)
      }
    })
  })
})
