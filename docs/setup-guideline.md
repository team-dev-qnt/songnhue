# Hướng dẫn cài đặt — songnhue

Tài liệu này lo phần **dựng máy lần đầu**. Chạy hằng ngày thì xem
[`run-guideline.md`](run-guideline.md).

---

## 1. Cần cài gì

**Bắt buộc với mọi người:**

| Công cụ | Phiên bản | Vì sao |
|---|---|---|
| Docker + Docker Compose | Docker ≥ 24, Compose ≥ 2.20 | Toàn bộ hạ tầng chạy trong container |
| Git | bất kỳ | |

**Chỉ cần khi bạn chạy service đó *native*:**

| Công cụ | Phiên bản | Ai cần |
|---|---|---|
| JDK 21 | Temurin 21 | Người sửa **backend** |
| Node.js | 22 LTS | Người sửa **frontend** |
| `psql` | 15+ | Ai muốn dùng `make migrate-info`, `make db-verify-audit` |

> **Không cần cài Maven.** Repo dùng `mvnw` wrapper.
>
> **Không cần cài JDK nếu bạn chỉ làm FE** — backend chạy trong Docker và
> *biên dịch bên trong container*. Ngược lại cũng vậy: người làm BE không cần
> Node.

Kiểm tra máy đã đủ chưa:

```bash
make doctor
```

---

## 2. Sáu bước dựng máy

```bash
git clone <repo> && cd songnhue

make hooks      # 1. bật hook kiểm tra commit message
make env        # 2. tạo deploy/env/local.env từ mẫu
# 3. sinh khóa (mục 3 bên dưới)
# 4. sửa deploy/env/local.env (mục 4 bên dưới)
make doctor     # 5. kiểm tra công cụ + cổng trống
make dev-infra  # 6. bật hạ tầng
```

Chọn chế độ chạy phù hợp với vai trò của bạn ở
[`run-guideline.md`](run-guideline.md).

---

## 3. Sinh khóa mã hóa

Khóa **không nằm trong repo** và không có giá trị mặc định — mỗi máy tự sinh.

```bash
# JWT ký RS256
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out deploy/keys/jwt-private.pem
openssl rsa -pubout -in deploy/keys/jwt-private.pem \
    -out deploy/keys/jwt-public.pem
chmod 600 deploy/keys/jwt-private.pem

# AES-256-GCM cho cột nhạy cảm — chép kết quả vào AES_KEY_V1
openssl rand -base64 32
```

`.gitignore` đã chặn `*.pem` / `*.key`. **Đừng bao giờ dùng `git add -f`** với
những file này.

---

## 4. Sửa `deploy/env/local.env`

File sinh ra từ `local.env.example` đã chạy được ngay ở local. Ba chỗ nên đổi:

| Biến | Việc cần làm |
|---|---|
| `AES_KEY_V1` | Dán chuỗi base64 vừa sinh ở mục 3 |
| `DB_PASSWORD`, `DB_MIGRATION_PASSWORD`, `DB_ARCHIVER_PASSWORD`, `DB_READONLY_PASSWORD`, `POSTGRES_PASSWORD`, `MINIO_SECRET_KEY` | Đổi khỏi `changeme_local` nếu bạn muốn (local thì không bắt buộc) |
| `VITE_API_BASE_URL` / `NEXT_PUBLIC_API_BASE_URL` | Trỏ tới **nơi backend đang chạy** — xem bảng cổng bên dưới |

> ⚠ **Mật khẩu DB chỉ có tác dụng ở LẦN ĐẦU.** Script init chỉ chạy khi volume
> PostgreSQL còn rỗng. Đổi mật khẩu sau đó thì phải `make reset-db` (xóa sạch
> dữ liệu) hoặc `ALTER ROLE ... PASSWORD` bằng tay.

### Cổng

Cổng của Docker **cố ý nằm ở dải riêng**, không đụng cổng mặc định của tiến
trình chạy native. Quy tắc: **thêm số `1` vào đầu**.

| Dịch vụ | Native | Docker |
|---|---|---|
| Backend | 8080 | **18080** |
| admin-app | 5173 | **15173** |
| public-web | 3000 | **13000** |
| PostgreSQL | 5432 | **15432** |
| MinIO API | 9000 | **19000** |
| MinIO console | 9001 | **19001** |
| SMTP (Mailpit) | 1025 | **11025** |
| Mailpit UI | 8025 | **18025** |

Nhờ vậy máy đã cài sẵn PostgreSQL/MinIO native vẫn chạy dự án bình thường, và
chạy song song native + Docker cùng lúc cũng không xung đột.

Container còn bind vào đúng `127.0.0.1`, nên nếu vẫn trùng cổng thì Compose báo
lỗi ngay lúc khởi động thay vì để ứng dụng lặng lẽ nối nhầm sang dịch vụ khác.

---

## 5. Kiểm tra dựng thành công

```bash
make dev-be                 # hạ tầng + backend trong Docker
curl http://localhost:18080/actuator/health
```

Kỳ vọng `{"status":"UP", ...}`.

| Truy cập | Địa chỉ |
|---|---|
| Backend health | http://localhost:18080/actuator/health |
| MinIO console | http://localhost:19001 (`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`) |
| Hộp thư Mailpit | http://localhost:18025 |
| PostgreSQL | `psql -h localhost -p 15432 -U songnhue_app -d songnhue` |

Kiểm tra schema đã lên đủ:

```bash
make migrate-info       # phải liệt kê 9 migration, success = t
make db-verify-audit    # phải trả về 0 dòng (chuỗi hash nguyên vẹn)
```

---

## 6. Tài khoản đăng nhập

Tài khoản `superadmin` được seed ở trạng thái **`PENDING_ACTIVATION`, chưa có
mật khẩu** — cố ý, để repo không chứa mật khẩu mặc định.

Kích hoạt bằng lệnh bootstrap đọc `BOOTSTRAP_ADMIN_PASSWORD` từ env. **Lệnh này
thuộc WS-5 (T5.7), hiện chưa có**, nên ở giai đoạn này chưa đăng nhập được —
Phase 0 mới đang dựng nền, chưa có màn hình đăng nhập.

---

## 7. Sự cố hay gặp

**`✗ Chưa có deploy/env/local.env`** → `make env`.

**`port is already allocated`** → cổng đã bị chiếm. Chạy `make doctor` để biết
tiến trình nào giữ, rồi đổi biến `DOCKER_*_PORT` tương ứng trong `local.env`.

**`role "songnhue_owner" does not exist`** → app đang nối vào **PostgreSQL khác**
chứ không phải container (thường là PostgreSQL cài sẵn trên máy). Kiểm tra
`DB_PORT` trong `local.env` phải là `15432`, và `make ps` phải thấy
`127.0.0.1:15432->5432`.

**`Thiếu extension bắt buộc: postgis…`** → volume PostgreSQL đã tồn tại từ trước
khi có script init. Chạy `make reset-db`.

**`Thiếu DB role: …`** → tương tự trên, `make reset-db`.

**Đổi mật khẩu trong `local.env` mà không ăn** → xem cảnh báo ở mục 4.

**Sửa code mà container vẫn chạy code cũ** → image không tự bám theo file trên
máy, image sẽ tự build lại (mặc định từ 17/8) — chỉ khi bạn tự thêm `NOBUILD=1` thì mới bỏ qua. Xem
[`run-guideline.md`](run-guideline.md) mục "Docker có biên dịch theo code local
không".

**Xóa sạch làm lại từ đầu:**

```bash
make reset-db      # xóa volume PostgreSQL + MinIO (hỏi xác nhận)
make dev-infra
```
