# ClearPA Engine (`clearpa-engine`)

> **Deterministic FHIR-to-EDI Prior Authorization Gateway & Orchestration Engine for CMS-0057-F Compliance**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2%2B-green.svg)](https://spring.io/projects/spring-boot)
[![FHIR Version](https://img.shields.io/badge/FHIR-R4_v4.0.1-firebrick.svg)](https://hl7.org/fhir/R4/)
[![Da Vinci IG](https://img.shields.io/badge/HL7_Da_Vinci-PAS_v2.0.0-purple.svg)](http://hl7.org/fhir/us/davinci-pas/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()

---

## Executive Overview

The **CMS Interoperability and Prior Authorization Final Rule (CMS-0057-F)** enforces strict, mandatory decision turnaround deadlines on Medicare Advantage, Medicaid, CHIP, and QHP payers: **72 hours for urgent/expedited requests** and **7 calendar days for standard requests**, complete with explicit denial justifications.

Standard commercial and open-source FHIR servers (e.g., stock HAPI FHIR or cloud PaaS FHIR stores) operate primarily as CRUD storage engines (`read`, `create`, `update`, `search`). They fail to execute multi-step Da Vinci PAS `$submit` state machines, handle bi-directional legacy EDI X12 278 translation, or dynamically monitor federal SLA breach risks out of the box.

**ClearPA (`clearpa-engine`)** is an open-source, original architectural framework engineered to bridge this exact industry gap. ClearPA extends standard FHIR endpoints with an event-driven, asynchronous orchestration pipeline, field-level envelope encryption, a zero-data-loss X12 278 translation bridge, and real-time SLA breach tracking.

---

## Key Features

* **Deterministic PAS Orchestration (`ClearPA-DPO`)**: Exposes the extended `POST /Claim/$submit` operation using custom HAPI FHIR providers, processing complex Da Vinci PAS bundles asynchronously to prevent gateway timeout failures.
* **Algorithmic FHIR-to-X12 Bridge (`ClearPA-FX278`)**: A zero-data-loss translation engine converting FHIR R4 `Claim` bundles into HIPAA-compliant EDI X12 278 (005010X217) transactions and X12 275 attachments.
* **Continuous CMS Audit Engine (`ClearPA-CCAI`)**: Non-repudiable audit logging interceptor capturing every ePA transaction to calculate real-time SLA breach risks for annual public CMS metrics reporting.
* **Zero-Trust Security Gateway (`ClearPA-ZUSG`)**: Enforces dynamic PKI certificate validation (UDAP), Mutual TLS (mTLS), and fine-grained Attribute-Based Access Control (ABAC) using OAuth2 JWT scoping.
* **Multi-Protocol Legacy Dispatch**: Features built-in transport adapters for real-time RESTful HTTP ingestion (`HTTP 202 Accepted`) and asynchronous SFTP batch file transfer.

---

## Architectural Reference Topology

ClearPA sits between Provider EHR systems and legacy Payer Core Utilization Management (UM) engines, decoupling synchronous RESTful FHIR API ingestion from asynchronous EDI processing:
