package com.healthcare.epa.bridge;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EpaX12BridgeTranslatorTest {

    private EpaX12BridgeTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new EpaX12BridgeTranslator();
    }

    @Test
    @DisplayName("Positive: Valid FHIR Bundle produces compliant X12 278 string")
    void translate_ValidBundle_ReturnsX12String() {
        Bundle bundle = new Bundle();
        Claim claim = new Claim();
        claim.setId("CLM-998877");
        bundle.addEntry().setResource(claim);

        String x12Output = translator.translateFhirToX12278(bundle, "TRK-1001");

        assertNotNull(x12Output);
        assertTrue(x12Output.contains("ISA*"));
        assertTrue(x12Output.contains("GS*HI*EPA_EHR*PAYER_UM*"));
        assertTrue(x12Output.contains("ST*278*0001*005010X217~"));
        assertTrue(x12Output.contains("BHT*0007*13*CLM-998877*"));
    }

    @Test
    @DisplayName("Negative: Null Bundle throws IllegalArgumentException")
    void translate_NullBundle_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> 
            translator.translateFhirToX12278(null, "TRK-1001"));
    }

    @Test
    @DisplayName("Negative: Bundle without Claim resource throws IllegalArgumentException")
    void translate_MissingClaimInBundle_ThrowsException() {
        Bundle bundle = new Bundle(); // Empty bundle without Claim
        assertThrows(IllegalArgumentException.class, () -> 
            translator.translateFhirToX12278(bundle, "TRK-1001"));
    }
}