# SPEC: Một số yêu cầu với BÁO CÁO NHANH

- Phiên bản: 1.0 — 16/09/2026
- Người viết: BA dự án Website Công ty TNHH MTV ĐTPT Thủy lợi Sông Nhuệ
- Nguồn: `Mẫu Báo cáo nhanh.docx` (Công ty cung cấp) + danh mục trạm bơm đã lưu (`danh-muc-tram-bom.md` và 7 file XNTL) + dữ liệu mực nước/mưa đã đặc tả (`spec-bieu-do-muc-nuoc.md`, `spec-bieu-do-tung-cong.md`, `spec-cong-thuc-noi-suy.md`)

---

## 1. Bối cảnh

"Báo cáo nhanh" là báo cáo **định kỳ theo đợt ứng phó ngập/úng**, dùng **chung một mẫu cho 4 công ty thủy lợi của Hà Nội** (Hà Nội, Sông Nhuệ, Sông Đáy, Sông Tích) gửi UBND Thành phố. Mẫu gốc lấy khung thời gian ví dụ "Từ 6h ngày 24/8/2026 đến 16h ngày 24/8/2026" — tức báo cáo theo **từng kỳ trong ngày** (không phải 1 lần/ngày cố định), cần cho phép người dùng chọn/nhập khung giờ báo cáo khi tạo.

Báo cáo gồm **1 văn bản chính** (số liệu tổng hợp theo công ty) kèm **3 phụ lục** (số liệu chi tiết theo trạm/theo xã) — khi gửi chính thức, phần "Bảng 1", "Bảng 2"... chỉ là tên nội bộ để trao đổi, **không in ra** trong file gửi đi.

---

## 2. Ba nguồn dữ liệu đầu vào (đúng theo yêu cầu Công ty)

| # | Nguồn | Trạng thái | Vai trò trong báo cáo |
|---|---|---|---|
| 1 | Danh mục máy bơm, trạm bơm | ✅ đã có trong memory: [[danh-muc-tram-bom]] (7 XNTL, 211 trạm, 1.011 máy) | Nguồn cho Phụ lục 1 (Bảng 1, Bảng 2) |
| 2 | Dữ liệu đo mực nước/lượng mưa tự động (link BHH) | ✅ đã đặc tả kiến trúc ở `spec-bieu-do-muc-nuoc.md`, `spec-cong-thuc-noi-suy.md` | Nguồn cho Phụ lục 2 (Bảng 3, Bảng 4) |
| 3 | Mẫu Báo cáo nhanh (.docx) | Đọc trong phiên này | **Chỉ là khuôn/format** — tiêu đề, cấu trúc bảng, chữ ký; **số liệu mẫu trong file (97 trạm/396 máy, 15mm mưa...) là số minh hoạ, không phải số thật, không hard-code** |

> ⚠️ Chu kỳ đo trong yêu cầu này (**mực nước 10 phút/lần, mưa 1 giờ/lần**) là chu kỳ **nguồn dữ liệu BHH**, khác với chu kỳ **polling hiển thị UI 60 phút** đã chốt cho biểu đồ chi tiết từng cống ở `spec-bieu-do-tung-cong.md` — 2 khái niệm không mâu thuẫn (nguồn cập nhật nhanh hơn UI hiển thị), nhưng module Báo cáo nhanh này cần đọc trực tiếp giá trị **mới nhất tại đúng thời điểm chốt báo cáo** (ví dụ đúng 16h), không lấy giá trị UI đã polling trễ.

---

## 3. Cấu trúc nội dung chính (thân báo cáo)

### 3.1 Tiêu đề
`BÁO CÁO NHANH — Công tác triển khai ứng phó với ngập lụt, úng trong hệ thống công trình thủy lợi do Công ty TNHH MTV Đầu tư phát triển Thuỷ lợi Sông Nhuệ quản lý (Từ {gio_bắt_đầu}h ngày {ngày} đến {gio_kết_thúc}h ngày {ngày})` — 2 mốc giờ là input người dùng chọn khi tạo báo cáo.

### 3.2 Mục 1 — Tình hình vận hành trạm bơm
Bảng tổng hợp theo **4 công ty thủy lợi** (không phải theo XNTL — XNTL là cấp phụ lục):

| TT | Công ty thuỷ lợi | Tổng số trạm | Tổng số máy | Tổng lưu lượng (m3/h) |
|---|---|---|---|---|
| | **Tổng cộng:** | =SUM | =SUM | =SUM |
| 1 | Hà Nội | | | |
| 2 | Sông Nhuệ | | | |
| 3 | Sông Đáy | | | |
| 4 | Sông Tích | | | |

