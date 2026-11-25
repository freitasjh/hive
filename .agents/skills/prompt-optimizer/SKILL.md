---
name: prompt-optimizer
description: "Portão de entrada portátil de demandas de mudança: brain antes do score, E1..E6, Rota A/Rota B, ledger de premissas e PICCO com contexto citado. Use antes de qualquer mudança não trivial; consulta factual pula o gate."
---

# Prompt Optimizer v4 — portátil

Portão de entrada de toda demanda de **mudança**. Consulta factual ao repo
pula o gate. O default escolhido pelo modelo não é o default do usuário —
lacuna preenchida em silêncio é requisito inventado.

Este pacote viaja inteiro: `SKILL.md`, `references/` e `fixtures/`. Ele não
depende de arquivos específicos de um repositório, de runner de teste nem de
ferramentas que possam não existir no ambiente.

## Stage 0 — contexto antes do score

Antes de contar E1..E6, nesta ordem:

1. **Brain literal:** `brain_search(query=<prompt do usuário, literal>, top_k=3)`.
   Paráfrase já é interpretação — não reescreva a query.
   Se a ferramenta não existir ou não responder, diga explicitamente que o
   brain está indisponível e continue apenas com fatos observáveis do prompt
   e do projeto. Nunca simule resultado, path ou citação do brain.
2. **Probe do projeto (somente leitura):** instruções, manifests,
   documentação, regras e nomes de diretório que **existem de fato** no
   ambiente. Cite `arquivo:linha` para cada fato usado. Não presuma a presença
   de nenhum arquivo específico.

Saída do Stage 0: fatos citáveis (`arquivo:linha` ou brain path) para o
`<context>`. O probe **nunca** preenche lacuna do usuário — só diz onde a
demanda cai. Nada é escrito.

## Os 6 elementos

Score = elementos **afirmados** / 6.

| # | Elemento | Checagem | Ausente = |
|---|----------|----------|-----------|
| E1 | Objetivo | O que muda no mundo quando estiver pronto? | crítico |
| E2 | Escopo | O que entra e o que NÃO entra? | crítico |
| E3 | Contexto | Módulo/domínio/tela/endpoint afetado | alto |
| E4 | Restrições | O que não pode quebrar? | alto |
| E5 | Critério de aceite | Como se sabe que ficou pronto? | médio |
| E6 | Intenção de rota | Feature / Bugfix / Refactor / Melhoria? | médio |

**Presente = o usuário AFIRMOU.** O que a IA deduziu conta como ausente.
E1 ou E2 ausentes = gate fechado.

## Rotas

**Rota A (score < 6/6).** PARAR. Nenhuma outra skill, subagente ou artefato —
nada é criado. Máximo 3 perguntas, ordenadas E1 > E2 > E3 > E4 > E5 > E6, na
[forma canônica](references/clarifying-questions.md#formato-de-saída-da-rota-a).
Máximo 2 rodadas; na 2ª sem fechamento, 2 interpretações com o que muda no
resultado e pedido de desempate.

**Rota B (score = 6/6).** Re-encoding em bloco PICCO
([template e exemplos](references/picco-template.md)) com 1 confirmação antes
de executar. Bloco efêmero: chat + prompt de delegação, nunca arquivo.

**Conflito de regra primeiro.** Violar regra do projeto é contradição, não
ambiguidade: nomeie a regra com caminho, ofereça o caminho permitido mais
próximo e pergunte sobre exceção explícita. Violador **e** incompleto:
conflito primeiro, Rota A depois.

**Exceção — consulta.** `show status`, `onde paramos?`, `o que Y faz?` e
leitura de estado pulam Stage 0, score e confirmação. `add feature X`,
`skip X` não pulam.

## Ledger de premissas

Premissa não confirmada que sobreviveu ao estoque (inferência marcada
"parcial" na Rota A, ou premissa da 1ª rodada não respondida na 2ª) **não**
vira fato e **não** entra no PICCO. Vai para o ledger da conversa:

```text
Premissas pendentes:
- <texto da premissa> (E3, inferida de "<trecho do prompt>")
```

O ledger é append-only dentro da conversa e não exige arquivo. Confirmação do
usuário remove o item e promove o texto para `<context>` com citação. Ledger é
rastro, não autorização para executar.

## Bloco PICCO (Rota B)

```xml
<role>[critério de comportamento + o que conta como defeito — nunca persona]</role>
<task>Entregar [E1] em [E3].</task>
<context>[E2 + E3 + E6; cada afirmação de contexto cita arquivo:linha ou brain path]</context>
<constraints>Sempre: [E4 positivas] · Nunca: [violações concretas]</constraints>
<acceptance>[E5 — comando/verificação observável, não adjetivo]</acceptance>
<open_questions><!-- Rota B: vazio --></open_questions>
```

Cinco regras: **≤ 10 constraints**; **role = critério, não persona**;
**positivas primeiro**; **`<context>` cita toda afirmação que não veio
literalmente do prompt**; **nunca escrever nem sugerir "pense passo a passo"**
(CoT escrito degrada modelo de raciocínio). `<acceptance>` deve ser
executável. Exemplos bons e ruins e contagem de constraints em
`references/picco-template.md`.

## Brain (depois)

Padrão que se repete 2x → `brain_store(layer="regras",
path="projeto/prompt-patterns/<elemento>-<padrao>", scope="projetos")` com
lacuna, pergunta que resolveu e resposta. 1 ocorrência é coincidência. Se o
brain estiver indisponível, mantenha o padrão como pendência explícita — não
finja que foi gravado.

## Verificação manual

Não há runner de teste neste pacote. As verificações abaixo são comportamentais
e executadas com os fixtures incluídos.

| # | Verificação | Critério |
|---|-------------|----------|
| V1 | Fixture vago (`fixtures/08-melhorar-cadastro.md`) | Rota A, ≤3 perguntas, zero artefato |
| V2 | Fixture completo (`fixtures/01-email-invalido.md`) | Rota B, PICCO válido, 6/6, 1 confirmação |
| V3 | Brain indisponível | Limitação declarada, nenhum path inventado |
| V4 | Pacote portátil | Nenhuma referência obrigatória a arquivo fora de `SKILL.md`, `references/` e `fixtures/` |
| V5 | Constraints do PICCO | ≤10, positivas primeiro, zero instrução de raciocínio |

## Arquivos do pacote

`SKILL.md` · `references/clarifying-questions.md` ·
`references/picco-template.md` · `fixtures/` (10 prompts: 7 Rota B / 3 Rota A).

Todos viajam juntos. Não copie apenas o `SKILL.md` para outro projeto.
