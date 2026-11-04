/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/knowledge/infrastructure/SourceTrustController.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Allow only knowledge curators to trust or revoke a precise imported source revision.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - No source text or model can modify this registry; the checksum must match stored lineage.
 *   - Revocation takes effect on the next retrieval/index request, independently of case grants.
 * ============================================================================
 */
package com.fraudengine.knowledge.infrastructure;

import com.fraudengine.platform.api.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@RestController
public class SourceTrustController {
    private final CurrentCaller caller;
    private final JdbcTemplate jdbc;

    public SourceTrustController(CurrentCaller caller, JdbcTemplate jdbc) {
        this.caller = caller;
        this.jdbc = jdbc;
    }

    public record Request(@NotBlank String sourceId, @Min(1) int sourceVersion,
            @Pattern(regexp = "[0-9a-f]{64}") String checksum, boolean revoked) {
    }

    @PostMapping("/v1/knowledge/source-trust")
    @Transactional
    public Map<String, Boolean> update(@Valid @RequestBody Request request) {
        var actor = caller.requireAuthenticated();
        if (!actor.hasPermission(Permission.MANAGE_KNOWLEDGE))
            throw new AccessDeniedException();
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM knowledge.document_registry WHERE source_id=? AND source_version=? AND checksum=? AND classification<>'C3')",
                Boolean.class, request.sourceId(), request.sourceVersion(), request.checksum())))
            throw new AccessDeniedException();
        jdbc.update(
                "INSERT INTO knowledge.trusted_sources(source_id,source_version,checksum,trusted_by,revoked) VALUES (?,?,?,?,?) ON CONFLICT(source_id,source_version) DO UPDATE SET checksum=EXCLUDED.checksum,trusted_by=EXCLUDED.trusted_by,trusted_at=now(),revoked=EXCLUDED.revoked",
                request.sourceId(), request.sourceVersion(), request.checksum(), actor.subject(), request.revoked());
        return Map.of("trusted", !request.revoked());
    }
}
