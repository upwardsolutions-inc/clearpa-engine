package com.healthcare.epa.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.healthcare.epa.interceptor.EpaGatewayAuditInterceptor;
import com.healthcare.epa.provider.EpaClaimResourceProvider;
import com.healthcare.epa.provider.EpaClaimResponseResourceProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class HapiServerConfig {

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServletRegistration(
            FhirContext fhirContext,
            EpaClaimResourceProvider epaClaimResourceProvider,
            EpaClaimResponseResourceProvider epaClaimResponseResourceProvider,
            EpaGatewayAuditInterceptor epaGatewayAuditInterceptor) {

        RestfulServer server = new RestfulServer(fhirContext);

        // Register both Claim and ClaimResponse resource providers
        server.setResourceProviders(List.of(
            epaClaimResourceProvider,
            epaClaimResponseResourceProvider
        ));
        
        server.registerInterceptor(epaGatewayAuditInterceptor);

        server.setServerName("Enterprise CMS-0057-F ePA Gateway");
        server.setServerVersion("2026.1.0");

        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/v1/*");
        registration.setName("HapiFhirServlet");
        return registration;
    }
}