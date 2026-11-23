/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/security/PrivateIdentityProvisioner.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Provision a reviewed new identity through the private Keycloak administrator lane.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-34
 * RELATED:     ADR-001, ADR-002, curriculum week 34
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - This standalone operator entry point is not a controller or model tool. It has no case or approval authority.
 *   - Passwords and the short-lived bearer remain in memory; fixed private endpoints and disabled redirects prevent credential forwarding.
 * ============================================================================
 */
package com.fraudengine.platform.security;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class PrivateIdentityProvisioner {
  private static final String BASE = "http://keycloak.fraud.internal:8080";
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ObjectMapper mapper = new ObjectMapper();

  private PrivateIdentityProvisioner() {}

  public static void main(String[] args) throws Exception {
    if (!"user-create".equals(System.getenv("IDENTITY_ADMIN_ACTION"))) {
      System.out.println(
          "Private identity job: review user-create and supply a new username with fresh injected"
              + " credentials. No user changed.");
      return;
    }
    new PrivateIdentityProvisioner().create();
  }

  private String required(String name) {
    var value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing private operator input: " + name);
    return value;
  }

  private HttpResponse<String> send(HttpRequest request, int expected) throws Exception {
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != expected)
      throw new IllegalStateException(
          "Private identity request failed with status " + response.statusCode());
    if (response.body().length() > 65536)
      throw new IllegalStateException("Private identity response exceeds bound");
    return response;
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15));
  }

  private void create() throws Exception {
    String username = required("PROVISION_USERNAME"), password = required("PROVISION_PASSWORD");
    if (username.length() > 100 || password.length() < 24)
      throw new IllegalArgumentException("Invalid reviewed new-user input");
    var fields = new LinkedHashMap<String, String>();
    fields.put("grant_type", "password");
    fields.put("client_id", "admin-cli");
    fields.put("username", required("KC_BOOTSTRAP_ADMIN_USERNAME"));
    fields.put("password", required("KC_BOOTSTRAP_ADMIN_PASSWORD"));
    String form =
        fields.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    var token =
        mapper
            .readTree(
                send(
                        request("/realms/master/protocol/openid-connect/token")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                        200)
                    .body())
            .path("access_token")
            .asText();
    if (token.isBlank()) throw new IllegalStateException("Private administrator token unavailable");
    String body =
        mapper.writeValueAsString(
            Map.of(
                "username",
                username,
                "enabled",
                true,
                "requiredActions",
                List.of("CONFIGURE_TOTP", "UPDATE_PASSWORD")));
    var created =
        send(
            request("/admin/realms/fraud/users")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            201);
    String location =
        created
            .headers()
            .firstValue("Location")
            .orElseThrow(() -> new IllegalStateException("New identity location missing"));
    String subject =
        URI.create(location)
            .getPath()
            .substring(URI.create(location).getPath().lastIndexOf('/') + 1);
    if (!subject.matches("[0-9a-fA-F-]{36}"))
      throw new IllegalStateException("Invalid new identity subject");
    String credential =
        mapper.writeValueAsString(Map.of("type", "password", "temporary", true, "value", password));
    send(
        request("/admin/realms/fraud/users/" + subject + "/reset-password")
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(credential))
            .build(),
        204);
    var verified =
        mapper.readTree(
            send(
                    request("/admin/realms/fraud/users/" + subject)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                    200)
                .body());
    boolean otp = false, change = false;
    for (var action : verified.path("requiredActions")) {
      if ("CONFIGURE_TOTP".equals(action.asText())) otp = true;
      if ("UPDATE_PASSWORD".equals(action.asText())) change = true;
    }
    if (!otp || !change)
      throw new IllegalStateException("New identity must change its password and enroll OTP");
    System.out.println(
        mapper.writeValueAsString(
            Map.of(
                "_purpose",
                "Private new-identity provisioning",
                "subject",
                subject,
                "otpEnrollmentRequired",
                true,
                "passwordChangeRequired",
                true)));
  }
}
