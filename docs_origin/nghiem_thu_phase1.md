**CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ**

*Dự án nâng cấp Cổng thông tin điện tử*

**YÊU CẦU CHỈNH SỬA WEBSITE**

*(Bản tổng hợp gửi đơn vị phát triển phần mềm)*

Phiên bản: 1.0

Ngày phát hành: 27/08/2026

Trạng thái: Ban hành để thực hiện

# **1\. THÔNG TIN CHUNG**

## **1.1. Mục đích**

Tài liệu này tổng hợp toàn bộ các nội dung cần chỉnh sửa, bổ sung trên bản website đơn vị phát triển đã dựng, để đưa website về đúng bố cục và cây nội dung Công ty đã phê duyệt. Tài liệu được dùng làm danh mục công việc (checklist) để đơn vị phát triển thực hiện và làm căn cứ đối chiếu khi nghiệm thu.

Tài liệu này KHÔNG thay thế tài liệu Đặc tả yêu cầu phần mềm (SRS). Các yêu cầu chức năng gốc vẫn giữ theo SRS ver 06.8.2026; cột "Tham chiếu SRS" trong bảng ở Mục 4 chỉ ra mã yêu cầu tương ứng để tiện truy vết.

## **1.2. Căn cứ xây dựng**

* Tài liệu "GIAO DIỆN WEBSITE NÂNG CẤP – Bố cục" do Công ty ban hành (gồm bố cục trang chủ, cây nội dung 5 mục, bảng danh mục công trình và bảng phân quyền truy cập).

* Tài liệu Đặc tả yêu cầu phần mềm SRS\_QuanTriDieuHanh\_TLSN ver 06.8.2026 (5 module M1–M5).

* Kết quả rà soát bản website hiện tại của đơn vị phát triển (ảnh chụp màn hình có chú thích và bảng ghi nhận ngày 25/08/2026).

* Bộ dữ liệu "Tài liệu nâng cấp Website Công ty": ảnh bìa theo tháng, Tổng quan Công ty, Sơ đồ tổ chức, Lãnh đạo Công ty, Xí nghiệp trực thuộc, Danh mục công trình theo Xí nghiệp, Bản đồ hệ thống (PDF, KMZ), Form văn bản, biểu mẫu báo cáo chống hạn/chống úng.

## **1.3. Phạm vi tài liệu**

Tài liệu tập trung vào Module 1 – Cổng thông tin điện tử và phần công khai của Module 2 (Danh mục công trình, bản đồ hệ thống), Module 3 (Mực nước, lượng mưa; Vận hành công trình). Các màn hình quản trị nội bộ, HRM và quản trị hệ thống không thuộc phạm vi đợt chỉnh sửa này.

# **2\. NGUYÊN TẮC CHỈNH SỬA**

* Giao diện và bố cục cơ bản của bản website hiện tại GIỮ NGUYÊN (hệ màu, kiểu khối, cách trình bày). Chỉ chỉnh sửa cấu trúc menu, tên khối, nội dung bên trong khối và bổ sung các khối còn thiếu.

* Menu chính, footer, các card chuyên mục và cây nội dung phải dùng CHUNG một hệ phân loại – hệ phân loại chuẩn nêu tại Mục 3\.

* Không hiển thị số liệu gán cứng (hard-code) như thể là số liệu thật. Khi chưa có nguồn dữ liệu, dùng trạng thái chờ hoặc thông báo rõ ràng.

* Mọi tham số vận hành (chu kỳ refresh, số bài hiển thị, số ảnh slider, thời gian chuyển ảnh) phải cấu hình được, không gán cứng trong mã nguồn.

* Phân quyền phải xử lý ở tầng route/API, không chỉ ẩn/hiện ở giao diện.

* Các mục đánh dấu ưu tiên "Cao" cần hoàn thành trước; các mục "Trung bình" và "Thấp" hoàn thành trong cùng đợt bàn giao.

# **3\. CÂY NỘI DUNG CHUẨN**

Đây là hệ phân loại chuẩn. Menu chính, footer và các card chuyên mục trên trang chủ đều phải bám theo bảng này.

