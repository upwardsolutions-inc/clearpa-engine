package com.healthcare.epa.service;

import ca.uhn.fhir.context.FhirContext;
import com.healthcare.epa.entity.EpaAdjudicationEntity;
import com.healthcare.epa.repository.EpaAdjudicationRepository;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EpaAdjudicationPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EpaAdjudicationPersistenceService.class);

    private final EpaAdjudicationRepository repository;
    private final FhirContext fhirContext;

    public EpaAdjudicationPersistenceService(EpaAdjudicationRepository repository, FhirContext fhirContext) {
        this.repository = repository;
        this.fhirContext = fhirContext;
    }

    @Transactional
    public void saveInitialRequest(String trackingId, Bundle requestBundle) {
        String jsonBundle = fhirContext.newJsonParser().encodeResourceToString(requestBundle);
        EpaAdjudicationEntity entity = new EpaAdjudicationEntity(trackingId, "QUEUED", jsonBundle);
        repository.save(entity);
        log.info("Saved initial ePA claim request with tracking ID: {}", trackingId);
    }

    @Transactional
    public void updateStatus(String trackingId, String status) {
        repository.findById(trackingId).ifPresentOrElse(entity -> {
            entity.setStatus(status);
            repository.save(entity);
            log.info("Updated status for tracking ID {} to {}", trackingId, status);
        }, () -> log.warn("Tracking ID {} not found for status update", trackingId));
    }

    @Transactional
    public void updateClaimResponseStatus(String controlNumber, String adjudicationOutcome, String x12ResponseContent) {
        Optional<EpaAdjudicationEntity> optionalEntity = repository.findById(controlNumber);

        if (optionalEntity.isPresent()) {
            EpaAdjudicationEntity entity = optionalEntity.get();
            entity.setStatus(adjudicationOutcome);
            entity.setResponseX12Payload(x12ResponseContent);
            repository.save(entity);
            log.info("Successfully updated claim adjudication state for control number: {}", controlNumber);
        } else {
            log.error("Received response for unknown control number/tracking ID: {}", controlNumber);
        }
    }

    @Transactional(readOnly = true)
    public Optional<EpaAdjudicationEntity> findByTrackingId(String trackingId) {
        return repository.findById(trackingId);
    }
}