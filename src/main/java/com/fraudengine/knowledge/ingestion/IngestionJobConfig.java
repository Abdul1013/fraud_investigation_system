package com.fraudengine.knowledge.ingestion;

import com.fraudengine.knowledge.ingestion.batch.*;
import com.fraudengine.knowledge.ingestion.report.RunReportWriter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.repeat.RepeatStatus;

/** One source revision per transaction; restarted executions resume at committed source boundaries. */
@Configuration
public class IngestionJobConfig {
    @Bean public Job knowledgeIngestionJob(JobRepository repository, Step ingestionStep, Step reportStep,
            IngestionReportListener listener) {
        return new JobBuilder("knowledgeIngestionJob",repository).listener(listener).start(ingestionStep).next(reportStep).build();
    }
    @Bean @StepScope public IngestionBatchReader ingestionReader(
            @Value("#{jobParameters['corpusRoot'] ?: 'classpath:corpus/'}") String root) {
        return new IngestionBatchReader(root);
    }
    @Bean public IngestionProcessor ingestionProcessor() { return new IngestionProcessor(); }
    @Bean @StepScope public IngestionWriter ingestionWriter(VectorStore vectors, JdbcTemplate jdbc,
            @Value("#{stepExecution.jobExecution.jobInstance.instanceId}") Long instanceId,
            @Value("${knowledge.embedding-provider:local}") String provider) {
        return new IngestionWriter(vectors,jdbc,instanceId,provider);
    }
    @Bean @StepScope public IngestionSkipListener ingestionSkips(JdbcTemplate jdbc,
            @Value("#{stepExecution.jobExecution.jobInstance.instanceId}") Long instanceId) {
        return new IngestionSkipListener(jdbc,instanceId);
    }
    @Bean public Step ingestionStep(JobRepository repository, PlatformTransactionManager transactions,
            IngestionBatchReader ingestionReader, IngestionProcessor processor, IngestionWriter writer,
            IngestionSkipListener skips) {
        var backoff = new org.springframework.retry.backoff.ExponentialBackOffPolicy();
        backoff.setInitialInterval(100); backoff.setMaxInterval(1000);
        return new StepBuilder("ingestionStep",repository).<DocumentBatch,DocumentBatch>chunk(1,transactions)
                .reader(ingestionReader).processor(processor).writer(writer).faultTolerant()
                .retryLimit(3).retry(TransientDataAccessException.class)
                .retry(org.springframework.ai.retry.TransientAiException.class).backOffPolicy(backoff)
                .skipLimit(50).skip(IllegalArgumentException.class).listener(skips).build();
    }
    @Bean public Step reportStep(JobRepository repository, PlatformTransactionManager transactions, RunReportWriter reports) {
        return new StepBuilder("reportStep",repository).tasklet((contribution, context) -> {
            reports.persist(contribution.getStepExecution().getJobExecution(),true);
            return RepeatStatus.FINISHED;
        }, transactions).build();
    }
}
