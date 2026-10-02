
# Data Classification Standard

**Document Owner:** [Security / Data Governance Team]
**Version:** [1.0]
**Effective Date:** [YYYY-MM-DD]
**Review Frequency:** [Annual / Semi-Annual]
**Classification Scheme:** [4-level / 5-level]

## 1. Purpose

This standard defines the organization's data classification scheme and the
minimum handling requirements associated with each classification level.

The objective is to ensure that data receives protection appropriate to its
confidentiality, integrity, availability, legal, regulatory, contractual,
and business requirements.

## 2. Scope

This standard applies to all information created, received, processed,
stored, transmitted, or otherwise handled by:

* Employees and contractors
* Applications and services
* Databases and data stores
* Cloud and third-party services
* AI/ML systems and associated data pipelines
* Physical and electronic records

## 3. Classification Principles

1. Data must be classified according to its required level of protection.
2. Classification should consider confidentiality, integrity, availability,
   regulatory requirements, contractual obligations, and business impact.
3. Classification applies to information regardless of its format or storage
   location.
4. Classification should be applied at the **appropriate granularity**,
   including field-level classification where different fields require
   different protections.
5. When classification is uncertain, the higher applicable classification
   should be used until the data owner resolves the uncertainty.
6. Classification must remain associated with data when it is copied,
   transferred, transformed, exported, or processed by another system.
7. Classification levels must be communicated through appropriate labels or
   metadata.

## 4. Classification Levels

| Level        | Name                   | Definition                                                                                                                                                                                                 | Typical Examples                                                                                              |
| ------------ | ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| **L1** | **Public**       | Information approved for public disclosure with minimal impact from unauthorized disclosure.                                                                                                               | Public website content, published policies, marketing materials, public documentation                         |
| **L2** | **Internal**     | Information intended for authorized personnel and normal business use; unauthorized disclosure could cause limited operational or reputational impact.                                                     | Internal documentation, runbooks, ADRs, internal procedures, non-sensitive source code                        |
| **L3** | **Confidential** | Information whose unauthorized disclosure, modification, or loss could cause material business, financial, operational, contractual, or reputational impact.                                               | Customer records, transaction data, merchant information, business-sensitive analytics, case records          |
| **L4** | **Restricted**   | Highly sensitive information requiring strict access controls because unauthorized disclosure, modification, or loss could create significant legal, regulatory, security, privacy, or operational impact. | Government IDs, authentication secrets, sensitive PII, KYC documents, security credentials, regulated records |

## 5. Handling Requirements

| Control                                  | L1 Public                             | L2 Internal                     | L3 Confidential                        | L4 Restricted                                                     |
| ---------------------------------------- | ------------------------------------- | ------------------------------- | -------------------------------------- | ----------------------------------------------------------------- |
| **Access**                         | Public                                | Authorized personnel            | Need-to-know                           | Strict need-to-know + explicit authorization                      |
| **Storage**                        | Approved organizational storage       | Approved organizational systems | Approved protected systems             | Approved restricted systems                                       |
| **Encryption in transit**          | Required where technically applicable | Required                        | Required                               | Required                                                          |
| **Encryption at rest**             | Recommended                           | Required                        | Required                               | Required                                                          |
| **Strong access control**          | No                                    | Yes                             | Yes                                    | Yes                                                               |
| **MFA for privileged access**      | N/A                                   | Required where applicable       | Required                               | Required                                                          |
| **External sharing**               | Permitted                             | Controlled                      | Restricted / approved                  | Prohibited unless explicitly authorized                           |
| **Logging / monitoring**           | Basic                                 | Standard                        | Enhanced                               | Enhanced / security monitored                                     |
| **Secure disposal**                | Standard disposal                     | Standard disposal               | Secure deletion                        | Secure destruction / cryptographic erasure where applicable       |
| **AI / external model processing** | Permitted                             | Controlled                      | Redaction / approved provider required | Prohibited unless explicitly authorized and technically protected |

## 6. Retention

Retention is determined by the applicable legal, regulatory, contractual,
operational, and business requirements.

