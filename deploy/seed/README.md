# Nội dung seed cho STAGING

**4 ảnh + 5 bài viết.** Ảnh đi vào **MinIO**, không nằm trong bundle giao diện.

```bash
deploy/seed/seed.sh --dry-run     # xem sẽ làm gì, không ghi gì
deploy/seed/seed.sh               # nạp thật
```

## Nạp lên staging: bấm tay ở GitHub

CD Staging **không** nạp dữ liệu — và đó là chủ ý (xem mục kế tiếp). Bước *Đồng bộ cấu hình* của
nó đã rsync cả thư mục này sang `/opt/songnhue/seed/` ở mỗi lượt triển khai, nên bộ seed trên máy
chủ luôn khớp với nhánh `staging`; chỉ còn thiếu người bấm chạy.

**Actions → `Nạp nội dung Staging` → Run workflow**, chọn `chay-thu` trước để xem nó định ghi gì,
rồi chạy lại với `nap-that`. Ô xác nhận phải gõ đúng `nap-noi-dung-staging`.

Workflow ấy **chỉ biết bộ secret `STAGING_*`** — không có tham số môi trường, nên không có đường
nào trỏ nó sang production. `SeedNeverAutomaticTest` canh cả bốn ràng buộc: không workflow tự động
nào gọi `seed.sh` · workflow seed chỉ có `workflow_dispatch` · không nhắc tới secret production ·
ô xác nhận còn nguyên.

Chạy thẳng trên máy chủ vẫn được: `cd /opt/songnhue && ./seed/seed.sh --dry-run`.

## ⛔ Cả 5 bài đều sao chép nguyên văn từ báo ngoài — CHỈ dùng cho staging

Cột `source` của từng bài ghi rõ URL gốc: `hanoimoi.vn` (4 bài) và `vneconomy.vn` (1 bài). Đây là
**toàn văn bài báo của người khác, kèm ảnh của họ**.

⛔ **Đừng chạy trên production.** Cổng thông tin của một doanh nghiệp nhà nước đăng lại nguyên văn
bài có bản quyền là vấn đề pháp lý, không phải lựa chọn kỹ thuật. Muốn đăng thì xin phép, hoặc
viết lại thành tin dẫn nguồn có trích dẫn.

Trên staging thì chấp nhận được, và có lý do để cần: môi trường đóng, `X-Robots-Tag: noindex,
nofollow`, và phải có bài **dài thật, ảnh thật** mới đo được bố cục và thời gian tải trang chủ
(**DOD1.17** — mục DoD Phase 1 duy nhất còn treo).

📌 Nội dung thật của cổng sẽ được tạo qua **màn hình quản trị**, không qua tệp này.

## Vì sao là script chứ không phải migration

Flyway chạy ở **mọi** môi trường, một chiều, không hỏi ai. Đưa 5 bài sao chép vào chuỗi migration
nghĩa là production tự đăng lại bài có bản quyền của người khác, im lặng, không ai bấm nút nào.

`CLAUDE.md`: *"⛔ Cấm seed dữ liệu 'cho đẹp demo'"*. Một script phải gõ tay, có `--dry-run`, in ra
mình ghi gì thì thoả điều đó. Một migration thì không.

## Không seed `categories`

Cây danh mục do migration `V202608191021__cms_seed_site_structure` sở hữu và **đã có sẵn trên mọi
môi trường**. Cả 5 bài gắn vào `tin-tuc` bằng cách tra theo `slug`. Seed lại danh mục là dựng một
nguồn sự thật thứ hai cho cùng một dữ liệu.

## Ảnh: vì sao phải vào MinIO

Bản local ghi cứng ảnh vào `frontend/public-web/public/images/`. Điều đó chạy được ở cổng công
khai — thư mục `public/` nằm trong image — nhưng **hỏng ở giao diện quản trị**: `admin-app` là
container khác, `/images/…` ở đó rơi vào nginx của nó và trả 404.

Seed chuyển sang đúng đường của hệ: `attachments` → MinIO → `GET /api/v1/public/files/{id}`, và
viết lại mọi `src="/images/x.jpeg"` trong thân bài thành đường dẫn ấy.

📌 **`cover_attachment_public_id` trước đây rỗng ở cả 18 bài local.** Truy vấn danh sách của cổng
đọc đúng cột đó (`ArticleRepository`, `v.coverAttachmentPublicId`), nên thumbnail không có gì để
hiện kể cả khi ảnh trong thân bài vẫn ổn. Seed gán ảnh bìa cho cả 5 bài, lấy ảnh đầu tiên trong
thân. `generate.py` **dừng hẳn** (`assert`) nếu gặp bài không có ảnh nào — thà hỏng lúc sinh còn
hơn ra một cổng có ô trống.

⚠ `scan_status = 'SKIPPED'`, **không phải** `'CLEAN'`: ClamAV chưa từng quét mấy tệp này. Ghi
`CLEAN` là nói dối sổ sách về một cơ chế bảo mật.

## Sinh lại tệp SQL

`*.sql` là **tệp sinh tự động** — sửa `generate.py` rồi sinh lại, đừng sửa tay:

```bash
docker exec songnhue-postgres psql -U songnhue_owner -d songnhue -At -c "SELECT json_build_object(…)" > /tmp/seed_db.json
python3 deploy/seed/generate.py /tmp/seed_db.json
```

Tách sinh khỏi chạy vì một tệp SQL đọc được là thứ duy nhất trả lời được câu *"seed này đã ghi
những gì vào CSDL"*.

## Đã kiểm chứng thế nào (25/8)

Nạp vào một CSDL nháp nhân bản lược đồ thật, **đã xoá hết bài nhưng GIỮ 9 danh mục** — đúng trạng
thái staging sau khi migrator chạy xong:

| Phép | Kết quả |
|---|---|
| Trước seed | danh mục 9 · bài 0 · đính kèm 0 |
| Sau seed | **4 ảnh · 5 bài · 5/5 có `published_version_id` · 5/5 có ảnh bìa · 5 liên kết `tin-tuc`** |
| Còn sót đường dẫn ghi cứng `/images/` | **0** |
| Chạy lại lần hai | không nhân đôi một hàng nào |
| Đẩy ảnh lên MinIO | đúng khoá, kích thước khớp `images.json` từng byte |

Hai lỗi chỉ lộ ra khi chạy thật, không đọc lược đồ mà thấy được:

* `ON CONFLICT (slug)` **không khớp** — `uq_articles_slug` là chỉ mục **một phần**
  (`WHERE deleted_at IS NULL`), phải nhắc lại vị từ ở `ON CONFLICT`.
* `author_user_id` là `NOT NULL`.

⚠ **Mắt xích cuối chỉ kiểm được trên môi trường thật.** Hàng trong CSDL và byte trong MinIO là hai
hệ thống khác nhau; lệch khoá là hỏng câm — CSDL vẫn nói tệp tồn tại, còn `GET` trả 404. Sau khi
chạy trên staging:

```bash
curl -sI "$BASE_URL/api/v1/public/files/15509c57-8e04-57e6-8d36-6a9cd1c68334"   # → 200 + image/jpeg
```
