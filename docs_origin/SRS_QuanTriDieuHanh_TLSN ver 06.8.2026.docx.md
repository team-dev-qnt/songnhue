  
**TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM**

*(Software Requirements Specification – SRS)*

**HỆ THỐNG PHẦN MỀM QUẢN TRỊ VÀ ĐIỀU HÀNH**

**CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ**

Phiên bản: 1.0

Ngày phát hành: 23/07/2026

*Trạng thái: Dự thảo để lấy ý kiến*

# **LỊCH SỬ THAY ĐỔI TÀI LIỆU**

| Phiên bản | Ngày | Mô tả thay đổi | Người thực hiện |
| :---- | :---- | :---- | :---- |
| 1.0 | 23/07/2026 | Soạn thảo lần đầu dựa trên tài liệu Tổng quan Hệ thống | Đội ngũ phân tích nghiệp vụ |

# **1\. GIỚI THIỆU**

## **1.1. Mục đích tài liệu**

Tài liệu này đặc tả chi tiết các yêu cầu chức năng và phi chức năng của Hệ thống Quản trị và Điều hành cho Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ (sau đây gọi tắt là "Công ty"), làm cơ sở thống nhất giữa các bên liên quan (chủ đầu tư, đơn vị phát triển phần mềm, đơn vị vận hành) trong suốt quá trình thiết kế, xây dựng, kiểm thử, nghiệm thu và bàn giao hệ thống.

Tài liệu được xây dựng dựa trên bản "Tổng quan Hệ thống Phần mềm Quản lý điều hành TLSN" do Công ty cung cấp, được chi tiết hóa thêm về mặt chức năng, phi chức năng, vai trò người dùng và tiêu chí nghiệm thu.

## **1.2. Phạm vi hệ thống**

Hệ thống bao gồm việc xây dựng:

* Cổng thông tin điện tử (E-Portal) công khai của Công ty.

* Hệ thống quản lý và hỗ trợ vận hành công trình thủy lợi (bao gồm bản đồ GIS).

* Hệ thống thu thập, xử lý và khai thác dữ liệu thủy văn theo thời gian thực.

* Hệ thống quản lý nhân sự (HRM) nội bộ.

* Phân hệ quản trị hệ thống, quản lý tài khoản và phân quyền.

Phạm vi không bao gồm: hạ tầng phần cứng máy chủ, mạng nội bộ (trừ khi có thỏa thuận riêng), và các hệ thống nghiệp vụ kế toán/tài chính không được nêu trong tài liệu gốc.

## **1.3. Mục tiêu hệ thống**

* Cung cấp nền tảng trực tuyến toàn diện, dễ sử dụng để công bố thông tin, quản lý công trình, giám sát và tổng hợp dữ liệu thủy văn, quản lý nhân sự, xây dựng báo cáo phục vụ điều hành.

* Cải thiện trải nghiệm người dùng qua giao diện hiện đại, thân thiện, tối ưu cho thiết bị di động (Responsive Web Design).

* Số hóa và tập trung hóa dữ liệu vận hành, giảm thao tác thủ công, tăng tính minh bạch và tốc độ ra quyết định.

## **1.4. Định nghĩa, thuật ngữ và từ viết tắt**

| Thuật ngữ | Giải thích |
| :---- | :---- |
| SRS | Software Requirements Specification – Đặc tả yêu cầu phần mềm |
| CMS | Content Management System – Hệ thống quản trị nội dung |
| GIS | Geographic Information System – Hệ thống thông tin địa lý |
| HRM | Human Resource Management – Quản lý nhân sự |
| RBAC | Role-Based Access Control – Kiểm soát truy cập theo vai trò |
| API | Application Programming Interface – Giao diện lập trình ứng dụng |
| WBS | Work Breakdown Structure – Cơ cấu phân rã công việc |
| RWD | Responsive Web Design – Thiết kế giao diện web đáp ứng đa thiết bị |
| Bên thứ 3 | Đơn vị/hệ thống bên ngoài cung cấp dữ liệu thủy văn qua Internet |

## **1.5. Tài liệu tham chiếu**

* Tổng quan Hệ thống Phần mềm Quản lý điều hành TLSN (tài liệu gốc do Công ty cung cấp).

# **2\. TỔNG QUAN HỆ THỐNG**

## **2.1. Bối cảnh và vị trí hệ thống**

Hệ thống được xây dựng nhằm thay thế/bổ sung cho các quy trình quản lý thủ công hoặc rời rạc hiện tại, tích hợp với hệ thống quản lý văn bản điều hành đã có của Công ty, và kết nối với nguồn dữ liệu thủy văn từ bên thứ 3 qua Internet.

## **2.2. Đối tượng người dùng và vai trò**

| Nhóm người dùng | Mô tả quyền truy cập |
| :---- | :---- |
| Khách (Public/Guest) | Truy cập thông tin chung: tin tức, sự kiện, giới thiệu Công ty, văn bản pháp luật, thông tin công trình công khai. |
| Cán bộ nội bộ (Nhân viên) | Truy cập chức năng chuyên môn theo phòng ban được phân công (nhập liệu, xem báo cáo liên quan). |
| Quản lý/Lãnh đạo phòng ban | Xem báo cáo tổng hợp, dữ liệu thủy văn, bản đồ công trình, phê duyệt nội dung/hồ sơ. |
| Ban giám đốc/Điều hành | Xem toàn bộ dashboard điều hành, báo cáo tổng hợp đa chiều, dữ liệu thời gian thực trên màn hình lớn. |
| Quản trị viên hệ thống (Admin) | Cấu hình hệ thống, quản lý tài khoản, phân quyền, nhật ký hoạt động, sao lưu/khôi phục dữ liệu. |

## **2.3. Cấu trúc module chức năng**

Hệ thống được chia thành 5 module chính, tương ứng với 5 nhóm nghiệp vụ:

1. Module 1 – Cổng thông tin điện tử (E-Portal).

2. Module 2 – Quản lý và hỗ trợ vận hành công trình thủy lợi (GIS).

3. Module 3 – Quản lý dữ liệu thủy văn.

4. Module 4 – Quản lý nhân sự (HRM).

5. Module 5 – Quản trị hệ thống, tài khoản và phân quyền.

## **2.4. Giả định và ràng buộc**

* Dữ liệu thủy văn phụ thuộc vào tính sẵn sàng và độ ổn định của API bên thứ 3; hệ thống cần có cơ chế xử lý lỗi khi nguồn dữ liệu gián đoạn.

* Hệ thống quản lý văn bản điều hành hiện có sẽ được tích hợp (không xây mới), thông qua API/kết nối do phía Công ty cung cấp thông tin kỹ thuật.

* Yêu cầu về màn hình trình chiếu lớn tại Phòng điều hành cần khảo sát thực tế về độ phân giải và vị trí lắp đặt trước khi thiết kế giao diện.

# **3\. YÊU CẦU CHỨC NĂNG CHI TIẾT**

Mỗi yêu cầu chức năng được gán mã theo định dạng M\<số module\>.\<số thứ tự\>, dùng để truy vết trong quá trình thiết kế, phát triển và kiểm thử.

## **3.1. Module 1 – Cổng thông tin điện tử (E-Portal)**

*Xây dựng cổng thông tin cung cấp thông tin minh bạch, kịp thời về hoạt động của Công ty; tích hợp hệ thống quản lý văn bản điều hành đã có vào cổng thông tin.*

| Mã YC | Chức năng | Mô tả yêu cầu | Vai trò sử dụng |
| :---- | :---- | :---- | :---- |
| M1.1 | Quản lý bài viết/tin tức | Thêm, sửa, xóa, xuất bản/gỡ bài viết; hỗ trợ soạn thảo văn bản có định dạng (rich text), đính kèm hình ảnh/tài liệu; lên lịch đăng bài; quản lý trạng thái (nháp/chờ duyệt/đã đăng). | Biên tập viên, Quản trị nội dung |
| M1.2 | Quản lý danh mục | Tạo, sửa, sắp xếp cây danh mục tin tức/bài viết (đa cấp) để tổ chức nội dung trên cổng thông tin. | Quản trị nội dung |
| M1.3 | Quản lý thư viện Media | Tải lên, tổ chức, tìm kiếm và tái sử dụng hình ảnh, video, tài liệu dùng chung cho các bài viết. | Biên tập viên |
| M1.4 | Quản lý Banner/Slide trang chủ | Cấu hình banner quảng bá, đường liên kết, thứ tự hiển thị và thời gian hiệu lực trên trang chủ. | Quản trị nội dung |
| M1.5 | Quản lý Liên hệ | Tiếp nhận, phân loại, phản hồi và lưu trữ các yêu cầu liên hệ gửi từ người dùng công cộng qua form trên cổng thông tin. | Nhân viên tiếp nhận, Quản lý phòng ban |
| M1.6 | Cấu hình giao diện cổng thông tin | Cho phép quản trị viên tùy chỉnh một số thành phần giao diện (logo, màu sắc chủ đạo, menu điều hướng, footer) mà không cần can thiệp mã nguồn. | Quản trị viên hệ thống |
| M1.7 | Quản lý phản hồi người dùng | Thu thập đánh giá/khảo sát mức độ hài lòng hoặc góp ý của người dùng về nội dung, dịch vụ công bố trên cổng thông tin. | Quản trị nội dung, Ban giám đốc |
| M1.8 | Tích hợp hệ thống quản lý văn bản điều hành | Hiển thị/đồng bộ danh sách văn bản điều hành (đã ban hành, được phép công khai) từ hệ thống quản lý văn bản hiện có của Công ty lên cổng thông tin. | Quản trị nội dung, Cán bộ văn thư |
| M1.9 | Tìm kiếm và tra cứu nội dung | Tìm kiếm bài viết, văn bản, công trình theo từ khóa, danh mục, thời gian đăng. | Người dùng công cộng, Nội bộ |
| M1.10 | Responsive Web Design | Giao diện cổng thông tin hiển thị tối ưu trên máy tính, máy tính bảng và điện thoại di động. | Tất cả người dùng |

### **3.1.1. Danh sách tác nhân**

| Tác nhân | Mô tả |
| :---- | :---- |
| Người dùng công cộng | Người truy cập cổng thông tin không cần đăng nhập, chỉ xem nội dung công khai. |
| Biên tập viên | Nhân viên được giao soạn thảo, cập nhật bài viết, media. |
| Quản trị nội dung | Người duyệt, xuất bản bài viết; quản lý danh mục, banner, liên hệ, phản hồi. |
| Cán bộ văn thư | Người quản lý văn bản điều hành phía hệ thống văn bản hiện có, cung cấp/đồng bộ dữ liệu văn bản. |
| Quản trị viên hệ thống | Cấu hình giao diện tổng thể của cổng thông tin. |

### **3.1.2. Đặc tả Use Case chi tiết**

#### ***UC1.1: Soạn thảo và xuất bản bài viết***

**Tác nhân:** Biên tập viên, Quản trị nội dung

**Mô tả:** Cho phép Biên tập viên soạn thảo bài viết/tin tức và Quản trị nội dung duyệt, xuất bản lên cổng thông tin.

**Tiền điều kiện:** Người dùng đã đăng nhập với vai trò có quyền quản lý nội dung.

**Luồng sự kiện chính:**

6. Biên tập viên chọn chức năng "Tạo bài viết mới".

7. Hệ thống hiển thị form soạn thảo (tiêu đề, nội dung rich text, danh mục, ảnh đại diện, tệp đính kèm, thẻ tag).

8. Biên tập viên nhập nội dung và chọn "Lưu nháp" hoặc "Gửi duyệt".

9. Hệ thống lưu bài viết với trạng thái tương ứng (Nháp/Chờ duyệt).

10. Quản trị nội dung xem danh sách bài viết chờ duyệt, mở xem chi tiết.

11. Quản trị nội dung chọn "Duyệt & Xuất bản" hoặc "Từ chối kèm ghi chú".

