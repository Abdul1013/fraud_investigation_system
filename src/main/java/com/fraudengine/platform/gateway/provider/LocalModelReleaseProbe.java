/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/provider/LocalModelReleaseProbe.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Verify retained local model identities and bounded inference without database or action capabilities.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This operator-only standalone entry point uses fixed synthetic text; it is not registered as an API or investigator tool.
 *   - The probe shares the model container network namespace to verify loopback sidecar access; no cloud model fallback is available.
 * ============================================================================
 */
package com.fraudengine.platform.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.api.*;
import com.fraudengine.platform.gateway.*;
import java.util.*;

public final class LocalModelReleaseProbe {
  private LocalModelReleaseProbe() {}

  public static void main(String[] args) throws Exception {
    var mapper = new ObjectMapper();
    var prompts = new PromptRegistry(mapper, "investigator-v1");
    var client = new OllamaLocalClient("http://127.0.0.1:11434", mapper);
    var releases = new ArrayList<ReleaseEnvelope>();
    for (String phase : List.of("a", "b"))
      try (var input =
          new org.springframework.core.io.ClassPathResource("releases/release-" + phase + ".json")
              .getInputStream()) {
        var release = mapper.readValue(input, ReleaseEnvelope.class);
        prompts.byReference(release.promptReference());
        String name = release.modelVersion().substring(7, release.modelVersion().lastIndexOf('@'));
        if (!release.modelVersion().equals("ollama:" + name + "@" + client.digest(name)))
          throw new IllegalStateException("Retained model identity mismatch");
        releases.add(release);
      }
    var release = releases.getFirst();
    long started = System.nanoTime();
    var vector =
        new OllamaRetrievalEmbedding(client, "embeddinggemma:week34-v1")
            .pinned(release.embeddingSpace())
            .embed("Synthetic transaction receipt evidence");
    if (vector.length != 768) throw new IllegalStateException("Embedding dimension mismatch");
    var adapter = new OllamaToolCallingAdapter(client, "qwen2.5:week34-b", mapper);
    var messages =
        List.of(
            new ToolCallingPort.Message(
                "system", prompts.byReference(release.promptReference()).text(), null, null),
            new ToolCallingPort.Message(
                "user",
                "Report only this supplied fact: synthetic case status is INVESTIGATING.",
                null,
                null));
    var response = adapter.turnPinned(messages, List.of(), release.modelVersion());
    if (!response.calls().isEmpty())
      throw new IllegalStateException("Probe must not request tools");
    try {
      adapter.turnPinned(
          messages,
          List.of(),
          release.modelVersion().substring(0, release.modelVersion().lastIndexOf('@') + 1)
              + "0".repeat(64));
      throw new IllegalStateException("Changed digest accepted");
    } catch (ModelUnavailableException expected) {
    }
    System.out.println(
        mapper.writeValueAsString(
            Map.of(
                "_purpose",
                "Actual loopback sidecar inference with pinned local weights",
                "release",
                release,
                "retainedReleaseIds",
                releases.stream().map(ReleaseEnvelope::releaseId).toList(),
                "embeddingDimensions",
                vector.length,
                "actualModelVersion",
                response.modelVersion(),
                "usage",
                response.usage(),
                "elapsedMillis",
                (System.nanoTime() - started) / 1_000_000.0,
                "digestMismatchRejected",
                true,
                "consequentialEffects",
                0)));
  }
}
