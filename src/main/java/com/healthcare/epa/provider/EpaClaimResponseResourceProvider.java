package com.healthcare.epa.provider;

import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.healthcare.epa.entity.EpaAdjudicationEntity;
import com.healthcare.epa.repository.EpaAdjudicationRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class EpaClaimResponseResourceProvider implements IResourceProvider {

    private final EpaAdjudicationRepository repository;

    public EpaClaimResponseResourceProvider(EpaAdjudicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return ClaimResponse.class;
    }

    @Search
    public List<ClaimResponse> searchByPreAuthRef(@OptionalParam(name = "preauth-ref") TokenParam preAuthRef) {
        if (preAuthRef == null) {
            return Collections.emptyList();
        }

        Optional<EpaAdjudicationEntity> entity = repository.findById(preAuthRef.getValue());
        if (entity.isEmpty()) {
            return Collections.emptyList();
        }

        ClaimResponse response = new ClaimResponse();
        response.setId("pas-resp-" + entity.get().getTrackingId());
        response.setPreAuthRef(entity.get().getTrackingId());
        
        switch (entity.get().getStatus()) {
            case "COMPLETE" -> response.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
            case "REJECTED", "PROCESSING_ERROR" -> response.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
            default -> response.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
        }

        return List.of(response);
    }
}