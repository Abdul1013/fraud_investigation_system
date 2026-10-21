/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/orchestration/infrastructure/mcp/package-info.java
 * CONTEXT:     orchestration
 * LAYER:       infrastructure
 * PURPOSE:     Own the local investigator MCP transport adapter.
 * ----------------------------------------------------------------------------
 * OWNER:       Investigation Orchestration
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-005, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - May import orchestration and platform public APIs plus the supported MCP SDK.
 *   - No direct decision executor, knowledge repository, or case infrastructure access is allowed.
 * ============================================================================
 */
/** Local stdio transport delegates exclusively to the authorized investigator tool API. */
package com.fraudengine.orchestration.infrastructure.mcp;
