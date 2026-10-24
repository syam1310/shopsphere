package com.shopsphere.payment_service.repository;

import com.shopsphere.payment_service.entity.PaymentMethod;
import com.shopsphere.payment_service.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("payment_test")
                    .withUsername("test")
                    .withPassword("test");
    @Autowired
    private PaymentRepository paymentRepository;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void atomicInsertWithSameKeyCreatesOnlyOnePayment() {
        String key = "integration-" + UUID.randomUUID();

        int firstInsert = paymentRepository.insertIfIdempotencyKeyAbsent(
                22L, 10L, new BigDecimal("500.00"),
                PaymentMethod.UPI.name(), PaymentStatus.SUCCESS.name(), key);

        int secondInsert = paymentRepository.insertIfIdempotencyKeyAbsent(
                22L, 10L, new BigDecimal("500.00"),
                PaymentMethod.UPI.name(), PaymentStatus.SUCCESS.name(), key);

        assertEquals(1, firstInsert);
        assertEquals(0, secondInsert);
        assertEquals(1, paymentRepository.findAll().stream()
                .filter(payment -> key.equals(payment.getIdempotencyKey()))
                .count());
    }
}