- Dòng "Sông Nhuệ": **"Tổng số trạm" và "Tổng số máy" không tự nhập ở đây** — 2 số này **copy trực tiếp từ dòng "Sông Nhuệ" đã tính sẵn ở Bảng 1** (mục 4.1), sau khi cán bộ đã nhập xong toàn bộ Bảng 2 cho kỳ báo cáo. Xem chuỗi tính toán đầy đủ ở mục 4.1–4.2 (Bảng 2 → Bảng 1 → quay lại đây).
- Công ty Sông Nhuệ chỉ điền được dòng của mình, 3 công ty còn lại (Hà Nội, Sông Đáy, Sông Tích) nằm ngoài phạm vi dữ liệu Công ty Sông Nhuệ quản lý, cần xác nhận cơ chế nhập/đồng bộ giữa các công ty (xem OI-BC1).
- Có 1 dòng ghi chú bắt buộc riêng cho trạm Yên Nghĩa (xem mục 3.4).

### 3.3 Mục 2 — Mực nước, lượng mưa
Không có bảng trong thân báo cáo — chỉ 1 dòng dẫn chiếu "Chi tiết tại phụ lục 2 kèm theo". → module chỉ cần sinh liên kết/đính kèm phụ lục, không cần render bảng ở đây.

### 3.4 Ghi chú riêng — Trạm bơm Yên Nghĩa
```
Ghi chú: Trạm bơm Yên Nghĩa vận hành {X} máy bơm với tổng lưu lượng bơm {Y} m3/s.
```
Nếu trạm không chạy máy nào, thay bằng: `Ghi chú: Trạm bơm Yên Nghĩa không vận hành.`
- `X` = số máy đang chạy (nhập tay, xem mục 5), lấy từ dòng "Yên Nghĩa" trong Phụ lục 1/Bảng 2.
- `Y` = tổng lưu lượng = `X` × Q/máy, đổi từ m3/h sang **m3/s** (chia 3600) — khác đơn vị m3/h dùng ở mọi bảng khác trong báo cáo, cần lưu ý khi code hàm tính.
- Yên Nghĩa là trạm bơm tiêu lớn nhất hệ thống: 10 máy, Q/máy = 43.200 m3/h (xem [[tram-bom-xntl-hoai-duc]]) — đây là lý do được tách riêng thành 1 dòng ghi chú bắt buộc, không gộp chung bảng.

### 3.5 Mục 3 — Diện tích ngập úng
Bảng tổng hợp theo 4 công ty (khác thứ tự với mục 1: Sông Đáy, Sông Nhuệ, Sông Tích, Hà Nội), 9 cột số liệu chia theo **Ngập trắng / Sâu nước / Tổng cộng × Lúa / Rau,màu,thuỷ sản / Cộng**:

| TT | Địa bàn | Ngập trắng: Lúa | Ngập trắng: Rau,màu,TS | Ngập trắng: Cộng | Sâu nước: Lúa | Sâu nước: Rau,màu,TS | Sâu nước: Cộng | Tổng: Lúa | Tổng: Rau,màu,TS | Tổng: Cộng |
|---|---|---|---|---|---|---|---|---|---|---|

- Mỗi ô "Cộng" = tổng Lúa + Rau,màu,TS cùng hàng (formula, không nhập tay).
- Dòng "Tổng cộng:" đầu bảng = tổng theo cột của cả 4 công ty (formula).
- Dòng "Sông Nhuệ" = tổng từ Phụ lục 3/Bảng 5 (tổng theo xã thuộc Công ty Sông Nhuệ quản lý).

### 3.6 Khối ký tên
`Nơi nhận: UBND Thành phố; Văn phòng BCH PTDS TP; Sở Nông nghiệp và Môi trường; Chi cục Thủy lợi và PCTT; Lưu: VT, QLN.` + khối "TỔNG GIÁM ĐỐC" (ký tên, để trống — không phải dữ liệu hệ thống điền).

---

## 4. Phụ lục 1 — Bảng 1 & Bảng 2 (trạm bơm)

Chuỗi xử lý đi theo **1 chiều duy nhất**: cán bộ nhập vào Bảng 2 (theo từng trạm) → hệ thống tự tính lên Bảng 1 (theo từng công ty) → hệ thống tự copy 2 số của Bảng 1 ngược lại Mục 1 thân báo cáo (mục 3.2). Không có chiều nhập ngược lại.

### 4.2 Bảng 2 — Nhập liệu theo từng trạm (nguồn cấp cho Bảng 1)

6 cột: TT | Tên công trình | Tổng số máy | Q 1 máy (m3/h) | **Tình hình vận hành (số máy)** | Nguồn tưới, hướng tiêu — nhóm theo XNTL.

**Điểm quan trọng khi code (khác với cách đã lưu ở `danh-muc-tram-bom`):** báo cáo này cần **mỗi nhóm máy cùng công suất Q là 1 dòng riêng** (ví dụ trạm "Đại Áng (tiêu)" có 4 máy Q=4.000 VÀ 1 máy Q=1.950 → 2 dòng). Catalog `danh-muc-tram-bom` đã lưu trước đó **gộp** các nhóm máy phụ vào cột "Ghi chú" dạng `+N máy Q=...` để rút gọn — **không dùng trực tiếp catalog đã gộp cho báo cáo này**, mà phải dùng lại dữ liệu gốc **chưa gộp** từ file `Danh_mu_c_TB_Cty_SN_2026.xlsx` (cấp độ "1 dòng = 1 nhóm máy cùng Q"), vì Bảng 1 cần đúng số lượng máy theo từng cỡ Q.

