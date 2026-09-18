# Đề nghị Công ty cung cấp — G6 · G8 · G10

**Ngày lập**: 09/09/2026 · **Đơn vị lập**: nhóm phát triển
**Gửi**: Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ

> Tài liệu này gộp **ba** đề nghị đang mở thành **một** đợt gửi, vì cả ba đều là *tệp mẫu và danh
> mục* — Công ty trả lời một lần là đủ.
>
> ⭐ **Phần việc của chúng tôi đã làm xong trước khi gửi thư này.** Mỗi mục dưới đây ghi rõ thứ hệ
> thống **đã sẵn sàng nhận**, để dữ liệu Công ty gửi sang là **nhập được ngay**, không phải chờ thêm
> một đợt lập trình nữa.

---

## Tóm tắt — 6 việc, xếp theo mức chặn

| # | Việc | Mục | Chặn cái gì | Mức |
|---|---|---|---|---|
| 1 | **Toạ độ GPS** của 19 điểm đo | G8 | **Bản đồ GIS đang trống hoàn toàn** — ⭐ nay chỉ cần upload | ⛔⛔ |
| 2 | **Danh mục công trình** (Excel, theo mẫu đính kèm) | G8 | Bản đồ · biểu tổng hợp · hồ sơ công trình | ⛔⛔ |
| 3 | **Chốt 7 hay 8 Xí nghiệp** + mã từng đơn vị | OI-05 | Phân quyền theo đơn vị · cột đơn vị của mọi danh mục | ⛔⛔ |
| 4 | **Danh sách CBNV** (Excel) | G6-a | Toàn bộ phân hệ Nhân sự | ⛔ |
| 5 | **File mẫu báo cáo** (BC-11, BC-05, BCNS-07) | G10 · G6 | Bố cục bản in — ⛔ *không* chặn số liệu | ⚠ |
| 6 | **Xác nhận 9 mã API lạ** ngoài danh mục 19 điểm đo | G8 | Chúng đang bị ghi vào cột *"Mã lạ"* | ⚠ |

---

## 1. G8 — 19 điểm đo: **đã nhận được một nửa, còn thiếu toạ độ**

✅ **Đã nhận và đã đưa vào hệ thống ngày 09/09/2026** (bảng đối chiếu Công ty cấp):

- **Tuyến sông**: 13/19 điểm đo
- **Lý trình**: 10/19 điểm đo

⬜ **6 điểm đo Công ty ghi "Chưa rõ" ở cả hai cột** — chúng tôi để trống, ⛔ không suy đoán:

| Mã API | Điểm đo | Vai trò |
|---|---|---|
| `F01732` | TB Hồng Vân | MN sông |
| `F01559` | TV Hà Nội | MN sông |
| `F01812` | An Cảnh | MN sông |
| `F01652` | Cống tiêu tự chảy Yên Nghĩa | Hạ lưu |
| `F01820` | Cống tiêu tự chảy Yên Nghĩa | Thượng lưu |
| `F01965` | Liên Mạc 2 | Hạ lưu |

### ⚠ Hai điểm cần Công ty xác nhận lại

**a) `F01519` Lương Cổ — hai bản của Công ty ghi khác nhau**

Bảng ánh xạ nhận trước đây ghi **Thượng lưu**; bảng đối chiếu ngày 09/09 ghi **Hạ lưu**. 18/19 dòng
còn lại khớp tuyệt đối giữa hai bản, nên đây là **một dòng sai ở một trong hai bản**, không phải hai
danh sách khác nhau.

⇒ Chúng tôi **đang lấy theo bản mới (Hạ lưu)**, vì bản ấy có thêm tuyến sông và lý trình, và nội bộ
nhất quán (Lương Cổ chỉ xuất hiện một lần, ở K72+506 — lý trình lớn nhất trên sông Nhuệ trong bảng,
tức cuối tuyến). **Đề nghị Công ty xác nhận.** Nếu sai, cột *"Mực nước thượng lưu"* và *"Mực nước hạ
lưu"* của biểu tổng hợp sẽ hiển thị số của điểm đo này ở nhầm cột.

