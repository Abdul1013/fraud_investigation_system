## Model / Provider Strategy

### Abstraction

```
Agent / Service
      |
      v
ModelGateway (port)          <-- Spring AI ChatClient / EmbeddingModel abstraction
      |
      +-- RoutingPolicy       (task -> tier -> provider)
  
      +-- RedactionFilter     (classification enforcement)
  
      +-- PromptRegistry      (promptId + version)
  
      +-- BudgetGuard         (token + cost caps per case)
  
      +-- FallbackChain       (primary -> secondary -> degraded mode)
  
      +-- TelemetryDecorator  (trace, cost, latency, version labels)
      |
      v
Provider Adapters: OpenAI | Anthropic 
```

### Routing table

| Task                                      | Tier   | Primary            | Fallback                 | Temp | Max tokens |
| ----------------------------------------- | ------ | ------------------ | ------------------------ | ---- | ---------- |
| Evidence summarization                    | cheap  | small model        | mid model                | 0.0  | 2k         |
| Policy/prior-case retrieval query rewrite | cheap  | small model        | none (skip rewrite)      | 0.0  | 512        |
| Fraud reasoning + recommendation          | strong | frontier model     | mid model                | 0.1  | 4k         |
| Compliance obligation check               | strong | frontier model     | none (escalate to human) | 0.0  | 2k         |
| Embeddings                                | fixed  | embedding model vN | cached only              | —   | —         |
| Customer communication draft              | mid    | mid model          | template fallback        | 0.2  | 1k         |

### Versioning contract

Every decision record stores:

```
model_provider, model_id, model_version,
prompt_id, prompt_version,
retrieval_config_version, corpus_version,
routing_policy_version, gateway_version
```

This is what makes ADR-003 and two-version comparison possible later.

### Degradation modes

- **Strong model unavailable** → mid model + mandatory human review.
- **All providers unavailable** → deterministic rules-only recommendation + human review, case marked `degraded`.
- **Budget exhausted for a case** → pause agent, escalate to human with partial evidence.
