# SPE Mini-Project: Calculator REST API with CI/CD

A Spring Boot calculator REST API. Jenkins builds, tests, containerizes and deploys it automatically, using Docker and Docker Compose.

## API

| Operation | Endpoint                      | Notes                                |
|-----------|-------------------------------|--------------------------------------|
| Add       | `GET /api/v1/add?a=&b=`       |                                      |
| Subtract  | `GET /api/v1/subtract?a=&b=`  |                                      |
| Multiply  | `GET /api/v1/multiply?a=&b=`  |                                      |
| Divide    | `GET /api/v1/divide?a=&b=`    | `b=0` returns HTTP 400               |
| Health    | `GET /actuator/health`        | Spring Boot Actuator                 |

Success response:

```json
{ "operation": "add", "a": 2.0, "b": 3.0, "result": 5.0 }
```

Error response (missing or non-numeric parameter, or division by zero), HTTP 400:

```json
{ "status": 400, "error": "Bad Request", "message": "Division by zero is not allowed", "timestamp": "..." }
```

## Build, test and run locally

Requires Java 21 and Maven 3.9+.

```bash
mvn clean package          # build target/calculator.jar (runs tests)
mvn test                   # tests only; JUnit XML goes to target/surefire-reports/
java -jar target/calculator.jar --server.port=8081   # 8080 is taken by Jenkins
curl "http://localhost:8081/api/v1/add?a=2&b=3"
```

## Docker

```bash
docker build -t calculator .
docker run --rm -p 8081:8080 calculator
```

The Dockerfile has two stages. A Maven/JDK stage builds the JAR, caching dependencies in their own layer. A slim JRE Alpine stage then runs only that JAR, as a non-root user.

## Configuration (.env)

All settings live in a `.env` file: `DOCKER_IMAGE`, `HOST_PORT` and `NOTIFY_EMAIL`. `.env` is gitignored. Create it from the template:

```bash
cp .env.example .env    # then edit the values
```

Docker Compose reads `.env` automatically. Jenkins loads it from a *Secret file* credential (see below). The Docker Hub login is never stored in `.env`; it stays in the Jenkins credential `dockerhub-credentials`.

## Docker Compose

```bash
docker compose up -d --build
curl "http://localhost:8081/api/v1/multiply?a=4&b=5"
docker compose ps
docker compose down
```

The stack contains:
- `calculator`: the API, with a health check against `/actuator/health`, a restart policy and environment variables.
- `nginx`: a reverse proxy on host port 8081 (override with `HOST_PORT`). It starts only once the API is healthy.

To run more API replicas behind the proxy: `docker compose up -d --scale calculator=2`.