**b) `F01657` Vân Đình thượng lưu — lý trình ghi "(K72+000 – sông Đáy)"**

Ô tuyến sông ghi *"Sông Vân Đình"* nhưng lý trình lại chú *"sông Đáy"*, và `F01705` (Vân Đình hạ lưu,
tuyến sông Đáy) mang đúng lý trình `K72+000`. Chúng tôi hiểu **K72+000 là lý trình trên sông Đáy**,
⛔ không phải trên sông Vân Đình, nên **để trống lý trình** của `F01657`.

Lý do không điền đại: sông Vân Đình dài khoảng 10 km. Một điểm ghi ở 72.000 m trên tuyến ấy sẽ đứng
sai vị trí trong **mọi phép sắp xếp theo tuyến** — kể cả biểu BC-11. **Đề nghị Công ty cho lý trình
đúng trên sông Vân Đình**, hoặc xác nhận điểm này thuộc tuyến sông Đáy.

### ⛔⛔ c) Toạ độ GPS — chưa có dòng nào

Bảng đối chiếu ngày 09/09 **không có cột toạ độ**. Hệ quả đo được hôm nay: **lớp bản đồ điểm đo
trống hoàn toàn** — 19/19 điểm không hiển thị được trên bản đồ điều hành.

**Cần**: vĩ độ / kinh độ hệ **WGS-84** (dạng thập phân, ví dụ `20.945123`, `105.782456`) cho từng mã
API. Lấy bằng điện thoại tại hiện trường là đủ chính xác cho mục đích hiển thị.

⛔ Chúng tôi **⛔ không suy toạ độ từ tên điểm đo**. Một chấm sai vài trăm mét trên bản đồ điều hành
sẽ không ai phát hiện bằng mắt, và nó tệ hơn hẳn một bản đồ trống — bản đồ trống thì còn nằm trong
danh sách việc cần làm.

> ⭐ **Gửi cách nào cũng được — hệ thống đã sẵn sàng nhận.**
> Trên phần mềm: **Dữ liệu thuỷ văn → Danh mục điểm đo → “Nhập vị trí từ tệp” → “Tải tệp mẫu”**.
> Tệp mẫu có sẵn 5 cột `ma_api` · `tuyen_song` · `ly_trinh` · `vi_do` · `kinh_do`; điền tới đâu hay
> tới đó — **ô để trống nghĩa là giữ nguyên giá trị hiện có, không phải xoá**. Hệ thống chạy khô
> trước và liệt kê từng dòng sai kèm số dòng đúng như trong Excel.
> Nếu Công ty gửi bảng ở dạng khác (Word, ảnh chụp, email), chúng tôi nhập hộ — xin cứ gửi.

---

## 2. G8 — danh mục công trình: **hệ thống đã sẵn sàng nhận, kèm tệp mẫu**

Hôm nay bảng công trình **có 0 hồ sơ**. Đây là thứ chặn nhiều nhất: bản đồ, biểu tổng hợp theo tuyến,
hồ sơ – tài liệu công trình, lịch sử bảo trì, và cả việc liên kết điểm đo với công trình đều đứng
trên danh mục này.

### ⭐ Cách gửi — đã có tệp mẫu do hệ thống sinh ra

Trên phần mềm: **Vận hành công trình → Danh mục công trình → Nhập từ tệp → “Tải tệp mẫu (.csv)”**.

Tệp mẫu gồm **dòng tiêu đề + một dòng mô tả quy cách từng ô**. Xoá dòng mô tả, điền dữ liệu vào từ
dòng 2 rồi tải lên. Hệ thống **chạy khô trước**: nó liệt kê từng dòng sai kèm số dòng đúng như trong
Excel, và **⛔ không ghi gì cho tới khi hết lỗi**.

**Bốn cột bắt buộc**: `ma_cong_trinh` · `ten_cong_trinh` · `loai_cong_trinh` · `ma_don_vi`.
15 cột còn lại (toạ độ, tuyến sông, lý trình, năm xây dựng, tổng vốn…) điền được tới đâu hay tới đó.

