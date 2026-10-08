package com.fraudengine.knowledge.ingestion.batch;

import com.fraudengine.knowledge.domain.DocType;
import com.fraudengine.knowledge.ingestion.reader.SourceDocumentLoader;
import org.springframework.batch.item.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.*;

/** Step-scoped reader with an ordered, checksum-pinned manifest and durable cursor. */
public class IngestionBatchReader extends ItemStreamSupport implements ItemStreamReader<DocumentBatch> {
    private final List<SourceConfig> sources;
    private final String manifest;
    private int index;
    public IngestionBatchReader(String corpusRoot) {
        setName("knowledgeSourceReader");
        try {
            String base = corpusRoot.startsWith("classpath:") || corpusRoot.startsWith("file:")
                    ? corpusRoot : "file:" + java.nio.file.Path.of(corpusRoot).toAbsolutePath() + "/";
            if (!base.endsWith("/")) base += "/";
            var resolver = new PathMatchingResourcePatternResolver();
            var configs = new ArrayList<SourceConfig>();
            Map<String, DocType> directories = Map.of("policies", DocType.POLICY,
                    "prior-cases", DocType.PRIOR_CASE, "regulatory", DocType.REGULATORY, "kyc", DocType.KYC);
            for (var entry : directories.entrySet()) {
                for (Resource resource : resolver.getResources(base + entry.getKey() + "/*.md")) {
                    String name = Objects.requireNonNull(resource.getFilename()).replaceFirst("\\.md$", "");
                    var match = java.util.regex.Pattern.compile("^(.*)-v(\\d+)$").matcher(name);
                    String id = entry.getKey() + "/" + name;
                    int version = 1;
                    if (match.matches()) { id = entry.getKey() + "/" + match.group(1); version = Integer.parseInt(match.group(2)); }
                    String checksum;
                    try (var stream = resource.getInputStream()) {
                        checksum = SourceDocumentLoader.sha256(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                    configs.add(new SourceConfig(resource, id, version, entry.getValue(), checksum));
                }
            }
            configs.sort(Comparator.comparing(SourceConfig::sourceId).thenComparingInt(SourceConfig::sourceVersion));
            if (configs.isEmpty()) throw new IllegalArgumentException("No Markdown sources in " + corpusRoot);
            sources = List.copyOf(configs);
            manifest = SourceDocumentLoader.sha256(sources.stream()
                    .map(s -> s.sourceId() + ":" + s.sourceVersion() + ":" + s.checksum()).reduce("", (a,b) -> a + "\n" + b));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot discover corpus", e); }
    }
    @Override public void open(ExecutionContext context) {
        if (context.containsKey("sourceManifest") && !manifest.equals(context.getString("sourceManifest")))
            throw new ItemStreamException("Corpus changed during restart; restore sources or launch a new job instance");
        index = context.getInt("sourceIndex", 0);
        context.putString("sourceManifest", manifest);
    }
    @Override public DocumentBatch read() {
        if (index >= sources.size()) return null;
        var config = sources.get(index++);
        DocumentBatch batch;
        try { batch = SourceDocumentLoader.load(config.resource(), config.sourceId(), config.sourceVersion(), config.type()); }
        catch (IllegalArgumentException malformed) { throw new MalformedSourceException(config.sourceId(),malformed); }
        if (!batch.checksum().equals(config.checksum())) throw new IllegalStateException("Source changed during execution");
        return batch;
    }
    @Override public void update(ExecutionContext context) {
        context.putInt("sourceIndex", index); context.putString("sourceManifest", manifest);
    }
    public record SourceConfig(Resource resource, String sourceId, int sourceVersion, DocType type, String checksum) { }
}
