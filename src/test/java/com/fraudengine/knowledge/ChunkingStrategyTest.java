package com.fraudengine.knowledge;

import com.fraudengine.knowledge.ingestion.transformer.*;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ChunkingStrategyTest {
    static Document source(String text, String type, String classification) {
        return new Document(text,new HashMap<>(Map.of("source_id","policy","source_version",1,
                "doc_type",type,"classification",classification,"content_hash","source-hash","source_path","file:fixture.md")));
    }
    @Test void preservesTablesHeadingsAndProvenanceWithDeterministicIds() {
        var chunker = new StructureAwareChunker();
        var input = List.of(source("## Eligibility\nA valid claim.\n## Evidence\n| Kind | Required |\n|---|---|\n| Receipt | Yes |", "policy","C0"));
        var chunks = chunker.apply(input);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).getText()).contains("## Evidence","| Receipt | Yes |");
        assertThat(chunks.get(1).getMetadata()).containsEntry("chunk_index",1).containsEntry("total_chunks",2)
                .containsEntry("source_version",1).containsEntry("classification","C0");
        assertThat(chunks.get(0).getMetadata().get("chunk_hash")).isNotEqualTo(chunks.get(1).getMetadata().get("chunk_hash"));
        assertThat(chunker.apply(input)).extracting(Document::getId).containsExactlyElementsOf(chunks.stream().map(Document::getId).toList());
    }
    @Test void excludesRestrictedTextEvenWhenTypeIsPolicy() {
        assertThat(new StructureAwareChunker().apply(List.of(source("SECRET","policy","C3")))).isEmpty();
        assertThat(new StructureAwareChunker().apply(List.of(source("SECRET","kyc","C3")))).isEmpty();
    }
    @Test void rejectsMissingProvenance() {
        assertThatThrownBy(() -> new MetadataEnricherTransformer().apply(List.of(new Document("text"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("source_id");
    }
    @Test void longSectionsHaveUniqueFinalIndices() {
        var chunks = new StructureAwareChunker().apply(List.of(source("## Long\n"+"A policy sentence. ".repeat(2000),"policy","C0")));
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).extracting(d -> d.getMetadata().get("chunk_index")).doesNotHaveDuplicates();
        assertThat(chunks).extracting(Document::getId).doesNotHaveDuplicates();
    }
}
