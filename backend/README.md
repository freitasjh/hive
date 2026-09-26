# 🤖 Hive - Multi-Agent System Backend

[![Java Version](https://img.shields.io/badge/Java-25--LTS-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Architecture](https://img.shields.io/badge/Architecture-Modular%20Monolith-blue?style=for-the-badge)](https://martinfowler.com/articles/modular-monolith.html)

O **Hive** é uma plataforma avançada para orquestração e execução de sistemas multi-agentes autônomos. Projetado para alta escalabilidade e segurança, o sistema utiliza motores de IA de última geração para gerenciar fluxos de trabalho complexos, bases de conhecimento vetoriais e interações em tempo real.

---

## 🏗 Arquitetura e Design de Domínio

O sistema adota uma abordagem de **Monolito Modular** fundamentada nos princípios de *Clean Architecture* e *Domain-Driven Design (DDD)*. Cada módulo é um contexto delimitado (*Bounded Context*) que garante baixo acoplamento e alta coesão.

### Estrutura de Módulos (Camadas)
Para manter o isolamento e a testabilidade, cada módulo é subdividido em:

*   **`API`**: Contratos de serviço, exceções de negócio e entidades de domínio (POJOs/Records). Camada agnóstica de tecnologia.
*   **`IMPL`**: Implementações de lógica de negócio, persistência JPA, integrações com Redis/RabbitMQ e Repositórios.
*   **`WEB`**: Controladores REST versionados (v1, v2) e Mappers de DTO.
*   **`DBUpdate`**: Gestão de migrações estruturais via **Flyway**, com histórico independente por módulo.

---

## 🛠 Stack Tecnológica

*   **Linguagem**: Java 25 (Utilizando *Project Leyden*, *Virtual Threads* e *Structured Concurrency*).
*   **Framework**: Spring Boot 4.0.3.
*   **IA & Agentes**: LangChain4j e LangGraph4j (Motor de orquestração de workflows).
*   **Persistência**: PostgreSQL 16 com extensão **pgvector** (Vector Store para RAG).
*   **Cache & Segurança**: Redis (Gestão de sessões, Blacklist de tokens e Rate Limiting).
*   **Mensageria**: RabbitMQ (Comunicação assíncrona e resiliência via DLQ).

---

## 🔒 Camada de Segurança (Security & Identity)

O Hive implementa defesas rigorosas baseadas no **OWASP Top 10**:

*   **Identificadores**: Uso mandatório de **UUID (v7)** como chaves primárias para prevenir IDOR.
*   **Isolamento Multi-tenant**: Validação estrita de `tenantId` em todas as camadas de persistência e cache.
*   **Gestão de Tokens (JWT + Redis)**:
    *   **Stateless with State**: Tokens JWT para autenticação, com validação de revogação em tempo real via **Blacklist no Redis**.
    *   **Refresh Token Rotation**: Rotação automática a cada uso, com validação de impressão digital (*Fingerprinting*) via `remoteIp` e `userAgent`.
    *   **Security Context**: Validação de contexto de rede para prevenir sequestro de sessão (*Session Hijacking*).

---

## 🚀 Funcionalidades Principais

1.  **Orquestração Multi-Agente**: Criação de agentes com habilidades específicas e workflows dinâmicos via LangGraph4j.
2.  **Base de Conhecimento (RAG)**: Ingestão de documentos e pesquisa semântica utilizando embeddings e bancos vetoriais.
3.  **Gestão de Inquilinos (Multi-tenancy)**: Isolamento completo de dados, configurações e modelos de IA por cliente.
4.  **Audit Trail**: Rastreabilidade completa de ações e execuções de agentes.

---

## 🚦 Como Iniciar

### Pré-requisitos
*   JDK 25 (LTS)
*   Maven 3.9+
*   Docker & Docker Compose

### Ambiente de Desenvolvimento
1.  Suba a infraestrutura base (Banco de Dados, Redis, RabbitMQ):
    ```bash
    docker-compose -f docker-compose-redis.yml up -d
    # Repita para postgres/rabbitmq se houver arquivos separados
    ```

2.  Compile e instale as dependências:
    ```bash
    mvn clean install
    ```

3.  Execute o bootstrap da aplicação:
    ```bash
    mvn spring-boot:run -pl bootstrap
    ```

---

## 🧪 Estratégia de Testes
Seguimos uma nomenclatura semântica obrigatória para garantir que os testes sirvam como documentação viva:
*   **Padrão**: `when_[acao]_with_[cenario]_then_[resultado]()`
*   **Tecnologias**: JUnit 5, Mockito e **Testcontainers** para testes de integração reais.

---
