# FleetOps Core Service

Core domain service for FleetOps. Manages vehicles, users, trip requests, mileage logs, and maintenance flags.

Base URL: `http://localhost:8080`

All protected endpoints require a Bearer token:
```
Authorization: Bearer <token>
```

Swagger UI (interactive docs): `http://localhost:8080/swagger-ui/index.html`

---

## Table of Contents

- [Auth](#auth)
- [Users](#users)
- [Vehicles](#vehicles)
- [Trip Requests](#trip-requests)
- [Mileage Logs](#mileage-logs)
- [Maintenance Flags](#maintenance-flags)
- [Vehicle Assignments](#vehicle-assignments)
- [Admin Reports](#admin-reports)
- [Quick-Start Flow](#quick-start-flow)

---

## Auth

### `POST /api/auth/login`
**Role:** Public

**Sample 1 — Admin login**
```json
{
  "email": "admin@fleetops.com",
  "password": "Admin@1234"
}
```

**Sample 2 — Field staff login**
```json
{
  "email": "john.driver@fleetops.com",
  "password": "Staff@5678"
}
```

**Response**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "admin@fleetops.com",
  "role": "ADMIN"
}
```

---

## Users

### `POST /api/admin/users`
**Role:** `ADMIN`

**Sample 1 — Create a Field Staff**
```json
{
  "name": "John Adeyemi",
  "email": "john.adeyemi@fleetops.com",
  "password": "Staff@1234",
  "role": "FIELD_STAFF"
}
```

**Sample 2 — Create a Fleet Manager**
```json
{
  "name": "Sarah Okonkwo",
  "email": "sarah.okonkwo@fleetops.com",
  "password": "Manager@5678",
  "role": "FLEET_MANAGER"
}
```

> Available roles: `FIELD_STAFF` · `FLEET_MANAGER` · `MAINTENANCE_TEAM` · `ADMIN`

---

### `GET /api/admin/users`
**Role:** `ADMIN`

```
GET /api/admin/users
Authorization: Bearer <token>
```

---

### `GET /api/admin/users/{id}`
**Role:** `ADMIN`

**Sample 1**
```
GET /api/admin/users/1
```

**Sample 2**
```
GET /api/admin/users/5
```

---

## Vehicles

### `POST /api/vehicles`
**Role:** `FLEET_MANAGER`

**Sample 1 — Default 5,000 km milestone**
```json
{
  "make": "Toyota",
  "model": "Land Cruiser",
  "plateNumber": "LG-245-KJA"
}
```

**Sample 2 — Custom 3,000 km milestone**
```json
{
  "make": "Ford",
  "model": "Ranger",
  "plateNumber": "ABJ-112-FCT",
  "milestoneInterval": 3000
}
```

---

### `GET /api/vehicles`
**Role:** `FLEET_MANAGER`, `ADMIN`

```
GET /api/vehicles
Authorization: Bearer <token>
```

---

### `GET /api/vehicles/available`
**Role:** `FIELD_STAFF`, `FLEET_MANAGER`, `ADMIN`

```
GET /api/vehicles/available
Authorization: Bearer <token>
```

---

### `GET /api/vehicles/{id}`
**Role:** `FLEET_MANAGER`, `ADMIN`

**Sample 1**
```
GET /api/vehicles/1
```

**Sample 2**
```
GET /api/vehicles/4
```

---

### `PATCH /api/vehicles/{id}/milestone-interval`
**Role:** `FLEET_MANAGER`

Updates the mileage threshold that triggers a maintenance flag for a vehicle.
The new interval takes effect on the next mileage log submission.

**Sample 1 — Set to 3,000 km**
```json
{
  "milestoneInterval": 3000
}
```

**Sample 2 — Reset to default 5,000 km**
```json
{
  "milestoneInterval": 5000
}
```

---

### `PATCH /api/vehicles/{id}/service-history`
**Role:** `FLEET_MANAGER`

**Sample 1 — Log an oil change**
```json
{
  "serviceHistory": "Oil and filter changed at 10,000 km on 2025-04-15. Next service due at 15,000 km."
}
```

**Sample 2 — Log a full inspection**
```json
{
  "serviceHistory": "Full inspection on 2025-05-01. Brake pads replaced, tyres rotated. Cleared for operation."
}
```

---

## Trip Requests

### `POST /api/trip-requests`
**Role:** `FIELD_STAFF`

**Sample 1**
```json
{
  "vehicleId": 2,
  "destination": "Lagos Island",
  "startDate": "2025-06-10",
  "endDate": "2025-06-12"
}
```

**Sample 2**
```json
{
  "vehicleId": 5,
  "destination": "Abuja Central Depot",
  "startDate": "2025-06-20",
  "endDate": "2025-06-23"
}
```

---

### `GET /api/trip-requests`
**Role:** `FLEET_MANAGER` — returns `PENDING` requests only

```
GET /api/trip-requests
Authorization: Bearer <token>
```

---

### `GET /api/trip-requests/all`
**Role:** `FLEET_MANAGER`, `ADMIN` — returns all requests across all statuses

```
GET /api/trip-requests/all
Authorization: Bearer <token>
```

---

### `GET /api/trip-requests/my`
**Role:** `FIELD_STAFF` — returns the authenticated user's own requests

```
GET /api/trip-requests/my
Authorization: Bearer <token>
```

---

### `PATCH /api/trip-requests/{id}/approve`
**Role:** `FLEET_MANAGER`

**Sample 1**
```
PATCH /api/trip-requests/3/approve
Authorization: Bearer <token>
```

**Sample 2**
```
PATCH /api/trip-requests/7/approve
Authorization: Bearer <token>
```

> Approving a request creates a `VehicleAssignment` and sets the vehicle status to `ASSIGNED`.

---

### `PATCH /api/trip-requests/{id}/reject`
**Role:** `FLEET_MANAGER`

**Sample 1**
```
PATCH /api/trip-requests/4/reject
Authorization: Bearer <token>
```

**Sample 2**
```
PATCH /api/trip-requests/9/reject
Authorization: Bearer <token>
```

---

### `PATCH /api/trip-requests/{id}/complete`
**Role:** `FLEET_MANAGER`

**Sample 1**
```
PATCH /api/trip-requests/3/complete
Authorization: Bearer <token>
```

**Sample 2**
```
PATCH /api/trip-requests/7/complete
Authorization: Bearer <token>
```

> Completing a trip sets the vehicle status back to `AVAILABLE`.

---

## Mileage Logs

### `POST /api/mileage-logs`
**Role:** `FIELD_STAFF`

**Sample 1 — Routine post-trip update**
```json
{
  "vehicleId": 2,
  "mileageAdded": 320.5
}
```

**Sample 2 — Long-distance trip**
```json
{
  "vehicleId": 5,
  "mileageAdded": 1450.0
}
```

> If this submission causes the vehicle to cross a mileage milestone (e.g. every 5,000 km), a
> `MaintenanceFlagCreatedEvent` is published to Kafka. The consumer creates a maintenance flag
> and notifies the fleet manager automatically.

---

### `GET /api/mileage-logs/vehicle/{vehicleId}`
**Role:** `FLEET_MANAGER`, `ADMIN`

**Sample 1**
```
GET /api/mileage-logs/vehicle/2
Authorization: Bearer <token>
```

**Sample 2**
```
GET /api/mileage-logs/vehicle/5
Authorization: Bearer <token>
```

---

## Maintenance Flags

### `GET /api/maintenance-flags`
**Role:** `FLEET_MANAGER`, `ADMIN`

```
GET /api/maintenance-flags
Authorization: Bearer <token>
```

---

### `GET /api/maintenance-flags/my`
**Role:** `MAINTENANCE_TEAM` — returns flags assigned to the current user

```
GET /api/maintenance-flags/my
Authorization: Bearer <token>
```

---

### `PATCH /api/maintenance-flags/{id}/assign`
**Role:** `FLEET_MANAGER`

**Sample 1**
```json
{
  "maintenanceTeamUserId": 4
}
```

**Sample 2**
```json
{
  "maintenanceTeamUserId": 7
}
```

---

### `PATCH /api/maintenance-flags/{id}/progress`
**Role:** `MAINTENANCE_TEAM`

**Sample 1 — Initial update**
```json
{
  "progressNotes": "Vehicle inspected. Engine oil and filter replaced. Awaiting brake pad delivery before completing service."
}
```

**Sample 2 — Follow-up update**
```json
{
  "progressNotes": "Brake pads replaced. Final checks in progress. Vehicle expected ready by end of day."
}
```

---

### `PATCH /api/maintenance-flags/{id}/resolve`
**Role:** `MAINTENANCE_TEAM`

**Sample 1**
```
PATCH /api/maintenance-flags/1/resolve
Authorization: Bearer <token>
```

**Sample 2**
```
PATCH /api/maintenance-flags/6/resolve
Authorization: Bearer <token>
```

> Resolving a flag automatically sets the vehicle status back to `AVAILABLE` and notifies the fleet manager.

---

## Vehicle Assignments

### `GET /api/assignments/vehicle/{vehicleId}`
**Role:** `FLEET_MANAGER`, `ADMIN`

**Sample 1**
```
GET /api/assignments/vehicle/2
Authorization: Bearer <token>
```

**Sample 2**
```
GET /api/assignments/vehicle/5
Authorization: Bearer <token>
```

---

## Admin Reports

### `GET /api/admin/reports/utilisation`
**Role:** `ADMIN`

```
GET /api/admin/reports/utilisation
Authorization: Bearer <token>
```

**Sample Response**
```json
{
  "totalVehicles": 10,
  "availableVehicles": 6,
  "assignedVehicles": 2,
  "maintenanceVehicles": 2,
  "totalTripsAllTime": 47,
  "pendingTripRequests": 3
}
```

---

### `GET /api/admin/reports/vehicle-health`
**Role:** `ADMIN`, `FLEET_MANAGER`

```
GET /api/admin/reports/vehicle-health
Authorization: Bearer <token>
```

**Sample Response**
```json
[
  {
    "vehicleId": 1,
    "plateNumber": "LG-245-KJA",
    "make": "Toyota",
    "model": "Land Cruiser",
    "currentMileage": 12450.0,
    "milestoneInterval": 5000.0,
    "status": "AVAILABLE",
    "openMaintenanceFlags": 0
  },
  {
    "vehicleId": 3,
    "plateNumber": "ABJ-112-FCT",
    "make": "Ford",
    "model": "Ranger",
    "currentMileage": 9800.0,
    "milestoneInterval": 3000.0,
    "status": "MAINTENANCE",
    "openMaintenanceFlags": 1
  }
]
```

---

## Quick-Start Flow

```
 1. Login as ADMIN           POST /api/auth/login
 2. Create users             POST /api/admin/users          (one per role needed)
 3. Login as FLEET_MANAGER   POST /api/auth/login
 4. Register vehicles        POST /api/vehicles
 5. Login as FIELD_STAFF     POST /api/auth/login
 6. Browse available         GET  /api/vehicles/available
 7. Submit trip request      POST /api/trip-requests
 8. Login as FLEET_MANAGER   POST /api/auth/login
 9. Approve trip             PATCH /api/trip-requests/{id}/approve
10. Login as FIELD_STAFF     POST /api/auth/login
11. Submit mileage log       POST /api/mileage-logs
    └─ if milestone crossed → maintenance flag auto-created via Kafka
12. Login as FLEET_MANAGER   POST /api/auth/login
13. Complete trip            PATCH /api/trip-requests/{id}/complete
14. Assign maintenance flag  PATCH /api/maintenance-flags/{id}/assign
15. Login as MAINTENANCE     POST /api/auth/login
16. Update progress          PATCH /api/maintenance-flags/{id}/progress
17. Resolve flag             PATCH /api/maintenance-flags/{id}/resolve
    └─ vehicle returns to AVAILABLE automatically
```
