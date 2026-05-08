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
- [Password Management](#password-management)
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

**Errors:** Returns `401` for wrong email or password (not 500).

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

## Password Management

### `PATCH /api/auth/change-password`
**Role:** Any authenticated user

The user must supply their current password for verification. Returns `401` if the current password is wrong.

```json
{
  "currentPassword": "OldPass@123",
  "newPassword": "NewPass@456"
}
```

**Response:** `204 No Content`

---

### `PATCH /api/admin/users/{id}/reset-password`
**Role:** `ADMIN`

Hard-sets any user's password without requiring the current one. Use this for account recovery.

**Sample 1**
```json
{
  "newPassword": "Reset@1234"
}
```

**Sample 2**
```json
{
  "newPassword": "Recover@5678"
}
```

**Response:** `204 No Content`. Returns `404` if the user ID does not exist.

---

## Users

### `POST /api/admin/users`
**Role:** `ADMIN`

> Available roles: `FIELD_STAFF` · `FLEET_MANAGER` · `MAINTENANCE_TEAM` · `ADMIN`
>
> Passing an invalid role value returns `400` with a message listing the accepted values.

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

**Sample 3 — Create a Maintenance Team member**
```json
{
  "name": "Emeka Nwosu",
  "email": "emeka.nwosu@fleetops.com",
  "password": "Maint@1234",
  "role": "MAINTENANCE_TEAM"
}
```

---

### `GET /api/admin/users`
**Role:** `ADMIN`

---

### `GET /api/admin/users/{id}`
**Role:** `ADMIN`

---

## Vehicles

Each vehicle has a **milestone interval** — the odometer reading (km) at which a maintenance flag is automatically raised. The default is **3,000 km** (configurable via `DEFAULT_MILESTONE_INTERVAL` env var). This can be overridden per vehicle at creation time or updated later.

Service history is recorded automatically when the fleet manager approves a completed maintenance. See [Maintenance Flags](#maintenance-flags).

### `POST /api/vehicles`
**Role:** `FLEET_MANAGER`, `ADMIN`

**Sample 1 — Use default 3,000 km milestone**
```json
{
  "make": "Toyota",
  "model": "Land Cruiser",
  "plateNumber": "LG-245-KJA"
}
```

**Sample 2 — Custom milestone**
```json
{
  "make": "Ford",
  "model": "Ranger",
  "plateNumber": "ABJ-112-FCT",
  "milestoneInterval": 5000
}
```

---

### `GET /api/vehicles`
**Role:** `FLEET_MANAGER`, `ADMIN`

Returns all vehicles. `serviceHistories` is always an empty list on list responses — use `GET /api/vehicles/{id}` for full history.

---

### `GET /api/vehicles/available`
**Role:** `FIELD_STAFF`, `FLEET_MANAGER`, `ADMIN`

Returns vehicles with status `AVAILABLE`. Vehicles under maintenance or currently assigned are excluded.

---

### `GET /api/vehicles/{id}`
**Role:** `FLEET_MANAGER`, `ADMIN`

Returns the vehicle with its full service history (most recent first).

**Sample Response**
```json
{
  "id": 1,
  "make": "Toyota",
  "model": "Land Cruiser",
  "plateNumber": "LG-245-KJA",
  "currentMileage": 6200.0,
  "milestoneInterval": 6000.0,
  "status": "AVAILABLE",
  "serviceHistories": [
    {
      "id": 1,
      "fleetManagerName": "Sarah Okonkwo",
      "notes": "Engine oil replaced. Brake pads inspected and cleared.",
      "newMilestoneInterval": 6000.0,
      "servicedAt": "2026-04-20T14:30:00"
    }
  ],
  "registeredAt": "2026-01-10T09:00:00"
}
```

---

### `PATCH /api/vehicles/{id}/milestone-interval`
**Role:** `FLEET_MANAGER`, `ADMIN`

Manually updates the mileage threshold that triggers a maintenance flag. Takes effect on the next mileage log submission.

```json
{
  "milestoneInterval": 5000
}
```

---

## Trip Requests

A field staff member submits a trip request for a specific vehicle and date range. Rules:
- The vehicle must be `AVAILABLE`.
- The same field staff cannot have two `PENDING` requests for the same vehicle simultaneously.
- When a request is approved, all other `PENDING` requests for that vehicle whose `startDate` falls before the approved trip's `endDate` are automatically rejected.
- A cron job runs daily at midnight to auto-reject any `PENDING` requests whose `startDate` has already passed.

### `POST /api/trip-requests`
**Role:** `FIELD_STAFF`

**Sample 1**
```json
{
  "vehicleId": 2,
  "destination": "Lagos Island",
  "startDate": "2026-07-10",
  "endDate": "2026-07-12"
}
```

**Sample 2**
```json
{
  "vehicleId": 5,
  "destination": "Abuja Central Depot",
  "startDate": "2026-07-20",
  "endDate": "2026-07-23"
}
```

---

### `GET /api/trip-requests`
**Role:** `FLEET_MANAGER` — returns `PENDING` requests only

---

### `GET /api/trip-requests/all`
**Role:** `FLEET_MANAGER`, `ADMIN` — returns all requests across all statuses

---

### `GET /api/trip-requests/my`
**Role:** `FIELD_STAFF` — returns the authenticated user's own requests

---

### `PATCH /api/trip-requests/{id}/approve`
**Role:** `FLEET_MANAGER`

Approves a `PENDING` trip request. Creates a `VehicleAssignment`, sets the vehicle status to `ASSIGNED`, and auto-rejects conflicting pending requests for the same vehicle.

---

### `PATCH /api/trip-requests/{id}/reject`
**Role:** `FLEET_MANAGER`

---

### `PATCH /api/trip-requests/{id}/complete`
**Role:** `FLEET_MANAGER`

Marks an `APPROVED` trip as completed. Sets the vehicle status back to `AVAILABLE`.

---

## Mileage Logs

After a trip is completed, the field staff submits the vehicle's current **odometer reading**. This is not a per-trip delta — it is the absolute reading from the vehicle's odometer. The system sets the vehicle's `currentMileage` directly to this value.

If the new reading causes the vehicle to cross its configured `milestoneInterval`, a `MaintenanceFlagCreatedEvent` is published to Kafka. The consumer creates a maintenance flag, sets the vehicle to `MAINTENANCE` (blocking future trip requests), and notifies the fleet manager — all asynchronously.

### `POST /api/mileage-logs`
**Role:** `FIELD_STAFF`

**Sample 1 — Odometer now reads 3,200 km**
```json
{
  "vehicleId": 2,
  "reportedMileage": 3200.0
}
```

**Sample 2 — Odometer now reads 5,850 km (crosses 5,000 km milestone)**
```json
{
  "vehicleId": 5,
  "reportedMileage": 5850.0
}
```

> The reported value must be greater than or equal to the vehicle's currently recorded mileage. Submitting a lower value returns `409`.

**Response**
```json
{
  "id": 12,
  "vehicleId": 2,
  "plateNumber": "LG-245-KJA",
  "submittedById": 3,
  "submittedByName": "John Adeyemi",
  "reportedMileage": 3200.0,
  "loggedAt": "2026-05-08T10:15:00"
}
```

---

### `GET /api/mileage-logs/vehicle/{vehicleId}`
**Role:** `FLEET_MANAGER`, `ADMIN` — returns logs newest first

---

## Maintenance Flags

A maintenance flag is raised automatically when a vehicle crosses its mileage milestone. The full lifecycle is:

```
OPEN → ASSIGNED → IN_PROGRESS → PENDING_APPROVAL → RESOLVED
```

| Status | Who sets it | How |
|---|---|---|
| `OPEN` | System (Kafka consumer) | Mileage milestone crossed |
| `ASSIGNED` | Fleet manager / Admin | `PATCH /{id}/assign` |
| `IN_PROGRESS` | Maintenance team | `PATCH /{id}/progress` |
| `PENDING_APPROVAL` | Maintenance team | `PATCH /{id}/done` — notifies fleet manager by email |
| `RESOLVED` | Fleet manager / Admin | `PATCH /{id}/approve` — requires new milestone + service notes |

> A vehicle blocked by a maintenance flag cannot receive new trip requests until the flag is `RESOLVED`.

---

### `GET /api/maintenance-flags`
**Role:** `FLEET_MANAGER`, `ADMIN`

---

### `GET /api/maintenance-flags/my`
**Role:** `MAINTENANCE_TEAM` — returns flags assigned to the current user

---

### `PATCH /api/maintenance-flags/{id}/assign`
**Role:** `FLEET_MANAGER`, `ADMIN`

Assigns an `OPEN` flag to a maintenance team member. Sends them an email notification.

```json
{
  "maintenanceTeamUserId": 4
}
```

---

### `PATCH /api/maintenance-flags/{id}/progress`
**Role:** `MAINTENANCE_TEAM`

Updates progress notes and moves the flag to `IN_PROGRESS`. Notifies the assigned fleet manager.

**Sample 1 — Initial update**
```json
{
  "progressNotes": "Vehicle inspected. Engine oil and filter replaced. Awaiting brake pad delivery."
}
```

**Sample 2 — Follow-up**
```json
{
  "progressNotes": "Brake pads replaced. Final checks in progress. Vehicle expected ready by end of day."
}
```

---

### `PATCH /api/maintenance-flags/{id}/done`
**Role:** `MAINTENANCE_TEAM`

Signals that work is complete. Moves the flag to `PENDING_APPROVAL` and sends an email to the fleet manager requesting approval.

```
PATCH /api/maintenance-flags/1/done
Authorization: Bearer <token>
```

---

### `PATCH /api/maintenance-flags/{id}/approve`
**Role:** `FLEET_MANAGER`, `ADMIN`

Approves a `PENDING_APPROVAL` flag. Requires:
- `newMilestoneInterval` — must be greater than both the previous milestone interval and the vehicle's current mileage
- `serviceNotes` — description of the work done (stored as a service history record on the vehicle)

On success: creates a `ServiceHistory` record, updates the vehicle's milestone interval, sets the vehicle to `AVAILABLE`, and notifies the maintenance team member.

**Sample 1**
```json
{
  "newMilestoneInterval": 6000,
  "serviceNotes": "Full service at 3,200 km. Engine oil, oil filter, and air filter replaced. Brake pads inspected — within tolerance."
}
```

**Sample 2**
```json
{
  "newMilestoneInterval": 10000,
  "serviceNotes": "Major service at 5,850 km. Timing belt, spark plugs, and coolant replaced. All systems cleared."
}
```

---

## Vehicle Assignments

### `GET /api/assignments/vehicle/{vehicleId}`
**Role:** `FLEET_MANAGER`, `ADMIN`

Returns the assignment history for a vehicle.

---

## Admin Reports

### `GET /api/admin/reports/utilisation`
**Role:** `ADMIN`

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

**Sample Response**
```json
[
  {
    "vehicleId": 1,
    "plateNumber": "LG-245-KJA",
    "make": "Toyota",
    "model": "Land Cruiser",
    "currentMileage": 3200.0,
    "milestoneInterval": 6000.0,
    "status": "AVAILABLE",
    "openMaintenanceFlags": 0
  },
  {
    "vehicleId": 3,
    "plateNumber": "ABJ-112-FCT",
    "make": "Ford",
    "model": "Ranger",
    "currentMileage": 5850.0,
    "milestoneInterval": 5000.0,
    "status": "MAINTENANCE",
    "openMaintenanceFlags": 1
  }
]
```

---

## Quick-Start Flow

```
 1.  Login as ADMIN              POST /api/auth/login
 2.  Create users                POST /api/admin/users          (one per role)
     └─ reset any password       PATCH /api/admin/users/{id}/reset-password
 3a. (Optional) Change own pwd  PATCH /api/auth/change-password
 3.  Login as FLEET_MANAGER      POST /api/auth/login
 4.  Register vehicles           POST /api/vehicles
 5.  Login as FIELD_STAFF        POST /api/auth/login
 6.  Browse available vehicles   GET  /api/vehicles/available
 7.  Submit trip request         POST /api/trip-requests
 8.  Login as FLEET_MANAGER      POST /api/auth/login
 9.  Approve trip                PATCH /api/trip-requests/{id}/approve
10.  Login as FIELD_STAFF        POST /api/auth/login
11.  Submit mileage log          POST /api/mileage-logs          (odometer reading)
     └─ if milestone crossed → vehicle → MAINTENANCE, fleet manager notified via Kafka
12.  Login as FLEET_MANAGER      POST /api/auth/login
13.  Complete trip               PATCH /api/trip-requests/{id}/complete
14.  Assign maintenance flag     PATCH /api/maintenance-flags/{id}/assign
15.  Login as MAINTENANCE_TEAM   POST /api/auth/login
16.  Update progress             PATCH /api/maintenance-flags/{id}/progress
17.  Mark work done              PATCH /api/maintenance-flags/{id}/done
     └─ fleet manager notified by email to approve
18.  Login as FLEET_MANAGER      POST /api/auth/login
19.  Approve maintenance         PATCH /api/maintenance-flags/{id}/approve
     └─ service history recorded, vehicle returns to AVAILABLE
```
