# Ride Booking Platform - Complete API Testing Guide

**All services running locally:**
- 🚗 Ride Service: `http://localhost:8080`
- 👨‍💼 Driver Service: `http://localhost:8081`
- 🔔 Notification Service: `http://localhost:8082`

---

## 🚗 RIDE SERVICE (Port 8080)

### 1️⃣ CREATE RIDE
**Endpoint:** `POST /api/rides`  
**Purpose:** Create a new ride request

**Request:**
```bash
curl -X POST http://localhost:8080/api/rides \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "rider-001",
    "pickupLocation": "Airport Terminal 3",
    "dropLocation": "Downtown Hotel"
  }'
```

**Request Body:**
```json
{
  "riderId": "rider-001",
  "pickupLocation": "Airport Terminal 3",
  "dropLocation": "Downtown Hotel"
}
```

**Expected Response:** `201 Created`
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

**Key Fields:**
- `rideId`: Generated UUID
- `status`: Always starts as "REQUESTED"
- `driverId`: null until driver assigned
- `rating`, `feedback`: null until ride completed and rated

---

### 2️⃣ GET RIDES BY RIDER
**Endpoint:** `GET /api/rides?riderId={riderId}`  
**Purpose:** Get all rides for a specific rider

**Request:**
```bash
curl http://localhost:8080/api/rides?riderId=rider-001
```

**Query Parameters:**
- `riderId` (required): String - ID of the rider

**Expected Response:** `200 OK`
```json
[
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
    "updatedAt": "2026-05-03T13:23:32.129943"
  },
  {
    "rideId": "660e8400-e29b-41d4-a716-446655440001",
    "riderId": "rider-001",
    "driverId": "driver-123",
    "pickupLocation": "Mall",
    "dropLocation": "Airport",
    "status": "COMPLETED",
    "rating": 5,
    "feedback": "Great driver!",
    "createdAt": "2026-05-02T10:00:00.000000",
    "updatedAt": "2026-05-02T11:30:00.000000"
  }
]
```

**Expected Behaviors:**
- Empty array `[]` if no rides exist for rider
- Array sorted by creation date

---

### 3️⃣ GET RIDE BY ID
**Endpoint:** `GET /api/rides/{rideId}`  
**Purpose:** Get detailed information about a specific ride

**Request:**
```bash
curl http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000
```

**Path Parameters:**
- `rideId` (required): String (UUID) - ID of the ride

**Expected Response:** `200 OK`
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
  "updatedAt": "2026-05-03T13:23:32.129943"
}
```

**Error Response:** `404 Not Found`
```json
{
  "message": "Ride not found with ID: invalid-id",
  "timestamp": "2026-05-03T13:25:00.000000",
  "status": 404
}
```

---

### 4️⃣ CANCEL RIDE
**Endpoint:** `POST /api/rides/{rideId}/cancel`  
**Purpose:** Cancel an existing ride

**Request:**
```bash
curl -X POST http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/cancel
```

**Path Parameters:**
- `rideId` (required): String (UUID) - ID of the ride to cancel

**Request Body:** Empty (no body required)

**Expected Response:** `200 OK`
```json
{
  "rideId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "rider-001",
  "driverId": null,
  "pickupLocation": "Airport Terminal 3",
  "dropLocation": "Downtown Hotel",
  "status": "CANCELLED",
  "rating": null,
  "feedback": null,
  "createdAt": "2026-05-03T13:23:32.129943",
  "updatedAt": "2026-05-03T13:25:00.000000"
}
```

**Key Change:**
- `status` changes from "REQUESTED" to "CANCELLED"
- `updatedAt` timestamp updated

**Error Scenarios:**
- Cannot cancel already cancelled ride (400 Bad Request)
- Cannot cancel completed ride (400 Bad Request)

---

### 5️⃣ COMPLETE RIDE
**Endpoint:** `POST /api/rides/{rideId}/complete`  
**Purpose:** Mark a ride as completed

**Request:**
```bash
curl -X POST http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/complete
```

**Path Parameters:**
- `rideId` (required): String (UUID) - ID of the ride to complete

**Request Body:** Empty (no body required)

**Expected Response:** `200 OK`
```json
{
  "rideId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "rider-001",
  "driverId": "driver-123",
  "pickupLocation": "Airport Terminal 3",
  "dropLocation": "Downtown Hotel",
  "status": "COMPLETED",
  "rating": null,
  "feedback": null,
  "createdAt": "2026-05-03T13:23:32.129943",
  "updatedAt": "2026-05-03T13:26:00.000000"
}
```

**Key Change:**
- `status` changes from "REQUESTED" to "COMPLETED"
- `updatedAt` timestamp updated
- Now ride can be rated

---

### 6️⃣ RATE RIDE
**Endpoint:** `PATCH /api/rides/{rideId}/rating`  
**Purpose:** Add rating and feedback to a completed ride

**Request:**
```bash
curl -X PATCH http://localhost:8080/api/rides/550e8400-e29b-41d4-a716-446655440000/rating \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 5,
    "feedback": "Excellent service, driver was very professional!"
  }'
