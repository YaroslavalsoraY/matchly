#!/usr/bin/env bash
# Управление локальным пользовательским кластером PostgreSQL 16 (без root и без Docker).
# Кластер живёт в ~/pgdata, слушает localhost:5432, unix-сокет в /tmp.
# Суперпользователь: postgres / postgres (по TCP - пароль, через сокет - trust).
#
#   infra/pg-local.sh start      # запустить сервер
#   infra/pg-local.sh stop       # остановить
#   infra/pg-local.sh status     # состояние
#   infra/pg-local.sh psql [...] # консоль psql от postgres
set -euo pipefail

PGBIN="${PGBIN:-/usr/lib/postgresql/16/bin}"
PGDATA="${PGDATA:-$HOME/pgdata}"
LOG="$PGDATA/server.log"

case "${1:-status}" in
  start)   "$PGBIN/pg_ctl" -D "$PGDATA" -l "$LOG" start ;;
  stop)    "$PGBIN/pg_ctl" -D "$PGDATA" stop ;;
  restart) "$PGBIN/pg_ctl" -D "$PGDATA" -l "$LOG" restart ;;
  status)  "$PGBIN/pg_ctl" -D "$PGDATA" status || true
           "$PGBIN/pg_isready" -h localhost -p 5432 || true ;;
  psql)    shift; psql -h /tmp -U postgres "$@" ;;
  *) echo "usage: $0 {start|stop|restart|status|psql [args]}" >&2; exit 2 ;;
esac
