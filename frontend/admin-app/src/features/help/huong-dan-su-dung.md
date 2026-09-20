# Hướng dẫn sử dụng hệ thống Thủy lợi Sông Nhuệ

> Dành cho cán bộ sử dụng **trang quản trị điều hành**.
> Không cần kiến thức kỹ thuật để đọc tài liệu này.

---

## Mục lục

| Phần                                   | Nội dung                              |
| -------------------------------------- | ------------------------------------- |
| [1](#1-hệ-thống-gồm-những-gì)          | Hệ thống gồm những gì                 |
| [2](#2-đăng-nhập-và-tài-khoản)         | Đăng nhập và tài khoản                |
| [3](#3-bản-đồ-màn-hình)                | Bản đồ màn hình — tìm nhanh chức năng |
| [4](#4-vai-trò-và-quyền)               | Vai trò và quyền — ai thấy gì         |
| [5](#5-vận-hành-công-trình)            | Vận hành công trình                   |
| [6](#6-dữ-liệu-thủy-văn)               | Dữ liệu thủy văn                      |
| [7](#7-nội-dung-cổng-thông-tin)        | Nội dung cổng thông tin               |
| [8](#8-nhân-sự)                        | Nhân sự                               |
| [9](#9-quản-trị-hệ-thống)              | Quản trị hệ thống                     |
| [10](#10-những-ô-đang-trống-và-vì-sao) | Những ô đang trống và vì sao          |
| [11](#11-xử-lý-tình-huống-thường-gặp)  | Xử lý tình huống thường gặp           |

---

## 1. Hệ thống gồm những gì

Hệ thống có **hai cửa** vào, dùng cho hai nhóm người khác nhau:

|               | Cổng thông tin điện tử                                         | Trang quản trị điều hành    |
| ------------- | -------------------------------------------------------------- | --------------------------- |
| **Ai dùng**   | Người dân, đơn vị ngoài                                        | Cán bộ Công ty              |
| **Đăng nhập** | Không cần                                                      | Bắt buộc                    |
| **Nội dung**  | Tin tức, văn bản, số liệu thủy văn công khai, biểu mẫu liên hệ | Toàn bộ nghiệp vụ điều hành |

Tài liệu này nói về **cửa thứ hai**.

### Địa chỉ truy cập

| Môi trường                                   | Cổng thông tin         | Trang quản trị               |
| -------------------------------------------- | ---------------------- | ---------------------------- |
| **Chính thức** — dùng hằng ngày              | `thuyloisongnhue.vn`   | `admin.thuyloisongnhue.vn`   |
| **Thử nghiệm** — tập huấn, thử tính năng mới | `staging.songnhue.com` | `admin-staging.songnhue.com` |

> ⚠ **Hai môi trường có dữ liệu riêng, hoàn toàn không dùng chung.** Số liệu nhập trên bản
> thử nghiệm **không** chảy sang bản chính thức, và ngược lại. Mọi việc thật phải làm trên
> `thuyloisongnhue.vn`.

Trang quản trị chia làm **5 nhóm công việc**:

```
Vận hành công trình   →  hồ sơ công trình, sửa chữa/sự cố, bản đồ, báo cáo
Dữ liệu thủy văn      →  điểm đo, số liệu tự động, ngưỡng, cảnh báo
Nội dung cổng         →  bài viết, hộp thư, góp ý, giao diện cổng
Nhân sự               →  hồ sơ cán bộ, danh bạ, nghỉ phép, sơ đồ tổ chức
Quản trị hệ thống     →  tài khoản, phân quyền, cấu hình, sao lưu, nhật ký
```

**Một nguyên tắc chung xuyên suốt:** mọi con số trên màn hình đều do máy chủ tính, màn
hình chỉ hiển thị. Vì vậy hai người ở hai máy khác nhau luôn nhìn thấy cùng một con số.

---

## 2. Đăng nhập và tài khoản

### 2.1. Lần đăng nhập đầu tiên

Quản trị viên cấp cho bạn **tên đăng nhập** và một **mật khẩu tạm**. Lần đầu vào, hệ
thống sẽ dẫn bạn qua 2–3 bước, theo đúng thứ tự:

```
1. Nhập tên đăng nhập + mật khẩu tạm
        ↓
2. (Nếu tài khoản của bạn bắt buộc xác thực 2 bước)
   Quét mã QR bằng ứng dụng xác thực → nhập mã 6 số để xác nhận
        ↓
3. Đổi mật khẩu tạm sang mật khẩu riêng của bạn
        ↓
   Vào hệ thống
```

> ⚠ **Ở bước 2, hệ thống hiển thị một danh sách "mã khôi phục". Hãy in hoặc chép ra
> giấy và cất nơi an toàn.** Đây là cách duy nhất bạn tự vào lại được nếu mất điện thoại.
> Danh sách này chỉ hiện đúng một lần.

**Ứng dụng xác thực** là phần mềm miễn phí cài trên điện thoại, ví dụ Google
Authenticator hoặc Microsoft Authenticator. Nó sinh một mã 6 số đổi mỗi 30 giây.

### 2.2. Quy định về mật khẩu

Các con số dưới đây Công ty sửa được bất cứ lúc nào tại _Quản trị hệ thống › Cấu hình
hệ thống_. Đây là giá trị hệ thống đặt sẵn khi bàn giao:

| Quy định                            | Giá trị đặt sẵn     |
| ----------------------------------- | ------------------- |
| Độ dài tối thiểu                    | 10 ký tự            |
| Phải có cả chữ và số                | Có                  |
| Bắt buộc đổi định kỳ                | 90 ngày             |
| Sai mật khẩu bao nhiêu lần thì khóa | 5 lần trong 15 phút |
| Khóa trong bao lâu                  | 15 phút             |

> Đổi mật khẩu sẽ **đăng xuất tất cả thiết bị** đang đăng nhập bằng tài khoản của bạn.
> Đây là cố ý: khi bạn nghi bị lộ mật khẩu, việc đổi mật khẩu phải đẩy người kia ra ngay.

### 2.3. Hai màn hình cá nhân

<!-- man-hinh: /hop-thu, /phien-dang-nhap -->

Nằm ở nhóm **Cá nhân** cuối thanh menu bên trái:

- **Hộp thư** — nơi nhận mọi thông báo của hệ thống: bài viết chờ bạn duyệt, đơn nghỉ
  phép chờ duyệt, cảnh báo ngưỡng thủy văn, thông báo chung từ quản trị viên.
- **Phiên đăng nhập** — danh sách các thiết bị đang đăng nhập bằng tài khoản của bạn,
  kèm thời gian và địa chỉ. Thấy thiết bị lạ thì bấm **Đăng xuất** ngay tại đây, không
  cần chờ quản trị viên.

### 2.4. Khi không đăng nhập được

| Tình huống                       | Cách xử lý                                              |
| -------------------------------- | ------------------------------------------------------- |
| Quên mật khẩu                    | Liên hệ quản trị viên để cấp lại mật khẩu tạm           |
| Mất điện thoại (có mã khôi phục) | Đăng nhập, bấm _Dùng mã khôi phục_, nhập một mã đã chép |
| Mất điện thoại (không còn mã)    | Liên hệ quản trị viên để đặt lại xác thực 2 bước        |
| Báo "tài khoản đã khóa"          | Chờ hết thời gian khóa, hoặc nhờ quản trị viên mở khóa  |

---

## 3. Bản đồ màn hình

<!-- man-hinh: /, /huong-dan -->

Dùng bảng này để tìm nhanh: _"việc này làm ở đâu"_.

> **Bạn chỉ nhìn thấy những mục mà vai trò của bạn được cấp.** Menu ngắn hơn bảng dưới
> là bình thường, không phải lỗi.

### Vận hành công trình

| Màn hình                     | Dùng để làm gì                                                     |
| ---------------------------- | ------------------------------------------------------------------ |
| Dashboard điều hành          | Nhìn toàn cảnh: số công trình, sự cố đang mở, cảnh báo, bản đồ     |
| Hồ sơ công trình             | Danh mục công trình, thông số kỹ thuật, tài liệu, lịch sử sửa chữa |
| Cụm công trình               | Nhóm nhiều công trình thành một cụm quản lý                        |
| Danh mục tình trạng vận hành | Danh sách mã tình hình vận hành cống do Công ty tự đặt             |
| Lớp bản đồ GIS               | Nạp thêm lớp bản đồ (tuyến kênh, ranh giới…) lên bản đồ điều hành  |
| Báo cáo vận hành             | Kết xuất các báo cáo định kỳ ra tệp                                |
| Báo cáo nhanh                | Báo cáo ứng phó ngập úng theo mẫu Công ty, xuất bản Word           |
| Danh mục máy bơm             | Danh sách cỡ máy và nhóm máy từng trạm (phục vụ Báo cáo nhanh)     |

### Dữ liệu thủy văn

| Màn hình                     | Dùng để làm gì                                           |
| ---------------------------- | -------------------------------------------------------- |
| Danh mục điểm đo             | Khai báo các trạm/điểm quan trắc                         |
| Loại chỉ số quan trắc        | Khai báo loại số liệu: mực nước, lượng mưa…              |
| Nguồn dữ liệu                | Khai báo hệ thống bên ngoài mà máy chủ tự lấy số liệu về |
| Nhật ký đồng bộ              | Xem mỗi lượt lấy số liệu thành công hay thất bại, vì sao |
| Mã lạ từ nguồn               | Các mã trạm nguồn gửi về mà hệ thống chưa khai báo       |
| Dữ liệu nghi ngờ             | Số liệu bất thường đang bị treo, chờ người duyệt         |
| Mức cảnh báo                 | Bộ mức: Báo động I, II, III… do Công ty đặt              |
| Ngưỡng cảnh báo              | Gán ngưỡng cụ thể cho từng điểm đo                       |
| Cảnh báo ngưỡng              | Danh sách cảnh báo đang xảy ra và đã kết thúc            |
| Biểu tổng hợp tuyến sông     | Bảng mực nước theo tuyến sông (dùng cho màn hình lớn)    |
| Biểu đồ mực nước 24h         | Biểu đồ diễn biến trong ngày                             |
| Báo cáo tổng hợp kỳ          | Kết xuất số liệu theo khoảng thời gian                   |
| Báo cáo đồng bộ & chất lượng | Thống kê chất lượng dữ liệu thu được                     |

### Nội dung cổng · Nhân sự · Quản trị

| Nhóm                  | Các màn hình                                                                                                                                                                   |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Nội dung cổng**     | Bài viết · Hộp thư liên hệ · Góp ý & đánh giá · Danh mục · Thư viện media · Kho tài liệu · Giao diện cổng                                                                      |
| **Nhân sự**           | Sơ đồ tổ chức · Hồ sơ cán bộ · Danh bạ nội bộ · Báo cáo nhân sự · Cảnh báo hết hạn · Danh mục chức vụ · Hồ sơ của tôi · Nghỉ phép của tôi · Duyệt nghỉ phép · Ngày nghỉ lễ     |
| **Quản trị hệ thống** | Tài khoản · Vai trò & phân quyền · Sơ đồ đơn vị · Cấu hình hệ thống · Tình trạng cấu hình · Nhật ký kiểm toán · Sao lưu & khôi phục · Tình trạng hệ thống · Thông báo hệ thống |

---

## 4. Vai trò và quyền

### 4.1. Mười hai vai trò

| Vai trò                       | Làm được gì                                                                                    |
| ----------------------------- | ---------------------------------------------------------------------------------------------- |
| **Quản trị tối cao**          | Toàn quyền, kể cả khôi phục dữ liệu. Bắt buộc xác thực 2 bước                                  |
| **Quản trị hệ thống**         | Cấu hình, tài khoản, phân quyền, sao lưu, nhật ký. _Không_ xem được thông tin nhân sự nhạy cảm |
| **Quản trị nhân sự**          | Toàn quyền dữ liệu nhân sự, gồm cả trường nhạy cảm. Bắt buộc xác thực 2 bước                   |
| **Biên tập viên**             | Soạn bài viết và media — không tự xuất bản được                                                |
| **Quản trị nội dung**         | Duyệt, xuất bản, gỡ bài; quản lý danh mục, banner, hộp thư, góp ý                              |
| **Cán bộ văn thư**            | Đánh dấu văn bản điều hành công khai                                                           |
| **Cán bộ kỹ thuật**           | Hồ sơ công trình, bản đồ, điểm đo, ngưỡng, ghi nhận và khắc phục sự cố                         |
| **Quản lý Xí nghiệp**         | Duyệt hồ sơ, đóng sự cố, báo cáo — trong phạm vi Xí nghiệp mình                                |
| **Cán bộ vận hành Xí nghiệp** | Ghi nhận sự cố, cập nhật tình hình vận hành — trong phạm vi Xí nghiệp mình                     |
| **Trực ban điều hành**        | Nhận cảnh báo ngưỡng, theo dõi màn hình lớn                                                    |
| **Ban giám đốc / Điều hành**  | Xem dashboard và báo cáo tổng hợp                                                              |
| **Cán bộ nội bộ**             | Chỉ xem, theo phạm vi đơn vị được phân                                                         |

Một tài khoản có thể mang **nhiều vai trò** cùng lúc; quyền là hợp của tất cả.

### 4.2. Phạm vi đơn vị — điều dễ hiểu nhầm nhất

Ngoài "được làm gì", hệ thống còn giới hạn "được thấy dữ liệu của ai".

> Cán bộ của **Xí nghiệp 3** mở màn hình _Hồ sơ công trình_ sẽ **chỉ thấy công trình
> của Xí nghiệp 3**. Đây không phải bộ lọc bạn bấm nhầm — đó là phạm vi cố định gắn
> với đơn vị của tài khoản, và máy chủ áp nó, không màn hình nào bỏ qua được.

Hệ quả cần biết trước để khỏi hiểu lầm:

- **Hai người ở hai Xí nghiệp mở cùng một báo cáo sẽ ra hai bộ số khác nhau — và cả
  hai đều đúng.** Mỗi người thấy phần của đơn vị mình.
- **Danh bạ nội bộ** và **Sơ đồ tổ chức** là hai ngoại lệ cố ý: chúng hiện **toàn Công
  ty**, vì mục đích của chúng là để mọi người tra được số máy của nhau.

### 4.3. Ba điểm về bảo mật nên biết

1. **Menu ẩn không có nghĩa là dữ liệu được khóa.** Việc ẩn menu chỉ để bạn khỏi bấm
   vào thứ chắc chắn bị từ chối. Chốt chặn thật nằm ở máy chủ.
2. **Thông tin nhân sự nhạy cảm** (số CCCD, số tài khoản, lương) được mã hóa và chỉ
   _Quản trị tối cao_, _Quản trị nhân sự_ và **chính người đó** xem được. Ngay cả
   _Quản trị hệ thống_ cũng không xem được.
3. **Mọi thao tác thay đổi dữ liệu đều được ghi lại**, kèm giá trị cũ và giá trị mới,
   trong _Nhật ký kiểm toán_, lưu 5 năm.

---

## 5. Vận hành công trình

### 5.1. Dashboard điều hành

<!-- man-hinh: /van-hanh/dieu-hanh -->

Màn hình mở đầu ca trực. Gồm:

- **Dãy ô số** ở trên cùng: công trình đang hoạt động · sự cố chưa xử lý · công việc
  bảo trì đang thực hiện · cảnh báo thủy văn đang xảy ra · điểm đo mất tín hiệu ·
  công trình chưa có tọa độ.
- **Bản đồ** công trình và điểm đo, màu theo tình trạng.
- **Năm biểu đồ**: phân bố theo trạng thái · tỷ lệ số hóa tọa độ · theo loại công trình ·
  theo cấp quản lý · theo đơn vị quản lý.

> **Ô trống khác ô số 0.** Một ô ghi `0` nghĩa là _đã đếm và không có gì_. Một ô để
> trống kèm chú thích nghĩa là _chưa có nguồn số liệu_. Hệ thống không bao giờ hiển
> thị 0 thay cho "chưa biết".

**Chế độ màn hình lớn (wall):** thêm `?mode=wall` vào cuối địa chỉ để chuyển sang giao
diện chữ to, nền tối, dùng cho màn hình treo tường Phòng điều hành. Nội dung giống hệt,
chỉ khác cỡ chữ và màu nền.

### 5.2. Hồ sơ công trình

<!-- man-hinh: /van-hanh/cong-trinh, /van-hanh/cum-cong-trinh -->

Mỗi công trình có một hồ sơ gồm **3 thẻ**:

| Thẻ                   | Nội dung                                                                                  |
| --------------------- | ----------------------------------------------------------------------------------------- |
| **Hồ sơ công trình**  | 4 bước nhập: Thông tin chung → Vị trí & Lưu vực → Thông số kỹ thuật → Tổ chức & Tài chính |
| **Tài liệu đính kèm** | Bản vẽ, quyết định, ảnh hiện trạng                                                        |
| **Lịch sử sửa chữa**  | Toàn bộ bản ghi sửa chữa, bảo trì và khắc phục sự cố                                      |

**Trạng thái công trình là do máy tính ra, không ai sửa tay được.** Thứ tự ưu tiên:

| Xét theo thứ tự                           | Trạng thái hiện ra      |
| ----------------------------------------- | ----------------------- |
| 1. Có bản ghi khắc phục sự cố đang mở     | **Sự cố** (đỏ)          |
| 2. Đang có việc bảo trì                   | **Đang bảo trì** (vàng) |
| 3. Có cảnh báo ngưỡng thủy văn            | **Cảnh báo** (vàng)     |
| 4. Có mã tình hình vận hành đang hiệu lực | theo mã Công ty đặt     |
| 5. Không rơi vào các mục trên             | **Bình thường** (xanh)  |

Muốn đổi trạng thái, hãy tác động vào **nguyên nhân** — đóng bản ghi sự cố, kết thúc
việc bảo trì — chứ không tìm ô để sửa trạng thái. Không có ô đó.

**Vòng đời hồ sơ** là việc khác, và sửa được: _Đang hoạt động_ → _Ngừng mùa vụ_ →
_Đã thanh lý_.

### 5.3. Ghi nhận sửa chữa, bảo trì và sự cố

<!-- man-hinh: /van-hanh/cong-trinh -->

> **Sự cố không phải một loại hồ sơ riêng.** Nó là một bản ghi trong _Lịch sử sửa chữa_
> mang loại **Khắc phục sự cố**. Không có màn hình "phiếu sự cố" nào khác để tìm.

Đường đi: _Hồ sơ công trình_ → mở công trình → thẻ **Lịch sử sửa chữa** → **Thêm**.

Các trường cần điền:

| Trường                                | Ghi chú                                                                         |
| ------------------------------------- | ------------------------------------------------------------------------------- |
| Loại công việc                        | Sửa chữa · Bảo trì định kỳ · Nâng cấp · Thay thế thiết bị · **Khắc phục sự cố** |
| Mức độ                                | Chỉ hiện khi chọn _Khắc phục sự cố_: Nghiêm trọng · Cao · Trung bình · Thấp     |
| Nội dung công việc                    | Mô tả                                                                           |
| Hạng mục / thiết bị                   | Bộ phận nào của công trình                                                      |
| Ngày bắt đầu / hoàn thành             | Bỏ trống ngày hoàn thành = việc đang thực hiện                                  |
| Đơn vị thực hiện                      | Đơn vị nội bộ hoặc nhà thầu ngoài                                               |
| Chi phí (triệu VNĐ) và nguồn kinh phí |                                                                                 |

Bản ghi có ba trạng thái: **Mới** → **Đang xử lý** → **Đã xử lý**. Chừng nào một bản
ghi loại _Khắc phục sự cố_ chưa **Đã xử lý**, công trình còn mang trạng thái **Sự cố**
trên dashboard và trên bản đồ.

### 5.4. Cập nhật tình hình vận hành cống

<!-- man-hinh: /van-hanh/danh-muc-tinh-hinh, /van-hanh/cong-trinh -->

Số liệu này **không có trong dữ liệu tự động**, phải nhập tay.

1. Danh sách mã (_Đóng hoàn toàn_, _Mở 1 cửa_…) do Công ty tự quản lý ở _Danh mục tình
   trạng vận hành_ — thêm mã mới không cần chờ nâng cấp phần mềm.
2. Nhập số liệu: màn hình _Hồ sơ công trình_ → nút **Nhập nhanh tình hình vận hành**,
   chọn nhiều công trình cùng lúc, chọn mã cho từng công trình, bấm Lưu.

> Chỉ những dòng bạn thực sự chọn mã mới được ghi. Bỏ trống một dòng nghĩa là
> "không cập nhật", không phải "xóa giá trị cũ".

### 5.5. Báo cáo vận hành

<!-- man-hinh: /van-hanh/bao-cao -->

Chọn mã báo cáo, chọn khoảng thời gian, bấm tải về. Các báo cáo đang dùng được:

| Mã    | Tên                              | Nội dung                                                               |
| ----- | -------------------------------- | ---------------------------------------------------------------------- |
| BC-06 | Cảnh báo & sự cố                 | Cảnh báo ngưỡng toàn hệ + bản ghi khắc phục sự cố theo phạm vi đơn vị  |
| BC-09 | Tổng hợp sửa chữa / bảo trì      | Từng bản ghi trong kỳ kèm chi phí, có dòng tổng                        |
| BC-10 | Danh mục & hiện trạng công trình | Toàn bộ công trình kèm loại, cấp quản lý, vòng đời, tình trạng, tọa độ |

Bốn mã **BC-01 → BC-04** vẫn hiện trong danh sách nhưng **không chọn được**, kèm lý do
ngay tại chỗ. Đây là cố ý — xem [phần 10](#10-những-ô-đang-trống-và-vì-sao).

Báo cáo hiện xuất ra **tệp CSV** (mở được bằng Excel). Bản in định dạng đang chờ Công ty
duyệt mẫu.

### 5.6. Báo cáo nhanh ứng phó ngập úng

<!-- man-hinh: /van-hanh/bao-cao-nhanh, /van-hanh/may-bom -->

Khác với các báo cáo ở mục trên: đây không phải "chọn mã rồi tải về", mà là một **hồ sơ
có vòng đời**, nhập số liệu trong đó.

```
Tạo kỳ báo cáo (chọn khung giờ, mặc định 6h–16h hôm nay)
        ↓
Nhập số liệu — Đang nhập
        ↓
Chốt kỳ → Đã chốt  (mọi ô nhập khóa lại)
        ↓
Tải bản Word theo đúng mẫu của Công ty
```

Các khối cần nhập: số máy bơm đang chạy của từng trạm · diện tích ngập trắng và sâu nước
theo địa phương · mực nước tại các cống · lượng mưa.

> **"Chốt kỳ" giữ nguyên số của thời điểm chốt.** Sau này danh mục máy bơm có thay đổi,
> bản Word đã xuất vẫn giữ đúng con số lúc chốt. Cần sửa thì **Mở lại** — thao tác này
> bắt nhập lý do và được ghi vào nhật ký.

Ô để trống thì bản Word **để trống**, không in số 0.

### 5.7. Lớp bản đồ GIS

<!-- man-hinh: /van-hanh/lop-ban-do -->

Nạp thêm lớp lên bản đồ điều hành: tuyến kênh, ranh giới, vùng tưới…

- **Chỉ nhận định dạng GeoJSON.** Nạp tệp KML/KMZ sẽ bị từ chối kèm thông báo rõ lý do.
  Hệ thống không nhận rồi để lớp không hiển thị được — như vậy người dùng sẽ tưởng bản
  đồ hỏng.
- Độ mờ nhập theo **phần trăm nguyên, 0–100**.
- Thứ tự chồng lớp kéo–thả trực tiếp trong danh sách.

---

## 6. Dữ liệu thủy văn

### 6.1. Số liệu về bằng cách nào

```
Hệ thống quan trắc bên ngoài
        │  máy chủ tự hỏi 2 phút một lần
        ↓
Nhật ký đồng bộ      ← xem mỗi lượt thành công hay thất bại tại đây
        ↓
Phân loại chất lượng: Hợp lệ  /  Nghi ngờ
        ↓
Biểu đồ · Báo cáo · Cảnh báo   ← CHỈ dùng số liệu "Hợp lệ"
```

> **Điều quan trọng nhất của cả module này:** số liệu bị đánh dấu **Nghi ngờ** vẫn nằm
> trong cơ sở dữ liệu nhưng **không được tính vào bất kỳ biểu đồ, báo cáo hay cảnh báo
> nào**. Màn hình _Dữ liệu nghi ngờ_ là nơi duy nhất nhìn thấy chúng. Không ai mở màn
> hình đó thì số liệu bị treo sẽ nằm im mãi mãi.

Nguồn không có API tra cứu lịch sử. **Số liệu của một khung giờ không lấy về được thì
mất vĩnh viễn** — vì vậy _Nhật ký đồng bộ_ cần được xem thường xuyên như xem sao lưu.

### 6.2. Khai báo điểm đo

<!-- man-hinh: /thuy-van/diem-do, /thuy-van/loai-chi-so -->

_Danh mục điểm đo_ → **Thêm**. Các trường:

| Trường               | Ý nghĩa                                                                                                |
| -------------------- | ------------------------------------------------------------------------------------------------------ |
| Mã nội bộ            | Mã Công ty tự đặt                                                                                      |
| **Mã ánh xạ API**    | Mã mà hệ thống nguồn dùng cho trạm này — **sai mã là toàn bộ lịch sử của trạm này chảy vào trạm khác** |
| Nguồn dữ liệu        | Lấy số từ nguồn nào                                                                                    |
| Loại chỉ số đo được  | Mực nước, lượng mưa…                                                                                   |
| Kinh độ / Vĩ độ      | Để hiện lên bản đồ. **Phải nhập đủ cả cặp** — thiếu một nửa là bị từ chối                              |
| Lý trình, tuyến sông | Vị trí trên tuyến                                                                                      |
| Giá trị nội suy      | Bật khi nguồn đánh dấu điểm này là số nội suy, không đo trực tiếp — để báo cáo phân biệt được          |
| Đang dùng            | Tắt thì hệ thống ngừng lấy số liệu của điểm đo này                                                     |

### 6.3. Nguồn dữ liệu và mã số truy cập

<!-- man-hinh: /thuy-van/nguon-du-lieu -->

_Nguồn dữ liệu_ là nơi khai báo hệ thống bên ngoài. Mã số truy cập đặt bằng biểu tượng
**chìa khóa**.

> **Màn hình không bao giờ hiển thị lại mã số**, kể cả cho Quản trị tối cao. Muốn đổi
> thì gõ mã mới. Không có ô "xem mã hiện tại" — đó là cố ý.

Khi một nguồn hỏng, cột **Việc phải làm** nói rõ phải làm gì:

| Thông báo               | Nghĩa là                  | Việc phải làm                                                                   |
| ----------------------- | ------------------------- | ------------------------------------------------------------------------------- |
| Chưa cấu hình mã số     | Chưa ai đặt mã            | Bấm chìa khóa để đặt                                                            |
| Nguồn từ chối mã số     | Mã sai hoặc hết hạn       | ⚠ Kiểm tra mã còn dấu `;` ở cuối không — thiếu dấu đó cho ra đúng thông báo này |
| Nguồn không trả lời kịp | Mạng chậm                 | Kiểm tra đường mạng trước khi nới thời gian chờ                                 |
| Không gọi được nguồn    | Sai địa chỉ hoặc mất mạng | Kiểm tra địa chỉ nguồn và đường ra Internet                                     |
| Trả về rỗng             | Phía nguồn đang bảo trì   | Chờ và theo dõi                                                                 |

Nguồn hỏng liên tiếp 3 lượt thì hệ thống tự gửi cảnh báo vào hộp thư người có quyền.

### 6.4. Nhật ký đồng bộ — đọc cho đúng

<!-- man-hinh: /thuy-van/nhat-ky-dong-bo -->

Bốn bộ đếm hiện riêng, và **`Ghi mới = 0` là chuyện bình thường**:

> Máy chủ hỏi nguồn 2 phút một lần, trong khi nguồn chỉ cập nhật 10 phút một lần. Vậy
> **4 trên 5 lượt sẽ là "Bỏ qua — đã đủ"**, và đó là đúng. Dòng "Bỏ qua" **không** tô
> màu đỏ.

Chỉ những dòng **đỏ** mới là hỏng thật.

### 6.5. Mã lạ từ nguồn

<!-- man-hinh: /thuy-van/ma-la -->

Nguồn gửi về khoảng 28 mã trạm, hệ thống mới khai báo 19. Chín mã còn lại hiện ở đây.

- Hệ thống **không tự tạo điểm đo** từ mã lạ. Đoán sai danh tính một trạm là toàn bộ
  lịch sử của trạm đó đi vào biểu đồ của trạm khác — mà biểu đồ vẫn vẽ đẹp.
- Nút _Khai thành điểm đo_ chỉ mở biểu mẫu với ô mã điền sẵn; bạn vẫn phải biết và điền
  trạm đó là gì.
- **Cột giá trị là số nguyên văn từ nguồn, chưa quy đổi.** Đơn vị luôn in cạnh con số —
  nguồn trả mực nước bằng **cm**, hệ thống lưu bằng **m**.

### 6.6. Ngưỡng và cảnh báo

<!-- man-hinh: /thuy-van/muc-canh-bao, /thuy-van/nguong-canh-bao, /thuy-van/canh-bao -->

Ba bước, theo thứ tự:

```
1. Mức cảnh báo      → khai bộ mức của Công ty (Báo động I, II, III…)
2. Ngưỡng cảnh báo   → gán giá trị cụ thể cho từng điểm đo + loại chỉ số
3. Cảnh báo ngưỡng   → theo dõi cảnh báo phát sinh
```

Khi đặt ngưỡng có ô **"Chờ bao lâu mới báo động (phút)"** — điều kiện phải giữ liên tục
bấy nhiêu phút mới phát cảnh báo, để tránh nhiễu cảm biến.

Đầu trang _Ngưỡng cảnh báo_ có khối **"Điểm đo chưa cấu hình ngưỡng"**. Hãy để mắt tới
nó: điểm đo không có ngưỡng thì dù nước lên bao nhiêu cũng không có cảnh báo nào.

**Đọc bảng lịch sử cảnh báo — hai cột dễ hiểu nhầm:**

| Bạn thấy                                      | Nghĩa thật                                                                   |
| --------------------------------------------- | ---------------------------------------------------------------------------- |
| _Đang xảy ra_ nhưng cột **Đã báo động** trống | Điều kiện chưa giữ đủ số phút cấu hình — **chưa ai nhận được thông báo nào** |
| _Đã kết thúc_ nhưng cột **Người đóng** trống  | Máy tự đóng vì giá trị đã về dưới ngưỡng — **không phải đã có người xử lý**  |

### 6.7. Duyệt dữ liệu nghi ngờ

<!-- man-hinh: /thuy-van/du-lieu-nghi-ngo -->

Mở _Dữ liệu nghi ngờ_, xem từng dòng, rồi chọn:

- **Duyệt là số liệu thật** — dòng đó chuyển sang _Hợp lệ_ và bắt đầu được tính vào
  biểu đồ, báo cáo, cảnh báo.
- **Loại bỏ** — đánh dấu là số rác.

> Bảng rỗng có **ba** nghĩa khác nhau, và màn hình nói rõ đang là nghĩa nào: bộ phân
> loại đang chạy và không có gì đáng ngờ · chưa ai cấu hình quy tắc · cấu hình có nhưng
> đang hỏng. Hai trường hợp sau cần xử lý.

### 6.8. Biểu đồ và báo cáo thủy văn

<!-- man-hinh: /thuy-van/bieu-tuyen-song, /thuy-van/bieu-do-muc-nuoc, /thuy-van/bao-cao-tong-hop, /thuy-van/bao-cao-dong-bo -->

Bốn màn hình đọc số liệu, không nhập gì:

| Màn hình                         | Trả lời câu hỏi                                                               |
| -------------------------------- | ----------------------------------------------------------------------------- |
| **Biểu đồ mực nước 24h**         | Mực nước một điểm đo diễn biến thế nào trong ngày                             |
| **Biểu tổng hợp tuyến sông**     | Cả tuyến sông đang ở mức nào — bảng dùng chung với màn hình treo tường        |
| **Báo cáo tổng hợp kỳ**          | Cao nhất, thấp nhất, trung bình trong một khoảng thời gian, kèm thời điểm đạt |
| **Báo cáo đồng bộ & chất lượng** | Trong kỳ lấy được bao nhiêu số, bao nhiêu bị treo, nguồn hỏng mấy lượt        |

> ⚠ **Cả bốn đều chỉ tính trên số liệu _Hợp lệ_.** Một đoạn biểu đồ bị hụt hoặc một con số
> trung bình trông lệch thường không phải lỗi biểu đồ — hãy mở _Dữ liệu nghi ngờ_ xem khoảng
> thời gian đó có bản ghi nào đang bị treo chờ duyệt không (§6.7).

Báo cáo xuất ra tệp CSV, mở được bằng Excel.

---

## 7. Nội dung cổng thông tin

### 7.1. Quy trình duyệt bài viết

<!-- man-hinh: /noi-dung/bai-viet, /noi-dung/danh-muc -->

Sáu trạng thái, và mỗi bước chuyển là một nút bấm:

| Đang ở trạng thái | Bấm nút           | Chuyển sang       | Ai bấm được       |
| ----------------- | ----------------- | ----------------- | ----------------- |
| Nháp              | Gửi duyệt         | Chờ duyệt         | Biên tập viên     |
| Chờ duyệt         | Duyệt và xuất bản | Xuất bản          | Quản trị nội dung |
| Chờ duyệt         | Yêu cầu chỉnh sửa | Yêu cầu chỉnh sửa | Quản trị nội dung |
| Yêu cầu chỉnh sửa | Gửi duyệt lại     | Chờ duyệt         | Biên tập viên     |
| Xuất bản          | Gửi duyệt bản sửa | Chờ duyệt         | Biên tập viên     |
| Xuất bản          | Gỡ bài            | Gỡ bài            | Quản trị nội dung |
| Xuất bản / Gỡ bài | Lưu trữ           | Lưu trữ           | Quản trị nội dung |
| Gỡ bài / Lưu trữ  | Đăng lại          | Xuất bản          | Quản trị nội dung |

- **Biên tập viên** soạn và gửi duyệt, không tự xuất bản được.
- **Quản trị nội dung** duyệt, xuất bản, gỡ bài.
- Người gửi nhận thông báo khi bài được duyệt hoặc bị trả về.
- Đăng lại một bài đã gỡ hoặc đã lưu trữ **không phải duyệt lại**.

**Hẹn giờ đăng:** điền ô _Thời điểm đăng_ trong biểu mẫu soạn bài. Bài được duyệt nhưng
chưa tới giờ sẽ mang trạng thái **Đã duyệt** — đã qua duyệt nhưng **chưa hiện trên cổng**.
Tới giờ, hệ thống tự chuyển sang _Xuất bản_.

**Các nút hiện trên màn hình là do máy chủ quyết định**, theo trạng thái bài **và** theo
quyền của bạn. Không thấy nút nghĩa là bước đó bạn không được làm — không phải màn hình lỗi.

> **Đường dẫn bài (slug) không tự đổi sau khi bài đã xuất bản.** Sửa lỗi chính tả trong
> tiêu đề sẽ không làm chết các liên kết đã chia sẻ.

### 7.2. Hộp thư liên hệ

<!-- man-hinh: /noi-dung/hop-thu-lien-he -->

Tiếp nhận biểu mẫu người dân gửi từ cổng.

```
Mới → Đã đọc → Đang xử lý → Đã phản hồi → Đóng → Lưu trữ
                                           ↑
                                     (mở lại được)
```

> **Đang xử lý** là trạng thái duy nhất không xóa được — tránh xóa nhầm việc đang làm dở.

### 7.3. Góp ý & đánh giá

<!-- man-hinh: /noi-dung/gop-y -->

Mọi góp ý gửi từ cổng đều **chờ duyệt**, không tự hiện. Duyệt → hiện trên cổng. Đã duyệt
vẫn **Ẩn** lại được; **Ẩn** và **Từ chối** đều duyệt lại được.

### 7.4. Thư viện media và Kho tài liệu

<!-- man-hinh: /noi-dung/thu-vien, /noi-dung/kho-tai-lieu -->

Hai kho, chung một màn hình, khác nhau ở **phạm vi công bố**:

- **Thư viện media** — ảnh, video dùng trong bài viết.
- **Kho tài liệu** — văn bản, biểu mẫu công bố trên cổng.

> **Xóa tệp thì hệ thống hỏi trước "tệp này đang được dùng ở đâu".** Xóa một ảnh đang
> nằm trong bài đã xuất bản sẽ làm bài đó thủng ảnh trên cổng, mà người xóa không hề biết.

Mọi tệp tải lên đều được **quét virus** trước khi dùng được. Trong lúc quét, tệp ở trạng
thái _Đang quét_ và chưa tải về được.

### 7.5. Giao diện cổng

<!-- man-hinh: /noi-dung/giao-dien -->

Ba việc gộp một chỗ vì cùng trả lời câu hỏi _"cổng trông như thế nào"_:

| Thẻ            | Nội dung                                        |
| -------------- | ----------------------------------------------- |
| Cấu hình chung | Logo, tên Công ty, thông tin liên hệ chân trang |
| Banner         | Ảnh chạy đầu trang, đặt được lịch bật/tắt       |
| Menu           | Cây menu của cổng, kéo–thả đổi thứ tự           |

---

## 8. Nhân sự

<!-- man-hinh: /nhan-su/so-do-to-chuc, /nhan-su/ho-so, /nhan-su/danh-ba, /nhan-su/bao-cao, /nhan-su/canh-bao-het-han, /nhan-su/chuc-vu, /nhan-su/ho-so-cua-toi -->

| Màn hình             | Ai vào được      | Nội dung                                                           |
| -------------------- | ---------------- | ------------------------------------------------------------------ |
| **Sơ đồ tổ chức**    | Hầu hết cán bộ   | Cây đơn vị kèm quân số, xuất được ảnh PNG/SVG. **Toàn Công ty**    |
| **Danh bạ nội bộ**   | 11/12 vai trò    | Tra tên, chức vụ, số máy. **Toàn Công ty**                         |
| **Hồ sơ cán bộ**     | Chỉ 3 vai trò    | Hồ sơ đầy đủ. **Cắt theo đơn vị**                                  |
| **Cảnh báo hết hạn** | Như hồ sơ cán bộ | Hợp đồng và chứng chỉ sắp/đã hết hạn, sắp xếp hạn gần nhất lên đầu |
| **Báo cáo nhân sự**  | Có quyền báo cáo | 8 mã báo cáo, xuất CSV                                             |

**Quân số trên sơ đồ tổ chức hiện hai số**: `trực tiếp / cả nhánh`. Một Xí nghiệp có 4
Tổ đội sẽ hiện `2 / 37` — 2 người thuộc trực tiếp Xí nghiệp, 37 người tính cả các Tổ đội
bên dưới. Khi hai số bằng nhau, chỉ hiện một số.

### Nghỉ phép

<!-- man-hinh: /nhan-su/nghi-phep, /nhan-su/duyet-nghi-phep, /nhan-su/ngay-le -->

```
Nhân viên: Nghỉ phép của tôi → Nộp đơn
        ↓
Quản lý đơn vị: Duyệt nghỉ phép → Duyệt / Từ chối (nêu lý do)
        ↓
Đơn đã duyệt vẫn Hủy được (nêu lý do)
```

- Người quản lý **chỉ thấy đơn trong phạm vi đơn vị mình**.
- Số ngày công trừ đi đã loại trừ ngày nghỉ lễ, theo danh sách ở _Ngày nghỉ lễ_.
- Khi nộp đơn, hệ thống cảnh báo nếu đơn vị đã có nhiều người nghỉ trùng lịch.
- Hệ thống hỗ trợ **duyệt hai cấp** cho đơn dài ngày. Mặc định đang bật **một cấp**; bật
  hai cấp và đặt ngưỡng số ngày tại _Cấu hình hệ thống_.
- Ngày lễ dương lịch cố định (theo Điều 112 Bộ luật Lao động 2019) đã có sẵn. **Tết Âm
  lịch và Giỗ Tổ phải nhập tay hằng năm** vì chúng theo lịch âm. Màn hình hiện rõ đã khai
  bao nhiêu trên tổng số ngày cần khai.

_Hồ sơ của tôi_ và _Nghỉ phép của tôi_ chỉ hiện với tài khoản **đã được liên kết với một
hồ sơ cán bộ**. Chưa thấy hai mục này thì nhờ quản trị viên liên kết giúp ở màn hình
_Tài khoản_.

---

## 9. Quản trị hệ thống

### 9.1. Tài khoản

<!-- man-hinh: /quan-tri/tai-khoan -->

_Quản trị hệ thống › Tài khoản_. Thao tác có sẵn:

| Thao tác                | Ghi chú                                                             |
| ----------------------- | ------------------------------------------------------------------- |
| Thêm tài khoản          | Nhập tên đăng nhập, họ tên, email, điện thoại, đơn vị, mật khẩu tạm |
| Gán vai trò             | Một tài khoản nhận được nhiều vai trò                               |
| Liên kết hồ sơ cán bộ   | Cần cho _Hồ sơ của tôi_ và _Nghỉ phép của tôi_                      |
| Đặt lại mật khẩu        | Cấp mật khẩu tạm mới, người dùng phải đổi khi đăng nhập             |
| Đặt lại xác thực 2 bước | Dùng khi người dùng mất điện thoại và hết mã khôi phục              |
| Khóa / Mở khóa          |                                                                     |

> **Chọn đúng đơn vị khi tạo tài khoản.** Đơn vị quyết định người đó nhìn thấy dữ liệu
> của ai — sửa sau được nhưng dễ bị quên.

### 9.2. Vai trò & phân quyền

<!-- man-hinh: /quan-tri/vai-tro -->

Sửa được ma trận quyền của từng vai trò. Vai trò _Quản trị tối cao_ là vai trò hệ thống,
không sửa và không xóa được.

### 9.3. Sơ đồ đơn vị

<!-- man-hinh: /quan-tri/don-vi -->

Cây đơn vị, dùng chung cho cả Xí nghiệp (vận hành) và phòng ban (nhân sự).

> ⚠ **Cây này là biên giới phân quyền.** Chuyển một đơn vị sang nhánh khác sẽ đổi phạm
> vi dữ liệu của **mọi tài khoản** thuộc nhánh đó. Thao tác chuyển có bước xác nhận
> riêng. Giải thể một đơn vị đang có người hoặc có công trình sẽ bị từ chối, kèm danh
> sách những gì đang vướng.

Ba ô liên hệ của mỗi đơn vị (địa chỉ, điện thoại, email) đổ thẳng ra bảng "Xí nghiệp
trực thuộc" trên cổng công khai.

### 9.4. Cấu hình hệ thống

<!-- man-hinh: /quan-tri/cau-hinh, /quan-tri/tinh-trang-cau-hinh -->

Nơi đặt toàn bộ tham số nghiệp vụ, **không cần nâng cấp phần mềm**:

| Nhóm     | Ví dụ tham số                                                           |
| -------- | ----------------------------------------------------------------------- |
| Bảo mật  | Độ dài mật khẩu, số lần sai bị khóa, giờ hành chính                     |
| Sao lưu  | Giữ bản sao lưu bao nhiêu ngày (mặc định 30), bật/tắt lịch tự động      |
| Thủy văn | Chu kỳ hỏi nguồn, số lượt hỏng thì cảnh báo, số năm giữ dữ liệu         |
| Vận hành | Tâm bản đồ, mức phóng to mặc định, số ngày coi tình hình vận hành là cũ |
| Nhân sự  | Số ngày báo trước hợp đồng hết hạn, thông số phép năm                   |
| Giới hạn | Dung lượng tối đa mỗi loại tệp tải lên                                  |
| Nhật ký  | Số năm giữ nhật ký kiểm toán (mặc định 5)                               |

Có một màn hình riêng — _Tình trạng cấu hình_ — cho biết những cấu hình hạ tầng (máy chủ
thư, kênh cảnh báo…) đã đặt chưa và thiếu thì hỏng gì. Màn hình này **chỉ báo có/không,
không bao giờ hiện giá trị**.

### 9.5. Nhật ký kiểm toán

<!-- man-hinh: /quan-tri/nhat-ky -->

Ghi lại mọi thay đổi dữ liệu: ai, lúc nào, giá trị cũ, giá trị mới. Giữ 5 năm.

Có nút **Kiểm tra tính toàn vẹn**. Mỗi dòng nhật ký mang một "dấu niêm phong" tính từ nội
dung dòng đó cộng dấu niêm phong của dòng trước, do cơ sở dữ liệu tự tính. Bấm nút này là
kiểm cả chuỗi.

> **Kết quả rỗng nghĩa là nguyên vẹn** — không có dòng nào bị sửa hay bị xóa.

### 9.6. Sao lưu & khôi phục

<!-- man-hinh: /quan-tri/sao-luu -->

**Phần sao lưu:** trang hiện rõ tình trạng — bản gần nhất lúc nào, dung lượng bao nhiêu,
lịch tự động đang bật hay tắt. Có cảnh báo nổi bật khi bản gần nhất đã quá hạn hoặc khi
lịch tự động đang tắt. Nút **Sao lưu ngay** chạy nền, kết quả xem ở bảng lịch sử.

Hệ thống tự tạo bản sao lưu trong hai trường hợp: **trước mỗi lần nâng cấp** và **ngay
trước khi khôi phục**.

**Phần khôi phục** — thao tác nguy hiểm nhất của toàn hệ thống. Nó **ghi đè toàn bộ cơ sở
dữ liệu hiện tại**. Hệ thống bắt qua **ba lớp xác nhận**:

```
1. Gõ chính xác cụm từ:  SONGNHUE
2. Nhập lý do khôi phục   (vào nhật ký bảo mật, không sửa lại được)
3. Nhập mã xác thực 2 bước đang hiển thị trên ứng dụng
```

Chỉ **Quản trị tối cao** làm được.

### 9.7. Tình trạng hệ thống

<!-- man-hinh: /quan-tri/tinh-trang -->

Đèn xanh/đỏ cho từng thành phần, tự làm mới mỗi 30 giây:

| Thành phần     | Hỏng thì sao                      |
| -------------- | --------------------------------- |
| Cơ sở dữ liệu  | Toàn hệ thống dừng                |
| Kho tệp        | Không tải lên/tải về được tệp nào |
| Máy chủ thư    | Không gửi được email thông báo    |
| Sao lưu        | Bản sao lưu gần nhất đã quá cũ    |
| Nguồn thủy văn | Không lấy được số liệu quan trắc  |

### 9.8. Thông báo hệ thống

<!-- man-hinh: /quan-tri/thong-bao -->

Gửi thông báo tới toàn bộ hoặc một nhóm tài khoản.

> **Gửi đi là không thu hồi được** — thông báo đã nằm trong hộp thư mọi người và email đã
> rời máy chủ. Vì vậy có bước xác nhận, và mặc định là gửi **toàn bộ** tài khoản đang
> hoạt động. Kiểm kỹ phạm vi trước khi bấm gửi.

---

## 10. Những ô đang trống và vì sao

Một số chỗ trong hệ thống hiện đang trống hoặc bị khóa. **Đây là trạng thái đúng, không
phải lỗi.** Nguyên tắc của hệ thống là: chỗ nào chưa có nguồn số liệu thì nói thẳng là
chưa có, tuyệt đối không điền số minh họa.

| Chỗ trống                                | Vì sao                                                                                                                      | Khi nào có                                                                                |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **Bản đồ chưa hiện điểm nào**            | Các điểm đo và công trình chưa có tọa độ                                                                                    | Công ty nhập tọa độ tại _Danh mục điểm đo_ và _Hồ sơ công trình_, hoặc tải lên tệp tọa độ |
| **Danh sách cán bộ rỗng**                | Công ty chưa cung cấp danh sách CBNV                                                                                        | Khi có danh sách, nhập vào là dùng được ngay                                              |
| **Báo cáo BC-01 → BC-04 bị khóa**        | Không phải chưa làm, mà **đã bỏ vĩnh viễn**: nhật ký vận hành và kế hoạch vụ mùa đã được Công ty xác nhận loại khỏi phạm vi | Không bao giờ — lý do in ngay tại chỗ                                                     |
| **Báo cáo BCNS-07 (mẫu 2C-BNV) bị khóa** | Mẫu do Bộ Nội vụ quy định, cấm tự chế bố cục; Công ty chưa gửi tệp mẫu gốc                                                  | Khi có tệp mẫu thật                                                                       |
| **Báo cáo chỉ xuất CSV, chưa có bản in** | Bố cục bản in đang chờ Công ty duyệt mẫu                                                                                    | Khi mẫu được duyệt                                                                        |
| **Chưa có ngưỡng cảnh báo nào**          | Bộ mức cảnh báo là số liệu chuyên môn của Công ty                                                                           | Khi Công ty đưa bộ mức, khai tại _Mức cảnh báo_                                           |
| **Chưa có lượng mưa tự động**            | Nguồn quan trắc chưa cung cấp chỉ số này                                                                                    | Hiện nhập tay trong Báo cáo nhanh                                                         |

> Các dòng _tọa độ_, _danh sách cán bộ_ và _ngưỡng cảnh báo_ **chỉ cần Công ty nhập số
> liệu vào màn hình đã có sẵn** — không phải chờ nâng cấp phần mềm. Riêng **BC-01 → BC-04
> sẽ không bao giờ mở lại**: chúng bị khóa vì chính Công ty đã xác nhận loại bỏ nghiệp vụ
> đó, không phải vì phần mềm còn thiếu.

---

## 11. Xử lý tình huống thường gặp

| Hiện tượng                                   | Nguyên nhân thường gặp                     | Cách xử lý                                                            |
| -------------------------------------------- | ------------------------------------------ | --------------------------------------------------------------------- |
| Menu ít mục hơn đồng nghiệp                  | Vai trò khác nhau                          | Bình thường. Cần thêm thì đề nghị quản trị viên cấp quyền             |
| Mở màn hình báo "không có quyền"             | Vai trò chưa được cấp                      | Đề nghị quản trị viên                                                 |
| Danh sách công trình thiếu                   | Phạm vi đơn vị của tài khoản               | Bình thường — bạn chỉ thấy phần của đơn vị mình                       |
| Bấm Lưu báo thành công nhưng cổng chưa đổi   | Cổng có bộ nhớ đệm, chậm vài phút          | Chờ rồi tải lại trang                                                 |
| Biểu đồ thủy văn bị hụt đoạn                 | Số liệu khoảng đó đang là _Nghi ngờ_       | Mở _Dữ liệu nghi ngờ_, xem và duyệt nếu là số thật                    |
| Số liệu thủy văn đứng yên                    | Nguồn đang hỏng                            | Mở _Nhật ký đồng bộ_ → đọc cột Việc phải làm                          |
| Nước lên nhưng không có cảnh báo             | Điểm đo chưa cấu hình ngưỡng               | Xem khối "Điểm đo chưa cấu hình ngưỡng" ở đầu trang _Ngưỡng cảnh báo_ |
| Không tìm thấy nút đổi trạng thái công trình | Trạng thái do máy tính ra                  | Tác động vào nguyên nhân: đóng sự cố, kết thúc bảo trì                |
| Không tìm thấy màn hình "phiếu sự cố"        | Sự cố là một dòng trong _Lịch sử sửa chữa_ | Mở công trình → thẻ Lịch sử sửa chữa                                  |
| Không thấy _Hồ sơ của tôi_                   | Tài khoản chưa liên kết hồ sơ cán bộ       | Nhờ quản trị viên liên kết                                            |
| Tải tệp lên xong chưa tải về được            | Đang quét virus                            | Chờ tệp chuyển sang _Đã quét sạch_                                    |
| Tải tệp bị từ chối                           | Vượt dung lượng hoặc sai định dạng         | Xem giới hạn ở _Cấu hình hệ thống_ nhóm Giới hạn                      |
| Nạp lớp bản đồ bị từ chối                    | Tệp KML/KMZ                                | Chuyển sang GeoJSON                                                   |

### Khi cần báo lỗi

Kèm theo ba thông tin này sẽ rút ngắn rất nhiều thời gian xử lý:

1. **Mã lỗi** hiện trên thông báo (dạng `OPS-2003`, `HYD-2016`…)
2. **Màn hình và thao tác** đang làm khi lỗi xảy ra
3. **Thời điểm** (giờ và phút)

---

_Tài liệu này mô tả hệ thống tại thời điểm 20/09/2026. Các tham số nêu trong tài liệu là
giá trị mặc định — giá trị đang áp dụng luôn xem tại Quản trị hệ thống › Cấu hình hệ thống._
