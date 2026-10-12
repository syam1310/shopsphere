# ShopSphere — E-Commerce Microservices Platform

ShopSphere is a Java-based e-commerce backend built with Spring Boot and a microservices architecture. The project
demonstrates REST API development, JWT authentication, service-to-service communication, inventory reservation, and
resilient order processing.

## Architecture

```text
                     Client / Postman
                            |
            +---------------+---------------+
            |               |               |
       User Service    Product Service   Order Service
         :8081             :8082             :8084
                                               |
                                         Inventory Service
                                              :8083
            |               |               |               |
        user_db          product_db      order_db       inventory_db
            +--------------- PostgreSQL -------------------+
```

Each service owns its application logic and database. The Order Service communicates with the Product and Inventory
services to validate products and reserve stock.

## Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot
- **Persistence:** Spring Data JPA, Hibernate, PostgreSQL
- **Security:** Spring Security, JWT, role-based authorization
- **API documentation:** OpenAPI / Swagger UI
- **Build:** Maven
- **Testing:** JUnit, Mockito, Testcontainers
- **Infrastructure:** Docker, Docker Compose

## Implemented Services

### 1. User Service — `8081`

- User registration and management
- Password hashing with BCrypt
- JWT access and refresh tokens
- Role-based authorization
- Logout and refresh-token revocation

### 2. Product Service — `8082`

- Product CRUD REST APIs
- Request validation and error handling
- JWT-protected endpoints and role-based access

### 3. Inventory Service — `8083`

- Inventory retrieval and management
- Stock reservation and release
- Idempotent reservation operations
- Internal service authentication
- Protection against invalid stock quantities

### 4. Order Service — `8084`

- Create and retrieve orders
- Product validation through the Product Service
- Inventory reservation through the Inventory Service
- Order status management
- Compensation handling when order processing fails
- Scheduled retries for failed inventory releases
- PostgreSQL concurrency testing for compensation claims

## API Documentation

When a service is running, Swagger UI is available at:

| Service   | Swagger UI                                  |
|-----------|---------------------------------------------|
| User      | http://localhost:8081/swagger-ui/index.html |
| Product   | http://localhost:8082/swagger-ui/index.html |
| Inventory | http://localhost:8083/swagger-ui/index.html |
| Order     | http://localhost:8084/swagger-ui/index.html |

Protected endpoints require the appropriate authentication or authorization.

## Running Locally

### Prerequisites

- Java 21
- Docker Desktop
- Maven, or the included Maven wrapper

### 1. Clone the repository

```bash
git clone https://github.com/syam1310/shopsphere.git
cd shopsphere
```

### 2. Configure environment variables

Create a local `.env` file from the project's required configuration. Define strong, locally generated values for
`JWT_SECRET` and `INTERNAL_SERVICE_SECRET`, and configure the service URLs.

**Never commit `.env` or real credentials to Git.**

### 3. Start PostgreSQL

```bash
docker compose up -d
```

Wait for the database containers to become healthy before starting the services.

### 4. Start the services

Start each service from its own directory, in separate terminals. Load the root `.env` variables into each terminal
before launching the service.

For example:

```bash
cd user-service
./mvnw spring-boot:run
```

Repeat for `product-service`, `inventory-service`, and `order-service`.

Ensure each service's database and required environment variables are configured before startup.

## Testing

Run the tests for an individual service using its Maven wrapper:

```bash
./mvnw test
```

Run this command from the service directory. The Order Service includes tests for compensation retry behavior and a
PostgreSQL-backed concurrency test using Testcontainers.

## Planned Enhancements

- API Gateway
- Redis caching
- Apache Kafka for event-driven communication
- Notification Service
- Docker-based deployment of all application services

## Repository

GitHub: https://github.com/syam1310/shopsphere