```

**Path Parameters:**
- `rideId` (required): String (UUID) - ID of the ride to rate

**Request Body:**
```json
{
  "rating": 5,
  "feedback": "Excellent service, driver was very professional!"
}
```

**Expected Response:** `200 OK`
```json
{
  "rideId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "rider-001",
  "driverId": "driver-123",
  "pickupLocation": "Airport Terminal 3",
  "dropLocation": "Downtown Hotel",
  "status": "COMPLETED",
  "rating": 5,
  "feedback": "Excellent service, driver was very professional!",
  "createdAt": "2026-05-03T13:23:32.129943",
  "updatedAt": "2026-05-03T13:27:00.000000"
}
```

**Constraints:**
- Ride must be in "COMPLETED" status
- Rating must be 1-5
- Feedback is optional

---

## 👨‍💼 DRIVER SERVICE (Port 8081)

### 1️⃣ REGISTER DRIVER
**Endpoint:** `POST /api/drivers`  
**Purpose:** Register a new driver in the system

**Request:**
```bash
curl -X POST http://localhost:8081/api/drivers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "phone": "9876543210",
    "vehicleNumber": "ABC123XYZ",
    "vehicleType": "CAR"
  }'
```

**Request Body:**
```json
{
  "name": "John Doe",
  "phone": "9876543210",
  "vehicleNumber": "ABC123XYZ",
  "vehicleType": "CAR"
}
```

**Expected Response:** `201 Created`
```json
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "John Doe",
  "phone": "9876543210",
  "vehicleNumber": "ABC123XYZ",
  "vehicleType": "CAR",
  "latitude": 0.0,
  "longitude": 0.0,
  "available": true,
  "rating": 5.0,
  "createdAt": "2026-05-03T13:24:00.000000"
}
```

**Default Values:**
- `available`: true
- `rating`: 5.0
- `latitude`, `longitude`: 0.0

**Constraints:**
- `phone` must be unique (duplicate returns 400)
- `vehicleNumber` must be unique (duplicate returns 400)

**Error Response (Duplicate Phone):** `400 Bad Request`
```json
{
  "message": "Driver with phone 9876543210 already exists",
  "timestamp": "2026-05-03T13:24:10.000000",
  "status": 400
}
```

---

### 2️⃣ LIST AVAILABLE DRIVERS
**Endpoint:** `GET /api/drivers`  
**Purpose:** Get all currently available drivers

**Request:**
```bash
curl http://localhost:8081/api/drivers
```

**Query Parameters:** None

**Expected Response:** `200 OK`
```json
[
  {
    "driverId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "John Doe",
    "phone": "9876543210",
    "vehicleNumber": "ABC123XYZ",
    "vehicleType": "CAR",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "available": true,
    "rating": 4.8,
    "createdAt": "2026-05-03T13:24:00.000000"
  },
  {
    "driverId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "Jane Smith",
    "phone": "9988776655",
    "vehicleNumber": "XYZ789",
    "vehicleType": "SUV",
    "latitude": 40.7489,
    "longitude": -73.9680,
    "available": true,
    "rating": 5.0,
    "createdAt": "2026-05-03T13:25:00.000000"
  }
]
```

**Expected Behaviors:**
- Only drivers with `available: true` are returned
- Empty array `[]` if no drivers available
- Sorted by creation date

---

### 3️⃣ GET DRIVER DETAILS
**Endpoint:** `GET /api/drivers/{driverId}`  
**Purpose:** Get specific driver information

**Request:**
```bash
curl http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000
```

**Path Parameters:**
- `driverId` (required): String (UUID) - ID of the driver

**Expected Response:** `200 OK`
```json
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "John Doe",
  "phone": "9876543210",
  "vehicleNumber": "ABC123XYZ",
  "vehicleType": "CAR",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "available": true,
  "rating": 4.8,
  "createdAt": "2026-05-03T13:24:00.000000"
}
```

**Error Response:** `404 Not Found`
```json
{
  "message": "Driver not found with ID: invalid-id",
  "timestamp": "2026-05-03T13:30:00.000000",
  "status": 404
}
```

---

### 4️⃣ GET DRIVER STATS
**Endpoint:** `GET /api/drivers/{driverId}/stats`  
**Purpose:** Get driver performance statistics

**Request:**
```bash
curl http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/stats
```

**Path Parameters:**
- `driverId` (required): String (UUID) - ID of the driver

**Expected Response:** `200 OK`
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

**Key Fields:**
- `totalAvailableCount`: Count of all available drivers in system

---

### 5️⃣ UPDATE DRIVER AVAILABILITY
**Endpoint:** `PATCH /api/drivers/{driverId}/availability`  
**Purpose:** Toggle driver availability status (online/offline)

**Request (Set Unavailable):**
```bash
curl -X PATCH http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/availability \
  -H "Content-Type: application/json" \
  -d '{
    "available": false
  }'
