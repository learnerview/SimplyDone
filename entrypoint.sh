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

# ---------------------------------------------------------------
# Normalize Render's REDIS_URL into Spring-compatible properties.
#
# Render provides:  redis://host:port        (no auth, internal Valkey)
#               or  redis://:password@host:port
#               or  redis://default:password@host:port
#               or  rediss://... (TLS external URL)
# Spring Boot reads: SPRING_DATA_REDIS_HOST, _PORT, _PASSWORD, _SSL_ENABLED
# ---------------------------------------------------------------
if [ -n "${REDIS_URL:-}" ]; then
  # Detect TLS from scheme
  case "$REDIS_URL" in
    rediss://*) REDIS_SSL=true  ;;
    *)          REDIS_SSL=false ;;
  esac

  # Strip the scheme
  REDIS_WITHOUT_SCHEME="${REDIS_URL#*://}"

  # Strip optional /database-number suffix
  REDIS_WITHOUT_SCHEME="${REDIS_WITHOUT_SCHEME%%/*}"

  # Detect credentials (@)
  case "$REDIS_WITHOUT_SCHEME" in
    *@*)
      REDIS_USERINFO="${REDIS_WITHOUT_SCHEME%%@*}"
      REDIS_HOSTPART="${REDIS_WITHOUT_SCHEME##*@}"
      # Password is everything after the first ':' in userinfo (skips empty/default username)
      case "$REDIS_USERINFO" in
        *:*) REDIS_PASSWORD="${REDIS_USERINFO#*:}" ;;
        *)   REDIS_PASSWORD="$REDIS_USERINFO"       ;;
      esac
      ;;
    *)
      REDIS_HOSTPART="$REDIS_WITHOUT_SCHEME"
      REDIS_PASSWORD=""
      ;;
  esac

  REDIS_HOST="${REDIS_HOSTPART%:*}"
  REDIS_PORT="${REDIS_HOSTPART##*:}"
  # If no colon was found, port equals host – apply default
  [ "$REDIS_PORT" = "$REDIS_HOST" ] && REDIS_PORT="6379"

  SPRING_DATA_REDIS_HOST="$REDIS_HOST"
  SPRING_DATA_REDIS_PORT="$REDIS_PORT"
  SPRING_DATA_REDIS_SSL_ENABLED="$REDIS_SSL"

  export SPRING_DATA_REDIS_HOST
  export SPRING_DATA_REDIS_PORT
  export SPRING_DATA_REDIS_SSL_ENABLED

  if [ -n "$REDIS_PASSWORD" ]; then
    SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD"
    export SPRING_DATA_REDIS_PASSWORD
  fi

  echo "==> REDIS_URL parsed successfully"
  echo "==> Redis host: ${REDIS_HOST}, port: ${REDIS_PORT}, ssl: ${REDIS_SSL}"
fi

exec java -jar app.jar
