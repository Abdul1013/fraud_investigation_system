/**
 * A model may select relevant quotations, never create citations or authorize actions.
 * The knowledge service validates every selection against the supplied evidence. A dedicated narrow
 * port avoids turning the existing broad ChatPort scaffold into a hidden authority boundary.
 */
package com.fraudengine.platform.gateway;

import java.util.List;

public interface EvidenceAnswerPort {
    record Evidence(String chunkId, String text) {
    }

    record ProposedQuote(String chunkId, String quote) {
    }

    List<ProposedQuote> select(String question, List<Evidence> evidence);

    String modelVersion();
}
