/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/SecurityAccounts.java
 * CONTEXT:     platform
 * LAYER:       config
 * PURPOSE:     Binds explicitly configured local-learning accounts with hashed passwords.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-28
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - An empty account list disables all logins instead of creating a default user.
 *   - Only BCrypt hashes are accepted; production identity-provider and MFA integration remain pending.
 *   - Roles are server configuration, never request fields; curator access is a separate operational role.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.Permission;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("engine.security")
public record SecurityAccounts(List<Account> accounts) {
    public SecurityAccounts {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    public record Account(String username, String passwordHash, Role role) {
        public Account {
            if (username == null || username.isBlank() || !username.equals(username.strip())
                    || username.contains(":") || role == null || passwordHash == null
                    || !passwordHash.matches("\\{bcrypt\\}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
                throw new IllegalArgumentException("Accounts require a valid subject, role, and {bcrypt} password hash");
            }
        }
    }

    public enum Role {
        INVESTIGATOR(Set.of(Permission.READ_CASE, Permission.READ_EVIDENCE, Permission.PROPOSE_ACTION)),
        ANALYST(Set.of(Permission.READ_CASE, Permission.READ_EVIDENCE, Permission.APPROVE_ACTION, Permission.READ_AUDIT)),
        AUDITOR(Set.of(Permission.READ_CASE, Permission.READ_EVIDENCE, Permission.READ_AUDIT)),
        KNOWLEDGE_CURATOR(Set.of(Permission.MANAGE_KNOWLEDGE, Permission.READ_GLOBAL_EVIDENCE));

        private final Set<Permission> permissions;

        Role(Set<Permission> permissions) {
            this.permissions = permissions;
        }

        public Set<Permission> permissions() {
            return permissions;
        }
    }
}
