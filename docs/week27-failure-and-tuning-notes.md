# Week 27 failure and tuning notes

These notes explain the measured retrieval decisions and remaining answer-quality failures. They accompany the [question-level score report](evidence/week27/semantic/retrieval-comparison.json) and [readable comparison](evidence/week27/semantic/retrieval-comparison.md), using the [golden dataset](../eval/datasets/week27-retrieval.json).

## Evaluation conditions

The recorded run on 13 October 2026 used real local EmbeddingGemma embeddings through Ollama, deterministic extractive answers, the same published corpus for both strategies, and k=5. The JSON report pins the dataset digest, corpus version, embedding model digest, retrieval configuration, and answer/prompt versions. The dataset has 12 answerable questions and 2 unsupported questions. These are small learning samples, not production fraud-performance evidence.

| Metric              | VECTOR | HYBRID |
| ------------------- | -----: | -----: |
| Recall@5            |  1.000 |  1.000 |
| Precision@5         |  0.233 |  0.233 |
| MRR                 |  0.958 |  0.944 |
| Answer relevance    |  0.917 |  0.917 |
| Exact quote support |  1.000 |  1.000 |
| Unknown abstention  |  1.000 |  1.000 |

## Observed failure: paraphrased evidence question

**Question:** “Which paperwork proves that the goods reached the buyer?”

**Expected evidence:** the Evidence Requirements section of `policies/chargeback-policy@3`, including delivery confirmation.

Both strategies retrieve the labelled evidence within the first five passages. The remaining failure occurs in quotation selection:

- VECTOR returns `INSUFFICIENT_EVIDENCE`: the deterministic selector cannot bridge the query wording to the evidence wording.
- HYBRID returns `ANSWERED`, but its accepted quotation is not from the labelled answer passage. Its text is supported by its citation, yet does not satisfy the relevance label.

**Lesson:** source/citation validity and answer relevance are separate properties. Exact quotation checks prevent invented quotations; they cannot establish that a quotation answers the question. The report retains this failure instead of claiming complete answer quality.

## Implemented tuning decisions

| Decision                                                     | Reason and evidence                                                                                                                      | Trade-off                                                                                     |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| VECTOR is the default                                        | Both strategies have equal Recall@5; VECTOR has slightly better MRR in this run and is the simpler path.                                 | This dataset does not prove VECTOR is always better. HYBRID remains selectable.               |
| Reciprocal-rank fusion with constant 60                      | Cosine and keyword scores use different scales. Combining ranks rewards agreement without averaging incompatible raw scores.             | The fusion constant is a baseline, not an exhaustively optimized value.                       |
| At least 20 candidates, increasing to 4×k with a cap of 100 | Candidate pools give fusion useful alternatives while keeping the small-corpus implementation bounded.                                   | Broader candidates introduce distractors. No candidate-pool sweep is claimed.                 |
| Vector similarity admission threshold 0.15                   | Makes candidate admission explicit and records it in the configuration version.                                                          | It is not an answer-confidence threshold and has not been calibrated on a large dataset.      |
| Exact scoped search                                          | All strategies enforce corpus, classification, type, and effective-date scope before ranking. Exact search is inspectable for 42 chunks. | Production-sized approximate indexes require separate measurement.                            |
| Independent model-specific index                             | Semantic embeddings must not reuse Week 26 hash vectors. Model digest and dimensions identify the search space.                          | New models require explicit re-indexing.                                                      |
| Exact quotation grounding                                    | Backend-owned citations and substring/hash checks reject unsupported model text.                                                         | Supported quotes can still be irrelevant, as the retained failure shows.                      |
| Explicit warm-up before timing                               | Earlier serial timing results reversed when model loading affected the first strategy.                                                   | Final serial observations still do not constitute a load benchmark or reliable speed ranking. |

These are documented choices supported by the comparison and deterministic checks. They are not claims that every parameter was optimized.

## Other verified failure controls

- Missing model-specific index and unknown published corpus produce explicit setup errors.
- Invalid HTTP query/result bounds are rejected.
- Restricted and unpublished sources do not appear in either strategy.
- Old corpus requests do not silently retrieve newer source revisions.
- Unknown citation IDs, invented quotations, and tampered text cannot produce supported answers.
- A transient concurrent Batch metadata-creation conflict is retried narrowly while preserving repository isolation.

See [verification evidence](evidence/week27/verification.json) and the [implementation walkthrough](week27-implementation.md) for test scope and operational limits. The recorded suite passed 43 tests on 13 October 2026; it is evidence for that tested state, not proof of arbitrary later edits.
