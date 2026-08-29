-- =============================================================================
-- Số bài chở trên DẢI CHỮ CHẠY dưới thanh điều hướng — đợt bố cục 29/08/2026.
--
-- ⭐ VÌ SAO KHOÁ NÀY RA ĐỜI SAU KHI DẢI CHỮ CHẠY ĐÃ CHẠY ĐƯỢC MỘT NGÀY
--
-- Bản vẽ ghi thẳng điều còn thiếu: *"Số bài cần khoá MỚI `site.home.marquee-count` (chưa có —
-- phải dựng kèm ô sửa ở màn hình quản trị, luật 27)"*. Dải chữ chạy lên cổng ngày 29/08 với
-- `const SO_TIN_CHAY = 10` viết trong `SiteHeader.tsx` — một tham số nghiệp vụ nằm trong mã,
-- đúng thứ quy tắc 12 cấm. Muốn dải ngắn lại vì Công ty thấy một vòng quá dài thì phải sửa mã
-- và dựng lại image; và không ai ngoài người viết mã biết con số ấy tồn tại.
--
-- Cùng họ với `site.home.news-count`, `site.home.documents-count`, `site.slider.max-items`:
-- ba khoá ấy đã đi đúng đường này từ trước, khoá thứ tư chỉ là chỗ bị bỏ sót.
--
-- ⚠ RÀNG BUỘC 1..20, KHÔNG PHẢI MỘT SỐ BẤT KỲ
--
-- Dải chữ chạy vẽ danh sách HAI LẦN để nối vòng liền mạch (xem `PortalTicker`), nên mỗi bài
-- thêm vào là hai nút DOM nữa nằm trên đường tới hạn của trang chủ — thứ DOD1.17 (< 3s) đang
-- đếm từng KB. 20 là trần đo được còn chịu nổi.
--
-- ⛔ 0 KHÔNG phải công tắc tắt dải, và phần mô tả dưới đây nói đúng như thế. Bản nháp đầu tiên
--    của tệp này ghi *"Đặt 0 để tắt hẳn dải chữ chạy"* — đo trên stack đang chạy thì sai: với 0
--    bài, `PortalInfoStrip` vẫn còn hai mục "Giờ làm việc" và "Thư điện tử" lấy từ `company.*`,
--    nên dải vẫn chạy. Một dòng mô tả hứa điều mã không làm là đúng thứ bẫy dự án này đã trả giá
--    nhiều lần — nó nằm trên màn hình cấu hình, người vận hành đọc nó như một cam kết, và không
--    có bài kiểm nào đối chiếu chữ với hành vi.
--
-- ⛔ KHÔNG seed tham số cho tính năng chưa dựng (quy tắc 15). Khoá này có nơi đọc NGAY trong
--    lượt này — `frontend/public-web/src/components/SiteHeader.tsx`. `PortalSettingsReadTest`
--    quét cả thư mục migration này rồi đối chiếu với mã cổng, nên một khoá không ai đọc là một
--    bài kiểm đỏ chứ không phải một việc để dành.
-- =============================================================================

INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order)
SELECT v.k, v.val, v.vtype, v.val, v.grp, v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    ('site.home.marquee-count', '10', 'INTEGER',
     'SITE', 'Số bài trên dải chữ chạy dưới thanh điều hướng',
     'Số tiêu đề bài mới nhất chạy ngang dưới thanh điều hướng. Đặt 0 thì dải chỉ còn giờ làm việc và thư điện tử — muốn bỏ hẳn hai mục ấy thì xoá giá trị của company.working-hours và company.email.',
     '^(0|[1-9]|1[0-9]|20)$', 99)
) AS v(k, val, vtype, grp, label, descr, validation, ord)
ON CONFLICT (setting_key) DO NOTHING;

-- ⚠ Khẳng định ĐẾM ĐƯỢC, không phải một lời hứa trong chú thích. `ON CONFLICT DO NOTHING` ở
--   trên nuốt mọi va chạm trong im lặng — không có khối này thì một khoá trùng tên đã tồn tại
--   với giá trị khác sẽ đi lọt, và migration vẫn xanh (đúng hình dạng §10.66: 0 hàng, 0 log).
DO $$
DECLARE
    so_khoa INT;
BEGIN
    SELECT count(*) INTO so_khoa
      FROM settings
     WHERE setting_key = 'site.home.marquee-count';

    IF so_khoa <> 1 THEN
        RAISE EXCEPTION 'V202608291045: cần đúng 1 khoá site.home.marquee-count, đếm được %', so_khoa;
    END IF;
END $$;
