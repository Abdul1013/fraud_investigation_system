package com.fraudengine.knowledge.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class PriorCaseDocumentReader implements DocumentReader {

    private final String resourcePattern;
    private final String sourceId;
    private final int sourceVersion;

    public PriorCaseDocumentReader(String resourcePattern, String sourceId, int sourceVersion){
        this.resourcePattern = resourcePattern;
        this.sourceId = sourceId;
        this.sourceVersion = sourceVersion; 
    }

    @Override 
    public List<Document> get(){
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(resourcePattern);
            List<Document> all = new ArrayList <>();

            for (Resource resource : resources) {
                // read raw content to compute hash
                String rawContent = new String(
                    resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);

                // Prior cases are structured: split by "---" section delimiter
                // Each section becomes its own Document

                String[] sections = rawContent.split("(?m)^---$");
                    for (int i = 0; i < sections.length; i++) {
                        String section = sections[i].trim();
                        if (section.isEmpty()) continue;

                        String hash = sha256(section);
                        Map<String, Object> meta = Map.of(
                             "source_id", sourceId,
                                "source_version", sourceVersion,
                                "doc_type", DocType.PRIOR_CASE,
                                "classification", "C2",
                                "content_hash", hash,
                                "source_path", resource.getFilename(),
                                "section_index", i
                        );
                        all.add(new Document(section, meta));
                    }

                    }
                    return all;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prior case documents", e);
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
