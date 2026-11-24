#!/bin/sh
# ============================================================================
# FILE: infra/temporal/schema-entrypoint.sh
# PURPOSE: Migrate Temporal-owned databases separately and initialize its private namespace explicitly.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: infra/terraform/ecs.tf
# NOTES: Contains no credentials. Images run privately and require reviewed deployment inputs.
# ============================================================================
set -eu
umask 077
if [ "${REGISTER_NAMESPACE_ONLY:-false}" = true ]; then
  : "${TEMPORAL_ADDRESS:?Private Temporal address is required}"
  for attempt in $(seq 1 30); do
    if temporal --address "$TEMPORAL_ADDRESS" operator namespace describe --namespace default >/dev/null 2>&1; then exit 0; fi
    if temporal --address "$TEMPORAL_ADDRESS" operator namespace create --namespace default --retention 3d >/dev/null 2>&1; then echo "Private namespace initialized";exit 0;fi
    sleep 1
  done
  temporal --address "$TEMPORAL_ADDRESS" operator namespace describe --namespace default >&2 || true
  echo "Private namespace was not ready within the bounded initialization window" >&2
  exit 1
fi
: "${POSTGRES_SEEDS:?Private database hostname is required}"
: "${POSTGRES_PWD:?Database credential must be injected}"
export SQL_PASSWORD="$POSTGRES_PWD"
tls=true
if [ "${TEMPORAL_LOCAL_LEARNING:-false}" = true ]; then tls=false; fi
for database in temporal temporal_visibility; do
  schema=temporal
  if [ "$database" = temporal_visibility ]; then schema=visibility; fi
  temporal-sql-tool --plugin postgres12 --ep "$POSTGRES_SEEDS" -p 5432 -u temporal_owner --db "$database"     --tls="$tls" --tls-disable-host-verification=false --tls-ca-file /etc/temporal/aws-rds.pem --tls-server-name "$POSTGRES_SEEDS" setup-schema -v 0.0
  temporal-sql-tool --plugin postgres12 --ep "$POSTGRES_SEEDS" -p 5432 -u temporal_owner --db "$database"     --tls="$tls" --tls-disable-host-verification=false --tls-ca-file /etc/temporal/aws-rds.pem --tls-server-name "$POSTGRES_SEEDS" update-schema -d "/etc/temporal/schema/postgresql/v12/$schema/versioned"
done
