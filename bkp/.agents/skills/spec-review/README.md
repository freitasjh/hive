# Skill: Spec Review — Hive AI

**Revisor de Specifications Hive AI** — Valida conformidade de specs com as regras de DDD Modular, padrões Maven multi-module, Vue 3 + PrimeVue 4, identifica gaps e gera plano de ação.

---

## Propósito

Esta skill foi criada para garantir que todas as specifications criadas no projeto **Hive AI** (Spring Boot 4.1 + Java 21 + Vue 3.5 + PrimeVue 4) seguem as regras de arquitetura, segurança e qualidade definidas no repositório antes da implementação.

**Objetivos:**
- Validar conformidade com regras canônicas (`.agents/rules/`)
- Identificar gaps por severidade
- Gerar feedback estruturado
- Preparar plano de ação
- Prevenir retrabalho durante implementação

---

## Estrutura

```
.agents/skills/spec-review/
├── SKILL.md                    # Definição e procedure da skill (adaptada Hive AI)
├── README.md                   # Este arquivo
└── assets/
    ├── checklist.md            # Checklist de validação Hive AI (50+ itens)
    ├── output-template.md      # Template de relatório (adaptado Hive AI)
    └── usage-guide.md          # Guia de uso (adaptado Hive AI)
```

---

## Quick Start

### Invocar a Skill

```
Revise a specification em .spec/backend/agent/ seguindo as regras do projeto.

Revise .spec/backend/agent/SPEC.md:
- Verifique conformidade com DDD Modular e padrões Maven multi-module
- Identifique gaps por severidade
- Gere um plano de ação detalhado
```

### Saída Esperada

```
Relatório com:
- Scorecard de conformidade (%)
- Gaps críticos (bloqueadores)
- Gaps de alta prioridade
- Recomendações (média/baixa)
- Pontos positivos
- Plano de ação estruturado
```

---

## O que a Skill Valida

### Estrutura (Forma)

- [ ] Título claro
- [ ] Descrição e escopo definidos
- [ ] Domínio e camada identificados
- [ ] Dependências entre módulos listadas
- [ ] Riscos mapeados

### Requisitos Funcionais (Conteúdo)

- [ ] User stories no formato padrão
- [ ] Critérios de aceite verificáveis
- [ ] Regras de negócio descritas
- [ ] Tratamento de erros especificado
- [ ] Edge cases documentados

### DDD Modular (Backend)

- [ ] Módulos seguem estrutura `api/` → `impl/` → `dbupdate/` → `web/`
- [ ] `impl` NUNCA depende de `impl` de outro módulo — só via `api`
- [ ] Camada `web` usa DTOs, nunca expõe entidades JPA
- [ ] Repository: estende `JpaRepository<Entity, UUID>` diretamente
- [ ] Service Layer com interface na `api`, implementação na `impl`
- [ ] VOs de domínio (não entidades) na camada de serviço
- [ ] `@HasPermission("PERMISSION_NAME")` em métodos de controller
- [ ] Controllers usam `RestControllerTool.of().buildSuccessResponse()`

### Flyway Migrations

- [ ] SQL em `{module}/dbupdate/src/main/resources/db/migration/{module}/`
- [ ] Naming `V{N}__{description}.sql` (começa em V2)
- [ ] Flyway bean + history table própria por módulo (`flyway_schema_history_{module}`)
- [ ] NUNCA alterar migrations já executadas — nova migration sempre

### Frontend (Vue 3 + PrimeVue 4)

- [ ] Composition API `<script setup lang="ts">`
- [ ] Stores Pinia (setup function)
- [ ] Services em `src/services/*.service.ts` (Axios baseURL `/hive/v1`)
- [ ] Componentes seguem padrão `TheTopbar`/`TheSidebar`/`MainLayout`
- [ ] Theming PrimeVue Aura + dark mode `.p-dark`
- [ ] Ícones apenas PrimeIcons (`pi pi-*`)
- [ ] CSS scoped nos componentes

### Testes

- [ ] Unitários: JUnit 5 + Mockito, padrão `@ExtendWith(MockitoExtension.class)`
- [ ] Fakes em `src/test/java/.../fake/`
- [ ] Naming: `when_{action}_with_{scenario}_then_{result}()`
- [ ] AssertJ preferencial, JUnit assertions aceitáveis
- [ ] Integração: `*IT.java` no módulo `integration-test/`
- [ ] PostgreSQL 16 via Testcontainers, MockMvc (NÃO RestAssured)
- [ ] `DatabaseCleaner.reset()` + `JwtTestHelper`
- [ ] Controllers testados APENAS via integração
- [ ] Cobertura: 80% service, 90% domain
- [ ] Sem `@SpringBootTest` em unitários, sem H2, sem Lombok

### Segurança

- [ ] JWT Bearer token via header `Authorization`
- [ ] `@HasPermission("PERMISSION_NAME")` em cada método de controller
- [ ] Validação de entrada com `@Valid` nos DTOs
- [ ] `MethodArgumentNotValidException` → 400 BAD_REQUEST (RFC 7807)
- [ ] Sem dados sensíveis em logs

### Performance

- [ ] Coleções pré-dimensionadas (`new ArrayList<>(size)`)
- [ ] Sem Streams em caminhos críticos (segurança, filtros HTTP)
- [ ] Mapeamento via MapStruct (`@Mapper(componentModel = "spring")`)

### Convenções

