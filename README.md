# Todo List Backend API

A production-ready REST API built with Spring Boot 3.5.6 and Java 17, featuring JWT-based authentication, role-based access control, per-user data isolation, account lockout, token revocation, and IP-based rate limiting.

## Storage Note

The application supports three runtime profiles:

- **default**: in-memory H2 database with Flyway-managed schema validation. Data is lost when the process restarts.
- **dev**: in-memory H2 with the schema re-created on every start and the H2 web console enabled. Intended for local development.
- **docker**: persistent PostgreSQL database, used by the Docker Compose setup.

Use the **docker** profile for any environment that requires persistent storage.

## Tech Stack

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17 (LTS)
- **Security**: Spring Security 6.x, JWT (jjwt 0.12.6), BCrypt
- **Database**: H2 (in-memory) / PostgreSQL, JPA/Hibernate, Flyway
- **Documentation**: SpringDoc OpenAPI, Swagger UI
- **Tools**: Lombok, Jackson, Maven
- **Testing**: JUnit 5.11.3, Mockito, RestAssured 5.5.0, AssertJ 3.26.3, Spring Boot Test

## Core Features

### Authentication and Security

- Registration and login endpoints protected by stateless JWT authentication.
- Token expiration defaults to 24 hours and is configurable.
- HS256 signing with a 256-bit secret key; startup fails fast if `JWT_SECRET` is missing or too short.
- Password policy enforced at registration: 8-100 characters, at least one lowercase letter, one uppercase letter, and one digit.
- Account lockout after repeated failed logins (5 attempts by default, configurable). While locked, login returns `423 Locked`.
- Every token carries a unique `jti` claim. `POST /api/auth/logout` blacklists the presented token, so a logged-out token is rejected on subsequent requests.
- IP-based rate limiting applied to all `/api/auth/**` endpoints. Exceeding the budget returns `429 Too Many Requests`.
- Optional bootstrap of the first `ADMIN` account at startup via environment variables.

### Role-Based Access Control

- Two roles: `USER` and `ADMIN`.
- `/api/tasks/**` requires authentication and enforces per-user data isolation: each caller only sees and manages their own tasks.
- `/api/admin/**` requires the `ADMIN` role and exposes user management and cross-user task visibility.
- Missing or invalid credentials return `401 Unauthorized` (JSON); authenticated callers without sufficient rights receive `403 Forbidden`.

### Admin Console

Explicit endpoints under `/api/admin/**`, gated by the `ADMIN` role:

- List all users (paginated).
- Change a user's role between `USER` and `ADMIN`.
- Delete a user; the user's tasks are removed as well.
- List tasks across all users (paginated).

Admin operations are self-protected: an administrator cannot change their own role or delete their own account (`400 Bad Request`).

### Task Management

- Full CRUD operations for todo items (create returns `201 Created`, delete returns `204 No Content`).
- Per-user data isolation via the `user_id` foreign key.
- Partial updates via `PATCH`; explicitly passing `null` clears `description` and `dueDate`.
- Input validation with Bean Validation annotations.

### Architecture

- Clean separation of concerns: Controller, Service, Repository layers.
- DTO pattern for all request and response objects.
- Global exception handling via `@RestControllerAdvice` with centralized JSON error responses and appropriate HTTP status codes.
- Database schema managed by Flyway migrations.

## How to Run

### Prerequisites

- Java 17 or later.
- Maven 3.8 or later, or the bundled Maven wrapper (`./mvnw`).

### Build and Run

```bash
# Clean and build the project
./mvnw clean install

# Run with the default profile (in-memory H2; JWT_SECRET is required)
JWT_SECRET=<your-256-bit-secret> ./mvnw spring-boot:run

# Run with the dev profile (in-memory H2, H2 console, schema re-created)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on `http://localhost:8080` by default.

### Run with Docker Compose

The repository includes a `Dockerfile` for the backend, a Dockerfile for the React frontend under `frontend/`, and a `docker-compose.yml` that wires together PostgreSQL, the backend, and the frontend.

```bash
# 1. Create the environment file from the template
cp .env.example .env

# 2. Generate a real secret and set it in .env
openssl rand -base64 32

# 3. Start the stack
docker compose up --build
```