12. Nếu duyệt: hệ thống chuyển trạng thái bài viết sang "Đã đăng" và hiển thị công khai trên cổng thông tin; có thể đặt lịch đăng vào thời điểm tương lai.

**Luồng thay thế / ngoại lệ:**

* Nếu Quản trị nội dung từ chối: hệ thống chuyển trạng thái về "Yêu cầu chỉnh sửa" kèm ghi chú, gửi thông báo cho Biên tập viên.

* Nếu bài viết thiếu trường bắt buộc (tiêu đề, nội dung): hệ thống báo lỗi và không cho lưu ở trạng thái "Gửi duyệt".

**Hậu điều kiện:** Bài viết được xuất bản công khai hoặc lưu ở trạng thái phù hợp; lịch sử thao tác được ghi log.

#### ***UC1.2: Quản lý danh mục và thư viện Media***

**Tác nhân:** Quản trị nội dung, Biên tập viên

**Mô tả:** Tổ chức cây danh mục nội dung và thư viện hình ảnh/video/tài liệu dùng chung.

**Tiền điều kiện:** Người dùng có quyền quản trị nội dung.

**Luồng sự kiện chính:**

13. Quản trị nội dung truy cập mục "Danh mục", thêm/sửa/xóa danh mục và sắp xếp thứ tự, cấp cha-con.

14. Biên tập viên truy cập "Thư viện Media", tải lên tệp hình ảnh/video/tài liệu.

15. Hệ thống kiểm tra định dạng, dung lượng tệp hợp lệ và lưu vào thư viện.

16. Biên tập viên tìm kiếm, chọn tệp từ thư viện để chèn vào bài viết đang soạn thảo.

**Luồng thay thế / ngoại lệ:**

* Nếu xóa một danh mục đang có bài viết liên kết, hệ thống cảnh báo và yêu cầu xác nhận chuyển bài viết sang danh mục khác trước khi xóa.

**Hậu điều kiện:** Cây danh mục và thư viện media được cập nhật, sẵn sàng sử dụng cho các bài viết.

#### ***UC1.3: Tiếp nhận và xử lý liên hệ/phản hồi***

**Tác nhân:** Người dùng công cộng, Nhân viên tiếp nhận, Quản lý phòng ban

**Mô tả:** Người dùng công cộng gửi yêu cầu liên hệ hoặc phản hồi/đánh giá qua cổng thông tin; nội bộ tiếp nhận và xử lý.

**Tiền điều kiện:** Không yêu cầu đăng nhập đối với người gửi; nhân viên xử lý phải đăng nhập.

**Luồng sự kiện chính:**

17. Người dùng công cộng điền form Liên hệ (họ tên, email/điện thoại, nội dung) và gửi.

18. Hệ thống xác thực dữ liệu đầu vào (chống spam bằng captcha) và lưu yêu cầu với trạng thái "Mới".

19. Hệ thống gửi thông báo cho Nhân viên tiếp nhận phụ trách.

20. Nhân viên tiếp nhận xem, phân loại yêu cầu và chuyển cho phòng ban liên quan hoặc phản hồi trực tiếp.

21. Nhân viên cập nhật trạng thái xử lý (Đang xử lý/Đã phản hồi/Đóng).

**Luồng thay thế / ngoại lệ:**

* Nếu quá thời hạn xử lý cấu hình (SLA), hệ thống có thể gửi nhắc nhở cho người phụ trách.

**Hậu điều kiện:** Yêu cầu liên hệ được lưu vết đầy đủ trạng thái xử lý; dữ liệu phản hồi/khảo sát được tổng hợp phục vụ báo cáo.

#### ***UC1.4: Đồng bộ văn bản điều hành lên cổng thông tin***

**Tác nhân:** Cán bộ văn thư, Quản trị nội dung, Hệ thống tự động

**Mô tả:** Hiển thị các văn bản điều hành được phép công khai từ hệ thống quản lý văn bản hiện có lên cổng thông tin.

**Tiền điều kiện:** Kết nối tích hợp với hệ thống quản lý văn bản điều hành đã được cấu hình.

**Luồng sự kiện chính:**

22. Cán bộ văn thư đánh dấu văn bản đủ điều kiện công khai trên hệ thống quản lý văn bản hiện có.

23. Hệ thống (theo lịch đồng bộ định kỳ hoặc theo sự kiện) lấy danh sách văn bản mới được đánh dấu công khai.

24. Hệ thống hiển thị danh sách văn bản lên mục tương ứng trên cổng thông tin, kèm thông tin số hiệu, ngày ban hành, loại văn bản, tệp đính kèm.

25. Quản trị nội dung có thể xem lại và ẩn/hiện văn bản trên cổng thông tin nếu cần.

**Luồng thay thế / ngoại lệ:**

* Nếu không lấy được dữ liệu từ hệ thống văn bản (lỗi kết nối), hệ thống ghi log lỗi và giữ nguyên dữ liệu đã đồng bộ lần gần nhất.

**Hậu điều kiện:** Danh sách văn bản điều hành công khai trên cổng thông tin được cập nhật đồng bộ với hệ thống nguồn.

### **3.1.3. Quy tắc nghiệp vụ**

* Bài viết chỉ hiển thị công khai khi ở trạng thái "Đã đăng" và trong khoảng thời gian hiệu lực (nếu có đặt lịch).

* Một tài khoản Biên tập viên không có quyền tự xuất bản bài viết của chính mình; bắt buộc qua bước duyệt của Quản trị nội dung (nguyên tắc tách biệt vai trò).

* Văn bản điều hành hiển thị công khai phải là văn bản đã được đánh dấu "cho phép công khai" từ hệ thống nguồn; hệ thống không tự ý thay đổi trạng thái công khai của văn bản gốc.

* Dung lượng tệp tải lên thư viện Media và định dạng cho phép cần được cấu hình giới hạn để tránh quá tải lưu trữ.

### **3.1.4. Yêu cầu dữ liệu – Bài viết/Tin tức**

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :---- | :---- | ----- | :---- |
| Tiêu đề | Chuỗi ký tự | x | Tối đa 250 ký tự |
| Nội dung | Rich text/HTML | x | Hỗ trợ chèn ảnh, bảng, liên kết |
| Danh mục | Tham chiếu Danh mục | x | Có thể chọn nhiều danh mục |
| Ảnh đại diện | Tệp hình ảnh |  | Hiển thị ở trang danh sách/chia sẻ mạng xã hội |
| Tệp đính kèm | Tệp (pdf, docx, xlsx...) |  | Nhiều tệp |
| Tác giả | Tham chiếu Người dùng | x | Tự động gán theo tài khoản đăng nhập |
| Trạng thái | Danh sách chọn | x | Nháp / Chờ duyệt / Yêu cầu chỉnh sửa / Đã đăng / Gỡ |
| Ngày đăng dự kiến | Ngày giờ |  | Phục vụ lên lịch đăng bài |
| Lượt xem | Số nguyên |  | Tự động đếm |

## **3.2. Module 2 – Quản lý & hỗ trợ vận hành công trình thủy lợi**

*Quản lý toàn diện danh mục, hồ sơ kỹ thuật, phân loại, lịch sử vận hành công trình; số hóa và khai thác bản đồ GIS nhiều lớp dữ liệu; hỗ trợ trình chiếu, cảnh báo trực quan trên màn hình lớn tại Phòng điều hành.*

| Mã YC | Chức năng | Mô tả yêu cầu | Vai trò sử dụng |
| :---- | :---- | :---- | :---- |
| M2.1 | Quản lý danh mục công trình | Thêm, sửa, xóa hồ sơ các loại công trình (cống, trạm bơm, kênh mương, đê điều...) với các thuộc tính: mã công trình, tên, loại, vị trí, đơn vị quản lý, tình trạng vận hành. | Cán bộ kỹ thuật, Quản lý công trình |
| M2.2 | Quản lý thông số kỹ thuật thiết kế | Lưu trữ và cập nhật các thông số thiết kế kỹ thuật riêng theo từng loại công trình (lưu lượng, cao trình, kích thước, công suất...). | Cán bộ kỹ thuật |
| M2.3 | Quản lý lịch sử sửa chữa/bảo trì | Ghi nhận các lần sửa chữa, bảo trì, nâng cấp công trình theo thời gian, đơn vị thực hiện, chi phí (nếu có). | Cán bộ kỹ thuật, Quản lý công trình |
| M2.4 | Quản lý hình ảnh và tài liệu công trình | Tải lên, tổ chức hình ảnh hiện trạng, bản vẽ kỹ thuật, tài liệu pháp lý liên quan tới từng công trình. | Cán bộ kỹ thuật |
| M2.5 | Phân loại công trình theo cấp quản lý | Gán công trình theo cấp quản lý (cấp Công ty/cấp Xí nghiệp/cấp Cụm) và đơn vị trực tiếp phụ trách. | Quản lý công trình |
| M2.6 | Quản lý lưu vực/khu tưới tiêu liên quan | Liên kết công trình với lưu vực, khu tưới tiêu hoặc hệ thống kênh mương mà công trình phục vụ. | Cán bộ kỹ thuật, Quản lý công trình |
| M2.7 | Bản đồ nền GIS công trình | Hiển thị bản đồ nền số, cho phép phóng to/thu nhỏ, di chuyển, chuyển đổi lớp bản đồ nền (vệ tinh/địa hình/hành chính). | Nội bộ, Quản lý, Ban giám đốc |
| M2.8 | Số hóa tọa độ công trình | Xác định và cập nhật tọa độ (kinh độ, vĩ độ) của từng công trình trên bản đồ, hỗ trợ nhập tọa độ thủ công hoặc chọn điểm trực tiếp trên bản đồ. | Cán bộ kỹ thuật |
| M2.9 | Xây dựng lớp dữ liệu bản đồ (Layer) | Cho phép bật/tắt các lớp dữ liệu khác nhau trên bản đồ (công trình, ranh giới quản lý, lưu vực, điểm đo thủy văn...). | Quản trị viên, Cán bộ kỹ thuật |
| M2.10 | Tooltip/liên kết thông tin nhanh trên bản đồ | Khi người dùng nhấp vào một điểm công trình trên bản đồ, hệ thống hiển thị hộp thông tin nhanh (tooltip) kèm liên kết đến trang chi tiết công trình. | Nội bộ, Quản lý |
| M2.11 | Cảnh báo trực quan theo tình trạng công trình | Hiển thị biểu tượng/màu sắc khác nhau trên bản đồ theo tình trạng vận hành của công trình (bình thường/cảnh báo/sự cố/bảo trì). | Nội bộ, Quản lý, Trực ban điều hành |
| M2.12 | Công cụ đo khoảng cách/diện tích trên bản đồ | Hỗ trợ công cụ đo khoảng cách, diện tích khu vực trực tiếp trên bản đồ phục vụ tra cứu kỹ thuật nhanh. | Cán bộ kỹ thuật |
| M2.13 | Xuất bản đồ/báo cáo vị trí công trình | Xuất hình ảnh bản đồ hoặc danh sách công trình theo khu vực đã chọn ra tệp (ảnh/PDF/Excel). | Quản lý công trình |
| M2.14 | Chế độ hiển thị màn hình lớn (Dashboard điều hành) | Giao diện tối ưu riêng cho màn hình lớn tại Phòng điều hành: tổng hợp bản đồ công trình, số liệu thủy văn, cảnh báo theo thời gian thực. | Ban giám đốc, Trực ban điều hành |
| M2.15 | Tự động làm mới dữ liệu Dashboard | Dữ liệu bản đồ và số liệu trên Dashboard tự động cập nhật theo chu kỳ cấu hình mà không cần thao tác thủ công. | Hệ thống tự động |
| M2.16 | Thống kê số lượng công trình theo loại/tình trạng | Tổng hợp biểu đồ/số liệu thống kê số lượng công trình theo loại, khu vực, tình trạng vận hành. | Quản lý công trình, Ban giám đốc |
| M2.17 | Tìm kiếm và lọc công trình | Tìm kiếm công trình theo tên, mã, khu vực, loại công trình, tình trạng trên danh sách và trên bản đồ. | Nội bộ, Quản lý |
| M2.18 | Nhật ký thay đổi hồ sơ công trình | Ghi nhận lịch sử chỉnh sửa hồ sơ công trình (người sửa, thời gian, nội dung thay đổi) phục vụ truy vết. | Cán bộ kỹ thuật, Quản trị viên hệ thống |