- [ ] Nomes em inglês (classes, métodos, variáveis), mensagens UI em PT-BR
- [ ] Zero comentários no código
- [ ] Nomenclatura: `*ServiceImpl`, `*ControllerV1`, `*Mapper`, `*DTO`
- [ ] Testes: `*Test.java` (unit), `*IT.java` (integration)
- [ ] Lombok NÃO usado
- [ ] Injeção por construtor (nunca `@Autowired` em campo)

---

## Severidades de Gaps

| Severidade | Símbolo | Bloqueador | Prazo | Exemplo |
|-----------|---------|-----------|-------|---------|
| **Crítica** | 🔴 | SIM | Imediato | Violação de dependência entre módulos DDD |
| **Alta** | 🟠 | SIM | Sprint atual | Falta de `@HasPermission` em controller |
| **Média** | 🟡 | NÃO | Próxima sprint | Nomenclatura inconsistente |
| **Baixa** | 🟢 | NÃO | Opcional | Métricas de performance na spec |

---

## Assets Incluídos

### 1. `SKILL.md` (Definição)

Descreve o propósito, procedure e referências da skill adaptada para Hive AI.

**Seções:**
- When to Use (quando invocar)
- Procedure (passos detalhados)
- Regras canônicas a verificar
- Referências consultadas

### 2. `assets/checklist.md` (50+ Itens)

Checklist completo adaptado para Hive AI.

**Categorias:**
1. Estrutura (9 itens)
2. Requisitos Funcionais (6 itens)
3. DDD Modular (10 itens)
4. Flyway (5 itens)
5. Frontend Vue 3 (10 itens)
6. Testes Backend (9 itens)
7. Segurança (6 itens)
8. Performance (4 itens)
9. Convenções (6 itens)

### 3. `assets/output-template.md` (Relatório Estruturado)

Template profissional para gerar relatórios de revisão.

**Seções:**
- Resumo executivo com scorecard
- Gaps críticos (com exemplos de correção)
- Gaps de alta/média/baixa prioridade
- Pontos positivos
- Plano de ação com prazos

### 4. `assets/usage-guide.md` (Guia Prático)

Guia de uso com exemplos práticos e dicas.

---

## Regras Canônicas Consultadas

A skill consulta **sempre** as seguintes regras:

### Obrigatórias (Todas as Specs)

- `.agents/rules/backend-coding-standards.md` — DDD modular, performance
- `.agents/rules/frontend-coding-standards.md` — Vue 3, Pinia, componentes
- `.agents/rules/workflow-rules.md` — Regras gerais de workflow

### Conforme Tipo de Spec

- **Backend:** `backend-coding-standards.md`, `backend-unit-tests.md`, `backend-integration-tests.md`
- **Frontend:** `frontend-coding-standards.md`
- **Flyway:** `db-migration-flyway.md`
### Documentos de Referência

- `AGENTS.md` — Visão geral do projeto e comandos
- `.agents/rules/` — Regras canônicas do projeto

---

## Workflow Completo

```
1. SPEC é Criada (SDD Compiler)
   └── sdd-compiler generate-spec → SPEC.md
   ↓
2. Invoke spec-review nos arquivos
   ├── Lê a specification
   ├── Executa checklist (50+ itens)
   ├── Cruza com regras canônicas
   ├── Classifica gaps por severidade
   └── Gera relatório
   ↓
3. Relatório com Gaps
   ├── 🔴 Críticos (bloqueadores)
   ├── 🟠 Altos
   ├── 🟡 Médios
   └── 🟢 Baixos
   ↓
4. Autor Corrige Críticos
   ├── Atualiza spec
   └── Re-invoca spec-review
   ↓
5. Spec Aprovada
   ├── sdd-compiler generate-plan → PLAN.md
   ├── sdd-compiler generate-tasks → TASKS.md
   ├── Implementação via tasks
   └── Code review (fullstack-code-reviewer)
   ↓
6. QA + Merge
```

---

## Dicas de Uso

### Use spec-review quando:
- Spec foi criada e precisa ser revisada antes da implementação
- Quer validar conformidade com DDD Modular e boas práticas Hive AI
- Precisa gerar feedback estruturado para o autor
- Quer criar um plano de ação para correções

### Não use quando:
- Quer revisar código implementado (use subagent `fullstack-code-reviewer`)
- Quer validar testes (use subagent `qa-engineer`)
- Quer análise profunda de segurança (use subagent `security-engineer`)

### Boas práticas:
1. **Faça iterações:** v0.1 → Review → v0.9 → Review → v1.0
2. **Endereçe críticos primeiro:** Bloqueiam aprovação
3. **Use plano de ação:** Quebra tasks de forma organizada
4. **Compartilhe relatório:** Com autor e stakeholders
5. **Re-revise após mudanças:** Mudanças significativas = novo review

---

## Benefícios

| Benefício | Descrição |
|-----------|-----------|
| **Qualidade** | Specs validadas contra 50+ critérios Hive AI |
| **Arquitetura** | Garante DDD Modular + padrões Maven multi-module |
| **Consistência** | Estrutura api/impl/web, padrões Vue 3 + PrimeVue |
| **Eficiência** | Menos retrabalho durante implementação |
| **Rastreabilidade** | Plano de ação estruturado |

---

## Histórico de Versões

| Versão | Data | Mudanças |
|--------|------|----------|
| v1.0 | 2026-06-17 | Versão inicial (PDV Java/JavaFX) |
| v2.0 | 2026-06-20 | Adaptado para MedFlow (DDD Modular, Vue 3) |
| v2.1 | 2026-07-17 | Adaptado para Hive AI (multi-agent, Flyway, @HasPermission) |

---

_Skill `spec-review` v2.1 — Revisor de Specifications Hive AI_
