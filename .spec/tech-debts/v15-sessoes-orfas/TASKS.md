# Decisão pendente — a V15 aborta e o ambiente de dev não sobe (58 sessões órfãs)

**Registrado em:** 2026-09-27, na verificação H3 do batch de UX do seletor.
**Status:** 🔴 **bloqueia o ambiente de dev**. Aguarda decisão do usuário.
**Classe:** **decisão de produto/dado. NÃO é técnica e NÃO é código.**

> 🚫 **Nada foi tocado.** As 58 sessões continuam lá. Nenhum template foi criado. Nenhuma migration
> foi editada. Este arquivo é um **registro de decisão pendente**, não um plano de execução.

---

## O fato medido

`backend/agent/dbupdate/.../V15__chat_session_prompt_template.sql` roda dentro de uma transação
(`supportsDdlTransactions=true`) e **aborta** se sobrar sessão sem prompt. Mensagem do próprio
guard, no boot:

```
V15 ABORTADA — 58 sessao(oes) de chat ficariam sem prompt_template_id. NADA FOI ALTERADO.

CAUSA: o agente que essas sessoes apontavam nao tinha prompt_template_id. A coluna e NULLABLE
desde a V4, entao agente sem template e estado de producao real, nao dado corrompido.

O ROLLBACK TAMBEM DESFEZ O PASSO 1 DESTA MIGRATION, ENTAO A COLUNA prompt_template_id NAO EXISTE
MAIS. O passo 1 abaixo recria.
```

Números no banco `hive` de dev, medidos hoje:

| métrica | valor |
|---------|-------|
| `chat_session` | **58** |
| `agent_id` apontando para agente **inexistente** | **0** |
| sessão cujo agente existe mas **não tem `prompt_template_id`** | **58** (100%) |
| sessão que a V15 conseguiria **recuperar** pelo backfill | **0** |
| tenants distintos | 2 |
| agentes sem `prompt_template_id` | 7 |
| `prompt_template` no banco | **0** |
| período | 2026-08-29 .. 2026-09-27 |
| `flyway_schema_history_agent` — versão aplicada | **14** |
| colunas de `chat_session` hoje | `id, create_at, update_at, tenant_id, user_id, agent_id, title, summary` |
| FK em `chat_session` | `fk_chat_session_agent` (apontando para `agent`) |
| tabela `agent` | **existe** (é a V16 que faz `DROP`) |
| `flyway_schema_history_tenant` / `_identity` | 3 / 3 (completas, não bloqueiam) |

**Migrations efetivamente aplicadas em `agent`:** V1 (baseline), V2, V3, V4, V5, V6, V7, V8, V9,
**V10** (`migrate provider to vendor`) e **V14** (`agent delete cascade`). **V15 e V16 nunca
aplicaram** — a V15 é a que aborta.

> ⚠️ **Número corrigido em 2026-09-27.** Este arquivo dizia versão aplicada **9**, e a frase dizia
> "V10, V14, V15, V16 nunca aplicaram". **Ambas erradas: V10 e V14 aplicaram.** O valor medido é
> **14**. Num documento cuja função é dizer a alguém qual é o estado do schema *antes* de rodar o SQL
> de recuperação, uma versão errada manda a pessoa raciocinar sobre um schema duas migrations
> atrás — e a diferença aqui não é cosmética: V14 (`agent delete cascade`) é justamente a que
> adicionou a cascata que a V15 vai remover.

### ⚠️ Como medir isso (o `max()` mente)

> `version` do Flyway é **`character varying`**, não numérico. Então
> `select max(version) from flyway_schema_history_agent` devolve **`9`** — porque `'9' > '14'` em
> ordem de texto. Foi exatamente assim que este arquivo ficou errado, e como a checagem de aceite
> também devolveu 9 numa primeira leitura.
>
> As três consultas que não mentem:
>
> ```sql
> -- por installed_rank (a mais confiável: é a ordem de aplicação real)
> SELECT version, description FROM flyway_schema_history_agent ORDER BY installed_rank DESC LIMIT 1;
>
> -- ou convertendo
> SELECT max(version::numeric) FROM flyway_schema_history_agent;
> ```
>
> Mesma armadilha da família: `totalPages`/`totalElements` que o `Page` do Spring entrega aninhado
> (`.spec/tech-debts/…` do batch de UX). **Toda métrica vinda do servidor ou do Flyway em string é
> candidata a comparação lexicográfica.** `N` e `N` em string não são o mesmo número, e nenhum dos
> dois avisa.

Não há mistério: é o histórico acumulado do banco de dev, e **o banco está vazio de prompts**. As 58
sessões apontam para 7 agentes que nunca tiveram template.

### Uma correção ao parecer anterior

O parecer dizia "`agent_id_legacy` mantém a associação". Isso descreve o **passo 4 da V15**, que
**nunca rodou**. Neste banco a coluna ainda se chama **`agent_id`** (e é `NOT NULL`). A associação
está preservada de qualquer forma — é a coluna que existe, com ou sem o sufixo. Quem for decidir
precisa saber o nome real: `agent_id`, hoje.

