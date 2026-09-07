# Ride Service

Manages ride lifecycle (creation, cancellation, completion) for the Ride Booking Platform. Publishes domain events via Atropos for downstream consumers.

## Tech Stack

- Java 17
- Spring Boot (via `zeta-spring-boot-pom:4.1.14`)
- PostgreSQL
- Atropos SDK (`atropos-client:2.2.13`)
- Lombok

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Ride Service                      │
│                                                  │
│  Controller → Service → Repository (PostgreSQL)  │
│                  ↓                                │
│           EventPublisher                         │
│                  ↓                                │
│         Atropos (Kinesis)                        │
└─────────────────────────────────────────────────┘
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/rides` | Create a new ride request |
| `GET` | `/api/rides/{rideId}` | Get ride details |
| `POST` | `/api/rides/{rideId}/cancel` | Cancel a ride |
| `POST` | `/api/rides/{rideId}/complete` | Complete a ride |

## Events Published

| Event | Topic | Trigger |
|-------|-------|---------|
| `RIDE_REQUESTED` | `_system_0_ride-requested` | New ride created |
| `RIDE_CANCELLED` | `_system_0_ride-cancelled` | Ride cancelled |
| `RIDE_COMPLETED` | `_system_0_ride-completed` | Ride completed |

## Project Structure

```
src/main/java/com/ridebooking/rideservice/
├── RideServiceApplication.java
├── config/          # Atropos client configuration
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities (Ride)
├── events/          # Event models & publisher
├── exception/       # Global exception handler
├── repository/      # Spring Data JPA repositories
└── service/         # Business logic
```

## Prerequisites

- Java 17
- Maven 3.8+
- PostgreSQL 15
- Zeta Artifactory credentials

## Local Setup

```bash
# 1. Set Artifactory credentials
export MAVEN_REPO_USERNAME=<your-username>
export MAVEN_REPO_PASSWORD=<your-password>

# 2. Start PostgreSQL
docker run -d --name ride-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ride_booking_db \
  -p 5432:5432 postgres:15

# 3. Build
mvn clean package -DskipTests -s .mvn/settings.xml

# 4. Run
mvn spring-boot:run -s .mvn/settings.xml
```

Service starts on **port 8081**.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ride_booking_db` | DB URL |
| `atropos.client.endpoint` | (env-specific) | Atropos endpoint |

## CI/CD

- Jenkins pipeline: `ci.JenkinsFile`
- Uses `MavenDockerPublish` shared library
- Publishes Docker image + Helm chart
- SonarQube scan enabled

## Related Services

- [driver-service](../driver-service) — Consumes `RIDE_REQUESTED`, assigns drivers
- [notification-service](../notification-service) — Consumes all events, sends notifications
- [cluster-spec](../cluster-spec) — Atropos topic/subscription definitions
# Dummy commit to trigger pipeline
