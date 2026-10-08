package com.fraudengine.knowledge.ingestion.reader;

import com.fraudengine.knowledge.domain.DocType;
import com.fraudengine.knowledge.ingestion.batch.DocumentBatch;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Loads one revision. Restricted text is never put in a model-facing Document. */
public final class SourceDocumentLoader {
    private SourceDocumentLoader() { }
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static DocumentBatch load(Resource resource, String sourceId, int version, DocType type) {
        try (var input = resource.getInputStream()) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (text.isBlank()) throw new IllegalArgumentException("Empty source: " + sourceId);
            String classification = type.defaultClassification();
            var declared = java.util.regex.Pattern.compile("(?im)^\\*{0,2}Classification:?\\*{0,2}\\s*:?\\s*(C[0-9]+)").matcher(text);
            if (declared.find()) classification = declared.group(1);
            if (!Set.of("C0", "C1", "C2", "C3").contains(classification))
                throw new IllegalArgumentException("Unsupported source classification: " + sourceId);
            // Type defaults are a floor, never a mechanism to downgrade restricted content.
            if (classification.compareTo(type.defaultClassification()) < 0)
                classification = type.defaultClassification();
            String hash = sha256(text);
            // Content-addressed raw snapshots preserve the exact source behind old citations.
            var directory = java.nio.file.Path.of("knowledge-raw");
            java.nio.file.Files.createDirectories(directory);
            var snapshot = directory.resolve(hash + ".md");
            if (!java.nio.file.Files.exists(snapshot)) {
                try { java.nio.file.Files.writeString(snapshot, text, java.nio.file.StandardOpenOption.CREATE_NEW); }
                catch (java.nio.file.FileAlreadyExistsException concurrent) { /* Same immutable content. */ }
            }
            if (java.nio.file.Files.getFileStore(snapshot).supportsFileAttributeView("posix")) {
                java.nio.file.Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                java.nio.file.Files.setPosixFilePermissions(snapshot, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
            String uri = snapshot.toAbsolutePath().toUri().toString();
            Map<String, Object> meta = new HashMap<>();
            meta.put("source_id", sourceId); meta.put("source_version", version);
            meta.put("doc_type", type.wireName()); meta.put("classification", classification);
            meta.put("content_hash", hash); meta.put("checksum", hash); meta.put("source_path", uri);
            var effective = java.util.regex.Pattern.compile("(?im)^\\*{0,2}Effective From:?\\*{0,2}\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(text);
            if (effective.find()) meta.put("effective_from", effective.group(1));
            List<Document> documents = type.isEmbeddable() && !classification.equals("C3")
                    ? List.of(new Document(text, meta)) : List.of();
            return new DocumentBatch(sourceId, version, type.wireName(), classification, hash, uri, documents);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read source " + sourceId, e); }
    }
}
