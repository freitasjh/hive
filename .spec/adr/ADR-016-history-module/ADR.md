---
adr_id: ADR-016
unit: adr
status: in-progress
title: "Extrair adapter JPA de histórico para módulo history; domínio permanece em agent/api"
data: 2026-11-21
decisor: architect
tipo: adr-sem-prd
---

# ADR-016 — Extrair adapter JPA de histórico para módulo `history`; domínio permanece em `agent/api`

> **Ciclo de execução aberto em 2026-11-21** (aprovação humana): `unit: adr` + `status: in-progress`. Os artefatos `SPEC.md`/`TASKS.md` nascem neste diretório, com marcador `<!-- scope: adr/ADR-016 -->` no TASKS (F4). Código, pom, config, teste e migration **nascem só na delegação ao `backend-engineer`**, nunca neste ADR. `workflow-state.json`, branch/PR e ADR de PRD seguem fora deste ciclo.
>
> Amparo para ADR sem PRD: `.agents/rules/prd-adr-pipeline.md:43-45` — "Arquitetural sem valor de produto novo ⇒ ADR sem PRD". O teto de 5 ADRs do PRD `langchain4j-ai-services` está gasto (ADR-001..005 em `.spec/prd/langchain4j-ai-services/ADRs/`); este ADR **não** entra naquele `ADR-INDEX.md`, **não** cria 6º ADR lá e **não emenda** ADR-001 nem ADR-003. Precedente legado fora da allowlist deste agente: `docs/adr/ADR-012`, `ADR-013`, `ADR_007`.

- **Decisão (variante B)**: extrair o adapter JPA/repository/mapper de histórico para módulo novo `backend/history/` (agregador + `history/impl`). Domínio (`ChatMessage`, `ChatSession`), porta (`ChatMessageService`) e a função de conversão de ADR-001 **permanecem em `agent/api`**. Nenhum `ChatMemory`/`@MemoryId` entra.
- **Motivo**: centralizar persistência de histórico, preparar base compartilhada para assistant/Agent futuro sem introduzir `ChatMemory`, e reduzir a aresta `publicapi/impl → agent/impl` no caminho de histórico (com ressalva evidenciada na §8 — a extração **não** elimina sozinha os dois imports de política de turno).
- **Alternativas descartadas**: manter em `agent/impl` (custo zero, aresta permanece) · migrar domínio para `history/api` (mudaria ADR-001/003) · incluir `ChatMemoryStore` (ADR-003 + A8/A12/A13) · extrair adapter para módulo novo (**escolhida**).
- **depende-de**: ADR-001 (`agent/api` como dono da conversão), ADR-003 (JPA é fonte de verdade; sem memória), ADR-007 (TX1/TX2 e window 20 — parte de persistência continua válida, `ADR_007_Chat_Persistence.md:16-17,26-27`). ADR-012 gatilho #3 **não dispara**: o histórico continua gerenciado por nós, não pela biblioteca (`ADR-012:95`).
- **Interface congelada**: porta `ChatMessageService` com 4 métodos exatos; `findLast20` preservado; TX1/TX2 preservadas; nenhuma mudança de schema SQL; nenhuma aresta nova para `agent/impl`.
- **Ownership de Flyway**: migrations de `chat_message` **ficam em `agent/dbupdate`** (débito de coesão registrado).
- **Fora desta decisão**: implementação, `ChatMemory`/agent, mudança de contrato HTTP, MFA, migrations de dados.
- **Status**: in-progress

---

## 1. Decisão detalhada

### 1.1 Nome e forma do módulo

Escolha: **`history`** (agregador `backend/history/pom.xml`, submódulo `backend/history/impl/`).

Justificativa do nome: o agregador raiz declara módulos de primeiro nível com palavra única — `tenant`, `commons`, `identity`, `security`, `agent`, `provider`, `publicapi` (`backend/pom.xml:16-27`). `history` segue essa convenção; `chat-history` introduziria hífen num nome de artefato onde nenhum outro módulo o usa, sem ganho de semântica — o domínio já é chat, o módulo é o *dono da persistência do histórico*.

Submódulo `history/api`: **não criado neste ADR** (a cláusula "somente se necessário" do pedido). A porta `ChatMessageService` e os tipos `ChatMessage`/`ChatSession` já vivem em `agent/api` (`ChatMessageService.java:11-20`; `agent/api/.../domain/ChatMessage.java`, `ChatSession.java`), e ADR-001 congela a função de conversão nesse mesmo módulo. Criar `history/api` agora duplicaria o contrato ou o esvaziaria. Consumidores compilam contra `agent/api`; se amanhã assistant/Agent exigir uma fachada de consulta de histórico, a criação de `history/api` é ADR separado.

