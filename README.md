# Chat System

A distributed real-time chat system built with Spring Boot, WebSocket, Apache Cassandra, Redis, and ZooKeeper. The system supports multiple chat servers with consistent hashing for user-to-server assignment, Redis pub/sub for cross-server message delivery, and multi-device presence tracking.

## Architecture Overview

The system is composed of several microservices that work together to provide real-time messaging:

- **API Server** handles REST endpoints for authentication, user management, and channel operations. It reads the chat server registry from ZooKeeper to direct clients to the correct WebSocket server.
- **Chat Servers** (horizontally scalable) manage WebSocket connections, persist messages to Cassandra, and use Redis pub/sub for cross-server message fan-out. Each server registers itself with ZooKeeper on startup.
- **Presence Server** tracks online/offline status and last-seen timestamps using Redis.
- **Notification Server** subscribes to Redis channels and persists notifications to Cassandra for offline users.

Users are assigned to chat servers via consistent hashing on their user ID. When a message is sent to a channel, the receiving chat server publishes it to Redis so that all other chat servers with connected members can deliver it in real time.

See `docs/components.drawio` for the full architecture diagram (open with [draw.io](https://app.diagrams.net)).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle (multi-module) |
| Real-time | WebSocket (STOMP) |
| Database | Apache Cassandra 4.1 |
| Cache / Pub-Sub | Redis 7 |
| Coordination | Apache ZooKeeper 3.9 |
| Monitoring | Prometheus + Grafana |
| Containers | Docker, Docker Compose |

## Prerequisites

- **Docker** (v20.10+)
- **Docker Compose** (v2.0+)
- **Java 21** (only if building outside Docker)

## Quick Start

Start all services:

```bash
docker compose up --build
```

This will start:
- Cassandra (port 9042)
- Redis (port 6379)
- ZooKeeper (port 2181)
- API Server (port 8080)
- Chat Server 1 (port 8081)
- Chat Server 2 (port 8082)
- Presence Server (port 8083)
- Notification Server (port 8084)
- Prometheus (port 9090)
- Grafana (port 3000)

Wait for the Cassandra initialization to complete before the application servers start. Docker Compose health checks handle this automatically.

## Load Testing

Run the load test client alongside the full system:

```bash
docker compose --profile test up load-client
```

This starts a load client that creates 20 simulated users, each sending 50 messages. The load client exposes Spring Boot Actuator on port 8085 for monitoring during the test.

## Modules

| Module | Port | Description |
|--------|------|-------------|
| `api-server` | 8080 | REST API for auth, users, channels, message history |
| `chat-server` | 8081, 8082 | WebSocket server for real-time messaging |
| `presence-server` | 8083 | User presence and online status tracking |
| `notification-server` | 8084 | Notification persistence and delivery |
| `load-client` | 8085 | Load testing client (test profile only) |
| `common` | -- | Shared models, DTOs, and utilities |

## API Endpoints

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT token |

### Users

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/{userId}` | Get user profile |
| GET | `/api/users/search?username={name}` | Search users by username |

### Channels

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/channels` | Create a new channel |
| GET | `/api/channels/{channelId}` | Get channel details |
| GET | `/api/channels/user/{userId}` | List channels for a user |
| POST | `/api/channels/{channelId}/members` | Add member to channel |
| GET | `/api/channels/{channelId}/members` | List channel members |

### Messages

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/channels/{channelId}/messages` | Get message history (paginated) |

### Server Discovery

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/servers/ws?userId={userId}` | Get assigned WebSocket server for a user |

## WebSocket Protocol

Connect to the assigned chat server WebSocket endpoint:

```
ws://<chat-server-host>:<port>/ws?token=<jwt-token>
```

### Message Types (Client to Server)

**Send Message:**
```json
{
  "type": "SEND_MESSAGE",
  "channelId": "uuid",
  "content": "Hello!",
  "messageType": "TEXT"
}
```

**Join Channel:**
```json
{
  "type": "JOIN_CHANNEL",
  "channelId": "uuid"
}
```

**Typing Indicator:**
```json
{
  "type": "TYPING",
  "channelId": "uuid"
}
```

### Message Types (Server to Client)

**New Message:**
```json
{
  "type": "NEW_MESSAGE",
  "channelId": "uuid",
  "messageId": "timeuuid",
  "senderId": "uuid",
  "senderName": "alice",
  "content": "Hello!",
  "messageType": "TEXT",
  "createdAt": "2026-03-10T12:00:00Z"
}
```

**User Presence Update:**
```json
{
  "type": "PRESENCE_UPDATE",
  "userId": "uuid",
  "status": "ONLINE"
}
```

## Monitoring

Grafana is available at [http://localhost:3000](http://localhost:3000) (login: admin/admin, or anonymous access enabled).

The pre-provisioned **Chat System Dashboard** includes:

- WebSocket active connections (gauge)
- Messages per second (sent/received rate)
- Message latency percentiles (P50, P95, P99)
- HTTP request rate by endpoint
- JVM heap memory usage per service
- Redis operation rates
- Error rates (4xx/5xx)

Prometheus is available at [http://localhost:9090](http://localhost:9090) and scrapes all application servers via their `/actuator/prometheus` endpoints every 15 seconds.

## Project Structure

```
chat-system/
├── api-server/              # REST API module
├── chat-server/             # WebSocket chat module
├── presence-server/         # Presence tracking module
├── notification-server/     # Notification module
├── load-client/             # Load testing module
├── common/                  # Shared code (models, DTOs, utilities)
├── scripts/
│   └── cassandra-init.cql   # Database schema initialization
├── monitoring/
│   ├── prometheus.yml       # Prometheus scrape configuration
│   └── grafana/
│       └── provisioning/    # Grafana datasources and dashboards
├── docs/
│   └── components.drawio    # Architecture diagram
├── Dockerfile               # Multi-stage build (all modules)
├── docker-compose.yml       # Full stack orchestration
├── build.gradle             # Root Gradle build
├── settings.gradle          # Gradle module settings
└── README.md
```

## Key Design Decisions

### Consistent Hashing for Server Assignment

Users are assigned to chat servers using consistent hashing on their user ID. This ensures even distribution across servers and minimizes reassignments when servers are added or removed. The API server reads the server registry from ZooKeeper and computes the assignment.

### Redis Pub/Sub for Cross-Server Messaging

When a user sends a message to a channel, the chat server publishes it to a Redis channel keyed by the chat channel ID. All chat servers subscribe to channels for their connected users, enabling real-time delivery across server boundaries without direct server-to-server communication.

### Cassandra Data Model

The schema is designed around query patterns rather than entity relationships:

- **messages** table uses `channel_id` as partition key with `message_id` (TIMEUUID) as clustering key in descending order, optimizing for "latest messages in channel" queries.
- **user_channels** table is partitioned by `user_id` with `joined_at` descending, enabling efficient "my channels" listing.
- **users_by_username** provides a lookup table for authentication without requiring secondary indexes.

### Multi-Device Support

Users can connect from multiple devices simultaneously. The presence server tracks all active sessions per user in Redis. A user is considered online as long as at least one session is active. Messages are delivered to all connected sessions across all chat servers.

### Service Discovery with ZooKeeper

Chat servers register ephemeral nodes in ZooKeeper on startup. If a server crashes, its node is automatically removed. The API server watches ZooKeeper for changes and updates its consistent hash ring accordingly, enabling automatic failover.
