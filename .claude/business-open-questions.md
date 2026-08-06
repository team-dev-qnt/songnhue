# BUSINESS SELF-REVIEW — ĐIỂM CHƯA CLEAR CẦN CONFIRM

> Kết quả rà soát chéo `function-spec.md` với tài liệu gốc **và SRS v1.0 (23/07/2026)**. Mỗi mục có **đề xuất mặc định** — bạn chỉ cần confirm ✔/✘ hoặc sửa. Đã confirm sẽ được cập nhật ngược vào `function-spec.md`.
> Ký hiệu: 🔴 gap lớn (chặn thiết kế DB/nghiệp vụ) · 🟡 cần chốt trước khi code module liên quan · ⚪ chốt sau được · ✅ đã giải quyết.

---

## 0. TRẠNG THÁI ĐỒNG BỘ SRS (cập nhật 2026-08-06)

Sau khi đối chiếu SRS v1.0, đã chốt 3 quyết định và cập nhật `function-spec.md` lên **v2.0**:

1. ✅ **Tái cấu trúc module theo SRS**: tách "Quản lý dữ liệu thủy văn" thành **MOD-03** riêng; gộp "Tích hợp văn bản điều hành" vào **MOD-01** (CN-01.7); HRM → **MOD-04**; Quản trị → **MOD-05** (bổ sung chức năng). Bảng traceability SRS↔CN ở function-spec §10.
2. ✅ **Giữ nhật ký vận hành / phiếu sự cố / báo cáo vận hành BC-01..08** như **phần mở rộng 🔷 ngoài SRS v1.0** (SRS không có các nghiệp vụ này). Đánh dấu rõ, **cần khách xác nhận nằm trong scope hợp đồng** — xem F1.
3. ✅ **Restore qua UI** (SRS M5.11): đảo quyết định E1 cũ — làm nút restore trên UI admin **kèm bảo vệ nhiều lớp** (xem F6 + `architecture-review.md` §7).

Các gap mới phát hiện từ SRS gom ở **mục F**.

---

## A. GAP LỚN — NGHIỆP VỤ BỊ THIẾU TRONG TÀI LIỆU GỐC

### A1. 🔴 Quản lý Kế hoạch tưới tiêu / Vụ mùa — VẪN THIẾU (SRS cũng không có)
Tài liệu gốc + SRS đều yêu cầu thống kê/so sánh theo mùa vụ (M3.18), báo cáo BC-04/BC-07, chỉ tiêu "% hoàn thành kế hoạch", "diện tích tưới/tiêu đạt" — nhưng **không module nào cho nhập kế hoạch vụ mùa**. SRS M2.6 có thêm liên kết "lưu vực/khu tưới tiêu" (đã đưa vào CN-02.1) làm cơ sở, nhưng chưa đủ.
**Đề xuất**: thêm CN-02.11 "Quản lý Vụ mùa & Kế hoạch": khai báo vụ (tên, từ–đến, loại tưới/tiêu); nhập kế hoạch theo vụ cho từng XN/công trình/khu tưới (diện tích, lưu lượng dự kiến, điện năng dự kiến); ai nhập: Quản lý XN, ai duyệt: Admin/Lãnh đạo.
**Cần confirm**: nghiệp vụ này có tồn tại? Kế hoạch lập theo cấp nào (Công ty giao xuống hay XN lập trình lên)? *(Lưu ý: BC-04/BC-07 là phần mở rộng 🔷 — nếu chốt bỏ phần mở rộng thì A1 giảm ưu tiên.)*

### A2. ✅ Danh mục Điểm đo/Trạm quan trắc — ĐÃ GIẢI QUYẾT trong SRS Module 3
SRS §3.3 dựng hẳn CRUD điểm đo (M3.1/M3.2) + bảng dữ liệu §3.3.4: mã điểm đo, **mã ánh xạ API bên thứ 3** (quy tắc: 1 điểm đo ↔ đúng 1 mã API), loại chỉ số + đơn vị đo, tọa độ, trạng thái bản ghi (Hợp lệ/Nghi ngờ/Loại bỏ), nhật ký đồng bộ. → Đã đưa vào **CN-03.1 + CN-03.2**. Đóng gap.

