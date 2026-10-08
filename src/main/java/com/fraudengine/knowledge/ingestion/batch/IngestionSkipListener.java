package com.fraudengine.knowledge.ingestion.batch;

import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Skipped sources remain visible; partial runs are never published as complete
 * corpora.
 */
public class IngestionSkipListener implements SkipListener<DocumentBatch, DocumentBatch> {
    private final JdbcTemplate jdbc;
    private final long instanceId;

    public IngestionSkipListener(JdbcTemplate jdbc, long instanceId) {
        this.jdbc = jdbc;
        this.instanceId = instanceId;
    }

    private void record(String source, Throwable failure) {
        jdbc.update(
                "INSERT INTO knowledge.ingestion_failures(job_instance_id,source_id,error) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                instanceId, source, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    @Override
    public void onSkipInRead(Throwable t) {
        record(t instanceof MalformedSourceException source ? source.sourceId() : "reader", t);
    }

    @Override
    public void onSkipInProcess(DocumentBatch item, Throwable t) {
        record(item.sourceId(), t);
    }

    @Override
    public void onSkipInWrite(DocumentBatch item, Throwable t) {
        record(item.sourceId(), t);
    }
}
