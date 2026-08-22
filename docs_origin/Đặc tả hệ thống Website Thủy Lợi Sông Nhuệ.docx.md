> ⚠⚠ **TÀI LIỆU LỊCH SỬ — ĐÃ BỊ SRS v1.0 (06/8/2026) THAY THẾ. ĐỪNG CODE THEO TỆP NÀY.**
>
> Đây là bản gốc đặc tả hệ thống website do khách hàng gửi ngày 20/7/2026, giữ lại **chỉ để đối chiếu lúc nghiệm thu**
> khi có câu hỏi "chỗ này trong đặc tả gốc ghi gì". Nội dung đã được tổng hợp vào
> `.claude/function-spec.md`, và **nhiều phần đã bị chính Công ty huỷ bỏ** ở BOQ đợt 1 và đợt 2
> (12/8/2026): nhật ký vận hành · phiếu sự cố riêng · kế hoạch tưới tiêu/vụ mùa · diện tích tưới tiêu ·
> trạng thái tổ máy realtime · đồng bộ danh sách văn bản điều hành.
>
> **Nguồn sự thật về nghiệp vụ là `.claude/function-spec.md`** (v2.2); thứ tự ưu tiên khi mâu thuẫn
> ghi ở `CLAUDE.md` mục "Cấu trúc tài liệu". Ghi chú thêm 21/8/2026.

---

**HỆ THỐNG QUẢN TRỊ VẬN HÀNH CHO**

**CÔNG TY ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ**

**1\. Đặc tả Hệ thống Website tin tức – Cổng thông tin điện tử cho Công ty Đầu tư Phát triển Thủy lợi Sông Nhuệ**

**1.1. Giới thiệu**

Bản đặc tả hệ thống này mô tả các yêu cầu chức năng và phi chức năng cho việc nâng cấp hệ thống website tin tức \- Cổng thông tin điện tử cho Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ. Mục tiêu là cung cấp một nền tảng trực tuyến toàn diện, dễ sử dụng để công bố thông tin, quản lý hoạt động và tương tác với người dùng, đồng thời hỗ trợ truy cập trên nhiều thiết bị khác nhau, bao gồm cả thiết bị di động.

**1.2. Mục tiêu hệ thống**

Cung cấp thông tin minh bạch và kịp thời về hoạt động của Công ty.

Hỗ trợ quản lý và vận hành các công trình thủy lợi.

Nâng cao hiệu quả truyền thông nội bộ và bên ngoài.

Cải thiện trải nghiệm người dùng thông qua giao diện hiện đại, thân thiện và tối ưu cho cả thiết bị di động (Responsive Web Design).

**1.3. Đối tượng người dùng**

Người dùng công cộng: Truy cập thông tin chung, tin tức, sự kiện, giới thiệu công ty, văn bản pháp luật.

Người dùng Nội bộ/ Quản lý: Truy cập thông tin chuyên sâu về mực nước, lượng mưa, vận hành công trình, bản đồ hệ thống, quản lý xí nghiệp, quản lý văn bản và báo cáo.

**1.4. Yêu cầu chức năng**

Hệ thống bao gồm các hệ thống con và chức năng chính sau:

Tui![][image1]

**1.4.1. Hệ thống quản trị website Cổng thông tin Sông Nhuệ**

Hệ thống quản trị Website tin tức \-Cổng thông tin Sông Nhuệ (CMS & Cổng thông tin) là thành phần quản lý toàn bộ nội dung hiển thị ra bên ngoài (Public) cho người dân, đối tác và khách hàng tra cứu. Mô-đun này đảm bảo tính nhất quán, chuyên nghiệp và khả năng cập nhật nhanh chóng cho cổng thông tin của công ty.

| Thông tin       | Chi tiết                                                     |
| :-------------- | :----------------------------------------------------------- |
| Tên mô-đun      | Hệ thống Quản trị Website (CMS & Cổng thông tin)             |
| Mã mô-đun       | MOD-01                                                       |
| Phiên bản       | 1.0                                                          |
| Nhóm người dùng | Biên tập viên, Quản trị viên nội dung, Admin hệ thống        |
| Tích hợp        | Mô-đun Vận hành (dữ liệu thủy văn), Mô-đun Quản trị hệ thống |
| Trạng thái      | Đang xây dựng đặc tả                                         |

## **Mục tiêu chức năng**

- Cung cấp công cụ tạo, chỉnh sửa, quản lý và xuất bản nội dung website của công ty một cách chuyên nghiệp và hiệu quả.

- Hỗ trợ quy trình duyệt nội dung nội bộ (Workflow) trước khi đăng tải ra bên ngoài.

- Tối ưu hóa nội dung cho công cụ tìm kiếm (SEO) nhằm tăng khả năng tiếp cận của cổng thông tin.

- Quản lý thống nhất giao diện, banner, media và các thành phần trực quan của website.

- Tích hợp dữ liệu thủy văn theo thời gian thực từ hệ thống vận hành để hiển thị cho người dùng.

## **Danh sách chức năng tổng hợp**

| Mã CN   | Tên chức năng              | Mức ưu tiên | Phân hệ   |
| :------ | :------------------------- | :---------- | :-------- |
| CN-01.1 | Quản lý Bài viết & Tin tức | Cao         | 1.1       |
| CN-01.2 | Quản lý Danh mục nội dung  | Cao         | 1.2       |
| CN-01.3 | Quản lý Thư viện Media     | Trung bình  | 1.3       |
| CN-01.4 | Quản lý Liên hệ            | Trung bình  | 1.5       |
| CN-01.5 | Quản lý Cấu hình giao diện | Trung bình  | 1.4 / 1.6 |
| CN-01.6 | Quản lý Phản hồi           | Thấp        | 1.7       |

Chức năng quản lý bài viết là trung tâm của hệ thống CMS, cho phép người dùng được phân quyền tạo mới, chỉnh sửa, duyệt và xuất bản các bài viết, tin tức lên website. Toàn bộ quy trình từ soạn thảo đến xuất bản được kiểm soát chặt chẽ thông qua cơ chế Workflow.

**1.4.1.1. Quản lý bài viết & tin tức**

### **a) Tạo & Chỉnh sửa Bài viết**

**Giao diện soạn thảo**

- Cung cấp trình soạn thảo văn bản nâng cao (Rich Text Editor) tích hợp CKEditor hoặc TinyMCE.

- Hỗ trợ các định dạng văn bản cơ bản: in đậm, in nghiêng, gạch chân, màu chữ, màu nền, căn lề.

- Chèn và quản lý hình ảnh inline: upload trực tiếp hoặc chọn từ thư viện Media, thiết lập căn trái/giữa/phải, kích thước, chú thích ảnh.

- Nhúng video từ YouTube/Vimeo qua URL hoặc embed code.

- Chèn bảng biểu (Table) với tùy chỉnh số hàng, số cột, màu nền ô.

- Hỗ trợ chèn liên kết nội bộ (Internal Link) và liên kết ngoài (External Link), tùy chọn mở trong tab mới.

- Chế độ xem trước (Preview) ngay trong giao diện soạn thảo trước khi lưu.

**Các trường thông tin bài viết**

| Trường           | Kiểu dữ liệu           | Bắt buộc | Mô tả                                                         |
| :--------------- | :--------------------- | :------- | :------------------------------------------------------------ |
| Tiêu đề          | Text (255 ký tự)       | Có       | Tiêu đề bài viết hiển thị trên website                        |
| Slug (URL)       | Text (auto/manual)     | Có       | URL thân thiện, tự động tạo từ tiêu đề, cho phép tùy chỉnh    |
| Ảnh đại diện     | Image                  | Không    | Hình ảnh thumbnail hiển thị trong danh sách bài viết          |
| Tóm tắt          | Textarea (500 ký tự)   | Không    | Đoạn mô tả ngắn xuất hiện trong danh sách và kết quả tìm kiếm |
| Nội dung         | Rich Text              | Có       | Nội dung đầy đủ soạn qua Rich Text Editor                     |
| Danh mục         | Dropdown (multi)       | Có       | Phân loại bài vào một hoặc nhiều danh mục                     |
| Tác giả          | Dropdown (User)        | Có       | Tự động điền tài khoản đang đăng nhập, cho phép thay đổi      |
| Nguồn tin        | Text (255 ký tự)       | Không    | Ghi rõ nguồn trích dẫn nếu là bài tổng hợp                    |
| Thẻ (Tags)       | Text (comma-separated) | Không    | Từ khóa phân loại bổ sung                                     |
| Ngày xuất bản    | DateTime               | Không    | Hẹn giờ đăng bài; mặc định là thời điểm nhấn Xuất bản         |
| Meta Title       | Text (70 ký tự)        | Không    | Tiêu đề SEO, mặc định lấy từ Tiêu đề bài viết                 |
| Meta Description | Textarea (160 ký tự)   | Không    | Mô tả SEO, mặc định lấy từ Tóm tắt                            |
| Meta Keywords    | Text                   | Không    | Từ khóa SEO phân cách bằng dấu phẩy                           |
| Trạng thái       | Enum                   | Có       | Nháp / Chờ duyệt / Xuất bản / Gỡ bài / Lưu trữ                |

### **b) Quy trình Xuất bản (Workflow)**

**Sơ đồ trạng thái**

- Nháp (Draft): Bài viết đang soạn thảo, chưa gửi duyệt. Chỉ tác giả và Admin xem được.

- Chờ duyệt (Pending Review): Tác giả đã hoàn thiện và gửi lên cho Biên tập viên/Admin xét duyệt.

- Xuất bản (Published): Bài đã được duyệt và hiển thị công khai trên website.

- Gỡ bài (Unpublished): Ẩn bài viết khỏi website mà không xóa dữ liệu.

- Lưu trữ (Archived): Bài viết cũ được lưu trữ, không hiển thị trên website nhưng vẫn truy cập được qua URL trực tiếp.

**Phân quyền theo Workflow**

| Vai trò                | Tạo nháp | Gửi duyệt | Duyệt/Từ chối | Xuất bản | Gỡ bài |
| :--------------------- | :------- | :-------- | :------------ | :------- | :----- |
| Biên tập viên          | ✔        | ✔         | ✘             | ✘        | ✘      |
| Trưởng ban biên tập    | ✔        | ✔         | ✔             | ✔        | ✔      |
| Quản trị viên nội dung | ✔        | ✔         | ✔             | ✔        | ✔      |
| Admin hệ thống         | ✔        | ✔         | ✔             | ✔        | ✔      |

### **c) Hỗ trợ SEO**

- Tự động tạo URL Slug từ tiêu đề bài viết (chuyển đổi ký tự tiếng Việt sang không dấu, thay khoảng trắng bằng dấu gạch ngang).

- Cho phép tùy chỉnh Slug thủ công; cảnh báo nếu Slug trùng với bài viết đã tồn tại.

- Giao diện điền Meta Title, Meta Description, Keywords trực quan ngay trong form chỉnh sửa bài viết.

- Hiển thị bộ đếm ký tự theo chuẩn Google: Meta Title (tối đa 70 ký tự), Meta Description (tối đa 160 ký tự), cảnh báo màu đỏ nếu vượt ngưỡng.

- Hỗ trợ Open Graph tags (og:title, og:description, og:image) cho chia sẻ mạng xã hội.

### **d) Hẹn giờ Đăng bài**

- Cho phép thiết lập ngày giờ xuất bản trong tương lai. Bài viết sẽ tự động chuyển trạng thái 'Xuất bản' vào đúng thời điểm đã hẹn.

- Hệ thống chạy cron job kiểm tra và xuất bản bài hẹn giờ mỗi 5 phút.

- Hiển thị danh sách bài đang hẹn giờ và thời điểm sẽ đăng trên trang quản lý.

## **e) Quản lý Danh sách Bài viết**

- Giao diện danh sách bài viết dạng bảng với các cột: ID, Tiêu đề, Danh mục, Tác giả, Ngày tạo, Ngày cập nhật, Trạng thái, Thao tác.

- Tìm kiếm bài viết theo từ khóa trong tiêu đề hoặc nội dung.

- Lọc theo: Danh mục, Trạng thái, Tác giả, Khoảng thời gian tạo/cập nhật.

- Sắp xếp theo: Ngày tạo, Ngày cập nhật, Tiêu đề (tăng/giảm dần).

- Chức năng Bulk Action: chọn nhiều bài viết để thực hiện đồng loạt (xóa, gỡ bài, lưu trữ).

- Phân trang với tùy chỉnh số bài hiển thị mỗi trang (20/50/100).

## **f) Lịch sử Thay đổi (Audit Log)**

- Ghi lại toàn bộ lịch sử chỉnh sửa bài viết: thời gian, người thực hiện, hành động (tạo mới / sửa / đổi trạng thái / xóa).

- Hỗ trợ so sánh phiên bản (Version Diff): xem sự khác biệt giữa phiên bản hiện tại và phiên bản trước.

- Cho phép khôi phục về phiên bản cũ (Rollback).

**1.4.1.2. Quản lý danh mục nội dung**

Chức năng quản lý danh mục cho phép xây dựng cấu trúc phân cấp nội dung website (cây danh mục), làm nền tảng để tổ chức bài viết, cấu hình điều hướng Menu và trang liệt kê nội dung theo chủ đề.

**a) Cấu trúc danh mục đa cấp**

- Hỗ trợ tạo danh mục nhiều cấp (tối thiểu 3 cấp): Cấp 1 (Menu chính) → Cấp 2 (Submenu) → Cấp 3 (Chuyên mục con).
- Mỗi danh mục có các thuộc tính:
  - Tên danh mục (hiển thị trên website)
  - Slug (URL path cho trang danh mục)
  - Danh mục cha (chọn từ danh sách hoặc để trống nếu là cấp 1\)
  - Mô tả ngắn (hiển thị trên trang danh mục)
  - Ảnh đại diện danh mục
  - Thứ tự hiển thị (Order/Sort)
  - Trạng thái Hiển thị / Ẩn
- Hiển thị cấu trúc cây danh mục trực quan dạng accordion (thu gọn/mở rộng) trong giao diện quản trị.

**b) Ví dụ cấu trúc danh mục**

| Cấp 1 (Menu chính)     | Cấp 2 (Submenu)         | Cấp 3 (Chuyên mục con) |
| :--------------------- | :---------------------- | :--------------------- |
| Tin tức & Sự kiện      | Tin công ty             |                        |
|                        | Tin ngành Thủy lợi      |                        |
|                        | Hoạt động Đảng/Đoàn thể |                        |
| Thông tin doanh nghiệp | Giới thiệu              | Lịch sử hình thành     |
|                        |                         | Cơ cấu tổ chức         |
|                        | Công bố thông tin       |                        |
| Dịch vụ & Công trình   | Danh mục công trình     |                        |
|                        | Dịch vụ cung cấp        |                        |

**1.4.1.3. Quản lý thư viện Media**

Thư viện Media là kho lưu trữ tập trung cho tất cả tài nguyên đa phương tiện (hình ảnh, video, tài liệu) được sử dụng trên website. Đây là nguồn tham chiếu duy nhất để đảm bảo quản lý nhất quán và tránh trùng lặp tài nguyên.

**a) Quản lý thư mục**

- Tạo cấu trúc thư mục phân cấp (tối đa 3 cấp): Ảnh (Ảnh tin tức, Ảnh sự kiện, Ảnh công trình), Video, Tài liệu.
- Thao tác trên thư mục: Tạo mới, Đổi tên, Xóa (chỉ khi thư mục rỗng), Di chuyển.
- Hiển thị dung lượng đã sử dụng và số lượng file trong mỗi thư mục.

