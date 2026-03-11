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
}
```

### 4. That's it

`AdapterRegistry` auto-discovers all `@Component` beans that implement `PrinterAdapter` via Spring DI. No registration step needed.

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
