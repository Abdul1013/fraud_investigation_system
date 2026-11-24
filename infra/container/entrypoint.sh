#!/bin/sh
# ============================================================================
# FILE: infra/container/entrypoint.sh
# PURPOSE: Select exactly one API, worker, migration or owner-bootstrap process.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: ADR-004, Dockerfile
# NOTES: No secrets enter command arguments; staging is the default profile. No automatic database migration on application roles.
# ============================================================================
set -eu
case "${FRAUD_ROLE:-api}" in
 api|worker)
  exec java -XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar    "--spring.profiles.active=${FRAUD_PROFILES:-staging},${FRAUD_ROLE:-api}" "$@" ;;
 identity-admin)
  exec java -XX:MaxRAMPercentage=65 -cp /app/app.jar -Dloader.main=com.fraudengine.platform.security.PrivateIdentityProvisioner org.springframework.boot.loader.launch.PropertiesLauncher ;;
 model-probe)
  exec java -XX:MaxRAMPercentage=65 -cp /app/app.jar -Dloader.main=com.fraudengine.platform.gateway.provider.LocalModelReleaseProbe org.springframework.boot.loader.launch.PropertiesLauncher ;;
 migrate)
  exec java -XX:MaxRAMPercentage=65 -cp /app/app.jar -Dloader.main=com.fraudengine.platform.config.MigrationMain org.springframework.boot.loader.launch.PropertiesLauncher ;;
 bootstrap)
  exec java -XX:MaxRAMPercentage=65 -cp /app/app.jar -Dloader.main=com.fraudengine.platform.config.DatabaseBootstrapMain org.springframework.boot.loader.launch.PropertiesLauncher ;;
 *) echo "Unknown deployment role" >&2;exit 2 ;;
esac
