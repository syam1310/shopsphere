package com.shopsphere.notification_service.repository;

import com.shopsphere.notification_service.entity.EmailJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {

    boolean existsByEventKey(String eventKey);

    List<EmailJob> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status, Instant now);
}
