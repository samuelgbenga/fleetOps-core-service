# FleetOps Core Service — User Stories

Generated from product requirements and system design discussions.

---

## Table of Contents

- [Authentication](#authentication)
- [User Management](#user-management)
- [Password Management](#password-management)
- [Vehicle Management](#vehicle-management)
- [Trip Requests](#trip-requests)
- [Mileage Reporting](#mileage-reporting)
- [Maintenance Management](#maintenance-management)
- [Service History](#service-history)
- [Notifications](#notifications)
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

### US-011 — Complete a trip
**As a** Fleet Manager or Admin,
**I want to** mark an approved trip as completed,
**So that** the vehicle is returned to the available pool.

**Acceptance Criteria:**
- `PATCH /api/trip-requests/{id}/complete` — only works on `APPROVED` trips
- Sets vehicle status back to `AVAILABLE`
- Returns `409` if the trip is not `APPROVED`

---

### US-012 — View trip requests
**As a** Fleet Manager or Admin,
**I want to** view trip requests,
**So that** I can manage the approval queue.

**Acceptance Criteria:**
- `GET /api/trip-requests` — returns `PENDING` requests only (`FLEET_MANAGER`)
- `GET /api/trip-requests/all` — returns all requests across all statuses (`FLEET_MANAGER`, `ADMIN`)
- `GET /api/trip-requests/my` — field staff sees only their own requests

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

### US-014 — Submit an odometer reading
**As a** Field Staff member,
**I want to** report the vehicle's odometer reading after a completed trip,
**So that** the system can track cumulative mileage and trigger maintenance when needed.

**Acceptance Criteria:**
- `POST /api/mileage-logs` accepts `{ vehicleId, reportedMileage }`
- `reportedMileage` is the **absolute odometer value** — not a per-trip delta
- Submitted value must be **≥** the vehicle's currently recorded mileage — returns `409` if lower
- Vehicle's `currentMileage` is set directly to the reported value
- Returns an instant `200` response confirming the submission
- Maintenance flagging and fleet manager notification happen **asynchronously** in the background via Kafka

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

| Event | Recipient |
|---|---|
| Trip request approved | Field staff who submitted |
| Trip request rejected (manual or auto) | Field staff who submitted |
| Maintenance flag assigned | Maintenance team member assigned |
| Maintenance progress update | Fleet manager who assigned the flag |
| Maintenance work marked done | Fleet manager who assigned the flag |
| Maintenance approved | Maintenance team member who did the work |
| Vehicle milestone reached | Fleet manager (first available) |

**Acceptance Criteria:**
- All notifications are sent **asynchronously** via Kafka (`notification.request` topic)
- The field staff member gets an **instant** response on mileage submission; the background event handles the rest
- Notification delivery does not block or fail the primary API response

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
| Vehicle | `AVAILABLE` · `ASSIGNED` · `MAINTENANCE` |
| Trip Request | `PENDING` · `APPROVED` · `REJECTED` · `COMPLETED` |
| Maintenance Flag | `OPEN` · `ASSIGNED` · `IN_PROGRESS` · `PENDING_APPROVAL` · `RESOLVED` |

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
