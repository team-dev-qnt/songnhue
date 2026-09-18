# Diễn tập khôi phục (T7.7)

> **Bản sao lưu chưa từng được khôi phục thử thì chưa phải bản sao lưu — chỉ là một tệp.**
>
> Nhịp: **một lần bắt buộc trước go-live**, sau đó **mỗi quý**.
>
> Kế hoạch Phase 0 cố ý cắt phần diễn tập tự động hằng tuần (`architecture-review.md` §6.5) — đổi
> lại thì lần thủ công này là bắt buộc, không phải khuyến khích.

## Vì sao phải làm, dù backup đang xanh

Bốn thứ chỉ lộ ra khi khôi phục thật, và không thứ nào có triệu chứng trước đó:

1. **Bản dump không đọc được** — phiên bản client lệch, tệp hỏng âm thầm, dump thiếu bảng vì lỗi
   phân quyền.
2. **RTO thật khác xa con số trên giấy.** Cam kết ≤ 4 giờ, nhưng chưa ai bấm đồng hồ cho một lượt
   khôi phục *thảm hoạ*.
3. **Có thứ không nằm trong bản dump.** Khoá AES/JWT nằm ngoài CSDL (đúng thiết kế) — chỉ khi khôi
   phục sang máy trắng mới phát hiện quy trình thiếu bước chép khoá.
4. ⭐ **Có thứ chỉ hỏng khi đích ĐÃ CÓ dữ liệu.** Bổ sung 08/09/2026: hai khuyết tật CHẶN của
   `pg_restore` **không thể** xuất hiện trên cluster vừa dựng lại. Mọi lượt diễn tập trước đó của dự
   án đều chạy trên đích rỗng ⇒ về nguyên tắc mù trước cả một lớp lỗi.

> ⇒ **Một lượt diễn tập trên CSDL rỗng không thay thế được lượt diễn tập trên CSDL có dữ liệu.**
> Từ nay mỗi quý phải làm **cả hai** cảnh, xem checklist.

---

## Checklist — in ra, gạch từng dòng

**Ngày diễn tập**: ____________  **Người thực hiện**: ____________
**Cảnh**: ☐ đích RỖNG (máy trắng / thảm hoạ)  ☐ đích CÓ DỮ LIỆU (ghi đè)

### Chuẩn bị

- [ ] Ghi **giờ bắt đầu**: `______` ← RTO tính từ đây
- [ ] Chọn bản dump: `______________________`
      (ưu tiên bản **đã kéo ra ngoài máy chủ CSDL** — đó mới là bản sẽ dùng thật khi máy chính chết)
- [ ] Ghi số bản ghi **trước** khi khôi phục:
  ```bash
  q() { docker exec -i songnhue-postgres psql -U postgres -d songnhue -At -c "$1" < /dev/null; }
  q "SELECT 'users='||(SELECT count(*) FROM users)||' org_units='||(SELECT count(*) FROM org_units)
     ||' settings='||(SELECT count(*) FROM settings)||' audit_logs='||(SELECT count(*) FROM audit_logs)"
  ```
  `users: ____  org_units: ____  settings: ____  audit_logs: ____`
- [ ] Ghi **quyền append-only trước**, để so sau (mục mới 08/09):
  ```bash
  q "SELECT has_table_privilege('songnhue_app','audit_logs','UPDATE')::text||' '||
            has_table_privilege('songnhue_app','audit_logs','DELETE')::text"
  ```
  `______` (phải là `false false`)

### Khôi phục

- [ ] Đối chiếu checksum bản dump — khớp, và **giá trị đo được dài 64 ký tự** (chuỗi rỗng bằng chuỗi
      rỗng cũng "khớp" — xem sự cố 12 ở `di-tru-du-lieu-giua-moi-truong.md`)
- [ ] `XAC_NHAN=<tên CSDL> ENV_FILE=/opt/songnhue/.env ./backup/khoi-phuc-qua-container.sh <đường-dẫn>`
      ⛔ **KHÔNG** dùng `deploy/backup/restore.sh` — nó không chạy được trên máy chủ, xem
      [khoi-phuc-du-lieu.md §2](khoi-phuc-du-lieu.md)
- [ ] Mã thoát **thật** = 0 (lấy `$?` trực tiếp, đừng qua đường ống — luật 32)
- [ ] Ghi **giờ kết thúc khôi phục**: `______`

### Kiểm tra sau khôi phục — đủ 7 mục

- [ ] Ứng dụng lên: `docker inspect -f '{{.State.Health.Status}}' songnhue-app` = `healthy`
- [ ] ⭐ **Đọc được bằng vai trò `songnhue_app`**, không phải bằng chủ sở hữu — phép §10.58 đã thiếu
- [ ] Đăng nhập được bằng một tài khoản thật (phải đăng nhập lại — `sessions` đã bị ghi đè)
- [ ] `q 'SELECT * FROM core_verify_audit_chain()'` → **rỗng** (chuỗi hash nguyên vẹn)
- [ ] Số bản ghi khớp con số ghi ở trên
- [ ] Sổ migration khớp mã nguồn đang chạy — **so cả số hàng lẫn danh sách script**, không chỉ
      `max(version)`
