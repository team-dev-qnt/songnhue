-- ═══════════════════════════════════════════════════════════════════════════════════════════
--  Ảnh hoạt động do Công ty gửi — 30 ảnh · 27/08/2026
-- ═══════════════════════════════════════════════════════════════════════════════════════════
--
--  Đây là ảnh THẬT Công ty gửi, không phải bộ dữ liệu "cho đẹp demo" (CLAUDE.md cấm điều đó).
--  Mỗi tiêu đề dưới đây lấy NGUYÊN VĂN từ tên tệp Công ty đặt — không câu nào do phía phát triển
--  nghĩ ra. Ví dụ: "Ảnh to. Cống Liên Mạc - Đầu nguồn Sông Nhuệ.jpg" → "Cống Liên Mạc - Đầu nguồn
--  Sông Nhuệ".
--
--  ⭐ PHÂN NHÓM THEO ĐÚNG QUY ƯỚC CÔNG TY TỰ ĐẶT TRONG TÊN TỆP
--
--    "Ảnh to. …"  (5 ảnh, gốc 2560–4032px) → BANNER, tức ảnh lớn của slider trang chủ
--    "AN1/2/3. …" (25 ảnh, gốc nhỏ hơn)    → MEDIA_FOLDER, tức thư viện ảnh
--
--  Không phải tôi tự chia — Công ty đã nói ra ý định ấy bằng tiền tố tên tệp.
--
--  ⚠ ĐÃ NÉN TRƯỚC KHI ĐƯA VÀO KHO. Bộ gốc là 49 MB, ảnh lớn nhất 8,8 MB — một ảnh 8,8 MB trên
--    trang chủ đi thẳng ngược NFR-02 ("trang chủ < 3s"), và 49 MB vào lịch sử git thì không gỡ ra
--    được nữa. Nén: hero 1600px q55 (~271 KB), thư viện 900px q55 (~124 KB) → tổng 4,35 MB, đúng
--    cỡ quy ước của bộ seed sẵn có (83–111 KB/ảnh).
--
--  ⚠ `public_id` là UUIDv5 sinh từ TÊN TỆP với một không gian tên cố định — chạy lại bộ sinh cho
--    ra đúng những id này, nên `ON CONFLICT DO NOTHING` là idempotent thật chứ không may rủi.
--
--  ⚠ `scan_status = 'SKIPPED'`, KHÔNG phải 'CLEAN' — ClamAV chưa quét mấy tệp này; ghi CLEAN là
--    nói dối sổ sách về một cơ chế bảo mật (luật 16). Cùng lý do với bộ seed 25/8.
--
--  ⛔ `storage_key` phải khớp TỪNG KÝ TỰ với khoá `mc cp` tạo ra từ `deploy/seed/media/`. Lệch một
--     chỗ thì `/api/v1/public/files/<id>` trả 404 trong khi CSDL vẫn nói tệp tồn tại — hỏng câm.
-- ═══════════════════════════════════════════════════════════════════════════════════════════

-- [1] Thư mục ảnh của thư viện
INSERT INTO media_folders (public_id, name, path, depth, sort_order, created_by)
SELECT '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0', 'Ảnh hoạt động Công ty', '/', 0, 10,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0');


-- [2] 5 ảnh lớn → slider trang chủ

INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('6ab30651-64ee-5d18-a9f8-66dde78d277e', 'BANNER', NULL, 'BANNER', 'Ảnh to. Lễ phát động Tết trồng cây - Đời đời nhớ ơn Bác Hồ Xuân Giáp Thìn - năm 2024.jpg', 'songnhue-media',
        'seed/portal/6ab30651-64ee-5d18-a9f8-66dde78d277e.jpeg', 'image/jpeg', 390904, '602e8e72f88b379d05791ffdb31d99087508e444522865ddfb0942eff19b6baa',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO banners (title, image_attachment_public_id, sort_order, active, created_by)
SELECT 'Lễ phát động Tết trồng cây - Đời đời nhớ ơn Bác Hồ Xuân Giáp Thìn - năm 2024', '6ab30651-64ee-5d18-a9f8-66dde78d277e', 1, TRUE,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE image_attachment_public_id = '6ab30651-64ee-5d18-a9f8-66dde78d277e');
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('fce78091-eadd-5839-a498-a28c2b6e3b87', 'BANNER', NULL, 'BANNER', 'Ảnh to. Đại Hội đại biểu Đảng bộ Khối Doanh nghiệp Hà Nội lần thứ III.jpg', 'songnhue-media',
        'seed/portal/fce78091-eadd-5839-a498-a28c2b6e3b87.jpeg', 'image/jpeg', 294981, 'baea2d5a9c671c11b03df9444d67418915e37d11ba0a11d3623b1884bcc94cc8',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO banners (title, image_attachment_public_id, sort_order, active, created_by)
SELECT 'Đại Hội đại biểu Đảng bộ Khối Doanh nghiệp Hà Nội lần thứ III', 'fce78091-eadd-5839-a498-a28c2b6e3b87', 2, TRUE,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE image_attachment_public_id = 'fce78091-eadd-5839-a498-a28c2b6e3b87');
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('3c6aef03-c1d5-5a8e-9d45-0510997408db', 'BANNER', NULL, 'BANNER', 'Ảnh to. Bộ trưởng Bộ NN&PTNT Lê Minh Hoan kiểm tra công tác phòng chống thiên tai năm 2024 tại trạm bơm Yên Nghĩa.jpg', 'songnhue-media',
        'seed/portal/3c6aef03-c1d5-5a8e-9d45-0510997408db.jpeg', 'image/jpeg', 302235, 'c0bad55d4587fe58f609634e6b8ff89b44d47809bba97813c24e9eb30b5c140c',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO banners (title, image_attachment_public_id, sort_order, active, created_by)
SELECT 'Bộ trưởng Bộ NN&PTNT Lê Minh Hoan kiểm tra công tác phòng chống thiên tai năm 2024 tại trạm bơm Yên Nghĩa', '3c6aef03-c1d5-5a8e-9d45-0510997408db', 3, TRUE,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE image_attachment_public_id = '3c6aef03-c1d5-5a8e-9d45-0510997408db');
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('0d748e75-cf9d-5914-94a3-394c02a9d407', 'BANNER', NULL, 'BANNER', 'Ảnh to. Cống Liên Mạc - Đầu nguồn Sông Nhuệ.jpg', 'songnhue-media',
        'seed/portal/0d748e75-cf9d-5914-94a3-394c02a9d407.jpeg', 'image/jpeg', 217510, '91dbcd62599d14384dcdd36340d150641f5aaee941bfe8caf294655f48e3c2e5',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO banners (title, image_attachment_public_id, sort_order, active, created_by)
SELECT 'Cống Liên Mạc - Đầu nguồn Sông Nhuệ', '0d748e75-cf9d-5914-94a3-394c02a9d407', 4, TRUE,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE image_attachment_public_id = '0d748e75-cf9d-5914-94a3-394c02a9d407');
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('8301221d-be30-595d-8d46-9dce8c029445', 'BANNER', NULL, 'BANNER', 'Ảnh to. Thứ trưởng Bộ NN&PTNN Hoàng Văn Thắng và Phó Chủ tịch UBND Thành phố Hà Nội Trần Xuân Việt cắt băng khánh thành Trạm bơm Ngoại Độ II.jpg', 'songnhue-media',
        'seed/portal/8301221d-be30-595d-8d46-9dce8c029445.jpeg', 'image/jpeg', 181304, '0701c463047169d0ccea974825b31c310529f954b08eb5d827144ce984ddd0de',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO banners (title, image_attachment_public_id, sort_order, active, created_by)
SELECT 'Thứ trưởng Bộ NN&PTNN Hoàng Văn Thắng và Phó Chủ tịch UBND Thành phố Hà Nội Trần Xuân Việt cắt băng khánh thành Trạm bơm Ngoại Độ II', '8301221d-be30-595d-8d46-9dce8c029445', 5, TRUE,
       (SELECT id FROM users WHERE username = 'superadmin')
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE image_attachment_public_id = '8301221d-be30-595d-8d46-9dce8c029445');