### **3.2.1. Danh sách tác nhân**

| Tác nhân | Mô tả |
| :---- | :---- |
| Cán bộ kỹ thuật | Nhập, cập nhật hồ sơ công trình, thông số kỹ thuật, lịch sử bảo trì và số hóa vị trí trên bản đồ. |
| Quản lý công trình | Xem, duyệt hồ sơ công trình, phân loại theo cấp quản lý, tra cứu và thống kê phục vụ quản lý. |
| Ban giám đốc / Trực ban điều hành | Theo dõi tổng quan công trình và dữ liệu vận hành trên dashboard, màn hình lớn. |
| Quản trị viên hệ thống | Cấu hình các lớp dữ liệu (layer) hiển thị trên bản đồ, giám sát nhật ký thay đổi hồ sơ. |

### **3.2.2. Đặc tả Use Case chi tiết**

#### ***UC2.1: Quản lý hồ sơ danh mục và thông số kỹ thuật công trình***

**Tác nhân:** Cán bộ kỹ thuật, Quản lý công trình

**Mô tả:** Thêm mới, cập nhật, tra cứu hồ sơ chi tiết, thông số kỹ thuật và phân loại cấp quản lý của công trình thủy lợi.

**Tiền điều kiện:** Người dùng có quyền quản lý công trình.

**Luồng sự kiện chính:**

26. Cán bộ kỹ thuật chọn "Thêm công trình mới", nhập các thông tin: mã công trình, tên, loại (cống/trạm bơm/kênh mương/đê điều...), vị trí, đơn vị quản lý, tình trạng vận hành.

27. Cán bộ kỹ thuật bổ sung thông số kỹ thuật thiết kế riêng theo từng loại công trình (lưu lượng, cao trình, công suất...).

28. Cán bộ kỹ thuật gán cấp quản lý (Công ty/Xí nghiệp/Cụm) và liên kết công trình với lưu vực/khu tưới tiêu liên quan.

29. Hệ thống kiểm tra tính hợp lệ (mã công trình không trùng) và lưu hồ sơ, đồng thời ghi nhật ký thay đổi.

30. Quản lý công trình tra cứu, tìm kiếm/lọc công trình theo tên, mã, khu vực, loại, tình trạng để xem chi tiết hoặc xuất danh sách.

**Luồng thay thế / ngoại lệ:**

* Nếu mã công trình đã tồn tại, hệ thống báo lỗi và yêu cầu nhập mã khác.

**Hậu điều kiện:** Hồ sơ công trình được lưu trữ đầy đủ, sẵn sàng liên kết với bản đồ GIS (UC2.2).

#### ***UC2.2: Quản lý lịch sử bảo trì, hình ảnh và tài liệu công trình***

**Tác nhân:** Cán bộ kỹ thuật, Quản lý công trình

**Mô tả:** Ghi nhận các lần sửa chữa/bảo trì và lưu trữ hình ảnh, tài liệu kỹ thuật, pháp lý liên quan tới công trình.

**Tiền điều kiện:** Hồ sơ công trình đã được tạo (UC2.1).

**Luồng sự kiện chính:**

31. Cán bộ kỹ thuật chọn công trình, thêm bản ghi "Lịch sử bảo trì" với ngày thực hiện, nội dung, đơn vị thực hiện, chi phí (nếu có).

32. Cán bộ kỹ thuật tải lên hình ảnh hiện trạng, bản vẽ kỹ thuật, tài liệu pháp lý gắn với công trình.

33. Hệ thống lưu trữ và hiển thị theo dòng thời gian (timeline) trên trang chi tiết công trình.

34. Quản lý công trình xem lại lịch sử bảo trì để phục vụ lập kế hoạch bảo trì tiếp theo.

**Luồng thay thế / ngoại lệ:**

* Nếu tệp tải lên vượt quá dung lượng cho phép, hệ thống báo lỗi và từ chối lưu.

**Hậu điều kiện:** Lịch sử vận hành và hồ sơ tài liệu công trình được lưu trữ đầy đủ, có thể tra cứu theo thời gian.

#### ***UC2.3: Số hóa và hiển thị công trình nhiều lớp dữ liệu trên bản đồ GIS***

**Tác nhân:** Cán bộ kỹ thuật, Quản trị viên hệ thống, Nội bộ

**Mô tả:** Gắn tọa độ công trình lên bản đồ số, tổ chức theo lớp dữ liệu, cung cấp công cụ đo đạc và cho phép tra cứu nhanh qua tooltip.

**Tiền điều kiện:** Hồ sơ công trình đã được tạo (UC2.1).

**Luồng sự kiện chính:**

35. Cán bộ kỹ thuật chọn công trình, nhập/xác định tọa độ (kinh độ, vĩ độ) trên bản đồ nền.

36. Hệ thống lưu tọa độ và hiển thị điểm công trình tương ứng trên bản đồ, với biểu tượng/màu sắc theo tình trạng vận hành.

37. Quản trị viên cấu hình các lớp dữ liệu (layer): công trình, ranh giới quản lý, lưu vực, điểm đo thủy văn... để người dùng bật/tắt khi xem bản đồ.

38. Người dùng nội bộ mở bản đồ, phóng to/thu nhỏ, chuyển lớp bản đồ nền, lọc theo loại công trình hoặc khu vực.

39. Người dùng sử dụng công cụ đo khoảng cách/diện tích trực tiếp trên bản đồ khi cần.

40. Người dùng nhấp vào một điểm công trình, hệ thống hiển thị tooltip thông tin nhanh (tên, loại, tình trạng) kèm liên kết đến trang chi tiết; có thể xuất bản đồ/danh sách công trình theo khu vực ra tệp.

**Luồng thay thế / ngoại lệ:**

* Nếu công trình chưa được số hóa tọa độ, hệ thống không hiển thị công trình đó trên bản đồ và cảnh báo cho Cán bộ kỹ thuật trong danh sách "Công trình chưa có vị trí GIS".

**Hậu điều kiện:** Bản đồ GIS hiển thị đầy đủ, chính xác vị trí và tình trạng các công trình theo lớp dữ liệu đã cấu hình.

#### ***UC2.4: Thống kê tổng hợp công trình***

**Tác nhân:** Quản lý công trình, Ban giám đốc

**Mô tả:** Tổng hợp số liệu, biểu đồ thống kê công trình theo loại, khu vực, tình trạng vận hành phục vụ báo cáo quản lý.

**Tiền điều kiện:** Dữ liệu danh mục công trình đã đầy đủ.

**Luồng sự kiện chính:**

41. Người dùng chọn chức năng "Thống kê công trình", thiết lập tiêu chí (theo loại/khu vực/tình trạng/cấp quản lý).

42. Hệ thống tổng hợp và hiển thị biểu đồ/bảng số liệu tương ứng.

43. Người dùng có thể xuất báo cáo thống kê ra tệp Excel/PDF.

**Hậu điều kiện:** Số liệu thống kê công trình hỗ trợ Ban giám đốc trong việc lập kế hoạch đầu tư, bảo trì.

#### ***UC2.5: Hiển thị Dashboard điều hành trên màn hình lớn***

**Tác nhân:** Ban giám đốc, Trực ban điều hành

**Mô tả:** Tổng hợp bản đồ công trình, số liệu thủy văn và cảnh báo theo thời gian thực, tối ưu cho màn hình lớn tại Phòng điều hành.

**Tiền điều kiện:** Dữ liệu công trình và dữ liệu thủy văn (Module 3\) đã sẵn sàng.

**Luồng sự kiện chính:**

44. Trực ban điều hành mở giao diện Dashboard trên màn hình lớn.

45. Hệ thống hiển thị bản đồ tổng quan công trình (kèm biểu tượng cảnh báo trực quan) kết hợp lớp dữ liệu thủy văn hiện hành (mực nước, lượng mưa theo trạm).

46. Hệ thống tự động làm mới dữ liệu theo chu kỳ cấu hình mà không cần thao tác thủ công.

47. Khi có cảnh báo ngưỡng thủy văn (M3.13) hoặc công trình chuyển trạng thái sự cố, hệ thống hiển thị nổi bật cảnh báo trên dashboard.

**Luồng thay thế / ngoại lệ:**

* Nếu mất kết nối dữ liệu, dashboard hiển thị thông báo "Dữ liệu chưa cập nhật" kèm thời điểm cập nhật gần nhất thay vì để trống hoặc gây hiểu nhầm.

**Hậu điều kiện:** Ban lãnh đạo/trực ban có cái nhìn tổng quan, cập nhật liên tục về tình trạng công trình và thủy văn phục vụ ra quyết định.

### **3.2.3. Quy tắc nghiệp vụ**

* Mỗi công trình phải có mã định danh duy nhất trong toàn hệ thống.

* Công trình chỉ hiển thị trên bản đồ GIS khi đã được số hóa tọa độ hợp lệ.

* Tình trạng vận hành hiển thị trên bản đồ (màu sắc/biểu tượng) phải đồng bộ theo thời gian thực với trạng thái được cập nhật trong hồ sơ công trình.

* Giao diện Dashboard màn hình lớn ưu tiên hiển thị trực quan (biểu đồ, bản đồ, số liệu lớn dễ đọc từ xa) hơn là bảng dữ liệu chi tiết.

* Việc chỉnh sửa/xóa hồ sơ công trình cần được ghi log để phục vụ truy vết (liên kết Module 5 – Nhật ký hoạt động).

* Một công trình có thể thuộc nhiều lớp dữ liệu bản đồ khác nhau nhưng chỉ thuộc một cấp quản lý và một đơn vị phụ trách chính.

### **3.2.4. Yêu cầu dữ liệu – Công trình thủy lợi**

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :---- | :---- | ----- | :---- |
| Mã công trình | Chuỗi ký tự | x | Duy nhất trong hệ thống |
| Tên công trình | Chuỗi ký tự | x |  |
| Loại công trình | Danh sách chọn | x | Cống / Trạm bơm / Kênh mương / Đê điều / Khác |
| Cấp quản lý | Danh sách chọn | x | Công ty / Xí nghiệp / Cụm |
| Đơn vị quản lý | Tham chiếu Đơn vị | x | Liên kết với sơ đồ tổ chức Module 4 |
| Lưu vực/Khu tưới tiêu liên quan | Tham chiếu |  |  |
| Tình trạng vận hành | Danh sách chọn | x | Bình thường / Cảnh báo / Sự cố / Bảo trì |
| Tọa độ (Kinh độ, Vĩ độ) | Số thực |  | Bắt buộc để hiển thị trên bản đồ GIS |
| Thông số thiết kế | Văn bản/Số |  | Tùy loại công trình |
| Lịch sử sửa chữa/bảo trì | Danh sách bản ghi |  | Ngày, nội dung, đơn vị thực hiện, chi phí |
| Hình ảnh/Tài liệu đính kèm | Tệp |  | Nhiều tệp |
| Nhật ký thay đổi hồ sơ | Danh sách bản ghi |  | Người sửa, thời gian, nội dung thay đổi |

## **3.3. Module 3 – Quản lý dữ liệu thủy văn**

*Xây dựng danh mục điểm đo, cơ sở dữ liệu lịch sử thủy văn; đọc, kiểm tra và bóc tách dữ liệu thời gian thực từ bên thứ 3; xây dựng biểu đồ, báo cáo, cảnh báo ngưỡng phục vụ ra quyết định.*

