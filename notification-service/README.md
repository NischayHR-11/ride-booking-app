# Notification Service

Consumes ride lifecycle events via Atropos webhooks and persists notifications for riders and drivers. Provides a query API for retrieving user notifications.

## Tech Stack

- Java 17
- Spring Boot (via `zeta-spring-boot-pom:4.1.14`)
- PostgreSQL
- Lombok

## Architecture

```
┌───────────────────────────────────────────────────┐
│              Notification Service                   │
│                                                    │
│  Atropos Webhooks ─→ EventConsumers               │
│                          ↓                         │
│                    Service Layer                   │
│                          ↓                         │
│              Repository (PostgreSQL)               │
│                          ↑                         │
│  Controller (GET) ───────┘                        │
└───────────────────────────────────────────────────┘
```

## API Endpoints

### Public APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/notifications/{userId}` | Get all notifications for a user |

### Internal Webhooks (Atropos)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/notifications/events/driver-assigned/webhook` | Receives `DRIVER_ASSIGNED` event |
| `POST` | `/api/notifications/events/ride-cancelled/webhook` | Receives `RIDE_CANCELLED` event |
| `POST` | `/api/notifications/events/ride-completed/webhook` | Receives `RIDE_COMPLETED` event |

## Events Consumed

| Event | Source | Notification Created |
|-------|--------|---------------------|
| `DRIVER_ASSIGNED` | driver-service | "Driver {name} assigned to your ride" |
| `RIDE_CANCELLED` | ride-service | "Your ride has been cancelled" |
| `RIDE_COMPLETED` | ride-service | "Your ride is complete. Fare: ₹{amount}" |

## Project Structure

```
src/main/java/com/ridebooking/notificationservice/
├── NotificationServiceApplication.java
├── controller/      # REST + Webhook controllers
├── dto/             # Response DTOs
├── entity/          # JPA entities (Notification)
├── events/          # Event models & consumers
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

# 2. Start PostgreSQL (if not already running)
docker run -d --name ride-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ride_booking_db \
  -p 5432:5432 postgres:15

# 3. Build
mvn clean package -DskipTests -s .mvn/settings.xml

# 4. Run
mvn spring-boot:run -s .mvn/settings.xml
```

Service starts on **port 8083**.

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8083` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ride_booking_db` | DB URL |

## CI/CD

- Jenkins pipeline: `ci.JenkinsFile`
- Uses `MavenDockerPublish` shared library
- Publishes Docker image + Helm chart
- SonarQube scan enabled

## Related Services

- [ride-service](../ride-service) — Publishes `RIDE_CANCELLED`, `RIDE_COMPLETED`
- [driver-service](../driver-service) — Publishes `DRIVER_ASSIGNED`
- [cluster-spec](../cluster-spec) — Atropos topic/subscription definitions
