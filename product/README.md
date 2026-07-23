# Epiphaneia

AI Agent diagnostic workstation for backend developers. Connect Prometheus, Elasticsearch, and Actuator endpoints — LLM-powered ReAct loop diagnoses root causes and produces structured reports.

## Quick Start (Docker)

```bash
# 1. Create environment file
cp .env.example .env
# Edit .env — fill in DB_PASSWORD and EPIPHANEIA_ENCRYPTION_KEY
# Generate key: openssl rand -hex 32

# 2. Start all services (--env-file points to the .env in project root)
docker compose --env-file .env -f docker/docker-compose.yml up -d

# 3. Check initial admin password
docker logs epiphaneia-app | grep -A2 "Admin credentials"

# 4. Open browser
# http://localhost:80
# Login: admin / (password from step 3)
```

## Local Development

```bash
# Prerequisites: PostgreSQL on localhost:5432 with database "epiphaneia"
# Create .env from template: cp .env.example .env → fill in DB_PASSWORD + EPIPHANEIA_ENCRYPTION_KEY

# Backend
cd product
./mvnw spring-boot:run -pl epiphaneia-server
# Reads .env via spring-boot-dotenv or set env vars manually:
#   $env:SPRING_DATASOURCE_PASSWORD="your-password"
#   $env:EPIPHANEIA_ENCRYPTION_KEY="your-hex-key"

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
| epiphaneia-domain | Domain entities and Spring Data repositories |
| epiphaneia-engine | PromQL/ES DSL query builders, Actuator probe |
| epiphaneia-connector | Prometheus/Elasticsearch connector implementations |
| epiphaneia-llm | LLM client, model routing, prompt templates |
| epiphaneia-agent-core | Diagnosis orchestration, ReAct loop, state machine, report synthesis |
| epiphaneia-server | Spring Boot entry, REST API, SSE, security filters |
| epiphaneia-web-ui | React 19 + Vite 6 frontend |

## Configuration

| Priority | Method | Use for |
|----------|--------|---------|
| 1 | `.env` file | DB password, encryption key |
| 2 | Web UI Settings | Data source URLs, LLM provider, applications, tokens |