| Mã YC | Chức năng | Mô tả yêu cầu | Vai trò sử dụng |
| :---- | :---- | :---- | :---- |
| M3.1 | Quản lý danh mục điểm đo/trạm đo | Thêm, sửa, xóa danh mục điểm đo thủy văn (tên, vị trí, loại chỉ số đo, đơn vị quản lý phụ trách). | Quản trị viên, Cán bộ kỹ thuật |
| M3.2 | Quản lý danh mục loại chỉ số đo | Định nghĩa các loại chỉ số quan trắc (mực nước, lượng mưa, lưu lượng...) và đơn vị đo tương ứng. | Quản trị viên hệ thống |
| M3.3 | Kết nối API dữ liệu thủy văn bên thứ 3 | Xây dựng module tự động kết nối, xác thực và đọc dữ liệu từ API do bên thứ 3 cung cấp qua Internet theo chu kỳ cấu hình được. | Hệ thống tự động, Quản trị viên |
| M3.4 | Bóc tách và chuẩn hóa dữ liệu | Xử lý, chuyển đổi định dạng dữ liệu thô nhận được thành cấu trúc chuẩn của hệ thống trước khi lưu trữ. | Hệ thống tự động |
| M3.5 | Kiểm tra tính hợp lệ dữ liệu đầu vào | Kiểm tra khoảng giá trị hợp lệ, trùng lặp thời điểm đo trước khi ghi nhận chính thức vào cơ sở dữ liệu. | Hệ thống tự động |
| M3.6 | Lưu trữ dữ liệu lịch sử thủy văn (time-series) | Lưu dữ liệu đã chuẩn hóa vào cơ sở dữ liệu lịch sử theo từng điểm đo, có gắn thời gian (timestamp) để phục vụ tra cứu và phân tích xu hướng. | Hệ thống tự động |
| M3.7 | Giám sát thời gian thực | Hiển thị số liệu mực nước, lượng mưa mới nhất theo thời gian thực (near real-time) trên dashboard. | Nội bộ, Quản lý, Trực ban |
| M3.8 | Biểu đồ diễn biến thủy văn theo điểm đo | Vẽ biểu đồ diễn biến mực nước, lượng mưa theo thời gian (ngày/tuần/tháng/năm) cho từng điểm đo. | Nội bộ, Quản lý |
| M3.9 | So sánh nhiều điểm đo trên cùng biểu đồ | Cho phép chọn nhiều điểm đo để so sánh diễn biến trên cùng một biểu đồ. | Nội bộ, Quản lý |
| M3.10 | Báo cáo khai thác dữ liệu định kỳ | Tạo và xuất báo cáo định kỳ (ngày/tuần/tháng) theo mẫu cố định phục vụ điều hành thường xuyên. | Quản lý, Ban giám đốc |
| M3.11 | Báo cáo theo yêu cầu (tùy chọn tham số) | Cho phép người dùng tự thiết lập tham số (điểm đo, khoảng thời gian, loại chỉ số) để tạo báo cáo theo nhu cầu đột xuất. | Quản lý, Ban giám đốc |
| M3.12 | Xuất báo cáo Excel/PDF | Hỗ trợ xuất mọi loại báo cáo thủy văn ra định dạng Excel hoặc PDF để lưu trữ, trình ký, chia sẻ. | Quản lý, Ban giám đốc |
| M3.13 | Cấu hình ngưỡng cảnh báo theo điểm đo | Cấu hình ngưỡng cảnh báo (thấp/cao) riêng cho từng điểm đo, từng loại chỉ số. | Quản trị viên hệ thống |
| M3.14 | Gửi thông báo cảnh báo vượt ngưỡng | Gửi thông báo (trên hệ thống/email) tới người dùng liên quan khi dữ liệu vượt ngưỡng cấu hình. | Hệ thống tự động, Trực ban điều hành |
| M3.15 | Xử lý gián đoạn kết nối | Ghi nhận và cảnh báo khi kết nối tới nguồn dữ liệu bên thứ 3 bị gián đoạn; tự động thử kết nối lại theo cơ chế cấu hình. | Hệ thống tự động, Quản trị viên |
| M3.16 | Nhật ký đồng bộ dữ liệu | Ghi và cho phép tra cứu lịch sử các lần đồng bộ dữ liệu (thành công/thất bại, thời gian, số bản ghi) phục vụ giám sát vận hành. | Quản trị viên hệ thống |
| M3.17 | Tích hợp hiển thị dữ liệu thủy văn lên bản đồ GIS | Hiển thị số liệu thủy văn mới nhất của từng điểm đo dưới dạng lớp dữ liệu (layer) trên bản đồ GIS công trình (liên kết Module 2). | Nội bộ, Quản lý |
| M3.18 | Thống kê tần suất/xu hướng dữ liệu theo mùa vụ | Tổng hợp thống kê so sánh dữ liệu thủy văn giữa các kỳ/mùa vụ (theo năm, theo tháng tương ứng của các năm) hỗ trợ phân tích xu hướng. | Quản lý, Ban giám đốc |

### **3.3.1. Danh sách tác nhân**

| Tác nhân | Mô tả |
| :---- | :---- |
| Hệ thống tự động (Job/Scheduler) | Thực hiện kết nối API, bóc tách, kiểm tra hợp lệ, chuẩn hóa và lưu dữ liệu thủy văn theo chu kỳ. |
| Quản trị viên hệ thống | Quản lý danh mục điểm đo/loại chỉ số, cấu hình kết nối API, ngưỡng cảnh báo, theo dõi nhật ký đồng bộ. |
| Cán bộ kỹ thuật | Cập nhật thông tin điểm đo, đối soát dữ liệu bất thường. |
| Nội bộ/Quản lý | Xem biểu đồ, dữ liệu thời gian thực, khai thác báo cáo. |
| Ban giám đốc | Xem báo cáo tổng hợp, thống kê xu hướng phục vụ ra quyết định điều hành. |
| Trực ban điều hành | Tiếp nhận cảnh báo ngưỡng thủy văn để xử lý kịp thời. |

### **3.3.2. Đặc tả Use Case chi tiết**

#### ***UC3.1: Quản lý danh mục điểm đo và loại chỉ số***

**Tác nhân:** Quản trị viên hệ thống, Cán bộ kỹ thuật

**Mô tả:** Thiết lập danh mục điểm đo/trạm đo và các loại chỉ số quan trắc làm cơ sở cho việc đồng bộ dữ liệu.

**Tiền điều kiện:** Người dùng có quyền quản trị hệ thống hoặc quản lý kỹ thuật.

**Luồng sự kiện chính:**

48. Quản trị viên định nghĩa các loại chỉ số đo (mực nước, lượng mưa, lưu lượng...) kèm đơn vị đo.

49. Cán bộ kỹ thuật thêm điểm đo mới: tên, vị trí, loại chỉ số theo dõi, đơn vị quản lý phụ trách.

50. Hệ thống lưu danh mục điểm đo, gán mã điểm đo duy nhất.

51. Quản trị viên ánh xạ (mapping) mã điểm đo nội bộ với mã điểm đo tương ứng phía API bên thứ 3\.

**Luồng thay thế / ngoại lệ:**

* Nếu mã điểm đo phía API bên thứ 3 không tồn tại hoặc đã được ánh xạ cho điểm đo khác, hệ thống báo lỗi.

**Hậu điều kiện:** Danh mục điểm đo sẵn sàng phục vụ đồng bộ dữ liệu tự động (UC3.2).

#### ***UC3.2: Đồng bộ dữ liệu thủy văn tự động từ bên thứ 3***

**Tác nhân:** Hệ thống tự động, Quản trị viên hệ thống

**Mô tả:** Định kỳ kết nối API bên thứ 3, lấy dữ liệu mực nước/lượng mưa, kiểm tra hợp lệ, chuẩn hóa và lưu vào cơ sở dữ liệu lịch sử.

**Tiền điều kiện:** Danh mục điểm đo và thông tin kết nối API (endpoint, khóa xác thực, chu kỳ đồng bộ) đã được cấu hình.

**Luồng sự kiện chính:**

52. Bộ lập lịch (Scheduler) kích hoạt tiến trình đồng bộ theo chu kỳ cấu hình (ví dụ mỗi 5–15 phút).

53. Hệ thống gửi yêu cầu tới API bên thứ 3 và nhận dữ liệu thô theo từng điểm đo đã ánh xạ.

54. Hệ thống bóc tách, chuyển đổi dữ liệu thô sang định dạng chuẩn nội bộ (điểm đo, thời điểm đo, giá trị, đơn vị).

55. Hệ thống kiểm tra tính hợp lệ của dữ liệu (khoảng giá trị cho phép, trùng lặp thời điểm).

56. Hệ thống lưu dữ liệu hợp lệ vào cơ sở dữ liệu lịch sử thủy văn và ghi nhật ký đồng bộ (kết quả, số bản ghi).

57. Hệ thống cập nhật thời điểm đồng bộ gần nhất để hiển thị trên dashboard giám sát thời gian thực.

**Luồng thay thế / ngoại lệ:**

* Nếu API bên thứ 3 không phản hồi hoặc lỗi xác thực: hệ thống ghi log lỗi, chuyển sang kịch bản UC3.5 (Xử lý gián đoạn kết nối).

* Nếu dữ liệu nhận về không hợp lệ (giá trị âm bất thường, thiếu trường): hệ thống bỏ qua bản ghi lỗi và ghi log cảnh báo dữ liệu.

**Hậu điều kiện:** Dữ liệu thủy văn mới nhất được lưu trữ và sẵn sàng phục vụ giám sát, biểu đồ, báo cáo, bản đồ GIS.

#### ***UC3.3: Giám sát thời gian thực và xem biểu đồ diễn biến***

**Tác nhân:** Nội bộ, Quản lý

**Mô tả:** Xem số liệu mực nước/lượng mưa mới nhất và biểu đồ diễn biến theo thời gian, có thể so sánh nhiều điểm đo.

**Tiền điều kiện:** Dữ liệu thủy văn đã được đồng bộ (UC3.2).

**Luồng sự kiện chính:**

58. Người dùng chọn điểm đo hoặc nhóm điểm đo cần xem.

59. Hệ thống hiển thị giá trị mới nhất (mực nước/lượng mưa) kèm thời điểm cập nhật.

60. Người dùng chọn khoảng thời gian (ngày/tuần/tháng/năm) để xem biểu đồ diễn biến.

61. Hệ thống truy vấn dữ liệu lịch sử và vẽ biểu đồ tương ứng.

62. Người dùng có thể chọn thêm điểm đo khác để so sánh trên cùng một biểu đồ.

**Luồng thay thế / ngoại lệ:**

* Nếu không có dữ liệu trong khoảng thời gian được chọn, hệ thống hiển thị thông báo "Không có dữ liệu" thay vì biểu đồ trống gây nhầm lẫn.

**Hậu điều kiện:** Người dùng có được thông tin trực quan phục vụ theo dõi và phân tích xu hướng thủy văn.

#### ***UC3.4: Xuất báo cáo khai thác dữ liệu thủy văn***

**Tác nhân:** Quản lý, Ban giám đốc

**Mô tả:** Tạo báo cáo định kỳ hoặc theo yêu cầu, cũng như thống kê xu hướng theo mùa vụ từ dữ liệu thủy văn lịch sử, hỗ trợ xuất Excel/PDF.

**Tiền điều kiện:** Dữ liệu thủy văn lịch sử đã có trong hệ thống.

**Luồng sự kiện chính:**

63. Người dùng chọn chức năng "Báo cáo thủy văn", chọn báo cáo định kỳ theo mẫu có sẵn hoặc thiết lập tham số tùy chọn (điểm đo, khoảng thời gian, loại chỉ số).

64. Hệ thống tổng hợp dữ liệu theo tham số đã chọn, bao gồm cả thống kê so sánh theo mùa vụ nếu được yêu cầu.

