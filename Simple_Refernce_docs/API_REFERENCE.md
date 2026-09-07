# Ride Booking Platform - API Reference

All services are running locally with PostgreSQL 16 backend. Database: `ride_booking_db`

---

## 🚗 Ride Service (Port 8080)

**Base URL:** `http://localhost:8080/api`

### 1. Create Ride
- **Endpoint:** `POST /rides`
- **Description:** Create a new ride request
- **Request Body:**
  ```json
  {
    "riderId": "rider-001",
    "pickupLocation": "Airport Terminal 3",
    "dropLocation": "Downtown Hotel"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "rideId": "550e8400-e29b-41d4-a716-446655440000",
    "riderId": "rider-001",
    "driverId": null,
    "pickupLocation": "Airport Terminal 3",
    "dropLocation": "Downtown Hotel",
    "status": "REQUESTED",
    "rating": null,
    "feedback": null,
    "createdAt": "2026-05-03T13:23:32.129943",
    "updatedAt": "2026-05-03T13:23:32.129962"
  }
  ```
- **Curl:**
  ```bash
  curl -X POST http://localhost:8080/api/rides \
    -H "Content-Type: application/json" \
    -d '{"riderId":"rider-001","pickupLocation":"Airport","dropLocation":"City Center"}'
  ```

### 2. Get All Rides by Rider
- **Endpoint:** `GET /rides?riderId={riderId}`
- **Description:** Retrieve all rides for a specific rider
- **Parameters:** `riderId` (query string)
- **Response:** `200 OK` - Array of rides
- **Curl:**
  ```bash
  curl http://localhost:8080/api/rides?riderId=rider-001
  ```

### 3. Get Ride by ID
- **Endpoint:** `GET /rides/{rideId}`
- **Description:** Get detailed information about a specific ride
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000
  ```

### 4. Cancel Ride
- **Endpoint:** `POST /rides/{rideId}/cancel`
- **Description:** Cancel an existing ride
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X POST http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/cancel
  ```

### 5. Complete Ride
- **Endpoint:** `POST /rides/{rideId}/complete`
- **Description:** Mark a ride as completed
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X POST http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/complete
  ```

### 6. Rate Ride
- **Endpoint:** `PATCH /rides/{rideId}/rating`
- **Description:** Add rating and feedback to a completed ride
- **Request Body:**
  ```json
  {
    "rating": 5,
    "feedback": "Excellent service, driver was very professional!"
  }
  ```
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X PATCH http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/rating \
    -H "Content-Type: application/json" \
    -d '{"rating":5,"feedback":"Great service!"}'
  ```

---

## 👨‍💼 Driver Service (Port 8081)

**Base URL:** `http://localhost:8081/api`

### 1. Register Driver
- **Endpoint:** `POST /drivers`
- **Description:** Register a new driver
- **Request Body:**
  ```json
  {
    "name": "John Doe",
    "phone": "9876543210",
    "vehicleNumber": "ABC123",
    "vehicleType": "CAR"
  }
  ```
- **Response:** `201 Created`
  ```json
  {
    "driverId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "John Doe",
    "phone": "9876543210",
    "vehicleNumber": "ABC123",
    "vehicleType": "CAR",
    "latitude": 0.0,
    "longitude": 0.0,
    "available": true,
    "rating": 5.0,
    "createdAt": "2026-05-03T13:24:00.000000"
  }
  ```
- **Curl:**
  ```bash
  curl -X POST http://localhost:8081/api/drivers \
    -H "Content-Type: application/json" \
    -d '{"name":"John Doe","phone":"9876543210","vehicleNumber":"ABC123","vehicleType":"CAR"}'
  ```

### 2. List Available Drivers
- **Endpoint:** `GET /drivers`
- **Description:** Get all currently available drivers
- **Response:** `200 OK` - Array of available drivers
- **Curl:**
  ```bash
  curl http://localhost:8081/api/drivers
  ```

### 3. Get Driver Details
- **Endpoint:** `GET /drivers/{driverId}`
- **Description:** Get specific driver information
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000
  ```

### 4. Get Driver Stats
- **Endpoint:** `GET /drivers/{driverId}/stats`
- **Description:** Get driver performance statistics
- **Response:** `200 OK`
  ```json
  {
    "driverId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "John Doe",
    "vehicleType": "CAR",
    "rating": 4.8,
    "available": true,
    "totalAvailableCount": 5
  }
  ```
- **Curl:**
  ```bash
  curl http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/stats
  ```

### 5. Update Driver Availability
- **Endpoint:** `PATCH /drivers/{driverId}/availability`
- **Description:** Toggle driver availability status
- **Request Body:**
  ```json
  {
    "available": false
  }
  ```
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X PATCH http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/availability \
    -H "Content-Type: application/json" \
    -d '{"available":false}'
  ```

### 6. Update Driver Location
- **Endpoint:** `PATCH /drivers/{driverId}/location`
- **Description:** Update driver GPS coordinates
- **Request Body:**
  ```json
  {
    "latitude": 40.7128,
    "longitude": -74.0060
  }
  ```
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X PATCH http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/location \
    -H "Content-Type: application/json" \
    -d '{"latitude":40.7128,"longitude":-74.0060}'
  ```

---

## 🔔 Notification Service (Port 8082)

**Base URL:** `http://localhost:8082/api`

