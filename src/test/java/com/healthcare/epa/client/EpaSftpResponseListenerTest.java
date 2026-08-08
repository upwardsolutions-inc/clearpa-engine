package com.healthcare.epa.client;

import com.healthcare.epa.service.EpaAdjudicationPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EpaSftpResponseListenerTest {

    @Mock private EpaAdjudicationPersistenceService persistenceService;

    @InjectMocks private EpaSftpResponseListener responseListener;

    @Test
    @DisplayName("Positive: Correctly extracts BHT03 control number and persists COMPLETE status")
    void processX12Response_ApprovedX12_UpdatesPersistence() throws Exception {
        String mockX12Approved = "ISA*00*...~\nGS*HI*...~\nST*278*0001~\nBHT*0007*13*CLAIM-999*20260807*1200~\nHL*1**20*1~\nA1~";

        // Invoke private helper or test public workflow
        java.lang.reflect.Method method = EpaSftpResponseListener.class
                .getDeclaredMethod("processX12Response", String.class);
        method.setAccessible(true);
        method.invoke(responseListener, mockX12Approved);

        verify(persistenceService).updateClaimResponseStatus("CLAIM-999", "COMPLETE", mockX12Approved);
    }

    @Test
    @DisplayName("Negative/Rejection: Correctly identifies non-A1 response as REJECTED status")
    void processX12Response_RejectedX12_UpdatesPersistenceWithRejected() throws Exception {
        String mockX12Rejected = "ISA*00*...~\nGS*HI*...~\nST*278*0001~\nBHT*0007*13*CLAIM-888*20260807*1200~\nHL*1**20*1~\nREJECTED~";

        java.lang.reflect.Method method = EpaSftpResponseListener.class
                .getDeclaredMethod("processX12Response", String.class);
        method.setAccessible(true);
        method.invoke(responseListener, mockX12Rejected);

        verify(persistenceService).updateClaimResponseStatus("CLAIM-888", "REJECTED", mockX12Rejected);
    }
}