Classe implementadora: `ChatMessageServiceImpl` passa de `br.com.systec.hive.agent.impl.service` para `br.com.systec.hive.history.impl.service`, continuando a `implements br.com.systec.hive.agent.api.service.ChatMessageService`. Juntos migram: `ChatMessageEntity`, `ChatSessionEntity` (de `agent.impl.entity`), `ChatMessageMapperDomain` (de `agent.impl.mapper`), `ChatMessageRepository`, `ChatSessionRepository` (de `agent.impl.repository`) — inventorário verificado em `backend/agent/impl/src/main/java/br/com/systec/hive/agent/impl/{entity,mapper,repository,service}/`.

### 1.2 O que fica (invariante)

- `agent/api`: `ChatMessage`, `ChatSession`, `ChatMessageService`, `ProviderMessages`/conversão de ADR-001, `ChatTurnGateway`.
- `agent/impl`: orquestração do turno, `ProviderResolution`, `TurnOutcomeMapper`, `FailureClassifier`, `LangChain4jTurnGateway`, `ChatServiceImpl`, `ChatAssistantTxService` — ou seja, **tudo o que não é adapter de histórico**.
- `agent/dbupdate`: dono das migrations V2–V5 e V7 override de `chat_session`/`chat_message` (`TECH-BRIEF.md:79`).
- Nenhuma aresta nova de `history/impl` para `agent/impl`. `history/impl` depende de `agent/api` (porta/tipos), `commons/impl` (`TenantContext`, `TenantAspect`) e `provider/api` (`ChatRole`) — as mesmas dependências que `ChatMessageServiceImpl.java:3-21` já usa hoje.

---

## 2. Motivo

1. **Centralização de persistência**: hoje o adapter de histórico mora dentro de `agent/impl` junto de política de turno, retry wrapper e telemetria (`TECH-BRIEF.md:78`). Extrair dá um único dono para `save`/`getMessages`/`findLast20`/`countBySessionIdAndTenantId` (`ChatMessageService.java:13-19`), separado da decisão de *como gerar* a resposta.
2. **Base compartilhada futura sem `ChatMemory`**: o pedido de "assistant/Agent futuro" é o gatilho #5 de ADR-012 (segunda implementação real da porta) e o gatilho #2 de ADR-013 (aggregate acima da sessão). Com o adapter em `history/impl`, um futuro consumidor usa a porta em `agent/api` e o adapter compartilhado, sem precisar importar `agent/impl`. Nenhuma `ChatMemory` entra: ADR-003 §Interface congelada 2 (`ADR-003-memoria-historico-memoryid/ADR.md:44`). Afirmação — LangChain4j oferece apenas "memory"; histórico integral deve ser mantido manualmente — `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 (**documentação não versionada**, A12 do TECH-BRIEF). Afirmação — `ChatMemoryStore` expõe `getMessages(Object)`, `updateMessages(Object,List<ChatMessage>)`, `deleteMessages(Object)` — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/store/memory/chat/ChatMemoryStore.java` — 2026-11-21 (A10). Afirmação — chamada concorrente para o mesmo `@MemoryId` corrompe `ChatMemory` sem proteção nativa — `https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` — 2026-11-21 (A8).
3. **K3 — com ressalva evidenciada**: a aresta `publicapi/impl → agent/impl` existe em `publicapi/impl/pom.xml:33-37` (`hive-agent-impl`) e em `PublicChatServiceImpl.java:9-10` (`ProviderResolution`, `TurnOutcomeMapper`). A extração resolve a **faceta de histórico** — nenhum consumidor de histórico precisará importar `agent/impl` para persistir/ler mensagens — mas **não** remove os dois imports de política de turno acima. Ver §8 e §9: registro honesto do que este ADR entrega e do que fica como débito.

---

## 3. Alternativas consideradas (com fonte)