-- [3] 25 ảnh thư viện

INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('7bbec8db-906c-5250-b70d-9f38ef8dac82', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN1. Đại hội Đại biểu Đoàn TNCS HCM UBND thành phố Hà Nội lần thứ I, nhiệm kỳ 2025-2030.JPG', 'songnhue-media',
        'seed/portal/7bbec8db-906c-5250-b70d-9f38ef8dac82.jpeg', 'image/jpeg', 128673, '9bdcd86901878a61716cc0fbc6dc5ac2b0493d793a8bb3cb22b5df81cc8d6fa1',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('62bcc5e8-a237-5dee-8d3f-f34b5d108d81', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN1.Công ty tham gia Đoàn công tác của Bộ NN&PTNT học tập tại Australia năm 2023.jpg', 'songnhue-media',
        'seed/portal/62bcc5e8-a237-5dee-8d3f-f34b5d108d81.jpeg', 'image/jpeg', 131010, '9c0183365a367d74d3c0c8b7e655e70623776b169ca8a4730e5676bd99d68b3a',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('bfa61c98-4fe1-5221-86ef-474e89806544', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2. Đoàn Thanh niên Công ty tham gia Ngày hội hiến máu năm 2023.jpg', 'songnhue-media',
        'seed/portal/bfa61c98-4fe1-5221-86ef-474e89806544.jpeg', 'image/jpeg', 94498, 'b74d0c6aa175a1adfa0971aadb23274b2a8f52ec4644eca69b6282bd1898576c',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('483e1777-7ade-55ca-a8f1-1e391480ca7b', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2. Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028.jpg', 'songnhue-media',
        'seed/portal/483e1777-7ade-55ca-a8f1-1e391480ca7b.jpeg', 'image/jpeg', 109831, 'c8ec183e2af98e6dc094f00d4303f81bc6cfa439732176adb1b46883db6c8ca1',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('fd58be19-98ac-52ed-909c-1a039527e81d', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2. Đội Dân quân tự vệ Công ty tham gia bắn đạn thật năm 2015.jpg', 'songnhue-media',
        'seed/portal/fd58be19-98ac-52ed-909c-1a039527e81d.jpeg', 'image/jpeg', 146627, '412908518564784eacf0fb33fb2930e1ea4e04452b0ee29e61e0b86ea1cf7b74',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('bdc61dfc-a388-586d-93bd-25d4d7d02da5', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2.Công ty tham gia Đoàn công tác của Bộ NN&PTNT học tập tại Hàn Quốc năm 2013.jpg', 'songnhue-media',
        'seed/portal/bdc61dfc-a388-586d-93bd-25d4d7d02da5.jpeg', 'image/jpeg', 81639, 'd2f46576f8beacdf4f67072002db6fdb8981a1a4cddec2e667b48613ccd251ae',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('947251cc-22a3-5fb9-9b27-8f71827dab3c', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2.Công ty thăm, động viên quân và dân quần đảo Trường Sa; cán bộ, chiến sĩ Nhà giàn DK-1 năm 2025.jpg', 'songnhue-media',
        'seed/portal/947251cc-22a3-5fb9-9b27-8f71827dab3c.jpeg', 'image/jpeg', 123594, '4208c07aa8898cea49095c5ad099753b396b233880f886858e0b9bb1d4ffc526',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('b4302069-fe6f-5682-8b19-8edb71ea64ce', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Bảo vệ đề tài, sáng kiến kinh nghiệm.jpg', 'songnhue-media',
        'seed/portal/b4302069-fe6f-5682-8b19-8edb71ea64ce.jpeg', 'image/jpeg', 88262, '5bb99f95f1bc3e28f1253910cd7018993fe294a972e006b8f5c0d69afa71aab5',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('c768ae21-9645-557e-9e2a-8899f8ef6c87', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Hội thao Công ty năm 2023.jpg', 'songnhue-media',
        'seed/portal/c768ae21-9645-557e-9e2a-8899f8ef6c87.jpeg', 'image/jpeg', 175780, 'a9c805a930fc9c45b34c8de5a28bd0e9c25e3c5bf3b761eeafc8f3417f792844',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('95683b7d-cfcc-5dd8-a9e0-83c0f7353a5b', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Tổ chức chương trình Vui tết Trung Thu năm 2022.jpg', 'songnhue-media',
        'seed/portal/95683b7d-cfcc-5dd8-a9e0-83c0f7353a5b.jpeg', 'image/jpeg', 107416, '0b9b6f3a2e634906998994196cd4f36797799285735eddfdd5a5ac5b9502904c',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('6ef8a2cf-ff4b-5622-b6ef-c68e6402940a', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3.Hội thao công ty năm 2025.JPG', 'songnhue-media',
        'seed/portal/6ef8a2cf-ff4b-5622-b6ef-c68e6402940a.jpeg', 'image/jpeg', 108214, '5887735d10b8290b27d49ca25dc63175204ed91e8e161c11a4fdf5802928a3a1',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('64a73733-0ace-57db-8476-be6de1e85ed5', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN1. Xây dựng cống Liên Mạc 1939.JPG', 'songnhue-media',
        'seed/portal/64a73733-0ace-57db-8476-be6de1e85ed5.jpeg', 'image/jpeg', 87272, '895a742ccd840023d6f70edbb4fe460397d309a6bb97ca2ffd3d49efa0b27f5a',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('82fdef34-60bc-578f-ba46-03f2e7bb2b0d', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN1.Nạo vét sông Nhuệ.jpg', 'songnhue-media',
        'seed/portal/82fdef34-60bc-578f-ba46-03f2e7bb2b0d.jpeg', 'image/jpeg', 96332, 'f1694ac032fdb3106baabc17cecdc8aa8720c5c1c54d5eb0ffe0b4053b6c2c76',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('d09dcd70-b139-5f45-98b4-12962ed863b8', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN1.Xây dựng cống Liên Mạc năm 1938.jpg', 'songnhue-media',
        'seed/portal/d09dcd70-b139-5f45-98b4-12962ed863b8.jpeg', 'image/jpeg', 132322, '5ecd8edfe600bcd791b4bbd892fa51d0c269e771ee50632a691afebc409fd649',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('db17fa21-f6e8-53c1-a94d-f38c4c84bea5', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2.  Xây dựng cống Liên Mạc 1939.JPG', 'songnhue-media',
        'seed/portal/db17fa21-f6e8-53c1-a94d-f38c4c84bea5.jpeg', 'image/jpeg', 161888, '5cd64eb2dfab700c66baca14db563f4cb66ac75ae5b1efe54d7a5b4763329cf9',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('a551e0bb-eab5-54c7-8ec8-15725e30251c', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2. Công trình đầu mối trạm bơm Hồng Vân.jpg', 'songnhue-media',
        'seed/portal/a551e0bb-eab5-54c7-8ec8-15725e30251c.jpeg', 'image/jpeg', 71578, '028b01a2d89fe2747d1c5c1bb8130b938719af3bf45c769688d5cfca936f5ce1',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('72202ca0-6f9e-5d2e-917b-b30d9fd26479', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2. Xây dựng cống Hà Đông năm 1939.jpg', 'songnhue-media',
        'seed/portal/72202ca0-6f9e-5d2e-917b-b30d9fd26479.jpeg', 'image/jpeg', 132006, '76a3ab8746a19e7e4fcc85b43b6c7bd7ea75125d59ae36b0f1e5d6670a4e3fb6',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('9dc02a24-0085-57ee-995e-30ec47aee7c5', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN2.Lắp đặt khẩn cấp máy bơm dã chiến trên sông Cầu Ngà chống ngập lụt khu dân cư.jpg', 'songnhue-media',
        'seed/portal/9dc02a24-0085-57ee-995e-30ec47aee7c5.jpeg', 'image/jpeg', 146193, 'b5682719ebade5fa91a5ec29c4b18c7444295da40cad6248542e5d76900f022e',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('2145d319-a695-58f1-9531-1eeedc926486', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Cống Lương Cổ.jpg', 'songnhue-media',
        'seed/portal/2145d319-a695-58f1-9531-1eeedc926486.jpeg', 'image/jpeg', 92079, 'd419bea6eabbb089d56f142aae2e8c64ef14d732adb7588d7d93d444e6f3bc00',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('d4613b17-e5d7-55e1-ae08-0e21fbcda790', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Toàn cảnh trạm bơm Yên Nghĩa.jpg', 'songnhue-media',
        'seed/portal/d4613b17-e5d7-55e1-ae08-0e21fbcda790.jpeg', 'image/jpeg', 158963, '2761ce2153bcdb986afacaee008c673f0ef4043f6b2b605b1e0fb53eefab2a52',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('186ecdcf-3c9c-5df6-9af5-fb2339f8f8b0', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Xây dựng cống Lương Cổ năm 1938.jpg', 'songnhue-media',
        'seed/portal/186ecdcf-3c9c-5df6-9af5-fb2339f8f8b0.jpeg', 'image/jpeg', 102183, 'f7c868647e54fe5d565a2addd6d8a6634216f4abf8b322fb565d0b9a7aa36974',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('6b58769d-093c-50d8-8dc5-717f74812be4', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', '1785207419516_3079181702633692887_3079181702633692887_38b04c2b20dae9b118da383d2c00630c.jpg', 'songnhue-media',
        'seed/portal/6b58769d-093c-50d8-8dc5-717f74812be4.jpeg', 'image/jpeg', 93084, '6a0a0bd8995cd2d1cc2b48eecbc94f23130936ffdb503a7d727a61b52ecb9894',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('f2fc302a-90ac-5fec-8ebd-ccadc7d83345', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', '1785224610882_4602082902160469425_4602082902160469425_cafc606f197af4bc15db928eb458370a.jpg', 'songnhue-media',
        'seed/portal/f2fc302a-90ac-5fec-8ebd-ccadc7d83345.jpeg', 'image/jpeg', 210983, 'e916ad11b2cb3726ae79719c2003401266c69e4c423b391a641a56fd78176c7c',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('f69fce5c-8d22-5b97-bfdd-7783a6a7a2da', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', '1785224749554_4602082902160469425_4602082902160469425_683fabe93a80ea6c88ef6b66c2b1f227.jpg', 'songnhue-media',
        'seed/portal/f69fce5c-8d22-5b97-bfdd-7783a6a7a2da.jpeg', 'image/jpeg', 204212, '2eac09ec48d86c82324cd6bbbca83cb48043b830c866f4407cead23383e6f40a',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;
INSERT INTO attachments (public_id, owner_type, owner_id, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ('4849040d-b4e6-53e3-9939-a51630cf6375', 'MEDIA_FOLDER', (SELECT id FROM media_folders WHERE public_id = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0'), 'SEED_PORTAL', 'AN3. Duy trì công trình thủy lợi cống Liên Mạc.jpg', 'songnhue-media',
        'seed/portal/4849040d-b4e6-53e3-9939-a51630cf6375.jpeg', 'image/jpeg', 192367, '29cfc02629821a683910babf357d3112f7f550915c80475166ec4c1e45881b63',
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;

-- [4] Trỏ khối thư viện trang chủ vào thư mục vừa dựng.
--     Chỉ ghi khi ô đang RỖNG — không giẫm lên lựa chọn Công ty tự đặt ở màn hình quản trị.
UPDATE settings
   SET setting_value = '0f1e2d3c-4b5a-5968-8776-a5b4c3d2e1f0', updated_at = now()
 WHERE setting_key = 'site.home.photos-folder'
   AND coalesce(setting_value, '') = '';
