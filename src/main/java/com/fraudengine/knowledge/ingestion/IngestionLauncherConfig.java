package com.fraudengine.knowledge.ingestion;

import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded background execution; database-backed Batch metadata preserves failed-instance restarts. */
@Configuration
public class IngestionLauncherConfig {
    @Bean public ThreadPoolTaskExecutor ingestionExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("knowledge-ingestion-"); return executor;
    }
    @Bean public TaskExecutorJobLauncher ingestionJobLauncher(JobRepository repository, ThreadPoolTaskExecutor ingestionExecutor) throws Exception {
        var launcher = new TaskExecutorJobLauncher(); launcher.setJobRepository(repository);
        launcher.setTaskExecutor(ingestionExecutor); launcher.afterPropertiesSet(); return launcher;
    }
}
