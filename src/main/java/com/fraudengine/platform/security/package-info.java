/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/package-info.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Own framework authentication adapters and HTTP access policy.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - May import platform public APIs and Spring Security; no other context internals.
 *   - Exposes CurrentCaller through the public API so business code avoids framework identity types.
 *   - Local Basic authentication is not verified step-up MFA; StepUpMfaFilter remains a scaffold.
 * ============================================================================
 */
package com.fraudengine.platform.security;
