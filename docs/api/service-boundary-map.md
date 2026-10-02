
# API & Service-Boundary Map: 

Modular Monolith First, Service-Oriented Boundaries by Design

**Version:** 1.0
**Status:** Proposed
**Owner:** Architecture / Platform Engineering
**Last Updated:** 2026-10-02

# 1. Architectural Principles

The platform follows these service-boundary principles:

1. **One clear business capability per service.**
2. **Each service owns its domain data and persistence model.**
3. **Services communicate only through published contracts.**
4. **No service directly accesses another service's database.**
5. **Synchronous communication is used for request/response operations requiring an immediate result.**
6. **Asynchronous events are used for decoupled state propagation and long-running workflows.**
7. **External clients never call internal services directly.**
8. **Authentication and coarse-grained authorization are enforced at the edge; fine-grained authorization is enforced by the receiving service.**
9. **Every service-to-service request is authenticated using workload identity and mutually authenticated transport.**
10. **Business identity and tracing context are propagated separately.**
11. **AI agents cannot directly mutate business systems outside explicitly authorized tools.**
12. **Contracts are versioned and backward compatibility is maintained within a supported version window.**

OWASP recommends defense-in-depth authorization at the gateway/proxy, microservice, and business-logic levels rather than relying exclusively on an API gateway.

# 2. Trust Zones

```text
┌───────────────────────────────────────────────────────────────┐
│                       EXTERNAL ZONE                          │
│                                                               │
│  Analyst UI / Admin UI / External Clients                    │
└─────────────────────────────┬─────────────────────────────────┘
                              │ HTTPS
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                     EDGE / TRUST ZONE                         │
│                                                               │
│  API Gateway / Ingress                                       │
│  Authentication / Rate Limiting / WAF / Coarse Authorization │
│                                                               │
└───────────────┬───────────────────────────┬───────────────────┘
                │                           │
                ▼                           ▼
┌───────────────────────────┐     ┌─────────────────────────────┐
│      DOMAIN ZONE          │     │       AI / INTELLIGENCE     │
│                           │     │          ZONE               │
│ ingestion-svc             │     │ agent-runtime               │
│ detection-svc             │     │ retrieval-svc               │
│ case-svc                  │     │ model-gateway               │
│ workflow-svc              │     │ tool-gateway / registry     │
│ decision-svc              │     │                             │
└─────────────┬─────────────┘     └──────────────┬──────────────┘
              │                                   │
              └────────────────┬──────────────────┘
                               ▼
                  ┌─────────────────────────────┐
                  │ GOVERNANCE / EVIDENCE ZONE  │
                  │                             │
                  │ evidence-svc                │
                  │ audit-svc                   │
                  │ reporting-svc               │
                  │ eval-svc                    │
                  └─────────────────────────────┘
```

Internal services must not be publicly reachable. NIST specifically identifies API gateways, secure service-to-service communication, service discovery, and service-mesh controls as important components of secure microservice architectures.

# 3. Service Boundary Definition

Every service MUST be documented using the following attributes:

| Attribute                     | Description                                        |
| ----------------------------- | -------------------------------------------------- |
| **Service**             | Unique service identifier                          |
| **Business Capability** | Business responsibility owned by the service       |
| **Data Ownership**      | Data for which the service is authoritative        |
| **Inbound Contracts**   | APIs/events accepted                               |
| **Outbound Contracts**  | APIs/events produced                               |
| **Communication Style** | REST, gRPC, event, workflow signal                 |
| **Allowed Callers**     | Services permitted to call it                      |
| **Dependencies**        | Services/infrastructure it may consume             |
| **Trust Level**         | External, internal, privileged, restricted         |
| **Authorization Model** | RBAC, ABAC, policy-based, resource-level           |
| **Synchronous SLA**     | Expected latency/availability requirements         |
| **Failure Behaviour**   | Timeout, retry, circuit breaker, dead letter, etc. |



# 4. Domain Service Map

