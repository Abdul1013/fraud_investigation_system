/**
 * WEEK 27 — Offline embedding fixture with punctuation-normalized token hashing. This gives
 * repeatable integration/evaluation plumbing without credentials. It has no semantic understanding;
 * spaceId and fixture=true explicitly prevent the test report from being passed off as
 * semantic-model quality. It is isolated from the Week 26 fixture, whose tokenization must remain
 * unchanged for old stored vectors.
 */
package com.fraudengine.platform.gateway.provider;

import com.fraudengine.platform.gateway.RetrievalEmbeddingPort;

import java.util.Locale;

public class FixtureRetrievalEmbedding implements RetrievalEmbeddingPort {
    @Override
    public String spaceId() {
        return "fixture:token-hash-v2:1536";
    }

    @Override
    public boolean fixture() {
        return true;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[1536];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            if (!token.isBlank()) vector[Math.floorMod(token.hashCode(), vector.length)] += 1;
        // Punctuation-only input has a nonzero sentinel, allowing the service to abstain without
        // NaN
        // distances.
        if (java.util.stream.IntStream.range(0, vector.length).allMatch(i -> vector[i] == 0))
            vector[0] = 1;
        return vector;
    }
}
