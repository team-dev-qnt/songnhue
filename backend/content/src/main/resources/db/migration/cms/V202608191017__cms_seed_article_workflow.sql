-- =============================================================================
-- Quy trình duyệt bài viết (WS-13 / T13.3) — CN-01.1
--
-- ⭐ TÁCH VAI TRÒ NẰM Ở DỮ LIỆU, KHÔNG Ở `if` TRONG SERVICE.
--
-- SRS §3.1.3: "Biên tập viên KHÔNG được tự xuất bản bài của chính mình". Cách
-- thi hành ở đây: bước SUBMIT đòi `cms:article:submit`, bước APPROVE đòi
-- `cms:article:approve`. Vai trò Biên tập viên (V202608131007) chỉ có mã thứ
-- nhất, nên tự bài của mình lên cổng là chuyện không có đường nào đi tới —
-- không phải nhờ ai đó nhớ viết câu `if` cho đúng.
--
-- Kiểm chứng ngược nằm ở `ArticleWorkflowTest`: đăng nhập bằng vai trò Biên tập
-- viên rồi gọi APPROVE, phải nhận AUTH-3001.
--
-- ⛔ Không có dòng `__NEW__` nào: bài viết luôn bắt đầu ở NHAP. Nhiều đường vào
--    đời là chuyện của bản ghi sửa chữa (T12.5), không phải của bài viết.
-- =============================================================================

INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('ARTICLE', 'ARTICLE', 'Bài viết', 'NHAP',
        'Soạn → Chờ duyệt → Xuất bản. Gỡ bài và tái xuất bản không cần duyệt lại.');

-- -----------------------------------------------------------------------------
-- Các bước chuyển
--
-- Cột `notify_permission` = ai CẦN BIẾT, khác hẳn `required_permission` = ai
-- ĐƯỢC BẤM. Gửi duyệt: người bấm là biên tập viên, người cần biết là quản trị
-- nội dung. Duyệt/trả về: người bấm là quản trị nội dung, người cần biết là tác
-- giả — cột `notify_owner`.
-- -----------------------------------------------------------------------------
INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, v.notify_event, v.notify_permission, v.notify_owner, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         -- Gửi duyệt — từ Nháp hoặc từ bài bị trả về
         ('NHAP', 'SUBMIT', 'CHO_DUYET',
          'cms:article:submit', 'ARTICLE_SUBMITTED', 'cms:article:approve', FALSE,
          'Gửi duyệt', 10),
         ('YEU_CAU_CHINH_SUA', 'SUBMIT', 'CHO_DUYET',
          'cms:article:submit', 'ARTICLE_SUBMITTED', 'cms:article:approve', FALSE,
          'Gửi duyệt lại', 11),

         -- ⭐ Đường vào của copy-on-write (điểm nghiệp vụ 1): sửa một bài ĐANG xuất bản
         --    thì bản sửa đi duyệt như bản mới, còn `published_version_id` giữ nguyên nên
         --    cổng vẫn phục vụ nội dung cũ. Không có bước này thì hoặc là bài biến mất khỏi
         --    cổng lúc bấm Lưu, hoặc là bản chưa duyệt lên thẳng cổng.
         ('XUAT_BAN', 'SUBMIT', 'CHO_DUYET',
          'cms:article:submit', 'ARTICLE_SUBMITTED', 'cms:article:approve', FALSE,
          'Gửi duyệt bản sửa', 12),

         -- Duyệt và trả về — hai kết quả của cùng một quyền, cùng báo cho tác giả
         ('CHO_DUYET', 'APPROVE', 'XUAT_BAN',
          'cms:article:approve', 'ARTICLE_APPROVED', NULL, TRUE,
          'Duyệt và xuất bản', 20),
         ('CHO_DUYET', 'REQUEST_CHANGES', 'YEU_CAU_CHINH_SUA',
          'cms:article:approve', 'ARTICLE_CHANGES_REQUESTED', NULL, TRUE,
          'Yêu cầu chỉnh sửa', 21),

         -- Gỡ bài / tái xuất bản. Spec: tái xuất bản từ Gỡ bài KHÔNG cần duyệt lại,
         -- vì nội dung không đổi — chỉ có việc bật/tắt hiển thị.
         ('XUAT_BAN', 'UNPUBLISH', 'GO_BAI',
          'cms:article:unpublish', 'ARTICLE_UNPUBLISHED', NULL, TRUE,
          'Gỡ bài', 30),
         ('GO_BAI', 'REPUBLISH', 'XUAT_BAN',
          'cms:article:publish', 'ARTICLE_REPUBLISHED', NULL, TRUE,
          'Đăng lại', 31),

         -- Lưu trữ: ẩn khỏi danh sách, vẫn vào được bằng đường dẫn trực tiếp
         ('XUAT_BAN', 'ARCHIVE', 'LUU_TRU',
          'cms:article:publish', NULL, NULL, FALSE, 'Lưu trữ', 40),
         ('GO_BAI', 'ARCHIVE', 'LUU_TRU',
          'cms:article:publish', NULL, NULL, FALSE, 'Lưu trữ', 41),
         ('LUU_TRU', 'REPUBLISH', 'XUAT_BAN',
          'cms:article:publish', 'ARTICLE_REPUBLISHED', NULL, TRUE,
          'Đưa trở lại', 42)
     ) AS v(from_state, action, to_state,
            required_permission, notify_event, notify_permission, notify_owner, label, sort_order)
WHERE d.entity_type = 'ARTICLE';

-- -----------------------------------------------------------------------------
-- Tham số nghiệp vụ của module
--
-- Quy tắc 12: tham số để trong `settings` có giao diện sửa, không nằm trong
-- application.yml và không hard-code.
-- -----------------------------------------------------------------------------
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, v.exportable, v.ord
FROM (VALUES
    ('cms.article.scheduled-publish-cron-minutes', '5', 'INTEGER',
     'CMS', 'Chu kỳ quét bài hẹn giờ đăng (phút)',
     'CN-01.1. Job chỉ bắn revalidate ISR đúng lúc bài tới hạn — bài đã duyệt vẫn tự hiện khi published_at <= now()',
     'min=1;max=60', TRUE, 10),
    ('cms.article.view-count-flush-seconds', '60', 'INTEGER',
     'CMS', 'Chu kỳ đẩy lượt xem xuống CSDL (giây)',
     'CN-01.1. Lượt xem cộng dồn trong bộ nhớ rồi ghi theo lô — là số XẤP XỈ, không kiểm toán được',
     'min=10;max=600', TRUE, 11),
    ('cms.article.view-count-window-minutes', '30', 'INTEGER',
     'CMS', 'Khoảng chống đếm trùng một người xem (phút)',
     'Cùng một trình duyệt xem lại bài trong khoảng này không cộng thêm lượt',
     'min=0;max=1440', TRUE, 12)
) AS v(k, val, vtype, grp, label, descr, validation, exportable, ord)
WHERE NOT EXISTS (SELECT 1 FROM settings s WHERE s.setting_key = v.k);
