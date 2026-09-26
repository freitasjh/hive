# ISSUE_007: MVP Frontend e Chat com Assistente

## 🎯 Objetivo
Implementar a interface inicial do usuário (Frontend) permitindo o cadastro de contas, login e uma interação básica de chat com o assistente de IA.

## 📋 Requisitos de Negócio
- O usuário deve ser capaz de criar uma nova conta (Tenant/User).
- O usuário deve ser capaz de realizar login para acessar o assistente.
- O usuário deve ter uma interface de chat intuitiva (inspirada em Gemini/ChatGPT).
- As mensagens do assistente devem ser processadas pelo backend Hive Agent.

## 🛠 Critérios de Aceite Técnicos

### Frontend (Vue 3 + PrimeVue)
- **Framework**: Vue 3 (Composition API com `<script setup lang="ts">`).
- **UI**: PrimeVue com o template Sakai (versão minimalista).
- **Login**: Componente de login integrado ao `POST /v1/auth/login`.
- **Cadastro**: Componente de registro integrado ao `POST /v1/user-accounts/create`.
- **Chat**: 
    - Topbar com menu simples (Home, Logout).
    - Área de histórico de mensagens com scroll automático.
    - Input de mensagem fixo no rodapé com suporte a 'Enter' para enviar.
    - Sanitização de XSS obrigatória (DOMPurify).
- **Estado**: Pinia para gerenciar o Token JWT e dados do usuário logado.

### Backend (Java/Spring Boot)
- **Controller**: Criar `ChatControllerV1` no módulo `agent/web`.
- **Service**: Implementar `ChatService` no módulo `agent/impl` integrando com `LangChain4jProvider`.
- **API**: Endpoint `POST /v1/agent/chat` recebendo `ChatMessageDTO` e retornando `ChatResponseDTO`.

## 🧪 Estratégia de Testes
- **Backend**: Testes unitários para `ChatService` e de integração para `ChatControllerV1`.
- **Frontend**: Testes unitários para os componentes de Login e Chat usando Vitest.

## 🚚 Definição de Pronto (DoD)
- [ ] Código implementado seguindo a stack definida.
- [ ] Testes unitários e de integração aprovados.
- [ ] Code Review com veredito `APPROVED`.
- [ ] Documentação técnica atualizada.
- [ ] Aceite do usuário confirmado.
