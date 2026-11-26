#!/usr/bin/env bash
# Boot de dev: valida stack Langfuse, sobe backend (:8080) e frontend (:5173).
# Credenciais nunca sao impressas. Uso: scripts/start-hive-dev.sh --help
set +x                      # neutraliza xtrace herdado antes de qualquer source
set -euo pipefail

ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
ENV_FILE="${HIVE_LF_ENV_FILE:-$ROOT/docker/.env.hive-langfuse}"
BASE_URL="${HIVE_LF_BASE_URL:-http://localhost:3000}"
TMP_DIR='/tmp/opencode'
BACK_PID="$TMP_DIR/hive-back.pid"
FRONT_PID="$TMP_DIR/hive-front.pid"
BACK_LOG="$TMP_DIR/hive-back.log"
FRONT_LOG="$TMP_DIR/hive-front.log"
TIMEOUT="${HIVE_DEV_TIMEOUT:-120}"
JAVA_HOME_DIR="${JAVA_HOME:-/home/joao/.sdkman/candidates/java/current}"

CONTAINERS=(
  hive-langfuse-langfuse-web-1
  hive-langfuse-langfuse-worker-1
  hive-langfuse-clickhouse-1
  hive-langfuse-minio-1
  hive-langfuse-postgres-1
  hive-langfuse-redis-1
)

fail() { printf 'start-hive-dev: ERRO: %s\n' "$1" >&2; exit 1; }
info() { printf 'start-hive-dev: %s\n' "$1"; }

usage() {
  cat <<'USO'
Uso: scripts/start-hive-dev.sh [--help|--stop]

Valida os 6 containers do projeto hive-langfuse e o health em
/api/public/health (200), carrega docker/.env.hive-langfuse sem exibir
valores, e sobe backend (:8080, /hive/actuator/health) e frontend (:5173)
com nohup. PIDs e logs em /tmp/opencode/hive-{back,front}.{pid,log}.

Opcoes:
  --help  mostra esta ajuda
  --stop  encerra backend e frontend por PID (ps -p antes do kill; sem varredura por nome)

Variaveis:
  HIVE_DEV_TIMEOUT   timeout de espera dos health checks em segundos (default 120)
  HIVE_LF_PK/SK      usadas so se o env nao existir; setup e chamado e falha se ausentes
  HIVE_LF_ENV_FILE   caminho alternativo do env (default docker/.env.hive-langfuse)
  HIVE_LF_BASE_URL   base do Langfuse (default http://localhost:3000)

Regras: nao duplica processo se PID vivo; para se 8080/5173 ja estao LISTEN;
credenciais nunca vao para stdout/stderr/log/PID file.
USO
}

read_pid() { if [ -f "$1" ]; then tr -d '[:space:]' < "$1"; fi; }
pid_alive() { [ -n "${1:-}" ] && ps -p "$1" -o comm= >/dev/null 2>&1; }

stop_pid() {
  local pid="$1" label="$2" pidfile="$3" owner
  [ -n "$pid" ] || return 0
  owner=$(ps -p "$pid" -o comm= 2>/dev/null || true)
  if [ -z "$owner" ]; then
    info "$label: ja encerrado (PID $pid)"
    rm -f -- "$pidfile"
    return 0
  fi
  case "$owner" in
    *java*|*node*|*npm*|*mvn*|*bash*|*sh*)
      # setsid torna o PID lider do grupo: matar o grupo tambem encerra o filho do npm.
      if [ "$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d '[:space:]')" = "$pid" ]; then
        kill -- "-$pid"
      else
        kill "$pid"
      fi
      rm -f -- "$pidfile"
      info "$label: encerrado por PID (dono=$owner)"
      ;;
    *)
      printf 'start-hive-dev: AVISO: %s PID %s dono=%s; nao encerrado\n' "$label" "$pid" "$owner" >&2
      ;;
  esac
}

case "${1:-}" in
  --help|-h)
    [ "$#" -eq 1 ] || fail 'argumentos extras com --help'
    usage
    exit 0
    ;;
  --stop)
    [ "$#" -eq 1 ] || fail 'argumentos extras com --stop'
    stop_pid "$(read_pid "$BACK_PID")" backend "$BACK_PID"
    stop_pid "$(read_pid "$FRONT_PID")" frontend "$FRONT_PID"
    info 'encerramento concluido; logs preservados'
    exit 0
    ;;
  '') ;;
  *) fail 'argumento desconhecido (use --help)' ;;
esac

case "$TIMEOUT" in
  ''|*[!0-9]*) fail 'HIVE_DEV_TIMEOUT deve ser inteiro positivo' ;;
esac
[ "$TIMEOUT" -gt 0 ] || fail 'HIVE_DEV_TIMEOUT deve ser inteiro positivo'

# Idempotencia antes de qualquer checagem: PID vivo => nao duplicar.
bp="$(read_pid "$BACK_PID")"
fp="$(read_pid "$FRONT_PID")"
if pid_alive "$bp" || pid_alive "$fp"; then
  info "ja rodando (back=${bp:-nenhum} front=${fp:-nenhum}); nada iniciado. Use --stop"
  exit 0
fi

