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
- [User Profile](#user-profile)
- [Password Management](#password-management)
- [Vehicles](#vehicles)
- [Trip Requests](#trip-requests)
- [Mileage Logs](#mileage-logs)
- [Maintenance Flags](#maintenance-flags)
- [Maintenance Chat](#maintenance-chat)
- [Vehicle Assignments](#vehicle-assignments)
- [Vehicle Activity Dashboard](#vehicle-activity-dashboard)
- [Media Management](#media-management)
- [Admin Reports](#admin-reports)
- [Email Notifications](#email-notifications)
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

Returns all users including deactivated ones. Each user object includes an `active` field indicating account status.

---

### `GET /api/admin/users/{id}`
**Role:** `ADMIN`

---

### `PATCH /api/admin/users/{id}/deactivate`
**Role:** `ADMIN`

Soft-deletes a user account. The user record is retained in the database but the account is blocked from logging in.

- Returns `204 No Content` on success
- Returns `404` if the user does not exist
- Returns `409 Conflict` if the account is already deactivated

**Response:** `204 No Content`

---

### `PATCH /api/admin/users/{id}/reactivate`
**Role:** `ADMIN`

Restores a previously deactivated account.

- Returns `204 No Content` on success
- Returns `404` if the user does not exist
- Returns `409 Conflict` if the account is already active

**Response:** `204 No Content`

> Deactivated users attempting to log in receive `401 Unauthorized` with the message `"Account is deactivated. Please contact an administrator."`

---

## User Profile

Any authenticated user can view and update their own profile without going through an admin endpoint.

### `GET /api/users/me`
**Role:** Any authenticated user

Returns the authenticated user's own profile information.

---

### `PATCH /api/users/me`
**Role:** Any authenticated user

Updates the authenticated user's display name. Email address cannot be changed via this endpoint.

```json
{
  "name": "John Adeyemi Jr."
}
```

---

### `PATCH /api/users/me/media`
**Role:** Any authenticated user

Sets or replaces the authenticated user's profile picture (Cloudinary-hosted). Replaces any existing entry.

```json
{
  "publicId": "fleetops/users/profile-123",
  "url": "https://res.cloudinary.com/demo/image/upload/v1/fleetops/users/profile-123.jpg"
}
```

**Response**
```json
{
  "id": 5,
  "publicId": "fleetops/users/profile-123",
  "url": "https://res.cloudinary.com/demo/image/upload/v1/fleetops/users/profile-123.jpg"
}
```

---

### `DELETE /api/users/me/media`
**Role:** Any authenticated user

Removes the authenticated user's profile picture. Returns `409 Conflict` if no profile media is currently set.

**Response:** `204 No Content`

---

## Vehicles

Each vehicle has a **milestone interval** — the odometer reading (km) at which a maintenance flag is automatically raised. The default is **3,000 km** (configurable via `DEFAULT_MILESTONE_INTERVAL` env var). This can be overridden per vehicle at creation time or updated later.

Service history is recorded automatically when the fleet manager approves a completed maintenance. See [Maintenance Flags](#maintenance-flags).

### Plate Number Format

Plate numbers follow the **Nigerian private vehicle standard**:

```
KJA-245BX
^^^         — 3-letter LGA registration code (e.g. KJA = Ikeja, Lagos)
    ^^^     — 3-digit sequence number (001–999)
       ^^   — 2-letter suffix
```

**Validation rules:**
- Input is trimmed and converted to uppercase automatically
- The 3-letter prefix must be a recognised LGA code (seeded from `nigeria_plate_codes.csv` on startup)
- Sequence number must be between `001` and `999` — `000` is rejected
- Returns `400 Bad Request` if the format is invalid or the LGA prefix is unrecognised
- Returns `409 Conflict` if the plate number is already registered

---

### `POST /api/vehicles`
**Role:** `FLEET_MANAGER`, `ADMIN`

**Sample 1 — Use default 3,000 km milestone**
```json
{
  "make": "Toyota",
  "model": "Land Cruiser",
  "plateNumber": "KJA-245BX"
}
```

**Sample 2 — Custom milestone**
```json
{
  "make": "Ford",
  "model": "Ranger",
  "plateNumber": "PHC-112AA",
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
  "plateNumber": "KJA-245BX",
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
**Role:** `FIELD_STAFF` — returns the authenticated user's own requests across all statuses

---

### `GET /api/trip-requests/my/approved`
**Role:** `FIELD_STAFF` — returns only the authenticated user's `APPROVED` trips (i.e. the vehicle(s) currently assigned to them)

---

### `PATCH /api/trip-requests/{id}/approve`
**Role:** `FLEET_MANAGER`

Approves a `PENDING` trip request. Creates a `VehicleAssignment`, sets the vehicle status to `ASSIGNED`, and auto-rejects conflicting pending requests for the same vehicle.

---

### `PATCH /api/trip-requests/{id}/reject`
**Role:** `FLEET_MANAGER`

---

### `PATCH /api/trip-requests/{id}/complete`
**Role:** `FIELD_STAFF` (own trip only) · `FLEET_MANAGER` · `ADMIN`

Marks an `APPROVED` trip as completed. Sets the vehicle status back to `AVAILABLE`.

- A field staff member can only complete **their own** trip — returns `403 Forbidden` if they attempt to complete another staff member's trip.
- Fleet managers and admins can complete **any** approved trip, including before the `endDate` (e.g. early vehicle withdrawal).

Accepts an **optional** JSON body:
```json
{
  "reportedMileage": 4350.0
}
```

- If `reportedMileage` is supplied it must be **≥** the vehicle's currently recorded mileage — returns `409` otherwise. The vehicle's `currentMileage` is updated and a `MileageLog` entry is created in the same request.
- If the body is omitted (or `reportedMileage` is `null`), the trip completes with no mileage update; a separate `POST /api/mileage-logs` call can be used afterwards.

---

## Mileage Logs

After a trip is completed (fleet manager calls `PATCH /{id}/complete`), the field staff submits the vehicle's current **odometer reading**. This is not a per-trip delta — it is the absolute reading from the vehicle's odometer. The system sets the vehicle's `currentMileage` directly to this value.

**Mileage logging is only permitted after trip completion.** The system verifies that the submitting field staff has a `COMPLETED` trip for that vehicle before accepting the log. Attempting to log mileage without a completed trip returns `409 Conflict`.

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

## Maintenance Chat

Messages sent within a maintenance flag. The conversation is locked once the flag is `RESOLVED` — no new messages can be posted, but the history remains readable.

### `POST /api/maintenance-flags/{flagId}/messages`
**Role:** `MAINTENANCE_TEAM`, `FLEET_MANAGER`, `ADMIN`

Sends a message to the flag conversation. Returns `409 Conflict` if the flag is `RESOLVED`.

```json
{
  "message": "Brake pads have arrived. Starting installation now."
}
```

**Response (201 Created)**
```json
{
  "id": 3,
  "flagId": 7,
  "senderId": 5,
  "senderName": "Chidi Nwosu",
  "senderRole": "MAINTENANCE_TEAM",
  "message": "Brake pads have arrived. Starting installation now.",
  "sentAt": "2026-05-10T11:23:00"
}
```

---

### `GET /api/maintenance-flags/{flagId}/messages`
**Role:** `MAINTENANCE_TEAM`, `FLEET_MANAGER`, `ADMIN`

Returns all messages for a flag ordered oldest → newest. Works for both active and `RESOLVED` flags.

---

## Vehicle Assignments

### `GET /api/assignments/vehicle/{vehicleId}`
**Role:** `FLEET_MANAGER`, `ADMIN`

Returns the assignment history for a vehicle.

---

## Vehicle Activity Dashboard

### `GET /api/admin/activity-logs`
**Role:** `ADMIN`

Returns vehicle activity events newest first. Supports optional query parameters.

| Parameter | Type | Description |
|---|---|---|
| `plateNumber` | string | Filter by vehicle plate number |
| `date` | `YYYY-MM-DD` | Filter to a single calendar day |

Both can be combined: `?plateNumber=KJA-001AB&date=2026-05-10`

**Sample Response**
```json
[
  {
    "id": 12,
    "vehicleId": 2,
    "plateNumber": "KJA-001AB",
    "eventType": "TRIP_REQUESTED",
    "description": "Emeka Obi (FIELD_STAFF) requested vehicle KJA-001AB for destination: Abuja (12 May – 15 May)",
    "actorName": "Emeka Obi",
    "actorRole": "FIELD_STAFF",
    "occurredAt": "2026-05-10T09:14:00"
  }
]
```

**Events logged:**

| Event type | Triggered by |
|---|---|
| `TRIP_REQUESTED` | Field staff submits a trip request |
| `TRIP_APPROVED` | Fleet manager approves a trip |
| `TRIP_REJECTED` | Manual or auto-conflict rejection |
| `MILEAGE_SUBMITTED` | Field staff or fleet manager submits odometer reading |
| `MAINTENANCE_SCHEDULED` | System — mileage milestone crossed |
| `MAINTENANCE_COMPLETED` | Maintenance team marks work done |
| `MILESTONE_UPDATED` | Fleet manager approves maintenance + sets new interval |

---

## Media Management

Admin can manage any user's profile picture. Fleet managers and admins can manage vehicle photos.

### `PATCH /api/admin/users/{id}/media`
**Role:** `ADMIN`

Sets or replaces the profile picture for any user.

```json
{
  "publicId": "fleetops/users/profile-456",
  "url": "https://res.cloudinary.com/demo/image/upload/v1/fleetops/users/profile-456.jpg"
}
```

---

### `DELETE /api/admin/users/{id}/media`
**Role:** `ADMIN`

Removes a user's profile picture. Returns `409 Conflict` if no media is set.

**Response:** `204 No Content`

---

### `POST /api/vehicles/{id}/media`
**Role:** `FLEET_MANAGER`, `ADMIN`

Adds one or more photos to a vehicle. Appends to any existing photos.

```json
[
  {
    "publicId": "fleetops/vehicles/v2-front",
    "url": "https://res.cloudinary.com/demo/image/upload/v1/fleetops/vehicles/v2-front.jpg"
  },
  {
    "publicId": "fleetops/vehicles/v2-side",
    "url": "https://res.cloudinary.com/demo/image/upload/v1/fleetops/vehicles/v2-side.jpg"
  }
]
```

---

### `DELETE /api/vehicles/{id}/media/{mediaId}`
**Role:** `FLEET_MANAGER`, `ADMIN`

Removes a specific photo from a vehicle by its media ID. Returns `404` if the media entry is not found on that vehicle.

**Response:** `204 No Content`

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
    "plateNumber": "KJA-245BX",
    "make": "Toyota",
    "model": "Land Cruiser",
    "currentMileage": 3200.0,
    "milestoneInterval": 6000.0,
    "status": "AVAILABLE",
    "openMaintenanceFlags": 0
  },
  {
    "vehicleId": 3,
    "plateNumber": "PHC-112AA",
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

## Email Notifications

All notifications are sent **asynchronously** via Kafka and do not block the primary API response.

| Event                                            | Recipient                                      |
|--------------------------------------------------|------------------------------------------------|
| Account created                                  | Newly registered user (welcome email)          |
| Trip request submitted                           | All fleet managers                             |
| Trip request approved                            | Field staff who submitted                      |
| Trip request rejected (manual or auto-conflict)  | Field staff who submitted                      |
| Maintenance flag assigned                        | Maintenance team member assigned               |
| Maintenance progress update                      | Fleet manager who assigned the flag            |
| Maintenance work marked done                     | Fleet manager who assigned the flag            |
| Maintenance approved                             | Maintenance team member who did the work       |
| Vehicle mileage milestone reached                | All fleet managers                             |

---

## Quick-Start Flow

```
 1.  Login as ADMIN              POST /api/auth/login
 2.  Create users                POST /api/admin/users              (one per role)
     └─ reset any password       PATCH /api/admin/users/{id}/reset-password
 3a. (Optional) Change own pwd  PATCH /api/auth/change-password
 3.  Login as FLEET_MANAGER      POST /api/auth/login
 4.  Register vehicles           POST /api/vehicles
 5.  Login as FIELD_STAFF        POST /api/auth/login
 6.  Browse available vehicles   GET  /api/vehicles/available
 7.  Submit trip request         POST /api/trip-requests
 8.  Login as FLEET_MANAGER      POST /api/auth/login
 9.  Approve trip                PATCH /api/trip-requests/{id}/approve
10.  Complete trip               PATCH /api/trip-requests/{id}/complete
     └─ optionally include { "reportedMileage": ... } to capture odometer reading inline (skips step 11)
     └─ FLEET_MANAGER / ADMIN can complete any trip; FIELD_STAFF can complete their own
11.  Login as FIELD_STAFF        POST /api/auth/login
12.  Submit mileage log          POST /api/mileage-logs             (if not submitted inline at step 10)
     └─ if milestone crossed → vehicle → MAINTENANCE, fleet manager notified via Kafka
13.  Login as FLEET_MANAGER      POST /api/auth/login
14.  Assign maintenance flag     PATCH /api/maintenance-flags/{id}/assign
15.  Login as MAINTENANCE_TEAM   POST /api/auth/login
16.  Update progress             PATCH /api/maintenance-flags/{id}/progress
17.  Mark work done              PATCH /api/maintenance-flags/{id}/done
     └─ fleet manager notified by email to approve
18.  Login as FLEET_MANAGER      POST /api/auth/login
19.  Approve maintenance         PATCH /api/maintenance-flags/{id}/approve
     └─ service history recorded, vehicle returns to AVAILABLE
```
