package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.impl.InventoryCompensationClaimRepositoryImpl;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class InventoryCompensationClaimIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("compensation_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void concurrentWorkers_claimDifferentRecords() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.registerBean(DataSource.class, () -> {
                DriverManagerDataSource ds = new DriverManagerDataSource();
                ds.setDriverClassName("org.postgresql.Driver");
                ds.setUrl(POSTGRES.getJdbcUrl());
                ds.setUsername(POSTGRES.getUsername());
                ds.setPassword(POSTGRES.getPassword());
                return ds;
            });

            context.register(TestJpaConfiguration.class);
            context.refresh();

            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            createSchema(jdbc);

            LocalDateTime now = LocalDateTime.now().minusMinutes(1);
            for (int i = 0; i < 4; i++) {
                insertCompensation(jdbc, now);
            }

            InventoryCompensationClaimRepository repository =
                    context.getBean(InventoryCompensationClaimRepository.class);
            TransactionTemplate transactions =
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class));


            CountDownLatch firstClaimed = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<List<Long>> first = executor.submit(() ->
                        transactions.execute(status -> {
                            List<Long> ids = repository
                                    .claimPendingCompensations(now, 2)
                                    .stream()
                                    .map(InventoryCompensation::getId)
                                    .toList();

                            // Keep the first transaction open while holding its locks.
                            firstClaimed.countDown();

                            try {
                                if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Timed out waiting to release first transaction");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }

                            return ids;
                        }));

                // Wait until worker one has claimed records and holds its locks.
                assertTrue(firstClaimed.await(10, TimeUnit.SECONDS), "First worker did not claim records in time");

                try {
                    Future<List<Long>> second = executor.submit(() ->
                            transactions.execute(status ->
                                    repository.claimPendingCompensations(now, 2)
                                            .stream()
                                            .map(InventoryCompensation::getId)
                                            .toList()));

                    // This should complete while worker one still holds its locks.
                    List<Long> secondIds = second.get(5, TimeUnit.SECONDS);

                    assertEquals(2, secondIds.size());

                    releaseFirst.countDown();

                    List<Long> firstIds = first.get(5, TimeUnit.SECONDS);

                    Set<Long> allClaimed = new HashSet<>(firstIds);
                    allClaimed.addAll(secondIds);

                    assertEquals(2, firstIds.size());
                    assertEquals(4, allClaimed.size(),
                            "Workers must not claim the same compensation record");

                } finally {
                    releaseFirst.countDown();
                }

                Integer processingCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_compensations WHERE status = 'PROCESSING'",
                        Integer.class);

                assertEquals(4, processingCount);

            } finally {
                releaseFirst.countDown();
                executor.shutdownNow();
            }

        }
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE inventory_compensations (
                    id BIGSERIAL PRIMARY KEY,
                    reservation_id UUID NOT NULL UNIQUE,
                    product_id BIGINT NOT NULL,
                    quantity INTEGER NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    attempt_count INTEGER NOT NULL,
                    next_retry_at TIMESTAMP NOT NULL,
                    claimed_at TIMESTAMP,
                    last_error VARCHAR(2000),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
    }

    private void insertCompensation(JdbcTemplate jdbc, LocalDateTime retryAt) {
        jdbc.update("""
                        INSERT INTO inventory_compensations (
                            reservation_id, product_id, quantity, status,
                            attempt_count, next_retry_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                101L,
                1,
                CompensationStatus.PENDING.name(),
                0,
                Timestamp.valueOf(retryAt),
                Timestamp.valueOf(retryAt),
                Timestamp.valueOf(retryAt));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestJpaConfiguration {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory =
                    new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.shopsphere.order_service.entity");
            factory.setPersistenceUnitName("compensation-test");
            factory.setJpaVendorAdapter(
                    new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put(
                    "hibernate.hbm2ddl.auto", "none");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        InventoryCompensationClaimRepository inventoryCompensationClaimRepository() {
            return new InventoryCompensationClaimRepositoryImpl();
        }
    }
}
