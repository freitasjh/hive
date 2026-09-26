# ISSUE_003: Integração do Frontend - Dashboard de Cartão de Crédito

## 1. Descrição
Ajustar o `CreditCardDashboardService.ts` para consumir os endpoints reais do backend, passando o `userId` necessário e alinhando as interfaces de dados com os novos DTOs.

## 2. Requisitos Técnicos
- **Service:** Atualizar `frontend/src/service/financial/CreditCardDashboardService.ts`.
  - Adicionar parâmetro `userId: string` aos métodos `getSummary` e `getProjections`.
  - Ajustar interfaces para bater com o Backend DTO:
    - `CreditCardDetails` deve ter `currentInvoiceAmount` (ou mapear `openInvoiceAmount` para este campo).
- **View:** Atualizar `frontend/src/views/financial/CreditCardDashboard.vue`.
  - Obter `userId` do `localStorage` ou `authStore` e passar para o serviço.
  - Garantir que o `formatCurrency` está sendo usado corretamente.
- **I18n:** Verificar se todas as chaves de tradução usadas na View existem em `frontend/src/assets/i18n/`.

## 3. Critérios de Aceite
- [ ] Chamadas de API enviando `userId` como query param.
- [ ] Dashboard carregando dados reais (integrado com o backend após este ser finalizado).
- [ ] Estado de loading e erro funcionando corretamente.
- [ ] Testes unitários do Service atualizados e passando.

## 4. Definição de Pronto (DoD)
- [ ] Código implementado e revisado.
- [ ] `npm run test` aprovado no frontend.
- [ ] Code Review pelo `@frontend-code-reviewer` com veredito `APPROVED`.
- [ ] Validação visual básica seguindo o `style-guide.md`.

## 5. Referências
- `docs/product/PRD_003_Credit_Card_Dashboard.md`
- `frontend/src/service/financial/CreditCardDashboardService.ts`
- `frontend/src/views/financial/CreditCardDashboard.vue`