### A2b. 🟡 Quan hệ Điểm đo ↔ Công trình — VẪN CHƯA RÕ (gap còn lại của A2)
SRS định nghĩa điểm đo độc lập nhưng **không nêu quan hệ điểm đo ↔ công trình** (1 công trình có điểm TL/HL? 1 điểm đo phục vụ nhiều công trình?). Ngưỡng cảnh báo SRS gắn theo điểm đo; nhưng nghiệp vụ vận hành cần biết điểm đo nào là thượng/hạ lưu của công trình nào.
**Đề xuất**: bảng `station_constructions` **n–n có vai trò** (role = thượng lưu / hạ lưu / mưa). Đã ghi chú trong CN-03.1.
**Cần confirm**: mô hình quan hệ thực tế ngoài hiện trường?

### A3. 🟡 Công trình "Kênh mương" / "Đê điều" — có hồ sơ riêng không?
Gốc có trạm bơm + cống + kênh mương; **SRS bổ sung "Đê điều"** (đã thêm vào loại công trình CN-02.1). Chỉ trạm bơm + cống có bảng trường chi tiết.
**Đề xuất**: kênh mương + đê điều dùng hồ sơ tối thiểu + là layer GIS (LineString) + gắn tài liệu; nếu cần thông số kỹ thuật riêng (chiều dài, cao trình đáy, cấp đê, lưu lượng thiết kế...) thì bổ sung bảng `canal_specs` / `dyke_specs`.
**Cần confirm**: đê điều/kênh mương cần quản lý thông số kỹ thuật đến mức nào?

---

## B. MOD-02 — VẬN HÀNH (phần lớn thuộc mở rộng 🔷)

### B1. 🟡 Nhật ký vận hành: đơn vị là NGÀY hay CA? *(🔷 mở rộng)*
Gốc mâu thuẫn: form có "Ca vận hành" nhưng check trùng theo "ngày".
**Đề xuất**: 1 nhật ký / (công trình × ngày × ca); unique key gồm ca; "Cả ngày" không tồn tại song song ca Ngày/Đêm cùng ngày.

### B2. 🟡 Nhập bù quá 3 ngày thì làm gì? *(🔷 mở rộng)*
**Đề xuất**: quá 3 ngày → chỉ Admin tạo hộ (có audit); không mở rộng hạn cho Operator.

### B3. 🟡 Quản lý XN vắng — ai duyệt nhật ký/nghỉ phép thay?
**Đề xuất**: chức năng ủy quyền duyệt có thời hạn (từ–đến, người được ủy quyền cùng XN hoặc cấp trên), audit "duyệt theo ủy quyền của X".

### B4. 🔴 Trạng thái tổ máy "Đang chạy/Dừng" realtime (GIS popup, KPI) — nguồn dữ liệu?
Nhật ký nhập cuối ca → không realtime. SRS cũng không đề cập SCADA/IoT trạng thái máy; "tình trạng vận hành" trong SRS chỉ là trường trạng thái công trình cập nhật thủ công (Bình thường/Cảnh báo/Sự cố/Bảo trì).
**Đề xuất**: hiển thị theo trạng thái công trình cập nhật thủ công + nhãn thời điểm; nếu có nhật ký 🔷 thì bổ sung "theo ca gần nhất". Không hứa realtime tổ máy trừ khi có SCADA.
**Cần confirm**: khách có nguồn trạng thái máy realtime không?

### B5. 🟡 Công thức "Diện tích tưới/tiêu đạt được" và "tiêu chuẩn tưới"
**Đề xuất**: tiêu chuẩn tưới = tham số cấu hình theo vụ/loại cây (gắn A1); công thức = Σ m³ bơm ÷ định mức m³/ha. Cần khách cấp định mức thực tế.

### B6. ⚪ Chuẩn hóa đơn vị đo
**Đề xuất**: chuẩn nội bộ = m (scale 3), mưa = mm (scale 1), Q = m³/s (scale 3); adapter chuyển đổi khi ingest (khớp SRS M3.4 "chuẩn hóa đơn vị").

### B7. ⚪ SMS Gateway
Chốt: nhà cung cấp (ESMS?), ai trả phí, danh sách số nhận theo điểm đo/công trình do ai duy trì, cần khi go-live hay phase sau?

### B8. ⚪ Màn hình lớn Phòng điều hành
SRS §8 cũng liệt kê đây là open issue (độ phân giải, số lượng, vị trí lắp đặt). Cần 1 buổi khảo sát Phòng điều hành trước khi thiết kế UI wall mode.

