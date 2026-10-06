/**
 * FILE:        src/main/java/com/fraudengine/knowledge/ingestion/reader/PolicyDocumentReader.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Reserves the batch reader for policy source documents.
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-2
 * RELATED:     ADR-005
 * NOTES:
 *   - TODO(week-2): implement a restartable source reader.
 *   - Source ingestion behavior is intentionally omitted.
 */
package com.fraudengine.knowledge.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkDownDocumentReaderConfig;
import org.springframwork.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class PolicyDocumentReader implements DocumentReader {

    private final String resourcePattern;
    private final String sourceId;
    private final int sourceVersion;

    public PolicyDocumentReader(String resourcePattern, String sourceId, int sourceVersion) {
        this.resourcePattern = resourcePattern;
        this.sourceId = sourceId;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public List<Document> get() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(resourcePattern);
            List<Document> all = new ArrayList<>();

            for (Resource resource : resources) {
                // read eaw content to compute has
                String rawContent = new String(resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);

                String contentHash = sha256(rawContent);

                // using sprinAI markdowndocument reader for structural parsing

                var config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(false)
                        .withIncludeCodeBlocks(false)
                        .withIncludeBlockquotes(true)
                        .withAdditionalMetadata(java.util.Map.of(
                                "source_id", sourceId,
                                "source_version", sourceVersion,
                                "doc_type", DocType.POLICY,
                                "classification", "C0",
                                "content_hash", contentHash,
                                "source_path", resource.getFilename()))
                        .build();
                var reader = new MarkdownDocumentReader(resource, config);
                all.addAll(reader.get());
            }
            return all;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read policy documents", e);
        }
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }
}