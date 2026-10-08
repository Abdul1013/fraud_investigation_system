package com.fraudengine.knowledge.ingestion.report;

import java.time.Instant;
import java.util.*;

/** Counts are source revisions and actual text chunks, never Batch write counts. */
public record IngestionRunReport(long runId, long jobInstanceId, Integer corpusVersion,
        Instant startedAt, Instant completedAt, String status, int documentsProcessed,
        int chunksCreated, int chunksSkippedDuplicate, List<Failure> failures,
        Map<String, SourceTrace> sourceTraceability) {
    public record Failure(String sourceId, String error) { }
    public record SourceTrace(int sourceVersion, String docType, String classification,
            String checksum, String rawUri, List<String> chunkIds) { }
}
