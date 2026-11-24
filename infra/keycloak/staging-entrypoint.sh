#!/bin/sh
# ============================================================================
# FILE: infra/keycloak/staging-entrypoint.sh
# PURPOSE: Copy public realm configuration into private scratch before starting optimized Keycloak.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: infra/terraform/ecs.tf
# NOTES: Contains no credentials. Images run privately and require reviewed deployment inputs.
# ============================================================================
set -eu
umask 077
mkdir -p /opt/keycloak/data/import
cp /opt/keycloak/conf/fraud-staging-realm.json /opt/keycloak/data/import/fraud-realm.json
exec /opt/keycloak/bin/kc.sh "$@"
