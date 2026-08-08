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

Standard commercial and open-source FHIR servers operate primarily as CRUD storage engines (`read`, `create`, `update`, `search`). They fail to execute multi-step Da Vinci PAS `$submit` state machines, handle bi-directional legacy EDI X12 278 translation, or dynamically monitor federal SLA breach risks out of the box.

**ClearPA (`clearpa-engine`)** is an open-source architectural framework engineered to bridge this exact industry gap. ClearPA extends standard FHIR endpoints with an event-driven, asynchronous orchestration pipeline, field-level envelope encryption, a zero-data-loss X12 278 translation bridge, and real-time SLA breach tracking.

---

## Key Features

* **Deterministic PAS Orchestration (`ClearPA-DPO`)**: Exposes the extended `POST /Claim/$submit` operation using custom HAPI FHIR providers, processing complex Da Vinci PAS bundles asynchronously to prevent gateway timeout failures.
* **Algorithmic FHIR-to-X12 Bridge (`ClearPA-FX278`)**: A zero-data-loss translation engine converting FHIR R4 `Claim` bundles into HIPAA-compliant EDI X12 278 (005010X217) transactions and X12 275 attachments.
* **Continuous CMS Audit Engine (`ClearPA-CCAI`)**: Non-repudiable audit logging interceptor capturing every ePA transaction to calculate real-time SLA breach risks for annual public CMS metrics reporting.
* **Zero-Trust Security Gateway (`ClearPA-ZUSG`)**: Enforces dynamic PKI certificate validation (UDAP), Mutual TLS (mTLS), and fine-grained Attribute-Based Access Control (ABAC) using OAuth2 JWT scoping.
* **Multi-Protocol Legacy Dispatch & Inbound Poller**: Features built-in transport adapters for real-time RESTful HTTP ingestion (`HTTP 202 Accepted`), background SFTP batch file transfer (`/outbound`), and automated response polling (`/inbound`).

---

## Architectural Reference Topology

ClearPA sits between Provider EHR systems and legacy Payer Core Utilization Management (UM) engines, decoupling synchronous RESTful FHIR API ingestion from asynchronous EDI processing:
```
[ Provider EHR System ]
        │
        ▼ 1. POST /fhir/v1/Claim/$submit
┌───────────────────────────────────────────────────────────────────────────────────┐
│ KONG API GATEWAY                                                                  │
│  - mTLS Auth & SMART-on-FHIR Scope Verification                                   │
│  - Injects X-Correlation-ID & X-Consumer-Username                                 │
└───────────────────────────────────────────────────────────────────────────────────┘
        │
        ▼ 2. Proxy HTTP Request
┌───────────────────────────────────────────────────────────────────────────────────┐
│ SPRING BOOT SERVICE (Spring Boot + HAPI FHIR)                                     │
│                                                                                   │
│  1. EpaClaimResourceProvider (@Operation $submit)                                 │
│     ├── Validates FHIR Bundle Structure                                           │
│     ├── Persists "QUEUED" state to H2/PostgreSQL Database                         │
│     └── Triggers @Async EpaSftpOutboundService                                    │
│                                                                                   │
│  2. EpaSftpOutboundService (@Async Thread Pool)                                   │
│     ├── Translates Bundle -> X12 278                                              │
│     └── Uploads PAS_REQ_<tracking_id>.278 to SFTP (/outbound/)                    │
│     ├── Uploads PAS_REQ_<tracking_id>.278 to SFTP (/outbound/)                    │
│     └── Updates status in DB to "TRANSMITTED_TO_MAINFRAME"                        |
│  3. EpaSftpInboundPoller / EpaSftpResponseListener (@Scheduled Poller)            │
│     ├── Polls SFTP directory (/inbound/) for .278 / .x12 responses                |
│     ├── Strips extensions (.278/.x12) to match exact tracking ID                  │
│     └── Updates ClaimResponse status in DB to "COMPLETE" or "REJECTED"            │
└───────────────────────────────────────────────────────────────────────────────────┘
        │                                 │
        ▼ 3. Outbound (.278)              ▲ 4. Inbound (.278)
┌───────────────────────────────────────────────────────────────────────────────────┐
│ATMOZ SFTP SERVER CONTAINER (epa-sftp-server:2222)                                 │
│   - Root Chroot Jail: /home/epauser                                               │
│   - Folders: /outbound , /inbound                                                 │
└───────────────────────────────────────────────────────────────────────────────────┘
│                                 ▲
▼ 3a. Read Request                │ 3b. Drop Response
┌───────────────────────────────────────────────────────────────────────────────────┐
│ MOCK PAYER SIMULATOR (payer_simulator.py / container)                             │
│   - Monitors ./sftp_data/outbound                                                 │
│   - Generates matching X12 278 response file                                      │
│   - Drops response_<tracking_id>.278 into ./sftp_data/inbound                     │
└───────────────────────────────────────────────────────────────────────────────────┘
```

# Setup & Execution Guide

### 1. Prerequisites
- **Java Development Kit (JDK):** Version 17 or higher (`java -version`)
- **Apache Maven:** Version 3.8.x or higher (`mvn -version`)
- **Docker Desktop:** Required for local SFTP container (`atmoz/sftp`)
- **Python 3.x:** (Optional) For running the standalone mock payer simulator

---

### 2. Infrastructure Setup

Start the local SFTP server and optional mock payer simulator using Docker Compose:

```bash
docker compose up -d sftp payer-simulator
```
Ensure permissions on mounted SFTP directories are owned by the epauser (UID 1001):

```bash
docker exec -it epa-sftp-server chown -R 1001:1001 //home/epauser/outbound //home/epauser/inbound
```

## 3. Build & Compile

Clone the repository directly on the `dev` branch and navigate into the root project directory:

```bash
git clone -b dev https://github.com/upwardsolutions-inc/clearpa-engine.git
```

Compile the project sources and resolve all dependencies (HAPI FHIR, Spring Boot, JSch, WireMock, and Fake SFTP, including BouncyCastle for Ed25519/EdDSA SSH key support):

```bash
mvn clean compile
```

## 4. Running Test Cases
ClearPA includes a robust automated testing suite comprising unit tests and multi-protocol integration tests (WireMock HTTP & Embedded SFTP).

Run Unit Tests Only

```bash
mvn test -Dtest='*Test,!*IntegrationTest'
```
Run Integration Tests Only
```bash
mvn test -Dtest='*IntegrationTest'
```
Run All Test Suites
```bash
mvn test
```
## 5. Running the Application

Start the Spring Boot application locally:
```bash
mvn spring-boot:run
```

Once initialized, endpoints will be available at:

```bash
FHIR Base Endpoint: https://localhost:8443/fhir/v1/
Prior Authorization Submit: POST http://localhost:8080/fhir/v1/Claim/$submit
ClaimResponse Status Query: GET http://localhost:8080/fhir/v1/ClaimResponse?preauth-ref=<tracking_id>
```