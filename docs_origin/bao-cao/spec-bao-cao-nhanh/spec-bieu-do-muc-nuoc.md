# SPEC: Màn hình "Biểu đồ mực nước" — MOD-03 (nhóm task L, trực ban TV)

- Phiên bản: 1.0 — 16/09/2026
- Người viết: BA dự án Website Công ty TNHH MTV ĐTPT Thủy lợi Sông Nhuệ
- Nguồn mẫu: `3.1 Biểu đồ mực nước (ghi chú).pdf` (bản giải thích) + `3.2 Biểu đồ mực nước (hiển thị).pdf` (bản chạy thật)
- Liên quan: `spec-description.md` (SPEC MOD-03 v1.0), `MOD-03_Thong_ke_cong_viec.xlsx` — nhóm task **L** (màn hình trực ban chiếu tivi 24/24)

> Đọc trước khi code: mọi ràng buộc kiến trúc của MOD-03 (token nguồn, chia 100 cm→m, phân quyền tầng API, không hard-code) vẫn áp dụng nguyên vẹn cho màn hình này — spec này chỉ bổ sung phần **vẽ đúng theo mẫu** + **mốc thời gian** + **chế độ trực ban**.

---

## 1. Mục đích

Một màn hình duy nhất, gồm **6 khung biểu đồ dạng mặt cắt dọc** (profile chart), mỗi khung vẽ chuỗi các cống/điểm đo mực nước nối tiếp nhau bằng đường gấp khúc, số liệu hiển thị to trong vòng tròn tại mỗi điểm — dùng để:
1. Cán bộ trực ban xem nhanh (Q3) — chỉ số tức thời.
2. Cán bộ có quyền cao hơn (Q2+) xem lại số liệu tại một thời điểm bất kỳ trong quá khứ.
3. Chiếu lên tivi phòng trực ban 24/24 (chế độ kiosk, không được rơi về màn hình đăng nhập — áp dụng lại L1–L6 đã chốt).

**Đây KHÔNG phải là biểu đồ đường theo thời gian (time-series realtime chart)** đã quy định phải nằm sau đăng nhập — đây là **mặt cắt không gian tại một thời điểm** (giống một "bảng số liệu" được vẽ trực quan hoá theo tuyến sông). Vì vậy:
- Trạng thái Q3 (xem tức thời) có thể coi tương đương "trang công khai/nội bộ xem nhanh".
- Trạng thái Q2+ (chọn lại thời điểm quá khứ) **phải** nằm sau xác thực, vì đây là tra cứu dữ liệu vận hành, không phải chỉ xem tức thời.
- Nếu Công ty muốn tách hẳn 2 chế độ ra 2 route khác nhau (route công khai chỉ Q3, route quản trị mới có Q2+), ghi vào **OI-L1** bên dưới để Công ty chốt — spec này tạm giả định cả 2 mức quyền nằm chung 1 màn hình, ẩn/hiện phần chọn giờ theo quyền đăng nhập.

---

## 2. Kiến trúc & luồng dữ liệu (kế thừa quyết định đã chốt)

```
songnhue.bhh40.net (BHH4.0, token trong URL)
        │  (backend job đồng bộ định kỳ — KHÔNG gọi từ frontend)
        ▼
   CSDL nội bộ (wl_reading, hydro_station...)
        │
        ▼
  API nội bộ  GET /api/hydro/diagram
        │
        ▼
   Frontend vẽ 6 khung biểu đồ
```

- Frontend **tuyệt đối không** gọi thẳng `http://songnhue.bhh40.net/...` — URL đó chỉ dùng trong job đồng bộ backend, đúng ràng buộc đã ghi ở `project_song_nhue_website.md`.
- Giá trị API BHH4.0 trả về **đơn vị cm nguyên** → backend **chia 100** trước khi lưu (mét), frontend chỉ nhận mét, không tự chia lần nữa.
- Số liệu thiếu → fallback theo mục 8 SPEC MOD-03 (không hiển thị số hard-code giả).

### 2.1 API nội bộ cần backend cung cấp

```
GET /api/hydro/diagram?time=2026-09-16T19:10:00+07:00
GET /api/hydro/diagram            (không truyền time = lấy tức thời, dùng cho Q3)
```

Response mẫu (rút gọn 1 nhóm):

