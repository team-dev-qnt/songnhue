# Đề nghị xác nhận — Báo cáo nhanh

**Ngày lập**: 18/09/2026 · **Gửi**: Công ty TNHH MTV ĐTPT Thủy lợi Sông Nhuệ · **Đơn vị lập**: nhóm phát triển

> ✅ **Công ty đã trả lời ngày 19/09/2026** — bản có trả lời: `docs_origin/bao-cao/spec-bao-cao-nhanh/xacnhan.md`;
> kết quả áp vào hệ thống ghi ở phụ lục của `spec-bao-cao-nhanh.md` cùng thư mục.

Chức năng **Báo cáo nhanh** đã dựng xong: nhập số liệu theo kỳ, chốt kỳ, xuất thẳng ra mẫu Word của Công ty.
Với mỗi điểm chưa rõ dưới đây, hệ thống **đang chạy theo phương án đề xuất** (cột *Đang làm*).
Công ty chỉ cần đánh dấu **Đồng ý** hoặc ghi phương án khác vào cột *Trả lời*.

---

## A. Số liệu cần Công ty cung cấp hoặc nhập

| # | Việc | Vì sao cần |
|---|---|---|
| A1 | **Nhập danh sách Xí nghiệp** trên màn hình *Đơn vị*, rồi **nhập 178 trạm bơm** theo tệp mẫu của hệ thống | Hiện hệ thống chưa có trạm bơm nào, nên Bảng 2 đang trống |
| A2 | **Nhập nhóm máy bơm** (số máy, Q một máy) bằng nút *Nhập từ tệp* ở màn hình *Danh mục máy bơm* | Bảng 1 và Bảng 2 tính từ danh mục này |
| A3 | **Gắn công trình cho 7 cống + trạm Yên Nghĩa** ở màn hình *Cấu hình Báo cáo nhanh* | Bảng 3 và ghi chú Yên Nghĩa lấy số theo công trình được gắn |

---

## B. Nội dung cần xác nhận

