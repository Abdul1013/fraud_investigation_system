/**
 *  Opt-in command-line operational evidence: ingest the bundled corpus, index it, evaluate
 * both strategies, then close the application. Normal HTTP startup never runs this expensive path.
 * Use a fresh disposable verification database for comparable reports; the runner does not reset or
 * mutate older corpus evidence. This produces evidence without introducing the broader CI evaluation release gate.
 */
package com.fraudengine.knowledge.retrieval;

import org.springframework.batch.core.*;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "knowledge.eval.enabled", havingValue = "true")
public class RetrievalEvaluationRunner implements ApplicationRunner {
    private final Job job;
    private final JobRepository jobs;
    private final RetrievalIndexService index;
    private final RetrievalEvaluator evaluator;
    private final ConfigurableApplicationContext context;
    private final String dataset, output;

    public RetrievalEvaluationRunner(
            Job knowledgeIngestionJob,
            JobRepository jobs,
            RetrievalIndexService index,
            RetrievalEvaluator evaluator,
            ConfigurableApplicationContext context,
            @Value("${knowledge.eval.dataset:eval/datasets/week27-retrieval.json}") String dataset,
            @Value("${knowledge.eval.output:eval/reports/week27}") String output) {
        this.job = knowledgeIngestionJob;
        this.jobs = jobs;
        this.index = index;
        this.evaluator = evaluator;
        this.context = context;
        this.dataset = dataset;
        this.output = output;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        var launcher = new com.fraudengine.knowledge.ingestion.RetryingJobLauncher();
        launcher.setJobRepository(jobs);
        launcher.afterPropertiesSet();
        var execution =
                launcher.run(
                        job,
                        new JobParametersBuilder()
                                .addString("corpusRoot", "classpath:corpus/")
                                .addString("evaluationRun", java.util.UUID.randomUUID().toString())
                                .toJobParameters());
        if (execution.getStatus() != BatchStatus.COMPLETED)
            throw new IllegalStateException("Evaluation ingestion failed");
        var indexed = index.index(null);
        evaluator.evaluate(Path.of(dataset), indexed.corpusVersion(), 5, Path.of(output));
        context.close();
    }
}
