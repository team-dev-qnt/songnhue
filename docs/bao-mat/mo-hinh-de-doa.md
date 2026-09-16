# Mô hình đe doạ — STRIDE

| | |
|---|---|
| **Ngày lập** | 16/09/2026 |
| **Task** | T61.40 (ASVS 11.1.5) — khoảng trống do lượt tự đánh giá ASVS T61.28 chỉ ra |
| **Phạm vi** | Hệ đang chạy trên hai VPS: `backend/` (6 module) · hai giao diện · nginx biên · PostgreSQL · MinIO · ClamAV · ngăn xếp giám sát trên VPS-2 |
| **Ngoài phạm vi** | DNS, hạ tầng nhà cung cấp VPS, hệ nguồn thuỷ văn `bhh40.net`, máy trạm của cán bộ |

> ⛔⛔ Tài liệu này **⛔ phải một lượt kiểm thử xâm nhập**. Nó là bảng liệt kê **có cấu trúc** các
> đường tấn công, để lượt rà sau ⛔ phải nhớ bằng trí nhớ. Mỗi ô "Đã có" trỏ tới một **phép kiểm hoặc
> một dòng cấu hình đo được** — ⛔ trỏ tới một lời hứa.
>
> ⚠ Lý do nó ra đời muộn: **toàn bộ 5 lỗ hổng nặng tìm ra ở T61.28 đều là lỗi LOGIC của luồng đăng
> nhập và luồng tải tệp** (vượt 2FA bằng mật khẩu · dò TOTP ⛔ bị khoá · SVG chạy script · `javascript:`
> trong href · thiếu `no-store`). Một lượt STRIDE trên luồng đăng nhập **trước khi viết mã** sẽ thấy
> ít nhất ba trong năm cái ấy.

## 0. Tài sản — thứ gì đáng để mất

Xếp theo hậu quả khi mất, ⛔ theo thứ tự trong mã.

| # | Tài sản | Vì sao nó đứng ở đây |
|---|---|---|
| A1 | **Dữ liệu thuỷ văn thô** (`hydro_raw_logs`, `hydro_readings`) | Nguồn ⛔ có API lịch sử (quy tắc 18) ⇒ **mất là vĩnh viễn**. Mọi quyết định vận hành cống dựa vào nó |
| A2 | **Trường 🔒 hồ sơ CBNV** (CCCD, lương, tài khoản, BHXH) | NĐ 13/2023; rò rỉ là nghĩa vụ pháp lý của Công ty, ⛔ phải một sự cố kỹ thuật |
| A3 | **Tài khoản quản trị** | Chiếm được là chiếm mọi thứ dưới đây |
| A4 | **Nhật ký kiểm toán** (`audit_logs`, `security_events`) | Là thứ trả lời *"ai đã làm gì"*. Sửa được nó thì mọi bằng chứng khác mất giá trị |
| A5 | **Dữ liệu cá nhân người dân** (biểu mẫu liên hệ, góp ý) | NĐ 13/2023; và đây là dữ liệu của người **⛔ có quan hệ lao động** với Công ty |
| A6 | **Bản sao lưu** | Vừa là đường khôi phục cuối cùng, vừa là bản sao đầy đủ của A1–A5 ở một chỗ dễ quên |
| A7 | **Uy tín tên miền** (máy chủ thư, nội dung cổng) | Tên miền vào danh sách đen là mọi thư nghiệp vụ ⛔ tới nơi |

## 1. S — Spoofing (giả danh)