**Quy trình nhập (theo đúng yêu cầu Công ty):**
1. Cán bộ **chọn/gõ tên công trình (trạm bơm)** → hệ thống tự hiện toàn bộ các dòng nhóm máy của trạm đó, mỗi dòng đã có sẵn **Tổng số máy** và **Q 1 máy** (khoá, lấy từ danh mục, không sửa).
2. Cán bộ **chỉ nhập 1 số duy nhất cho mỗi dòng nhóm máy**: số máy đang hoạt động, vào ô "Tình hình vận hành (số máy)". Ví dụ: trạm Yên Nghĩa có 1 dòng (10 máy, Q=43.200) → cán bộ nhập "5" nếu có 5/10 máy đang chạy.
3. "Tình hình vận hành (số máy)" **luôn ≤ Tổng số máy** của đúng dòng đó (validate) — số máy đang chạy không thể vượt số máy thiết kế của nhóm.

Field "Tình hình vận hành (số máy)" là **số nhập tay theo từng kỳ báo cáo** (không phải hằng số danh mục), phải lưu tách riêng khỏi `total_may` (cố định, từ danh mục): ví dụ `may_dang_van_hanh` gắn với `kỳ báo cáo` + `thời điểm nhập` + `người nhập`, khoá ngoại tới đúng dòng nhóm máy (trạm + Q) trong danh mục.

### 4.1 Bảng 1 — Tổng hợp toàn Thành phố theo công ty, chia theo cỡ máy (TỰ TÍNH TỪ BẢNG 2)

14 cột: TT, Công ty thuỷ lợi, Tổng số trạm, Tổng số máy, **9 cột "Trong đó loại máy bơm (1.000 m3/h)"**: `43 | 22 | 12 | 8 | 4 | 2÷3 | 1,1÷1,9 | 1 | <1`, Tổng lưu lượng (m3/h).

**Toàn bộ 4 nhóm cột đều tính từ dữ liệu "Tình hình vận hành" vừa nhập ở Bảng 2 trong kỳ báo cáo hiện tại — KHÔNG lấy từ tổng số liệu tĩnh của danh mục (211 trạm/1.011 máy toàn công ty).** Công thức từng cột, tính riêng cho mỗi công ty (dòng "Sông Nhuệ" chỉ tính trên các trạm thuộc Công ty Sông Nhuệ):

- **Tổng số trạm** = đếm số trạm **có ít nhất 1 nhóm máy đang hoạt động > 0** trong kỳ báo cáo (COUNT DISTINCT trạm, không phải SUM). Một trạm có nhiều nhóm máy, nhiều máy cùng đang chạy **vẫn chỉ tính là 1 trạm**.
- **Tổng số máy** = SUM toàn bộ giá trị "Tình hình vận hành (số máy)" trên mọi dòng/mọi trạm của công ty đó (không phải tổng số máy thiết kế).
- **9 cột cỡ máy** (43, 22, 12, 8, 4, 2÷3, 1,1÷1,9, 1, <1 nghìn m3/h): với mỗi dòng ở Bảng 2, cộng giá trị "Tình hình vận hành" của dòng đó vào đúng cột ứng với Q của dòng. Ví dụ: trạm Yên Nghĩa có 5 máy 43.000 m3/h đang hoạt động → cộng 5 vào cột "43" của dòng Sông Nhuệ (hoặc dòng công ty tương ứng nếu Yên Nghĩa không thuộc Sông Nhuệ — xem OI-BC4 về việc Yên Nghĩa thuộc nhóm XNTL nào).
- **Tổng lưu lượng (m3/h)** = SUM(Tình hình vận hành × Q 1 máy) trên mọi dòng của công ty đó.
- Dòng "Tổng cộng:" trên cùng = tổng theo cột của cả 4 công ty (formula, không nhập tay).
- **Cấu trúc 9 cột cỡ máy trùng khớp với sheet `Sheet3` đã thấy trong `Danh_mu_c_TB_Cty_SN_2026.xlsx`** — xác nhận đây là bảng phân loại chuẩn dùng chung toàn Thành phố, không phải Công ty tự đặt ra.

### 4.3 Quay lại Mục 1 thân báo cáo (mục 3.2)

Sau khi Bảng 1 đã tính xong, hệ thống **copy đúng 2 giá trị "Tổng số trạm" và "Tổng số máy" của dòng "Sông Nhuệ" ở Bảng 1** sang dòng "2 Sông Nhuệ" ở bảng Mục 1 (mục 3.2) — không tính lại công thức riêng ở đó, chỉ tham chiếu/copy để đảm bảo 2 nơi luôn khớp nhau tuyệt đối.

