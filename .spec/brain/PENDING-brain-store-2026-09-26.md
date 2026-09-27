# PENDENTE — brain_store (2026-09-27)

O MCP do Brain estava intermitente durante o Batch 1 (410 Gone / timeout em `ping` e `status`).
O conteúdo abaixo está pronto para ser gravado assim que o servidor voltar.

---

## 1) `layer=regras` (ou `estudos`), `scope=global`

**path:** `estudos/remocao-modulo-acopla-o-chamador`
**tags:** `arquitetura`, `remocao`, `refactor-destrutivo`, `testes`, `escopo`, `java`

```markdown
# Remover um módulo acopla o chamador — mapeie quem importa antes de apagar

## Contexto

Remoção escopada do módulo `knowledge` (RAG) no Hive AI, 2026-09-27. O objetivo era "o módulo
`agent` passa a compilar e funcionar sem `knowledge`". O plano listava 8 arquivos de `main` para
corrigir — e os 8 estavam certos, e mesmo assim o corte não fechava, porque o módulo removido tinha
deixado dependências em lugares que ninguém tinha mapeado.

## A regra

**Antes de apagar o código de um módulo, grep o uso pelos CHAMADORES, não pelos amigos.** A lista de
arquivos a corrigir só está completa quando você viu *todo* import do pacote deletado, em `main` e em
`test`. A lista de arquivos do módulo que você vai deletar é a metade fácil; a metade que vira cascata
são os `import` que apontam *para* ele.

Os dois que quase escaparam, e que nenhum plano de escopo adivinharia:

1. **`RagDiagnostic.causeOf(Throwable)`** — método de um record de *domínio de KB*, usado por
   `ChatServiceImpl` para classificar a falha no `ChatTrace`. Não tinha nome de RAG, não vivia em
   pasta de RAG, e a dependência era invertida: o `agent` dependia do `knowledge` por um método
   utilitário de nome genérico.
2. **Dois endpoints de vínculo KB** onde o briefing dizia "o endpoint" (singular). Deixar um dos dois
   vivo com o serviço já removido é contrato quebrado, não código morto.

## O grep que fecha o escopo

    # 1. Quem importa o módulo que vai sumir — main E test.
    grep -rn "hive\.knowledge\|knowledge\.\(api\|impl\)" backend/ --include="*.java" | grep -v /target/

    # 2. Quem declara o artefato no build — a lista de poms é maior que a do briefing.
    grep -rn "hive-knowledge" backend/ --include="pom.xml"
    grep -rn "hive-knowledge" backend/pom.xml        # <modules> raiz

    # 3. Quem chama o método removido pelo NOME, dentro e fora da pasta óbvia.
    grep -rn "<nomeDoMétodoRemovido>" backend/ --include="*.java" | grep -v /target/

O passo 3 achou o `causeOf`. Os passos 1 e 2 acharam o resto.

## Sintoma de que você esqueceu o passo 3

O build reclama de `package ...knowledge... does not exist` — mas o arquivo que reclama **não é nenhum
dos que você editou**. É um consumidor cujo único contato com o módulo era um método de nome genérico.

## Consequência do não-mapeamento

O corte vira cascata: você apaga, o build quebra, e a resposta errada é "vou manter o knowledge como
dependência opcional" — que é exatamente o congelamento que a decisão de produto rejeitou. O custo do
atalho é maior que o corte.

## Corolário 1: cobertura viaja com o comportamento, não com o arquivo

Ao mover um método utilitário vivo de um módulo para outro, **mover também os testes que o cobriam** —
ou, se ele nunca teve cobertura direta, **escrever a cobertura nova e dizer isso**. Aqui `causeOf` só
era testado indiretamente (`ChatServiceImplTest`, `KbRetrievalServiceTest`); `git ls-tree` no tag
prova que nenhum teste de `RagDiagnostic` existia. Então os 6 testes do `FailureClassifier` são
**cobertura nova**, não coverage portada — e narrar como "portada" é falsificar o histórico do
débito. A distinção importa porque "portada" fecha a conta e "nova" aumenta a cobertura.

## Corolário 2: "comportamento idêntico" é uma afirmação que precisa de prova por ambiente

Atingi "zero mudança no que sobrevive" e declarei isso no relatório. Era **falso**, e um code review
achou: a coluna `agent.knowledge_bases` é `ADD COLUMN ... TEXT[]` — nullable, sem `DEFAULT` — e a
migration `V13` leu dela e **nunca limpou**. Em banco limpo o comportamento era idêntico (`null`),
então nenhum teste pegou. Em qualquer banco que rodou a era do link, a API passou a devolver nomes de
KBs que não existiam mais.

**Regra:** "idêntico" só vale se você verificou os *estados достижимые*, não o estado do seu ambiente
de teste. Um campo nullable sem default habitado por uma migration antiga é um estado alcançável que
o seu `TRUNCATE` esconde. Pergunte: *qual valor esse campo tem em cada ambiente que já rodou?*

## Corolário 3: meça antes e depois com o MESMO método; não confie na contagem de anotações

`@Test` não é o número de execuções: `@ParameterizedTest` e `@CsvSource` multi-arg rendem mais, e
parsear anotações à mão erra. A prova é worktree na tag de segurança, rodar o mesmo comando dos
dois lados, e comparar classe por classe:

    git worktree add /tmp/antes <tag>
    cd /tmp/antes/backend && mvn -o test
    mvn -o test

Resultado medido aqui: 306 em classes deletadas + 24 aparados de 3 classes que sobrevivem − 6 novos =
**−324**, e 700 − 324 = 376 = o "depois" medido. Um teste que desaparece sem entrar nessa conta é um
teste perdido, e a tabela por classe mostra qual.
```