```json
{
  "requested_time": "2026-09-16T19:10:00+07:00",
  "is_realtime": false,
  "groups": [
    {
      "group_id": "song_nhue",
      "title": "BIỂU ĐỒ MỰC NƯỚC TẠI CÁC CỐNG TRÊN SÔNG NHUỆ",
      "nodes": [
        { "node_id": "lien_mac",       "name": "LIÊN MẠC",        "chainage": "K0+390",  "values": { "TL": 6.80, "HL": 5.62 } },
        { "node_id": "dthl_lien_mac",  "name": "ĐTHL LIÊN MẠC",   "chainage": "K1+085",  "values": { "HL": 5.62 } },
        { "node_id": "ha_dong",        "name": "HÀ ĐÔNG",         "chainage": "K18+100", "values": { "TL": 5.00, "HL": 4.99 } },
        { "node_id": "cong_6_cua_hoa_binh", "name": "Cống 6 cửa Hòa Bình", "chainage": "K27+770", "values": { "VALUE": 4.50 } },
        { "node_id": "cong_xa_tieu_vinh_mo2", "name": "Cống xả tiêu TB Vĩnh Mộ 2", "chainage": "K38+500", "values": { "VALUE": 4.20 } },
        { "node_id": "dong_quan",      "name": "ĐỒNG QUAN",       "chainage": "K43+750", "values": { "TL": 3.99, "HL": 3.89 } },
        { "node_id": "nhat_tuu",       "name": "NHẬT TỰU",        "chainage": "K63+405", "values": { "TL": 3.79, "HL": 3.78 } },
        { "node_id": "luong_co",       "name": "LƯƠNG CỔ",        "chainage": "K72+506", "values": { "TL": 3.75 } }
      ]
    }
  ]
}
```

> Các con số 680, 562, 500... trong 2 file PDF mẫu **chỉ là số minh hoạ vị trí đặt vòng tròn trên mặt cắt**, không phải ngưỡng báo động hay dữ liệu thật. Không hard-code các số này vào code — chỉ dùng để test giao diện.

---

## 3. Cấu trúc 6 khung & thứ tự node (đọc đúng theo mẫu)

Bố cục 3 hàng, đúng theo file mẫu (bắt buộc giữ nguyên tỉ lệ hàng/khung khi responsive desktop; xem mục 6 cho mobile):

