# BUSINESS SELF-REVIEW — ĐIỂM CHƯA CLEAR CẦN CONFIRM

> Kết quả rà soát chéo `function-spec.md` với 2 tài liệu gốc. Mỗi mục có **đề xuất mặc định** — bạn chỉ cần confirm ✔/✘ hoặc sửa. Đã confirm sẽ được cập nhật ngược vào `function-spec.md`.
> Ký hiệu: 🔴 gap lớn (chặn thiết kế DB/nghiệp vụ) · 🟡 cần chốt trước khi code module liên quan · ⚪ chốt sau được.

---

## A. GAP LỚN — NGHIỆP VỤ BỊ THIẾU TRONG TÀI LIỆU GỐC

### A1. 🔴 Quản lý Kế hoạch tưới tiêu / Vụ mùa — KHÔNG có chức năng nào nhập kế hoạch
Tài liệu gốc yêu cầu: BC-04 "Báo cáo kết quả vụ tưới/tiêu", BC-07 "So sánh kế hoạch – thực hiện" (ghi rõ "kế hoạch được nhập"), chỉ tiêu "Tỷ lệ hoàn thành kế hoạch", "Diện tích tưới/tiêu đạt được" — nhưng **không có chức năng nào để khai báo vụ mùa và nhập kế hoạch**.
**Đề xuất**: thêm CN-02.9 "Quản lý Vụ mùa & Kế hoạch": khai báo vụ (tên, từ ngày–đến ngày, loại tưới/tiêu); nhập kế hoạch theo vụ cho từng XN/công trình (diện tích, lưu lượng dự kiến, điện năng dự kiến); ai nhập: Quản lý XN, ai duyệt: Admin/Lãnh đạo.
**Cần bạn confirm**: có đúng nghiệp vụ này tồn tại? Kế hoạch lập theo cấp nào (Công ty giao xuống hay XN tự lập trình lên)?

### A2. 🔴 Danh mục Trạm quan trắc — chưa có chỗ quản lý
Tài liệu gốc nói "trạm gắn với nguồn API", "trạm quan trắc gắn với công trình" nhưng không có CRUD trạm quan trắc. Quan hệ trạm ↔ công trình chưa định nghĩa (1 trạm đo cho nhiều công trình? công trình có nhiều trạm TL/HL?).
**Đề xuất**: thêm entity `stations` (mã, tên, tọa độ, thông số đo được, nguồn API, trạng thái) + quan hệ **n–n có vai trò** với công trình (`station_constructions`: role = thượng lưu/hạ lưu/mưa). Ngưỡng cảnh báo cấu hình theo (công trình × thông số) và đọc từ trạm gắn theo vai trò.
**Cần bạn confirm**: mô hình quan hệ thực tế ngoài hiện trường?

### A3. 🟡 Công trình "Kênh mương" — có hồ sơ riêng không?
Gốc liệt kê 3 loại (trạm bơm, cống, kênh mương) nhưng chỉ có bảng trường cho trạm bơm + cống. Kênh mương xuất hiện lại ở GIS dạng layer LineString.
**Đề xuất**: kênh mương KHÔNG có hồ sơ CRUD riêng, chỉ là layer GIS + có thể gắn tài liệu. Nếu cần hồ sơ (chiều dài, cao trình đáy, lưu lượng thiết kế...) thì bổ sung bảng `canal_specs`.

---

## B. MOD-02 — VẬN HÀNH

### B1. 🟡 Nhật ký vận hành: đơn vị là NGÀY hay CA?
Gốc mâu thuẫn: form có trường "Ca vận hành" (Ngày/Đêm/Cả ngày) nhưng check trùng lại theo "ngày đã có nhật ký chưa".
**Đề xuất**: 1 nhật ký / (công trình × ngày × ca); unique key gồm ca; "Cả ngày" không được tồn tại song song với ca Ngày/Đêm cùng ngày.

### B2. 🟡 Nhập bù quá 3 ngày thì làm gì?
Gốc chỉ nói "tối đa 3 ngày". Thực tế sẽ có trường hợp quên/sự cố mạng dài hơn.
**Đề xuất**: quá 3 ngày → chỉ Admin được tạo hộ (có audit); không mở rộng hạn cho Operator.

### B3. 🟡 Quản lý XN vắng — ai duyệt nhật ký/nghỉ phép thay?
Chưa có cơ chế ủy quyền.
**Đề xuất**: chức năng ủy quyền duyệt có thời hạn (từ ngày–đến ngày, người được ủy quyền cùng XN hoặc cấp trên), ghi audit "duyệt theo ủy quyền của X".

### B4. 🔴 Trạng thái tổ máy "Đang chạy/Dừng" realtime (GIS popup, KPI "Đang vận hành") — nguồn dữ liệu?
Nhật ký chỉ nhập cuối ca → không có realtime. Không thấy đề cập SCADA/IoT trạng thái máy.
**Đề xuất**: hiển thị theo **nhật ký gần nhất + nhãn thời điểm** ("theo ca gần nhất, cập nhật lúc..."), không hứa realtime. Nếu sau này có SCADA thì nâng cấp.
**Cần bạn confirm**: khách có nguồn trạng thái máy realtime không?

### B5. 🟡 Công thức "Diện tích tưới/tiêu đạt được" và "tiêu chuẩn tưới"
Gốc: "tính theo lưu lượng thực tế và tiêu chuẩn tưới" — không có công thức, không nói tiêu chuẩn tưới (m³/ha) lấy đâu.
**Đề xuất**: tiêu chuẩn tưới là tham số cấu hình theo vụ/loại cây trồng (gắn A1); công thức = Σ m³ bơm ÷ định mức m³/ha. Cần khách cấp định mức thực tế.

