# BUG — Threshold de similaridade único para todos os modelos + log de RAG que mente

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (KB com nomic nunca recupera) · 🟡 P1 (log com dado falso)
- **Origem:** medição real no pgvector após o fix do prefixo de tarefa
- **Decisão:** usuário aprovou — `nomic-embed-text` → **0.60**, OpenAI mantém 0.70, e corrigir o log
- **Rodada:** única

## Medição (mesma KB `teste`, 4 sondas, `1-(embedding<=>query)`)

Depois do prefixo de tarefa nos dois lados:

| Sonda | Antes do prefixo | Depois do prefixo |
|---|---|---|
| Relevante longa | 0.6490 | 0.6481 |
| Relevante curta | 0.5701 | **0.6219** |
| **Irrelevante** (banco de dados) | **0.6141** ← acima da relevante curta | 0.5972 |
| Sem relação (protein folding) | 0.5333 | 0.5851 |

O prefixo **corrigiu uma inversão de ranking**: antes a pergunta irrelevante pontuava acima da
relevante curta. Margem relevante-vs-irrelevante: 0.035 → **0.051**.

Um corte em **0.60** separa as 4 sondas: relevante curta (0.6219) entra; irrelevante (0.5972) e
sem relação (0.5851) ficam fora. Margem ~0.02 — **estreita**, porque a KB tem 5 chunks de um PDF
pequeno (um deles é a página de direitos autorais, outro é fragmento truncado). Isso está registrado
como limitação conhecida, não como resultado ideal.

## Bug 1 🔴 — Threshold único `0.7` para todos os modelos

`ChatServiceImpl.RAG_MIN_SCORE = 0.7` e `KbRetrievalService.DEFAULT_MIN_SCORE = 0.7` — **duas
constantes, um conceito** (já apontado pelo reviewer). Com `nomic-embed-text`, cujas
similaridades são sistematicamente mais baixas, o corte rejeita acertos.

### Solução
1. **Threshold default por modelo**, na **mesma** tabela de convenção
   (`EmbeddingTaskConvention`), para não criar um segundo lugar com política de embedding:
   - `nomic*` → **0.60** (medido)
   - `text-embedding-3-*` → **0.70** (comportamento atual, não regredir)
   - demais / desconhecido → **0.70** (conservador)
2. **Uma única fonte**: remover `RAG_MIN_SCORE` de `ChatServiceImpl` e `DEFAULT_MIN_SCORE` de
   `KbRetrievalService`. O threshold efetivo passa a ser resolvido a partir do **modelo da KB**,
   dentro de `retrieveWithOutcome` (que já conhece o modelo). `ChatTrace`/`RagDiagnostic` seguem
   reportando o número **que rodou** (o `ragThreshold` já existe).
3. **Preservar override explícito**: o parâmetro `minScore` do método continua aceitando um valor
   quando o chamador realmente quer forçar (o playground pode querer). Distinguir "não
   especificado" de "0.70 explícito" — cuidado para não fazer `0` cair no default sem querer.
4. `ChatServiceImpl:485` (caminho do playground/search) deve usar a **mesma** resolução, senão a
   busca na tela e a do chat discordam.
5. **Não** introduzir coluna `min_score` por KB agora (P2 futuro, já registrado).

### Testes
- `EmbeddingTaskConventionTest` (ou o registry): default por modelo — nomic 0.60, text-embedding-3 0.70, desconhecido 0.70, e a **ausência de hardcode remanescente** (grep por `0.7` no path de decisão).
- `KbRetrievalServiceTest`: KB com modelo nomic e **sem** `minScore` → threshold efetivo 0.60 no `RagRetrievalResult.threshold()`; KB com `text-embedding-3-small` → 0.70; `minScore` explícito (0.55) respeitado; `minScore` = 0 (o **comportamento atual** cai no default — **não regredir**).
- `ChatServiceImplTest`: o `ChatTrace.ragThreshold` vem do resultado real, nunca de constante local.
- Regressão: `KnowledgeSearchIT` e `RagChatE2EIT` (OpenAI) seguem verdes **sem** alteração de expectativa — se precisarem mudar, é sinal de que a resolução está errada.
- **Teste de integração com o vetor medido**: semear um chunk com o vetor real de um texto e
  consultar com um texto cuja similaridade medida ficou em 0.62 → recuperou; 0.59 → não recuperou.
  Isso fixa o comportamento com o número real em vez de 1.0 artificial.

## Bug 2 🟡 — A linha de log de RAG no nível agent reporta dado falso

Hoje saem **duas** linhas por mensagem:

```
rag outcome=no_chunk_above_threshold ... foundKbs=1 ... reason=vector search returned no chunk above the similarity threshold   ✅
rag outcome=no_chunk_above_threshold agentId=... foundKbs=0 ... reason=empty retrieval                                          ❌
```

A segunda (nível agent, `ChatServiceImpl`) **inventa** `foundKbs=0` e `reason="empty retrieval"`,
descartando o que o `RagRetrievalResult` já sabe. Pior que não logar: manda o operador
investigar o lugar errado — exatamente o que aconteceu na investigação deste bug.

### Solução
1. `ChatServiceImpl` deve montar o `RagDiagnostic` a partir do **`RagRetrievalResult` real**
   (outcome, `foundKbs`, threshold, razão), e não com literais.
2. Se a informação não existe no resultado, **añadir ao resultado** — não inventar no chamador.
   `RagRetrievalResult` precisa carregar `foundKbs` e `reason` (ou o `RagDiagnostic` completo).
3. **Duas linhas continues Justas**, desde que a segunda seja **igual** à primeira mais o
   `agentId`. Se a informação for idêntica, considerar emitir **uma** linha só (com `agentId`)
   e evitar redundância. Julgue e justifique: o reviewer já disse que os dois níveis são
   complementares (retrieval vs turno) — mas isso só vale se a info for coerente.
4. **Teste de não divergência**: para cada `RagOutcome`, a informação da linha do agent deve
   bater com a do retrieval. Teste de caracterização, não de string.

## Critérios de aceite
- [ ] Threshold por modelo na tabela de convenção: nomic 0.60, OpenAI 0.70, desconhecido 0.70
- [ ] `RAG_MIN_SCORE` e `DEFAULT_MIN_SCORE` removidos (grep limpo)
- [ ] Override explícito respeitado; `0` mantém o comportamento atual
- [ ] Playground e chat usam a mesma resolução
- [ ] Log do agent **não** inventa `foundKbs`/`reason`
- [ ] Teste de não divergência entre as linhas
- [ ] Teste de integração com os vetores medidos (0.62 recupera / 0.59 não)
- [ ] `mvn clean install -DskipTests` + `mvn test` + `mvn verify -pl integration-test -Pintegration -am` → 0 falhas · `npm run test` + `npm run build` → 0 falhas
- [ ] `fullstack-code-reviewer` zero 🔴
- [ ] Brain (MCP fora — deixar nota)
- [ ] Processos finalizados
