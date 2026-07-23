# Real Time Chat App

A backend for a real-time chat application built with Spring Boot. It supports one-to-one and group chats over WebSockets (STOMP), stores messages in PostgreSQL, and uses Redis for presence, caching, rate limiting and cross-instance fan-out.

## Features

- Registration and login with JWT, plus role-based access (`USER`, `ADMIN`)
- Direct (1:1) and group conversations with owner/admin/member roles
- Real-time messaging over STOMP, with a REST fallback
- Message history with cursor (keyset) pagination
- Idempotent sends: retrying the same `clientMessageId` never creates a duplicate
- Online/offline presence with multi-tab support and last-seen
- Typing indicators
- Delivery and read receipts, with unread counts
- Redis caching, per-user rate limiting, and pub/sub relay for running multiple instances
- Centralized validation and error handling for both REST and WebSocket
- Docker Compose setup with health checks

## Tech stack

Java 21, Spring Boot 3.5 (Web, Security, WebSocket, Data JPA, Validation, Cache, Actuator), PostgreSQL 16, Flyway, Redis 7, jjwt, Lombok, JUnit 5, Testcontainers, Docker.

## Architecture

```
Controller  ->  Service  ->  Repository  ->  PostgreSQL
    |              |
 STOMP handler     +-> Redis (presence, cache, rate limit, pub/sub)
```

Code is organized by feature, and each feature package is layered:

| Package | Responsibility |
|---|---|
| `auth` | register/login, JWT issue and validation, security principal |
| `user` | profiles, search, admin user management |
| `conversation` | direct and group chats, membership, roles |
| `message` | sending, history, receipts |
| `presence` | online status, typing indicators |
| `websocket` | STOMP config, auth/subscription interceptors, event publishing, redis relay |
| `common` | config, exceptions, shared DTOs, rate limiter |

The schema is managed by Flyway (`src/main/resources/db/migration`). Hibernate only validates it.

## Running

### With Docker

```bash
cp .env.example .env
docker compose up --build
```

The app runs on `http://localhost:8080`. If ports 5432 or 6379 are already in use, change `POSTGRES_PORT` / `REDIS_PORT` in `.env`.

To create an admin on startup, set `ADMIN_USERNAME` and `ADMIN_PASSWORD`.

### Locally

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

### Configuration

| Variable | Default | Notes |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `rtca` / `rtca` / `rtca` | |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `JWT_SECRET` | dev value | base64, at least 256 bits. **Set this in production** |
| `JWT_TTL` | `1h` | |
| `WS_ALLOWED_ORIGINS` | `*` | comma separated |
| `MSG_RATE_LIMIT` / `MSG_RATE_WINDOW` | `20` / `10s` | messages per user per window |
| `RELAY_ENABLED` | `true` | Redis pub/sub fan-out |

## REST API

All endpoints except `/api/auth/**` need an `Authorization: Bearer <token>` header.

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | create account |
| POST | `/api/auth/login` | `{login, password}` returns access token |
| GET / PATCH | `/api/users/me` | own profile |
| GET | `/api/users/{id}` | public profile |
| GET | `/api/users/search?q=` | search by username/display name |
| GET | `/api/conversations` | my conversations with unread counts, most recent first |
| GET | `/api/conversations/{id}` | conversation details |
| POST | `/api/conversations/direct` | `{userId}` get or create a direct chat |
| POST | `/api/conversations/groups` | `{name, memberIds}` create a group |
| PATCH | `/api/conversations/groups/{id}` | rename (admins) |
| POST | `/api/conversations/groups/{id}/members` | add members (admins) |
| DELETE | `/api/conversations/groups/{id}/members/{userId}` | remove member or leave |
| PATCH | `/api/conversations/groups/{id}/members/{userId}/role` | change role (owner) |
| GET | `/api/conversations/{id}/messages?before=&after=&limit=` | history |
| POST | `/api/conversations/{id}/messages` | `{clientMessageId, content}` send |
| POST | `/api/conversations/{id}/messages/read` | `{messageId}` mark read |
| GET | `/api/presence?userIds=1,2` | presence status |
| GET / PATCH | `/api/admin/users` | admin only |

Errors come back in one consistent shape:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "Validation failed",
  "path": "/api/auth/register", "fieldErrors": { "email": "must be a well-formed email address" } }
```

## WebSocket (STOMP)

Connect to `ws://localhost:8080/ws` and send the token in the CONNECT frame:

```
CONNECT
Authorization:Bearer <token>
accept-version:1.2
```

