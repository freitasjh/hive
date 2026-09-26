# ISSUE_004: Implementação de Testes de Integração - Transações de Cartão de Crédito

## 1. Descrição
Implementar a suíte completa de testes de integração para o fluxo de transações de cartão de crédito, cobrindo criação de compras, parcelamento, faturamento e integração com o core financeiro (pagamento de faturas).

## 2. Requisitos Técnicos
- **Localização:** `backend/integration-test/src/test/java/br/com/systec/opusfinancial/financial/creditcard/`.
- **Tecnologias:** JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL).
- **Cenários de Sucesso (TC-01 a TC-06):**
  - Compra em parcela única.
  - Compra parcelada (12x).
  - Tratamento de arredondamento em dízimas (ex: 100/3).
  - Reuso de faturas existentes para o mesmo cartão/mês.
  - Pagamento de fatura com múltiplas parcelas.
  - Pagamento de fatura vazia.
- **Cenários de Erro (TC-07 a TC-10):**
  - Cartão inexistente.
  - Fatura inexistente.
  - Pagamento duplo de fatura.
  - Falta de conta bancária associada ao cartão.
- **Validações de Banco (Assertions):**
  - Verificar estados em `credit_card_transaction`, `credit_card_installment` e `credit_card_invoice`.
  - Validar criação de `financial_transaction` no core após pagamento da fatura.

## 3. Critérios de Aceite
- [ ] Todos os 10 casos de teste (TC-01 a TC-10) implementados e passando.
- [ ] Assertions rigorosas para integridade de dados entre tabelas.
- [ ] Uso de Testcontainers para banco de dados real nos testes.
- [ ] Mocking apenas para serviços externos (se houver); integração com Core Financeiro deve ser real ou via Spy.
- [ ] Execução bem-sucedida via `mvn integration-test -Pintegration`.

## 4. Definição de Pronto (DoD)
- [ ] Código de teste implementado e revisado.
- [ ] `mvn integration-test -Pintegration` aprovado no módulo de integração.
- [ ] Code Review pelo `@backend-code-reviewer` com veredito `APPROVED`.
- [ ] Sem violações das lições aprendidas em `LESSONS_LEARNED.md`.

## 5. Referências
- `docs/product/PRD_002_Credit_Card_Integration_Testing_Strategy.md`
- `LESSONS_LEARNED.md` (Atenção às lições sobre Maven e revision property).