65. Hệ thống hiển thị báo cáo dạng bảng/biểu đồ trên màn hình.

66. Người dùng chọn xuất báo cáo ra tệp Excel hoặc PDF.

67. Hệ thống sinh tệp và cho phép tải về.

**Luồng thay thế / ngoại lệ:**

* Nếu người dùng thiết lập tham số không hợp lệ (ví dụ ngày kết thúc trước ngày bắt đầu), hệ thống báo lỗi và yêu cầu nhập lại.

**Hậu điều kiện:** Báo cáo được tạo và có thể lưu trữ/chia sẻ phục vụ ra quyết định quản lý điều hành.

#### ***UC3.5: Cấu hình ngưỡng và xử lý gián đoạn kết nối***

**Tác nhân:** Hệ thống tự động, Quản trị viên hệ thống, Trực ban điều hành

**Mô tả:** Phát hiện dữ liệu vượt ngưỡng cấu hình hoặc gián đoạn kết nối nguồn dữ liệu và thông báo kịp thời.

**Tiền điều kiện:** Ngưỡng cảnh báo theo từng điểm đo và cơ chế thử kết nối lại đã được Quản trị viên cấu hình.

**Luồng sự kiện chính:**

68. Sau mỗi lần đồng bộ dữ liệu, hệ thống so sánh giá trị mới với ngưỡng cảnh báo đã cấu hình cho từng điểm đo.

69. Nếu giá trị vượt ngưỡng, hệ thống tạo cảnh báo và gửi thông báo (trên hệ thống/email) tới Trực ban điều hành/Quản lý liên quan.

70. Song song, hệ thống theo dõi tình trạng kết nối API; nếu phát hiện gián đoạn, ghi nhận thời điểm mất kết nối vào nhật ký đồng bộ.

71. Hệ thống tự động thử kết nối lại theo chu kỳ đã cấu hình.

72. Khi kết nối phục hồi, hệ thống ghi log phục hồi và tiếp tục đồng bộ dữ liệu bình thường.

**Luồng thay thế / ngoại lệ:**

* Nếu gián đoạn kéo dài quá ngưỡng thời gian cấu hình, hệ thống gửi cảnh báo cấp cao hơn tới Quản trị viên hệ thống.

**Hậu điều kiện:** Sự cố dữ liệu/kết nối được phát hiện và xử lý kịp thời, giảm thiểu rủi ro thiếu dữ liệu phục vụ điều hành.

#### ***UC3.6: Hiển thị dữ liệu thủy văn trên bản đồ GIS***

**Tác nhân:** Nội bộ, Quản lý

**Mô tả:** Hiển thị số liệu thủy văn mới nhất của từng điểm đo như một lớp dữ liệu trên bản đồ GIS công trình.

**Tiền điều kiện:** Điểm đo đã được số hóa tọa độ; dữ liệu thủy văn đã được đồng bộ.

**Luồng sự kiện chính:**

73. Người dùng mở bản đồ GIS (Module 2), bật lớp dữ liệu "Điểm đo thủy văn".

74. Hệ thống hiển thị vị trí các điểm đo cùng giá trị đo mới nhất trên bản đồ.

75. Người dùng nhấp vào một điểm đo để xem nhanh biểu đồ diễn biến gần nhất.

**Hậu điều kiện:** Người dùng có cái nhìn không gian trực quan kết hợp giữa vị trí công trình và số liệu thủy văn.

### **3.3.3. Quy tắc nghiệp vụ**

* Mỗi bản ghi dữ liệu thủy văn phải gắn với một điểm đo cụ thể và một mốc thời gian (timestamp) duy nhất; không cho phép trùng lặp thời điểm đo trên cùng một điểm đo.

* Mỗi điểm đo chỉ được ánh xạ với đúng một mã điểm đo phía API bên thứ 3\.

* Ngưỡng cảnh báo được cấu hình riêng theo từng điểm đo và từng loại chỉ số (không dùng chung một ngưỡng cho toàn hệ thống).

* Dữ liệu không hợp lệ (ngoài khoảng giá trị vật lý cho phép) không được đưa vào cơ sở dữ liệu lịch sử chính thức, nhưng vẫn cần lưu log để phục vụ đối soát.

* Chu kỳ đồng bộ và số lần thử kết nối lại tối đa khi gián đoạn phải có thể cấu hình được, không hard-code trong mã nguồn.

* Mọi lần đồng bộ (thành công hay thất bại) đều phải được ghi vào nhật ký đồng bộ để phục vụ giám sát vận hành.

### **3.3.4. Yêu cầu dữ liệu – Điểm đo & Bản ghi thủy văn**

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :---- | :---- | ----- | :---- |
| Mã điểm đo | Chuỗi ký tự | x | Duy nhất |
| Tên/Vị trí điểm đo | Chuỗi ký tự | x |  |
| Loại chỉ số | Danh sách chọn | x | Mực nước / Lượng mưa / Lưu lượng / Khác |
| Mã ánh xạ API bên thứ 3 | Chuỗi ký tự | x | Dùng để đối chiếu khi đồng bộ |
| Tọa độ điểm đo | Số thực |  | Phục vụ hiển thị trên bản đồ GIS |
| Thời điểm đo | Ngày giờ | x | Gắn timestamp nguồn |
| Giá trị đo | Số thực | x | Kèm đơn vị đo |
| Ngưỡng cảnh báo (thấp/cao) | Số thực |  | Cấu hình theo điểm đo và loại chỉ số |
| Trạng thái bản ghi | Danh sách chọn | x | Hợp lệ / Nghi ngờ / Loại bỏ |
| Nguồn dữ liệu | Chuỗi ký tự | x | Tên/địa chỉ API bên thứ 3 |
| Nhật ký đồng bộ | Danh sách bản ghi |  | Thời gian, kết quả, số bản ghi đồng bộ |

## **3.4. Module 4 – Quản lý nhân sự (HRM)**

*Quản lý sơ đồ tổ chức, hồ sơ cán bộ nhân viên, quá trình công tác, đào tạo, hợp đồng, danh bạ nội bộ, phân quyền xem hồ sơ và báo cáo thống kê nhân sự đa chiều.*

| Mã YC | Chức năng | Mô tả yêu cầu | Vai trò sử dụng |
| :---- | :---- | :---- | :---- |
| M4.1 | Quản lý sơ đồ tổ chức | Xây dựng và hiển thị cây tổ chức: các đơn vị, phòng ban trực thuộc, thêm/sửa/xóa và sắp xếp cấu trúc phân cấp. | Quản trị nhân sự |
| M4.2 | Quản lý hồ sơ cán bộ nhân viên | Lưu trữ thông tin cá nhân cơ bản, phòng ban, chức vụ của từng cán bộ nhân viên. | Quản trị nhân sự |
| M4.3 | Quản lý lý lịch và trình độ chuyên môn | Lưu trữ chi tiết quá trình học tập, bằng cấp, trình độ chuyên môn, ngoại ngữ, tin học của từng cán bộ. | Quản trị nhân sự |
| M4.4 | Quản lý lịch sử điều động, bổ nhiệm | Ghi nhận quá trình điều động, bổ nhiệm, luân chuyển vị trí công tác theo thời gian. | Quản trị nhân sự |
| M4.5 | Quản lý khen thưởng, kỷ luật | Ghi nhận các quyết định khen thưởng, kỷ luật gắn với hồ sơ cán bộ theo thời gian. | Quản trị nhân sự |
| M4.6 | Quản lý quá trình đào tạo, bồi dưỡng | Ghi nhận các khóa đào tạo, bồi dưỡng nghiệp vụ mà cán bộ đã tham gia, kèm chứng chỉ liên quan. | Quản trị nhân sự |
| M4.7 | Quản lý hợp đồng lao động | Số hóa, lưu trữ và theo dõi hiệu lực hợp đồng lao động của từng cán bộ nhân viên. | Quản trị nhân sự |
| M4.8 | Quản lý giấy tờ/chứng chỉ số hóa | Lưu trữ bản số hóa các giấy tờ liên quan (bằng cấp, chứng chỉ, giấy khám sức khỏe...) gắn với hồ sơ nhân viên. | Quản trị nhân sự |
| M4.9 | Cảnh báo hợp đồng/giấy tờ sắp hết hạn | Tự động rà soát và cảnh báo các hợp đồng, chứng chỉ, giấy tờ sắp hết hạn theo ngưỡng thời gian cấu hình. | Quản trị nhân sự |
| M4.10 | Quản lý nghỉ phép | Ghi nhận và tra cứu thông tin ngày phép, tình trạng nghỉ phép của cán bộ nhân viên. | Quản trị nhân sự |
| M4.11 | Danh bạ nội bộ | Cung cấp danh bạ tra cứu thông tin liên hệ (điện thoại, email nội bộ, phòng ban) của cán bộ nhân viên. | Toàn bộ nhân viên nội bộ |
| M4.12 | Tìm kiếm và lọc hồ sơ nhân sự | Tìm kiếm, lọc hồ sơ nhân viên theo tên, mã nhân viên, phòng ban, chức vụ, trình độ. | Quản trị nhân sự |
| M4.13 | Phân quyền xem hồ sơ theo cấp quản lý | Giới hạn quyền xem/sửa hồ sơ nhân sự theo cấp quản lý (chỉ xem được nhân sự thuộc đơn vị mình phụ trách). | Quản trị nhân sự, Quản trị viên hệ thống |
| M4.14 | Báo cáo thống kê nhân sự theo phòng ban | Tổng hợp số lượng, cơ cấu nhân sự theo từng phòng ban, đơn vị. | Quản trị nhân sự, Ban giám đốc |
| M4.15 | Báo cáo thống kê theo trình độ/độ tuổi | Tổng hợp cơ cấu nhân sự theo trình độ chuyên môn, nhóm độ tuổi, giới tính. | Quản trị nhân sự, Ban giám đốc |
| M4.16 | Báo cáo biến động nhân sự | Thống kê biến động tuyển mới/nghỉ việc/điều chuyển theo kỳ báo cáo (tháng/quý/năm). | Quản trị nhân sự, Ban giám đốc |
| M4.17 | Xuất báo cáo nhân sự (Excel/PDF) | Hỗ trợ xuất mọi loại báo cáo, danh sách nhân sự ra định dạng Excel hoặc PDF. | Quản trị nhân sự, Ban giám đốc |

### **3.4.1. Danh sách tác nhân**

| Tác nhân | Mô tả |
| :---- | :---- |
| Quản trị nhân sự | Quản lý toàn bộ dữ liệu sơ đồ tổ chức, hồ sơ, lý lịch, đào tạo, hợp đồng, báo cáo nhân sự. |
| Ban giám đốc | Xem báo cáo thống kê nhân sự tổng hợp phục vụ ra quyết định. |
| Nhân viên nội bộ | Tra cứu danh bạ nội bộ, xem thông tin hồ sơ cá nhân của chính mình. |
| Quản trị viên hệ thống | Cấu hình phân quyền xem hồ sơ theo cấp quản lý. |

### **3.4.2. Đặc tả Use Case chi tiết**

#### ***UC4.1: Quản lý sơ đồ tổ chức***

**Tác nhân:** Quản trị nhân sự

**Mô tả:** Xây dựng, cập nhật cây tổ chức các đơn vị, phòng ban trực thuộc Công ty.

**Tiền điều kiện:** Người dùng có quyền quản trị nhân sự.

**Luồng sự kiện chính:**

76. Quản trị nhân sự chọn "Sơ đồ tổ chức", thêm đơn vị/phòng ban mới, chỉ định đơn vị cha (nếu có).

77. Hệ thống hiển thị cây tổ chức cập nhật.

78. Quản trị nhân sự có thể kéo-thả hoặc chỉnh sửa để sắp xếp lại cấu trúc phân cấp.

