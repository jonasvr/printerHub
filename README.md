# PrinterHub

Open-source 3D printer fleet management dashboard. Monitor and control printers from any brand — Bambu Lab, Prusa, Creality and more — from a single UI.

- **Free to self-host** locally via Docker
- **Optional cloud SaaS** at $5–35/month (Phase 3)
- **MIT licensed**

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, PostgreSQL, Spring WebSocket |
| Frontend | Angular 17, TypeScript, TailwindCSS, RxJS |
| Infra (local) | Docker Compose, Mosquitto MQTT |
| Infra (cloud) | Digital Ocean, HiveMQ Cloud, GitHub Actions |

---

## Prerequisites

| Tool | Min version |
|---|---|
| Java (Temurin) | 21 |
| Maven | 3.6.3 |
| Docker Desktop | 24+ |
| Node.js | 18+ |
| Angular CLI | 17+ (`npm i -g @angular/cli`) |

---

## Running locally

```bash
# 1. Start Postgres + Mosquitto
make up

# 2. Build and run the backend (new terminal)
make backend

# 3. Start the Angular dev server (new terminal)
make frontend
```

Or run everything at once (requires `npm i -g concurrently`):

```bash
make dev-all
```

See all available commands:

```bash
make help
```

---

## Development URLs

| Service | URL | Notes |
|---|---|---|
| Angular app | http://localhost:4200 | Hot-reloads on save |
| REST API | http://localhost:8080/api/v1 | |
| Swagger UI | http://localhost:8080/swagger-ui.html | Interactive API explorer |
| OpenAPI spec (JSON) | http://localhost:8080/v3/api-docs | Raw OpenAPI 3.0 |
| PostgreSQL | localhost:5432 | DB: `printerhub`, user: `printerhub` |
| Mosquitto MQTT | localhost:1883 | TCP |
| Mosquitto MQTT (WS) | localhost:9001 | WebSocket clients |

---

## Debug logging

Set `logging.level.com.printerhub: DEBUG` in `backend/cloud-service/src/main/resources/application.yml` (it is on by default in dev) to see:

- **Raw MQTT messages** — every incoming JSON payload from a printer, labelled by serial number
- **Filtered status updates** — only messages that contain `gcode_state` (full snapshots) or relevant partial fields (temps, progress) that actually update the dashboard

```
DEBUG Raw MQTT [01P00A410600839]: {"print":{"gcode_state":"RUNNING","nozzle_temper":220,...}}
DEBUG MQTT update from 01P00A410600839 — state=PRINTING, progress=42.0%, nozzle=220°C
```

To turn off verbose MQTT logging without losing other debug output, set the adapter specifically:

```yaml
logging:
  level:
    com.printerhub: DEBUG
    com.printerhub.adapter.bambu: INFO   # suppress raw MQTT lines
```

---

## Project structure

```
printerhub/
├── backend/
│   ├── core/                  # Shared entities, repositories, adapter interface
│   ├── adapters/
│   │   └── bambu-adapter/     # Bambu Lab MQTT adapter
│   └── cloud-service/         # Spring Boot main app (REST API, WebSocket)
├── frontend/                  # Angular app
├── docker/local/              # docker-compose.yml, mosquitto.conf, .env
└── Makefile
```

---

## Adding a new printer brand

PrinterHub uses a pluggable adapter pattern — adding a new brand doesn't require touching any existing code.

### 1. Create a new Maven module

Under `backend/adapters/`, copy the structure of `bambu-adapter`:

```
adapters/
└── prusa-adapter/
    ├── pom.xml
    └── src/main/java/com/printerhub/adapter/prusa/
        └── PrusaAdapter.java
```

Add the new module to `backend/pom.xml`:

```xml
<modules>
    <module>core</module>
    <module>adapters/bambu-adapter</module>
    <module>adapters/prusa-adapter</module>   <!-- add this -->
    <module>cloud-service</module>
</modules>
```

Add it as a dependency in `cloud-service/pom.xml`:

```xml
<dependency>
    <groupId>com.printerhub</groupId>
    <artifactId>prusa-adapter</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Add the brand to the enum

In `backend/core/src/main/java/com/printerhub/core/entity/PrinterBrand.java`:

```java
public enum PrinterBrand {
    BAMBU,
    PRUSA   // add your brand here
}
```

### 3. Implement `PrinterAdapter`

```java
@Component
public class PrusaAdapter implements PrinterAdapter {

    @Override
    public PrinterBrand getSupportedBrand() {
        return PrinterBrand.PRUSA;
    }

    @Override
    public void connect(Printer printer) {
        // Start HTTP polling loop, open a socket, etc.
    }

    @Override
    public void disconnect(UUID printerId) { ... }

    @Override
    public PrinterStatusUpdate getStatus(UUID printerId) { ... }

    @Override
    public void pause(UUID printerId) { ... }

    @Override
    public void resume(UUID printerId) { ... }

    @Override
    public void cancel(UUID printerId) { ... }

    // Optional — override to support the periodic "full status refresh" feature.
    // Called by PushAllScheduler on a configurable interval (default 60 s).
    // The default implementation is a no-op, so this is safe to skip.
    @Override
    public void requestFullStatus(UUID printerId) {
        // e.g. trigger an immediate HTTP status poll
    }
}
```

### 4. That's it

`AdapterRegistry` auto-discovers all `@Component` beans that implement `PrinterAdapter` via Spring DI. No registration step needed.

---

## Configuration

Key properties in `backend/cloud-service/src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `printerhub.allowed-origins` | `http://localhost:4200` | Comma-separated list of allowed CORS origins. Set via env var `PRINTERHUB_ALLOWED_ORIGINS` in production. |
| `printerhub.pushall-interval-seconds` | `60` | How often (in seconds) the backend requests a full status dump from each printer. Adjustable at runtime via the dashboard UI (30–300 s). |

Example production override:

```yaml
printerhub:
  allowed-origins: https://app.example.com,https://admin.example.com
  pushall-interval-seconds: 120
```

---

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 (Weeks 1–8) | Local MVP — Bambu adapter, REST API, Angular dashboard | In progress |
| 2 (Weeks 9–13) | Multi-brand (Prusa), public GitHub launch | Planned |
| 3 (Weeks 14–21) | Cloud SaaS — Digital Ocean, Stripe billing, JWT auth, multi-user | Planned |
| 4 (Week 22+) | Community adapters, mobile app (Capacitor), analytics | Planned |

---

## License

MIT
