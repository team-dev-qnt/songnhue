-- =============================================================================
-- WS-35 / T35.8 — Danh sách điểm đo công bố lên CỔNG CÔNG KHAI
--
-- ⚠ Số hiệu 1064, ⛔ KHÔNG phải giờ-phút
-- -----------------------------------------------------------------------------
-- `nnnn` là dãy chạy toàn kho, ⛔ không phải dấu thời gian (§10.66 — hai tệp
-- V202608241255/1256 từng đánh số bằng giờ-phút và rơi xuống DƯỚI bản staging đã
-- áp, làm hai lượt CD đỏ). Đỉnh `dev` sau khi PR #78 gộp là 1063, nên tệp này là
-- 1064. `MigrationNamingTest` canh dãy ấy.
--
-- ⭐ Vì sao khoá này ra đời CÙNG commit với đoạn mã đọc nó
-- -----------------------------------------------------------------------------
-- Luật 15, và dự án đã trả giá đúng chỗ này: tám khoá HYDRO seed ngày 13/08 nằm
-- 18 ngày không ai đọc — người vận hành thấy ô nhập, sửa nó, và không có gì đổi.
-- Vế đọc của khoá dưới đây là `HydroSettings.maDiemDoLenCong()`, và nó có người
-- gọi ngay: `PublicHydroService.mucNuoc()`.
--
-- ⛔⛔ RỖNG nghĩa là "công bố TẤT CẢ điểm đo đang hoạt động", ⛔ không phải "không
--     công bố gì"
-- -----------------------------------------------------------------------------
-- Đây là chỗ dễ đọc ngược nhất của cả khoá, nên nó được nói ra ở CẢ BA nơi: mô tả
-- dưới đây (người vận hành đọc), javadoc hàm đọc (lập trình viên đọc), và một bài
-- kiểm giữ cả hai nhánh.
--
-- Lý do là nghiệp vụ, ⛔ không phải kỹ thuật: mục OI-03 (10 cống trục chính nào
-- lên cổng) Công ty CHƯA chốt. Hai cách hiểu "rỗng":
--
--   * rỗng = không công bố gì  ⇒ hôm nay cổng mất 19 dòng SỐ THẬT đang chạy, để
--     đổi lấy một khối trạng thái chờ. Tức là ta tự quyết định thay Công ty rằng
--     "chưa chốt" nghĩa là "giấu đi";
--   * rỗng = công bố tất cả    ⇒ giữ nguyên hiện trạng đã được duyệt 04/09, và
--     ngày OI-03 về thì điền danh sách vào ô này — ⛔ không cần deploy (quy tắc 12).
--
-- Chọn vế thứ hai. ⚠ Và nó ⛔ KHÔNG phải một bộ dữ liệu dự phòng (quy tắc 16): 19
-- dòng ấy đều là điểm đo THẬT với số đo THẬT; ô nào chưa có nguồn vẫn rỗng kèm lý
-- do. Ở đây "rỗng" chỉ chọn PHẠM VI công bố, ⛔ không bịa ra nội dung.
--
-- ⚠ Thứ tự trong danh sách LÀ thứ tự hiển thị
-- -----------------------------------------------------------------------------
-- Cổng đọc đúng thứ tự người vận hành gõ. Công ty sẽ muốn cống trục chính đứng
-- trước, và một danh sách "chọn được nhưng không xếp được" thì lần đầu dùng đã
-- phải mở lại mã.
--
-- ⚠ Mã ⛔ KHÔNG khớp điểm đo nào thì phải KÊU, ⛔ không lặng lẽ ngắn đi
-- -----------------------------------------------------------------------------
-- Gõ nhầm một mã trong danh sách 10 mã cho ra 9 dòng — và 9 dòng số thật trông y
-- hệt một bảng đúng. `HydroSettings.maDiemDoLenCong()` ghi WARN nêu đích danh mã
-- không khớp; xem javadoc của nó.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'hydro.portal.station-codes', '', 'STRING', '',
    'HYDRO', 'Điểm đo công bố lên Cổng thông tin điện tử',
    'Danh sách MÃ điểm đo, ngăn bằng dấu phẩy — ví dụ: TB01,TB02,TB03. Thứ tự gõ ở đây LÀ thứ tự '
    'các dòng hiện trên bảng "Mực nước, lượng mưa" của cổng. '
    'ĐỂ TRỐNG nghĩa là công bố TẤT CẢ điểm đo đang hoạt động (⛔ không phải "không công bố gì"). '
    'Mã không khớp điểm đo nào sẽ bị bỏ qua và ghi cảnh báo vào nhật ký hệ thống — nếu bảng trên '
    'cổng thiếu dòng, hãy kiểm tra lại chính tả các mã ở đây trước tiên.',
    NULL, TRUE, TRUE, 67
)
ON CONFLICT (setting_key) DO NOTHING;
