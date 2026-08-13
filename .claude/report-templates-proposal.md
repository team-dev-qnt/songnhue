# ĐỀ XUẤT FORMAT MẪU BÁO CÁO — GỬI CÔNG TY XÂY DỰNG MẪU CHUẨN

> Lập ngày 2026-08-12. Căn cứ: Công ty phản hồi *"Đề xuất format mẫu báo cáo để công ty xây dựng"* (BOQ E3).
> **Mức độ chi tiết**: đây là **khung + danh mục + trường dữ liệu** đủ để Công ty duyệt và dựng file mẫu. **Layout chi tiết (căn lề, font, vị trí ô, công thức Excel) sẽ làm khi vào Phase phát triển module tương ứng** — không làm trước để tránh phải sửa lại khi nghiệp vụ thay đổi.
> Trạng thái: ⬜ **chờ Công ty duyệt** (câu hỏi G10 trong `business-open-questions.md`).

---

## 1. NGUYÊN TẮC CHUNG CHO MỌI BÁO CÁO

### 1.1. Khối cấu trúc chuẩn (5 khối, áp dụng cho tất cả)

```
┌─ KHỐI 1: ĐẦU TRANG (header) ────────────────────────────┐
│ Logo + CÔNG TY TNHH MTV ĐTPT THỦY LỢI SÔNG NHUỆ         │
│ Đơn vị lập: <Xí nghiệp / Phòng ban>                     │
│ ─────────────────────────────────────────────────────── │
│            TÊN BÁO CÁO (in hoa, đậm)                    │
│         Kỳ báo cáo: từ dd/MM/yyyy đến dd/MM/yyyy        │
└─────────────────────────────────────────────────────────┘
┌─ KHỐI 2: THAM SỐ LỌC ĐÃ ÁP DỤNG ────────────────────────┐
│ Đơn vị: … | Công trình: … | Điểm đo: … | Loại: …        │
│ (in ra để người đọc biết số liệu đang lọc theo gì)      │
└─────────────────────────────────────────────────────────┘
┌─ KHỐI 3: TÓM TẮT / CHỈ TIÊU CHÍNH (nếu có) ─────────────┐
│ 3–6 con số tổng hợp quan trọng nhất của kỳ              │
└─────────────────────────────────────────────────────────┘
┌─ KHỐI 4: BẢNG DỮ LIỆU CHI TIẾT ─────────────────────────┐
│ STT | <các cột theo từng báo cáo> | Ghi chú             │
│ Dòng TỔNG CỘNG ở cuối (nếu có cột số)                   │
└─────────────────────────────────────────────────────────┘
┌─ KHỐI 5: CHÂN TRANG (footer) ───────────────────────────┐
│ Ngày lập: dd/MM/yyyy HH:mm  |  Người lập: <họ tên>      │
│ NGƯỜI LẬP BIỂU        TRƯỞNG ĐƠN VỊ       GIÁM ĐỐC      │
│ (ký, ghi rõ họ tên)                                     │
│ Trang x/y  |  Mã báo cáo: BC-xx  |  Mã tra cứu: <id>    │
└─────────────────────────────────────────────────────────┘
```

### 1.2. Quy ước bắt buộc

| Hạng mục | Quy ước |
|---|---|
| Khổ giấy | A4 dọc mặc định; **A4 ngang** cho báo cáo > 7 cột; A3 cho sơ đồ tổ chức |
| Font | Times New Roman 12 (nội dung) / 13–14 (tiêu đề) — theo thể thức văn bản hành chính |
| Ngày giờ | `dd/MM/yyyy` và `dd/MM/yyyy HH:mm` — **giờ Việt Nam (UTC+7)** |
| Số đo | Mực nước **m** (3 chữ số thập phân); lượng mưa **mm** (1); lưu lượng **m³/s** (3) |
| Tiền | VND, dấu chấm ngăn nghìn, không thập phân |
| Ô không có dữ liệu | Ký hiệu `-` (không để trống, không ghi `0` vì 0 là giá trị thật) |
| Dữ liệu nghi ngờ | In nghiêng + dấu `(*)` + chú thích cuối bảng |
| Định dạng xuất | **Excel (.xlsx) và PDF** cho mọi báo cáo |
| Mã tra cứu | Mỗi lần xuất sinh 1 mã (job id) — đối chiếu lại đúng bản đã xuất |

