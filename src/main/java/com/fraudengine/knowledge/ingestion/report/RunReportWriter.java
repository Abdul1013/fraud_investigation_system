package com.fraudengine.knowledge.ingestion.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Database publication is authoritative and transactional; disk reports are exportable artifacts. */
@Component
public class RunReportWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Path directory;
    public RunReportWriter(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${knowledge.reports-directory:reports}") String directory) {
        this.jdbc = jdbc; this.mapper = mapper; this.directory = Path.of(directory);
    }
    @Transactional
    public IngestionRunReport persist(JobExecution execution, boolean success) {
        long instanceId = execution.getJobInstance().getInstanceId();
        Integer version = null;
        var failures = jdbc.query("SELECT source_id,error FROM knowledge.ingestion_failures WHERE job_instance_id=? ORDER BY source_id",
                (rs,n) -> new IngestionRunReport.Failure(rs.getString(1), rs.getString(2)), instanceId);
        for (Throwable failure : execution.getAllFailureExceptions()) {
            Throwable cause = failure;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            failures.add(new IngestionRunReport.Failure("job", cause.getClass().getSimpleName() + ": " + cause.getMessage()));
        }
        String status = success ? (failures.isEmpty() ? "COMPLETED" : "PARTIAL") : "FAILED";
        if (success && failures.isEmpty()) {
            // Serialize promotions. New snapshot = previous published sources + this successful run.
            jdbc.execute("SELECT pg_advisory_xact_lock(260026)");
            version = jdbc.queryForObject("""
                    INSERT INTO knowledge.corpus_versions(run_id,document_count,chunk_count) VALUES (?,0,0)
                    RETURNING version
                    """, Integer.class, execution.getId());
            jdbc.update("""
                    INSERT INTO knowledge.corpus_members(corpus_version,document_id)
                    SELECT ?,id FROM (
                        SELECT DISTINCT ON (d.source_id) d.id FROM knowledge.document_registry d
                        WHERE d.id IN (SELECT document_id FROM knowledge.ingestion_sources WHERE job_instance_id=?)
                           OR d.id IN (SELECT document_id FROM knowledge.corpus_members
                                      WHERE corpus_version=(SELECT MAX(version) FROM knowledge.corpus_versions WHERE version < ?))
                        ORDER BY d.source_id,d.source_version DESC
                    ) selected
                    """, version, instanceId, version);
            jdbc.update("""
                    UPDATE knowledge.corpus_versions SET
                    document_count=(SELECT COUNT(*) FROM knowledge.corpus_members WHERE corpus_version=?),
                    chunk_count=(SELECT COALESCE(SUM(d.chunk_count),0) FROM knowledge.corpus_members m
                                 JOIN knowledge.document_registry d ON d.id=m.document_id WHERE m.corpus_version=?)
                    WHERE version=?
                    """, version, version, version);
        }
        Map<String, IngestionRunReport.SourceTrace> trace = new LinkedHashMap<>();
        var rows = jdbc.queryForList("""
                SELECT d.* FROM knowledge.ingestion_sources s JOIN knowledge.document_registry d ON d.id=s.document_id
                WHERE s.job_instance_id=? ORDER BY d.source_id,d.source_version
                """, instanceId);
        for (var row : rows) {
            List<String> chunks = jdbc.query("SELECT id::text FROM knowledge.chunks_lineage WHERE document_id=? ORDER BY chunk_index",
                    (rs,n) -> rs.getString(1), row.get("id"));
            trace.put(row.get("source_id") + "@" + row.get("source_version"), new IngestionRunReport.SourceTrace(
                    ((Number)row.get("source_version")).intValue(), row.get("doc_type").toString(), row.get("classification").toString(),
                    row.get("checksum").toString(), row.get("raw_uri").toString(), chunks));
        }
        var totals = jdbc.queryForMap("""
                SELECT COUNT(*) AS documents,COALESCE(SUM(chunks_created),0) AS created,
                COALESCE(SUM(chunks_skipped_duplicate),0) AS duplicates
                FROM knowledge.ingestion_sources WHERE job_instance_id=?
                """, instanceId);
        var report = new IngestionRunReport(execution.getId(), instanceId, version,
                execution.getStartTime().toInstant(ZoneOffset.UTC), Instant.now(), status,
                ((Number)totals.get("documents")).intValue(), ((Number)totals.get("created")).intValue(),
                ((Number)totals.get("duplicates")).intValue(), List.copyOf(failures), trace);
        try {
            String json = mapper.writeValueAsString(report);
            jdbc.update("""
                    INSERT INTO knowledge.ingestion_runs(run_id,job_instance_id,corpus_version,started_at,completed_at,status,
                    documents_processed,chunks_created,chunks_skipped_duplicate,report_json) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)
                    ON CONFLICT(run_id) DO UPDATE SET corpus_version=EXCLUDED.corpus_version,status=EXCLUDED.status,
                    completed_at=EXCLUDED.completed_at,report_json=EXCLUDED.report_json
                    """, report.runId(), instanceId, version, java.sql.Timestamp.from(report.startedAt()),
                    java.sql.Timestamp.from(report.completedAt()), status, report.documentsProcessed(), report.chunksCreated(),
                    report.chunksSkippedDuplicate(), json);
            return report;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize run report", e); }
    }
    public void exportStored(long runId) {
        String json = jdbc.queryForObject("SELECT report_json::text FROM knowledge.ingestion_runs WHERE run_id=?", String.class, runId);
        try { export(mapper.readValue(json, IngestionRunReport.class)); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot read persisted report", e); }
    }
    public void export(IngestionRunReport report) {
        try {
            Files.createDirectories(directory);
            mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("ingestion-run-" + report.runId() + ".json").toFile(), report);
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Report {} persisted in DB; disk export failed", report.runId(), e);
        }
    }
}
