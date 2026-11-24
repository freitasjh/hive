# RESEARCH — API key complementar da API pública

**Data:** 2026-11-23 · **PRD:** `public-api-key-complementar` · **Status:** insumo para decisão; §16 ainda pendente.

## 1. Pergunta

Como oferecer onboarding e compatibilidade de SDKs OpenAI sem substituir o `client_credentials` já publicado, preservando tenant, escopo, rotação, revogação e telemetria, com cota própria para a key? `x-goog-api-key` foi avaliado e ficou fora do escopo por decisão do operador.

## 2. Fontes oficiais (afirmação — URL — data)

1. OpenAI aceita API keys e tokens de curta duração via workload identity federation; revogação de API key em segundos, demais atualizações até ~15 min — https://platform.openai.com/docs/api-reference/authentication — 2026-11-23.
2. OpenAI usa `Authorization: Bearer <credencial>` — https://platform.openai.com/docs/api-reference/authentication — 2026-11-23.
3. Gemini migra de standard keys para authorization keys ligadas a service account; novas keys são auth keys desde 28/05/2026; standard keys irrestritas são rejeitadas — https://ai.google.dev/gemini-api/docs/api-key — 2026-11-23.
4. Gemini usa header `x-goog-api-key` em REST — https://ai.google.dev/gemini-api/docs/api-key — 2026-11-23.
5. Gemini recomenda tratar a key como senha, nunca versionar, usar variáveis de ambiente/Secret Manager e alertas de billing — https://ai.google.dev/gemini-api/docs/api-key — 2026-11-23.
6. RFC 6749 §4.4 define `client_credentials` para clientes confidenciais; §2.3.1 exige Basic; §5.1 exige `no-store` — https://www.rfc-editor.org/rfc/rfc6749.html — 2026-11-21.
7. RFC 9700 §2.3 recomenda tokens com audiência e privilégio restritos; §2.5 recomenda autenticação assimétrica; §2.2 recomenda tokens sender-constrained — https://www.rfc-editor.org/rfc/rfc9700.html — 2026-11-23.

## 3. Estado atual do Hive (fonte interna)

- `PublicClientTokenIssuer.java:114-139` — JWT com `aud=hive.public`, `tenant-id`, `permissions`, `pcode`, `plock`, `plim` (inclui `r`), expiração configurável por client.
- `PublicApiClientAccessWriter.java:23` — grant `PUBLIC_CHAT_COMPLETE`.
- `PublicApiSecurityFilter.java` — valida JWT e injeta authorities + `TenantContext`.
- `RedisQuotaGuard` — cota por tenant; `plim.r` via claim; fail-open preservado.
- Enum de erros e envelope OpenAI da ADR-001 — contrato congelado.

## 4. Alternativas

### A — `Authorization: Bearer sk-...`

- **Prós:** compatível com SDKs OpenAI e com a maioria dos clients HTTP; uma linha de config; mesmo header do JWT atual (facilita adaptação do filtro).
- **Contras:** `Bearer` já carrega JWT; exige discriminar tipo de credencial no filtro sem ambiguidade; prefixo `sk-` é convenção OpenAI, não padrão.
- **Fonte:** afirmações 1 e 2.
- **Custo:** baixo-médio.

### B — `x-goog-api-key: ...`

- **Prós:** formato explícito e separado de `Authorization`, sem risco de confundir com JWT; aceito por clients Gemini; útil quando o integrador já usa esse header.
- **Contras:** menos universal que Bearer; dois caminhos de autenticação no filtro; não resolve sozinho onboarding OpenAI-style.
- **Fonte:** afirmações 3 e 4.
- **Custo:** baixo-médio.

### C — Ambos os headers

- **Prós:** cobre os dois ecossistemas; escolha do integrador.
- **Contras:** superfície maior, mais ITs, mais documentação; risco de precedência ambígua se ambos vierem na mesma requisição (exigir falha fechada).
- **Fonte:** combinação das afirmações 1–4; precedência é inferência (sem fonte externa), verificar no ADR.
- **Custo:** médio.

### D — Manter somente `client_credentials` (baseline)

- **Prós:** zero mudança; expiração/escopo/tenant/cota já provados; RFC 9700 favorece tokens com audiência e privilégio restritos (afirmação 7).
- **Contras:** atrito de onboarding e adaptação de SDKs — motivação desta pesquisa.
- **Custo:** zero.

## 5. Recomendação de insumo (não é decisão)

**Recomendação alinhada ao operador: alternativa A — apenas `Authorization: Bearer sk-...`.** B (`x-goog-api-key`) e C (ambos) permanecem documentadas como avaliadas, mas **fora do escopo**; não há regra de precedência a decidir.

Desenho de insumo: validador de API key no filtro M2M, aceitando `Bearer sk-...` ao lado do JWT atual. A key é **ligada ao client existente** — tenant e grants vêm da linha, nunca da key — e pode ter **cota própria** (claim de limite distinto do `plim.r` do JWT). `turnId`/`clientId` e os headers de rate limit continuam refletindo a credencial usada. D (manter só OAuth) fica como fallback se o ganho de onboarding não justificar nova superfície.

Razão: OpenAI usa `Authorization: Bearer` com API key (afirmação 1/2), e a própria Gemini migrou para chave **ligada a identidade** (afirmação 3) — o que reforça "key complementar com a mesma identidade do client", sem transformá-la em segredo estático fora de controle.

## 6. Riscos e perguntas para ADR

1. Como discriminar no filtro entre JWT e API key sem aceitar um tipo onde o outro é esperado?
2. Hash da key: BCrypt (como `client_secret`) ou HMAC com chave do servidor? Critério: verificação sem recuperação e rotação com janela.
3. Onde a key aparece no OpenAPI: dois esquemas Bearer com nomes distintos (JWT e `sk-`), ambos em `security` alternativo.
4. Cota da key é própria — IT deve provar que o limite aplicado vem da credencial usada, enquanto `turnId`/`clientId` e `X-RateLimit-*` permanecem corretos.
5. Ausência de sender-constraint (RFC 9700 §2.2) e segredo simétrico (§2.5) — registrar como débito, não resolver neste PRD.

## 7. Limitações

Não foi consultada documentação de outros provedores além de OpenAI e Gemini. Afirmações sobre precedência de headers e sobre hash ideal são inferências locais, marcadas como tal. Nenhuma recomendação de biblioteca nova; nenhuma assinatura de API verificada além dos headers citados.
