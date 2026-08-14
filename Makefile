# =============================================================================
# songnhue — lệnh dùng chung cho dev và vận hành
#
# Bốn chế độ chạy local — chọn theo việc bạn ĐANG SỬA cái gì
# (chi tiết: docs/run-guideline.md · cài đặt lần đầu: docs/setup-guideline.md):
#
#   make dev-infra   BE + FE native   → Docker chỉ chạy PG + MinIO + Mailpit
#   make dev-be      bạn code FE      → Docker chạy thêm BACKEND
#   make dev-fe      bạn code BE      → Docker chạy thêm 2 APP FE
#   make dev-docker  QA / demo        → Docker chạy TẤT CẢ
#
# Thêm BUILD=1 để build lại image từ mã nguồn hiện tại.
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

LOCAL_ENV  := $(DEPLOY)/env/local.env
# Mọi lệnh chạy local đều đi qua compose.local.yml (file này `include` compose.infra.yml).
# Không bật profile nào = chỉ hạ tầng.
DC_LOCAL   := $(COMPOSE) --env-file $(LOCAL_ENV) -f $(DEPLOY)/compose.local.yml

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

define need_local_env
	@test -f "$(LOCAL_ENV)" || { \
		echo ""; \
		echo "  ✗ Chưa có $(LOCAL_ENV)"; \
		echo "    Tạo bằng:  make env"; \
		echo "    Rồi sửa giá trị theo docs/setup-guideline.md"; \
		echo ""; \
		exit 1; \
	}
endef

define need_fe_app
	@test -f "$(FRONTEND)/$(1)/package.json" || { \
		echo ""; \
		echo "  ✗ Chưa có frontend/$(1)/ — không build được image cho service này."; \
		echo "    Hạng mục tạo thư mục này: $(2)"; \
		echo "    Xem tiến độ: .claude/phase0-tracking.md"; \
		echo ""; \
		echo "    Trong lúc chờ, dùng: make dev-be   (hạ tầng + backend trong Docker)"; \
		echo ""; \
		exit 1; \
	}
endef

# BUILD=1 → build lại image từ mã nguồn hiện tại trước khi chạy
BUILD_FLAG := $(if $(BUILD),--build,)

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
# Nguyên tắc: service nào ĐANG SỬA thì chạy native, còn lại đẩy vào Docker.
# Chi tiết từng vai trò: docs/run-guideline.md
#
# Thêm BUILD=1 vào bất kỳ lệnh nào để build lại image từ mã nguồn hiện tại.

.PHONY: dev-infra
dev-infra: ## [BE+FE native] Chỉ hạ tầng: PostgreSQL, MinIO, Mailpit
	$(call need_local_env)
	$(DC_LOCAL) up -d
	@echo "✓ Hạ tầng đã chạy. Khởi động backend: make dev-native"

.PHONY: dev-be
dev-be: ## [bạn code FE] Hạ tầng + BACKEND trong Docker
	$(call need_local_env)
	$(DC_LOCAL) --profile backend up -d $(BUILD_FLAG)
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 echo "✓ Backend (Docker): http://localhost:$${DOCKER_APP_PORT:-18080}  ·  Log: make logs"; \
	 echo "  FE native trỏ VITE_API_BASE_URL vào cổng này."

.PHONY: dev-fe
dev-fe: ## [bạn code BE] Hạ tầng + 2 APP FE trong Docker (FE gọi backend NATIVE)
	$(call need_local_env)
	$(call need_fe_app,admin-app,WS-8 / T8.1)
	$(call need_fe_app,public-web,WS-9 / T9.1)
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 api="http://localhost:$${APP_PORT:-8080}/api/v1"; \
	 echo "  → FE trong Docker sẽ gọi backend NATIVE tại $$api"; \
	 VITE_API_BASE_URL="$$api" NEXT_PUBLIC_API_BASE_URL="$$api" \
	 NEXT_PUBLIC_SITE_URL="http://localhost:$${DOCKER_PUBLIC_WEB_PORT:-13000}" \
	 $(DC_LOCAL) --profile admin --profile public up -d $(BUILD_FLAG)
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 echo "✓ admin-app: http://localhost:$${DOCKER_ADMIN_APP_PORT:-15173}  ·  public-web: http://localhost:$${DOCKER_PUBLIC_WEB_PORT:-13000}"
	@echo "  Nhớ khởi động backend native: make dev-native"

