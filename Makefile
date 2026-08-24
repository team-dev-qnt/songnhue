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
# Image LUÔN được build lại từ mã nguồn hiện tại; thêm NOBUILD=1 để bỏ qua.
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

# Build lại image từ mã nguồn hiện tại — MẶC ĐỊNH BẬT. `NOBUILD=1` để bỏ qua.
#
# ⚠ Trước 17/8 mặc định là NGƯỢC LẠI (phải gõ BUILD=1 mới build lại), và nó đã gây ra
#   đúng kiểu hỏng im lặng mà dự án này gặp nhiều lần: image `songnhue-app:local` nằm
#   nguyên từ WS-3, tức là bản dựng TRƯỚC khi có controller nào. Container lên, healthcheck
#   `/actuator/health` xanh, mà MỌI endpoint `/api/v1/**` trả 404 — nhìn từ ngoài thì "hệ
#   thống chạy tốt". Không ai phát hiện suốt WS-4→WS-8.
#
#   Đo thật: build lại khi mã nguồn KHÔNG đổi tốn ~10 giây (Docker dùng lại toàn bộ lớp
#   cache). Đó là cái giá quá rẻ so với việc chạy thử trên một bản dựng cũ ba ngày.
BUILD_FLAG := $(if $(NOBUILD),,--build)

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
# Mọi lệnh dev-* đều build lại image trước khi chạy (~10s khi mã không đổi).
# Thêm NOBUILD=1 nếu chắc chắn image đang khớp mã nguồn.

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
	@# ⚠⚠ Ghi đè ĐÍCH CHUYỂN TIẾP, tuyệt đối không ghi đè `VITE_API_BASE_URL`.
	@#
	@# Bản trước tiêm `VITE_API_BASE_URL=http://localhost:8080/api/v1` vào lúc build,
	@# tức là bảo trình duyệt gọi thẳng sang cổng 8080 trong khi trang nằm ở cổng
	@# 15173 — khác origin, backend không cấu hình CORS, preflight trả 403 và cả
	@# giao diện chết. Ở chế độ này FE vẫn gọi `/api/v1` cùng origin; thứ duy nhất
	@# khác `dev-docker` là nginx/Next phải trỏ ra backend NATIVE trên máy chủ chứ
	@# không phải service `app` trong mạng Docker. Cả hai biến dưới đọc LÚC CHẠY.
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 host="http://host.docker.internal:$${APP_PORT:-8080}"; \
	 echo "  → FE trong Docker sẽ chuyển tiếp /api sang backend NATIVE tại $$host"; \
	 API_UPSTREAM="$$host" API_INTERNAL_BASE_URL="$$host/api/v1" \
	 VITE_API_BASE_URL= NEXT_PUBLIC_API_BASE_URL= \
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
	@# ⚠ `--build` cũng bắt buộc ở đây, cùng lý do với BUILD_FLAG ở trên và nặng hơn một bậc:
	@#   migration nằm trong jar, nên image cũ nghĩa là chạy Flyway của bản mã CŨ. Đã xảy ra
	@#   thật (17/8): thêm một migration rồi `make migrate` báo "✓ Migration hoàn tất" với đúng
	@#   số bản cũ — thành công theo mọi dấu hiệu nhìn thấy được, mà migration mới thì không chạy.
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DEPLOY)/compose.$(ENV).yml run --rm --build migrator

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

# --- Chạy y hệt CI ở máy -----------------------------------------------------
#
# ⚠⚠ VÌ SAO CẦN MỘT LỆNH RIÊNG thay vì "nhớ chạy make lint và make test".
#
#   Ngày 24/8 một PR bị CI chặn ở `format:check` sau khi đã báo "lint sạch" —
#   vì `npm run lint` CHỈ chạy ESLint, còn Prettier là script thứ hai. Chạy một
#   nửa cổng kiểm rồi kết luận đã qua là đúng hình dạng lỗi mà dự án này gặp
#   nhiều lần: cơ chế có mặt, nhưng lượt kiểm không đi qua nó.
#
#   Thứ tự dưới đây khớp `.github/workflows/ci.yml`, và cố ý xếp theo "cái nào
#   phát hiện lỗi rẻ nhất thì chạy trước": định dạng (giây) → kiểu → test →
#   build. Sai định dạng thì biết sau 30 giây thay vì chờ hết Testcontainers.
#
#   ⛔ KHÔNG thay được lượt chạy CI thật: quét CVE và đóng gói image lên GHCR
#      chỉ chạy trên runner. Lệnh này bao phủ 2 job `backend` + `frontend`.
.PHONY: ci-local
ci-local: ## Chạy đúng trình tự cổng kiểm của CI (trừ CVE scan + đóng gói image)
	@echo ""
	@echo "  [1/8] Backend — Spotless + Checkstyle"
	@cd $(BACKEND) && ./mvnw -B -ntp spotless:check checkstyle:check -q
	@echo "  [2/8] Frontend — ESLint"
	@cd $(FRONTEND) && npm run lint --silent
	@echo "  [3/8] Frontend — Prettier"
	@cd $(FRONTEND) && npm run format:check --silent
	@echo "  [4/8] Frontend — kiểm kiểu"
	@cd $(FRONTEND) && npm run typecheck --silent
	@echo "  [5/8] Frontend — test"
	@cd $(FRONTEND) && npm test --silent
	@echo "  [6/8] Frontend — build admin-app"
	@cd $(FRONTEND) && npm run build --workspace admin-app --silent
	@echo "  [7/8] Frontend — build public-web"
	@cd $(FRONTEND) && npm run build --workspace public-web --silent
	@echo "  [8/8] Backend — verify (test + ArchUnit + cổng bao phủ)"
	@cd $(BACKEND) && ./mvnw -B -ntp verify
	@echo ""
	@echo "  ✓ Mọi cổng kiểm CI chạy ở máy đều xanh."
	@echo "    Còn lại chỉ chạy trên runner: quét CVE, đóng gói image GHCR."
	@echo ""