**b) Upload File**

- Hỗ trợ upload nhiều file cùng lúc (Multi-upload) qua giao diện kéo-thả hoặc chọn file.
- Thanh tiến trình upload (Progress bar) hiển thị phần trăm hoàn thành cho từng file.
- Tự động nén và chuyển đổi ảnh sang định dạng WebP để tối ưu dung lượng (giữ bản gốc làm fallback).
- Tự động tạo thumbnail cho ảnh (3 kích thước: nhỏ 150px, vừa 400px, lớn 800px).

**Giới hạn file được hỗ trợ**

| Loại file | Định dạng cho phép             | Dung lượng tối đa | Ghi chú                        |
| :-------- | :----------------------------- | :---------------- | :----------------------------- |
| Hình ảnh  | JPG, PNG, GIF, WebP, SVG       | 10 MB/file        | Tự động nén sang WebP          |
| Video     | MP4, WebM                      | 500 MB/file       | Khuyến nghị dùng YouTube embed |
| Tài liệu  | PDF, DOC, DOCX, XLS, XLSX, PPT | 50 MB/file        |                                |
| Nén       | ZIP                            | 100 MB/file       | Giải nén tự động nếu chứa ảnh  |

**c) Quản lý File**

- Giao diện xem file dạng lưới (Grid) và danh sách (List), có thể chuyển đổi.
- Tìm kiếm file theo tên; lọc theo: loại file, thư mục, ngày upload.
- Xem chi tiết file: tên, dung lượng, kích thước (đối với ảnh), ngày upload, người upload, URL công khai.
- Sao chép URL file vào clipboard chỉ bằng 1 click.
- Xóa file với xác nhận; cảnh báo nếu file đang được tham chiếu trong bài viết.
- Di chuyển file sang thư mục khác.

**1.4.1.4. Quản lý cấu hình giao diện**

Phần quản lý giao diện bao gồm ba thành phần chính: Banner/Carousel trang chủ, Footer website và Widget hiển thị dữ liệu thủy văn thời gian thực.

**a) Quản lý Banner**

**Quản lý danh sách Banner**

- Giao diện quản lý danh sách các hình ảnh trong carousel trang chủ.
- Mỗi banner bao gồm các thuộc tính:
  - Hình ảnh: upload hoặc chọn từ thư viện Media (khuyến nghị kích thước 1920x600px)
  - Tiêu đề overlay (tùy chọn): hiển thị chữ đè lên hình ảnh
  - Mô tả ngắn (tùy chọn)
  - Liên kết (URL): khi nhấn vào banner chuyển đến trang cụ thể, có thể là liên kết nội bộ hoặc ngoài
  - Thứ tự hiển thị: kéo thả để sắp xếp
  - Trạng thái: Bật / Tắt (tắt banner mà không cần xóa)
  - Ngày bắt đầu / Ngày kết thúc hiển thị (hỗ trợ banner theo sự kiện, chiến dịch)

**Cấu hình Slider**

- Tốc độ chuyển slide: cấu hình thời gian dừng mỗi slide (mặc định 5 giây).
- Hiệu ứng chuyển: Fade hoặc Slide (chọn một trong hai).
- Bật/Tắt tự động chạy (Autoplay).
- Bật/Tắt hiển thị nút điều hướng (Prev/Next arrows, Dots indicator).

**b) Quản lý Footer**

- Trình soạn thảo HTML đơn giản (WYSIWYG) để cập nhật nội dung footer.
- Các khối nội dung trong Footer:
  - Khối Thông tin công ty: Tên công ty, địa chỉ, số điện thoại, email, fax.
  - Khối Bản đồ: Nhúng Google Maps iframe bằng cách dán embed code vào trường cấu hình.
  - Khối Liên kết nhanh: Danh sách liên kết tùy chỉnh (tên \+ URL).
  - Khối Mạng xã hội: Icon và link đến Facebook, Zalo, YouTube của công ty.
  - Thông báo bản quyền: Dòng copyright cuối trang.
- Xem trước footer ngay trong giao diện quản trị trước khi lưu.

**c) Widget Thủy văn (Tích hợp dữ liệu thời gian thực)**

**Mô tả**

Widget thủy văn hiển thị dữ liệu quan trắc thời gian thực trên trang chủ website, lấy từ Mô-đun Vận hành công trình. Mục tiêu cung cấp thông tin nhanh cho người dân, nhà quản lý về tình trạng thủy văn hiện tại.

**Cấu hình Widget**

- Chọn danh sách trạm quan trắc hiển thị trên widget (chọn nhiều trạm).
- Chọn các thông số hiển thị: Mực nước, Lưu lượng, Nhiệt độ nước, Trạng thái cống.
- Cấu hình tần suất làm mới dữ liệu (Auto-refresh): 5 phút / 10 phút / 15 phút / 30 phút.
- Bật/Tắt hiển thị màu cảnh báo khi thông số vượt ngưỡng an toàn (màu vàng: cảnh báo, màu đỏ: nguy hiểm).
- Tùy chỉnh vị trí hiển thị widget: Sidebar phải, Banner dưới, Floating widget.

**Cơ chế lấy dữ liệu**

- Dữ liệu được lấy qua REST API nội bộ từ Mô-đun Vận hành công trình (MOD-02).
- Giao diện Public gọi API endpoint có xác thực token để lấy số liệu thủy văn mới nhất.
- Trong trường hợp API không phản hồi, hiển thị thông báo 'Không có dữ liệu' thay vì lỗi kỹ thuật.

**1.4.1.5. Quản lý liên hệ**

**Mô tả chức năng**

Chức năng quản lý liên hệ tiếp nhận, lưu trữ và xử lý các yêu cầu gửi qua form liên hệ trên website từ người dân, đối tác và khách hàng.

**a) Quản lý Form Liên hệ**

- Cấu hình các trường trong form liên hệ Public: Họ tên, Email, Số điện thoại, Chủ đề, Nội dung, Mã xác nhận (CAPTCHA).
- Bật/Tắt từng trường; thiết lập trường bắt buộc.
- Tích hợp Google reCAPTCHA v3 để chống spam.
- Thiết lập email nhận thông báo khi có liên hệ mới (nhiều email nhận, phân cách dấu phẩy).
- Email xác nhận tự động gửi lại cho người gửi.

**b) Quản lý Danh sách Liên hệ**

Hiển thị danh sách tất cả liên hệ đã nhận với thông tin: Người gửi, Email, Điện thoại, Chủ đề, Ngày nhận, Trạng thái.

- Trạng thái liên hệ: Mới (Unread) / Đã đọc / Đang xử lý / Đã xử lý / Lưu trữ.
- Xem chi tiết từng liên hệ; ghi chú nội bộ (Memo) về kết quả xử lý.
- Xuất danh sách liên hệ ra Excel để báo cáo.
- Xóa liên hệ với xác nhận; không cho phép xóa liên hệ đang trong trạng thái 'Đang xử lý.

**1.4.1.6. Quản lý cấu hình giao diện Website**

- Tên website, slogan, logo (upload ảnh, khuyến nghị SVG hoặc PNG nền trong suốt).
- Favicon (icon tab trình duyệt, kích thước 32x32px).
- Màu chủ đạo (Primary Color) và màu phụ (Secondary Color) theo bộ nhận diện thương hiệu.
- Google Analytics Tracking ID (nhúng script tracking).
- Google Tag Manager Container ID.
- Chế độ Bảo trì (Maintenance Mode): Bật/Tắt, cấu hình thông điệp hiển thị khi website bảo trì.

**a) Cấu hình Menu Điều hướng**

- Quản lý Menu chính (Header Navigation) và Menu phụ (Footer Navigation) độc lập.
- Mỗi item menu có: Tên hiển thị, Loại liên kết (Danh mục, Bài viết, URL tùy chỉnh), Mở trong tab mới, Trạng thái, Thứ tự.
- Kéo thả để sắp xếp và tạo submenu (nested menu).

**b) Cấu hình Trang đặc biệt**

- Trang chủ: Chọn nội dung hiển thị (Slider, Bài viết nổi bật, Dịch vụ, Widget thủy văn).
- Trang 404: Tùy chỉnh thông điệp và liên kết gợi ý khi trang không tìm thấy.
- Trang Tìm kiếm: Cấu hình phạm vi tìm kiếm (Bài viết, Danh mục, Tài liệu).

**1.4.1.7. Quản lý phản hồi**

Chức năng quản lý phản hồi kiểm duyệt bình luận của độc giả dưới các bài viết trên website trước khi hiển thị công khai.

**a) Chi tiết chức năng**

- Bật/Tắt chức năng bình luận cho toàn bộ website hoặc từng danh mục nội dung.
- Quy trình duyệt phản hồi: Tất cả bình luận mới ở trạng thái 'Chờ duyệt', chỉ hiển thị sau khi được duyệt.
- Giao diện danh sách phản hồi: Nội dung, Người gửi, Email, Bài viết liên quan, Ngày gửi, Trạng thái.
- Thao tác: Duyệt, Từ chối, Xóa, Đánh dấu Spam.
- Tích hợp Akismet hoặc cơ chế lọc spam tương đương để tự động nhận diện và giữ bình luận spam.
- Thông báo email khi có phản hồi mới cần duyệt.

**1.4.2. Hệ thống quản lý vận hành công trình**

Mô-đun 2 là lõi nghiệp vụ kỹ thuật của hệ thống, phục vụ trực tiếp công tác vận hành, điều tiết tưới tiêu và giám sát an toàn công trình thủy lợi. Mô-đun số hóa toàn bộ quy trình từ quản lý hồ sơ công trình, thu thập dữ liệu thủy văn tự động, trực quan hóa trên bản đồ GIS đến ghi nhận nhật ký và xuất báo cáo định kỳ.

| Thông tin         | Chi tiết                                                                   |
| ----------------- | -------------------------------------------------------------------------- |
| Tên mô-đun        | Hệ thống Quản lý Vận hành Công trình                                       |
| Mã mô-đun         | MOD-02                                                                     |
| Phiên bản         | 1.0                                                                        |
| Nhóm người dùng   | Vận hành viên (Operator), Cán bộ kỹ thuật, Quản lý Xí nghiệp, Admin        |
| Tích hợp          | MOD-01 (CMS – Widget thủy văn), MOD-04 (Văn bản điều hành), MOD-05 (Admin) |
| Công nghệ đặc thù | REST API polling, WebSocket push, GIS (GeoJSON/KMZ), Async Job Queue       |
| Trạng thái        | Đang xây dựng đặc tả                                                       |

**Mục tiêu chức năng**

- Xây dựng kho dữ liệu chuẩn hóa cho toàn bộ công trình (trạm bơm, cống, kênh mương) thuộc phạm vi quản lý.
- Tự động thu thập và lưu trữ số liệu thủy văn từ các trạm quan trắc, giảm thao tác nhập liệu thủ công.
- Phát hiện và cảnh báo sớm khi mực nước, lưu lượng vượt ngưỡng an toàn thiết kế.
- Cung cấp bản đồ GIS tương tác, trực quan hóa không gian hệ thống công trình.
- Số hóa nhật ký vận hành hàng ngày của các Xí nghiệp, thay thế sổ giấy.
- Tự động tổng hợp và xuất báo cáo định kỳ theo mẫu chuẩn của Công ty.

**Danh sách chức năng tổng hợp**

| Mã CN   | Tên chức năng                   | Ưu tiên    | Người dùng chính         |
| ------- | ------------------------------- | ---------- | ------------------------ |
| CN-02.1 | Quản lý Danh mục Công trình     | Cao        | Admin, Kỹ thuật          |
| CN-02.2 | Giám sát Dữ liệu Thủy văn (API) | Cao        | Kỹ thuật, Quản lý        |
| CN-02.3 | Cảnh báo ngưỡng thủy văn        | Cao        | Kỹ thuật, Quản lý        |
| CN-02.4 | Quản trị Bản đồ GIS             | Trung bình | Admin, Kỹ thuật          |
| CN-02.5 | Nhập Nhật ký Vận hành           | Cao        | Vận hành viên (Operator) |
| CN-02.6 | Tổng hợp & Xuất Báo cáo         | Cao        | Quản lý, Kỹ thuật        |
| CN-02.7 | Quản lý Sự cố Công trình        | Trung bình | Operator, Kỹ thuật       |
| CN-02.8 | Dashboard Vận hành tổng thể     | Trung bình | Tất cả vai trò           |

**1.4.2.1. Quản lý danh mục công trình**

Chức năng quản lý danh mục công trình là nền tảng dữ liệu cho toàn bộ Mô-đun 2\. Mỗi công trình (trạm bơm, cống điều tiết, kênh mương) được hồ sơ hóa đầy đủ với thông tin kỹ thuật, tọa độ địa lý, tài liệu vận hành và trạng thái hoạt động hiện tại.

**a) Phân cấp tổ chức công trình**

Hệ thống tổ chức công trình theo cấu trúc phân cấp 4 bậc:

- Cấp 1 – Công ty: Toàn bộ hệ thống công trình trực thuộc Công ty TNHH Thủy lợi Sông Nhuệ.
- Cấp 2 – Xí nghiệp: Đơn vị quản lý trực tiếp (Xí nghiệp 1, 2, 3... hoặc theo địa bàn).
- Cấp 3 – Cụm công trình: Nhóm công trình cùng phục vụ một vùng tưới tiêu.
- Cấp 4 – Công trình đơn lẻ: Trạm bơm, cống, kênh mương cụ thể.

**b) Hồ sơ trạm bơm**

**Thông tin định danh & Kỹ thuật**

| Trường thông tin     | Kiểu dữ liệu       | Bắt buộc | Mô tả / Ràng buộc                                |
| -------------------- | ------------------ | -------- | ------------------------------------------------ |
| Mã công trình        | Text (20 ký tự)    | Có       | Mã duy nhất, không trùng, VD: TB-SN-001          |
| Tên công trình       | Text (200 ký tự)   | Có       | Tên đầy đủ của trạm bơm                          |
| Loại công trình      | Enum               | Có       | Trạm bơm tưới / Trạm bơm tiêu / Trạm bơm hỗn hợp |
| Xí nghiệp quản lý    | Dropdown (FK)      | Có       | Liên kết đến bảng danh sách Xí nghiệp            |
| Cụm công trình       | Dropdown (FK)      | Không    | Liên kết đến cụm công trình                      |
| Địa chỉ              | Text (500 ký tự)   | Có       | Địa chỉ hành chính đầy đủ                        |
| Tọa độ Vĩ độ (Lat)   | Decimal (9,6)      | Có       | VD: 20.983456 – dùng cho GIS                     |
| Tọa độ Kinh độ (Lng) | Decimal (9,6)      | Có       | VD: 105.834567 – dùng cho GIS                    |
| Năm xây dựng         | Integer (4 chữ số) | Không    |                                                  |
| Năm đưa vào sử dụng  | Integer            | Không    |                                                  |
| Đơn vị thiết kế      | Text               | Không    | Tên tổ chức thiết kế                             |
| Đơn vị thi công      | Text               | Không    | Tên nhà thầu thi công                            |
| Tổng vốn đầu tư      | Decimal (15,2)     | Không    | Đơn vị: triệu đồng VND                           |

**Thông số kỹ thuật Trạm bơm**

