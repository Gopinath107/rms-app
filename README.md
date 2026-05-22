# Resource Management System (RMS) - Backend Service

Welcome to the backend service of the **Resource Management System (RMS)**. This is a secure, high-throughput enterprise REST API service built with **Java 21**, **Spring Boot 3.5.5**, and backed by **PostgreSQL**. The application is architected to support complex candidate resume parsing, multi-role access controls, report exports, resource tracking, and automated email workflows.

---

## 🛠️ Technology Stack

*   **Runtime Environment**: Java 21 (LTS)
*   **Framework**: Spring Boot 3.5.5 (Starter Web, Starter Data JPA, Starter Validation)
*   **Security**: Spring Security + JSON Web Tokens (JWT) using `jjwt` (v0.12.5)
*   **Database**: PostgreSQL
*   **Object Storage**: AWS SDK v2 (S3 Client Integration)
*   **Document Processing**:
    *   **Apache POI 5.2.5**: Spreadsheet exports (Excel generation)
    *   **Apache Tika 2.9.2**: PDF/Docx text extraction and resume parsing
    *   **documents4j 1.1.5**: Microsoft Word document transformations
*   **Email Engine**: Spring Starter Mail + Zoho SMTP Client
*   **Utilities**: Project Lombok (boilerplate reduction), Commons IO, Apache Commons CSV

---

## 📁 Repository Structure

```text
rms-app/
├── src/
│   ├── main/
│   │   ├── java/com/ris/rms/
│   │   │   ├── config/          # CORS, SQS/S3, JPA configurations
│   │   │   ├── controller/      # REST API Controllers (endpoints)
│   │   │   ├── dto/             # Request/Response Data Transfer Objects
│   │   │   ├── entity/          # JPA Hibernate Entity models
│   │   │   ├── exception/       # Custom exceptions and global error handler
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # JWT Authentication filters, providers & helpers
│   │   │   ├── service/         # Business logic implementation layers
│   │   │   └── util/            # Helper classes (Tika parses, CSV utilities)
│   │   └── resources/
│   │       ├── db/              # Database schema scripts / initial seeds
│   │       ├── application.properties      # Core settings (database connections, ports)
│   │       └── application-dev.properties  # Development configurations
│   └── test/                    # JUnit and Mockito test suites
├── pom.xml                      # Maven dependencies and plugin definitions
└── README.md                    # Backend documentation (this file)
```

---

## ⚙️ Core Configuration

The configuration parameters are managed in [src/main/resources/application.properties](file:///c:/Users/Gopinath%20Kannan/Pictures/RMS-19/rms-app/src/main/resources/application.properties).

### 1. Database Connection (PostgreSQL)
The application expects a PostgreSQL instance running locally on port `5432`. It operates within a dedicated schema named `rms` inside the database named `resource`.
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/resource?currentSchema=rms
spring.datasource.username=rms
spring.datasource.password=rms
spring.jpa.hibernate.ddl-auto=update
spring.datasource.hikari.connection-init-sql=SET search_path TO rms
```

### 2. Security & JWT
Role-based route permissions are governed by JSON Web Tokens.
*   **Secret Key**: `rms-secret-key-for-jwt-token-generation-must-be-at-least-256-bits-long-for-HS256-algorithm`
*   **Expiration Duration**: 86400000 ms (24 hours)

### 3. AWS S3 Integration
The backend integrates with AWS S3 for hosting resume attachments:
*   **Region**: `eu-north-1`
*   **Bucket Name**: `rms-resume-bucket`
*   **Enforce SSE**: `true`

### 4. Zoho Mail Server Settings
Auto-email notifications for interview schedules and allocations use Zoho SMTP:
*   **SMTP Host**: `smtp.zoho.com`
*   **Port**: `587` (TLS enabled)

---

## 🏗️ REST API Controller Modules

The backend exposes resources across several key domains:
*   `/auth`: User registration, authentication, token retrieval.
*   `/api/employees`: Employee details, skillset matrices, experience years.
*   `/api/demands`: Resource demand tracking, allocation targets, statuses.
*   `/api/allocations`: Mapping resources to projects, budgeting, active tracking.
*   `/api/candidates`: Candidate lifecycle, multi-tiered interview results.
*   `/api/interviews`: L1/L2/L3 schedules, feedback logs, result updates.
*   `/api/projects`: Client project catalogs, dates, and ownership roles.
*   `/api/notifications`: Subscribed user event-driven broadcasts.
*   `/api/resume-parse`: Text extraction on docx/pdf CV submissions using Apache Tika.

---

## 🏃 Getting Started

### Prerequisites

*   **Java 21 Development Kit (JDK)**
*   **Apache Maven** (v3.8.x or later)
*   **PostgreSQL** instance running locally with schema `rms` initialized

### Database Setup
Before starting the backend, make sure to execute the following on your PostgreSQL instance:
```sql
CREATE DATABASE resource;
\c resource
CREATE SCHEMA rms;
CREATE USER rms WITH PASSWORD 'rms';
GRANT ALL PRIVILEGES ON DATABASE resource TO rms;
GRANT ALL PRIVILEGES ON SCHEMA rms TO rms;
```

### Running Locally

1.  Navigate into the `rms-app` directory:
    ```bash
    cd rms-app
    ```
2.  Build the Maven project and download dependencies:
    ```bash
    mvn clean install
    ```
3.  Run the Spring Boot application:
    ```bash
    mvn spring-boot:run
    ```

The application starts by default on port `8081`. You can access Swagger documentation or verify server status via the root endpoints.
To override standard profiles, use:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
