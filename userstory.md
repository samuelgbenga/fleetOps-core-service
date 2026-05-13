# FleetOps Core Service — User Stories

Generated from product requirements and system design discussions.

---

## Table of Contents

- [Authentication](#authentication)
- [User Management](#user-management)
- [Password Management](#password-management)
- [Vehicle Management](#vehicle-management)
- [Vehicle Lifecycle](#us-032--vehicle-lifecycle-tracking)
- [Trip Requests](#trip-requests)
- [Mileage Reporting](#mileage-reporting)
- [Maintenance Management](#maintenance-management)
- [Maintenance Chat](#maintenance-chat)
- [Service History](#service-history)
- [Notifications](#notifications)
- [Vehicle Activity Dashboard](#vehicle-activity-dashboard)
- [Media Management](#media-management)
- [User Profile](#user-profile)
- [Observability](#observability)
- [Reliability](#reliability)

---

## Authentication

### US-001 — Login with valid credentials
**As a** user (any role),
**I want to** log in with my email and password,
**So that** I receive a JWT token to access the system.

**Acceptance Criteria:**
- `POST /api/auth/login` accepts `{ email, password }`
- Returns a JWT token, the user's email, and their role on success
- Returns `401 Unauthorized` with message `"Invalid email or password"` for wrong credentials
- Does **not** return `500 Internal Server Error` for authentication failures

---

## User Management

### US-002 — Create a user account
**As an** Admin,
**I want to** create user accounts and assign roles,
**So that** staff members can access the system with the correct permissions.

**Acceptance Criteria:**
- `POST /api/admin/users` accepts `{ name, email, password, role }`
- Available roles: `FIELD_STAFF`, `FLEET_MANAGER`, `MAINTENANCE_TEAM`, `ADMIN`
- Passing an invalid role value (e.g. `"_STAFF"`) returns `400 Bad Request` listing accepted values — not `500`
- Duplicate email returns `409 Conflict`
- Password is stored as a BCrypt hash, never plain text

---

### US-003 — View all users
**As an** Admin,
**I want to** retrieve a list of all registered users,
**So that** I can manage the system's user base.

**Acceptance Criteria:**
- `GET /api/admin/users` returns all users
- `GET /api/admin/users/{id}` returns a single user or `404` if not found
- Both endpoints are restricted to `ADMIN` only

---

### US-026 — Deactivate and reactivate a user account
**As an** Admin,
**I want to** deactivate or reactivate any user account,
**So that** I can revoke access without permanently deleting the user's history.

**Acceptance Criteria:**
- `PATCH /api/admin/users/{id}/deactivate` soft-deletes the account (sets `active = false`)
  - Returns `404` if the user does not exist
  - Returns `409 Conflict` if the account is already deactivated
  - Returns `204 No Content` on success
- `PATCH /api/admin/users/{id}/reactivate` restores the account (sets `active = true`)
  - Returns `404` if the user does not exist
  - Returns `409 Conflict` if the account is already active
  - Returns `204 No Content` on success
- A deactivated user attempting to log in receives `401 Unauthorized` with message `"Account is deactivated. Please contact an administrator."`
- `GET /api/admin/users` returns all users (active and inactive) with an `active` field so the admin can identify and reactivate accounts
- Only active fleet managers receive email notifications (mileage milestones, new trip requests)
- Restricted to `ADMIN` only

---

## Password Management

### US-004 — Change own password
**As an** authenticated user (any role),
**I want to** change my own password,
**So that** I can maintain my account security.

**Acceptance Criteria:**
- `PATCH /api/auth/change-password` accepts `{ currentPassword, newPassword }`
- Current password must be verified before the new one is set
- Returns `401` if the current password is incorrect
- Returns `204 No Content` on success
- New password is stored as a BCrypt hash

---

### US-005 — Admin resets any user's password
**As an** Admin,
**I want to** hard-set any user's password without requiring their current one,
**So that** I can recover locked or compromised accounts.

**Acceptance Criteria:**
- `PATCH /api/admin/users/{id}/reset-password` accepts `{ newPassword }`
- No current password verification required
- Returns `404` if the user does not exist
- Returns `204 No Content` on success
- Restricted to `ADMIN` only

---

## Vehicle Management

### US-006 — Register a vehicle
**As a** Fleet Manager or Admin,
**I want to** register a vehicle in the system,
**So that** it can be tracked, requested, and maintained.

**Acceptance Criteria:**
- `POST /api/vehicles` accepts `{ make, model, plateNumber, milestoneInterval? }`
- If `milestoneInterval` is not provided, defaults to the value in `application.yml` (`3000` km, configurable via `DEFAULT_MILESTONE_INTERVAL` env var)
- Duplicate plate number returns `409 Conflict`
- Both `FLEET_MANAGER` and `ADMIN` roles can register vehicles
- Plate number is validated per US-027 before registration

---

### US-027 — Nigerian plate number validation
**As a** Fleet Manager or Admin,
**I want** the system to validate plate numbers against the Nigerian vehicle registration standard,
**So that** only correctly formatted plates with recognised LGA codes are accepted.

**Acceptance Criteria:**
- Plate number format: `ABC-123DE` — 3-letter LGA code, hyphen, 3-digit sequence (001–999), 2-letter suffix
- Input is trimmed and uppercased automatically before validation
- The 3-letter prefix must exist in the `lga_codes` table (seeded from `nigeria_plate_codes.csv` at startup)
- Sequence number `000` is rejected; valid range is `001–999`
- Returns `400 Bad Request` if format is invalid or LGA prefix is unrecognised
- The normalised (trimmed, uppercased) value is stored in the database
- LGA codes are seeded once on startup; re-seeding is skipped if the table already has records

---

### US-007 — View vehicles
**As a** Fleet Manager or Admin,
**I want to** view all vehicles and their current status,
**So that** I can manage the fleet.

**Acceptance Criteria:**
- `GET /api/vehicles` returns all vehicles (no service histories on list responses)
- `GET /api/vehicles/available` returns only `AVAILABLE` vehicles (accessible to `FIELD_STAFF` as well)
- `GET /api/vehicles/{id}` returns the vehicle with its full service history (most recent first)
- Vehicles under `MAINTENANCE` or `ASSIGNED` do **not** appear in the available list

---

### US-032 — Vehicle lifecycle tracking
**As a** Fleet Manager or Admin,
**I want to** see a lifecycle percentage on each vehicle that reflects its cumulative wear across mileage, trips, and maintenance rounds,
**So that** I can identify vehicles approaching end-of-life and remove them from active service before they become a liability.

**Acceptance Criteria:**
- Each vehicle exposes `lifecyclePercentage` (0–100) and `markedForSale` (boolean) — visible only to `FLEET_MANAGER` and `ADMIN`
- Lifecycle is computed using a weighted formula:
  - **50%** — mileage wear: `currentMileage / maxMileage` (default max 300,000 km)
  - **25%** — qualified trips: `COMPLETED` trips with a linked mileage log ÷ `maxTrips` (default 500)
  - **25%** — maintenance rounds: resolved maintenance flags ÷ `maxMaintenanceRounds` (default 30)
- A **qualified trip** requires a mileage log submitted via `POST /api/mileage-logs` with `tripRequestId` pointing to that trip — unlinked logs do not count
- `POST /api/mileage-logs` accepts an optional `tripRequestId`; if provided it must belong to the submitting user, reference the same vehicle, and be `COMPLETED` — returns `409` otherwise
- A background job recalculates lifecycle for **all** vehicles every hour (`@Scheduled(cron = "0 0 * * * *")`)
- Lifecycle is also recalculated immediately after every mileage log submission
- When `lifecyclePercentage >= 80`:
  - Vehicle `status` is set to `OUT_OF_SERVICE`
  - `markedForSale` is set to `true`
  - Vehicle is excluded from the available pool and cannot receive new trip requests
- `maxMileage`, `maxTrips`, and `maxMaintenanceRounds` are stored per vehicle and default to 300,000 / 500 / 30 respectively — allowing per-vehicle customisation
- DB changes delivered via Flyway migration `V2__vehicle_lifecycle_fields.sql`

---

### US-008 — Update milestone interval
**As a** Fleet Manager or Admin,
**I want to** update the mileage threshold that triggers a maintenance flag for a vehicle,
**So that** I can customise the service schedule per vehicle.

**Acceptance Criteria:**
- `PATCH /api/vehicles/{id}/milestone-interval` accepts `{ milestoneInterval }` (minimum 100 km)
- Takes effect immediately on the next mileage log submission
- Returns `404` if the vehicle is not found

---

## Trip Requests

> **Design Decision — Why vehicle availability is not time-based**
>
> A reasonable question is: why does approving a trip request two months in the future
> immediately set the vehicle to `ASSIGNED` and block all other requests, rather than
> allowing the vehicle to be assigned to other trips in the intervening period?
>
> The decision was deliberate. The system relies on the **fleet manager's judgement** about
> the health and readiness of each vehicle. When a fleet manager approves a future trip,
> they are asserting that the vehicle will be fit for that trip — which implicitly means
> it should not be subjected to additional wear from other trips in the lead-up period.
> Allowing the vehicle to be assigned to intermediate trips would undermine that health
> guarantee and could leave the vehicle unfit for the originally approved journey.
>
> Time-based slot availability (where a vehicle can be re-assigned in windows between
> approved trips) is noted as a future enhancement, but it would require the fleet manager
> to actively reason about cumulative mileage and maintenance windows — a complexity
> that is out of scope for the current system design.

### US-009 — Submit a trip request
**As a** Field Staff member,
**I want to** request a vehicle for a specific destination and date range,
**So that** I can get approval to use the vehicle for my assignment.

**Acceptance Criteria:**
- `POST /api/trip-requests` accepts `{ vehicleId, destination, startDate, endDate }`
- The vehicle must have status `AVAILABLE` — returns `400` otherwise
- The same field staff member cannot have two `PENDING` requests for the same vehicle simultaneously — returns `409`
- If the vehicle has an overlapping approved assignment for the same dates — returns `409`
- Created request starts in `PENDING` status

---

### US-010 — Approve or reject a trip request
**As a** Fleet Manager or Admin,
**I want to** approve or reject pending trip requests,
**So that** vehicle usage is controlled and tracked.

**Acceptance Criteria:**
- `PATCH /api/trip-requests/{id}/approve` — approves a `PENDING` request
  - Creates a `VehicleAssignment` record
  - Sets vehicle status to `ASSIGNED`
  - Notifies the field staff member by email
  - Auto-rejects all other `PENDING` requests for the same vehicle where the approved trip's `endDate` is after their `startDate`
  - Rejected field staff members are notified by email
- `PATCH /api/trip-requests/{id}/reject` — rejects a `PENDING` request
  - Notifies the field staff member by email
- Both endpoints restricted to `FLEET_MANAGER` and `ADMIN`
- Both return `409` if the request is not in `PENDING` status

---

### US-011 — Complete a trip (with optional mileage submission)
**As a** Field Staff member or Fleet Manager,
**I want to** mark an approved trip as completed — and optionally record the odometer reading at that moment,
**So that** the vehicle is returned to the available pool and mileage can be captured in a single action.

> **Design Decision — No date gate on trip completion**
>
> The system deliberately does **not** enforce that a trip can only be completed on or after its
> `endDate`. Real-world operations rarely follow a rigid schedule — a field trip may finish early,
> conditions may change mid-journey, or a fleet manager may need to withdraw a vehicle before the
> original end date (equipment fault, re-prioritisation, etc.). Blocking completion until the
> proposed end date would force workarounds and break legitimate workflows.
>
> The `endDate` on a trip request exists to signal **intent** and to detect date conflicts during
> approval — it is not a hard lock on when completion can occur.

**Acceptance Criteria:**
- `PATCH /api/trip-requests/{id}/complete` — only works on `APPROVED` trips; returns `409` otherwise
- Accessible by **`FIELD_STAFF`**, `FLEET_MANAGER`, and `ADMIN`
  - A field staff member can only complete **their own** trip — returns `403` if they attempt to complete another staff member's trip
  - Fleet managers and admins can complete **any** approved trip (supports early vehicle withdrawal)
- No date restriction — the trip may be completed before, on, or after its `endDate`
- Sets vehicle status back to `AVAILABLE`
- Accepts an **optional** request body `{ "reportedMileage": <double> }`
  - If `reportedMileage` is provided:
    - Must be **≥** the vehicle's current recorded mileage — returns `409` if lower
    - Updates the vehicle's `currentMileage` to the reported value
    - Creates a `MileageLog` entry attributed to the caller
    - Triggers a maintenance event if the new mileage crosses the configured milestone interval
  - If omitted (body is absent or `reportedMileage` is null), the trip completes with no mileage update
- The inline mileage path bypasses the standalone "must have a completed trip" guard because the trip is being completed in the same request

---

### US-012 — View trip requests
**As a** Fleet Manager or Admin,
**I want to** view trip requests,
**So that** I can manage the approval queue.

**Acceptance Criteria:**
- `GET /api/trip-requests` — returns `PENDING` requests only (`FLEET_MANAGER`)
- `GET /api/trip-requests/all` — returns all requests across all statuses (`FLEET_MANAGER`, `ADMIN`)
- `GET /api/trip-requests/my` — field staff sees all their own requests across all statuses
- `GET /api/trip-requests/my/approved` — field staff sees only their currently `APPROVED` trips (the vehicle(s) assigned to them)

---

### US-013 — Auto-expire stale pending requests (Cron Job)
**As the** system,
**I want to** automatically reject pending trip requests whose start date has passed,
**So that** the approval queue does not accumulate requests that can never be fulfilled.

**Acceptance Criteria:**
- A scheduled job runs daily at midnight (`0 0 0 * * *`)
- Finds all `PENDING` requests where `startDate < today`
- Sets their status to `REJECTED`
- Logs the count of auto-rejected requests

---

## Mileage Reporting

> **Design Decision — What mileage tracking is (and is not) for**
>
> Mileage reporting in this system has a **single purpose**: determining when a vehicle is due for
> scheduled maintenance based on kilometres covered. When the vehicle's cumulative odometer reading
> crosses a configured threshold (`milestoneInterval`), the system automatically raises a maintenance
> flag and notifies the fleet manager.
>
> Mileage is **not** used to:
> - Detect or flag stolen vehicles
> - Verify that a vehicle stayed within a trip's intended route or distance
> - Produce per-trip distance reports
>
> The `reportedMileage` value is an **absolute odometer reading** — not a per-trip delta. The system
> trusts the field staff to report the actual instrument reading. Accuracy is expected by policy, not
> enforced by GPS or external data sources.

### US-014 — Submit an odometer reading
**As a** Field Staff member,
**I want to** report the vehicle's odometer reading after a completed trip,
**So that** the system can track cumulative mileage and trigger maintenance when needed.

**Acceptance Criteria:**
- `POST /api/mileage-logs` accepts `{ vehicleId, reportedMileage, tripRequestId? }`
- `reportedMileage` is the **absolute odometer value** — not a per-trip delta
- The submitting field staff must have a `COMPLETED` trip for that vehicle — returns `409` otherwise
- Submitted value must be **≥** the vehicle's currently recorded mileage — returns `409` if lower
- Vehicle's `currentMileage` is set directly to the reported value
- If `tripRequestId` is provided, it is validated (must match user + vehicle + `COMPLETED`) and linked to the log — enabling this trip to count toward the vehicle's lifecycle calculation (see US-032)
- Returns an instant `200` response confirming the submission
- Maintenance flagging and fleet manager notification happen **asynchronously** in the background via Kafka
- Lifecycle recalculation is triggered synchronously after each submission (see US-032)

---

### US-015 — Auto-trigger maintenance on milestone
**As the** system,
**I want to** automatically flag a vehicle for maintenance when its odometer reading crosses the configured milestone interval,
**So that** vehicles are serviced on schedule without manual tracking.

**Acceptance Criteria:**
- When `reportedMileage` causes `floor(new / interval) > floor(old / interval)`, a maintenance event is published to Kafka
- The Kafka consumer creates a `MaintenanceFlag` record and sets the vehicle status to `MAINTENANCE`
- The vehicle is **blocked** from new trip requests while in `MAINTENANCE` status
- The fleet manager is notified by email

---

### US-016 — View mileage history for a vehicle
**As a** Fleet Manager or Admin,
**I want to** view the mileage log history for a vehicle,
**So that** I can audit usage over time.

**Acceptance Criteria:**
- `GET /api/mileage-logs/vehicle/{vehicleId}` returns logs sorted newest first
- Returns `404` if the vehicle does not exist
- Restricted to `FLEET_MANAGER` and `ADMIN`

---

## Maintenance Management

### US-017 — Assign a maintenance flag to a team member
**As a** Fleet Manager or Admin,
**I want to** assign an open maintenance flag to a maintenance team member,
**So that** the work is tracked and the right person is notified.

**Acceptance Criteria:**
- `PATCH /api/maintenance-flags/{id}/assign` accepts `{ maintenanceTeamUserId }`
- Flag must be in `OPEN` status — returns `409` otherwise
- Sets flag status to `ASSIGNED`, records who assigned it and when
- Sends an email notification to the assigned maintenance team member
- Restricted to `FLEET_MANAGER` and `ADMIN`

---

### US-018 — Update maintenance progress
**As a** Maintenance Team member,
**I want to** update my progress notes on a maintenance flag,
**So that** the fleet manager can see what work has been done.

**Acceptance Criteria:**
- `PATCH /api/maintenance-flags/{id}/progress` accepts `{ progressNotes }`
- Flag must be in `ASSIGNED` or `IN_PROGRESS` status — returns `409` otherwise
- Sets flag status to `IN_PROGRESS`
- Notifies the assigning fleet manager by email
- Restricted to `MAINTENANCE_TEAM`

---

### US-019 — Signal maintenance work is complete
**As a** Maintenance Team member,
**I want to** mark my maintenance work as done and alert the fleet manager,
**So that** the fleet manager knows to review and approve the vehicle's return to service.

**Acceptance Criteria:**
- `PATCH /api/maintenance-flags/{id}/done` (no request body)
- Flag must be in `ASSIGNED` or `IN_PROGRESS` status — returns `409` otherwise
- Sets flag status to `PENDING_APPROVAL`, records the timestamp
- Sends an email notification to the assigning fleet manager requesting approval
- Restricted to `MAINTENANCE_TEAM`

---

### US-020 — Approve maintenance and return vehicle to service
**As a** Fleet Manager or Admin,
**I want to** approve a completed maintenance job and set a new mileage milestone,
**So that** the vehicle is returned to service with an updated service schedule.

**Acceptance Criteria:**
- `PATCH /api/maintenance-flags/{id}/approve` accepts `{ newMilestoneInterval, serviceNotes }`
- Flag must be in `PENDING_APPROVAL` status — returns `409` otherwise
- `newMilestoneInterval` must be **greater than** both the previous interval and the vehicle's current mileage — returns `409` otherwise
- Creates a `ServiceHistory` record on the vehicle (stores fleet manager's full name, notes, new interval, and timestamp)
- Updates the vehicle's `milestoneInterval` to the new value
- Sets vehicle status back to `AVAILABLE`
- Sets flag status to `RESOLVED`, records resolved timestamp
- Notifies the maintenance team member by email that the approval went through
- Restricted to `FLEET_MANAGER` and `ADMIN`

---

### US-021 — View maintenance flags
**As a** Fleet Manager or Admin,
**I want to** view all maintenance flags,
**So that** I can monitor the health of the fleet.

**Acceptance Criteria:**
- `GET /api/maintenance-flags` returns all flags with their current status
- `GET /api/maintenance-flags/my` returns only flags assigned to the authenticated maintenance team member
- Flag status lifecycle: `OPEN → ASSIGNED → IN_PROGRESS → PENDING_APPROVAL → RESOLVED`

---

## Service History

### US-022 — View a vehicle's service history
**As a** Fleet Manager or Admin,
**I want to** view the full service history of a vehicle,
**So that** I can audit past maintenance work.

**Acceptance Criteria:**
- `GET /api/vehicles/{id}` includes a `serviceHistories` list sorted newest first
- Each entry contains: fleet manager's full name, service notes, new milestone interval set, and service date
- Fetching service history is **only** possible from the vehicle — there is no reverse endpoint (service history → vehicle) to prevent circular references
- List endpoints (`GET /api/vehicles`) return an empty `serviceHistories` array for performance

---

## Notifications

### US-023 — Email notifications for key events
**As a** user of any role,
**I want to** receive email notifications when actions that affect me occur,
**So that** I stay informed without having to poll the system.

**Notification matrix:**

| Event | Recipient | Kafka type |
|---|---|---|
| Account created | Newly registered user | `ACCOUNT_CREATED` |
| Trip request submitted | All fleet managers | `TRIP_REQUESTED` |
| Trip request approved | Field staff who submitted | `TRIP_APPROVED` |
| Trip request rejected (manual or auto) | Field staff who submitted | `TRIP_REJECTED` |
| Maintenance flag assigned | Maintenance team member assigned | `FLAG_ASSIGNED` |
| Maintenance progress update | Fleet manager who assigned the flag | `FLAG_PROGRESS` |
| Maintenance work marked done | Fleet manager who assigned the flag | `FLAG_PENDING_APPROVAL` |
| Maintenance approved | Maintenance team member who did the work | `FLAG_RESOLVED` |
| Vehicle milestone reached | Fleet manager (all fleet managers) | `MAINTENANCE_FLAG_RAISED` |

**Acceptance Criteria:**
- All notifications are sent **asynchronously** via Kafka (`notification.request` topic)
- Fleet managers are notified immediately when a new trip request is submitted (`PENDING`)
- A welcome email is sent to each user upon account creation
- The field staff member gets an **instant** response on mileage submission; the background event handles the rest
- Notification delivery does not block or fail the primary API response

---

## Vehicle Activity Dashboard

### US-029 — Admin vehicle activity log (real-time fleet health dashboard)
**As an** Admin,
**I want to** see a live feed of every significant fleet event — searchable by vehicle plate number and date,
**So that** I can monitor the health and usage of every vehicle in real time without needing to check individual records.

**Events captured:**

| Event type | Triggered by | Example log line |
|---|---|---|
| `TRIP_REQUESTED` | Field staff submits trip request | `Emeka Obi (FIELD_STAFF) requested vehicle KJA-001AB for destination: Abuja (12 May – 15 May)` |
| `TRIP_APPROVED` | Fleet manager approves | `Trip request #42 approved for vehicle KJA-001AB — assigned to Emeka Obi` |
| `TRIP_REJECTED` | Fleet manager rejects (manual or auto) | `Trip request #45 auto-rejected for vehicle KJA-001AB — conflict with approved trip until 15 May` |
| `MILEAGE_SUBMITTED` | Field staff or fleet manager submits odometer reading | `Emeka Obi (FIELD_STAFF) reported odometer reading of 5,240 km on vehicle KJA-001AB` |
| `MAINTENANCE_SCHEDULED` | System — milestone threshold crossed | `Vehicle KJA-001AB flagged for maintenance — milestone of 5,000 km reached` |
| `MAINTENANCE_COMPLETED` | Maintenance team marks work done | `Chidi Nwosu (MAINTENANCE_TEAM) marked maintenance work done on vehicle KJA-001AB` |
| `MILESTONE_UPDATED` | Fleet manager approves maintenance | `Fleet Manager Tunde Bello approved maintenance and set new milestone interval to 10,000 km for KJA-001AB` |

**Architecture:**
- Each service publishes a `VehicleActivityEvent` to the Kafka topic `fleet.activity`
- A `VehicleActivityConsumer` listens to the topic and persists each event to the `vehicle_activity_logs` table
- All event publishing is fire-and-forget (async via Kafka) — primary API responses are not blocked

**Acceptance Criteria:**
- `GET /api/admin/activity-logs` — returns all logs, newest first
- `GET /api/admin/activity-logs?plateNumber=KJA-001AB` — filter by plate number
- `GET /api/admin/activity-logs?date=2026-05-10` — filter by date (all events on that calendar day)
- `GET /api/admin/activity-logs?plateNumber=KJA-001AB&date=2026-05-10` — combined filter
- Each log entry includes: `id`, `vehicleId`, `plateNumber`, `eventType`, `description`, `actorName`, `actorRole`, `occurredAt`
- Restricted to `ADMIN` only
- The `vehicle_activity_logs` table is indexed on `plate_number` and `occurred_at` for efficient filtering
- The frontend refreshes manually to see new entries (no push / automatic polling in this version)

> **Future Enhancement — WebSocket push**
>
> The current implementation serves data on demand (REST pull). A future upgrade will add a
> WebSocket channel so the admin dashboard receives new activity events in real time without
> any manual refresh.

---

## Maintenance Chat

### US-028 — In-flag conversation between maintenance team and fleet manager
**As a** Maintenance Team member or Fleet Manager,
**I want to** exchange messages within a maintenance flag,
**So that** we can coordinate work in real time without switching to a separate communication tool.

> **Current Implementation — Manual refresh (REST polling)**
>
> Messages are stored in the database and served via a plain REST endpoint. The frontend
> fetches the latest messages whenever the user manually refreshes or navigates back to
> the flag detail view. There is no automatic push mechanism in this version.
>
> **Future Enhancement — WebSocket**
>
> A WebSocket channel (Spring `@MessageMapping` / STOMP) will be introduced to push new
> messages to connected clients instantly, eliminating the need for any manual refresh.
> The database model and REST endpoints implemented here will remain unchanged; only the
> delivery layer will be upgraded.

**Acceptance Criteria:**
- `POST /api/maintenance-flags/{flagId}/messages` — send a message
  - Accepts `{ "message": "<text>" }`
  - Sender is resolved from the authenticated user's JWT
  - Returns `201 Created` with the saved message (id, senderName, senderRole, message, sentAt)
  - Returns `404` if the maintenance flag does not exist
  - Returns `409 Conflict` if the flag status is `RESOLVED` (conversation is locked — no new messages)
- `GET /api/maintenance-flags/{flagId}/messages` — fetch all messages
  - Returns messages ordered oldest → newest (`sentAt ASC`)
  - Always returns the full history, including for `RESOLVED` flags (read-only after resolution)
  - Returns `404` if the maintenance flag does not exist
- Both endpoints restricted to `MAINTENANCE_TEAM`, `FLEET_MANAGER`, and `ADMIN`
- No automatic delivery / push — the client fetches on demand

---

## Media Management

> **Design Decision — Media Entity**
>
> Rather than storing URLs as plain strings on User and Vehicle, media assets are represented
> as a proper `Media` entity with two fields: `publicId` (the Cloudinary asset identifier, unique
> across the system) and `url` (the full CDN delivery URL). This separates media lifecycle from
> the owning entity and allows clean replacement/deletion via `orphanRemoval = true`.
>
> - **User → Media**: `@OneToOne` — one profile picture per user. FK stored on the `users` table.
> - **Vehicle → Media**: `@OneToMany` via a `vehicle_media` join table — multiple photos per vehicle.
> - The `Media` entity is **unidirectional** — it holds no reference back to `User` or `Vehicle`.

### US-030 — Self-service user profile
**As an** authenticated user of any role,
**I want to** view and update my own profile and manage my own profile picture,
**So that** I can maintain my account details without going through an administrator.

**Acceptance Criteria:**
- `GET /api/users/me` — returns the caller's own profile (name, email, role, profileMedia)
- `PATCH /api/users/me` — updates the caller's name only; email cannot be changed
  - Accepts `{ "name": "<string>" }` — `@NotBlank` validated
  - Returns `200` with the updated user response
- `PATCH /api/users/me/media` — sets or replaces the caller's profile picture
  - Accepts `{ "publicId": "<cloudinary-id>", "url": "<cdn-url>" }`
  - Replaces any existing media (old `Media` row is deleted via `orphanRemoval`)
  - Returns `200` with the saved `MediaResponse`
- `DELETE /api/users/me/media` — removes the caller's profile picture
  - Returns `409 Conflict` if no profile media is currently set
  - Returns `204 No Content` on success
- All four endpoints are accessible to **any** authenticated user (no `@PreAuthorize` role restriction)
- Caller is resolved from the JWT in the security context — users can only modify their own profile

---

### US-031 — Admin and fleet manager media management
**As an** Admin or Fleet Manager,
**I want to** manage profile pictures for users and vehicle photo galleries,
**So that** I can maintain accurate visual records for staff and fleet assets.

**Acceptance Criteria:**
- `PATCH /api/admin/users/{id}/media` — sets or replaces any user's profile picture (`ADMIN` only)
  - Accepts `{ "publicId", "url" }`; returns `200` with `MediaResponse`
  - Returns `404` if the user does not exist
- `DELETE /api/admin/users/{id}/media` — removes any user's profile picture (`ADMIN` only)
  - Returns `409 Conflict` if no media is set; `204 No Content` on success
- `POST /api/vehicles/{id}/media` — appends photos to a vehicle (`FLEET_MANAGER`, `ADMIN`)
  - Accepts an **array** of `{ "publicId", "url" }` objects (one or more)
  - Appends to existing photos (does not replace); returns `200` with the full updated photo list
  - Returns `404` if the vehicle does not exist
- `DELETE /api/vehicles/{id}/media/{mediaId}` — removes a specific photo from a vehicle (`FLEET_MANAGER`, `ADMIN`)
  - Returns `404` if the media entry is not found on that vehicle
  - Returns `204 No Content` on success

---

## User Profile

*(See US-030 above — self-service profile endpoints are documented there.)*

---

## Observability

### US-024 — AOP-based request logging
**As a** system operator,
**I want** all API requests and service method calls to be automatically logged using AOP,
**So that** I can monitor system behaviour and diagnose issues without adding logging to every method.

**Acceptance Criteria:**
- An AOP aspect intercepts all public service methods
- Logs the method name, arguments (sanitised — no passwords), and execution time
- Errors are logged with full stack trace at `ERROR` level
- Normal completions are logged at `INFO` or `DEBUG` level

---

## Reliability

### US-025 — Transactional outbox for Kafka events
**As a** system operator,
**I want** Kafka events to be stored in a database outbox table before being published,
**So that** no events are lost if Kafka is temporarily unavailable.

**Acceptance Criteria:**
- When a domain event is raised (maintenance flag, notification, etc.), it is first persisted to an `outbox_events` table in the same transaction as the business data
- A background poller reads unpublished outbox events and publishes them to Kafka
- Successfully published events are marked as `PUBLISHED` with a timestamp
- Events that fail to publish are retried with backoff
- The system recovers without data loss after a Kafka restart

---

## Status Reference

| Entity | Status Values |
|---|---|
| Vehicle | `AVAILABLE` · `ASSIGNED` · `MAINTENANCE` · `OUT_OF_SERVICE` |
| Trip Request | `PENDING` · `APPROVED` · `REJECTED` · `COMPLETED` |
| Maintenance Flag | `OPEN` · `ASSIGNED` · `IN_PROGRESS` · `PENDING_APPROVAL` · `RESOLVED` |

> `OUT_OF_SERVICE` is set automatically when a vehicle's `lifecyclePercentage` reaches 80%. It cannot be set manually.

---

## Implementation Status

| Story | Status |
|---|---|
| US-001 Login / 401 on bad credentials | ✅ Done |
| US-002 Create user / invalid role → 400 | ✅ Done |
| US-003 View users | ✅ Done |
| US-004 Change own password | ✅ Done |
| US-005 Admin reset password | ✅ Done |
| US-006 Register vehicle (ADMIN + FLEET_MANAGER) | ✅ Done |
| US-007 View vehicles with service histories | ✅ Done |
| US-008 Update milestone interval | ✅ Done |
| US-009 Submit trip request / duplicate guard | ✅ Done |
| US-010 Approve / reject trip + auto-reject conflicts | ✅ Done |
| US-011 Complete trip | ✅ Done |
| US-012 View trip requests | ✅ Done |
| US-013 Cron job — auto-expire stale requests | ✅ Done |
| US-014 Submit odometer reading / backwards guard | ✅ Done |
| US-015 Auto-trigger maintenance on milestone | ✅ Done |
| US-016 View mileage history | ✅ Done |
| US-017 Assign maintenance flag | ✅ Done |
| US-018 Update maintenance progress | ✅ Done |
| US-019 Signal work done → PENDING_APPROVAL | ✅ Done |
| US-020 Approve maintenance + service history | ✅ Done |
| US-021 View maintenance flags | ✅ Done |
| US-022 View vehicle service history | ✅ Done |
| US-023 Email notifications via Kafka | ✅ Done |
| US-024 AOP-based request logging | 🔲 Pending |
| US-025 Transactional outbox for Kafka events | 🔲 Pending |
| US-026 Deactivate / reactivate user account | ✅ Done |
| US-027 Nigerian plate number validation | ✅ Done |
| US-028 Maintenance flag chat (REST, manual refresh) | ✅ Done |
| US-029 Vehicle activity log / admin dashboard | ✅ Done |
| US-030 Self-service profile (view, update name, manage own media) | ✅ Done |
| US-031 Admin + fleet manager media management (user + vehicle) | ✅ Done |
| US-032 Vehicle lifecycle tracking + OUT_OF_SERVICE automation | ✅ Done |
