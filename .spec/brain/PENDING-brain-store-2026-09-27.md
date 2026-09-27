# PENDING brain_store — marco do Assistant validado ponta a ponta

**Data:** 2026-09-27 · `brain_ping` → HTTP 410 (5a vez seguida)

Destino quando o Brain voltar:
- `layer=arquitetura`, `path=hive/decisoes/assistant-loop-validado-end-to-end`, `scope=projetos`
- tags: hive, mvp, validacao, produto, aprendizado, e2e, escopo

---

# Marco: loop do Assistant validado ponta a ponta com provider real

**Status:** MVP do loop elementar **validado com modelo de verdade**.

## O que foi validado

Cadastrar credencial → configurar default do tenant (`/settings/llm`) → cadastrar prompt
template → abrir chat → escolher prompt → enviar mensagem → **resposta do modelo real**.

Fluxo completo funcionando. Sem provider real, isto nunca tinha rodado desde a mudança de
escopo: ate aqui a geracao era coberta por IT com Testcontainers e um 502 com chave falsa.

## Por que isto importa mais que qualquer codigo

A decisao de remover o RAG veio do diagnostico: *"estamos dando um passo maior que a perna,
principalmente que ainda nao temos um MVP bem estruturado com assistent"*. Este marco fecha a
divida que aquela decisao comprou.

**A licao que ele paga, e que vale mais que a decisao:**

> Construir a segunda abstracao antes do primeiro loop estar fechado custa caro mesmo quando a
> segunda esta correta. O RAG estava funcional, testado, com E2E real, revisado e documentado por
> 3 ADRs. E mesmo assim era o lugar errado — nao porque era ruim, mas porque **nao era o que ainda
> nao existia**.

O sintoma que antecede essa classe de erro: **otimizacao de camada de infraestrutura enquanto o
loop de produto esta aberto.** A conta nao aparece em metrica de qualidade — RAG passando em
tudo, 700 testes verdes, 3 ADRs. Aparece em produto que nao e usavel.

## Regra que decorre

**Antes de construir a segunda capacidade, exercite a primeira em ambiente real, por um humano,
no browser.** Nao por IT, nao por curl, nao por mock.

Caso concreto deste projeto: o `PromptSelector` pedia `size=100` contra um servidor que responde
400 para `size>50` — **400 em toda montagem da tela**, invisivel a 145 testes porque o mock aceita
qualquer numero. So o browser real viu.

**Rede de seguranca estrutural tambem morre com o codigo que ela protegia.** O
`HardcodedSimilarityCutOffGuard` protegia contra hardcodar threshold de similaridade; o unico
decidedor de threshold morreu, e o guard ficou orfao em `src/main` protegendo um erro que nao pode
mais ocorrer. Guard sem consumidor e orcamento de manutencao de uma condicao que ninguem mais impoe.

## O que este marco destrava

- **API publica** — agora ha um loop interno funcionando para expor. A pergunta de autenticacao e
  cota (que ficou aberta) agora tem o que proteger.
- **RAG de novo, se um dia fizer sentido** — com gatilho explicito e o loop ja validado, em vez de
  como segunda abstracao adivinhada.

## Pendente

- **Rotacionar `OPENCODE_GO`**: a chave antiga foi exposta em conversa. O fluxo acima cadastrou
  credencial nova — **confirmar que a antiga foi revogada e nao so contornada**.
- **P1 de RBAC** (`.spec/tech-debts/security-rbac/`): todo usuario autenticado e superusuario, RBAC
  decorativo. Relevante agora que existe produto usavel.
