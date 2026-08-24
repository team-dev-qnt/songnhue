# Migration của module `hr` (MOD-04 Nhân sự) — prefix `hr`

Đặt tên: `V<yyyyMMddHHmm>__hr_<mô_tả>.sql` (conventions.md §1.2).
Chưa có migration nào — module này thuộc **Phase 3**.

**Cấm sửa file đã merge** — chỉ thêm file mới.

Nhắc riêng cho module này:

- Trường nhạy cảm 🔒 (CCCD, lương/hệ số, hồ sơ sức khỏe) để ở **bảng riêng
  `employee_sensitive`**, mã hóa AES-256-GCM, khóa ngoài DB — tuân thủ NĐ
  13/2023 (CLAUDE.md quy tắc 10).
- `org_units` **dùng chung với MOD-02**, không tạo bảng phòng ban riêng
  (CLAUDE.md quy tắc 7).
- `users.employee_id` trỏ sang bảng nhân viên của module này nhưng **không có
  FK** — ràng buộc giữ ở tầng service để không phá ranh giới module.
- Mọi thông số phép năm nằm ở bảng `settings` nhóm `HR`, **cấm hard-code**
  (chốt C1).
