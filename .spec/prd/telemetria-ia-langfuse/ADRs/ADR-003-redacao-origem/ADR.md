---
prd_id: telemetria-ia-langfuse
adr_id: ADR-003
title: "Redação na origem e privacidade/LGPD do texto de prompt/resposta em produção"
status: Proposto
data: 2026-11-25
depende-de: ADR-001
rf: "RF3, RF4"
type: adr
---

# ADR-003 — Redação na origem e privacidade do texto de prompt/resposta

- **Data:** 2026-11-25
- **Status:** Proposto — decisão congelada; sem merge (`ADR-INDEX.md`: `todo`).
- **Emenda 2026-11-25 (RED-01):** implementação do redator decidida por deepsearch — regras próprias em JDK, **zero dependência nova**. Ver §"Emenda RED-01".
- **depende-de:** ADR-001 (filho `generation` e portador de turno).
- **RFs atendidos:** RF3 (texto redigido antes de sair do backend), RF4 (fail-closed).

## Contexto

O produto exige prompt/resposta no trace em produção, mas `ChatTrace.java:15` é textualmente limpo e RB1 determina que texto bruto nunca sai do backend. O masking server-side do Langfuse é Enterprise Edition e tem default **fail-open**; o masking do Collector acontece depois de o dado ter saído da aplicação. Base legal/classificação LGPD são premissas do operador, não decididas aqui (PRD §6).

## Decisão

1. **Redação no backend, antes da serialização OTLP.** O texto capturado nos callbacks do `ChatModelListener` (ADR-001) passa pelo redator e só a versão redigida entra no portador de turno. O exportador (ADR-002) recebe spans já redigidos.
2. **Anexar ao filho `generation`, não ao root.** `langfuse.observation.input`/`output` são preenchidos apenas no span de tentativa ao provider — é onde vivem `model`, usage e falha. Root permanece sem texto (menor superfície de exposição e payload).
3. **Política fechada.** CPF, CNPJ, e-mail, telefone, tokens, senhas e chaves de API são substituídos por `[REDACTED:<rule>]`, com `<rule>` no conjunto fechado `cpf|cnpj|email|phone|secret`. Nenhum outro placeholder é aceito downstream. A implementação das regras é própria, em JDK (`Pattern.compile`/`Matcher.find` + dígitos verificadores); o **CNPJ aceita também o formato alfanumérico** em produção desde jul/2026, e o CPF mantém as duas representações (formatado ou 11 dígitos). Detalhes, fontes e inferências em §"Emenda RED-01".
4. **Fail-closed por construção (RF4).** Se o redator lançar, expirar, estiver indisponível ou a política não puder ser aplicada integralmente, **omitemos ambas as chaves** `langfuse.observation.input`/`output`. Metadados do span (`provider`, `model`, `usage_details`, `attempt`, `level`) permanecem. Texto bruto não vai para log, métrica, mensagem de erro nem para qualquer outro atributo.
5. **Sem masking EE/Collector nesta fatia.** Defesa em profundidade posterior, mediante licença ou componente novo, jamais substitui a redação na origem.
6. `ChatTrace` e `ChatTraceRecorder.record(ChatTrace)` permanecem inalterados; o contrato público do chat não muda (RB2).

## Interface congelada

| Nome congelado | Valor / comportamento fixado |
|---|---|
| Atributos de texto | `langfuse.observation.input` e `langfuse.observation.output`, **somente** no filho `generation` |
| Placeholder | `[REDACTED:<rule>]`, `<rule> ∈ {cpf, cnpj, email, phone, secret}` |
| Regras obrigatórias | CPF, CNPJ, e-mail, telefone, tokens, senhas, chaves de API |
| Implementação do redator | Regras próprias em JDK (`java.util.regex.Pattern.compile(String)` + `Matcher.find()` e validação de dígitos verificadores); **zero dependência nova** no `pom` |
| Formato de CNPJ | Numérico **e** alfanumérico (12 posições alfanuméricas + 2 dígitos verificadores numéricos, módulo 11 com valor ASCII−48); regex só-números é proibida |
| Prefixo `sk-` | Requisito **local** do produto (SPEC/RF-05), sem fonte externa — não apresentar como fato de terceiro |
| Fallback de entropia para `secret` | **INFERNÊNCIA** (não congelada como fato): candidato a regra complementar, sujeito a teste/IT antes de virar contrato |
| Fail-closed | Falha/incompletude do redator ⇒ chaves de texto ausentes; metadados preservados |
| Superfícies proibidas para texto bruto | `ChatTrace`, log do backend, rótulo de métrica, mensagem de erro, qualquer atributo OTLP que não seja o par redigido |
| Métrica de falha | `chat.telemetry.redaction.failures.total` (incremento por evento fail-closed; ver ADR-005) |
| Root | sem `langfuse.observation.input`/`output` |

