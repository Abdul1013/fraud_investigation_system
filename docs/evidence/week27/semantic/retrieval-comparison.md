# Retrieval comparison

Dataset SHA-256: `5b0c70ce62148966d371c5e2de000c5e10f85ba62e09ff2ef0ace37f3548bf95`

Corpus: 3, k=5, embedding space: `ollama:embeddinggemma@85462619ee721b466c5927d109d4cb765861907d5417b9109caebc4e614679f1`, fixture=false

| Strategy | Recall@k | Precision@k | MRR | Answer relevance | Quote support | Unknown abstention | Mean ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| VECTOR | 1.000 | 0.233 | 0.958 | 0.917 | 1.000 | 1.000 | 40.9 |
| HYBRID | 1.000 | 0.233 | 0.944 | 0.917 | 1.000 | 1.000 | 37.4 |

Quote support means exact source substring plus valid citation, not semantic relevance. Latencies are serial observations after warm-up, not load-test percentiles. Fixtures cannot establish semantic quality.

## Failure examples

- VECTOR / paraphrase: recall=1.0, status=INSUFFICIENT_EVIDENCE, answerRelevant=false. Question: Which paperwork proves that the goods reached the buyer?
- HYBRID / paraphrase: recall=1.0, status=ANSWERED, answerRelevant=false. Question: Which paperwork proves that the goods reached the buyer?
