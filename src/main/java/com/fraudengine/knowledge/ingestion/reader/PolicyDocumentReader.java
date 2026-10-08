package com.fraudengine.knowledge.ingestion.reader;

import com.fraudengine.knowledge.domain.DocType;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.List;

/**
 * Adapter for one source revision; wildcard source discovery belongs to the
 * Batch reader.
 */
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
            var resources = new PathMatchingResourcePatternResolver().getResources(resourcePattern);
            if (resources.length != 1)
                throw new IllegalArgumentException("Expected one source: " + resourcePattern);
            return SourceDocumentLoader.load(resources[0], sourceId, sourceVersion, DocType.POLICY).documents();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
