# Checklist de Revisão de Specification — Hive AI

Use este checklist ao revisar specifications (SPEC.md). Para cada item, marque:
- Conforme / Presente
- Ponto de atenção / Incompleto
- Não conforme / Ausente
- N/A Não aplicável

---

## 1. Estrutura e Documentação da Specification

| Item | Status | Comentário |
|------|--------|-----------|
| **Título claro e descritivo** | [ ] | Descreve o objetivo da feature |
| **Visão geral (Overview)** | [ ] | Contextualiza o problema a resolver |
| **Escopo definido (Incluso/Excluído)** | [ ] | Deixa claro o que faz e não faz parte |
| **Domínio afetado identificado** | [ ] | agent / identity / tenant / security / provider / commons |
| **Camada afetada identificada** | [ ] | api / impl / dbupdate / web / frontend |
| **Dependências entre módulos** | [ ] | Quais módulos `api` são necessários |
| **Requisitos não-funcionais** | [ ] | Performance, segurança |
| **Riscos identificados** | [ ] | Riscos técnicos e mitigações planejadas |
| **Data de criação / Versão** | [ ] | Rastreabilidade de quando foi criada |

---

## 2. Requisitos Funcionais

| Item | Status | Comentário |
|------|--------|-----------|
| **User stories claras** | [ ] | Formato "Como [ator], eu quero [ação], para [benefício]" |
| **Critérios de aceite verificáveis** | [ ] | Condições objetivas para completude |
| **Casos de uso documentados** | [ ] | Fluxos principais e alternativos |
| **Regras de negócio descritas** | [ ] | Validações, restrições, cálculos |
| **Fluxo de erro tratado** | [ ] | Como o sistema responde a exceções |
| **Cenários edge case** | [ ] | Limites, casos extremos |

---

## 3. DDD Modular (Backend)

### 3.1 Arquitetura e Estrutura

| Item | Status | Comentário |
|------|--------|-----------|
| **Módulos seguem estrutura api/impl/dbupdate/web** | [ ] | api (interfaces/model), impl (services/repos), web (controllers/DTOs) |
| **impl NÃO depende de impl de outro módulo** | [ ] | Só via api (interfaces) |
| **Camada web usa DTOs, nunca entidades JPA** | [ ] | Controllers recebem/devolvem DTOs |
| **Repository Pattern** | [ ] | Estende `JpaRepository<Entity, UUID>` diretamente |
| **Service Layer** | [ ] | Interfaces na `api`, implementações na `impl` |
| **VOs de domínio (não entidades) no service** | [ ] | Mappers convertem domain ↔ entity |
| **@HasPermission("PERMISSION_NAME")** | [ ] | Controle de acesso por permissão |
| **Controllers usam RestControllerTool** | [ ] | `RestControllerTool.of().buildSuccessResponse()` |
| **Pré-dimensionamento de coleções** | [ ] | `new ArrayList<>(size)` |
| **Sem Streams em caminhos críticos** | [ ] | Segurança, filtros HTTP |

### 3.2 Performance

| Item | Status | Comentário |
|------|--------|-----------|
| **Coleções pré-dimensionadas** | [ ] | Tamanho inicial definido |
| **Mapeamento via MapStruct** | [ ] | `@Mapper(componentModel = "spring")`, sem reflection manual |
| **Sem Streams em segurança/filtros** | [ ] | Usar loops for-each ou indexados |

---

## 4. Flyway Migrations