### 1.3. Cách hệ thống dùng file mẫu

Công ty cung cấp file `.xlsx` / `.docx` có **placeholder** dạng `{{tên_biến}}`; hệ thống điền dữ liệu vào và xuất PDF. Ví dụ:

```
{{ky_bao_cao}}       → "01/08/2026 - 31/08/2026"
{{don_vi}}           → "Xí nghiệp Thủy lợi Hà Đông"
{{nguoi_lap}}        → "Nguyễn Văn A"
{{#rows}} … {{/rows}} → vùng lặp cho từng dòng bảng
{{tong_chi_phi}}     → 125.400.000
```

→ **Công ty chỉ cần dựng file mẫu đúng thể thức của mình**, đánh dấu chỗ nào là dữ liệu động; không cần biết kỹ thuật.

---

## 2. DANH MỤC BÁO CÁO ĐỀ XUẤT

> Đã cập nhật theo scope chốt 12/8/2026: **bỏ BC-01/02/03/04/07/08** (mất nguồn dữ liệu do bỏ nhật ký vận hành + kế hoạch vụ mùa).

### 2.1. Nhóm Công trình (MOD-02)

| Mã | Tên báo cáo | Tần suất | Người dùng chính |
|---|---|---|---|
| **BC-09** | Tổng hợp sửa chữa, bảo trì công trình | Tháng / Quý / Năm / Tùy chọn | Phòng Kỹ thuật, Ban giám đốc |
| **BC-10** | Danh mục & hiện trạng công trình | Theo yêu cầu | Ban giám đốc, báo cáo cấp trên |
| **BC-06** | Tổng hợp cảnh báo & sự cố | Tháng / Tùy chọn | Trực ban, Ban giám đốc |

**BC-09 — Tổng hợp sửa chữa, bảo trì** *(báo cáo quan trọng nhất của MOD-02 sau khi cắt scope)*
- Tham số đầu vào: khoảng thời gian, Xí nghiệp, công trình, loại công việc.
- Chỉ tiêu tóm tắt: tổng số lượt sửa chữa · tổng chi phí · số công trình có phát sinh · số việc chưa hoàn thành.
- Cột bảng: STT | Mã CT | Tên công trình | Xí nghiệp | Loại công việc | Nội dung | Hạng mục/thiết bị | Ngày bắt đầu | Ngày hoàn thành | Đơn vị thực hiện | Chi phí (VND) | Nguồn vốn | Kết quả nghiệm thu | Ghi chú.
- Nhóm dòng theo Xí nghiệp → có dòng "Cộng theo XN" và "TỔNG CỘNG".

**BC-10 — Danh mục & hiện trạng công trình**
- Tham số: Xí nghiệp, loại công trình, trạng thái, cấp quản lý.
- Chỉ tiêu tóm tắt: số công trình theo loại · theo trạng thái (Bình thường/Cảnh báo/Sự cố/Bảo trì).
- Cột: STT | Mã CT | Tên | Loại | Cấp quản lý | Đơn vị phụ trách | Tuyến sông – Lý trình | Năm xây dựng | Thông số chính (công suất/khẩu độ) | Trạng thái | Lần bảo trì gần nhất.

**BC-06 — Cảnh báo & sự cố** *(chốt G1: nguồn = `alert_events` + `maintenance_logs` loại "Khắc phục sự cố" — không có bảng `incidents` riêng)*
- Cột: STT | Ngày giờ | Công trình/Điểm đo | Loại (cảnh báo ngưỡng / sự cố) | Mức độ | Nội dung | Giá trị đo – ngưỡng | Thời gian tồn tại | Người xử lý | Kết quả | Ghi chú.
- 2 khối tách biệt trong cùng báo cáo: **(1) Cảnh báo ngưỡng thủy văn** (từ `alert_events`) · **(2) Sự cố công trình** (từ `maintenance_logs`), có dòng cộng riêng từng khối.