validate_stack() {
  local c status missing=0 code
  command -v docker >/dev/null 2>&1 || fail 'docker ausente'
  docker info >/dev/null 2>&1 || fail 'docker daemon inalcancavel'
  for c in "${CONTAINERS[@]}"; do
    status=$(docker ps --filter "name=^${c}$" --format '{{.Status}}' 2>/dev/null || true)
    case "$status" in
      Up*) ;;
      *) printf 'start-hive-dev: container ausente/parado: %s\n' "$c" >&2; missing=1 ;;
    esac
  done
  [ "$missing" -eq 0 ] || fail 'stack hive-langfuse incompleta (6 containers Up exigidos)'
  code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 5 \
    "${BASE_URL%/}/api/public/health" 2>/dev/null || true)
  [ "$code" = '200' ] || fail "Langfuse health HTTP ${code:-000} (esperado 200)"
  info 'Langfuse: 6 containers Up; health 200'
}

resolve_env() {
  if [ ! -f "$ENV_FILE" ]; then
    [ -n "${HIVE_LF_PK:-}" ] || fail 'env ausente e HIVE_LF_PK nao definido'
    [ -n "${HIVE_LF_SK:-}" ] || fail 'env ausente e HIVE_LF_SK nao definido'
    info 'env ausente: chamando setup (credenciais redatadas)'
    bash "$ROOT/scripts/setup-hive-langfuse.sh" || fail 'setup-hive-langfuse.sh falhou'
    [ -f "$ENV_FILE" ] || fail 'setup nao gerou o env'
  fi
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  [ -n "${HIVE_TELEMETRY_ENABLED:-}" ] || fail 'env sem HIVE_TELEMETRY_ENABLED'
  [ -n "${LANGFUSE_OTLP_TRACES_ENDPOINT:-}" ] || fail 'env sem LANGFUSE_OTLP_TRACES_ENDPOINT'
  [ -n "${LANGFUSE_INGESTION_PK:-}" ] || fail 'env sem LANGFUSE_INGESTION_PK'
  [ -n "${LANGFUSE_INGESTION_SK:-}" ] || fail 'env sem LANGFUSE_INGESTION_SK'
  info "env carregado: $ENV_FILE (4 vars, valores nao exibidos)"
}

port_listening() {
  local out
  out=$(ss -H -ltn "( sport = :$1 )" 2>/dev/null || true)
  [ -n "$out" ]
}

wait_http() {
  local label="$1" url="$2" logfile="$3" pid="${4:-}" code deadline=$(( $(date +%s) + TIMEOUT ))
  while :; do
    code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 3 "$url" 2>/dev/null || true)
    if [ "$code" = '200' ]; then
      info "$label: HTTP 200"
      return 0
    fi
    if [ -n "$pid" ] && ! pid_alive "$pid"; then
      printf 'start-hive-dev: ERRO: %s terminou antes do health (ver %s)\n' "$label" "$logfile" >&2
      return 1
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      printf 'start-hive-dev: ERRO: %s timeout %ss (ultimo HTTP %s; ver %s)\n' \
        "$label" "$TIMEOUT" "${code:-000}" "$logfile" >&2
      return 1
    fi
    sleep 1
  done
}

validate_stack
resolve_env

command -v ss >/dev/null 2>&1 || fail 'ss ausente (impossivel checar portas)'
if port_listening 8080; then fail 'porta 8080 ja esta LISTEN (use --stop ou libere a porta)'; fi
if port_listening 5173; then fail 'porta 5173 ja esta LISTEN (use --stop ou libere a porta)'; fi

[ -x "$JAVA_HOME_DIR/bin/java" ] || fail "Java ausente em $JAVA_HOME_DIR/bin/java"
export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$TMP_DIR"

setsid nohup mvn -f "$ROOT/backend/pom.xml" -pl bootstrap spring-boot:run \
  >"$BACK_LOG" 2>&1 &
bp=$!
printf '%s\n' "$bp" > "$BACK_PID"

(
  cd "$ROOT/frontend" || exit 1
  setsid nohup npm run dev >"$FRONT_LOG" 2>&1 &
  printf '%s\n' "$!" > "$FRONT_PID"
)
fp="$(read_pid "$FRONT_PID")"

if ! wait_http 'backend' 'http://127.0.0.1:8080/hive/actuator/health' "$BACK_LOG" "$bp" \
  || ! wait_http 'frontend' 'http://127.0.0.1:5173/' "$FRONT_LOG" "$fp"; then
  stop_pid "$bp" backend "$BACK_PID"
  stop_pid "$fp" frontend "$FRONT_PID"
  fail 'boot falhou; processos iniciados foram encerrados'
fi

if grep -qE 'pk-lf-|sk-lf-|Basic [[:alnum:]+/=]+' "$BACK_LOG" "$FRONT_LOG" 2>/dev/null; then
  stop_pid "$bp" backend "$BACK_PID"
  stop_pid "$fp" frontend "$FRONT_PID"
  fail 'credencial detectada em log; processos encerrados'
fi

info "backend UP  PID=$bp  log=$BACK_LOG"
info "frontend UP PID=$fp  log=$FRONT_LOG"
printf 'start-hive-dev: uso: scripts/start-hive-dev.sh | scripts/start-hive-dev.sh --stop | HIVE_DEV_TIMEOUT=180 scripts/start-hive-dev.sh\n'
printf 'start-hive-dev: riscos: Postgres/Kafka do Hive nao validados aqui; timeout %ss; --stop encerra apenas por PID\n' "$TIMEOUT"
