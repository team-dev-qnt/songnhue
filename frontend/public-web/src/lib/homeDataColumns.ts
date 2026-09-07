/**
 * Tên cột của hai bảng số liệu trên trang chủ — lấy từ `function-spec.md`, không tự đặt.
 *
 * <h2>Vì sao hàng tiêu đề cột được dựng khi CHƯA có một dòng dữ liệu nào</h2>
 *
 * §7 của văn bản nghiệm thu 27/08 nói thẳng: *"Nếu tại thời điểm bàn giao chưa có API, khối vẫn
 * phải dựng đầy đủ và để trạng thái chờ dữ liệu, sẵn sàng đấu nối khi có nguồn."* Hàng tiêu đề
 * là **lược đồ của khối**, không phải dữ liệu của khối — nó trả lời câu hỏi *"khi có số thì tôi
 * sẽ đọc được những gì"*, thứ mà một ô rỗng trơn không trả lời được.
 *
 * <p>⛔ Ranh giới, và nó hẹp: được dựng **tên cột**, cấm dựng **dòng**. Bản trước của khối mực
 * nước có 5 trạm quan trắc viết cứng kèm mực nước và một mức cảnh báo BĐ I trên tên cống CÓ
 * THẬT; chúng lên staging và không ai nhìn ra đường dữ liệu đã chết (§10.54). Một cái tên cột
 * không thể bị đọc nhầm thành một phép đo; một dòng "Cống Liên Mạc · +2,15 m" thì có.
 *
 * <h2>Vì sao ở `lib/` chứ không nằm trong chính component</h2>
 *
 * Để đếm được. Bộ test khẳng định **số lượng** cột (8 và 6) đúng theo đặc tả — một khẳng định
 * về số lượng không chia sẻ giả định nào với mã hiển thị, nên nó bắt được cả trường hợp ai đó
 * xoá một cột cho "gọn bảng" (luật 29).
 */

/**
 * Biểu tổng hợp theo tuyến sông — **CN-03.4**, 8 cột.
 *
 * ⚠ "Lượng mưa (mm)" giữ trong danh sách dù v1 chắc chắn hiển thị `-`: đặc tả ghi rõ *"Cột
 * lượng mưa hiển thị `-` ở v1 (chưa có nguồn — G3)"*. Bỏ cột đi là giấu mất một khoảng trống
 * mà Công ty cần nhìn thấy để biết còn thiếu nguồn nào.
 */
export const COT_MUC_NUOC = [
  'Tuyến sông',
  'Công trình / điểm đo',
  'Lý trình',
  'Mực nước thượng lưu (m)',
  'Mực nước hạ lưu (m)',
  'Lượng mưa (mm)',
  'Thời điểm đo',
  'Chất lượng',
] as const;

/**
 * Tình hình vận hành từng cống — **CN-02.11**, 6 cột.
 *
 * ⚠ Không có cột "Người cập nhật" và "Ghi chú": hai trường ấy có trong bảng
 * `construction_operation_status` nhưng thuộc phạm vi nội bộ (lọc tầng 3 theo Xí nghiệp). Đưa
 * chúng ra cổng công khai là một quyết định về phạm vi công bố, không phải một cột thêm vào.
 */
export const COT_VAN_HANH = [
  'Công trình',
  'Xí nghiệp quản lý',
  'Mã tình hình vận hành',
  'Giá trị tham số',
  'Thời điểm hiệu lực',
  'Cập nhật lần cuối',
] as const;
