#!/usr/bin/env bash
# ACC-16 — trava de producao: sem job ativo e cadencia verificavel, bloquear deploy.
set -eu
ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
CRON="${RETENTION_CRON_FILE:-/etc/cron.d/hive-retention}"

fail() { printf 'gate: %s\n' "$1" >&2; exit 1; }

[ -f "$CRON" ] || fail "cron do job de retencao ausente"
grep -q '0 3 \* \* \*' "$CRON" || fail "cadencia diaria as 03:00 ausente"
grep -q 'docker compose' "$CRON" || fail "invocacao via Docker Compose ausente"
grep -q 'langfuse-retention' "$CRON" || fail "servico do job ausente no cron"
grep -q 'RETENTION_DAYS: "30"' "$ROOT/docker/docker-compose-retention.yml" || fail "janela de 30 dias ausente"
grep -q 'hive-retention-job' "$ROOT/docker/docker-compose-retention.yml" || fail "imagem do job ausente"

printf 'gate: job ativo, cadencia 03:00, retencao 30 dias\n'
