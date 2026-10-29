# Calculator REST API

A production-style Calculator REST API built with Spring Boot, wrapped in a complete CI/CD pipeline using Jenkins, Docker and Docker Compose. A push to GitHub builds, tests, containerizes and deploys the app with no manual steps.

**SPE Mini-Project** | CSE 816 Software Production Engineering | M.Tech (CSE) | Term I, 2026-27

## Architecture

```
Developer ──git push──▶ GitHub ──webhook──▶ Jenkins Pipeline ──push──▶ Docker Hub
                                                  │                        │
                                          compose up -d                  pull
                                                  ▼                        │
                                   Docker Compose (network: calc-net) ◀────┘
                                    ┌──────────────────────────┐
                    host :8081 ───▶ │   Nginx reverse proxy    │  nginx:1.27-alpine
                                    └────────────┬─────────────┘
                                                 │ proxy_pass
                                    ┌────────────▼─────────────┐
                                    │      Calculator API      │  Spring Boot, :8080
                                    │  health: /actuator/health│  (internal only)
                                    └──────────────────────────┘
```

## Prerequisites

| Tool           | Version |
|----------------|---------|
| Java           | 21      |
| Maven          | 3.9+    |
| Docker         | 24+     |
| Docker Compose | v2      |
| Jenkins        | 2.4xx+ (only for the pipeline) |

## Local Development

### 1. Build

```bash
mvn clean package -DskipTests
```

Produces `target/calculator.jar`.

### 2. Run tests

```bash
mvn test
```

Runs 13 JUnit 5 tests (5 unit, 8 MockMvc integration). JUnit XML reports are written to `target/surefire-reports/`.

### 3. Run locally (without Docker)

```bash
java -jar target/calculator.jar --server.port=8081
```

The API is then available at `http://localhost:8081`. Port 8081 is used because Jenkins usually occupies 8080.

## Configuration (`.env`)

All deployment settings live in a `.env` file, which is gitignored. Create it from the template:

```bash
cp .env.example .env    # then edit the values
```

| Variable       | Purpose                                    | Example                              |
|----------------|--------------------------------------------|--------------------------------------|
| `DOCKER_IMAGE` | Docker Hub repository to push and run      | `your-dockerhub-username/spe-calculator` |
| `HOST_PORT`    | Host port Nginx publishes the API on       | `8081`                               |
| `NOTIFY_EMAIL` | Where Jenkins sends build status emails    | `you@example.com`                    |

Docker Compose reads `.env` automatically. The Docker Hub login is never stored here; it lives in Jenkins credentials.

## Docker

### Build image

```bash
docker build -t calculator .
```

The Dockerfile is multi-stage:

- **Build stage** (`maven:3.9.9-eclipse-temurin-21`): resolves dependencies in their own cached layer, then packages the JAR.
- **Runtime stage** (`eclipse-temurin:21-jre-alpine`): copies only the JAR and runs it as a non-root `app` user. Exposes 8080.

### Run container

```bash
docker run -d -p 8081:8080 --name calculator calculator
curl "http://localhost:8081/api/v1/add?a=2&b=3"
```

## Docker Compose (Full Stack)

```bash
docker compose up -d --build
```

Starts two services on the `calc-net` bridge network:

| Service      | Description |
|--------------|-------------|
| `calculator` | Spring Boot API on internal port 8080. Health check on `/actuator/health`, `restart: unless-stopped`. |
| `nginx`      | Reverse proxy on `localhost:${HOST_PORT}` (default 8081). Starts only once `calculator` is healthy. |

```bash
docker compose ps                              # check status and health
docker compose up -d --scale calculator=2      # run two API replicas behind Nginx
docker compose down                            # tear down
```

## API Reference

All operations take numeric query parameters `a` and `b` and return JSON. Examples use the Compose stack on port 8081.

| Operation | Endpoint                     |
|-----------|------------------------------|
| Add       | `GET /api/v1/add?a=&b=`      |
| Subtract  | `GET /api/v1/subtract?a=&b=` |
| Multiply  | `GET /api/v1/multiply?a=&b=` |
| Divide    | `GET /api/v1/divide?a=&b=`   |
| Health    | `GET /actuator/health`       |

### Add

```bash
curl "http://localhost:8081/api/v1/add?a=2&b=3"
# {"operation":"add","a":2.0,"b":3.0,"result":5.0}
```

