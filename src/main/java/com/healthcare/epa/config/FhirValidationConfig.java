package com.healthcare.epa.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(FhirValidationConfig.class);

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public FhirValidator fhirValidator(FhirContext fhirContext) {
        FhirValidator validator = fhirContext.newValidator();
        
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
        instanceValidator.setAnyExtensionsAllowed(true);
        instanceValidator.setErrorForUnknownProfiles(true);

        validator.registerValidatorModule(instanceValidator);
        return validator;
    }

    public void validatePasBundle(FhirContext fhirContext, FhirValidator validator, Bundle bundle) {
        ValidationResult result = validator.validateWithResult(bundle);
        if (!result.isSuccessful()) {
            log.warn("Da Vinci PAS Bundle Validation Failed. Found {} issues", result.getMessages().size());
            // Generates structured FHIR OperationOutcome on validation failure
            throw new ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException(
                fhirContext, result.toOperationOutcome());
        }
    }
}