| Service                 | Business Capability                | Owns / Is Authoritative For                      | Inbound                                        | Outbound                                       | Style                    | Allowed Callers                            |
| ----------------------- | ---------------------------------- | ------------------------------------------------ | ---------------------------------------------- | ---------------------------------------------- | ------------------------ | ------------------------------------------ |
| **ingestion-svc** | Signal intake and normalization    | Raw/normalized fraud signals                     | `POST /v1/signals`, `GET /v1/signals/{id}` | `SignalReceived.v1`, `SignalNormalized.v1` | REST + events            | Edge, approved producers                   |
| **detection-svc** | Risk/fraud scoring                 | Scores and scoring metadata                      | `ScoreService` gRPC                          | `ScoreComputed.v1`                           | gRPC + events            | ingestion-svc, authorized internal callers |
| **case-svc**      | Case lifecycle management          | Cases, case state, assignments                   | Case REST API                                  | `CaseCreated.v1`, `CaseStateChanged.v1`    | REST + events            | analyst-api, workflow-svc, decision-svc    |
| **workflow-svc**  | Investigation orchestration        | Workflow/investigation state                     | Investigation API + Temporal signals           | workflow events/signals                        | REST + Temporal          | case-svc, agent-runtime                    |
| **decision-svc**  | Recommendation and decision policy | Recommendations, approvals, decision records     | Decision API                                   | `DecisionCreated.v1`, `ActionApproved.v1`  | REST + events            | workflow-svc, analyst-api                  |
| **evidence-svc**  | Evidence management                | Evidence metadata, integrity records, references | Evidence API                                   | `EvidenceAdded.v1`                           | REST + events            | case-svc, workflow-svc                     |
| **audit-svc**     | Security/business audit trail      | Immutable audit records                          | `POST /v1/audit`                             | Audit stream/export                            | REST + append-only store | All authorized services                    |
| **reporting-svc** | Regulatory/operational reporting   | Generated reports                                | Report API                                     | Report events                                  | REST + async jobs        | authorized consumers                       |
| **eval-svc**      | Model/agent evaluation             | Evaluation runs/results                          | Evaluation API                                 | Eval events                                    | REST + async             | ML/AI engineering                          |

# 5. Intelligence / AI Service Map

| Service                 | Responsibility                                    | Owns                                           | Must NOT Do                                              | Communication |
| ----------------------- | ------------------------------------------------- | ---------------------------------------------- | -------------------------------------------------------- | ------------- |
| **agent-runtime** | Agent planning and controlled execution           | Agent run/step state                           | Directly modify domain databases or call model providers | REST          |
| **tool-registry** | Tool catalog, schemas, permissions                | Tool definitions and versions                  | Execute arbitrary business actions                       | REST          |
| **tool-gateway**  | Secure tool execution                             | Execution metadata                             | Allow unapproved tools or bypass authorization           | REST/gRPC     |
| **retrieval-svc** | Retrieval and knowledge access                    | Retrieval indexes / corpus metadata            | Become system of record for source business data         | REST          |
| **model-gateway** | Model/provider abstraction and policy enforcement | Provider configuration, model routing metadata | Allow services to call providers directly                | REST/gRPC     |

### Mandatory AI Boundary

```text
workflow-svc
      │
      ▼
agent-runtime
      │
      ├──────────► retrieval-svc
      │
      ├──────────► tool-gateway
      │                 │
      │                 ▼
      │            domain services
      │
      ▼
model-gateway
      │
      ▼
approved model provider(s)
```

The **agent runtime is not a privileged service account for the whole  platform**.

It receives only the permissions required for the current workflow and can perform state-changing operations only through explicitly registered and authorized tools.

# 6. Edge / External API

External users and clients access the platform through an API Gateway or equivalent ingress layer.

### External API

| Endpoint                             | Capability/ Purpose                            | Authorization                                     |
| ------------------------------------ | ----------------------------------------------- | ------------------------------------------------- |
| `GET /api/v1/queue`                | Analyst work queue                              | `analyst:queue:read` (analyst role)             |
| `GET /api/v1/cases/{id}`           | Case detail + evidence + citation               | `case:read` `(analyst role)`                 |
| `POST /api/v1/cases/{id}/decision` | Approve/override decision                       | Analyst`case:decision` + step-up authentication |
| `POST /api/v1/cases/{id}/comment`  | Add analyst comment/ note, feed future retrival | `case:comment` `(analyst role)`              |
| `GET /api/v1/audit/{caseId}`       | Full Audit Trial export                        | `audit:read` (Auditor Role)                     |
| `/actuator/health`                 | Service health                                  | Internal operations network                       |
| `/actuator/prometheus`             | Metrics                                         | Internal operations network                       |

The gateway should provide:

