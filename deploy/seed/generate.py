#!/usr/bin/env python3
"""
Sinh tệp SQL seed nội dung cổng từ bản xuất của CSDL local.

Phạm vi: **5 bài sao chép từ báo ngoài** + 4 ảnh của chúng. Không có gì khác — xem README.

⚠ Đây là bộ SINH, không phải bộ chạy. Kết quả (`*.sql`) được commit và review như mã;
  chạy chúng là việc của `seed.sh`. Tách hai việc vì một tệp SQL đọc được là thứ duy nhất
  cho phép trả lời câu "seed này đã ghi những gì vào CSDL".

Dùng: python3 deploy/seed/generate.py /tmp/seed_db.json
"""
import json
import pathlib
import re
import sys

RA = pathlib.Path(__file__).parent
BUCKET = "songnhue-media"
HEADER = """-- ⚠ TỆP SINH TỰ ĐỘNG — sửa `deploy/seed/generate.py` rồi sinh lại, đừng sửa tay.
-- Idempotent: chạy lại nhiều lần không nhân đôi dữ liệu."""


def q(v):
    """Trích dẫn cho SQL. `None`/rỗng → NULL, để phân biệt 'chưa có' với 'chuỗi rỗng'."""
    if v is None or v == "":
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


def main(nguon):
    d = json.loads(pathlib.Path(nguon).read_text())
    imgs = json.loads((RA / "images.json").read_text())
    doi = {f"/images/{i['file']}": f"/api/v1/public/files/{i['public_id']}" for i in imgs}

    def viet_lai(html):
        for cu, moi in doi.items():
            html = (html or "").replace(cu, moi)
        return html

    def anh_bia(html):
        m = re.search(r"/api/v1/public/files/([0-9a-f-]{36})", viet_lai(html))
        return m.group(1) if m else None

    cat_slug = {c["id"]: c["slug"] for c in d["cats"]}
    ban = {}
    for v in d["vers"]:
        ban.setdefault(v["article_id"], []).append(v)

    # Chỉ lấy bài có `source` là URL — đó là định nghĩa của "bài sao chép".
    bai = [a for a in d["arts"] if (a["source"] or "").startswith("http")]

    # ---------------- 01 · đính kèm ----------------
    L = [HEADER, f"""
-- =============================================================================
-- 01 · ĐÍNH KÈM — {len(imgs)} ảnh của {len(bai)} bài seed
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
"""]
    for i in imgs:
        L.append(f"""INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ({q(i['public_id'])}, 'MEDIA_FOLDER', 'SEED_PORTAL', {q(i['file'])}, {q(BUCKET)},
        {q(i['storage_key'])}, {q(i['content_type'])}, {i['size']}, {q(i['sha256'])},
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;""")
    (RA / "01-attachments.sql").write_text("\n".join(L) + "\n")

    # ---------------- 02 · bài viết ----------------
    L = [HEADER, f"""
-- =============================================================================
-- 02 · {len(bai)} BÀI SAO CHÉP NGUYÊN VĂN TỪ BÁO NGOÀI — CHỈ dùng cho STAGING
--
-- Cột `source` của từng bài ghi rõ URL gốc (hanoimoi.vn, vneconomy.vn). Đây là toàn văn
-- bài báo của người khác, kèm ảnh của họ.
--
-- ⛔ ĐỪNG chạy trên PRODUCTION. Cổng thông tin của một doanh nghiệp nhà nước đăng lại
--    nguyên văn bài có bản quyền là vấn đề pháp lý, không phải lựa chọn kỹ thuật.
--
-- Trên staging thì chấp nhận được: môi trường đóng, `X-Robots-Tag: noindex, nofollow`, và
-- cần có bài dài thật để đo bố cục và thời gian tải trang chủ (DOD1.17).
--
-- ⚠ KHÔNG seed `categories`: cây danh mục do migration `V202608191021__cms_seed_site_structure`
--   sở hữu và đã có sẵn trên mọi môi trường. Seed lại là dựng một nguồn sự thật thứ hai cho
--   cùng một dữ liệu.
-- =============================================================================
"""]
    for a in bai:
        bans = sorted(ban.get(a["id"], []), key=lambda v: v["version_no"])
        dang = next((v for v in bans if v["id"] == a["published_version_id"]), bans[-1])
        noi_dung = viet_lai(dang["content"])
        bia = dang["cover_attachment_public_id"] or anh_bia(dang["content"])
        assert bia, f"bài {a['id']} không có ảnh nào → thumbnail sẽ trống"
        slug = a["slug"]
        dm = [cat_slug[l["category_id"]] for l in d["links"] if l["article_id"] == a["id"]]
        L.append(f"""
-- ---- {dang['title'][:70]}
--      nguồn: {a['source']}
INSERT INTO articles (title, slug, summary, content, source, status, published_at, meta_title,
        meta_description, meta_keywords, author_user_id, created_by)
VALUES ({q(dang['title'])}, {q(slug)}, {q(dang['summary'])}, {q(noi_dung)}, {q(a['source'])},
        {q(a['status'])}, {q(a['published_at'])}, {q(a['meta_title'])}, {q(a['meta_description'])},
        {q(a['meta_keywords'])}, (SELECT id FROM users WHERE username = 'superadmin'),
        (SELECT id FROM users WHERE username = 'superadmin'))
-- ⚠ `uq_articles_slug` là chỉ mục MỘT PHẦN (`WHERE deleted_at IS NULL`). Bỏ vị từ ở đây thì
--    Postgres báo "no unique or exclusion constraint matching" và cả tệp dừng.
ON CONFLICT (slug) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO article_versions (article_id, version_no, title, slug, summary, content,
        cover_attachment_public_id, meta_title, meta_description, meta_keywords, note, created_by)
SELECT a.id, 1, {q(dang['title'])}, {q(slug)}, {q(dang['summary'])}, {q(noi_dung)},
       {q(bia)}, {q(dang['meta_title'])}, {q(dang['meta_description'])}, {q(dang['meta_keywords'])},
       'Nội dung seed cho staging', a.created_by
FROM articles a WHERE a.slug = {q(slug)}
  AND NOT EXISTS (SELECT 1 FROM article_versions v WHERE v.article_id = a.id);

-- Không có dòng này thì bài KHÔNG hiện trên cổng: truy vấn danh sách đọc `v.title`,
-- `v.summary`, `v.coverAttachmentPublicId` qua `published_version_id` — bỏ trống là
-- INNER JOIN không khớp, và cổng dựng ra một trang hợp lệ mà rỗng.
UPDATE articles a SET published_version_id = v.id
FROM article_versions v WHERE v.article_id = a.id AND a.slug = {q(slug)}
  AND a.published_version_id IS NULL;""")
        for c in dm:
            L.append(f"""INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = {q(slug)} AND c.slug = {q(c)}
ON CONFLICT DO NOTHING;""")
    (RA / "02-articles.sql").write_text("\n".join(L) + "\n")
    print(f"đính kèm {len(imgs)} · bài {len(bai)}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/seed_db.json")
