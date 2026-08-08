# Integration Test Suite Reference

This document outlines the end-to-end integration test cases implemented within the **ClearPA Engine (`clearpa-engine`)** framework.

The integration test suite verifies the system's core capabilities:
1. **Synchronous Ingestion & Asynchronous Dispatch:** Accepting FHIR R4 Da Vinci PAS bundles via RESTful HTTP endpoints, saving state to H2/PostgreSQL, and transforming them into HIPAA-compliant EDI X12 278 (005010X217) transactions.
2. **Multi-Protocol Transport Adapters:** Reliably transmitting transformed EDI payloads over **HTTP REST** (`202 Accepted` queue model) and **Secure File Transfer Protocol (SFTP)** batch uploads (`/outbound`) to legacy Utilization Management (UM) adjudication systems.
3. **Inbound Polling & Adjudication Reconciliation:** Polling remote SFTP folders (`/inbound`), stripping file extensions (`.278`, `.x12`, `response_` prefix) to resolve the underlying tracking ID, and updating database records to `COMPLETE` or `REJECTED`.

---

## Test Execution Command

To execute the integration test suite in Git Bash, PowerShell, or terminal environments, run:

```bash
mvn test -Dtest='*IntegrationTest'
```

---

## Integration Test Matrix

| Test Case ID | Test Name | Protocol / Target | Primary Target Class | Description | Expected Result / Assertion |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **IT-01** | `executeE2eHttpTransmission` | HTTP REST (WireMock) | `EpaMultiProtocolTransportService` | Simulates an outbound RESTful HTTP POST transmission of a translated EDI X12 278 payload to a legacy UM ingestion endpoint mocked by WireMock. | • WireMock intercepts `POST /api/v1/x12/ingest`.<br>• Request contains `Content-Type: application/x12` header.<br>• Request includes tracking header `X-EPA-Tracking-ID`.<br>• Method returns `true` upon receiving an `HTTP 202 Accepted` response. |
| **IT-02** | `executeE2eSftpTransmission` | SFTP (Apache MINA SSHD / JSch) | `EpaMultiProtocolTransportService` | Simulates an asynchronous SFTP batch file transfer of an EDI X12 278 transaction to a secure remote directory monitored by legacy batch processing engines. | • Connects to embedded SFTP server on port `2222`.<br>• Authenticates with test credentials.<br>• Transmits file `PAS_REQ_TEST-100.278` to target directory `/outbound/`.<br>• Method returns `true` and file exists on remote filesystem. |
| **IT-03** | `submitPriorAuthorization_E2E` | Full Flow (HAPI FHIR Gateway) | `EpaClaimResourceProvider` | Executes an end-to-end `$submit` operation by submitting a complete Da Vinci PAS FHIR R4 Bundle to the HAPI FHIR gateway endpoint (`POST /Claim/$submit`). | • Returns FHIR `ClaimResponse` resource.<br>• Status saved in database as `QUEUED`, then updated to `TRANSMITTED_TO_MAINFRAME`.<br>• preAuthRef contains valid tracking prefix (CORR-* or PAS-*).<br> • Outbound X12 file deposited in `/outbound`. |
| **IT-04** | `pollInboundX12Response_E2E` | SFTP Poller & Persistence | `EpaSftpInboundPoller` / `EpaSftpResponseListener` | Simulates dropping a mainframe X12 278 response file into /inbound/, testing regex extension stripping (.278/.x12) and DB state update. | • Detects file `/inbound/response_CORR-TEST-999.278`.<br>• Strips extension to isolate tracking ID `CORR-TEST-999`.<br>• Updates database record status to `COMPLETE`.<br> • Removes or archives processed file from SFTP `/inbound`. |

---