.PHONY: dev-docker
dev-docker: ## [QA / demo] TOÀN BỘ stack trong Docker (FE gọi backend DOCKER)
	$(call need_local_env)
	$(call need_fe_app,admin-app,WS-8 / T8.1)
	$(call need_fe_app,public-web,WS-9 / T9.1)
	$(DC_LOCAL) --profile full up -d $(BUILD_FLAG)
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 echo "✓ backend: http://localhost:$${DOCKER_APP_PORT:-18080}  ·  admin-app: http://localhost:$${DOCKER_ADMIN_APP_PORT:-15173}  ·  public-web: http://localhost:$${DOCKER_PUBLIC_WEB_PORT:-13000}"

.PHONY: dev-native
dev-native: ## Chạy backend NATIVE từ máy (cần `make dev-infra` trước)
	cd $(BACKEND) && ./mvnw -pl app -am spring-boot:run

.PHONY: build-images
build-images: ## Build lại image backend từ mã nguồn local (không chạy)
	$(call need_local_env)
	$(DC_LOCAL) --profile backend build

.PHONY: ps
ps: ## Liệt kê container đang chạy của dự án
	$(DC_LOCAL) ps

.PHONY: down
down: ## Dừng mọi container của dự án (GIỮ dữ liệu)
	@$(DC_LOCAL) --profile full down --remove-orphans
	@echo "✓ Đã dừng — dữ liệu trong volume vẫn còn"

.PHONY: reset-db
reset-db: ## ⚠ XÓA SẠCH volume DB + MinIO rồi dựng lại từ đầu
	@echo "  ⚠ Thao tác này XÓA TOÀN BỘ dữ liệu local (PostgreSQL + MinIO)."
	@printf "    Gõ 'xoa' để xác nhận: " && read ans && [ "$$ans" = "xoa" ] || { echo "    Đã hủy."; exit 1; }
	@$(DC_LOCAL) --profile full down -v --remove-orphans
	@echo "✓ Đã xóa. Dựng lại: make dev-infra (script init sẽ chạy lại)"

.PHONY: logs
logs: ## Xem log stack local
	$(DC_LOCAL) --profile full logs -f --tail=100

# --- Database ----------------------------------------------------------------
.PHONY: migrate
migrate: ## Chạy Flyway migration trong Docker (service `migrator` riêng)
	$(call need_file,$(DEPLOY)/compose.$(ENV).yml,WS-11 / T11.3)
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DEPLOY)/compose.$(ENV).yml run --rm migrator

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

.PHONY: gen-keys
gen-keys: ## Sinh cặp khoá RSA ký access token cho môi trường local (WS-5)
	@# Khoá là bí mật: .gitignore đã chặn *.pem, và trên máy chủ khoá nằm ở
	@# /opt/songnhue/keys/ — NGOÀI bản sao lưu CSDL (architecture-review.md §6.5).
	@test -f "$(DEPLOY)/keys/jwt-private.pem" \
		&& { echo "  Khoá đã tồn tại — KHÔNG ghi đè."; \
		     echo "  Ghi đè là mọi access token và phiên đang sống đều mất hiệu lực."; \
		     echo "  Muốn sinh lại: xoá $(DEPLOY)/keys/jwt-*.pem rồi chạy lại."; } \
		|| { mkdir -p "$(DEPLOY)/keys" \
		     && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
		            -out "$(DEPLOY)/keys/jwt-private.pem" 2>/dev/null \
		     && openssl rsa -pubout -in "$(DEPLOY)/keys/jwt-private.pem" \
		            -out "$(DEPLOY)/keys/jwt-public.pem" 2>/dev/null \
		     && chmod 600 "$(DEPLOY)/keys/jwt-private.pem" \
		     && echo "✓ Đã sinh $(DEPLOY)/keys/jwt-private.pem (chmod 600) và jwt-public.pem"; }