- [ ] ⭐ **Quyền append-only còn nguyên**: 4 phép `false`, `INSERT audit_logs` `true`
- [ ] **Giải mã được trường nhạy cảm**: mở một hồ sơ nhân sự có trường 🔒 và xem được nội dung
      → chứng minh khoá AES trên máy đích khớp dữ liệu trong bản dump.
      *(Từ Phase 3 mới có dữ liệu này; trước đó ghi "chưa áp dụng".)*

### Kết quả

- [ ] **RTO thật**: `______` phút  ← so với cam kết **≤ 4 giờ**
- [ ] Vấn đề gặp phải: `________________________________________________`
- [ ] Việc phải sửa: `________________________________________________`

---

## Sau diễn tập

1. **Ghi con số RTO thật vào nhật ký dưới.** Đây là bằng chứng cho NFR-08 lúc nghiệm thu; con số
   trên giấy không thay thế được.
2. RTO vượt 4 giờ → đây là **phát hiện phải xử lý**, không phải ghi chú.
3. Nếu diễn tập trên Staging bằng dữ liệu Production: xoá dữ liệu cá nhân thật sau khi xong
   (NĐ 13/2023), hoặc giữ Staging ở cùng mức bảo vệ như Production.
4. Nếu dựng CSDL nháp để diễn tập: `DROP DATABASE` sau khi xong — nó là một bản sao đầy đủ dữ liệu
   thật (đo 08/09: 33 MB).

---

## Nhật ký diễn tập

| Ngày | Người làm | Cảnh | Bản dump | RTO / cửa sổ | Vấn đề gặp phải |
|---|---|---|---|---|---|
| 26/08/2026 | QuanTran | đích **rỗng** (dựng lại cluster staging, T11.3-b) | bộ công cụ dự án + `pg_dumpall` dự phòng | — | ⛔ Tìm ra **T7.13-a**: `--no-privileges` tước ACL ⇒ khôi phục ra CSDL `songnhue_app` không đọc nổi (§10.58) |
| 08/09/2026 | Claude + QuanTran | đích **CÓ DỮ LIỆU** (production thật + CSDL nháp `songnhue_thu`) | `di-tru-staging-20260907-2330.dump` 1.250.768 B | cửa sổ ghi đè **6 giây** | ⛔ Ba khuyết tật CHẶN — xem dưới |

### 08/09/2026 — chi tiết

**Cảnh**: khôi phục một bản dump của **môi trường khác** lên production **đang có dữ liệu**. Diễn tập
trước trên CSDL nháp `songnhue_thu` — bản sao đúng của production (`datlocprovider=i`,
`daticulocale=vi-VN`, `datacl` và `nspacl` giống từng ký tự, 107 bảng, 15 phân mảnh).

**Ba khuyết tật CHẶN, cả ba chỉ lộ ra vì đích có dữ liệu:**

1. `pg_restore --clean` vấp bảng phân mảnh — `cannot drop index … because index … requires it`
2. Bộ lọc mục lục để lọt 3 mục `EXTENSION`; §10.58 đã gán lỗi ấy cho `COMMENT - EXTENSION` và **vá
   nhầm chỗ**
3. Dữ liệu nguồn mang `songnhue_app = arwd` trên ~35 bảng append-only ⇒ khôi phục nguyên trạng là
   **âm thầm hạ cấp** đích

Và một khuyết tật thứ tư về công cụ: `deploy/backup/restore.sh` — đường khôi phục thủ công **duy
nhất** — không chạy được trên bất kỳ máy chủ nào.

**Kết quả nghiệm thu**: 8/8 đạt · checksum migration khớp **0 khác biệt** · chuỗi băm audit rỗng ·
`songnhue_app` đọc được · quyền append-only 5/5 đúng · `Anh < Dung < Đăng < Em`.

**Cửa sổ ghi đè đo được**: bản chụp `PRE_RESTORE` lúc `00:20:39` → ứng dụng chạy lại lúc `00:20:45`
= **6 giây**.

### ⚠ Lượt 08/09 KHÔNG chứng minh những điều sau

Ghi ra để không ai đọc nhầm bảng trên thành "đã diễn tập xong":

- ❌ **Khôi phục sang máy TRẮNG.** Cả hai lượt đều dùng cluster đang chạy, có sẵn vai trò, extension
  và bind mount. Cảnh thảm hoạ thật — VPS-1 chết, dựng máy mới — **chưa ai đi qua**.
- ❌ **Khôi phục từ bản sao ngoài máy chủ.** VM-3 chưa tồn tại; `pull-from-prod.sh` chưa chạy lần
  nào. Bản dump vẫn nằm **cùng máy** với CSDL nó sao lưu.
- ❌ **RTO thảm hoạ.** Con số 6 giây là *cửa sổ ghi đè* của một lượt di trú có chuẩn bị, không phải
  RTO. Ô `RTO thật` của cam kết ≤ 4 giờ **vẫn trống**.
- ❌ **Bước chép khoá AES/JWT.** Chưa có dữ liệu 🔒 nên chưa kiểm được.

⇒ **T7.7 và T7.13 chưa đóng.** Lượt diễn tập còn thiếu là: bản dump kéo từ ngoài máy chủ → khôi phục
lên một cluster **mới dựng** → bấm đồng hồ tới lúc 7 phép kiểm xanh hết.
