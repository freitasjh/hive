#!/usr/bin/env bash
# ACC-07 — IT REAL de 403. So executa com ambiente fornecido pelo operador; sem ele, a inferencia
# "Full access" do ADR-004 permanece PENDENTE e NAO deve ser promovida a contrato.
set -eu
: "${LANGFUSE_REAL_BASE_URL:?URL real do Langfuse}"
: "${APPLICATION_AUTH:?Basic da chave Application (cofre)}"
: "${VIEWER_AUTH:?Basic da chave Viewer (cofre)}"
: "${FULL_AUTH:?Basic da chave Full access (cofre)}"
: "${CUTOFF:?cutoff ISO-8601}"

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
base="${LANGFUSE_REAL_BASE_URL%/}"
read_url="$base/api/public/v2/observations?isRootObservation=true&fields=core,basic&toStartTime=$CUTOFF"
delete_url="$base/api/public/traces"

status() { curl -sS -o "$tmp" -w '%{http_code}' "$@"; }

# Leitura com Application: negada e sem traceId no corpo.
code=$(status -H "Authorization: Basic $APPLICATION_AUTH" "$read_url")
[ "$code" = "403" ] || { printf 'FALHA: leitura com Application retornou %s\n' "$code" >&2; exit 1; }
! grep -q 'traceId' "$tmp" || { printf 'FALHA: corpo de negacao contem traceId\n' >&2; exit 1; }

# Delete com Application e Viewer: 403, nenhum trace removido.
for auth in "$APPLICATION_AUTH" "$VIEWER_AUTH"; do
  code=$(status -X DELETE -H "Authorization: Basic $auth" -H 'Content-Type: application/json' \
    -d '{"traceIds":["000000000000000000000000"]}' "$delete_url")
  [ "$code" = "403" ] || { printf 'FALHA: DELETE com chave nao-admin retornou %s\n' "$code" >&2; exit 1; }
done

# Leitura com Viewer: permitida.
code=$(status -H "Authorization: Basic $VIEWER_AUTH" "$read_url")
[ "$code" = "200" ] || { printf 'FALHA: leitura com Viewer retornou %s\n' "$code" >&2; exit 1; }

# Delete com Full access: confirma ou invalida a inferencia do ADR.
code=$(status -X DELETE -H "Authorization: Basic $FULL_AUTH" -H 'Content-Type: application/json' \
  -d '{"traceIds":["000000000000000000000000"]}' "$delete_url")
[ "$code" = "200" ] || { printf 'FALHA: DELETE com Full access retornou %s — voltar ao architect\n' "$code" >&2; exit 1; }

printf 'IT real: 403 para Application/Viewer no DELETE; Full access aceita; inferencia confirmada\n'
