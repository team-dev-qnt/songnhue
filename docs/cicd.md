# Luồng CI/CD — chốt 2026-08-15, xác nhận lại 2026-08-23

> Đi kèm: `docs/branch-protection.md` (cấu hình phía GitHub) ·
> `docs/deploy-guideline.md` (dựng máy chủ từ đầu, từng bước) ·
> `.github/workflows/` (ci · promotion-guard · security-scan · deploy-staging · deploy-prod)

> ### Ghi chú 23/8 — một vòng đi lạc, và vì sao quay lại
>
> Có một bản chỉnh sửa chuyển toàn bộ luồng này sang PaaS (Vercel + Railway): xoá hai workflow
> triển khai, xoá job đóng gói image, bỏ ba lớp chặn của production. Bản đó **đã hoàn nguyên**.
>
> Không phải vì PaaS tệ, mà vì với repo **này** nó đòi tháo năm bảo đảm đã dựng có chủ đích:
> thêm CORS vào backend · hạ cookie refresh từ `SameSite=Strict` xuống `None` · bỏ service
> `migrator` riêng · bỏ bốn vai trò CSDL tách quyền (mất tính chỉ-ghi-thêm của `audit_logs` ở
> tầng CSDL) · chuyển toàn bộ header bảo mật ra khỏi nơi đang có bài kiểm canh chúng.
>
> Ghi lại ở đây để lần sau ai đó cân nhắc PaaS thì bắt đầu từ danh sách này, chứ không bắt đầu lại
> từ đầu.

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

**Kiểm một lần.** Toàn bộ kiểm tra nặng chạy ở `dev`: định dạng, quy ước, **toàn bộ bộ kiểm BE + FE**, luật kiến
trúc, ma trận phân quyền, cổng bao phủ. Hai chặng sau không kiểm lại — chạy lại cùng bộ kiểm tra
trên cùng mã nguồn tốn vài phút mỗi lần đề bạt mà không phát hiện thêm gì.

**Đóng gói một lần.** Image build đúng một lần, ở CI của `dev`, gắn tag theo **commit SHA**. Staging
và production đề bạt chính image đó.

Nguyên tắc thứ hai là điều kiện để nguyên tắc thứ nhất có nghĩa. Nếu mỗi chặng tự build lại, thì
cùng một commit vẫn có thể ra image khác nhau — base image đã cập nhật, thư viện hệ thống đổi
phiên bản — và production chạy một thứ chưa ai từng thử. Kiểm một lần rồi build ba lần là **không
kiểm gì cả**.

> Hệ quả cần nhớ: **nếu một thứ không được kiểm ở `dev` thì nó không được kiểm ở đâu cả.**

### 2.1. ⚠⚠ "Một lần" nghĩa là BA image, không phải một (sửa 23/8)

Nguyên tắc trên viết từ 15/8, nhưng CI chỉ thực hiện được **một phần ba** của nó: job `Đóng gói
image` đóng gói `app`, và hai giao diện thì không ai đóng gói. Hai workflow triển khai vì thế chỉ
`up -d app nginx`.

Hậu quả nếu để nguyên: **backend mới chạy dưới giao diện cũ**, ở cả staging lẫn production, và
không một bước nào báo sai. Lượt deploy xanh, health-check xanh, smoke test xanh. Mã lỗi mới,
trường mới, quyền mới đều có ở API mà màn hình không biết tới — triệu chứng là vài ô trống và vài
nút không phản hồi, thứ rất khó truy về nguyên nhân "chưa ai đóng gói frontend".

Đây đúng khuôn *"công tắc chưa ai đọc là một lỗi, không phải việc để dành"* (`CLAUDE.md` §12): cam
kết viết ra ở §2 mà không có đường thực thi nào.

Nay có ba gói trên GHCR, cùng quy tắc gắn thẻ:

| Gói | Sinh ra bởi | Chạy khi |
|---|---|---|
| `…/app` | job `Đóng gói image` | vùng `backend/` đổi |
| `…/admin-app` | job `Đóng gói image frontend` (ma trận) | vùng `frontend/` đổi |
| `…/public-web` | job `Đóng gói image frontend` (ma trận) | vùng `frontend/` đổi |

**Ba image có thể mang ba digest khác nhau, và đó là đúng.** Một lượt chỉ sửa frontend thì
`admin-app` được dựng lại còn `app` giữ nguyên bản đã kiểm. Ép đóng gói lại phần không đổi là tạo
ra một image chưa ai thử — đúng thứ nguyên tắc này sinh ra để tránh.

### 2.1-b. Nhưng MỌI commit `dev` đều có đủ ba TAG (25/8)

Khác biệt nằm ở chỗ *digest* và *tag* không phải một thứ. Job `Gắn tag SHA cho image không đổi`
chạy cuối CI: image nào lượt này không dựng lại thì nó **gắn thêm** tag `<sha-mới>` lên **đúng
digest cũ** bằng `docker buildx imagetools create`. Không một byte nào được dựng lại; `app:<sha-mới>`
và `app:<sha-cũ>` trỏ vào cùng một manifest.

Đổi lại, hai chặng sau chỉ cần **một** SHA để định danh cả bản phát hành, và bước tra image rút từ
`50 commit × 3 image` xuống ba lượt tra thẳng. Chính vòng quét ấy đã đẻ ra §10.43: nó nuốt stderr
nên 403 (chưa xác thực) và 404 (chưa dựng) cho ra cùng một thông báo *"không tìm thấy image"*, rồi
phải thêm một bước tra thử riêng chỉ để phân biệt hai thứ đó.

## 3. Chặng `dev` — nơi mọi thứ được kiểm

`ci.yml` chạy khi push vào `dev` và khi có PR hướng vào `dev`.

| Job | Việc | Bắt buộc để merge |
|---|---|:-:|
| `Vùng nào thay đổi` | So đường dẫn thay đổi, quyết định chạy job nào | — |
| `Backend — build, lint, test` | Spotless · Checkstyle · `mvn verify` (test đơn vị + Testcontainers + ArchUnit + cổng bao phủ) | ✅ |
| `Frontend — lint` | ESLint + `tsc --noEmit` (tự bỏ qua tới khi WS-8/WS-9 có mã) | ✅ |
| `Đóng gói image` | Build backend. **Dựng ở cả PR, chỉ ĐẨY** `ghcr.io/…/app:<sha>` khi push vào `dev` | ⬜ nên bật |
| `Đóng gói image frontend` | Ma trận `admin-app` + `public-web`. Cùng luật: dựng ở PR, đẩy khi push | ⬜ nên bật |
| `Gắn tag SHA cho image không đổi` | Chỉ khi push `dev`. Gắn thêm tag `<sha>` lên digest cũ của image lượt này không dựng lại — để mọi commit `dev` có đủ ba tag (§2.1-b) | — |
| `Soi phụ thuộc PR thêm vào` | `dependency-review-action` — chỉ soi phần PR **thêm vào**, đọc Advisory Database của GitHub, vài giây | ❌ (xem §3.2) |

> ⚠ **OWASP Dependency-Check đã CHUYỂN RA khỏi `ci.yml`** (18/8) sang `security-scan.yml` chạy theo lịch — xem §3.3.

> ⚠⚠ **Hai job đóng gói image nay chạy ở cả PR** (24/8, `architecture-review.md` §10.38). Trước đó
> chúng có `if: github.event_name == 'push'`, nên lượt dựng image đầu tiên của một thay đổi diễn ra
> **sau khi đã merge** — chỗ duy nhất chúng có thể đỏ là `dev`. Đúng chuyện đã xảy ra với PR #10:
> mọi cổng kiểm ở PR xanh, merge xong `dev` đỏ ngay ở bước dựng `public-web`.
>
> Image là chỗ **duy nhất** thấy được những gì chỉ tồn tại lúc build container — `ARG` để trống,
> `.env.local` vắng mặt, tầng runtime chép hụt. Không thứ nào có mặt ở `npm run build` hay
> `mvn verify`, kể cả khi chạy trên cùng commit.
>
> ⬜ **Còn phải bấm ở GitHub**: thêm `Đóng gói image` và `Đóng gói image frontend` vào
> `required_status_checks` của nhánh `dev` (§ script ở `docs/branch-protection.md`). Chưa bật thì
> chúng chỉ *hiện* lỗi ở PR chứ không *chặn* merge.

