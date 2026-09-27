# ~~Decisão pendente~~ RESOLVIDA — a V15 não existe mais (histórico reescrito)

**Registrado em:** 2026-09-27, na verificação H3 do batch de UX do seletor.
**Resolvido em:** 2026-09-27, pelo batch de reescrita do histórico de migrations do módulo `agent`.
**Status:** ✅ **encerrado** — a causa raiz não existe mais.
**Classe original:** decisão de produto/dado.

> ## O que mudou
>
> A V15 (`V15__chat_session_prompt_template.sql`) **não existe mais**, nem a V16, nem nenhuma outra.
> O histórico do módulo `agent` foi reescrito de 12 migrations para 4, todas `CREATE` puras:
>
> | # | Arquivo | Cria |
> |---|---------|------|
> | 1 | `V2__create_prompt_template.sql` | `prompt_template` no formato final |
> | 2 | `V3__create_provider_credential.sql` | `provider_credential` no formato final |
> | 3 | `V4__create_chat_session.sql` | `chat_session` com `prompt_template_id` NOT NULL + FK RESTRICT |
> | 4 | `V5__create_chat_message.sql` | `chat_message` com FK CASCADE para `chat_session` |
>
> **Este arquivo inteiro descreve um problema que a reescrita eliminou pela raiz.** O guard da V15
> existia para se recusar a destruir a associação de sessões que apontavam para um `agent`. Sem a
> tabela `agent` e sem a coluna `agent_id` — que nunca nascem agora — não há dado legado para
> backfill, não há órfã possível, e não há o que preservar em `agent_id_legacy`. A perda deixa de ser
> possível por construção do schema, e não por uma verificação rodada no boot.
>
> **O texto abaixo foi preservado como registro do que foi medido e decidido.** Ele vale como
> histórico do raciocínio, e os números são a medição que justificou a decisão — mas as
> recomendações dele ("não mexer na V15", "a V15 e a V16 continuam intocadas") **não valem mais**, e
> foram superadas pela decisão do usuário registrada logo abaixo.
>
> ### Decisão do usuário (autorizada, 2026-09-27)
>
> > "Como ainda não foi para testes e produção, pode ajustar as migrations para refletir o que for
> > de criação e configuração, não precisa ter migration para arrumar nenhuma tabela."
>
> Isso é a **opção A** (descartar as 58 sessões) mais a **C** (preservar o inventário antes), e foi
> executada: `pg_dump` completo antes de qualquer coisa, depois o schema do módulo `agent` foi
> recriado do zero.
>
> ### O que foi perdido
>
> | dado | quantidade | reversível |
> |------|-----------|------------|
> | `chat_session` | 58 | sim, pelo dump |
> | `chat_message` | 130 | sim, pelo dump |
> | `provider_credential` (credencial cifrada) | 1 | **não** — a chave de criptografia não está no dump; restaurar exige recadastrar a credencial |
> | `prompt_template` | 0 (nada a perder) | — |
>
> Dump: `.spec/snapshots/pre-rewrite-20260927-173430-full.dump` (formato custom, com dados).
>
> ### O que aconteceu com o resto do schema
>
> - `identity` (V2, V3) e `tenant` (V2, V3) **não foram tocados** — históricos Flyway intactos.
> - O resíduo do módulo `knowledge` (deletado em 2026-09-26, sem código no repositório) foi
>   removido junto: `agent_knowledge_base`, `kb_document`, `kb_embeddings_1536`, `kb_embeddings_768`,
>   `knowledge_base` e `flyway_schema_history_knowledge`. Foi **obrigatório**, porque
>   `agent_knowledge_base` tinha `fk_agent_kb_agent` apontando para `agent(id)`.
> - `flyway_schema_history_agent` foi recriado: baseline V1 + V2, V3, V4, V5, todas `success = true`.
>
> ### E o `ChatSessionPromptTemplateMigrationIT`?
>
> Removido. Os 5 testes validavam o comportamento da V15 contra um banco no estado V14 — um estado
> que não existe mais em lugar nenhum. A cobertura de formato de schema não ficou pendente: passou
> para `ChatSessionIT`, que foi corrigido e ganhou 2 testes
> (`when_migrationsApplied_then_promptTemplateIdIsNotNullByConstruction` e
> `when_migrationsApplied_then_promptTemplateHasNoDuplicatedActiveIndex`).

---

<details>
<summary>Registro original (2026-09-27, antes da reescrita) — preservado como histórico</summary>

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

> ⚠️ **SUPERADO.** Estas três regras eram sobre a V15, que não existe mais. A guarda de dado que
> elas defendiam **não foi afrouxada** — ela foi tornado estrutural: `chat_session.prompt_template_id`
> é NOT NULL no `CREATE TABLE`, então sessão órfã é impossível por construção. As regras 2 e 3 também
> continuam válidas como regra geral ("conserto de banco de dev não mora em migration versionada").

- ❌ Afrouxar o guard, trocar `RAISE EXCEPTION` por `WARNING`, ou adicionar um `IF NOT EXISTS` que
  masks a sessão órfã. O guard existe **por causa** de sessões assim, e a migração explica isso em
  40 linhas de comentário. Afrouxá-lo converte um abort explícito em perda silenciosa em qualquer
  ambiente que tenha histórico.
- ❌ Criar template default **dentro** de uma migration versionada. Isso é conserto de banco de dev
  morando no repositório, e vai rodar em produção.
- ❌ Apagar a tabela `chat_session` inteira em vez das 58 linhas. As outras tabelas do schema
  continuam válidas, e a V15/V16 precisam de `chat_session` para existirem.

## Depois da decisão

> ✅ **EXECUTADO (2026-09-27).** O plano abaixo era o cenário "aplicar V15 e V16 sobre o banco
> existente". O que aconteceu foi outro: o histórico foi reescrito e o schema do módulo `agent` foi
> recriado do zero, então não há V15 nem V16 para aplicar. Comparação do executado com o previsto:
>
> | # | previsto | executado |
> |---|----------|-----------|
> | 1 | dev sobe | ✅ sobe (`Successfully applied 4 migrations ... now at version v5`) |
> | 2 | histórico vai a 16 | vai a **5** (baseline V1 + V2..V5) |
> | 3 | `prompt_template_id` NOT NULL + FK; `agent_id` → `agent_id_legacy` | `prompt_template_id` NOT NULL + FK RESTRICT ✅; **`agent_id_legacy` não é criada** (não há o que preservar) |
> | 4 | tabela `agent` dropada pela V16 | `agent` **nunca é criada** ✅ |
> | 5 | H3 contra o banco de dev | pendente (fora do escopo deste batch) |

1. `mvn spring-boot:run -pl bootstrap -Dspring-boot.run.profiles=dev` sobe
2. `flyway_schema_history_agent` sai de **14** e chega a **16** (aplica V15 e V16)
3. `chat_session` ganha `prompt_template_id` (NOT NULL, FK para `prompt_template`) e perde `agent_id`
   — que **vira `agent_id_legacy`, não é apagada**; a FK `fk_chat_session_agent` cai junto
4. a tabela `agent` é dropada (V16)
5. o H3 completo do Bloco B pode rodar contra o banco de dev, sem banco greenfield paralelo

</details>

