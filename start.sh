#!/usr/bin/env bash
# Единая точка входа Matchly для Linux и macOS.
#
#   ./start.sh            локальный режим: проверить Java 21 и PostgreSQL, при отсутствии базы поднять её в Docker,
#                         затем запустить приложение (бэкенд + интерфейс) на http://localhost:8080
#   ./start.sh --docker   всё в Docker: собрать образ и поднять базу и приложение одной командой
#   ./start.sh --stop     остановить контейнеры Docker
#   ./start.sh --test     прогнать автотесты (PostgreSQL не нужен)
#   ./start.sh --help     эта справка
#
# Настройки берутся из файла .env (см. .env.example) или переменных окружения.
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;35m[matchly]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[matchly]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[matchly] %s\033[0m\n' "$*" >&2; exit 1; }
has()  { command -v "$1" >/dev/null 2>&1; }

usage() { sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; }

load_env() {
  if [ -f .env ]; then
    set -a; . ./.env; set +a
  fi
  DB_HOST="${MATCHLY_DB_HOST:-localhost}"
  DB_PORT="${MATCHLY_DB_PORT:-5432}"
  DB_NAME="${MATCHLY_DB_NAME:-matchly_db}"
}

docker_ready() { has docker && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; }

ensure_java() {
  if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
    if ! has java; then
      local candidate
      candidate=$(ls -d "$HOME"/.jdks/jdk-21* 2>/dev/null | sort | tail -1 || true)
      [ -n "$candidate" ] && export JAVA_HOME="$candidate" && export PATH="$JAVA_HOME/bin:$PATH"
    fi
  else
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  has java || die "Java не найдена. Установите JDK 21 (см. README.md, шаг 1) или используйте ./start.sh --docker"
  local version
  version=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
  [ "$version" -ge 21 ] 2>/dev/null || die "Нужна Java 21 или новее, найдена версия $version"
  log "Java $version найдена"
}

db_reachable() { (exec 3<>"/dev/tcp/${DB_HOST}/${DB_PORT}") 2>/dev/null; }

wait_healthy() {
  local container=$1 tries=${2:-40}
  for _ in $(seq 1 "$tries"); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null || echo starting)
    [ "$status" = healthy ] && return 0
    sleep 1
  done
  return 1
}

ensure_database() {
  if db_reachable; then
    log "PostgreSQL доступен на ${DB_HOST}:${DB_PORT}"
    return
  fi
  warn "PostgreSQL не отвечает на ${DB_HOST}:${DB_PORT}"
  if docker_ready; then
    log "Поднимаю базу в Docker (docker compose up -d db)"
    docker compose up -d db
    wait_healthy matchly-db || die "База в Docker не стала здоровой за 40 секунд: docker compose logs db"
    log "База готова"
  elif [ -x infra/pg-local.sh ] && [ -d "${PGDATA:-$HOME/pgdata}" ]; then
    log "Запускаю локальный пользовательский кластер PostgreSQL (infra/pg-local.sh start)"
    infra/pg-local.sh start
    db_reachable || die "Кластер не запустился"
  else
    die "Нет ни PostgreSQL на ${DB_HOST}:${DB_PORT}, ни Docker. Установите одно из них (README.md, шаг 2) и повторите."
  fi
}

load_env
case "${1:-}" in
  -h|--help) usage; exit 0 ;;
  --stop)    docker_ready || die "Docker недоступен"; docker compose down; exit 0 ;;
  --docker)
    docker_ready || die "Docker недоступен. Установите Docker (Desktop) и убедитесь, что текущий пользователь имеет к нему доступ."
    log "Собираю образ и запускаю базу и приложение. Интерфейс: http://localhost:${MATCHLY_PORT:-8080}"
    exec docker compose up --build ;;
  --test)    ensure_java; exec ./mvnw test ;;
  "") ;;
  *) die "Неизвестный параметр: $1 (см. ./start.sh --help)" ;;
esac

ensure_java
ensure_database
export MATCHLY_DB_URL="${MATCHLY_DB_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}}"
log "Запускаю приложение: ${MATCHLY_DB_URL}"
log "Интерфейс: http://localhost:${MATCHLY_PORT:-8080}   Swagger: http://localhost:${MATCHLY_PORT:-8080}/swagger-ui.html"
exec ./mvnw -q spring-boot:run