### 3.1. Lọc theo đường dẫn — tiết kiệm ở đâu, và cố ý KHÔNG tiết kiệm ở đâu

Đổi tài liệu thì không chạy bộ kiểm backend; đổi FE thì không build backend. Hai vùng này không dùng
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

### 3.3-0. Job OWASP làm gì — đọc mục này trước khi sửa nó

Nhiệm vụ đúng một câu: **đối chiếu mọi thư viện mà backend kéo vào với cơ sở dữ liệu lỗ hổng công
khai, và chặn nếu có mã CVSS ≥ 7.**

Nó **không** đọc mã nguồn của dự án, không tìm lỗi logic, không kiểm cấu hình. Chỉ nhìn danh sách
phụ thuộc — kể cả phụ thuộc bắc cầu — rồi hỏi "phiên bản này có nằm trong dải bị ảnh hưởng của CVE
nào không".

| Bước | Việc | Ghi chú |
|---|---|---|
| Nạp bộ nhớ đệm CSDL NVD | lấy CSDL lỗ hổng của lượt trước | quyết định job chạy 20 giây hay 25 phút — §3.3-a |
| Kiểm khoá NVD | thiếu `NVD_API_KEY` thì **bỏ qua và nói to** | thiếu khoá → NVD giới hạn tốc độ tới mức không dùng được |
| Cập nhật CSDL NVD | tải phần mới từ NVD | ~378.000 bản ghi cho lần dựng đầu |
| Lưu bộ nhớ đệm | `if: always()` | vì bước sau **được thiết kế để đỏ** — §3.3-a |
| Dựng jar mọi module | `package -DskipTests` | `aggregate` cần jar liên module để giải phụ thuộc |
| **Dependency-Check `aggregate`** | quét + áp ngưỡng CVSS ≥ 7 | §3.3-b |
| Giữ lại báo cáo CVE | tải lên artifact | thứ **duy nhất** đọc được sau khi job đỏ |

Hai nguồn dữ liệu, và chỉ một cái còn dùng:

