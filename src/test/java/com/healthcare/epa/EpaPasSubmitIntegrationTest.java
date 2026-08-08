package com.healthcare.epa;

import ca.uhn.fhir.context.FhirContext;
import com.healthcare.epa.client.EpaMultiProtocolTransportService;
import com.healthcare.epa.entity.EpaAdjudicationEntity;
import com.healthcare.epa.repository.EpaAdjudicationRepository;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EpaPasSubmitIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private FhirContext fhirContext;
    @Autowired private EpaAdjudicationRepository repository;

    @MockBean private EpaMultiProtocolTransportService transportService;

    @Test
    @DisplayName("Integration Test: End-to-End POST /fhir/v1/Claim/$submit creates DB record and returns QUEUED ClaimResponse")
    void processPriorAuthRequest_EndToEnd_Success() {
        // Stub outbound SFTP transport to succeed
        when(transportService.transmitViaSftp(anyString(), anyString())).thenReturn(true);

        // 1. Construct Valid Da Vinci PAS Request Bundle
        Bundle pasBundle = new Bundle();
        pasBundle.setType(Bundle.BundleType.COLLECTION);

        Claim claim = new Claim();
        claim.setId("CLM-INT-100");
        claim.setUse(Claim.Use.PREAUTHORIZATION);

        Patient patient = new Patient();
        patient.setId("PAT-100");

        Coverage coverage = new Coverage();
        coverage.setId("COV-100");

        pasBundle.addEntry().setResource(claim);
        pasBundle.addEntry().setResource(patient);
        pasBundle.addEntry().setResource(coverage);

        String jsonPayload = fhirContext.newJsonParser().encodeResourceToString(pasBundle);

        // 2. Set Gateway Request Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
        headers.set("X-Correlation-ID", "INT-CORRELATION-999");
        headers.set("X-Consumer-Username", "EHR_PARTNER_CLINIC");

        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        // 3. Execute HTTP POST to $submit extended operation endpoint
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/fhir/v1/Claim/$submit", 
                requestEntity, 
                String.class
        );

        // 4. Assert HTTP Status and FHIR Response Body
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        ClaimResponse claimResponse = fhirContext.newJsonParser()
                .parseResource(ClaimResponse.class, response.getBody());

        assertEquals(ClaimResponse.RemittanceOutcome.QUEUED, claimResponse.getOutcome());
        assertEquals("INT-CORRELATION-999", claimResponse.getPreAuthRef());

        // 5. Verify Persistence in H2 Database
        Optional<EpaAdjudicationEntity> dbEntity = repository.findById("INT-CORRELATION-999");
        assertTrue(dbEntity.isPresent());
        assertEquals("INT-CORRELATION-999", dbEntity.get().getTrackingId());
        assertTrue(dbEntity.get().getStatus().equals("QUEUED") || 
                   dbEntity.get().getStatus().equals("TRANSMITTED_TO_MAINFRAME"));
    }
}