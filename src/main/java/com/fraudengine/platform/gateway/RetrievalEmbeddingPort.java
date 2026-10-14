/**
 * Provider-neutral embedding contract for Week 27's separate search index. An
 * embedding-space identifier includes provider/model revision; the repository never mixes spaces or
 * silently reuses Week 26 token-hash vectors for a semantic model. Float arrays keep provider SDK
 * types out of knowledge code.
 */
package com.fraudengine.platform.gateway;

public interface RetrievalEmbeddingPort {
    float[] embed(String text);

    String spaceId();

    boolean fixture();
}
