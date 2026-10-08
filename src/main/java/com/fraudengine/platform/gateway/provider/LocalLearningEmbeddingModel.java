package com.fraudengine.platform.gateway.provider;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import java.util.*;

/** Deterministic offline fixture, not a semantic model. Never use its vectors for quality claims. */
public class LocalLearningEmbeddingModel implements EmbeddingModel {
    @Override public int dimensions() { return 1536; }
    @Override public float[] embed(Document document) { return vector(document.getText()); }
    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        for (int i=0; i<request.getInstructions().size(); i++)
            results.add(new Embedding(vector(request.getInstructions().get(i)), i));
        return new EmbeddingResponse(results);
    }
    private float[] vector(String text) {
        float[] vector = new float[1536];
        // Token hashing gives reproducible non-zero vectors while remaining completely local.
        for (String token : text.toLowerCase(Locale.ROOT).split("\\s+"))
            vector[Math.floorMod(token.hashCode(), vector.length)] += 1.0f;
        return vector;
    }
}
