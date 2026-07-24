# Vendor Payment Service

An event-driven backend for paying vendors against incoming invoices, with an
**LLM-powered incident-triage endpoint** that explains failed payments in plain English
from their own logs. Built with **Kotlin + Spring Boot, Kafka, PostgreSQL, and Redis**.

## Architecture

```
POST /invoices ─► Kafka topic: invoices.received
                        │
                        ▼
                 PaymentProcessor  (Kafka consumer)
                   ├─ validates against a Redis vendor cache + business rules
                   ├─ writes payment state ─► PostgreSQL
                   └─ emits payments.settled  OR  payments.failed
                        │
                        ▼
        GET /incidents/{paymentId}/explain
                   └─ feeds the failed payment's trace/logs to an
                      IncidentAnalyzer → plain-English root cause + fix
```

The REST layer never blocks on processing — it publishes an event and returns immediately.
A separate consumer does validation and settlement, so the pipeline stays decoupled and
resilient: if the consumer is down, invoices wait in the topic rather than being lost.

## The incident-triage endpoint

When a payment fails, `GET /incidents/{id}/explain` reads that payment's recorded trace
(every decision it went through) and produces a plain-English root cause and remediation
step. The analyzer is pluggable behind an interface:

- **`MockIncidentAnalyzer`** *(default)* — derives the root cause from the failure reason
  and trace. No API key, fully offline, deterministic.
- **`ClaudeIncidentAnalyzer`** *(profile `claude`)* — a real LLM call over the same inputs,
  using the official `com.anthropic:anthropic-java` SDK. Reads `ANTHROPIC_API_KEY` from
  the environment.

The endpoint code is identical either way — switching to the live model is a profile flag.

## Run it

```bash
docker compose up -d          # Kafka (KRaft), Postgres, Redis
./gradlew bootRun             # starts the service on :8080
./demo.sh                     # walks a settled payment, a failed one, and the triage endpoint
```

Real LLM analyzer instead of the mock:

```bash
export ANTHROPIC_API_KEY=...
./gradlew bootRun --args='--spring.profiles.active=claude'
```

## Try it by hand

```bash
# settles (approved vendor, under the ceiling)
curl -X POST localhost:8080/invoices -H 'content-type: application/json' \
  -d '{"vendorId":"VENDOR-ACME","amount":250,"currency":"USD"}'

# fails (unknown vendor) — grab the paymentId from the response
curl -X POST localhost:8080/invoices -H 'content-type: application/json' \
  -d '{"vendorId":"VENDOR-SHADY","amount":900,"currency":"USD"}'

# explain the failure
curl localhost:8080/incidents/<paymentId>/explain
```

Seeded approved vendors: `VENDOR-ACME`, `VENDOR-GLOBEX`. Validation rules live in
`PaymentProcessor.validate` — unknown vendor, non-positive amount, over the $10k
auto-approve ceiling, or a non-USD currency.

## Stack

Kotlin 2.1 · Spring Boot 3.4 · Spring for Apache Kafka · Spring Data JPA · Spring Data Redis ·
PostgreSQL 16 · Redis 7 · Kafka 3.9 (KRaft) · anthropic-java · JDK 21