# --- Backup / Restore (WS-7) -------------------------------------------------
# ⚠ Đây là ĐƯỜNG THỦ CÔNG, dùng khi ứng dụng không chạy được. Đường bình thường
#   là job 02:00 hằng đêm và nút trên màn hình M5.10 — cả hai ghi vào cùng một
#   sổ đăng ký `system_backups`.

.PHONY: backup
backup: ## Tạo bản backup thủ công (pg_dump + checksum + ghi sổ)
	$(call need_file,$(DEPLOY)/backup/backup.sh,WS-7 / T7.1)
	ENV=$(ENV) $(DEPLOY)/backup/backup.sh

.PHONY: restore
restore: ## ⚠ Khôi phục từ bản backup — GHI ĐÈ TOÀN BỘ, hỏi xác nhận trước
	$(call need_file,$(DEPLOY)/backup/restore.sh,WS-7 / T7.5)
	ENV=$(ENV) $(DEPLOY)/backup/restore.sh

.PHONY: backup-verify
backup-verify: ## Kiểm bản backup mới nhất KHÔNG chứa khoá AES/JWT (DoD 13d)
	@test -f "$(ENV_FILE)" || { echo "  ✗ Chưa có $(ENV_FILE) — chạy: make env"; exit 1; }
	@set -a; . "$(ENV_FILE)"; set +a; \
	 latest="$$(find "$$BACKUP_DIR" -maxdepth 1 -name 'songnhue-*.dump' -type f | sort | tail -1)"; \
	 test -n "$$latest" || { echo "  ✗ Chưa có bản backup nào trong $$BACKUP_DIR — chạy: make backup"; exit 1; }; \
	 echo "  Kiểm: $$latest"; \
	 $(DEPLOY)/backup/verify-no-keys.sh "$$latest"

.PHONY: obs-up
obs-up: ## Dựng Prometheus + Grafana (mô phỏng VM-3 tại máy local)
	$(call need_file,$(DEPLOY)/compose.observability.yml,WS-7 / T7.9)
	@set -a; . "$(LOCAL_ENV)"; set +a; \
	 GRAFANA_ADMIN_PASSWORD="$${GRAFANA_ADMIN_PASSWORD:-changeme_local}" \
	 PROD_APP_TARGET="$${PROD_APP_TARGET:-host.docker.internal:8080}" \
	 STAGING_APP_TARGET="$${STAGING_APP_TARGET:-host.docker.internal:8080}" \
	 $(COMPOSE) -f $(DEPLOY)/compose.observability.yml up -d
	@echo "✓ Prometheus: http://localhost:19090  ·  Grafana: http://localhost:13001"

.PHONY: obs-down
obs-down: ## Dừng stack giám sát
	$(COMPOSE) -f $(DEPLOY)/compose.observability.yml down

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
hooks: ## Bật git hook: định dạng commit message + chặn nhánh lỗi thời sau squash
	git config core.hooksPath .githooks
	@chmod +x .githooks/* 2>/dev/null || true
	@echo "✓ Đã bật .githooks (commit-msg + pre-push)"

.PHONY: branch-check
branch-check: ## Nhánh hiện tại có lỗi thời sau squash merge không (pre-push tự chạy)
	@./.githooks/check-branch-freshness.sh

.PHONY: seed-portal
seed-portal: ## Seed dữ liệu mẫu cho Cổng thông tin qua REST API và verify luồng E2E
	@npx tsx tools/seeder/seed-portal-data.ts

.PHONY: clean
clean: ## Xóa artifact build
	cd $(BACKEND) && ./mvnw -q clean
	rm -rf $(FRONTEND)/*/dist $(FRONTEND)/*/.next
	@echo "✓ Đã dọn"
