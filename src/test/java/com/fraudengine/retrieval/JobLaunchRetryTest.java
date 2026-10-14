/**
 * WEEK 27 — Tests the newly discovered launch conflict at the metadata boundary. A forced
 * serialization conflict is retried once, while an already-completed instance is not retried.
 * Mocking only the repository isolates the launch policy from the separate business step retry
 * semantics.
 */
package com.fraudengine.retrieval;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fraudengine.knowledge.ingestion.RetryingJobLauncher;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.repository.*;
import org.springframework.dao.CannotAcquireLockException;

class JobLaunchRetryTest {
    @Test
    void retriesOnlyTransientMetadataCreationConflict() throws Exception {
        var repository = mock(JobRepository.class);
        var job = mock(Job.class);
        var params = new JobParameters();
        when(job.getName()).thenReturn("metadata-test");
        when(job.getJobParametersValidator()).thenReturn(p -> {});
        var execution = new JobExecution(new JobInstance(1L, "metadata-test"), params);
        execution.setId(1L);
        when(repository.createJobExecution("metadata-test", params))
                .thenThrow(new CannotAcquireLockException("serialization conflict"))
                .thenReturn(execution);
        var launcher = new RetryingJobLauncher();
        launcher.setJobRepository(repository);
        launcher.setTaskExecutor(task -> {});
        launcher.afterPropertiesSet();
        assertThat(launcher.run(job, params)).isSameAs(execution);
        verify(repository, times(2)).createJobExecution("metadata-test", params);
    }

    @Test
    void completedInstanceIsNotRetried() throws Exception {
        var repository = mock(JobRepository.class);
        var job = mock(Job.class);
        var params = new JobParameters();
        when(job.getName()).thenReturn("metadata-test");
        when(job.getJobParametersValidator()).thenReturn(p -> {});
        when(repository.createJobExecution("metadata-test", params))
                .thenThrow(new JobInstanceAlreadyCompleteException("already complete"));
        var launcher = new RetryingJobLauncher();
        launcher.setJobRepository(repository);
        launcher.afterPropertiesSet();
        assertThatThrownBy(() -> launcher.run(job, params))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        verify(repository).createJobExecution("metadata-test", params);
    }
}
