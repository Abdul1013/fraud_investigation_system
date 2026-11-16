<!--
============================================================================
FILE:        docs/week32-implementation-plan.md
PURPOSE:     Align tracing, metrics, accounting and troubleshooting resources with executable evidence.
OWNER:       Fraud Investigation Engineering
SINCE:       week-32
RELATED:     context.md, curriculum week 32
NOTES:
  - Human review remains pending; executable checks determine technical status.
============================================================================
-->

# Week 32 implementation plan

Status: technical checks passed; reviewed

| Resource concept      | Module                                           | Acceptance                                                                                          |
| --------------------- | ------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| Distributed traces    | OperationalTelemetry and lifecycle adapters      | One correlated API/outbox/activity/model/retrieval/tool/approval/execution trace plus audit linkage |
| Async context         | Owning-context trace-parent columns              | W3C identifiers survive retries/restarts without credentials or baggage                             |
| Metrics and versions  | LifecycleTelemetry, CostMetrics                  | Fixed stage/outcome/release/queue labels, bounded HTTP dimensions                                   |
| Usage and cost        | Native model receipt usage and gateway counters  | Actual provider counts where reported; local compute assumptions separate from API charges          |
| Collector and storage | Local OTLP collector, Prometheus, Tempo, Grafana | Real ingestion/query and sanitized samples                                                          |
| Alerts                | Prometheus rules and forced failure/slow request | Actual firing and recovery, not just configuration files                                            |
| Operational learning  | Dashboard and troubleshooting runbook            | Reproducible demonstration with fixture/native evidence separated                                   |

Use Boot-managed Micrometer 1.15.5 and OpenTelemetry 1.49.0. Local backend pins were checked against primary release pages on 26 October 2026: collector 0.163.2, Prometheus 3.15.1, Grafana 13.2.4, Tempo 3.1.1. Compatibility is established by actual startup and protocol checks, not release recency alone.

The application pushes OTLP metrics and traces to loopback collector endpoints. The collector exports a private Prometheus scrape target; the application does not add a public metrics route. Span attribute allowlisting removes raw URLs and exception messages before storage. Model prompts, credentials and evidence are not span attributes.
