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
| `Soi phụ thuộc PR thêm vào` | `dependency-review-action` — chỉ soi phần PR **thêm vào**, đọc Advisory Database của GitHub, vài giây | ❌ (xem §3.2) |

> ⚠ **OWASP Dependency-Check đã CHUYỂN RA khỏi `ci.yml`** (18/8) sang `security-scan.yml` chạy theo lịch — xem §3.3.

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

Vì cùng lý do đó, job quét phụ thuộc **không** nằm trong danh sách bắt buộc: nó dựa vào dữ liệu bên
ngoài, có ngày hỏng vì lý do chẳng liên quan tới PR. Chặn merge bằng một thứ hay hỏng vì lý do bên
ngoài là cách nhanh nhất để cả đội học thói quen bỏ qua CI.

### 3.2-b. ⚠⚠ Job bị bỏ qua NGẪU NHIÊN — bẫy `skipped` đã sập thật (18/8)

Mục 3.2 nói skip được tính là đạt. Ngày 18/8 điều đó xảy ra, ở một đường không ai canh: **bộ lọc
đường dẫn bỏ qua job `Backend — build, lint, test` một cách ngẫu nhiên**.

Thủ phạm là một dòng shell trông vô hại:

```bash
if echo "$changed" | grep -qE '^(backend/|...)'; then
```

`grep -q` thoát ngay khi thấy dòng khớp đầu tiên và đóng đầu đọc của ống. `echo` còn dữ liệu chưa ghi
hết thì nhận SIGPIPE và thoát 141; dưới `set -o pipefail`, trạng thái của **cả pipeline** thành 141 =
thất bại — **dù grep đã khớp**. Nhánh `else` chạy, `backend=false`, job bị bỏ qua, và required check
báo `skipped` = đạt.

Là một **cuộc đua**, không phải lỗi tất định: `echo` kịp ghi hết vào bộ đệm ống (64KB) thì không có
SIGPIPE. Cùng PR #1 — lượt 12:56 backend chạy bình thường, lượt 13:34 bị bỏ qua.

Hai thay đổi, và cái thứ hai quan trọng hơn:

1. **Here-string thay cho ống** — không có ống thì không có cuộc đua.
2. **Mặc định là `true`.** Bộ lọc chỉ được phép hạ xuống `false` khi nó chạy trót lọt và thật sự
   không khớp. Trục trặc thì chạy thừa. Một bộ lọc hỏng theo hướng "chạy thừa" tốn vài phút CI; hỏng
   theo hướng "bỏ qua" thì **không phát ra dấu hiệu nào** và PR merge với bộ kiểm chưa từng chạy.

### 3.3. ⚠ Quét CVE toàn kho là việc THEO LỊCH, không phải việc của PR (chốt 18/8)

Lượt CI **đầu tiên** của repo cho thấy chỗ này viết sai. Job `Quét lỗ hổng phụ thuộc` chạy **30 phút
rồi bị timeout huỷ**: không có `NVD_API_KEY` thì NVD giới hạn tốc độ rất nặng, mà lần đầu phải tải
378.798 bản ghi.

Hai bài học, bài thứ hai mới là bài chính:

1. **`continue-on-error: true` không phải van an toàn vạn năng.** Nó áp cho job *thất bại*; job bị
   **huỷ vì timeout** thoát khỏi nó và nhuộm `cancelled` lên cả lượt chạy. Cái van viết ra để "quét
   CVE không bao giờ chặn đường" đã không hoạt động ngay lần đầu cần tới.
2. **Nhịp của việc này không phải nhịp của PR.** Kho phụ thuộc không an toàn hơn vì có người mở PR,
   và nó kém an toàn đi kể cả khi không ai đụng vào mã — vì thế giới công bố thêm CVE. Gắn vào PR
   vừa làm chậm PR vừa **bỏ sót đúng trường hợp đáng lo nhất**: nhánh hai tuần không ai đụng tới.

Chốt: `security-scan.yml` chạy **02:15 UTC hằng đêm** (09:15 giờ Việt Nam, có kết quả trước giờ làm)
+ `workflow_dispatch` + khi `pom.xml`/`package-lock.json` đổi. Ở PR giữ `dependency-review-action` —
trả lời đúng câu hỏi của PR ("PR này có kéo thêm thư viện dính lỗ hổng nào không") trong vài giây.

Hai thứ khiến nó không lặp lại:

- **CSDL NVD cache riêng**, khoá xoay theo tuần + `restore-keys`. Mặc định plugin để CSDL trong
  `~/.m2/repository/…`, lẫn với bộ nhớ đệm Maven vốn đánh khoá theo hash pom — đổi một dependency là
  mất luôn vài GB và tải lại từ đầu. Hai thứ có nhịp thay đổi khác hẳn nhau thì phải cache riêng.
