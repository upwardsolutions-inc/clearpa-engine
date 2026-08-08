package com.healthcare.epa.service;

import com.healthcare.epa.entity.EpaAdjudicationEntity;
import com.healthcare.epa.repository.EpaAdjudicationRepository;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class EpaSftpInboundPoller {

    private static final Logger log = LoggerFactory.getLogger(EpaSftpInboundPoller.class);

    private final SftpRemoteFileTemplate sftpTemplate;
    private final EpaAdjudicationRepository repository;

    @Value("${epa.sftp.remote-inbound-dir}")
    private String inboundDir;

    public EpaSftpInboundPoller(SessionFactory<SftpClient.DirEntry> sessionFactory,
                                EpaAdjudicationRepository repository) {
        this.sftpTemplate = new SftpRemoteFileTemplate(sessionFactory);
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollInboundFolder() {
        sftpTemplate.execute(session -> {
            SftpClient.DirEntry[] files = session.list(inboundDir);
            for (SftpClient.DirEntry file : files) {
                if (file.getAttributes().isRegularFile()) {
                    String remoteFilePath = inboundDir + "/" + file.getFilename();
                    log.info("Discovered inbound X12 response file: {}", remoteFilePath);

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    session.read(remoteFilePath, outputStream);
                    String fileContent = outputStream.toString(StandardCharsets.UTF_8);

                    processX12Response(file.getFilename(), fileContent);

                    // Clean up processed file from remote SFTP
                    session.remove(remoteFilePath);
                    log.info("Processed and removed file from SFTP: {}", remoteFilePath);
                }
            }
            return null;
        });
    }

    private void processX12Response(String filename, String content) {
        // Strip prefix 'response_' and extensions (.278 or .x12)
        String trackingId = filename
                .replace("response_", "")
                .replaceAll("\\.(278|x12)$", "")
                .trim();

        log.info("Parsed tracking ID [{}] from filename [{}]", trackingId, filename);

        Optional<EpaAdjudicationEntity> optionalEntity = repository.findById(trackingId);
        if (optionalEntity.isPresent()) {
            EpaAdjudicationEntity entity = optionalEntity.get();
            
            // Determine status based on X12 content or set to COMPLETE
            String status = content.contains("A1") ? "COMPLETE" : "REJECTED";
            entity.setStatus(status);
            entity.setResponseX12Payload(content);
            
            repository.save(entity);
            log.info("Successfully updated tracking ID {} to status {} in H2", trackingId, status);
        } else {
            log.warn("Received SFTP response for unknown tracking ID: {}", trackingId);
        }
    }
}