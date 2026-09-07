# Cluster Spec — Ride Booking Platform

Defines the Atropos event infrastructure (topics, subscriptions) and Heracles routing for the Ride Booking Platform microservices.

## What This Contains

| Component | Purpose |
|-----------|---------|
| `spec.yaml` | Cluster specification metadata |
| `helm-chart/` | Atropos topics, subscriptions, and Heracles routes |
| `cdd-templates/` | CCE deployment descriptors with zone-specific values |

## Atropos Topics

| Topic | Publisher | Event |
|-------|-----------|-------|
| `_system_0_ride-requested` | ride-service | New ride created |
| `_system_0_ride-cancelled` | ride-service | Ride cancelled |
| `_system_0_ride-completed` | ride-service | Ride completed |
| `_system_0_driver-assigned` | driver-service | Driver assigned to ride |
| `_system_0_no-driver-available` | driver-service | No driver found 

## Atropos Subscriptions

| Subscription | Topic | Consumer | Webhook |
|--------------|-------|----------|---------|
| `ride-requested-to-driver` | ride-requested | driver-service | `/api/drivers/events/ride-requested/webhook` |
| `driver-assigned-to-notification` | driver-assigned | notification-service | `/api/notifications/events/driver-assigned/webhook` |
| `ride-cancelled-to-notification` | ride-cancelled | notification-service | `/api/notifications/events/ride-cancelled/webhook` |
| `ride-completed-to-notification` | ride-completed | notification-service | `/api/notifications/events/ride-completed/webhook` |

## Event Flow

```
ride-service
    ├── RIDE_REQUESTED ──→ driver-service (webhook)
    │                          ├── DRIVER_ASSIGNED ──→ notification-service (webhook)
    │                          └── NO_DRIVER_AVAILABLE
    ├── RIDE_CANCELLED ──→ notification-service (webhook)
    └── RIDE_COMPLETED ──→ notification-servic (webhook
```

## Heracles Routes

| Service | Internal Port | Route Path |
|---------|--------------|------------|
| ride-service | 8081 | `/ride-service/**` |
| driver-service | 8082 | `/driver-service/**` |
| notification-service | 8083 | `/notification-service/**` |

## Deployment

This cluster-spec is deployed via CDD (Continuous Deployment Descriptor) to CCE zones. The `zone-values/` directory contains environment-specific overrides.

## CI/CD

- Jenkins pipeline: `ci.Jenkinsfile`
- Publishes Helm chart to Artifactory
- CDD auto-deploys on merge to master

## Related Repos

- [ride-service](../ride-service) — Ride lifecycle management
- [driver-service](../driver-service) — Driver management & matching
- [notification-service](../notification-service) — User notifications