| Classification            | Default Retention Rule                             |
| ------------------------- | -------------------------------------------------- |
| **L1 Public**       | [Define organizational default]                    |
| **L2 Internal**     | [Define organizational default]                    |
| **L3 Confidential** | [Define organizational / regulatory requirement]   |
| **L4 Restricted**   | [Define applicable legal / regulatory requirement] |

Classification **must not be used as the sole basis for determining
retention**.

## 7. Data Labelling

Data should carry its classification through an appropriate label, metadata
field, filename convention, database attribute, document header, or system
tag.

Examples:

```text
PUBLIC
INTERNAL
CONFIDENTIAL
RESTRICTED
```

For structured systems:

```text
classification = "restricted"
```

Where technically appropriate, classification metadata should be propagated
automatically between systems.

## 8. Data Ownership

Every governed dataset or information asset must have an assigned **Data
Owner**.

The Data Owner is responsible for:

* Determining the appropriate classification
* Reviewing classification when circumstances change
* Approving exceptions
* Ensuring appropriate handling requirements are defined

The **System Owner** is responsible for implementing the technical controls
required by the classification.

## 9. Classification Decision Criteria

When assigning a classification, evaluate:

### Confidentiality

What would be the impact of unauthorized disclosure?

* Low
* Moderate
* High
* Critical

### Integrity

What would be the impact of unauthorized alteration or destruction?

* Low
* Moderate
* High
* Critical

### Availability

What would be the impact if the information became unavailable?

* Low
* Moderate
* High
* Critical

### Regulatory / Legal Impact

Does the data fall under:

* Privacy legislation
* Financial regulation
* Employment requirements
* Contractual confidentiality
* Security obligations
* Other legal or regulatory requirements

### Business Impact

Consider potential:

* Financial loss
* Operational disruption
* Reputational damage
* Customer impact
* Legal exposure
* Security impact

The final classification should reflect the **highest applicable protection
requirement**, unless an approved risk-based determination states otherwise.

## 10. Data Type and Classification

Data type and classification are separate concepts.

For example:

| Data Type            | Possible Classification                                       |
| -------------------- | ------------------------------------------------------------- |
| Customer profile     | Confidential                                                  |
| Government ID        | Restricted                                                    |
| Public policy        | Public                                                        |
| Internal runbook     | Internal                                                      |
| Transaction record   | Confidential                                                  |
| Authentication token | Restricted                                                    |
| Model weights        | Internal / Confidential / Restricted depending on sensitivity |
| Evaluation dataset   | Confidential / Restricted depending on content                |
| Embedding            | Inherits source-data classification                           |

A data type must **not automatically determine its classification**.

## 11. AI and Machine Learning Handling

Where AI/ML systems process organizational information:

1. AI handling requirements must follow the classification of the source data.
2. Restricted data must not be sent to external model providers unless an
   explicitly approved control permits it.
3. Sensitive data must be redacted or tokenized where required before model
   processing.
4. Embeddings and other derived artifacts must inherit the applicable source
   classification unless a documented assessment establishes otherwise.
5. Logs, traces, prompts, evaluation datasets, and cached model inputs must
   be classified according to the sensitivity of the information they contain.
6. Classification controls must be enforced by system controls rather than
   relying solely on user or prompt instructions.

## 12. Exceptions

Any exception to this standard must:

* Have a documented business or technical justification
* Identify the affected data
* Identify the risk introduced
* Define compensating controls
* Have approval from [Security / Data Owner / Risk Owner]
* Have an expiration or review date

## 13. Review and Reclassification

Data must be reclassified when:

* Its intended use changes
* New regulatory requirements apply
* Its sensitivity changes
* It is combined with other information that increases sensitivity
* A security or privacy assessment identifies a different protection need
* The organization's business or threat environment materially changes

## 14. Related Standards and Policies

* ISO/IEC 27001
* ISO/IEC 27002 — Information Classification and Labelling
* Applicable privacy and data-protection legislation
* Information Security Policy
* Access Control Policy
* Data Retention Policy
* Data Handling Policy
* Incident Response Policy
* AI/ML Security Policy

## 15. Document Control

| Version | Date       | Author | Change          | Approval   |
| ------- | ---------- | ------ | --------------- | ---------- |
| 1.0     | YYYY-MM-DD | [Name] | Initial version | [Approver] |
