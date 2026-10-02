
# Data Classification Standard

## 1. Purpose

This standard defines the classification levels for data handled by the
platform and establishes the minimum storage, encryption, retention, and AI
handling requirements for each classification.

## 2. Classification Levels

| Class                           | Examples                                                                  | Storage                                           | Encryption                                          | Retention                 | AI Usage Rules                                                                  |
| ------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------- |
| **C0 — Public**          | Published fraud policy, public T&Cs                                       | Object store                                      | TLS                                                 | 5 years                   | Free to embed and retrieve                                                      |
| **C1 — Internal**        | Runbooks, ADRs, model cards, prompt templates                             | Git + object store                                | TLS + at-rest                                       | 3 years                   | Retrievable; no PII in prompts                                                  |
| **C2 — Confidential**    | Transaction records, device fingerprints, merchant data, case notes       | PostgreSQL                                        | TLS + at-rest + column-level encryption for amounts | 7 years                   | Retrievable with redaction; never sent to external providers unredacted         |
| **C3 — Restricted**      | PII, KYC documents, government IDs, SAR/STR drafts, authentication tokens | Tokenized PostgreSQL + KMS-protected object store | TLS + KMS + field-level tokenization                | Per applicable regulation | **Not embeddable.** Tokenized references only. No external provider calls |
| **C4 — Model Artifacts** | Embeddings, model weights, evaluation datasets, traces                    | pgvector + object store                           | At-rest                                             | 2 years                   | Traces must be scrubbed of C3 data before storage                               |

## 3. Handling Rules

### 3.1 Field-Level Classification

Classification is a **field-level attribute** in the schema.

Classification must not be inferred solely from the table, database, service,
or container in which data is stored.

### 3.2 Classification Ceiling

The retrieval service enforces a **maximum classification level per caller**.

A caller may only retrieve data at or below the classification ceiling
assigned to its identity.

For example, a standard service identity cannot retrieve C3 data.

### 3.3 Model Gateway Redaction

Redaction is enforced at the **model gateway** before data is passed to a
model.

Redaction must not depend on prompts, agent instructions, or application
logic because those controls may be modified or bypassed.

### 3.4 Embeddings

Embeddings inherit the classification of their source chunk.

**C3 data must never be embedded.**

Any retrieval system must preserve the source classification when creating,
storing, indexing, or retrieving embeddings.

### 3.5 Audit Records

Audit records are classified as **C2/C3** depending on the information they
contain.

Audit records must be:

* Append-only
* Protected against unauthorized modification
* Retained for 7 years where applicable
* Subject to the same access and encryption controls as their classification

## 4. Classification Principles

1. **Default to the higher classification** when the appropriate classification
   is uncertain.
2. **Classification follows the data** when data is copied, transformed,
   indexed, or embedded.
3. **Access control must enforce classification**, rather than relying solely
   on application conventions.
4. **Sensitive data must not be exposed to external AI providers** unless the
   applicable classification explicitly permits it and required redaction
   controls have been applied.
5. **C3 data must remain outside embedding pipelines.**
6. **Derived data must be reviewed for classification** rather than
   automatically treated as public or less sensitive.

## 5. Ownership

The **Data Owner** is responsible for determining the appropriate
classification of data.

The **System/Service Owner** is responsible for implementing and maintaining
the required technical controls.

Security or compliance teams may review classifications and handling controls
as required.
