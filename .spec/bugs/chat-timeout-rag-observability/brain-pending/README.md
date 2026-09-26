# Brain pendente — MCP indisponível (HTTP 410) em 2026-09-26

O `brain_store` falhou com **HTTP 410 Gone** durante a execução. As duas notas foram
preservadas aqui, com o cabeçalho `brain_store(...)` já no formato correto — só reprocessar:

| Arquivo | Destino |
|---|---|
| `1-regras-global-hive-observabilidade-caminho-de-retorno-vazio.md` | `brain_store(layer="regras", path="hive/observabilidade/caminho-de-retorno-vazio", scope="global")` |
| `2-regras-projetos-hive-llm-timeout-e-retry.md` | `brain_store(layer="regras", path="hive/llm/timeout-e-retry", scope="projetos")` |

A segunda contém a tabela de campo-por-provider para configurar retry/timeout no
LangChain4j 1.19.0 (inclusive o `maximumRetries` do Gemini, que difere dos demais
`maxRetries`) — informação cara de redescobrir.

**Ação:** quando o Brain voltar, rodar o `brain_store` dos dois e apagar esta pasta.
