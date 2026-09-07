# Sổ nghiệm thu Cổng TTĐT — **Phiên bản 1**, chốt 28/08/2026

> **Đối chiếu với**: `docs_origin/nghiem_thu_phase1.md` — *"YÊU CẦU CHỈNH SỬA WEBSITE" v1.0*,
> 27/08/2026, trạng thái *Ban hành để thực hiện*.
>
> **Tài liệu này là một BẢN ĐỐI CHIẾU, không phải nguồn sự thật.** Công việc và nợ kỹ thuật có
> đúng một nguồn: `.claude/master-tracking.md` (`conventions.md` §6). Ở đây chỉ có thứ mà sổ kia
> không mang: ánh xạ **từng mã CR → trạng thái**, và câu trả lời cho §9.
>
> **Phạm vi thực hiện**: WS-24 (27/8) + WS-25 (28/8).

---

## 1. Con số

| | |
|---|---|
| Mã CR trong bảng §4 | **43** *(tài liệu ghi "44 mục"; **CR-13** vắng khỏi bảng mà vẫn được §5.2 trích dẫn — hiểu là khối "Mực nước, lượng mưa" trên trang chủ và làm theo §5.2)* |
| ✅ Đã đóng | **34** / 43 |
| ◐ Đóng một phần | **2** / 43 — CR-15, CR-20 |
| ⬜ Còn mở | **7** / 43 — CR-08, CR-14, CR-29, CR-33, CR-34, CR-38, CR-44 |
| *(ngoài bảng)* | **CR-13** ⬜ — mã §5.2 trích dẫn mà bảng §4 không có dòng. Bảng §2 dưới đây có **44** dòng: 43 mã của bảng + CR-13 |
| Đo được trên hệ đang chạy | 17/17 đường dẫn menu → HTTP 200 · 676 phép kiểm backend · 249 phép kiểm giao diện · 0 CVE ≥ 7 |

> ### ⚠ Một điều Công ty cần biết trước khi đọc bảng dưới
>
> **34 mã đã đóng từ 27/8, nhưng SÁU trong số đó tới 28/8 mới thật sự dùng được.**
>
> Đợt 27/8 dựng xong đường **đọc** — trang, bảng, API — và tick hoàn thành. Lượt rà 28/8 phát hiện
> đường **ghi** của sáu mục ấy không tồn tại: bảng danh bạ lãnh đạo không màn hình nào nhập được,
> ba cột địa chỉ/điện thoại/email của Xí nghiệp không biểu mẫu nào có ô, hai liên kết tài liệu của
> bảng công trình không ai gán được tệp. Các trang sẽ hiện đúng — và **rỗng vĩnh viễn**.
>
> Trạng thái ✅ trong bảng dưới đây là trạng thái **sau khi đã vá**, tức đã có đủ cả hai chiều.
> Chi tiết: `architecture-review.md` §10.62.

---

## 2. Bảng đối chiếu 43 mã CR

### A. Menu chính và điều hướng

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-01 | ✅ | Menu HEADER **8 mục cấp 1 + 12 mục cấp 2** dựng trong CSDL (`V202608271031`), bỏ "Thông báo". Sửa được ở màn hình Menu |
| CR-02 | ✅ | Submenu Giới thiệu: Tổng quan · Cơ cấu tổ chức · Lãnh đạo Công ty · Xí nghiệp trực thuộc |
| CR-03 | ✅ | Tách **Tin thủy lợi** / **Tin Công ty**. Danh mục cũ đổi tên **tại chỗ** nên bài viết giữ nguyên đường dẫn |
| CR-04 | ✅ | Mục cấp 1 "Hoạt động Đảng, đoàn thể" |
| CR-05 | ✅ | 4 mục con, **cả 4 đều có trang thật đứng sau** (không mục nào trả 404) |
| CR-06 | ✅ | "Văn bản điều hành" → **Công bố thông tin**, 2 nhánh + 8 mục con |
| CR-07 | ✅ | Không dựng module văn bản nội bộ. Nút mở tab mới sang `quanlyvanban.hanoi.gov.vn`; **địa chỉ là cấu hình**, trước đây ghi cứng ở 3 tệp |
| CR-08 | ⬜ | **Đợt 3.** Cố ý *chưa* dựng nút Đăng nhập — §2 cấm phân quyền bằng cách ẩn ở giao diện, và một nút dẫn tới hư không tệ hơn không có nút. Cơ chế đã chốt (xem §4) |
| CR-09 | ✅ | Chân trang đọc **cùng bảng menu** với đầu trang → hai nơi không lệch được. Gỡ cột 5 liên kết viết cứng |

