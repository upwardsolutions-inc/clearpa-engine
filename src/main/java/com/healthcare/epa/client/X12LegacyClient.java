package com.healthcare.epa.client;

public interface X12LegacyClient {
    boolean sendX12Transaction(String x12Payload);
}
