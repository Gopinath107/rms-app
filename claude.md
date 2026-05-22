# CLAUDE.md — Backend (rms-app) AI Developer Handbook

> This document is the single source of truth for any AI assistant (Claude, Copilot, Gemini, etc.) working on the **rms-app** backend codebase. Read this file in full before making any changes.

---

## 1. Project Identity

| Property | Value |
| :--- | :--- |
| **Application** | Resource Management System — Backend API Service |
| **Framework** | Spring Boot 3.5.5 |
| **Language** | Java 21 (LTS) |
| **Build Tool** | Apache Maven |
| **Database** | PostgreSQL (schema: `rms`, database: `resource`) |
| **Server Port** | `8081` |
| **Base Package** | `com.ris.rms` |
| **Architecture** | Layered: Controller → Service (Interface) → Service Impl → Repository → Entity |

---

## 2. Repository Structure (Complete)

```
rms-app/
├── pom.xml                            # Maven build configuration and dependencies
├── src/
│   ├── main/
│   │   ├── java/com/ris/rms/
│   │   │   ├── RmsAppApplication.java         # @SpringBootApplication entry point
│   │   │   │
│   │   │   ├── config/                        # Spring configuration beans
│   │   │   │   ├── SecurityConfig.java            # Spring Security filter chain, JWT filter registration
│   │   │   │   ├── CorsConfig.java                # CORS allowed origins (dev + prod), methods, headers
│   │   │   │   ├── S3Config.java                  # AWS S3 client bean (region, credentials)
│   │   │   │   ├── MailAsyncConfig.java           # Async thread pool for email sending
│   │   │   │   └── AppConfig.java                 # General app-level beans
│   │   │   │
│   │   │   ├── security/                      # Authentication & authorization
│   │   │   │   ├── JwtUtil.java                   # JWT token generation, validation, claim extraction
│   │   │   │   ├── JwtAuthenticationFilter.java   # OncePerRequestFilter — extracts & validates JWT from Authorization header
│   │   │   │   ├── CustomUserDetailsService.java  # Loads UserAccount from DB for Spring Security
│   │   │   │   └── PasswordHashUtil.java          # BCrypt password hashing utilities
│   │   │   │
│   │   │   ├── controller/                    # REST API endpoints (21 controllers)
│   │   │   │   ├── AuthController.java            # POST /api/auth/login, /api/auth/register, password reset
│   │   │   │   ├── EmployeeController.java        # CRUD + search + import/export for internal employees
│   │   │   │   ├── CandidateController.java       # CRUD + resume upload + parse for external candidates
│   │   │   │   ├── DemandController.java          # Demand tracking, reports, stage counts, demand matching
│   │   │   │   ├── AllocationController.java      # Resource-to-project allocation CRUD
│   │   │   │   ├── ProjectController.java         # Project CRUD
│   │   │   │   ├── CompanyController.java         # Client/company (account) CRUD
│   │   │   │   ├── InterviewController.java       # Interview scheduling, feedback, level results
│   │   │   │   ├── InterviewBatchController.java  # Batch interview operations
│   │   │   │   ├── ResourceRequestController.java # Individual resource request CRUD
│   │   │   │   ├── ResReqGroupController.java     # Resource request group operations (bulk create, flow)
│   │   │   │   ├── ResReqDecisionController.java  # Approval/rejection decisions on resource requests
│   │   │   │   ├── FlowController.java            # Multi-step workflow/flow engine for request lifecycle
│   │   │   │   ├── AccountController.java         # Account (client company) management
│   │   │   │   ├── UserAccountController.java     # User account CRUD (system admin)
│   │   │   │   ├── NotificationController.java    # In-app notification retrieval and management
│   │   │   │   ├── DepartmentController.java      # Department CRUD
│   │   │   │   ├── RoleController.java            # Role listing and CRUD
│   │   │   │   ├── SkillController.java           # Skill master data CRUD
│   │   │   │   ├── StatusMetaController.java      # Status master data listing
│   │   │   │   └── ResumeParseController.java     # Resume text extraction endpoint
│   │   │   │
│   │   │   ├── service/                       # Service interfaces (24 interfaces)
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── EmployeeService.java
│   │   │   │   ├── CandidateService.java
│   │   │   │   ├── DemandService.java
│   │   │   │   ├── DemandMatchingService.java     # AI-powered demand-to-resource matching logic
│   │   │   │   ├── AllocationService.java
│   │   │   │   ├── ProjectService.java
│   │   │   │   ├── CompanyService.java
│   │   │   │   ├── InterviewService.java
│   │   │   │   ├── ResourceRequestService.java
│   │   │   │   ├── ResReqGroupService.java
│   │   │   │   ├── ResReqDecisionService.java
│   │   │   │   ├── FlowService.java
│   │   │   │   ├── AccountService.java
│   │   │   │   ├── UserAccountService.java
│   │   │   │   ├── NotificationService.java
│   │   │   │   ├── DepartmentService.java
│   │   │   │   ├── RoleService.java
│   │   │   │   ├── SkillService.java
│   │   │   │   ├── StatusMetaService.java
│   │   │   │   ├── EmailService.java              # Email composition + sending interface
│   │   │   │   ├── ResumeParseService.java        # Resume text extraction + structured parsing (~30KB, complex)
│   │   │   │   ├── ResumeStorageService.java      # S3 upload/download for resumes
│   │   │   │   └── LlmClientService.java          # External LLM API integration for intelligent matching
│   │   │   │
│   │   │   ├── service/impl/                  # Service implementations (20 classes)
│   │   │   │   ├── AuthServiceImpl.java           # Login auth, JWT token issuance
│   │   │   │   ├── EmployeeServiceImpl.java       # Employee CRUD, Excel import/export, search (~69KB)
│   │   │   │   ├── CandidateServiceImpl.java      # Candidate lifecycle, resume ops (~56KB)
│   │   │   │   ├── DemandServiceImpl.java         # Demand CRUD, reports, aggregation (~90KB — largest service)
│   │   │   │   ├── AllocationServiceImpl.java     # Allocation management (~16KB)
│   │   │   │   ├── ProjectServiceImpl.java        # Project CRUD with skill associations (~12KB)
│   │   │   │   ├── CompanyServiceImpl.java        # Company/client CRUD
│   │   │   │   ├── InterviewServiceImpl.java      # Full interview lifecycle management (~48KB)
│   │   │   │   ├── ResourceRequestServiceImpl.java  # Request CRUD + status management (~24KB)
│   │   │   │   ├── ResReqGroupServiceImpl.java    # Group request operations + bulk create (~38KB)
│   │   │   │   ├── ResReqDecisionServiceImpl.java # Approval workflow logic (~14KB)
│   │   │   │   ├── FlowServiceImpl.java           # Multi-step request flow engine (~42KB)
│   │   │   │   ├── AccountServiceImpl.java        # Account management
│   │   │   │   ├── UserAccountServiceImpl.java    # User CRUD + role assignment (~13KB)
│   │   │   │   ├── EmailServiceImpl.java          # HTML email templates + Zoho SMTP sending (~26KB)
│   │   │   │   ├── NotificationServiceImpl.java   # Notification persistence
│   │   │   │   ├── DepartmentServiceImpl.java     # Department CRUD
│   │   │   │   ├── RoleServiceImpl.java           # Role CRUD
│   │   │   │   ├── SkillServiceImpl.java          # Skill master CRUD
│   │   │   │   └── StatusMetaServiceImpl.java     # Status lookup
│   │   │   │
│   │   │   ├── dto/                           # Data Transfer Objects (35 DTOs)
│   │   │   │   ├── AuthLoginRequest.java          # Login request body (username, password, role)
│   │   │   │   ├── AuthUserDto.java               # Login response (token, user info)
│   │   │   │   ├── EmployeeDto.java               # Employee create/update payload (~4.4KB)
│   │   │   │   ├── CandidateDto.java              # Candidate create/update payload (~3.2KB)
│   │   │   │   ├── DemandCreateDto.java           # Demand creation request
│   │   │   │   ├── DemandUpdateDto.java           # Demand update request
│   │   │   │   ├── DemandResponseDto.java         # Demand response with relationships (~2KB)
│   │   │   │   ├── DemandReportRequest.java       # Report filter parameters
│   │   │   │   ├── DemandRequestSummaryDto.java   # Summary view of demand requests
│   │   │   │   ├── DemandStageCountsDto.java      # Aggregated counts by demand stage
│   │   │   │   ├── DetailedResourceReportRequest.java  # Detailed export report params
│   │   │   │   ├── AllocationDto.java             # Allocation payload
│   │   │   │   ├── ProjectDto.java                # Project payload
│   │   │   │   ├── ProjectHistoryDto.java         # Project assignment history
│   │   │   │   ├── CompanyDto.java                # Company payload
│   │   │   │   ├── AccountDto.java                # Account (client) payload
│   │   │   │   ├── InterviewDto.java              # Interview scheduling payload (~2KB)
│   │   │   │   ├── ResourceRequestDto.java        # Resource request payload (~1.7KB)
│   │   │   │   ├── ResourceRequestGroupDto.java   # Group request payload
│   │   │   │   ├── BulkCreateResReqDto.java       # Bulk resource request creation
│   │   │   │   ├── BulkCreateResponseDto.java     # Bulk creation response
│   │   │   │   ├── FlowRequest.java               # Workflow step execution request
│   │   │   │   ├── GroupFlowDto.java              # Group flow state DTO (~2.3KB)
│   │   │   │   ├── GroupRequestView.java          # Group request view summary
│   │   │   │   ├── LevelProgressDto.java          # Interview level progress tracking
│   │   │   │   ├── MatchResponseDto.java          # Demand-resource match results
│   │   │   │   ├── UserAccountDto.java            # User account payload
│   │   │   │   ├── DepartmentDto.java             # Department payload
│   │   │   │   ├── RoleDto.java                   # Role payload
│   │   │   │   ├── SkillDto.java                  # Skill payload
│   │   │   │   ├── StatusOptionDto.java           # Status option for dropdowns
│   │   │   │   ├── EmployeeDocumentDto.java       # Employee document metadata
│   │   │   │   ├── ResumeShareDto.java            # Resume sharing payload
│   │   │   │   ├── ImportResultDto.java           # Excel import result summary
│   │   │   │   └── OktaTokenResponse.java         # Okta SSO token response (if SSO enabled)
│   │   │   │
│   │   │   ├── entity/                        # JPA/Hibernate entities (29 entities)
│   │   │   │   ├── Employee.java                  # Core employee entity (~6.3KB) — skills, projects, documents
│   │   │   │   ├── EmployeeSkill.java             # Many-to-many: Employee ↔ Skill
│   │   │   │   ├── EmployeeSkillId.java           # Composite key for EmployeeSkill
│   │   │   │   ├── EmployeeDocument.java          # Employee resume/document storage metadata
│   │   │   │   ├── Candidate.java                 # External candidate entity (~5.8KB)
│   │   │   │   ├── CandidateSkill.java            # Many-to-many: Candidate ↔ Skill
│   │   │   │   ├── CandidateSkillId.java          # Composite key for CandidateSkill
│   │   │   │   ├── CandidateDocument.java         # Candidate resume/document metadata
│   │   │   │   ├── Demand.java                    # Resource demand entity (~2.6KB)
│   │   │   │   ├── Allocation.java                # Resource-to-project allocation (~1.9KB)
│   │   │   │   ├── Project.java                   # Project entity
│   │   │   │   ├── ProjectSkill.java              # Many-to-many: Project ↔ Skill
│   │   │   │   ├── ProjectSkillId.java            # Composite key for ProjectSkill
│   │   │   │   ├── Company.java                   # Client/company entity
│   │   │   │   ├── Account.java                   # Account entity (client grouping)
│   │   │   │   ├── Interview.java                 # Interview scheduling entity (~1.8KB)
│   │   │   │   ├── InterviewFeedback.java         # Interview feedback per level
│   │   │   │   ├── ResourceRequest.java           # Individual resource request (~2.3KB)
│   │   │   │   ├── ResourceRequestGroup.java      # Grouped resource requests (~1.5KB)
│   │   │   │   ├── ResourceRequestSkill.java      # Many-to-many: ResourceRequest ↔ Skill
│   │   │   │   ├── ResourceRequestSkillId.java    # Composite key
│   │   │   │   ├── ResourceRequestApproval.java   # Approval/rejection record
│   │   │   │   ├── UserAccount.java               # Login user account entity (~1.7KB)
│   │   │   │   ├── Role.java                      # Role master entity
│   │   │   │   ├── Skill.java                     # Skill master entity
│   │   │   │   ├── Department.java                # Department master entity
│   │   │   │   ├── StatusMaster.java              # Status master (demand statuses, etc.)
│   │   │   │   ├── Notification.java              # In-app notification entity
│   │   │   │   └── PasswordResetToken.java        # Password reset token entity
│   │   │   │
│   │   │   ├── repository/                    # Spring Data JPA repositories (25 repositories)
│   │   │   │   ├── EmployeeRepository.java        # Custom queries for search, filtering (~2.4KB)
│   │   │   │   ├── CandidateRepository.java
│   │   │   │   ├── DemandRepository.java
│   │   │   │   ├── AllocationRepository.java      # Custom queries for allocation lookups
│   │   │   │   ├── ProjectRepository.java
│   │   │   │   ├── CompanyRepository.java
│   │   │   │   ├── InterviewRepository.java       # Custom queries for interview filtering
│   │   │   │   ├── InterviewFeedbackRepository.java
│   │   │   │   ├── ResourceRequestRepository.java  # Complex queries for request management (~1.5KB)
│   │   │   │   ├── ResReqGroupRepository.java
│   │   │   │   ├── ResReqApprovalRepository.java
│   │   │   │   ├── ResourceRequestSkillRepository.java
│   │   │   │   ├── AccountRepository.java
│   │   │   │   ├── UserAccountRepository.java
│   │   │   │   ├── RoleRepository.java
│   │   │   │   ├── SkillRepository.java
│   │   │   │   ├── DepartmentRepository.java
│   │   │   │   ├── EmployeeSkillRepository.java
│   │   │   │   ├── CandidateSkillRepository.java
│   │   │   │   ├── EmployeeDocumentRepository.java  # S3 key lookups (~1.4KB)
│   │   │   │   ├── CandidateDocumentRepository.java  # S3 key lookups (~1.2KB)
│   │   │   │   ├── ProjectSkillRepository.java
│   │   │   │   ├── NotificationRepository.java    # Unread count queries
│   │   │   │   ├── PasswordResetTokenRepository.java
│   │   │   │   └── StatusMasterRepository.java
│   │   │   │
│   │   │   ├── exception/                     # Exception handling
│   │   │   │   └── GlobalExceptionHandler.java    # @ControllerAdvice — unified error response format
│   │   │   │
│   │   │   └── util/                          # Utility classes
│   │   │       └── PasswordHashGenerator.java     # Standalone BCrypt hash generator tool
│   │   │
│   │   └── resources/
│   │       ├── application.properties             # Production/default config
│   │       ├── application-dev.properties         # Dev profile config
│   │       ├── db/                                # SQL schema scripts / seed data
│   │       ├── static/                            # Static resources (if any)
│   │       └── templates/                         # Thymeleaf templates (if any)
│   │
│   └── test/                                      # Test directory
│
└── target/                                        # Maven build output (git-ignored)
```

