# Débito técnico — `PROMPT_MANAGE` barra a leitura de prompt de quem pode conversar

**Registrado em:** 2026-09-27, na verificação V2 do batch de UX do seletor.
**Status:** 🟡 P2 visível apenas quando o RBAC existir. **NÃO corrigido** — é decisão de produto.
**Classe:** produto / autorização. **Não é segurança** — ver "Por que não é `security-rbac`".

---

## O fato, medido

`PromptTemplateControllerV1.list()` tem `@HasPermission(SecurityConstants.PROMPT_MANAGE)`
(`PromptTemplateControllerV1.java:64`). Medido com token de permissão reduzida (probe temporário,
revertido):

```
token só com CHAT_SEND → GET /v1/prompt-templates?page=0&size=50  → 403
token completo         → GET /v1/prompt-templates?page=0&size=50  → 200
                            {"content":[],"page":{"size":50,"number":0,"totalElements":0,"totalPages":0}}
```

O gate funciona. O problema é **qual** permissão ele exige.

## Por que é um problema de produto

O produto é o **Assistant**: conversar. O seletor de prompt é a única forma de escolher o prompt do
turno, e `GET /v1/prompt-templates` é a única fonte dele. Então a cadeia é:

> Um usuário que tem `CHAT_SEND` mas não `PROMPT_MANAGE` **não consegue usar o produto**: abre o
> chat, abre o seletor, leva 403 dentro do dropdown, e não tem como conversar.

E o pior detalhe: a falha é um **403 dentro de um dropdown**, não uma página de erro. O usuário vê
uma lista vazia e um "não foi possível buscar" — parece que o tenant não tem prompts, não que ele
não tem permissão. O usuário conclui que o problema é dele ou que o produto está quebrado.

## Por que é invisível hoje

`SecurityTokenServiceImpl.generateToken` concede as 6 permissões a **qualquer** usuário que se
autentica. Ou seja: hoje não existe token com essa combinação, e o caminho nunca é exercitado.
Vai aparecer no dia em que o P1 do RBAC for corrigido
(`.spec/tech-debts/security-rbac/TASKS.md` P1-1) — **e vai aparecer junto com ele**, o que significa
que quem corrigir o RBAC vai encontrar este 403 como um bug novo, sem contexto.

## Por que não entra em `security-rbac`

Este item **não é** uma falha de segurança: o gate está fazendo o que foi pedido, e nenhuma permissão
está sendo concedida a quem não deveria. O problema é o **desenho da permissão**: uma permissão de
**escrita/admin** (`*_MANAGE`) guarding uma **leitura** que o produto inteiro depende. Corrigir é
decidir o que um papel de leitor pode ver — decisão de produto, não de segurança.

## Decisão pendente

> **Destravar a leitura de prompt para quem pode conversar?**

Três saídas, com o custo de cada uma:

| Opção | O que muda | Custo |
|--------|-----------|-------|
| **A** | `GET /v1/prompt-templates` (e só ela) passa a aceitar `CHAT_SEND` **ou** `PROMPT_MANAGE` | Menor diff. Mas é um `anyOf` — o padrão `anyOf={"CHAT_SEND","AGENT_MANAGE"}` já existe em outro controller e está no `HasPermissionAspect`. Precisa decidir se leitura de prompt é visível para quem nem manda mensagem. |
| **B** | Nova permissão `PROMPT_READ`, concedida junto de `CHAT_SEND` | Mais explícito e auditável. Custa uma permissão a mais no token e mais um item na matriz de papéis que o P1 do RBAC vai precisar definir de qualquer forma. |
| **C** | Endpoint de leitura dedicado (`GET /v1/prompts/selectable`) com escopo próprio, separado do CRUD | Mais limpo de longo prazo: o seletor precisa de nome/código/status, não do `template` completo nem de poder de escrita. Diff maior, e cria um segundo contrato de leitura. |

A minha leitura é que **B** é a que sobrevive melhor, porque a matriz de papéis vai ser feita no
batch do P1 do RBAC e essa permissão entra nela naturalmente. Mas **é decisão de produto** e não foi
tomada aqui.

## Se a resposta for "não, leitura de prompt é privilégio de admin"

Então o produto tem um problema de modelagem que vale registrar: o Assistant exige, para funcionar,
uma permissão de escrita. Nesse caso o caminho é o inverso — **todos os papéis que podem conversar
têm `PROMPT_MANAGE`**, e a distinção real passa a ser entre quem *conversa* e quem *administra
provedores*. Registrar a decisão aqui.

## Barreira de teste

Enquanto a decisão não sai, um IT pinaria o comportamento atual para que a correção não venha por
acidente: um token só com `CHAT_SEND` leva 403 em `GET /v1/prompt-templates`. Hoje **não existe**
esse teste. Ele não foi adicionado porque fixaria um comportamento que provavelmente está errado —
fixar o queque provavelmente precisa mudar é dívida, não barreira.
