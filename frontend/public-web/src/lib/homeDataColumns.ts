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
 * Để đếm được. Bộ test khẳng định **số lượng** cột (9 và 6) đúng theo đặc tả — một khẳng định
 * về số lượng không chia sẻ giả định nào với mã hiển thị, nên nó bắt được cả trường hợp ai đó
 * xoá một cột cho "gọn bảng" (luật 29).
 */

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

/**
 * Cột của bảng "Mực nước, lượng mưa" trên **trang chủ** — spec §5.2, CN-03.4. **T44.8**.
 *
 * ⛔ **Một dòng là một CÔNG TRÌNH**, nên thượng lưu và hạ lưu là hai cột cạnh nhau. Bảng trước
 * WS-44 để một dòng một ĐIỂM ĐO, nên một trong hai cột ấy luôn rỗng ở mọi dòng.
 *
 * ⚠ Cột **"MN sông / Bể hút"** ⛔ không phải cột thừa: 5/14 công trình (trạm thuỷ văn Hà Nội ·
 * Ba Thá · An Cảnh · TB Hồng Vân · TB Yên Nghĩa) ⛔ **không có** cặp thượng/hạ lưu — chỉ tiêu của
 * chúng là `MN_SONG` hoặc `BE_HUT`. Bỏ cột này thì năm dòng ấy hiện **trống trơn**, đúng thứ
 * bảng mới sinh ra để chữa. Bản đầu của WS-44 đã quên nó và `homeDataColumns.test.ts` bắt được.
 *
 * ⚠ **"Chất lượng" ⛔ không còn là một cột riêng** — nhãn nghi ngờ nay đi kèm CHÍNH Ô mang số
 * (nền vàng + dấu ⚠ + chữ ẩn cho trình đọc màn hình), vì một ô nghi ngờ ở giữa mười ô tốt thì
 * một cột "Chất lượng" ở cuối dòng ⛔ không nói được ô nào.
 */
export const COT_TRANG_CHU_MUC_NUOC = [
  'Tuyến sông',
  'Công trình',
  'Lý trình',
  'Thượng lưu',
  'Hạ lưu',
  'Chênh lệch',
  'MN sông / Bể hút',
  'Lượng mưa',
  'Thời điểm đo',
] as const;
