# Xoay khoá AES và khoá ký JWT

> Hai loại khoá, **cơ chế xoay khác hẳn nhau**. Nhầm lẫn giữa chúng là cách nhanh nhất để mất vĩnh
> viễn dữ liệu nhân sự đã mã hoá.
>
> | | Khoá AES-256-GCM | Khoá ký JWT (RS256) |
> |---|---|---|
> | Dùng để | Mã hoá trường nhạy cảm HR, credential bên thứ 3 | Ký access token |
> | Nằm ở | Biến môi trường `AES_KEY_V*` | Tệp `/opt/songnhue/keys/jwt-*.pem` |
> | **Vứt khoá cũ đi thì** | **Mất vĩnh viễn dữ liệu đã mã hoá bằng nó** | Mọi người phải đăng nhập lại |
> | Xoay | Thêm khoá mới, **giữ khoá cũ**, mã hoá lại dần | Thay cặp khoá, đổi `kid` |

Khi nào xoay: nghi khoá lộ · nhân sự giữ khoá nghỉ việc · định kỳ theo chính sách ·
[verify-no-keys.sh](../../deploy/backup/verify-no-keys.sh) phát hiện khoá lọt vào bản sao lưu.

---

## A. Xoay khoá AES

### ⛔ Điều tuyệt đối không được làm

**Không xoá `AES_KEY_V1` khi thêm `AES_KEY_V2`.** Mỗi bản mã lưu kèm `key_id` chỉ ra nó được mã hoá
bằng khoá nào (`CryptoService`). Bỏ khoá cũ đi là mọi bản ghi mang `key_id` đó **không giải mã lại
được nữa** — không có cách nào khôi phục, kể cả từ bản sao lưu, vì bản sao lưu cũng chỉ chứa dữ liệu
đã mã hoá.

### ⭐ Chống trùng CCCD qua lượt xoay khoá — đã có cơ chế (T61.11, 14/09/2026)

Cột `employee_sensitive.national_id_fingerprint` là vân tay HMAC dẫn xuất từ khoá, dạng `<key_id>:<hex>`,
và chỉ mục `uq_employee_sensitive_cccd` so trên **cả chuỗi**. Trước 14/09/2026, đổi `AES_KEY_ID` là **tắt
âm thầm** phép chống trùng (CCCD cũ `v1:…`, CCCD nhập lại `v2:…`). Nay có hai lớp:

1. **Phép kiểm trùng so dưới MỌI khoá đang nạp** (`CryptoService.fingerprintsForAllKeys`) ⇒ đúng **ngay**
   sau khi khởi động lại, ⛔ chờ gì. Điều kiện: **khoá cũ còn nạp** — đúng thứ mục trên đã cấm gỡ.
2. **Job `CRYPTO_REENCRYPT` tự chạy lúc khởi động** khi còn hàng mang khoá cũ: mã hoá lại mọi cột ở
   `employee_sensitive` · `api_sources.credential` · `user_totp.secret_encrypted`, và tính lại vân tay
   **cùng giao dịch** với bản mã của từng hàng. ⛔ Có nút bấm nào.

⛔ Job **HỎNG** (`ADM-2019` trong `jobs.last_error`) khi còn hàng ⛔ đổi được — bản mã hỏng, hoặc hai hồ sơ
cùng CCCD đã lọt vào **trước** bản vá (chỉ mục duy nhất chặn lượt ghi vân tay mới). ⇒ ⛔ gỡ khoá cũ; đọc log
ứng dụng (`Mã hoá lại <bảng>#<id> không được`) để biết hàng nào.

### Các bước

```bash
# 1. Sinh khoá mới (32 byte)
openssl rand -base64 32

# 2. Thêm vào /opt/songnhue/.env — GIỮ NGUYÊN dòng khoá cũ
AES_KEY_V1=<khoá cũ, giữ nguyên>
AES_KEY_V2=<khoá mới>
AES_KEY_ID=v2          # ← chỉ đổi dòng này: từ nay MÃ HOÁ MỚI dùng v2

# 3. Khởi động lại app
cd /opt/songnhue
# ⚠ compose đòi ${APP_IMAGE:?} … mà .env ⛔ có — lấy từ container đang chạy (khuôn deploy-hong.md)
export APP_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-app)
export ADMIN_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-admin-app)
export PUBLIC_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-public-web)
COMPOSE=compose.prod.yml          # VPS-2 (staging): COMPOSE=compose.staging.yml
docker compose --env-file .env -f "$COMPOSE" up -d app; echo "MA_THOAT=$?"   # phải 0
```

> ⚠ **Sửa 19/09/2026 (WS-68, `T68.6`)**: bản cũ ghi `docker compose -f compose.prod.yml up -d app` — thiếu `--env-file .env`
> và ba biến ảnh, nên trên máy chủ nó **thoát 1** với *required variable APP_IMAGE is missing a value* (đúng hình dạng
> §10.48 · §10.81): lượt xoay khoá dừng ở bước khởi động lại mà `.env` đã đổi dở.

Từ lúc này: ghi mới dùng `v2`; đọc dữ liệu cũ vẫn tự dùng `v1` nhờ `key_id`.

```bash
# 4. Theo dõi job tự đặt lúc khởi động (máy chủ ⛔ có psql trên host — đi qua docker exec)
q() { docker exec -i songnhue-postgres psql -U postgres -d songnhue -At -c "$1" < /dev/null; }
q "SELECT status, progress, last_error, result FROM jobs WHERE job_type = 'CRYPTO_REENCRYPT' ORDER BY id DESC LIMIT 1"
# Kỳ vọng: SUCCEEDED · 100 · last_error rỗng · result có "conLai":0
```

