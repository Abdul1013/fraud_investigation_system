package com.fraudengine.knowledge;

import com.fraudengine.knowledge.ingestion.transformer.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DeduplicationServiceTest {
    @Test void filtersOnlyPreviouslyPersistedChunkPositions() {
        var jdbc = mock(JdbcTemplate.class);
        var docs = new StructureAwareChunker().apply(List.of(ChunkingStrategyTest.source("## One\nFirst.\n## Two\nSecond.","policy","C0")));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("policy"),eq(1),eq(0),any())).thenReturn(1);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("policy"),eq(1),eq(1),any())).thenReturn(0);
        assertThat(new DeduplicationTransformer(jdbc,"policy",1).apply(docs)).containsExactly(docs.get(1));
    }
}
