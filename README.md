# fleet-core-service

Core domain service for FleetOps. Manages vehicles, users, trip requests, mileage logs, and maintenance flags.

## Run

```bash
docker-compose up --build
```

API available at `http://localhost:8080`

## Key Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fleetops_core` | PostgreSQL URL |
| `DB_USERNAME` | `fleetops` | DB user |
| `DB_PASSWORD` | `fleetops` | DB password |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka broker |
| `JWT_SECRET` | see yml | 256-bit base64 secret |
| `JWT_EXPIRY_MS` | `86400000` | Token expiry (24h) |

## Kafka Topics

| Topic | Direction | Purpose |
|---|---|---|
| `maintenance.flag.created` | Produced + Consumed internally | Mileage milestone → create flag |
| `notification.request` | Produced | Sends notification events to notification-service |

## Notes

- Start this service **before** `notification-service` — it owns Kafka and PostgreSQL.
- The `notification-service` connects to the same `fleetops-net` Docker network.
- Change `JWT_SECRET` in production.