79. Quản trị nhân sự gán trưởng đơn vị/phòng ban (tham chiếu tới hồ sơ cán bộ).

**Luồng thay thế / ngoại lệ:**

* Nếu xóa một đơn vị đang có nhân viên/công trình liên kết, hệ thống cảnh báo và yêu cầu chuyển dữ liệu liên kết trước khi xóa.

**Hậu điều kiện:** Sơ đồ tổ chức phản ánh đúng cấu trúc hiện hành, làm cơ sở tham chiếu cho hồ sơ nhân viên và phân quyền.

#### ***UC4.2: Quản lý hồ sơ cán bộ, lý lịch và quá trình công tác***

**Tác nhân:** Quản trị nhân sự

**Mô tả:** Tạo, cập nhật hồ sơ cá nhân, lý lịch, trình độ chuyên môn và ghi nhận quá trình điều động, bổ nhiệm, khen thưởng, kỷ luật của cán bộ nhân viên.

**Tiền điều kiện:** Sơ đồ tổ chức đã được thiết lập (UC4.1).

**Luồng sự kiện chính:**

80. Quản trị nhân sự chọn "Thêm hồ sơ nhân viên mới", nhập thông tin cá nhân, phòng ban, chức vụ.

81. Hệ thống lưu hồ sơ và gán mã nhân viên tự động (duy nhất).

82. Quản trị nhân sự bổ sung chi tiết lý lịch: trình độ chuyên môn, bằng cấp, ngoại ngữ, tin học.

83. Quản trị nhân sự ghi nhận các sự kiện công tác theo thời gian: điều động, bổ nhiệm, khen thưởng, kỷ luật.

84. Quản trị nhân sự/Nhân viên tìm kiếm hồ sơ theo tên, mã nhân viên, phòng ban, chức vụ, trình độ khi cần tra cứu.

**Luồng thay thế / ngoại lệ:**

* Nếu thông tin bắt buộc (họ tên, mã nhân viên) bị thiếu hoặc mã nhân viên trùng, hệ thống báo lỗi và không cho lưu.

**Hậu điều kiện:** Hồ sơ cán bộ được lưu trữ đầy đủ, có thể truy vết lịch sử công tác qua thời gian.

#### ***UC4.3: Quản lý đào tạo, bồi dưỡng cán bộ***

**Tác nhân:** Quản trị nhân sự

**Mô tả:** Ghi nhận các khóa đào tạo, bồi dưỡng nghiệp vụ mà cán bộ tham gia và chứng chỉ đạt được.

**Tiền điều kiện:** Hồ sơ nhân viên đã tồn tại (UC4.2).

**Luồng sự kiện chính:**

85. Quản trị nhân sự thêm bản ghi khóa đào tạo: tên khóa học, đơn vị tổ chức, thời gian, kết quả.

86. Quản trị nhân sự đính kèm chứng chỉ/giấy chứng nhận hoàn thành (nếu có).

87. Hệ thống lưu và hiển thị lịch sử đào tạo trên hồ sơ cán bộ.

**Hậu điều kiện:** Lịch sử đào tạo được lưu trữ đầy đủ, hỗ trợ lập kế hoạch phát triển nhân sự.

#### ***UC4.4: Quản lý hợp đồng và giấy tờ liên quan***

**Tác nhân:** Quản trị nhân sự

**Mô tả:** Số hóa, lưu trữ và theo dõi hiệu lực hợp đồng lao động cùng các giấy tờ liên quan; cảnh báo trước hạn.

**Tiền điều kiện:** Hồ sơ nhân viên đã tồn tại (UC4.2).

**Luồng sự kiện chính:**

88. Quản trị nhân sự tải lên bản scan/số hóa hợp đồng lao động, bằng cấp, chứng chỉ gắn với hồ sơ nhân viên.

89. Quản trị nhân sự nhập ngày hiệu lực, ngày hết hạn hợp đồng/giấy tờ.

90. Hệ thống tự động rà soát các hợp đồng/giấy tờ sắp hết hạn (theo ngưỡng cấu hình, ví dụ 30 ngày trước hạn) và gửi cảnh báo cho Quản trị nhân sự.

91. Quản trị nhân sự ghi nhận thông tin nghỉ phép liên quan (nếu có) trên cùng hồ sơ.

**Luồng thay thế / ngoại lệ:**

* Nếu hợp đồng đã hết hạn mà chưa được gia hạn/cập nhật, hệ thống đánh dấu trạng thái "Hết hạn" và tiếp tục cảnh báo định kỳ.

**Hậu điều kiện:** Hồ sơ hợp đồng/giấy tờ được lưu trữ số hóa, giảm rủi ro bỏ sót hạn hợp đồng.

#### ***UC4.5: Phân quyền xem hồ sơ theo cấp quản lý***

**Tác nhân:** Quản trị viên hệ thống, Quản trị nhân sự

**Mô tả:** Giới hạn phạm vi xem/sửa hồ sơ nhân sự theo cấp quản lý và đơn vị phụ trách.

**Tiền điều kiện:** Sơ đồ tổ chức và tài khoản người dùng đã được thiết lập.

**Luồng sự kiện chính:**

92. Quản trị viên hệ thống cấu hình quy tắc phân quyền: quản lý cấp Xí nghiệp/Cụm chỉ xem được hồ sơ nhân sự thuộc đơn vị mình.

93. Hệ thống áp dụng quy tắc khi người dùng truy cập danh sách/hồ sơ nhân sự.

**Luồng thay thế / ngoại lệ:**

* Nếu người dùng cố truy cập hồ sơ ngoài phạm vi được phân quyền, hệ thống từ chối truy cập và ghi log.

**Hậu điều kiện:** Dữ liệu nhân sự được bảo vệ theo đúng phạm vi trách nhiệm quản lý.

#### ***UC4.6: Tra cứu danh bạ và xuất báo cáo thống kê nhân sự***

**Tác nhân:** Nhân viên nội bộ, Quản trị nhân sự, Ban giám đốc

**Mô tả:** Tra cứu thông tin liên hệ nội bộ và tạo báo cáo thống kê nhân sự đa chiều (phòng ban, trình độ, độ tuổi, biến động).

**Tiền điều kiện:** Dữ liệu hồ sơ nhân viên đã đầy đủ.

**Luồng sự kiện chính:**

94. Nhân viên nội bộ tìm kiếm đồng nghiệp theo tên/phòng ban trong danh bạ, xem thông tin liên hệ công vụ.

95. Quản trị nhân sự chọn "Báo cáo thống kê nhân sự", thiết lập tham số (kỳ báo cáo, phòng ban, tiêu chí thống kê: trình độ/độ tuổi/biến động).

96. Hệ thống tổng hợp và hiển thị báo cáo dạng bảng/biểu đồ.

97. Ban giám đốc xem báo cáo tổng hợp hoặc yêu cầu xuất báo cáo dạng tệp Excel/PDF.

**Hậu điều kiện:** Người dùng có thông tin liên hệ nhanh chóng; Ban giám đốc có số liệu nhân sự phục vụ ra quyết định.

### **3.4.3. Quy tắc nghiệp vụ**

* Mỗi nhân viên có một mã nhân viên duy nhất, không thay đổi trong suốt quá trình công tác.

* Thông tin cá nhân nhạy cảm (lý lịch, lương – nếu có trong phạm vi mở rộng) chỉ được truy cập bởi Quản trị nhân sự và chính nhân viên đó, tuân thủ nguyên tắc phân quyền tối thiểu.

* Quản lý cấp Xí nghiệp/Cụm chỉ được xem hồ sơ nhân sự thuộc đơn vị mình phụ trách, không xem được toàn bộ dữ liệu nhân sự Công ty.

* Hệ thống phải cảnh báo trước khi hợp đồng lao động hoặc giấy tờ quan trọng hết hạn theo ngưỡng thời gian có thể cấu hình.

* Danh bạ nội bộ chỉ hiển thị thông tin liên hệ công vụ (điện thoại/email nội bộ), không hiển thị dữ liệu cá nhân nhạy cảm khác.

* Mọi thay đổi trên hồ sơ nhân sự (lịch sử công tác, khen thưởng/kỷ luật) phải được lưu vết theo thời gian, không cho phép ghi đè mất dấu vết cũ.

### **3.4.4. Yêu cầu dữ liệu – Hồ sơ cán bộ nhân viên**

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :---- | :---- | ----- | :---- |
| Mã nhân viên | Chuỗi ký tự | x | Duy nhất |
| Họ và tên | Chuỗi ký tự | x |  |
| Phòng ban/Đơn vị | Tham chiếu Sơ đồ tổ chức | x |  |
| Chức vụ | Chuỗi ký tự | x |  |
| Ngày vào làm việc | Ngày | x |  |
| Trình độ chuyên môn/Bằng cấp | Danh sách bản ghi |  | Có thể nhiều bằng cấp |
| Điện thoại/Email nội bộ | Chuỗi ký tự |  | Hiển thị trong danh bạ |
| Lịch sử điều động/bổ nhiệm | Danh sách bản ghi |  | Theo thời gian |
| Lịch sử khen thưởng/kỷ luật | Danh sách bản ghi |  | Theo thời gian |
| Lịch sử đào tạo | Danh sách bản ghi |  | Tên khóa học, thời gian, kết quả, chứng chỉ |
| Hợp đồng lao động | Danh sách tệp \+ metadata |  | Ngày hiệu lực, ngày hết hạn |
| Giấy tờ/chứng chỉ số hóa | Danh sách tệp |  | Bằng cấp, chứng chỉ, giấy khám sức khỏe... |
| Thông tin nghỉ phép | Danh sách bản ghi |  | Ngày nghỉ, loại phép, số ngày còn lại |
| Trạng thái làm việc | Danh sách chọn | x | Đang làm việc / Nghỉ việc / Nghỉ phép dài hạn |

## **3.5. Module 5 – Quản trị hệ thống, tài khoản và phân quyền**

*Cấu hình hệ thống; quản lý tài khoản, nhóm quyền chi tiết theo vai trò; giám sát tình trạng hệ thống; theo dõi nhật ký hoạt động, bảo mật đăng nhập; sao lưu và khôi phục dữ liệu.*

