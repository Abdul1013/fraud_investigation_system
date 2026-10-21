/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/mcp/InvestigationMcpConfig.java
 * CONTEXT:     orchestration
 * LAYER:       config
 * PURPOSE:     Register protocol-native MCP tools backed by the shared investigator dispatcher.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Explicit profile and trusted process configuration bind one service subject and one case.
 *   - Discovery exposes only investigator tools; every invocation re-enters the same authorized dispatcher.
 *   - The SDK owns initialize, tools/list, tools/call, framing, and protocol errors.
 * ============================================================================
 */
package com.fraudengine.orchestration.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.orchestration.api.InvestigatorTools;
import com.fraudengine.platform.api.LocalServiceIdentity;
import com.fraudengine.platform.api.ToolCallingPort;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class InvestigationMcpConfig {
    @Bean
    List<McpServerFeatures.SyncToolSpecification> investigatorMcpTools(InvestigatorTools tools,
            LocalServiceIdentity identity, ObjectMapper mapper,
            @Value("${investigation.mcp.subject}") String subject,
            @Value("${investigation.mcp.case-id}") String caseId) {
        if (subject.isBlank() || caseId.isBlank()) throw new IllegalArgumentException("MCP requires a provisioned subject and case");
        return identity.call(subject, tools::definitions).stream().map(definition -> {
            var schema = mapper.convertValue(definition.schema(), McpSchema.JsonSchema.class);
            var tool = McpSchema.Tool.builder().name(definition.name()).description(definition.description()).inputSchema(schema).meta(java.util.Map.of("effect", definition.effect().name())).build();
            return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) ->
                    identity.call(subject, () -> {
                        var result = tools.call(caseId, new ToolCallingPort.Call(UUID.randomUUID().toString(),
                                definition.name(), request.arguments()), "mcp-client:model-unspecified");
                        try {
                            return McpSchema.CallToolResult.builder().isError(result.error())
                                    .addTextContent(mapper.writeValueAsString(result)).build();
                        } catch (java.io.IOException failure) {
                            return McpSchema.CallToolResult.builder().isError(true).addTextContent("Tool result unavailable").build();
                        }
                    })).build();
        }).toList();
    }
}
