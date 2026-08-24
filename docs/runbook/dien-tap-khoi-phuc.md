# Diễn tập khôi phục (T7.7)

> **Bản sao lưu chưa từng được khôi phục thử thì chưa phải bản sao lưu — chỉ là một tệp.**
>
> Nhịp: **một lần bắt buộc trước go-live**, sau đó **mỗi quý**. Làm trên **VM-2 (Staging)**, không
> bao giờ trên Production.
>
> Kế hoạch Phase 0 cố ý cắt phần diễn tập tự động hằng tuần (`architecture-review.md` §6.5) — đổi
> lại thì lần thủ công này là bắt buộc, không phải khuyến khích.

## Vì sao phải làm, dù backup đang xanh

Ba thứ chỉ lộ ra khi khôi phục thật, và cả ba đều không có triệu chứng nào trước đó:

1. **Bản dump không đọc được** — phiên bản client lệch, tệp hỏng âm thầm, dump thiếu bảng vì lỗi
   phân quyền.
2. **RTO thật khác xa con số trên giấy.** Cam kết ≤ 4 giờ, nhưng chưa ai bấm đồng hồ.
3. **Có thứ không nằm trong bản dump.** Khoá AES/JWT nằm ngoài CSDL (đúng thiết kế) — chỉ khi khôi
   phục sang máy trắng mới phát hiện là quy trình khôi phục thiếu bước chép khoá.

---

## Checklist — in ra, gạch từng dòng

**Ngày diễn tập**: ____________  **Người thực hiện**: ____________

### Chuẩn bị

- [ ] Ghi lại **giờ bắt đầu**: `______` ← RTO tính từ đây
- [ ] Chọn bản dump: `______________________` (ưu tiên bản **đã kéo về VM-3**, không phải bản trên VM-1 — đó mới là bản sẽ dùng thật khi VM-1 chết)
- [ ] Ghi số bản ghi **trước** khi khôi phục, để so sau:

  ```sql
  SELECT 'users' t, count(*) FROM users
  UNION ALL SELECT 'org_units', count(*) FROM org_units
  UNION ALL SELECT 'settings', count(*) FROM settings
  UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs;
  ```
  `users: ____  org_units: ____  settings: ____  audit_logs: ____`

### Khôi phục

- [ ] Đối chiếu checksum bản dump — khớp
- [ ] `ENV=staging deploy/backup/restore.sh <đường-dẫn>`
- [ ] `pg_restore` kết thúc mã 0
- [ ] Ghi **giờ kết thúc pg_restore**: `______`

### Kiểm tra sau khôi phục — đủ 6 mục

- [ ] Ứng dụng lên: `/actuator/health/readiness` = UP
- [ ] Đăng nhập được bằng một tài khoản thật (phải đăng nhập lại — bảng `sessions` đã bị ghi đè)
- [ ] `make db-verify-audit ENV=staging` → **rỗng** (chuỗi hash nguyên vẹn)
- [ ] Số bản ghi khớp con số ghi ở trên (chênh lệch hợp lý với thời điểm dump)
- [ ] `make migrate-info ENV=staging` → phiên bản schema khớp mã nguồn đang chạy
- [ ] **Giải mã được trường nhạy cảm**: mở một hồ sơ nhân sự có trường 🔒 và xem được nội dung
      → chứng minh khoá AES trên VM-2 khớp dữ liệu trong bản dump. *(Từ Phase 3 mới có dữ liệu này;
      trước đó ghi "chưa áp dụng".)*

### Kết quả

- [ ] **RTO thật**: `______` phút  ← so với cam kết **≤ 4 giờ**
- [ ] Vấn đề gặp phải: `________________________________________________`
- [ ] Việc phải sửa: `________________________________________________`

---

## Sau diễn tập

1. **Ghi con số RTO thật vào bảng dưới.** Đây là bằng chứng cho NFR-08 lúc nghiệm thu; con số trên
   giấy không thay thế được.
2. RTO vượt 4 giờ → đây là **phát hiện phải xử lý**, không phải ghi chú. Hoặc rút ngắn quy trình,
   hoặc điều chỉnh cam kết với Công ty — nhưng không được để nguyên hai con số mâu thuẫn.
3. Xoá dữ liệu production vừa khôi phục khỏi Staging nếu có dữ liệu cá nhân thật (NĐ 13/2023), hoặc
   giữ Staging ở cùng mức bảo vệ như Production.

## Nhật ký diễn tập

| Ngày | Người làm | Bản dump | RTO thật | Vấn đề gặp phải |
|---|---|---|---|---|
| ⬜ *chưa diễn tập lần nào* | | | | **Bắt buộc làm trước go-live** |
