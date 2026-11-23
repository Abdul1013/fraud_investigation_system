/**
 * ============================================================================
 * FILE:        src/test/java/com/fraudengine/platform/gateway/EmbeddingBatchTest.java
 * CONTEXT:     platform
 * LAYER:       test
 * PURPOSE:     Verify native batch ordering and reject malformed vectors at the provider boundary.
 * ----------------------------------------------------------------------------
 * OWNER:       Fraud Investigation Engineering
 * SINCE:       week-33
 * RELATED:     ADR-001, ADR-002, curriculum week 33
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - A loopback protocol fixture proves request shape and validation, not semantic quality.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;
import static org.assertj.core.api.Assertions.*;
import com.fraudengine.platform.gateway.provider.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
class EmbeddingBatchTest {
 @Test void orderedBatchAndMalformedResponses()throws Exception{
  var response=new AtomicReference<>("{\"embeddings\":[[1,2],[3,4]]}");var captured=new AtomicReference<JsonNode>();var mapper=new ObjectMapper();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/embed",exchange->{captured.set(mapper.readTree(exchange.getRequestBody()));byte[] bytes=response.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}});server.start();
  try {
   var port=new OllamaRetrievalEmbedding(new OllamaLocalClient("http://127.0.0.1:"+server.getAddress().getPort(),mapper),"fixture-model");
   var vectors=port.embedBatch(List.of("first input","second input"));
   assertThat(vectors.get(0)).containsExactly(1,2);assertThat(vectors.get(1)).containsExactly(3,4);
   assertThat(captured.get().path("input").get(0).asText()).isEqualTo("first input");assertThat(captured.get().path("truncate").asBoolean()).isFalse();
   for(String invalid:List.of("{\"embeddings\":[[1,2]]}","{\"embeddings\":[[1,2],[3]]}","{\"embeddings\":[[0,0],[3,4]]}","{\"embeddings\":[[\"bad\",2],[3,4]]}")){
    response.set(invalid);assertThatThrownBy(()->port.embedBatch(List.of("a","b"))).isInstanceOf(ModelUnavailableException.class);
   }
   assertThatThrownBy(()->port.embedBatch(Collections.nCopies(33,"a"))).isInstanceOf(IllegalArgumentException.class);
  }finally{server.stop(0);}
 }
}
