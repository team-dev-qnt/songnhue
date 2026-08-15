# Luồng CI/CD — chốt 2026-08-15

> Đi kèm: `docs/branch-protection.md` (cấu hình phía GitHub) ·
> `.github/workflows/` (ci · promotion-guard · deploy-staging · deploy-prod)

## 1. Hình dạng

```
nhánh feature ──PR──► dev ──PR──► staging ──PR──► production
                       │           │                │
                   CI đầy đủ   CD tự động      CD khi có lệnh
                   + đóng gói   (không kiểm     (dừng chờ duyệt,
                     image       lại, không      dump trước, không
                                 build lại)      build lại)
```

`master` **nằm ngoài luồng** — nhánh riêng của chủ repo, không workflow nào chạm tới.

## 2. Hai nguyên tắc, và chúng là một

**Kiểm một lần.** Toàn bộ kiểm tra nặng chạy ở `dev`: định dạng, quy ước, 226 bài kiểm, luật kiến
trúc, ma trận phân quyền, cổng bao phủ. Hai chặng sau không kiểm lại — chạy lại cùng bộ kiểm tra
trên cùng mã nguồn tốn vài phút mỗi lần đề bạt mà không phát hiện thêm gì.

**Đóng gói một lần.** Image build đúng một lần, ở CI của `dev`, gắn tag theo **commit SHA**. Staging
và production đề bạt chính image đó.

Nguyên tắc thứ hai là điều kiện để nguyên tắc thứ nhất có nghĩa. Nếu mỗi chặng tự build lại, thì
cùng một commit vẫn có thể ra image khác nhau — base image đã cập nhật, thư viện hệ thống đổi
phiên bản — và production chạy một thứ chưa ai từng thử. Kiểm một lần rồi build ba lần là **không
kiểm gì cả**.

> Hệ quả cần nhớ: **nếu một thứ không được kiểm ở `dev` thì nó không được kiểm ở đâu cả.**

## 3. Chặng `dev` — nơi mọi thứ được kiểm

`ci.yml` chạy khi push vào `dev` và khi có PR hướng vào `dev`.

| Job | Việc | Bắt buộc để merge |
|---|---|:-:|
| `Vùng nào thay đổi` | So đường dẫn thay đổi, quyết định chạy job nào | — |
| `Backend — build, lint, test` | Spotless · Checkstyle · `mvn verify` (test đơn vị + Testcontainers + ArchUnit + cổng bao phủ) | ✅ |
| `Frontend — lint` | ESLint + `tsc --noEmit` (tự bỏ qua tới khi WS-8/WS-9 có mã) | ✅ |
| `Đóng gói image` | Build + đẩy `ghcr.io/…/app:<sha>` — chỉ khi **push**, không chạy cho PR | — |
| `Quét lỗ hổng phụ thuộc` | OWASP Dependency-Check + `npm audit` | ❌ (xem §3.2) |

### 3.1. Lọc theo đường dẫn — tiết kiệm ở đâu, và cố ý KHÔNG tiết kiệm ở đâu

Đổi tài liệu thì không chạy 226 bài kiểm; đổi FE thì không build backend. Hai vùng này không dùng
chung hệ thống build nào nên **không thể làm hỏng nhau** — phần tiết kiệm này gần như không có rủi
ro, khác hẳn với "chỉ chạy test liên quan tới code vừa đổi".

**Cố ý không lọc mịn hơn tới từng module backend.** Hai lý do, lý do thứ hai mới là lý do chính:

1. *Tiết kiệm gần bằng không.* `core` chiếm gần như toàn bộ mã nguồn backend và 4 module còn lại
   đều phụ thuộc vào nó. "Chỉ chạy phần đổi" hầu như luôn quy về "chạy tất cả".
2. *Rủi ro thì rất thật.* Những lỗi nặng nhất mà WS-10 tìm ra đều là lỗi **liên tầng**, và tất cả
   đều bị bắt bởi bài kiểm nằm ở module `app` chứ không phải ở module vừa sửa:

   | Lỗi | Sửa ở đâu | Bị bắt ở đâu |
   |---|---|---|
   | Tầng 3 phân quyền không hoạt động | `core/common/persistence` | `app` — `ScopeFilterEndToEndTest` |
   | Controller gọi thẳng repository | `core/api` | `app` — `LayeringTest` |
   | `WorkflowAware` sai tầng | `core/application` | `app` — `LayeringTest` |
   | Mã quyền lệch với bảng `permissions` | `core/api` | `app` — `RbacMatrixTest` |

   Bỏ `app` vì "lần này chỉ sửa core" là bỏ đúng những bài kiểm sinh ra để bắt loại lỗi đó.

Người dùng chấp nhận rủi ro với "code/logic đã có" — bảng trên cho thấy rủi ro ấy nằm ở mức *chọn
test theo module*, chứ không nằm ở mức *chọn vùng theo đường dẫn*. Nên lấy phần tiết kiệm ở mức an
toàn, và không lấy ở mức nguy hiểm.

### 3.2. ⚠ Lọc ở mức job, không lọc ở mức workflow