### B. Bố cục trang chủ

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-10 | ✅ | Slider ảnh, **5 tham số đều từ cấu hình** (số ảnh, nhịp chuyển, tự chạy, mũi tên, chấm). ⚠ Khối rỗng cho tới khi tải ảnh lên |
| CR-11 | ✅ | Giữ nguyên 3 tin Hot |
| CR-12 | ✅ | "Dòng thời sự" → **Tin tức – Sự kiện**, kèm nhãn chuyên mục. Số bài cấu hình được |
| CR-13 | ⬜ | Khối dựng đủ khung §7; **chưa có nguồn số liệu** — xem OI-01 |
| CR-14 | ⬜ | **Đợt 3** cùng CR-08 |
| CR-15 | ◐ | Khối **mới hoàn toàn**, dựng đủ khung §7. Chưa có API nguồn — xem OI-02 |
| CR-16 | ✅ | Đã bỏ khối "Chỉ đạo điều hành" |
| CR-17 | ✅ | → **Công bố thông tin**, có mục tương ứng trên menu chính. Danh mục nguồn và số dòng cấu hình được |
| CR-18 | ✅ | 5 card **đọc thẳng cây menu** thay vì đọc danh mục — §2 đòi một hệ phân loại dùng chung, đọc chung nguồn thì "đồng bộ" không còn là việc phải nhớ |
| CR-19 | ✅ | Đấu nối `org_units`. ⭐ **28/8**: ba cột địa chỉ/điện thoại/email trước đó *hiển thị được mà không nhập được* — nay có ô nhập. Rỗng cho tới khi Công ty nhập (OI-05) |
| CR-20 | ◐ | Ảnh stock không liên quan **đã gỡ hết** (25/8). ⭐ **28/8**: video phóng sự nay dán mã ở màn hình cấu hình. ⬜ Thư viện **ảnh** chưa mở ra cổng — xem §5 |
| CR-21 | ✅ | ⭐ **28/8**: 4 liên kết cơ quan cấp trên chuyển từ mã nguồn vào **màn hình Menu** → Công ty tự sửa. "Sở Nông nghiệp và Môi trường" đã đổi; hai tên còn lại xem §4 |
| CR-22 | ✅ | Trang `/lien-he` + khung Google Map, đọc **cùng một khoá bản đồ** với chân trang |

### C. Các trang chức năng

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-23 | ✅ | `gioi-thieu-chung` → **Tổng quan**; gộp "Chức năng nhiệm vụ" vào |
| CR-24 | ✅ | Sơ đồ cây đọc `org_units`. ⭐ **28/8**: tên, tên viết tắt, loại đơn vị nay **sửa được sau khi tạo** — trước đó chỉ tạo mới được |
| CR-25 | ✅ | Bảng 3 cột. ⭐ **28/8**: bảng danh bạ trước đó **không màn hình nào nhập được**; nay có màn hình, kèm công tắc ẩn/hiện từng người |
| CR-26 | ✅ | Bảng 6 cột. ⭐ **28/8**: cột 2–4 và cột 5–6 đều đã có đường nhập |
| CR-27 | ✅ | Trang danh sách công trình theo Xí nghiệp (không còn là bài viết) |
| CR-28 | ✅ | Bảng đủ 7 cột. ⭐ **28/8**: cột "Quy trình vận hành" và "Phương án bảo vệ" nay **chọn được tệp** từ tài liệu đã tải lên — trước đó hai cột này không ai gán được gì |
| CR-29 | ⬜ | Chờ **tệp** PDF hai tỷ lệ từ Công ty + tầng đăng nhập + OI-07 |
| CR-30 | ✅ | Năm → Vụ, dựng bằng danh mục CMS. Bộ chọn đi qua tham số địa chỉ nên chạy cả khi tắt JavaScript |
| CR-31 | ✅ | 5 nhóm: Luật · Nghị định · Thông tư/Chỉ thị · Quyết định · Hướng dẫn |
| CR-32 | ✅ | 3 nhóm: Kế hoạch · Thông cáo, Báo cáo · Quyết định |

