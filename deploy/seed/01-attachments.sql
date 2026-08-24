-- ⚠ TỆP SINH TỰ ĐỘNG — sửa `deploy/seed/generate.py` rồi sinh lại, đừng sửa tay.
-- Idempotent: chạy lại nhiều lần không nhân đôi dữ liệu.

-- =============================================================================
-- 01 · ĐÍNH KÈM — 4 ảnh của 5 bài seed
--
-- ⚠ Hàng ở đây phải khớp TỪNG BYTE với đối tượng `seed.sh` đẩy lên MinIO: cùng bucket,
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