```
┌──────────────────────────── Hàng 1 (1 khung, full width) ────────────────────────────┐
│                    SÔNG NHUỆ — 8 điểm                                                │
└────────────────────────────────────────────────────────────────────────────────────┘
┌───────────────── Hàng 2 (2 khung, ~50/50) ─────────────────────────────────────────┐
│        SÔNG HỒNG — 6 điểm        │        SÔNG ĐÁY — 3 điểm                        │
└──────────────────────────────────┴───────────────────────────────────────────────┘
┌──────────── Hàng 3 (3 khung, ~33/33/33) ────────────────────────────────────────────┐
│  SÔNG LA KHÊ — 3 điểm  │  SÔNG VÂN ĐÌNH — 2 điểm  │  SÔNG DUY TIÊN — 2 điểm         │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

Mỗi khung là 1 **profile chart**: node theo đúng thứ tự trái→phải dưới đây, nối bằng 1 đường gấp khúc duy nhất đi qua lần lượt các giá trị theo đúng thứ tự liệt kê (không phải nối riêng TL và riêng HL thành 2 đường — chỉ 1 đường zig-zag đi qua toàn bộ điểm, kể cả khi 1 trạm có cả TL và HL thì đường đi xuống từ TL rồi mới sang trạm kế tiếp).

### 3.1 Sông Nhuệ (group_id: `song_nhue`) — hàng 1

| # | node_id | Tên | Chainage | Giá trị | Ghi chú |
|---|---|---|---|---|---|
| 1 | lien_mac | LIÊN MẠC | K0+390 | TL, HL | |
| 2 | dthl_lien_mac | ĐTHL LIÊN MẠC | K1+085 | HL only | |
| 3 | ha_dong | HÀ ĐÔNG | K18+100 | TL, HL | |
| 4 | cong_6_cua_hoa_binh | Cống 6 cửa Hòa Bình | K27+770 | 1 giá trị | không có nhãn TL/HL trong mẫu |
| 5 | cong_xa_tieu_vinh_mo2 | Cống xả tiêu TB Vĩnh Mộ 2 | K38+500 | 1 giá trị | không có nhãn TL/HL trong mẫu |
| 6 | dong_quan | ĐỒNG QUAN | K43+750 | TL, HL | |
| 7 | nhat_tuu | NHẬT TỰU | K63+405 | TL, HL | |
| 8 | luong_co | LƯƠNG CỔ | K72+506 | TL only | điểm cuối, không có HL |

### 3.2 Sông Hồng (`song_hong`) — hàng 2 trái, 6 điểm, mỗi điểm 1 giá trị (không TL/HL)

LIÊN MẠC → LONG BIÊN → HỒNG VÂN → AN CẢNH → THỤY PHÚ II → TẮC GIANG

### 3.3 Sông Đáy (`song_day`) — hàng 2 phải, 3 điểm, mỗi điểm 1 giá trị

YÊN NGHĨA (K38+000) → BA THÁ (K46+500) → VÂN ĐÌNH (K72+000)

### 3.4 Sông La Khê (`song_la_khe`) — hàng 3 trái, 3 điểm

| # | node_id | Tên | Giá trị | Ghi chú |
|---|---|---|---|---|
| 1 | cua_song_la_khe | Cửa sông La Khê (K15+470 bờ hữu sông Nhuệ) | 1 giá trị | **GIÁ TRỊ NỘI SUY** — xem mục 3.7 |
| 2 | be_hut_tb_yen_nghia | Bể hút trạm bơm Yên Nghĩa | 1 giá trị | |
| 3 | yen_nghia_lk | Yên Nghĩa (K6+322) | TL, HL | trùng tên "Yên Nghĩa" với node ở Sông Đáy nhưng là điểm đo khác — dùng node_id riêng, không gộp |

### 3.5 Sông Vân Đình (`song_van_dinh`) — hàng 3 giữa, 2 điểm

| # | node_id | Tên | Giá trị |
|---|---|---|---|
| 1 | hoa_my | Hòa Mỹ (K1+460) | TL, HL |
| 2 | van_dinh_song_vd | Vân Đình (K72+000) | TL, HL |

> Lưu ý: "Vân Đình" ở đây (K72+000, thuộc sông Vân Đình, có TL/HL) khác với "VÂN ĐÌNH" ở mục 3.3 (thuộc Sông Đáy, 1 giá trị, cũng K72+000) — cùng vị trí Km nhưng khác sông/khác bộ dữ liệu, cần 2 node_id riêng biệt, không được để code coi là trùng và gộp lại.

### 3.6 Sông Duy Tiên (`song_duy_tien`) — hàng 3 phải, 2 điểm

| # | node_id | Tên | Giá trị | Ghi chú |
|---|---|---|---|---|
| 1 | cua_song_duy_tien | Cửa sông Duy Tiên (K57+420 bờ tả sông Nhuệ) | 1 giá trị | **GIÁ TRỊ NỘI SUY** |
| 2 | diep_son | Điệp Sơn (K21+000) | TL, HL | |

### 3.7 Điểm "GIÁ TRỊ NỘI SUY" (nội suy)

2 node (cửa sông La Khê, cửa sông Duy Tiên) không phải trạm đo thật — là giá trị **nội suy** từ 2 trạm đo thật gần nhất dọc sông Nhuệ, tại đúng vị trí Km cửa sông nhánh giao với sông Nhuệ. Backend cần:
- Nội suy tuyến tính theo khoảng cách Km giữa 2 trạm đo thật liền kề trên sông Nhuệ bao quanh vị trí K15+470 (cho La Khê) và K57+420 (cho Duy Tiên).
- **Công thức chính xác đã được Công ty chốt — xem `spec-cong-thuc-noi-suy.md` (đóng OI-L2 bên dưới).**
- Trong response API, đánh dấu rõ `"interpolated": true` cho 2 node này để frontend có thể hiển thị khác biệt (ví dụ viền nét đứt hoặc icon nhỏ) nếu Công ty yêu cầu — không bắt buộc trong bản đầu, nhưng field phải có sẵn để tránh phải đổi contract API sau này.

---

## 4. Mốc thời gian ("Thời điểm")

Hai loại ô nhập giờ theo đúng ghi chú trong file mẫu:

- **Mục (1)** — 1 ô "Thời điểm" **riêng cho từng khung** (6 ô, mỗi khung 1 ô). Điền giờ vào đây → chỉ khung đó chạy theo giờ vừa chọn.
- **Mục (2)** — 1 ô "Thời điểm" **chung cho toàn màn hình** (đặt góc trên bên phải, ngoài mọi khung — xem đúng vị trí trong bản mẫu). Điền giờ vào đây → **ghi đè** toàn bộ 6 ô (1), tất cả khung cùng chạy theo giờ này.
- Ký hiệu "(1)" / "(2)" trong file mẫu **chỉ là chú thích cho BA/dev hiểu, không hiển thị literal "(1)" "(2)" lên UI thật** — UI thật chỉ có nhãn "Thời điểm".

### 4.1 Định dạng nhập/hiển thị — "giờ chẵn"

- Cho phép **gõ tay** hoặc **chọn qua picker** (cả 2 cách đều phải hoạt động, không bắt buộc 1 trong 2).
- Định dạng hiển thị/nhập theo kiểu Việt Nam rút gọn, **không dùng dấu hai chấm**:
  - Đúng giờ, 0 phút → `7h`, `19h` (không hiển thị `7h00`, `19h00`).
  - Có phút khác 0 → `19h10` (giờ + "h" + phút 2 chữ số, không có khoảng trắng).
- Ngày: cho nhập kèm ngày (dd/mm/yyyy) — mặc định là ngày hiện tại nếu người dùng chỉ nhập giờ.
- Validate: giờ 0–23, phút 0–59. Nếu API/CSDL chỉ có bản ghi tại các mốc đo cố định (ví dụ đo mỗi 10 phút) thì khi người dùng chọn giờ lẻ không trùng mốc đo, backend trả về **bản ghi gần nhất trước đó** kèm field `matched_time` khác `requested_time` để frontend có thể hiện chú thích nhỏ "gần nhất lúc..." — **cần Công ty xác nhận chu kỳ đo thực tế → OI-L3**.

### 4.2 Theo quyền hạn

| Quyền | Được xem tức thời | Được chỉnh mục (1)/(2) để xem quá khứ |
|---|---|---|
| Q3 (thấp nhất) | ✅ | ❌ — ẩn hẳn ô nhập giờ, hoặc hiện dạng disabled |
| Q2 trở lên | ✅ | ✅ |

- Phân quyền xử lý **ở tầng API** (nếu Q3 cố gửi `time=...` thì API vẫn trả 200 nhưng bỏ qua param, luôn trả tức thời + `is_realtime: true`, hoặc trả 403 tuỳ Công ty chọn — ghi vào OI-L1), không chỉ ẩn ở giao diện — đúng nguyên tắc đã chốt cho toàn MOD-03.

---

## 5. Hiển thị số to (yêu cầu UI cho trực ban/tivi)

- Số trong vòng tròn là nội dung quan trọng nhất khung hình → font-size phải đủ lớn để đọc từ xa vài mét (tham khảo: tối thiểu ~28–32px trên desktop, scale lớn hơn khi chiếu tivi độ phân giải cao — xem mục 6).
- Tên trạm/nhãn TL-HL/chainage là phụ, có thể nhỏ hơn số liệu 40–50%.
- Vòng tròn: dùng SVG, không dùng ảnh bitmap, để scale sắc nét ở mọi độ phân giải tivi.
- Vị trí Y (chiều cao) của vòng tròn trên trục dọc mỗi khung: co giãn theo tỉ lệ min–max của các giá trị **trong cùng khung đó** (không dùng chung 1 thang đo cho cả 6 khung, vì biên độ giá trị mỗi sông khác xa nhau — Sông Nhuệ 3,75–6,80m trong khi Sông Hồng 5,90–7,50m) — mục đích chỉ để giữ đúng "hình dáng mặt cắt" trực quan như bản mẫu, không phải biểu đồ đo chính xác theo tỷ lệ tuyệt đối.
- Trạng thái mất kết nối/dữ liệu cũ quá hạn: đổi màu số liệu (ví dụ xám hoặc viền cam) — áp dụng chung với L6 (cảnh báo khi màn hình ngừng cập nhật quá ngưỡng), **ngưỡng thời gian cụ thể để coi là "cũ" chưa chốt → OI-L4**.

---

## 6. Chế độ trực ban / tivi 24/24 (áp dụng lại L1–L6 đã chốt)

Không lặp lại chi tiết đã có trong `mod03_quyet_dinh_ky_thuat.md`, chỉ nhắc các điểm bắt buộc khi code màn hình này:

- **L1**: route/trang này khi vào bằng tài khoản thiết bị (kiosk) chỉ hiển thị đúng 6 khung + nút fullscreen, không có menu điều hướng sang khu quản trị khác.
- **L2**: nếu request có access token hết hạn khi đang mở màn hình, phải tự làm mới token ngầm (silent refresh), không redirect về `/login`.
- **L4**: layout ẩn toàn bộ menu/header phụ khi ở chế độ trình chiếu; có đồng hồ hệ thống + dòng "Cập nhật lúc HH:mm" (giờ đồng bộ dữ liệu gần nhất, khác với "Thời điểm" người dùng chọn ở mục 4).
- **L5**: tự động gọi lại `/api/hydro/diagram` theo chu kỳ polling (đề xuất 60s cho chế độ tức thời; không cần polling khi đang xem 1 mốc quá khứ cố định) — **chu kỳ chính xác cần Công ty chốt → OI-L5**. Có cơ chế tự reload trang định kỳ (ví dụ mỗi 6–12 giờ) để tránh treo bộ nhớ trình duyệt khi chạy liên tục nhiều ngày.
- **L6**: nếu 2 lần polling liên tiếp đều lỗi/timeout, hiện banner cảnh báo nhỏ góc màn hình, không được để trắng màn hình.
- **Nút "Xem toàn màn hình"**: dùng Fullscreen API chuẩn trình duyệt (`requestFullscreen`), đặt cố định 1 vị trí (góc trên phải, cạnh ô "Thời điểm (2)") theo đúng mẫu.

### 6.1 Responsive

- Bố cục 3 hàng ở mục 3 là bố cục cho **màn hình lớn/tivi**. Trên màn hình hẹp (tablet dọc, điện thoại), cho phép **xếp dọc 6 khung theo đúng thứ tự** (Sông Nhuệ → Sông Hồng → Sông Đáy → Sông La Khê → Sông Vân Đình → Sông Duy Tiên), mỗi khung full width, không bắt buộc giữ 2–3 khung/hàng.

---

## 7. Vấn đề cần Công ty/dev xác nhận (Open Issues)

| Mã | Nội dung | Ảnh hưởng nếu không chốt |
|---|---|---|
| OI-L1 | Route Q3 (xem tức thời) có tách riêng khỏi route Q2+ (có ô chọn giờ) hay dùng chung 1 route rồi ẩn/hiện theo quyền? | Ảnh hưởng kiến trúc route/API auth |
| ~~OI-L2~~ | ✅ **Đã chốt** — công thức nội suy tuyến tính theo Km, chi tiết + hằng số tại `spec-cong-thuc-noi-suy.md` | — |
| OI-L3 | Chu kỳ đo thực tế của nguồn BHH4.0 (bao nhiêu phút/lần) để biết cách xử lý khi người dùng chọn giờ lẻ không trùng mốc đo | Ảnh hưởng UX ô nhập giờ và cách hiện "gần nhất lúc..." |
| OI-L4 | Ngưỡng thời gian để coi dữ liệu là "cũ/mất cập nhật" khi hiển thị trực ban | Ảnh hưởng cảnh báo L6 |
| OI-L5 | Chu kỳ polling khi xem tức thời (đề xuất 60s) | Ảnh hưởng tải server + độ trễ cập nhật trên tivi |
| OI-L6 | 2 node/vòng tròn không có nhãn TL/HL (Cống 6 cửa Hòa Bình, Cống xả tiêu TB Vĩnh Mộ 2) — giá trị đó là mực nước gì (thượng lưu, hạ lưu, hay 1 mực nước duy nhất của cống)? | Đặt sai tên field `VALUE` có thể gây hiểu nhầm khi tích hợp với bảng `wl_reading` đã có sẵn TL/HL trong SPEC MOD-03 |

---

## 8. Định nghĩa "Done" cho task nhóm G/L liên quan màn hình này

- [ ] Cả 6 khung vẽ đúng thứ tự node theo mục 3, không thiếu/thừa/sai vị trí điểm.
- [ ] Không có bất kỳ lệnh gọi trực tiếp tới `songnhue.bhh40.net` từ mã frontend.
- [ ] Toàn bộ giá trị hiển thị đã qua bước chia 100 (mét), không hiển thị số nguyên cm.
- [ ] Q3 không thể xem/sửa được mốc thời gian quá khứ kể cả khi sửa tay request.
- [ ] Định dạng giờ hiển thị đúng kiểu `7h` / `19h` / `19h10`, không có `19h00`.
- [ ] Chạy thử liên tục ở chế độ kiosk fullscreen tối thiểu 72 giờ không bị rơi về màn hình đăng nhập (dùng chung tiêu chí K8 đã có).