### D. Dữ liệu thời gian thực và API

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-33 | ⬜ | **Trả lời OI-01: chưa có API.** Đang để trạng thái chờ, **không có một số liệu gán cứng nào** — 5 trạm quan trắc bịa của bản cũ đã gỡ 25/8 |
| CR-34 | ⬜ | **Trả lời OI-02: chưa có API.** Như trên |
| CR-35 | ✅ | Mọi khối thời gian thực có dòng *"Cập nhật lúc HH:mm dd/MM/yyyy"*. ⚠ Mốc lấy từ **máy chủ**, không lấy giờ máy người xem |
| CR-36 | ✅ | "Dữ liệu tạm thời chưa khả dụng" + mốc gần nhất, **giữ nguyên bố cục**, không số liệu giả |
| CR-37 | ✅ | Chu kỳ tự làm mới **cấu hình được** (mặc định 5 phút — OI-09 chờ chốt) + nút làm mới tay |

### E. Phân quyền

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-38 | ⬜ | **Đợt 3.** Sẽ chặn ở **tầng route/API** đúng như §2 đòi, kèm phép kiểm gọi thẳng API khi chưa đăng nhập phải trả 401/403 |

### F. Nội dung cần sửa hoặc bỏ

| Mã | Trạng thái | Đo được / ghi chú |
|---|:---:|---|
| CR-39 | ✅ | Đã bỏ "Doanh nghiệp 100% vốn Nhà nước" |
| CR-40 | ✅ | Đã bỏ email khỏi chân trang. **Giữ dữ liệu** — vẫn hiện ở trang Liên hệ, chờ OI-04 |
| CR-41 | ✅ | Đã bỏ giờ làm việc khỏi chân trang (giữ dữ liệu, như trên) |
| CR-42 | ✅ | *"Tầng 4-5 Tòa nhà Newhouse – Phường Hà Đông – Thành phố Hà Nội"*, và **thôi ép chữ hoa** — giao diện không tự quyết định thay người nhập |
| CR-43 | ✅ | Liên hệ và Fax đọc từ cấu hình, không nằm trong mã nguồn |
| CR-44 | ⬜ | Ràng buộc **nhập liệu**, không phải mã. Cổng hiện nguyên văn địa điểm đã nhập — cố ý không "chuẩn hoá" hộ theo một bảng ánh xạ không ai duyệt |

---

## 3. Checklist §10 — 9 mục

| Mục | | Bằng chứng đo được |
|---|:---:|---|
| Menu, footer, card chuyên mục dùng **một** hệ phân loại | ✅ | Cả ba đọc chung bảng `menu_items`; phép kiểm đối chiếu **hai chiều** menu ↔ tuyến đường ở mỗi lượt CI |
| 43 mã CR được thực hiện hoặc có lý do cụ thể | ✅ | Bảng §2 ở trên |
| Nút Văn bản điều hành mở tab mới, không có danh sách văn bản nội bộ | ✅ | Đo trên hệ chạy: `target="_blank" rel="noopener noreferrer"`, đích `quanlyvanban.hanoi.gov.vn` |
| Nút Đăng nhập hoạt động, chưa đăng nhập thì API cũng không lấy được dữ liệu | ⬜ | **Đợt 3** |
| Khối thời gian thực có "Cập nhật lúc" + trạng thái dự phòng | ✅ | CR-35, CR-36 |
| Bảng công trình đủ 7 cột, link Quyết định và Google Map hoạt động | ◐ | 7 cột xong; hai liên kết tài liệu **nay gán được tệp** (28/8) — chờ dữ liệu |
| Hiển thị đúng trên máy tính, máy tính bảng và điện thoại | ✅ | ⭐ **Sửa 28/8.** Thanh điều hướng đo được là **tràn khung 1454/1192px** — xuống dòng ở *mọi* bề rộng; và menu con **không mở được bằng chạm** trên máy tính bảng. Nay có ngăn kéo dưới 1024px, menu con mở bằng chạm và bằng bàn phím |
| Không còn nội dung nhóm F, không còn ảnh minh hoạ không liên quan | ✅ | CR-39→43; ảnh stock gỡ 25/8. Một bộ canh chạy ở mỗi lượt CI chặn dữ liệu bịa quay lại |
| Các vấn đề §9 đã có câu trả lời hoặc thành công việc | ✅ | §4 dưới đây |