---

## C. MOD-04 — NHÂN SỰ *(trước là MOD-03)*

### C1. 🟡 Phép năm: pro-rata và mốc thâm niên
**Đề xuất**: (a) pro-rata theo tháng `12 × số tháng / 12`, làm tròn 0.5; (b) thâm niên tính từ `ngày vào làm` tại Công ty.

### C2. 🟡 Luồng duyệt nghỉ phép: 1 cấp hay nhiều cấp?
**Đề xuất**: mặc định 1 cấp (trưởng đơn vị); cấu hình ngưỡng "≥ N ngày cần thêm cấp duyệt 2" (mặc định tắt). Confirm quy chế nội bộ.

### C3. 🟡 Phạm vi tài khoản: TOÀN BỘ CBNV có tài khoản không?
**Đề xuất**: cấp tài khoản toàn bộ; NV không dùng máy tính → quản lý đơn vị tạo đơn phép hộ (trường "người tạo hộ").

### C4. ⚪ Lương — chỉ lưu trường, KHÔNG có module tính lương/chấm công
Xác nhận phạm vi: lưu mức lương/hệ số (mã hóa), không tính lương, không chấm công. *(SRS §3.4.4 để lương ngoài phạm vi cơ bản — khớp.)*

### C5. ⚪ Mẫu lý lịch 2C-BNV
BCNS-07 in theo mẫu 2C-BNV — cần file mẫu chính thức (gộp đợt confirm file mẫu báo cáo).

---

## D. MOD-01 — CMS

### D1. 🟡 Bình luận/Phản hồi: ẩn danh hay bắt đăng nhập? Có nên làm không?
SRS M1.7 gọi là "Quản lý phản hồi người dùng" (đánh giá/khảo sát hài lòng) — nhẹ hơn bình luận công khai.
**Đề xuất**: phase 1 làm khảo sát/góp ý có kiểm duyệt 100%, TẮT bình luận công khai tự do; nếu bật thì yêu cầu họ tên + email + reCAPTCHA.

### D2. ⚪ Vai trò biên tập: mấy cấp?
SRS dùng 2 vai trò: **Biên tập viên + Quản trị nội dung** (+ Cán bộ văn thư cho văn bản). function-spec v2.0 đã theo SRS (bỏ "Trưởng ban biên tập" riêng). Confirm bộ máy thật.

### D3. ⚪ Đa ngôn ngữ
SRS không đề cập. **Đề xuất**: chỉ tiếng Việt, không i18n content. Confirm để chốt hẳn.

### D4. ⚪ Chuyển dữ liệu website cũ
Có cần migrate bài viết/tài liệu từ site cũ? Số lượng? Ảnh hưởng go-live.

---

## E. KHÁC

### E1. ✅ Backup/Restore — ĐÃ CHỐT LẠI theo SRS: có nút Restore UI
Trước đề xuất restore chỉ qua runbook ops. **SRS M5.11 + quyết định 2026-08-06**: làm nút restore trên UI admin (xem F6 + `architecture-review.md` §7 cho biện pháp bảo vệ). UI vẫn hiển thị trạng thái backup gần nhất; runbook PITR giữ song song.

### E2. ⚪ Retention audit log
**Đề xuất**: 5 năm (đồng bộ retention thủy văn chi tiết).

### E3. Đã chờ confirm từ trước (SRS §8 cũng liệt kê là open issues)
- Phương án tích hợp hệ thống văn bản điều hành (SSO/API/CSDL/định dạng) — phạm vi CN-01.7.
- File mẫu báo cáo chuẩn công ty (+ mẫu 2C-BNV — C5).
- Tài liệu API telemetry thật (định dạng, tần suất, xác thực).
- **Số lượng điểm đo/công trình ban đầu; số lượng user theo vai trò** (SRS §8) — làm cơ sở NFR hiệu năng.

---

## F. GAP / ĐIỂM MỚI PHÁT HIỆN TỪ SRS v1.0

### F1. 🔴 Xác nhận scope: nhật ký vận hành + sự cố + báo cáo vận hành BC-01..08
SRS v1.0 **không có** các nghiệp vụ này (chỉ có "lịch sử sửa chữa/bảo trì" M2.3). function-spec giữ chúng như **mở rộng 🔷** (từ tài liệu gốc "Đặc tả hệ thống Website").
**Cần confirm gấp**: các nghiệp vụ 🔷 này có nằm trong phạm vi hợp đồng/nghiệm thu không? Đây là khối lượng lớn (workflow duyệt, tổng hợp cron, template báo cáo) — ảnh hưởng estimate & lộ trình.

