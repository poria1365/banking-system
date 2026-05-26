# Banking Transaction Processing System

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-ff6600?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Redis](https://img.shields.io/badge/Redis-7.2-red?logo=redis)](https://redis.io/)
[![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-blue)](#architecture)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## Overview

A production-grade banking backend built with **Hexagonal (Ports & Adapters) Architecture**. The system provides REST APIs for account management and fund transfers, with strong consistency guarantees, fault tolerance, and full observability.

The implementation identifies and solves six real-world engineering challenges that cause silent data corruption or system failure in production banking systems. See [Engineering Challenges Solved](#engineering-challenges-solved) for the full breakdown.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Technology Stack](#technology-stack)
4. [Prerequisites](#prerequisites)
5. [Getting Started](#getting-started)
6. [Configuration Reference](#configuration-reference)
7. [API Reference](#api-reference)
8. [Transaction Lifecycle](#transaction-lifecycle)
9. [Saga Pattern & Compensation](#saga-pattern--compensation)
10. [Concurrency & Consistency Model](#concurrency--consistency-model)
11. [Event Topology](#event-topology)
12. [Database Schema](#database-schema)
13. [Observability](#observability)
14. [Security](#security)
15. [Scalability](#scalability)
16. [Running Tests](#running-tests)
17. [Known Limitations & Production Considerations](#known-limitations--production-considerations)
18. [Engineering Challenges Solved](#engineering-challenges-solved)

---

## Architecture

The system follows **Hexagonal Architecture** (also known as Ports and Adapters). The domain model is completely isolated from frameworks, databases, and transport protocols. All external dependencies are accessed through explicit interfaces (ports) and implemented by adapters.

```
┌─────────────────────────────────────────────────────────────────┐
│                          REST Clients                           │
└──────────────────────────────┬──────────────────────────────────┘
                               │  HTTP + JWT
                ┌──────────────▼──────────────┐
                │     Adapter · In · Web       │
                │  AccountController           │
                │  TransactionController       │
                │  AuthController              │
                │  GlobalExceptionHandler      │
                └──────────────┬──────────────┘
                               │  Inbound Ports (interfaces)
                ┌──────────────▼──────────────┐
                │      Application Layer       │
                │  CreateAccountService        │
                │  TransferMoneyService        │
                │  GetAccountService           │
                │  GetTransactionService       │
                │  TransferSagaOrchestrator    │
                └──────────────┬──────────────┘
                               │  Outbound Ports (interfaces)
         ┌─────────────────────▼──────────────────────┐
         │               Domain Layer                  │
         │  Account  ·  Transaction  ·  Money          │
         │  Domain Events  ·  Domain Exceptions        │
         │  (Pure Java — zero framework dependency)    │
         └─────────────────────┬──────────────────────┘
                               │
         ┌─────────────────────▼──────────────────────┐
         │            Adapter · Out                    │
         │  JpaAccountRepositoryAdapter                │
         │  JpaTransactionRepositoryAdapter            │
         │  RabbitMQEventPublisher                     │
         │  RedissonDistributedLock                    │
         └──────────┬─────────────────┬───────────────┘
                    │                 │
           ┌────────▼───────┐  ┌──────▼──────────────┐
           │  PostgreSQL 16  │  │  RabbitMQ 3.13       │
           │  Redis 7.2      │  │  (banking.events     │
           └────────────────┘  │   topic exchange)     │
                               └─────────────────────-─┘
```

### Layer Responsibilities

| Layer | Responsibility |
|---|---|
| **Domain** | Business rules, aggregates, value objects, domain events, exceptions. No Spring, no JPA. |
| **Application** | Use case orchestration. Coordinates domain objects and outbound ports. |
| **Adapter · In** | Translates HTTP requests into use-case commands. Handles serialisation and validation. |
| **Adapter · Out** | Implements outbound ports: JPA persistence, RabbitMQ publishing, Redis locking. |
| **Infrastructure** | Framework configuration, security filter chain, AOP aspects, stereotypes. |

---

## Project Structure

```
src/main/java/com/banking/
│
├── BankingApplication.java
│
├── domain/
│   ├── model/
│   │   ├── Account.java              # Aggregate root — owns balance rules
│   │   ├── Transaction.java          # Tracks full transfer lifecycle
│   │   ├── Money.java                # Immutable value object
│   │   ├── AccountStatus.java        # ACTIVE | FROZEN | CLOSED
│   │   ├── TransactionStatus.java    # PENDING → PROCESSING → COMPLETED / FAILED / ...
│   │   └── TransactionType.java      # TRANSFER | DEPOSIT | WITHDRAWAL
│   ├── event/
│   │   ├── DomainEvent.java          # Abstract base
│   │   ├── TransferInitiatedEvent.java
│   │   ├── AccountDebitedEvent.java
│   │   ├── AccountCreditedEvent.java
│   │   ├── TransferCompletedEvent.java
│   │   ├── TransferFailedEvent.java
│   │   └── DebitReversedEvent.java
│   ├── exception/
│   │   ├── DomainException.java
│   │   ├── AccountNotFoundException.java
│   │   ├── AccountInactiveException.java
│   │   ├── InsufficientFundsException.java
│   │   ├── DuplicateTransactionException.java
│   │   └── TransactionNotFoundException.java
│   └── port/
│       ├── in/                       # Inbound ports (use case interfaces)
│       │   ├── CreateAccountUseCase.java
│       │   ├── GetAccountUseCase.java
│       │   ├── GetTransactionUseCase.java
│       │   ├── TransferMoneyUseCase.java
│       │   └── command/
│       │       ├── CreateAccountCommand.java
│       │       └── TransferMoneyCommand.java
│       └── out/                      # Outbound ports (infrastructure interfaces)
│           ├── AccountRepository.java
│           ├── TransactionRepository.java
│           ├── EventPublisher.java
│           └── DistributedLockPort.java
│
├── application/
│   ├── service/
│   │   ├── CreateAccountService.java
│   │   ├── GetAccountService.java
│   │   ├── GetTransactionService.java
│   │   └── TransferMoneyService.java
│   └── saga/
│       └── TransferSagaOrchestrator.java
│
├── adapter/
│   ├── in/web/
│   │   ├── AccountController.java
│   │   ├── TransactionController.java
│   │   ├── AuthController.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── dto/
│   │       ├── ApiResponse.java
│   │       ├── AccountResponse.java
│   │       ├── CreateAccountRequest.java
│   │       ├── TransactionResponse.java
│   │       └── TransferRequest.java
│   └── out/
│       ├── persistence/
│       │   ├── entity/
│       │   │   ├── AccountEntity.java
│       │   │   └── TransactionEntity.java
│       │   ├── AccountJpaRepository.java
│       │   ├── TransactionJpaRepository.java
│       │   ├── JpaAccountRepositoryAdapter.java
│       │   └── JpaTransactionRepositoryAdapter.java
│       └── messaging/
│           └── RabbitMQEventPublisher.java
│
└── infrastructure/
    ├── annotation/
    │   ├── UseCase.java
    │   └── PersistenceAdapter.java
    ├── config/
    │   ├── RabbitMQConfig.java
    │   ├── RedissonConfig.java
    │   └── SecurityConfig.java
    ├── lock/
    │   ├── RedissonDistributedLock.java
    │   └── LockAcquisitionException.java
    ├── observability/
    │   └── TransactionLoggingAspect.java
    └── security/
        ├── JwtAuthenticationFilter.java
        └── JwtTokenService.java
```

---

## Technology Stack

| Component | Technology | Purpose |
|---|---|---|
| Language | Java 21 | Virtual threads, records, pattern matching |
| Framework | Spring Boot 3.3.5 | Application container, DI, web, security |
| Persistence | Spring Data JPA + PostgreSQL 16 | Transactional account and transaction storage |
| Schema Management | Flyway | Versioned, repeatable DB migrations |
| Messaging | RabbitMQ 3.13 | Asynchronous domain event publishing |
| Distributed Locking | Redisson 3.27.2 + Redis 7.2 | Cross-pod concurrency guard for transfers |
| Security | Spring Security + jjwt 0.12.5 | Stateless JWT authentication (HMAC-SHA256) |
| Fault Tolerance | Resilience4j 2.2.0 | Circuit breaker + retry on transient failures |
| Observability | Micrometer + Prometheus | Use-case latency histograms |
| AOP | Spring AOP | Cross-cutting metrics and logging |

---

## Prerequisites

| Requirement | Minimum Version |
|---|---|
| Java | 21 |
| Maven | 3.9 |
| Docker + Docker Compose | Docker 24 |
| PostgreSQL | 16 (or via Docker) |

---

## Getting Started

### Step 1 — Start infrastructure services

```bash
docker-compose up -d
```

This starts:
- **RabbitMQ** on `localhost:5672` (Management UI: http://localhost:15672)
- **Redis** on `localhost:6379`

> **PostgreSQL is not included in `docker-compose.yml`.** Add it manually or use an existing instance. See the snippet in [Configuration Reference](#configuration-reference).

### Step 2 — Configure environment variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/banking
export DB_USER=banking
export DB_PASS=banking_secret
export JWT_SECRET=your-minimum-256-bit-secret-key-replace-this
```

All other variables default to values matching the Docker Compose services.

### Step 3 — Run the application

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`. Flyway runs schema migrations automatically on startup.

### Step 4 — Verify

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## Configuration Reference

All configuration is in `src/main/resources/application.yml`. Every sensitive value is externalisable via environment variable.

| Environment Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/banking` | JDBC connection URL |
| `DB_USER` | `banking` | Database username |
| `DB_PASS` | `banking_secret` | Database password |
| `DB_POOL_MAX` | `20` | HikariCP maximum pool size |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASS` | `redis_secret` | Redis password |
| `REDIS_SSL` | `false` | Enable TLS for Redis connection |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USER` | `banking` | RabbitMQ username |
| `RABBITMQ_PASS` | `banking_secret` | RabbitMQ password |
| `JWT_SECRET` | *(dev placeholder)* | HMAC-SHA256 signing key — **must be overridden in production** |

### Adding PostgreSQL to docker-compose.yml

```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: banking
    POSTGRES_USER: banking
    POSTGRES_PASSWORD: banking_secret
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

---

## API Reference

All endpoints (except `/actuator/health` and `/api/v1/auth/token`) require a valid `Authorization: Bearer <token>` header.

---

### Authentication

#### `POST /api/v1/auth/token`

Obtain a JWT access token. This demo endpoint accepts any non-blank credentials.

**Request**

```json
{
  "username": "alice",
  "password": "any"
}
```

**Response `200 OK`**

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "type": "Bearer",
    "expiresIn": 86400
  },
  "timestamp": "2026-05-24T10:00:00Z"
}
```

**cURL**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"any"}' | jq -r .data.token)
```

---

### Accounts

#### `POST /api/v1/accounts`

Create a new bank account.

**Request**

```json
{
  "ownerId": "alice",
  "initialBalance": 1000.00,
  "currency": "USD"
}
```

| Field | Type | Constraints |
|---|---|---|
| `ownerId` | string | Required, max 100 characters |
| `initialBalance` | decimal | Required, ≥ 0.00 |
| `currency` | string | Required, exactly 3 characters (ISO 4217) |

**Response `201 Created`**

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "ownerId": "alice",
    "balance": 1000.00,
    "currency": "USD",
    "status": "ACTIVE",
    "createdAt": "2026-05-24T10:00:00Z",
    "updatedAt": "2026-05-24T10:00:00Z"
  },
  "timestamp": "2026-05-24T10:00:00Z"
}
```

**cURL**

```bash
ALICE_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"ownerId":"alice","initialBalance":1000.00,"currency":"USD"}' \
  | jq -r .data.id)
```

---

#### `GET /api/v1/accounts/{accountId}`

Retrieve an account by its UUID.

**Response `200 OK`** — same shape as the create response above.

**Error Responses**

| Condition | Status |
|---|---|
| Account does not exist | `404 Not Found` |
| Missing or invalid JWT | `401 Unauthorized` |

---

### Transactions

#### `POST /api/v1/transactions/transfer`

Initiate a money transfer between two accounts. This operation is **idempotent**: replaying the same `idempotencyKey` returns the original transaction result without processing it again.

**Request**

```json
{
  "idempotencyKey": "txn-unique-client-generated-key",
  "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "toAccountId":   "661f9500-e29b-41d4-a716-446655440001",
  "amount": 250.00,
  "currency": "USD"
}
```

| Field | Type | Constraints |
|---|---|---|
| `idempotencyKey` | string | Required, max 64 characters — must be unique per logical transfer intent |
| `fromAccountId` | UUID | Required — must differ from `toAccountId` |
| `toAccountId` | UUID | Required |
| `amount` | decimal | Required, > 0.00 |
| `currency` | string | Required, 3-character ISO 4217 code |

**Response `201 Created`** (transfer completed synchronously)

```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "idempotencyKey": "txn-unique-client-generated-key",
    "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "toAccountId":   "661f9500-e29b-41d4-a716-446655440001",
    "amount": 250.00,
    "currency": "USD",
    "type": "TRANSFER",
    "status": "COMPLETED",
    "failureReason": null,
    "createdAt": "2026-05-24T10:01:00Z",
    "completedAt": "2026-05-24T10:01:00Z"
  },
  "timestamp": "2026-05-24T10:01:00Z"
}
```

**Response `202 Accepted`** — returned when the saga has not yet reached a terminal state.

**Error Responses**

| Condition | Status |
|---|---|
| Source or target account not found | `404 Not Found` |
| Source account frozen or closed | `422 Unprocessable Entity` |
| Insufficient funds | `422 Unprocessable Entity` |
| Validation failure (missing/invalid fields) | `400 Bad Request` |
| Missing or invalid JWT | `401 Unauthorized` |
| Distributed lock timeout | `503 Service Unavailable` |

**cURL**

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"idempotencyKey\": \"txn-$(date +%s)\",
    \"fromAccountId\": \"$ALICE_ID\",
    \"toAccountId\": \"$BOB_ID\",
    \"amount\": 250.00,
    \"currency\": \"USD\"
  }" | jq .
```

---

#### `GET /api/v1/transactions/{transactionId}`

Retrieve a single transaction by UUID.

**Error Responses**

| Condition | Status |
|---|---|
| Transaction does not exist | `404 Not Found` |
| Missing or invalid JWT | `401 Unauthorized` |

---

#### `GET /api/v1/transactions/account/{accountId}`

Retrieve all transactions in which the given account appears as either sender or receiver. Results are sorted by `createdAt` descending (newest first).

**Response `200 OK`**

```json
{
  "success": true,
  "data": [
    {
      "id": "...",
      "status": "COMPLETED",
      "amount": 250.00,
      "currency": "USD",
      ...
    }
  ],
  "timestamp": "2026-05-24T10:02:00Z"
}
```

---

### Observability Endpoints

| Endpoint | Auth Required | Description |
|---|---|---|
| `GET /actuator/health` | No | Application and dependency health |
| `GET /actuator/info` | No | Build information |
| `GET /actuator/metrics` | Yes | Available metric names |
| `GET /actuator/metrics/{name}` | Yes | Specific metric details |
| `GET /actuator/prometheus` | Yes | Prometheus scrape endpoint |
| `GET /actuator/loggers` | Yes | Logger level inspection and modification |

---

### Full End-to-End Script

The following script demonstrates the complete happy path:

```bash
BASE=http://localhost:8080

# 1. Authenticate
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"any"}' | jq -r .data.token)
echo "Token acquired."

# 2. Create accounts
ALICE_ID=$(curl -s -X POST $BASE/api/v1/accounts \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"ownerId":"alice","initialBalance":1000.00,"currency":"USD"}' | jq -r .data.id)
echo "Alice: $ALICE_ID"

BOB_ID=$(curl -s -X POST $BASE/api/v1/accounts \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"ownerId":"bob","initialBalance":0.00,"currency":"USD"}' | jq -r .data.id)
echo "Bob:   $BOB_ID"

# 3. Transfer $250 from Alice to Bob
TXN=$(curl -s -X POST $BASE/api/v1/transactions/transfer \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d "{\"idempotencyKey\":\"e2e-$(date +%s)\",
       \"fromAccountId\":\"$ALICE_ID\",
       \"toAccountId\":\"$BOB_ID\",
       \"amount\":250.00,\"currency\":\"USD\"}")
echo "Status: $(echo $TXN | jq -r .data.status)"

# 4. Verify balances (Alice: 750, Bob: 250)
echo "Alice balance: $(curl -s $BASE/api/v1/accounts/$ALICE_ID \
  -H "Authorization: Bearer $TOKEN" | jq .data.balance)"
echo "Bob balance:   $(curl -s $BASE/api/v1/accounts/$BOB_ID \
  -H "Authorization: Bearer $TOKEN" | jq .data.balance)"

# 5. Account history
echo "Alice's transactions:"
curl -s $BASE/api/v1/transactions/account/$ALICE_ID \
  -H "Authorization: Bearer $TOKEN" | jq '[.data[] | {id,status,amount}]'
```

---

## Transaction Lifecycle

```
           ┌──────────┐
           │  PENDING  │  ← Intent recorded to DB before locks are acquired
           └─────┬─────┘
                 │
           ┌─────▼──────┐
           │ PROCESSING  │  ← Saga started; locks held; atomic debit+credit in progress
           └─────┬───────┘
          ┌──────┴───────────────────┐
          │                          │
   ┌──────▼──────┐           ┌───────▼──────┐
   │  COMPLETED  │           │    FAILED    │  ← Business rule violated (e.g. insufficient
   └─────────────┘           └──────────────┘    funds, frozen account); DB rolled back —
                                                  no money moved
                                    │  Status save failed after atomic commit
                             ┌──────▼────────────────────┐
                             │    NEEDS_MANUAL_REVIEW     │  ← Balances are correct in DB;
                             └───────────────────────────-┘    only the status record failed
```

---

## Saga Pattern

The transfer is orchestrated by `TransferSagaOrchestrator` in three phases:

```
Phase 1 — Mark PROCESSING (own DB transaction, commits immediately)
          Crash-recovery signal: a recovery job can detect rows stuck here.

Phase 2 — Atomic debit + credit (Spring TransactionTemplate — single DB transaction)
          source.debit(amount)   → accountRepository.save(source)
          target.credit(amount)  → accountRepository.save(target)
          If anything throws, the DB rolls back both saves automatically.
          No application-level compensation is needed.

Phase 3 — Mark COMPLETED
          If this save fails, the balances in the DB are already correct.
          Do NOT compensate — mark NEEDS_MANUAL_REVIEW instead.
```

### Failure scenarios

| Failure point | Cause | Outcome |
|---|---|---|
| Phase 2 — `DomainException` | Business rule violated (insufficient funds, frozen account) | DB rolls back both saves. Mark `FAILED` — no money moved. |
| Phase 2 — other exception | Transient infrastructure error (DB timeout, network) | Exception propagates; `@Retry` on `TransferMoneyService` re-attempts. |
| Phase 3 — status save fails | DB write failure after atomic commit | Balances are correct. Mark `NEEDS_MANUAL_REVIEW` — do not compensate. |

**Why no application-level compensation?** The old approach ran debit and credit as two separate DB transactions, leaving a window where money was in transit. Wrapping both in a single `TransactionTemplate` means either both commit or neither does — the database rollback replaces the need for a compensating transaction entirely. Scenario C (status save fails) is the only remaining case requiring manual attention.

---

## Concurrency & Consistency Model

The system employs two independent concurrency guards, each catching failure modes the other cannot:

### Layer 1 — Distributed Redis Lock (Redisson MultiLock)

Prevents concurrent transfers from running simultaneously on the same accounts across any number of application instances.

```
Request A: transfer(Alice → Bob)  acquires locks [Alice-UUID, Bob-UUID] (sorted)
Request B: transfer(Bob → Alice)  acquires locks [Alice-UUID, Bob-UUID] (same order)
→ Consistent acquisition order eliminates deadlock
```

Configuration:
- **Wait timeout**: 10 seconds before `LockAcquisitionException`
- **Lease time**: 30 seconds automatic expiry (prevents deadlock on JVM crash)
- **Topology**: single-node by default; Sentinel and Cluster modes documented in `RedissonConfig.java`

### Layer 2 — Optimistic Locking (`@Version` on `AccountEntity`)

Catches any race conditions that bypass the Redis lock (e.g. a Redis failover during the transfer window).

Hibernate appends `AND version = ?` to every `UPDATE accounts`. If another transaction committed between this transaction's read and write, Hibernate throws `OptimisticLockException`, which Resilience4j retries up to 3 times.

### Idempotency

Two-layer deduplication:

1. **Application layer**: `findByIdempotencyKey()` before acquiring any locks. Only **terminal** transactions (`COMPLETED`, `FAILED`, `COMPENSATED`, `NEEDS_MANUAL_REVIEW`) return early. A `PENDING` or `PROCESSING` transaction falls through so `@Retry` can re-run the saga — necessary because the previous attempt may have been interrupted.
2. **Database layer**: `UNIQUE` constraint on `idempotency_key` — last-resort guard if two identical requests both pass step 1 before either saves. `JpaTransactionRepositoryAdapter` catches the resulting `DataIntegrityViolationException` and returns the existing record.
3. **Inside the lock**: after acquiring the Redis lock, the transaction status is re-read. If it is already terminal (set by a concurrent duplicate that finished first), the saga is skipped entirely.

---

## Event Topology

```
banking.events  (Topic Exchange — durable)
│
├── transfer.initiated  →  banking.transfer.initiated   (durable, DLX → banking.dead-letter)
├── account.debited     →  banking.account.debited       (durable)
├── account.credited    →  banking.account.credited      (durable)
├── transfer.completed  →  banking.transfer.completed    (durable)
├── transfer.failed     →  banking.transfer.failed       (durable)
└── dead-letter         →  banking.dead-letter           (durable — poison message sink)

Note: the `account.debited` routing key is no longer used for debit reversals. With the atomic
`TransactionTemplate`, a failed credit causes a DB rollback rather than a compensating credit event.
```

### Domain Events Published Per Transfer

| Event | Trigger | Routing Key |
|---|---|---|
| `TransferInitiatedEvent` | Saga Phase 1 — PROCESSING recorded | `transfer.initiated` |
| `AccountDebitedEvent` | Phase 2 atomic commit succeeded — source debited | `account.debited` |
| `AccountCreditedEvent` | Phase 2 atomic commit succeeded — target credited | `account.credited` |
| `TransferCompletedEvent` | Phase 3 — status COMPLETED saved | `transfer.completed` |
| `TransferFailedEvent` | Any terminal failure path | `transfer.failed` |

> Note: `DebitReversedEvent` has been removed. The previous application-level compensation (debit reversal) is no longer needed because debit and credit are now wrapped in a single `TransactionTemplate` — the DB rolls back both atomically on failure.

> **Important**: Event publishing is currently fire-and-forget. The transaction commits before events are published. For guaranteed at-least-once delivery in production, implement the **Transactional Outbox Pattern**.

---

## Database Schema

Schema is managed by Flyway. Migrations run automatically on startup and are located in `src/main/resources/db/migration/`.

### `accounts` table (`V1__create_accounts.sql`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `owner_id` | VARCHAR(100) | Logical user identifier |
| `balance` | NUMERIC(19,2) | CHECK balance >= 0 |
| `currency` | CHAR(3) | ISO 4217 code |
| `status` | VARCHAR(20) | ACTIVE / FROZEN / CLOSED |
| `version` | BIGINT | Optimistic lock counter — managed by Hibernate |
| `created_at` | TIMESTAMPTZ | Immutable after insert |
| `updated_at` | TIMESTAMPTZ | Updated on every mutation |

### `transactions` table (`V2__create_transactions.sql`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `idempotency_key` | VARCHAR(64) | `UNIQUE` — prevents duplicate processing |
| `from_account_id` | UUID | FK → accounts.id |
| `to_account_id` | UUID | FK → accounts.id; CHECK ≠ from_account_id |
| `amount` | NUMERIC(19,2) | CHECK amount > 0 |
| `currency` | CHAR(3) | ISO 4217 code |
| `type` | VARCHAR(20) | TRANSFER / DEPOSIT / WITHDRAWAL |
| `status` | VARCHAR(30) | See [Transaction Lifecycle](#transaction-lifecycle) |
| `failure_reason` | VARCHAR(1000) | Populated on non-COMPLETED terminal states |
| `created_at` | TIMESTAMPTZ | Immutable after insert |
| `updated_at` | TIMESTAMPTZ | Updated on every status change |
| `completed_at` | TIMESTAMPTZ | Populated when status = COMPLETED |

**Indexes**

| Index | Column(s) | Type |
|---|---|---|
| `idx_accounts_owner_id` | `owner_id` | B-tree |
| `idx_txn_from_account_id` | `from_account_id` | B-tree |
| `idx_txn_to_account_id` | `to_account_id` | B-tree |
| `idx_txn_created_at` | `created_at` | B-tree |
| `idx_txn_status` | `status` | Partial — excludes `COMPLETED` and `FAILED` rows |

---

## Observability

### Metrics

Every `@UseCase`-annotated method is automatically instrumented by `TransactionLoggingAspect` using AOP. No manual instrumentation is required in service classes.

**Available metrics:**

```
banking.usecase.transfer{class, outcome}           — latency histogram (ms)
banking.usecase.create_account{class, outcome}
banking.usecase.get_account{class, outcome}
banking.usecase.get_transaction{class, outcome}
banking.usecase.get_transactions_by_account{class, outcome}
```

Tags: `class` (service class name), `outcome` (`success` | `error`)

Access via Prometheus: `GET /actuator/prometheus`

### Structured Logging

MDC context is available on every request:

| MDC Key | Value |
|---|---|
| `traceId` | OpenTelemetry trace ID (when tracing is configured) |
| `transactionId` | UUID of the in-flight transaction |
| `idempotencyKey` | Client-supplied idempotency key |

Log format: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n`

### Distributed Tracing

Add the following dependencies to `pom.xml` to enable export to Jaeger or Zipkin:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

---

## Security

### Authentication

All API endpoints except `/actuator/health`, `/actuator/info`, and `POST /api/v1/auth/token` require a valid JWT in the `Authorization` header:

```
Authorization: Bearer <token>
```

Tokens are signed with HMAC-SHA256. The secret must be at least 256 bits and **must be set via the `JWT_SECRET` environment variable** in any non-development environment.

### Production Hardening Checklist

| Item | Status |
|---|---|
| Replace demo `AuthController` with OAuth2 / OpenID Connect (e.g. Keycloak) | Recommended |
| Add `@PreAuthorize` so users can only access their own accounts | Recommended |
| Enable TLS (`server.ssl.*`) | Required |
| Rotate JWT secret regularly | Required |
| Enable Redis TLS (`REDIS_SSL=true`) | Required |
| Use RabbitMQ TLS with mutual authentication | Required |
| Set `spring.jpa.show-sql=false` (already set) | Done |

---

## Scalability

The application is designed to scale horizontally. All state is externalised to PostgreSQL, Redis, and RabbitMQ.

```
                    [Load Balancer]
                   /      │       \
             svc:8080  svc:8080  svc:8080   ← Stateless; scale freely
                   \      │       /
           ┌────────▼──────▼──────▼────────┐
           │         PostgreSQL             │  ← Primary + read replicas
           │         Redis Cluster          │  ← Distributed lock coordination
           │         RabbitMQ Cluster       │  ← HA mirrored queues
           └───────────────────────────────┘
```

| Concern | Strategy |
|---|---|
| Session state | None — JWT is stateless |
| Write concurrency | Redisson MultiLock + `@Version` optimistic locking |
| Read scaling | PostgreSQL read replicas; CQRS read model (Redis / Elasticsearch) |
| Event throughput | Multiple RabbitMQ consumers per queue; horizontal consumer scaling |
| Data volume | Partition `transactions` by `created_at` or archive old rows to cold storage |
| Lock topology | Upgrade to Redisson Sentinel or Cluster mode for Redis HA |

---

## Running Tests

```bash
./mvnw test
```

Tests use an H2 in-memory database in PostgreSQL-compatibility mode. Flyway is disabled in the test profile; Hibernate creates the schema from entity definitions using `ddl-auto: create-drop`.

Test coverage by layer:

| Layer | Test Class | Approach |
|---|---|---|
| Domain model | `MoneyTest`, `AccountTest`, `TransactionTest` | Pure unit tests — no Spring |
| Commands | `CreateAccountCommandTest`, `TransferMoneyCommandTest` | Pure unit tests |
| Application services | `CreateAccountServiceTest`, `GetAccountServiceTest`, etc. | Mockito |
| Saga orchestrator | `TransferSagaOrchestratorTest` | Mockito — all failure branches covered |
| Web controllers | `AccountControllerTest`, `TransactionControllerTest` | `MockMvc` standalone |
| JPA adapters | `JpaAccountRepositoryAdapterTest`, `JpaTransactionRepositoryAdapterTest` | `@DataJpaTest` + H2 |

---

## Known Limitations & Production Considerations

| Area | Current State | Production Recommendation |
|---|---|---|
| **Event delivery** | Fire-and-forget — events may be lost on RabbitMQ failure | Implement Transactional Outbox Pattern |
| **Authentication** | Demo endpoint accepts any credentials | Integrate OAuth2 / OpenID Connect (Keycloak) |
| **Authorisation** | No per-user resource ownership checks | Add `@PreAuthorize` with JWT subject verification |
| **Multi-currency** | Transfers require matching currencies | Add FX rate service and currency conversion |
| **Pagination** | Account history returns all transactions unbounded | Implement cursor-based pagination |
| **Rate limiting** | No throttling at the application layer | Add token-bucket rate limiting at the API gateway |
| **Saga crash recovery** | PROCESSING rows left by a crashed JVM are never retried | Add a scheduled job to detect and re-drive stuck PROCESSING transactions |
| **Event ordering** | No guaranteed ordering across partitions | Use Kafka with partition key = account ID if ordering is critical |

---

## Engineering Challenges Solved

This section documents six concrete problems that cause silent data corruption or hard-to-reproduce failures in real banking systems, and exactly how each one is addressed here.

---

### Challenge 1 — Stale Account Reads Inside the Lock

**The problem**

A naive implementation loads account balances before acquiring the distributed lock, then uses those balances inside the lock to run the saga:

```
1. source = accountRepository.findById(...)   ← read happens HERE
2. target = accountRepository.findById(...)   ← read happens HERE
3. distributedLock.executeWithLocks(...)
4.   source.debit(amount)                     ← uses stale data
5.   accountRepository.save(source)           ← writes a stale version
```

Between steps 2 and 3, another transfer can acquire the lock, debit the same source account, and commit. The first request then overwrites that commit with its stale read — effectively reversing the concurrent transfer and creating money from nothing.

**The fix**

Accounts are loaded **inside** the lock, after any competing transfer has already committed:

```java
distributedLock.executeWithLocks(lockOrder, () -> {
    // Any prior holder of these locks has committed by the time we get here.
    Account source = accountRepository.findById(command.fromAccountId()).orElseThrow();
    Account target = accountRepository.findById(command.toAccountId()).orElseThrow();
    sagaOrchestrator.execute(current, source, target, command.amount());
});
```

**Files**: `TransferMoneyService.java`

---

### Challenge 2 — `@Retry` Breaks Idempotency

**The problem**

`@Retry` re-enters the entire method on a transient failure. If the idempotency check returns early for **any** existing transaction, then a retry that finds a `PENDING` or `PROCESSING` record returns that stale state immediately — the saga never runs again, and the transfer is permanently stuck.

```
Attempt 1: saves PENDING → acquires lock → DB timeout during saga → @Retry triggers
Attempt 2: findByIdempotencyKey → finds PENDING → returns PENDING immediately ← BUG
```

**The fix**

The early-return guard checks `isTerminal()` before short-circuiting. `PENDING` and `PROCESSING` fall through so the retry can complete the saga:

```java
Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
if (existing.isPresent() && existing.get().isTerminal()) {
    return existing.get();  // only COMPLETED / FAILED / etc. return here
}
// PENDING or PROCESSING: fall through and re-run the saga
```

**Files**: `TransferMoneyService.java`

---

### Challenge 3 — Race Condition Between Idempotency Check and First Save

**The problem**

Two identical requests arrive simultaneously. Both call `findByIdempotencyKey` before either has saved, both find nothing, and both proceed to `transactionRepository.save()`. The second save violates the `UNIQUE` constraint on `idempotency_key`.

```
Thread A: findByIdempotencyKey → empty
Thread B: findByIdempotencyKey → empty
Thread A: save(PENDING) → OK
Thread B: save(PENDING) → DataIntegrityViolationException ← duplicate key
```

**The fix**

Three layers of defence work together:

1. **Save before the lock** — intent is recorded as `PENDING` before lock acquisition. The DB `UNIQUE` constraint on `idempotency_key` is the definitive guard for concurrent duplicates; `JpaTransactionRepositoryAdapter` catches `DataIntegrityViolationException` and returns the existing record.
2. **Re-read status inside the lock** — after acquiring the lock, the transaction's current status is re-read. If a concurrent duplicate already finished the saga, the status is terminal and the saga is skipped.
3. **Re-use the existing record on retry** — if a non-terminal transaction already exists (e.g., from a prior interrupted attempt), it is reused rather than creating a new one.

**Files**: `TransferMoneyService.java`, `JpaTransactionRepositoryAdapter.java`

---

### Challenge 4 — Non-Atomic Debit and Credit

**The problem**

Running debit and credit as two separate database transactions leaves a window of inconsistency:

```
DB transaction A:  UPDATE accounts SET balance = 600 WHERE id = alice  ← commits
                   ← JVM crash, network failure, or any exception HERE
DB transaction B:  UPDATE accounts SET balance = 400 WHERE id = bob    ← never runs
```

After a crash between the two commits, Alice has lost $400 and Bob has gained nothing. The old approach tried to fix this with application-level compensation (a third DB write to reverse the debit), but compensation can also fail — leading to a second inconsistency and a `NEEDS_MANUAL_REVIEW` state that still required human intervention.

**The fix**

Debit and credit are wrapped in a single `TransactionTemplate`, making them one atomic DB transaction:

```java
transactionTemplate.execute(status -> {
    source.debit(amount);
    accountRepository.save(source);   // ─┐ same DB transaction
    target.credit(amount);            //  │
    accountRepository.save(target);   // ─┘ commit or roll back together
    return null;
});
```

If anything throws inside the template, the database rolls back both saves automatically. There is no partial state, no compensation logic, and no `COMPENSATING`/`COMPENSATED` status needed. The only remaining edge case is Phase 3 (status save fails after the atomic commit) — at that point the balances are correct and the only repair needed is updating the status record.

**Files**: `TransferSagaOrchestrator.java`, `TransactionConfig.java`

---

### Challenge 5 — Deadlock from Unordered Lock Acquisition

**The problem**

Two transfers share a common account and run concurrently on different threads or nodes:

```
Transfer A (Alice → Bob): tries to lock Alice, then Bob
Transfer B (Bob → Alice): tries to lock Bob, then Alice
```

If A locks Alice and B locks Bob simultaneously, each waits for the other's lock indefinitely — a classic deadlock.

**The fix**

Lock IDs are sorted into a canonical order before acquisition. Every thread on every node acquires the same two locks in the same order, making the circular-wait condition impossible:

```java
List<UUID> lockOrder = List.of(command.fromAccountId(), command.toAccountId())
    .stream().sorted().toList();
distributedLock.executeWithLocks(lockOrder, () -> { ... });
```

**Files**: `TransferMoneyService.java`, `RedissonDistributedLock.java`

---

### Challenge 6 — JVM Crash Leaves Transfers Permanently Stuck in PROCESSING

**The problem**

If the JVM crashes after the saga marks a transaction `PROCESSING` but before the atomic debit+credit commits, the transaction row stays in `PROCESSING` forever. No retry is triggered because the process is dead. The idempotency check on the next request from the client will fall through (non-terminal), re-run the saga, and complete correctly — but only if the client retries. Requests that are never retried are permanently lost.

**Current state**

The `PROCESSING` status is persisted to the database immediately (Phase 1 of the saga) specifically as a crash-recovery signal. A monitoring query can detect rows stuck in `PROCESSING` beyond a threshold and alert operations:

```sql
SELECT id, created_at FROM transactions
WHERE status = 'PROCESSING'
  AND updated_at < NOW() - INTERVAL '5 minutes';
```

**Known gap**

There is no automated recovery job in this implementation. A stuck `PROCESSING` row requires either a client retry or manual operator intervention. See [Known Limitations](#known-limitations--production-considerations).

**Files**: `TransferSagaOrchestrator.java` (Phase 1 commit), `Transaction.java` (`markProcessing`)

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
