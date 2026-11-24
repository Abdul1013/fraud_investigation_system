#!/bin/sh
# ============================================================================
# FILE: infra/temporal/server-entrypoint.sh
# PURPOSE: Render only non-secret server coordinates into private scratch storage.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: infra/terraform/ecs.tf
# NOTES: Contains no credentials. Images run privately and require reviewed deployment inputs.
# ============================================================================
set -eu
umask 077
: "${POSTGRES_SEEDS:?Private database hostname is required}"
: "${POSTGRES_PWD:?Database credential must be injected}"
case "$POSTGRES_SEEDS" in *[!a-zA-Z0-9.-]*) echo "Invalid database hostname" >&2;exit 2;;esac
tls=true
if [ "${TEMPORAL_LOCAL_LEARNING:-false}" = true ]; then tls=false; fi
private_ip=$(getent hosts "$(hostname)" | awk '$1 ~ /^[0-9.]+$/ {print $1;exit}')
test -n "$private_ip"
sed -e "s/__DATABASE_HOST__/$POSTGRES_SEEDS/g" -e "s/__PRIVATE_IP__/$private_ip/g" -e "s/__TLS_ENABLED__/$tls/g" /etc/temporal/staging.yml > /tmp/temporal-staging.yml
# Transport is restricted to API/worker security groups. No public workflow administration or UI.
# This learning staging boundary uses private-network trust; production requires a separate mTLS/authorizer review.
exec temporal-server --config-file /tmp/temporal-staging.yml --allow-no-auth start