| Thông số                                | Kiểu dữ liệu | Đơn vị | Mô tả                                           |
| --------------------------------------- | ------------ | ------ | ----------------------------------------------- |
| Công suất thiết kế tổng                 | Decimal      | kW     | Tổng công suất lắp đặt                          |
| Số lượng máy bơm                        | Integer      | Tổ máy | Số tổ máy hiện có                               |
| Số lượng máy bơm dự phòng               | Integer      | Tổ máy | Số tổ máy dự phòng                              |
| Lưu lượng thiết kế mỗi máy              | Decimal      | m³/s   | Lưu lượng định mức 1 tổ máy                     |
| Cột nước thiết kế                       | Decimal      | m      | Chênh lệch cột nước thiết kế                    |
| Lưu lượng tổng thiết kế                 | Decimal      | m³/s   | Tự động tính \= SL máy × Lưu lượng/máy          |
| Diện tích tưới tiêu                     | Decimal      | ha     | Diện tích phục vụ tưới tiêu                     |
| Nguồn điện cấp                          | Enum         |        | Lưới điện quốc gia / Máy phát dự phòng / Cả hai |
| Điện áp vận hành                        | Decimal      | kV     | VD: 6kV, 10kV, 35kV                             |
| Ngưỡng mực nước cho phép vận hành (min) | Decimal      | m      | Dừng máy nếu MN \< ngưỡng này                   |
| Ngưỡng mực nước cho phép vận hành (max) | Decimal      | m      | Cảnh báo nếu MN \> ngưỡng này                   |

**c) Hồ sơ Cống điều tiết**

| Trường thông tin               | Kiểu dữ liệu     | Bắt buộc | Mô tả                                              |
| ------------------------------ | ---------------- | -------- | -------------------------------------------------- |
| Mã công trình                  | Text (20 ký tự)  | Có       | VD: CG-SN-001                                      |
| Tên cống                       | Text (200 ký tự) | Có       |                                                    |
| Loại cống                      | Enum             | Có       | Cống hộp / Cống tròn / Cống van phẳng / Cống Clape |
| Xí nghiệp quản lý              | Dropdown         | Có       |                                                    |
| Tọa độ                         | Decimal          | Có       | Lat/Long                                           |
| Số lượng khoang cống           | Integer          | Có       | Số khẩu cống                                       |
| Khẩu độ mỗi khoang             | Decimal          | Có       | Đơn vị: m                                          |
| Cao trình ngưỡng cống          | Decimal          | Có       | Đơn vị: m (hệ cao độ quốc gia)                     |
| Cao trình đỉnh cống            | Decimal          | Không    |                                                    |
| Lưu lượng thiết kế             | Decimal          | Có       | m³/s                                               |
| Loại thiết bị đóng mở          | Enum             | Có       | Thủ công / Điện / Thủy lực                         |
| Ngưỡng MN thượng lưu cảnh báo  | Decimal          | Có       | Màu vàng khi vượt ngưỡng                           |
| Ngưỡng MN thượng lưu nguy hiểm | Decimal          | Có       | Màu đỏ khi vượt ngưỡng                             |

**d) Tài liệu đính kèm**

**Loại tài liệu được quản lý**

- Quy trình vận hành: File PDF quy định cụ thể các bước vận hành máy bơm/cống.
- Phương án bảo vệ công trình: File PDF phương án an ninh, phòng chống thiên tai.
- Hồ sơ hoàn công: Bản vẽ thiết kế as-built (PDF, DWG).
- Biên bản kiểm tra định kỳ: Kết quả kiểm tra hàng năm, đột xuất.
- Hợp đồng bảo trì, sửa chữa: Lưu trữ các hợp đồng liên quan.
- Ảnh chụp thực địa: Hình ảnh hiện trạng công trình theo thời gian.

**Quản lý tài liệu**

- Mỗi công trình có tab 'Tài liệu' riêng; upload nhiều file cùng lúc (định dạng: PDF, DOC, DOCX, DWG, JPG, PNG).
- Giới hạn dung lượng mỗi file: 50MB. Tổng dung lượng mỗi công trình: 500MB.
- Gán nhãn loại tài liệu, ngày lập, ngày hết hiệu lực (nếu có).
- Phân quyền xem/tải tài liệu: Chỉ Operator của Xí nghiệp quản lý và cấp trên mới được truy cập.
- Lịch sử phiên bản tài liệu: Khi upload phiên bản mới, phiên bản cũ được lưu trữ tự động.

**e) Quản lý tình trạng công trình**

| Trạng thái              | Màu hiển thị | Mô tả                              | Ai cập nhật        |
| ----------------------- | ------------ | ---------------------------------- | ------------------ |
| Hoạt động bình thường   | Xanh lá      | Đang vận hành đúng thiết kế        | Operator, Admin    |
| Đang bảo trì            | Vàng         | Tạm dừng để bảo trì định kỳ        | Admin, Kỹ thuật    |
| Sự cố – Ngừng hoạt động | Đỏ           | Hỏng hóc, chờ xử lý                | Operator, Kỹ thuật |
| Ngừng theo mùa vụ       | Xám          | Không vận hành theo lịch thủy nông | Quản lý            |
| Đã thanh lý             | Đen          | Công trình không còn sử dụng       | Admin              |

**1.4.2.2. Giám sát dữ liệu thủy văn**

Hệ thống tự động thu thập dữ liệu quan trắc từ các trạm đo thủy văn tự động (thiết bị IoT/sensor ngoài thực địa) thông qua cơ chế gọi API định kỳ (Polling). Dữ liệu được lưu trữ, hiển thị trực quan và kích hoạt cảnh báo khi vượt ngưỡng cho phép.

**a) Kiến trúc thu thập dữ liệu**

**Luồng dữ liệu tổng quát**

- Trạm quan trắc ngoài thực địa (RTU/DataLogger) → Gửi dữ liệu lên Server trung gian (Telemetry Server) của đơn vị cung cấp thiết bị.
- Hệ thống MOD-02 gọi REST API của Telemetry Server theo chu kỳ Polling (cấu hình 15 – 30 phút).
- Dữ liệu nhận về được validate, chuẩn hóa đơn vị và lưu vào cơ sở dữ liệu thủy văn nội bộ.
- API Gateway nội bộ phân phối dữ liệu thủy văn đã xử lý cho: Dashboard quản trị, Widget CMS (MOD-01), Cảnh báo Alert.

**Cấu hình kết nối API nguồn**

| Tham số cấu hình       | Kiểu         | Mô tả                                             |
| ---------------------- | ------------ | ------------------------------------------------- |
| Tên nguồn dữ liệu      | Text         | Tên định danh nguồn, VD: 'Trạm Hà Đông – VNMC'    |
| URL Endpoint           | URL          | Địa chỉ API endpoint của Telemetry Server         |
| Phương thức xác thực   | Enum         | API Key / Bearer Token / Basic Auth / OAuth2      |
| Thông tin xác thực     | Encrypted    | Lưu mã hóa AES-256, không hiển thị dạng plaintext |
| Chu kỳ polling         | Integer      | Đơn vị phút, tối thiểu 5 phút, mặc định 15 phút   |
| Timeout kết nối        | Integer      | Đơn vị giây, mặc định 30 giây                     |
| Số lần thử lại (Retry) | Integer      | Khi lỗi kết nối, mặc định 3 lần                   |
| Trạm gắn với nguồn     | Multi-select | Gán API source cho các trạm thuộc nguồn này       |
| Trạng thái             | Boolean      | Bật/Tắt kết nối                                   |

**b) Dữ liệu thu thập**

**Các thông số thủy văn được thu thập**

| Thông số                 | Ký hiệu | Đơn vị   | Mô tả                                       |
| ------------------------ | ------- | -------- | ------------------------------------------- |
| Mực nước thượng lưu      | H_TL    | cm / m   | Mực nước phía thượng lưu công trình         |
| Mực nước hạ lưu          | H_HL    | cm / m   | Mực nước phía hạ lưu công trình             |
| Lượng mưa lũy kế         | P       | mm       | Lượng mưa tích lũy theo chu kỳ đo           |
| Lượng mưa tức thời       | P_r     | mm/h     | Cường độ mưa tại thời điểm đo               |
| Lưu lượng                | Q       | m³/s     | Lưu lượng qua công trình (nếu có sensor)    |
| Nhiệt độ nước            | T_w     | °C       | Tùy chọn, nếu trạm hỗ trợ                   |
| Độ đục                   | NTU     | NTU      | Tùy chọn, nếu trạm hỗ trợ                   |
| Trạng thái thiết bị      | Status  | Enum     | Online / Offline / Warning / Error          |
| Thời gian đo (Timestamp) | TS      | ISO 8601 | Múi giờ UTC+7, lưu dưới dạng Unix timestamp |

**c) Lưu trữ dữ liệu thủy văn**

- Dữ liệu thô (raw data) từ API được lưu nguyên bản trong bảng log riêng để phục vụ audit và tái xử lý.
- Dữ liệu đã chuẩn hóa lưu vào bảng time-series với index theo (station_id, timestamp).
- Chính sách lưu trữ: Dữ liệu chi tiết (từng bản ghi 15-30 phút) lưu giữ 5 năm; Dữ liệu tổng hợp theo ngày lưu giữ vĩnh viễn.
- Dữ liệu cũ hơn 2 năm được chuyển sang Cold Storage (compressed archive) nhưng vẫn truy vấn được qua giao diện.
- Hỗ trợ nhập liệu thủ công (Manual Entry) khi API nguồn gián đoạn; đánh dấu rõ ràng nguồn \= 'Manual'.

**d) Giao diện hiển thị dữ liệu**

**Bảng số liệu thời gian thực**

- Hiển thị bảng dữ liệu mới nhất của tất cả trạm quan trắc, tự động làm mới (Auto-refresh) mỗi 5 phút.
- Cột hiển thị: Tên trạm, H_TL, H_HL, Lượng mưa, Lưu lượng, Trạng thái thiết bị, Thời gian cập nhật.
- Đổi màu nền ô: Xanh bình thường → Vàng cảnh báo → Đỏ nguy hiểm theo ngưỡng đã cấu hình.
- Hiển thị badge 'OFFLINE' màu đỏ nếu thời gian cập nhật cuối \> 1 giờ so với hiện tại.

**Biểu đồ đường (Line Chart)**

- Thư viện: Apache ECharts hoặc Highcharts – hỗ trợ zoom, pan, export ảnh.
- Chọn trạm quan trắc (một hoặc nhiều trạm cùng lúc để so sánh).
- Chọn thông số hiển thị: Mực nước TL, Mực nước HL, Lượng mưa, Lưu lượng.
- Chọn khoảng thời gian: Nhanh (24h, 7 ngày, 30 ngày) hoặc tùy chỉnh (date picker từ – đến).
- Hiển thị đường ngưỡng cảnh báo (nét đứt màu vàng) và ngưỡng nguy hiểm (nét đứt màu đỏ) trên biểu đồ.
- Tooltip chi tiết khi hover: Thời gian chính xác, giá trị, trạng thái so với ngưỡng.
- Xuất dữ liệu biểu đồ: PNG, SVG, CSV.

**Bộ lọc & Tìm kiếm**

- Lọc theo Xí nghiệp quản lý.
- Lọc theo Cụm công trình.
- Lọc theo Trạm quan trắc.
- Lọc theo Khoảng thời gian: Ngày / Tuần / Tháng / Năm / Tùy chỉnh.
- Lọc theo Trạng thái trạm: Online / Offline / Cảnh báo.

**1.4.2.3. Hệ thống cảnh báo ngưỡng**

Hệ thống tự động theo dõi dữ liệu thủy văn theo thời gian thực và phát cảnh báo đa kênh khi các thông số đo vượt ngưỡng an toàn đã được cấu hình sẵn cho từng công trình.

**a) Cấu hình Ngưỡng cảnh báo**

- Mỗi công trình cấu hình ngưỡng riêng cho từng thông số (H_TL, H_HL, Q, Lượng mưa).
- Ba mức ngưỡng:
  - Bình thường: Dưới ngưỡng cảnh báo – hiển thị màu xanh.
  - Cảnh báo (Warning): Giá trị vượt mức ngưỡng 1 – hiển thị màu vàng, gửi thông báo.
  - Nguy hiểm (Critical): Giá trị vượt mức ngưỡng 2 – hiển thị màu đỏ, gửi cảnh báo khẩn cấp.
- Cấu hình ngưỡng dạng: Lớn hơn (\>), Nhỏ hơn (\<), Nằm ngoài khoảng \[min, max\], Tốc độ thay đổi (delta/giờ).
- Thời gian trễ (Delay): Cảnh báo chỉ kích hoạt khi điều kiện duy trì liên tục trong X phút (tránh cảnh báo nhầm do nhiễu sensor).

**b) Kênh phát cảnh báo**

| Kênh cảnh báo                  | Mức kích hoạt       | Thông tin trong cảnh báo                                   | Ghi chú                                       |
| ------------------------------ | ------------------- | ---------------------------------------------------------- | --------------------------------------------- |
| Dashboard Web (Banner đỏ/vàng) | Warning \+ Critical | Tên công trình, thông số, giá trị, ngưỡng, thời gian       | Hiển thị ngay, yêu cầu xác nhận đã đọc        |
| Email tự động                  | Warning \+ Critical | Đầy đủ thông tin \+ link trực tiếp đến màn hình công trình | Danh sách email nhận cấu hình theo công trình |
| SMS (tùy chọn)                 | Critical only       | Tin nhắn ngắn gọn: Tên CT \+ Thông số \+ Giá trị           | Tích hợp SMS Gateway (ESMS/Twilio)            |
| Thông báo In-app (Web Push)    | Warning \+ Critical | Notification popup trên trình duyệt                        | Yêu cầu user cấp quyền browser notification   |

**c) Quản lý lịch sử cảnh báo**

- Lưu toàn bộ lịch sử cảnh báo: Công trình, Thông số, Giá trị tại thời điểm, Mức cảnh báo, Thời gian bắt đầu, Thời gian kết thúc (khi về ngưỡng bình thường), Người đã xác nhận, Ghi chú xử lý.
- Giao diện lịch sử cảnh báo: Lọc theo mức, công trình, khoảng thời gian.
- Phân loại cảnh báo: Đang xảy ra / Đã xử lý / Bỏ qua (False Alarm).
- Cán bộ kỹ thuật có thể ghi chú 'Biện pháp xử lý' và đóng cảnh báo.

**1.4.2.4. Quản trị bản đồ hệ thống (GIS)**

Cung cấp bản đồ số tương tác hiển thị toàn bộ hệ thống công trình thủy lợi theo tọa độ địa lý thực. Tích hợp dữ liệu GIS (KMZ/GeoJSON) để hiển thị bản đồ quy hoạch kênh mương, ranh giới lưu vực và thông tin vận hành theo thời gian thực.

**a) Nền bản đồ (Base Map)**

- Hỗ trợ chọn nền bản đồ: Google Maps (Satellite, Roadmap, Terrain), OpenStreetMap, Google Hybrid.
- Điều hướng bản đồ: Zoom in/out, Pan, Fullscreen, Reset về vị trí mặc định (tâm hệ thống công trình).
- Tìm kiếm địa điểm (Geocoding) để di chuyển bản đồ đến vị trí cụ thể.
- Lưu trữ vị trí/zoom mặc định theo tài khoản người dùng.

**b) Hiển thị Công trình trên bản đồ**

**Marker công trình**

- Mỗi công trình được hiển thị bằng icon marker có màu theo trạng thái vận hành:
- Xanh lá: Hoạt động bình thường.
- Vàng: Đang bảo trì hoặc có cảnh báo.
- Đỏ: Sự cố hoặc vượt ngưỡng nguy hiểm.
- Xám: Ngừng hoạt động theo mùa vụ.
- Icon marker phân biệt loại công trình: Trạm bơm (icon máy bơm), Cống (icon cửa cống), Kênh (đường nét).
- Khi Zoom gần (\>= level 14): Hiển thị tên công trình bên cạnh marker.