| Mục cấp 1 | Cấp 2 / Nội dung hiển thị |
| ----- | ----- |
| **Trang chủ** | Slider ảnh hoạt động (10–20 ảnh, 3–5 giây/ảnh) – Tin tức & Sự kiện – 3 tin Hot – Mực nước, lượng mưa – Vận hành công trình – Công bố thông tin – Chuyên mục & lĩnh vực – Truyền thông & hình ảnh – Liên kết đơn vị |
| **Giới thiệu** | Tổng quan: một bài viết giới thiệu chung (lịch sử hình thành, quá trình phát triển, chức năng, nhiệm vụ)Cơ cấu tổ chức: sơ đồ cây cơ cấu tổ chức Công tyLãnh đạo Công ty: bảng 3 cột (Họ và tên / Chức danh / Điện thoại liên hệ)Xí nghiệp trực thuộc: bảng 6 cột (Tên XN / Địa chỉ / Điện thoại / Email / Giám đốc XN / Điện thoại liên hệ) |
| **Tin tức – Sự kiện** | Tin thủy lợi: bài viết liên quan ngành thủy lợi của UBND thành phố Hà Nội, Sở Nông nghiệp và Môi trườngTin Công ty: bài viết về hoạt động phục vụ sản xuất của Công ty |
| **Hoạt động Đảng, đoàn thể** | Bài viết về công tác Đảng và đoàn thể: Công đoàn, Đoàn Thanh niên, Hội Phụ nữ, Hội Cựu chiến binh |
| **Quản lý, vận hành** | Danh mục công trình: Bản đồ hệ thống \+ danh sách công trình theo từng Xí nghiệp trực thuộcTiến độ sản xuất: Năm → Vụ Xuân / Vụ Mùa / Vụ ĐôngMực nước, lượng mưa: public CHỈ hiển thị mực nước, lượng mưa của 10 cống trên trục chính tại giờ truy cập; theo dõi theo tuần/tháng phải đăng nhậpVận hành công trình: public CHỈ hiển thị số liệu vận hành trạm bơm của từng Xí nghiệp tại ngày truy cập; xem sâu hơn phải đăng nhập |
| **Công bố thông tin** | Văn bản pháp luật: Luật – Nghị định – Thông tư/Chỉ thị – Quyết định – Hướng dẫnVăn bản Công ty: Kế hoạch – Thông cáo, Báo cáo – Quyết định |
| **Liên hệ** | Thông tin liên hệ Công ty \+ bản đồ Google Map địa chỉ trụ sở \+ form liên hệ |
| **(Liên kết ngoài)** | Văn bản điều hành: KHÔNG xây module nội bộ. Click mở tab mới sang https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi |

# **4\. BẢNG YÊU CẦU CHỈNH SỬA CHI TIẾT**

Tổng cộng 44 mục, chia theo 6 nhóm. Đề nghị đơn vị phát triển xác nhận từng mã CR khi hoàn thành.

