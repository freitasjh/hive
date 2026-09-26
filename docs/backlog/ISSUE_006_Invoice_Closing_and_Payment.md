# ISSUE_006: Fluxo de Fechamento e Pagamento de Fatura de Cartão

## 📋 Descrição
Implementar o processo de fechamento de fatura de cartão de crédito. Este processo deve:
1.  Alterar o status da fatura de `OPEN` para `CLOSED`.
2.  Realizar a "Materialização" da próxima parcela para todas as transações ativas (complemento da ISSUE_005).
3.  Permitir o registro do pagamento da fatura (total ou parcial).

## 🎯 Critérios de Aceite
1.  **Backend**:
    - Endpoint para fechar fatura: `POST /api/v1/credit-cards/{id}/invoices/{invoiceId}/close`.
    - Ao fechar, o sistema deve buscar todas as `CreditCardTransaction` ativas e criar o primeiro registro de `CreditCardInstallment` para a nova fatura aberta.
    - Endpoint para pagar fatura: `POST /api/v1/credit-cards/{id}/invoices/{invoiceId}/pay`.
2.  **Integração Core**:
    - O pagamento da fatura deve gerar uma transação de saída (`ExpenseTransaction`) no Core financeiro, vinculada a uma conta bancária.
3.  **Qualidade**:
    - Testes de integração para o ciclo de vida da fatura (Aberto -> Fechado -> Pago).
    - Validação de que a projeção virtual reflete a materialização após o fechamento.

## 🛠️ Sub-tarefas
- [ ] Implementar lógica de materialização de parcelas no `CreditCardInvoiceService`.
- [ ] Criar endpoints de fechamento e pagamento no Controller.
- [ ] Integrar com `FinancialCoreService` para gerar lançamento de despesa no pagamento.
- [ ] Criar interface no Frontend para "Fechar" e "Pagar" na lista de faturas.

## 📅 Status
- **Status**: To Do
- **Prioridade**: Alta
- **Referência**: ADR_002, PRD_004
