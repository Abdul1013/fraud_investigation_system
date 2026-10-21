/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/audit/api/dto/AuditEventDto.java
 * CONTEXT:     audit
 * LAYER:       api
 * PURPOSE:     Return ordered audit metadata without evidence, credentials, or token bodies.
 * ----------------------------------------------------------------------------
 * OWNER:       Audit, Reporting & Compliance
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - A sequence cursor permits replay without invoking a model or executor.
 *   - Even metadata is treated as C2 and requires an explicit audit capability and case grant.
 * ============================================================================
 */
package com.fraudengine.audit.api.dto;

import java.time.Instant;

public record AuditEventDto(long sequence, Instant occurredAt, String subject, String type, String referenceId) {}
