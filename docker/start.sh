#!/bin/sh
# Entry point of the API image.
# With STRUCTURAL_MAPPER_EMBEDDED=true the structural mapper listens on 127.0.0.1:8010 inside this container, which is
# where the API looks for it by default (STRUCTURAL_MAPPER_URL), so no extra service or variable is needed.
# Otherwise only the API starts, and plan interpretation stays a preview unless STRUCTURAL_MAPPER_URL points elsewhere.
set -eu

if [ "${STRUCTURAL_MAPPER_EMBEDDED:-false}" = "true" ]; then
  echo "starting the embedded structural mapper on 127.0.0.1:8010"
  ( cd /opt/structural-mapper && exec /opt/venv/bin/uvicorn app:app --host 127.0.0.1 --port 8010 ) &
  # The model and the API share the instance memory, so the JVM gets a smaller share.
  default_java_opts="-XX:MaxRAMPercentage=40"
else
  default_java_opts="-XX:MaxRAMPercentage=75"
fi

# JAVA_OPTS is split on purpose so that several flags can be passed.
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-$default_java_opts} -jar /app/app.jar
