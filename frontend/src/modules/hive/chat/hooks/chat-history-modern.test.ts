import { describe, expect, it, vi } from "vitest"
import type { ChatSessionDTO } from "@/services/chat.types"
import { groupSessionsByDate } from "./chat-history"

const session = (id: string, updateAt: string): ChatSessionDTO => ({
  id,
  title: id,
  updateAt,
  createAt: updateAt,
  userId: "u",
  promptTemplateId: "p",
})

const nodeProcess = (globalThis as unknown as {
  process?: { env: Record<string, string | undefined> }
}).process

describe("chat-history — dia civil imune a DST", () => {
  it("08→09/11/2026 em America/Sao_Paulo: meia-noite não troca o grupo", () => {
    const originalTz = nodeProcess?.env.TZ
    if (nodeProcess) nodeProcess.env.TZ = "America/Sao_Paulo"
    try {
      // 09/11 00:15 local e 08/11 23:45 local: no mesmo fuso, grupos distintos.
      const reference = new Date(2026, 10, 9, 0, 15)
      const previous = new Date(2026, 10, 8, 23, 45)
      expect(previous.getTime()).toBeLessThan(reference.getTime())

      expect(groupSessionsByDate([session("ontem", previous.toISOString())], reference).map((g) => g.key)).toEqual([
        "yesterday",
      ])
      expect(groupSessionsByDate([session("hoje", reference.toISOString())], reference).map((g) => g.key)).toEqual([
        "today",
      ])
    } finally {
      if (nodeProcess) {
        if (originalTz === undefined) delete nodeProcess.env.TZ
        else nodeProcess.env.TZ = originalTz
      }
    }
  })
})

describe("chat-history — Object.groupBy com fallback", () => {
  const reference = new Date(2026, 10, 20, 15, 0, 0)
  const groupsFixture = [
    session("hoje", new Date(2026, 10, 20, 12).toISOString()),
    session("ontem", new Date(2026, 10, 19, 12).toISOString()),
  ]

  it("usa Object.groupBy quando disponível e tolera resultado null-prototype", () => {
    const objectWithGroupBy = Object as typeof Object & {
      groupBy?: (items: ChatSessionDTO[], keyOf: (item: ChatSessionDTO) => string) => Record<string, ChatSessionDTO[]>
    }
    const original = objectWithGroupBy.groupBy
    const spy = vi.fn((items: ChatSessionDTO[], keyOf: (item: ChatSessionDTO) => string) => {
      const buckets: Record<string, ChatSessionDTO[]> = Object.create(null)
      for (const item of items) (buckets[keyOf(item)] ??= []).push(item)
      return buckets
    })
    objectWithGroupBy.groupBy = spy
    try {
      const groups = groupSessionsByDate(groupsFixture, reference)
      expect(spy).toHaveBeenCalled()
      expect(groups.map((group) => group.key)).toEqual(["today", "yesterday"])
      expect(groups.map((group) => group.sessions.map((item) => item.id))).toEqual([["hoje"], ["ontem"]])
    } finally {
      if (original === undefined) delete objectWithGroupBy.groupBy
      else objectWithGroupBy.groupBy = original
    }
  })

  it("cai no reduce quando Object.groupBy não existe", () => {
    const objectWithGroupBy = Object as typeof Object & { groupBy?: unknown }
    const original = objectWithGroupBy.groupBy
    try {
      delete objectWithGroupBy.groupBy
      const groups = groupSessionsByDate(groupsFixture, reference)
      expect(groups.map((group) => group.key)).toEqual(["today", "yesterday"])
      expect(groups.map((group) => group.sessions.map((item) => item.id))).toEqual([["hoje"], ["ontem"]])
    } finally {
      if (original !== undefined) objectWithGroupBy.groupBy = original
    }
  })
})
