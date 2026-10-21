/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/decision/infrastructure/DecisionService.java
 * CONTEXT:     decision
 * LAYER:       infrastructure
 * PURPOSE:     Owns immutable proposals, human decisions, and atomic simulated execution.
 * ----------------------------------------------------------------------------
 * OWNER:       Decision & Action
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Only propose is called by investigator tools; approval and execution are separate human commands.
 *   - Row locks serialize consumption; idempotency checks occur under the same recommendation lock.
 *   - The simulator, consumption, idempotent result, and AuditApi append share one transaction.
 *   - Basic authentication cannot satisfy StepUpVerifier; isolated tests simulate verified assertions.
 * ============================================================================
 */
package com.fraudengine.decision.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.audit.api.AuditApi;
import com.fraudengine.cases.api.CaseApi;
import com.fraudengine.decision.api.DecisionApi;
import com.fraudengine.decision.api.dto.*;
import com.fraudengine.decision.domain.ApprovalTokenService;
import com.fraudengine.decision.domain.WriteGate;
import com.fraudengine.knowledge.api.KnowledgeApi;
import com.fraudengine.knowledge.api.dto.RetrievalResult;
import com.fraudengine.platform.api.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DecisionService implements DecisionApi {
    private final JdbcTemplate jdbc;
    private final CurrentCaller identity;
    private final StepUpVerifier stepUp;
    private final CaseApi cases;
    private final KnowledgeApi knowledge;
    private final AuditApi audit;
    private final ApprovalTokenService tokens;
    private final ObjectMapper mapper;
    private final WriteGate gate = new WriteGate();

    public DecisionService(JdbcTemplate jdbc, CurrentCaller identity, StepUpVerifier stepUp, CaseApi cases,
            KnowledgeApi knowledge, AuditApi audit, ApprovalTokenService tokens, ObjectMapper mapper) {
        this.jdbc = jdbc; this.identity = identity; this.stepUp = stepUp; this.cases = cases;
        this.knowledge = knowledge; this.audit = audit; this.tokens = tokens; this.mapper = mapper;
    }

    @Override
    public RecommendationDto propose(String caseId, String rationale, RetrievalResult evidence, String modelVersion) {
        var caller = require(Permission.PROPOSE_ACTION);
        requireCase(caseId);
        if (rationale == null || rationale.isBlank() || rationale.length() > 2000
                || modelVersion == null || modelVersion.isBlank() || modelVersion.length() > 300) {
            throw new IllegalArgumentException("Invalid proposal description or model version");
        }
        knowledge.validateCaseEvidence(caseId, evidence);
        var binding = new Binding("binding-v1", caseId, "SIMULATED_ACCOUNT_FREEZE", "simulated:" + caseId,
                Map.of("frozen", true), rationale, evidence, modelVersion,
                modelVersion.startsWith("mcp-client:") ? "mcp-client:prompt-unspecified" : "week28-investigator-v1", 1);
        String json = json(binding), digest = hash(json);
        UUID id = UUID.nameUUIDFromBytes((caller.subject() + ":" + digest).getBytes(StandardCharsets.UTF_8));
        int inserted = jdbc.update("""
                INSERT INTO decision.recommendations(id,case_id,proposer,rationale,binding_json,binding_digest,state)
                VALUES (?,?,?,?,?,?,'PENDING_APPROVAL') ON CONFLICT DO NOTHING
                """, id, caseId, caller.subject(), rationale, json, digest);
        if (inserted == 1) audit.append(caller.subject(), "RECOMMENDATION_CREATED", caseId, id.toString());
        return dto(load(id));
    }

    @Override
    public RecommendationReview review(UUID id) {
        identity.requireAuthenticated();
        var row = load(id);
        requireCase(row.caseId());
        var binding = binding(row);
        knowledge.validateCaseEvidence(row.caseId(), binding.evidence());
        return new RecommendationReview(dto(row), binding.action(), binding.target(), binding.rationale(),
                binding.evidence(), binding.modelVersion(), binding.promptVersion());
    }

    @Override
    public ApprovalTokenDto approve(UUID id) {
        var caller = require(Permission.APPROVE_ACTION);
        var row = load(id);
        requireCase(row.caseId());
        try {
            stepUp.requireVerified(caller.subject());
            boolean expiredApproval = "APPROVED".equals(row.state()) && row.expiresAt() != null
                    && !row.expiresAt().isAfter(Instant.now());
            if ((!"PENDING_APPROVAL".equals(row.state()) && !expiredApproval) || caller.subject().equals(row.proposer())) {
                throw new AccessDeniedException();
            }
            knowledge.validateCaseEvidence(row.caseId(), binding(row).evidence());
        } catch (AccessDeniedException denied) {
            audit.rejected(caller.subject(), "APPROVAL_DENIED", row.caseId(), id.toString());
            throw denied;
        }
        var signed = tokens.issue(id, row.digest(), caller.subject());
        jdbc.update("""
                UPDATE decision.recommendations SET state='APPROVED',version=version+1,
                approval_id=?,approver=?,expires_at=? WHERE id=?
                """, signed.id(), caller.subject(), java.sql.Timestamp.from(signed.expiresAt()), id);
        audit.append(caller.subject(), "APPROVED".equals(row.state()) ? "APPROVAL_RENEWED" : "APPROVAL_GRANTED", row.caseId(), id.toString());
        return new ApprovalTokenDto(id, signed.token(), signed.expiresAt());
    }

    @Override
    public RecommendationDto reject(UUID id) {
        var caller = require(Permission.APPROVE_ACTION);
        var row = load(id);
        requireCase(row.caseId());
        if (!"PENDING_APPROVAL".equals(row.state())) throw new IllegalStateException("Recommendation is not pending");
        jdbc.update("UPDATE decision.recommendations SET state='REJECTED',version=version+1 WHERE id=?", id);
        audit.append(caller.subject(), "RECOMMENDATION_REJECTED", row.caseId(), id.toString());
        return dto(load(id));
    }

    @Override
    public ExecutionResult execute(UUID id, String token, String key) {
        var caller = require(Permission.APPROVE_ACTION);
        if (key == null || key.isBlank() || key.length() > 100) throw new IllegalArgumentException("Invalid idempotency key");
        var row = load(id);
        requireCase(row.caseId());
        String requestDigest = hash(id + ":" + token);
        var existing = jdbc.query("SELECT request_digest,result_json FROM decision.execution_results"
                + " WHERE subject=? AND idempotency_key=?", (rs, n) -> Map.entry(rs.getString(1), rs.getString(2)),
                caller.subject(), key);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().getKey().equals(requestDigest)) throw new IllegalStateException("Idempotency key conflict");
            return read(existing.getFirst().getValue(), ExecutionResult.class);
        }
        try {
            UUID verified = tokens.verify(token, id, row.digest(), caller.subject());
            gate.requireApproved(binding(row).action(), row.state(), row.approvalId(), verified, row.expiresAt());
            if (!caller.subject().equals(row.approver()) || !hash(row.json()).equals(row.digest())) {
                throw new AccessDeniedException();
            }
            knowledge.validateCaseEvidence(row.caseId(), binding(row).evidence());
        } catch (AccessDeniedException denied) {
            audit.rejected(caller.subject(), "EXECUTION_DENIED", row.caseId(), id.toString());
            throw denied;
        }
        jdbc.update("INSERT INTO decision.simulated_accounts(case_id) VALUES (?) ON CONFLICT DO NOTHING", row.caseId());
        jdbc.update("UPDATE decision.simulated_accounts SET frozen=true,freeze_count=1 WHERE case_id=? AND frozen=false", row.caseId());
        jdbc.update("UPDATE decision.recommendations SET state='EXECUTED',version=version+1 WHERE id=?", id);
        var result = new ExecutionResult(id, row.caseId(), true, true);
        jdbc.update("INSERT INTO decision.execution_results VALUES (?,?,?,?,?)",
                caller.subject(), key, requestDigest, id, json(result));
        audit.append(caller.subject(), "SIMULATED_FREEZE_EXECUTED", row.caseId(), id.toString());
        return result;
    }

    private void requireCase(String caseId) {
        // Recommendation rationale and approval metadata are C2 even when source evidence is public.
        if (!"C2".equals(cases.evidenceScope(caseId).classificationCeiling())) throw new AccessDeniedException();
    }

    private CallerContext require(Permission permission) {
        var caller = identity.requireAuthenticated();
        if (!caller.hasPermission(permission)) throw new AccessDeniedException();
        return caller;
    }

    private Row load(UUID id) {
        return jdbc.query("SELECT * FROM decision.recommendations WHERE id=? FOR UPDATE", (rs, n) ->
                new Row(rs.getObject("id", UUID.class), rs.getString("case_id"), rs.getString("proposer"),
                        rs.getString("binding_json"), rs.getString("binding_digest"), rs.getString("state"),
                        rs.getObject("approval_id", UUID.class), rs.getString("approver"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()), id)
                .stream().findFirst().orElseThrow(AccessDeniedException::new);
    }

    private RecommendationDto dto(Row row) { return new RecommendationDto(row.id(), row.caseId(), row.state(), row.digest()); }
    private Binding binding(Row row) { return read(row.json(), Binding.class); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot encode decision binding", error); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot decode decision binding", error); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private record Row(UUID id, String caseId, String proposer, String json, String digest, String state,
            UUID approvalId, String approver, Instant expiresAt) {}
    private record Binding(String bindingVersion, String caseId, String action, String target, Map<String, Boolean> parameters,
            String rationale, RetrievalResult evidence, String modelVersion, String promptVersion, int recommendationVersion) {}
}
