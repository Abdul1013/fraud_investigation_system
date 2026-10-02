service level objective (SLO) are our internal target set to ensure that our services deliver meet customers’ expectations. These customer expectations are outlined in service level agreements (SLAs),  between us and the customer. We use uptime monitoring services to track your uptime and downtime throughout the given peroid. It can include automated monitoring and alerts, allowing us to track uptime and other key metrics constantly.

- It should support our SLA
- Keep it simple
- It should be adaptable

## SLO Targets

| SLI                                                 | Target    | Window      | Error budget | Owner               |
| --------------------------------------------------- | --------- | ----------- | ------------ | ------------------- |
| Signal → case created (p99)                        | ≤ 30 s   | 28d         | 0.1%         | ingestion           |
| Case triage complete (p95)                          | ≤ 60 s   | 28d         | 5%           | detection           |
| AI investigation complete (p95)                     | ≤ 5 min  | 28d         | 5%           | orchestration       |
| Approval → action executed (p99)                   | ≤ 10 s   | 28d         | 0.1%         | decision            |
| Case API availability                               | 99.9%     | 28d         | 43 min       | case-svc            |
| Agent orchestration availability                    | 99.5%     | 28d         | 3.6 h        | workflow-svc        |
| Retrieval p95 latency                               | ≤ 800 ms | 28d         | 5%           | retrieval           |
| Citation coverage (answers with ≥1 valid citation) | ≥ 95%    | 7d          | 5%           | retrieval + agent   |
| Groundedness (no unsupported claims)                | ≥ 98%    | 7d          | 2%           | eval                |
| Unsafe write attempts blocked                       | 100%      | 7d          | 0            | decision + security |
| Cost per investigated case                          | ≤ $0.35  | 28d         | 10%          | platform            |
| Eval gate pass rate on release                      | 100%      | per release | 0            | eval                |

**Failure policy:** if error budget for "unsafe write attempts blocked" is consumed, all automated write actions are frozen and every action requires dual analyst approval until root-caused.