| Mã | Khu vực / Hạng mục | Hiện trạng bản dev | Yêu cầu chỉnh sửa | Ưu tiên | Tham chiếu SRS |
| ----- | ----- | ----- | ----- | :---: | :---: |
| **A. MENU CHÍNH VÀ ĐIỀU HƯỚNG** |  |  |  |  |  |
| **CR-01** | **Menu chính** | Trang chủ – Giới thiệu – Tin tức – Thông báo – Văn bản điều hành – Liên hệ | Đổi thành: Trang chủ – Giới thiệu – Tin tức & Sự kiện – Hoạt động Đảng, Đoàn thể – Quản lý, vận hành – Công bố thông tin – Liên hệ.Bỏ mục "Thông báo", thay bằng "Quản lý, vận hành". | **Cao** | *M1.6* |
| **CR-02** | **Submenu Giới thiệu** | Chưa thể hiện đầy đủ submenu | Submenu gồm: Tổng quan – Cơ cấu tổ chức – Lãnh đạo Công ty – Xí nghiệp trực thuộc. | **Cao** | *M1.2* |
| **CR-03** | **Submenu Tin tức & Sự kiện** | Chỉ có "Tin tức" chung | Chia 2 nhánh: Tin thủy lợi (tin ngành – UBND TP Hà Nội, Sở Nông nghiệp và Môi trường) và Tin Công ty (hoạt động phục vụ sản xuất của Công ty). | **Cao** | *M1.2* |
| **CR-04** | **Hoạt động Đảng, Đoàn thể** | Chưa có trên menu | Thêm mục cấp 1\. Nội dung gồm bài viết về công tác Đảng, Công đoàn, Đoàn Thanh niên, Hội Phụ nữ, Hội Cựu chiến binh. | **Cao** | *M1.1, M1.2* |
| **CR-05** | **Submenu Quản lý, vận hành** | Chưa có đúng cấu trúc trên menu | Thêm submenu: Danh mục công trình – Tiến độ sản xuất – Mực nước, lượng mưa – Vận hành công trình. | **Cao** | *M2.1, M3.7* |
| **CR-06** | **Công bố thông tin** | Đang là mục "Văn bản điều hành" | Đổi thành "Công bố thông tin", gồm 2 nhóm: Văn bản pháp luật và Văn bản Công ty. Đây là nội dung public do website quản lý. | **Cao** | *M1.8* |
| **CR-07** | **Link Văn bản điều hành** | Đang xử lý như một module nội dung của website | KHÔNG xây module văn bản điều hành nội bộ trên website. Nếu Công ty muốn giữ nút/menu "Văn bản điều hành" trên header thì khi click phải mở TAB MỚI sang:https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi | **Cao** | *M1.8* |
| **CR-08** | **Nút Đăng nhập trên header** | Trang chủ chưa có vị trí Đăng nhập rõ ràng | Bổ sung nút ĐĂNG NHẬP ở header, đặt gần ô Tìm kiếm hoặc hotline. Dùng để xem các thông tin nội bộ (mực nước/lượng mưa và vận hành công trình theo tuần/tháng). Lưu ý: chức năng nội bộ ở đây vẫn là XEM thông tin trên trang, không phải màn hình admin thêm/sửa/xóa. | **Cao** | *M5.1, M5.2* |
| **CR-09** | **Footer – Liên kết nhanh / Nghiệp vụ thủy lợi** | Đang theo cấu trúc menu cũ | Đồng bộ toàn bộ link footer với menu và cây nội dung mới. Tránh để menu trên và footer dùng hai hệ phân loại khác nhau. | Trung bình | *M1.6* |
| **B. BỐ CỤC TRANG CHỦ** |  |  |  |  |  |
| **CR-10** | **Banner / ảnh lớn đầu trang** | Đang dùng "Giới thiệu chung" như một bài viết | Chuyển thành slider ảnh hoạt động của Công ty: khoảng 10–20 ảnh chạy xoay vòng, tự chuyển 3–5 giây, có nút ‹ / › và indicator.Ảnh KHÔNG cần gắn link bài viết. | **Cao** | *M1.4* |
| **CR-11** | **Khối 3 tin dưới banner** | Đang hiển thị 3 tin nổi bật | GIỮ NGUYÊN – đây là phần tin tức Hot. | Giữ nguyên | *M1.1* |
| **CR-12** | **Khối bên phải banner** | Đang là "Dòng thời sự" | Đổi tên thành TIN TỨC – SỰ KIỆN. Hiển thị khoảng 5 bài mới nhất, có tag phân biệt Tin thủy lợi / Tin Công ty. | Trung bình | *M1.1* |
| **CR-14** | **Mực nước – dữ liệu chi tiết** | Chưa thể hiện cơ chế xem sâu | Thêm "Xem chi tiết" / "Đăng nhập". Sau khi đăng nhập mới theo dõi được số liệu theo TUẦN, THÁNG. | **Cao** | *M3.8, M5.2* |
| **CR-15** | **Vận hành công trình** | Trang chủ chưa có block riêng | Thêm block VẬN HÀNH CÔNG TRÌNH đặt cạnh hoặc dưới khối Mực nước – lượng mưa/ Quan trắc. Bản public CHỈ hiển thị số liệu vận hành trạm bơm của từng Xí nghiệp tại ngày truy cập; xem chi tiết yêu cầu đăng nhập. | **Cao** | *M2.14, M3.7* |
| **CR-16** | **Khối "Chỉ đạo điều hành"** | Đang chứa Cơ cấu tổ chức – Chức năng nhiệm vụ – Giới thiệu chung; tên khối không đúng với nội dung bên trong | Bỏ block này  | **Cao** | *M1.2* |
| **CR-17** | **Khối "Văn bản & Quyết định"** | Block riêng bên phải, chưa có item tương ứng trên menu chính | Đổi tên thành CÔNG BỐ THÔNG TIN. Hiển thị các văn bản mới nhất kèm link "Xem tất cả", và phải có mục tương ứng trên menu chính. | **Cao** | *M1.8* |
| **CR-18** | **Chuyên mục & lĩnh vực hoạt động** | Đang là Tin tức – Thông báo – Giới thiệu; 4 card lĩnh vực chưa khớp cây nội dung | Đổi thành 5 card đồng bộ với menu chính: Giới thiệu – Tin tức & Sự kiện – Hoạt động Đảng, Đoàn thể – Quản lý, vận hành – Công bố thông tin. | Trung bình | *M1.6* |
| **CR-19** | **Đơn vị trực thuộc** | Đang báo "chưa được đấu nối" | Chuẩn bị và đấu nối dữ liệu các Xí nghiệp trực thuộc (danh sách chốt theo OI-05). | Trung bình | *M4.1* |
| **CR-20** | **Truyền thông & hình ảnh hoạt động** | Video và thư viện ảnh đang trống hoặc dùng ảnh minh họa không liên quan (xe khách, nhà máy) | Giữ block này; thay bằng ảnh/video hoạt động thực tế của Công ty. Không dùng ảnh stock không liên quan đến ngành thủy lợi. | Thấp | *M1.3* |
| **CR-21** | **Liên kết đơn vị** | Đang có Bộ NN\&PTNT, UBND TP, Sở..., Cục Thủy lợi | Giữ block; rà soát lại tên và đường link chính thức khi nhập dữ liệu (lưu ý tên gọi Sở Nông nghiệp và Môi trường theo tổ chức hiện hành). | Thấp | *—* |
| **CR-22** | **Bản đồ trụ sở** | Chưa có | Khối/trang Liên hệ có bản đồ Google Map địa chỉ trụ sở Công ty (theo bố cục được duyệt). | Trung bình | *M1.5* |
| **C. CÁC TRANG CHỨC NĂNG** |  |  |  |  |  |
| **CR-23** | **Giới thiệu \> Tổng quan** | Chưa đúng vị trí | Một bài viết giới thiệu chung về Công ty: lịch sử hình thành, quá trình phát triển, chức năng, nhiệm vụ. | Trung bình | *M1.1* |
| **CR-24** | **Giới thiệu \> Cơ cấu tổ chức** | Đang nằm trong khối "Chỉ đạo điều hành" | Hiển thị sơ đồ cây cơ cấu tổ chức bộ máy quản lý của Công ty (dữ liệu theo file "SƠ ĐỒ TỔ CHỨC BỘ MÁY QUẢN LÝ"). | Trung bình | *M4.1* |
| **CR-25** | **Giới thiệu \> Lãnh đạo Công ty** | Chưa có | Bảng kẻ ngang 3 cột: (1) Họ và tên – (2) Chức danh – (3) Điện thoại liên hệ. | Trung bình | *M1.1* |
| **CR-26** | **Giới thiệu \> Xí nghiệp trực thuộc** | Chưa có | Bảng kẻ ngang 6 cột: (1) Tên Xí nghiệp – (2) Địa chỉ – (3) Điện thoại Xí nghiệp – (4) Email Xí nghiệp – (5) Họ và tên Giám đốc Xí nghiệp – (6) Điện thoại liên hệ. | Trung bình | *M1.1* |
| **CR-27** | **Danh mục công trình** | Chưa thể hiện đúng yêu cầu, đang xử lý như một bài viết | Xây trang danh sách công trình theo từng Xí nghiệp, kèm Bản đồ hệ thống. Đây là chức năng chính của mục Quản lý, vận hành, không nên chỉ là bài viết. | **Cao** | *M2.1, M2.7* |
| **CR-28** | **Bảng công trình** | Chưa có | Bảng 7 cột: TT – Tên trạm bơm – Địa điểm – Thông tin chủ yếu – Quy trình vận hành – Phương án bảo vệ – Vị trí. Đặc tả chi tiết tại Mục 5.1. | **Cao** | *M2.1, M2.2* |
| **CR-29** | **Bản đồ hệ thống** | Chưa có | Đăng bản đồ hệ thống dạng PDF (2 tỷ lệ 1/50.000 và 1/75.000) cho người dùng công cộng; file KMZ yêu cầu đăng nhập mới xem/tải. | **Cao** | *M2.7, M2.9* |
| **CR-30** | **Tiến độ sản xuất** | Chưa thấy trên website | Thêm mục thuộc Quản lý, vận hành: chọn Năm → Vụ Xuân / Vụ Mùa / Vụ Đông. | Trung bình | *M2.16* |
| **CR-31** | **Văn bản pháp luật** | Chưa phân loại | Phân nhóm: Luật – Nghị định – Thông tư/Chỉ thị – Quyết định – Hướng dẫn. Phạm vi: các văn bản liên quan lĩnh vực Thủy lợi, Môi trường, Đất đai, Xây dựng... | Trung bình | *M1.8* |
| **CR-32** | **Văn bản Công ty** | Chưa phân loại | Phân nhóm: Kế hoạch – Thông cáo/Báo cáo – Quyết định. Phạm vi: các văn bản chỉ đạo, điều hành về tất cả các mặt của Công ty. | Trung bình | *M1.8* |
| **D. DỮ LIỆU THỜI GIAN THỰC VÀ API** |  |  |  |  |  |
| **CR-33** | **API Mực nước, lượng mưa** | Chưa rõ đã tích hợp API realtime hay đang gán cứng số liệu | Dev xác nhận trạng thái API (xem OI-01). Nếu CHƯA có API: để trạng thái placeholder rõ ràng, tuyệt đối không hiển thị số liệu gán cứng như số liệu thật. Nếu ĐÃ có: trang chủ tự lấy dữ liệu hiện tại của 10 cống trên trục chính. | **Cao** | *M3.3* |
| **CR-34** | **API Vận hành công trình** | Chưa rõ đã có API dữ liệu vận hành trạm bơm | Kiểm tra và xác nhận API nguồn cho: trạng thái trạm bơm, số máy đang chạy, lưu lượng, thời điểm cập nhật. Hiển thị dữ liệu theo ngày truy cập. | **Cao** | *M3.3* |
| **CR-35** | **Thời gian cập nhật dữ liệu** | Chưa có | Mọi khối dữ liệu thời gian thực phải có dòng "Cập nhật lúc: HH:mm dd/MM/yyyy" để người dùng biết dữ liệu mới đến thời điểm nào. | **Cao** | *M3.7* |
| **CR-36** | **Trường hợp mất kết nối API** | Hiện chỉ hiển thị "chưa được đấu nối" | Bổ sung trạng thái fallback: "Dữ liệu tạm thời chưa khả dụng" kèm thời điểm cập nhật gần nhất. Giữ nguyên layout, không để khối trống và không hiển thị số liệu giả. | **Cao** | *M3.15* |
| **CR-37** | **Refresh dữ liệu** | Chưa rõ cơ chế | Cho phép tự động refresh theo chu kỳ CẤU HÌNH ĐƯỢC (không hard-code trong mã nguồn) và bổ sung nút refresh thủ công. Chu kỳ thực tế theo API/backend, chốt sau khi có nguồn dữ liệu. | Trung bình | *M3.3, M5.5* |
| **E. PHÂN QUYỀN** |  |  |  |  |  |
| **CR-38** | **Phân quyền truy cập** | Chưa thể hiện đầy đủ | Áp dụng đúng bảng phân quyền tại Mục 6\. Public xem được hầu hết nội dung; riêng Mực nước, lượng mưa và Vận hành công trình có phần chi tiết yêu cầu đăng nhập. Dev lưu ý ngay khi thiết kế route/API, không xử lý bằng cách ẩn ở giao diện. | **Cao** | *M5.2, M5.3* |
| **F. NỘI DUNG HIỂN THỊ CẦN SỬA HOẶC BỎ** |  |  |  |  |  |
| **CR-39** | **Footer – dòng mô tả** | "Doanh nghiệp 100% vốn Nhà nước" | Bỏ dòng này. | Trung bình | *—* |
| **CR-40** | **Footer – Email** | "EMAIL: songnhue2015@gmail.com" | Bỏ khỏi footer theo kết quả rà soát. Lưu ý: tài liệu Bố cục vẫn ghi email này – cần Công ty chốt (xem OI-04). | Trung bình | *—* |
| **CR-41** | **Footer – Giờ làm việc** | "Giờ làm việc: Thứ Hai – Thứ Sáu: 08:00 – 17:00 (Trực ban PCTT 24/24h)" | Bỏ dòng này. | Trung bình | *—* |
| **CR-42** | **Footer – Địa chỉ trụ sở** | "TẦNG 4-5 TÒA NHÀ NEW HOUSE XALA – KHU ĐÔ THỊ XALA – QUẬN HÀ ĐÔNG – THÀNH PHỐ HÀ NỘI" | Sửa theo địa giới hành chính mới: "Tầng 4-5 Tòa nhà Newhouse – Phường Hà Đông – Thành phố Hà Nội". Bỏ cấp "Quận". | **Cao** | *—* |
| **CR-43** | **Footer – Thông tin liên hệ** | Đã có địa chỉ, điện thoại, fax | Giữ nguyên: Liên hệ (024) 33.546.247 – Fax (024) 33.540.794. Chỉ rà soát lại cho khớp dữ liệu chính thức. | Thấp | *—* |
| **CR-44** | **Trường "Địa điểm" của công trình** | Chưa thống nhất | Mọi trường "Địa điểm" trong bảng danh mục công trình ghi theo địa giới hành chính cấp xã MỚI. | **Cao** | *M2.1* |