> Critério de canário = RF3/RF4: payload OTLP capturado não contém literal sensível; trace no Langfuse contém só a versão redigida; com redator forçado a falhar, o span existe com `provider`/`model`/usage e sem input/output; 0 `sk-` em log/payload.

## Emenda RED-01 — decisão de implementação (deepsearch 2026-11-25)

**Gate:** `RED-01` (`SPEC.md` §12, NFR-05). Deepsearch do `engineer` em 2026-11-25, nota `estudos/projetos/hive/adr-003-004-deepsearch-redacao-retencao-2026-11-25` (22 fontes; websearch + webfetch em fontes oficiais). **Context7: indisponível** (sem MCP tool e sem CLI `ctx7`/`context7`) — declarado, não substituído por memória. Esta emenda não altera SPEC/TASKS (o `engineer` as atualiza depois) e não adiciona código.

### Decisão

1. **Regras próprias, zero dependência nova.** O redator usa apenas JDK: `Pattern.compile(String)` compilado uma vez e `Matcher.find()` por evento; `Pattern` é imutável e thread-safe, `Matcher` não é — um `Matcher` por chamada, nunca compartilhado. CPF/CNPJ com dígitos verificadores próprios e validados; e-mail e telefone por regras próprias; segredos por prefixos conhecidos. `spring-boot-starter-validation`/HV 9.1.0.Final já estão no `pom`, mas **não são usados como motor de redação** — não há justificativa para nova dependência. Fonte: Oracle JDK 25 `Pattern` — 2026-11-25; repo `backend/dependencies/pom.xml:21` (`java.version=25`) — 2026-11-25.
2. **CNPJ alfanumérico é obrigatório.** A Receita Federal informa que o novo formato entrou em produção em **27/07/2026** e o primeiro CNPJ alfanumérico foi emitido em **31/07/2026**; a regra aceita 12 posições alfanuméricas + 2 dígitos verificadores numéricos (módulo 11; valor = ASCII−48, com A=17, B=18, …). **Fonte governamental vence sobre a documentação do Hibernate Validator 9.2**, que afirma "starting January 2026"; usar regex só-números vazaria CNPJs válidos emitidos a partir de julho/2026. CNPJs numéricos preexistentes permanecem válidos. Fontes: Receita Federal (CNPJ Alfanumérico / cronograma; cálculo do DV; primeira emissão) — 2026-11-25; Hibernate Validator 9.2 Reference Guide e `CNPJ.Format` — 2026-11-25.
3. **CPF: duas representações.** Mesma regra própria aceita `XXX.XXX.XXX-XX` e sequência de 11 dígitos, com dígitos verificadores válidos; valores parcialmente formatados são rejeitados. Fonte: Hibernate Validator 9.2 Migration Guide (§"CPF validation") — 2026-11-25.
4. **`sk-` é requisito local, não fato externo.** A obrigação de redigir literais com prefixo `sk-` vem da SPEC/RF-05 e do critério de canário deste ADR; nenhuma fonte externa consultada a estabelece como padrão de terceiro. Fonte: `SPEC.md` §7.3/§10 (RF-05) — 2026-11-25.
5. **Entropia para `secret` é INFERNÊNCIA.** O fallback por entropia para detectar tokens sem prefixo conhecido **não** está confirmado em fonte externa e **não** faz parte do contrato congelado desta emenda; só entra se teste/IT demonstrar que não redige texto útil e não gera falso-negativo no fixture. Fonte: deepsearch 2026-11-25 (rótulo explícito do `engineer`).

### Inferências e riscos (rótulo obrigatório)

| Afirmação | Classificação | Ação |
|---|---|---|
| `sk-` como prefixo obrigatório | Requisito local (sem fonte externa) | Manter como requisito do produto; nunca citar como padrão de terceiro |
| Fallback de entropia para segredos sem prefixo | **INFERNÊNCIA** | Não congelar; validar por teste antes de propor nova emenda |
| DLP externo (GCP/AWS/Presidio) como alternativa | **INFERNÊNCIA** do deepsearch | Descartado: texto bruto sairia do backend, violando RF3/RB1; nenhuma afirmação de capacidade de vendor assumida como fato |
| Data de adoção do CNPJ alfanumérico | Fonte governamental (jul/2026) sobre docs de HV (jan/2026) | Regra própria cobre ambos os formatos; divergência registrada, não resolvida a favor do HV |