| Mã YC | Chức năng | Mô tả yêu cầu | Vai trò sử dụng |
| :---- | :---- | :---- | :---- |
| M5.1 | Quản lý tài khoản người dùng | Tạo, sửa, khóa/mở khóa, đặt lại mật khẩu cho tài khoản người dùng nội bộ. | Quản trị viên hệ thống |
| M5.2 | Phân quyền theo vai trò (RBAC) | Định nghĩa vai trò (Role), gán quyền truy cập chức năng/dữ liệu tương ứng cho từng vai trò; gán vai trò cho tài khoản. | Quản trị viên hệ thống |
| M5.3 | Quản lý nhóm quyền chi tiết theo màn hình/chức năng | Cấu hình chi tiết quyền xem/thêm/sửa/xóa/xuất dữ liệu cho từng màn hình, từng chức năng trong hệ thống. | Quản trị viên hệ thống |
| M5.4 | Cấu hình thông tin chung hệ thống | Thiết lập thông tin Công ty, logo, thông tin liên hệ hiển thị trên cổng thông tin. | Quản trị viên hệ thống |
| M5.5 | Cấu hình chu kỳ đồng bộ dữ liệu thủy văn | Thiết lập chu kỳ, thời gian chờ, số lần thử lại khi đồng bộ dữ liệu từ API bên thứ 3\. | Quản trị viên hệ thống |
| M5.6 | Cấu hình ngưỡng cảnh báo mặc định | Thiết lập giá trị ngưỡng cảnh báo mặc định áp dụng khi tạo điểm đo thủy văn mới. | Quản trị viên hệ thống |
| M5.7 | Nhật ký hoạt động (Audit Log) | Ghi nhận lịch sử thao tác quan trọng của người dùng trên hệ thống (đăng nhập, thêm/sửa/xóa dữ liệu, thay đổi phân quyền). | Quản trị viên hệ thống |
| M5.8 | Tra cứu và lọc nhật ký hoạt động | Tìm kiếm, lọc nhật ký theo người dùng, khoảng thời gian, loại thao tác, module liên quan. | Quản trị viên hệ thống |
| M5.9 | Sao lưu dữ liệu tự động theo lịch | Thực hiện sao lưu định kỳ tự động đối với cơ sở dữ liệu hệ thống theo lịch cấu hình. | Hệ thống tự động |
| M5.10 | Sao lưu dữ liệu theo yêu cầu | Cho phép Quản trị viên chủ động thực hiện sao lưu ngay khi cần (trước khi nâng cấp, thay đổi lớn). | Quản trị viên hệ thống |
| M5.11 | Khôi phục dữ liệu (Restore) | Cho phép khôi phục dữ liệu từ bản sao lưu đã chọn khi xảy ra sự cố. | Quản trị viên hệ thống |
| M5.12 | Giám sát tình trạng hệ thống | Theo dõi tình trạng hoạt động (health check) của các dịch vụ, API tích hợp (văn bản điều hành, thủy văn), cảnh báo khi dịch vụ gặp sự cố. | Quản trị viên hệ thống |
| M5.13 | Quản lý thông báo hệ thống | Gửi và quản lý các thông báo chung của hệ thống tới người dùng (bảo trì, cập nhật phiên bản...). | Quản trị viên hệ thống |
| M5.14 | Quản lý phiên đăng nhập | Theo dõi các phiên đăng nhập đang hoạt động của người dùng, cho phép đăng xuất từ xa khi cần. | Quản trị viên hệ thống |
| M5.15 | Chính sách mật khẩu và bảo mật tài khoản | Cấu hình độ phức tạp mật khẩu, thời hạn đổi mật khẩu định kỳ, khóa tài khoản sau nhiều lần đăng nhập sai. | Quản trị viên hệ thống |
| M5.16 | Cảnh báo đăng nhập bất thường | Phát hiện và cảnh báo các dấu hiệu đăng nhập bất thường (nhiều lần sai mật khẩu, đăng nhập ngoài giờ...). | Hệ thống tự động, Quản trị viên hệ thống |
| M5.17 | Xuất/nhập cấu hình hệ thống | Cho phép xuất và nhập lại bộ cấu hình hệ thống, hỗ trợ sao lưu cấu hình hoặc chuyển đổi môi trường (staging/production). | Quản trị viên hệ thống |

### **3.5.1. Danh sách tác nhân**

| Tác nhân | Mô tả |
| :---- | :---- |
| Quản trị viên hệ thống (Super Admin) | Toàn quyền cấu hình, quản lý tài khoản/phân quyền, giám sát và vận hành hệ thống. |
| Hệ thống tự động | Thực hiện sao lưu định kỳ, ghi nhật ký hoạt động, giám sát tình trạng dịch vụ, phát hiện đăng nhập bất thường. |

### **3.5.2. Đặc tả Use Case chi tiết**

#### ***UC5.1: Quản lý tài khoản và phân quyền chi tiết theo vai trò***

**Tác nhân:** Quản trị viên hệ thống

**Mô tả:** Tạo, quản lý tài khoản người dùng, định nghĩa vai trò và cấu hình chi tiết quyền truy cập theo từng màn hình/chức năng (RBAC).

**Tiền điều kiện:** Quản trị viên đã đăng nhập với quyền quản trị hệ thống.

**Luồng sự kiện chính:**

98. Quản trị viên chọn "Quản lý vai trò", định nghĩa vai trò mới (ví dụ: Biên tập viên, Quản trị nội dung, Cán bộ kỹ thuật, Quản trị nhân sự...).

99. Quản trị viên cấu hình chi tiết quyền xem/thêm/sửa/xóa/xuất dữ liệu cho từng màn hình, từng chức năng gán cho vai trò đó.

100. Quản trị viên chọn "Tạo tài khoản mới", nhập thông tin người dùng (họ tên, email/tên đăng nhập, phòng ban) và gán một hoặc nhiều vai trò.

101. Hệ thống sinh mật khẩu tạm thời hoặc gửi email kích hoạt tài khoản, áp dụng chính sách mật khẩu đã cấu hình.

102. Quản trị viên có thể khóa/mở khóa hoặc đặt lại mật khẩu tài khoản khi cần.

103. Người dùng đăng nhập; hệ thống kiểm tra vai trò và quyền chi tiết được gán để hiển thị đúng chức năng/dữ liệu được phép truy cập.

**Luồng thay thế / ngoại lệ:**

* Nếu email/tên đăng nhập đã tồn tại, hệ thống báo lỗi khi tạo tài khoản mới.

* Nếu tài khoản bị khóa hoặc đăng nhập sai quá số lần cho phép, hệ thống từ chối đăng nhập và hiển thị thông báo phù hợp.

**Hậu điều kiện:** Tài khoản và phân quyền chi tiết được thiết lập đúng theo vai trò công việc của từng người dùng.

#### ***UC5.2: Cấu hình tham số vận hành hệ thống***

**Tác nhân:** Quản trị viên hệ thống

**Mô tả:** Thiết lập các tham số vận hành chung: thông tin Công ty, chu kỳ đồng bộ dữ liệu thủy văn, ngưỡng cảnh báo mặc định, chu kỳ sao lưu.

**Tiền điều kiện:** Quản trị viên có quyền cấu hình hệ thống.

**Luồng sự kiện chính:**

104. Quản trị viên truy cập màn hình "Cấu hình hệ thống".

105. Quản trị viên cập nhật thông tin Công ty (tên, logo, liên hệ hiển thị trên cổng thông tin).

106. Quản trị viên cấu hình chu kỳ đồng bộ dữ liệu thủy văn, ngưỡng cảnh báo mặc định áp dụng cho điểm đo mới, chu kỳ sao lưu.

107. Hệ thống lưu cấu hình và áp dụng ngay hoặc theo lịch hiệu lực đã chọn.

108. Quản trị viên có thể xuất bộ cấu hình hiện tại ra tệp hoặc nhập lại cấu hình từ tệp đã lưu.

**Luồng thay thế / ngoại lệ:**

* Nếu giá trị cấu hình nhập vào không hợp lệ (ví dụ chu kỳ âm), hệ thống báo lỗi và giữ nguyên cấu hình cũ.

**Hậu điều kiện:** Tham số vận hành hệ thống được cập nhật, áp dụng thống nhất cho toàn bộ các module liên quan.

#### ***UC5.3: Giám sát tình trạng hệ thống và quản lý thông báo***

**Tác nhân:** Quản trị viên hệ thống, Hệ thống tự động

**Mô tả:** Theo dõi tình trạng hoạt động của các dịch vụ/API tích hợp và gửi thông báo chung tới người dùng khi cần.

**Tiền điều kiện:** Các dịch vụ/API tích hợp đã được cấu hình kết nối.

**Luồng sự kiện chính:**

109. Hệ thống định kỳ kiểm tra tình trạng (health check) các dịch vụ/API tích hợp (văn bản điều hành, dữ liệu thủy văn).

110. Nếu phát hiện dịch vụ gặp sự cố, hệ thống cảnh báo cho Quản trị viên.

111. Quản trị viên có thể soạn và gửi thông báo chung của hệ thống (bảo trì, cập nhật phiên bản) tới toàn bộ hoặc một nhóm người dùng.

**Hậu điều kiện:** Quản trị viên nắm được tình trạng vận hành tổng thể của hệ thống và có thể chủ động thông báo cho người dùng.

#### ***UC5.4: Tra cứu nhật ký hoạt động***

**Tác nhân:** Quản trị viên hệ thống

**Mô tả:** Ghi nhận và cho phép tra cứu lịch sử thao tác quan trọng của người dùng trên hệ thống.

**Tiền điều kiện:** Hệ thống đã ghi log các thao tác theo thời gian thực.

**Luồng sự kiện chính:**

112. Hệ thống tự động ghi lại mỗi thao tác quan trọng (đăng nhập/đăng xuất, thêm/sửa/xóa dữ liệu, thay đổi phân quyền) kèm người thực hiện, thời gian, module liên quan.

113. Quản trị viên truy cập "Nhật ký hoạt động", lọc theo người dùng, khoảng thời gian, loại thao tác hoặc module.

114. Quản trị viên xem chi tiết một bản ghi log để phục vụ điều tra sự cố hoặc kiểm tra tuân thủ.

**Hậu điều kiện:** Toàn bộ thao tác nhạy cảm trên hệ thống có thể truy vết đầy đủ, phục vụ kiểm tra và trách nhiệm giải trình.

#### ***UC5.5: Quản lý phiên đăng nhập và cảnh báo bảo mật***

**Tác nhân:** Quản trị viên hệ thống, Hệ thống tự động

**Mô tả:** Theo dõi các phiên đăng nhập đang hoạt động và phát hiện dấu hiệu đăng nhập bất thường.

**Tiền điều kiện:** Chính sách bảo mật tài khoản đã được cấu hình.

**Luồng sự kiện chính:**

115. Hệ thống ghi nhận mỗi phiên đăng nhập (thời gian, thiết bị/IP nếu xác định được).

116. Quản trị viên xem danh sách phiên đăng nhập đang hoạt động, có thể chọn đăng xuất từ xa một phiên bất kỳ.

117. Hệ thống tự động phát hiện dấu hiệu bất thường (nhiều lần sai mật khẩu liên tiếp, đăng nhập ngoài giờ hành chính) và gửi cảnh báo cho Quản trị viên.

**Luồng thay thế / ngoại lệ:**

* Nếu phát hiện quá số lần đăng nhập sai cho phép, hệ thống tạm khóa tài khoản theo chính sách bảo mật đã cấu hình.

**Hậu điều kiện:** Rủi ro truy cập trái phép được phát hiện và ngăn chặn kịp thời.

#### ***UC5.6: Sao lưu và khôi phục dữ liệu***

**Tác nhân:** Hệ thống tự động, Quản trị viên hệ thống

**Mô tả:** Thực hiện sao lưu định kỳ hoặc theo yêu cầu đối với cơ sở dữ liệu hệ thống và khôi phục khi xảy ra sự cố.

**Tiền điều kiện:** Chu kỳ sao lưu đã được cấu hình (UC5.2).

**Luồng sự kiện chính:**

118. Hệ thống tự động thực hiện sao lưu (backup) toàn bộ hoặc một phần cơ sở dữ liệu theo lịch đã cấu hình.

119. Quản trị viên có thể chủ động thực hiện sao lưu theo yêu cầu trước khi nâng cấp hoặc thay đổi lớn.

120. Hệ thống lưu trữ bản sao lưu tại vị trí đã cấu hình và ghi log kết quả sao lưu (thành công/thất bại).

121. Khi xảy ra sự cố mất/hỏng dữ liệu, Quản trị viên chọn bản sao lưu phù hợp và thực hiện thao tác "Khôi phục dữ liệu".

122. Hệ thống khôi phục dữ liệu từ bản sao lưu đã chọn và thông báo kết quả.

**Luồng thay thế / ngoại lệ:**

* Nếu quá trình sao lưu thất bại, hệ thống gửi cảnh báo ngay cho Quản trị viên để xử lý kịp thời, tránh gián đoạn kế hoạch sao lưu tiếp theo.

**Hậu điều kiện:** Dữ liệu hệ thống được bảo vệ và có thể khôi phục khi cần, giảm thiểu rủi ro mất dữ liệu.

### **3.5.3. Quy tắc nghiệp vụ**

* Một tài khoản có thể được gán nhiều vai trò; quyền truy cập thực tế là hợp của quyền các vai trò được gán.

* Không cho phép xóa vĩnh viễn tài khoản đã có lịch sử thao tác trên hệ thống; chỉ cho phép khóa (vô hiệu hóa) để đảm bảo tính toàn vẹn của nhật ký hoạt động.