### 2.2. Nhóm Thủy văn (MOD-03)

| Mã | Tên báo cáo | Tần suất | Người dùng chính |
|---|---|---|---|
| **BC-05** | Báo cáo thủy văn tháng | Tháng (tự động) | Trực ban, Ban giám đốc |
| **BC-11** | Biểu tổng hợp mực nước theo tuyến sông | **Theo mốc giờ trong ngày** | Trực ban điều hành |
| **BC-12** | Chi tiết dữ liệu quan trắc theo yêu cầu | Theo yêu cầu | Kỹ thuật |
| **BC-13** | Nhật ký đồng bộ & chất lượng dữ liệu | Tháng | Admin, Kỹ thuật |

**BC-11 — Biểu tổng hợp mực nước** *(mô phỏng đúng biểu Công ty đang dùng — ưu tiên cao nhất)*
- Bố cục: nhóm theo tuyến sông (Nhuệ / Đáy / Hồng / La Khê / Vân Đình / Duy Tiên); mỗi công trình 1 cột, tách **TL – HL**; ghi lý trình dưới tên; dòng "Tình hình vận hành" (MT/ĐK/ĐT…) — lấy từ **CN-02.11 nhập tay**, mã + màu theo danh mục cấu hình (chốt G4).
- ⚠ **Cột lượng mưa**: v1 **chưa có nguồn dữ liệu** (API không có endpoint lượng mưa — G3) → hiển thị `-`. Chờ chốt G3-a.
- Điểm đo **mất tín hiệu** hiển thị `-` kèm chú thích ô màu xám (chốt G3).
- Header: `Thời điểm: HHhmm; ngày dd tháng MM năm yyyy`.
- Chú thích cuối biểu: `MT: Mở treo | ĐK: Đóng kín | ĐTTL+x.xxm: Điều tiết thượng lưu | ĐTHL+x.xxm: Điều tiết hạ lưu`.
- ⚠ Đây cũng là **layout của màn hình lớn (wall mode)** → làm 1 lần, dùng 2 nơi.

**BC-05 — Thủy văn tháng**
- Chỉ tiêu: mực nước max/min/trung bình từng điểm đo (kèm thời điểm đạt max/min) · tổng lượng mưa · số lần vượt ngưỡng cảnh báo/nguy hiểm · số giờ mất kết nối.
- Cột: STT | Điểm đo | Tuyến sông – Lý trình | MN max (m) | Thời điểm | MN min (m) | Thời điểm | MN TB (m) | Lượng mưa (mm) *(v1: `-`, chờ G3-a)* | Số lần vượt ngưỡng | Ghi chú.
- ⚠ Mọi chỉ tiêu **chỉ tính trên bản ghi `quality = HOP_LE`**; bản ghi `NGHI_NGO` loại khỏi max/min/TB (xem quy ước §1).

**BC-12 — Chi tiết quan trắc theo yêu cầu**
- Tham số: điểm đo (nhiều), loại chỉ số, khoảng thời gian, bước thời gian (giờ/ngày).
- Cột: Thời điểm | Điểm đo | Chỉ số | Giá trị | Đơn vị | Chất lượng (Hợp lệ/Nghi ngờ) | Nguồn (API/Nhập tay).

**BC-13 — Nhật ký đồng bộ & chất lượng dữ liệu**
- Cột: Ngày | Nguồn API | Số lần gọi thật | Số lần bỏ qua (rate-limit) | Thành công | Thất bại | Số bản ghi nhận | Bản ghi nghi ngờ | **Số khung 10' bị bỏ sót** | Thời gian nguồn OFFLINE | Số trạm mất tín hiệu | Ghi chú.
- ⚠ Cột **"số khung bị bỏ sót"** là chỉ tiêu quan trọng nhất của báo cáo này: vì nguồn không có API lịch sử, mỗi khung bỏ sót là dữ liệu mất vĩnh viễn (NFR-03).