**Pop-up thông tin khi click marker**

- Tên công trình, Mã, Loại, Xí nghiệp quản lý.
- Trạng thái vận hành hiện tại (badge màu).
- Dữ liệu thủy văn mới nhất: Mực nước TL/HL, Lưu lượng, Thời gian cập nhật.
- Trạng thái các tổ máy (nếu là Trạm bơm): Đang chạy / Dừng.
- Nút 'Xem chi tiết' → Chuyển đến trang hồ sơ công trình đầy đủ.
- Nút 'Xem biểu đồ' → Mở biểu đồ thủy văn của trạm quan trắc gắn với công trình.

**c) Quản lý Lớp bản đồ (Layer Management)**

**Upload và quản lý Layer**

- Admin upload file GeoJSON hoặc KMZ để tạo layer mới (Giới hạn: 20MB/file).
- Hệ thống tự động parse và validate cú pháp GeoJSON/KMZ; hiển thị lỗi chi tiết nếu file không hợp lệ.
- Thiết lập thuộc tính mỗi layer: Tên hiển thị, Màu đường/vùng, Độ trong suốt (Opacity 0-100%), Loại geometry (Point/Line/Polygon).
- Thứ tự render layer (z-index): Kéo thả để điều chỉnh layer nào hiển thị trên cùng.

**Ví dụ các layer thường dùng**

| Tên Layer                   | Loại file            | Mô tả nội dung                        |
| --------------------------- | -------------------- | ------------------------------------- |
| Bản đồ kênh mương chính     | GeoJSON (LineString) | Hệ thống kênh tưới tiêu cấp 1, cấp 2  |
| Ranh giới lưu vực           | GeoJSON (Polygon)    | Đường ranh giới các lưu vực tưới tiêu |
| Bản đồ quy hoạch thủy lợi   | KMZ                  | Bản đồ quy hoạch đến năm 2030         |
| Vùng cảnh báo lũ            | GeoJSON (Polygon)    | Vùng có nguy cơ ngập lụt cao          |
| Ranh giới địa bàn Xí nghiệp | GeoJSON (Polygon)    | Phân vùng quản lý các Xí nghiệp       |

**Điều khiển hiển thị Layer**

- Panel bên trái danh sách tất cả layer với toggle Bật/Tắt (Checkbox).
- Slider điều chỉnh độ trong suốt cho từng layer.
- Nút 'Chỉ hiện layer này' để focus vào một layer.
- Bộ nhớ trạng thái layer hiển thị theo phiên đăng nhập (session).

**1.4.2.5. Nhập nhật ký vận hành**

Các Vận hành viên (Operator) tại các Xí nghiệp thực hiện nhập số liệu vận hành thực tế hàng ngày vào hệ thống, thay thế sổ nhật ký giấy truyền thống. Dữ liệu này là đầu vào chính để tổng hợp báo cáo và tính toán kết quả vụ mùa.

**a) Quy trình nhập Nhật ký**

1. Operator đăng nhập hệ thống → Chọn menu 'Nhật ký vận hành'.
2. Chọn Công trình (chỉ hiển thị các công trình thuộc Xí nghiệp của Operator).
3. Chọn ngày vận hành (mặc định là ngày hiện tại; cho phép nhập bù tối đa 3 ngày trước).
4. Hệ thống kiểm tra: Ngày đã có nhật ký chưa? Nếu chưa → Tạo mới. Nếu đã có → Cảnh báo, yêu cầu xác nhận Chỉnh sửa (Sửa nhật ký cũ cần Admin duyệt).
5. Nhập số liệu vào Form (xem chi tiết mục 2.5.3).
6. Lưu nháp (Draft) hoặc Gửi xác nhận (Submit).
7. Quản lý Xí nghiệp nhận thông báo, duyệt nhật ký (xem mục 2.5.5).

**b) Form nhật ký vận hành – Chi tiết các trường**

**Thông tin chung**

| Trường         | Kiểu     | Bắt buộc | Mô tả                                         |
| -------------- | -------- | -------- | --------------------------------------------- |
| Công trình     | Dropdown | Có       | Chỉ hiển thị CT thuộc Xí nghiệp của Operator  |
| Ngày vận hành  | Date     | Có       | Mặc định hôm nay; nhập bù tối đa 3 ngày trước |
| Ca vận hành    | Enum     | Có       | Ca ngày (6h-18h) / Ca đêm (18h-6h) / Cả ngày  |
| Người vận hành | Text     | Có       | Họ tên người trực tiếp vận hành               |
| Thời tiết      | Enum     | Không    | Nắng / Mưa nhỏ / Mưa vừa / Mưa to / Bão       |

**Số liệu vận hành máy bơm (Với mỗi tổ máy)**

| Trường                 | Kiểu    | Đơn vị | Bắt buộc    | Ghi chú                                |
| ---------------------- | ------- | ------ | ----------- | -------------------------------------- |
| Tổ máy số              | Integer |        | Có          | Từ 1 đến số lượng máy của công trình   |
| Trạng thái             | Enum    |        | Có          | Chạy / Dừng / Bảo trì / Sự cố          |
| Giờ bắt đầu chạy       | Time    | hh:mm  | Có khi Chạy |                                        |
| Giờ kết thúc chạy      | Time    | hh:mm  | Có khi Chạy | Validation: \> Giờ bắt đầu             |
| Số giờ chạy máy        | Decimal | Giờ    | Auto        | Tự động tính \= Giờ KT – Giờ BD        |
| Lưu lượng thực tế      | Decimal | m³/s   | Có khi Chạy | Nhập từ đồng hồ đo hoặc tính toán      |
| Lưu lượng bơm (kỳ này) | Decimal | m³     | Auto        | Tự tính \= Lưu lượng × Số giờ × 3600   |
| Điện năng tiêu thụ     | Decimal | kWh    | Không       | Nhập từ đồng hồ điện nếu có            |
| Ghi chú tổ máy         | Text    |        | Không       | Ghi nhận bất thường riêng của từng máy |

**Dữ liệu thủy văn tại thời điểm vận hành**

| Trường                      | Kiểu    | Đơn vị | Bắt buộc | Ghi chú                       |
| --------------------------- | ------- | ------ | -------- | ----------------------------- |
| Mực nước thượng lưu đầu ca  | Decimal | m      | Có       | Đo thực tế hoặc lấy từ sensor |
| Mực nước thượng lưu cuối ca | Decimal | m      | Có       |                               |
| Mực nước hạ lưu đầu ca      | Decimal | m      | Không    |                               |
| Mực nước hạ lưu cuối ca     | Decimal | m      | Không    |                               |
| Lượng mưa trong ca          | Decimal | mm     | Không    | Đo từ vũ lượng kế tại chỗ     |

**Ghi nhận Sự cố trong ca**

- Checkbox 'Có sự cố trong ca'. Nếu tích → Hiển thị form nhập sự cố:
  - Loại sự cố: Dropdown: Điện / Cơ khí / Thủy công / Thiên tai / Khác.
  - Mô tả sự cố: Textarea chi tiết diễn biến.
  - Mức độ: Enum: Nhẹ (vẫn vận hành được) / Trung bình (giảm hiệu suất) / Nặng (phải ngừng máy).
  - Biện pháp xử lý tạm thời: Textarea.
  - Thời điểm xảy ra: DateTime.
  - Ảnh chụp hiện trường: Upload ảnh (tối đa 5 ảnh, mỗi ảnh max 5MB).
- Sự cố mức 'Nặng' tự động tạo phiếu sự cố (Issue Ticket) trong CN-02.7 và thông báo cho Kỹ thuật.

**c) Validation dữ liệu nhập**

| Trường            | Quy tắc kiểm tra                                        | Thông báo lỗi                               |
| ----------------- | ------------------------------------------------------- | ------------------------------------------- |
| Số giờ chạy máy   | 0 \< Số giờ \<= 24 (hoặc độ dài ca)                     | Số giờ chạy không hợp lệ                    |
| Lưu lượng thực tế | \> 0 và \<= Lưu lượng thiết kế × 1.2                    | Lưu lượng vượt 120% thiết kế – xác nhận lại |
| Mực nước          | Trong khoảng \[MN*tối_thiểu, MN_tối*đa\] của công trình | Mực nước ngoài dải vận hành – kiểm tra lại  |
| Ngày nhập bù      | \<= Ngày hiện tại, \>= Ngày hiện tại – 3                | Chỉ được nhập bù tối đa 3 ngày trước        |
| Giờ kết thúc      | \> Giờ bắt đầu                                          | Giờ kết thúc phải sau giờ bắt đầu           |

**d) Quy trình duyệt nhật ký**

| Trạng thái                    | Mô tả                                        | Người thực hiện | Hành động tiếp theo                         |
| ----------------------------- | -------------------------------------------- | --------------- | ------------------------------------------- |
| Nháp (Draft)                  | Operator đang nhập, chưa gửi                 | Operator        | Tiếp tục chỉnh sửa hoặc Gửi                 |
| Chờ duyệt (Pending)           | Operator đã Gửi, chờ Quản lý Xí nghiệp duyệt | Operator        | Không sửa được; chỉ Quản lý duyệt/từ chối   |
| Đã duyệt (Approved)           | Nhật ký được xác nhận, đưa vào tổng hợp      | Quản lý XN      | Dữ liệu chính thức; cần Admin để sửa        |
| Từ chối (Rejected)            | Quản lý XN từ chối, kèm lý do                | Quản lý XN      | Operator chỉnh sửa và gửi lại               |
| Yêu cầu sửa (Revision Needed) | Cần bổ sung thêm thông tin                   | Quản lý XN      | Operator bổ sung, không cần tạo nhật ký mới |

**1.4.2.6. Tổng hợp và xuất báo cáo**

Hệ thống tự động tổng hợp dữ liệu từ nhật ký vận hành và số liệu thủy văn để tạo các báo cáo định kỳ và đột xuất theo mẫu chuẩn của Công ty. Xử lý bất đồng bộ (Async Job) đảm bảo không gây chậm hệ thống khi xuất báo cáo lớn.

**a) Danh mục báo cáo**

| Mã BC | Tên báo cáo                          | Kỳ báo cáo | Đầu vào                                | Định dạng  |
| ----- | ------------------------------------ | ---------- | -------------------------------------- | ---------- |
| BC-01 | Báo cáo vận hành ngày                | Ngày       | Nhật ký vận hành ngày                  | PDF, Excel |
| BC-02 | Báo cáo tổng hợp tuần                | Tuần       | Tổng hợp nhật ký 7 ngày                | PDF, Excel |
| BC-03 | Báo cáo tổng hợp tháng               | Tháng      | Tổng hợp nhật ký cả tháng              | PDF, Excel |
| BC-04 | Báo cáo kết quả vụ tưới/tiêu         | Vụ mùa     | Tổng hợp theo kỳ tưới tiêu             | PDF, Excel |
| BC-05 | Báo cáo thủy văn tổng hợp            | Tháng      | Dữ liệu từ API quan trắc               | PDF, Excel |
| BC-06 | Báo cáo cảnh báo & sự cố             | Tháng      | Lịch sử cảnh báo \+ Issue Ticket       | PDF        |
| BC-07 | Báo cáo so sánh kế hoạch – thực hiện | Vụ/Năm     | Dữ liệu thực tế vs. kế hoạch được nhập | PDF, Excel |
| BC-08 | Báo cáo tiêu thụ điện năng           | Tháng      | Điện năng tiêu thụ từ nhật ký          | PDF, Excel |

**b) Tổng hợp tự động**

**Tổng hợp theo kỳ**

- Hệ thống cron job chạy tự động: Tổng hợp ngày (00:05 hàng ngày), Tổng hợp tuần (Thứ 2 00:10), Tổng hợp tháng (Ngày 1 00:15).
- Các chỉ tiêu tự động tính toán:
  - Tổng giờ chạy máy: Σ Số giờ chạy của tất cả tổ máy trong kỳ.
  - Tổng lưu lượng bơm: Σ Lưu lượng (m³) qua tất cả công trình trong kỳ.
  - Tổng điện năng tiêu thụ: Σ kWh tiêu thụ trong kỳ.
  - Diện tích tưới/tiêu đạt được: Tính theo lưu lượng thực tế và tiêu chuẩn tưới.
  - Tỷ lệ hoàn thành kế hoạch: So sánh thực tế / Kế hoạch × 100%.
  - Số ngày/ca có sự cố: Đếm từ nhật ký sự cố.

**c) Giao diện tạo báo cáo thủ công**

- Người dùng chọn: Loại báo cáo, Xí nghiệp / Công trình (một hoặc nhiều), Kỳ báo cáo (Date range), Định dạng xuất (PDF / Excel).
- Nhấn 'Tạo báo cáo' → Hệ thống gửi job vào hàng đợi (Queue).
- Hiển thị thông báo 'Báo cáo đang được tạo, bạn sẽ nhận thông báo khi hoàn thành'.
- Khi job hoàn thành: Hiển thị thông báo In-app \+ Gửi email kèm link tải xuống (link hợp lệ 24 giờ).
- Lịch sử báo cáo đã tạo: Lưu trữ 90 ngày để tải lại khi cần.

**d) Async Job Queue – Xử lý bất đồng bộ**

**Kiến trúc**

- Sử dụng Message Queue (Redis Queue / RabbitMQ) để xử lý tác vụ tạo báo cáo nặng.
- Worker process riêng biệt (tách khỏi web server) xử lý các job trong queue.
- Số worker có thể scale theo tải; mặc định 2 worker.

**Trạng thái Job**

| Trạng thái | Mô tả                                      | Hành động hệ thống                   |
| ---------- | ------------------------------------------ | ------------------------------------ |
| Pending    | Job đã được thêm vào queue, chờ xử lý      | Hiển thị spinner, cho phép hủy       |
| Processing | Worker đang xử lý job                      | Hiển thị thanh tiến trình % (nếu có) |
| Completed  | Tạo báo cáo thành công                     | Gửi thông báo \+ email \+ link tải   |
| Failed     | Xử lý thất bại sau 3 lần retry             | Thông báo lỗi \+ gợi ý thử lại       |
| Cancelled  | Người dùng hủy khi đang Pending/Processing | Xóa job khỏi queue                   |

**e) Mẫu báo cáo chuẩn công ty**

- Tất cả báo cáo PDF/Excel sử dụng template đã được thiết kế sẵn theo bộ nhận diện thương hiệu:
  - Header: Logo Công ty, Tên báo cáo, Kỳ báo cáo, Đơn vị lập.
  - Footer: Số trang (Trang X/Y), Ngày xuất báo cáo, Người xuất báo cáo.
  - Chữ ký điện tử: Vị trí dành cho Lãnh đạo ký duyệt (tùy chọn).
- Admin có thể upload template Excel (.xlsx) hoặc template PDF (.docx → PDF) mới khi có thay đổi mẫu.

**1.4.2.7. Quản lý sự cố công trình**

Hệ thống quản lý toàn bộ vòng đời của sự cố công trình từ khi ghi nhận đến khi khắc phục hoàn toàn, thay thế quy trình báo cáo sự cố bằng giấy tờ và email rời rạc.

**a) Tạo phiếu sự cố**

Phiếu sự cố được tạo bởi:

- Tự động: Khi Operator tích 'Sự cố mức Nặng' trong nhật ký vận hành.
- Thủ công: Operator hoặc Kỹ thuật tạo mới trực tiếp từ menu 'Sự cố'.
- Từ cảnh báo: Từ màn hình Alert, người dùng nhấn 'Tạo phiếu sự cố' để xử lý tiếp.

