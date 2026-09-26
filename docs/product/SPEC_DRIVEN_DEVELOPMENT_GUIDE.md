# Guia: Spec-Driven Development (SDD) no Hive

Este guia formaliza o processo de **Desenvolvimento Orientado à Especificação** (SDD) do projeto Hive. O objetivo é garantir que toda implementação seja precedida por uma fase de design rigorosa, minimizando retrabalho e desalinhamentos entre agentes e humanos.

## 📐 O que é SDD?
No Hive, SDD significa que **a Especificação é a única fonte da verdade**. Nenhuma implementação (Backend ou Frontend) pode ser iniciada sem que os documentos de design técnico e requisitos estejam finalizados e aprovados.

---

## 🔄 Fluxo de Trabalho (Pipeline SDD)

### Fase 1: Descoberta e Requisitos (`docs/product/`)
- **Agente:** `@product-strategy-specialist`
- **Output:** `PRD_XXX.md` (Product Requirement Document).
- **Mandatário:** Deve conter casos de uso de IA, limites de tokens esperados e critérios de aceite técnicos.

### Fase 2: Decisões Arquiteturais (`docs/adr/`)
- **Agente:** `@architect`
- **Output:** `ADR_XXX.md` (Architecture Decision Record).
- **Mandatário:** Necessário apenas para mudanças estruturais (ex: novos providers de IA, novos módulos, mudanças de banco de dados).

### Fase 3: Especificação Técnica e Design (`docs/design/`)
- **Agentes:** `@architect` + `@ux_designer` + `@design_system_engineer`
- **Outputs:**
    *   `DESIGN_SPEC_XXX.md`: Define contratos de API, schemas de banco de dados (SQL e Vetorial) e lógica de prompts.
    *   Wireframes e contratos de componentes UI.

### Fase 4: Handoff e Implementação (`Fase de Construção`)
- **Agente:** Orquestrador
- **Ação:** O Orquestrador lê todas as specs geradas e delega para `@backend-specialist` e `@frontend-specialist`.
- **Regra:** O prompt de delegação **DEVE** referenciar os arquivos gerados nas fases 1, 2 e 3.

### Fase 5: Validação e Quality Gate
- **Agentes:** `@backend-code-reviewer`, `@frontend-code-reviewer`, `@ai-evaluator`
- **Ação:** A revisão de código é feita comparando a implementação **diretamente com a Spec**. Se a Spec diz que o campo é X e o código implementou Y, é reprovado imediatamente.

---

## 📜 Regras de Ouro do SDD

1.  **"Spec First, Code Later":** Se não há especificação escrita em `docs/`, não há tarefa de desenvolvimento.
2.  **"Handoff is Documentation":** O relatório de entrega de um agente deve ser um documento que descreve o que foi feito e como validar.
3.  **"Validation Against Spec":** Testes unitários e de integração devem cobrir 100% dos requisitos descritos na `PRD` e `DESIGN_SPEC`.
4.  **"Evals as Specs":** Critérios de qualidade de IA (precisão, tom de voz) são partes integrantes da especificação.

---

## 🛠️ Como Iniciar uma Nova Task
Para iniciar qualquer desenvolvimento no Hive, o Orquestrador deve seguir este script:
1.  Invocar o especialista de produto para gerar o PRD.
2.  Invocar o arquiteto para definir o design técnico.
3.  Só então, invocar os desenvolvedores para codificar.