### B6. ⚪ Chuẩn hóa đơn vị đo
Gốc ghi H đơn vị "cm / m" lẫn lộn.
**Đề xuất**: chuẩn nội bộ = m (NUMERIC scale 3), mưa = mm (scale 1), Q = m³/s (scale 3); adapter chuyển đổi từ đơn vị nguồn API khi ingest.

### B7. ⚪ SMS Gateway
Critical alert gửi SMS — cần chốt: nhà cung cấp (ESMS?), ai trả phí, danh sách số nhận theo công trình do ai duy trì, có cần khi go-live hay phase sau?

### B8. ⚪ Màn hình lớn Phòng điều hành
Chưa có yêu cầu cụ thể nội dung trình chiếu (rotate màn nào, bao lâu). Cần 1 buổi làm việc với Phòng điều hành trước khi thiết kế UI wall mode.

---

## C. MOD-03 — NHÂN SỰ

### C1. 🟡 Phép năm: pro-rata và mốc thâm niên
Gốc chỉ ghi 12/13/14 ngày theo thâm niên. Chưa rõ: (a) NV vào giữa năm → phép tính theo tỷ lệ tháng làm việc (Điều 113 BLLĐ tính theo tháng)? (b) Thâm niên tính từ ngày vào Công ty hay cộng cả thời gian trước?
**Đề xuất**: (a) pro-rata theo tháng: `12 × số tháng làm việc / 12`, làm tròn 0.5; (b) thâm niên = thời gian làm tại Công ty (theo `ngày vào làm`).

### C2. 🟡 Luồng duyệt nghỉ phép: 1 cấp hay nhiều cấp?
Gốc: "Quản lý trực tiếp duyệt". Thực tế DN nhà nước thường: nghỉ dài (>N ngày) cần cấp cao hơn duyệt.
**Đề xuất**: mặc định 1 cấp (trưởng đơn vị); cấu hình được ngưỡng "≥ N ngày cần thêm cấp duyệt 2" (mặc định tắt). Confirm quy chế nội bộ thực tế.

### C3. 🟡 Phạm vi tài khoản: TOÀN BỘ CBNV có tài khoản không?
Danh bạ + nghỉ phép giả định mọi NV đăng nhập được. Công nhân vận hành trạm có dùng hệ thống không, hay đăng ký phép qua giấy/quản lý nhập hộ?
**Đề xuất**: cấp tài khoản toàn bộ; NV không dùng máy tính → quản lý đơn vị tạo đơn phép hộ (có trường "người tạo hộ").

### C4. ⚪ Lương — chỉ lưu trường, KHÔNG có module tính lương/chấm công
Xác nhận phạm vi: hệ thống lưu mức lương/hệ số trong hồ sơ (mã hóa), không tính lương, không chấm công; phép trừ theo đơn được duyệt, không đối chiếu máy chấm công.

### C5. ⚪ Mẫu lý lịch 2C-BNV
BCNS-07 in theo mẫu 2C-BNV — cần file mẫu chính thức khách đang dùng (gộp vào đợt confirm file mẫu báo cáo).

---

## D. MOD-01 — CMS

### D1. 🟡 Bình luận: ẩn danh hay bắt đăng nhập? Có nên làm không?
Cổng TTĐT cơ quan nhà nước cho bình luận ẩn danh = gánh nặng kiểm duyệt; Akismet kém với tiếng Việt.
**Đề xuất**: phase 1 TẮT bình luận (ưu tiên thấp nhất trong gốc); nếu bật thì yêu cầu họ tên + email + reCAPTCHA, duyệt 100% trước khi hiện.

### D2. ⚪ Vai trò "Trưởng ban biên tập" có thật không?
Ma trận workflow gốc có 4 role CMS. Confirm bộ máy thật: ai soạn, ai duyệt (có thể chỉ 2 role: Biên tập viên + Admin nội dung).

### D3. ⚪ Đa ngôn ngữ
Gốc không đề cập. **Đề xuất**: chỉ tiếng Việt, không thiết kế i18n content (tiết kiệm đáng kể độ phức tạp). Confirm để chốt hẳn.

### D4. ⚪ Chuyển dữ liệu website cũ
Đây là "nâng cấp website" — có cần migrate bài viết/tài liệu từ site cũ sang không? Số lượng bao nhiêu? Ảnh hưởng kế hoạch go-live.

---

## E. KHÁC

### E1. ⚪ Backup/Restore (MOD-05): thao tác qua UI admin hay chỉ quy trình vận hành (ops)?
**Đề xuất**: restore là quy trình ops có runbook, KHÔNG làm nút restore trên UI (rủi ro cao); UI chỉ hiển thị trạng thái backup gần nhất.

### E2. ⚪ Retention audit log
Spec chưa định thời hạn lưu audit. **Đề xuất**: 5 năm (đồng bộ retention dữ liệu thủy văn chi tiết).

### E3. Đã chờ confirm từ trước (nhắc lại)
- Phương án tích hợp hệ thống văn bản điều hành (phạm vi MOD-04).
- File mẫu báo cáo chuẩn công ty (+ mẫu 2C-BNV — C5).
- Tài liệu API telemetry thật.

---

## CÁCH CONFIRM NHANH

Trả lời theo mã mục, VD: `A1: đúng, XN lập trình Công ty duyệt · B1: theo ca · D1: tắt phase 1 · còn lại theo đề xuất`.
Sau khi confirm, tôi sẽ: cập nhật `function-spec.md` (thêm CN-02.9, danh mục trạm, sửa các mục liên quan) + đồng bộ `implement.md` (dependency nhóm C) + đóng mục tương ứng trong file này.