# **5\. ĐẶC TẢ BỔ SUNG MỘT SỐ KHỐI CHỨC NĂNG**

## **5.1. Bảng danh mục công trình (CR-28)**

Mỗi Xí nghiệp trực thuộc có một trang danh sách công trình, hiển thị bảng thống kê theo mẫu sau:

| TT | Tên trạm bơm | Địa điểm | Thông tin chủ yếu | Quy trình vận hành | Phương án bảo vệ | Vị trí |
| :---: | ----- | ----- | ----- | ----- | ----- | ----- |
| 1 | Trạm bơm A | Địa điểm hành chính cấp xã theo địa giới hành chính mới | Số máy × Lưu lượng 1 máy bơm | Gắn link Quyết định phê duyệt / Quy trình vận hành công trình | Gắn link Quyết định phê duyệt / Phương án bảo vệ công trình | Gắn link địa chỉ / Google Map công trình |
| 2 | .... |  |  |  |  |  |

* Cột "Địa điểm": ghi theo địa giới hành chính cấp xã mới.

* Cột "Thông tin chủ yếu": ghi theo dạng Số máy × Lưu lượng 1 máy bơm.

* Cột "Quy trình vận hành" và "Phương án bảo vệ": gắn link tới Quyết định phê duyệt tương ứng (tệp PDF).

