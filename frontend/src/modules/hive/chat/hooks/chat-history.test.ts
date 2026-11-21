import { describe, expect, it } from "vitest"
import type { ChatMessageDTO, ChatSessionDTO } from "@/services/chat.types"
import {
  CHAT_HISTORY_GROUP_ORDER,
  filterMessagesByQuery,
  filterSessionsByQuery,
  formatSessionDate,
  groupSessionsByDate,
} from "./chat-history"

const session = (id: string, title: string, updateAt: string): ChatSessionDTO => ({
  id,
  title,
  updateAt,
  createAt: updateAt,
  userId: "u",
  promptTemplateId: "p",
})

const message = (id: string, content: string): ChatMessageDTO => ({
  id,
  content,
  sessionId: "s",
  role: "USER",
  createAt: "2026-11-20T12:00:00Z",
})

/** Datas locais fixas: ISO gerado a partir de meia-dia local evita dispositivo/TZ. */
const daysAgo = (reference: Date, days: number) =>
  new Date(reference.getFullYear(), reference.getMonth(), reference.getDate() - days, 12).toISOString()

describe("chat-history — busca local", () => {
  const sessions = [
    session("1", "Relatório mensal", daysAgo(new Date(), 0)),
    session("2", "Checklist de viagem", daysAgo(new Date(), 1)),
    session("3", "Tradução", daysAgo(new Date(), 8)),
  ]

  it("query vazia devolve todas as sessões na ordem recebida", () => {
    expect(filterSessionsByQuery(sessions, "   ")).toEqual(sessions)
  })

  it("filtra sessões por título, sem diferenciar caixa", () => {
    expect(filterSessionsByQuery(sessions, "CHECK").map((item) => item.id)).toEqual(["2"])
    expect(filterSessionsByQuery(sessions, "relatório").map((item) => item.id)).toEqual(["1"])
    expect(filterSessionsByQuery(sessions, "inexistente")).toEqual([])
  })

  it("filtra mensagens carregadas por conteúdo", () => {
    const messages = [message("m1", "Resumo do texto"), message("m2", "Crie um checklist"), message("m3", "Traduza isto")]
    expect(filterMessagesByQuery(messages, "RESUMO").map((item) => item.id)).toEqual(["m1"])
    expect(filterMessagesByQuery(messages, "check").map((item) => item.id)).toEqual(["m2"])
    expect(filterMessagesByQuery(messages, "")).toEqual(messages)
  })
})

describe("chat-history — agrupamento por updateAt", () => {
  const reference = new Date(2026, 10, 20, 15, 0, 0) // 20/11/2026 local

  it("separa Hoje / Ontem / Últimos 7 dias / Anteriores", () => {
    const sessions = [
      session("hoje", "Hoje", daysAgo(reference, 0)),
      session("ontem", "Ontem", daysAgo(reference, 1)),
      session("semana", "Semana", daysAgo(reference, 4)),
      session("antigo", "Antigo", daysAgo(reference, 9)),
    ]
    const groups = groupSessionsByDate(sessions, reference)

    expect(groups.map((group) => group.key)).toEqual(CHAT_HISTORY_GROUP_ORDER)
    expect(groups.map((group) => group.sessions.map((item) => item.id))).toEqual([
      ["hoje"],
      ["ontem"],
      ["semana"],
      ["antigo"],
    ])
  })

  it("omite grupos vazios e preserva a ordem relativa do backend (updateAt DESC)", () => {
    const sessions = [
      session("a", "A", daysAgo(reference, 5)),
      session("b", "B", daysAgo(reference, 2)),
    ]
    const groups = groupSessionsByDate(sessions, reference)

    expect(groups.map((group) => group.key)).toEqual(["last7"])
    expect(groups[0].sessions.map((item) => item.id)).toEqual(["a", "b"])
  })

  it("data inválida cai em Anteriores em vez de quebrar a lista", () => {
    const groups = groupSessionsByDate([session("ruim", "Ruim", "não-é-data")], reference)
    expect(groups.map((group) => group.key)).toEqual(["older"])
  })
})

describe("chat-history — formatação de data com Intl", () => {
  it("usa o locale do documento (pt-BR / en-US), sem lib de data", () => {
    const iso = new Date(2026, 10, 20, 12).toISOString()
    const pt = formatSessionDate(iso, "pt-BR")
    const en = formatSessionDate(iso, "en-US")

    expect(pt).toBeTruthy()
    expect(en).toBeTruthy()
    expect(pt).toContain("20")
    expect(en).toContain("20")
    expect(pt).not.toBe(en)
  })

  it("data inválida não produz 'Invalid Date' visível", () => {
    expect(formatSessionDate("não-é-data", "pt-BR")).toBe("")
  })
})