### 4.4 Tuỳ chọn ẩn trạm không hoạt động (cho gọn)

Với 211 trạm trong danh mục nhưng thường chỉ một phần đang chạy tại 1 kỳ báo cáo, Bảng 2 cần **tuỳ chọn ẩn bớt trạm không có hoạt động** để dễ xem/nhập:

- Thêm 1 công tắc (toggle) trên màn hình Bảng 2, ví dụ **"Chỉ hiện trạm đang hoạt động"** — mặc định **tắt** (đề xuất, hiện đủ toàn bộ danh mục để không bỏ sót khi rà soát; Công ty xác nhận lại nếu muốn mặc định bật — xem OI-BC6), người dùng bật/tắt được bất cứ lúc nào.
- **Đơn vị ẩn là cả trạm, không ẩn từng dòng nhóm máy lẻ**: 1 trạm bị ẩn khi và chỉ khi **tất cả** các dòng nhóm máy của trạm đó có "Tình hình vận hành" = 0 hoặc chưa nhập. Chỉ cần 1 nhóm máy > 0 thì hiện **đủ mọi dòng** của trạm đó (kể cả các nhóm máy khác của cùng trạm đang = 0), để người xem thấy đúng tỷ lệ máy đang chạy/tổng số máy của trạm.
- Việc ẩn **chỉ là filter hiển thị**, không xoá hay bỏ qua dữ liệu: các dòng "0 máy chạy" của trạm bị ẩn vẫn phải lưu đủ trong CSDL và vẫn được tính đúng vào Bảng 1 (không ảnh hưởng công thức ở mục 4.1 — trạm có 0 máy chạy vốn đã không được đếm vào "Tổng số trạm" dù có ẩn hiển thị hay không).
- **Phạm vi áp dụng**: mặc định áp dụng cho màn hình nhập liệu (giúp cán bộ đỡ cuộn qua nhiều trạm không liên quan). Việc có áp dụng luôn cho **bản báo cáo chính thức xuất ra** (file gửi UBND Thành phố) hay bản chính thức luôn liệt kê đủ toàn bộ danh mục (kể cả trạm 0 máy) — **cần Công ty xác nhận, xem OI-BC7**, vì đây là văn bản hành chính chính thức nên có thể cần giữ đủ danh sách để đối chiếu.

---

## 5. Yêu cầu nhập liệu vận hành (UX — theo đúng đề nghị của Công ty trong file mẫu)

- Cán bộ **gõ/chọn tên trạm bơm** (ô tìm kiếm/autocomplete) → hệ thống tự hiện **các dòng nhóm máy** (theo Q) thuộc đúng trạm đó, kèm sẵn Tổng số máy + Q/máy (khoá, lấy từ danh mục).
- Cán bộ chỉ nhập **1 con số cho mỗi dòng nhóm máy**: số máy đang vận hành — không nhập lại tên trạm, Q/máy, tổng số máy.
- Toàn bộ phần còn lại là **tự động, không cán bộ nào tổng hợp tay**, theo đúng 1 chiều: Bảng 2 (nhập) → Bảng 1 (tự tính theo công ty, theo cỡ máy) → Mục 1 thân báo cáo dòng "Sông Nhuệ" (tự copy từ Bảng 1) → ghi chú Yên Nghĩa ở mục 3.4 (tự tính từ đúng dòng Yên Nghĩa ở Bảng 2).

---

## 6. Phụ lục 2 — Bảng 3 & Bảng 4 (mực nước, lượng mưa)

**Toàn bộ số liệu 2 bảng này lấy tự động — không nhập tay** (khác hẳn Bảng 2/Bảng 5 là nhập tay). Đúng theo kiến trúc đã chốt ở `spec-bieu-do-muc-nuoc.md` (mục 2): báo cáo **đọc từ CSDL nội bộ** (`wl_reading`, `rain_reading` — đã được backend job đồng bộ sẵn từ API BHH theo giờ), **không tự gọi thẳng API BHH tại thời điểm xuất báo cáo**. Cách tính khác nhau giữa 2 bảng vì bản chất đại lượng khác nhau:

