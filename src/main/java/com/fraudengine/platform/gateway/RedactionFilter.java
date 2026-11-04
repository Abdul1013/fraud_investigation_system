/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/RedactionFilter.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Apply a bounded outbound text policy before any local provider POST.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Structural source classification remains authoritative; these patterns are an additional disclosure guard.
 *   - Known instruction override payloads are rejected, not treated as higher-priority instructions.
 *   - Pattern redaction is deliberately limited; it cannot establish comprehensive PII detection.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.fraudengine.platform.api.AccessDeniedException;
import java.util.regex.Pattern;

public final class RedactionFilter {
    private static final Pattern RESTRICTED = Pattern.compile(
            "(?is)RESTRICTED_C3_SENTINEL|classification[\\\"\\s:*]+C3|-----BEGIN (?:RSA )?PRIVATE KEY|\\botp[_ -]?secret\\b|\\bBearer\\s+[A-Za-z0-9._~-]{12,}");
    private static final Pattern OVERRIDE = Pattern.compile(
            "(?is)ignore (?:all |the )?(?:previous|system) instructions|(?:reveal|print|send) (?:the )?(?:system prompt|credentials|otp seed)|(?:call|execute) (?:the )?(?:execute_account_freeze|approve_action)");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern ACCOUNT = Pattern.compile("(?<![A-Za-z0-9])\\d{10,19}(?![A-Za-z0-9])");

    public String text(String value) {
        if (value.length() > 100_000 || RESTRICTED.matcher(value).find() || OVERRIDE.matcher(value).find())
            throw new AccessDeniedException();
        return ACCOUNT.matcher(EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]")).replaceAll("[REDACTED_NUMBER]");
    }

    public JsonNode payload(JsonNode value) {
        if (value.isTextual())
            return TextNode.valueOf(text(value.textValue()));
        if (value.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(payload(item)));
            return result;
        }
        if (value.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            value.fields().forEachRemaining(entry -> result.set(entry.getKey(), payload(entry.getValue())));
            return result;
        }
        return value;
    }
}