* Nhật ký hoạt động không được phép chỉnh sửa hoặc xóa bởi bất kỳ vai trò nào, kể cả Quản trị viên hệ thống, nhằm đảm bảo tính toàn vẹn phục vụ kiểm tra/kiểm toán.

* Bản sao lưu dữ liệu cần được lưu trữ tách biệt khỏi máy chủ vận hành chính để đảm bảo an toàn khi xảy ra sự cố hạ tầng.

* Chính sách mật khẩu (độ phức tạp, thời hạn đổi, số lần đăng nhập sai tối đa) phải có thể cấu hình được, không hard-code trong mã nguồn.

* Cảnh báo đăng nhập bất thường phải được gửi gần thời gian thực (near real-time), không chờ xử lý theo lô.

### **3.5.4. Yêu cầu dữ liệu – Tài khoản & Vai trò**

| Trường dữ liệu | Kiểu dữ liệu | Bắt buộc | Ghi chú |
| :---- | :---- | ----- | :---- |
| Tên đăng nhập/Email | Chuỗi ký tự | x | Duy nhất |
| Họ và tên | Chuỗi ký tự | x |  |
| Vai trò (Role) | Danh sách tham chiếu | x | Có thể gán nhiều vai trò |
| Phòng ban | Tham chiếu Sơ đồ tổ chức |  |  |
| Trạng thái tài khoản | Danh sách chọn | x | Đang hoạt động / Khóa |
| Lần đăng nhập gần nhất | Ngày giờ |  | Tự động cập nhật |
| Danh sách phiên đăng nhập | Danh sách bản ghi |  | Thời gian, thiết bị/IP |
| Danh sách quyền theo Vai trò | Danh sách chức năng/module | x | Cấu hình dạng ma trận quyền chi tiết theo màn hình |
| Nhật ký hoạt động | Danh sách bản ghi | x | Người thực hiện, thời gian, thao tác, module |

# **4\. YÊU CẦU PHI CHỨC NĂNG**

## **4.1. Hiệu năng**

* Thời gian tải trang chủ cổng thông tin không quá 3 giây trong điều kiện mạng bình thường.

* Hệ thống đáp ứng tối thiểu 200 người dùng truy cập đồng thời không suy giảm hiệu năng đáng kể (mục tiêu cần khảo sát, thống nhất thêm với Công ty).

* Việc đọc và lưu dữ liệu thủy văn thời gian thực không làm chậm các chức năng khác của hệ thống.

## **4.2. Bảo mật**

* Xác thực người dùng nội bộ qua tài khoản/mật khẩu, hỗ trợ mã hóa mật khẩu (hashing) và có thể mở rộng xác thực 2 lớp (2FA) trong tương lai.

* Phân quyền truy cập chức năng và dữ liệu theo vai trò (RBAC), đảm bảo nguyên tắc "chỉ cấp quyền tối thiểu cần thiết".

* Toàn bộ giao tiếp giữa trình duyệt và máy chủ sử dụng giao thức HTTPS.

* Ghi log đầy đủ các thao tác nhạy cảm (đăng nhập, thay đổi phân quyền, xóa dữ liệu).

## **4.3. Khả năng sử dụng (Usability)**

* Giao diện thiết kế theo hướng hiện đại, thân thiện, nhất quán giữa các module.

* Responsive Web Design: hiển thị tối ưu trên desktop, tablet và mobile.

* Hỗ trợ tài liệu hướng dẫn sử dụng và đào tạo cho quản trị viên và người dùng cuối.

## **4.4. Độ tin cậy và khả năng sẵn sàng**

* Hệ thống hoạt động ổn định với thời gian sẵn sàng (uptime) mục tiêu tối thiểu 99% (không tính thời gian bảo trì đã lên lịch).

* Có cơ chế sao lưu dữ liệu định kỳ và quy trình khôi phục khi xảy ra sự cố (Module 5).

* Xử lý gián đoạn kết nối với nguồn dữ liệu thủy văn bên thứ 3 mà không làm gián đoạn hoạt động toàn hệ thống.

## **4.5. Khả năng mở rộng và bảo trì**

* Kiến trúc hệ thống cho phép bổ sung thêm module hoặc mở rộng số lượng điểm đo thủy văn, công trình trong tương lai mà không cần thiết kế lại toàn bộ.

* Mã nguồn tuân thủ quy chuẩn coding convention, có tài liệu kỹ thuật đi kèm để thuận tiện bảo trì.

## **4.6. Khả năng tương thích**

* Tương thích với các trình duyệt phổ biến hiện hành (Chrome, Firefox, Edge, Safari) phiên bản gần nhất.

* Bản đồ GIS tương thích với các định dạng dữ liệu không gian phổ biến (GeoJSON, Shapefile...) — cần thống nhất cụ thể ở giai đoạn thiết kế chi tiết.

## **4.7. Pháp lý và tuân thủ**

* Tuân thủ quy định pháp luật Việt Nam về bảo vệ dữ liệu cá nhân đối với thông tin nhân sự (Module 4).

* Nội dung công bố trên cổng thông tin tuân thủ quy định về cung cấp thông tin của doanh nghiệp nhà nước/công ty TNHH MTV theo quy định hiện hành.

# **5\. YÊU CẦU VỀ KIẾN TRÚC VÀ TÍCH HỢP HỆ THỐNG**

## **5.1. Mô hình triển khai**

Hệ thống được đề xuất triển khai theo mô hình web-based, kiến trúc nhiều lớp (presentation – application – data), cho phép truy cập qua trình duyệt web mà không cần cài đặt phần mềm client.

## **5.2. Các điểm tích hợp chính**

| Điểm tích hợp | Mô tả |
| :---- | :---- |
| Hệ thống quản lý văn bản điều hành hiện có | Tích hợp một chiều hoặc hai chiều để hiển thị/đồng bộ văn bản điều hành lên cổng thông tin (M1.8). |
| API dữ liệu thủy văn bên thứ 3 | Kết nối định kỳ/thời gian thực để lấy dữ liệu mực nước, lượng mưa (M3.1). |
| Bản đồ nền GIS | Sử dụng dịch vụ bản đồ nền (ví dụ Google Maps, OpenStreetMap hoặc nền bản đồ do Công ty cung cấp) để hiển thị lớp dữ liệu công trình (M2.3–M2.5). |
| Màn hình trình chiếu lớn Phòng điều hành | Giao diện dashboard tối ưu độ phân giải và bố cục riêng cho màn hình lớn (M2.6). |

## **5.3. Yêu cầu về cơ sở dữ liệu**

* Thiết kế cơ sở dữ liệu riêng biệt/phân vùng hợp lý cho: dữ liệu nội dung (CMS), dữ liệu công trình/GIS, dữ liệu thủy văn lịch sử, dữ liệu nhân sự, và dữ liệu quản trị hệ thống.

* Dữ liệu thủy văn cần thiết kế tối ưu cho truy vấn theo thời gian (time-series) để phục vụ biểu đồ và báo cáo hiệu quả.

# **6\. LIÊN KẾT VỚI KẾ HOẠCH TRIỂN KHAI (WBS)**

Phần này liệt kê cơ cấu phân rã công việc (WBS) làm cơ sở tham chiếu khi lập kế hoạch triển khai chi tiết; các đầu việc được ánh xạ tới các module yêu cầu chức năng ở Mục 3\.

### **1\. Quản lý dự án & Khảo sát thiết kế**

* Khảo sát quy trình vận hành thực tế tại các trạm/công trình.

* Thống kê các loại báo cáo thủy văn, nhân sự hiện có.

* Chốt tài liệu Đặc tả yêu cầu phần mềm (SRS) — tài liệu này.

* Thiết kế UI/UX (Responsive Web Design) cho giao diện Public, Dashboard nội bộ, màn hình lớn.

* Thiết kế kiến trúc hệ thống & cơ sở dữ liệu, bao gồm giải pháp tích hợp API bên thứ 3\.

### **2\. Module 1: Cổng thông tin điện tử**

* Quản trị nội dung (CMS): bài viết, danh mục, media, banner.

* Tích hợp hệ thống quản lý văn bản điều hành đã có.

* Tương tác người dùng: liên hệ, phản hồi, cấu hình giao diện.

### **3\. Module 2: Quản lý & vận hành công trình thủy lợi (GIS)**

* Danh mục công trình: hồ sơ chi tiết cống, trạm bơm, đê điều.

* Tích hợp bản đồ GIS: số hóa tọa độ, layer, tooltip.

* Hiển thị điều hành (Dashboard) cho màn hình lớn.

### **4\. Module 3: Quản lý dữ liệu thủy văn**

* Kết nối API, đọc dữ liệu tự động từ bên thứ 3\.

* Xử lý dữ liệu thời gian thực: bóc tách, chuẩn hóa, lưu trữ.

* Biểu đồ & báo cáo diễn biến mực nước, lượng mưa, xuất báo cáo định kỳ.

### **5\. Module 4: Quản lý nhân sự (HRM)**

* Sơ đồ tổ chức: cây đơn vị, phòng ban, chi nhánh.

* Hồ sơ cán bộ: thông tin cá nhân, lý lịch, lịch sử công tác.

* Quản lý hợp đồng & hồ sơ số hóa.

* Thống kê nhân sự, danh bạ nội bộ.

### **6\. Module 5: Quản trị hệ thống & Bảo mật**

* Quản lý người dùng, cấp tài khoản, phân quyền theo vai trò.

* Log hệ thống: theo dõi nhật ký hoạt động.

* An toàn dữ liệu: cấu hình backup và quy trình khôi phục.

### **7\. Kiểm thử, Triển khai & Đào tạo**

* Kiểm thử phần mềm (QA/QC): chức năng, bảo mật, hiệu năng.

* Đào tạo quản trị viên (Admin) và người dùng cuối (User).

* Triển khai & bàn giao: cài đặt lên Server, nghiệm thu, chuyển giao mã nguồn/tài liệu hướng dẫn.

# **7\. TIÊU CHÍ NGHIỆM THU**

* Toàn bộ chức năng nêu tại Mục 3 được xây dựng đầy đủ và hoạt động đúng theo mô tả.

* Hệ thống vượt qua kiểm thử chức năng, bảo mật và hiệu năng theo kịch bản kiểm thử được phê duyệt.

* Dữ liệu thủy văn được đồng bộ chính xác, đúng chu kỳ đã cấu hình từ nguồn bên thứ 3\.

* Bản đồ GIS hiển thị đúng vị trí công trình và thông tin liên kết chính xác.

* Giao diện đạt yêu cầu Responsive trên các thiết bị/trình duyệt phổ biến đã thống nhất.

* Tài liệu hướng dẫn sử dụng, tài liệu kỹ thuật và mã nguồn được bàn giao đầy đủ.

* Hoàn tất đào tạo cho quản trị viên và người dùng cuối.

# **8\. CÁC VẤN ĐỀ CẦN LÀM RÕ THÊM (OPEN ISSUES)**

Các điểm sau cần được Công ty và đơn vị phát triển thống nhất trong giai đoạn khảo sát chi tiết, do tài liệu gốc chưa nêu cụ thể:

* Đặc tả kỹ thuật (định dạng, tần suất, phương thức xác thực) của API dữ liệu thủy văn bên thứ 3\.

* Thông tin kỹ thuật để tích hợp với hệ thống quản lý văn bản điều hành hiện có (API/CSDL/định dạng trao đổi).

* Số lượng điểm đo thủy văn, số lượng và loại công trình cần quản lý ban đầu.

* Độ phân giải, số lượng và vị trí lắp đặt màn hình lớn tại Phòng điều hành.

* Số lượng người dùng dự kiến theo từng nhóm vai trò, làm cơ sở xác định yêu cầu hiệu năng cụ thể.

* Yêu cầu cụ thể về hạ tầng triển khai (hạ tầng tại chỗ hay cloud, cấu hình máy chủ).