- **Bảng 3 (mực nước) — giá trị tức thời tại đúng 1 mốc giờ**: tiêu đề cột ghi rõ "Mực nước hồi `{giờ}`h ngày `{ngày}`" (ví dụ "hồi 16h") — hệ thống lấy **đúng bản ghi tại giờ đó** (giờ kết thúc kỳ báo cáo) từ API, không cộng dồn, không lấy trung bình. Nếu API đo mỗi 10 phút/lần mà giờ chốt báo cáo không trùng đúng mốc đo, áp dụng lại quy tắc "lấy bản ghi gần nhất trước đó" đã có ở mục 4.1 `spec-bieu-do-tung-cong.md`.
- **Bảng 4 (lượng mưa) — giá trị cộng dồn theo khoảng thời gian**: tiêu đề cột ghi "Lượng mưa từ `{giờ_bắt_đầu}`h ngày ... đến `{giờ_kết_thúc}`h ngày ..." (ví dụ "từ 6h đến 16h") — hệ thống phải **cộng dồn (SUM) các bản ghi mưa theo giờ trong API** trong đúng khoảng đó, không phải giá trị tại 1 thời điểm. 2 mốc giờ này chính là 2 mốc giờ đã chọn ở tiêu đề toàn báo cáo (mục 3.1), không phải nhập riêng.
- Cả 2 mốc giờ trong Bảng 3 và Bảng 4 **phải luôn trùng với khung giờ đã chọn ở tiêu đề báo cáo** (mục 3.1) — không cho phép chọn giờ khác đi khi đã chốt khung giờ báo cáo.

### 6.1 Bảng 3 — Mực nước

Bảng mẫu là **bảng dùng chung toàn Thành phố**, liệt kê nhiều hệ thống sông (Đà, Hồng, Tích, Bùi, Đáy, Thanh Hà, Cầu, Đuống, Cà Lồ, Ngũ Huyện Khê...) — **Công ty Sông Nhuệ chỉ chịu trách nhiệm mục "11. Hệ thống sông Nhuệ (cống)"**: Liên Mạc, Hà Đông, Đồng Quan, Hòa Mỹ, Vân Đình, Nhật Tựu, Lương Cổ (TL/HL mỗi điểm).

- **Cần Công ty xác nhận cơ chế hiển thị**: hệ thống tạo báo cáo của Công ty Sông Nhuệ có cần render toàn bộ bảng 39 dòng (các hệ thống sông của công ty khác để trống) hay chỉ xuất phần mục 11 rồi ghép vào bảng chung ở khâu tổng hợp Thành phố? → xem OI-BC1.

**⚠️ Phát hiện chênh lệch số liệu Km giữa mẫu báo cáo này và `spec-bieu-do-muc-nuoc.md`/2 file PDF biểu đồ mực nước đã dùng trước đó — CẦN CÔNG TY XÁC NHẬN SỐ NÀO ĐÚNG:**

| Trạm | Km trong mẫu Báo cáo nhanh | Km trong biểu đồ mực nước (đã lưu) |
|---|---|---|
| Liên Mạc | H-K53+450 (ghi theo lý trình sông Hồng) | K0+390 (lý trình sông Nhuệ) |
| Hà Đông | K18+182 | K18+100 |
| Đồng Quan | K43+694 | K43+750 |
| Hòa Mỹ | K1+446 | K1+460 |
| Vân Đình | Đ-K65+348 (lý trình sông Đáy) | K72+000 |
| Nhật Tựu | K63+405 | K63+405 (khớp) |
| Lương Cổ | K72+506 | K72+506 (khớp) |

→ 5/7 điểm lệch số Km, đặc biệt Liên Mạc và Vân Đình còn lệch cả **hệ quy chiếu lý trình** (sông Hồng/sông Đáy so với sông Nhuệ) — ảnh hưởng trực tiếp tới **công thức nội suy đã chốt ở `spec-cong-thuc-noi-suy.md`** (dùng đúng các số Km 1085/18100/43750/63405 làm hằng số cố định). Nếu số liệu mẫu báo cáo này mới hơn/đúng hơn thì công thức nội suy cần cập nhật lại hằng số — **không tự sửa, chờ Công ty xác nhận nguồn nào là số chính thức**.

### 6.2 Bảng 4 — Lượng mưa

Tương tự: bảng chung toàn Thành phố (37 điểm đo), Công ty Sông Nhuệ chỉ điền 7 điểm: Liên Mạc, Hà Đông, Đồng Quan, Hòa Mỹ, Vân Đình, Nhật Tựu, Lương Cổ, **và thêm Điệp Sơn** (8 điểm — Điệp Sơn không có trong danh sách mực nước Bảng 3 nhưng có trong Bảng 4 mưa, cũng là node đã có trong `spec-bieu-do-muc-nuoc.md` mục Sông Duy Tiên).

---

## 7. Phụ lục 3 — Bảng 5 (diện tích ngập úng theo xã/phường)