* Cột "Vị trí": gắn link Google Map tới vị trí công trình.

* Trang danh mục công trình có thêm mục "Bản đồ hệ thống" (xem 5.6).

## **5.2. Khối "Mực nước, lượng mưa" (CR-13, CR-14)**

* Bản public: chỉ hiển thị mực nước và lượng mưa của 10 cống trên trục chính, số liệu tại giờ truy cập website.

* Mỗi cống hiển thị: tên cống, giá trị đo kèm đơn vị, trạng thái (Bình thường / Cảnh báo cấp I, II, III).

* Có dòng "Cập nhật lúc: HH:mm dd/MM/yyyy" ngay trong khối.

* Có nút "Xem chi tiết" hoặc "Đăng nhập". Sau khi đăng nhập mới xem được số liệu theo TUẦN và THÁNG (bảng và biểu đồ diễn biến).

* Khi mất kết nối nguồn dữ liệu: hiển thị "Dữ liệu tạm thời chưa khả dụng" kèm thời điểm cập nhật gần nhất, giữ nguyên bố cục khối.

## **5.3. Khối "Vận hành công trình" (CR-15)**

* Khối riêng trên trang chủ, đặt cạnh hoặc ngay dưới khối Mực nước, lượng mưa.

* Bản public: hiển thị số liệu vận hành trạm bơm của từng Xí nghiệp tại ngày truy cập website.

