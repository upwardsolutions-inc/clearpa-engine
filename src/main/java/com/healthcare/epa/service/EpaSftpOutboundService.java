package com.healthcare.epa.service;

import com.healthcare.epa.bridge.EpaX12BridgeTranslator;
import com.healthcare.epa.client.EpaMultiProtocolTransportService;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EpaSftpOutboundService {

    private static final Logger log = LoggerFactory.getLogger(EpaSftpOutboundService.class);

    private final EpaX12BridgeTranslator translator;
    private final EpaMultiProtocolTransportService transportService;
    private final EpaAdjudicationPersistenceService persistenceService;

    public EpaSftpOutboundService(EpaX12BridgeTranslator translator,
                                 EpaMultiProtocolTransportService transportService,
                                 EpaAdjudicationPersistenceService persistenceService) {
        this.translator = translator;
        this.transportService = transportService;
        this.persistenceService = persistenceService;
    }

    /**
     * Executes asynchronously off the main Tomcat HTTP request thread.
     */
    @Async("sftpExecutor")
    public void processAndSendSftp(Bundle pasBundle, String trackingId) {
        try {
            log.info("Beginning background X12 translation for tracking ID: {}", trackingId);

            // 1. Translate FHIR Bundle to ANSI X12 278 String
            String x12Payload = translator.translateFhirToX12278(pasBundle, trackingId);

            // 2. Transmit file to Legacy Mainframe via SFTP
            String fileName = "PAS_REQ_" + trackingId + ".278";
            boolean delivered = transportService.transmitViaSftp(x12Payload, fileName);

            if (!delivered) {
                log.error("Failed to upload X12 payload to SFTP for tracking ID: {}", trackingId);
                persistenceService.updateStatus(trackingId, "TRANSMISSION_FAILED");
            } else {
                log.info("Successfully dropped X12 278 file onto SFTP: {}", fileName);
                persistenceService.updateStatus(trackingId, "TRANSMITTED_TO_MAINFRAME");
            }

        } catch (Exception e) {
            log.error("Error processing outbound SFTP transfer for tracking ID {}: {}", trackingId, e.getMessage());
            persistenceService.updateStatus(trackingId, "PROCESSING_ERROR");
        }
    }
}