| Item | Status | Comentário |
|------|--------|-----------|
| **SQL em {module}/dbupdate/.../db/migration/{module}/** | [ ] | Caminho padrão do módulo dbupdate |
| **Naming V{N}__{description}.sql** | [ ] | Começa em V2; history table própria por módulo |
| **NUNCA alterar migrations já executadas** | [ ] | Criar nova migration sempre |
| **Flyway bean + history table por módulo** | [ ] | `flyway_schema_history_{module}` |

---

## 5. Frontend (Vue 3 + PrimeVue 4)

### 5.1 Estrutura e Estilo

| Item | Status | Comentário |
|------|--------|-----------|
| **Composition API com `<script setup lang="ts">`** | [ ] | Formato obrigatório |
| **Componentes com CSS scoped** | [ ] | style.css só para variáveis globais |
| **Path aliases conforme vite.config.ts** | [ ] | Não inventar aliases |
| **API baseURL `/hive/v1`** | [ ] | Proxy Vite `/hive` → localhost:8080/hive |

### 5.2 Estado e Dados

| Item | Status | Comentário |
|------|--------|-----------|
| **Stores Pinia (setup function)** | [ ] | Getters/actions, estado local no componente |
| **Services em src/services/*.service.ts** | [ ] | Wrappers da instância Axios `api.ts` |
| **Interceptor Axios** | [ ] | Bearer token do localStorage, redirect em 401 |
| **Tipos TS em src/types/** | [ ] | Interfaces/tipos do domínio |
| **Ícones PrimeIcons (`pi pi-*`)** | [ ] | Não usar mdi-* / ri-* |
| **Theming PrimeVue Aura** | [ ] | Deep Indigo + Amber, dark mode `.p-dark` |

### 5.3 Testes Frontend

| Item | Status | Comentário |
|------|--------|-----------|
| **Vitest + jsdom** | [ ] | Configurado em vite.config.ts |
| **Stores/componentes testados** | [ ] | Actions, getters e renderização |
| **Cobertura mínima 70%** | [ ] | Nas novas funções/componentes |

---

## 6. Testes Backend

| Item | Status | Comentário |
|------|--------|-----------|
| **Estratégia: unitários + integração** | [ ] | Ambos obrigatórios |
| **Unitários: JUnit 5 + Mockito** | [ ] | @ExtendWith(MockitoExtension.class) |
| **Naming: when_{action}_with_{scenario}_then_{result}()** | [ ] | snake_case descritivo |
| **Fakes em src/test/java/.../fake/** | [ ] | Dados de teste isolados |
| **Integração: *IT.java no module integration-test** | [ ] | PostgreSQL 16 via Testcontainers |
| **MockMvc (NÃO RestAssured)** | [ ] | @AutoConfigureMockMvc (spring-boot-webmvc-test) |
| **DatabaseCleaner.reset() para limpeza** | [ ] | TRUNCATE ... CASCADE entre testes |
| **JwtTestHelper para autenticação** | [ ] | Sem segredo hardcoded |
| **Cobertura 80% service, 90% domain** | [ ] | Mínima obrigatória |

---

## 7. Segurança

| Item | Status | Comentário |
|------|--------|-----------|
| **JWT Bearer token** | [ ] | Header Authorization |
| **@HasPermission("PERMISSION_NAME")** | [ ] | Em cada método de controller |
| **Validação de entrada em todas as camadas** | [ ] | @Valid, Bean Validation |
| **Sem dados sensíveis em logs** | [ ] | Senhas, tokens, PII |
| **Erros RFC 7807 (application/problem+json)** | [ ] | ControllerExceptionHandler global |
| **SQL Injection prevenido** | [ ] | JPA PreparedStatement |

---

## 8. Performance

| Item | Status | Comentário |
|------|--------|-----------|
| **Pré-dimensionamento de coleções** | [ ] | ArrayList, HashSet com tamanho inicial |
| **Sem Streams em caminhos críticos** | [ ] | Segurança, autenticação, filtros |
| **Mapeamento via MapStruct** | [ ] | `@Mapper(componentModel = "spring")` |

---

## 9. Convenções de Código

| Item | Status | Comentário |
|------|--------|-----------|
| **Nomes de classes em inglês** | [ ] | Service, Repository, Controller |
| **Mensagens UI em PT-BR** | [ ] | Labels, alerts, toasts |
| **Zero comentários no código** | [ ] | Código auto-documentado |
| **Nomenclatura consistente** | [ ] | *ServiceImpl, *Mapper, *ControllerV1, *DTO |
| **Testes: *Test.java (unit) / *IT.java (integration)** | [ ] | Padrão de nomenclatura |
| **Lombok NÃO usado** | [ ] | Proibido no projeto |
| **Injeção por construtor (nunca @Autowired campo)** | [ ] | Spring recomenda |

---

## Resumo de Status

```
Itens Conformes:       [ ] / [ ]
Itens com Atenção:     [ ] / [ ]
Itens Não-Conformes:   [ ] / [ ]
N/A (Não aplicável):   [ ] / [ ]
```

**Percentual de Conformidade:** `[ ]%`

---

## Referências Consultadas

Regras canônicas verificadas durante a análise:
- [ ] `.agents/rules/backend-coding-standards.md`
- [ ] `.agents/rules/frontend-coding-standards.md`
- [ ] `.agents/rules/db-migration-flyway.md`
- [ ] `.agents/rules/backend-unit-tests.md`
- [ ] `.agents/rules/backend-integration-tests.md`
- [ ] `.agents/rules/workflow-rules.md`
- [ ] `AGENTS.md`
