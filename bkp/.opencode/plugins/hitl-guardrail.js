// HITL Guardrail — Hive AI Orchestrator
// ============================================================
// Guardrail de Human-in-the-Loop para o workflow SDD do Hive AI.
//
// O que faz:
// 1. Injeta regra permanente no system prompt reforçando que o
//    orquestrador DEVE parar e aguardar aprovação humana entre
//    batches/fases (nunca encadear delegações).
// 2. Loga conclusão de delegações via task tool para auditoria.
//
// Como funciona:
// - `experimental.chat.system.transform`: injeta o bloco HITL no
//   system prompt antes de cada chamada ao modelo.
// - `event`: observa tool.execute.after para tool "task" e loga.
//
// Carregado automaticamente de .opencode/plugins/ (ver opencode.json).
// Aplicar: reiniciar o opencode.

export const HitlGuardrail = async ({ client }) => {
  const HITL_RULE = `
## 🚨 REGRA DE GUARDRAIL — HUMAN-IN-THE-LOOP OBRIGATÓRIO (NON-NEGOTIABLE)
Guardrail automático do opencode. Regra de governança do Hive AI:

1. **PARADA OBRIGATÓRIA ENTRE BATCHES**: após concluir QUALQUER batch de
   delegação (developer-engineer → fullstack-code-reviewer), o orquestrador
   DEVE parar e apresentar ao usuário o resumo (tasks concluídas, code
   review, testes) + perguntar explicitamente: "Posso avançar para o próximo
   batch/fase?". NUNCA iniciar o próximo batch na mesma resposta sem resposta
   humana explícita.

2. **PORTÕES DE FASE**: entre fases SDD (P0→P1→P2→P3→P4→P5→P6), apresentar
   status + resultados + decisão requerida. Aguardar resposta. Exceção única:
   loop de correção automática (re-delegar developer → reviewer) até 3
   iterações; após 3, parar e reportar.

3. **PROIBIDO encadear múltiplas delegações em sequência** na mesma rodada.
   Cada delegação via task tool deve ser seguida de processamento do
   resultado + PARADA para portão humano.

4. **Override** só com sintaxe explícita do usuário ("avance mesmo assim",
   "override portão [nome]"). Registrar no workflow-state.json e no Brain.

5. Se a resposta terminar sem portão humano e houver batch pendente, o
   orquestrador VIOLOU a regra. Corrija: pare, apresente o último batch e
   peça aprovação antes de continuar.
`

  return {
    "experimental.chat.system.transform": async (input, output) => {
      const existing = output.system ?? ""
      if (!existing.includes("HUMAN-IN-THE-LOOP OBRIGATÓRIO")) {
        output.system = existing + HITL_RULE
      }
    },

    event: async ({ event }) => {
      if (event.type === "tool.execute.after" && event.tool === "task") {
        try {
          await client.app.log({
            body: {
              service: "hitl-guardrail",
              level: "info",
              message: "Delegação via task concluída — verificar portão HITL antes do próximo batch",
              extra: { tool: event.tool, sessionID: event.sessionID },
            },
          })
        } catch {
          // log é best-effort; não quebra o fluxo
        }
      }
    },
  }
}

export default HitlGuardrail
