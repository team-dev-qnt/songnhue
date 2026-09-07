# Migration của module `operations` (MOD-02 Vận hành công trình + GIS) — prefix `ops`

Đặt tên: `V<yyyyMMdd><nnnn>__ops_<mô_tả>.sql` (conventions.md §1.2).

⛔⛔ **`<nnnn>` là SỐ THỨ TỰ CHẠY TIẾP TOÀN KHO, KHÔNG PHẢI GIỜ-PHÚT.** Số hiệu mới phải
**lớn hơn mọi số đã có** trong cả kho. Đánh số bằng giờ-phút đã làm **hai lượt CD đỏ liên
tiếp** ngày 27/08/2026 (§10.66) — hai cách viết chỉ khác nhau ở đúng chỗ không ai nhìn là
thứ tự sắp xếp. Chạy `make migration-order` trước mỗi PR có migration.
Chưa có migration nào — module này thuộc **Phase 2**.

**Cấm sửa file đã merge** — chỉ thêm file mới.

Nhắc riêng cho module này:

- **Không có bảng `incidents`** — sự cố là `maintenance_logs` với loại
  `Khắc phục sự cố` (chốt G1, CLAUDE.md quy tắc 15).
- **Trạng thái công trình là giá trị dẫn xuất** — không tạo cột cho người dùng
  sửa tay (CLAUDE.md quy tắc 4).
- `construction_operation_status` **append-only theo nghiệp vụ**: cập nhật =
  thêm dòng mới có `effective_at`, không UPDATE dòng cũ (conventions.md §4.3).
- Mã tình hình vận hành là **bảng danh mục có CRUD**, không phải enum trong code
  (CLAUDE.md quy tắc 16).
- Số đo và tiền dùng `NUMERIC`, cấm `float/double` (CLAUDE.md quy tắc 2).
