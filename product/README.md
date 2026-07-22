# Epiphaneia

AI Agent diagnostic workstation for backend developers. Connect Prometheus, Elasticsearch, and Actuator endpoints — LLM-powered ReAct loop diagnoses root causes and produces structured reports.

## Quick Start (Docker)

```bash
# 1. Create environment file
cp .env.example .env
# Edit .env — fill in DB_PASSWORD and EPIPHANEIA_ENCRYPTION_KEY
# Generate key: openssl rand -hex 32

# 2. Start all services
docker compose -f docker/docker-compose.yml up -d

# 3. Open browser
# http://localhost:80
# Login: admin / (see console output on first boot)
```

## Local Development

```bash
# Backend (requires PostgreSQL + application-local.yml)
cd product
$env:SPRING_PROFILES_ACTIVE="local"
./mvnw spring-boot:run -pl epiphaneia-server

# Frontend (separate terminal)
cd product/epiphaneia-web-ui
npm install
npm run dev
# Opens http://localhost:5173 — API proxy → localhost:8080
```

## Build

```bash
./mvnw clean install -DskipTests      # all Java modules
cd epiphaneia-web-ui && npm run build  # frontend
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| frontend | 80 | React 19 SPA + Nginx reverse proxy |
| app | 8080 (internal) | Spring Boot 4.1 backend |
| postgres | 5432 (internal) | PostgreSQL 16 |

## Modules

| Module | Purpose |
|--------|---------|
| epiphaneia-infra | Connector SPI, AES-256-GCM encryption, exceptions |
| epiphaneia-connector | Prometheus/Elasticsearch connector implementations |
| epiphaneia-engine | PromQL/ES DSL query builders, Actuator probe |
| epiphaneia-agent-core | Domain model, repositories, ReAct orchestration, LLM routing |
| epiphaneia-server | Spring Boot entry, REST API, SSE, security filters |
| epiphaneia-web-ui | React 19 + Vite 6 frontend |

## Configuration

| Priority | Method | Use for |
|----------|--------|---------|
| 1 | `.env` file | DB password, encryption key |
| 2 | Web UI Settings | Data source URLs, LLM provider, applications, tokens |