```

**Request Body:**
```json
{
  "available": false
}
```

**Expected Response:** `200 OK`
```json
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "John Doe",
  "phone": "9876543210",
  "vehicleNumber": "ABC123XYZ",
  "vehicleType": "CAR",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "available": false,
  "rating": 4.8,
  "createdAt": "2026-05-03T13:24:00.000000"
}
```

**Toggle Back to Available:**
```bash
curl -X PATCH http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/availability \
  -H "Content-Type: application/json" \
  -d '{"available": true}'
```

---

### 6️⃣ UPDATE DRIVER LOCATION
**Endpoint:** `PATCH /api/drivers/{driverId}/location`  
**Purpose:** Update driver GPS coordinates

**Request:**
```bash
curl -X PATCH http://localhost:8081/api/drivers/550e8400-e29b-41d4-a716-446655440000/location \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 40.7128,
    "longitude": -74.0060
  }'
```

**Request Body:**
```json
{
  "latitude": 40.7128,
  "longitude": -74.0060
}
```

**Expected Response:** `200 OK`
```json
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "John Doe",
  "phone": "9876543210",
  "vehicleNumber": "ABC123XYZ",
  "vehicleType": "CAR",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "available": true,
  "rating": 4.8,
  "createdAt": "2026-05-03T13:24:00.000000"
}
```

**Key Fields:**
- `latitude`: -90 to 90
- `longitude`: -180 to 180

---

## 🔔 NOTIFICATION SERVICE (Port 8082)

### 1️⃣ GET USER NOTIFICATIONS
**Endpoint:** `GET /api/notifications/{userId}`  
**Purpose:** Retrieve all notifications for a user

**Request:**
```bash
curl http://localhost:8082/api/notifications/rider-001
```

**Path Parameters:**
- `userId` (required): String - ID of the user

**Expected Response:** `200 OK`
```json
[
  {
    "notificationId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "rider-001",
    "message": "Driver John Doe has been assigned to your ride",
    "type": "DRIVER_ASSIGNED",
    "sent": true,
    "read": false,
    "createdAt": "2026-05-03T13:24:30.000000"
  },
  {
    "notificationId": "660e8400-e29b-41d4-a716-446655440001",
    "userId": "rider-001",
    "message": "Your ride has been completed",
    "type": "RIDE_COMPLETED",
    "sent": true,
    "read": true,
    "createdAt": "2026-05-03T13:26:00.000000"
  }
]
```

**Expected Behaviors:**
- Empty array `[]` if no notifications exist
- Sorted by creation date (newest first)
- Contains both read and unread notifications

---

### 2️⃣ MARK NOTIFICATION AS READ
**Endpoint:** `PATCH /api/notifications/{notificationId}/read`  
**Purpose:** Mark a notification as read

**Request:**
```bash
curl -X PATCH http://localhost:8082/api/notifications/550e8400-e29b-41d4-a716-446655440000/read
```

**Path Parameters:**
- `notificationId` (required): String (UUID) - ID of the notification

**Request Body:** Empty (no body required)

**Expected Response:** `200 OK`
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "rider-001",
  "message": "Driver John Doe has been assigned to your ride",
  "type": "DRIVER_ASSIGNED",
  "sent": true,
  "read": true,
  "createdAt": "2026-05-03T13:24:30.000000"
}
```

