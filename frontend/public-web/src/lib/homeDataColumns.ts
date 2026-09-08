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
 * Biểu tổng hợp theo tuyến sông — **CN-03.4**, 9 cột (8 tới 08/09/2026 — xem chú thích cột 6).
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
  // ⭐ Cột thứ 9, thêm 08/09/2026 — DOD2.3. `position_role` có NĂM giá trị hợp lệ và ba trong số
  //   đó (`MN_SONG`, `BE_HUT`, `MUA`) ⛔ không thuộc cặp thượng/hạ lưu của một cống. Backend trước
  //   đó chia nhị phân, nên mực nước của 4 trạm thuỷ văn sông lên cổng dưới tiêu đề "Mực nước hạ
  //   lưu (m)" — chính TÊN trạm đã tự mâu thuẫn với tiêu đề: "Trạm thuỷ văn Hà Nội — Mực nước sông".
  'Mực nước sông (m)',
  'Lượng mưa (mm)',
  'Thời điểm đo',
  'Chất lượng',
] as const;

/**
 * Lưới và bề rộng tối thiểu của bảng "Mực nước, lượng mưa" — **một chỗ khai duy nhất**.
 *
 * ⛔ Trước 08/09/2026 hai chuỗi này được ghi lặp ở **bốn** chỗ (`WaterLevelBlock` × 2 và trang
 * `/quan-ly-van-hanh/muc-nuoc-luong-mua` × 2), ngay cạnh một chú thích của `WaterLevelRows` tự
 * cảnh báo: *"phải TRÙNG với lớp truyền cho `ColumnHeaderRow`, nếu không cột lệch"*. Đó đúng là
 * luật 14 — chỗ nào con người phải nhớ ở nhiều nơi thì chỗ ấy cần một thứ nhớ hộ — và giá của nó
 * hiện ra ngay lượt thêm cột thứ 9: bốn chuỗi phải sửa, sót một là **hàng tiêu đề lệch khỏi hàng
 * dữ liệu** mà `tsc` ⛔ không thấy gì.
 *
 * ⚠ Số cột trong `grid-cols-[…]` phải bằng `COT_MUC_NUOC.length` — `homeDataColumns.test.ts` đếm
 * cả hai và so, nên lần sau thêm cột mà quên lưới thì bộ canh đỏ chứ ⛔ không phải người dùng.
 */
export const LUOI_MUC_NUOC = 'grid-cols-[1.1fr_1.7fr_0.9fr_1fr_1fr_1fr_0.95fr_1.1fr_0.9fr]';

/** ⚠ Tăng cùng lượt thêm cột: 920 → 1020px. Hẹp hơn thì bảng bóp chữ thay vì cuộn ngang. */
export const BE_RONG_TOI_THIEU_MUC_NUOC = 'min-w-[1020px]';

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
