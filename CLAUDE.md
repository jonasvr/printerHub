# PrinterHub — Claude instructions

## README upkeep
After any session where the following change, update README.md to match:
- A new service is added to `docker/local/docker-compose.yml` → update Dev URLs table
- A new `make` target is added to `Makefile` → update the quick-start section if user-facing
- A new Maven module is added under `backend/adapters/` → update project structure map
- The `PrinterAdapter` interface changes → update the "Adding a new adapter" code example
- A roadmap phase is marked complete or scope changes → update Roadmap table

## Explanations
- Explain changes after each file or logical group, not inline as comments
- Focus on *why* a decision was made, not just *what* the code does
- The user is learning — flag non-obvious patterns (e.g. open/closed principle, soft-delete, UUID keys)

## Code style
- Java: follow existing conventions in the file; Lombok where already used
- Angular: standalone components only, no NgModule
- No new comments unless logic is genuinely non-obvious
- No docstrings on getters/setters/simple delegators

## Dashboard card layouts
The dashboard has 3 card layout views (layout 1 = card grid, layout 2 = compact rows, layout 3 = status hero), all in `dashboard.component.html`.
When adding, removing, or adjusting any data field displayed on a printer card (e.g. a new temperature, a new status flag, a new action button), check and update **all three layouts** so they stay in sync.
Helper methods shared by layouts 2 & 3 live on `DashboardComponent` (e.g. `st()`, `stateOf()`, `isPrinting()`, etc.) — add new helpers there, not inline in the template.

## What NOT to do
- Do not auto-commit
- Do not add error handling for impossible cases
- Do not over-engineer single-use code into abstractions
- Do not add features beyond what was asked
