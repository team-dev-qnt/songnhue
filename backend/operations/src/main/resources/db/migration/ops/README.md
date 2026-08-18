# Migration của module `operations` (MOD-02 Vận hành công trình + GIS) — prefix `ops`

Đặt tên: `V<yyyyMMddHHmm>__ops_<mô_tả>.sql` (conventions.md §1.2).
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
