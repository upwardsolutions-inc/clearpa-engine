package com.healthcare.epa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gateway.header-mapping")
public class GatewayHeaderProperties {

    private String correlationId = "X-Correlation-ID";
    private String consumerId = "X-Consumer-Username";
    private String consumerRole = "X-Consumer-Role";
    private String clientIp = "X-Forwarded-For";

    // Getters and Setters
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getConsumerRole() { return consumerRole; }
    public void setConsumerRole(String consumerRole) { this.consumerRole = consumerRole; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}