Job bị `if:` bỏ qua vẫn báo về một check run, và branch protection tính nó là đạt. Còn workflow
không trigger (do `paths:` ở mức trên cùng) thì check bắt buộc **không bao giờ xuất hiện** và PR kẹt
vĩnh viễn ở *"Expected — Waiting for status to be reported"* — nút merge xám, không dòng lỗi nào.

Vì cùng lý do đó, job quét CVE **không** nằm trong danh sách bắt buộc: nó phụ thuộc dữ liệu NVD bên
ngoài, có ngày hỏng vì lý do chẳng liên quan tới PR. Chặn merge bằng một thứ hay hỏng vì lý do bên
ngoài là cách nhanh nhất để cả đội học thói quen bỏ qua CI.

## 4. Chặng `staging` — tự động

`deploy-staging.yml` chạy khi push vào `staging` (tức là ngay sau khi merge PR từ `dev`).

Không kiểm lại, không build lại. Chỉ làm bốn việc: tra image theo SHA → gắn thêm tag `staging` →
`pull` + chạy `migrator` + `up -d` → smoke test `/actuator/health`.

### 4.1. ⚠ SHA nào mới là SHA có image

`github.sha` trên `staging` là **commit merge vừa tạo**, không phải commit của `dev` — mà image lại
gắn tag theo commit của `dev`. Với merge commit, `HEAD^2` chính là đỉnh `dev` lúc merge, nên
workflow thử `HEAD^2` trước rồi mới tới `HEAD`.

Đây cũng là lý do `required_linear_history` bị **tắt** ở staging/production
(`branch-protection.md` §2.3): squash sinh SHA mới không có cha thứ hai, và mối liên hệ với image đã
kiểm bị cắt đứt.

## 5. Chặng `production` — chỉ khi có lệnh

`deploy-prod.yml` **không có trigger `push`**. Merge vào `production` không tự deploy: đưa lên môi
trường thật là một quyết định vận hành có thời điểm của nó (ngoài giờ hành chính, sau khi đã báo
Công ty), không phải hệ quả tự động của một thao tác git.

Chạy bằng `workflow_dispatch`, bắt buộc nhập **commit SHA** và **lý do**. Ba lớp chặn:

1. **`environment: production`** — GitHub dừng chờ người duyệt bấm nút.
2. **Commit phải là tổ tiên của `staging`** — chặn đúng cái sai nguy hiểm nhất của deploy thủ công:
   gõ nhầm một SHA chưa bao giờ chạy ở staging. Không có bước này thì "manual deploy" nghĩa là "ai
   gõ gì cũng lên được".
3. **`pg_dump` ngay trước khi deploy** — điểm quay lui **duy nhất** về dữ liệu. Hệ này không có
   PITR (`architecture-review.md` §6.5), bản dump đêm trước là thứ gần nhất, nên migration hỏng lúc
   10h sáng là mất cả buổi làm việc.

## 6. Cổng đề bạt

`promotion-guard.yml` là check bắt buộc **duy nhất** của `staging` và `production`. Chạy ~5 giây,
kiểm hai điều:

- Nhánh nguồn đúng chặng trước (`dev` → staging, `staging` → production). GitHub **không có** tuỳ
  chọn "chỉ nhận PR từ nhánh X" — thiếu job này thì ai cũng mở được PR từ một nhánh feature thẳng
  vào production, và không check nặng nào chặn lại vì ta đã cố ý không yêu cầu chúng ở đó.
- **Đúng commit đang đề bạt** đã xanh CI, tra qua API check-runs của chính SHA đó — không phải
  "nhánh `dev` nói chung đang xanh". `dev` hoàn toàn có thể vừa nhận một commit đỏ.

## 7. Secret cần đặt (WS-11)

| Secret | Dùng ở | Ghi chú |
|---|---|---|
| `STAGING_HOST` / `STAGING_USER` / `STAGING_SSH_KEY` / `STAGING_BASE_URL` | CD Staging | Thiếu → workflow **cảnh báo và bỏ qua bước deploy**, không báo đỏ giả |
| `PROD_HOST` / `PROD_USER` / `PROD_SSH_KEY` / `PROD_BASE_URL` | CD Production | Như trên |
| `NVD_API_KEY` | Quét CVE | Không bắt buộc; thiếu thì lượt quét đầu mất hàng chục phút |

`GITHUB_TOKEN` có sẵn, dùng để đẩy/kéo image trên GHCR.

## 8. Việc còn phải làm

- [ ] Tạo nhánh `staging` và `production` (nợ #23)
- [ ] Áp dụng branch protection theo `docs/branch-protection.md` — cần quyền admin (nợ #23)
- [ ] Tạo GitHub Environment `production` có required reviewer (`branch-protection.md` §4.3)
- [ ] Dựng 3 VM + `compose.staging.yml` / `compose.prod.yml` + `backup/pre-deploy-dump.sh` — WS-11
- [ ] Đặt các secret ở §7 — WS-11/T11.7
- [ ] Push để pipeline chạy thật lần đầu (nợ #24)