.PHONY: doctor
doctor: ## Kiểm tra máy đã đủ điều kiện chạy dự án chưa
	@echo ""
	@echo "  Công cụ"
	@command -v docker  >/dev/null && echo "    ✓ docker   $$(docker --version | cut -d' ' -f3 | tr -d ,)" || echo "    ✗ docker   — BẮT BUỘC"
	@docker compose version >/dev/null 2>&1 && echo "    ✓ compose  $$(docker compose version --short)" || echo "    ✗ docker compose — BẮT BUỘC"
	@command -v java >/dev/null && echo "    ✓ java     $$(java -version 2>&1 | head -1 | awk -F'\"' '{print $$2}')  (chỉ cần khi chạy backend native)" || echo "    ⬜ java   — chỉ cần khi chạy backend native"
	@command -v node >/dev/null && echo "    ✓ node     $$(node --version)  (chỉ cần khi chạy frontend native)" || echo "    ⬜ node   — chỉ cần khi chạy frontend native"
	@command -v psql >/dev/null && echo "    ✓ psql     $$(psql --version | cut -d' ' -f3)  (cho make migrate-info)" || echo "    ⬜ psql   — cần cho make migrate-info"
	@echo ""
	@echo "  Cấu hình"
	@test -f "$(LOCAL_ENV)" && echo "    ✓ $(LOCAL_ENV)" || echo "    ✗ Chưa có deploy/env/local.env — chạy: make env"
	@test -f "$(LOCAL_ENV)" && awk -F= '/^(DB_PASSWORD|DB_MIGRATION_PASSWORD|MINIO_SECRET_KEY)=/ && ($$2 == "" || $$2 ~ /^ *#/) {print "    ✗ Chưa điền giá trị: " $$1}' "$(LOCAL_ENV)" || true
	@test -f "$(LOCAL_ENV)" && grep -q '^AES_KEY_V1=REPLACE_ME' "$(LOCAL_ENV)" \
		&& { echo "    ✗ AES_KEY_V1 còn là placeholder — app sẽ KHÔNG khởi động."; \
		     echo "      Sinh khoá:  openssl rand -base64 32"; } \
		|| test ! -f "$(LOCAL_ENV)" || echo "    ✓ AES_KEY_V1 đã đặt"
	@test -f "$(LOCAL_ENV)" && { grep -q '^JWT_PRIVATE_KEY_PATH=' "$(LOCAL_ENV)" && \
		{ test -f "$(DEPLOY)/keys/jwt-private.pem" && echo "    ✓ Khoá JWT đã sinh" \
		  || echo "    ⬜ Chưa sinh khoá JWT (chỉ cần từ WS-5) — xem docs/setup-guideline.md mục 3"; }; } || true
	@echo ""
	@echo "  Cổng Docker publish ra host (trùng cổng = compose báo lỗi khi khởi động)"
	@set -a; [ -f "$(LOCAL_ENV)" ] && . "$(LOCAL_ENV)"; set +a; \
	 for p in "PostgreSQL:$${DOCKER_DB_PORT:-15432}" "MinIO:$${DOCKER_MINIO_PORT:-19000}" \
	          "MinIO console:$${DOCKER_MINIO_CONSOLE_PORT:-19001}" "SMTP Mailpit:$${DOCKER_SMTP_PORT:-11025}" \
	          "Mailpit UI:$${DOCKER_MAILPIT_UI_PORT:-18025}" "Backend:$${DOCKER_APP_PORT:-18080}" \
	          "admin-app:$${DOCKER_ADMIN_APP_PORT:-15173}" "public-web:$${DOCKER_PUBLIC_WEB_PORT:-13000}"; do \
	     name="$${p%%:*}"; port="$${p##*:}"; \
	     if lsof -nP -iTCP:$$port -sTCP:LISTEN >/dev/null 2>&1; then \
	         owner=$$(lsof -nP -iTCP:$$port -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $$1}'); \
	         if docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null | grep -q ":$$port->"; then \
	             echo "    ✓ $$port  $$name — đang do container của dự án giữ"; \
	         else \
	             echo "    ✗ $$port  $$name — ĐANG BỊ CHIẾM bởi '$$owner'. Đổi cổng trong deploy/env/local.env"; \
	         fi; \
	     else echo "    ✓ $$port  $$name — trống"; fi; \
	 done
	@echo ""

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
