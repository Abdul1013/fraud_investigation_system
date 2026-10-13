/**
 *SQL adapter shared by both retrieval strategies. Every query joins published corpus
 * membership, source registry, chunk lineage, and stored text BEFORE ranking; classification and
 * effective-date filters apply identically to vector and keyword candidates. The HTTP body cannot
 * enlarge the allowed classifications. Named SQL parameters avoid injection, and bounded candidates
 * keep this first implementation understandable. Exact cosine search is appropriate for the tiny
 * corpus; benchmarking an approximate index comes later. Index completeness errors are explicit,
 * never silent fallbacks.
 */
package com.fraudengine.knowledge.retrieval;

import com.fraudengine.knowledge.api.dto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class RetrievalRepository {
        private final JdbcTemplate jdbc;
        private final NamedParameterJdbcTemplate named;
        private static final String SCOPE = """
                        FROM knowledge.corpus_members m
                        JOIN knowledge.document_registry d ON d.id=m.document_id
                        JOIN knowledge.chunks_lineage c ON c.document_id=d.id
                        JOIN knowledge.vector_store v ON v.id=c.vector_id
                        WHERE m.corpus_version=:corpus AND d.classification IN (:allowed)
                          AND d.classification <> 'C3'
                          AND (CAST(:type AS text) IS NULL OR d.doc_type=:type)
                        """;
        private static final String DATES = """
                          AND (d.effective_from IS NULL OR d.effective_from <= :asOf)
                          AND (d.effective_to IS NULL OR d.effective_to > :asOf)
                        """;
        private static final String COLUMNS = "c.id, v.content,"
                        + " d.source_id,d.source_version,d.checksum,c.chunk_hash,d.classification";

        public RetrievalRepository(JdbcTemplate jdbc) {
                this.jdbc = jdbc;
                this.named = new NamedParameterJdbcTemplate(jdbc);
        }

        public int resolveCorpus(Integer requested) {
                Integer version = requested == null
                                ? jdbc.queryForObject(
                                                "SELECT MAX(version) FROM knowledge.corpus_versions", Integer.class)
                                : requested;
                if (version == null)
                        throw new CorpusUnavailableException("No published corpus. Complete ingestion first.");
                if (jdbc.queryForObject(
                                "SELECT COUNT(*) FROM knowledge.corpus_versions WHERE version=?",
                                Integer.class,
                                version) == 0)
                        throw new CorpusUnavailableException("Unknown published corpus: " + version);
                return version;
        }

        private Map<String, Object> parameters(
                        int corpus, String type, LocalDate asOf, List<String> allowed) {
                Map<String, Object> params = new HashMap<>();
                params.put("corpus", corpus);
                params.put("type", type);
                params.put("allowed", allowed);
                params.put("asOf", Timestamp.from(asOf.atStartOfDay(ZoneOffset.UTC).toInstant()));
                return params;
        }

        public List<RetrievalResult.Passage> eligible(int corpus, List<String> allowed) {
                return named.query(
                                "SELECT " + COLUMNS + ",0.0 AS score " + SCOPE + " ORDER BY c.id",
                                parameters(corpus, null, LocalDate.now(ZoneOffset.UTC), allowed),
                                this::passage);
        }

        public void requireComplete(
                        int corpus, String type, LocalDate date, List<String> allowed, String space) {
                var params = parameters(corpus, type, date, allowed);
                params.put("space", space);
                int missing = named.queryForObject(
                                "SELECT COUNT(*) "
                                                + SCOPE
                                                + DATES
                                                + " AND NOT EXISTS (SELECT 1 FROM knowledge.retrieval_embeddings e"
                                                + " WHERE e.chunk_id=c.id AND e.embedding_space=:space)",
                                params,
                                Integer.class);
                if (missing > 0)
                        throw new IndexUnavailableException(
                                        "Index missing "
                                                        + missing
                                                        + " eligible chunks. Index this corpus for "
                                                        + space);
        }

        public List<RetrievalResult.Passage> vector(
                        int corpus,
                        RetrievalRequest request,
                        List<String> allowed,
                        String space,
                        float[] vector,
                        int limit,
                        double minimum) {
                var params = parameters(corpus, request.docType(), date(request), allowed);
                params.put("space", space);
                params.put("vector", vectorLiteral(vector));
                params.put("limit", limit);
                params.put("minimum", minimum);
                String sql = "SELECT * FROM (SELECT "
                                + COLUMNS
                                + ",1-((SELECT e.embedding FROM knowledge.retrieval_embeddings e WHERE"
                                + " e.chunk_id=c.id AND e.embedding_space=:space) <=> CAST(:vector AS"
                                + " vector)) AS score "
                                + SCOPE
                                + DATES
                                + ") ranked WHERE score >= :minimum ORDER BY score DESC,id LIMIT :limit";
                return named.query(sql, params, this::passage);
        }

        public List<RetrievalResult.Passage> keyword(
                        int corpus, RetrievalRequest request, List<String> allowed, int limit) {
                var params = parameters(corpus, request.docType(), date(request), allowed);
                String terms = Arrays.stream(request.query().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                                .filter(s -> !s.isBlank())
                                .distinct()
                                .limit(100)
                                .collect(java.util.stream.Collectors.joining(" OR "));
                params.put("query", terms);
                params.put("limit", limit);
                return named.query(
                                "SELECT "
                                                + COLUMNS
                                                + ",ts_rank_cd(v.search_text,websearch_to_tsquery('english',:query)) AS"
                                                + " score "
                                                + SCOPE
                                                + DATES
                                                + " AND v.search_text @@ websearch_to_tsquery('english',:query) ORDER BY"
                                                + " score DESC,c.id LIMIT :limit",
                                params,
                                this::passage);
        }

        public boolean indexed(UUID id, String space) {
                return jdbc.queryForObject(
                                "SELECT COUNT(*) FROM knowledge.retrieval_embeddings WHERE chunk_id=? AND"
                                                + " embedding_space=?",
                                Integer.class,
                                id,
                                space) > 0;
        }

        public boolean store(UUID id, String space, boolean fixture, float[] vector) {
                jdbc.update(
                                "INSERT INTO knowledge.retrieval_spaces(embedding_space,dimensions,fixture) VALUES"
                                                + " (?,?,?) ON CONFLICT DO NOTHING",
                                space,
                                vector.length,
                                fixture);
                Integer expected = jdbc.queryForObject(
                                "SELECT dimensions FROM knowledge.retrieval_spaces WHERE embedding_space=?",
                                Integer.class,
                                space);
                if (expected != vector.length)
                        throw new IndexUnavailableException(
                                        "Embedding dimensions changed inside space " + space);
                return jdbc.update(
                                "INSERT INTO"
                                                + " knowledge.retrieval_embeddings(chunk_id,embedding_space,dimensions,embedding)"
                                                + " VALUES (?,?,?,?::vector) ON CONFLICT DO NOTHING",
                                id,
                                space,
                                vector.length,
                                vectorLiteral(vector)) > 0;
        }

        public void requireDimensions(String space, int dimensions) {
                var widths = jdbc.queryForList(
                                "SELECT dimensions FROM knowledge.retrieval_spaces WHERE embedding_space=?",
                                Integer.class,
                                space);
                if (!widths.isEmpty() && widths.getFirst() != dimensions)
                        throw new IndexUnavailableException(
                                        "Query embedding dimensions do not match indexed model space");
        }

        private RetrievalResult.Passage passage(ResultSet rs, int row) throws SQLException {
                return new RetrievalResult.Passage(
                                rs.getString("content"),
                                rs.getDouble("score"),
                                new Citation(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("source_id"),
                                                rs.getInt("source_version"),
                                                rs.getString("checksum"),
                                                rs.getString("chunk_hash"),
                                                rs.getString("classification")));
        }

        public static LocalDate date(RetrievalRequest request) {
                return request.asOf() == null ? LocalDate.now(ZoneOffset.UTC) : request.asOf();
        }

        public static String vectorLiteral(float[] vector) {
                if (vector == null || vector.length == 0 || vector.length > 16000)
                        throw new IndexUnavailableException("Invalid embedding dimension");
                double magnitude = 0;
                StringJoiner out = new StringJoiner(",", "[", "]");
                for (float value : vector) {
                        if (!Float.isFinite(value))
                                throw new IndexUnavailableException("Non-finite embedding");
                        magnitude += (double) value * value;
                        out.add(Float.toString(value));
                }
                if (magnitude == 0)
                        throw new IndexUnavailableException("Zero vector has undefined cosine similarity");
                return out.toString();
        }
}
