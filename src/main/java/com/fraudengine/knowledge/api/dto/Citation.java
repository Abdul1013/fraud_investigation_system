/**
 * A citation binds an answer to one immutable chunk and source revision. Checksums come
 * from registry/lineage joins, not from generated model text. We exclude local raw-file paths from
 * the public response because source identity and digest are enough for review without exposing the
 * filesystem.
 */
package com.fraudengine.knowledge.api.dto;

import java.util.UUID;

public record Citation(
                UUID chunkId,
                String sourceId,
                int sourceVersion,
                String sourceChecksum,
                String chunkHash,
                String classification) {
}
