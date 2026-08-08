package com.healthcare.epa.provider;

import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.healthcare.epa.service.EpaAdjudicationPersistenceService;
import com.healthcare.epa.service.EpaSftpOutboundService;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpaClaimResourceProviderTest {

    @Mock private EpaSftpOutboundService sftpOutboundService;
    @Mock private EpaAdjudicationPersistenceService persistenceService;

    @InjectMocks private EpaClaimResourceProvider provider;

    private Bundle createValidPasBundle() {
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(new Claim());
        bundle.addEntry().setResource(new Patient());
        bundle.addEntry().setResource(new Coverage());
        return bundle;
    }

    @Test
    @DisplayName("Positive: Valid PAS Bundle returns QUEUED ClaimResponse and triggers SFTP transfer")
    void submit_ValidBundle_ReturnsQueuedClaimResponse() {
        Bundle bundle = createValidPasBundle();

        ClaimResponse response = provider.submitPriorAuthorization(bundle, null);

        assertNotNull(response);
        assertEquals(ClaimResponse.RemittanceOutcome.QUEUED, response.getOutcome());
        assertEquals(ClaimResponse.ClaimResponseStatus.ACTIVE, response.getStatus());
        assertNotNull(response.getPreAuthRef());

        verify(persistenceService, times(1)).saveInitialRequest(anyString(), eq(bundle));
        verify(sftpOutboundService, times(1)).processAndSendSftp(eq(bundle), anyString());
    }

    @Test
    @DisplayName("Negative: Null Bundle throws UnprocessableEntityException")
    void submit_NullBundle_ThrowsUnprocessableEntityException() {
        assertThrows(UnprocessableEntityException.class, () -> 
            provider.submitPriorAuthorization(null, null));
        
        verifyNoInteractions(persistenceService, sftpOutboundService);
    }

    @Test
    @DisplayName("Negative: Incomplete Bundle (missing Coverage) throws UnprocessableEntityException")
    void submit_MissingCoverage_ThrowsUnprocessableEntityException() {
        Bundle incompleteBundle = new Bundle();
        incompleteBundle.addEntry().setResource(new Claim());
        incompleteBundle.addEntry().setResource(new Patient());

        assertThrows(UnprocessableEntityException.class, () -> 
            provider.submitPriorAuthorization(incompleteBundle, null));

        verifyNoInteractions(persistenceService, sftpOutboundService);
    }
}