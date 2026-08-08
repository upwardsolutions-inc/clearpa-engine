package com.healthcare.epa.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "epa_claim_adjudication")
public class EpaAdjudicationEntity {

    @Id
    @Column(name = "tracking_id", nullable = false, length = 64)
    private String trackingId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_bundle_json", columnDefinition = "TEXT")
    private String requestBundleJson;

    @Column(name = "response_x12_payload", columnDefinition = "TEXT")
    private String responseX12Payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EpaAdjudicationEntity() {}

    public EpaAdjudicationEntity(String trackingId, String status, String requestBundleJson) {
        this.trackingId = trackingId;
        this.status = status;
        this.requestBundleJson = requestBundleJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status; 
        this.updatedAt = Instant.now();
    }

    public String getRequestBundleJson() { return requestBundleJson; }
    public void setRequestBundleJson(String requestBundleJson) { this.requestBundleJson = requestBundleJson; }

    public String getResponseX12Payload() { return responseX12Payload; }
    public void setResponseX12Payload(String responseX12Payload) { this.responseX12Payload = responseX12Payload; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}