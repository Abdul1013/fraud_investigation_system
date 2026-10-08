/**
 * FILE:        src/main/java/com/fraudengine/knowledge/ingestion/transformer/StructureAwareChunkingTransformer.java
 * CONTEXT:     knowledge
 * LAYER:       infrastructure
 * PURPOSE:     Reserves structure-aware document chunking for the batch pipeline, Chunks documents according to their doc_type.
 * OWNER:       Knowledge & Evidence
 * SINCE:       week-2
 * RELATED:     ADR-005
 *  - policy:      semantic sections (H2 boundaries), then token split
 *  - prior_case:  structured fields already split by reader; token split only
 *  - regulatory:  section-based split, preserve tables as atomic units
 *  - kyc:         not embedded; never reaches this transformer
 *   
 */
package com.fraudengine.knowledge.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import com.fraudengine.knowledge.domain.DocType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StructureAwareChunker implements DocumentTransformer {

    private static final int POLICY_CHUNK_SIZE = 512;
    private static final int CASE_CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 50;

    private final TokenTextSplitter policySplitter;
    private final TokenTextSplitter caseSplitter;

    public StructureAwareChunker() {
        this.policySplitter = new TokenTextSplitter(POLICY_CHUNK_SIZE, 350, 5, 10000, true,
                List.of('.', '?', '!', '\n'));
        this.caseSplitter = new TokenTextSplitter(CASE_CHUNK_SIZE, 350, 5, 10000, true, List.of('.', '?', '!', '\n'));
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            DocType docType = DocType.fromWire((String) doc.getMetadata().get("doc_type"));
            List<Document> chunks = switch (docType) {
                case POLICY -> chunkPolicy(doc);
                case PRIOR_CASE -> chunkPriorCase(doc);
                case REGULATORY -> chunkRegulatory(doc);
                case KYC -> List.of(); // should never reach here
                // No chunking for other types
            };
            result.addAll(chunks);
        }
        return result;
    }

    /**
     * Policy documents: split on H2 boundaries first (semantic sections),
     * then apply token splitting to oversized sections.
     */

    private List<Document> chunkPolicy(Document doc) {
        String text = doc.getText();
        // Split on markdownH2 headers (semantic sections)
        String[] sections = text.split("(?m)^##\\s+");

        if (section.length <= 1) {
            // No H2 headers found, fallback to token splitting
            return policySplitter.apply(List.of(doc));
        }

        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            String sectionText = sections[i].trim();
            if (sectionText.isEmpty())
                continue;

            var meta = new java.util.HashMap<>(doc.getMetadata());
            meta.put("section_index", i);
            meta.put("chunk_index", i);
            meta.put("total_chuncks", sections.length);

            var sectionDoc = new Document(sectionText, meta);
            // If the section is too large, token-split it
            if (sectionText.length() > POLICY_CHUNK_SIZE * 4) {
                chunks.addAll(policySplitter.apply(List.of(sectionDoc)));
            } else {
                chunks.add(sectionDoc);
            }

        }
        return chunks;
    }

    /**
     * Prior case documents: already structured, so just apply token splitting.
     * to keep chunks within embedding limits
     */

    private List<Document> chunkPriorCase(Document doc) {
        var chunks = caseSplitter.apply(List.of(doc));
        // Add chunk_index and total_chunks metadata to each resulting chunk
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            var meta = new java.util.HashMap<>(chunks.get(i).getMetadata());
            meta.put("chunk_index", i);
            meta.put("total_chunks", chunks.size());
            result.add(new Document(chunks.get(i).getId(), chunks.get(i).getText(), meta));
        }
        return result;
    }

    /**
     * Regulatory documents: split on numbered sections (e.g., "3.2 Title"),
     * preserve tables as atomic units.
     */

    private List<Document> chunkRegulatory(Document doc) {
        String text = doc.getText();
        // Split on numbered section headers
        String[] sections = text.split("(?m)^\\d+\\.\\d*\\s+");

        if (sections.length <= 1) {
            return policySplitter.apply(List.of(doc));
        }

        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty())
                continue;

            var meta = new java.util.HashMap<>(doc.getMetadata());
            meta.put("section_index", i);
            meta.put("chunk_index", i);
            meta.put("total_chunks", sections.length);

            chunks.add(new Document(section, meta));
        }
        return chunks;
    }
}

// DocType docType = DocType.fromWire((String)
// doc.getMetadata().get("doc_type"));
// List<Document> chunks = switch (docType) {
// case POLICY -> chunkPolicy(doc);
// case PRIOR_CASE -> chunkPriorCase(doc);
// case REGULATORY -> chunkRegulatory(doc);
// case KYC -> List.of(); // should never reach here
// };