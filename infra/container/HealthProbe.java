/**
 * ============================================================================
 * FILE: infra/container/HealthProbe.java
 * PURPOSE: Probe API readiness or a fresh worker heartbeat without curl or shell credentials.
 * OWNER: Platform & Operations
 * SINCE: week-34
 * RELATED: Dockerfile
 * NOTES: Compiled with the pinned Java 21 builder; exit status only, no response body output.
 * ============================================================================
 */
import java.nio.file.*;

public final class HealthProbe {
  public static void main(String[] args) throws Exception {
    if ("worker".equals(System.getenv("FRAUD_ROLE"))) {
      var marker = Path.of("/tmp/fraud-worker-ready");
      if (!Files.exists(marker)
          || System.currentTimeMillis() - Long.parseLong(Files.readString(marker)) > 20000)
        System.exit(1);
    } else {
      var request =
          java.net.http.HttpRequest.newBuilder(
                  java.net.URI.create("http://127.0.0.1:8080/actuator/health/readiness"))
              .timeout(java.time.Duration.ofSeconds(3))
              .GET()
              .build();
      if (java.net.http.HttpClient.newHttpClient()
              .send(request, java.net.http.HttpResponse.BodyHandlers.discarding())
              .statusCode()
          != 200) System.exit(1);
    }
  }
}
