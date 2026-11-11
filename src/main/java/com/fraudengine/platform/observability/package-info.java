/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/observability/package-info.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Document ownership of lifecycle telemetry and local exporter integration.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, curriculum week 32
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Owned by Platform & Operations; other contexts import the public OperationalTelemetry port.
 *   - Implementation uses Micrometer, OpenTelemetry and Spring; it does not query other contexts tables.
 * ============================================================================
 */
package com.fraudengine.platform.observability;