## O guard está certo

Ele recusa destruir dado, dentro de transação, com rollback inteiro e mensagem que se explica sozinha.
Nada é meio-aplicado, nenhum dado é perdido, e o próximo boot tenta de novo. **É o comportamento
correto** e não deve ser afrouxado para o ambiente "subir".

O efeito colateral é que **`mvn spring-boot:run` no perfil dev não sobe** com este banco. Esse é o
custo a decidir, não um defeito do guard.

## As três saídas, com o custo de cada uma

### A — Descartar as 58 sessões

`DELETE FROM chat_session` (ou `chat_message` junto, por cascade) e seguir.

- **Custo de dado:** as 58 conversas e suas mensagens somem. **Irreversível** sem backup.
- **Custo de produto:** nenhum, se o histórico de dev não vale nada. É dado de teste.
- **Custo de migração:** zero — a V15 passa a rodar limpa.
- **Reversibilidade:** só com backup. Sem ele, é definitiva.
- **Quando é a certa:** quando o histórico de dev é descartável por definição, o que provavelmente
  é — mas **é a sua decisão, não minha**.

### B — Criar um template default por tenant e reprocessar

Criar um `prompt_template` por tenant (2) e preencher `agent.prompt_template_id` para os 7 agentes, ou
`chat_session` direto. A V15 faz o backfill sozinha a partir de `agent.prompt_template_id`.

- **Custo de dado:** preserva as 58 conversas, mas **reescreve o que elas significam** — 58 sessões
  passam a dizer que foram respondidas por um template que nunca as respondeu. Dado sintético
  apresentado como real.
- **Custo de produto:** baixo, e o ambiente sobe preservando o histórico.
- **Custo de migração:** zero, se o fill for feito por SQL avulso **fora** das migrations (ou uma
  V14.5 dedicada, o que é discutível: uma migration que existe só para consertar banco de dev não
  deveria ir para o repositório).
- **Reversibilidade:** sim, se o `prompt_template` criado for marcado como descartável.
- **Quando é a certa:** quando se quer exercitar o histórico com volume realista (o ChatSidebar com
  paginação, por exemplo — que foi o que o 🟡3 expôs).

### C — Preservar e decidir depois

Nada é tocado. O ambiente de dev continua sem subir com este banco, e o H3 segue rodando contra um
banco greenfield separado.

- **Custo de dado:** zero.
- **Custo de produto:** o ambiente de dev padrão fica quebrado, e quem for subir o projeto vai bater
  no mesmo abort — **e provavelmente vai culpar o guard em vez do dado**.
- **Custo de migração:** zero.
- **Quando é a certa:** quando o histórico de dev tem valor de referência, ou quando se quer
  primeiro registrar o inventário (backup) antes de qualquer escolha.

## A recomendação (e por que não decido)

**B, com o `prompt_template` marcado como descartável**, e só depois de um
`pg_dump` do banco. Motivos:

- A é mais simples e provavelmente o que o usuário quer, **se** ele confirmar que o histórico de dev
  não tem valor. Eu não tenho como saber disso — e é exatamente por isso que a decisão é dele.
- B resolve o volume realista que o 🟡3 precisa (o paginador do `ChatSidebar` só é visível com mais
  de 20 conversas) sem custar nada.
- C deixa a armadilha armada para a próxima pessoa que subir o projeto.

Independentemente da escolha: **faça backup antes**. `docker exec postgres pg_dump -U postgres hive > ~/hive-dev-backup.sql`.

**Em qualquer uma das três, a V15 e a V16 continuam intocadas.** Nenhuma delas é o problema; o
problema é o dado que a V15 se recusa a destruir.

## O que NÃO fazer (registrado para quem for mexer)

- ❌ Afrouxar o guard, trocar `RAISE EXCEPTION` por `WARNING`, ou adicionar um `IF NOT EXISTS` que
  masks a sessão órfã. O guard existe **por causa** de sessões assim, e a migração explica isso em
  40 linhas de comentário. Afrouxá-lo converte um abort explícito em perda silenciosa em qualquer
  ambiente que tenha histórico.
- ❌ Criar template default **dentro** de uma migration versionada. Isso é conserto de banco de dev
  morando no repositório, e vai rodar em produção.
- ❌ Apagar a tabela `chat_session` inteira em vez das 58 linhas. As outras tabelas do schema
  continuam válidas, e a V15/V16 precisam de `chat_session` para existirem.

## Depois da decisão

1. `mvn spring-boot:run -pl bootstrap -Dspring-boot.run.profiles=dev` sobe
2. `flyway_schema_history_agent` sai de **14** e chega a **16** (aplica V15 e V16)
3. `chat_session` ganha `prompt_template_id` (NOT NULL, FK para `prompt_template`) e perde `agent_id`
   — que **vira `agent_id_legacy`, não é apagada**; a FK `fk_chat_session_agent` cai junto
4. a tabela `agent` é dropada (V16)
5. o H3 completo do Bloco B pode rodar contra o banco de dev, sem banco greenfield paralelo
