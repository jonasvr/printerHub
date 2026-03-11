# PrinterHub — top-level Makefile
#
# Usage:
#   make help          list all targets
#   make up            start Docker services (Postgres + Mosquitto)
#   make dev           start backend + frontend in parallel (requires tmux or two terminals)
#   make backend       build & run the Spring Boot app
#   make frontend      install deps (if needed) + start Angular dev server

.PHONY: help up down logs ps \
        backend backend-build backend-run \
        frontend frontend-install frontend-dev \
        build clean env-check

# ── Colours for help output ────────────────────────────────────────────────
CYAN  := \033[36m
RESET := \033[0m

# ── Paths ──────────────────────────────────────────────────────────────────
DOCKER_DIR  := docker/local
BACKEND_DIR := backend
FRONTEND_DIR := frontend

# ── Default target ─────────────────────────────────────────────────────────
.DEFAULT_GOAL := help

help: ## Show this help
	@echo ""
	@echo "PrinterHub development commands:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*##"}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'
	@echo ""

# ── Infrastructure ─────────────────────────────────────────────────────────
up: env-check ## Start Docker services (Postgres + Mosquitto)
	cd $(DOCKER_DIR) && docker compose up -d
	@echo "Postgres  → localhost:5432"
	@echo "Mosquitto → localhost:1883 (MQTT) / localhost:9001 (WS)"

down: ## Stop Docker services
	cd $(DOCKER_DIR) && docker compose down

down-v: ## Stop Docker services AND delete volumes (wipes DB data)
	cd $(DOCKER_DIR) && docker compose down -v

logs: ## Tail Docker service logs
	cd $(DOCKER_DIR) && docker compose logs -f

ps: ## Show running Docker containers
	cd $(DOCKER_DIR) && docker compose ps

# ── Backend ────────────────────────────────────────────────────────────────
backend-build: ## Compile all Maven modules (skips tests)
	cd $(BACKEND_DIR) && mvn clean install -DskipTests

backend-run: ## Run the Spring Boot app (requires Postgres running)
	cd $(BACKEND_DIR)/cloud-service && mvn spring-boot:run

backend-test: ## Run backend tests
	cd $(BACKEND_DIR) && mvn test

backend: backend-build backend-run ## Build then run the backend

# ── Frontend ───────────────────────────────────────────────────────────────
frontend-install: ## Install npm dependencies
	cd $(FRONTEND_DIR) && npm install

frontend-run: ## Start Angular dev server (port 4200)
	cd $(FRONTEND_DIR) && npx ng serve

frontend-build: ## Build Angular for production
	cd $(FRONTEND_DIR) && npx ng build --configuration production

frontend: frontend-install frontend-dev ## Install deps then start dev server

# ── Combined shortcuts ─────────────────────────────────────────────────────
dev: up ## Start infra + print instructions for backend & frontend
	@echo ""
	@echo "Infrastructure is up. Now open two more terminals and run:"
	@echo ""
	@echo "  Terminal 2:  make backend-run"
	@echo "  Terminal 3:  make frontend-dev"
	@echo ""
	@echo "Or, if you have 'concurrently' installed globally:"
	@echo "  make dev-all"
	@echo ""

dev-all: up ## Start everything concurrently (requires: npm i -g concurrently)
	npx concurrently \
		--names "backend,frontend" \
		--prefix-colors "cyan,magenta" \
		"cd $(BACKEND_DIR)/cloud-service && mvn spring-boot:run" \
		"cd $(FRONTEND_DIR) && npx ng serve"

# ── Utilities ──────────────────────────────────────────────────────────────
build: backend-build frontend-build ## Build everything for production

kill-backend: ## Kill whatever is running on port 8080
	-@netstat -ano 2>/dev/null | grep ':8080 ' | awk '{print $$5}' | head -1 | xargs -r -I{} taskkill //PID {} //F 2>/dev/null || true

clean: ## Remove build artifacts
	cd $(BACKEND_DIR) && mvn clean
	rm -rf $(FRONTEND_DIR)/dist $(FRONTEND_DIR)/.angular

env-check: ## Verify .env file exists, create from example if not
	@if [ ! -f $(DOCKER_DIR)/.env ]; then \
		echo "No .env found — copying from .env.example"; \
		cp $(DOCKER_DIR)/.env.example $(DOCKER_DIR)/.env; \
	fi
