# ============================================================================
# FILE: Dockerfile
# PURPOSE: Build a reproducible Java 21 artifact and run one non-root deployment role.
# OWNER: Platform & Operations
# SINCE: week-26
# RELATED: ADR-004, curriculum week 34
# NOTES: Immutable base inputs, no package installation, no runtime build tools, no embedded credentials or model weights.
# ============================================================================
FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
ARG SOURCE_DATE_EPOCH=1792886400
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package
COPY infra/container/HealthProbe.java /build/HealthProbe.java
COPY infra/container/entrypoint.sh /build/entrypoint.sh
ADD --checksum=sha256:90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /build/aws-rds.pem
RUN javac --release 21 -d /build/probe /build/HealthProbe.java && \
    touch -d "@$SOURCE_DATE_EPOCH" /build/target/fraud-investigation-engine-0.0.1-SNAPSHOT.jar /build/probe/HealthProbe.class /build/entrypoint.sh /build/aws-rds.pem

FROM eclipse-temurin:21-jre-noble@sha256:604429d8095873ed54c1a38bd3e4caf3b7a6c5db3656f827043fe613d3af485a AS runtime
ARG SOURCE_DATE_EPOCH=1792886400
ARG SOURCE_TREE_SHA=unrecorded
LABEL org.opencontainers.image.title="Fraud investigation learning staging" org.fraud.source-tree-sha256="$SOURCE_TREE_SHA"
WORKDIR /app
COPY --from=build --chown=10001:10001 --chmod=0444 /build/target/fraud-investigation-engine-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=build --chmod=0444 /build/probe/HealthProbe.class /app/HealthProbe.class
COPY --from=build --chmod=0555 /build/entrypoint.sh /app/entrypoint.sh
COPY --from=build --chmod=0444 /build/aws-rds.pem /app/aws-rds.pem
USER 10001:10001
ENV FRAUD_ROLE=api FRAUD_PROFILES=staging
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=3 CMD ["java","-cp","/app","HealthProbe"]
ENTRYPOINT ["/app/entrypoint.sh"]