---

## 2) `layer=projetos`, `scope=projetos`

**path:** `projetos/hive/batch1-rag-removal-review-lessons`
**tags:** `hive`, `rag`, `remocao`, `code-review`, `assistant`

```markdown
# Batch 1 (remoção do RAG) — o que o code review ensina sobre o método

**Data:** 2026-09-27 · **Status:** code review aprovado com 6 🟡, todos de "verdade em comentário".

## O padrão dos 6 achados

**5 dos 6 eram comentários que afirmavam mais do que o código faz.** Nenhum era bug de lógica. O
reviewer confirmou de forma independente que nada de segurança, permissão, tenant ou transação
regrediu — e tinha razão: o único achado com consequência real (a API devolvendo `knowledgeBases`
pendurado) nasceu de eu ter escrito um javadoc que afirmava "o mesmo valor que toda leitura já via".

## A lição central: o danger de uma refatoração não é o que você quebra, é o que você promete

Em refatoração destrutiva, o artefato mais perigoso não é o código — é a frase no relatório e no
javadoc. "Comportamento idêntico", "zero mudança no que sobrevive", "byte a byte" são afirmações
verificáveis, e cada uma delas é uma dívida se não for provada. As três correções de relato que o
reviewer exigiu (branch removida no `causeOf`; testes novos e não portados; contagem 305 vs 306) são
todas do mesmo tipo: **a aritmética do relato tinha que fechar, e não fechava**.

## Processo que funcionou (e que deve ser padrão)

1. Medir a linha de base em worktree na tag, com o mesmo comando do estado final.
2. Alterar, medir de novo, comparar **classe por classe**.
3. Antes de declarar "zero mudança", perguntar por **ambiente**, não por teste: o que esse campo
   contém em cada banco que já rodou? A resposta estava numa migration de três dias antes.
4. Toda vez que um comentário for corrigido, verificar a afirmação com grep, não com memória.
   Foi assim que achei que o javadoc do `agent/api/pom.xml` citava `TurnReply` usando langchain4j —
   herança do texto antigo, e `TurnReply` nunca importou nada de langchain4j.

## Especificidade do projeto

- **Mapper com `ignore = true` ≠ apagar a linha `@Mapping`.** MapStruct mapeia campos de mesmo nome
  implicitamente: apagar a linha mantém o mapeamento. O `knowledgeBases` foi "removido" do
  `toDomain` e continuaria sendo lido da coluna.
- **`insertable = false, updatable = false` é o guard de escrita que não depende de disciplina.** Um
  método que "descarta o valor antes de salvar" é convenção; a anotação é o banco recusando. Manter
  os dois é correto, desde que o comentário não diga que o primeiro basta.
- **Módulo de teste órfão:** `HardcodedSimilarityCutOffGuard` (commons/test-support) ficou sem
  consumidor real — o único import era o que o Batch 1 deixou morto. Guard de "não hardcode threshold
  de similaridade" sem ninguém perguntando sobre threshold. **Reportado, não removido**: decisão de
  Batch 3.
- **Dependências mortas com comentário que aponta para código apagado** são a assinatura de remoção
  parcial: `opennlp-tools` e `jtokkit` sobreviveram porque a revisão de escopo olhou o plano, não o
  grafo de dependências. O `langchain4j` agregador ficou (o `provider` usa) e só o comentário mentia.
```

---

## Como gravar

```
brain_store(layer="estudos",  path="estudos/remocao-modulo-acopla-o-chamador",         scope="global",   tags=[...], content=<corpo da nota 1>)
brain_store(layer="projetos", path="projetos/hive/batch1-rag-removal-review-lessons",  scope="projetos", tags=[...], content=<corpo da nota 2>)
```

Depois de gravar, apagar este arquivo.