* TLS termination
* Authentication
* Coarse authorization
* Rate limiting
* Request size limits
* WAF/security filtering where applicable
* API version routing
* Request validation
* Audit/event emission for security-relevant operations

The gateway must **not** be the only authorization layer. Business and resource-level authorization remains the responsibility of the receiving service.

# 7. Internal API Rules

- Internal APIs are never publicly exposed.
- Every request carries `X-Correlation-Id`, `X-Case-Id` (if applicable), `X-Actor-Id`.

* Every AI-produced payload is schema-validated (JSON Schema) before crossing a boundary.
* Write endpoints are idempotent via `Idempotency-Key`.
* No service calls a model provider directly; all traffic goes through `model-gateway`.

### Synchronous communication

Use:

* REST for conventional service APIs
* gRPC for latency-sensitive, high-volume internal RPC
* Temporal signals/commands for durable workflow interactions

gRPC is well suited to coarse-grained service-to-service message exchange,
while the exact choice should be driven by the communication requirement rather
than by applying one protocol universally.

### Asynchronous communication

An event broker is used for domain events.

Example:

```text
ingestion-svc
      │
      └──► SignalReceived.v1
                   │
                   ▼
            detection-svc
                   │
                   └──► ScoreComputed.v1
                              │
                              ▼
                          case-svc
                              │
                              └──► CaseCreated.v1
                                         │
                                         ▼
                                     workflow-svc
```

Events represent **facts that happened**, not hidden RPC calls.

Prefer:

```text
CaseCreated.v1
ScoreComputed.v1
EvidenceAdded.v1
DecisionApproved.v1
```

over:

```text
CreateCaseCommand
DoFraudCheck
RunAgentCommand
```

Commands may still be used where an explicit directed action is required.

# 8. Data Ownership Rules

Each domain service owns its persistence.

```text
ingestion-svc  ──► ingestion database
detection-svc  ──► detection database
case-svc       ──► case database
workflow-svc   ──► workflow database
decision-svc   ──► decision database
evidence-svc   ──► evidence metadata database
audit-svc      ──► immutable audit store
retrieval-svc  ──► retrieval/index store
```

### Prohibited

```text
case-svc ──► SELECT * FROM detection_db.scores
```

### Required

```text
case-svc ──► detection-svc API
```

or:

```text
detection-svc ──► ScoreComputed.v1 ──► event broker
```

This preserves service autonomy and prevents persistence-level coupling.

# 9. Service-to-Service Authentication

Every service has its own **workload identity**.

```text
Service A
   │
   │ mTLS
   ▼
Service B
```

Recommended controls:

* Workload identity
* mTLS for internal traffic
* Service-level authorization
* Short-lived credentials/tokens where applicable
* Centralized key/certificate rotation
* Network policies restricting which services may communicate

NIST's cloud-native zero-trust guidance emphasizes application and service
identity rather than relying on network location, while OWASP recommends
proxy-mediated mTLS/workload identity for service-to-service authentication.

# 10. Identity Propagation

Do **not** trust arbitrary client-supplied values such as:

```http
X-Actor-Id: admin
```

The caller's identity should originate from an authenticated identity provider and be propagated internally in a trusted, verifiable representation.

Conceptually:

```text
External Token
      │
      ▼
API Gateway
      │
      ├── authenticates user
      ├── authorizes coarse operation
      └── creates trusted internal identity context
                     │
                     ▼
              Internal Services
```

OWASP specifically warns against blindly forwarding externally supplied identity headers and recommends a trusted signed internal identity representation for downstream services.

# 11. Request Context & Traceability

Replace the current correlation model with two distinct concepts.

### Distributed tracing

Use standard W3C Trace Context `OpenTelemetry uses W3C Trace Context propagation, including traceparent, for cross-service tracing.`

```http
traceparent: 00-<trace-id>-<span-id>-01
tracestate: ...
```

### Business context

Explicit application metadata where applicable such as

```http
X-Case-Id: CASE-12345
X-Request-Id: REQ-12345
```

### Actor identity

Do not accept from untrusted clients, Instead derive actor identity from the authenticated principal/internal identity context.

```http
X-Actor-Id: ...
```

# 12. Idempotency

`Idempotency-Key` is required for **state-changing operations that may be retried**.

Examples:

```http
POST /v1/cases
POST /v1/evidence
POST /v1/approvals
POST /v1/actions
```

