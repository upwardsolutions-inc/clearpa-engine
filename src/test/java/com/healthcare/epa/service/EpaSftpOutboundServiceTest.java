package com.healthcare.epa.service;

import com.healthcare.epa.bridge.EpaX12BridgeTranslator;
import com.healthcare.epa.client.EpaMultiProtocolTransportService;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpaSftpOutboundServiceTest {

    @Mock private EpaX12BridgeTranslator translator;
    @Mock private EpaMultiProtocolTransportService transportService;
    @Mock private EpaAdjudicationPersistenceService persistenceService;

    @InjectMocks private EpaSftpOutboundService outboundService;

    @Test
    @DisplayName("Positive: Successful SFTP transmission updates status to TRANSMITTED_TO_MAINFRAME")
    void processAndSend_Success_UpdatesStatusToTransmitted() {
        Bundle bundle = new Bundle();
        String trackingId = "TRK-555";
        String mockX12 = "ISA*00*...";

        when(translator.translateFhirToX12278(bundle, trackingId)).thenReturn(mockX12);
        when(transportService.transmitViaSftp(eq(mockX12), anyString())).thenReturn(true);

        outboundService.processAndSendSftp(bundle, trackingId);

        verify(persistenceService).updateStatus(trackingId, "TRANSMITTED_TO_MAINFRAME");
    }

    @Test
    @DisplayName("Negative: Failed SFTP upload updates status to TRANSMISSION_FAILED")
    void processAndSend_SftpFailure_UpdatesStatusToFailed() {
        Bundle bundle = new Bundle();
        String trackingId = "TRK-555";
        String mockX12 = "ISA*00*...";

        when(translator.translateFhirToX12278(bundle, trackingId)).thenReturn(mockX12);
        when(transportService.transmitViaSftp(eq(mockX12), anyString())).thenReturn(false);

        outboundService.processAndSendSftp(bundle, trackingId);

        verify(persistenceService).updateStatus(trackingId, "TRANSMISSION_FAILED");
    }

    @Test
    @DisplayName("Negative: Translation error handles exception and updates status to PROCESSING_ERROR")
    void processAndSend_TranslationError_UpdatesStatusToError() {
        Bundle bundle = new Bundle();
        String trackingId = "TRK-555";

        when(translator.translateFhirToX12278(bundle, trackingId))
                .thenThrow(new IllegalArgumentException("Invalid FHIR structure"));

        outboundService.processAndSendSftp(bundle, trackingId);

        verify(persistenceService).updateStatus(trackingId, "PROCESSING_ERROR");
        verifyNoInteractions(transportService);
    }
}