import type { DashboardViewId } from "@/components/shadcn/navigation"

/**
 * Conteúdo estático da visão geral do Hive.
 *
 * Fonte dos dados (somente leitura, sem chamadas de rede):
 * - backend/pom.xml:16-27 — módulos Maven
 * - backend/README.md:25-32 — linguagem, framework, persistência, cache e mensageria
 * - backend/dependencies/pom.xml:19-25 — versões de Java e Spring Boot
 */

export const VIEW_LABELS: Record<DashboardViewId, string> = {
  overview: "Visão geral",
  login: "Entrar",
  providers: "Provedores e credenciais",
  agents: "Config LLM",
  chat: "Assistant",
  prompts: "Prompts",
}

export const HIVE_OVERVIEW_COPY = {
  title: "Visão geral do Hive",
  subtitle:
    "Orquestração de sistemas multi-agentes autônomos em um monolito modular.",
  modulesTitle: "Módulos do backend",
  modulesDescription: "Contextos delimitados declarados no projeto Maven.",
  stackTitle: "Stack tecnológica",
  stackDescription: "Linguagem, framework, persistência, cache e mensageria.",
  placeholderTitle: "Em preparação",
  placeholderDescription:
    "Esta área está reservada para uma entrega futura. Volte à visão geral para consultar as informações do Hive.",
  placeholderDetail: ": conteúdo não incluído nesta entrega.",
  backLabel: "Voltar à visão geral",
} as const

export interface HiveMetric {
  label: string
  value: string
  detail: string
}

export const HIVE_METRICS: readonly HiveMetric[] = [
  { label: "Projeto", value: "Hive", detail: "HIVE AI" },
  { label: "Arquitetura", value: "Monolito modular", detail: "Clean Architecture + DDD" },
  { label: "Módulos", value: "9", detail: "contextos delimitados" },
  { label: "Stack", value: "5", detail: "tecnologias principais" },
]

export const HIVE_MODULES: readonly string[] = [
  "tenant",
  "commons",
  "bootstrap",
  "identity",
  "security",
  "agent",
  "provider",
  "publicapi",
  "integration-test",
]

export const HIVE_STACK: readonly string[] = [
  "Java 25",
  "Spring Boot 4.1.0",
  "PostgreSQL 16 + pgvector",
  "Redis",
  "RabbitMQ",
]
