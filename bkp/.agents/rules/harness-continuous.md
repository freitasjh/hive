# Harness Contínuo (NON-NEGOTIABLE)

> 🚨 **REGRA MÁXIMA DO PROJETO.** Esta é a **fonte da verdade** do harness de qualidade do Hive AI. Substitui e complementa `workflow-rules.md`, `backend-unit-tests.md`, `backend-integration-tests.md` e `frontend-coding-standards.md` em tudo que se refere a **validação funcional e qualidade ponta-a-ponta**.
>
> **Constatação (2026-09-02):** mesmo com regras existentes, o sistema vem sendo entregue quebrado (telas mortas, lógica não funcional, débitos de teste acumulados). As regras a seguir corrigem esse desvio.

---

## Índice

- [H0. Princípios Invioláveis](#h0-princípios-invioláveis)
- [H1. Suite Completa em Todo Desenvolvimento](#h1-suite-completa-em-todo-desenvolvimento)
- [H2. Zero Tolerância a Falhas Pré-existentes](#h2-zero-tolerância-a-falhas-pré-existentes)
- [H3. Harness E2E Greenfield (Wipe + Re-cadastro)](#h3-harness-e2e-greenfield-wipe--re-cadastro)
- [H4. Avaliação Contínua (Telas, Regras, Melhorias)](#h4-avaliação-contínua-telas-regras-melhorias)
- [H5. Encerramento Limpo + Brain Obrigatório](#h5-encerramento-limpo--brain-obrigatório)
- [H6. Portão de Conclusão Expandido (7 itens)](#h6-portão-de-conclusão-expandido-7-itens)
- [H7. Cadência de Execução](#h7-cadência-de-execução)
- [Anexo A. Comandos Canônicos](#anexo-a-comandos-canônicos)
- [Anexo B. Template de Relatório de Harness](#anexo-b-template-de-relatório-de-harness)

---

## H0. Princípios Invioláveis

1. **Testes não são débito.** Um teste falhando é um defeito do produto, não do teste.
2. **Suite completa roda sempre.** Não importa se o dev mexeu em 1 linha — suite completa roda antes de declarar done.
3. **Funciona na mão, não só no CI.** Harness E2E com DB real + browser real é obrigatório em pontos de virada.
4. **Frontend é sistema.** Tela quebrada = sistema quebrado. Sem exceção.
5. **Harness precede orgulho.** Antes de declarar "implementação completa", provar funcionamento ponta-a-ponta.
6. **Regressão é assassina de release.** Qualquer teste que quebrava antes e quebra agora = bloqueia até root cause + fix.

---

## H1. Suite Completa em Todo Desenvolvimento

> 🚨 **NOTA OBRIGATÓRIA (2026-09-26) — o perfil `integration` DESLIGA o surefire.**
> `backend/pom.xml` define `<skip>true</skip>` no `maven-surefire-plugin` dentro do perfil
> `integration`. Portanto **`mvn clean install -Pintegration` roda ZERO testes unitários** e pode
> terminar "BUILD SUCCESS" sem nenhum teste executado. **Nunca** use esse comando sozinho como
> gate de suite completa.
>
> Sequência correta (a que realmente executa tudo):
> ```bash
> docker compose -f docker/docker-compose-vetor.yml up -d
> mvn clean install -DskipTests                        # build completo
> mvn test                                             # unit (reactor inteiro)
> mvn verify -pl integration-test -Pintegration -am     # integração (Testcontainers)
> ```
>
> **`-am` é obrigatório** no comando de integração: sem ele o Maven pode consumir jars
> `stale` do `~/.m2` em vez dos freshly-built e "passar" com código antigo.


> Substitui `workflow-rules.md` seção 5.4 e a frase "falhas pré-existentes toleradas" da seção 4 fase 4.

### H1.1 — Gatilhos (rodar suite completa)

A suite completa de testes **DEVE** ser executada **antes** de declarar qualquer um destes estados:

| Estado | Comando mínimo |
|--------|----------------|
| Task concluída | `mvn test` + `mvn verify -pl integration-test -Pintegration -am` |
| Sub-fase SDD concluída | `mvn clean install -DskipTests` + os dois acima |
| Bug corrigido | idem |
| PR aberto / commit de merge | suite completa (3 comandos, ver nota de H1) |
| Fim de sprint / release | idem + harness E2E (H3) |

### H1.2 — Comportamento diante de falha

Qualquer teste falhando — **novo, existente, pré-existente** — dispara protocolo obrigatório:

```
1. PARAR imediatamente o fluxo do dev.
2. Reportar falha com: classe:linha, mensagem completa, stack trace, suíte.
3. Diagnosticar causa raiz (R3 do workflow-rules.md).
4. Corrigir o teste OU corrigir o código (whichever resolve a regressão).
5. Re-rodar a suite completa. Não avançar enquanto existir falha.
6. Registrar no Brain: brain_store("regras" | "estudos", "hive/<area>/<causa>", ..., scope="projetos"|"global").
```

### H1.3 — Comando canônico de suite (3 comandos)

```bash
# Backend — unit + integration (Docker up antes)
docker compose -f docker/docker-compose-vetor.yml up -d
mvn clean install -DskipTests
mvn test
mvn verify -pl integration-test -Pintegration -am
```

Suite de unidade:
```bash
mvn test
```

Suite de integração isolada:
```bash
mvn verify -pl integration-test -Pintegration -am
```

Frontend:
```bash
cd frontend && npm run test && npm run build
```

---

## H2. Zero Tolerância a Falhas Pré-existentes

> **Substitui:** "falhas pré-existentes toleradas" da seção 4 Fase 4 do `workflow-rules.md`.

### H2.1 — Política

- **Estado de referência:** **H1 (suite completa, 3 comandos — ver nota):** `mvn clean install -DskipTests` → `mvn test` → `mvn verify -pl integration-test -Pintegration -am` deve retornar **0 falhas, 0 erros, 0 skips não-autorizados** antes de QUALQUER novo dev começar.
- **Débito pré-existente** = bug. Tratar como bug P1; sprint de saneamento (ver H2.3).
- **Não usar** `@Disabled`, `@Ignore`, `assumeTrue` ou comentário de skip para "passar a suite". Esses são débito técnico disfarçado.

### H2.2 — Detecção automática ao iniciar dev

Antes de delegar implementação (`developer-engineer`):

```bash
# Orquestrador roda antes de Fase 2 (Implementação)
# 3 comandos: o perfil `integration` desliga o surefire, então unit e IT vão separados.
docker compose -f docker/docker-compose-vetor.yml up -d
mvn clean install -DskipTests
mvn test
mvn verify -pl integration-test -Pintegration -am
```

- ✅ **0 falhas** → prosseguir.
- ❌ **N falhas** → **bloquear dev**, criar `.spec/tech-debts/{contexto}/TASKS.md` com cada falha, priorizar saneamento **antes** da feature/bug.

### H2.3 — Sprint de Saneamento

Quando débitos pré-existentes são identificados:

1. Listar TODAS as falhas em `.spec/tech-debts/{contexto}/TASKS.md`.
2. Classificar: 🔴 P1 (bloqueia) | 🟡 P2 (degrada) | 🟢 P3 (cosmético).
3. **Resolver 🔴 P1 antes** de iniciar feature nova do mesmo domínio.
4. Resolução via mesmo workflow (SPEC → PLAN → TASKS → impl → review → verify).
5. Atualizar `workflow-state.json` com `tech_debt_cleanup` ativo.

---

## H3. Harness E2E Greenfield (Wipe + Re-cadastro)

> **Inspirado em:** `harness-funcional-obrigatorio.md` do Brain + lição "Testes não pegam bugs de UI/contrato/HTTP" (2026-08-19 code-audit).

### H3.1 — Propósito

Testes unitários e de integração **não pegam**:
- Telas brancas, componentes não montados, props erradas.
- Rotas duplicadas (`/api/api/`), headers faltando.
- i18n não aplicado, label hardcoded.
- CORS, autenticação em browser real.
- Fluxo de cadastro completo (criar tenant → usuário/identity → provider → agent → prompt template → agente default → sessão de chat → mensagem multi-turn).

Para esses defeitos, **o único harness que pega é humano + Chrome DevTools em DB zerado**.

### H3.2 — Quando executar (MANDATORY)

| Momento | Frequência |
|---------|-----------|
| Fim de **cada sub-fase SDD** (1.x, 2.x, 3.x) | Por phase |
| Fim de **cada bug de UI/fullstack** | Por bug |
| **Fim de sprint** (semanal, sexta) | Semanal |
| Antes de **release/tag** | Por release |
| Após mudança em **frontend stack/router/i18n/auth/migration base** | Imediato |

### H3.3 — Protocolo (12 passos)

```bash
# 1. Subir dependências (PostgreSQL+pgvector, Redis)
docker compose -f docker/docker-compose-vetor.yml up -d
docker compose -f docker/docker-compose-redis.yml up -d

# 2. WIPE do banco (DROP + recreate via Flyway no próximo start)
docker compose -f docker/docker-compose-vetor.yml down -v
docker compose -f docker/docker-compose-vetor.yml up -d

# 3. Subir backend
cd backend && mvn spring-boot:run -pl bootstrap &
# wait: backend UP em :8080 (healthcheck 200)

# 4. Subir frontend
cd frontend && npm run dev &
# wait: vite UP em :5173

# 5. Chrome DevTools abre sistema
chrome-devtools new_page http://localhost:5173
chrome-devtools take_snapshot
```

### H3.4 — Fluxo de re-cadastro obrigatório

Executar **manualmente via UI** (não API), nesta ordem:

1. **Login** com credenciais dev (ver `.env` / AGENTS.md).
2. **Criar tenant** (se ainda não existe) + **usuário/identity** admin.
3. **Registrar provider** (ex: OpenAI/local) com credencial.
4. **Criar agent** — modelo + system prompt.
5. **Criar prompt template** e associar ao agent.
6. **Definir agente default** nas configurações.
7. **Abrir nova sessão de chat** — selecionar agent.
8. **Enviar mensagem** — validar resposta do agent.
9. **Enviar segunda mensagem** — validar multi-turn (contexto preservado).
10. **Validar persistência** — sessão/mensagens aparecem no histórico; reload da página mantém dados.
11. **Validar i18n** — trocar locale, todas as strings traduzidas?
12. **Logout** — voltar ao login limpo.

### H3.5 — Validações Chrome DevTools obrigatórias

Em **CADA** passo, checar:

| Aspecto | Critério |
|---------|----------|
| **Network** | Sem `/api/api/`, sem 4xx/5xx, payload esperado |
| **Console** | Zero `[ERROR]`, zero `[WARN]` |
| **Snapshot** | Elementos renderizam, dados populam |
| **Screenshot** | Layout bate com `docs/design/DESIGN_SYSTEM.md` |
| **Navegação** | Breadcrumb/sidebar atualiza, voltar preserva state |
| **Responsive** | 1280x800 + 375x800 (mobile) sem quebra |
| **Acessibilidade básica** | aria-labels em inputs/botões, focus visível |

### H3.6 — Encerramento limpo (H5 antes de devolver ao orquestrador)

```bash
# 6. Encerrar processos (SEMPRE — ver H5)
lsof -ti :8080 | xargs kill -9
lsof -ti :5173 | xargs kill -9
```

---

## H4. Avaliação Contínua (Telas, Regras, Melhorias)

> Diferente de H3 (que valida funcionamento), H4 captura **melhorias de UX, regras de negócio e lógica** que só observação humana pega.

### H4.1 — Checklist de avaliação por área

Em cada entrega (feature/bug de UI), o agente deve avaliar:

**Telas (UI/UX)**
- [ ] Padrão `TheTopbar` + `TheSidebar` + `MainLayout` respeitado?
- [ ] Estados de loading/empty/error tratados?
- [ ] Form: padrão de view existente (ex: admin views)?
- [ ] i18n completo (zero string hardcoded)?
- [ ] Dark mode / contraste / foco visível?
- [ ] Design tokens de `docs/design/DESIGN_SYSTEM.md` (PrimeVue Aura, Deep Indigo + Amber)?

**Regras de Negócio**
- [ ] Edge cases (vazio, null, negativo, máximo, concorrência)?
- [ ] RBAC validado (testar como cada perfil)?
- [ ] Tenancy isolation (SAAS) — sem cross-tenant leak?
- [ ] Auditoria: `createAt`/`updateAt`/`tenantId` populados?

**Lógica**
- [ ] N+1 queries? (`@EntityGraph` + `distinct` em paginação — ver brain)
- [ ] Transações consistentes? (`@Transactional` nos boundaries certos)
- [ ] Idempotência em consumers RabbitMQ?
- [ ] Validação server-side (não confiar em client)?

**Performance**
- [ ] Listas paginadas (não full-scan)?
- [ ] Debounce em search?
- [ ] Lazy load de rotas pesadas?

### H4.2 — Melhorias capturadas

Cada melhoria identificada vira:
- **P1 (quebra UX)** → bug em `.spec/bugs/{contexto}/TASKS.md`.
- **P2 (degrada)** → item em `.spec/tech-debts/{contexto}/TASKS.md`.
- **P3 (cosmético)** → item em `.spec/roadmap/{contexto}/TASKS.md` (backlog futuro).

### H4.3 — Brain recording de melhorias

Após cada entrega, `brain_store` com achados relevantes:

```python
brain_store(
    layer="regras",
    path="hive/ux-patterns/<componente>",
    content="## Achado: <título>\n\nContexto: ...\nPadrão esperado: ...\nAplicar em: ...",
    scope="projetos"
)
```

---

## H5. Encerramento Limpo + Brain Obrigatório

> Substitui R2 do `workflow-rules.md` seção 4.0 e complementa com registro cerebral.

### H5.1 — Encerramento de processos

Toda vez que DevTools / harness subir backend `:8080` ou frontend `:5173`, **DEVE** encerrar ao terminar:

```bash
# Verificar e matar processos órfãos
lsof -ti :8080 | xargs kill -9 2>/dev/null
lsof -ti :5173 | xargs kill -9 2>/dev/null
# Verificar zero resultado antes de devolver
lsof -i :8080 || echo "8080 free"
lsof -i :5173 || echo "5173 free"
```

### H5.2 — Brain store por tipo de achado

| Tipo | Layer | Scope | Quando |
|------|-------|-------|--------|
| Erro/bug encontrado | `regras` ou `estudos` | `projetos` (projeto-específico) ou `global` (reusable) | Pós-fix |
| Decisão arquitetural | `arquitetura` | `projetos` | Pós-ADR |
| Aprendizado conceitual | `estudos` | `global` | Após estudo |
| Resumo de sessão | `sessoes` | — | Fim de dia |
| Anti-pattern detectado | `regras` | `projetos` ou `global` | Ao identificar |

Template mínimo:
```python
brain_store(
    layer="<layer>",
    path="<path>",
    content="## <Título>\n\n### Contexto\n...\n\n### Causa raiz\n...\n\n### Solução\n...\n\n### Prevenção\n...",
    scope="projetos|global"
)
```

### H5.3 — Verificação no retorno do subagente

Orquestrador valida retorno contém:
- [ ] `processos finalizados ✅` (H5.1)
- [ ] `brain_store executado ✅` (H5.2)
- [ ] `suite completa OK ✅` (H1)

**Ausência de qualquer item = re-delegar.**

---

## H6. Portão de Conclusão Expandido (7 itens)

> **Substitui** Pre-Completion Gate de `workflow-rules.md` seção 6.3.

Antes de declarar **QUALQUER** fase de implementação (feature OU bugfix) completa:

```markdown
## Pre-Completion Checklist (Harness Gate — NON-NEGOTIABLE)

### Build & Testes
- [ ] **H1 (suite completa, 3 comandos — ver nota):** `mvn clean install -DskipTests` → `mvn test` → `mvn verify -pl integration-test -Pintegration -am` → 0 falhas
- [ ] `cd frontend && npm run test` → 0 falhas
- [ ] `cd frontend && npm run build` → 0 erros TypeScript

### Harness Funcional (H3 — quando aplicável)
- [ ] E2E Greenfield executado (DB wipe + re-cadastro UI) → todos os 12 passos OK
- [ ] Network 0 erros, Console 0 erros/warns, Snapshot esperado
- [ ] Screenshots anexados em `.spec/{feature}/harness/`

### Avaliação Contínua (H4)
- [ ] Checklist UI/UX + Negócio + Lógica + Performance avaliado
- [ ] Melhorias P1 viraram bugs; P2/P3 registradas em `.spec/tech-debts/` ou `.spec/roadmap/`

### Code Review (workflow-rules seção 6)
- [ ] `fullstack-code-reviewer` invocado APÓS última mudança
- [ ] Zero débitos 🔴 Críticos
- [ ] Débitos 🟡 Atenção corrigidos ou justificados

### Brain & Workflow State
- [ ] `brain_store` de lições da fase executado (H5.2)
- [ ] `workflow-state.json` atualizado com `code_review_status: passed` + `harness_status: passed`

### Encerramento
- [ ] Processos `:8080`/`:5173` finalizados (H5.1)
```

**Qualquer item ❌ = fase NÃO está completa. Voltar e corrigir.**

---

## H7. Cadência de Execução

| Atividade | Frequência | Responsável | Duração |
|-----------|-----------|-------------|---------|
| Suite completa (H1) | Por task/bug/feature | `developer-engineer` | ~5-15 min |
| Relatório de falhas (H1.2) | Imediato quando falha | `developer-engineer` | contínua |
| E2E Greenfield (H3) | Fim de sub-fase + fim de sprint + release | `qa-engineer` ou `developer-engineer` | ~30-60 min |
| Avaliação contínua (H4) | Por entrega de UI/fullstack | `developer-engineer` + `product-manager` | embutido |
| Brain store (H5) | Por achado relevante | qualquer agente | 2-5 min |
| Saneamento de débito (H2.3) | Quando débitos > 0 ao iniciar dev | `developer-engineer` | variável |
| Auditoria de regra (H4 cross) | Mensal | `architect` + `qa-engineer` | ~2h |

---

## Anexo A. Comandos Canônicos

```bash
# === Backend ===

# Suite completa (unit + IT) — referência absoluta
# NÃO use `mvn clean install -Pintegration`: o perfil desliga o surefire (zero unitários).
docker compose -f docker/docker-compose-vetor.yml up -d
mvn clean install -DskipTests                            # build completo
mvn test                                                 # unit (reactor inteiro)
mvn verify -pl integration-test -Pintegration -am         # integração (Testcontainers)

# Apenas unit
mvn test

# Apenas IT (requer Docker; -am obrigatório para não usar jar stale do ~/.m2)
mvn verify -pl integration-test -Pintegration -am

# IT específico
mvn verify -pl integration-test -Pintegration -Dit.test=LoginIT

# Build de um módulo só
mvn clean install -pl agent/impl -am -DskipTests

# Compilar módulo (sem testes, rápido)
mvn compile -pl agent/impl -am

# === Frontend ===

# Typecheck + build
npm run build

# Apenas typecheck (rápido)
npx vue-tsc --noEmit

# Apenas build Vite
npx vite build

# Testes
npm run test

# === Dev local ===

# Subir backend
mvn spring-boot:run -pl bootstrap

# Subir frontend
npm run dev

# Wipe DB
docker compose -f docker/docker-compose-vetor.yml down -v
docker compose -f docker/docker-compose-vetor.yml up -d

# Encerrar processos
lsof -ti :8080 | xargs kill -9
lsof -ti :5173 | xargs kill -9
```

---

## Anexo B. Template de Relatório de Harness

Todo retorno de subagente (developer/qa) que executou H1/H3/H4 **DEVE** incluir:

```markdown
## Relatório de Harness — {feature|bug}

### H1 — Suite Completa
- Comandos: `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am`
- Resultado: ✅ PASS / ❌ FAIL
- Unit tests: N passed / M failed / K skipped
- IT tests: N passed / M failed / K skipped
- Falhas (se houver): classe:linha — mensagem

### H2 — Débito Pré-existente (se aplicável)
- Antes do dev: 0 falhas ✅ / N falhas ❌ (listadas abaixo)
- Ação tomada: ...

### H3 — E2E Greenfield (se aplicável)
- DB wipe + recreate: ✅
- Backend UP :8080: ✅
- Frontend UP :5173: ✅
- Fluxo de re-cadastro (12 passos): ✅ todos / ❌ passos X,Y falharam
- Network: 0 erros / N erros
- Console: 0 erros / 0 warns / N erros / M warns
- Screenshots: .spec/{feature}/harness/step-{N}.png
- Processos finalizados: ✅ :8080 free, :5173 free

### H4 — Avaliação Contínua
- Telas: ✅ padrão OK / ⚠️ item X melhorável
- Regras de negócio: ✅ / ⚠️ edge case X
- Lógica: ✅ / ⚠️ N+1 em X
- Performance: ✅ / ⚠️ lista não paginada em X

### H5 — Brain
- brain_store executado: ✅ / ❌
- Paths salvos: regras/hive/..., arquitetura/hive/...

### Conclusão
- Fase completa: ✅ / ❌
- Bloqueios remanescentes: lista
```

---

## Governança

- Este documento tem precedência sobre `workflow-rules.md` em tudo que conflitar.
- Mudanças neste documento exigem aprovação do orquestrador + atualização do `workflow-state.json`.
- Violação = retrabalho completo do dev/bug (mesmo porte de R1-R4 do workflow-rules).
- Conformidade auditada mensalmente (H7) por `architect` + `qa-engineer`.

**Última atualização:** 2026-09-02 — origem: constatação de entregas quebradas apesar de regras existentes.
