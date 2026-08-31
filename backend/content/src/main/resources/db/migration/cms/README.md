# Migration của module `content` (MOD-01 Cổng TTĐT) — prefix `cms`

Đặt tên: `V<yyyyMMdd><nnnn>__cms_<mô_tả>.sql` (conventions.md §1.2).

⛔⛔ **`<nnnn>` là SỐ THỨ TỰ CHẠY TIẾP TOÀN KHO, KHÔNG PHẢI GIỜ-PHÚT.** Số hiệu mới phải
**lớn hơn mọi số đã có** trong cả kho. Đánh số bằng giờ-phút đã làm **hai lượt CD đỏ liên
tiếp** ngày 27/08/2026 (§10.66) — hai cách viết chỉ khác nhau ở đúng chỗ không ai nhìn là
thứ tự sắp xếp. Chạy `make migration-order` trước mỗi PR có migration.
Chưa có migration nào — module này thuộc **Phase 1**.

**Cấm sửa file đã merge** — chỉ thêm file mới.

Nhắc riêng cho module này: bảng `external_system_credentials` lưu mã số hệ thống
văn bản điều hành của từng người dùng — cột credential mã hóa AES-256-GCM, khóa
ngoài DB, không log, không trả ra API, không nằm trong bản export cấu hình
(conventions.md §4.7).
