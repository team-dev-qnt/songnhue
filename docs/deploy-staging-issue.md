# Tổng Hợp Sự Cố & Bài Học Triển Khai Staging
**Ngày ghi nhận:** 25/08/2026
**Mục tiêu:** Lưu trữ các lỗi đã gặp phải trong quá trình deploy lên môi trường Staging (VPS-2) để rút kinh nghiệm và chuẩn bị kịch bản hoàn hảo cho môi trường Production (VPS-1).

---

## 1. Lỗi Xác Thực Khi Kéo Image Từ GitHub Container Registry (GHCR)
- **Mô tả:** Lệnh `docker compose up` thất bại ngay bước đầu tiên với thông báo `unauthorized` khi cố gắng kéo các image (app, admin-app, public-web) từ `ghcr.io`.
- **Nguyên nhân:** Mặc dù repo GitHub có thể là private/public, nhưng VPS chưa được xác thực (đăng nhập) với GHCR.
- **Cách khắc phục:** 
  - Tạo một Personal Access Token (PAT) trên GitHub (có quyền `read:packages`).
  - Chạy lệnh `docker login ghcr.io -u <github-username> -p <PAT>` trên VPS trước khi chạy docker compose.
- **Hành động cho Production:** Đảm bảo VPS Production đã được `docker login` hợp lệ, hoặc sử dụng cơ chế deploy tự động qua GitHub Actions truyền token trực tiếp.

## 2. Thiếu Biến Môi Trường Khởi Tạo PostgreSQL (10-bootstrap.sh)
- **Mô tả:** Container `songnhue-postgres` bị crash liên tục khởi động lại, log báo lỗi biến môi trường không được thiết lập (`DB_ARCHIVER_PASSWORD`, v.v.). Sau đó là lỗi `FATAL: password authentication failed for user "songnhue_app"`.
- **Nguyên nhân:** 
  - Script khởi tạo `10-bootstrap.sh` yêu cầu rất nhiều biến mật khẩu phân quyền (như `DB_PASSWORD`, `DB_ARCHIVER_PASSWORD`, `DB_READONLY_PASSWORD`, `DB_MIGRATION_PASSWORD`). 
  - File `.env` thiếu một số biến này, và nghiêm trọng hơn là file `compose.prod.yml` gốc đã không map biến `DB_PASSWORD: ${DB_PASSWORD}` vào block `environment` của `postgres`.
- **Cách khắc phục:**
  - Bổ sung đầy đủ các biến mật khẩu vào file `.env` trên VPS.
  - Cập nhật lại file `compose.prod.yml` để truyền đủ biến vào container.
- **Hành động cho Production:** 
  - Rà soát kỹ file `.env.production` phải chứa đủ TẤT CẢ các password cho các Role DB.
  - Test kịch bản khởi tạo DB rỗng cẩn thận. (Lưu ý: Script initdb chỉ chạy 1 lần khi volume postgres-data trống).

## 3. Lỗi Quyền Truy Cập Hệ Thống File (File System Permissions)
- **Mô tả:** Container `songnhue-app` (Backend Java) bị crash ngay khi khởi động với 2 ngoại lệ:
  1. `java.nio.file.AccessDeniedException: /opt/songnhue/keys/jwt-private.pem`
  2. `java.io.FileNotFoundException: /var/log/songnhue/app.log (Permission denied)`
- **Nguyên nhân:** 
  - Các thư mục `/opt/songnhue/keys` và `/var/log/songnhue` được tạo bởi user `root` (hoặc user host) trên VPS.
  - Tuy nhiên, ứng dụng Spring Boot bên trong container chạy dưới một non-root user (UID khác). Do đó, container bị hệ điều hành chặn quyền đọc khóa JWT và ghi file log.
- **Cách khắc phục:**
  - Cấp quyền đọc file khóa mã hóa: `sudo chmod -R 644 /opt/songnhue/keys/*` và `sudo chmod 755 /opt/songnhue/keys`.
  - Cấp quyền ghi thư mục log: `sudo chmod 777 /var/log/songnhue`.
- **Hành động cho Production:** 
  - Trước khi start app trên Production, phải chạy sẵn các lệnh cấp quyền thư mục tương ứng. Hoặc cấu hình UID/GID map chính xác giữa host và container trong Dockerfile.

## 4. Báo Cáo "Unhealthy" Giả Của Nginx
- **Mô tả:** Lệnh `docker compose ps` báo Nginx đang ở trạng thái `unhealthy` dù hệ thống web vẫn truy cập bình thường, log Nginx báo cảnh báo `ssl_stapling`.
- **Nguyên nhân:** 
  - Healthcheck của Nginx đang ping vào `http://127.0.0.1/.well-known/acme-challenge/` bằng lệnh `wget --spider`.
  - Vì Certbot không đang trong quá trình gia hạn, thư mục webroot trống, Nginx trả về `404 Not Found` hoặc `403 Forbidden`. Lệnh `wget` hiểu mã lỗi này là "dịch vụ chết" nên đánh dấu unhealthy.