---

## 4. Trả lời §9 — mười vấn đề cần làm rõ

### Phía phát triển trả lời (tài liệu đề nghị trả lời **ngay trong tuần**)

**OI-01 — API mực nước, lượng mưa: CHƯA CÓ.** Mô-đun quản lý dữ liệu thuỷ văn chưa dựng. Nguồn
`songnhue.bhh40.net` đã khảo sát xong và có **ba giới hạn Công ty cần biết trước khi nghiệm thu**:

1. ⛔ **Không có API lượng mưa** — nguồn chỉ cấp mực nước. Cột "lượng mưa" của §5.2 **không tự động
   hoá được** bằng nguồn hiện tại; hoặc nhập tay, hoặc bổ sung một nguồn khác.
2. ⛔ **Không có API lịch sử** — chỉ đọc được giá trị *hiện tại*. Bảng theo tuần/tháng của CR-14 vì
   thế chỉ có dữ liệu **kể từ ngày hệ thống bắt đầu thu thập**, không truy ngược được.
3. ⚠ Nguồn phủ **19 điểm đo**, ít hơn biểu giấy Công ty đang dùng.

**OI-02 — API vận hành trạm bơm: CHƯA CÓ.** Hệ thống đã có bảng ghi *tình hình vận hành* nhập tay,
nhưng mỗi bản ghi mang **một** giá trị tham số, còn §5.3 đòi bốn trường cùng lúc theo ngày (trạng
thái · số máy chạy · lưu lượng · thời điểm). Thiếu ba thứ: API nguồn · danh mục công trình (G8) ·
quyết định mở nhật ký vận hành ra công khai (dữ liệu ấy thuộc phạm vi từng Xí nghiệp).

**OI-07 — KMZ:** đề xuất **cho tải về sau đăng nhập** ở đợt đầu, chưa nhúng trình xem. Lý do: KMZ là
tệp nén chứa KML, trình duyệt không mở trực tiếp; nhúng trình xem đòi chuyển sang GeoJSON, mà bước
chuyển ấy **thay đổi dữ liệu** nên phải có người của Công ty đối soát kết quả. Cho tải về giữ nguyên
bản gốc và không tạo ra một bản thứ hai để lệch.

### Chờ Công ty trả lời

| Mã | Nội dung | Chặn gì |
|:---:|---|---|
| **OI-03** | Danh sách chính xác **10 cống trục chính** hiện công khai ở trang chủ | Nội dung khối CR-13 |
| **OI-04** | Email chân trang: bỏ hẳn hay thay bằng email công vụ | Trang Liên hệ (dữ liệu vẫn đang giữ) |
| **OI-05** | **7 hay 8 Xí nghiệp** — Bố cục ghi 7, danh mục công trình có 8 (thêm XNTL Nhật Tựu) | ⛔ **Nhập liệu CR-19, CR-25, CR-26** |
| **OI-06** | Cơ chế cấp tài khoản, bao nhiêu nhóm quyền, phạm vi mỗi nhóm | Đợt 3 (CR-08/38) |
| **OI-08** | Thanh trên cùng: giữ / đổi tên / bỏ "Tra cứu văn bản" và "Gửi phản ánh kiến nghị" | Đợt này **giữ nguyên** — bỏ trước là tự quyết thay Công ty |
| **OI-09** | Chu kỳ tự làm mới mong muốn (5 / 10 / 15 phút) | Hiện đặt 5 phút, **đổi bằng một cú bấm**, không cần cập nhật phần mềm |
| **OI-10** | Hai biểu mẫu Báo cáo nhanh chống hạn / chống úng: chỉ tải mẫu hay nhập trực tuyến | Đợt 3 |

### ⭐ Mục mới, phát sinh 28/8 — đề nghị Công ty chốt

**OI-11 — Google Analytics / GTM: có chấp nhận đánh đổi về quyền riêng tư không?**
Bật GA/GTM buộc cổng tải mã từ máy chủ Google, nghĩa là **địa chỉ IP của mọi người dân tra cứu cổng
được gửi sang Google ở mỗi lượt tải trang**, và người đọc không có cách nào từ chối. Đây cũng là lý
do cổng đã tự lưu trữ phông chữ thay vì lấy từ Google. Nếu Công ty chỉ cần **số liệu truy cập**, có
phương án thống kê chạy trên chính máy chủ của Công ty, không gửi dữ liệu ra ngoài. Hai ô nhập mã
GA/GTM đã tạm gỡ khỏi màn hình cấu hình vì chúng **chưa từng có tác dụng** — sẽ dựng lại cùng lượt
với phần hạ tầng đứng sau, theo phương án Công ty chọn.

