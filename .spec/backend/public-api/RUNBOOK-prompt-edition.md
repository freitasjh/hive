# RUNBOOK — edição de prompt com integração pública (modo `PINNED`)

- **Feature:** `public-api-chat` (ADR-014) · **Decisão:** D1 — default `FOLLOW_ACTIVE`, `PINNED`
  disponível por credencial e por `prompt_code`.
- **Quando ler:** antes de dar `PUT` num prompt template que alguma credencial pública referencia
  em modo `PINNED`.

---

## O que acontece, mecanicamente

`PromptTemplateServiceImpl.update()` **muta a linha in-place** e incrementa `version`
(`:82-88`). Não existe tabela de histórico. Não existe snapshot.

Uma credencial `PINNED` na versão N aponta para uma linha que, após o `PUT`, **está na versão N+1**.
A próxima chamada pública devolve:

```
409 prompt_version_not_found   (invalid_request_error)
```

Não é degradação silenciosa — é a asserção de conformidade funcionando. Mas **a integração cai**, e
o sintoma aparece no ERP, não em quem editou o prompt.

## Procedimento (5 passos)

1. **Antes do `PUT`:** listar as credenciais com `pin_mode = 'PINNED'` para o `prompt_code`:
   ```sql
   SELECT c.client_id, c.label, p.pinned_version
   FROM public_api_client_prompt p
   JOIN public_api_client c ON c.id = p.client_ref
   WHERE p.prompt_code = '<code>' AND p.pin_mode = 'PINNED' AND c.revoked_at IS NULL;
   ```
   Zero linhas → pode editar, ninguém está pinado.
2. **Se houver linhas:** combinar a janela com o dono da integração **antes** de publicar. O 409 é
   imediato após o `PUT` — não há carência.
3. **`PUT` no template.** A versão incrementa (N → N+1).
4. **Re-pinar** cada credencial afetada (UPDATE de 1 linha em `public_api_client_prompt`) ou
   mover para `FOLLOW_ACTIVE`, se a integração aceitar seguir o publicado.
5. **Confirmar** com uma chamada de teste (`prompt_version` na resposta `metadata.hive{}` == o
   novo pin).

## Procedimento alternativo — evitar o 409

Se a integração **não** precisa de asserção de conformidade (mesmo time edita o prompt e opera a
integração — que é o caso que arbitrou o default `FOLLOW_ACTIVE`), mudar a credencial para
`FOLLOW_ACTIVE` **antes** do `PUT`. A resposta passa a ecoar `prompt_version` +
`prompt_fingerprint` (SHA-256 do texto efetivo completo), e o ERP loga qual versão rodou — que é a
propriedade que o `PINNED` existia para entregar, sem o 409 a cada edição.

## O que NÃO fazer

- **Não** recriar a credencial para "destravar": `client_id` novo = provisionamento novo = o ERP
  troca segredo no CI.
- **Não** editar `pinned_version` sem olhar o passo 1: `PINNED` numa versão que nunca existiu dá o
  mesmo 409, e aí o diagnóstico engana.
- **Não** confiar no log do Flyway ou no `validate` como prova de que a migração/subida ocorreu —
  precedente do projeto: `repair()` + jar velho = suporte reportado com schema antigo.

## Dívida relacionada (não paga neste ADR)

Snapshot de verdade (congelar o texto) exige **template imutável-on-activate** — muda o editor de
prompt. Batch próprio, fora do escopo da API pública. Enquanto não existir, `PINNED` é
**tripwire**: detecta o drift, não o previne.
