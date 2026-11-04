/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/OidcIdentityLinks.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Resolve verified issuer/subject pairs to explicitly provisioned application identities.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-31
 * RELATED:     ADR-001, ADR-002, curriculum week 31
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Keycloak usernames, requested roles and token role claims never grant application permissions.
 *   - Disabling a link is checked on each request and worker turn; case grants remain separately required.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import com.fraudengine.platform.api.AccessDeniedException;

@Repository
public class OidcIdentityLinks {
    private final JdbcTemplate jdbc;

    public OidcIdentityLinks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserDetails verified(String issuer, String subject) {
        var rows = jdbc.query(
                "SELECT application_subject,application_role FROM platform.identity_links WHERE issuer=? AND external_subject=? AND enabled=true",
                (r, n) -> user(r.getString(1), r.getString(2)), issuer, subject);
        if (rows.size() != 1)
            throw new AccessDeniedException();
        return rows.getFirst();
    }

    public UserDetails worker(String applicationSubject) {
        var rows = jdbc.query(
                "SELECT application_subject,application_role FROM platform.identity_links WHERE application_subject=? AND enabled=true",
                (r, n) -> user(r.getString(1), r.getString(2)), applicationSubject);
        if (rows.size() != 1)
            throw new AccessDeniedException();
        return rows.getFirst();
    }

    private UserDetails user(String subject, String role) {
        return new User(subject, "unused-not-a-login-password",
                SecurityAccounts.Role.valueOf(role).permissions().stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.name())).toList());
    }
}