⚠ Riêng cột **`ma_cum`** (nhóm công trình) chỉ nhận mã đã khai trong danh mục cụm — khai tại
**Vận hành công trình → Cụm công trình**. Nếu Công ty chưa phân cụm thì **cứ bỏ trống**, nhập bình
thường; phân cụm sau lúc nào cũng được.

**Hỗ trợ `.xlsx` và `.csv`.** ⚠ Định dạng `.xls` (Excel 2003 trở về trước) hệ thống **⛔ không đọc
được** — mở bằng Excel rồi *Lưu thành* `.xlsx` là được. Tối đa **5.000 dòng** mỗi tệp.

### ⛔⛔ Phụ thuộc bắt buộc: OI-05

Cột `ma_don_vi` phải là mã đơn vị **có thật trong hệ thống**. Hôm nay hệ thống mới có **đúng một đơn
vị**: `CTY` (Công ty). Danh mục công trình chia theo Xí nghiệp thì **⛔ không nhập được** cho tới khi
Công ty chốt **7 hay 8 Xí nghiệp** và mã của từng đơn vị.

> ⚠ Đây là mục `OI-05` đang mở từ 27/8: **Bố cục tổ chức ghi 7 Xí nghiệp, danh mục công trình ghi 8.**

---

## 3. G6 — Nhân sự: **thiếu một thứ chưa ai hỏi**

### a) File mẫu 2C-BNV *(mục G6 gốc)*

Báo cáo lý lịch cán bộ phải in **đúng mẫu 2C-BNV/2008 của Bộ Nội vụ**, ⛔ không tự chế được.
**Cần**: bản `.doc`/`.xls` **Công ty đang dùng** — các đơn vị thường có biến thể riêng.

⭐ **Xin nói rõ phạm vi để Công ty không phải chờ nhầm**: mục này chặn **đúng báo cáo BCNS-07**, tức
1 trong 8 báo cáo nhân sự, tức 1 trong 9 chức năng của phân hệ Nhân sự. Nó **⛔ không** chặn hồ sơ
CBNV, sơ đồ tổ chức, hợp đồng lao động hay nghỉ phép.

### b) ⭐ Danh sách CBNV — **đây mới là thứ chặn cả phân hệ**

**Cần** (Excel, một dòng một người):

> họ tên · ngày sinh · giới tính · **phòng ban / Xí nghiệp** · chức danh · ngày vào Công ty ·
> loại hợp đồng + ngày hết hạn · trình độ

> ⭐ **Đã có tệp mẫu gửi kèm**: `mau-danh-sach-cbnv.csv` — 12 cột, dòng 2 mô tả quy cách từng ô.
> Mở thẳng bằng Excel là đúng dấu tiếng Việt; xoá dòng mô tả rồi điền từ dòng 2.

🔒 **Xin gửi các trường nhạy cảm SAU và GỬI RIÊNG** — số CCCD, số BHXH, số tài khoản ngân hàng,
lương. Hệ thống lưu chúng ở một bảng riêng, **mã hoá AES-256-GCM với khoá đặt ngoài cơ sở dữ liệu**,
theo Nghị định 13/2023/NĐ-CP. ⛔ Đề nghị **không** đưa chúng vào cùng tệp danh sách chung và **không**
gửi qua thư điện tử thường — chúng cố ý **không** có trong tệp mẫu vì lý do đó.

⚠ Cột phòng ban cũng phụ thuộc **OI-05** như mục 2.

---

## 4. G10 — file mẫu báo cáo: **xin gửi kèm một bản in thử**

Thay vì đề nghị Công ty duyệt 22 mẫu báo cáo trên giấy, chúng tôi xin gửi kèm **một bản kết xuất
thật** để Công ty góp ý cụ thể trên đúng thứ mình sẽ nhận.

**Cần từ Công ty**: file mẫu **thật** (bản Word/Excel đang dùng) của:

| Mã | Tên | Vì sao cần bản thật |
|---|---|---|
| **BC-11** | Biểu tổng hợp mực nước theo tuyến sông | Bố cục hai tầng, gộp ô, mỗi công trình một cột tách TL–HL — ⛔ không đoán được từ mô tả |
| **BC-05** | Báo cáo thuỷ văn tháng | Xác nhận thứ tự cột và cách trình bày ô trống |
| **BCNS-07** | Lý lịch 2C-BNV | Như mục 3a |

