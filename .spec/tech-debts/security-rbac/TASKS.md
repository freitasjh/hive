# Débito técnico pré-existente — RBAC decorativo e nome de permissão herdado

**Registrado em:** 2026-09-27, durante a revisão do Batch 2 (Assistant sem RAG).
**Atualizado em:** 2026-09-27, após o batch de UX do seletor — o P1-1 gained **uma barreira de
teste** (paridade verificada) e as **2 permissões de conhecimento saíram** das duas listas, então a
contagem de 8 passou a 6.
**Status:** 🔴 P1 pré-existente. **NÃO corrigido neste batch**, e a decisão é explícita.
**Classe:** segurança / autorização.

---

## P1-1 — Todo usuário autenticado é superusuário

**Arquivo:** `backend/security/impl/src/main/java/br/com/systec/hive/security/impl/service/SecurityTokenServiceImpl.java:175`

A concessão de permissões do token usa a **lista hardcoded** do método, não
`SecurityConstants.DEFAULT_PERMISSIONS`. Consequência: as **6** permissões (`PROMPT_MANAGE`,
`PROMPT_ADMIN`, `AGENT_MANAGE`, `AGENT_ADMIN`, `CHAT_SEND`, `CHAT_SESSION_MANAGE`) são concedidas a
**qualquer usuário que se autentique**.

> ⚠️ **Contagem corrigida em 2026-09-27.** Este arquivo dizia "8 permissões" e listava
> `KNOWLEDGE_MANAGE` e `KNOWLEDGE_READ`. As duas **não existem mais**: foram removidas de
> `SecurityConstants` e da lista de concessão porque nenhum endpoint as verificava desde a queda do
> `KnowledgeBaseControllerV1`. Quem fosse corrigir este P1 procurando `KNOWLEDGE_MANAGE` não acharia
> nada. As duas entradas foram também riscadas do histórico deste arquivo.

`SecurityConstants.DEFAULT_PERMISSIONS` é apenas dado de exibição — nada o lê no caminho da
concessão. Alguém que o mudar achando estar configuring o RBAC não muda nada, e não recebe nenhum
sinal de que não mudou.

> ✅ **Mitigação adicionada em 2026-09-27 (não é a correção).**
> `LoginIT.when_login_then_tokenCarriesExactlyTheDeclaredDefaultPermissions` compara o claim
> `permissions` de um token **real** com `SecurityConstants.DEFAULT_PERMISSIONS` e falha se divergirem
> em qualquer direção. A duplicação deixa de ser um comentário que mente e passa a ser um invariante
> testado. O P1 continua aberto: as duas listas podem concordar e ainda assim ambas estarem erradas —
> quem concede a lista inteira continua sendo o mesmo código.

O gate por permissão **funciona e é testado**: um token sem `CHAT_SEND` leva 403 em
`/v1/chat` (`ValidateIT.when_validate_withoutPermission_then_returns403`). O que não funciona é a
**concessão** — não existe caminho hoje que produza um token com menos permissões, então o gate
nunca exercise o caminho de verdade.

**Por que não foi corrigido no Batch 2:**

1. Está **fora do diff** e **fora do escopo** pedido.
2. Este batch **não ampliou o elo mais fraco**: `CredentialControllerV1` (trocar a API key de
   qualquer tenant) já estava atrás do mesmo `AGENT_MANAGE` antes do Batch 2. O Batch 2 adicionou
   `TenantLlmConfigControllerV1` sob a mesma permissão, o que aumenta a **superfície** atrás de um
   gate já permissivo — não o cria. A porta já estava destrancada; o Batch 2 só colocou mais uma
   coisa atrás dela.
3. Consertar isso exige decidir a **matriz de papéis** (quem é admin, quem é membro, quem é leitor),
   que é decisão de produto e de segurança — não de refactor.

**Impacto de não corrigir agora:** qualquer usuário autenticado pode trocar a chave de API e o modelo
padrão de **qualquer tenant**. Num ambiente single-tenant isso é irrelevante; num multi-tenant é
uma escalação de privilégio entre clientes.

**Correção proposta (batch próprio, fora daqui):**

- Ler a concessão de uma fonte única — `SecurityConstants.DEFAULT_PERMISSIONS` para o papel padrão,
  com um papel por usuário persistido.
- Adicionar um papel `MEMBER` de menor privilégio e reservar `*_ADMIN` a ele, para que o teste do
  gate 403 passe a exercitar um token que existe na prática.
- Adicionar um IT que afirme que um usuário do papel `MEMBER` **não** tem `AGENT_ADMIN` — hoje não
  há como escrever esse teste, porque não há token assim.

---

## P1-2 — `AGENT_MANAGE` não significa mais "gerenciar agentes"

**Arquivos:** `backend/commons/api-tools/src/main/java/br/com/systec/hive/commons/api/tools/security/SecurityConstants.java:10,20`

O nome nasceu em 2026-08-30, quando a permissão guardava o editor de agentes. Hoje ela guarda:

| Endpoint | O que faz |
|----------|-----------|
| `GET/PUT/DELETE /v1/provider-credentials/{provider}` | **trocar a chave de API do tenant** |
| `GET /v1/providers/{provider}/models` | discovery do catálogo remoto |
| `POST /v1/providers/validate` | probe de conectividade |
| `GET/PUT /v1/tenant/llm-config` | **mudar o modelo que responde por todo o tenant** |

O conceito de agente morreu no Batch 2; o nome da permissão ficou. Quatro controllers, nove
endpoints, zero agentes.

**Por que não foi corrigido:** renomear uma permissão é **breaking change de dado**, não de código.
A string vive dentro dos tokens já emitidos e em qualquer tabela que a persista. Trocá-la exige
migrar tokens emitidos (ou esperar a expiração) e aceitar que tokens antigos carreguem a string
antiga. É batch próprio, e precisa de decisão sobre o período de convivência das duas strings.

**Correção proposta:** `LLM_MANAGE` (ou `PROVIDER_MANAGE`), com convivência das duas strings
aceita por um período, e o nome novo no `DEFAULT_PERMISSIONS` novo.

---

## Débito 3 (informativo, não P1) — módulo `agent` e serviço `agent-admin` com o nome errado

- O módulo Maven `agent` não tem mais nada de agente: é chat, sessão e prompt. Renomear toca ~11
  diretórios, ~60 arquivos, a localização Flyway e exigiria decidir sobre
  `flyway_schema_history_agent`. **Zero valor de produto.**
- `frontend/src/services/agent-admin.service.ts` guarda credenciais, catálogo e probe — coisas de
  **provedor**. Renomear tocaria `CredentialsView`, `ConnectionTester` e os testes deles.

Nenhum dos dois foi tocado no Batch 2. O nome mente, e a decisão de renomear é do próximo batch que
for mexer nesses arquivos.