- **Thiếu `NVD_API_KEY` thì BỎ QUA và nói to** trong Job Summary kèm đường xin khoá, thay vì chạy 30
  phút rồi chết. Một job treo lâu thì người ta thôi không đọc nó nữa — mà đọc kết quả mới là điểm.

### 3.3-a. ⚠⚠ Bộ nhớ đệm NVD chỉ lưu khi job xanh — mà job này sinh ra để đỏ (sửa 18/8)

Lượt quét thật đầu tiên (`gh run view 32145220978`) phơi ra một lỗi trong chính cách viết ở §3.3.
CSDL NVD dựng mất **26 phút** rồi **không được lưu**: `gh api repos/.../actions/caches` không có mục
`dc-data-*` nào.

Nguyên nhân: `actions/cache` khai `post-if: success()`, nên **bước lưu chỉ chạy khi job thành
công**. Mà job này được thiết kế để **đỏ mỗi khi có CVE ≥ 7** — tức là trạng thái thường trực của
nó, không phải ngoại lệ.

Vòng tự triệt tiêu: **cơ chế tăng tốc chỉ hoạt động trong đúng trường hợp duy nhất mà nó không cần
thiết** (không có lỗ hổng nào). Có lỗ hổng — lúc cần chạy lại nhiều nhất — thì mỗi lượt trả đủ 26
phút.

Cách sửa, và cũng là cách chung cho mọi job "cố ý đỏ":

| Sai | Đúng |
|---|---|
| `actions/cache@v4` gộp | `actions/cache/restore@v4` + `actions/cache/save@v4` với **`if: always()`** |
| Một bước `mvn verify` làm cả cập nhật CSDL lẫn quét | **Tách hai bước**: `dependency-check:update-only` (chậm, gần như luôn xanh) → **lưu cache** → `verify` (nhanh, thường đỏ) |

Bước quét chạy với `-DautoUpdate=false` để không chạm mạng NVD lần thứ hai.

> **Luật rút ra**: bất cứ khi nào một bước *tốn kém nhưng ổn định* nằm chung job với một bước *rẻ
> nhưng hay đỏ*, phải hỏi kết quả của cái sau có quyết định cái trước được giữ lại hay không. Ở đây
> câu trả lời là có, và nó vô lý.

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
| `NVD_API_KEY` | `security-scan.yml` | ✅ **Đã đặt 18/8** ở **cấp repo**. **Thiếu thì bỏ qua hẳn phép quét OWASP** (có cảnh báo trong Job Summary). Xin miễn phí ~2 phút: <https://nvd.nist.gov/developers/request-an-api-key> |

> ⚠ **Đặt đúng cấp, không chỉ đúng tên.** Lần đầu `NVD_API_KEY` được đặt vào **environment
> `staging`** và phép quét vẫn bị bỏ qua: environment secret chỉ đến được job có khai
> `environment:`, mà `security-scan.yml` không khai — `secrets.NVD_API_KEY` giải ra chuỗi rỗng,
> **không có lỗi nào**, chỉ là bước "Kiểm khoá NVD" báo thiếu. Quy tắc: **khoá của công cụ CI đặt ở
> cấp repo; chỉ khoá gắn với một môi trường triển khai mới đặt ở environment**. Đặt nhầm vào
> environment có luật chờ duyệt còn tệ hơn — lượt quét đêm sẽ nằm chờ người bấm.

`GITHUB_TOKEN` có sẵn, dùng để đẩy/kéo image trên GHCR. ✅ **Kiểm chứng 18/8**: job `Đóng gói image`
đẩy được lên GHCR ở lượt push đầu tiên vào `dev`, dù repo đặt `default_workflow_permissions: read` —
`permissions: packages: write` khai tường minh ở job ghi đè được mặc định đó.

## 8. Việc còn phải làm

- [x] Tạo nhánh `staging` và `production` — xong 15/8/2026
- [x] Áp dụng branch protection theo `docs/branch-protection.md` — xong 15/8/2026
- [x] Tạo GitHub Environment `production` có required reviewer — xong 15/8/2026
- [ ] **Chỉnh 2 mục lộ ra khi kiểm chứng** — `branch-protection.md` §6.2 (`strict` ở hai chặng đề
      bạt · thiếu context `Vùng nào thay đổi`). *Mục thứ 3 của bản 15/8 — hạ số người duyệt — đã bỏ
      ngày 18/8: repo có hai collaborator admin nên `reviews: 1` chạy được thật.*
