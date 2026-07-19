# Epiphaneia

AI Agent diagnostic workstation for backend developers.

**Status**: ArchitectureDesign scaffold — `mvn compile` passes, business logic in Development phase.

## Quick Start

```bash
cp .env.example .env
# edit .env with your DB_PASSWORD
docker compose -f docker/docker-compose.yml up -d
```

## Build

```bash
./mvnw compile        # compile all modules
./mvnw verify          # compile + tests + ArchUnit + OWASP scan
cd epiphaneia-web-ui && npm install && npm run build  # frontend
```

## Modules

| Module | Purpose |
|--------|---------|
| epiphaneia-infra | Connector SPI, encryption, exceptions |
| epiphaneia-connector | Prometheus/Elasticsearch connector implementations |
| epiphaneia-engine | PromQL/ES DSL query builders |
| epiphaneia-agent-core | Domain model, repositories, agent orchestration interfaces |
| epiphaneia-server | Spring Boot application + REST API |
| epiphaneia-web-ui | React 19 + Vite 6 frontend |
