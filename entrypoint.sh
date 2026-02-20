#!/bin/sh
set -eu

# ---------------------------------------------------------------
# Normalize Render's DATABASE_URL into Spring-compatible env vars.
#
# Render provides:  postgresql://USER:PASS@HOST:PORT/DBNAME
# JDBC driver needs: jdbc:postgresql://HOST:PORT/DBNAME
#                    + username and password as separate properties
# ---------------------------------------------------------------
if [ -z "${SPRING_DATASOURCE_URL:-}" ] && [ -n "${DATABASE_URL:-}" ]; then
  case "$DATABASE_URL" in
    postgres://*|postgresql://*)
      # Strip the scheme  →  USER:PASS@HOST:PORT/DBNAME
      WITHOUT_SCHEME="${DATABASE_URL#*://}"

      # Extract userinfo (everything before the first @)
      USERINFO="${WITHOUT_SCHEME%%@*}"

      # Extract host+port+dbname (everything after the first @)
      HOSTPART="${WITHOUT_SCHEME#*@}"

      # Split userinfo into user and password
      DB_USER="${USERINFO%%:*}"
      DB_PASS="${USERINFO#*:}"

      # If no port in HOSTPART, add default 5432
      # HOSTPART is like: host:port/dbname  or  host/dbname
      HOST_AND_PORT="${HOSTPART%%/*}"
      DB_NAME="${HOSTPART#*/}"

      case "$HOST_AND_PORT" in
        *:*) ;; # already has port
        *)   HOSTPART="${HOST_AND_PORT}:5432/${DB_NAME}" ;;
      esac

      SPRING_DATASOURCE_URL="jdbc:postgresql://${HOSTPART}"
      SPRING_DATASOURCE_USERNAME="${DB_USER}"
      SPRING_DATASOURCE_PASSWORD="${DB_PASS}"

      export SPRING_DATASOURCE_URL
      export SPRING_DATASOURCE_USERNAME
      export SPRING_DATASOURCE_PASSWORD

      echo "==> DATABASE_URL parsed successfully"
      echo "==> JDBC URL: jdbc:postgresql://${HOST_AND_PORT}/[dbname]"
      ;;
  esac
fi

exec java -jar app.jar