* Nội dung tối thiểu: tên trạm bơm / Xí nghiệp, trạng thái vận hành, số máy đang chạy, lưu lượng, thời điểm cập nhật.

* Xem chi tiết theo tuần, tháng: yêu cầu đăng nhập.

## **5.4. Đăng nhập và chức năng nội bộ (CR-08)**

* Nút ĐĂNG NHẬP đặt ở header, gần ô Tìm kiếm hoặc hotline.

* Khi click, hệ thống hiển thị ô Tên đăng nhập và Mật khẩu.

* Sau khi đăng nhập, người dùng xem được Mực nước, lượng mưa và Vận hành công trình theo TUẦN, THÁNG.

* Lưu ý: chức năng nội bộ trong phạm vi này vẫn là XEM thông tin trên trang, không phải màn hình quản trị thêm/sửa/xóa nội dung.

* Phân quyền xử lý ở tầng route/API. Người dùng chưa đăng nhập gọi trực tiếp API dữ liệu chi tiết phải bị từ chối.

## **5.5. Tiến độ sản xuất (CR-30)**

* Thuộc mục Quản lý, vận hành.

* Người dùng chọn Năm, sau đó chọn một trong ba vụ: Vụ Xuân / Vụ Mùa / Vụ Đông.

* Hiển thị nội dung tiến độ sản xuất tương ứng với năm và vụ đã chọn.

