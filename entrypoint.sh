#!/bin/sh
set -eu

if [ -z "${SPRING_DATASOURCE_URL:-}" ] && [ -n "${DATABASE_URL:-}" ]; then
  case "$DATABASE_URL" in
    postgres://*|postgresql://*)
      SPRING_DATASOURCE_URL="jdbc:postgresql://${DATABASE_URL#*://}"
      export SPRING_DATASOURCE_URL
      ;;
  esac
fi

exec java -jar app.jar
