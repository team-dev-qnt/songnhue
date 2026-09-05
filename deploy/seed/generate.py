#!/usr/bin/env python3
"""
Sinh migration seed nội dung cổng từ bản xuất của CSDL local.

Phạm vi: **5 bài sao chép từ báo ngoài** + 4 ảnh của chúng. Không có gì khác — xem README.

⚠ Đây là bộ SINH, không phải bộ chạy. Kết quả là MỘT tệp migration được commit và review
  như mã; chạy nó là việc của Flyway ở service `migrator`, và chỉ khi `SEED_LOCATION` trỏ
  vào thư mục ấy.

⚠ Byte của ảnh KHÔNG do tệp này lo. Chúng nằm ở `deploy/seed/media/<storage_key>` và lên
  MinIO qua `minio-init`. Bố cục thư mục CHÍNH LÀ khoá đối tượng — đừng đặt tiền tố
  `seed/portal/` ở thêm chỗ nào nữa, một tiền tố viết ở hai nơi là một tiền tố sẽ lệch.

Dùng: python3 deploy/seed/generate.py /tmp/seed_db.json
"""
import json
import pathlib
import re
import sys

RA = pathlib.Path(__file__).parent
DICH = RA.parent.parent / "backend/content/src/main/resources/db/seed/portal/V202608251100__seed_portal_content.sql"
BUCKET = "songnhue-media"

PRO = """-- ⚠ TỆP SINH TỰ ĐỘNG — sửa `deploy/seed/generate.py` rồi sinh lại, đừng sửa tay.
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

"""

DK = """-- =============================================================================
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

"""

BV = """-- =============================================================================
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


"""


def q(v):
    """Trích dẫn cho SQL. `None`/rỗng → NULL, để phân biệt 'chưa có' với 'chuỗi rỗng'."""
    if v is None or v == "":
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


def ghep(dinh_kem, bai_viet):
    """Nối phần văn xuôi (hằng số ở trên) với phần dữ liệu (sinh ra). Tách hàm để
    `SeedGateTest` có thể đối chiếu tệp đã commit với đúng công thức này."""
    return PRO + DK + "\n".join(dinh_kem) + "\n\n" + BV + "\n".join(bai_viet) + "\n"


def main(nguon):
    d = json.loads(pathlib.Path(nguon).read_text())
    imgs = json.loads((RA / "images.json").read_text())
    doi = {f"/images/{i['original_name']}": f"/api/v1/public/files/{i['public_id']}" for i in imgs}

    for i in imgs:
        tep = RA / "media" / i["storage_key"]
        assert tep.is_file(), f"thiếu byte: {tep} — hàng CSDL không có tệp là hỏng câm"
        assert tep.stat().st_size == i["size"], f"lệch kích thước: {tep}"

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

    dinh_kem = []
    for i in imgs:
        dinh_kem.append(f"""INSERT INTO attachments (public_id, owner_type, purpose, original_name, storage_bucket,
        storage_key, content_type, size_bytes, checksum_sha256, status, scan_status, created_by)
VALUES ({q(i['public_id'])}, 'MEDIA_FOLDER', 'SEED_PORTAL', {q(i['original_name'])}, {q(BUCKET)},
        {q(i['storage_key'])}, {q(i['content_type'])}, {i['size']}, {q(i['sha256'])},
        'READY', 'SKIPPED', (SELECT id FROM users WHERE username = 'superadmin'))
ON CONFLICT (public_id) DO NOTHING;""")

    bai_viet = []
    for a in bai:
        bans = sorted(ban.get(a["id"], []), key=lambda v: v["version_no"])
        dang = next((v for v in bans if v["id"] == a["published_version_id"]), bans[-1])
        noi_dung = viet_lai(dang["content"])
        bia = dang["cover_attachment_public_id"] or anh_bia(dang["content"])
        assert bia, f"bài {a['id']} không có ảnh nào → thumbnail sẽ trống"
        slug = a["slug"]
        dm = [cat_slug[l["category_id"]] for l in d["links"] if l["article_id"] == a["id"]]
        khoi = [f"""
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
  AND a.published_version_id IS NULL;"""]
        for c in dm:
            khoi.append(f"""INSERT INTO article_categories (article_id, category_id)
SELECT a.id, c.id FROM articles a, categories c WHERE a.slug = {q(slug)} AND c.slug = {q(c)}
ON CONFLICT DO NOTHING;""")
        bai_viet.append("\n".join(khoi))

    DICH.parent.mkdir(parents=True, exist_ok=True)
    DICH.write_text(ghep(dinh_kem, bai_viet))
    print(f"{DICH} — đính kèm {len(imgs)} · bài {len(bai)}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/seed_db.json")
