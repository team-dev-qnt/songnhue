-- =============================================================================
-- Gỡ ba tham số CMS không dòng mã nào đọc — "công tắc chết"
--
-- Rà soát toàn bộ bảng `settings` đối chiếu với mã nguồn (Java lẫn TypeScript,
-- tính cả khoá ghép từ tiền tố) tìm ra ba khoá nhóm CMS bày ra trên màn hình
-- cấu hình mà không có nơi đọc:
--
--   cms.article.view-count-flush-seconds       (60)
--   cms.article.scheduled-publish-cron-minutes (5)
--   cms.article.view-count-window-minutes      (30)
--
-- Hai khoá đầu KHÔNG PHẢI thiếu sót — `ViewCountService` và
-- `ScheduledPublishScanner` cố ý ghi cứng chu kỳ, và tài liệu của chúng nói rõ
-- vì sao: `@Scheduled` chốt chu kỳ lúc dựng bean, nên một tham số sửa được trên
-- giao diện mà không có tác dụng cho tới lần khởi động lại chính là kiểu công
-- tắc chết đã trả giá ở WS-12. Quyết định ở tầng mã là đúng; thứ thiếu là bước
-- gỡ dòng dữ liệu tương ứng. Để lại thì quản trị viên đổi "chu kỳ đẩy = 5 giây",
-- giao diện báo thành công, và không gì thay đổi.
--
-- Khoá thứ ba nặng hơn: nó mô tả một tính năng KHÔNG TỒN TẠI. `ViewCountService`
-- cộng thẳng mỗi lượt `POST …/views`, không có bất kỳ phép khử trùng lặp theo
-- người xem nào. Nhãn "Khoảng chống đếm trùng một người xem (phút)" hứa một thứ
-- hệ thống không làm. Ghi thành nợ để quyết định sau (cần một cách nhận diện
-- người xem — cookie hoặc băm IP — nên là quyết định tính năng, không phải việc
-- dọn dẹp); trong lúc đó `view_count` vẫn là số xấp xỉ như migration gốc đã ghi.
-- =============================================================================

DELETE FROM settings
WHERE setting_key IN (
    'cms.article.view-count-flush-seconds',
    'cms.article.scheduled-publish-cron-minutes',
    'cms.article.view-count-window-minutes'
);