It is not necessary to require an idempotency key on every GET request.

The receiving service must:

1. Validate the key.
2. Associate it with the authenticated caller and operation.
3. Persist the result.
4. Return the original result for a repeated request with the same key.
5. Prevent conflicting reuse of the same key.

# 13. Contract Standards

Every externally or internally published API must have a machine-readable contract.

### REST

Use:

```text
OpenAPI
```

### gRPC

Use:

```text
Protocol Buffers
```

### Events

Use:

```text
AsyncAPI
+ event schema
```

Every contract must define:

* Request schema
* Response schema
* Error schema
* Authentication requirements
* Authorization requirements
* Version
* Timeout expectations
* Idempotency behaviour
* Retry behaviour
* Data classification
* PII/sensitive-field handling

# 14. API Versioning

Version APIs explicitly.

```text
/api/v1/...
/v1/...
```

Do not introduce breaking changes into an existing version.

Example:

```text
v1 → additive changes only

v2 → breaking contract changes
```

Events must also be versioned:

```text
ScoreComputed.v1
ScoreComputed.v2
```

# 15. Reliability Requirements

Every synchronous dependency must define:

* Timeout
* Retry policy
* Maximum retry count
* Backoff strategy
* Circuit breaker policy
* Fallback behaviour
* Bulkhead/isolation strategy where appropriate

Example:

```text
agent-runtime
      │
      ├── retrieval-svc
      │      timeout: 500ms
      │      retries: 2
      │
      └── model-gateway
             timeout: 10s
             retries: policy-based
             circuit breaker: enabled
```

NIST identifies load balancing, circuit breaking, throttling, and monitoring as important resilience capabilities for microservice architectures.

# 16. AI Payload Boundary

No AI-generated data crosses a service boundary without validation.

```text
Model
  │
  ▼
model-gateway
  │
  ▼
Schema Validation
  │
  ├── invalid → reject/quarantine
  │
  └── valid
       │
       ▼
agent-runtime
       │
       ▼
authorized tool/service
```

Every AI-generated object must have:

* Schema version
* Producer
* Correlation/trace context
* Confidence/uncertainty where applicable
* Provenance
* Data classification
* Validation result

AI output must never be treated as trusted merely because it came from an
internal model.

# 17. Tool Boundary

Agents do not receive unrestricted service credentials.

```text
agent-runtime
      │
      ▼
tool-gateway
      │
      ├── authorize tool
      ├── authorize resource
      ├── validate arguments
      ├── apply policy
      ├── execute
      └── audit
              │
              ▼
          domain service
```

A tool should be:

* Explicitly registered
* Schema-defined
* Versioned
* Permissioned
* Audited
* Rate-limited
* Bound to an allowed caller
* Bound to an allowed target/resource

For high-impact actions:

```text
Agent recommendation
       ↓
Decision service
       ↓
Human approval / policy approval
       ↓
Tool gateway
       ↓
Action execution
```

# 18. Audit Boundary

`audit-svc` is a **sink**, not a general-purpose data store.

Services send security/business audit events to it:

```text
service
   │
   └──► audit-svc
             │
             └──► append-only storage
```

Audit records should include at minimum:

```text
event_id
timestamp
trace_id
actor
actor_type
service
action
resource_type
resource_id
result
policy_decision
source_ip / workload identity where appropriate
classification
```

Audit records must not be modified by ordinary application services.

# 19. Endpoint Design

### ingestion-svc

```text
POST /v1/signals
GET  /v1/signals/{signalId}
```

### detection-svc

Prefer gRPC contract:

```text
ScoreService.Score
ScoreService.GetScore
```

rather than exposing a REST-style path internally when gRPC is the selected transport.

### case-svc

```text
POST  /v1/cases
GET   /v1/cases/{caseId}
PATCH /v1/cases/{caseId}/state
GET   /v1/cases?queue={queue}
```

### workflow-svc

```text
POST /v1/investigations
GET  /v1/investigations/{investigationId}
GET  /v1/investigations/{investigationId}/events
```

### decision-svc

```text
POST /v1/recommendations
POST /v1/approvals
POST /v1/actions
```

### evidence-svc

```text
POST /v1/evidence
GET  /v1/evidence?caseId={caseId}
GET  /v1/evidence/{evidenceId}
```

### retrieval-svc

