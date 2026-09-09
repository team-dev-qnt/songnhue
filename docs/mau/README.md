# Tệp mẫu gửi Công ty

⛔⛔ **Thư mục này chỉ giữ những tệp mẫu mà hệ thống CHƯA sinh ra được.**

Mọi đường nhập đã dựng đều có **nút “Tải tệp mẫu”** ngay trên màn hình, và tệp ấy được **sinh từ
chính danh mục cột mà bộ đọc dùng** (`CotMau` + `BieuMauCsv` ở `core/common/importer`). Đó là cách
duy nhất giữ tệp mẫu ⛔ **không lệch** khỏi bộ đọc vào ngày ai đó thêm một cột — luật 14.

| Đường nhập | Tệp mẫu lấy ở đâu |
|---|---|
| Danh mục công trình | Vận hành công trình → Danh mục công trình → **Nhập từ tệp** → *Tải tệp mẫu* |
| Vị trí điểm đo (tuyến sông · lý trình · toạ độ) | Dữ liệu thuỷ văn → Danh mục điểm đo → **Nhập vị trí từ tệp** → *Tải tệp mẫu* |
| **Danh sách CBNV** | `mau-danh-sach-cbnv.csv` — **tệp tĩnh, xem cảnh báo dưới** |

---

## ⚠ `mau-danh-sach-cbnv.csv` — tệp tĩnh, và nó CÓ HẠN DÙNG

Phân hệ Nhân sự (MOD-04) **chưa dựng**: `backend/hr/` hôm nay có 6 tệp và cả 6 là `package-info.java`.
⇒ ⛔ Chưa có bộ đọc nào để sinh tệp mẫu từ đó.

Tệp này tồn tại vì **Công ty cần bắt đầu điền từ bây giờ** — thu thập danh sách CBNV của cả Công ty
mất nhiều tuần, và chờ tới khi MOD-04 dựng xong mới hỏi là mất trắng khoảng thời gian ấy (mục
`G6-a`).

⛔⛔ **Việc bắt buộc khi dựng MOD-04**: khai `COT_MAU` cho bộ nhập CBNV, thêm nút *Tải tệp mẫu*, rồi
**XOÁ tệp này**. Một tệp mẫu tĩnh nằm cạnh một bộ đọc là hai nguồn sự thật, và chúng sẽ lệch nhau ở
đúng ngày ai đó thêm cột — bản tĩnh thì ⛔ không có gì canh.

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
- Cột `ma_don_vi` phải khớp danh mục đơn vị trong hệ thống — phụ thuộc **OI-05** (7 hay 8 Xí nghiệp).