### F2. 🟡 Trạng thái bản ghi thủy văn: Hợp lệ / Nghi ngờ / Loại bỏ (SRS §3.3.4)
SRS thêm cờ chất lượng 3 mức cho mỗi bản ghi. Đã đưa vào CN-03.2.
**Cần confirm**: tiêu chí phân loại "Nghi ngờ" vs "Loại bỏ" (ngoài khoảng vật lý = loại bỏ; lệch bất thường so với lịch sử = nghi ngờ?).

### F3. 🟡 Lưu vực / Khu tưới tiêu (SRS M2.6) — thực thể mới
Công trình liên kết lưu vực/khu tưới tiêu. Đã đưa vào CN-02.1.
**Cần confirm**: có cần CRUD danh mục lưu vực/khu tưới riêng (mã, tên, diện tích, ranh giới GIS) hay chỉ là trường tham chiếu văn bản? (Liên quan A1 kế hoạch vụ mùa + B5 diện tích tưới.)

### F4. ⚪ Công cụ GIS mới (SRS M2.12, M2.13)
Đo khoảng cách/diện tích trên bản đồ + xuất bản đồ/danh sách ra ảnh/PDF/Excel. Đã đưa vào CN-02.4. Không có vướng nghiệp vụ — chỉ ghi nhận khối lượng FE.

### F5. ⚪ Chức năng MOD-05 mới theo SRS
Bổ sung vào CN-05.3/05.6/05.7: **xuất/nhập cấu hình hệ thống** (M5.17), **health-check dịch vụ/API tích hợp** (M5.12), **thông báo hệ thống** (M5.13), **quản lý phiên + đăng xuất từ xa** (M5.14), **cảnh báo đăng nhập bất thường ngoài giờ** (M5.16), **ma trận quyền chi tiết theo màn hình** (M5.3). Ghi nhận khối lượng.
**Cần confirm**: "đăng nhập ngoài giờ hành chính" — định nghĩa khung giờ hành chính để cảnh báo (M5.16)?

### F6. 🟡 Restore UI — biện pháp bảo vệ bắt buộc
Làm nút restore (M5.11) nhưng rủi ro cao (ghi đè toàn bộ DB).
**Đề xuất bảo vệ** (đã ghi `architecture-review.md` §7 + CN-05.5): chỉ Super Admin + 2FA; xác nhận nhiều bước (gõ tên hệ thống + lý do); chạy async có tiến độ + khóa hệ thống trong lúc restore; ưu tiên restore ra staging trước; ghi security event; giữ runbook PITR cho khôi phục điểm-thời-gian mà UI không làm được.
**Cần confirm**: chấp nhận ràng buộc 2FA + xác nhận nhiều bước?

### F7. ⚪ Định dạng GIS: Shapefile (SRS §4.6)
SRS nêu GIS "tương thích GeoJSON, Shapefile..."; nội bộ đang chốt GeoJSON/KMZ.
**Đề xuất**: v1 nhận GeoJSON/KMZ; hỗ trợ import Shapefile (chuyển sang GeoJSON khi upload) nếu khách có sẵn dữ liệu .shp. Chốt ở thiết kế chi tiết.

### F8. ⚪ NFR lệch nhẹ giữa SRS và nội bộ
SRS: uptime ≥99%, 200 user đồng thời, 2FA "mở rộng tương lai". Nội bộ chặt hơn: 99.5% giờ hành chính, 100–300 user, 2FA bắt buộc cho Admin. function-spec v2.0 ghi cả hai (lấy mức chặt hơn làm mục tiêu nội bộ). Không chặn — chỉ cần thống nhất con số nghiệm thu với khách.

---

## CÁCH CONFIRM NHANH

Trả lời theo mã mục, VD: `F1: nhật ký vận hành CÓ trong scope · A1: XN lập Công ty duyệt · A2b: 1 CT có 2 điểm TL/HL · F6: đồng ý 2FA · còn lại theo đề xuất`.
Sau khi confirm, tôi sẽ cập nhật `function-spec.md` + đồng bộ `implement.md`/`architecture-review.md` + đóng mục tương ứng ở đây.
