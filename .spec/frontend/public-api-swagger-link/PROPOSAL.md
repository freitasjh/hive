# PROPOSAL — link para Swagger na aba API pública

**Data:** 2026-11-24 · feature UI · aprovado.

## PICCO

```
<role> Orchestrator + frontend-engineer
<task> Botao "Ver documentacao" na aba API publica abrindo Scalar no group public
<context> AiAdminPage.tsx:69,78 -> PublicClientsContainer/View; springdoc 3.2.0; /v3/api-docs/public; /scalar; context /hive; sessao por cookie; deep link #public validado em browser real (springdoc.org/#scalar-support e guides.scalar.com/scalar/scalar-api-references/configuration, consultados 2026-11-24)
<constraints> Sem codigo novo de Swagger; spec nao anonima; sem token na URL; sem mudar groups/esquemas; target=_blank + rel=noopener noreferrer; pt/en; alvo 44px; sem backend novo
<acceptance> Link visivel, abre group public com os 2 endpoints; Vitest + build 0; H3 admin 375/1280, console/network limpos
<open_questions>
</open_questions>
```

## Estado do Swagger (verificado)

- springdoc `3.2.0`; `GroupedOpenApi` `public` em `PublicApiOpenApiConfig.java:17-21` cobre `/v1/public/**`.
- `/hive/v3/api-docs/public` → 200; paths: `POST /v1/public/auth/token`, `POST /v1/public/v1/chat/completions`; esquemas: `bearer-key`, `apiKeyAuth`.
- `/hive/scalar` → 200; sources: `APIs INTERNAS` e `public`.
- Deep link `/hive/scalar#public` seleciona o group `public` em carga completa (validado em chrome-devtools).
- Acesso autenticado na chain humana; navegador com cookie HttpOnly de refresh carrega `/scalar` e `/v3/api-docs/public` sem header explicito. Nenhum token entra na URL.

## Escopo (unica mudanca)

- `frontend/src/modules/hive/admin/public-clients/PublicClientsView.tsx`: link "Ver documentacao" no cabecalho, ao lado de busca e "Novo client", `href="/hive/scalar#public"`, `target="_blank"`, `rel="noopener noreferrer"`, icone `ExternalLink` do lucide, `min-h-11`, `data-testid="public-docs-link"`.
- `frontend/src/modules/hive/admin/i18n.ts`: chave `admin.publicClients.docsLink` nos dois idiomas (pt "Ver documentacao", en "View documentation").
- `frontend/src/modules/hive/admin/public-clients/PublicClientsView.test.tsx`: assertion de `href`, `target`, `rel` e texto/i18n.

Fora de escopo: backend, configuracao do Scalar, groups, esquemas, endpoints `/v1/public-api/**` (admin de clients/keys — permanecem no group `1-INTERNAL`), embed/iframe, lista propria de endpoints.

## Criterios de aceite

1. Link visivel em `#providers/public-clients`, sem permissao extra alem da ja exigida pela tela.
2. Clique abre `/hive/scalar#public` em nova aba com os 2 endpoints do group `public`.
3. Nenhum token/segredo na URL; documentacao continua autenticada.
4. `npm test` e build do front com 0 falhas.
5. H3 na tela Admin: 375px e 1280px sem overflow, foco visivel/alvo 44px, console 0 `[ERROR]`/`[WARN]` e rede sem 4xx/5xx nao esperados.

## Riscos conhecidos

- URL de mesma origem depende do context-path `/hive`; se front e backend ficarem em origens distintas, ajustar o href.
- Pagina Scalar tem ruido de console pre-existente (fontes `fonts.scalar.com` e favicon 404). H3 e avaliado na tela Admin; destino verificado por status HTTP/spec, nao por console limpo.

## Decisao pos-H3 (2026-11-24)

- H3: AMBER. Tela Admin PASS em 1280/375; destino falhou no fluxo natural com 401 — cookie de refresh sozinho nao autentica `/scalar` e `/v3/api-docs/**` (`SecurityConfig.java:77-88`). Teste manual anterior do orchestrator estava com header Bearer injetado via CDP; premissa "cookie basta" estava errada.
- Operador aprovou correcao backend (opcao A): filtro JWT deve aceitar o cookie de refresh como credencial humana **somente** nas rotas de documentacao. Sem `permitAll` — spec continua autenticada.
- Operador aprovou ignorar o problema de origens distintas no dev (front 5173 / back 8080); producao e same-origin. Registrado como risco, sem mudanca de config.
- Relatorio QA: `harness/public-api-swagger-link.json`; code review: APPROVED, 0 🔴.

