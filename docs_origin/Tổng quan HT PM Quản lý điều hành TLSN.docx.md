**HỆ THỐNG PHẦN MỀM QUẢN TRỊ VÀ ĐIỀU HÀNH**

**CÔNG TY ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ**

**1.1. Giới thiệu**

Bản đặc tả hệ thống này mô tả các yêu cầu chức năng và phi chức năng cho việc Xây dựng hệ thống quản lý điều hành công trình và Cổng thông tin điện tử cho Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ. 

**1.2. Mục tiêu hệ thống**

Mục tiêu là cung cấp một nền tảng trực tuyến toàn diện, dễ sử dụng để công bố thông tin, quản lý công trình, giám sát và tổng hợp dữ liệu thủy văn, quản lý nhân sự, xây dựng các báo cáo, dữ liệu tổng hợp hỗ trợ việc quản lý điều hành của công ty. 

Cải thiện trải nghiệm người dùng thông qua giao diện hiện đại, thân thiện và tối ưu cho cả thiết bị di động (Responsive Web Design).

**1.3. Đối tượng người dùng**

Người dùng công cộng: Truy cập thông tin chung, tin tức, sự kiện, giới thiệu công ty, văn bản pháp luật.

Người dùng Nội bộ/ Quản lý: Truy cập thông tin chuyên sâu về mực nước, lượng mưa, bản đồ hệ thống, quản lý công trình, quản lý nhân sự, các báo cáo.

**1.4. Yêu cầu chức năng**

Hệ thống bao gồm các module chức năng chính sau:

**\- Module 1: Cổng thông tin điện tử**

Xây dựng cổng thông tin cấp thông tin minh bạch và kịp thời về hoạt động của Công ty. Tích hợp hệ thống quản lý văn bản điều hành (đã có) vào cổng thông tin của Công ty. 

Các chức năng chính bao gồm: quản lý bài viết, danh mục, media, banner, liên hệ, cấu hình giao diện, phản hồi của người dùng.

**\- Module 2: Quản lý và hỗ trợ vận hành các công trình thủy lợi.**

Các chức năng chính bao gồm: quản lý danh mục công trình; thông tin chi tiết về công trình; bản đồ GIS công trình kèm link truy cập thông tin chi tiết; hỗ trợ trình chiếu trên màn hình lớn của Phòng điều hành Công ty. 

**\- Module 3: Quản lý dữ liệu thủy văn**

Dữ liệu này được lấy từ nguồn do bên thứ 3 cung cấp thông qua internet. 

Các chức năng chính bao gồm: Xây dựng cơ sở dữ liệu lịch sử thủy văn tại các điểm đo; đọc, bóc tách dữ liệu thủy văn do bên thứ 3 cung cấp theo thời gian thực qua internet, lưu vào cơ sở dữ liệu thủy văn; xây dựng các biểu đồ, báo cáo khai thác dữ liệu phục vụ việc ra quyết định quản lý điều hành của Công ty.

**\- Module 4: Hệ thống Quản lý nhân sự công ty.**

Các chức năng chính bao gồm: Quản lý sơ đồ tổ chức của công ty, các đơn vị, phòng ban trực thuộc; quản lý hồ sơ cán bộ nhân viên, lý lịch cá nhân, lịch sử công tác, hợp đồng và các giấy tờ liên quan, danh bạ nội bộ; xây dựng các báo cáo thống kê nhân sự.

**\- Module 5: Quản trị hệ thống phần mềm, cấp tài khoản, phân quyền.**

Các chức năng chính bao gồm: cấu hình hệ thống; quản lý tài khoản và phân quyền; theo dõi nhật ký hoạt động; sao lưu và khôi phục dữ liệu.

### 

### **1.5. PHÂN RÃ CÔNG VIỆC (WBS)** 

#### ***1\. Quản lý dự án & Khảo sát thiết kế (Project Management & Design)***

**1.1. Khảo sát & Phân tích chi tiết:**

