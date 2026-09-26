# ISSUE_002: Implementação do Backend - Dashboard de Cartão de Crédito

## 1. Descrição
Implementar a camada de backend (Controller, Service, Repository e DTOs) para o Dashboard de Cartão de Crédito, permitindo a visualização consolidada de limites e projeções de faturas futuras.

## 2. Requisitos Técnicos
- **Pacote Base:** `br.com.systec.opusfinancial.financial.creditcard` (Atenção: Corrigir o prefixo `br.com.systec` em relação ao Handoff original).
- **DTOs:** Implementar como `Java Records` em `backend/financial/credit-card/api/`.
  - `DashboardSummaryDTO`
  - `CreditCardDetailsDTO` (Incluir campo `creditLimit: BigDecimal` para alinhar com UI)
  - `MonthlyProjectionDTO`
- **Controller:** `CreditCardDashboardController` em `backend/financial/credit-card/web/`.
  - Endpoint: `GET /api/v1/credit-cards/dashboard/summary`
  - Endpoint: `GET /api/v1/credit-cards/dashboard/projections`
- **Service:** Interface em `api` e implementação em `impl`.
- **Performance:** Proibido consultas N+1. Utilizar Projeções de Construtor DTO no Repository.
- **Tecnologias:** Java 21, Spring Boot 3.4+, Spring Data JPA.

## 3. Critérios de Aceite
- [ ] DTOs implementados como Records.
- [ ] Controller seguindo os contratos do `HANDOFF_REPORT_003`.
- [ ] Service implementando a lógica de agregação de limites.
- [ ] Repository utilizando JPQL com `new DTO(...)` para evitar N+1.
- [ ] Testes unitários para o Service (Mocking Repositories).
- [ ] Testes de integração para os Endpoints (usando Testcontainers).
- [ ] Documentação OpenAPI (Swagger) ativa para os novos endpoints.

## 4. Definição de Pronto (DoD)
- [ ] Código implementado e revisado.
- [ ] `mvn clean test` aprovado no módulo.
- [ ] `mvn integration-test -Pintegration` aprovado.
- [ ] Code Review pelo `@backend-code-reviewer` com veredito `APPROVED`.
- [ ] Sem violações das lições aprendidas em `LESSONS_LEARNED.md`.

## 5. Referências
- `docs/product/PRD_003_Credit_Card_Dashboard.md`
- `docs/product/HANDOFF_REPORT_003_Credit_Card_Dashboard.md`
- `LESSONS_LEARNED.md`
