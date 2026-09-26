# PRD - Versionamento de PromptTemplates

## 1. Objetivo de Negócio
Estabelecer um sistema robusto de gestão de ciclo de vida para os `PromptTemplates`. O versionamento permite que os usuários evoluam seus prompts (experimentação e refinamento) sem interromper o funcionamento de agentes que dependem de versões estáveis, permitindo rollback imediato e testes de novas versões em ambiente controlado antes de serem ativadas globalmente por tenant.

## 2. Regras de Negócio
| ID | Regra | Descrição |
|:---|:---|:---|
| RN01 | Identificação por Código | Um `code` (String) identifica um conjunto de versões de um prompt. Diferentes versões do mesmo prompt compartilham o mesmo `code`. |
| RN02 | Unicidade de Versão Ativa | Para um dado `tenantId` e `code`, apenas **uma** versão pode estar com o status `active = true`. |
| RN03 | Status Inicial (Draft) | Toda nova versão de um prompt deve nascer com o status `draft = true` e `active = false`. |
| RN04 | Transição de Status | Ao ativar uma nova versão (`active = true`), a versão que estava anteriormente ativa para aquele `tenantId` e `code` deve ser automaticamente desativada (`active = false`). |
| RN05 | Imutabilidade de Versão | Uma versão marcada como `active = true` ou que já foi utilizada em execuções históricas não deve ser editada (exceto metadados de identificação), incentivando a criação de uma nova versão para alterações no template. |
| RN06 | Incremento de Versão | O campo `version` (Integer) deve ser incrementado automaticamente para o mesmo `code` dentro de um `tenantId`. |

## 3. Estratégia de Testes Obrigatória

### 3.1. Testes Unitários (Backend)
- **Tecnologia:** JUnit 5, Mockito.
- **Cenários Mandatórios:**
    - Validação de incremento automático da versão ao criar um novo prompt para um `code` existente.
    - Validação de que um novo prompt sempre nasce como `draft=true` e `active=false`.
    - Lógica de ativação: Garantir que o serviço desativa a versão anterior ao ativar a nova.
    - Erro ao tentar ativar uma versão que não pertence ao tenant solicitante.

### 3.2. Testes de Integração (Backend)
- **Tecnologia:** Testcontainers (PostgreSQL).
- **Cenários Mandatórios:**
    - Persistência completa de uma nova versão e verificação da sequência no banco de dados.
    - Teste de concorrência: Garantir que múltiplas requisições simultâneas de ativação mantenham apenas uma versão ativa (uso de travas pessimistas ou lógica de update atômico se necessário).
    - Busca de "Active Prompt": Verificar se o endpoint de busca por `code` retorna corretamente a única versão ativa para o tenant.

### 3.3. Testes de Frontend
- **Tecnologia:** Vitest / Vue Test Utils.
- **Cenários Mandatórios:**
    - Exibição da lista de versões para um determinado `code`.
    - Botão de "Ativar" deve estar desabilitado se a versão já for a ativa.
    - Feedback visual claro para versões `DRAFT`.

## 4. Critérios de Aceite

### 4.1. Backend
- [ ] Endpoint `POST /api/v1/prompts` cria nova versão com `draft=true`, `active=false` e `version` incrementado.
- [ ] Endpoint `PATCH /api/v1/prompts/{id}/activate` desativa a versão atual e ativa a informada (dentro do mesmo `tenantId` e `code`).
- [ ] Endpoint `GET /api/v1/prompts/active?code={code}` retorna a versão ativa atual.
- [ ] Migração Flyway (`DBUpdate`) criada para suportar os novos campos e índices de unicidade se necessário (ex: índice parcial para `active=true`).

### 4.2. Frontend
- [ ] Tela de listagem de prompts exibindo o `code` e permitindo expandir para ver o histórico de versões.
- [ ] Badge visual indicando se a versão é `DRAFT`, `ACTIVE` ou `ARCHIVED` (versões não ativas e não rascunho).
- [ ] Modal de confirmação ao ativar uma versão, informando que a anterior será desativada.
- [ ] Editor de prompt permitindo salvar como "Nova Versão" (Draft).
