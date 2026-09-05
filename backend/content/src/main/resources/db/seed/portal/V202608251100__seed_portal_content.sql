-- ⚠ TỆP SINH TỰ ĐỘNG — sửa `deploy/seed/generate.py` rồi sinh lại, đừng sửa tay.
-- Idempotent: chạy lại nhiều lần cũng ra cùng một trạng thái.

-- =============================================================================
-- NỘI DUNG KHỞI TẠO CHO CỔNG — 4 ảnh + 5 bài. CHỈ chạy ở STAGING.
--
-- ⛔ TỆP NÀY KHÔNG NẰM TRONG `spring.flyway.locations` MẶC ĐỊNH.
--
--    Nó ở `classpath:db/seed/portal`, và chỉ được giải khi biến `SEED_LOCATION`
--    trỏ vào đó. Mặc định là `classpath:db/seed/none` — một thư mục cố ý không có
--    migration nào. Production không đặt biến ấy, nên Flyway ở production KHÔNG
--    BAO GIỜ nhìn thấy tệp này: không phải "chạy rồi không làm gì", mà là không
--    tồn tại.
--
--    Vì sao phải chặn cứng đến thế: khối [1] dưới đây XOÁ BÀI. Chuyện bản quyền
--    của 5 bài chép lại đã được cân nhắc và bỏ qua; chuyện một migration xoá nội
--    dung thật của Công ty thì không.
--
-- ⚠ Byte của 4 ảnh KHÔNG nằm ở đây — SQL không đẩy được byte. Chúng lên MinIO qua
--   service `minio-init` (biến `SEED_MEDIA_DIR`), chạy TRƯỚC `migrator` ở mỗi lượt
--   triển khai. Hai vế phải bật CÙNG NHAU: hàng trong CSDL mà không có byte trong
--   MinIO là hỏng câm — CSDL vẫn nói tệp tồn tại, còn `GET` trả 404. `SeedGateTest`
--   canh đúng chỗ đó, vì đây là thứ con người phải nhớ ở hai nơi (luật 14).
--
-- ⚠ Seed ghi thẳng `status = 'XUAT_BAN'`, tức KHÔNG đi qua Workflow engine (luật
--   4). Hệ quả: 5 bài này không có vết audit xuất bản nào. Chấp nhận được vì đây
--   là dữ liệu để ĐO trên staging, không phải nội dung nghiệp vụ — nhưng đừng lấy
--   tệp này làm mẫu cho bất kỳ đường ghi nào khác.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- [1] Dọn bài cũ
--
-- ⚠⚠ KHÔNG phải `DELETE FROM articles`. `menu_items.article_id` tham chiếu
--    `articles(id)` mà KHÔNG khai `ON DELETE` — tức RESTRICT — nên xoá sạch là
--    migration dừng giữa chừng vì lỗi khoá ngoại, và dừng SAU khi đã xoá được một
--    phần.
--
--    Vị từ dưới đây canh theo QUAN HỆ, không theo danh sách slug: *xoá mọi bài
--    không có mục menu nào trỏ tới*. Nó tự bảo vệ 4 trang tĩnh do
--    `V202608191021__cms_seed_site_structure` sở hữu (gioi-thieu-chung ·
--    chuc-nang-nhiem-vu · co-cau-to-chuc · lien-he), và vẫn đúng khi sau này có
--    thêm trang tĩnh thứ năm — một danh sách slug viết cứng thì lần thêm ấy sẽ
--    làm gãy menu, im lặng.
--
-- Xoá CỨNG, không `deleted_at`: đây là dựng lại trạng thái đầu của một môi trường
-- đo đạc, không phải hành vi xoá bài của người dùng. Bài ẩn mà còn nằm trong bảng
-- vẫn hiện ở màn hình quản trị và làm sai mọi phép đếm.
--
-- `article_versions`, `article_categories`, `article_tags` đều `ON DELETE CASCADE`
-- nên không cần dọn tay; `articles.published_version_id` là `ON DELETE SET NULL`.
-- -----------------------------------------------------------------------------
DELETE FROM articles a
 WHERE NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.article_id = a.id);

-- =============================================================================
-- [2] ĐÍNH KÈM — 4 ảnh của 5 bài seed
--
-- ⚠ Hàng ở đây phải khớp TỪNG BYTE với đối tượng `minio-init` đẩy lên MinIO: cùng bucket,
--   cùng `storage_key`. Lệch một chỗ thì `GET /api/v1/public/files/<id>` trả 404 trong khi
--   CSDL vẫn nói tệp tồn tại — hỏng câm, đúng loại khó truy nhất.
--
-- ⚠ `scan_status = 'SKIPPED'`, KHÔNG phải `'CLEAN'`. ClamAV chưa từng quét mấy tệp này;
--   ghi `CLEAN` là nói dối sổ sách về một cơ chế bảo mật (CLAUDE.md luật 16).
--
-- ⚠ `owner_type = 'MEDIA_FOLDER'` là bắt buộc, không phải tuỳ chọn: `PublicPortalService`
--   chỉ phục vụ ba loại chủ sở hữu công khai (MEDIA_FOLDER · BANNER · SITE_CONFIG). Loại
--   khác trả 404 y hệt tệp không tồn tại.
-- =============================================================================

INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('15509c57-8e04-57e6-8d36-6a9cd1c68334', 'MEDIA_FOLDER', 'SEED_PORTAL', '17ab5afa-cd46-438a-b828-453bb00ac266.jpeg', 'songnhue-media',
        'seed/portal/15509c57-8e04-57e6-8d36-6a9cd1c68334.jpeg', 'image/jpeg', 113720, '05f88ed5980fe1915fec0d16c702b4a86e3a32870db944d84e1d034558deab2d',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('144a2a14-487d-5972-953d-d4008ba1f555', 'MEDIA_FOLDER', 'SEED_PORTAL', '43a6383e-f199-412b-a7f9-cd2260e96a74.jpeg', 'songnhue-media',
        'seed/portal/144a2a14-487d-5972-953d-d4008ba1f555.jpeg', 'image/jpeg', 84676, 'b81c39e2ec84288f8a66d650836b155642a9a536c5d1bdf244ccb0250d3f7767',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('97b3154f-42c3-5386-95b5-adb11922337e', 'MEDIA_FOLDER', 'SEED_PORTAL', '78639110-9bdf-445d-b48a-a777b6717a4c.jpeg', 'songnhue-media',
        'seed/portal/97b3154f-42c3-5386-95b5-adb11922337e.jpeg', 'image/jpeg', 107118, '017bf1e8cc84207eea3e2ca353405465d487535b6f7d3d1cbd34bea911cfa789',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('9d3d1319-1397-5215-b187-c925c35623cc', 'MEDIA_FOLDER', 'SEED_PORTAL', '89ba87e1-f0ba-49f9-9711-915ddd3956c5.jpeg', 'songnhue-media',
        'seed/portal/9d3d1319-1397-5215-b187-c925c35623cc.jpeg', 'image/jpeg', 39774, '0a3b8fa85c7c919490ceb0fbe994c2f5081461a41b8a5f82060c4d3da7c186da',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;

-- =============================================================================
-- [3] 5 BÀI SAO CHÉP NGUYÊN VĂN TỪ BÁO NGOÀI
--
-- Cột `source` của từng bài ghi rõ URL gốc (hanoimoi.vn, vneconomy.vn). Đây là toàn văn
-- bài báo của người khác, kèm ảnh của họ.
--
-- Chỉ chạy ở staging — môi trường đóng, `X-Robots-Tag: noindex, nofollow`. Lý do cần bài
-- DÀI THẬT, ẢNH THẬT: DOD1.17 (trang chủ < 3s) chỉ đo được trên nội dung thật. Cổng chặn
-- production nằm ở đầu tệp.
--
-- ⚠ KHÔNG seed `categories`: cây danh mục do migration `V202608191021__cms_seed_site_structure`
--   sở hữu và đã có sẵn trên mọi môi trường. Seed lại là dựng một nguồn sự thật thứ hai cho
--   cùng một dữ liệu.
-- =============================================================================


-- ---- Xã Phú Xuyên tăng cường ứng trực, chủ động phòng ngừa ngập úng
--      nguồn: https://hanoimoi.vn/xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-1238587.html
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ('Xã Phú Xuyên tăng cường ứng trực, chủ động phòng ngừa ngập úng', 'xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-2517', 'Trước diễn biến phức tạp, khó lường của bão số 4 và nguy cơ xảy ra mưa lớn trên diện rộng, các trạm thuỷ lợi ở xã Phú Xuyên đã tăng cường ứng trực, chủ động tiêu thoát nước, phòng ngừa ngập úng cục bộ.', '<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="3436.jpg">
 <figcaption>Trạm bơm Nam Liên ở xã Phú Xuyên đang khai thác, vận hành 7/10 tổ máy. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Những ngày này, Trạm bơm Nam Liên ở xã Phú Xuyên đang tập trung khai thác, vận hành 7/10 tổ máy để kịp thời tiêu thoát nước cho đồng ruộng và các khu dân cư sau những đợt mưa lớn kéo dài. Ông Tạ Quang Hương - Trạm trưởng cho biết, địa bàn tiêu úng chủ yếu ở thôn Phong Triều, Nam Quất và vùng phụ cận. Đây là những thôn có diện tích đất sản xuất nông nghiệp, nuôi trồng thuỷ sản lớn và thường bị ngập cục bộ khi có mưa kéo dài.</p>
<p class="align-justify">“Mỗi máy có công suất tiêu úng là 2.500m<sup>3</sup>/h, dự kiến trong đợt này trạm sẽ tiêu úng khoảng hơn 1 triệu m<sup>3</sup> nước. Chúng tôi tiếp tục cập nhật diễn biến thời tiết, lượng mưa, duy trì ứng trực 24/24h nhằm vận hành hệ thống thuỷ lợi thông suốt, an toàn, kịp thời tiêu úng, phòng ngừa xảy ra ngập cục bộ cho đến khi thời tiết ổn định”, ông Tạ Quang Hương cho biết.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="75687876-8.jpg">
 <figcaption>
  Dự kiến trong đợt này Trạm bơm Nam Liên sẽ tiêu úng khoảng hơn 1 triệu m<sup> 3</sup> nước. Ảnh: PV
 </figcaption>
</figure>
<p class="align-justify">Hiện nay, do ảnh hưởng của bão số 4 tại xã Phú Xuyên đang xảy ra những đợt mưa lớn kéo dài khiến cho một số vùng đồng ruộng trũng thấp, kênh mương thủy lợi, ao hồ… nước có xu hướng dâng cao. Trước tình hình đó, xã đã đẩy mạnh tuyên truyền, cảnh báo, chỉ đạo các Hợp tác xã Nông nghiệp, các đơn vị khai thác, vận hành công trình thủy lợi tập trung theo dõi, cập nhật diễn biến thời tiết, lượng mưa để có kế hoạch tiêu úng, thoát nước kịp thời, góp phần ổn định sản xuất, đời sống của người dân.</p>
<figure>
 <img src="/api/v1/public/files/97b3154f-42c3-5386-95b5-adb11922337e" alt="74586989.jpg">
 <figcaption>Tại các ao hồ nuôi trồng thủy sản ở xã Phú Xuyên nước có xu hướng dâng cao sau những đợt mưa lớn kéo dài. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Qua tìm hiểu được biết, vụ mùa năm nay xã Phú Xuyên gieo cấy 1.779,2ha lúa và một số diện tích rau màu các loại. Ngoài ra, toàn xã có trên 654ha ao hồ, mặt nước nuôi trồng thủy sản, trong đó nuôi thâm canh là 546,21ha còn lại là nuôi bán thâm canh. Do đặc thù địa hình trũng thấp, mưa lớn kéo nên một số vùng sản xuất nông nghiệp, nuôi trồng thủy sản, vùng ven khu dân cư có hiện tượng nước dâng cao, tiềm ẩn nguy cơ xảy ra ngập úng cục bộ.</p>
<p class="align-justify">Trao đổi với phóng viên, ông Nguyễn Phú Huấn - Chuyên viên Phòng Kinh tế xã Phú Xuyên cho biết, trước mùa mưa bão, địa phương đã đẩy mạnh tuyên truyền thực hiện phương châm “4 tại chỗ” nhằm chủ động ứng phó kịp thời, hiệu quả các tình huống thiên tai. Đồng thời thành lập đoàn kiểm tra toàn bộ hệ thống công trình đê điều, thuỷ lợi và các khu vực xung yếu có nguy cơ xảy ra ngập úng để có phương án phòng ngừa, đảm bảo an toàn cho sản xuất và đời sống người dân.</p>
<p class="align-justify">“Hiện nay 13/13 trạm bơm ở xã Phú Xuyên đang tập trung vận hành tiêu thoát nước. Các trạm bơm sẽ duy trì ứng trực, căn cứ vào lượng mưa, lượng nước đổ về để tiêu thoát phù hợp trên toàn hệ thống công trình thuỷ lợi. Bên cạnh đó, các đơn vị cũng chủ động khơi thông dòng chảy, vớt rác, bèo tây và vật cản tại cửa thu, cửa xả, hạn chế tình trạng ách tắc, làm giảm khả năng tiêu thoát nước”, ông Nguyễn Phú Huấn thông tin.</p>', 'https://hanoimoi.vn/xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-1238587.html',
        'XUAT_BAN', '2026-08-24T08:58:32.517+00:00', 'Xã Phú Xuyên tăng cường ứng trực, chủ động phòng ngừa ngập úng', 'Trước diễn biến phức tạp, khó lường của bão số 4 và nguy cơ xảy ra mưa lớn trên diện rộng, các trạm thuỷ lợi ở xã Phú Xuyên đã tăng cường ứng trực, chủ động tiê',
        'seeding, article', (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, 'Xã Phú Xuyên tăng cường ứng trực, chủ động phòng ngừa ngập úng', 'xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-2517', 'Trước diễn biến phức tạp, khó lường của bão số 4 và nguy cơ xảy ra mưa lớn trên diện rộng, các trạm thuỷ lợi ở xã Phú Xuyên đã tăng cường ứng trực, chủ động tiêu thoát nước, phòng ngừa ngập úng cục bộ.', '<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="3436.jpg">
 <figcaption>Trạm bơm Nam Liên ở xã Phú Xuyên đang khai thác, vận hành 7/10 tổ máy. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Những ngày này, Trạm bơm Nam Liên ở xã Phú Xuyên đang tập trung khai thác, vận hành 7/10 tổ máy để kịp thời tiêu thoát nước cho đồng ruộng và các khu dân cư sau những đợt mưa lớn kéo dài. Ông Tạ Quang Hương - Trạm trưởng cho biết, địa bàn tiêu úng chủ yếu ở thôn Phong Triều, Nam Quất và vùng phụ cận. Đây là những thôn có diện tích đất sản xuất nông nghiệp, nuôi trồng thuỷ sản lớn và thường bị ngập cục bộ khi có mưa kéo dài.</p>
<p class="align-justify">“Mỗi máy có công suất tiêu úng là 2.500m<sup>3</sup>/h, dự kiến trong đợt này trạm sẽ tiêu úng khoảng hơn 1 triệu m<sup>3</sup> nước. Chúng tôi tiếp tục cập nhật diễn biến thời tiết, lượng mưa, duy trì ứng trực 24/24h nhằm vận hành hệ thống thuỷ lợi thông suốt, an toàn, kịp thời tiêu úng, phòng ngừa xảy ra ngập cục bộ cho đến khi thời tiết ổn định”, ông Tạ Quang Hương cho biết.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="75687876-8.jpg">
 <figcaption>
  Dự kiến trong đợt này Trạm bơm Nam Liên sẽ tiêu úng khoảng hơn 1 triệu m<sup> 3</sup> nước. Ảnh: PV
 </figcaption>
</figure>
<p class="align-justify">Hiện nay, do ảnh hưởng của bão số 4 tại xã Phú Xuyên đang xảy ra những đợt mưa lớn kéo dài khiến cho một số vùng đồng ruộng trũng thấp, kênh mương thủy lợi, ao hồ… nước có xu hướng dâng cao. Trước tình hình đó, xã đã đẩy mạnh tuyên truyền, cảnh báo, chỉ đạo các Hợp tác xã Nông nghiệp, các đơn vị khai thác, vận hành công trình thủy lợi tập trung theo dõi, cập nhật diễn biến thời tiết, lượng mưa để có kế hoạch tiêu úng, thoát nước kịp thời, góp phần ổn định sản xuất, đời sống của người dân.</p>
<figure>
 <img src="/api/v1/public/files/97b3154f-42c3-5386-95b5-adb11922337e" alt="74586989.jpg">
 <figcaption>Tại các ao hồ nuôi trồng thủy sản ở xã Phú Xuyên nước có xu hướng dâng cao sau những đợt mưa lớn kéo dài. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Qua tìm hiểu được biết, vụ mùa năm nay xã Phú Xuyên gieo cấy 1.779,2ha lúa và một số diện tích rau màu các loại. Ngoài ra, toàn xã có trên 654ha ao hồ, mặt nước nuôi trồng thủy sản, trong đó nuôi thâm canh là 546,21ha còn lại là nuôi bán thâm canh. Do đặc thù địa hình trũng thấp, mưa lớn kéo nên một số vùng sản xuất nông nghiệp, nuôi trồng thủy sản, vùng ven khu dân cư có hiện tượng nước dâng cao, tiềm ẩn nguy cơ xảy ra ngập úng cục bộ.</p>
<p class="align-justify">Trao đổi với phóng viên, ông Nguyễn Phú Huấn - Chuyên viên Phòng Kinh tế xã Phú Xuyên cho biết, trước mùa mưa bão, địa phương đã đẩy mạnh tuyên truyền thực hiện phương châm “4 tại chỗ” nhằm chủ động ứng phó kịp thời, hiệu quả các tình huống thiên tai. Đồng thời thành lập đoàn kiểm tra toàn bộ hệ thống công trình đê điều, thuỷ lợi và các khu vực xung yếu có nguy cơ xảy ra ngập úng để có phương án phòng ngừa, đảm bảo an toàn cho sản xuất và đời sống người dân.</p>
<p class="align-justify">“Hiện nay 13/13 trạm bơm ở xã Phú Xuyên đang tập trung vận hành tiêu thoát nước. Các trạm bơm sẽ duy trì ứng trực, căn cứ vào lượng mưa, lượng nước đổ về để tiêu thoát phù hợp trên toàn hệ thống công trình thuỷ lợi. Bên cạnh đó, các đơn vị cũng chủ động khơi thông dòng chảy, vớt rác, bèo tây và vật cản tại cửa thu, cửa xả, hạn chế tình trạng ách tắc, làm giảm khả năng tiêu thoát nước”, ông Nguyễn Phú Huấn thông tin.</p>',
       '15509c57-8e04-57e6-8d36-6a9cd1c68334', 'Xã Phú Xuyên tăng cường ứng trực, chủ động phòng ngừa ngập úng', 'Trước diễn biến phức tạp, khó lường của bão số 4 và nguy cơ xảy ra mưa lớn trên diện rộng, các trạm thuỷ lợi ở xã Phú Xuyên đã tăng cường ứng trực, chủ động tiê', 'seeding, article',
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = 'xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-2517'
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = 'xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-2517'
  AND a.published_version_id IS NULL;
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = 'xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-2517' AND c.slug = 'tin-tuc'
ON CONFLICT DO NOTHING;

-- ---- Bão số 4 tiếp tục hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới
--      nguồn: https://hanoimoi.vn/bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-1266648.html
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ('Bão số 4 tiếp tục hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới', 'bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-2827', 'Bão số 4 được xem là một trong những cơn bão phức tạp nhất trong nhiều năm qua, khi di chuyển rất chậm và liên tục đổi hướng. Dự kiến bão sẽ còn hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới.', '<figure>
 <img src="/api/v1/public/files/9d3d1319-1397-5215-b187-c925c35623cc" alt="dbqg_xtnd_20260823_2300.jpeg">
 <figcaption class="align-center">
  Hướng di chuyển của bão số 4. <i>Ảnh: NCHMF.</i>
 </figcaption>
</figure>
<p>Bản tin cập nhật của Trung tâm Dự báo khí tượng thủy văn quốc gia cho biết, vị trí tâm bão số 4 hiện trên vùng biển phía Đông Bắc đặc khu Bạch Long Vĩ, cách Quảng Ninh khoảng 160km về phía Đông Đông Nam, cách Hải Phòng khoảng 165km về phía Đông. Sức gió mạnh nhất vùng gần tâm bão mạnh cấp 9, giật cấp 11.</p>
<p>Trong những giờ qua, bão số 4 di chuyển chậm theo hướng Đông Nam, tốc độ khoảng 3km/h. Dự kiến đến 22h tối nay (24-8), bão trên khu vực phía Đông Vịnh Bắc Bộ, cách đặc khu Bạch Long Vĩ khoảng 110km về phía Đông.</p>
<p>Cho đến ngày 26-8, bão số 4 tiếp tục di chuyển chậm và liên tục đổi hướng. Cường độ bão duy trì ở cấp 9, giật cấp 11. Khu vực chịu ảnh hưởng trực tiếp của bão vẫn là Vịnh Bắc Bộ; đất liền ven biển và vùng biển ven bờ tỉnh Quảng Ninh, thành phố Hải Phòng.</p>
<p>Cơ quan khí tượng thủy văn nhận định, đến 22h ngày 26-8, bão số 4 suy yếu thành áp thấp nhiệt đới trên trên đất liền phía Nam tỉnh Quảng Tây (Trung Quốc). Cường độ áp thấp nhiệt đới mạnh cấp 6-7, giật cấp 9. Đồng thời, tiếp tục ảnh hưởng đến vùng biển Vịnh Bắc Bộ.</p>
<p>Do ảnh hưởng của bão số 4, trong 2-3 ngày tới, trên Vịnh Bắc Bộ (bao gồm các đặc khu: Bạch Long Vĩ, Vân Đồn, Cô Tô, Cát Hải; đảo Hòn Dấu), vùng biển ven bờ tỉnh Quảng Ninh và thành phố Hải Phòng nguy cơ có<b> </b>gió mạnh cấp 6-7, vùng gần tâm bão đi qua mạnh cấp 8-9, giật cấp 10-11; sóng biển cao 3,0-5,0m; biển động rất mạnh.</p>
<p>Điều kiện thời tiết nguy hiểm có thể gây lật, chìm hoặc hư hỏng tàu cá, tàu vận tải, tàu du lịch và các phương tiện hoạt động trên biển; lồng bè nuôi trồng thủy sản, khu neo đậu tàu thuyền và công trình ven biển có nguy cơ bị hư hỏng. Đồng thời, khiến hoạt động khai thác thủy sản, vận tải và du lịch trên biển có khả năng bị gián đoạn.</p>', 'https://hanoimoi.vn/bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-1266648.html',
        'XUAT_BAN', '2026-08-24T08:58:32.827+00:00', 'Bão số 4 tiếp tục hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới', 'Bão số 4 được xem là một trong những cơn bão phức tạp nhất trong nhiều năm qua, khi di chuyển rất chậm và liên tục đổi hướng. Dự kiến bão sẽ còn hoành hành trên',
        'seeding, article', (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, 'Bão số 4 tiếp tục hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới', 'bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-2827', 'Bão số 4 được xem là một trong những cơn bão phức tạp nhất trong nhiều năm qua, khi di chuyển rất chậm và liên tục đổi hướng. Dự kiến bão sẽ còn hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới.', '<figure>
 <img src="/api/v1/public/files/9d3d1319-1397-5215-b187-c925c35623cc" alt="dbqg_xtnd_20260823_2300.jpeg">
 <figcaption class="align-center">
  Hướng di chuyển của bão số 4. <i>Ảnh: NCHMF.</i>
 </figcaption>
</figure>
<p>Bản tin cập nhật của Trung tâm Dự báo khí tượng thủy văn quốc gia cho biết, vị trí tâm bão số 4 hiện trên vùng biển phía Đông Bắc đặc khu Bạch Long Vĩ, cách Quảng Ninh khoảng 160km về phía Đông Đông Nam, cách Hải Phòng khoảng 165km về phía Đông. Sức gió mạnh nhất vùng gần tâm bão mạnh cấp 9, giật cấp 11.</p>
<p>Trong những giờ qua, bão số 4 di chuyển chậm theo hướng Đông Nam, tốc độ khoảng 3km/h. Dự kiến đến 22h tối nay (24-8), bão trên khu vực phía Đông Vịnh Bắc Bộ, cách đặc khu Bạch Long Vĩ khoảng 110km về phía Đông.</p>
<p>Cho đến ngày 26-8, bão số 4 tiếp tục di chuyển chậm và liên tục đổi hướng. Cường độ bão duy trì ở cấp 9, giật cấp 11. Khu vực chịu ảnh hưởng trực tiếp của bão vẫn là Vịnh Bắc Bộ; đất liền ven biển và vùng biển ven bờ tỉnh Quảng Ninh, thành phố Hải Phòng.</p>
<p>Cơ quan khí tượng thủy văn nhận định, đến 22h ngày 26-8, bão số 4 suy yếu thành áp thấp nhiệt đới trên trên đất liền phía Nam tỉnh Quảng Tây (Trung Quốc). Cường độ áp thấp nhiệt đới mạnh cấp 6-7, giật cấp 9. Đồng thời, tiếp tục ảnh hưởng đến vùng biển Vịnh Bắc Bộ.</p>
<p>Do ảnh hưởng của bão số 4, trong 2-3 ngày tới, trên Vịnh Bắc Bộ (bao gồm các đặc khu: Bạch Long Vĩ, Vân Đồn, Cô Tô, Cát Hải; đảo Hòn Dấu), vùng biển ven bờ tỉnh Quảng Ninh và thành phố Hải Phòng nguy cơ có<b> </b>gió mạnh cấp 6-7, vùng gần tâm bão đi qua mạnh cấp 8-9, giật cấp 10-11; sóng biển cao 3,0-5,0m; biển động rất mạnh.</p>
<p>Điều kiện thời tiết nguy hiểm có thể gây lật, chìm hoặc hư hỏng tàu cá, tàu vận tải, tàu du lịch và các phương tiện hoạt động trên biển; lồng bè nuôi trồng thủy sản, khu neo đậu tàu thuyền và công trình ven biển có nguy cơ bị hư hỏng. Đồng thời, khiến hoạt động khai thác thủy sản, vận tải và du lịch trên biển có khả năng bị gián đoạn.</p>',
       '9d3d1319-1397-5215-b187-c925c35623cc', 'Bão số 4 tiếp tục hoành hành trên Vịnh Bắc Bộ trong 2-3 ngày tới', 'Bão số 4 được xem là một trong những cơn bão phức tạp nhất trong nhiều năm qua, khi di chuyển rất chậm và liên tục đổi hướng. Dự kiến bão sẽ còn hoành hành trên', 'seeding, article',
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = 'bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-2827'
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = 'bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-2827'
  AND a.published_version_id IS NULL;
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = 'bao-so-4-tiep-tuc-hoanh-hanh-tren-vinh-bac-bo-trong-2-3-ngay-toi-2827' AND c.slug = 'tin-tuc'
ON CONFLICT DO NOTHING;

-- ---- Thường Tín xử lý điểm xung yếu tuyến đê sông Nhuệ
--      nguồn: https://hanoimoi.vn/thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-1247199.html
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ('Thường Tín xử lý điểm xung yếu tuyến đê sông Nhuệ', 'thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-2895', 'Ngày 23-8, Phó Chủ tịch UBND xã Thường Tín (Hà Nội) Nguyễn Văn Tản cùng các phòng ban chuyên môn đã trực tiếp kiểm tra tại hiện trường, chỉ đạo công tác khắc phục hậu quả mưa bão, xử lý các điểm xung yếu trên tuyến đê sông Nhuệ thuộc địa phận thôn Hà Liễu.', '<p class="align-justify">Thực hiện công điện của Chủ tịch UBND Thành phố Hà Nội về việc tiếp tục chủ động ứng phó thiên tai, khắc phục nhanh hậu quả mưa lũ trên địa bàn thành phố. Trước diễn biến phức tạp của thời tiết và ảnh hưởng của bão số 4, những ngày qua và ngày 23-8, lãnh đạo UBND xã Thường Tín đã trực tiếp đi kiểm tra tại hiện trường, chỉ đạo công tác phòng chống mưa bão và xử lý điểm xung yếu tại tuyến đê sông Nhuệ.</p>
<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="1787468764015_2088326008786858043_6313318397890927965_f82f62418c086d4fc9526acde3df9955.jpg">
 <figcaption class="align-center">Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản yêu cầu các lực lượng khẩn trương triển khai phương án xử lý điểm xung yếu đê sông Nhuệ tại thôn Hà Liễu. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Tại khu vực điểm xung yếu, xuất hiện vị trí sạt lở thân đê dài gần 20m, tiềm ẩn nguy cơ mất an toàn, đặc biệt trong trường hợp mực nước đến nay vẫn đang tiếp tục dâng cao, nguy cơ làm ảnh hưởng trực tiếp đến mái và thân đê sông Nhuệ địa phận thôn Hà Liễu.</p>
<p class="align-justify">Căn cứ vào tình hình thực tiễn và diễn biến mưa còn phức tạp trong những ngày tới, Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản yêu cầu các lực lượng khẩn trương triển khai phương án xử lý, huy động máy xúc, nhân lực và phương tiện tại chỗ để đắp bù đất, gia cố mái đê, xử lý vị trí sạt lở, bảo đảm an toàn công trình đê điều.</p>
<p class="align-justify">Việc khắc phục hậu quả mưa bão số 4 phải được triển khai khẩn trương, chủ động, không để bị động, bất ngờ, ưu tiên xử lý ngay những vị trí xung yếu, có nguy cơ phát sinh sự cố. Các lực lượng cần thường xuyên kiểm tra, tuần tra, theo dõi chặt chẽ diễn biến tuyến đê, kịp thời phát hiện và xử lý các điểm có nguy cơ mất an toàn.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="1787468764036_2088326008786858043_6313318397890927965_3d7d6bf04968c5b17bacde0f76440bab.jpg">
 <figcaption class="align-center">Mực nước sông Nhuệ đang tiếp tục dâng cao, nguy cơ gây ảnh hưởng đến điểm xung yếu tại thôn Hà Liễu. Ảnh: PV</figcaption>
</figure>
<p>“Các lực lượng thực hiện nghiêm phương châm “4 tại chỗ”, chuẩn bị đầy đủ vật tư, phương tiện, nhân lực, sẵn sàng ứng phó với các tình huống phát sinh; tuyệt đối không chủ quan, lơ là, góp phần bảo đảm an toàn hệ thống đê điều, hạn chế thấp nhất thiệt hại do mưa bão gây ra, bảo vệ tính mạng và tài sản của nhân dân”, Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản nhấn mạnh.</p>', 'https://hanoimoi.vn/thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-1247199.html',
        'XUAT_BAN', '2026-08-24T08:58:32.895+00:00', 'Thường Tín xử lý điểm xung yếu tuyến đê sông Nhuệ', 'Ngày 23-8, Phó Chủ tịch UBND xã Thường Tín (Hà Nội) Nguyễn Văn Tản cùng các phòng ban chuyên môn đã trực tiếp kiểm tra tại hiện trường, chỉ đạo công tác khắc ph',
        'seeding, article', (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, 'Thường Tín xử lý điểm xung yếu tuyến đê sông Nhuệ', 'thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-2895', 'Ngày 23-8, Phó Chủ tịch UBND xã Thường Tín (Hà Nội) Nguyễn Văn Tản cùng các phòng ban chuyên môn đã trực tiếp kiểm tra tại hiện trường, chỉ đạo công tác khắc phục hậu quả mưa bão, xử lý các điểm xung yếu trên tuyến đê sông Nhuệ thuộc địa phận thôn Hà Liễu.', '<p class="align-justify">Thực hiện công điện của Chủ tịch UBND Thành phố Hà Nội về việc tiếp tục chủ động ứng phó thiên tai, khắc phục nhanh hậu quả mưa lũ trên địa bàn thành phố. Trước diễn biến phức tạp của thời tiết và ảnh hưởng của bão số 4, những ngày qua và ngày 23-8, lãnh đạo UBND xã Thường Tín đã trực tiếp đi kiểm tra tại hiện trường, chỉ đạo công tác phòng chống mưa bão và xử lý điểm xung yếu tại tuyến đê sông Nhuệ.</p>
<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="1787468764015_2088326008786858043_6313318397890927965_f82f62418c086d4fc9526acde3df9955.jpg">
 <figcaption class="align-center">Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản yêu cầu các lực lượng khẩn trương triển khai phương án xử lý điểm xung yếu đê sông Nhuệ tại thôn Hà Liễu. Ảnh: PV</figcaption>
</figure>
<p class="align-justify">Tại khu vực điểm xung yếu, xuất hiện vị trí sạt lở thân đê dài gần 20m, tiềm ẩn nguy cơ mất an toàn, đặc biệt trong trường hợp mực nước đến nay vẫn đang tiếp tục dâng cao, nguy cơ làm ảnh hưởng trực tiếp đến mái và thân đê sông Nhuệ địa phận thôn Hà Liễu.</p>
<p class="align-justify">Căn cứ vào tình hình thực tiễn và diễn biến mưa còn phức tạp trong những ngày tới, Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản yêu cầu các lực lượng khẩn trương triển khai phương án xử lý, huy động máy xúc, nhân lực và phương tiện tại chỗ để đắp bù đất, gia cố mái đê, xử lý vị trí sạt lở, bảo đảm an toàn công trình đê điều.</p>
<p class="align-justify">Việc khắc phục hậu quả mưa bão số 4 phải được triển khai khẩn trương, chủ động, không để bị động, bất ngờ, ưu tiên xử lý ngay những vị trí xung yếu, có nguy cơ phát sinh sự cố. Các lực lượng cần thường xuyên kiểm tra, tuần tra, theo dõi chặt chẽ diễn biến tuyến đê, kịp thời phát hiện và xử lý các điểm có nguy cơ mất an toàn.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="1787468764036_2088326008786858043_6313318397890927965_3d7d6bf04968c5b17bacde0f76440bab.jpg">
 <figcaption class="align-center">Mực nước sông Nhuệ đang tiếp tục dâng cao, nguy cơ gây ảnh hưởng đến điểm xung yếu tại thôn Hà Liễu. Ảnh: PV</figcaption>
</figure>
<p>“Các lực lượng thực hiện nghiêm phương châm “4 tại chỗ”, chuẩn bị đầy đủ vật tư, phương tiện, nhân lực, sẵn sàng ứng phó với các tình huống phát sinh; tuyệt đối không chủ quan, lơ là, góp phần bảo đảm an toàn hệ thống đê điều, hạn chế thấp nhất thiệt hại do mưa bão gây ra, bảo vệ tính mạng và tài sản của nhân dân”, Phó Chủ tịch UBND xã Thường Tín Nguyễn Văn Tản nhấn mạnh.</p>',
       '15509c57-8e04-57e6-8d36-6a9cd1c68334', 'Thường Tín xử lý điểm xung yếu tuyến đê sông Nhuệ', 'Ngày 23-8, Phó Chủ tịch UBND xã Thường Tín (Hà Nội) Nguyễn Văn Tản cùng các phòng ban chuyên môn đã trực tiếp kiểm tra tại hiện trường, chỉ đạo công tác khắc ph', 'seeding, article',
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = 'thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-2895'
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = 'thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-2895'
  AND a.published_version_id IS NULL;
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = 'thuong-tin-xu-ly-diem-xung-yeu-tuyen-de-song-nhue-2895' AND c.slug = 'tin-tuc'
ON CONFLICT DO NOTHING;

-- ---- Hà Nội: Dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn
--      nguồn: https://vneconomy.vn/ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan.htm
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ('Hà Nội: Dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn', 'ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan-3024', 'Lộ trình thực hiện dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn từ cống Liên Mạc đến đường Vành đai 4 với chiều dài 30,5km. Giai đoạn 2 tiếp nối từ năm 2029 đến năm 2030 để hoàn thiện 31km còn lại, kéo dài từ đường Vành đai 4 đến hết địa phận Hà Nội...', '<figure class="article-figure js-article-figure image detail__image align-center">
 <img src="/api/v1/public/files/97b3154f-42c3-5386-95b5-adb11922337e" alt="Ô nhiễm tại các dòng sông vẫn là thách thức ở nhiều đô thị. Ảnh: Hoàng Bách">
 <figcaption class="article-figure__caption">Ô nhiễm tại các dòng sông vẫn là thách thức ở nhiều đô thị. Ảnh: Hoàng Bách</figcaption>
</figure>
<p class="text-justify">Thông tin từ Hà Nội cho biết tại Hội nghị lần thứ sáu Ban Chấp hành Đảng bộ Thành phố khóa XVIII, Phó Chủ tịch UBND TP. Hà Nội Bùi Duy Cường đã trình bày Tờ trình về chủ trương đầu tư dự án với mục tiêu tu bổ hệ thống đê điều, nạo vét và xử lý triệt để tình trạng ô nhiễm môi trường tại lưu vực sông sông Nhuệ.&nbsp;</p>
<p class="text-justify">Theo đó, sông Nhuệ có vai trò là trục xương sống quan trọng trong hệ thống thủy lợi và cảnh quan của Thủ đô với chiều dài khoảng 61,5km chảy qua địa bàn Hà Nội. Dòng sông bắt đầu từ cống Liên Mạc và đi qua tổng cộng 19 xã, phường. Không chỉ là một đường thủy thuần túy, sông Nhuệ còn là trục kết nối các tuyến giao thông huyết mạch của thành phố như đường Tây Thăng Long, Quốc lộ 32, Đại lộ Thăng Long, Quốc lộ 6 và đường Vành đai 4.</p>
<p class="text-justify">Tuy nhiên, hiện nay, sông Nhuệ đang trong tình trạng cạn kiệt nguồn nước và ô nhiễm môi trường cực kỳ nghiêm trọng. Dòng sông gần như đã mất đi khả năng cấp nước và không còn bảo đảm chức năng của một nguồn nước mặt thông thường. Các chỉ tiêu về chất lượng nước thường xuyên được ghi nhận ở mức rất xấu, vượt xa các quy chuẩn cho phép.&nbsp;Chính vì vậy, nguyện vọng của chính quyền các địa phương dọc dòng sông cũng như đông đảo người dân khu vực là sớm có một giải pháp tổng thể để xử lý ô nhiễm và kết nối giao thông, tạo không gian sống trong lành hơn.</p>
<p class="text-justify">Để giải quyết bài toán này, Tập đoàn Geleximco đã đề xuất Thành phố triển khai một dự án đa mục tiêu. Dự án không chỉ dừng lại ở việc làm sạch dòng sông mà còn hướng tới hoàn thiện hệ thống thu gom, xử lý nước thải tập trung để giảm thiểu ô nhiễm bền vững. Bên cạnh đó, dự án còn đảm bảo khả năng chủ động tiêu thoát nước, giúp chống ngập úng cho khu vực nội đô và phía Tây thành phố Hà Nội. Việc cải tạo sông Nhuệ cũng sẽ nâng cao hiệu quả của các công trình thủy lợi trong khu vực, đồng thời góp phần chỉnh trang đô thị và giảm thiểu ùn tắc giao thông.</p>
<p class="text-justify">Về khía cạnh tài chính và phương thức thực hiện, dự án có sơ bộ tổng mức đầu tư lên tới 75.115 tỷ đồng. Thành phố dự kiến triển khai theo phương thức đối tác công tư (PPP), cụ thể là loại hình hợp đồng Xây dựng - Chuyển giao (BT). Tổng diện tích đất dự kiến sử dụng cho dự án vào khoảng 855ha. Thời gian thực hiện dự án được hoạch định kéo dài trong 5 năm, từ năm 2026 đến năm 2030.</p>
<p class="text-justify">Để đảm bảo tính khả thi về tài chính, phương thức thanh toán cho nhà đầu tư được xác định sơ bộ từ các quỹ đất đối ứng với giá trị thanh toán khoảng 69.106 tỷ đồng. Các quỹ đất này bao gồm các khu đất dọc hai bên sông và dự kiến sẽ có thêm khoảng 5 khu đất khác tại những vị trí khác nhau để đảm bảo quyền lợi cho nhà đầu tư khi thực hiện hợp đồng BT.</p>
<p class="text-justify">Dự án cải tạo sông Nhuệ được cấu trúc chặt chẽ với 4 dự án thành phần nhằm giải quyết dứt điểm các khía cạnh khác nhau. Dự án thành phần 1 tập trung vào công tác giải phóng mặt bằng, tạo quỹ đất sạch để thi công. Dự án thành phần 2 là trung tâm của kỹ thuật với các hoạt động nạo vét, kè bờ và xây dựng tuyến sông dài toàn bộ 61,5km. Dự án thành phần 3 chú trọng vào việc phát triển hạ tầng giao thông và hệ thống hạ tầng kỹ thuật, bao gồm cả các cống thu gom nước thải chạy dọc hai bên bờ sông. Cuối cùng, dự án thành phần 4 sẽ tập trung đầu tư xây dựng các nhà máy xử lý nước thải theo đúng quy hoạch đã đề ra.</p>
<p class="text-justify">Về lộ trình thực hiện, dự án sẽ được chia làm hai giai đoạn cụ thể. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn từ cống Liên Mạc đến đường Vành đai 4 với chiều dài 30,5km. Giai đoạn 2 sẽ tiếp nối từ năm 2029 đến năm 2030 để hoàn thiện 31km còn lại, kéo dài từ đường Vành đai 4 đến hết địa phận Hà Nội.</p>', 'https://vneconomy.vn/ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan.htm',
        'XUAT_BAN', '2026-08-24T08:58:33.024+00:00', 'Hà Nội: Dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn', 'Lộ trình thực hiện dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn ',
        'seeding, article', (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, 'Hà Nội: Dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn', 'ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan-3024', 'Lộ trình thực hiện dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn từ cống Liên Mạc đến đường Vành đai 4 với chiều dài 30,5km. Giai đoạn 2 tiếp nối từ năm 2029 đến năm 2030 để hoàn thiện 31km còn lại, kéo dài từ đường Vành đai 4 đến hết địa phận Hà Nội...', '<figure class="article-figure js-article-figure image detail__image align-center">
 <img src="/api/v1/public/files/97b3154f-42c3-5386-95b5-adb11922337e" alt="Ô nhiễm tại các dòng sông vẫn là thách thức ở nhiều đô thị. Ảnh: Hoàng Bách">
 <figcaption class="article-figure__caption">Ô nhiễm tại các dòng sông vẫn là thách thức ở nhiều đô thị. Ảnh: Hoàng Bách</figcaption>
</figure>
<p class="text-justify">Thông tin từ Hà Nội cho biết tại Hội nghị lần thứ sáu Ban Chấp hành Đảng bộ Thành phố khóa XVIII, Phó Chủ tịch UBND TP. Hà Nội Bùi Duy Cường đã trình bày Tờ trình về chủ trương đầu tư dự án với mục tiêu tu bổ hệ thống đê điều, nạo vét và xử lý triệt để tình trạng ô nhiễm môi trường tại lưu vực sông sông Nhuệ.&nbsp;</p>
<p class="text-justify">Theo đó, sông Nhuệ có vai trò là trục xương sống quan trọng trong hệ thống thủy lợi và cảnh quan của Thủ đô với chiều dài khoảng 61,5km chảy qua địa bàn Hà Nội. Dòng sông bắt đầu từ cống Liên Mạc và đi qua tổng cộng 19 xã, phường. Không chỉ là một đường thủy thuần túy, sông Nhuệ còn là trục kết nối các tuyến giao thông huyết mạch của thành phố như đường Tây Thăng Long, Quốc lộ 32, Đại lộ Thăng Long, Quốc lộ 6 và đường Vành đai 4.</p>
<p class="text-justify">Tuy nhiên, hiện nay, sông Nhuệ đang trong tình trạng cạn kiệt nguồn nước và ô nhiễm môi trường cực kỳ nghiêm trọng. Dòng sông gần như đã mất đi khả năng cấp nước và không còn bảo đảm chức năng của một nguồn nước mặt thông thường. Các chỉ tiêu về chất lượng nước thường xuyên được ghi nhận ở mức rất xấu, vượt xa các quy chuẩn cho phép.&nbsp;Chính vì vậy, nguyện vọng của chính quyền các địa phương dọc dòng sông cũng như đông đảo người dân khu vực là sớm có một giải pháp tổng thể để xử lý ô nhiễm và kết nối giao thông, tạo không gian sống trong lành hơn.</p>
<p class="text-justify">Để giải quyết bài toán này, Tập đoàn Geleximco đã đề xuất Thành phố triển khai một dự án đa mục tiêu. Dự án không chỉ dừng lại ở việc làm sạch dòng sông mà còn hướng tới hoàn thiện hệ thống thu gom, xử lý nước thải tập trung để giảm thiểu ô nhiễm bền vững. Bên cạnh đó, dự án còn đảm bảo khả năng chủ động tiêu thoát nước, giúp chống ngập úng cho khu vực nội đô và phía Tây thành phố Hà Nội. Việc cải tạo sông Nhuệ cũng sẽ nâng cao hiệu quả của các công trình thủy lợi trong khu vực, đồng thời góp phần chỉnh trang đô thị và giảm thiểu ùn tắc giao thông.</p>
<p class="text-justify">Về khía cạnh tài chính và phương thức thực hiện, dự án có sơ bộ tổng mức đầu tư lên tới 75.115 tỷ đồng. Thành phố dự kiến triển khai theo phương thức đối tác công tư (PPP), cụ thể là loại hình hợp đồng Xây dựng - Chuyển giao (BT). Tổng diện tích đất dự kiến sử dụng cho dự án vào khoảng 855ha. Thời gian thực hiện dự án được hoạch định kéo dài trong 5 năm, từ năm 2026 đến năm 2030.</p>
<p class="text-justify">Để đảm bảo tính khả thi về tài chính, phương thức thanh toán cho nhà đầu tư được xác định sơ bộ từ các quỹ đất đối ứng với giá trị thanh toán khoảng 69.106 tỷ đồng. Các quỹ đất này bao gồm các khu đất dọc hai bên sông và dự kiến sẽ có thêm khoảng 5 khu đất khác tại những vị trí khác nhau để đảm bảo quyền lợi cho nhà đầu tư khi thực hiện hợp đồng BT.</p>
<p class="text-justify">Dự án cải tạo sông Nhuệ được cấu trúc chặt chẽ với 4 dự án thành phần nhằm giải quyết dứt điểm các khía cạnh khác nhau. Dự án thành phần 1 tập trung vào công tác giải phóng mặt bằng, tạo quỹ đất sạch để thi công. Dự án thành phần 2 là trung tâm của kỹ thuật với các hoạt động nạo vét, kè bờ và xây dựng tuyến sông dài toàn bộ 61,5km. Dự án thành phần 3 chú trọng vào việc phát triển hạ tầng giao thông và hệ thống hạ tầng kỹ thuật, bao gồm cả các cống thu gom nước thải chạy dọc hai bên bờ sông. Cuối cùng, dự án thành phần 4 sẽ tập trung đầu tư xây dựng các nhà máy xử lý nước thải theo đúng quy hoạch đã đề ra.</p>
<p class="text-justify">Về lộ trình thực hiện, dự án sẽ được chia làm hai giai đoạn cụ thể. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn từ cống Liên Mạc đến đường Vành đai 4 với chiều dài 30,5km. Giai đoạn 2 sẽ tiếp nối từ năm 2029 đến năm 2030 để hoàn thiện 31km còn lại, kéo dài từ đường Vành đai 4 đến hết địa phận Hà Nội.</p>',
       '97b3154f-42c3-5386-95b5-adb11922337e', 'Hà Nội: Dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn', 'Lộ trình thực hiện dự án cải tạo sông Nhuệ được đề xuất chia thành hai giai đoạn. Giai đoạn 1 dự kiến kéo dài từ khi khởi công đến hết năm 2028, thực hiện đoạn ', 'seeding, article',
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = 'ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan-3024'
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = 'ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan-3024'
  AND a.published_version_id IS NULL;
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = 'ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan-3024' AND c.slug = 'tin-tuc'
ON CONFLICT DO NOTHING;

-- ---- Hà Nội: Ngành thủy lợi ứng trực ngày đêm, tích cực tiêu úng chống ngập
--      nguồn: https://hanoimoi.vn/ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-1250844.html
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ('Hà Nội: Ngành thủy lợi ứng trực ngày đêm, tích cực tiêu úng chống ngập', 'ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-3081', 'Mưa kéo dài nhiều ngày qua khiến nhiều xã, phường trên địa bàn Hà Nội đối diện nguy cơ ngập lụt. Việc bảo vệ an toàn những diện tích sản xuất nông nghiệp, nhất là lúa vụ Mùa của bà con nông dân là nhiệm vụ hết sức quan trọng của ngành thủy lợi.', '<figure>
 <img src="/api/v1/public/files/9d3d1319-1397-5215-b187-c925c35623cc" alt="img_1317.jpg">
 <figcaption class="align-center">
  Trạm bơm Cao Xuân Dương vận hành tiêu thoát nước chống ngập úng trong chiều 23-8-2026. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Tại trạm bơm Cao Xuân Dương (xã Dân Hoà), 2 tổ máy được cán bộ, công nhân viên tại trạm tổ chức vận hành liên tục từ 7h sáng ngày 21-8. Đây cũng là thời điểm Hà Nội nhận định bước vào đợt mưa kéo dài.</p>
<p>Anh Nguyễn Huy Thanh, Trạm trưởng Trạm bơm Cao Xuân Dương cho biết, trạm được bố trí 6 tổ máy, với năng lực tiêu 4.100 m3/h, có nhiệm vụ tiêu úng chống ngập cho khoảng 200ha canh tác nông nghiệp.</p>
<p>Nhờ chủ động vận hành hệ thống thuỷ lợi từ sớm, hiện nay trên địa bàn xã Dân Hoà nói riêng, hầu hết các diện tích lúa vụ Mùa của bà con đều được bảo vệ an toàn; chưa ghi nhận những diện tích bị ngập úng.</p>
<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="img_1325.jpg">
 <figcaption class="align-center">
  Cán bộ, công nhân vận hành hệ thống trạm bơm Cao Xuân Dương. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Theo thống kê của Chi cục Thuỷ lợi và Phòng, chống thiên tai Hà Nội, tính đến 16h chiều 23-8, 4 doanh nghiệp thuỷ lợi trên địa bàn thành phố đang vận hành tổng cộng 172 trạm bơm. Tổng lưu lượng tiêu thoát đạt hơn 2 triệu m3/h.</p>
<p>Trong số này, Công ty TNHH MTV đầu tư phát triển thuỷ lợi sông Nhuệ đang vận hành 91 trạm; tiếp đến là các Công ty TNHH MTV đầu tư phát triển thuỷ lợi: Sông Đáy 40 trạm, sông Tích 30 trạm và Hà Nội 11 trạm.</p>
<p>Chủ tịch Công ty TNHH MTV đầu tư phát triển thủy lợi sông Đáy Trần Đình Cường cho biết, trong cao điểm mưa lũ, đơn vị chỉ đạo các xí nghiệp bố trí cán bộ ứng trực 24/24h, sẵn sàng vận hành từ sớm hệ thống các công trình, bảo đảm tiêu thoát nước chủ động.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="img_1364.jpg">
 <figcaption class="align-center">
  Hệ thống thủy lợi tiêu thoát nước hiệu quả bảo vệ những diện tích nông nghiệp. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Theo bản tin cập nhật của Trung tâm Dự báo khí tượng thuỷ văn quốc gia, do ảnh hưởng của bão số 4, dự kiến trên địa bàn Hà Nội sẽ còn có mưa vừa, mưa to đến ngày 24-8. Đến ngày 25-8, lượng mưa giảm dần nhưng nhiều xã, phường tiếp tục có mưa rào rải rác và dông.</p>
<p>Chi cục trưởng Chi cục Thủy lợi và Phòng, chống thiên tai Hà Nội Nguyễn Duy Du cho biết, đơn vị hiện đang tiếp tục theo dõi sát các bản tin dự báo, cảnh báo mưa lũ; phối hợp chặt chẽ với các doanh nghiệp thuỷ lợi, chủ động tổ chức vận hành hệ thống các trạm bơm để tiêu thoát nước nước sớm.</p>
<p>Để chủ động ứng phó với nguy cơ ngập lụt, đại diện Chi cục Thủy lợi và Phòng, chống thiên tai Hà Nội cũng đề nghị các xã, phường tiếp tục thực hiện nghiêm các nội dung chỉ đạo tại Công điện số 04/CĐ-UBND của UBND thành phố Hà Nội.</p>
<p>Các xã, phường, đặc biệt là các địa phương ven sông, cần tiếp tục theo dõi sát diễn biến mưa lũ để chủ động triển khai các biện pháp ứng phó với lũ lên trên các sông, bảo đảm an toàn các khu vực dân cư trũng thấp và diện tích canh tác nông nghiệp ven sông.</p>', 'https://hanoimoi.vn/ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-1250844.html',
        'XUAT_BAN', '2026-08-24T08:58:33.081+00:00', 'Hà Nội: Ngành thủy lợi ứng trực ngày đêm, tích cực tiêu úng chống ngập', 'Mưa kéo dài nhiều ngày qua khiến nhiều xã, phường trên địa bàn Hà Nội đối diện nguy cơ ngập lụt. Việc bảo vệ an toàn những diện tích sản xuất nông nghiệp, nhất ',
        'seeding, article', (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, 'Hà Nội: Ngành thủy lợi ứng trực ngày đêm, tích cực tiêu úng chống ngập', 'ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-3081', 'Mưa kéo dài nhiều ngày qua khiến nhiều xã, phường trên địa bàn Hà Nội đối diện nguy cơ ngập lụt. Việc bảo vệ an toàn những diện tích sản xuất nông nghiệp, nhất là lúa vụ Mùa của bà con nông dân là nhiệm vụ hết sức quan trọng của ngành thủy lợi.', '<figure>
 <img src="/api/v1/public/files/9d3d1319-1397-5215-b187-c925c35623cc" alt="img_1317.jpg">
 <figcaption class="align-center">
  Trạm bơm Cao Xuân Dương vận hành tiêu thoát nước chống ngập úng trong chiều 23-8-2026. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Tại trạm bơm Cao Xuân Dương (xã Dân Hoà), 2 tổ máy được cán bộ, công nhân viên tại trạm tổ chức vận hành liên tục từ 7h sáng ngày 21-8. Đây cũng là thời điểm Hà Nội nhận định bước vào đợt mưa kéo dài.</p>
<p>Anh Nguyễn Huy Thanh, Trạm trưởng Trạm bơm Cao Xuân Dương cho biết, trạm được bố trí 6 tổ máy, với năng lực tiêu 4.100 m3/h, có nhiệm vụ tiêu úng chống ngập cho khoảng 200ha canh tác nông nghiệp.</p>
<p>Nhờ chủ động vận hành hệ thống thuỷ lợi từ sớm, hiện nay trên địa bàn xã Dân Hoà nói riêng, hầu hết các diện tích lúa vụ Mùa của bà con đều được bảo vệ an toàn; chưa ghi nhận những diện tích bị ngập úng.</p>
<figure>
 <img src="/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334" alt="img_1325.jpg">
 <figcaption class="align-center">
  Cán bộ, công nhân vận hành hệ thống trạm bơm Cao Xuân Dương. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Theo thống kê của Chi cục Thuỷ lợi và Phòng, chống thiên tai Hà Nội, tính đến 16h chiều 23-8, 4 doanh nghiệp thuỷ lợi trên địa bàn thành phố đang vận hành tổng cộng 172 trạm bơm. Tổng lưu lượng tiêu thoát đạt hơn 2 triệu m3/h.</p>
<p>Trong số này, Công ty TNHH MTV đầu tư phát triển thuỷ lợi sông Nhuệ đang vận hành 91 trạm; tiếp đến là các Công ty TNHH MTV đầu tư phát triển thuỷ lợi: Sông Đáy 40 trạm, sông Tích 30 trạm và Hà Nội 11 trạm.</p>
<p>Chủ tịch Công ty TNHH MTV đầu tư phát triển thủy lợi sông Đáy Trần Đình Cường cho biết, trong cao điểm mưa lũ, đơn vị chỉ đạo các xí nghiệp bố trí cán bộ ứng trực 24/24h, sẵn sàng vận hành từ sớm hệ thống các công trình, bảo đảm tiêu thoát nước chủ động.</p>
<figure>
 <img src="/api/v1/public/files/144a2a14-487d-5972-953d-d4008ba1f555" alt="img_1364.jpg">
 <figcaption class="align-center">
  Hệ thống thủy lợi tiêu thoát nước hiệu quả bảo vệ những diện tích nông nghiệp. <i>Ảnh: Tùng Nguyễn.</i>
 </figcaption>
</figure>
<p>Theo bản tin cập nhật của Trung tâm Dự báo khí tượng thuỷ văn quốc gia, do ảnh hưởng của bão số 4, dự kiến trên địa bàn Hà Nội sẽ còn có mưa vừa, mưa to đến ngày 24-8. Đến ngày 25-8, lượng mưa giảm dần nhưng nhiều xã, phường tiếp tục có mưa rào rải rác và dông.</p>
<p>Chi cục trưởng Chi cục Thủy lợi và Phòng, chống thiên tai Hà Nội Nguyễn Duy Du cho biết, đơn vị hiện đang tiếp tục theo dõi sát các bản tin dự báo, cảnh báo mưa lũ; phối hợp chặt chẽ với các doanh nghiệp thuỷ lợi, chủ động tổ chức vận hành hệ thống các trạm bơm để tiêu thoát nước nước sớm.</p>
<p>Để chủ động ứng phó với nguy cơ ngập lụt, đại diện Chi cục Thủy lợi và Phòng, chống thiên tai Hà Nội cũng đề nghị các xã, phường tiếp tục thực hiện nghiêm các nội dung chỉ đạo tại Công điện số 04/CĐ-UBND của UBND thành phố Hà Nội.</p>
<p>Các xã, phường, đặc biệt là các địa phương ven sông, cần tiếp tục theo dõi sát diễn biến mưa lũ để chủ động triển khai các biện pháp ứng phó với lũ lên trên các sông, bảo đảm an toàn các khu vực dân cư trũng thấp và diện tích canh tác nông nghiệp ven sông.</p>',
       '9d3d1319-1397-5215-b187-c925c35623cc', 'Hà Nội: Ngành thủy lợi ứng trực ngày đêm, tích cực tiêu úng chống ngập', 'Mưa kéo dài nhiều ngày qua khiến nhiều xã, phường trên địa bàn Hà Nội đối diện nguy cơ ngập lụt. Việc bảo vệ an toàn những diện tích sản xuất nông nghiệp, nhất ', 'seeding, article',
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = 'ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-3081'
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = 'ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-3081'
  AND a.published_version_id IS NULL;
INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = 'ha-noi-nganh-thuy-loi-ung-truc-ngay-dem-tich-cuc-tieu-ung-chong-ngap-3081' AND c.slug = 'tin-tuc'
ON CONFLICT DO NOTHING;
