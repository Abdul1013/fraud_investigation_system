package com.fraudengine.knowledge.ingestion.transformer;

import com.fraudengine.knowledge.domain.DocType;
import com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader;
import org.springframework.ai.document.*;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Preserve semantic headings and whole tables; oversized tables fail rather
 * than lose meaning.
 */
public class StructureAwareChunker implements DocumentTransformer {
    private final TokenTextSplitter splitter = new TokenTextSplitter(512, 100, 1, 10000, true);

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document source : documents) {
            DocType type = DocType.fromWire(source.getMetadata().get("doc_type").toString());
            if (!type.isEmbeddable() || "C3".equals(source.getMetadata().get("classification")))
                continue;
            String[] sections = source.getText().split("(?m)(?=^##\\s|^\\d+\\.\\d*\\s)");
            for (String section : sections) {
                if (section.isBlank())
                    continue;
                // A Markdown table stays atomic. Long tables require a source-specific parser.
                boolean hasTable = section.lines().anyMatch(line -> line.strip().startsWith("|"));
                if (hasTable && section.length() > 12000)
                    throw new IllegalArgumentException("Table section exceeds supported ingestion size");
                Document part = new Document(section.trim(), new HashMap<>(source.getMetadata()));
                result.addAll(hasTable ? List.of(part) : splitter.apply(List.of(part)));
            }
        }
        List<Document> indexed = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            Document doc = result.get(i);
            var meta = new HashMap<>(doc.getMetadata());
            String hash = SourceDocumentLoader.sha256(doc.getText());
            meta.put("chunk_hash", hash);
            meta.put("chunk_index", i);
            meta.put("total_chunks", result.size());
            String identity = meta.get("source_id") + ":" + meta.get("source_version") + ":" + i + ":" + hash;
            String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            indexed.add(new Document(id, doc.getText(), meta));
        }
        return indexed;
    }
}