**Key Change:**
- `read` changes from false to true

---

## 📋 COMPLETE TEST FLOW (End-to-End)

### Step 1: Register a Driver
```bash
# Register Driver
DRIVER_RESPONSE=$(curl -s -X POST http://localhost:8081/api/drivers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Johnson",
    "phone": "9999888877",
    "vehicleNumber": "CAR001",
    "vehicleType": "CAR"
  }')

DRIVER_ID=$(echo $DRIVER_RESPONSE | jq -r '.driverId')
echo "Driver ID: $DRIVER_ID"
```

### Step 2: Verify Driver is Available
```bash
curl http://localhost:8081/api/drivers
```

### Step 3: Request a Ride
```bash
# Create Ride
RIDE_RESPONSE=$(curl -s -X POST http://localhost:8080/api/rides \
  -H "Content-Type: application/json" \
  -d '{
    "riderId": "rider-john",
    "pickupLocation": "123 Main St",
    "dropLocation": "456 Oak Ave"
  }')

RIDE_ID=$(echo $RIDE_RESPONSE | jq -r '.rideId')
echo "Ride ID: $RIDE_ID"
```

### Step 4: View Ride Details
```bash
curl http://localhost:8080/api/rides/$RIDE_ID
```

### Step 5: Get Rider's All Rides
```bash
curl 'http://localhost:8080/api/rides?riderId=rider-john'
```

### Step 6: Complete the Ride
```bash
curl -X POST http://localhost:8080/api/rides/$RIDE_ID/complete
```

### Step 7: Rate the Ride
```bash
curl -X PATCH http://localhost:8080/api/rides/$RIDE_ID/rating \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 5,
    "feedback": "Excellent driver!"
  }'
```

### Step 8: Check Notifications
```bash
curl http://localhost:8082/api/notifications/rider-john
```

### Step 9: Mark Notification as Read
```bash
NOTIFICATION_ID=$(curl -s http://localhost:8082/api/notifications/rider-john | jq -r '.[0].notificationId')

curl -X PATCH http://localhost:8082/api/notifications/$NOTIFICATION_ID/read
```

---

## ✅ Response Status Codes Reference

| Code | Meaning |
|------|---------|
| **200** | OK - Request successful |
| **201** | Created - Resource created successfully |
| **400** | Bad Request - Invalid input |
| **404** | Not Found - Resource doesn't exist |
| **500** | Internal Server Error |

---

## 🧪 Quick Test Commands

**Create everything quickly:**
```bash
# Register driver
DRIVER=$(curl -s -X POST http://localhost:8081/api/drivers \
  -H "Content-Type: application/json" \
  -d '{"name":"Quick Driver","phone":"5555555555","vehicleNumber":"QUICK1","vehicleType":"CAR"}' | jq -r '.driverId')

# Create ride
RIDE=$(curl -s -X POST http://localhost:8080/api/rides \
  -H "Content-Type: application/json" \
  -d '{"riderId":"quick-test","pickupLocation":"Start","dropLocation":"End"}' | jq -r '.rideId')

# Complete ride
curl -X POST http://localhost:8080/api/rides/$RIDE/complete

# Rate ride
curl -X PATCH http://localhost:8080/api/rides/$RIDE/rating \
  -H "Content-Type: application/json" \
  -d '{"rating":5,"feedback":"Perfect!"}'

# Check all
echo "Driver: $DRIVER"
echo "Ride: $RIDE"
echo "Ride Details:" && curl http://localhost:8080/api/rides/$RIDE
echo "Notifications:" && curl http://localhost:8082/api/notifications/quick-test
```

