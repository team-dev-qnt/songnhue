# Migration của module `hydro` (MOD-03 Dữ liệu thủy văn) — prefix `hyd`

Đặt tên: `V<yyyyMMdd><nnnn>__hyd_<mô_tả>.sql`

⛔⛔ **`<nnnn>` là SỐ THỨ TỰ CHẠY TIẾP TOÀN KHO, KHÔNG PHẢI GIỜ-PHÚT.** Chuỗi ấy chạy
`1046 → 1047 → 1048 → …` xuyên suốt cả kho, không đếm lại theo ngày; số hiệu mới phải
**lớn hơn mọi số đã có**, kể cả số của một tệp mang ngày lớn hơn hôm nay.

> ⚠ Tệp này từng ghi `V<yyyyMMddHHmm>` — chép từ `conventions.md` §1.2, bản cũ. Hai cách
> viết **chỉ khác nhau ở đúng chỗ không ai nhìn**: thứ tự sắp xếp. Ngày 27/08/2026 hai
> migration đánh số bằng giờ-phút (`202608272320`) rơi xuống *dưới* ba migration mang ngày
> 28 đã áp trên staging ⇒ out-of-order ⇒ **hai lượt CD đỏ liên tiếp**, và cùng lỗi ấy làm
> một câu seed `UPDATE` chạm **0 hàng, không lỗi, không log** (§10.66). Quy ước đúng nằm ở
> `docs/coding-guide.md` §3.1 và có bộ canh: `backend/tools/kiem-thu-tu-migration.sh`
> (bước 2/10 của `make ci-local` + job CI *Thứ tự migration*).

Chạy `make migration-order` **trước mỗi PR có migration**, và `make migration-manifest`
sau khi thêm/đổi tên tệp.

**Cấm sửa file đã merge** — chỉ thêm file mới.

## ⚠ Bắt buộc khi tạo `hydro_raw_logs`

Bảng này là **bản sao duy nhất** của dữ liệu nguồn — nguồn `bhh40.net` **không có
API lịch sử**, mất là mất vĩnh viễn (CLAUDE.md quy tắc 18). Nên:

1. Ghi **nguyên văn** response vào bảng **trước khi** parse.
2. Bảng append-only: migration tạo bảng phải **REVOKE** lại quyền ghi đè, vì
   default privileges ở `V202608131006__core_db_role_grants.sql` cấp sẵn
   `UPDATE, DELETE` cho `songnhue_app` trên mọi bảng tạo sau:

```sql
REVOKE UPDATE, DELETE, TRUNCATE ON hydro_raw_logs FROM songnhue_app;
GRANT SELECT, DELETE ON hydro_raw_logs TO songnhue_archiver;
```

## Nhắc khác

- `quality = NGHI_NGO` **nằm chung bảng chính** → mọi truy vấn báo cáo / alert /
  tổng hợp phải lọc `quality = HOP_LE` (CLAUDE.md quy tắc 14 — bẫy sai số liệu
  dễ mắc nhất của dự án).
- Seed điểm đo **dùng mã API (`F#####`), cấm dùng tên** — có 2 công trình cùng
  tên "Yên Nghĩa" (function-spec.md CN-03.1).
- **Cấm validate liên điểm đo kiểu "TL phải cao hơn HL"** — 2/5 cặp có giá trị
  đảo ngược và vẫn hợp lệ.
- Mực nước lưu **m** scale 3; nguồn trả **cm** → adapter chia 100 lúc ingest.
