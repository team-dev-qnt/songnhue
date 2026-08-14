# =============================================================================
# songnhue — lệnh dùng chung cho dev và vận hành
#
# Hai lối chạy local (conventions.md §1.7):
#   make dev-infra   → chỉ PG + MinIO + MailHog trong Docker, app chạy NATIVE từ IDE
#   make dev-docker  → TOÀN BỘ stack trong Docker
#
# `make` không tham số = hiện danh sách lệnh.
# =============================================================================

SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT       := $(shell cd "$(dir $(lastword $(MAKEFILE_LIST)))" && pwd)
BACKEND    := $(ROOT)/backend
FRONTEND   := $(ROOT)/frontend
DEPLOY     := $(ROOT)/deploy
MVNW       := $(BACKEND)/mvnw
COMPOSE    := docker compose

ENV        ?= local
ENV_FILE   := $(DEPLOY)/env/$(ENV).env

# Kiểm tra file compose tồn tại — các file này do WS-3/WS-11 tạo
define need_file
	@test -f "$(1)" || { \
		echo ""; \
		echo "  ✗ Chưa có file: $(1)"; \
		echo "    Hạng mục tạo file này: $(2)"; \
		echo "    Xem tiến độ: .claude/phase0-tracking.md"; \
		echo ""; \
		exit 1; \
	}
endef

# -----------------------------------------------------------------------------
.PHONY: help
help: ## Hiện danh sách lệnh
	@echo ""
	@echo "  songnhue — các lệnh khả dụng"
	@echo ""
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "  Biến: ENV=local|staging|prod   (mặc định: local)"
	@echo ""

# --- Môi trường chạy ---------------------------------------------------------
.PHONY: dev-infra
dev-infra: ## Bật hạ tầng (PG+PostGIS, MinIO, MailHog) — app chạy native
	$(call need_file,$(DEPLOY)/compose.infra.yml,WS-3 / T3.1)
	$(COMPOSE) -f $(DEPLOY)/compose.infra.yml up -d
	@echo "✓ Hạ tầng đã chạy. Khởi động app: make dev-native"

.PHONY: dev-native
dev-native: ## Chạy backend native (cần `make dev-infra` trước)
	cd $(BACKEND) && ./mvnw -pl app -am spring-boot:run

.PHONY: dev-docker
dev-docker: ## Chạy TOÀN BỘ stack trong Docker (infra + app + 2 FE)
	$(call need_file,$(DEPLOY)/compose.local.yml,WS-3 / T3.2)
	$(COMPOSE) -f $(DEPLOY)/compose.local.yml up -d --build
	@echo "✓ Stack đã chạy. Log: make logs"

.PHONY: down
down: ## Dừng mọi container của dự án
	-@$(COMPOSE) -f $(DEPLOY)/compose.local.yml down 2>/dev/null
	-@$(COMPOSE) -f $(DEPLOY)/compose.infra.yml down 2>/dev/null
	@echo "✓ Đã dừng"

.PHONY: logs
logs: ## Xem log stack local
	$(COMPOSE) -f $(DEPLOY)/compose.local.yml logs -f --tail=100

# --- Database ----------------------------------------------------------------
.PHONY: migrate
migrate: ## Chạy Flyway migration (service `migrator` riêng, không qua app)
	$(call need_file,$(DEPLOY)/compose.$(ENV).yml,WS-3 / T3.2 · WS-11 / T11.4)
	$(COMPOSE) -f $(DEPLOY)/compose.$(ENV).yml run --rm migrator

.PHONY: migrate-info
migrate-info: ## Xem trạng thái migration đã áp dụng
	@test -f "$(ENV_FILE)" || { echo "  ✗ Chưa có $(ENV_FILE) — chạy: make env"; exit 1; }
	@set -a; . "$(ENV_FILE)"; set +a; \
	 PGPASSWORD="$$DB_MIGRATION_PASSWORD" psql -h "$$DB_HOST" -p "$$DB_PORT" \
	     -U "$$DB_MIGRATION_USER" -d "$$DB_NAME" -P pager=off -c \
	     "SELECT installed_rank AS \"#\", version, description, success, installed_on \
	        FROM flyway_schema_history ORDER BY installed_rank;"