- Dữ liệu **nhập tay hoàn toàn theo xã/phường** — không có API tự động (khác hẳn nguồn mực nước/mưa).
- Danh sách 20 xã/phường thuộc Công ty Sông Nhuệ trong mẫu: Đông Ngạc, Thượng Cát, Đại Mỗ, Thanh Trì, Đại Thanh, Nam Phù, Ngọc Hồi, Thường Tín, Thượng Phúc, Chương Dương, Hồng Vân, Phú Xuyên, Phượng Dực, Chuyên Mỹ, Đại Xuyên, Ứng Hòa, Hòa Xá, Ứng Thiên, Vân Đình, Lĩnh Nam — đây là **địa giới hành chính phường/xã sau sáp nhập**, không phải tên huyện cũ, khớp với điểm mâu thuẫn "Phường Hà Đông vs Quận Hà Đông" đã ghi trong `project_song_nhue_website.md` — dùng danh sách này làm chuẩn cho mọi nơi cần liệt kê đơn vị hành chính của Công ty Sông Nhuệ.
- Mỗi ô "Cộng" = Lúa + Rau,màu (formula). Dòng tổng theo công ty ("III Công ty TL Sông Nhuệ") = tổng theo cột của toàn bộ 20 xã (formula).
- **Ai nhập, tần suất nhập (mỗi kỳ báo cáo hay chỉ khi có phát sinh ngập) chưa được nêu rõ → OI-BC2.**

---

## 8. Open Issues

| Mã | Nội dung | Ảnh hưởng |
|---|---|---|
| OI-BC1 | Cơ chế phối hợp dữ liệu giữa 4 công ty thủy lợi (Hà Nội/Sông Nhuệ/Sông Đáy/Sông Tích) khi cùng dùng 1 mẫu báo cáo — Công ty Sông Nhuệ chỉ nhập được phần của mình, ai tổng hợp thành báo cáo chung cuối cùng? | Quyết định phạm vi hệ thống: chỉ làm phần Sông Nhuệ, hay cần cổng tổng hợp liên công ty |
| OI-BC2 | Diện tích ngập úng theo xã: ai nhập, tần suất nhập | Thiết kế quy trình nhập liệu, phân quyền |
| OI-BC3 | **Chênh lệch Km giữa mẫu báo cáo và biểu đồ mực nước đã lưu** (mục 6.1) — số nào đúng, có cần sửa lại hằng số nội suy đã chốt không | Sai lý trình ảnh hưởng độ chính xác công thức nội suy 2 điểm cửa sông |
| OI-BC4 | Nhóm XNTL trong mẫu báo cáo (5 nhóm, có gộp/đổi tên) khác danh mục gốc đã lưu (7 nhóm) — ví dụ mẫu gộp "XNTL Từ Liêm" nhưng thiếu 4 trạm tưới của Bắc Từ Liêm; nhóm "XNTL Hà Đông" (Yên Nghĩa) trong mẫu tương ứng nhóm "Hoài Đức" trong danh mục gốc | Cần xác nhận cách đặt tên/gộp nhóm chính thức trước khi code Bảng 2 |
| OI-BC5 | Đơn vị lưu lượng trong ghi chú Yên Nghĩa là **m3/s** trong khi mọi bảng khác dùng **m3/h** — xác nhận đây có đúng ý Công ty hay là lỗi đánh máy trong mẫu | Sai đơn vị hiển thị nếu không xử lý đúng phép đổi |
| OI-BC6 | Công tắc "chỉ hiện trạm đang hoạt động" ở Bảng 2 — mặc định bật hay tắt | Ảnh hưởng trải nghiệm nhập liệu hàng ngày |
| OI-BC7 | Bản báo cáo chính thức xuất ra (gửi UBND TP) có áp dụng ẩn trạm không hoạt động hay luôn liệt kê đủ toàn bộ danh mục | Ảnh hưởng tính đầy đủ của văn bản hành chính chính thức |

> **Trạng thái 19/9/2026** (bảng trên giữ nguyên văn BA): Công ty đã trả lời OI-BC1 · 2 · 4 · 5 · 6 · 7 cùng OI-BC8→17 —
> xem phụ lục *Công ty trả lời*. Còn mở: **OI-BC3** (thuộc màn hình biểu đồ mặt cắt, `T66.15`) · **OI-BC4/10** trả lời xong
> nhưng đụng OI-05 (`T66.14`) · OI-BC13 chưa đánh dấu.

---

## 9. Định nghĩa "Done"

> Đối chiếu từng mục với phép kiểm: phụ lục *Đối chiếu Định nghĩa Done (§9)* — ô `[ ]` dưới đây là văn bản BA, ⛔ phải trạng thái.

