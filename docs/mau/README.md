# Tệp mẫu gửi Công ty

⛔⛔ **Thư mục này chỉ giữ những tệp mẫu mà hệ thống CHƯA sinh ra được.**

Mọi đường nhập đã dựng đều có **nút “Tải tệp mẫu”** ngay trên màn hình, và tệp ấy được **sinh từ
chính danh mục cột mà bộ đọc dùng** (`CotMau` + `BieuMauCsv` ở `core/common/importer`). Đó là cách
duy nhất giữ tệp mẫu ⛔ **không lệch** khỏi bộ đọc vào ngày ai đó thêm một cột — luật 14.

| Đường nhập | Tệp mẫu lấy ở đâu |
|---|---|
| Danh mục công trình | Vận hành công trình → Danh mục công trình → **Nhập từ tệp** → *Tải tệp mẫu* |
| Vị trí điểm đo (tuyến sông · lý trình · toạ độ) | Dữ liệu thuỷ văn → Danh mục điểm đo → **Nhập vị trí từ tệp** → *Tải tệp mẫu* |
| **Danh sách CBNV** | Nhân sự → Hồ sơ CBNV → **Nhập từ tệp** → *Tải tệp mẫu* (từ 20/09/2026 — `T68.23`) |

---

## ✅ `mau-danh-sach-cbnv.csv` tĩnh đã được XOÁ — 20/09/2026 (`T68.23`)

Thư mục này từng giữ một tệp mẫu **tĩnh** cho danh sách CBNV, vì `backend/hr` chưa có bộ nhập nào để
sinh tệp mẫu từ chính danh mục cột. Cái giá của nó đã hiện ra đúng như dự đoán: tệp tĩnh **lệch lược
đồ** — `loai_hop_dong` có *Thời vụ* mà ràng buộc CSDL ⛔ nhận, `trinh_do` có *Sau đại học* trong khi hệ
tách Tiến sĩ / Thạc sĩ. Nay bộ nhập CBNV đã dựng, tệp mẫu **do backend sinh** từ `COT_MAU`, và tệp tĩnh
đã bị xoá: hai nguồn sự thật thì sẽ lệch, và bản tĩnh ⛔ có gì canh (luật 14).

⭐ Tệp mẫu mới có **16 cột** (thêm mã chức vụ, ngày ký hợp đồng, trạng thái, ngày nghỉ việc) và đọc được
cả ô ngày của Excel — người điền ⛔ phải nhớ gõ ngày dưới dạng văn bản.

### 🔒 Trường nhạy cảm CỐ Ý không có trong tệp

**CCCD · số BHXH · số tài khoản ngân hàng · lương** ⛔ **không** nằm trong mẫu này, và đó là chủ ý.

Chúng thuộc bảng riêng `employee_sensitive`, mã hoá **AES-256-GCM** với khoá đặt **ngoài** cơ sở dữ
liệu (Nghị định 13/2023/NĐ-CP, quy tắc 10 của dự án). Gộp chúng vào một tệp CSV đi qua thư điện tử
là đưa chúng ra khỏi mọi lớp bảo vệ ấy **trước khi** chúng kịp vào hệ thống.

⇒ Xin Công ty gửi phần này **sau, riêng, và qua kênh đã thống nhất**.

### Ghi chú định dạng

- Dấu tách là **`;`** (khớp dấu tách danh sách của Excel bản tiếng Việt) và tệp có **BOM UTF-8** —
  mở thẳng bằng Excel là đúng dấu tiếng Việt, ⛔ không cần thao tác nào.
- **Dòng 2 là MÔ TẢ quy cách**, ⛔ không phải một bản ghi ví dụ. Xoá dòng ấy trước khi điền.
  (Mẫu gồm dòng ví dụ *hợp lệ* thì tới ngày nhập, nó sẽ im lặng tạo ra đúng bản ghi ví dụ ấy.)
- Cột `ma_don_vi` phải khớp danh mục đơn vị trong hệ thống — phụ thuộc **OI-05** (7 hay 8 Xí nghiệp; từ 19/09 thêm danh sách thứ ba của Báo cáo nhanh — `master-tracking.md` T66.14).
