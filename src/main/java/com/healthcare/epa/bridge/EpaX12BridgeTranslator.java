package com.healthcare.epa.bridge;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EpaX12BridgeTranslator {

    public String translateFhirToX12278(Bundle pasBundle, String trackingId) {
        if (pasBundle == null) {
            throw new IllegalArgumentException("Bundle payload cannot be null.");
        }

        Claim claim = (Claim) pasBundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof Claim)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EPA-FX278: Claim resource missing from bundle."));

        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("HHmm"));
        String ctrl = trackingId.length() >= 9 ? trackingId.substring(0, 9) : trackingId;

        StringBuilder x12 = new StringBuilder();
        x12.append(String.format("ISA*00*          *00*          *ZZ*EPA_EHR        *ZZ*PAYER_UM       *%s*%s*^*00501*%s*0*P*:~\n",
                now.format(DateTimeFormatter.ofPattern("yyMMdd")), timeStr, ctrl));
        x12.append(String.format("GS*HI*EPA_EHR*PAYER_UM*%s*%s*1*X*005010X217~\n", dateStr, timeStr));
        x12.append("ST*278*0001*005010X217~\n");
        x12.append(String.format("BHT*0007*13*%s*%s*%s~\n", claim.getIdElement().getIdPart(), dateStr, timeStr));
        x12.append("HL*1**20*1~\n");
        x12.append("NM1*X3*2*EPA HEALTH PLAN*****PI*PAYER01~\n");
        x12.append("SE*6*0001~\n");
        x12.append("GE*1*1~\n");
        x12.append(String.format("IEA*1*%s~\n", ctrl));

        return x12.toString();
    }
}