- [ ] Toàn bộ ô "Tổng cộng"/"Cộng" trong mọi bảng (Bảng 1, Bảng 2 phần đầu, thân báo cáo mục 1 & 3, Bảng 5) là công thức tự tính, không cho nhập tay.
- [ ] Bảng 2 Phụ lục 1 giữ granularity 1 dòng/1 nhóm máy cùng Q (không gộp như catalog `danh-muc-tram-bom`).
- [ ] "Tổng số trạm" ở Bảng 1 = COUNT trạm có ≥1 máy đang chạy trong kỳ (không phải SUM, không phải tổng số trạm trong danh mục tĩnh) — 1 trạm nhiều máy đang chạy vẫn tính 1.
- [ ] "Tổng số máy" và 9 cột cỡ máy ở Bảng 1 = SUM đúng cột "Tình hình vận hành" của Bảng 2 theo từng cỡ Q, không lấy tổng số máy thiết kế.
- [ ] Dòng "Sông Nhuệ" ở Mục 1 thân báo cáo copy đúng 2 số từ dòng "Sông Nhuệ" ở Bảng 1, không có công thức tính riêng — luôn khớp tuyệt đối giữa 2 nơi.
- [ ] Công tắc ẩn trạm không hoạt động ở Bảng 2 hoạt động theo đơn vị "cả trạm" (không ẩn dòng lẻ), không xoá/bỏ sót dữ liệu khi tính Bảng 1.
- [ ] Cột "Tình hình vận hành (số máy)" lưu riêng theo từng kỳ báo cáo, không ghi đè lên "Tổng số máy" của danh mục gốc.
- [ ] Ghi chú Yên Nghĩa tự sinh đúng câu chữ theo mẫu (kể cả trường hợp "không vận hành"), đổi đúng đơn vị m3/s.
- [ ] Nhập liệu vận hành theo đúng luồng gõ tên trạm → hiện nhóm máy → nhập số đang chạy.
- [ ] Phụ lục 2 (Bảng 3, 4) chỉ điền đúng phần dữ liệu thuộc Công ty Sông Nhuệ, không tự bịa số cho hệ thống sông của công ty khác.
- [ ] Bảng 3 lấy đúng giá trị tức thời tại giờ kết thúc kỳ báo cáo (không cộng dồn); Bảng 4 lấy đúng tổng cộng dồn trong cả khung giờ báo cáo (không phải giá trị tức thời) — không lẫn 2 cách tính.
- [ ] Không dùng số liệu mẫu trong file `Mẫu Báo cáo nhanh.docx` (97 trạm/396 máy, 15mm mưa, 20/20/40ha...) làm dữ liệu thật ở bất kỳ đâu trong code hay dữ liệu test cuối cùng.

---

## Phụ lục — Đối chiếu với kho mã (18/09/2026, WS-66)

Phần trên giữ nguyên văn bản BA. Những chỗ dưới đây **đo được là lệch** khi dựng; bản dựng đi theo cột "Kho làm":

| Mục spec | Spec ghi | Đo được | Kho làm |
|---|---|---|---|
| §2 nguồn Bảng 2 | "211 trạm, 1.011 máy" | con số ấy đọc từ sheet `Trạm bơm` (tự lệch: cộng dòng ra 1.025). Bảng 2 của mẫu khớp 1:1 sheet **`TB Tiêu (KH)`** — 178 trạm / 830 máy | nhập theo `TB Tiêu (KH)` (OI-BC9) |
| §4.1 phân cỡ | `Sheet3` là "bảng phân loại chuẩn" | `Sheet3` **rỗng**; 35/830 máy ⛔ thuộc cột nào theo nhãn | 9 cỡ biên đề xuất, sửa được (OI-BC8) |
| §4.2 loại dòng | 2 loại | 3 loại — dòng ⛔ số TT mà **có tên** là một trạm khác (`Ngọ Xá II`, `Xém (mới)`…) | mỗi trạm một công trình |
| §6 Bảng 4 | "lấy tự động" | ⛔ điểm đo mưa nào (G3-a) | **nhập tay theo kỳ** 8 điểm Sông Nhuệ, để trống = ô trống (đổi 18/09 tối; ngày G3-a về thì nguồn tự động THAY ô nhập) |
| tên bảng | `wl_reading` · `rain_reading` · `hydro_station` | ⛔ tồn tại | `hydro_readings` · `stations` (lọc `HOP_LE`) |
| Bảng 3 Lương Cổ | — | mẫu ghi TL có số | `F01519` đổi về Thượng lưu (`V202609181085`) |
| Bảng 3 điểm đo · ghi chú Yên Nghĩa | mã cố định | 3/14 vế ⛔ có điểm đo (OI-BC14); danh mục có HAI công trình tên "Yên Nghĩa" | Công ty **chọn công trình** cho 7 cống + trạm Yên Nghĩa trên màn hình *Cấu hình Báo cáo nhanh*; điểm đo từng vế suy từ liên kết điểm đo–công trình (`V202609181088`) |

Open issue mới: OI-BC8 → OI-BC17 ở `.claude/master-tracking.md` T66.8.

### Công ty trả lời (19/09/2026 — `xacnhan.md` cùng thư mục)

