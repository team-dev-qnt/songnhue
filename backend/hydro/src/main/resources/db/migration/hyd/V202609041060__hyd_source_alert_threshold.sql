-- =============================================================================
-- WS-30 / T30.6 — Ngưỡng cảnh báo "nguồn dữ liệu đang hỏng"
--
-- ⭐ Vì sao khoá này ra đời CÙNG commit với đoạn mã đọc nó
-- -----------------------------------------------------------------------------
-- Tám khoá nhóm HYDRO seed ngày 13/08 nằm 18 ngày KHÔNG AI ĐỌC (luật 15, §10.9):
-- người vận hành thấy tám ô nhập trên màn hình Cấu hình hệ thống, sửa chúng, và
-- không có gì đổi. Khoá dưới đây có người đọc ngay trong cùng đợt —
-- `HydroSettings.soLanHongTruocKhiCanhBao()` → `ApiSourceHealthService`.
--
-- ⚠ Vì sao KHÔNG dùng lại `hydro.polling.max-retry`
-- -----------------------------------------------------------------------------
-- Hai con số nghe giống nhau, đo hai thứ khác nhau:
--   * `max-retry`      = thử lại BAO NHIÊU LẦN trong một lượt việc (hàng đợi lo)
--   * khoá này         = BAO NHIÊU LƯỢT GỌI hỏng liên tiếp thì đánh thức con người
-- Dùng chung một ô cho hai việc là "một công tắc cho hai bóng đèn": người vận
-- hành hạ số lần thử lại để nguồn đỡ tải, và vô tình làm cảnh báo réo sớm hơn.
--
-- ⚠ Vì sao KHÔNG có trạng thái "OFFLINE"
-- -----------------------------------------------------------------------------
-- `ApiSourceStatus` cố ý chỉ có HOAT_DONG / TAM_DUNG, và javadoc của nó nói rõ lý
-- do: một cột trạng thái DO MÁY GHI thì đứng yên đúng lúc nó quan trọng nhất —
-- poller chết ⇒ không ai ghi ⇒ nguồn vẫn hiện "bình thường" trong khi không có số
-- nào về. Sức khoẻ nguồn vì thế đọc từ `last_success_at` / `consecutive_failures`,
-- là những cột mang SỰ KIỆN ĐÃ XẢY RA. T30.6 viết "đánh dấu nguồn OFFLINE" từ
-- trước quyết định ấy; phần còn đúng của nó là "cảnh báo Admin", và đó là thứ
-- migration này cấu hình.
-- =============================================================================
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
VALUES (
    'hydro.source.alert-after-failures', '3', 'INTEGER', '3',
    'HYDRO', 'Số lượt gọi hỏng liên tiếp thì cảnh báo Quản trị',
    'Đếm theo cột consecutive_failures của từng nguồn. Cảnh báo phát ĐÚNG MỘT LẦN lúc vượt ngưỡng '
    'và một lần nữa lúc nguồn trở lại — không réo mỗi 2 phút: một chuông kêu liên tục vì một lý do '
    'ai cũng biết là một chuông sẽ bị tắt, rồi vẫn tắt vào ngày nguồn hỏng thật.',
    'min=1;max=100', TRUE, TRUE, 66
)
ON CONFLICT (setting_key) DO NOTHING;
