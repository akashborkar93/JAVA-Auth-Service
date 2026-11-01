# Secure Auth Platform

A Spring Boot service with a browser UI for registration, login, and a profile page. It uses server-side
sessions (an HttpOnly cookie) instead of tokens, and ships with unit and integration tests, coverage,
SonarQube analysis, Trivy scanning, and a hardened container image.

## Stack

- Java 17, Spring Boot 4.1.2, Spring Security 7, Gradle 8.14.3 (Kotlin DSL) with dependency locking
- JUnit 5, Mockito, AssertJ, MockMvc, JaCoCo
- SonarQube (Gradle `org.sonarqube` plugin), Trivy
- Multi-stage Docker image on Eclipse Temurin, non-root, read-only filesystem

## Run it

Requires JDK 17.

```bash
./gradlew build                          # compile + tests + coverage   (Windows: gradlew.bat build)
java -jar auth-service/build/libs/app.jar
```

Open **http://localhost:8081** in a browser:

1. **Register** a user (username 3-50 chars, password 12-72 chars).
2. **Sign in**, and the page shows your profile (username, role, member-since).
3. **Sign out** ends the session. Reloading the page while signed in keeps you signed in until the session expires (15 minutes).

With Docker (no Java or Gradle needed, the Dockerfile builds the app):

```bash
docker build -t secure-auth/auth-service .
docker run --rm -p 8081:8081 --read-only --tmpfs /tmp --cap-drop ALL \
  --security-opt no-new-privileges:true secure-auth/auth-service
```

Then open http://localhost:8081.

Data is stored in an in-memory H2 database, so **users are lost on restart**.

## Endpoints

| Method | Path                 | Auth    | Notes |
|--------|----------------------|---------|-------|
| GET    | `/`                  | none    | Browser UI (register, login, profile forms) |
| GET    | `/api/auth/csrf`     | none    | Returns the CSRF header name and token for POST requests |
| POST   | `/api/auth/register` | none    | 201 with profile, 409 if username taken, 400 on invalid input |
| POST   | `/api/auth/login`    | none    | 200 with profile and sets the session cookie; 401 on bad credentials |
| POST   | `/api/auth/logout`   | session | 204; invalidates the session |
| GET    | `/api/auth/me`       | session | Current user's profile; 401 without a session |
| GET    | `/actuator/health`   | none    | Liveness and readiness |

Errors use RFC 7807 `application/problem+json`. All POSTs need the CSRF token from `/api/auth/csrf` in the
`X-CSRF-TOKEN` header; the UI does this automatically.

## Security controls

- **Passwords**: BCrypt (strength 12); 12-72 bytes, longer input is rejected instead of silently truncated.
- **No user enumeration**: unknown users and wrong passwords return the same 401, and a dummy hash is checked so timing is similar.
- **Sessions**: `HttpOnly`, `SameSite=Strict` cookie, 15-minute timeout, session id rotated on login (anti-fixation), invalidated on logout. Set `SESSION_COOKIE_SECURE=true` when serving over HTTPS.
- **CSRF protection is on** for every state-changing request.
- **Browser hardening**: Content-Security-Policy (`script-src 'self'`, no inline scripts), `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`. The UI writes user data with `textContent`, never `innerHTML`.
- **Other**: only `/actuator/health` exposed, no stack traces in responses, non-root container user, read-only root filesystem, all capabilities dropped.

Known limitations (out of scope for this sample): no login rate limiting or account lockout, in-memory H2 and in-memory sessions (use a managed database and a shared session store to run multiple instances), and no TLS (put a gateway or ingress in front).

## Tests

```bash
./gradlew test               # 14 tests
./gradlew jacocoTestReport   # auth-service/build/reports/jacoco/test/
```

- `AuthServiceTest`: registration and credential-check logic, including the no-enumeration path.
- `AuthControllerIT`: register, login, profile and logout flow with a real session; CSRF enforcement; duplicate, invalid, and over-long-password handling; the UI page and its CSP header.

## Scanning

```bash
# SonarQube (point at your separately hosted server)
export SONAR_HOST_URL=https://<your-sonarqube-server> SONAR_TOKEN=<token>
./gradlew sonar -Dsonar.qualitygate.wait=true

# Trivy
trivy fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed \
  --skip-dirs build --skip-dirs .gradle --exit-code 1 .
trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 secure-auth/auth-service:local

# Everything at once
scripts/security-scan.sh
```

Dependency scanning reads `auth-service/gradle.lockfile`. Regenerate after dependency changes with `./gradlew build --write-locks`.

## CI pipeline and artifacts

`.github/workflows/ci.yml` runs on every push to `main` and on pull requests, in three jobs:

1. **build**: compiles and tests, then uploads `app.jar` as a GitHub artifact (`auth-service-jar`) and test/coverage reports.2. **scan** (after build): SonarQube analysis (when `SONAR_TOKEN` is set) and Trivy filesystem scan.
3. **image** (after build): builds the Docker image and runs a Trivy image scan.

The root `Dockerfile` is multi-stage: it compiles the jar (`bootJar`, tests skipped because the **build** job already ran them) and copies it into a minimal JRE image.

For SonarQube, set secret `SONAR_TOKEN` and variable `SONAR_HOST_URL`.

Trivy filesystem and image scans use the official `aquasecurity/trivy-action@v0.36.0` (HIGH/CRITICAL, unfixed ignored, fails the job on findings).

Local equivalent: `docker build -t secure-auth/auth-service .`

## Verification status

| Check | Result |
|-------|--------|
| `./gradlew build` (14 tests, JaCoCo) | Passing |
| Running app: page served, register, login, profile via cookie, logout, 401 and 403 paths | Passing |
| Trivy filesystem scan (HIGH/CRITICAL, unfixed ignored) | Passing; 0 findings |
| Docker image build and `trivy image` scan | **Not run** (Docker is not installed on the build machine) |
| SonarQube analysis and quality gate | **Not run** (needs a running SonarQube server) |