**Thông tin phiếu sự cố**

| Trường                  | Kiểu              | Bắt buộc | Mô tả                                                    |
| ----------------------- | ----------------- | -------- | -------------------------------------------------------- |
| Mã phiếu                | Auto              | Auto     | Tự tăng, VD: SC-2026-0001                                |
| Công trình liên quan    | Dropdown          | Có       |                                                          |
| Tiêu đề sự cố           | Text              | Có       | Mô tả ngắn gọn trong 200 ký tự                           |
| Loại sự cố              | Enum              | Có       | Điện / Cơ khí / Thủy công / Thiên tai / An ninh / Khác   |
| Mức độ nghiêm trọng     | Enum              | Có       | Nghiêm trọng (Critical) / Cao (High) / Trung bình / Thấp |
| Mô tả chi tiết          | Textarea          | Có       | Diễn biến, nguyên nhân sơ bộ, ảnh hưởng                  |
| Thời điểm phát hiện     | DateTime          | Có       |                                                          |
| Người phát hiện         | Text              | Có       |                                                          |
| Ảnh / Video hiện trường | Upload            | Không    | Tối đa 10 ảnh, 2 video (mỗi video max 100MB)             |
| Phân công xử lý         | User multi-select | Không    | Giao việc cho Kỹ thuật viên cụ thể                       |
| Hạn xử lý               | DateTime          | Không    |                                                          |

**b) Quy trình xử lý sự cố**

| Trạng thái                    | Màu       | Mô tả                                        | Người chuyển trạng thái |
| ----------------------------- | --------- | -------------------------------------------- | ----------------------- |
| Mới (New)                     | Đỏ đậm    | Phiếu mới tạo, chưa phân công                | Hệ thống/Người tạo      |
| Đang xử lý (In Progress)      | Cam       | Đã phân công, kỹ thuật đang khắc phục        | Kỹ thuật được phân công |
| Chờ vật tư (On Hold)          | Vàng      | Dừng chờ phụ tùng, vật tư                    | Kỹ thuật                |
| Đã khắc phục tạm (Workaround) | Xanh nhạt | Giải pháp tạm thời đã áp dụng                | Kỹ thuật                |
| Chờ nghiệm thu (Testing)      | Tím       | Sửa xong, chờ Quản lý nghiệm thu             | Kỹ thuật                |
| Đã đóng (Closed)              | Xanh      | Sự cố đã được xử lý hoàn toàn, nghiệm thu OK | Quản lý, Admin          |
| Hủy (Cancelled)               | Xám       | Phiếu nhầm hoặc không cần xử lý              | Admin                   |

**c) Nhật ký xử lý sự cố**

- Mỗi phiếu sự cố có tab 'Nhật ký' ghi lại toàn bộ diễn biến xử lý.
- Kỹ thuật thêm cập nhật tiến độ, ảnh/video xử lý, đổi trạng thái.
- Tất cả thay đổi trạng thái tự động ghi vào nhật ký với timestamp và tên người thực hiện.
- Gửi email thông báo tự động cho các bên liên quan khi trạng thái thay đổi.

**1.4.2.8. Dashboard vận hành tổng thể**

Dashboard là màn hình tổng quan hiển thị toàn cảnh tình trạng vận hành hệ thống theo thời gian thực, phục vụ lãnh đạo và cán bộ kỹ thuật theo dõi nhanh mà không cần truy cập từng mục riêng lẻ.

**a) Các thành phần Dashboard**

**Hàng 1 – KPI Cards (số liệu tổng hợp nhanh)**

| Card                          | Giá trị hiển thị                                | Màu trạng thái   |
| ----------------------------- | ----------------------------------------------- | ---------------- |
| Tổng công trình               | Số công trình đang hoạt động / Tổng số          | Xanh nếu \>= 90% |
| Đang vận hành                 | Số trạm bơm/cống đang hoạt động ngay lúc này    | Xanh / Vàng / Đỏ |
| Cảnh báo thủy văn đang xảy ra | Số cảnh báo hiện đang ở mức Warning \+ Critical | Vàng/Đỏ nếu \> 0 |
| Sự cố chưa xử lý              | Số phiếu sự cố ở trạng thái New \+ In Progress  | Đỏ nếu \> 0      |
| Nhật ký hôm nay               | Số nhật ký đã nộp / Tổng số cần nộp hôm nay     | Xanh nếu 100%    |
| Lưu lượng bơm hôm nay         | Tổng m³ đã bơm trong ngày hiện tại              | Thông tin        |

**Hàng 2 – Bản đồ GIS thu nhỏ**

- Bản đồ tổng quan toàn hệ thống công trình với marker màu theo trạng thái.
- Click marker → Popup thông tin nhanh \+ link đến chi tiết.
- Nút 'Mở bản đồ đầy đủ' để chuyển sang giao diện GIS đầy đủ.

**Hàng 3 – Biểu đồ & Bảng**

- Biểu đồ cột: Lưu lượng bơm 7 ngày gần nhất (so sánh các Xí nghiệp).
- Biểu đồ đường: Mực nước thủy văn 24 giờ qua của các trạm đang cảnh báo.
- Bảng danh sách 5 sự cố mới nhất chưa xử lý (click để xem chi tiết).
- Bảng danh sách công trình đang có nhật ký chưa duyệt.

**PHỤ LỤC A: PHÂN QUYỀN THEO VAI TRÒ (RBAC)**

| Chức năng                  | Admin | Quản lý XN      | Kỹ thuật | Operator        |
| -------------------------- | ----- | --------------- | -------- | --------------- |
| Xem danh mục công trình    | ✔     | ✔               | ✔        | ✔ (chỉ XN mình) |
| Thêm/Sửa hồ sơ công trình  | ✔     | ✘               | ✔        | ✘               |
| Upload tài liệu công trình | ✔     | ✔               | ✔        | ✔ (chỉ XN mình) |
| Xem dữ liệu thủy văn       | ✔     | ✔               | ✔        | ✔               |
| Cấu hình API nguồn dữ liệu | ✔     | ✘               | ✘        | ✘               |
| Cấu hình ngưỡng cảnh báo   | ✔     | ✔               | ✔        | ✘               |
| Xem lịch sử cảnh báo       | ✔     | ✔               | ✔        | ✔               |
| Đóng/Xử lý cảnh báo        | ✔     | ✔               | ✔        | ✘               |
| Xem/Tương tác bản đồ GIS   | ✔     | ✔               | ✔        | ✔               |
| Upload/Quản lý layer GIS   | ✔     | ✘               | ✔        | ✘               |
| Nhập nhật ký vận hành      | ✔     | ✔               | ✔        | ✔ (chỉ XN mình) |
| Duyệt nhật ký vận hành     | ✔     | ✔               | ✘        | ✘               |
| Xem nhật ký của XN khác    | ✔     | ✘               | ✔        | ✘               |
| Tạo phiếu sự cố            | ✔     | ✔               | ✔        | ✔               |
| Phân công xử lý sự cố      | ✔     | ✔               | ✔        | ✘               |
| Đóng phiếu sự cố           | ✔     | ✔               | ✘        | ✘               |
| Xem báo cáo                | ✔     | ✔ (chỉ XN mình) | ✔        | ✔ (chỉ XN mình) |
| Tạo/Xuất báo cáo           | ✔     | ✔               | ✔        | ✘               |
| Cấu hình mẫu báo cáo       | ✔     | ✘               | ✘        | ✘               |

**PHỤ LỤC B: YÊU CẦU PHI CHỨC NĂNG**

| Hạng mục                  | Yêu cầu                                           | Tiêu chí đo lường                                  |
| ------------------------- | ------------------------------------------------- | -------------------------------------------------- |
| Hiệu năng – Polling API   | Thu thập dữ liệu thủy văn đúng chu kỳ             | Sai lệch so với chu kỳ cấu hình \< 10%             |
| Hiệu năng – Dashboard     | Tải dashboard không quá 3 giây                    | P95 latency \< 3s với 50 users đồng thời           |
| Hiệu năng – Báo cáo PDF   | Tạo báo cáo 1 tháng / 1 XN không quá 60 giây      | Async job completed \< 60s                         |
| Độ tin cậy – Polling      | Tự động retry khi API nguồn lỗi                   | Retry 3 lần; alert sau 3 lần thất bại liên tiếp    |
| Lưu trữ – Time-series     | Dữ liệu thủy văn chi tiết lưu 5 năm               | Không mất dữ liệu; backup hàng ngày                |
| Bảo mật – Dữ liệu API Key | Mã hóa thông tin xác thực API nguồn               | AES-256, không hiển thị plaintext trong UI         |
| Phân quyền dữ liệu        | Operator chỉ xem/nhập dữ liệu của XN mình         | Unit test \+ integration test 100% pass            |
| Audit Log                 | Ghi log mọi thao tác tạo/sửa/xóa dữ liệu vận hành | Log đầy đủ: user, timestamp, action, old/new value |
| Khả dụng (Availability)   | Uptime \>= 99.5% trong giờ hành chính             | Monitoring alert khi downtime \> 15 phút           |

**1.4.3. Hệ thống quản lý nhân sự**

Mô-đun 3 số hóa toàn bộ nghiệp vụ quản lý nhân sự nội bộ của Công ty TNHH Thủy lợi Sông Nhuệ, từ cơ cấu tổ chức, hồ sơ cá nhân cán bộ nhân viên, lý lịch chuyên môn, lịch sử công tác đến danh bạ nội bộ và thống kê nhân sự. Mô-đun tuân thủ các quy định về bảo vệ dữ liệu cá nhân theo Nghị định 13/2023/NĐ-CP.

| Thông tin        | Chi tiết                                                                    |
| ---------------- | --------------------------------------------------------------------------- |
| Tên mô-đun       | Hệ thống Quản lý Nhân sự (HRM)                                              |
| Mã mô-đun        | MOD-03                                                                      |
| Phiên bản        | 1.0                                                                         |
| Nhóm người dùng  | Admin HR, Lãnh đạo, Quản lý phòng ban/Xí nghiệp, Nhân viên (Viewer)         |
| Tích hợp         | MOD-05 (Tài khoản hệ thống), MOD-02 (Xí nghiệp), MOD-04 (Văn bản điều hành) |
| Dữ liệu nhạy cảm | CMND/CCCD, Lương, Hợp đồng lao động – Phân quyền và mã hóa nghiêm ngặt      |
| Tuân thủ pháp lý | Nghị định 13/2023/NĐ-CP; Bộ luật Lao động 2019; Luật Lưu trữ 2011           |
| Trạng thái       | Đang xây dựng đặc tả                                                        |

**Mục tiêu chức năng**

- Xây dựng cơ sở dữ liệu nhân sự tập trung, thay thế hồ sơ giấy và file Excel phân tán.
- Trực quan hóa cơ cấu tổ chức dạng sơ đồ cây, cập nhật linh hoạt khi có thay đổi nhân sự.
- Quản lý toàn diện hồ sơ cá nhân, lý lịch chuyên môn và lịch sử sự kiện nhân sự theo dòng thời gian.
- Cung cấp danh bạ nội bộ cho toàn thể nhân viên tra cứu thông tin liên lạc của đồng nghiệp.
- Tự động hóa thống kê nhân sự và xuất báo cáo phục vụ quản lý nội bộ và báo cáo cơ quan cấp trên.
- Số hóa quy trình đăng ký và phê duyệt nghỉ phép, theo dõi số dư phép năm tự động.

**Danh sách chức năng tổng hợp**

| Mã CN   | Tên chức năng                        | Ưu tiên    | Người dùng chính         |
| ------- | ------------------------------------ | ---------- | ------------------------ |
| CN-03.1 | Quản lý Sơ đồ Tổ chức                | Cao        | Admin HR, Lãnh đạo       |
| CN-03.2 | Quản lý Hồ sơ Cán bộ Nhân viên       | Cao        | Admin HR, Quản lý đơn vị |
| CN-03.3 | Quản lý Lý lịch Cá nhân & Chuyên môn | Cao        | Admin HR                 |
| CN-03.4 | Quản lý Lịch sử Công tác (Timeline)  | Trung bình | Admin HR                 |
| CN-03.5 | Quản lý Tài liệu Hồ sơ Nhân viên     | Cao        | Admin HR                 |
| CN-03.6 | Danh bạ Nội bộ                       | Cao        | Tất cả nhân viên         |
| CN-03.7 | Thống kê & Báo cáo Nhân sự           | Trung bình | Admin HR, Lãnh đạo       |
| CN-03.8 | Quản lý Nghỉ phép (Leave Management) | Trung bình | Nhân viên, Quản lý       |

**1.4.3.1. Quản lý sơ đồ tổ chức**

Chức năng xây dựng và quản lý sơ đồ tổ chức toàn Công ty dạng cây phân cấp (Org Chart). Sơ đồ phản ánh đúng cơ cấu thực tế và cập nhật ngay khi có thay đổi nhân sự hoặc tổ chức bộ máy.

**a. Cấu trúc phân cấp tổ chức**

Hệ thống hỗ trợ cấu trúc tổ chức tối thiểu 5 cấp, linh hoạt mở rộng thêm cấp nếu cần:

| Cấp   | Đơn vị điển hình      | Ví dụ thực tế                           | Người đứng đầu             |
| ----- | --------------------- | --------------------------------------- | -------------------------- |
| Cấp 1 | Công ty               | Công ty TNHH Thủy lợi Sông Nhuệ         | Giám đốc Công ty           |
| Cấp 2 | Khối chức năng        | Khối Văn phòng, Khối Sản xuất           | Phó Giám đốc phụ trách     |
| Cấp 3 | Phòng ban / Xí nghiệp | P. Hành chính, P. Kế hoạch, XN1, XN2... | Trưởng phòng / Giám đốc XN |
| Cấp 4 | Tổ / Đội / Cụm        | Tổ cơ điện, Cụm công trình Bắc          | Tổ trưởng / Đội trưởng     |
| Cấp 5 | Trạm / Vị trí         | Trạm bơm Cầu Bươu, Cống Liên Mạc        | Trạm trưởng (nếu có)       |

**b. Thông tin mỗi đơn vị tổ chức**

| Trường               | Kiểu             | Bắt buộc | Mô tả / Ràng buộc                                |
| -------------------- | ---------------- | -------- | ------------------------------------------------ |
| Mã đơn vị            | Text (20 ký tự)  | Có       | Duy nhất toàn hệ thống, VD: PB-HC, XN-01         |
| Tên đầy đủ           | Text (200 ký tự) | Có       | Tên chính thức theo quyết định thành lập         |
| Tên viết tắt         | Text (20 ký tự)  | Có       | VD: P.HC, XN1, BGĐ                               |
| Đơn vị cha           | Dropdown (FK)    | Không    | Để trống nếu là cấp cao nhất (Công ty)           |
| Loại đơn vị          | Enum             | Có       | Phòng ban / Xí nghiệp / Tổ/Đội / Ban / Khác      |
| Người đứng đầu       | Dropdown (FK-NV) | Không    | Liên kết hồ sơ NV; 1 người chỉ đứng đầu 1 đơn vị |
| Người phụ trách thay | Dropdown (FK-NV) | Không    | Phó phụ trách khi người đứng đầu vắng            |
| Địa chỉ văn phòng    | Text (500 ký tự) | Không    |                                                  |
| Số điện thoại đơn vị | Text (15 ký tự)  | Không    | Máy bàn / Hotline của phòng ban                  |
| Email đơn vị         | Email            | Không    | Email chung của phòng/xí nghiệp                  |
| Ngày thành lập       | Date             | Không    |                                                  |
| Chức năng nhiệm vụ   | Textarea         | Không    | Mô tả nhiệm vụ chính của đơn vị                  |
| Trạng thái           | Enum             | Có       | Đang hoạt động / Đã giải thể / Tạm dừng          |
| Thứ tự hiển thị      | Integer          | Có       | Thứ tự trong sơ đồ cùng cấp cha                  |