---

## 3. Security Architecture

### Authentication Flow
1. Client sends `POST /api/auth/login` with `{ username, password, roleName }`.
2. `AuthServiceImpl` looks up the `UserAccount` by username, validates credentials.
3. On success, `JwtUtil.generateToken()` creates a JWT with username as subject.
4. Token is returned to the client in the response body.
5. All subsequent requests must include `Authorization: Bearer <token>` header.

### JWT Filter Chain
```
Request → CorsFilter → JwtAuthenticationFilter → SecurityFilterChain → Controller
```

- `JwtAuthenticationFilter` extends `OncePerRequestFilter`.
- It extracts the token from the `Authorization` header, validates it via `JwtUtil`, loads the user via `CustomUserDetailsService`, and sets the `SecurityContext`.
- If the token is missing or invalid, the request proceeds unauthenticated (Spring Security's `authorizeHttpRequests` rules decide access).

### Public Endpoints (No Authentication Required)
```
/api/auth/**           — Login, register, password reset
/api/public/**         — Public data endpoints
/api/user-accounts/list — User listing (for login dropdowns)
```

### Password Handling
- `PasswordHashUtil.java` provides BCrypt hashing.
- ⚠️ **Current state**: `SecurityConfig` uses `NoOpPasswordEncoder` (passwords stored in plain text). This is a known technical debt item.

### CORS Configuration
- Defined in `CorsConfig.java` with dual configuration (WebMvcConfigurer + CorsConfigurationSource bean).
- Allowed origins include localhost ports (`3000`, `3001`, `5173`, `5174`) and production IPs.
- All standard HTTP methods are allowed (`GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`).
- `Authorization` and `X-Total-Count` headers are exposed.
- Credentials are allowed (`allowCredentials = true`).

---

## 4. Database Schema

### Connection Details (Use placeholders — never commit real credentials)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/resource?currentSchema=rms
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password
spring.datasource.hikari.connection-init-sql=SET search_path TO rms
spring.jpa.hibernate.ddl-auto=update
```

### Schema: `rms` within database `resource`
Hibernate auto-creates/updates tables from JPA entities. Key tables:

| Entity | Table | Key Relationships |
| :--- | :--- | :--- |
| `Employee` | `employee` | → EmployeeSkill (M2M), EmployeeDocument, Allocation |
| `Candidate` | `candidate` | → CandidateSkill (M2M), CandidateDocument |
| `Demand` | `demand` | → Project, Company, Skill |
| `Allocation` | `allocation` | → Employee, Project |
| `Project` | `project` | → Company, ProjectSkill (M2M) |
| `Company` | `company` | → Projects |
| `Account` | `account` | → Company grouping |
| `Interview` | `interview` | → Candidate, InterviewFeedback |
| `InterviewFeedback` | `interview_feedback` | → Interview |
| `ResourceRequest` | `resource_request` | → ResourceRequestGroup, ResourceRequestSkill (M2M) |
| `ResourceRequestGroup` | `resource_request_group` | → ResourceRequest (1:M) |
| `ResourceRequestApproval` | `resource_request_approval` | → ResourceRequest |
| `UserAccount` | `user_account` | → Role |
| `Role` | `role` | Master data |
| `Skill` | `skill` | Master data |
| `Department` | `department` | Master data |
| `StatusMaster` | `status_master` | Master data |
| `Notification` | `notification` | → UserAccount |
| `PasswordResetToken` | `password_reset_token` | → UserAccount |

### Many-to-Many Join Tables
- `EmployeeSkill` (composite key: `EmployeeSkillId` → employeeId + skillId)
- `CandidateSkill` (composite key: `CandidateSkillId` → candidateId + skillId)
- `ProjectSkill` (composite key: `ProjectSkillId` → projectId + skillId)
- `ResourceRequestSkill` (composite key: `ResourceRequestSkillId` → requestId + skillId)

---

## 5. External Integrations

### AWS S3 (Resume Storage)
- Configured in `S3Config.java` and `application.properties`.
- `ResumeStorageService.java` handles upload/download/delete operations.
- Files are stored with server-side encryption (SSE) enforced.
- Credentials and bucket details should be set via environment variables (never hardcode).

### Resume Parsing (Apache Tika + Custom Logic)
- `ResumeParseService.java` (~30KB) is a complex service that:
  - Accepts PDF/DOCX uploads
  - Extracts raw text using Apache Tika (tika-core + tika-parsers)
  - Applies custom regex/heuristic logic to extract structured fields (name, email, phone, skills, experience, education)
  - Returns structured JSON for form auto-fill on the frontend

### Document Conversion (documents4j)
- Microsoft Word document transformation capabilities.
- Used for converting DOCX to PDF or other formats when needed.

### Excel Generation (Apache POI)
- `poi` + `poi-ooxml` v5.2.5 for `.xlsx` spreadsheet creation.
- Used in employee export, demand reports, and resource report generation.
- CSV support via Apache Commons CSV.

### Email Service (Zoho SMTP)
- `EmailServiceImpl.java` (~26KB) constructs HTML email templates for:
  - Interview scheduling notifications
  - Resource request status updates
  - Allocation confirmations
  - Password reset links
- Async sending via `MailAsyncConfig.java` thread pool.
- SMTP config: Zoho at port 587 with STARTTLS.

### LLM Integration
- `LlmClientService.java` provides an HTTP client for calling external LLM APIs.
- `DemandMatchingService.java` uses this for intelligent demand-to-resource matching.

---

## 6. API Endpoint Reference

All endpoints are prefixed with `/api`.

### Authentication
| Method | Path | Description |
| :--- | :--- | :--- |
| POST | `/api/auth/login` | Authenticate and receive JWT token |
| POST | `/api/auth/register` | Register new user account |
| POST | `/api/auth/forgot-password` | Initiate password reset |
| POST | `/api/auth/reset-password` | Complete password reset |

### Employees
| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/employees` | List employees (paginated, filtered) |
| GET | `/api/employees/{id}` | Get employee by ID |
| POST | `/api/employees` | Create employee |
| PUT | `/api/employees/{id}` | Update employee |
| DELETE | `/api/employees/{id}` | Delete employee |
| POST | `/api/employees/import` | Bulk import from Excel |
| GET | `/api/employees/export` | Export to Excel |

### Candidates
| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/candidates` | List candidates |
| POST | `/api/candidates` | Create candidate |
| PUT | `/api/candidates/{id}` | Update candidate |
| DELETE | `/api/candidates/{id}` | Delete candidate |
| POST | `/api/candidates/{id}/resume` | Upload resume |
| GET | `/api/candidates/{id}/resume` | Download resume |

### Demands
| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/demands` | List demands |
| POST | `/api/demands` | Create demand |
| PUT | `/api/demands/{id}` | Update demand |
| POST | `/api/demands/report` | Generate demand report |
| GET | `/api/demands/stage-counts` | Get counts by stage |
| POST | `/api/demands/match` | AI-powered resource matching |
| POST | `/api/demands/detailed-export` | Detailed Excel export |

### Interviews
| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/interviews` | List interviews |
| POST | `/api/interviews` | Schedule interview |
| PUT | `/api/interviews/{id}` | Update interview |
| POST | `/api/interviews/{id}/feedback` | Submit L1/L2/L3 feedback |

### Resource Requests & Groups
| Method | Path | Description |
| :--- | :--- | :--- |
| GET | `/api/resource-requests` | List requests |
| POST | `/api/resource-requests` | Create request |
| GET | `/api/res-req-groups` | List request groups |
| POST | `/api/res-req-groups/bulk` | Bulk create requests |
| POST | `/api/res-req-decisions/{id}` | Approve/reject request |
| POST | `/api/flows` | Execute workflow step |

### Supporting Resources
| Method | Path | Description |
| :--- | :--- | :--- |
| CRUD | `/api/projects` | Project management |
| CRUD | `/api/companies` | Company/client management |
| CRUD | `/api/accounts` | Account management |
| CRUD | `/api/departments` | Department management |
| CRUD | `/api/roles` | Role management |
| CRUD | `/api/skills` | Skill master data |
| GET | `/api/status-meta` | Status options |
| CRUD | `/api/user-accounts` | User account management |
| GET | `/api/notifications` | Notification retrieval |
| POST | `/api/resume-parse` | Resume text extraction |

---

## 7. Layered Architecture Conventions

### Controller Layer
- Annotated with `@RestController` and `@RequestMapping("/api/...")`.
- **Only** handles HTTP concerns: request parsing, response building, status codes.
- Delegates all business logic to service interfaces.
- Uses `@RequiredArgsConstructor` (Lombok) for constructor injection.

### Service Layer
- **Interfaces** in `service/` package define contracts.
- **Implementations** in `service/impl/` contain all business logic.
- Services are `@Service` annotated.
- Services may call other services (e.g., `DemandServiceImpl` calls `EmailService` for notifications).
- Heavy services (DemandServiceImpl ~90KB, EmployeeServiceImpl ~69KB) contain complex business logic — refactor carefully.

### Repository Layer
- Extends `JpaRepository<Entity, Long>` (or appropriate ID type).
- Custom queries use `@Query` annotations with JPQL or native SQL.
- Repository methods follow Spring Data naming conventions where possible.

### DTO Layer
- DTOs are used for all request/response bodies — entities are never exposed directly.
- Mapping between DTOs and entities happens in the service layer.
- DTOs use Lombok `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`.

### Entity Layer
- JPA `@Entity` annotated with `@Table(name = "...")`.
- Use `@GeneratedValue(strategy = GenerationType.IDENTITY)` for auto-increment IDs.
- Relationships use `@ManyToOne`, `@OneToMany`, `@ManyToMany` with appropriate fetch types.
- Composite keys use `@EmbeddedId` with dedicated `@Embeddable` ID classes.

---

## 8. Coding Conventions

### File Naming
- Controllers: `<Domain>Controller.java`
- Services: `<Domain>Service.java` (interface), `<Domain>ServiceImpl.java` (implementation)
- Repositories: `<Domain>Repository.java`
- Entities: `<Domain>.java` (singular noun)
- DTOs: `<Domain>Dto.java` or `<Domain>Request.java` / `<Domain>Response.java`

### Lombok Usage
All classes use Lombok annotations to reduce boilerplate:
- `@Data` — getters, setters, toString, equals, hashCode
- `@Builder` — builder pattern
- `@NoArgsConstructor` / `@AllArgsConstructor`
- `@RequiredArgsConstructor` — constructor injection in services/controllers
- `@Getter` / `@Setter` — selective use on entities

### Error Handling
- `GlobalExceptionHandler.java` uses `@ControllerAdvice` to catch and format exceptions.
- Return standardized error responses with appropriate HTTP status codes.
- Never expose stack traces in production (configured in `application.properties`).

---

## 9. Security Rules

- ⚠️ **NEVER** commit real database credentials, AWS access keys, secret keys, JWT secrets, SMTP passwords, or any other sensitive values.
- ⚠️ **NEVER** log tokens, passwords, or secrets at any log level.
- Use environment variables or external config for all secrets in production.
- In documentation and code comments, always use placeholder values (e.g., `your_password_here`, `YOUR_AWS_ACCESS_KEY`).
- The JWT secret must be at least 256 bits for HS256 algorithm.

---

## 10. Development Commands

```bash
mvn clean install           # Clean build + download dependencies + run tests
mvn spring-boot:run         # Start the application (port 8081)
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # Start with dev profile
mvn test                    # Run unit tests only
mvn package -DskipTests     # Build JAR without running tests
```

---

## 11. Key Dependencies Reference

| Dependency | Version | Purpose |
| :--- | :--- | :--- |
| `spring-boot-starter-web` | 3.5.5 | REST API framework |
| `spring-boot-starter-data-jpa` | 3.5.5 | JPA / Hibernate ORM |
| `spring-boot-starter-security` | 3.5.5 | Authentication & authorization |
| `spring-boot-starter-validation` | 3.5.5 | Bean validation (`@Valid`, `@NotNull`, etc.) |
| `spring-boot-starter-mail` | 3.5.5 | Email sending |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | 0.12.5 | JWT creation and validation |
| `postgresql` | (managed) | PostgreSQL JDBC driver |
| `lombok` | (managed) | Boilerplate code generation |
| `poi` / `poi-ooxml` | 5.2.5 | Excel spreadsheet generation |
| `commons-csv` | 1.10.0 | CSV parsing |
| `tika-core` / `tika-parsers-standard-package` | 2.9.2 | Document text extraction (PDF, DOCX) |
| `documents4j-local` / `documents4j-transformer-msoffice-word` | 1.1.5 | Word document conversion |
| `software.amazon.awssdk:s3` / `auth` | 2.29.0 (BOM) | AWS S3 file storage |
| `commons-io` | 2.16.1 | IO utilities |
