# SPEC: Công thức nội suy mực nước tại 2 điểm "cửa sông"

- Phiên bản: 1.0 — 16/09/2026
- Nguồn: `Hướng dẫn cách tính toán nội suy.docx` (Công ty cung cấp)
- Đóng **OI-L2** trong `spec-bieu-do-muc-nuoc.md` (công thức nội suy cho 2 node `cua_song_la_khe` và `cua_song_duy_tien` ở mục 3.7 file đó).

---

## 1. Nguyên tắc chung

Nội suy tuyến tính (linear interpolation) theo **khoảng cách lý trình (chainage/Km)** dọc sông Nhuệ, giữa 2 trạm đo thật liền kề bao quanh vị trí cửa sông nhánh cần tính:

```
X = X1 + (X2 − X1) / (K2 − K1) × (Ktarget − K1)
```

Trong đó:
- `X1`, `X2`: mực nước tại 2 trạm đo thật (lấy từ API mực nước — dữ liệu **đã có sẵn**, không cần gọi thêm API riêng).
- `K1`, `K2`: lý trình (Km) của 2 trạm đo thật đó.
- `Ktarget`: lý trình (Km) của điểm cửa sông cần nội suy.
- Tất cả `K1`, `K2`, `Ktarget` là **số hằng (constant) cố định theo công thức**, hard-code trong code tính toán — **không** lấy từ API, không đổi theo thời gian.
- `X1`, `X2` dùng **đơn vị nào cũng cho ra tỉ lệ đúng** (vì là phép nội suy tuyến tính tỉ lệ) — khuyến nghị tính bằng **mét** (sau khi đã chia 100 theo quy tắc chung MOD-03) để đồng nhất với các giá trị khác hiển thị trên UI, tránh phải đổi đơn vị ngược lại.

> Theo đúng yêu cầu: **"Hai chỉ số này chỉ dùng cho phần này thôi"** — nghĩa là 4 hằng số Km (`1085`, `18100`, `43750`, `63405`) và 2 hằng số Km đích (`15470`, `57420`) **chỉ phục vụ riêng phép tính nội suy này**, không tái sử dụng cho mục đích khác trong hệ thống (không dùng làm chainage hiển thị chung, không dùng trong logic khác) — nên đặt thành **hằng số riêng, đặt tên rõ ràng, trong module tính nội suy**, không gộp chung với bảng `hydro_station` dùng cho các mục đích khác.

---

## 2. Công thức 1 — Cửa sông La Khê

```
X_la_khe = X1 + (X2 − X1) / (18100 − 1085) × (15470 − 1085)
```

| Ký hiệu | Ý nghĩa | Nguồn giá trị | Ánh xạ tới node đã định nghĩa (`spec-bieu-do-muc-nuoc.md`) |
|---|---|---|---|
| `X1` | Mực nước hạ lưu cống điều tiết hạ lưu Liên Mạc (cống Liên Mạc 2), K1+085 | API mực nước (đã sync sẵn) | node `dthl_lien_mac` (ĐTHL LIÊN MẠC) → field **HL** |
| `X2` | Mực nước thượng lưu cống Hà Đông, K18+100 | API mực nước (đã sync sẵn) | node `ha_dong` (HÀ ĐÔNG) → field **TL** |
| `K1` | 1085 | hằng số cố định | — |
| `K2` | 18100 | hằng số cố định | — |
| `Ktarget` | 15470 | hằng số cố định | vị trí node `cua_song_la_khe` (Cửa sông La Khê, K15+470) |
| `X_la_khe` | Kết quả nội suy | tính toán | ghi vào giá trị hiển thị của node `cua_song_la_khe` |

---

## 3. Công thức 2 — Cửa sông Duy Tiên

```
X_duy_tien = X1 + (X2 − X1) / (63405 − 43750) × (57420 − 43750)
```

| Ký hiệu | Ý nghĩa | Nguồn giá trị | Ánh xạ tới node đã định nghĩa (`spec-bieu-do-muc-nuoc.md`) |
|---|---|---|---|
| `X1` | Mực nước hạ lưu cống Đồng Quan, K43+750 | API mực nước (đã sync sẵn) | node `dong_quan` (ĐỒNG QUAN) → field **HL** |
| `X2` | Mực nước thượng lưu cống Nhật Tựu, K63+405 | API mực nước (đã sync sẵn) | node `nhat_tuu` (NHẬT TỰU) → field **TL** |
| `K1` | 43750 | hằng số cố định | — |
| `K2` | 63405 | hằng số cố định | — |
| `Ktarget` | 57420 | hằng số cố định | vị trí node `cua_song_duy_tien` (Cửa sông Duy Tiên, K57+420) |
| `X_duy_tien` | Kết quả nội suy | tính toán | ghi vào giá trị hiển thị của node `cua_song_duy_tien` |

---

## 4. Ghi chú triển khai

- Cả `X1` và `X2` của mỗi công thức đều là các giá trị **đã tồn tại sẵn** trong hệ thống (đều là node đã có trong bảng mục 3 của `spec-bieu-do-muc-nuoc.md`) — **không phát sinh trạm đo mới, không gọi thêm API mới**, chỉ cần đọc lại đúng 2 giá trị đã sync.
- Thời điểm tính nội suy: dùng **cùng mốc thời gian** (`requested_time`/tức thời) đang render cho toàn bộ 6 khung — tức khi người dùng đổi "Thời điểm" (mục (1)/(2) trong `spec-bieu-do-muc-nuoc.md`), `X1`, `X2` phải lấy đúng tại mốc đó rồi mới tính `X`, không dùng giá trị tức thời để tính cho một mốc quá khứ.
- **Thiếu dữ liệu**: nếu `X1` hoặc `X2` tại mốc thời gian yêu cầu bị null/thiếu (do lỗi đồng bộ) → không tính được `X` → áp dụng fallback chung của MOD-03 (mục 8 SPEC MOD-03: không hiển thị số giả) cho node nội suy đó, không được tính nội suy với 1 vế null hoặc gán 0.
- Đặt tên hằng số trong code gợi ý (ví dụ):
  ```
  INTERP_LA_KHE = { k1: 1085, k2: 18100, k_target: 15470,
                     x1_node: "dthl_lien_mac", x1_field: "HL",
                     x2_node: "ha_dong",       x2_field: "TL" }

  INTERP_DUY_TIEN = { k1: 43750, k2: 63405, k_target: 57420,
                       x1_node: "dong_quan", x1_field: "HL",
                       x2_node: "nhat_tuu",  x2_field: "TL" }
  ```
- Field `interpolated: true` đã có sẵn trong response mẫu ở mục 2 (`spec-bieu-do-muc-nuoc.md`) — giữ nguyên, dùng để frontend biết đây là giá trị tính toán chứ không phải đo trực tiếp.

---

## 5. Định nghĩa "Done"

- [ ] `X_la_khe` và `X_duy_tien` tính đúng công thức ở mục 2, 3 — kiểm bằng cách thay số tay và so khớp kết quả code.
- [ ] 4 hằng số Km trạm + 2 hằng số Km đích không bị lấy nhầm từ API hay từ bảng `hydro_station` dùng chung.
- [ ] Khi `X1` hoặc `X2` null tại một mốc thời gian, node nội suy hiển thị đúng trạng thái "thiếu dữ liệu", không hiển thị số sai/số 0.
- [ ] OI-L2 trong `spec-bieu-do-muc-nuoc.md` được cập nhật trạng thái "đã chốt", trỏ sang file này.