### Subtract

```bash
curl "http://localhost:8081/api/v1/subtract?a=10&b=3"
# {"operation":"subtract","a":10.0,"b":3.0,"result":7.0}
```

### Multiply

```bash
curl "http://localhost:8081/api/v1/multiply?a=4&b=5"
# {"operation":"multiply","a":4.0,"b":5.0,"result":20.0}
```

### Divide

```bash
curl "http://localhost:8081/api/v1/divide?a=10&b=4"
# {"operation":"divide","a":10.0,"b":4.0,"result":2.5}
```

### Errors → HTTP 400

Division by zero, a missing parameter, or a non-numeric parameter all return HTTP 400 with a structured body:

```bash
curl "http://localhost:8081/api/v1/divide?a=5&b=0"
# {"status":400,"error":"Bad Request","message":"Division by zero is not allowed","timestamp":"..."}

curl "http://localhost:8081/api/v1/add?a=5"
# {"status":400,"error":"Bad Request","message":"Missing required parameter 'b'","timestamp":"..."}

curl "http://localhost:8081/api/v1/add?a=x&b=1"
# {"status":400,"error":"Bad Request","message":"Parameter 'a' must be a number, got 'x'","timestamp":"..."}
```

### Health check

```bash
curl "http://localhost:8081/actuator/health"
# {"status":"UP",...}
```

## CI/CD Pipeline (Jenkins)

The `Jenkinsfile` defines a declarative pipeline with 6 stages:

| Stage        | What it does |
|--------------|--------------|
| Checkout     | Pulls the latest code from GitHub and restores `.env` from the `calculator-env` credential |
| Build        | `mvn clean package -DskipTests` |
| Test         | `mvn test`, publishes JUnit reports, fails the build on test failure |
| Docker Build | Builds the image tagged `:<build number>` and `:latest` |
| Push         | Logs in with `dockerhub-credentials` and pushes both tags to Docker Hub |
| Deploy       | `docker compose up -d --no-build --remove-orphans`, then polls `/actuator/health` until `UP` and runs a smoke test |

- **Trigger:** GitHub webhook on every push.
- **Notifications:** an email to `NOTIFY_EMAIL` on success and on failure.
- **Idempotent:** `docker compose up -d` only recreates containers whose image or config changed, so re-runs don't break a running deployment. Concurrent builds are disabled.

### Jenkins setup

1. Install plugins: Pipeline, Git, GitHub, JUnit, Mailer, Timestamper.
2. Give the Jenkins user access to Docker: `sudo usermod -aG docker jenkins`, then restart Jenkins.
3. Add credentials under *Manage Jenkins → Credentials*:

   | ID                      | Kind                  | Contents |
   |-------------------------|-----------------------|----------|
   | `dockerhub-credentials` | Username with password | Docker Hub username and access token |
   | `calculator-env`        | Secret file           | Your filled-in `.env` |

4. Configure SMTP under *Manage Jenkins → System → E-mail Notification*.
5. Create a *Pipeline* job using *Pipeline script from SCM* pointing at this repository, and tick *GitHub hook trigger for GITScm polling*.
6. In the GitHub repository, add a webhook to `http://<jenkins-host>/github-webhook/`.

## Project Structure

```
SPE_Mini_Project/
├── src/
│   ├── main/
│   │   ├── java/com/spe/calculator/
│   │   │   ├── CalculatorApplication.java     # Spring Boot entry point
│   │   │   ├── CalculatorController.java      # REST endpoints
│   │   │   ├── CalculatorService.java         # arithmetic logic
│   │   │   ├── CalculationResponse.java       # success response record
│   │   │   ├── ErrorResponse.java             # error response record
│   │   │   └── GlobalExceptionHandler.java    # maps errors to HTTP 400
│   │   └── resources/application.properties
│   └── test/java/com/spe/calculator/
│       ├── CalculatorServiceTest.java             # unit tests
│       └── CalculatorControllerIntegrationTest.java # MockMvc tests
├── nginx/nginx.conf          # reverse proxy config
├── pom.xml                   # Maven build
├── Dockerfile                # multi-stage build
├── .dockerignore
├── docker-compose.yml        # API + Nginx stack
├── Jenkinsfile               # declarative CI/CD pipeline
├── .env.example              # configuration template
├── .gitignore
└── README.md
```