### Hiện trạng bốn báo cáo thuỷ văn — nói thẳng để Công ty biết mình đang chờ gì

| Mã | Số liệu | Kết xuất | Ghi chú |
|---|---|---|---|
| **BC-05** Thuỷ văn tháng | ✅ | ✅ CSV | ⭐ Vừa bổ sung 5 cột đặc tả còn thiếu (09/09) |
| **BC-11** Biểu tổng hợp tuyến sông | ✅ | ✅ CSV | ⭐ Vừa có đường xuất (09/09); **bố cục bản in chờ file mẫu** |
| **BC-12** Chi tiết quan trắc | ✅ | ✅ CSV | Đầy đủ |
| **BC-13** Nhật ký đồng bộ | ✅ | ✅ CSV | ⭐ Vừa sửa: bản cũ xuất nhầm bảng (09/09) |

✅ **Mã số API thuỷ văn đã hoạt động từ 09/09** (xem mục 5), nên bốn báo cáo trên sẽ có số liệu
thật ngay khi hệ thống bắt đầu thu thập. Bản in thử gửi kèm thư này dựng trên dữ liệu của những
ngày đầu, nên số lượng dòng còn ít — **bố cục** mới là thứ xin Công ty góp ý.

⚠ Cột **"Lượng mưa"** của BC-05 và BC-11 sẽ **luôn trống**: hệ thống nguồn `bhh40.net` ⛔ không có
API lượng mưa (mục **G3-a**). Chúng tôi ghi thẳng *"Chưa có nguồn"* vào ô thay vì để trống hoặc ghi
`0` — ghi `0` là khẳng định *"trời không mưa"*, một câu chúng tôi không có căn cứ để nói.

---

## 5. ✅ Mã số API thuỷ văn — **đã nhận và đã kiểm chứng** (09/09/2026)

Công ty đã cấp mã số truy cập `songnhue.bhh40.net`. Chúng tôi đã gọi thử vào nguồn thật và xác nhận:

- ✅ Nguồn trả về **28 bản ghi mực nước**, đúng định dạng hệ thống đang chờ.
- ✅ **19/19 điểm đo** trong danh mục của hệ thống đều có mặt trong 28 mã ấy.
- ℹ️ 9 mã còn lại (`F01535`, `F01613`, `F01659`, `F01696`, `F01700`, `F01706`, `F01811`, `F01830`,
  `F01863`) **không nằm trong danh mục 19 điểm đo** Công ty xác nhận ở G8b. Hệ thống ghi nhận chúng
  vào cột *"Mã lạ"* của báo cáo BC-13 thay vì bỏ qua im lặng.
  ⬜ **Xin Công ty cho biết**: 9 mã này có thuộc phạm vi quản lý của Công ty không? Nếu có, xin bổ
  sung tên điểm đo và vai trò để đưa vào danh mục.

⚠ **Một lưu ý kỹ thuật quan trọng, xin ghi vào biên bản**: khi mã số sai hoặc hết hạn, nguồn
`bhh40.net` **vẫn trả mã thành công (HTTP 200)**, chỉ khác ở nội dung. Hệ thống của chúng tôi phân
biệt được hai trạng thái này và sẽ báo động khi mã số ngừng hoạt động — nhưng nếu Công ty **đổi mã
số** mà không báo, khoảng thời gian giữa hai lần là **mất dữ liệu vĩnh viễn**: nguồn ⛔ **không có
API tra cứu lịch sử**, hệ thống chỉ ghi được số liệu kể từ lúc kết nối.

⇒ **Đề nghị**: khi cần đổi mã số, báo trước để chúng tôi cập nhật trong cùng ngày.

---

## Đầu mối

Mọi câu hỏi về định dạng tệp, cách điền, hay cách gửi trường nhạy cảm — xin liên hệ nhóm phát triển.
Chúng tôi sẵn sàng nhận bản nháp chưa đầy đủ và báo lại chỗ nào cần bổ sung, thay vì để Công ty chuẩn
bị trọn vẹn rồi mới biết sai một cột.
