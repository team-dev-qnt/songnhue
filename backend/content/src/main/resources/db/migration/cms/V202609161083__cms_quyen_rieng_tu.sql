-- =============================================================================
-- T61.39 — Thông báo quyền riêng tư + dấu đồng ý trên biểu mẫu cổng (NĐ 13/2023)
--
-- ⛔⛔ HAI KHOÁ NÀY CỐ Ý ĐỂ TRỐNG.
--   Nội dung thông báo là văn bản PHÁP LÝ của Công ty (Điều 13 NĐ 13/2023 đòi nêu rõ: dữ liệu nào,
--   mục đích gì, ai xử lý, lưu bao lâu, quyền của chủ thể). Tự chế một đoạn "cho có" là công bố một
--   cam kết pháp lý ⛔ ai duyệt — nặng hơn hẳn việc để trống. Quy tắc 16 của dự án: ô chưa có nguồn
--   thì nói thẳng là chưa có, và màn hình "Tình trạng cấu hình" hiện nó ở mức CẢNH BÁO.
--
-- ⚠ Hệ quả hành vi, cố ý: CHƯA có thông báo ⇒ biểu mẫu ⛔ hỏi ô đồng ý (một ô tick vào một thông báo
--   RỖNG là đồng ý với KHÔNG GÌ CẢ — thứ đó tệ hơn ⛔ có ô, vì nó tạo ra bằng chứng giả). ĐÃ có thông
--   báo ⇒ ô đồng ý BẮT BUỘC và thời điểm đồng ý được lưu lại.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, NULL, v.vtype, NULL, 'SITE', v.label, v.descr, NULL, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.privacy.notice', 'TEXT',
     'Thông báo quyền riêng tư trên biểu mẫu cổng (NĐ 13/2023)',
     'Văn bản do Công ty cung cấp, hiện ngay trên biểu mẫu Liên hệ và Góp ý kèm ô đồng ý. '
     'ĐỂ TRỐNG = chưa có thông báo ⇒ biểu mẫu ⛔ hiện ô đồng ý.',
     90),
    ('site.privacy.policy-url', 'STRING',
     'Đường dẫn trang Chính sách quyền riêng tư',
     'Tuỳ chọn. Thường là một bài viết trên cổng (/bai-viet/<slug>). Hiện thành liên kết "Xem chi tiết" '
     'cạnh thông báo.',
     91)
) AS v(k, vtype, label, descr, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);

-- Dấu ĐỒNG Ý: NĐ 13/2023 Điều 11 đòi chứng minh được chủ thể đã đồng ý, nên nó là một MỐC THỜI GIAN
-- trên chính bản ghi, ⛔ phải một cờ boolean (một cờ ⛔ trả lời được "đồng ý lúc nào, theo bản thông
-- báo nào"). NULL = gửi trước khi có thông báo, hoặc thông báo chưa cấu hình.
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS consent_at timestamptz;
ALTER TABLE feedbacks ADD COLUMN IF NOT EXISTS consent_at timestamptz;

COMMENT ON COLUMN contacts.consent_at IS
    'T61.39 — thời điểm người gửi tick ô đồng ý với thông báo quyền riêng tư. NULL: chưa có thông báo lúc gửi.';
COMMENT ON COLUMN feedbacks.consent_at IS
    'T61.39 — thời điểm người gửi tick ô đồng ý với thông báo quyền riêng tư. NULL: chưa có thông báo lúc gửi.';