**OI-12 — Tên chính thức của hai cơ quan ở dải liên kết.** CR-21 yêu cầu rà soát tên và địa chỉ.
"Sở Nông nghiệp và Môi trường Hà Nội" đã sửa theo tài liệu chỉ đích danh. Hai dòng còn lại —
**Bộ Nông nghiệp & PTNT** và **Cục Thuỷ lợi** — giữ nguyên chờ Công ty xác nhận: đổi tên một cơ quan
nhà nước theo suy đoán thì sai cũng không ai phát hiện, mà cổng lại là nơi công bố. *Khác biệt sau
28/8: xác nhận xong thì sửa bằng một cú bấm ở màn hình Menu, không cần cập nhật phần mềm.*

---

## 5. Những gì Công ty sẽ thấy **rỗng** khi nghiệm thu, và vì sao

⛔ Dự án có một luật tự áp: **ô nào chưa có nguồn thì nói thẳng là chưa có.** Không dựng dữ liệu mẫu
"cho giao diện sống động" — một bộ dữ liệu dự phòng không làm dịu sự cố, nó **xoá dấu vết** của sự
cố. Bản dev trước 25/8 từng có 19 bài viết, 4 văn bản *có số hiệu và người ký*, 5 trạm thuỷ văn *có
mực nước*, 9 số điện thoại — tất cả bịa, tất cả đã lên môi trường thử, và **không ai nhìn ra đường
dữ liệu đã chết**.

| Khu vực | Rỗng vì | Đầy lên khi |
|---|---|---|
| Slider ảnh trang chủ | chưa tải ảnh | Công ty gửi 10–20 ảnh hoạt động |
| Lãnh đạo Công ty · Xí nghiệp trực thuộc | `org_units` cố ý không seed | **OI-05** chốt xong rồi nhập |
| Danh mục công trình · Tiến độ sản xuất | dữ liệu công trình thuộc **G8** | Công ty gửi danh mục |
| Mực nước, lượng mưa · Vận hành công trình | **chưa có API** (OI-01, OI-02) | dựng mô-đun thuỷ văn / có nguồn |
| Video phóng sự | chưa dán mã video | dán vào màn hình cấu hình |
| Thư viện ảnh công trình | chưa mở ra cổng công khai | quyết định phạm vi công bố — nợ nội bộ |

---

## 6. Còn nợ phía phát triển — 3 mục, có số đo, không giấu

| | Nợ | Ảnh hưởng người dùng |
|---|---|---|
| **T25.22** | Sửa dữ liệu tổ chức / công trình **không xoá bộ nhớ đệm của cổng** | Nhập xong, cổng đổi theo sau **tối đa 5 phút**. Không mất dữ liệu, tự lành. Màn hình quản trị đã ghi rõ dòng cảnh báo để người nhập không tưởng mình lưu hỏng |
| **T25.23** | 25 mã màu ghi cứng còn lại ở **trang quản trị** (cổng công khai đã sạch) | Không ảnh hưởng hiển thị; là nợ bảo trì |
| **T25.24** | Thư viện ảnh công trình chưa có đường công khai | Ô ảnh của khối Truyền thông còn rỗng |

---

## 7. Ba việc còn lại để đóng nghiệm thu v2

1. **Đợt 3 — đăng nhập trên cổng** (CR-08 · CR-14 · CR-38 · vế KMZ của CR-29). Cơ chế đã chốt: form
   đăng nhập ngay trên cổng, một vai trò *"Người xem cổng"*, và phân quyền chặn ở **tầng route/API**
   — không ẩn ở giao diện.
2. **Nhập liệu** — mở khoá bằng **OI-05** (7 hay 8 Xí nghiệp) và **G8** (danh mục công trình).
3. **Nguồn số liệu** — mở khoá bằng câu trả lời cho OI-01 / OI-02.

> **Ba việc này độc lập với nhau** và có thể chạy song song: việc 1 là phần mềm, việc 2 và 3 là dữ
> liệu. Không việc nào chặn việc nào.
