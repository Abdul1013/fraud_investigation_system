package com.fraudengine.knowledge.ingestion.batch;

import com.fraudengine.knowledge.ingestion.report.RunReportWriter;
import org.springframework.batch.core.*;
import org.springframework.stereotype.Component;

/** Success reports run as a final job step so publication failures fail the job. */
@Component
public class IngestionReportListener implements JobExecutionListener {
    private final RunReportWriter reports;
    public IngestionReportListener(RunReportWriter reports) { this.reports = reports; }
    @Override public void afterJob(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.COMPLETED) reports.export(reports.persist(execution, false));
        else reports.exportStored(execution.getId());
    }
}
