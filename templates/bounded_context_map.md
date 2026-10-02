
# Bounded Context Map

**System:** [System Name]
**Architecture:** [Modular Monolith / Microservices / Hybrid]
**Version:** [1.0]
**Date:** [YYYY-MM-DD]
**Owner:** [Team / Architect]

## 1. Context Overview

| # | Bounded Context | Purpose               | Owns               | Does NOT Own                |
| - | --------------- | --------------------- | ------------------ | --------------------------- |
| 1 | [Context A]     | [Business capability] | [Entities / rules] | [Excluded responsibilities] |
| 2 | [Context B]     | [Business capability] | [Entities / rules] | [Excluded responsibilities] |
| 3 | [Context C]     | [Business capability] | [Entities / rules] | [Excluded responsibilities] |

## 2. Context Definition

For each bounded context:

### [Context Name]

**Purpose**
[What business problem does this context solve?]

**Owns**

* [Entity / capability]
* [Entity / capability]
* [Business rules]

**Does NOT Own**

* [Responsibility owned elsewhere]
* [Responsibility owned elsewhere]

**Core Domain Concepts**

| Concept   | Meaning within this context |
| --------- | --------------------------- |
| [Concept] | [Context-specific meaning]  |
| [Concept] | [Context-specific meaning]  |

**Aggregates**

| Aggregate   | Aggregate Root | Key Invariants                |
| ----------- | -------------- | ----------------------------- |
| [Aggregate] | [Root]         | [Rules that must remain true] |

**Commands**

| Command   | Purpose  |
| --------- | -------- |
| [Command] | [Action] |

**Events Published**

| Event          | Meaning              |
| -------------- | -------------------- |
| `[Event].v1` | [Fact that occurred] |

**Events Consumed**

| Event          | Source Context | Purpose    |
| -------------- | -------------- | ---------- |
| `[Event].v1` | [Context]      | [Reaction] |

## 3. Context Relationships

| Source      | Relationship | Target      | Contract                |
| ----------- | ------------ | ----------- | ----------------------- |
| [Context A] | Depends on   | [Context B] | API / Interface / Event |
| [Context B] | Publishes to | [Context C] | Event                   |
| [Context C] | Queries      | [Context A] | API                     |

### Relationship Rules

* Contexts do not access another context's persistence directly.
* Cross-context communication occurs through explicit contracts.
* Domain concepts are interpreted according to the receiving context's model.
* Dependencies should be intentional and documented.
* Circular dependencies should be avoided.

## 4. Data Ownership

| Context     | Authoritative Data | Storage                     | Classification |
| ----------- | ------------------ | --------------------------- | -------------- |
| [Context A] | [Data]             | [PostgreSQL / Object Store] | [Level]        |
| [Context B] | [Data]             | [Storage]                   | [Level]        |

> Each context is authoritative for its own domain model and data.

## 5. Interface Boundary

**Inbound**

```text
[Interface / API / Command]
```

**Outbound**

```text
[Event / API / Interface]
```

**Communication Style**

```text
In-process interface
REST
gRPC
Event
Workflow signal
```

## 6. Security Boundary

**Who may access this context?**
[Roles / services / workloads]

**Authorization model:**
[RBAC / ABAC / policy-based]

**Sensitive data processed:**
[None / Internal / Confidential / Restricted]

**Audit requirements:**
[Actions requiring audit]

## 7. AI / Automation Boundary

**AI components allowed:**
[None / Agent / Model / Retrieval]

**Can consume AI output?**
[Yes / No]

**Can initiate writes?**
[Yes / No]

**Requires deterministic validation?**
[Yes / No]

**Human approval required?**
[Yes / No / Specific operations]

## 8. Current Implementation

**Deployment:**
[Module inside monolith / Independent service]

**Code location:**

```text
/src/[context-name]/
```

**Persistence:**
[Schema / tables / repository]

**Internal contract:**
[Interface / application service]

## 9. Future Extraction

**Potential service boundary:**
[Yes / No]

**Extraction candidate:**
[Yes / No / Undetermined]

**Extraction dependencies:**

* [Dependency]
* [Dependency]

**Conditions for extraction:**

* [Scaling requirement]
* [Independent deployment requirement]
* [Security/isolation requirement]
* [Team ownership requirement]

## 10. Boundary Rules

1. This context owns [capability].
2. Other contexts must not directly modify its state.
3. Other contexts interact through published contracts.
4. Domain logic remains inside the owning context.
5. Shared infrastructure does not imply shared domain ownership.
6. Changes to the context's public contract must be versioned.
7. New cross-context dependencies must be documented.

## 11. Context Validation

* [ ] Business responsibility is cohesive.
* [ ] Ownership is clear.
* [ ] "Does NOT own" boundary is explicit.
* [ ] Domain vocabulary is defined.
* [ ] Data ownership is defined.
* [ ] Cross-context relationships are documented.
* [ ] No direct database coupling exists.
* [ ] Security boundary is defined.
* [ ] AI/write authority is defined where relevant.
* [ ] Current modular implementation is identified.
* [ ] Future extraction implications are understood.

## 12. Context Map

```text
[Context A]
     |
     | [Contract / Event]
     v
[Context B]
     |
     | [Contract / Event]
     v
[Context C]
```

## 13. Change History

| Version | Date       | Author   | Change             |
| ------- | ---------- | -------- | ------------------ |
| 1.0     | YYYY-MM-DD | [Author] | Initial definition |
