# PENDING — brain_store (2026-09-27)

> O MCP do Brain respondeu **HTTP 410** em três tentativas durante o Batch 2
> (`brain_store` → `-32001 Request timed out`; `brain_status` → `410`). As duas chamadas falharam.
> Conteúdo pronto para ser gravado quando o servidor voltar.

## Alvo 1

```
layer = arquitetura
path  = hive/decisoes/agent-morre-prompt-por-conversa
scope = projetos
tags  = [hive, refactor, assistant, flyway, decisao]
```

## Alvo 2 (opcional, mesma sessão)

```
layer = sessoes
path  = hive/2026-09-27
```

resumo: Batch 2 da decisão `scope-assistant-sem-rag`. Conceito Agent removido por completo
(entidade, serviço, controller, mappers, DTOs, tabela `agent`, `chat_session.agent_id`).
`tenant_llm_config` criada no módulo `tenant` (1:1, FK segura por shares de histórico Flyway).
`chat_session.prompt_template_id NOT NULL FK RESTRICT`; coluna antiga **renomeada** para
`agent_id_legacy`, dado preservado. Gate: 376 unit · 70 IT · 145 FE, 0 falhas.

---

## Conteúdo de `hive/decisoes/agent-morre-prompt-por-conversa`

```markdown
---
tags: [hive, refactor, assistant, agent, flyway, decisao]
---

# Batch 2 (2026-09-27): o conceito Agent morreu; o prompt virou parâmetro da conversa

Segundo e último batch da decisão `scope-assistant-sem-rag`. O Batch 1 removeu a base de conhecimento;
o Batch 2 removeu o agente. O produto agora é um Assistant: chat simples, sem agente, sem tools,
sem knowledge, não-agentico.

## As responsabilidades que o agente misturava

O agente existia porque reunia num só registro coisas com **lifetimes diferentes**:

| O quê | De quem é | Por quê |
|-------|----------|---------|
| prompt | da **conversa** | o usuário escolhe ao abrir a conversa; a sessão guarda |
| provider + modelo | do **tenant** | um só, é o que o tenant comprou |
| temperature/topP/maxTokens | do **prompt** | acompanham o texto, não o modelo (`inference_params` JSONB) |

Separar as duas não sobrou nada para a entidade fazer — e o aggregate root passou a ser a própria
`chat_session`. **Não criar entidade `Assistant`**: seria a mesma coisa com outro nome.

## Decisão 1 — `tenant_llm_config` mora no módulo `tenant`, não no `agent`

A FK `tenant_id → tenant(id)` só é segura quando as duas tabelas estão no **mesmo histórico Flyway**.
O módulo `agent` deliberadamente NÃO usa FK cross-module (documentado na sua V9): historiales
independentes e sem ordem garantida. Essa é a razão — não preferência de modelagem — que a
configuração de LLM foi para `tenant`, e que ela ganhou um módulo `web` novo
(`tenant/web/internal/v1`).

## Decisão 2 — `chat_session.agent_id`: renomear, não apagar

`DROP COLUMN agent_id` + `ADD prompt_template_id` destruiria em silêncio a associação de toda sessão
existente em qualquer ambiente com histórico. Ordem implementada em
`agent/V15__chat_session_prompt_template.sql` (uma transação):

1. `ADD COLUMN prompt_template_id` (nullable) — nada destruído ainda
2. **backfill** a partir de `agent.prompt_template_id` — associação preservada **por dado**
3. **guard**: sessão órfã (agente sem template) → `RAISE EXCEPTION` com a contagem e o SQL de
   remediação. O Flyway roda cada migration PostgreSQL numa transação, então o rollback é inteiro:
   **abortar é reversível, seguir com NULL não é**
4. FK cai e a coluna é **RENOMEADA** para `agent_id_legacy` — o dado fica; a reversão é um
   `RENAME COLUMN ... TO agent_id`
5. `prompt_template_id` vira `NOT NULL` + FK `ON DELETE RESTRICT` para `prompt_template`
6. `agent/V16__drop_agent.sql` apaga a tabela `agent` e **imprime** quantas sessões ficaram com
   `agent_id_legacy` para auditoria

`agent_id_legacy` saiu do caminho do código (nenhuma query, nenhuma FK) mas não do banco. A remoção
física dela é batch próprio.

**Armadilhas vividas, todas achadas rodando e não lendo:**

- O `RENAME` carrega o `NOT NULL` que a V5 pôs em `agent_id`. Sem um
  `ALTER COLUMN agent_id_legacy DROP NOT NULL` na mesma migration, **toda inserção de sessão nova
  responde 409** — e o 409 genérico não diz nada sobre a causa.
- `DO $$ ... $$` aninhado: o parser de script do Flyway corta no primeiro `$$` e o bloco interno
  vira erro de sintaxe. Use tag distinta (`$fk$`).
- Um `$$` **dentro de um comentário** corta o bloco do mesmo jeito. Nunca escreva delimitador de
  dollar-quote em comentário de migration.

## Decisão 3 — código de erro novo

`TENANT_LLM_CONFIG_NOT_FOUND` (422), derivado do `SimpleName` da exceção pelo
`ControllerExceptionHandler` como todos os outros. 422 e não 404/500 porque o request é válido, o
problema é do tenant e só o tenant resolve. **Nenhum prefixo do código no corpo da mensagem**: o
campo `code` já o carrega, e repetir é ter duas verdades para o mesmo erro.

Sem fallback global e sem criação automática — um default implícito esconderia a configuração
ausente atrás de um comportamento que "funciona" e não é diagnosticável.

## Dívida que este batch deixou, de propósito

`ChatTrace.agentId` agora carrega um `promptTemplateId` e o log diz `agentId=<uuid de prompt>`.
Isso é mentira para quem lê o log, e é deliberado: o critério de aceitação pedia `ChatTrace` e
`LoggingChatTraceRecorder` byte a byte, e mexer neles só para renomear um campo é o tipo de
"melhoria" que se perde dentro de um batch destrutivo. Renomear é batch próprio. Os 4 campos
`rag*` continuam mortos pelo mesmo motivo.

## Lição transversal — o harness com browser pegou o que 145 testes FE não pegaram

`PromptSelector` pedia `size=100`; `PromptTemplateControllerV1` responde **400 para `size > 50`**.
O componente era um erro de 400 em **toda montagem**. Nenhum teste unitário pegaria: o mock aceita
qualquer número, e o teste de frontend só verificava que `list()` foi chamado. Só um browser real
contra o backend real mostra o `GET /prompt-templates?page=0&size=100 [400]`.

Regra: **todo parâmetro de paginação novo é conferido contra o teto do servidor antes**, e o número
aparece numa constante nomeada (`MAX_PROMPTS_IN_SELECTOR`) com o motivo — não num literal solto.
```

