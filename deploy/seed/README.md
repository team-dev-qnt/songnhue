# Nội dung seed cho cổng — 4 ảnh + 5 bài

Chạy **tự động trong mỗi lượt triển khai staging**, không còn nút bấm nào. Hai vế:

| Vế | Đi đường nào | Ai chạy |
|---|---|---|
| Hàng CSDL (4 `attachments` + 5 `articles`) | migration Flyway `V202608251100__seed_portal_content.sql` | `migrator` |
| Byte của ảnh | `deploy/seed/media/**` → bucket media | `minio-init` |

Cả hai bật bằng **một** cặp biến trong `.env`, và phải bật cùng nhau:

```dotenv
SEED_LOCATION=classpath:db/seed/portal   # MỘT biến, hai vế cùng đọc:
                                         #   migrator   → giải thêm location này
                                         #   minio-init → đẩy byte từ /seed-media
```

`SeedGateTest` canh cặp ấy không lệch, và đối chiếu `size_bytes`/`checksum_sha256` trong SQL với
**byte thật của tệp trên đĩa**.

## Thứ tự: byte trước, hàng sau

`migrator` khai `depends_on: minio-init (service_completed_successfully)`. Migration ghi hàng
`attachments` **khẳng định** byte đã có trong MinIO, nên ràng buộc đặt ở chỗ lời khẳng định được
viết ra, không đặt ở thứ tự dòng lệnh trong workflow. Gõ tay `docker compose run --rm migrator`
lúc chữa cháy vẫn ra đúng thứ tự.

## ⛔ Production: để trống cả hai biến

Migration này **mở đầu bằng lệnh xoá bài**. Bật ở production nghĩa là xoá nội dung thật của Công ty
rồi đăng 5 bài chép lại của báo ngoài — migration chạy một chiều, không có nút xác nhận nào chặn.

Cổng chặn nằm ở **location**, không nằm trong tệp SQL: tệp seed ở `classpath:db/seed/portal`, ngoài
`spring.flyway.locations` mặc định (`classpath:db/seed/none` — thư mục có thật và cố ý rỗng). Không
đặt `SEED_LOCATION` thì Flyway ở production **không nhìn thấy** tệp ấy; không phải "chạy rồi không
làm gì".

⚠ Đã bật ở staging thì **giữ bật**. Gỡ location sau khi migration đã vào `flyway_schema_history` sẽ
làm `validate` đỏ với *"applied migration not resolved"*. Muốn thôi seed thì dựng lại CSDL.

## Xoá những bài nào

```sql
DELETE FROM articles a
 WHERE NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.article_id = a.id);
```

Canh theo **quan hệ**, không theo danh sách slug. `menu_items.article_id` tham chiếu `articles(id)`
mà không khai `ON DELETE` — tức RESTRICT — nên `DELETE FROM articles` trần sẽ dừng giữa chừng vì lỗi
khoá ngoại, sau khi đã xoá được một phần. Vị từ trên tự bảo vệ 4 trang tĩnh do
`V202608191021__cms_seed_site_structure` sở hữu (`gioi-thieu-chung` · `chuc-nang-nhiem-vu` ·
`co-cau-to-chuc` · `lien-he`), và vẫn đúng khi có trang tĩnh thứ năm.

📌 Hệ quả cần biết: **bài tạo tay trên staging sẽ bị xoá** ở lượt migration này (một lần duy nhất,
vì đây là migration có phiên bản).

## Ảnh: vì sao vào MinIO, và vì sao bố cục thư mục lại là khoá

Bản local ghi cứng ảnh vào `frontend/public-web/public/images/`. Chạy được ở cổng công khai nhưng
**hỏng ở giao diện quản trị**: `admin-app` là container khác, `/images/…` ở đó trả 404. Seed đi đúng
đường của hệ: `attachments` → MinIO → `GET /api/v1/public/files/{id}`.

Tệp nằm ở `deploy/seed/media/<storage_key>`, nên `mc cp --recursive /seed-media/ local/<bucket>/`
sinh ra **đúng** khoá mà SQL ghi. Không tiền tố nào viết cứng trong lệnh — một tiền tố viết ở hai
nơi là một tiền tố sẽ lệch.

⚠ `scan_status = 'SKIPPED'`, **không phải** `'CLEAN'`: ClamAV chưa từng quét mấy tệp này.

⚠ Seed ghi thẳng `status = 'XUAT_BAN'` → **không đi qua Workflow engine** (luật 4), nên 5 bài này
không có vết audit xuất bản. Chấp nhận được với dữ liệu để đo trên staging; đừng lấy làm mẫu.

📌 Nội dung thật của cổng sẽ tạo qua **màn hình quản trị**, không qua tệp này.

## Sinh lại

`V202608251100__seed_portal_content.sql` là **tệp sinh tự động** — sửa `generate.py` rồi sinh lại,
đừng sửa tay:

```bash
docker exec songnhue-postgres psql -U songnhue_owner -d songnhue -At \
  -c "SELECT json_build_object(…)" > /tmp/seed_db.json
python3 deploy/seed/generate.py /tmp/seed_db.json
```

## Kiểm sau khi deploy

Smoke test của CD đã hỏi ba câu này rồi (xem `.github/workflows/deploy.yml`), nên lượt deploy xanh
là ba câu ấy đã đạt:

1. `GET /api/v1/public/articles` trả về ≥ 1 bài — phân biệt **cổng có nội dung** với **cổng rỗng**
2. `GET /api/v1/public/files/<ảnh bìa lấy từ chính phản hồi ở câu 1>` → `image/*` — phép kiểm duy
   nhất chứng minh **MinIO có byte**, thứ mà mọi câu SQL đều không nói được. ⚠ Lấy id từ phản hồi
   chứ không ghi cứng id của bộ seed: id ấy cố ý **không tồn tại ở production**
3. Trang chủ trả HTML có thumbnail — cột đọc là `article_versions.cover_attachment_public_id`