## **5.6. Bản đồ hệ thống (CR-29)**

* Bản PDF: 2 tỷ lệ 1/50.000 (khổ 1200×1650) và 1/75.000 (khổ 810×1100), cho phép người dùng công cộng xem và tải.

* Bản KMZ: yêu cầu đăng nhập mới được xem hoặc tải (cơ chế cụ thể theo OI-07).

# **6\. BẢNG PHÂN QUYỀN TRUY CẬP (CR-38)**

| TT | Tên mục | Tên thẻ | Quyền truy cập | Cách thức truy cập / Ghi chú |
| :---: | ----- | ----- | :---: | ----- |
| 1 | **Giới thiệu** | (toàn bộ submenu) | Tất cả người dùng |  |
| 2 | **Tin tức – Sự kiện** | Tin thủy lợi / Tin Công ty | Tất cả người dùng |  |
| 3 | **Hoạt động Đảng, đoàn thể** |  | Tất cả người dùng |  |
| 4 | **Quản lý, vận hành** | Danh mục công trình | Tất cả người dùng | Riêng file KMZ bản đồ hệ thống yêu cầu đăng nhập |
| 4 | **Quản lý, vận hành** | Tiến độ sản xuất | Tất cả người dùng |  |
| 4 | **Quản lý, vận hành** | Mực nước, lượng mưa | **Phân quyền** | Public: 10 cống trục chính tại giờ truy cập. Đăng nhập để theo dõi theo tuần, tháng |
| 4 | **Quản lý, vận hành** | Vận hành công trình | **Phân quyền** | Public: số liệu vận hành trạm bơm tại ngày truy cập. Đăng nhập để theo dõi theo tuần, tháng |
| 5 | **Công bố thông tin** | Văn bản pháp luật | Tất cả người dùng |  |
| 5 | **Công bố thông tin** | Văn bản Công ty | Tất cả người dùng |  |
| 6 | **Liên hệ** |  | Tất cả người dùng |  |

*Nguyên tắc: hầu hết nội dung là public. Riêng phần chi tiết của Mực nước, lượng mưa và Vận hành công trình (theo tuần, tháng) và file KMZ bản đồ hệ thống yêu cầu đăng nhập.*

# **7\. YÊU CẦU CHUNG VỀ DỮ LIỆU THỜI GIAN THỰC**

Áp dụng cho tất cả các khối lấy dữ liệu từ nguồn bên ngoài (Mực nước, lượng mưa; Vận hành công trình):

* Mỗi khối phải có dòng "Cập nhật lúc: HH:mm dd/MM/yyyy".

* Tự động refresh theo chu kỳ cấu hình được; bổ sung nút refresh thủ công.

* Khi nguồn dữ liệu gián đoạn: hiển thị "Dữ liệu tạm thời chưa khả dụng" kèm thời điểm cập nhật gần nhất. Không để khối trống, không hiển thị số liệu giả.

* Ghi log các lần đồng bộ (thành công / thất bại, thời gian, số bản ghi) phục vụ đối soát.

* Nếu tại thời điểm bàn giao chưa có API, khối vẫn phải dựng đầy đủ và để trạng thái chờ dữ liệu, sẵn sàng đấu nối khi có nguồn.

# **8\. TÓM TẮT PHẦN CÒN THIẾU SO VỚI BỐ CỤC ĐÃ DUYỆT**

Các hạng mục sau có trong tài liệu Bố cục nhưng chưa thấy trên bản website hiện tại:

* Mục cấp 1 "Hoạt động Đảng, Đoàn thể" (CR-04).

* Toàn bộ nhánh "Quản lý, vận hành" gồm 4 mục con (CR-05).

* Trang Lãnh đạo Công ty và trang Xí nghiệp trực thuộc dạng bảng (CR-25, CR-26).

* Trang danh sách công trình theo Xí nghiệp và bảng công trình 7 cột (CR-27, CR-28).

* Bản đồ hệ thống PDF và KMZ (CR-29).

* Mục Tiến độ sản xuất theo Năm và Vụ (CR-30).

* Phân loại văn bản pháp luật và văn bản Công ty (CR-31, CR-32).

* Khối Vận hành công trình trên trang chủ (CR-15).

* Nút Đăng nhập và cơ chế phân quyền xem dữ liệu chi tiết (CR-08, CR-38).

* Bản đồ Google Map địa chỉ trụ sở ở mục Liên hệ (CR-22).