**c. Giao diện sơ đồ tổ chức**

**Chế độ xem Cây (Tree View)**

- Hiển thị dạng cây dọc hoặc cây ngang (toggle chuyển đổi); mỗi nút hiển thị: Tên đơn vị, tên viết tắt, tên người đứng đầu, ảnh đại diện, số lượng nhân sự.
- Thu gọn / Mở rộng từng nhánh bằng click; Zoom in/out và Pan điều hướng sơ đồ lớn.
- Nút 'Fit to screen' để đưa toàn bộ sơ đồ vừa màn hình.

**Chế độ xem Danh sách (List View)**

- Bảng phẳng với cột: Cấp, Đơn vị cha, Tên đơn vị, Người đứng đầu, Số nhân sự, Trạng thái.
- Lọc theo cấp tổ chức, loại đơn vị, trạng thái. Tìm kiếm nhanh theo tên hoặc mã đơn vị.

**Thao tác quản lý**

- Thêm đơn vị mới: Điền form → Chọn đơn vị cha → Lưu → Cây tự cập nhật ngay.
- Di chuyển đơn vị sang nhánh khác (thay đổi đơn vị cha) bằng kéo thả.
- Giải thể đơn vị: Chỉ được phép khi đơn vị không còn nhân viên; xác nhận 2 bước.

**d. Xuất sơ đồ tổ chức**

- Xuất ảnh PNG/SVG độ phân giải cao để in ấn hoặc trình chiếu.
- Xuất file PDF khổ A3 ngang (Landscape).
- Xuất danh sách đơn vị dạng Excel để tham chiếu và lưu hồ sơ.

**1.4.3.2. Quản lý hồ sơ cán bộ nhân viên**

Mỗi cán bộ nhân viên có một hồ sơ số tập trung gồm đầy đủ thông tin cá nhân, thông tin công tác, chuyên môn và tài liệu đính kèm. Hồ sơ được phân quyền truy cập chặt chẽ để bảo vệ dữ liệu cá nhân nhạy cảm theo quy định pháp luật.

**a. Thông tin cá nhân**

| Trường                           | Kiểu dữ liệu     | Bắt buộc | Ghi chú / Ràng buộc                                                    |
| -------------------------------- | ---------------- | -------- | ---------------------------------------------------------------------- |
| Mã nhân viên                     | Text (20 ký tự)  | Có       | Tự sinh hoặc nhập thủ công; duy nhất; VD: NV-2019-001                  |
| Họ và tên đầy đủ                 | Text (100 ký tự) | Có       | Viết hoa đúng chuẩn tên người Việt Nam                                 |
| Họ tên không dấu                 | Text (100 ký tự) | Auto     | Tự động chuyển đổi, dùng cho Full-text search                          |
| Ảnh đại diện                     | Image            | Không    | JPG/PNG; tự động crop vuông 300×300px; hiển thị trong sơ đồ và danh bạ |
| Ngày sinh                        | Date             | Có       | Validation: 18 ≤ tuổi ≤ 70                                             |
| Giới tính                        | Enum             | Có       | Nam / Nữ / Khác                                                        |
| Dân tộc                          | Text             | Không    |                                                                        |
| Quê quán                         | Text (200 ký tự) | Không    | Tỉnh/Thành phố quê gốc                                                 |
| Số CMND/CCCD                     | Text (12 ký tự)  | Có       | 🔒 Nhạy cảm – 9 hoặc 12 số; unique toàn hệ thống                       |
| Ngày cấp CMND/CCCD               | Date             | Có       | 🔒 Nhạy cảm                                                            |
| Nơi cấp CMND/CCCD                | Text (100 ký tự) | Có       | 🔒 Nhạy cảm                                                            |
| Địa chỉ thường trú               | Text (300 ký tự) | Có       |                                                                        |
| Địa chỉ tạm trú                  | Text (300 ký tự) | Không    |                                                                        |
| Số điện thoại cá nhân            | Text (15 ký tự)  | Có       | Số di động chính; hiển thị trong danh bạ nội bộ                        |
| Email nội bộ                     | Email            | Không    | Email tên miền công ty nếu có; hiển thị trong danh bạ                  |
| Email cá nhân                    | Email            | Không    |                                                                        |
| Tình trạng hôn nhân              | Enum             | Không    | Độc thân / Đã kết hôn / Ly hôn / Góa                                   |
| Thông tin người liên hệ khẩn cấp | Textarea         | Không    | Họ tên, quan hệ, SĐT người liên hệ khi khẩn cấp                        |

**b. Thông tin công tác**

| Trường                    | Kiểu dữ liệu          | Bắt buộc | Ghi chú / Ràng buộc                                                                 |
| ------------------------- | --------------------- | -------- | ----------------------------------------------------------------------------------- |
| Đơn vị công tác           | Dropdown (FK đơn vị)  | Có       | Liên kết đến Sơ đồ tổ chức (MOD-03.1)                                               |
| Chức vụ                   | Dropdown (FK chức vụ) | Có       | Lấy từ danh mục Chức vụ chuẩn hóa                                                   |
| Chức danh nghề nghiệp     | Text (100 ký tự)      | Không    | VD: Kỹ sư thủy lợi, Kế toán viên                                                    |
| Ngày vào làm              | Date                  | Có       | Ngày bắt đầu làm việc tại Công ty                                                   |
| Loại hợp đồng lao động    | Enum                  | Có       | Thử việc / HĐLĐ xác định thời hạn / HĐLĐ không thời hạn / Cộng tác viên             |
| Ngày ký hợp đồng hiện tại | Date                  | Có       |                                                                                     |
| Ngày hết hạn hợp đồng     | Date                  | Không    | Để trống nếu HĐLĐ không xác định thời hạn                                           |
| Mức lương cơ bản          | Decimal (15,2)        | Không    | 🔒 Nhạy cảm – Đơn vị: đồng VND                                                      |
| Hệ số lương               | Decimal (4,2)         | Không    | 🔒 Nhạy cảm                                                                         |
| Số tài khoản ngân hàng    | Text (20 ký tự)       | Không    | 🔒 Nhạy cảm – Tài khoản nhận lương                                                  |
| Ngân hàng                 | Text (100 ký tự)      | Không    | 🔒 Nhạy cảm                                                                         |
| Mã số thuế cá nhân        | Text (10-13 ký tự)    | Không    | 🔒 Nhạy cảm                                                                         |
| Số sổ BHXH                | Text (10 ký tự)       | Không    | 🔒 Nhạy cảm                                                                         |
| Trạng thái làm việc       | Enum                  | Có       | Đang làm / Thử việc / Nghỉ thai sản / Nghỉ không lương / Đã nghỉ việc / Đã nghỉ hưu |
| Ngày nghỉ việc            | Date                  | Không    | Bắt buộc khi Trạng thái \= 'Đã nghỉ việc'                                           |
| Lý do nghỉ việc           | Enum \+ Text          | Không    | Tự nguyện / Hết hợp đồng / Kỷ luật / Nghỉ hưu / Khác (kèm ghi chú)                  |

**c. Danh mục chức vụ chuẩn hóa**

Admin HR quản lý danh mục chức vụ để tránh trùng lặp tên gọi và đảm bảo nhất quán dữ liệu thống kê:

| Trường          | Mô tả                                                                              |
| --------------- | ---------------------------------------------------------------------------------- |
| Mã chức vụ      | Duy nhất; VD: GD, PGD, TP, PP, NV, CN                                              |
| Tên chức vụ     | VD: Giám đốc, Phó Giám đốc, Trưởng phòng, Phó phòng, Nhân viên, Công nhân          |
| Nhóm chức vụ    | Lãnh đạo cấp Công ty / Lãnh đạo cấp Phòng/XN / Chuyên viên / Nhân viên / Công nhân |
| Mô tả           | Tóm tắt trách nhiệm chính của chức vụ                                              |
| Thứ tự xếp hạng | Dùng để sắp xếp trong danh sách và báo cáo                                         |

**1.4.3.4. Quản lý lý lịch cá nhân & chuyên môn**

Lý lịch chuyên môn ghi nhận chi tiết quá trình đào tạo, bằng cấp, chứng chỉ nghề nghiệp và trình độ ngoại ngữ/tin học của từng cán bộ nhân viên. Đây là cơ sở để lập kế hoạch đào tạo bồi dưỡng, điều động nhân sự và xét thi đua khen thưởng.

**a. Trình độ học vấn và đào tạo**

**Thông tin học vấn cao nhất**

| Trường                    | Kiểu    | Bắt buộc | Mô tả                                                            |
| ------------------------- | ------- | -------- | ---------------------------------------------------------------- |
| Trình độ học vấn cao nhất | Enum    | Có       | THCS / THPT / Trung cấp / Cao đẳng / Đại học / Thạc sĩ / Tiến sĩ |
| Chuyên ngành đào tạo      | Text    | Có       | VD: Thủy lợi, Điện kỹ thuật, Kế toán, Quản trị kinh doanh        |
| Hình thức đào tạo         | Enum    | Có       | Chính quy / Tại chức / Liên thông / Từ xa                        |
| Trường đào tạo            | Text    | Có       | Tên trường đại học/cao đẳng/trung cấp                            |
| Năm tốt nghiệp            | Integer | Có       |                                                                  |
| Xếp loại tốt nghiệp       | Enum    | Không    | Xuất sắc / Giỏi / Khá / Trung bình khá / Trung bình              |
| Số hiệu bằng              | Text    | Không    | Số hiệu ghi trên văn bằng                                        |

**Lịch sử đào tạo bổ sung (nhiều bản ghi)**

- Ghi nhiều khóa đào tạo/bồi dưỡng; mỗi bản ghi gồm: Tên khóa học, Đơn vị tổ chức, Từ ngày – đến ngày, Hình thức (Trong nước / Nước ngoài / Trực tuyến), Chứng chỉ đạt được, File đính kèm (PDF/JPG).

**b. Bằng cấp & Chứng chỉ chuyên môn**

| Loại                       | Ví dụ điển hình                                | Thông tin ghi nhận                                            |
| -------------------------- | ---------------------------------------------- | ------------------------------------------------------------- |
| Bằng cấp chính quy         | Bằng Đại học Thủy lợi                          | Loại bằng, Trường, Năm, Xếp loại, Số hiệu bằng, File đính kèm |
| Chứng chỉ hành nghề        | Chứng chỉ hành nghề xây dựng hạng II           | Số chứng chỉ, Cơ quan cấp, Ngày cấp, Ngày hết hiệu lực, File  |
| Chứng chỉ kỹ năng nghề     | Vận hành máy bơm ly tâm bậc 4                  | Cấp độ (bậc 1-5), Cơ quan cấp, Năm cấp, File                  |
| Chứng chỉ bồi dưỡng        | Quản lý nhà nước ngạch chuyên viên             | Tên khóa, Đơn vị cấp, Năm, File                               |
| Chứng chỉ ngoại ngữ        | IELTS 6.0, TOEIC 750, Chứng chỉ B1             | Loại chứng chỉ, Điểm số, Năm thi, Hạn hiệu lực, File          |
| Chứng chỉ tin học          | MOS Word/Excel, Chứng chỉ IC3                  | Loại, Cơ quan cấp, Năm, File                                  |
| Chứng chỉ an toàn lao động | Huấn luyện ATLĐ nhóm 3 – Kỹ thuật an toàn điện | Nhóm an toàn, Đơn vị cấp, Ngày cấp, Ngày hết hiệu lực, File   |

**c. Trình độ ngoại ngữ & tin học**

| Thông tin                       | Các giá trị / Mô tả                                                                          |
| ------------------------------- | -------------------------------------------------------------------------------------------- |
| Ngoại ngữ chính                 | Tiếng Anh / Tiếng Nga / Tiếng Pháp / Tiếng Trung / Tiếng Nhật / Khác                         |
| Trình độ ngoại ngữ              | Sơ cấp (A1/A2) / Trung cấp (B1/B2) / Cao cấp (C1/C2) / Bản ngữ; hoặc điểm IELTS/TOEIC cụ thể |
| Ngoại ngữ thứ hai (nếu có)      | Tương tự ngoại ngữ chính                                                                     |
| Trình độ tin học                | Cơ bản / Trung cấp / Nâng cao / Chuyên sâu theo lĩnh vực                                     |
| Phần mềm chuyên dụng thành thạo | Liệt kê tên phần mềm: AutoCAD, HEC-RAS, MIKE, SAP kế toán, GIS...                            |

**1.4.3.4. Quản lý lịch sử công tác**

Lịch sử công tác ghi lại toàn bộ sự kiện nhân sự quan trọng trong suốt quá trình làm việc của từng cán bộ, hiển thị dạng dòng thời gian (Timeline). Đây là tài liệu căn cứ để xét thi đua, khen thưởng, thăng tiến và giải quyết các chế độ chính sách.

**a. Các loại sự kiện nhân sự**

| Loại sự kiện              | Thông tin ghi nhận                                              | Ai tạo   |
| ------------------------- | --------------------------------------------------------------- | -------- |
| Tuyển dụng / Tiếp nhận    | Ngày vào làm, Vị trí ban đầu, Loại HĐLĐ, Người ký quyết định    | Admin HR |
| Ký mới / Gia hạn hợp đồng | Số HĐ, Ngày ký, Loại HĐ, Thời hạn, Mức lương mới                | Admin HR |
| Điều động / Thuyên chuyển | Từ đơn vị – đến đơn vị, Chức vụ mới, Ngày hiệu lực, Số QĐ       | Admin HR |
| Bổ nhiệm / Miễn nhiệm     | Chức vụ mới/cũ, Ngày hiệu lực, Số QĐ bổ nhiệm                   | Admin HR |
| Nâng lương / Nâng bậc     | Hệ số lương cũ → mới, Ngày hiệu lực, Số QĐ nâng lương           | Admin HR |
| Khen thưởng               | Hình thức khen, Cấp khen thưởng, Lý do, Ngày khen, Số QĐ        | Admin HR |
| Kỷ luật                   | Hình thức kỷ luật, Lý do, Ngày quyết định, Thời hạn, Số QĐ      | Admin HR |
| Đào tạo / Bồi dưỡng       | Tên khóa, Nơi đào tạo, Thời gian, Kết quả, File chứng nhận      | Admin HR |
| Nghỉ phép dài hạn         | Loại nghỉ (thai sản/không lương/dưỡng bệnh), Từ ngày – đến ngày | Admin HR |
| Nghỉ việc / Nghỉ hưu      | Ngày nghỉ, Lý do, Chế độ được hưởng, Biên bản bàn giao          | Admin HR |

**b. Giao diện Timeline**

- Hiển thị dạng dòng thời gian dọc, sự kiện mới nhất hiển thị đầu tiên (reverse chronological).
- Mỗi sự kiện có card riêng: Icon loại sự kiện, Ngày tháng, Tiêu đề, Mô tả chi tiết, File đính kèm (QĐ, HĐ, văn bản gốc).
- Lọc hiển thị theo loại sự kiện (Điều động, Khen thưởng, Hợp đồng...) hoặc khoảng thời gian.
- Nút 'Thêm sự kiện' chỉ hiển thị với Admin HR.

