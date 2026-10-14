/**
 *  Retry PostgreSQL serialization conflicts during Batch job-metadata creation. The real
 * concurrent-ingestion regression exposed a transient SERIALIZABLE conflict before a job starts. We
 * preserve Batch's isolation and uniqueness checks rather than lowering them; only the lock
 * exception is retried, at most three attempts with a short backoff. Running/completed instance
 * errors are never retried. Step-level business failures still use the job's existing retry and
 * restart policy.
 */
package com.fraudengine.knowledge.ingestion;

import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.*;
import org.springframework.dao.CannotAcquireLockException;

public class RetryingJobLauncher extends TaskExecutorJobLauncher {
    @Override
    public JobExecution run(Job job, JobParameters parameters)
            throws JobExecutionAlreadyRunningException,
            JobRestartException,
            JobInstanceAlreadyCompleteException,
            JobParametersInvalidException {
        for (int attempt = 0;; attempt++) {
            try {
                return super.run(job, parameters);
            } catch (CannotAcquireLockException conflict) {
                if (attempt >= 2)
                    throw conflict;
                try {
                    Thread.sleep(100L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new JobRestartException(
                            "Interrupted while retrying job metadata creation", e);
                }
            }
        }
    }
}