```text
POST /v1/retrieve
POST /v1/ingest
GET  /v1/corpus/{corpusId}/versions
```

### model-gateway

```text
POST /v1/chat
POST /v1/embed
```

External model-provider credentials and provider-specific APIs remain entirely behind this boundary.

# 20. Dependency Rules

Each service should maintain an explicit dependency allow-list.

Example:

```text
ingestion-svc
  └── event-bus

detection-svc
  ├── event-bus
  └── scoring/model interface

case-svc
  ├── detection-svc
  └── audit-svc

workflow-svc
  ├── case-svc
  ├── decision-svc
  ├── agent-runtime
  └── Temporal

agent-runtime
  ├── retrieval-svc
  ├── model-gateway
  └── tool-gateway
```

A service must not introduce an undeclared direct dependency on another service.

# 21. Service Boundary Rules

A service boundary should be reconsidered when:

* A service has multiple unrelated business responsibilities.
* Multiple teams need to deploy it independently.
* The service's data is being directly accessed by other services.
* Business rules are duplicated across services.
* A service requires excessive synchronous calls to another service.
* An AI component gains direct access to business databases.
* A service becomes an authorization bypass.
* A service cannot evolve its API without coordinating deployments across the
  platform.

# 22. Reference Interaction Model

```text
                         ┌───────────────┐
                         │  Analyst/Admin│
                         └───────┬───────┘
                                 │
                              HTTPS
                                 │
                         ┌───────▼───────┐
                         │  API Gateway  │
                         └───────┬───────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
              ▼                  ▼                  ▼
         ┌─────────┐        ┌──────────┐      ┌──────────┐
         │ Case    │        │ Decision │      │ Reporting│
         │ Service │        │ Service  │      │ Service  │
         └────┬────┘        └────┬─────┘      └──────────┘
              │                  │
              ▼                  ▼
         ┌──────────┐       ┌──────────────┐
         │ Workflow │◄─────►│ Agent Runtime│
         │ Service  │       └──────┬───────┘
         └────┬─────┘              │
              │             ┌──────┼────────┐
              │             │      │        │
              │             ▼      ▼        ▼
              │        Retrieval  Model   Tool
              │          Svc     Gateway Gateway
              │
              ▼
         ┌───────────┐
         │ Event Bus │
         └─────┬─────┘
               │
        ┌──────┴────────┐
        ▼               ▼
   Ingestion       Detection
      Svc             Svc

Cross-cutting:
────────────────────────────────────────────────────
Identity / IAM
Service Identity + mTLS
Policy / Authorization
Observability / OpenTelemetry
Audit
Secrets / KMS
Schema Registry
```

# 23. Contract Rules

The following rules are mandatory:

* Every request has distributed trace context.
* Business correlation identifiers are propagated where applicable.
* Caller identity is derived from authenticated identity, not trusted from
  arbitrary client headers.
* Every internal service authenticates the caller.
* Every service authorizes access to its own resources.
* No direct cross-service database access.
* Mutating retryable operations support idempotency.
* Every API/event has a versioned schema.
* AI-generated payloads are schema validated before being consumed.
* No service calls an external model provider directly.
* Agent actions must pass through an authorized tool boundary.
* Restricted operations must generate an audit record.
* Internal services are not externally addressable.
* Sensitive data classification accompanies data across service boundaries.

# 24. Service Contract Checklist

Before introducing a service, answer:

```text
Service:
Business capability:
Service owner:
Data owner:

What data does it own?
What data can it read?
What data can it modify?

Who can call it?
How are callers authenticated?
How is authorization enforced?

What are its synchronous APIs?
What are its asynchronous events?

What is its timeout?
What is its retry policy?
What is its failure behaviour?

What data classification does it process?
Can it process restricted data?

Does it process AI-generated data?
Can it call the model gateway?
Can it execute tools?

What does it audit?
What telemetry does it emit?

What is its API/event version?
What are its compatibility guarantees?
```

## 25. Architectural Position

The service boundary is defined by **business responsibility + data ownership + trust boundary + contract**, not merely by URL structure.

A REST endpoint being present does not automatically make a good service boundary.

The architecture should therefore optimize for:

```text
Clear ownership
       +
Low coupling
       +
Explicit contracts
       +
Independent authorization
       +
Independent deployment
       +
Controlled trust boundaries
```
