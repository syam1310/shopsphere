package com.shopsphere.notification_service.service;

import com.shopsphere.notification_service.entity.EmailJob;
import com.shopsphere.notification_service.repository.EmailJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(
        name = "notification.email.worker.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailJobWorker {

    private static final Logger log =
            LoggerFactory.getLogger(EmailJobWorker.class);

    private final EmailJobRepository repository;
    private final EmailService emailService;

    public EmailJobWorker(
            EmailJobRepository repository,
            EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelay = 5000)
    public void process() {
        var jobs = repository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        "PENDING", Instant.now());

        for (EmailJob job : jobs) {
            try {
                emailService.send(
                        job.recipient,
                        job.subject,
                        job.message);

                job.status = "SENT";
                job.sentAt = Instant.now();

            } catch (Exception ex) {
                job.attempts++;

                if (job.attempts >= 5) {
                    job.status = "FAILED";
                } else {
                    long delaySeconds = Math.min(
                            300L, 10L * (1L << (job.attempts - 1)));
                    job.nextAttemptAt =
                            Instant.now().plusSeconds(delaySeconds);
                }

                log.error(
                        "Email job {} failed, attempt {}",
                        job.id, job.attempts, ex);
            }

            repository.save(job);
        }
    }
}