| Mã | Câu hỏi | Đang làm | Trả lời |
|---|---|---|---|
| **OI-BC9** | Tệp *Danh mục TB Cty SN 2026.xlsx* có 3 sheet với số liệu khác nhau (212/1.043 máy · 211/1.025 máy · 147/830 máy). Ví dụ: trạm Ngoại Độ ở sheet này có 11 máy, sheet kia có 20 máy; Ngọ Xá có 24 máy và 34 máy. **Sheet nào là danh mục chính thức?** | Dùng sheet **`TB Tiêu (KH)`** (178 trạm, 830 máy) vì sheet này khớp với Bảng 2 của mẫu. Các dòng tổng viết tay trong tệp không dùng, hệ thống tự cộng | ☐ Đồng ý ☐ Khác: |
| **OI-BC10** | Tên và số lượng Xí nghiệp: sheet `Trạm bơm` có 7 Xí nghiệp (Thường Tín, Bắc Từ Liêm, Nam Từ Liêm, Hoài Đức…), sheet `TB Tiêu (KH)` có 6 (Hồng Vân, Từ Liêm, Hà Đông…). **Bảng 2 in theo cách chia nào?** | Chờ Công ty — hệ thống in theo Xí nghiệp mà Công ty nhập ở A1 | Danh sách: |
| **OI-BC8** | 9 cột cỡ máy của Bảng 1 chưa có biên rõ ràng: 35/830 máy không rơi vào cột nào (Q = 25.200 · 7.300 · 1.950 m³/h). **Đề xuất biên dưới đây có đúng không?** | `43`: từ 32.500 · `22`: 17.000–32.500 · `12`: 10.000–17.000 · `8`: 6.000–10.000 · `4`: 3.500–6.000 · `2÷3`: 2.000–3.500 · `1,1÷1,9`: 1.100–2.000 · `1`: 1.000–1.100 · `<1`: dưới 1.000 (m³/h; Q đúng bằng biên thì xếp vào cột trên). Công ty có thể tự sửa biên trên giao diện | ☐ Đồng ý ☐ Khác: |
| **OI-BC1** | Mẫu là bảng chung của 4 công ty thủy lợi. **Công ty Sông Nhuệ xuất cả bảng hay chỉ phần của mình?** | Xuất **cả bảng**: điền phần Sông Nhuệ, để trống phần của 3 công ty kia (không ghi số 0) | ☐ Đồng ý ☐ Khác: |
| **OI-BC2** | **Ai nhập diện tích ngập úng theo xã (Bảng 5) và lượng mưa (Bảng 4)?** Nhập mỗi kỳ hay chỉ khi có ngập? | Người có quyền lập báo cáo nhập trong từng kỳ. Ô chưa nhập thì để trống | ☐ Đồng ý ☐ Khác: |
| **OI-BC7** | **Bản Word gửi UBND có liệt kê đủ mọi trạm (kể cả trạm không chạy máy nào) không?** | Liệt kê **đủ toàn bộ** danh mục. Nút *"Chỉ hiện trạm đang hoạt động"* chỉ lọc trên màn hình nhập | ☐ Đồng ý ☐ Khác: |
| **OI-BC6** | Nút *"Chỉ hiện trạm đang hoạt động"* mặc định **bật hay tắt**? | **Tắt**, để hiện đủ danh mục khi rà soát | ☐ Tắt ☐ Bật |
| **OI-BC5** | Ghi chú Yên Nghĩa dùng đơn vị **m³/s**, trong khi các bảng khác dùng m³/h. **Có đúng ý Công ty không?** | Giữ **m³/s** như mẫu (hệ thống tự đổi từ m³/h) | ☐ Đồng ý ☐ Đổi sang m³/h |
| **OI-BC12** | Ô *"Tổng lưu lượng"* ở Mục 1 để trống trong mẫu; tệp *Yêu cầu* ghi hai số khác nhau (650.000 ở phần thân, 1.0950.000 ở Bảng 1). **Ô này lấy từ đâu?** | Chép nguyên số của dòng Sông Nhuệ trong Bảng 1, giống 2 ô *Tổng số trạm* và *Tổng số máy* | ☐ Đồng ý ☐ Khác: |
| **OI-BC13** | Cùng một cột, phần thân Mục 3 ghi *"Rau, màu, thuỷ sản"*, còn Bảng 5 ghi *"Rau, màu"*. **Dùng nhãn nào?** | Giữ nguyên chữ của mẫu ở cả hai chỗ | ☐ Rau, màu, thuỷ sản ☐ Rau, màu |
| **OI-BC11** | Điểm đo **F01771** (Liên Mạc thượng lưu): hệ thống ghi *Sông Nhuệ, K0+390*, còn mẫu ghi *"TL (hồng)", H-K53+450*. **Điểm này thuộc sông Hồng phải không?** | Chưa sửa, chờ Công ty | ☐ Sông Hồng ☐ Sông Nhuệ |
| **OI-BC14** | Bảng 3 có 3/14 ô **chưa có điểm đo**: Hà Đông (hạ lưu), Hòa Mỹ (thượng lưu), Lương Cổ (hạ lưu). **Các vị trí này có điểm đo không?** | Để trống kèm lý do. Khi có điểm đo, Công ty liên kết điểm đo với cống là bảng tự có số | Mã điểm đo (nếu có): |
| **OI-BC15** | Hệ thống chưa có nguồn **lượng mưa tự động**. | Bảng 4 **nhập tay** theo từng kỳ. Khi có nguồn tự động thì chuyển sang lấy tự động | ☐ Đồng ý ☐ Khác: |
| **OI-BC16** | Mẫu tô đỏ/nền vàng ở các ô tự điền; mực nước Bảng 3 có tiêu đề *(m)* nhưng số minh hoạ ghi `200`. | Bản xuất bỏ tô màu ở ô đã điền. Mực nước in theo **mét, 2 chữ số thập phân** (ví dụ `4,52`) | ☐ Đồng ý ☐ Khác: |
| **OI-BC17** | Cột *Lúa* của nhóm *Tổng cộng* ở Bảng 5 quá hẹp, số có 3 chữ số bị xuống dòng. **Công ty có cho nới cột không?** | Giữ nguyên bố cục mẫu, chưa tự sửa | ☐ Cho nới ☐ Giữ nguyên |

---

## C. Đã chốt, chỉ để Công ty biết

- **F01519 (Lương Cổ)** đã đổi về **thượng lưu** theo mẫu Báo cáo nhanh. Trước đó bản chụp ngày 09/09 ghi là hạ lưu. Trên cổng thông tin, số của Lương Cổ nay hiện ở cột *Thượng lưu*.
- **Kỳ báo cáo đã chốt thì không sửa được.** Muốn mở lại cần quyền riêng và phải ghi lý do; hệ thống lưu lại ai đã mở và vì sao.

---

**Công ty vui lòng gửi lại bản này sau khi đã điền cột *Trả lời*.** Mục nào đánh dấu *Đồng ý* thì không cần làm thêm, vì hệ thống đang chạy đúng như vậy.