## Lição de projeto (global, mesma nota)

Escopo: `hive/frontend/harness-browser-pega-400-de-paginacao`
`scope = global` — vale para qualquer projeto Vue/Spring com seletor paginado.

---

## Alvo 3 (adicionado na revisão, 2026-09-27)

```
layer = regras
path  = plpgsql-flyway-armadilhas-e-testes-que-se-copiam
scope = global
tags  = [plpgsql, flyway, teste, revisao]
```

> `brain_store` tentou de novo e respondeu `-32001 Request timed out` / `410`. Terceira sessão
> seguida com o MCP fora.

## Conteúdo de `plpgsql-flyway-armadilhas-e-testes-que-se-copiam`

```markdown
---
tags: [hive, flyway, migration, guard, revisao, licao]
---

# Três erros de PL/pgSQL + a armadilha do teste que se copia (2026-09-27)

Todos achados **rodando** a migration num PostgreSQL 16 de verdade, não lendo. Registrados porque
são todos silenciosos: o build passa, o teste passa, e a migration está errada.

## 1. `RAISE EXCEPTION` não aceita concatenação no literal de formato

A gramática de RAISE é `level 'formato' [, args]`. Depois do literal, um `||` é **erro de
sintaxe** — não concatenação. `RAISE EXCEPTION 'a' || 'b'` falha.

    -- ERRADO: syntax error at or near "||"
    RAISE EXCEPTION 'parte 1'||E'\n' || 'parte 2', v_total;

    -- CERTO: monte a mensagem numa variável
    v_msg := 'parte 1'||E'\n' || 'parte 2';
    RAISE EXCEPTION '%', v_msg;

## 2. `format()` embutido numa concatenação: o primeiro separador é vírgula, não `||`

    -- ERRADO: o `||` onde a vírgula dos argumentos de format() era esperada
    format('... VALUES (... %L ...);', || '<TENANT_ID>', 'default')

    -- CERTO
    format('... VALUES (... %L ...);', '<TENANT_ID>', 'default')

E use `format`/`%L` em vez de aspas escapadas à mão: escrever `''` dentro de uma string
PL/pgSQL para produzir uma aspa é contagem de aspas, e errar produz uma mensagem que o operador
não consegue colar. Com `%L` o literal sai corretamente citado.

## 3. Delimitador de dollar-quote em COMENTÁRIO

O parser de script do Flyway corta o arquivo no primeiro `$$` **mesmo dentro de um comentário**, e
o bloco é encerrado ali. Vale para `$$` e para qualquer tag: nunca escreva delimitador de
dollar-quote num comentário, nem no texto de uma mensagem de erro.

## 4. `string_agg` com LATERAL + `LIMIT 1` colapsa a contagem

    -- ERRADO: reduz N linhas a 1, então count(*) sai 1
    FROM chat_session cs
    LEFT JOIN LATERAL (SELECT ... LIMIT 1) x ON true

    -- CERTO: duas listas disjuntas por FILTER sobre o MESMO conjunto
    count(*),
    string_agg(DISTINCT cs.tenant_id::text, ', ') FILTER (WHERE EXISTS (SELECT 1 FROM prompt_template pt WHERE pt.tenant_id = cs.tenant_id)),
    string_agg(DISTINCT cs.tenant_id::text, ', ') FILTER (WHERE NOT EXISTS (SELECT 1 FROM prompt_template pt WHERE pt.tenant_id = cs.tenant_id))

## 5. A armadilha maior: um teste que copia o que deve testar

Duas vezes neste batch, o teste provou que ele mesmo escrevia a coisa certa, e não que a coisa
existia:

**(a) O `PromptSelector` preso à cópia de si mesmo.** O `size` do seletor é o mesmo contrato em
três lugares: o controller, o componente, o teste do componente. O teste afirmava `size: 50` e o
mock aceitava qualquer número — então baixar o teto do servidor para 20 deixaria o teste VERDE e o
browser levando 400 em toda montagem. Corrigido com um IT que afirma `size=51` → 400 e `size=50` →
200: é o único teste que lê o número do SERVIDOR.

**(b) A guarda de rota testada por uma cópia.** A primeira versão do spec do `beforeEach` colava as
três linhas da guarda dentro do teste — o equivalente exato de reescrever o SQL da mensagem de
remédio em vez de extraí-lo. Corrigido importando o router real e navegando nele (com reset para
`/login` antes de cada `push`, porque o router é singleton e `push` para o path atual é no-op).

**Regra:** um teste que reconstrói o objeto sob teste prova que o teste sabe escrever aquele objeto.
Extraia o texto do artefato real e **execute-o**. Vale para migration, chave de log, guard de rota
e qualquer remediable que um humano vá copiar e colar.

## 6. Chave de log é interface

Mudar o SENTIDO do valor sem mudar a CHAVE quebra a interface em silêncio. `ChatTrace.agentId`
passou a carregar um `promptTemplateId` e o log dizia `agentId=` — e, como o trace era a única
identidade de prompt na linha, era a única ocorrência com o nome errado enquanto o
`ChatServiceImpl` logava `promptTemplateId=` corretamente. Dois nomes para o mesmo valor na mesma
operação: quem correlacionar uma pela outra leva mismatch confiante.

"Preservar byte a byte" é um meio, não um fim, e a intenção por trás precisa sobreviver ao meio.
Não preservar nome de campo cuja semântica mudou não é "melhoria" — é a entrega incorreta.
```

## Alvo 4 (opcional, mesma revisão)

```
layer = arquitetura
path  = hive/decisoes/permission-guard-no-frontend-e-a-p1-do-rbac
scope = projetos
```

resumo: a rota `/settings/llm` declarava `meta.permission` que ninguém lia — sinal falso. O
`beforeEach` agora consulta o claim `permissions` do JWT. É **UX gate, não fronteira de
segurança**: a execução é o `@HasPermission` do servidor, e apagar o módulo inteiro do frontend
continua devolvendo 403. A consequência prática é que **não existe token real sem `AGENT_MANAGE`**
— a concessão usa lista hardcoded em `SecurityTokenServiceImpl:175`, então o caminho de negação do
guard não é alcançável com sessão legítima neste build. Débito P1 registrado em
`.spec/tech-debts/security-rbac/TASKS.md`, não corrigido.
