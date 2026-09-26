# ISSUE_005: Implementação de Projeção Virtual - Transações de Cartão de Crédito

## 📋 Descrição
Migrar a lógica de criação de parcelas de cartão de crédito de "Geração Antecipada Total" para "Projeção Virtual (Just-In-Time)". 
O sistema deve parar de criar registros físicos para parcelas futuras no momento da compra e passar a calculá-las dinamicamente para exibição, persistindo-as apenas quando a fatura correspondente for aberta/fechada.

## 🎯 Critérios de Aceite
1.  **Backend (Refatoração)**:
    - O `CreditCardTransactionServiceImpl.create` não deve mais iterar e criar N parcelas no banco.
    - Implementar `CreditCardProjectionService` com o endpoint `GET /api/credit-cards/{id}/projection`.
    - Garantir que o cálculo de parcelas futuras considere o dia de fechamento do cartão (Melhor Dia de Compra).
2.  **Backend (Persistência)**:
    - Implementar um Job ou serviço de gatilho para materializar a próxima parcela física na fatura quando a atual fechar.
3.  **Frontend**:
    - O Dashboard e o Extrato devem consumir a API de Projeção para exibir meses futuros.
4.  **Qualidade**:
    - Testes unitários para o motor de projeção (casos de borda: meses curtos, virada de ano).
    - Teste de integração para o fluxo de compra -> projeção -> fechamento de fatura.
5.  **Documentação**:
    - Atualizar a documentação técnica se necessário.

## 🛠️ Sub-tarefas
- [ ] Refatorar `CreditCardTransactionServiceImpl.java` (remover loop de parcelas).
- [ ] Criar `CreditCardProjectionServiceImpl.java` (lógica de cálculo dinâmico).
- [ ] Implementar endpoint REST de projeção.
- [ ] Ajustar o `CreditCardInvoiceService` para lidar com materialização sob demanda.
- [ ] Atualizar o Frontend (Store e Views) para usar a nova API.
- [ ] Criar script de limpeza para parcelas órfãs antigas (Opcional/Segurança).

## 📅 Status
- **Status**: To Do
- **Prioridade**: Alta
- **Referência**: PRD_004, ADR_002
