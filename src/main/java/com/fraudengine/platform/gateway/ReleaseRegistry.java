/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/ReleaseRegistry.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Load bounded immutable release resources and register their exact content transactionally.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - An empty path preserves explicit local learning compatibility; deployment profiles require a configured release.
 *   - Atomic local pointer replacement or a new container configuration activates a release. Old database envelopes do not change.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.platform.api.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class ReleaseRegistry implements ReleaseCatalog {
  private final String path;
  private final ResourceLoader resources;
  private final ObjectMapper mapper;
  private final PromptCatalog prompts;
  private final JdbcTemplate jdbc;

  public ReleaseRegistry(
      @Value("${release.active-file:}") String path,
      ResourceLoader resources,
      ObjectMapper mapper,
      PromptCatalog prompts,
      JdbcTemplate jdbc) {
    this.path = path;
    this.resources = resources;
    this.mapper = mapper;
    this.prompts = prompts;
    this.jdbc = jdbc;
  }

  @Override
  public ReleaseEnvelope active() {
    if (path.isBlank()) return null;
    try (var input = resources.getResource(path).getInputStream()) {
      byte[] bytes = input.readNBytes(65537);
      if (bytes.length > 65536) throw new IllegalArgumentException("Release exceeds size bound");
      var envelope = mapper.readValue(bytes, ReleaseEnvelope.class);
      prompts.byReference(envelope.promptReference());
      return envelope;
    } catch (java.io.IOException failed) {
      throw new IllegalStateException("Cannot read configured AI release", failed);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(ReleaseEnvelope envelope) {
    try {
      String body = mapper.writeValueAsString(envelope);
      String digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(body.getBytes(StandardCharsets.UTF_8)));
      jdbc.update(
          "INSERT INTO platform.ai_releases(release_id,checksum,envelope) VALUES (?,?,?::jsonb) ON"
              + " CONFLICT DO NOTHING",
          envelope.releaseId(),
          digest,
          body);
      if (!digest.equals(
          jdbc.queryForObject(
              "SELECT checksum FROM platform.ai_releases WHERE release_id=?",
              String.class,
              envelope.releaseId())))
        throw new IllegalStateException("Immutable release ID conflict");
    } catch (java.security.NoSuchAlgorithmException
        | com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException("Cannot encode AI release", failure);
    }
  }
}