### 1. Get User Notifications
- **Endpoint:** `GET /notifications/{userId}`
- **Description:** Retrieve all notifications for a user
- **Response:** `200 OK` - Array of notifications ordered by creation date (newest first)
  ```json
  [
    {
      "notificationId": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "rider-001",
      "message": "Driver John Doe has been assigned to your ride",
      "type": "DRIVER_ASSIGNED",
      "sent": true,
      "read": false,
      "createdAt": "2026-05-03T13:24:00.000000"
    }
  ]
  ```
- **Curl:**
  ```bash
  curl http://localhost:8082/api/notifications/rider-001
  ```

### 2. Mark Notification as Read
- **Endpoint:** `PATCH /notifications/{notificationId}/read`
- **Description:** Mark a notification as read
- **Response:** `200 OK`
- **Curl:**
  ```bash
  curl -X PATCH http://localhost:8082/api/notifications/550e8400-e29b-41d4-a716-446655440000/read
  ```

---

## 🔄 Event Types

The services communicate via Atropos (Kinesis) events:

### Ride Service Events
- `RIDE_REQUESTED` - When a rider requests a new ride
- `RIDE_CANCELLED` - When a ride is cancelled
- `RIDE_COMPLETED` - When a ride is finished

### Driver Service Events
- `DRIVER_ASSIGNED` - When a driver is matched to a ride
- `NO_DRIVER_AVAILABLE` - When no drivers are available

### Notification Service Events
Consumes:
- `DRIVER_ASSIGNED` → Creates notification for rider
- `RIDE_CANCELLED` → Notifies both rider and driver
- `RIDE_COMPLETED` → Creates completion notification

---

## 📊 Entity Models

### Ride
```json
{
  "rideId": "UUID",
  "riderId": "String",
  "driverId": "String or null",
  "pickupLocation": "String",
  "dropLocation": "String",
  "status": "REQUESTED|CANCELLED|COMPLETED",
  "rating": "Integer or null (1-5)",
  "feedback": "String or null",
  "createdAt": "Timestamp",
  "updatedAt": "Timestamp"
}
```

### Driver
```json
{
  "driverId": "UUID",
  "name": "String",
  "phone": "String (unique)",
  "vehicleNumber": "String (unique)",
  "vehicleType": "String",
  "latitude": "Double",
  "longitude": "Double",
  "available": "Boolean",
  "rating": "Double (default: 5.0)",
  "createdAt": "Timestamp"
}
```

### Notification
```json
{
  "notificationId": "UUID",
  "userId": "String",
  "message": "String",
  "type": "DRIVER_ASSIGNED|RIDE_CANCELLED|RIDE_COMPLETED",
  "sent": "Boolean",
  "read": "Boolean",
  "createdAt": "Timestamp"
}
```

---

## ✅ Health Checks

Each service exposes a health endpoint:

```bash
curl http://localhost:8080/actuator/health  # Ride Service
curl http://localhost:8081/actuator/health  # Driver Service
curl http://localhost:8082/actuator/health  # Notification Service
```

---

## 🧪 Example Workflow

### Complete Flow
```bash
# 1. Register a driver
DRIVER_ID=$(curl -s -X POST http://localhost:8081/api/drivers \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","phone":"9988776655","vehicleNumber":"XYZ789","vehicleType":"SUV"}' | jq -r '.driverId')

# 2. Make driver available
curl -X PATCH http://localhost:8081/api/drivers/$DRIVER_ID/availability \
  -H "Content-Type: application/json" \
  -d '{"available":true}'

# 3. Request a ride
RIDE_ID=$(curl -s -X POST http://localhost:8080/api/rides \
  -H "Content-Type: application/json" \
  -d '{"riderId":"rider-001","pickupLocation":"Mall","dropLocation":"Airport"}' | jq -r '.rideId')

# 4. Check ride status
curl http://localhost:8080/api/rides/$RIDE_ID

# 5. Complete the ride
curl -X POST http://localhost:8080/api/rides/$RIDE_ID/complete

# 6. Rate the ride
curl -X PATCH http://localhost:8080/api/rides/$RIDE_ID/rating \
  -H "Content-Type: application/json" \
  -d '{"rating":5,"feedback":"Excellent!"}'

# 7. Check notifications
curl http://localhost:8082/api/notifications/rider-001
```

---

## 🗄️ Database

- **Host:** localhost
- **Port:** 5432
- **Database:** ride_booking_db
- **User:** postgres
- **Password:** postgres
- **Tables:** rides, drivers, notifications (auto-created)

---

## 🚀 Services Status

| Service | Port | Status | Database |
|---------|------|--------|----------|
| Ride Service | 8080 | Running | ✅ Connected |
| Driver Service | 8081 | Running | ✅ Connected |
| Notification Service | 8082 | Running | ✅ Connected |

