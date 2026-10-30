package com.shopsphere.notification_service.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "email_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_job_event",
                columnNames = "event_key"))
public class EmailJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "event_key", nullable = false, unique = true)
    public String eventKey;

    @Column(nullable = false)
    public String recipient;

    @Column(nullable = false)
    public String subject;

    @Column(nullable = false, length = 10000)
    public String message;

    @Column(nullable = false)
    public String status = "PENDING";

    @Column(nullable = false)
    public int attempts = 0;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public Instant nextAttemptAt = Instant.now();
    public Instant sentAt;
}