The backend listens on `http://localhost:8080`, the frontend on `http://localhost:3000`. To bootstrap the first admin account, set `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `ADMIN_EMAIL` in `.env` before the first start.

## Security and JWT Usage

### Token Configuration

- **Default expiration**: 24 hours (86,400,000 ms).
- **Secret**: Base64-encoded value that decodes to at least 256 bits (32 bytes). Set via the `JWT_SECRET` environment variable; there is no default and startup aborts without it.
- **Algorithm**: HS256.

### Authentication Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new `USER` account (`201 Created`) |
| POST | `/api/auth/login` | Authenticate and obtain a JWT |
| POST | `/api/auth/logout` | Revoke the presented token (`204 No Content`) |

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users, paginated (`?page=0&size=20`) |
| PATCH | `/api/admin/users/{id}/role` | Change a user's role; body `{"role": "USER" or "ADMIN"}` |
| DELETE | `/api/admin/users/{id}` | Delete a user and their tasks (`204 No Content`) |
| GET | `/api/admin/tasks` | List all tasks across users, paginated (`?page=0&size=20`) |

### Secured Endpoints

All endpoints under `/api/**` require a valid JWT, with the following exceptions:

- `/api/auth/login` and `/api/auth/register` are public.
- `/api/auth/logout` requires authentication.
- `/api/test/health` and `/` are public.

**Bearer token format:**

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNzQ0...
```

**Role mapping:**

- `ROLE_USER`: standard user operations on own tasks.
- `ROLE_ADMIN`: all user operations plus admin endpoints (all tasks, user management).

### Account Lockout

After the configured number of consecutive failed login attempts (default 5), the account is locked for the configured duration (default 15 minutes). While locked, login attempts return `423 Locked`. A successful login clears the failed-attempt counter and any active lock.

### Rate Limiting

The authentication endpoints are throttled per client IP using a fixed-window counter. When the configured request budget is exceeded, requests return `429 Too Many Requests`. The rate limiter is in-memory and per-instance; a shared store is required for multi-instance deployments.

## API Documentation

### Swagger UI

Open in your browser:

```
http://localhost:8080/swagger-ui.html
```

The standalone `swagger-ui.html` page fetches the OpenAPI specification from SpringDoc's default endpoint (`/v3/api-docs`). The UI provides interactive testing, an "Authorize" button for JWT setup, request and response examples, and schema documentation.

### Base Path

All API routes are prefixed with `/api`.

### Example Requests

```bash
# Get all tasks (requires authentication)
curl -X GET http://localhost:8080/api/tasks \
  -H "Authorization: Bearer <your_jwt_token>"

# Create a new task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_jwt_token>" \
  -d '{"title":"Learn Spring Boot","description":"Complete the tutorial","dueDate":"2026-12-31"}'

# List all users as an administrator
curl -X GET "http://localhost:8080/api/admin/users?page=0&size=20" \
  -H "Authorization: Bearer <your_admin_jwt_token>"

# Promote a user to ADMIN
curl -X PATCH http://localhost:8080/api/admin/users/<id>/role \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_admin_jwt_token>" \
  -d '{"role":"ADMIN"}'
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64-encoded 256-bit secret | required in all profiles |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds | `86400000` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed origins | `http://localhost:3000,http://localhost:5173` |
| `LOCKOUT_MAX_ATTEMPTS` | Failed logins before the account is locked | `5` |
| `LOCKOUT_DURATION_MS` | Lockout duration in milliseconds | `900000` |
| `RATE_LIMIT_ENABLED` | Enable IP-based rate limiting on auth endpoints | `true` |
| `RATE_LIMIT_MAX_REQUESTS` | Maximum requests per IP within the window | `10` |
| `RATE_LIMIT_WINDOW_MS` | Rate-limit window in milliseconds | `60000` |
| `ADMIN_USERNAME` | Username for the bootstrap admin account | `admin` |
| `ADMIN_PASSWORD` | Password for the bootstrap admin account; blank disables bootstrap | *(empty)* |
| `ADMIN_EMAIL` | Email for the bootstrap admin account | `admin@example.com` |
| `DB_HOST` | PostgreSQL host (docker profile) | `postgres` |
| `DB_PORT` | PostgreSQL port (docker profile) | `5432` |
| `DB_NAME` | PostgreSQL database name (docker profile) | `tododb` |
| `DB_USERNAME` | PostgreSQL user (docker profile) | `todo` |
| `DB_PASSWORD` | PostgreSQL password (docker profile) | `todo` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `default` |

### H2 Console

Enabled only in the `dev` profile. Access the database at:

```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:todo
User:     sa
Password: (leave empty)
```

## Project Structure

```
src/main/java/com/example/todo/
|-- config/            # Spring configuration classes, admin bootstrap runner
|-- security/          # JWT filter and provider, rate limit filter, UserDetailsService
|-- controller/        # REST endpoints (@RestController)
|-- service/           # Business logic (@Service)
|-- repository/        # Data access layer (@Repository, JPA)
|-- entity/            # JPA entities (Task, User, RevokedToken)
|-- dto/               # Request/Response DTOs
|   |-- user/          # Registration, login, response, pagination, role update
|-- exception/         # Custom exceptions and global exception handler
```

Database migrations live in `src/main/resources/db/migration/` and are applied by Flyway.

## Testing

### Run All Tests

```bash
./mvnw test
```

### Test Coverage

- **Unit tests**: Service and security layers (JUnit 5, Mockito).
- **API tests**: RestAssured-based integration tests covering authentication, CRUD, authorization, pagination, lockout, logout, and admin endpoints.
- **Security tests**: JWT generation, validation, and revocation.

### Test Examples

```bash
# Run only unit tests
./mvnw test -Dtest="*Test"

# Run tests with the test profile
./mvnw test -Dspring.profiles.active=test
```

### Test Structure

- `src/test/java/com/example/todo/`
  - `api/tests/` - End-to-end API flow tests (RestAssured)
  - `controller/` - REST endpoint integration tests
  - `service/` - Business logic tests
  - `security/` - Authentication and authorization tests
  - `util/` - Test helpers and base classes

## Roadmap

- **Refresh Tokens**: long-lived refresh tokens combined with short-lived access tokens.
- **Audit Logging**: entity change tracking with creation and modification timestamps.
- **CI/CD Pipeline**: automated build, test, and security scanning pipeline.
- **Caching**: Redis-backed caching for performance optimization.
- **API Versioning**: support for multiple API versions.
