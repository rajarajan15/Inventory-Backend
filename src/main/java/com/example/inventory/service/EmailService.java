package com.example.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Sends plain-text notification emails through Gmail SMTP (configured via spring.mail.*).
 * <p>
 * When {@code app.mail.enabled=false} (the default for local dev and tests) emails are only logged,
 * so invitation links can be copied from the console. Sending is async and failures are logged,
 * never propagated, so an SMTP outage cannot break a business operation.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String fromAddress;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            logger.warn("Skipping email '{}': no recipient configured", subject);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || mailSender == null) {
            logger.info("[EMAIL disabled] To: {} | Subject: {}\n{}", to, subject, body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setTo(to);
            message.setSubject(subject.replaceAll("[\r\n]+", " ")); // user-supplied names must not inject headers
            message.setText(body);
            mailSender.send(message);
            logger.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            logger.error("Failed to send email to {} ({}): {}", to, subject, e.getMessage());
        }
    }

    @Async
    public void sendToAll(Collection<String> recipients, String subject, String body) {
        for (String to : recipients) {
            send(to, subject, body);
        }
    }
}