- **NVD** (`nvd.nist.gov`) — nguồn chính, cần `NVD_API_KEY` (miễn phí).
- ~~Sonatype OSS Index~~ — **đã tắt 18/8**. Nó hỏng ở gần như mọi artifact suốt 4 lượt (130 cảnh báo
  mỗi lượt) mà chưa đóng góp dữ liệu nào; tới khi Sonatype chặn truy cập ẩn danh (401) thì nó nâng
  thành `AnalysisException` và giết cả build. Muốn dùng lại phải có tài khoản Sonatype (nợ #49).

Khi job đỏ, có **ba** kiểu hỏng khác hẳn nhau — đọc nhầm kiểu là sửa nhầm chỗ:

| Dấu hiệu trong log | Nghĩa là gì | Làm gì |
|---|---|---|
| `One or more dependencies were identified with vulnerabilities…` | **Đúng việc của nó** — có lỗ hổng thật | Nâng phiên bản; không nâng được thì thẩm định rồi suppress có hạn (`conventions.md` §4.5) |
| `One or more exceptions occurred during dependency-check analysis` | Hạ tầng quét hỏng (mạng, nguồn dữ liệu, xác thực) | Sửa hạ tầng. ⛔ **Không** dùng `failOnError=false` |
| `NoDataException: … database does not exist` | Chưa dựng CSDL NVD | Bộ nhớ đệm trượt hoặc bước cập nhật bị bỏ |

> ⚠ **Điểm in ra trong thông báo không phải điểm dùng để chặn.** DC in **CVSS v4**, chặn theo **điểm
> cao nhất mọi thang**. Nên `CVE-2026-34479(6.9)` nằm dưới tiêu đề "≥ 7.0" là đúng — mã đó có v3 =
> 7.5. Chi tiết `conventions.md` §4.5 mục 4.

### 3.3-b. ⚠⚠ `check` chỉ soi ĐƯỢC MỘT MODULE rồi dừng — đổi sang `aggregate` (18/8)

Bản đầu gắn goal `check` vào phase `verify`. `check` chạy **riêng từng module**, mà Maven dừng
reactor ở module đầu tiên hỏng. Với `failBuildOnCVSS=7`, module đầu tiên có lỗ hổng sẽ chặn năm
module còn lại — chúng in `SKIPPED`, tức **chưa từng được quét**.

Đọc thẳng từ `Reactor Summary` của các lượt thật:

```
Vòng 1–4   Core FAILURE · Content SKIPPED · Operations SKIPPED · Hydro SKIPPED · HR SKIPPED · App SKIPPED
Vòng 5     Core SUCCESS · Content ✓ · Operations ✓ · Hydro ✓ · HR ✓ · App FAILURE ← CVE-2026-54291
```

Bốn lượt quét liên tiếp chỉ soi đúng **một** module. Mỗi lần dọn sạch `core` thì reactor đi thêm một
bước và lộ ra module kế — người sửa tưởng mình đang đập chuột chũi, còn sự thật là **chưa bao giờ
nhìn thấy toàn cảnh**. Nguy hiểm hơn cả sự phiền toái: nếu `core` tình cờ sạch từ đầu, ta đã tưởng
cả dự án sạch trong khi năm module chưa ai quét.

Thay bằng `aggregate` — soi dependency của gốc **và mọi module con** trong một lượt, áp ngưỡng một
lần, ra **một** danh sách đầy đủ và **một** báo cáo:

```bash
./mvnw -P security package -DskipTests          # aggregate cần jar liên module
./mvnw -P security dependency-check:aggregate
```

Plugin khai `inherited=false` để module con không tự chạy lại. Kiểm chứng: log chỉ có **một** dòng
`--- dependency-check:12.1.3:aggregate (default-cli) @ songnhue-backend ---`.

> Bài học chung, không riêng gì OWASP: **một phép kiểm dừng ở lỗi đầu tiên thì số lượt lặp bằng số
> lỗi, và mỗi lượt lại giấu đi phần còn lại.** Với phép kiểm chạy 20 phút và có người ngồi đợi, đó là
> khác biệt giữa "sửa một lần" và "sửa năm lần mà vẫn không biết còn bao nhiêu".

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

`deploy-staging.yml` chạy khi push vào `staging` (tức ngay sau khi merge PR từ `dev`). Nó chỉ làm
**một** việc riêng của staging — giải xem commit nào trên `dev` đã dựng ra mã này — rồi gọi
`deploy.yml`.

### 4.0. ⭐ Một thân chung cho cả hai môi trường (25/8)

`deploy.yml` là `workflow_call`: đăng nhập GHCR → tra ba image → rsync cấu hình → `pg_dump` →
`pull` + `migrator` + `up -d` → smoke test → gắn tag môi trường → tóm tắt. Hai tệp `deploy-staging`
và `deploy-prod` chỉ còn phần **khác nhau thật sự**: cổng chặn đầu vào, tệp compose, và bộ secret
máy chủ.

Vì sao gộp: staging chỉ có giá trị khi nó **giống** production. Trước khi gộp, hai tệp dài 333 +
277 dòng và trùng nhau ~85%; ba lần sửa gần nhất đều phải sửa hai chỗ, và §10.44 chỉ được sửa ở
**một** chỗ trong bản đầu. Cùng lý do `compose.staging.yml` chỉ `include` `compose.prod.yml`.

⛔ Bộ secret **truyền vào từ caller**, không đọc trực tiếp trong thân chung. Nên đường staging không
chạm nổi secret `PROD_*` dù có gõ nhầm input.

⚠ Reusable workflow **không tự cấp quyền cho mình được** — token của nó bị chặn trên bởi quyền của
job gọi. Khai `packages: write` ở thân chung là chưa đủ; job `uses:` ở cả hai caller phải khai lại.
`GhcrLookupAuthTest` canh chỗ này.

### 4.1. ⚠ Commit nào trên `dev` đã dựng ra mã đang nằm trên `staging`

Trả lời bằng **cây tệp**, không bằng quan hệ cha–con. `github.sha` trên `staging` là commit merge
vừa tạo, không phải commit của `dev`; và với một PR bị **squash** thì không có cha thứ hai nào để
lần theo — cha thứ nhất lại là tổ tiên chung cổ lỗ, một commit từ thời chưa có mã (§10.42).

Cách làm: so `HEAD^{tree}` của staging với cây của từng commit trên `dev` (thử `merge-base` trước
cho nhanh, rồi quét ngược tối đa 200 commit). Trùng khít nghĩa là **cùng nội dung**, bất kể ai bấm
nút merge nào. Không tìm thấy thì **dừng hẳn** — deploy một image không tương ứng với mã đang chạy
là cách chắc chắn nhất để có một môi trường không ai giải thích được.

Đây cũng là lý do `required_linear_history` bị **tắt** ở staging/production
(`branch-protection.md` §2.3).

### 4.1-b. Smoke test hỏi ba câu, không phải một (25/8)

Lượt deploy 24/8 **xanh trọn vẹn** trong khi cổng không có một bài nào (§10.45): câu hỏi duy nhất
lúc ấy là `/api/v1/public/site-config`, và nó không phân biệt được cổng có nội dung với cổng rỗng.
Nay:

1. `/api/v1/public/site-config` trả envelope `success` — đi hết chặng nginx → public-web → Route
   Handler → app → postgres;
2. `/api/v1/public/articles` có **ít nhất `so_bai_toi_thieu` bài** — cổng có nội dung. Ngưỡng là
   một **input của môi trường**, không phải một hằng số: staging đặt **9** (4 trang tĩnh của
   `V202608191021` + 5 bài seed), production đặt **1**. Với ngưỡng 1 thì quên thêm `SEED_LOCATION`
   vào `/opt/songnhue/.env` — tệp **không** được rsync — vẫn cho ra một lượt deploy xanh trên một
   cổng thiếu nội dung, đúng §10.45;
3. `/api/v1/public/files/<ảnh bìa lấy từ chính phản hồi câu 2>` trả `image/*` — **MinIO có byte**.
   Đây là phép kiểm duy nhất chứng minh được điều đó: hàng trong CSDL và byte trong kho là hai hệ
   thống khác nhau, lệch nhau là hỏng câm.
   ⚠ Lấy id từ phản hồi chứ **không ghi cứng** id của bộ seed — id ấy cố ý không tồn tại ở
   production, và ghi cứng nó là biến mọi lượt deploy production thành đỏ vì đúng cái mà thiết kế
   yêu cầu phải vắng mặt. Không bài nào có ảnh bìa thì in **⚠ BỎ QUA**, không in ✓.

Câu 2 và 3 chỉ có nghĩa vì bộ seed nội dung nay nằm trong chuỗi migration — xem `deploy/seed/README.md`.

### 4.1-c. Triển khai theo DIGEST, và có đường quay lui (25/8)

**Digest, không phải tag.** Bước xác định image giải `:<sha>` thành `@sha256:…` rồi triển khai bằng
digest. Tag là một cái tên và tên thì gán lại được; giữa lúc workflow tra và lúc máy chủ `pull`, một
lượt CI khác có thể đã trỏ tag đi nơi khác. Hiếm — nhưng hậu quả là một môi trường chạy thứ không ai
truy ra được, đúng loại lỗi không điều tra được sau đó.

**Quay lui tự động khi smoke test đỏ.** Trước khi đổi, workflow hỏi máy chủ *cái gì đang chạy*
(`docker inspect` trên container, không đọc tệp compose) và ghi lại ba digest. Smoke test đỏ →
dựng lại ba image cũ → hỏi lại câu 1.

⛔ **Đây là quay lui về MÃ NGUỒN, không phải về DỮ LIỆU.** `migrator` đã chạy xong trước đó và
migration là một chiều: nếu nó đã đổi lược đồ thì mã cũ có thể không chạy nổi trên lược đồ mới, và
bước quay lui **không cứu được gì**. Đường quay lui về dữ liệu là bản chụp `predeploy-*` sinh ra ở
đầu lượt — `docs/runbook/khoi-phuc-du-lieu.md`. Giới hạn này ghi ngay trong workflow, ở chỗ người ta
đọc lúc hoảng, để "đã có rollback tự động" không thành một lời trấn an sai.

⚠ Lượt deploy đầu tiên chưa có container nào → không có đích quay lui. Workflow nói ra điều đó bằng
một cảnh báo, thay vì dựng lại từ một chuỗi rỗng.

### 4.1-d. `make rehearse` — diễn tập trước khi merge vào `staging`

Bảy sự cố §10.42 → §10.49 đều nằm trên đường triển khai, và **`make ci-local` không đụng tới đường
ấy**: không compose, không `minio-init`, không thứ tự khởi động. Nên "xanh ở máy" chưa bao giờ nói
gì về chúng.

`make rehearse` chạy **đúng `compose.staging.yml`** và **đúng lệnh CD gõ** (`run --rm migrator`),
rồi hỏi ba câu mà chỉ hệ thật trả lời được: bucket có được tạo không · migration seed ghi đủ hàng
không · **mỗi `storage_key` trong CSDL có byte thật trong MinIO không**. Câu thứ ba không bài kiểm
JUnit nào trả lời được.

⛔ Nó **không** nói gì về nginx biên · TLS · tên miền · CSP · quyền thư mục trên máy chủ · hai image
giao diện · hành vi dưới tải. Danh sách đầy đủ kèm lý do nằm trong `deploy/compose.rehearse.yml` —
đọc trước khi tin vào một lượt diễn tập xanh.

### 4.2. ⚠⚠ Một image chạy hai môi trường — chỗ nguyên tắc §2 va vào Next.js

Next nướng mọi biến `NEXT_PUBLIC_*` vào bundle **lúc build**. `NEXT_PUBLIC_SITE_URL` là gốc của
`sitemap.xml`, `robots.txt`, thẻ canonical và ảnh Open Graph — mà staging và production có địa chỉ
khác nhau. Hai đòi hỏi không thể cùng đúng:

* *đóng gói một lần rồi đề bạt* → hai môi trường chạy **cùng** một image;
* *mỗi môi trường có URL riêng* → phải build **hai** image khác nhau.

Chốt: **giữ nguyên tắc §2**, build một image mang URL production, và chặn hậu quả ở chỗ nó thật sự
gây hại. Hậu quả duy nhất đáng lo là Google bò vào staging, thấy một bản sao toàn bộ nội dung tự
khai canonical trỏ về production — đúng hình dạng nội dung trùng lặp, và cổng thật là bên chịu
thiệt. Nginx biên của staging vì thế trả `X-Robots-Tag: noindex, nofollow` (biến `ROBOTS_TAG`
trong `.env`), chặn ngay trước khi trang nào kịp được đọc.

Đây là cách chữa **triệu chứng**, và ghi ra như vậy để không ai tưởng đã xong. Cách chữa gốc là cho
`SITE_URL` đọc lúc chạy thay vì lúc build — `frontend/public-web/src/lib/site.ts` chỉ được các tệp
phía máy chủ dùng (`layout.tsx`, `sitemap.ts`, `robots.ts`), nên đổi được mà không chạm bundle của
trình duyệt. Xem `docs/deploy-guideline.md` §9.3.

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

   ⭐ Từ 25/8 bước này chạy ở **cả staging**. Trước đó chỉ production có, và điều đó sai ở hai vế:
   migration được thử LẦN ĐẦU ở staging nên staging mới là nơi dễ mất dữ liệu nhất; và một đường sao
   lưu chỉ được đi thử đúng vào lúc production cần tới nó là một đường **chưa từng được thử**.

## 6. Cổng đề bạt

`promotion-guard.yml` là check bắt buộc **duy nhất** của `staging` và `production`. Chạy ~5 giây,
kiểm hai điều:

- Nhánh nguồn đúng chặng trước (`dev` → staging, `staging` → production). GitHub **không có** tuỳ
  chọn "chỉ nhận PR từ nhánh X" — thiếu job này thì ai cũng mở được PR từ một nhánh feature thẳng
  vào production, và không check nặng nào chặn lại vì ta đã cố ý không yêu cầu chúng ở đó.
- **Đúng commit đang đề bạt** đã xanh CI, tra qua API check-runs của chính SHA đó — không phải
  "nhánh `dev` nói chung đang xanh". `dev` hoàn toàn có thể vừa nhận một commit đỏ.

## 7. Secret cần đặt (WS-11)

| Secret | Đặt ở | Dùng ở | Ghi chú |
|---|---|---|---|
| `STAGING_HOST` · `STAGING_USER` · `STAGING_SSH_KEY` · `STAGING_BASE_URL` · **`STAGING_SSH_KNOWN_HOSTS`** | environment `staging` | CD Staging | ⬜ đủ 4 (đo 26/8) — **cần đặt thêm cái thứ NĂM** (29/8). Thiếu **cả năm** → cảnh báo và bỏ qua; thiếu **một số** → đỏ |
| `PROD_HOST` · `PROD_USER` · `PROD_SSH_KEY` · `PROD_BASE_URL` · **`PROD_SSH_KNOWN_HOSTS`** | environment `production` | CD Production | ⛔ **chưa có cái nào** (đo 26/8). Thiếu ở production → lượt chạy **DỪNG ĐỎ**, không bỏ qua |

⭐ **`*_SSH_KNOWN_HOSTS` vào bộ ngày 29/8** (§10.68-C). Giá trị là **một dòng `known_hosts`** — `<host> ssh-ed25519 AAAA…`, lấy bằng `cat /etc/ssh/ssh_host_ed25519_key.pub` **trên máy chủ**, thay phần cuối `root@…` bằng địa chỉ đứng ở `*_HOST`. Trước đó workflow tự dò khoá bằng `ssh-keyscan`, và chính lượt dò ấy — 5 kết nối đóng trước xác thực — làm fail2ban của máy chủ **cấm IP runner ngay ở lệnh đầu tiên**. Ghim khoá vừa gỡ nguyên nhân, vừa đổi *tin-lần-đầu-mỗi-lượt* thành xác minh thật.
| `NVD_API_KEY` | **repo** | `security-scan.yml` | ✅ **Đã đặt 18/8**. **Thiếu thì bỏ qua hẳn phép quét OWASP** (có cảnh báo trong Job Summary). Xin miễn phí ~2 phút: <https://nvd.nist.gov/developers/request-an-api-key> |

**Biến (Variables, không phải Secret) — đặt ở cấp repo:**

| Biến | Dùng ở | Ghi chú |
|---|---|---|
| `PUBLIC_SITE_URL` | job `Đóng gói image frontend` | Địa chỉ **production** của cổng công khai, ví dụ `https://songnhue.vn`. Nướng vào bundle lúc build — xem §4.2. Thiếu thì sitemap/canonical trỏ về `localhost`, và Job Summary nói to điều đó |

> ⚠ Đây là **biến**, không phải bí mật: nó đi vào bundle mà cả thế giới tải về được. Để nhầm vào
> Secrets thì vẫn chạy, nhưng nó sẽ bị che trong log — và che một giá trị công khai chỉ làm việc
> gỡ lỗi khó hơn mà không thêm an toàn nào.

> ⚠ **Đặt đúng cấp, không chỉ đúng tên.** Lần đầu `NVD_API_KEY` được đặt vào **environment
> `staging`** và phép quét vẫn bị bỏ qua: environment secret chỉ đến được job có khai
> `environment:`, mà `security-scan.yml` không khai — `secrets.NVD_API_KEY` giải ra chuỗi rỗng,
> **không có lỗi nào**, chỉ là bước "Kiểm khoá NVD" báo thiếu. Quy tắc: **khoá của công cụ CI đặt ở
> cấp repo; chỉ khoá gắn với một môi trường triển khai mới đặt ở environment**. Đặt nhầm vào
> environment có luật chờ duyệt còn tệ hơn — lượt quét đêm sẽ nằm chờ người bấm.

### 7.1. ⚠ Thiếu secret: staging bỏ qua, production DỪNG ĐỎ

Cùng một cổng (`.github/scripts/kiem-secret-may-chu.sh`), ba trạng thái:

| tình trạng | staging | production |
|---|---|---|
| đủ bốn | đi tiếp | đi tiếp |
| thiếu **một số** | ⛔ đỏ | ⛔ đỏ |
| thiếu **cả bốn** | cảnh báo + bỏ qua | ⛔ **đỏ** |

Vì sao lệch nhau: CD Staging chạy **tự động** sau mỗi lượt merge, nên một môi trường chưa dựng mà
nhuộm đỏ cả dòng CI của mọi người là đổi một lỗi thật lấy một lỗi phiền. CD Production chỉ chạy khi
**có người bấm** — và người ấy đang chờ biết đã deploy được hay chưa; im lặng bỏ qua là câu trả lời
sai nhất.

⛔ Bản trước chỉ hỏi **một** biến (`HOST`) và luôn bỏ qua trong im lặng. Với environment
`production` rỗng — đúng trạng thái đo được 26/8 — một lượt CD Production sẽ **xanh trọn vẹn** mà
không byte nào chạm máy chủ. `SecretGateTest` (7 bài) chạy thật script với từng tổ hợp.

`GITHUB_TOKEN` có sẵn, dùng để đẩy/kéo image trên GHCR. ✅ **Kiểm chứng 18/8**: job `Đóng gói image`
đẩy được lên GHCR ở lượt push đầu tiên vào `dev`, dù repo đặt `default_workflow_permissions: read` —
`permissions: packages: write` khai tường minh ở job ghi đè được mặc định đó.

## 8. Việc còn phải làm

⛔ **Không liệt kê ở đây.** `conventions.md` §6 chốt `.claude/master-tracking.md` là **nguồn duy
nhất** của task và nợ — và bản trước của mục này đã chứng minh vì sao: nó giữ **5 khoản nợ chưa
từng có trong sổ**, gồm cả *"diễn tập khôi phục trước go-live"* và *"bật Dependency graph"*. Một
danh sách nợ trong văn xuôi là một danh sách không ai đối soát.

Nợ của WS-11 nay ở `master-tracking.md`: `T11.2` (dựng 2 VPS) · `T11.3-b` (dựng lại cluster staging
sai collation) · `T11.7` (secret production §7) · `T11.7-a` (biến `PUBLIC_SITE_URL`) · `T11.35`–`T11.38`
(quyền thư mục host · `docker login` · healthcheck nginx · hai đường seed) · `T7.13` (diễn tập khôi
phục, ghi RTO thật).

✅ **Đóng 26/8**: `T11.32` Dependency graph (đã bật từ trước, sổ ghi sai) · `T11.39` nợ #27 (bảo vệ
nhánh) · `T22.23` nợ #46 (context đóng gói image) · `T11.40` secret scanning + push protection +
Dependabot.

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