* Khảo sát quy trình vận hành thực tế tại các trạm/công trình.  
  * Thống kê các loại báo cáo thủy văn, nhân sự hiện có.  
  * Chốt tài liệu Đặc tả yêu cầu phần mềm (SRS).

  **1.2. Thiết kế UI/UX (Responsive Web Design):**

  * Thiết kế giao diện cổng thông tin (Public).  
  * Thiết kế giao diện Dashboard quản trị nội bộ.  
  * Thiết kế giao diện hiển thị màn hình lớn (Phòng điều hành).

  **1.3. Thiết kế kiến trúc hệ thống & Cơ sở dữ liệu:**

  * Thiết kế Cơ sở dữ liệu cho Thủy văn, Nhân sự, GIS.  
  * Thiết kế giải pháp tích hợp API với bên thứ 3 (Dữ liệu thủy văn).

#### ***2\. Module 1: Cổng thông tin điện tử (E-Portal)***

**2.1. Quản trị nội dung (CMS):** Quản lý bài viết, danh mục, media, banner.  
**2.2. Tích hợp hệ thống quản lý văn bản điều hành đã có**.  
**2.3. Tương tác người dùng:** Quản lý liên hệ, phản hồi, cấu hình giao diện.

#### ***3\. Module 2: Quản lý & Vận hành công trình thủy lợi (GIS)***

**3.1. Danh mục công trình:** Quản lý hồ sơ chi tiết các cống, trạm bơm, đê điều.  
**3.2. Tích hợp bản đồ GIS:**

* Số hóa tọa độ các công trình trên bản đồ.  
  * Xây dựng lớp dữ liệu (Layer) và các Tooltip hiển thị thông tin nhanh khi click.

  **3.3. Hiển thị điều hành (Dashboard):** Tối ưu hóa giao diện trình chiếu cho màn hình lớn tại phòng điều hành.

#### ***4\. Module 3: Quản lý dữ liệu thủy văn (Data Integration)***

**4.1. Kết nối API:** Xây dựng module tự động đọc dữ liệu từ bên thứ 3\.  
**4.2. Xử lý dữ liệu thời gian thực:** Bóc tách, chuẩn hóa dữ liệu và lưu vào DB lịch sử của Công ty.  
**4.3. Biểu đồ & Báo cáo:**

* Xây dựng biểu đồ diễn biến mực nước, lượng mưa.  
  * Xuất báo cáo định kỳ phục vụ ra quyết định.

#### ***5\. Module 4: Quản lý nhân sự (HRM)***

**5.1. Sơ đồ tổ chức:** Quản lý cây thư mục các đơn vị, phòng ban, chi nhánh.  
**5.2. Hồ sơ cán bộ:** Quản lý thông tin cá nhân, lý lịch, lịch sử công tác.  
**5.3. Quản lý hợp đồng & Hồ sơ:** Lưu trữ số hóa văn bản, hợp đồng, giấy tờ liên quan.  
**5.4. Thống kê nhân sự:** Báo cáo biến động nhân sự, danh bạ nội bộ.

#### ***6\. Module 5: Quản trị hệ thống & Bảo mật (Admin & Security)***

**6.1. Quản lý người dùng:** Cấp tài khoản, phân quyền theo vai trò (Role-based).  
**6.2. Log hệ thống:** Theo dõi nhật ký hoạt động của người dùng.  
**6.3. An toàn dữ liệu:** Cấu hình sao lưu (Backup) và quy trình khôi phục.

#### ***7\. Kiểm thử, Triển khai & Đào tạo (Testing & Deployment)***

**7.1. Kiểm thử phần mềm (QA/QC):** Kiểm thử chức năng, bảo mật và hiệu năng.  
**7.2. Đào tạo:**

* Đào tạo quản trị viên (Admin).  
  * Đào tạo người dùng cuối (User).

  **7.3. Triển khai & Bàn giao:** Cài đặt lên Server, nghiệm thu và chuyển giao mã nguồn/tài liệu hướng dẫn.

