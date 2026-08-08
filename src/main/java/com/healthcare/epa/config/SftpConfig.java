package com.healthcare.epa.config;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

@Configuration
public class SftpConfig {

    @Value("${epa.sftp.host}")
    private String host;

    @Value("${epa.sftp.port}")
    private int port;

    @Value("${epa.sftp.username}")
    private String username;

    @Value("${epa.sftp.password}")
    private String password;

    @Bean
    public SessionFactory<SftpClient.DirEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(host);
        factory.setPort(port);
        factory.setUser(username);
        factory.setPassword(password);
        factory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<SftpClient.DirEntry>(factory);
    }

    @Bean
    public SftpRemoteFileTemplate sftpRemoteFileTemplate(SessionFactory<SftpClient.DirEntry> sessionFactory) {
        return new SftpRemoteFileTemplate(sessionFactory);
    }
}