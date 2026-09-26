// Harness Validator — Hive AI Orchestrator
// ============================================================
// Força validação end-to-end via Chrome DevTools MCP antes de
// declarar qualquer feature de frontend como concluída.
//
// Regra: o desenvolvedor (subagente) e o orquestrador NÃO podem
// confiar em "testes unitários passaram" ou "vue-tsc OK" como
// prova de funcionamento. Confiabilidade exige:
//   1. Verificar se o sistema está rodando (porta 8080 backend, 5173 frontend)
//   2. Se não estiver, iniciar (mvn spring-boot:run -pl bootstrap + npm run dev)
//   3. Se backend mudou, parar e reiniciar
//   4. Chrome DevTools: navigate → snapshot → console → network
//   5. Validar visual + funcional com evidência
//
// Carregado automaticamente de .opencode/plugins/ (ver opencode.json).

export const HarnessValidator = async ({ client }) => {
  const HARNESS_RULE = `
## 🔧 REGRA DE HARNESS — VALIDAÇÃO END-TO-END OBRIGATÓRIA (NON-NEGOTIABLE)
Este plugin injeta a regra de harness para o Hive AI. Aplica-se a
**qualquer agente que implementa ou valida frontend** (developer-engineer,
orchestrator, qa-engineer, etc.).

**Testes unitários passando NÃO são prova de funcionamento.** São apenas
uma camada. Confiabilidade exige validação na TELA rodando.

### Antes de declarar QUALQUER feature frontend como concluída:

1. **Verificar se o sistema está rodando** (context path do backend é \`/hive\`):
   - Backend: \`lsof -i :8080\` ou \`curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/hive/v1/auth/login\` (esperado 400/405, nunca connection refused)
   - Frontend: \`curl -s http://localhost:5173\` (ou lsof -i :5173)
   - Se não estiver rodando: \`cd backend && mvn spring-boot:run -pl bootstrap\` + \`cd frontend && npm run dev\`

2. **Se backend foi alterado**: PARAR e REINICIAR. Backend não tem hot-reload confiável para mudanças em entities/migrations/beans.
   \`pkill -f "spring-boot:run" && cd backend && mvn spring-boot:run -pl bootstrap\`

3. **Se frontend foi alterado**: Vite tem hot-reload, MAS se mudar config (vite.config, tsconfig, package.json) ou deps, REINICIAR.
   \`pkill -f "vite" && cd frontend && npm run dev\`

4. **Chrome DevTools MCP — validação visual obrigatória**:
   \`mcp__chrome-devtools__new_page\` → URL da feature (ex: http://localhost:5173/prompts)
   \`mcp__chrome-devtools__take_snapshot\` → ler o a11y tree (vê labels, textos, hierarquia)
   \`mcp__chrome-devtools__list_console_messages\` → ZERO \`[ERROR]\` ou \`[WARN]\`
   \`mcp__chrome-devtools__list_network_requests\` → 2xx esperado, sem 4xx/5xx
   \`mcp__chrome-devtools__take_screenshot\` → evidência visual

5. **Validar i18n**: se a feature tem labels de UI, eles DEVEM estar no idioma do browser (pt-BR). Se ver labels em inglês em browser pt-BR, **NÃO está concluído**.

6. **Encerramento limpo (R2)**: ao terminar o harness, finalizar o front e o backend iniciados para o teste (não deixar processos órfãos em :8080/:5173).

7. **Reportar evidência no retorno ao orquestrador**:
   \`## Harness validation
   - Backend: port 8080 [UP/DOWN]
   - Frontend: port 5173 [UP/DOWN]
   - Console: [N errors, M warnings]
   - Network: [N 2xx, M 4xx/5xx]
   - Visual: [screenshot path / descrição]
   - i18n: [labels conferidos — PT-BR ✅/EN ❌]
   - Processos finalizados: [✅/❌]\`

### Quando o subagente retorna "concluído", o orquestrador DEVE:
- Validar que o harness foi executado (evidência acima)
- Se não foi, RE-DELGAR com instrução explícita: "antes de retornar, execute harness end-to-end via chrome-devtools"
- Nunca confiar apenas em "testes passam"
`

  return {
    "experimental.chat.system.transform": async (input, output) => {
      const existing = output.system ?? ""
      if (!existing.includes("HARNESS — VALIDAÇÃO END-TO-END")) {
        output.system = existing + HARNESS_RULE
      }
    },

    event: async ({ event }) => {
      // Loga conclusões de subagentes para que o orchestrator possa auditar
      if (event.type === "tool.execute.after" && event.tool === "task") {
        try {
          await client.app.log({
            body: {
              service: "harness-validator",
              level: "warn",
              message: "Subagente concluído — verificar evidência de HARNESS antes de aceitar",
              extra: { tool: event.tool, sessionID: event.sessionID },
            },
          })
        } catch {}
      }
    },
  }
}

export default HarnessValidator