| Đường tấn công | Đã có gì | Đo ở đâu | Còn hở |
|---|---|---|---|
| Đoán/dò mật khẩu | Khoá 5 lần / 15 phút · BCrypt cost 12 · hạn mức `LOGIN` 30/15' theo IP | `DangNhapKhoaTaiKhoanTest`, `RateLimitPolicy.LOGIN` | Danh sách mật khẩu đã lộ ⛔ có (T61.48) |
| **Có mật khẩu rồi vượt 2FA** | Vé challenge ⛔ đăng ký lại được (`AUTH-0009`); dò mã TOTP tính vào khoá tài khoản | `HaiBuocHttpTest` 4 bài | — (vá ở T61.30/T61.33) |
| Giả IP để né hạn mức | `ClientIp` bóc tới request gốc, ⛔ tin `X-Forwarded-For` | `IpThatTrongNhatKyHttpTest` | — |
| Mượn phiên đang mở trên máy ⛔ khoá | Thao tác nhạy cảm đòi nhập lại mã 2FA (khôi phục CSDL · bí mật tích hợp · tham số bảo mật · đặt lại mật khẩu) | `CauHinhHeThongHttpTest`, `DatLaiMatKhauHttpTest` | **Gán vai trò ⛔ đòi** (T61.48 #4) |
| Chuỗi refresh token bị trộm | Phát hiện dùng lại ⇒ thu hồi cả chuỗi | `RefreshTokenService`, luật Prometheus `REFRESH_REUSE_DETECTED` | **⛔ có trần tuyệt đối** ⇒ chuỗi sống mãi nếu dùng đều (T61.48 #1) |

## 2. T — Tampering (sửa trái phép)

| Đường tấn công | Đã có gì | Còn hở |
|---|---|---|
| Sửa nhật ký kiểm toán | Vai trò runtime ⛔ có `DELETE`/`UPDATE` trên `audit_logs`·`security_events`·`hydro_raw_logs`; chuỗi băm nối tiếp | Bản dump cũ từng mang ACL yếu — đã vá 08/09 (§10.80) |
| Sửa trạng thái ⛔ qua workflow | Đổi trạng thái chỉ qua Workflow engine, có chữ ký chuỗi | — |
| Tệp tải lên mang mã độc | ClamAV (T61.4) + lọc SVG theo cây DOM (T61.32) + kiểm magic bytes | **Thiếu cấu hình ClamAV ⇒ `SKIPPED` mà tệp vẫn tải về được** (T61.48 #2) |
| Ghi đè tệp ngoài thư mục khi giải nén | Tên mục trong ZIP bỏ mọi đoạn đường dẫn (T61.40) | — |
| Sửa cấu hình hạ tầng từ giao diện | Kiến trúc §12.1: 6 nhóm biến **⛔ bao giờ** rời `.env` | — |

## 3. R — Repudiation (chối bỏ)

| Đường | Đã có gì | Còn hở |
|---|---|---|
| *"⛔ phải tôi sửa"* | `audit_logs` ghi giá trị cũ/mới; `security_events` ghi mọi lượt đăng nhập, đổi quyền, đọc trường 🔒 | Đổi tham số `settings` **nay** có `@Audited` (T61.42) — trước 15/09 thì ⛔ |
| *"⛔ ai báo tôi"* | Chính chủ nhận thông báo khi mật khẩu/2FA bị đổi (T61.36) | — |
| Đọc dữ liệu 🔒 mà ⛔ để lại vết | `HR_SENSITIVE_FIELDS_READ` ghi từng lượt đọc | — |

## 4. I — Information disclosure (rò rỉ)

| Đường | Đã có gì | Còn hở |
|---|---|---|
| Đoán UUID tệp của người khác | `readForOwner` tự kiểm chủ sở hữu | — |
| Bộ đệm trình duyệt dùng chung | `Cache-Control: no-store` + `nosniff` cho `/api/**` | — |
| Bản đồ API lộ ra | Swagger TẮT ở tầng ứng dụng **và** nginx chặn | — |
| Nhật ký mang dữ liệu | Log ràng buộc chỉ giữ TÊN ràng buộc (T61.40) | `rejectedValue` vẫn ra response ở vài chỗ |
| Bí mật trong bản sao lưu | Credential mã hoá AES-GCM; payload job ⛔ mang email | — |
| **SSRF** đọc mạng nội bộ | Chặn theo chữ viết **và** theo địa chỉ đã phân giải (T61.38) | DNS rebinding (khai trong javadoc) |
| Phạm vi đơn vị bị vượt | `ScopeGuard` ở `operations` + `hr` | **`content` và `hydro` ⛔ có** (T61.48 #7) |

## 5. D — Denial of service

| Đường | Đã có gì | Còn hở |
|---|---|---|
| Dồn lượt gọi | 4 xô hạn mức; đường ghi công khai riêng 10 lượt/giờ/IP (T61.37) | Một NAT chung vẫn là một xô — T61.17 vế nginx chờ đo |
| Tệp nén nở ra hàng GB | Trần giải nén 64 MB cho xlsx (T61.40) | — |
| Kết xuất nặng | Xô `EXPORT` theo người dùng, trần trong `settings` | — |
| **Máy chủ thư thành máy phát tán** | Thư xác nhận chỉ gửi khi reCAPTCHA đang bảo vệ (T61.37) | Chờ khoá G13 để bật lại thư xác nhận |
| Poller chết ⇒ mất số liệu | Thang leo cảnh báo + chuông healthchecks.io | Chờ QuanTran đặt biến (§B6) |

## 6. E — Elevation of privilege

| Đường | Đã có gì | Còn hở |
|---|---|---|
| Tự cấp quyền cho mình | Trần cấp quyền = quyền của chính người cấp (`ADM-2022`, T61.43) | — |
| Tự liên kết hồ sơ để đọc trường 🔒 | `ADM-2018` chặn | — |
| Tự đặt lại 2FA / mật khẩu của chính mình | `ADM-2021` · `ADM-2025` | — |
| Quản trị viên đọc bí mật tích hợp | Chỉ ghi, ⛔ đọc lại được, cần SUPER_ADMIN + mã 2FA | — |

## 7. Ba câu hỏi lượt rà sau phải hỏi lại

1. **Chức năng mới có tạo một đường ghi mới vào A1–A7 ⛔?** Nếu có: nó đi qua workflow engine, có audit, có phạm vi đơn vị ⛔?
2. **Nó có nhận dữ liệu từ NGOÀI ⛔?** (biểu mẫu, tệp, URL, webhook) — nếu có thì ràng buộc độ dài, hạn mức riêng và lượt kiểm định dạng nằm ở đâu?
3. **Nó có thêm một secret mới ⛔?** — §12.1 quyết định chỗ đặt: `.env` hay bảng bí mật tích hợp, ⛔ bao giờ là `settings`.
