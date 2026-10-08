package com.fraudengine.knowledge;

import com.fraudengine.knowledge.ingestion.report.IngestionRunReport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IngestionReportTest {
    @Test void reportRoundTripsTimeCountersAndEmptyRestrictedLineage() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var report = new IngestionRunReport(1,1,1,Instant.now(),Instant.now(),"COMPLETED",1,0,0,List.of(),
                Map.of("kyc@1",new IngestionRunReport.SourceTrace(1,"kyc","C3","hash","file:restricted.md",List.of())));
        assertThat(mapper.readValue(mapper.writeValueAsString(report),IngestionRunReport.class)).isEqualTo(report);
    }
}
