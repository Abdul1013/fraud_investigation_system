# ============================================================================
# FILE: infra/keycloak/Dockerfile
# PURPOSE: Build a PostgreSQL-optimized Keycloak image with unchanged real OTP flows.
# OWNER: Platform & Operations
# SINCE: week-34
# RELATED: infra/terraform/ecs.tf
# NOTES: Contains no credentials. Images run privately and require reviewed deployment inputs.
# ============================================================================
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b AS build
ENV KC_DB=postgres KC_HEALTH_ENABLED=true KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build
FROM quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79572168cb25886b80a8b5b8d4ed4390743e3eecb810538683e209dd6b
COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY --chmod=0444 infra/keycloak/staging-realm.json /opt/keycloak/conf/fraud-staging-realm.json
COPY --chmod=0555 infra/keycloak/staging-entrypoint.sh /opt/keycloak/staging-entrypoint.sh
ADD --checksum=sha256:90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b --chmod=0444 https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /opt/keycloak/conf/aws-rds.pem
USER 1000:1000
ENTRYPOINT ["/opt/keycloak/staging-entrypoint.sh"]
CMD ["start","--optimized","--import-realm"]
