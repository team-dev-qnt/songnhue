# Phân loại dữ liệu

| | |
|---|---|
| **Ngày lập** | 16/09/2026 |
| **Task** | T61.40 (ASVS 8.3.4) |
| **Vì sao** | Luật *"trường nào là 🔒"* trước nay chỉ sống trong chú thích mã và trong trí nhớ. Một bảng mới sinh ra ⛔ có chỗ nào để tra |

## Bốn mức

| Mức | Nghĩa | Bắt buộc kỹ thuật |
|---|---|---|
| **M0 — Công khai** | Đã hoặc sẽ đăng trên cổng | ⛔ ràng buộc; vẫn phải qua lọc XSS trước khi hiển thị |
| **M1 — Nội bộ** | Chỉ người đăng nhập | Phân quyền RBAC; `no-store`; ⛔ vào bản export cấu hình |
| **M2 — Dữ liệu cá nhân** (NĐ 13/2023) | Xác định được một con người | + phạm vi đơn vị · audit khi ghi · thông báo + đồng ý khi thu thập từ người dân · ⛔ vào payload job, ⛔ vào log |
| **M3 — Nhạy cảm 🔒** | Rò là hậu quả pháp lý trực tiếp | + bảng riêng · AES-256-GCM khoá ngoài CSDL · `@Audited(excludeFields)` · **audit khi ĐỌC** · ⛔ trả ra API trừ endpoint chuyên biệt |

## Bảng tra

| Dữ liệu | Mức | Chỗ ở | Ghi chú đo được |
|---|---|---|---|
| Bài viết đã đăng, danh mục, banner | M0 | `articles`, `categories` | — |
| Số đo thuỷ văn, trạng thái công trình | M0 | `hydro_readings`, `constructions` | Lên cổng công khai |
| Bản ghi sửa chữa / bảo trì | M1 | `maintenance_logs` | — |
| Tham số `settings` | M1 | `settings` | ⛔ bao giờ chứa credential — DDL nói thẳng |
| Họ tên, email, điện thoại cán bộ | M2 | `employees`, `users` | Danh bạ nội bộ đọc được toàn Công ty (CN-04.6) |
| Liên hệ / góp ý của người dân | M2 | `contacts`, `feedbacks` | Có `consent_at` từ T61.39 |
| Nhật ký kiểm toán | M2 | `audit_logs` | Giữ 5 năm; nhiều người đọc hơn bảng gốc ⇒ ⛔ để giá trị 🔒 lọt vào |
| **CCCD, lương, hệ số, tài khoản NH, MST, BHXH** | **M3** | `employee_sensitive` | Mã hoá; vân tay HMAC cho chống trùng; `HR_SENSITIVE_FIELDS_READ` mỗi lượt đọc |
| **Secret TOTP** | **M3** | `user_totp.secret_encrypted` | — |
| **Mã số nguồn thuỷ văn** | **M3** | `api_sources.credential` | ⛔ trả ra API kể cả dạng che |
| **Khoá bí mật reCAPTCHA** | **M3** | `integration_secrets` | Ghi một chiều (T61.44) |
| Mật khẩu | **M3** | `users.password_hash` | Chỉ hash BCrypt; ⛔ bao giờ là bản mã giải được |

## Ba luật rút ra

1. **Một trường M3 mới ⇒ ba việc, ⛔ phải một**: cột mã hoá · khai `excludeFields` · ghi sự kiện khi ĐỌC. Bộ canh bytecode (T48.1) ép việc thứ hai; hai việc kia còn dựa vào người viết.
2. **M2 trở lên ⛔ bao giờ vào payload của job** — payload nằm nguyên văn trong `jobs` và lọt vào mọi bản sao lưu. Dùng mã công khai rồi tra lại.
3. **Nhân bản môi trường luôn kéo theo mức cao nhất**: bản dump production về staging mang trọn M3. Runbook `di-tru-du-lieu-giua-moi-truong.md` §11 là chỗ duy nhất quyết định che hay ⛔ — ⛔ để lượt sau tự nghĩ lại.
