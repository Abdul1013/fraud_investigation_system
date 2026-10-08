package com.fraudengine.knowledge.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.core.repository.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Local learning API: triggers use a server-selected corpus, never caller-supplied filesystem paths. */
@RestController
@RequestMapping("/v1/knowledge")
public class KnowledgeController {
    private final JobLauncher launcher;
    private final Job job;
    private final JobExplorer explorer;
    private final JdbcTemplate jdbc;
    private final String corpusRoot;
    public KnowledgeController(@Qualifier("ingestionJobLauncher") JobLauncher launcher, Job knowledgeIngestionJob,
            JobExplorer explorer, JdbcTemplate jdbc, @Value("${knowledge.corpus-root:classpath:corpus/}") String root) {
        this.launcher=launcher; this.job=knowledgeIngestionJob; this.explorer=explorer; this.jdbc=jdbc; this.corpusRoot=root;
    }
    public record StartRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String runKey) { }
    @PostMapping("/ingestions")
    public ResponseEntity<Map<String,Object>> start(@Valid @RequestBody StartRequest request) throws Exception {
        var execution = launcher.run(job, new JobParametersBuilder().addString("runKey", request.runKey())
                .addString("corpusRoot", corpusRoot).toJobParameters());
        return ResponseEntity.accepted().body(Map.of("runId",execution.getId(),"status",execution.getStatus().name()));
    }
    @PostMapping("/ingestions/{runId}/restart")
    public ResponseEntity<Map<String,Object>> restart(@PathVariable long runId) throws Exception {
        var old = requireExecution(runId);
        if (old.getStatus() != BatchStatus.FAILED && old.getStatus() != BatchStatus.STOPPED)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Only failed/stopped runs can restart");
        var execution = launcher.run(job, old.getJobParameters());
        return ResponseEntity.accepted().body(Map.of("runId",execution.getId(),"status",execution.getStatus().name()));
    }
    @GetMapping("/ingestions/{runId}")
    public Map<String,Object> status(@PathVariable long runId) {
        var execution = requireExecution(runId);
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("runId",runId); response.put("jobInstanceId",execution.getJobInstance().getInstanceId());
        response.put("status",execution.getStatus().name());
        var reports = jdbc.queryForList("SELECT report_json::text FROM knowledge.ingestion_runs WHERE run_id=?",String.class,runId);
        if (!reports.isEmpty()) {
            try { response.put("report",new com.fasterxml.jackson.databind.ObjectMapper().readTree(reports.getFirst())); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        return response;
    }
    @GetMapping("/corpus/versions")
    public List<Map<String,Object>> versions() {
        return jdbc.queryForList("SELECT * FROM knowledge.corpus_versions ORDER BY version DESC");
    }
    private JobExecution requireExecution(long id) {
        var execution = explorer.getJobExecution(id);
        if (execution == null || !execution.getJobInstance().getJobName().equals(job.getName()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown ingestion run");
        return execution;
    }
    @ExceptionHandler({JobInstanceAlreadyCompleteException.class, JobExecutionAlreadyRunningException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict(Exception e) { return Map.of("error",e.getMessage()); }
}
