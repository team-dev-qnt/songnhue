-- =============================================================================
-- Hai tài liệu công bố của mỗi công trình — CR-28
--
-- Nguồn: "YÊU CẦU CHỈNH SỬA WEBSITE" v1.0 ngày 27/08/2026 §5.1. Bảng danh mục công
-- trình 7 cột, trong đó hai cột là LIÊN KẾT tới tệp PDF:
--   • "Quy trình vận hành"  → Quyết định phê duyệt Quy trình vận hành công trình
--   • "Phương án bảo vệ"    → Quyết định phê duyệt Phương án bảo vệ công trình
--
-- ⚠ VÌ SAO KHÔNG DÙNG THẲNG `attachments` VỚI owner_type = 'CONSTRUCTION'
--
-- Cơ chế đính kèm đã có (`ConstructionDocumentService`) treo tệp vào công trình,
-- nhưng nó là một DANH SÁCH không phân loại: không có trường nào nói tệp thứ ba
-- trong danh sách là Quy trình vận hành hay là ảnh chụp hiện trường. Cột bảng ở
-- CR-28 cần trỏ đích danh MỘT tệp, nên chỗ giữ "tệp nào đóng vai gì" phải tồn tại.
--
-- Hai cột trỏ thẳng rẻ hơn một cột `doc_kind` trên `attachments`: vai trò ở đây là
-- 1-1 (một công trình có đúng một quy trình vận hành hiện hành), và ràng buộc ấy
-- được CSDL giữ hộ thay vì phải kiểm ở tầng service.
--
-- ⚠ Kiểu UUID trỏ `attachments.public_id`, không phải khoá chạy số — cùng khuôn
--   với `categories.cover_attachment_public_id`: module `operations` không được
--   import entity của core (quy tắc 6), nên nó cầm UUID.
--
-- ⛔ KHÔNG seed. Các tệp Quyết định thật thuộc G8 / OI-05, Công ty chưa gửi.
--   Ô rỗng ⇒ cổng hiện dấu "—", không dựng một liên kết trỏ vào hư không.
-- =============================================================================

ALTER TABLE constructions
    ADD COLUMN operating_procedure_attachment_public_id UUID
        REFERENCES attachments (public_id) ON DELETE SET NULL,
    ADD COLUMN protection_plan_attachment_public_id UUID
        REFERENCES attachments (public_id) ON DELETE SET NULL;

COMMENT ON COLUMN constructions.operating_procedure_attachment_public_id IS
    'Cột "Quy trình vận hành" của bảng danh mục công trình công khai (CR-28) — Quyết định '
    'phê duyệt Quy trình vận hành, dạng PDF. NULL = chưa có, cổng hiện dấu gạch.';
COMMENT ON COLUMN constructions.protection_plan_attachment_public_id IS
    'Cột "Phương án bảo vệ" của bảng danh mục công trình công khai (CR-28) — Quyết định '
    'phê duyệt Phương án bảo vệ, dạng PDF. NULL = chưa có, cổng hiện dấu gạch.';

-- Cột "Vị trí" của CR-28 KHÔNG có cột mới: liên kết Google Map dựng từ
-- `latitude`/`longitude` đã có sẵn. Thêm một cột `map_url` là mời hai nguồn toạ độ
-- cùng tồn tại rồi lệch nhau — và cột dẫn xuất trộn hai nguồn đúng là hình dạng
-- lỗi đã trả giá ở `ConstructionStatusService` (luật 13).
