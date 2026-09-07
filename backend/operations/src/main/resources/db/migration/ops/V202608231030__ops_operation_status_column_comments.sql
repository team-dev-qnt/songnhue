-- =============================================================================
-- Đợt vá #9 — Chú thích cột mô tả SAI hành vi thật của mã
-- =============================================================================
--
-- V202608221029 ghi ở cột `mapped_status`:
--     "Để trống = giữ nguyên trạng thái hiện tại."
--
-- Mã không làm thế, và không thể làm thế. `operational_status` là giá trị DẪN
-- XUẤT: ConstructionStatusService.tinh() tính lại từ đầu ở mỗi lượt, nên không
-- có "trạng thái hiện tại" nào để mà giữ. Mã có mapped_status rỗng chỉ đơn giản
-- là không đóng góp gì ở mắt xích 4, và phép tính rơi xuống mắt xích 5 —
-- BINH_THUONG.
--
-- Khoảng cách giữa hai cách hiểu không nhỏ: người quản trị đọc chú thích cũ sẽ
-- để trống mapped_status với ý "mã này không ảnh hưởng trạng thái", rồi ngạc
-- nhiên khi công trình đang SU_CO bị hạ xuống BINH_THUONG. (Thực tế mắt xích 1
-- đứng trên mắt xích 4 nên tình huống đó không xảy ra — nhưng người đọc chú
-- thích không biết điều đó, và họ đang cấu hình dựa trên một lời hứa sai.)
--
-- ⚠ Không sửa tại chỗ ở V202608221029: tệp đó đã chạy trên máy dev và staging,
--    mà `validateOnMigrate=true` so khớp checksum. Sửa một ký tự trong tệp đã
--    áp dụng là làm mọi lượt khởi động sau đó fail. Chú thích SQL cũng không tra
--    lại được từ CSDL — COMMENT ON thì tra được bằng \d+.

COMMENT ON COLUMN operation_status_codes.mapped_status IS
    'Trạng thái dẫn xuất mà mã này ánh xạ sang (mắt xích 4 của CN-02.1). '
    'Để TRỐNG = mã không tham gia suy ra trạng thái, phép tính rơi xuống mặc định BINH_THUONG. '
    'KHÔNG có nghĩa "giữ nguyên trạng thái hiện tại" — trạng thái được tính lại từ đầu mỗi lượt.';

COMMENT ON COLUMN operation_status_codes.active IS
    'Mã còn dùng được hay đã ẩn. Ẩn rồi thì không ghi nhận dòng mới được nữa (OPS-2018), '
    'nhưng các dòng cũ vẫn giữ nguyên và vẫn quyết định trạng thái công trình.';

COMMENT ON COLUMN construction_operation_status.org_unit_id IS
    'Đơn vị tại thời điểm ghi nhận, chép từ công trình. Giữ nguyên khi công trình được bàn giao — '
    'nên phép tính trạng thái phải đọc bảng này bằng câu NATIVE (không lọc phạm vi), '
    'còn màn hình lịch sử thì đọc bằng câu JPQL (có lọc).';
