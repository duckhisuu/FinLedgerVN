#!/bin/sh
set -e

if [ -z "$SPRING_DATASOURCE_URL" ] && [ -n "$DATABASE_URL" ]; then
  proto_stripped="${DATABASE_URL#*://}"
  case "$proto_stripped" in
    *@*)
      creds="${proto_stripped%%@*}"
      hostpart="${proto_stripped#*@}"
      export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${creds%%:*}}"
      if [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
        case "$creds" in
          *:*) export SPRING_DATASOURCE_PASSWORD="${creds#*:}" ;;
        esac
      fi
      ;;
    *)
      hostpart="$proto_stripped"
      ;;
  esac
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${hostpart}"
fi

if [ -z "$SPRING_DATA_REDIS_URL" ] && [ -n "$REDIS_URL" ]; then
  export SPRING_DATA_REDIS_URL="$REDIS_URL"
fi

exec java ${JAVA_OPTS:-"-Xmx512m -Xms256m"} -jar /app/app.jar
