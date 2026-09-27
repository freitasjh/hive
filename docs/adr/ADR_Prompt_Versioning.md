# ADR: Prompt Versioning Architecture

## Status
Proposed

> ### ⚠️ Não implementada — anotado em 2026-09-27
>
> Este ADR está `Proposed` e **nunca foi construído**: não existe versionamento de prompt no código.
> O que existe é a coluna `version` e a resolução por `code`+`version`
> (`PromptTemplateRepository.findByTenantIdAndCodeAndVersion`), que é outra coisa e não implementa
> o modelo descrito aqui (níveis, `draft`/`active` por versão, histórico de transições, rollback).
>
> Vale registrar porque o resto da documentação do produto cita "versionamento de prompt" como
> capacidade existente, e ela não é. **Não está superseded** — está *não implementada*, que é
> diferente: a decisão continua defensável, só falta construção.


## Context
O sistema Hive requer um mecanismo robusto de versionamento de prompts para permitir experimentação e rollback sem impacto imediato nos agentes em execução. 
Foi identificada uma inconsistência entre a `PromptTemplateEntity` e o script de migração `V2__create_agent_tables.sql`, onde campos essenciais como `code`, `params`, `active` e `draft` foram omitidos no banco de dados.

## Decision
Adotaremos um modelo de versionamento baseado em `code` e `status`, onde cada prompt possui um identificador único de negócio (`code`) e múltiplas versões numéricas.

### 1. Database Schema (Flyway V3)
A migração `V3` deve:
- Adicionar `code` (VARCHAR) obrigatório.
- Adicionar `status` (VARCHAR) para gerenciar o ciclo de vida (DRAFT, ACTIVE, ARCHIVED).
- Adicionar `params` (text[]) para variáveis dinâmicas do prompt.
- Aplicar constraint de unicidade: `(tenant_id, code, version)`.
- Aplicar constraint de unicidade parcial: `(tenant_id, code)` onde `status = 'ACTIVE'` (apenas uma versão ativa por código).

```sql
ALTER TABLE prompt_template ADD COLUMN code VARCHAR(255) NOT NULL;
ALTER TABLE prompt_template ADD COLUMN status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL;
ALTER TABLE prompt_template ADD COLUMN params text[];

-- Constraint de unicidade por versão
ALTER TABLE prompt_template ADD CONSTRAINT uk_prompt_template_tenant_code_version UNIQUE (tenant_id, code, version);

-- Índice parcial para garantir apenas uma versão ACTIVE por code por tenant
CREATE UNIQUE INDEX uk_prompt_template_active_version 
ON prompt_template (tenant_id, code) 
WHERE status = 'ACTIVE';
```

### 2. Domain Model Refinement
- Criar o Enum `PromptStatus`: `DRAFT`, `ACTIVE`, `ARCHIVED`.
- Refatorar `PromptTemplateEntity` para remover `boolean active` e `boolean draft`, substituindo por `PromptStatus status`.

### 3. API Contracts (OpenAPI)

#### POST /v1/prompt-templates/{code}/draft
- **Objetivo**: Cria um novo rascunho. Se for baseado em um código existente, herda os parâmetros e o template da última versão, incrementando o número da versão.
- **Request Body**: `PromptTemplateDTO` (Opcional).
- **Response**: `PromptTemplateDTO` (Status: DRAFT).

#### PATCH /v1/prompt-templates/{id}/activate
- **Objetivo**: Ativa a versão informada.
- **Lógica**: 
    1. Busca a versão pelo ID.
    2. Altera o status da versão atualmente `ACTIVE` para `ARCHIVED` para aquele `code` e `tenant_id`.
    3. Altera o status da versão alvo para `ACTIVE`.
- **Response**: `200 OK` ou `204 No Content`.

#### GET /v1/prompt-templates/{code}/versions
- **Objetivo**: Lista todas as versões de um determinado `code`, ordenadas decrescentemente pela versão.
- **Response**: `List<PromptTemplateDTO>`.

## Consequences
- **Prós**: Ciclo de vida claro, facilidade de rollback, suporte a experimentação.
- **Contras**: Maior complexidade na lógica de criação (incremento de versão) e ativação (transação atômica).
