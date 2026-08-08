package com.healthcare.epa.client;

import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component
public class EpaMultiProtocolTransportService implements X12LegacyClient {

    private final RestTemplate restTemplate;

    @Value("${epa.legacy.http.endpoint-url}")
    private String httpEndpointUrl;

    @Value("${epa.legacy.sftp.host}")
    private String sftpHost;

    @Value("${epa.legacy.sftp.port}")
    private int sftpPort;

    @Value("${epa.legacy.sftp.username}")
    private String sftpUsername;

    @Value("${epa.legacy.sftp.password}")
    private String sftpPassword;

    public EpaMultiProtocolTransportService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean sendX12Transaction(String x12Payload) {
        return transmitViaHttp(x12Payload, "EPA-TX-" + System.currentTimeMillis());
    }

    public boolean transmitViaHttp(String x12Payload, String trackingId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x12"));
        headers.set("X-EPA-Tracking-ID", trackingId);

        HttpEntity<String> request = new HttpEntity<>(x12Payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(httpEndpointUrl, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean transmitViaSftp(String x12Payload, String fileName) {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUsername, sftpHost, sftpPort);
            session.setPassword(sftpPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            Channel channel = session.openChannel("sftp");
            channel.connect();
            channelSftp = (ChannelSftp) channel;

            // Ensure outbound directory exists before putting file
            try {
                channelSftp.stat("/outbound");
            } catch (SftpException e) {
                channelSftp.mkdir("/outbound");
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(x12Payload.getBytes(StandardCharsets.UTF_8));
            channelSftp.put(inputStream, "/outbound/" + fileName);

            return true;
        } catch (Exception e) {
            // Log the actual exception details
            org.slf4j.LoggerFactory.getLogger(EpaMultiProtocolTransportService.class)
                    .error("SFTP transmission failed for file {}: {}", fileName, e.getMessage(), e);
            return false;
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}