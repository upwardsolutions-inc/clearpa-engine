package com.healthcare.epa.client;

import com.jcraft.jsch.*;
import com.healthcare.epa.service.EpaAdjudicationPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

@Component
public class EpaSftpResponseListener {

    private static final Logger log = LoggerFactory.getLogger(EpaSftpResponseListener.class);

    private final EpaAdjudicationPersistenceService persistenceService;

    @Value("${epa.legacy.sftp.host}") private String sftpHost;
    @Value("${epa.legacy.sftp.port}") private int sftpPort;
    @Value("${epa.legacy.sftp.username}") private String sftpUsername;
    @Value("${epa.legacy.sftp.password}") private String sftpPassword;

    public EpaSftpResponseListener(EpaAdjudicationPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Scheduled(fixedDelay = 30000)
    public void pollInboundResponses() {
        Session session = null;
        ChannelSftp channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUsername, sftpHost, sftpPort);
            session.setPassword(sftpPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            String remoteDir = "/inbound/";
            String archiveDir = "/inbound/archive/";

            // Ensure archive folder exists on SFTP server
            ensureDirectoryExists(channel, archiveDir);

            // List all .278 and .x12 response files inside /inbound/
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> fileList = channel.ls(remoteDir + "*");

            for (ChannelSftp.LsEntry entry : fileList) {
                String fileName = entry.getFilename();
                
                // Skip directories (like 'archive') and dot files
                if (entry.getAttrs().isDir() || fileName.startsWith(".")) {
                    continue;
                }

                log.info("Processing inbound mainframe X12 file: {}", fileName);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                
                channel.get(remoteDir + fileName, outputStream);
                String x12ResponseContent = outputStream.toString(StandardCharsets.UTF_8);

                processX12Response(x12ResponseContent);

                // Move processed file to /inbound/archive/
                channel.rename(remoteDir + fileName, archiveDir + fileName);
                log.info("Successfully processed and archived mainframe response file: {}", fileName);
            }

        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                log.debug("Inbound directory empty or pending file creation.");
            } else {
                log.error("SFTP channel error polling responses: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error polling inbound SFTP mainframe responses: {}", e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private void ensureDirectoryExists(ChannelSftp channel, String path) {
        try {
            channel.stat(path);
        } catch (SftpException e) {
            try {
                channel.mkdir(path);
                log.info("Created missing SFTP directory: {}", path);
            } catch (SftpException ex) {
                log.error("Failed to create SFTP directory {}: {}", path, ex.getMessage());
            }
        }
    }

    private void processX12Response(String x12Content) {
        String controlNumber = extractControlNumber(x12Content);
        String adjudicationOutcome = x12Content.contains("A1") ? "COMPLETE" : "REJECTED";

        persistenceService.updateClaimResponseStatus(controlNumber, adjudicationOutcome, x12Content);
    }

    private String extractControlNumber(String x12) {
        if (x12 == null || x12.isBlank()) {
            return "UNKNOWN";
        }
        for (String segment : x12.split("~")) {
            String trimmedSegment = segment.trim();
            if (trimmedSegment.startsWith("BHT")) {
                String[] elements = trimmedSegment.split("\\*");
                if (elements.length >= 4) {
                    return elements[3];
                }
            }
        }
        return "UNKNOWN";
    }
}