**1.4.3.5. Quản lý tài liệu hồ sơ nhân viên**

Mỗi hồ sơ nhân viên có kho tài liệu số riêng với cấu trúc thư mục cố định, lưu trữ toàn bộ giấy tờ pháp lý và tài liệu cá nhân liên quan đến quá trình làm việc tại Công ty.

**a. Cấu trúc thư mục cố định**

| Thư mục                   | Loại tài liệu điển hình                                          | Định dạng     | Dung lượng tối đa |
| ------------------------- | ---------------------------------------------------------------- | ------------- | ----------------- |
| 01\. Giấy tờ tùy thân     | CMND/CCCD, Hộ chiếu, Sổ hộ khẩu, Giấy khai sinh (bản công chứng) | PDF, JPG, PNG | 10 MB/file        |
| 02\. Bằng cấp – Chứng chỉ | Bằng ĐH, Chứng chỉ nghề, Chứng chỉ ngoại ngữ (bản công chứng)    | PDF, JPG      | 10 MB/file        |
| 03\. Hợp đồng lao động    | Tất cả HĐLĐ đã ký kể cả hợp đồng đã hết hiệu lực                 | PDF           | 20 MB/file        |
| 04\. Quyết định nhân sự   | QĐ bổ nhiệm, điều động, khen thưởng, kỷ luật, nâng lương         | PDF           | 10 MB/file        |
| 05\. Ảnh nhân sự          | Ảnh 3×4, ảnh thẻ chính thức các thời kỳ                          | JPG, PNG      | 5 MB/file         |
| 06\. Hồ sơ y tế           | Kết quả khám sức khỏe định kỳ hàng năm                           | PDF, JPG      | 20 MB/file        |
| 07\. Tài liệu khác        | Giấy phép lái xe, Thẻ nghề, các giấy tờ liên quan khác           | PDF, JPG, PNG | 10 MB/file        |

**b. Tính năng quản lý tài liệu**

- Upload file vào đúng thư mục; hỗ trợ multi-upload nhiều file cùng lúc với thanh tiến trình.
- Phiên bản hóa (Versioning): Upload file mới cùng tên tự động lưu bản cũ vào lịch sử, không ghi đè.
- Xem trước (Preview) tài liệu PDF và ảnh ngay trong trình duyệt mà không cần tải xuống.
- Tải xuống đơn file hoặc toàn bộ hồ sơ dưới dạng file ZIP.
- Ghi chú cho từng file: Ngày cấp, Ngày hết hạn (nếu có), Ghi chú nội bộ.
- Cảnh báo tài liệu sắp hết hạn: Màu vàng khi còn \< 90 ngày, màu đỏ khi đã hết hạn.
- Kiểm tra hồ sơ đầy đủ: Hiển thị % hoàn thiện hồ sơ và danh sách tài liệu còn thiếu theo yêu cầu tối thiểu.

**1.4.3.6. Danh bạ nội bộ**

Danh bạ nội bộ cho phép toàn thể nhân viên có tài khoản (kể cả Viewer) tra cứu thông tin liên hệ cơ bản của đồng nghiệp nhanh chóng. Đây là thông tin công khai nội bộ, không tiết lộ dữ liệu cá nhân nhạy cảm.

**a. Giao diện danh bạ**

**Trang danh sách**

- Hiển thị tất cả nhân viên trạng thái 'Đang làm' dạng card lưới (Grid) hoặc bảng (List), toggle chuyển đổi.
- Mỗi card: Ảnh đại diện, Họ tên, Chức vụ, Đơn vị, SĐT, Email.
- Sắp xếp mặc định: Theo đơn vị → Chức vụ → Tên. Phân trang: 20 / 50 / Tất cả bản ghi.

**Tìm kiếm Full-text**

- Tìm kiếm theo: Họ tên (hỗ trợ cả có dấu và không dấu), Số điện thoại, Email, Tên đơn vị, Chức vụ.
- Kết quả realtime (debounce 300ms); highlight từ khóa trong kết quả; thông báo 'Không tìm thấy' nếu trống.

**Bộ lọc nâng cao**

- Lọc theo Đơn vị/Phòng ban/Xí nghiệp (multi-select), Chức vụ (multi-select), Giới tính.
- Kết hợp nhiều bộ lọc cùng lúc; nút 'Xóa tất cả bộ lọc' để reset.

**Trang chi tiết nhân viên (trong Danh bạ)**

- Ảnh đại diện lớn, thông tin cơ bản: Họ tên, Mã NV, Chức vụ, Đơn vị.
- Thông tin liên lạc: SĐT nội bộ (nút gọi điện trên mobile), Email nội bộ (nút gửi email).
- Vị trí trong sơ đồ tổ chức: Đơn vị cha và người đứng đầu trực tiếp.
- Đồng nghiệp cùng đơn vị: Danh sách 3-5 người cùng phòng/tổ để liên hệ nhanh.

**1.4.3.7. Thống kê & Báo cáo nhân sự**

**a. Dashboard thống kê nhân sự**

**Nhóm KPI Cards – Tổng quan nhanh**

| Chỉ tiêu                   | Giá trị hiển thị                           | Ghi chú                         |
| -------------------------- | ------------------------------------------ | ------------------------------- |
| Tổng số nhân viên đang làm | Số NV trạng thái 'Đang làm' hiện tại       | Tách riêng: Biên chế / Hợp đồng |
| Mới tuyển dụng (tháng này) | Số NV có ngày vào làm trong tháng hiện tại |                                 |
| Nghỉ việc (tháng này)      | Số NV nghỉ việc trong tháng hiện tại       |                                 |
| Tỷ lệ nghỉ việc            | Nghỉ / Tổng đầu kỳ × 100%                  | So sánh với tháng trước         |
| HĐ sắp hết hạn (30 ngày)   | Số HĐLĐ hết hạn trong 30 ngày tới          | Cảnh báo vàng nếu \> 0          |
| Chứng chỉ sắp hết hiệu lực | Số chứng chỉ hết hạn trong 90 ngày tới     | Để kịp gia hạn                  |

**Nhóm Biểu đồ thống kê**

| Tên biểu đồ                | Loại      | Nội dung                                   | Bộ lọc      |
| -------------------------- | --------- | ------------------------------------------ | ----------- |
| Nhân sự theo Đơn vị        | Cột ngang | Số NV từng phòng ban/XN sắp xếp giảm dần   | Tháng/Năm   |
| Cơ cấu giới tính           | Tròn      | Tỷ lệ % Nam/Nữ; số tuyệt đối trong tooltip | Theo đơn vị |
| Cơ cấu trình độ học vấn    | Tròn      | Trung cấp, CĐ, ĐH, Thạc sĩ, Tiến sĩ        | Theo đơn vị |
| Cơ cấu loại hợp đồng       | Tròn      | Thử việc / Xác định / Không thời hạn / CTV | Theo đơn vị |
| Biến động nhân sự 12 tháng | Đường     | 2 đường: Tuyển mới và Nghỉ việc theo tháng | Năm         |
| Cơ cấu độ tuổi             | Cột       | Nhóm: \<30, 30-40, 40-50, 50-60, \>60      | Theo đơn vị |
| Thâm niên công tác         | Cột       | Nhóm: \<1 năm, 1-5, 5-10, 10-20, \>20 năm  | Theo đơn vị |

**b. Danh mục báo cáo nhân sự**

| Mã BC   | Tên báo cáo                          | Mô tả nội dung                                                                                                      | Định dạng  |
| ------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------- | ---------- |
| BCNS-01 | Danh sách trích ngang nhân sự        | Họ tên, Mã NV, Ngày sinh, Chức vụ, Đơn vị, Ngày vào làm, Trình độ, SĐT, Email. Dùng cho báo cáo cấp trên và nội bộ. | Excel, PDF |
| BCNS-02 | Danh sách nhân sự theo đơn vị        | Nhóm nhân viên theo từng phòng ban/xí nghiệp, kèm tổng số đầu mỗi nhóm.                                             | Excel, PDF |
| BCNS-03 | Báo cáo biến động nhân sự            | Danh sách NV tuyển mới và NV nghỉ việc trong kỳ (tháng/quý/năm) kèm lý do.                                          | Excel, PDF |
| BCNS-04 | Danh sách HĐ sắp hết hạn             | NV có HĐLĐ hết hạn trong N ngày tới (N cấu hình được, mặc định 60 ngày).                                            | Excel      |
| BCNS-05 | Báo cáo cơ cấu nhân sự               | Thống kê đa chiều: theo đơn vị, giới tính, trình độ, loại HĐ, độ tuổi, thâm niên.                                   | Excel, PDF |
| BCNS-06 | Danh sách chứng chỉ sắp hết hiệu lực | NV có chứng chỉ hết hạn trong 90 ngày tới, kèm tên chứng chỉ và ngày hết hạn.                                       | Excel      |
| BCNS-07 | Lý lịch cá nhân theo mẫu             | In lý lịch 1 nhân viên theo mẫu chuẩn của Công ty (tham chiếu mẫu 2C-BNV).                                          | PDF        |
| BCNS-08 | Báo cáo tổng hợp nhân sự năm         | Báo cáo tổng kết nhân sự cuối năm gửi Công đoàn, Sở LĐTBXH.                                                         | Excel, PDF |

**1.4.3.8. Quản lý nghỉ phép**

Số hóa quy trình đăng ký và phê duyệt nghỉ phép, thay thế phiếu giấy truyền thống. Tự động tính toán số ngày phép còn lại theo Bộ luật Lao động 2019 và chính sách nội bộ của Công ty.

**a. Cấu hình chính sách nghỉ phép**

- Admin HR cấu hình số ngày phép năm theo thâm niên (Điều 113 BLLĐ 2019):
  - Thâm niên \< 5 năm: 12 ngày phép năm.
  - Thâm niên 5 – 10 năm: 13 ngày phép năm.
  - Thâm niên \> 10 năm: 14 ngày phép năm.
- Cấu hình phép đặc biệt: Thai sản (180 ngày), Nghỉ cưới (3 ngày), Tang lễ thân nhân (3 ngày), Khám sức khỏe (1 ngày/năm).
- Phép năm không dùng hết: Cấu hình chuyển tối đa N ngày sang năm sau (mặc định 5 ngày).
- Cấu hình danh sách ngày lễ, ngày nghỉ theo quy định của Nhà nước (hệ thống trừ tự động khi tính số ngày nghỉ).

**b. Quy trình đăng ký nghỉ phép**

1. Nhân viên đăng nhập → Chọn 'Đăng ký nghỉ phép' → Chọn loại phép, ngày bắt đầu, ngày kết thúc.
2. Hệ thống tự tính số ngày (trừ ngày nghỉ cuối tuần và ngày lễ); hiển thị số ngày phép còn lại.
3. Cảnh báo nếu số ngày đăng ký \> số ngày còn lại; yêu cầu xác nhận nếu muốn nghỉ vượt phép (nghỉ không lương).
4. Nhập lý do, người bàn giao công việc (nếu cần). Nhấn 'Gửi đơn'.
5. Quản lý trực tiếp nhận thông báo email → Đăng nhập hệ thống → Xem đơn → Duyệt / Từ chối kèm lý do.
6. Nhân viên nhận thông báo kết quả qua email và In-app notification.
7. Sau khi Duyệt: Số ngày phép tự động trừ khỏi số dư phép năm của nhân viên.

**c. Bảng theo dõi số dư phép năm**

| Chỉ tiêu                 | Ý nghĩa / Công thức tính                                              |
| ------------------------ | --------------------------------------------------------------------- |
| Số ngày phép được hưởng  | Tính theo thâm niên công tác \+ phép chuyển từ năm trước (nếu có)     |
| Số ngày đã nghỉ          | Tổng ngày trong các đơn nghỉ trạng thái 'Đã duyệt' trong năm hiện tại |
| Số ngày đang chờ duyệt   | Ngày phép trong các đơn đang ở trạng thái 'Chờ duyệt'                 |
| Số ngày phép còn lại     | \= Được hưởng – Đã nghỉ – Đang chờ duyệt                              |
| Số ngày nghỉ không lương | Các đơn nghỉ vượt phép đã được duyệt trong năm                        |

**d. Lịch nghỉ phép đơn vị**

- Quản lý phòng ban/xí nghiệp xem lịch nghỉ phép dạng Calendar View của tất cả nhân viên trong đơn vị.
- Phát hiện trùng lịch: Cảnh báo khi trong cùng khoảng thời gian có quá nhiều người trong đơn vị xin nghỉ (ngưỡng cấu hình được, VD: \>50% quân số).
- Lọc lịch theo loại phép, theo nhân viên cụ thể.

**2\. Biểu đồ UML Hệ thống Website Công ty Sông Nhuệ**

Phần này trình bày toàn bộ các biểu đồ UML mô hình hóa hệ thống website, bao gồm: Use Case Diagram tổng quát, Class Diagram domain model, Sequence Diagram cho các luồng nghiệp vụ chính, Activity Diagram, Component Diagram, State Machine Diagram và Deployment Diagram. Các biểu đồ tuân theo chuẩn UML 2.5 và phản ánh đúng kiến trúc Modular Monolith của hệ thống.

**2.1. Use Case Diagram Tổng quát**

| Tên biểu đồ | Use Case Diagram \- Hệ thống Website Thủy Lợi Sông Nhuệ                      |
| :---------- | :--------------------------------------------------------------------------- |
| Phạm vi     | Toàn bộ hệ thống — 4 nhóm vai trò (Actor) và 23 use case chính               |
| Mục đích    | Xác định ranh giới hệ thống, các tác nhân và chức năng cốt lõi của từng role |

![][image2]

_Hình 2.1 — Use Case Diagram tổng quát hệ thống với 4 nhóm Actor_

Giải thích các Actor:

- Guest (Công cộng): Truy cập thông tin công khai — tin tức, sự kiện, thông tin doanh nghiệp, gửi form liên hệ, xem widget thủy văn thời gian thực.

- Viewer / Nội bộ: Có tài khoản nội bộ, xem được báo cáo vận hành, dữ liệu thủy văn chi tiết, bản đồ GIS, tra cứu hồ sơ công trình và danh bạ nhân viên.

- Operator (Vận hành viên): Nhập nhật ký vận hành hàng ngày, tạo phiếu sự cố, upload tài liệu công trình, theo dõi cảnh báo thủy văn — chỉ trong phạm vi Xí nghiệp của mình.

- Admin (Quản trị hệ thống): Quyền cao nhất — quản trị toàn bộ CMS, cấu hình API nguồn dữ liệu, phân quyền tài khoản, quản lý ngưỡng cảnh báo, xuất báo cáo, quản lý nhân sự.

**2.2. Class Diagram — Domain Model**

| Tên biểu đồ | Class Diagram \- Domain Model Hệ thống                                         |
| :---------- | :----------------------------------------------------------------------------- |
| Số class    | 14 class chính trong 3 module nghiệp vụ                                        |
| Pattern     | Domain-Driven Design (DDD) — phân tách rõ Aggregate Root, Entity, Value Object |

![][image3]

_Hình 2.2 — Class Diagram với 14 domain class chính_

Các nhóm class chính:

- Nhóm CMS (MOD-01): User, Article, Category, MediaFile, Contact — quản lý toàn bộ nội dung website công khai.

- Nhóm Vận hành (MOD-02): PumpStation, WaterLevelReading, OperationLog, Alert, IncidentReport — số hóa nghiệp vụ vận hành công trình thủy lợi.

- Nhóm Nhân sự & Báo cáo (MOD-03/06): Employee, Department, Document, ReportJob — quản lý nhân sự và xuất báo cáo bất đồng bộ.

