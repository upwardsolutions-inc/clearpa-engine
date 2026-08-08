package com.healthcare.epa.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.healthcare.epa.config.GatewayHeaderProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Interceptor
@Component
public class EpaGatewayAuditInterceptor {

    private static final Logger auditLog = LoggerFactory.getLogger("CMS_AUDIT_LOGGER");
    private final GatewayHeaderProperties headerProperties;

    public EpaGatewayAuditInterceptor(GatewayHeaderProperties headerProperties) {
        this.headerProperties = headerProperties;
    }

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void captureIncomingEpaRequest(RequestDetails requestDetails, 
                                          HttpServletRequest servletRequest, 
                                          HttpServletResponse servletResponse) {

        if (requestDetails != null && "$submit".equals(requestDetails.getOperation())) {
            
            // Safe fallback for Remote Address to prevent NPE when servletRequest is null in test contexts
            String defaultIp = (servletRequest != null && servletRequest.getRemoteAddr() != null) 
                    ? servletRequest.getRemoteAddr() 
                    : "127.0.0.1";

            // Dynamic Header Extraction via RequestDetails & HttpServletRequest
            String correlationId = getHeaderOrDefault(requestDetails, servletRequest, 
                    headerProperties != null ? headerProperties.getCorrelationId() : "X-Correlation-ID", 
                    UUID.randomUUID().toString());

            String consumerId = getHeaderOrDefault(requestDetails, servletRequest, 
                    headerProperties != null ? headerProperties.getConsumerId() : "X-Consumer-Username", 
                    "UNKNOWN_CONSUMER");

            String consumerRole = getHeaderOrDefault(requestDetails, servletRequest, 
                    headerProperties != null ? headerProperties.getConsumerRole() : "X-Consumer-Groups", 
                    "DEFAULT_ROLE");

            String clientIp = getHeaderOrDefault(requestDetails, servletRequest, 
                    headerProperties != null ? headerProperties.getClientIp() : "X-Forwarded-For", 
                    defaultIp);

            // Populate Thread Context (MDC) for Logback / Tracing
            MDC.put("correlationId", correlationId);
            MDC.put("consumerId", consumerId);

            // Audit Record Payload
            auditLog.info("""
                {
                  "event": "PAS_SUBMIT_INGRESS",
                  "timestamp": "%s",
                  "correlation_id": "%s",
                  "consumer_id": "%s",
                  "consumer_role": "%s",
                  "client_ip": "%s",
                  "http_method": "%s",
                  "uri": "%s"
                }
                """.formatted(
                    Instant.now(), 
                    correlationId, 
                    consumerId, 
                    consumerRole, 
                    clientIp, 
                    requestDetails.getRequestType(), 
                    requestDetails.getCompleteUrl()
                ).trim());
        }
    }

    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void captureOutgoingEpaResponse(RequestDetails requestDetails, 
                                           HttpServletRequest servletRequest, 
                                           HttpServletResponse servletResponse) {

        if (requestDetails != null && "$submit".equals(requestDetails.getOperation())) {
            try {
                int status = (servletResponse != null) ? servletResponse.getStatus() : 200;

                auditLog.info("""
                    {
                      "event": "PAS_SUBMIT_EGRESS",
                      "timestamp": "%s",
                      "correlation_id": "%s",
                      "http_status": %d
                    }
                    """.formatted(Instant.now(), MDC.get("correlationId"), status).trim());
            } finally {
                MDC.clear(); // Prevent ThreadLocal leaks in Servlet Pool
            }
        }
    }

    private String getHeaderOrDefault(RequestDetails requestDetails, HttpServletRequest servletRequest, String headerName, String defaultValue) {
        if (headerName == null) {
            return defaultValue;
        }
        // Primary check using HAPI FHIR RequestDetails
        if (requestDetails != null) {
            String value = requestDetails.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        // Secondary fallback using raw HttpServletRequest
        if (servletRequest != null) {
            String value = servletRequest.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }
}