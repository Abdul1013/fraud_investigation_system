/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/SecurityConfig.java
 * CONTEXT:     platform
 * LAYER:       config
 * PURPOSE:     Configures local-learning Basic authentication with route permissions and CSRF.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, docs/week28-implementation-plan.md
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - HTTP Basic is for loopback learning or TLS only; CSRF stays enabled because browsers cache credentials.
 *   - No session authentication is persisted; sessions hold CSRF tokens only. Default users are disabled.
 *   - Legacy unscoped retrieval is curator-only until case-scoped evidence is implemented.
 *   - Unknown routes are denied, and no approval or execution endpoint is exposed.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fraudengine.platform.api.Permission;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityAccounts.class)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityAccounts configuration) {
        var users = new InMemoryUserDetailsManager();
        for (var account : configuration.accounts()) {
            users.createUser(User.withUsername(account.username()).password(account.passwordHash())
                    .authorities(account.role().permissions().stream().map(Enum::name).toArray(String[]::new))
                    .build());
        }
        return users;
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
            type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/security/me", "/v1/security/csrf").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/investigations")
                            .hasAuthority(Permission.PROPOSE_ACTION.name())
                        .requestMatchers(HttpMethod.GET, "/v1/investigations/*", "/v1/investigations/*/events",
                                "/v1/recommendations/*")
                            .hasAuthority(Permission.READ_CASE.name())
                        .requestMatchers(HttpMethod.POST, "/v1/recommendations/*/approve",
                                "/v1/recommendations/*/reject", "/v1/recommendations/*/execute")
                            .hasAuthority(Permission.APPROVE_ACTION.name())
                        .requestMatchers(HttpMethod.GET, "/v1/cases/*/audit")
                            .hasAuthority(Permission.READ_AUDIT.name())
                        .requestMatchers(HttpMethod.GET, "/v1/cases/*")
                            .hasAuthority(Permission.READ_CASE.name())
                        .requestMatchers(HttpMethod.POST, "/v1/cases/*/evidence")
                            .hasAuthority(Permission.READ_EVIDENCE.name())
                        .requestMatchers(HttpMethod.POST, "/v1/knowledge/ingestions",
                                "/v1/knowledge/ingestions/*/restart", "/v1/knowledge/retrieval/index")
                            .hasAuthority(Permission.MANAGE_KNOWLEDGE.name())
                        .requestMatchers(HttpMethod.GET, "/v1/knowledge/ingestions/*",
                                "/v1/knowledge/corpus/versions")
                            .hasAuthority(Permission.MANAGE_KNOWLEDGE.name())
                        .requestMatchers(HttpMethod.POST, "/v1/retrieve", "/v1/knowledge/retrieval",
                                "/v1/knowledge/answers")
                            .hasAuthority(Permission.READ_GLOBAL_EVIDENCE.name())
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}