### Mã hoá lại dữ liệu cũ

Chỉ **sau khi** toàn bộ dữ liệu cũ đã được đọc-ghi lại bằng `v2` mới được gỡ `AES_KEY_V1`. Kiểm còn
bao nhiêu bản ghi dùng khoá cũ:

```sql
-- ⚠ Bảng KHÔNG có cột `key_id` (chốt T51.0): id khoá nằm ở TIỀN TỐ của từng bản mã.
--   Câu `SELECT key_id … FROM employee_sensitive` ở bản cũ của runbook này báo lỗi cột không tồn tại.
SELECT split_part(national_id, ':', 1) AS khoa, count(*)
  FROM employee_sensitive WHERE national_id IS NOT NULL GROUP BY 1;
SELECT split_part(credential, ':', 1) AS khoa, count(*)
  FROM api_sources WHERE credential IS NOT NULL GROUP BY 1;
-- ⛔ user_totp CÓ cột `key_id` nhưng nó là cột CHẾT (ghi một lần, 0 nơi đọc — T51.0) ⇒ đọc tiền tố bản mã:
SELECT split_part(secret_encrypted, ':', 1) AS khoa, count(*) FROM user_totp GROUP BY 1;
```

Chỉ gỡ `AES_KEY_V1` khi **cả ba** câu trên ra đúng một dòng `v2` **và** job gần nhất `SUCCEEDED` với
`"conLai":0`. Job chạy lại ở mỗi lượt khởi động nếu còn hàng khoá cũ (người dùng lưu đè bằng bản đã mở
trước lượt xoay có thể ghi lại bản mã `v1` — vô hại khi khoá cũ còn nạp, lượt kế đổi nốt).

### Sau khi xoay

- Xoá khoá cũ khỏi mọi nơi lưu ngoài máy chủ (trình quản lý mật khẩu, ghi chú, GitHub Secrets).
- ⚠ **Bản sao lưu cũ vẫn chứa dữ liệu mã hoá bằng khoá cũ.** Vứt khoá cũ đi thì những bản đó
  không khôi phục được đầy đủ. Giữ khoá cũ ở nơi an toàn cho tới khi bản sao lưu cuối cùng dùng nó
  đã quá hạn 30 ngày.

---

## B. Xoay khoá ký JWT

Nhẹ nhàng hơn nhiều: hậu quả tối đa là mọi người đăng nhập lại.

```bash
# 1. Sinh cặp mới, ĐẶT TÊN KHÁC — chưa ghi đè gì cả
cd /opt/songnhue/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private-v2.pem
openssl rsa -pubout -in jwt-private-v2.pem -out jwt-public-v2.pem
chmod 600 jwt-private-v2.pem

# 2. Trỏ env sang cặp mới, ĐỔI CẢ kid
JWT_KEY_ID=v2
JWT_PRIVATE_KEY_PATH=/opt/songnhue/keys/jwt-private-v2.pem
JWT_PUBLIC_KEY_PATH=/opt/songnhue/keys/jwt-public-v2.pem

# 3. Khởi động lại
cd /opt/songnhue
# ⚠ compose đòi ${APP_IMAGE:?} … mà .env ⛔ có — lấy từ container đang chạy (khuôn deploy-hong.md)
export APP_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-app)
export ADMIN_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-admin-app)
export PUBLIC_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-public-web)
COMPOSE=compose.prod.yml          # VPS-2 (staging): COMPOSE=compose.staging.yml
docker compose --env-file .env -f "$COMPOSE" up -d app; echo "MA_THOAT=$?"   # phải 0
```

> ⚠ **Sửa 19/09/2026 (WS-68, `T68.6`)**: bản cũ ghi `docker compose -f compose.prod.yml up -d app` — thiếu `--env-file .env`
> và ba biến ảnh, nên trên máy chủ nó **thoát 1** với *required variable APP_IMAGE is missing a value* (đúng hình dạng
> §10.48 · §10.81): lượt xoay khoá dừng ở bước khởi động lại mà `.env` đã đổi dở.

**Đổi `JWT_KEY_ID` là bắt buộc.** `kid` nằm trong header token để hệ thống biết dùng khoá nào kiểm
chữ ký. Đổi khoá mà giữ nguyên `kid` thì token cũ được coi là ký bằng khoá mới → kiểm chữ ký thất
bại với thông báo khó hiểu, thay vì bị từ chối rõ ràng.

Hệ quả: mọi access token đang sống thành không hợp lệ, người dùng đăng nhập lại. Refresh token nằm
trong DB nên không ảnh hưởng — nhưng nếu muốn buộc đăng nhập lại hoàn toàn:

```sql
UPDATE sessions SET revoked_at = now(), revoke_reason = 'KEY_ROTATION' WHERE revoked_at IS NULL;
```

Giữ cặp khoá cũ thêm vài ngày rồi mới xoá — quay lui nhanh khi có chuyện.

---

## C. Sau mọi lần xoay khoá

```bash
ENV=prod deploy/backup/backup.sh    # bản sao lưu đầu tiên sau khi xoay
make backup-verify ENV=prod         # khoá KHÔNG nằm trong bản dump
```

Ghi vào sổ vận hành: **ngày xoay · lý do · ai làm · khoá cũ đang giữ ở đâu**. Câu hỏi "khoá `v1` còn
ở đâu không" sẽ được hỏi vào đúng lúc cần khôi phục một bản sao lưu cũ.