.PHONY: migrate-native
migrate-native: ## Chạy migration từ máy (app chạy native, cần `make dev-infra` trước)
	@test -f "$(ENV_FILE)" || { echo "  ✗ Chưa có $(ENV_FILE) — chạy: make env"; exit 1; }
	cd $(BACKEND) && ./mvnw -B -q -pl app -am package -DskipTests
	@set -a; . "$(ENV_FILE)"; set +a; \
	 java -jar $(BACKEND)/app/target/songnhue-app.jar --spring.profiles.active=migrate

.PHONY: db-verify-audit
db-verify-audit: ## Kiểm tra tính toàn vẹn chuỗi hash của audit_logs (rỗng = nguyên vẹn)
	@test -f "$(ENV_FILE)" || { echo "  ✗ Chưa có $(ENV_FILE) — chạy: make env"; exit 1; }
	@set -a; . "$(ENV_FILE)"; set +a; \
	 PGPASSWORD="$$DB_PASSWORD" psql -h "$$DB_HOST" -p "$$DB_PORT" \
	     -U "$$DB_USER" -d "$$DB_NAME" -P pager=off \
	     -c "SELECT * FROM core_verify_audit_chain();"

# --- Build & kiểm thử --------------------------------------------------------
.PHONY: build
build: ## Build backend (bỏ qua test)
	cd $(BACKEND) && ./mvnw -B clean package -DskipTests

.PHONY: test
test: test-be test-fe ## Chạy toàn bộ test (BE + FE)

.PHONY: test-be
test-be: ## Test backend: unit + Testcontainers + ArchUnit
	cd $(BACKEND) && ./mvnw -B clean verify

.PHONY: test-fe
test-fe: ## Test frontend
	cd $(FRONTEND) && npm run test --if-present

# --- Chất lượng mã -----------------------------------------------------------
.PHONY: format
format: ## Tự động định dạng lại mã (BE + FE)
	cd $(BACKEND) && ./mvnw -q spotless:apply
	cd $(FRONTEND) && npm run format
	@echo "✓ Đã định dạng"

.PHONY: lint
lint: lint-be lint-fe ## Kiểm tra định dạng + quy tắc (không sửa)

.PHONY: lint-be
lint-be: ## Backend: Spotless check + Checkstyle
	cd $(BACKEND) && ./mvnw -B spotless:check checkstyle:check

.PHONY: lint-fe
lint-fe: ## Frontend: ESLint + Prettier check
	cd $(FRONTEND) && npm run lint && npm run format:check

# --- Backup / Restore (WS-7) -------------------------------------------------
.PHONY: backup
backup: ## Tạo bản backup thủ công (pg_dump + checksum)
	$(call need_file,$(DEPLOY)/backup/backup.sh,WS-7 / T7.1)
	ENV=$(ENV) $(DEPLOY)/backup/backup.sh

.PHONY: restore
restore: ## Khôi phục từ bản backup — hỏi xác nhận trước khi ghi đè
	$(call need_file,$(DEPLOY)/backup/restore.sh,WS-7 / T7.5)
	ENV=$(ENV) $(DEPLOY)/backup/restore.sh

# --- Tiện ích ----------------------------------------------------------------
.PHONY: env
env: ## Tạo file env từ mẫu (ENV=local|staging|prod)
	@test -f "$(ENV_FILE)" \
		&& echo "  File $(ENV_FILE) đã tồn tại — không ghi đè." \
		|| { cp "$(ENV_FILE).example" "$(ENV_FILE)" && chmod 600 "$(ENV_FILE)" \
		     && echo "✓ Đã tạo $(ENV_FILE) — sửa giá trị trước khi chạy"; }

.PHONY: hooks
hooks: ## Bật git hook kiểm tra định dạng commit message
	git config core.hooksPath .githooks
	@chmod +x .githooks/* 2>/dev/null || true
	@echo "✓ Đã bật .githooks"

.PHONY: clean
clean: ## Xóa artifact build
	cd $(BACKEND) && ./mvnw -q clean
	rm -rf $(FRONTEND)/*/dist $(FRONTEND)/*/.next
	@echo "✓ Đã dọn"