Các quan hệ chính: User 1–\* Article (viết bài), Article \*–\* Category (phân loại), PumpStation 1–\* WaterLevelReading (quan trắc), WaterLevelReading \*–\* Alert (kích hoạt cảnh báo), Employee \*–1 Department (thuộc đơn vị).

**2.3. Biểu đồ Tuần tự — Đăng nhập & Xem Dữ liệu Thủy văn**

| Use Case liên quan | UC-Login, UC-ViewHydroData (FR-05, FR-09, NFR-03)                           |
| :----------------- | :-------------------------------------------------------------------------- |
| Actors             | Browser → Frontend React → Backend API → Redis Cache → MySQL → External API |
| Điểm đáng chú ý    | JWT Auth \+ Redis cache layer \+ Fallback khi External API lỗi              |

![][image4]

_Hình 2.3 — Sequence Diagram đăng nhập JWT và truy vấn dữ liệu thủy văn_

Luồng chính (Happy Path):

1\. Người dùng nhập credentials → Frontend POST /auth/login lên Backend.

2\. Backend kiểm tra Redis cache (token cũ còn hiệu lực?). Nếu cache miss → truy vấn MySQL, BCrypt verify mật khẩu.

3\. Tạo JWT (TTL 8h), lưu vào Redis → Trả token về Frontend.

4\. Frontend dùng Bearer JWT gọi GET /hydro/realtime. Backend check cache → Trả về JSON dữ liệu thủy văn.

Xử lý ngoại lệ:

- Sai credentials: HTTP 401 \+ message "Sai tên đăng nhập hoặc mật khẩu".

- External API offline: Backend phục vụ dữ liệu từ cache với thông báo thời gian cập nhật cuối.

- JWT hết hạn: HTTP 401 → Frontend tự động redirect về trang đăng nhập.

**2.4. Biểu đồ Tuần tự — Polling Dữ liệu từ External API**

| Use Case liên quan | UC-CollectHydroData (FR-05, NFR-09)                                                      |
| :----------------- | :--------------------------------------------------------------------------------------- |
| Cơ chế             | Polling 15 phút \+ Retry exponential backoff \+ Alert khi thất bại kéo dài               |
| Actors             | Scheduler → Polling Service → Telemetry API → DB Time-series → Alert Service → Email/SMS |

![][image5]

_Hình 2.4 — Sequence Diagram polling dữ liệu thủy văn với cơ chế retry_

Cơ chế Retry & Resilience:

- Retry Policy: 3 lần thử lại với exponential backoff (5 → 10 → 20 phút) khi External API trả HTTP 5xx hoặc timeout.

- Sau 3 lần thất bại liên tiếp: Hệ thống đánh dấu trạm OFFLINE trên dashboard và gửi email Alert cho Admin.

- Dữ liệu thủy văn sau khi nhận được validate (kiểm tra range hợp lệ, đơn vị), chuẩn hóa rồi mới lưu vào DB time-series.

- Mọi bản ghi raw data được lưu log riêng phục vụ audit và tái xử lý khi cần thiết.

**2.5. Biểu đồ Tuần tự — Xuất Báo cáo Bất đồng bộ (Async)**

| Use Case liên quan | UC-GenerateReport (FR-07, NFR-09)                                         |
| :----------------- | :------------------------------------------------------------------------ |
| Pattern            | Async Job Queue (Redis Queue / RabbitMQ) — không block UI                 |
| Actors             | User → Frontend → API Gateway → Job Queue → Report Worker → Email Service |

![][image6]

_Hình 2.5 — Sequence Diagram xuất báo cáo bất đồng bộ với Job Queue_

Luồng bất đồng bộ:

- Người dùng chọn loại báo cáo, kỳ báo cáo → Frontend POST /reports/generate → API trả HTTP 202 Accepted kèm job_id (không chờ hoàn thành).

- Job được thêm vào Queue → Report Worker nhận và xử lý độc lập (tổng hợp dữ liệu, render PDF/Excel).

- Khi hoàn thành: Cập nhật trạng thái COMPLETED \+ fileUrl → Gửi email thông báo \+ link tải (hiệu lực 24h).

- Người dùng GET /reports/{job_id} để lấy fileUrl và tải xuống bất kỳ lúc nào trong 24h.

- Xử lý lỗi: Sau 3 lần retry thất bại → job chuyển trạng thái FAILED \+ thông báo lỗi cho người dùng.

**2.6. Biểu đồ Hoạt động — Quản lý Bài viết (CMS)**

| Use Case liên quan | UC-ManageArticle (FR-03)                                                  |
| :----------------- | :------------------------------------------------------------------------ |
| Swimlanes          | 3 làn: Biên tập viên — Trưởng ban / Admin — Hệ thống                      |
| Phạm vi            | Vòng đời đầy đủ: Tạo → Gửi duyệt → Duyệt/Từ chối → Xuất bản → Lưu trữ/Xóa |

![][image7]

_Hình 2.6 — Activity Diagram quản lý bài viết CMS với 3 swimlane_

Các điểm quyết định chính:

- Validation nội dung: Hệ thống kiểm tra các trường bắt buộc (Tiêu đề, Nội dung, Danh mục), độ dài Meta Title/Description trước khi cho phép gửi duyệt.

- Phê duyệt: Trưởng ban biên tập / Admin quyết định Duyệt (→ Published) hoặc Từ chối kèm lý do (→ Biên tập viên sửa lại).

- Hệ thống tự động: Tạo Audit Log cho mọi thao tác, gửi email thông báo, cập nhật search index sau khi xuất bản.

- Hẹn giờ đăng: Bài được đặt ngày tương lai sẽ tự động Published vào đúng thời điểm qua cron job 5 phút/lần.

**2.7. Biểu đồ Thành phần (Component Diagram)**

| Kiến trúc      | Modular Monolith — triển khai trên một cụm server, module tách biệt rõ ràng |
| :------------- | :-------------------------------------------------------------------------- |
| Pattern        | Layered Architecture: Client → API Gateway → Business Services → Data Layer |
| Tích hợp ngoài | Telemetry API (IoT), Email/SMS Gateway, Google Maps API                     |

![][image8]

_Hình 2.7 — Component Diagram kiến trúc Modular Monolith_

Mô tả các layer:

- Client Layer: React SPA cho Public Website (SSR cho trang public, SPA cho admin), hỗ trợ Mobile Responsive 360px→2560px. Tất cả traffic đi qua Nginx Reverse Proxy (HTTPS/TLS).

- API Gateway / Backend Layer: Spring Boot với các module độc lập — Auth (JWT/BCrypt), CMS API (MOD-01), Hydro API (MOD-02), HR API (MOD-03), Report API (MOD-06). Giao tiếp nội bộ qua service interface.

- Data / Service Layer: MySQL 8.0 (primary data), Redis (cache session \+ hydro data \+ job queue), MinIO/S3 (file storage), Report Worker (xử lý job bất đồng bộ).

- External Services: Telemetry API (IoT/RTU), Email Gateway (SMTP), SMS Gateway (ESMS/Twilio), Google Maps API (GIS).

**2.8. Biểu đồ Trạng thái — Vòng đời Bài viết**

| Use Case liên quan | UC-ManageArticle (FR-03)                                              |
| :----------------- | :-------------------------------------------------------------------- |
| Số trạng thái      | 6 trạng thái: NHÁP → CHỜ DUYỆT → XUẤT BẢN → GỠ BÀI / LƯU TRỮ → ĐÃ XÓA |
| Đặc điểm           | Trạng thái ĐÃ XÓA là terminal state — không thể khôi phục             |

![][image9]

_Hình 2.8 — State Machine Diagram vòng đời bài viết_

Mô tả các chuyển trạng thái:

| Chuyển trạng thái    | Sự kiện      | Người thực hiện    | Ghi chú                                                       |
| :------------------- | :----------- | :----------------- | :------------------------------------------------------------ |
| NHÁP → CHỜ DUYỆT     | Gửi duyệt    | Biên tập viên      | Bài viết khóa chỉnh sửa, gửi notification cho Trưởng ban      |
| CHỜ DUYỆT → NHÁP     | Từ chối      | Trưởng ban / Admin | Trả về kèm lý do từ chối; biên tập viên được chỉnh sửa tiếp   |
| CHỜ DUYỆT → XUẤT BẢN | Duyệt        | Trưởng ban / Admin | Bài hiển thị công khai; search index cập nhật                 |
| XUẤT BẢN → GỠ BÀI    | Gỡ bài       | Trưởng ban / Admin | Ẩn khỏi website; URL trả 404; dữ liệu giữ nguyên              |
| XUẤT BẢN → LƯU TRỮ   | Lưu trữ      | Admin              | Bài ẩn khỏi listing nhưng vẫn truy cập được qua URL trực tiếp |
| GỠ BÀI → XUẤT BẢN    | Tái xuất bản | Trưởng ban / Admin | Bài được hiển thị trở lại; không cần duyệt lại                |
| \* → ĐÃ XÓA          | Xóa          | Admin              | Xóa mềm (soft delete); lưu trữ audit log; không phục hồi được |

**2.9. Biểu đồ Triển khai (Deployment Diagram)**

| Môi trường        | Production — 3 môi trường: Dev / Staging / Production                         |
| :---------------- | :---------------------------------------------------------------------------- |
| Hạ tầng           | Nginx Active-Passive, App Server cluster, MySQL Primary-Replica, Redis, MinIO |
| Yêu cầu liên quan | NFR-01 (Uptime ≥99.5%), NFR-02 (Scale ngang), NFR-04 (Logging tập trung)      |

![][image10]

_Hình 2.9 — Deployment Diagram hạ tầng triển khai Production_

Mô tả hạ tầng triển khai:

- Nginx Active-Passive: Reverse proxy với SSL termination (HTTPS/TLS 1.3), load balancing giữa 2 App Server. Passive node sẵn sàng tiếp quản trong ≤30 giây khi Active node lỗi.

- App Server (x2): Chạy React SPA (hoặc SSR), Spring Boot API, và Report Worker. Horizontal scale được khi cần. CI/CD pipeline rolling deployment hoặc blue-green.

- MySQL Primary-Replica: Primary xử lý Write; Replica xử lý Read (report queries, analytics). Backup tự động hàng ngày, retention 30 ngày.

- Redis Cluster: Cache session người dùng (TTL 8h), cache dữ liệu thủy văn (TTL 15 phút), Job Queue cho Report Worker.

- MinIO (S3-compatible): Lưu trữ ảnh media (CMS), tài liệu công trình, file báo cáo xuất. Phân tách khỏi DB để tránh ảnh hưởng hiệu năng.

- Monitoring: Prometheus \+ Grafana theo dõi uptime, latency, memory/CPU. Log tập trung JSON format với rotation 30 ngày. Alert khi downtime \>15 phút.

- External Services: Telemetry API kết nối qua HTTPS với API Key mã hóa AES-256; Email/SMS gateway cho cảnh báo; Google Maps API cho GIS.

**3\. Dự trù chi phí Phát triển hệ thống Website Công ty Sông Nhuệ**

Bảng chi phí chi tiết

| Hạng mục                                   | Mô tả                                                                                               | Đơn Giá (VNĐ/ Ngày) | Số ngày làm việc | Thành tiền (VNĐ) |
| :----------------------------------------- | :-------------------------------------------------------------------------------------------------- | :------------------ | :--------------- | :--------------- |
| 1\. Phân tích & thiết kế                   |                                                                                                     |                     |                  |                  |
| 1.1. Khảo sát yêu cầu                      | Phỏng vấn stakeholder, viết đặc tả chức năng & phi chức năng                                        |                     |                  |                  |
| 1.2. Thiết kế hệ thống                     | Kiến trúc Modular Monolith, thiết kế CSDL MySQL, ERD, sơ đồ thành phần                              |                     |                  |                  |
| 1.3. Thiết kế giao diện (UI/UX)            | Wireframe, mockup responsive (mobile → 4K), prototype tương tác                                     |                     |                  |                  |
| 1.4. Thiết kế UML & tài liệu kỹ thuật      | Use Case, Class, Sequence, Activity, Component, Deployment diagram                                  |                     |                  |                  |
| 2\. Phát triển phần mềm                    |                                                                                                     |                     |                  |                  |
| 2.1. Lập trình Frontend                    | React/Vue SPA, Responsive Design 360px–2560px, SSR/Static Gen cho trang public                      |                     |                  |                  |
| 2.2. Lập trình Backend                     | Spring Boot, Auth module (JWT/BCrypt), CMS bài viết, quản lý văn bản, tìm kiếm full-text tiếng Việt |                     |                  |                  |
| 2.3. Module dữ liệu vận hành               | Polling External API (retry, backoff), lưu WaterLevelReading, hiển thị bảng \+ line chart           |                     |                  |                  |
| 2.4. Tích hợp bản đồ KMZ/ GeoJSON          | Tích hợp bản đồ động, zoom/pan/layer, upload KMZ, tải xuống theo role                               |                     |                  |                  |
| 2.5. Module báo cáo vận hành               | Form báo cáo ngày \+ kỳ, xuất PDF/Excel/Word async job, lưu lịch sử báo cáo                         |                     |                  |                  |
| 2.6. Module quản lý xí nghiệp & công trình | CRUD xí nghiệp, trạm bơm, liên kết Google Maps, sơ đồ cơ cấu tổ chức động                           |                     |                  |                  |
| 2.7. Tích hợp hệ thống & bảo mật           | HTTPS/TLS, CSP, Rate limiting, Audit log, scan malware file upload, CI/CD pipeline                  |                     |                  |                  |
| 3\. Kiểm thử                               |                                                                                                     |                     |                  |                  |
| 3.1. Kiểm thử chức năng                    | Đảm bảo các chức năng hoạt động đúng theo yêu cầu.                                                  |                     |                  |                  |
| 3.2. Kiểm thử hiệu năng                    | Tải trang \<3s/4G, API \<500ms, đồng thời 100–300 users, xuất báo cáo \<10s                         |                     |                  |                  |
| 3.3. Kiểm thử bảo mật                      | Kiểm tra các lỗ hổng bảo mật tiềm ẩn.                                                               |                     |                  |                  |
| 3.4. Kiểm thử tương thích                  | Chrome/Firefox/Edge/Safari, thiết bị mobile, mạng 3G                                                |                     |                  |                  |
| 4\. Triển khai                             |                                                                                                     |                     |                  |                  |
| 4.1. Cài đặt cấu hình máy chủ              | Nginx Active-Passive, MySQL, Redis, File Storage, cấu hình 3 môi trường Dev/Staging/Prod            |                     |                  |                  |
| 4.2. Deploy & Go-live                      | Rolling/Blue-Green deployment, cấu hình HTTPS, DNS, log tập trung JSON \+ rotation 30 ngày          |                     |                  |                  |
| 4.3. Đào tạo bàn giao                      | Hướng dẫn sử dụng CMS, hướng dẫn vận hành hệ thống cho Admin, tài liệu Swagger/OpenAPI              |                     |                  |                  |
| 5\. Bảo trì và hỗ trợ                      |                                                                                                     |                     |                  |                  |
| 5.1. Bảo trì hệ thống                      | Sửa lỗi phát sinh, cập nhật bảo mật, giám sát uptime ≥99.5%, backup dữ liệu                         |                     |                  |                  |
| 5.2. Hỗ trợ kỹ thuật                       | Hỗ trợ người dùng, xử lý sự cố, tối ưu hiệu năng sau go-live                                        |                     |                  |                  |
| Tổng cộng                                  |                                                                                                     |                     |                  |                  |
