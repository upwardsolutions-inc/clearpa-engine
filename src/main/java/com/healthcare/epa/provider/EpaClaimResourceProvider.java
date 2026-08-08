package com.healthcare.epa.provider;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.healthcare.epa.service.EpaAdjudicationPersistenceService;
import com.healthcare.epa.service.EpaSftpOutboundService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EpaClaimResourceProvider implements IResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(EpaClaimResourceProvider.class);

    private final EpaSftpOutboundService sftpOutboundService;
    private final EpaAdjudicationPersistenceService persistenceService;

    public EpaClaimResourceProvider(EpaSftpOutboundService sftpOutboundService,
                                    EpaAdjudicationPersistenceService persistenceService) {
        this.sftpOutboundService = sftpOutboundService;
        this.persistenceService = persistenceService;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Claim.class;
    }

    /**
     * Implements Da Vinci PAS Extended Operation: POST /Claim/$submit
     */
    @Operation(name = "$submit", type = Claim.class, idempotent = false)
    public ClaimResponse submitPriorAuthorization(
            @OperationParam(name = "bundle") Bundle requestBundle,
            RequestDetails requestDetails) {

        String correlationId = requestDetails != null ? requestDetails.getHeader("X-Correlation-ID") : null;
        String consumerIdentity = requestDetails != null ? requestDetails.getHeader("X-Consumer-Username") : null;

        log.info("Processing PAS $submit. Correlation ID: {}, Consumer: {}", correlationId, consumerIdentity);

        if (requestBundle == null) {
            throw new UnprocessableEntityException("Missing required Da Vinci PAS $submit Bundle payload.");
        }

        // 1. Extract Core HAPI Resources for validation
        Claim claim = extractResourceFromBundle(requestBundle, Claim.class);
        Patient patient = extractResourceFromBundle(requestBundle, Patient.class);
        Coverage coverage = extractResourceFromBundle(requestBundle, Coverage.class);

        if (claim == null || patient == null || coverage == null) {
            throw new UnprocessableEntityException(
                "Da Vinci PAS Bundle must contain valid Claim, Patient, and Coverage resources.");
        }

        String trackingId = (correlationId != null && !correlationId.isBlank()) 
                ? correlationId 
                : UUID.randomUUID().toString();

        // 2. Persist initial request record in H2/PostgreSQL
        persistenceService.saveInitialRequest(trackingId, requestBundle);

        // 3. Offload X12 translation & SFTP delivery to background task thread pool
        sftpOutboundService.processAndSendSftp(requestBundle, trackingId);

        // 4. Return immediate QUEUED response to client
        return buildClaimResponse(claim, trackingId);
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> T extractResourceFromBundle(Bundle bundle, Class<T> resourceClass) {
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resourceClass::isInstance)
                .map(resource -> (T) resource)
                .findFirst()
                .orElse(null);
    }

    private ClaimResponse buildClaimResponse(Claim claim, String trackingId) {
        ClaimResponse response = new ClaimResponse();
        response.setId("pas-resp-" + UUID.randomUUID().toString());
        response.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
        response.setType(claim.getType());
        response.setUse(ClaimResponse.Use.PREAUTHORIZATION);
        response.setPatient(claim.getPatient());
        response.setCreated(new java.util.Date());
        response.setInsurer(claim.getInsurer());

        // Asynchronous SFTP processing requires QUEUED remittance outcome
        response.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
        response.setPreAuthRef(trackingId);

        // Profile Metadata
        Meta meta = new Meta();
        meta.addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse");
        response.setMeta(meta);

        // Line Item Adjudication Node
        ClaimResponse.ItemComponent item = response.addItem();
        item.setItemSequence(1);
        
        ClaimResponse.AdjudicationComponent adjudication = item.addAdjudication();
        adjudication.getCategory().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/adjudication")
                .setCode("submitted");

        return response;
    }
}