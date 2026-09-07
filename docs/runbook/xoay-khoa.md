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

### Các bước

```bash
# 1. Sinh khoá mới (32 byte)
openssl rand -base64 32

# 2. Thêm vào /opt/songnhue/.env — GIỮ NGUYÊN dòng khoá cũ
AES_KEY_V1=<khoá cũ, giữ nguyên>
AES_KEY_V2=<khoá mới>
AES_KEY_ID=v2          # ← chỉ đổi dòng này: từ nay MÃ HOÁ MỚI dùng v2

# 3. Khởi động lại app
docker compose -f compose.prod.yml up -d app
```

Từ lúc này: ghi mới dùng `v2`; đọc dữ liệu cũ vẫn tự dùng `v1` nhờ `key_id`.

### Mã hoá lại dữ liệu cũ

Chỉ **sau khi** toàn bộ dữ liệu cũ đã được đọc-ghi lại bằng `v2` mới được gỡ `AES_KEY_V1`. Kiểm còn
bao nhiêu bản ghi dùng khoá cũ:

```sql
SELECT key_id, count(*) FROM employee_sensitive GROUP BY key_id;
```

⬜ Công cụ mã hoá lại hàng loạt thuộc **Phase 3 (MOD-04 HRM)** — Phase 0 chưa có bảng dữ liệu nhạy
cảm nào nên chưa cần. Tới đó thì bổ sung mục này.

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
docker compose -f compose.prod.yml up -d app
```

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
