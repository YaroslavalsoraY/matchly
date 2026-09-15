#!/usr/bin/env bash
# Запуск Matchly на Linux/macOS: сборка и старт через Maven Wrapper.
# Требуется JDK 21 (переменная JAVA_HOME или java в PATH) и доступный PostgreSQL (см. README).
set -euo pipefail
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.jdks" ]; then
  # берём самый свежий JDK 21 из ~/.jdks, если JAVA_HOME не задан
  cand=$(ls -d "$HOME"/.jdks/jdk-21* 2>/dev/null | sort | tail -1 || true)
  [ -n "$cand" ] && export JAVA_HOME="$cand" && export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version
exec ./mvnw -q spring-boot:run "$@"