| # | Alternativa | Veredito | Evidência / fonte |
|---|---|---|---|
| a | **Manter adapter em `agent/impl`** (custo zero) | Rejeitada | Zero diff, mas K3 permanece e o acoplamento histório↔turno se mantém: `publicapi/impl/pom.xml:33-37`, `PublicChatServiceImpl.java:9-10`, `TECH-BRIEF.md:97,249`. Contradiz o objetivo de base compartilhada para assistant/Agent (gatilhos ADR-012 #5, ADR-013 #2). |
| b | **Migrar domínio (`ChatMessage`/`ChatSession`) e porta para `history/api`** | Rejeitada | ADR-001 congela a conversão em `agent/api` (`ADR-001:16,20,47`); ADR-003 congela JPA como fonte de verdade (`ADR-003:43`). Exigiria emendar ambos — explicitamente fora do escopo deste ADR e do teto de 5 ADRs do PRD. |
| c | **Incluir `ChatMemoryStore` sobre JPA no módulo novo** | Rejeitada | ADR-003 (`ADR-003:17,44`); A12/A13/A8: memória ≠ histórico, evict em memória também apaga do store, chamada concorrente corrompe. Afirmação — mensagens evictadas de `ChatMemory` também saem do `ChatMemoryStore` — `https://docs.langchain4j.dev/tutorials/chat-memory` — 2026-11-21 (**não versionada**, A13). `TenantContext` é `ScopedValue` que não propaga (`commons/impl/.../security/TenantContext.java:8-16`; `TECH-BRIEF.md:167`), agravando risco cross-tenant em store assíncrono (K9). |
| d | **Extrair adapter JPA/repository/mapper para módulo novo, domínio em `agent/api`** | **Escolhida** | Preserva ADR-001 e ADR-003 byte a byte; resolve a faceta histórica de K3 sem aresta nova para `agent/impl`; custo limitado a movimento de classes + imports + poms, com baseline de suíte já verde (§9). |

---

## 4. Interface congelada

Contrato que o implementador futuro não pode mudar sem novo ADR:

1. **Porta inalterada, em `agent/api`**: `br.com.systec.hive.agent.api.service.ChatMessageService`, exatamente quatro métodos — `save(UUID sessionId, UUID tenantId, ChatRole role, String content)`, `Page<ChatMessage> getMessages(UUID sessionId, UUID tenantId, UUID userId, Pageable pageable)`, `List<ChatMessage> findLast20(UUID sessionId, UUID tenantId)`, `long countBySessionIdAndTenantId(UUID sessionId, UUID tenantId)` (`ChatMessageService.java:11-20`). Assinaturas, tipos de retorno e exceções (`ChatSessionNotFoundException`, `IllegalArgumentException` para content vazio/>4000 em `ChatMessageServiceImpl.java:41-45`) permanecem idênticos.
2. **Implementadora**: `br.com.systec.hive.history.impl.service.ChatMessageServiceImpl implements` a porta acima. `@Service`, `@Transactional` em `save` (`ChatMessageServiceImpl.java:23-24,38-40`) preservados. Um único bean no contexto — nenhuma segunda implementação em `agent/impl`.
3. **`findLast20` preservado (ADR-003)**: `PageRequest.of(0, 20, DESC createAt)` → `findBySessionIdAndTenantIdOrderByCreateAtDesc` → `Collections.reverse` → `mapper::toDomain` (`ChatMessageServiceImpl.java:68-74`). Window 20 e ordem ASC final são invariantes (`ADR_007_Chat_Persistence.md:26-27`).
4. **TX1/TX2 preservadas (ADR-007)**: TX1 save da mensagem do usuário antes da chamada ao provider; fora de TX o `findLast20` + geração; TX2 `ChatAssistantTxService.saveAssistantAndTouch` atômico (`ADR_007_Chat_Persistence.md:26`; `ChatServiceImpl.java` em `agent/impl`). O adapter continua participando só como leitura/escrita — **nenhuma transação é movida para dentro de `history/impl` além do `@Transactional` que já existe em `save`**.
5. **Pacotes migrados, comportamento não**: `ChatMessageEntity`, `ChatSessionEntity`, `ChatMessageMapperDomain`, `ChatMessageRepository`, `ChatSessionRepository` saem de `br.com.systec.hive.agent.impl.*` para `br.com.systec.hive.history.impl.*`. Nenhuma lógica de mapper, `@Filter`, `@Enumerated`, FK ou índice é alterada.
6. **Schema SQL congelado**: zero mudança em `.sql`, zero migration nova, zero `flyway_schema_history_*` novo. `chat_message.content NOT NULL`, FK `ON DELETE CASCADE`, `tenant_id NOT NULL`, 7 índices (`ADR_007_Chat_Persistence.md:25`) permanecem.
7. **Sem `ChatMemory`/`@MemoryId`**: nenhum `ChatMemoryStore`, `ChatMemoryAccess`, `chatMemory(`, `chatMemoryProvider(` ou `@MemoryId` em `backend/**` (estado atual em `TECH-BRIEF.md:196`; verificação estática em `ADR-003:67`).
8. **Aresta**: nenhuma dependência Maven nova de `history/impl` → `agent/impl`, e nenhum import de `agent.impl.*` em `history/impl`. `agent/impl` passa a depender de `history/impl` apenas para wiring runtime (via `bootstrap`), nunca no contrato. `publicapi/impl` não ganha dependência de `history/impl` — consome a porta em `agent/api`.
9. **ADR-001 intacto**: a conversão `domínio → mensagens do provider` permanece função pura em `agent/api`, ordem "template primeiro, histórico depois" congelada (`ADR-001:16,49`).

---

## 5. Ownership de Flyway

**Decisão**: as migrations de `chat_message`/`chat_session` (V2–V5, V7 override — `TECH-BRIEF.md:79`) **permanecem em `agent/dbupdate`**. `history/` **não** ganha módulo `dbupdate`, não ganha `flyway_schema_history_history` e não ganha `DROP SCHEMA`.

Justificativa:

- `history/impl` não cria tabela nova — apenas troca o dono do código Java. Mover `.sql` para outro módulo mudaria o histórico Flyway sem mudar o schema, e é exatamente a operação que ADR-013 classificou como "no-op que se apresenta como sucesso": `AgentDbUpdateConfig` chama `flyway.repair()` antes de `migrate()`, o que realinha checksum e marca versões sumidas como `deleted` sem executar SQL (`ADR-013:152-163`). O procedimento correto de realocação exigiria `DROP SCHEMA` do módulo + `flyway_schema_history_*` (`ADR-013:166-169`) — risco desproporcional a uma mudança que não altera coluna alguma.
- Dois históricos Flyway independentes e sem ordem garantida são o argumento que ADR-013 usou para manter `provider_credential` em `agent` (`ADR-013:189-193`). Aplicar a mesma régua aqui: sem tabela nova, sem segundo histórico.
- Custo zero de `flyway_schema_history`: nenhuma linha é reescrita, nenhum `repair()` é disparado, nenhuma janela de "log verde que mente" (`ADR-013:175-179`).

**Débito de coesão registrado**: `agent/dbupdate` passa a ser dono do schema enquanto `history/impl` é dono do código que o acessa. Quem ler só o módulo verá adapter sem migration; quem ler só `dbupdate` verá migration sem adapter. Aceito de propósito para não pagar o custo de migração de histórico Flyway. Reavaliar apenas se surgir tabela **nova** de histórico (ex.: `chat_history_snapshot`) — aí `history/dbupdate` passa a ser necessário e a decisão volta em ADR próprio.

---

## 6. Sequência de movimentação (alto nível — sem código)

1. **Novos poms**: agregador `backend/history/pom.xml` + `backend/history/impl/pom.xml`; registrar `<module>history</module>` em `backend/pom.xml:16-27`. Dependências do impl espelham as atuais de `agent/impl/pom.xml:22-61` relevantes ao adapter (`commons-impl`, `commons-api`, `agent-api`, `provider-api`, `spring-boot-starter-data-jpa`, `mapstruct`), **sem** `hive-agent-impl`.
2. **Mover classes**: os 6 arquivos da §1.1 para `history/impl`, com rename de pacote `agent.impl` → `history.impl`. `ChatMessageServiceImpl` mantém `implements ChatMessageService` de `agent/api`.
3. **Ajustar imports**: todo `import br.com.systec.hive.agent.impl.{entity,mapper,repository,service}.Chat*` em `agent/impl` (e em testes) passa a apontar para `history.impl` onde o símbolo tiver mudado. `agent/impl` continua importando `agent.api.service.ChatMessageService` — a porta não se move.
4. **Ajustar wiring**: `bootstrap` passa a incluir o novo módulo no classpath de runtime; `ChatServiceImpl` continua injetando a porta, não a implementação concreta.
5. **Remover aresta sobre histórico**: `publicapi/impl/pom.xml:33-37` e `PublicChatServiceImpl.java:9-10` são tratados conforme §8/§9 — este ADR **não** os altera por conta própria; a remoção total depende de decisão separada sobre `ProviderResolution`/`TurnOutcomeMapper`. O que este passo garante é que **nenhuma dependência de histórico** passe a existir entre `publicapi/impl` e `agent/impl`.
6. **Suíte**: `commands.suiteFull` — baseline já medido nesta sessão: `npm test` EXIT=0 (376/376 front), 62 surefire reports backend sem falhas, log `/tmp/opencode/ai-services-tests-2026-11-21.log`.
7. **Review**: `code-reviewer` sem 🔴; verificação estática de paths MFA e de imports; `brain_store` da decisão após implementação (fora deste ADR decision-only).

**Zero diff declarado em paths MFA**: nenhum arquivo sob `backend/security/**/Mfa*` ou `backend/identity/**/Mfa*` é tocado em nenhum passo.

---

## 7. Riscos e débitos

| # | Risco / débito | Evidência | Tratamento |
|---|---|---|---|
| K3 | Aresta `publicapi/impl → agent/impl` **não é eliminada** pela extração do adapter: os dois imports são `ProviderResolution` e `TurnOutcomeMapper`, classes de política de turno | `publicapi/impl/pom.xml:33-37`; `PublicChatServiceImpl.java:9-10`; `ProviderResolution.java:42-45` (cycle: `CredentialResolver` em `agent/impl`); `TurnOutcomeMapper.java:13` (`FailureClassifier` em `agent/impl`) | Declarado em §8/§9. Faceta histórica resolvida; faceta de política de turno fica para ADR separado. **Não reescrever o critério de verificação como se estivesse satisfeito.** |
| Ownership de schema | `agent/dbupdate` dono do SQL, `history/impl` dono do código | §5; `TECH-BRIEF.md:79` | Débito de coesão aceito; reavaliar só com tabela nova. |
| Package rename | `ChatMessageServiceImplTest.java` e `ChatMessageMapperDomainTest.java` (em `agent/impl/src/test/...`) quebram com o rename de pacote | glob verificado nesta sessão; `ADR-012:86` (precedente: testes que fixavam decisão morreram com a extração e só voltaram porque reviewer cobrou) | Mover os testes junto com as classes; não apagar guardas; baseline de `findLast20` (20 mensagens, ordem ASC) preservado. |
| Tenant | `TenantContext` é `ScopedValue<UUID>`, `getCurrentTenant()` lança quando ausente, não propaga para thread pool; `TenantAspect` segue com filtro desligado quando não encontra tenant | `commons/impl/.../security/TenantContext.java:8-16`; `TenantAspect.java:23-35`; `TECH-BRIEF.md:167-172`; K9 | Nenhum callback assíncrono entra neste ADR; `history/impl` herda exatamente o comportamento atual. Se amanhã entrar store assíncrono, é ADR novo (ADR-003 §4). |
| Dupla fonte de verdade | Se `ChatMemory` entrar depois, `chat_message` e store podem divergir | A8/A12/A13; `ADR-003:43-47` | Bloqueado por ADR-003 (item 7 da interface congelada); reabertura só com ADR e IT de corrida. |
| Gatilho ADR-012 #3 | Reavaliação de `AiServices` se "histórico passar a ser gerenciado pela biblioteca" | `ADR-012:95` | **Não dispara**: este ADR apenas troca o dono do adapter JPA; a biblioteca não gerencia histórico. |
| Gatilho ADR-013 #2 | Reavaliação do aggregate root se "múltiplas conversas independentes / memória de longo prazo" | `ADR-013:214-215` | **Não dispara**: sessão continua aggregate root; nenhuma entidade nova. |

---

## 8. Achado de evidência — K3 e o critério `grep = zero`

Registro obrigatório de honestidade arquitetural (`.agents/rules/prd-adr-pipeline.md` não autoriza afirmar efeito não provado; `TECH-BRIEF.md:12-14` exige fonte, não memória).

**Fato verificado nesta sessão**: o `grep` por `agent.impl` em `backend/publicapi` retorna exatamente 4 ocorrências — 2 em `PublicChatServiceImpl.java:9-10` (main) e 2 em `PublicChatServiceImplTest.java:8,46` (teste). Os símbolos são `ProviderResolution` e `TurnOutcomeMapper`. **Nenhuma delas é classe de persistência de histórico.** `ChatMessageServiceImpl`, `ChatMessageEntity`, `ChatMessageRepository` etc. não são importados por `publicapi/impl` hoje.

**Consequência**: mover o adapter JPA para `history/impl` **não** altera o resultado desse `grep`. A remoção total da aresta exigiria relocar também `ProviderResolution` e `TurnOutcomeMapper`, o que esbarra em restrições reais:

- `ProviderResolution.java:42-45` documenta que vive em `impl` por razão de build: `CredentialResolver` lê repositório de credencial criptografada em `agent/impl`; mover a classe para `agent/api` criaria ciclo Maven `api → impl`.
- `TurnOutcomeMapper.java:13` importa `br.com.systec.hive.agent.impl.diagnostics.FailureClassifier`; mover para `agent/api` exigiria mover o classificador junto.

**Veredito deste ADR**: entrega a **faceta de histórico de K3** (base compartilhada sem importar `agent/impl` para persistência) e **não entrega** a remoção integral do `grep = zero`. O critério `grep` é registrado na §9 como **condição dependente de ADR separado**, não como prova deste ADR. Se o solicitante exigir `grep = zero` como gate desta decisão, escopo deve ser ampliado em novo ADR — não alterado por inferência silenciosa aqui.

---

## 9. Como verificar

**Gate A — alcançável por este ADR (obrigatório)**:

1. Baseline de suíte: `npm test` EXIT=0 e 376/376 testes frontend; 62 surefire reports backend sem falhas; log `/tmp/opencode/ai-services-tests-2026-11-21.log`. Toda execução pós-implementação deve reproduzir 0 falhas, 0 erros, 0 skips não autorizados (H0/H1 — `.agents/rules/always-on.md` §5).
2. ITs de caracterização: `ChatMessageServiceImplTest.java` (em `agent/impl/src/test/...`, a mover com a classe) e `ChatMessageMapperDomainTest.java` preservam o baseline de `findLast20` — 20 mensagens, `DESC createAt` + `Collections.reverse`, ordem ASC final (`ChatMessageServiceImpl.java:68-74`; `ADR-003:66`).
3. Verificação estática: `grep` por `ChatMemory|ChatMemoryStore|ChatMemoryProvider|@MemoryId|chatMemory(` em `backend/**` = zero (como hoje, `TECH-BRIEF.md:196`).
4. Verificação estática de schema: `git diff` sobre `*.sql` e `flyway_schema_history` = zero.
5. Verificação de aresta nova: `grep` por `agent.impl` em `backend/history/**` = zero; `history/impl/pom.xml` sem `hive-agent-impl`.
6. `git diff --name-only` filtrado por `backend/security/**/Mfa*` e `backend/identity/**/Mfa*` = zero linhas.
7. Code review: `code-reviewer` sem 🔴; 🟡 corrigidos ou justificados; registro em `workflow-state.json` — fora deste ADR decision-only.

**Gate B — declarado pelo solicitante, NÃO satisfeito apenas por este ADR**:

8. `grep` por `br.com.systec.hive.agent.impl` em `backend/publicapi/**` = zero. Hoje = 4 ocorrências (`PublicChatServiceImpl.java:9-10`, `PublicChatServiceImplTest.java:8,46`). Requer ADR separado para `ProviderResolution`/`TurnOutcomeMapper` (+ `FailureClassifier`/`CredentialResolver`); ver §8. **Não marcar como verde por este ADR.**

---

## 10. Fora desta decisão

- Implementação, criação de poms, rename efetivo de arquivos, ajuste de imports e wiring (§6 descreve a sequência, não a executa).
- `ChatMemory`, `ChatMemoryStore`, `@MemoryId`, agent, tools, RAG, starter de Spring Boot — ADR-003 e ADR-012/013 permanecem.
- Mudança de contrato HTTP, DTOs, envelope OpenAI, cota, retry, classificação de erro, telemetria.
- MFA: nenhum arquivo `security/**/Mfa*` ou `identity/**/Mfa*`.
- Migrations de dados, `DROP SCHEMA`, `flyway.repair()`, novo `flyway_schema_history_*` (§5).
- Emenda a ADR-001, ADR-003, ADR-007, ADR-012 ou ADR-013; criação de 6º ADR no PRD `langchain4j-ai-services`; alteração do ADR-INDEX daquele PRD; `workflow-state.json`; branch/PR.

---

## 11. Consequências se estiver errado

- Se o domínio precisar mesmo migrar para `history/api`, este ADR terá criado um módulo adapter cuja porta ainda aponta para outro módulo — reversível por novo ADR, sem mudança de schema (o custo é de imports/poms, não de dado).
- Se K3 for lido como "zero imports" e este ADR for tratado como entrega completa, o gate falhará em revisão; §8 existe para impedir essa leitura.
- Se `history/impl` acumular política de turno no futuro, o módulo vira segundo `agent/impl` — a fronteira congelada no item 8 da §4 é a rede de segurança.