### 2.3. Nhóm Nhân sự (MOD-04) — giữ nguyên BCNS-01..08 theo SRS M4.17

| Mã | Tên báo cáo | Ghi chú format |
|---|---|---|
| BCNS-01 | Danh sách trích ngang CBNV | A4 ngang |
| BCNS-02 | Danh sách nhân sự theo đơn vị | Nhóm theo phòng/XN, có dòng cộng |
| BCNS-03 | Biến động nhân sự (tuyển mới / nghỉ việc / điều chuyển) | Theo tháng, có biểu đồ |
| BCNS-04 | Hợp đồng lao động sắp hết hạn | Lọc theo ngưỡng 30/90 ngày |
| BCNS-05 | Cơ cấu nhân sự (trình độ / độ tuổi / giới tính) | Bảng + biểu đồ tròn |
| BCNS-06 | Chứng chỉ, bằng cấp sắp hết hiệu lực | |
| **BCNS-07** | **Lý lịch cán bộ mẫu 2C-BNV** | ⚠ **Bắt buộc dùng đúng file mẫu Bộ Nội vụ** — Công ty cần cung cấp (G6) |
| BCNS-08 | Tổng hợp nhân sự năm | |

> **Lưu ý bảo mật**: BCNS-01/02/07 chứa dữ liệu cá nhân. Trường 🔒 (CCCD, lương, STK, BHXH) **chỉ in khi người xuất có quyền Admin HR**; các vai trò khác xuất ra bản đã mask. Mỗi lần xuất ghi audit + đóng dấu chìm "BẢN IN NỘI BỘ" + tên người xuất.

### 2.4. Nhóm Cổng thông tin & Quản trị

| Mã | Tên báo cáo | Ghi chú |
|---|---|---|
| BCQT-01 | Thống kê bài viết theo danh mục / tác giả / trạng thái | Excel |
| BCQT-02 | Tổng hợp liên hệ & phản hồi từ cổng | Có SLA xử lý |
| BCQT-03 | Nhật ký hoạt động hệ thống (audit) theo kỳ | Chỉ Admin, không cho sửa |

---

## 3. VIỆC CẦN CÔNG TY LÀM

1. **Duyệt danh mục báo cáo** ở §2 — bổ sung/bỏ bớt báo cáo nào Công ty thực sự dùng.
2. **Cung cấp file mẫu thật** (nếu đang dùng bản Word/Excel nào) cho tối thiểu: **BC-09, BC-11, BC-05, BCNS-07 (2C-BNV)** — 4 mẫu này quyết định phần lớn khối lượng.
3. **Xác nhận thể thức**: khối chữ ký (mấy người ký, chức danh gì), có cần quốc hiệu tiêu ngữ không, có cần đóng dấu treo không.
4. Xác nhận **báo cáo nào phải gửi cấp trên** (Sở NN&PTNT / UBND) — vì nhóm này phải theo mẫu quy định của cơ quan quản lý, không tự đặt.

---

## 4. GHI CHÚ CHO DEV (làm sau, khi vào Phase tương ứng)

- Toàn bộ báo cáo chạy qua **Async Job Queue** (`POST` → 202 + `jobId` → worker → link tải TTL 24h). Không render đồng bộ.
- Template engine: **JXLS/Apache POI** cho `.xlsx`, **docx-stamper** cho `.docx`, convert PDF bằng LibreOffice headless — chốt cụ thể ở Phase 3.
- Mọi số liệu tổng hợp đọc từ **bảng agg**, không scan bảng raw (quy tắc §8 CLAUDE.md).
- File mẫu lưu trong MinIO, có versioning — đổi mẫu không cần deploy lại.
- Bảng `report_templates` (mã báo cáo, tên, file mẫu, tham số đầu vào dạng JSON schema, quyền xem) → UI cho Admin thay file mẫu.
- Ưu tiên làm trước: **BC-11** (dùng chung với wall mode) → **BC-05** → **BC-09** → nhóm BCNS.