# **9\. CÁC VẤN ĐỀ CẦN LÀM RÕ**

Các nội dung sau cần được xác nhận để hoàn thiện. Đề nghị đơn vị phát triển trả lời các mục thuộc phần kỹ thuật ngay trong tuần để không ảnh hưởng tiến độ.

| Mã | Nội dung cần làm rõ | Bên trả lời |
| :---: | ----- | :---: |
| **OI-01** | API dữ liệu mực nước, lượng mưa đã sẵn sàng chưa? Nếu có: endpoint, phương thức xác thực, tần suất cập nhật, danh sách mã điểm đo để ánh xạ. Nếu chưa: dự kiến thời điểm có API và nguồn cung cấp. | Dev \+ Công ty |
| **OI-02** | API dữ liệu vận hành trạm bơm đã có chưa? Các trường dữ liệu trả về (trạng thái, số máy đang chạy, lưu lượng, thời điểm cập nhật)? | Dev \+ Công ty |
| **OI-03** | Danh sách chính xác 10 cống trên trục chính được hiển thị công khai ở trang chủ. | Công ty |
| **OI-04** | Email hiển thị ở footer: tài liệu Bố cục ghi songnhue2015@gmail.com nhưng bản rà soát gạch bỏ. Chốt bỏ hẳn hay thay bằng email công vụ chính thức? | Công ty |
| **OI-05** | Số lượng Xí nghiệp trực thuộc: tài liệu Bố cục liệt kê 7 XN cho mục Vận hành công trình (Liên Mạc, Từ Liêm, Hà Đông, Thanh Trì, Hồng Vân, Phú Xuyên, Ứng Hòa), trong khi bộ dữ liệu Danh mục công trình có 8 (thêm XNTL Nhật Tựu). Chốt danh sách cho từng mục. | Công ty |
| **OI-06** | Cơ chế cấp tài khoản đăng nhập: ai cấp, có bao nhiêu nhóm quyền, phạm vi dữ liệu mỗi nhóm được xem? | Công ty \+ Dev |
| **OI-07** | File KMZ bản đồ hệ thống: cho tải về sau đăng nhập hay nhúng viewer trên web? Có cần chuyển sang GeoJSON để hiển thị lớp bản đồ không? | Dev |
| **OI-08** | Thanh trên cùng hiện có "Tra cứu văn bản" và "Gửi phản ánh kiến nghị": giữ, đổi tên hay bỏ? "Tra cứu văn bản" có trùng chức năng với Công bố thông tin / Văn bản điều hành không? | Công ty |
| **OI-09** | Chu kỳ tự động refresh dữ liệu thời gian thực mong muốn (5 / 10 / 15 phút)? | Công ty \+ Dev |
| **OI-10** | Hai biểu mẫu Báo cáo nhanh chống hạn (Form 1\) và Báo cáo nhanh chống úng (Form 2\) đưa vào mục Vận hành công trình sau đăng nhập theo cơ chế nào: chỉ cho tải mẫu, hay nhập trực tuyến và tổng hợp? | Công ty |

# **10\. CHECKLIST NGHIỆM THU ĐỢT CHỈNH SỬA**

* Menu chính, footer và card chuyên mục dùng đúng một hệ phân loại theo Mục 3\.

* Toàn bộ 44 mã CR tại Mục 4 được thực hiện hoặc có phản hồi lý do cụ thể.

* Nút "Văn bản điều hành" mở tab mới sang hệ thống quanlyvanban.hanoi.gov.vn, không có danh sách văn bản nội bộ trên website.

* Nút Đăng nhập hoạt động; khi chưa đăng nhập không truy cập được dữ liệu chi tiết kể cả khi gọi trực tiếp API.

* Các khối dữ liệu thời gian thực có dòng "Cập nhật lúc" và trạng thái fallback khi mất kết nối.

* Bảng danh mục công trình đủ 7 cột, các link Quyết định và Google Map hoạt động.

* Giao diện hiển thị đúng trên máy tính, máy tính bảng và điện thoại (Responsive).

* Không còn nội dung đã yêu cầu bỏ tại nhóm F và không còn ảnh minh họa không liên quan đến ngành.

* Các vấn đề tại Mục 9 đã có câu trả lời hoặc được ghi nhận thành công việc tiếp theo.

*Mọi vướng mắc trong quá trình thực hiện đề nghị trao đổi trực tiếp với đầu mối phía Công ty để thống nhất trước khi triển khai.*