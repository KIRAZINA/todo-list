# Todo List Backend API

A production-ready REST API built with Spring Boot 3.5.6 and Java 17, featuring JWT-based authentication, role-based access control, and per-user data isolation.

**Storage note:** the default profile still uses an in-memory H2 database, so data is lost when the process restarts. This keeps Phase 1 lightweight; use a persistent database profile before treating this as production storage.

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17 (LTS)
- **Security**: Spring Security 6.x, JWT (jjwt 0.12.6), BCrypt
- **Database**: H2 (in-memory), JPA/Hibernate, Flyway
- **Documentation**: SpringDoc OpenAPI, Swagger UI
- **Tools**: Lombok, Jackson, Maven
- **Testing**: JUnit 5.11.3, Mockito, RestAssured 5.5.0, AssertJ 3.26.3, Spring Boot Test

## ✨ Core Features

- **JWT Authentication**
  - Secure registration & login endpoints
  - 24-hour default token expiration (configurable)
  - Stateless session management
  - HS256 algorithm with 256-bit secret key (startup fails fast without `JWT_SECRET`)

- **Role-Based Access Control (RBAC)**
  - `USER` and `ADMIN` roles
  - Fine-grained endpoint protection (`ROLE_USER`/`ROLE_ADMIN` on task endpoints)
  - 401 Unauthorized (JSON) for missing/invalid credentials, 403 for insufficient rights

- **Task Management**
  - Full CRUD operations for todo items (create → 201, delete → 204)
  - Per-user data isolation via `userId` foreign key
  - Partial updates via PATCH; explicit `null` clears `description`/`dueDate`
  - Input validation with Bean Validation annotations

- **Layered Architecture**
  - Clean separation: Controller → Service → Repository
  - DTO pattern for request/response objects
  - Global exception handling with `@ControllerAdvice`
  - Centralized error responses with proper HTTP status codes

## 🚀 How to Run

### Prerequisites
- Java 17+ (`java -version`)
- Maven 3.8+ (`mvn -v`) — or use the bundled Maven wrapper

### Build & Run

```bash
# Clean and build the project
./mvnw clean install

# Run the application (JWT_SECRET is required in the default profile)
JWT_SECRET=<your-256-bit-secret> ./mvnw spring-boot:run

# Run with the dev profile (in-memory H2, H2 console, schema re-created)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on **`http://localhost:8080`** by default.

## 🔐 Security & JWT Usage

### Token Configuration
- **Default Expiration**: 24 hours (86,400,000 ms)
- **Secret**: 256-bit Base64-encoded key (set via `JWT_SECRET` env var; no default — startup aborts without it)
- **Algorithm**: HS256

### Authentication Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | User registration (201 Created) |
| POST | `/api/auth/login` | JWT token generation |

### Secured Endpoints
All endpoints under `/api/**` (except `/api/auth/**` and `/api/test/health`) require a valid JWT.

**Bearer Token Format:**
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNzQ0...
```

**Role Mapping:**
- `ROLE_USER` → Standard user operations (own tasks)
- `ROLE_ADMIN` → User + admin operations (all tasks, user management)

## 📚 API Documentation

### Swagger UI
Open in your browser:
```
http://localhost:8080/swagger-ui.html
```

The standalone `swagger-ui.html` page is configured to fetch the OpenAPI specification from SpringDoc's default endpoint (`/v3/api-docs`). The UI includes:
- Interactive API testing
- Authentication setup via "Authorize" button
- Request/response examples
- Schema documentation

**Note**: SpringDoc OpenAPI dependency is already included in `pom.xml`. The `/v3/api-docs` endpoint will be available automatically.

### Base Path
All API routes are prefixed with `/api`.

### Example Request
```bash
# Get all tasks (requires auth)
curl -X GET http://localhost:8080/api/tasks \
  -H "Authorization: Bearer <your_jwt_token>"

# Create a new task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"title":"Learn Spring Boot","description":"Complete the tutorial","dueDate":"2024-12-31"}'
```

## ⚙️ Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64-encoded 256-bit secret | required in default profile |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds | `86400000` |
| `H2_CONSOLE_ENABLED` | Enable H2 web console | `false` (dev profile only) |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `default` |

### H2 Console
Enabled only in the `dev` profile. Access the database at:
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:todo
User:     sa
Password: (leave empty)
```

## 📁 Project Structure

```
src/main/java/com/example/todo/
├── config/            # Spring configuration classes
├── security/          # JWT filter, SecurityConfig, UserDetailsService
├── controller/        # REST endpoints (@RestController)
├── service/           # Business logic (@Service)
├── repository/        # Data access layer (@Repository, JPA)
├── entity/            # JPA entities (Task, User)
├── dto/               # Request/Response DTOs
│   ├── request/       # Incoming payload classes
│   └── response/      # Outgoing payload classes
└── exception/         # Global exception handler (@ControllerAdvice)
```

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Test Coverage
- **Unit Tests**: Service & Repository layers (JUnit 5, Mockito)
- **API Tests**: RestAssured-based integration tests (auth, CRUD, authorization, pagination)
- **Security Tests**: JWT authentication and authorization

### Test Examples
```bash
# Run only unit tests
mvn test -Dtest="*Test"

# Run tests with specific profile
mvn test -Dspring.profiles.active=test

# Run with coverage report (if JaCoCo configured)
mvn clean test jacoco:report
```

### Test Structure
- `src/test/java/com/example/todo/`
  - `api/tests/` - End-to-end API flow tests (RestAssured)
  - `controller/` - REST endpoint integration tests
  - `service/` - Business logic tests
  - `security/` - Authentication/authorization tests
  - `util/` - Test helpers and base classes

## 🤝 Next Steps / Roadmap

- [ ] **PostgreSQL Migration** — Replace H2 with production-ready RDBMS
- [ ] **Refresh Tokens** — Long-lived refresh + short-lived access tokens
- [ ] **Audit Logging** — Entity change tracking with `@CreatedDate`, `@LastModifiedDate`
- [ ] **Dockerization** — Multi-stage Dockerfile + Docker Compose
- [ ] **CI/CD Pipeline** — GitHub Actions with Maven, JUnit, and security scanning
- [ ] **Rate Limiting** — API endpoint protection against abuse
- [ ] **Caching** — Redis integration for performance optimization
- [ ] **API Versioning** — Support for multiple API versions