- **Cách khắc phục:** Không ảnh hưởng đến dịch vụ, có thể bỏ qua. Cảnh báo `ssl_stapling` cũng là bình thường với các chứng chỉ cấu hình nội bộ hoặc Cloudflare Origin.
- **Hành động cho Production:** Có thể điều chỉnh lại lệnh healthcheck trong `compose.prod.yml` (ping vào `/` thay vì challenge path) để tránh gây hoang mang cho người giám sát hệ thống.

## 5. Quy Trình Chạy Migrator (Flyway)
- **Mô tả:** Migrator phải được chạy độc lập và trả về kết quả trước khi Backend khởi động.
- **Bài học:** Lệnh `docker compose ... run --rm migrator` hoạt động rất tốt (đã apply thành công 32/32 scripts). Trong Production, đây là bước bắt buộc (CI/CD pipeline cần chạy lệnh này đồng bộ) để đảm bảo DB Schema đã sẵn sàng trước khi nạp `app` mới, vì bản thân `app` đã bị tắt tự động chạy Flyway (`FLYWAY_ENABLED=false`).

---
**Tóm lược:** Quá trình deploy staging đã giúp bộc lộ các điểm yếu về **phân quyền hệ thống (Linux permissions)** và **sự đồng bộ cấu hình (Env mapping)**. Xử lý triệt để các vấn đề này sẽ giúp lần deploy Production sắp tới diễn ra mượt mà và tự động hóa 100%.

---

# Phần II — Đã xử lý gì ở lượt dọn 25/8, và cái gì còn nguyên

> Nguyên nhân gốc từng mục: `.claude/architecture-review.md` §10.40 → §10.50.

| # | Sự cố ở Phần I | Trạng thái | Chốt ở đâu |
|---|---|---|---|
| 1 | GHCR `unauthorized` | **Còn là việc tay** trên máy chủ | Runner tự đăng nhập bằng `GITHUB_TOKEN`. Trên VPS vẫn cần `docker login ghcr.io` một lần — chưa có bước tự động, xem "còn nguyên" bên dưới |
| 2 | Thiếu biến khởi tạo Postgres | **Đã bịt bằng phép kiểm** | `PostgresInitEnvTest` đối chiếu `compose.prod.yml` ↔ `compose.infra.yml` ↔ `10-bootstrap.sh` cả hai chiều thiếu/thừa (§10.41) · `ComposeEnvCompletenessTest` bắt biến compose đòi mà tệp mẫu không có |
| 3 | Quyền hệ thống tệp (`keys/`, `/var/log`) | **Còn nguyên** | Vẫn phải `chmod` tay trước lượt dựng đầu — chưa có bước nào canh |
| 4 | Nginx "unhealthy" giả | **Còn nguyên** | Healthcheck vẫn ping `acme-challenge`. Không ảnh hưởng dịch vụ, nhưng đúng hình dạng luật 8 |
| 5 | Quy trình Migrator | **Đã thành ràng buộc** | `migrator` nay còn `depends_on: minio-init (service_completed_successfully)`, nên thứ tự byte→hàng đúng kể cả khi gõ tay |

## Thêm ba thứ Phần I chưa thấy, vì chúng chỉ lộ ra sau đó

* **`minio-init` chưa từng chạy** — staging chạy suốt với MinIO **không có bucket nào**: mọi lượt
  tải tệp lên, kết xuất báo cáo, kết xuất audit đều hỏng, im lặng. Lộ ra qua một dòng của `mc`
  (§10.49). `NoOrphanServiceTest` nay canh **đồ thị** service, không canh danh sách.
* **Deploy xanh mà cổng rỗng** (§10.45) — smoke test cũ không phân biệt được *cổng có nội dung* với
  *cổng rỗng hợp lệ*. Nay hỏi ba câu, câu thứ ba là phép kiểm duy nhất chứng minh **MinIO có byte**.
* **Bộ seed là một đường chỉ chạy khi được bấm** — nay nằm trong chuỗi migration, chạy ở mỗi lượt
  triển khai staging (§10.50).

## ⬜ Việc còn phải làm trước production

1. **Quyền thư mục trên máy chủ** (mục 3) — `keys/` và `/var/log/songnhue` phải khớp UID của user
   trong container. Cách chữa gốc: cố định UID/GID ở Dockerfile rồi `chown` theo, thay vì `chmod 777`.
2. **`docker login` trên VPS** (mục 1) — hoặc chuyển sang để workflow đẩy image qua SSH.
3. **Healthcheck nginx** (mục 4) — đổi đích sang một đường đại diện cho dịch vụ.
4. **Nợ #46** — 3 context đóng gói image chưa nằm trong `required_status_checks` của `dev`.