| Mã | Trả lời | Kho làm |
|---|---|---|
| OI-BC1 · 2 · 5 · 6 · 7 · 12 · 15 · 16 | Đồng ý phương án đang chạy | ⛔ đổi |
| OI-BC4 · OI-BC10 | Bảng 2 chia theo **7 nhóm của sheet `Trạm bơm`** (Thanh Trì · Thường Tín · Phú Xuyên · Ứng Hoà · Bắc Từ Liêm · Nam Từ Liêm · Hoài Đức) | ⚠ Khối Bảng 2 lấy từ đơn vị quản lý của công trình (`org_units`). Danh sách này là danh sách **thứ ba**, khác cả hai danh sách của OI-05 ⇒ chưa nhập Xí nghiệp cho tới khi chốt MỘT danh sách (`T66.14`, `architecture-review.md` §12.2 h) |
| OI-BC8 | Nhãn cột là cỡ danh định **đã làm tròn** (43.200 in ở "43"); giữ nguyên 9 cột, tính theo biên đã gửi | ⛔ đổi biên; ghi chú trên màn hình *Danh mục máy bơm* đổi từ "đề xuất" sang "đã xác nhận" |
| OI-BC9 | Đồng ý `TB Tiêu (KH)` | ⛔ đổi |
| OI-BC11 | `F01771` thuộc **Sông Nhuệ** | ⛔ đổi dữ liệu điểm đo |
| OI-BC13 | ⛔ trả lời | giữ nguyên chữ của mẫu ở cả hai chỗ |
| OI-BC14 | Chưa có điểm đo ở 3 vế | 3 ô để trống kèm lý do |
| OI-BC17 | **Cho nới** cột "Lúa" nhóm Tổng cộng ở Bảng 5 | bản xuất chia đều bề rộng "Lúa"/"Rau, màu" của nhóm ấy (631 + 990 → 810 + 811 twip, tổng ⛔ đổi); tệp mẫu trong kho vẫn trùng byte bản Công ty gửi |

OI-BC3 (lệch Km) thuộc màn hình biểu đồ mặt cắt — ⛔ phải phạm vi Báo cáo nhanh, vẫn mở.

### Đối chiếu Định nghĩa Done (§9) — 19/9/2026

Mỗi dòng trỏ tới phép kiểm đang chạy trong CI (`BaoCaoNhanhHttpTest` qua HTTP · `TinhBaoCaoNhanhTest` JUnit trần ·
`baoCaoNhanhRules.test.ts` FE). ⛔ Đánh dấu ✅ theo lời khai — mỗi ✅ có tên bài kiểm.

| # | Mục §9 | Trạng thái | Bằng chứng |
|---|---|---|---|
| 1 | Ô "Tổng cộng"/"Cộng" tự tính, ⛔ nhập tay | ✅ | `TinhBaoCaoNhanhTest` bất biến 5 · màn hình chỉ đọc, số từ BE |
| 2 | Bảng 2 một dòng = một nhóm máy cùng Q | ✅ | `nhom_may_bom` duy nhất theo (trạm, Q) · `xuatWordKhuHoi` (trạm 2 nhóm ⇒ 2 dòng, gộp dọc) |
| 3 | Tổng số trạm = trạm có ≥ 1 máy chạy | ✅ | `TinhBaoCaoNhanhTest` bất biến 3 |
| 4 | Tổng số máy + 9 cột = số đang chạy theo cỡ | ✅ | `TinhBaoCaoNhanhTest` bất biến 1 + 3 |
| 5 | Mục 1 chép đúng dòng Sông Nhuệ của Bảng 1 | ✅ | `TinhBaoCaoNhanhTest` bất biến 2 · `chuoiTinhMotChieu` |
| 6 | Công tắc ẩn theo cả trạm, ⛔ mất dữ liệu | ✅ | `baoCaoNhanhRules.test.ts` "trạm hoạt động ⇔ ít nhất một nhóm > 0" · Bảng 1 tính trên dữ liệu, ⛔ trên bộ lọc |
| 7 | Số máy vận hành lưu riêng theo kỳ | ✅ | bảng `bao_cao_nhanh_van_hanh` · `chotVaMoLai` (danh mục đổi sau chốt ⛔ đổi văn bản) |
| 8 | Ghi chú Yên Nghĩa đúng câu, cả ca ⛔ vận hành, m³/s | ✅ | `TinhBaoCaoNhanhTest` bất biến 4 · `xuatWordKhuHoi` |
| 9 | Nhập theo luồng gõ tên trạm → nhóm máy → số đang chạy | ✅ | `baoCaoNhanhRules.test.ts` "tìm theo tên trạm" · kiểm tay trên trình duyệt T66.7 |
| 10 | Phụ lục 2 chỉ điền phần Sông Nhuệ | ✅ | `xuatWordKhuHoi` (Bảng 4 công ty khác trống) |
| 11 | Bảng 3 giá trị tức thời tại giờ kết thúc; Bảng 4 cộng dồn | ✅ Bảng 3 · 🔄 Bảng 4 | Bảng 3: `bang3MucNuocTaiThoiDiem`. Bảng 4 **đổi**: nhập tay theo kỳ (OI-BC15) — phép cộng dồn chỉ có khi nguồn mưa tự động về (G3-a) |
| 12 | ⛔ dùng số liệu mẫu làm dữ liệu thật | ✅ | `xuatWordKhuHoi` khẳng định số minh hoạ của mẫu ("200", "12", các số 0) bị xoá · ⛔ seed trạm/máy nào |