### Impacto nas tasks (para o `engineer`, sem alteração aqui)

- **RED-03** (RF-04): o fixture e os testes de CPF/CNPJ devem incluir CNPJ alfanumérico válido (ex.: formato `AA.AAA.AAA/AAAA-DV` com DV calculado pela tabela oficial), além do numérico `12.345.678/0001-90`; a asserção não pode aceitar regex só-números como prova de sucesso.
- **RED-04** (RF-05): manter `sk-abcdef1234567890` e `senha=SuperSecret123` ⇒ `[REDACTED:secret]`; **não** implementar entropia como contrato — se implementada, fica atrás de teste dedicado e é registrada como inferência validada em nova emenda.
- Zero diff em `pom.xml` por esta fatia é a evidência mecânica da decisão "zero dependência nova".

## Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| Masking server-side EE com `LANGFUSE_INGESTION_MASKING_CALLBACK_FAIL_CLOSED=true` | Exige licença Enterprise (decisão congelada: Community); default é `false` (fail-open); evento chega a blob storage antes da máscara; cobre só `/api/public/otel`. Fonte: self-host data masking — 2026-11-25. |
| Masking no Collector | Dado já saiu da aplicação — contraria literalmente RF3 ("antes de sair do backend"); filtro de spans pode remover root. Fonte: masking docs — 2026-11-25. |
| Hook client-side do SDK (`mask_otel_spans`, `LangfuseSpanProcessor({mask})`) | Fail-closed nativo, mas documentado para Python/JS; stack é Java. Fonte: masking docs — 2026-11-25. |
| Não enviar texto nenhum | Contraria escopo aprovado do produto (prompt/resposta no trace com redação). |
| Redação apenas no log | Não protege o payload OTLP nem o trace no Langfuse. |
| Regex própria só-números para CNPJ | Vazaria CNPJs alfanuméricos emitidos desde jul/2026 (gov.br vence sobre a doc do HV que diz jan/2026). Fonte: Receita Federal — 2026-11-25. |
| Phileas `ai.philterd:phileas:4.5.0` | Superfície maior que a necessária: README não lista CPF/CNPJ entre entidades nativas (há validadores `mod11` com variantes `cpf`/`cnpj` desde 4.2.0 e marketing do site afirma cobertura — fonte divergente, registrada); release notes marcam 4.5.0 como "not yet released"; README do repo exige Java 25 para build (site diz 21+, release notes 4.2.0 citam bytecode 17 — divergência); detecção de nomes por Ph-Eye via HTTP (ou módulo ONNX opcional) acrescenta hop/serviço e risco a RF3. Contraria "zero dependência nova". Fontes: README/release notes do repo e site do produto — 2026-11-25. |
| Libphonenumber (`PhoneNumberUtil.findNumbers`) e Commons Validator (`EmailValidator.getInstance().isValid(String)`) | 2–3 dependências novas para regras que o JDK resolve; `findNumbers` classifica apenas telefone e `EmailValidator` declara "not guaranteed to catch all possible errors"; nenhuma cobre `secret` ou CNPJ alfanumérico. Fontes: repositório Google e Javadoc Apache Commons Validator 1.11.0 — 2026-11-25. |
| HV `@CNPJ`/`@CPF` como motor da redação | Já presente no `pom`, mas `format` default é `NUMERIC` e `@Incubating`; a doc 9.2 diverge da Receita; usar validação de bean como redator de texto acrescentaria acoplamento sem cobrir `secret`. Fontes: Javadoc HV 9.1 e Reference Guide 9.2 — 2026-11-25. |
| DLP externo (GCP/AWS/Presidio) | **INFERNÊNCIA** do deepsearch: exigiria envio do texto bruto para fora do backend, violando RF3/RB1; nenhuma capacidade de vendor foi assumida como fato. |

## Consequências

**Positivas**
- Zero dependência nova: superfície de supply chain e manutenção não crescem; regras são auditáveis no próprio repositório.
- CNPJ alfanumérico coberto desde já — fecha o vazamento que uma regex só-números abriria em produção (jul/2026).
- Literal sensível nunca deixa o processo — satisfaz RB1/RF3 sem depender de licença EE.
- Fail-closed é verificável por teste determinístico (RF4) e degrada para metadados úteis, não para span vazio.
- Texto fica no filho `generation`, junto de modelo/usage/falha: diagnóstico de tentativa sem duplicar dado no root.