| Direction | Destination | Payload |
|---|---|---|
| send | `/app/conversations.{id}.send` | `{clientMessageId, content}` |
| send | `/app/conversations.{id}.typing` | `{typing: true/false}` |
| send | `/app/conversations.{id}.delivered` | `{messageId}` |
| send | `/app/conversations.{id}.read` | `{messageId}` |
| subscribe | `/topic/conversations.{id}` | `MESSAGE`, `TYPING`, `DELIVERED`, `READ` events (members only) |
| subscribe | `/topic/presence.{userId}` | `PRESENCE` events |
| subscribe | `/user/queue/events` | `ACK` for your own sends |
| subscribe | `/user/queue/errors` | errors from your frames |

Every event uses the envelope `{"type": "...", "payload": {...}}`.

## Design notes

### Idempotent message handling
The client generates a `clientMessageId` for each message and reuses it on retries. There is a unique constraint on `(sender_id, client_message_id)`, and messages are inserted with `INSERT ... ON CONFLICT DO NOTHING RETURNING id`:
- If a row is returned, the message is new. It gets broadcast, and the response is `201`.
- If nothing is returned, the message is a retry. The original is returned with `200` (or `duplicate: true` in the ACK), and nothing is broadcast again.

This holds under concurrent retries because Postgres decides the winner atomically.

### Concurrency
- **Direct chats:** each user pair has a unique `direct_key` (`minId:maxId`). Creation uses `ON CONFLICT DO NOTHING`, and the losing request re-reads the winner's row. Ten parallel "start chat" requests give one conversation.
- **Group edits:** `conversations.version` is checked with an `OPTIMISTIC_FORCE_INCREMENT` lock, so concurrent membership or rename changes can't silently overwrite each other. The loser gets a `409` and can retry.
- **Read/delivered receipts:** these are stored as pointers per participant. The updates use `WHERE last_read_message_id < :id`, so the pointers only move forward, and late or duplicate receipts do nothing.
- **Last message timestamp:** `lastMessageAt` is updated with a conditional bulk update. It never goes backwards and never conflicts with group edits.

### Transactions
Service methods own the transaction boundaries, and reads are `readOnly`. Broadcasts run in `AFTER_COMMIT` listeners, so clients never see a message that was rolled back. Cache evictions for membership changes also run after commit, so a concurrent reader can't re-cache stale data.

### Scalability
- **Horizontal scaling:** every event is published to a Redis channel. Each instance delivers it to its own STOMP sessions, so users on different nodes can talk to each other.
- **Presence:** each user has a Redis sorted set of live sessions scored by expiry. A heartbeat refreshes the scores, so multiple tabs work and sessions from a crashed node expire on their own. Lua scripts keep connect/disconnect counting atomic.
- **History:** keyset pagination on `(conversation_id, id DESC)` stays fast at any depth, unlike `OFFSET`.
- **N+1 avoidance:** participants and unread counts for a page of conversations are loaded in one query each.
- **Hot-path caching:** membership checks run on every send and subscribe, so positive results are cached in Redis. User summaries are cached too.

### Failure handling
- **Redis is treated as an optimisation:**
  - If the relay can't publish, it delivers locally.
  - The rate limiter fails open.
  - Cache errors fall through to the DB.
  - Presence errors are logged and never break a socket.
- **Validation and errors:** all validation and domain errors map to clear HTTP statuses. WebSocket errors go only to the sender, on `/user/queue/errors`.
- **Rejected frames:** an unauthenticated CONNECT or a forbidden SUBSCRIBE gets a STOMP ERROR frame.
- **Operations:** graceful shutdown, readiness/liveness probes (DB and Redis), and container health checks.

### Security
- Stateless JWT auth, with BCrypt password hashes.
- WebSocket sessions are authenticated on CONNECT, and every SUBSCRIBE to a conversation topic is checked for membership.
- Users who aren't members get `404` rather than `403`, so conversation ids don't leak.
- Sending is rate-limited per user across all instances.

## Testing

```bash
./mvnw verify
```

- Unit tests cover the auth service and JWT handling. MockMvc tests cover the auth endpoints and error mapping.
- Integration tests use Testcontainers for real Postgres and Redis (Docker required). They cover:
  - concurrent direct-chat creation
  - idempotent sends
  - pagination with no gaps or duplicates
  - non-member access
  - unread counts
  - STOMP messaging end to end

## Possible improvements

- Refresh tokens and token revocation
- An external broker relay (RabbitMQ/ActiveMQ STOMP) instead of the simple broker plus Redis relay
- An outbox table for guaranteed event delivery when Redis is down for a long time
- Message edit/delete, attachments, and push notifications
