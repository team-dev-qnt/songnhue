# Migration của module `content` (MOD-01 Cổng TTĐT) — prefix `cms`

Đặt tên: `V<yyyyMMddHHmm>__cms_<mô_tả>.sql` (conventions.md §1.2).
Chưa có migration nào — module này thuộc **Phase 1**.

**Cấm sửa file đã merge** — chỉ thêm file mới.

Nhắc riêng cho module này: bảng `external_system_credentials` lưu mã số hệ thống
văn bản điều hành của từng người dùng — cột credential mã hóa AES-256-GCM, khóa
ngoài DB, không log, không trả ra API, không nằm trong bản export cấu hình
(conventions.md §4.7).