**Negativas**
- Regras de CPF/CNPJ/e-mail/telefone/segredos vivem no código e exigem canário contínuo; falsos positivos podem redigir texto útil. O custo é mantido internamente: qualquer mudança de formato (ex.: novo padrão de CNPJ) exige nova emenda + teste, não upgrade de dependência.
- Divergência de datas entre gov.br (jul/2026) e HV 9.2 (jan/2026) significa que documentação de terceiro não é fonte de verdade para regra de negócio; a regra correta é aceitar os dois formatos.
- `sk-` sem fonte externa e entropia como inferência são pontos de fragilidade documental: o primeiro é premissa local, o segundo não pode virar contrato sem evidência de teste.
- Quando o redator falha, perde-se o conteúdo daquela tentativa — a falha só aparece no contador e no log sem texto.
- Community não oferece rede de segurança server-side; um bug de redação só é detectado pelo canário.
- Classificação LGPD/base legal segue como premissa do operador; este ADR não a decide.

## Fontes

- Langfuse — masking (na aplicação vs Collector vs SDK; fail-closed do SDK) — https://langfuse.com/docs/observability/features/masking — 2026-11-25.
- Langfuse — self-host data masking (EE, `...FAIL_CLOSED=false`, só `/api/public/otel`) — https://langfuse.com/self-hosting/security/data-masking — 2026-11-25.
- PRD — §5 (RF3/RF4), §6 (privacidade/LGPD), §7 (respostas do operador), §11 (RB1) — 2026-11-25.
- RESEARCH — eixo D3 — 2026-11-25.
- Oracle — JDK 25 `Pattern`/`Matcher` (imutabilidade e thread-safety de `Pattern`) — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html — 2026-11-25.
- Receita Federal — CNPJ Alfanumérico e cronograma (produção 27/07/2026; primeiro CNPJ 31/07/2026) — https://www.gov.br/receitafederal/pt-br/acesso-a-informacao/acoes-e-programas/programas-e-atividades/cnpj-alfanumerico — 2026-11-25.
- Receita Federal — primeiro CNPJ alfanumérico emitido (31/07/2026) — https://www.gov.br/receitafederal/pt-br/assuntos/noticias/2026/julho/receita-federal-gera-o-primeiro-cnpj-em-formato-alfanumerico — 2026-11-25.
- Receita Federal/Serpro — cálculo do dígito verificador do CNPJ alfanumérico (módulo 11, ASCII−48) — https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf — 2026-11-25.
- Hibernate Validator — Reference Guide 9.2 (`@CNPJ` "starting January 2026") — https://docs.hibernate.org/validator/9.2/reference/en-US/html_single — 2026-11-25; `CNPJ.Format` — https://docs.hibernate.org/validator/9.2/api/org/hibernate/validator/constraints/br/CNPJ.Format.html — 2026-11-25; Javadoc 9.1 (`format=NUMERIC`, `@Incubating`) — https://docs.hibernate.org/validator/9.1/api/org/hibernate/validator/constraints/br/CNPJ.html — 2026-11-25.
- Hibernate Validator — Migration Guide 9.2 (CPF aceita só formato completo ou 11 dígitos) — https://docs.hibernate.org/validator/9.2/migration/en-US/html_single/ — 2026-11-25.
- Philterd — Phileas README e release notes 4.5.0 (Java 25 no build; Ph-Eye HTTP; validadores `mod11`/`luhn`) — https://github.com/philterd/phileas — 2026-11-25; https://github.com/philterd/phileas/blob/main/RELEASE_NOTES.md — 2026-11-25; site do produto — https://philterd.ai/phileas — 2026-11-25.
- Google — libphonenumber (`PhoneNumberUtil.findNumbers`) — https://github.com/google/libphonenumber — 2026-11-25.
- Apache — Commons Validator 1.11.0 `EmailValidator` ("not guaranteed to catch all possible errors") — https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/EmailValidator.html — 2026-11-25.
- Deepsearch do `engineer` (nota canônica no Brain; 22 fontes; Context7 indisponível) — `estudos/projetos/hive/adr-003-004-deepsearch-redacao-retencao-2026-11-25` — 2026-11-25.