- [x] **Đưa mã lên `dev`** — PR #1 `common → dev` merge (Squash) 18/8/2026; lượt push sau đó chạy
      trọn 5/5 job, `git diff origin/dev origin/common` rỗng
- [x] Đặt `NVD_API_KEY` **ở cấp repo** — 18/8/2026 (§7)
- [ ] **Bật Dependency graph** (Settings → Code security) — không bật thì job `Soi phụ thuộc PR thêm
      vào` tự bỏ qua, tức là phép kiểm phụ thuộc ở PR chưa chạy lần nào
- [ ] Dựng 3 VM + `compose.staging.yml` / `compose.prod.yml` + `backup/pre-deploy-dump.sh` — WS-11
- [ ] Đặt các secret còn lại ở §7 — WS-11/T11.7

## 9. Hai quy ước merge ngược nhau — dễ nhầm nhất

| PR | Kiểu merge | Vì sao |
|---|---|---|
| nhánh feature → `dev` | **Squash** hoặc **Rebase** | `dev` bật `required_linear_history` — merge commit bị chặn |
| `dev` → `staging` → `production` | **Create a merge commit** | `deploy-staging.yml` tìm image qua `HEAD^2`; squash sinh SHA mới, cắt đứt liên kết với image đã kiểm (§4.1) |

GitHub không giới hạn được kiểu merge theo từng nhánh, nên đây là quy ước người dùng phải nhớ. Bù
lại, làm sai ở vế thứ hai thì **hỏng to tiếng**: không tìm thấy image → workflow dừng ngay với thông
báo "commit này chưa từng qua CI của dev", không có bản deploy nửa vời nào.

### 9.1. ⚠⚠ Squash xong thì nhánh nguồn ĐÃ CHẾT — đừng dùng lại (18/8, sập 2 lần trong một ngày)

Vế thứ nhất thì ngược lại: nó **hỏng im lặng**, và đã hỏng hai lần liên tiếp.

Squash tạo một commit **mới** mang nội dung nhánh nguồn nhưng **không mang lịch sử** của nó. Git vì
thế không có cách nào biết `dev` đã chứa công việc đó. Tổ tiên chung giữa hai nhánh đứng nguyên tại
chỗ cũ, và GitHub hiển thị PR bằng diff **ba chấm** `merge-base...head` — nên nó dựng lại *toàn bộ*
khác biệt kể từ điểm đó.

| Lần | Biểu hiện |
|---|---|
| 1 | PR hiện **437 tệp** trong khi nhánh chỉ thật sự khác **8** tệp. Merge vẫn được (429 tệp là thao tác rỗng) nhưng không ai review nổi |
| 2 | Commit chồng lên nền chưa reset → **xung đột thật** ở `backend/pom.xml`, `CLAUDE.md`, `.claude/phase0-tracking.md` — dù nội dung hai bên **giống hệt nhau** |

Lần 2 nặng hơn vì cùng một thay đổi tồn tại hai lần dưới hai danh tính khác nhau (`0bb9461` trên
nhánh, `4ece60b` là bản squash trên `dev`), git thấy hai bên cùng sửa một vùng văn bản.

**Luật**: sau mỗi lần squash merge, nhánh nguồn coi như đã chết.

```bash
# Cách đúng — cắt nhánh mới cho hạng mục sau
git checkout dev && git pull && git checkout -b <hạng-mục-mới>

# Nếu vẫn muốn dùng lại tên nhánh cũ thì PHẢI reset trước khi commit tiếp
git reset --hard origin/dev
git cherry-pick <chỉ những commit thật sự mới>
```

**Và có cơ chế canh, không chỉ có ghi chú.** `.githooks/pre-push` chặn trước khi đẩy — vì cả hai lần
trên đều không có dấu hiệu nào cho tới lúc mở PR. Cách phát hiện chính xác, không dùng ngưỡng đoán:
đếm số tệp **xuất hiện trong diff ba chấm nhưng nội dung đã giống hệt trên base** — lớn hơn 0 nghĩa
là nhánh lỗi thời.

```bash
make hooks                    # bật (một lần cho mỗi bản sao repo)
make branch-check             # hỏi tay bất cứ lúc nào
make branch-check-selftest    # chứng minh phép canh BẮT ĐƯỢC vi phạm
SKIP_BRANCH_CHECK=1 git push  # bỏ qua khi biết mình đang làm gì
```

> Phép tự kiểm không phải trang trí: bản đầu của chính script này **báo đạt mà không bắt được gì** —
> nó dựng repo mô phỏng trong một subshell rồi kiểm ở ngoài, tức soi nhầm repo thật. Đúng loại "xanh
> mà không chạy" ở `conventions.md` §1.5, và tự kiểm là thứ bắt được.
