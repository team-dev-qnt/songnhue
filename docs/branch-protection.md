# Bảo vệ nhánh — T10.7

> **Trạng thái**: ✅ **đã áp dụng 15/8/2026** trên `team-dev-qnt/songnhue`. Ba nhánh `dev`,
> `staging`, `production` đều có bảo vệ; environment `production` đã có người duyệt.
> Kết quả kiểm chứng từng mục: **§6**.
>
> ⚠ **Bản đầu của tài liệu này có hai lỗi, đã sửa ở §4** — cấu hình áp theo bản cũ vẫn còn hai chỗ
> phải chỉnh: `strict` ở staging/production (§2.4) và thiếu một context ở `dev` (§2.5). Lệnh sửa
> nằm ở **§6.2**.
>
> **`master` nằm ngoài luồng** — nhánh riêng của chủ repo, không workflow nào chạm tới, không đặt
> bảo vệ. Đã xác nhận: `master` trả 404 "Branch not protected", đúng ý định.

## 1. Vì sao đây là mục duy nhất của WS-10 không nằm trong mã nguồn

Mọi cổng chất lượng khác — định dạng, quy ước, luật kiến trúc, ma trận phân quyền, chuỗi hash, bao
phủ mã — đều là tệp trong repo: ai clone về cũng có, ai sửa cũng để lại vết trong lịch sử commit.
Bảo vệ nhánh thì không: nó là **cấu hình phía GitHub**, sống ngoài repo, không có phiên bản, và tắt
đi thì **không sinh ra bất kỳ dấu vết nào** trong mã nguồn.

Hệ quả thực tế: CI có thể xanh rực rỡ trong khi vẫn có người push thẳng vào `production`. Tài liệu
này tồn tại để trạng thái đó **được nhìn thấy** thay vì được giả định.

## 2. Luồng đã chốt: kiểm một lần ở `dev`, hai chặng sau chỉ xác minh

```
nhánh feature ──PR──► dev ──PR──► staging ──PR──► production
                       ▲            ▲                ▲
              toàn bộ kiểm tra   promotion        promotion
              nặng chạy ở đây     guard            guard
```

Nguyên tắc: **mọi kiểm tra nặng diễn ra đúng một lần, ở `dev`.** Chạy lại cùng bộ kiểm tra trên
cùng một mã nguồn ở hai chặng sau tốn thêm vài phút mỗi lần đề bạt mà không phát hiện thêm được gì.

Vì vậy ba nhánh **không dùng chung cấu hình**. Chúng khác nhau ở đúng chỗ: `dev` yêu cầu kết quả
kiểm tra, `staging`/`production` yêu cầu bằng chứng rằng thứ đang lên là thứ đã được kiểm.

### 2.1. ⚠ Bẫy: check bắt buộc mà không bao giờ chạy

Required status check gắn theo **tên job**, và GitHub chờ đúng tên đó xuất hiện trên commit. Nếu để
`"Backend — build, lint, test"` là bắt buộc trên `staging` nhưng `ci.yml` chỉ trigger cho `dev`, thì
PR `dev → staging` đứng mãi ở *"Expected — Waiting for status to be reported"*. Không có thông báo
lỗi nào, chỉ là nút merge xám vĩnh viễn.

Vì vậy: bỏ check nặng khỏi staging/production nghĩa là **không liệt kê chúng trong `contexts`**,
không phải tắt job.

### 2.2. ⚠ Vì sao vẫn phải có đúng một check ở hai chặng sau

Bỏ trắng `contexts` thì không gì ngăn được người mở PR từ một nhánh feature bất kỳ thẳng vào
`production`. **GitHub không có tuỳ chọn "chỉ nhận PR từ nhánh X"** — đó là lỗ mà branch protection
không bịt được.

`.github/workflows/promotion-guard.yml` bịt lỗ đó bằng một job ~5 giây, làm hai việc:

1. Nhánh nguồn đúng chặng trước (`dev` → staging, `staging` → production).
2. **Đúng commit đang đề bạt** đã xanh CI ở chặng trước — hỏi API check-runs của chính SHA đó, chứ
   không phải "nhánh dev nói chung đang xanh". `dev` hoàn toàn có thể vừa nhận một commit đỏ.

### 2.3. ⚠ `required_linear_history` chỉ bật ở `dev`

Nghe ngược đời, nhưng: chặng đề bạt cần **merge commit thật** để SHA của `dev` nằm nguyên trong
`staging`, nhờ đó câu "commit này đã xanh ở dev" mới kiểm chứng được. Squash sinh SHA mới và cắt đứt
liên kết ấy. Mà `required_linear_history: true` lại **cấm merge commit** — hai thứ không đi cùng
nhau được.

Nên: `dev` giữ lịch sử thẳng (nhánh feature rebase/squash vào); `staging`/`production` cho phép
merge commit.

### 2.4. ⚠ `strict` phải TẮT ở staging/production — nếu không, chặng đề bạt tự khoá sau lần đầu

Đây là **lỗi trong bản đầu của tài liệu này**, phát hiện khi kiểm chứng lại cấu hình đã áp.

`strict: true` nghĩa là "nhánh nguồn phải cập nhật với nhánh đích mới được merge". Ở `dev` điều đó
đúng và cần thiết. Ở hai chặng đề bạt nó tạo ra một **vòng khoá không lối ra**:

1. Merge PR `dev → staging` lần đầu → `staging` sinh một **merge commit** không có trong `dev`.
2. Lần đề bạt kế tiếp, GitHub thấy `dev` **thiếu** commit đó → báo *out of date*, chặn merge, hiện
   nút **Update branch**.
3. Nút đó có hai chế độ, và **cả hai đều bị chính bảo vệ của `dev` chặn**:
   - *Update with merge commit* → tạo merge commit trên `dev` → vi phạm `required_linear_history`.
   - *Update with rebase* → viết lại lịch sử `dev` → cần force push → vi phạm `allow_force_pushes: false`.

Không còn đường hợp lệ nào. Merge ngược `staging → dev` bằng squash cũng không cứu được: nội dung
hai nhánh y hệt nhau nên diff rỗng, GitHub từ chối mở PR.

Mà `strict` ở đây **không mua được gì**. Nó sinh ra để chặn tình huống "hai PR xanh riêng lẻ hợp lại
thành nhánh đỏ" — tức là khi có *hợp nhất* thật sự. Chặng đề bạt không hợp nhất gì cả: nó chuyển
giao đúng một commit đã xanh, và `Promotion guard` đã xác minh chính SHA đó. Đổi một thứ vô ích lấy
một vòng khoá là lỗ rõ ràng.

→ **`strict: false` ở `staging` và `production`.** Giữ `true` ở `dev`.

### 2.5. ⚠ Job `Vùng nào thay đổi` phải nằm trong danh sách bắt buộc

Cũng là lỗi bản đầu, và thuộc đúng loại "xanh mà không chạy" mà `conventions.md` §1.5 sinh ra để
chặn.

`ci.yml` lọc theo đường dẫn bằng cách cho job `changes` quyết định, rồi hai job nặng khai
`if: needs.changes.outputs.backend == 'true'`. Branch protection **tính job bị skip là đạt** — đó
chính là điều làm cơ chế lọc hoạt động được (§2.1).

Nhưng job bị skip **vì job phụ thuộc hỏng** cũng được tính là đạt y như vậy. Nên nếu `changes` chết
— `git diff` lỗi, checkout hỏng, runner timeout — thì:

`changes` đỏ → `backend` và `frontend` bị skip → GitHub coi hai check bắt buộc là **đã đạt** →
**PR merge được trong khi không một bài kiểm nào từng chạy.**

`changes` không nằm trong `contexts` nên cái đỏ duy nhất ấy không chặn gì. Kết quả: toàn bộ nguyên
tắc "kiểm một lần ở `dev`" bị vô hiệu hoá bởi một lỗi hạ tầng vặt, không dòng cảnh báo nào.

→ Thêm **`Vùng nào thay đổi`** vào `contexts` của `dev`. Job này quyết định mọi job khác có chạy
hay không, nên nó phải là thứ bắt buộc phải xanh.

### 2.6. ~~Một người thì `required_approving_review_count: 1` là cấm merge~~ — ĐÃ HẾT HIỆU LỰC (18/8)

> **Bản 15/8 nhận định sai vì đếm thiếu collaborator.** Lúc đó tôi đọc ra đúng một tài khoản
> (`Toclac18`) và kết luận phải hạ `required_approving_review_count` xuống 0. Thực tế repo có
> **hai** collaborator quyền admin — `Toclac18` và `quannt18` — và **PR #1 đã merge bình thường
> với `reviews: 1`**: `Toclac18` mở, `quannt18` duyệt, không phải bấm bypass lần nào.
>
> → **Giữ nguyên `required_approving_review_count: 1` ở cả ba nhánh.** Không cần sửa gì.

Lập luận gốc vẫn đúng và vẫn đáng giữ, chỉ là tiền đề không còn: nếu số lượt duyệt bắt buộc lớn hơn
số người duyệt được, thì đường merge duy nhất là nút **"Merge without waiting for requirements to be
met"** — và **nút ấy bỏ qua tất cả, gồm cả status check**. Biến "1 người duyệt" thành thứ trang trí
đã đành; nặng hơn là nó tập thói quen bấm bypass ở *mọi* lần merge. Nên luật chung là: **số lượt
duyệt bắt buộc không bao giờ được vượt số người có thể duyệt**, chứ không phải "càng chặt càng tốt".

⚠ Một điều cần nói thẳng để không tự huyễn hoặc: hai tài khoản này thuộc **cùng một người**. Luật
duyệt vì thế đang là **thủ tục**, không phải một cặp mắt thứ hai — nó buộc PR đi qua giao diện review
chứ không bảo đảm có người khác thật sự đọc mã. Giá trị thật của nó chỉ xuất hiện khi đội có người
thứ hai.

## 3. Ba hồ sơ

| | `dev` | `staging` | `production` |
|---|---|---|---|
| Check bắt buộc | `Vùng nào thay đổi`, `Backend — build, lint, test`, `Frontend — lint` | `Promotion guard` | `Promotion guard` |
| Nguồn hợp lệ | nhánh feature bất kỳ | chỉ `dev` | chỉ `staging` |
| Số người duyệt | 0 khi đội 1 người — xem §2.6 | 0 | 0 + môi trường có approval |
| `strict` (bắt cập nhật với base) | ✅ | ❌ — xem §2.4 | ❌ — xem §2.4 |
| `required_linear_history` | ✅ | ❌ — xem §2.3 | ❌ |
| Cách merge | Squash / Rebase | **Create a merge commit** — xem §3.2 | **Create a merge commit** |
| Force push / xoá nhánh | cấm | cấm | cấm |
| `enforce_admins` | false | false | **bật khi đội ≥ 2 người** |

### 3.1. Các mục đang nới vì đội thực chất là 1 người — bật cùng lúc khi có người thứ hai

Những mục dưới đây **không phải sơ suất**. Ghi thành một chỗ để khi có người thứ hai thì bật một
lượt, không phải đi tìm.

| Mục | Đang | Đổi thành | Vì sao đang nới |
|---|---|---|---|
| ~~`required_approving_review_count`~~ | **1** | — | ✅ **Không còn trong danh sách này (18/8)** — hai tài khoản admin nên `1` chạy được thật, xem §2.6 |
| `require_last_push_approval` | false | true | Cùng một người vừa đẩy vừa duyệt thì luật này chỉ tạo thêm bước bấm |
| `dismiss_stale_reviews` | false | true | Như trên |
| `enforce_admins` (`production`) | false | true | Một người thì khoá cả admin là mất đường xử lý sự cố |
| `prevent_self_review` (environment `production`) | false | true | Người bấm deploy cũng là người duy nhất duyệt được |

### 3.2. ⚠ PR đề bạt phải merge bằng "Create a merge commit"

`deploy-staging.yml` tìm image theo `HEAD^2` — cha thứ hai của merge commit chính là đỉnh `dev` lúc
merge, và image được gắn tag theo SHA đó (`docs/cicd.md` §4.1). Squash hoặc rebase sinh SHA mới,
không có cha thứ hai, và mối liên hệ với image đã kiểm bị cắt.

Repo bật cả ba kiểu merge (cần Squash cho nhánh feature vào `dev`), và GitHub **không cho giới hạn
kiểu merge theo từng nhánh** — nên đây là quy ước người dùng phải giữ, không phải thứ cấu hình chặn
được. Bù lại, hỏng ở đây **hỏng to tiếng**: workflow không tìm thấy image thì dừng ngay với thông
báo *"commit này chưa từng qua CI của dev"*, không có bản deploy nửa vời nào lên staging.

## 4. Lệnh áp dụng

Yêu cầu: `gh` đã đăng nhập bằng tài khoản có quyền admin trên repo.

> Đây là **bản đã sửa** (§2.4, §2.5, §2.6). Cấu hình đang chạy trên repo áp theo bản cũ — chạy lại
> hai khối này là khớp.

> ⚠⚠ **Ba context mới thêm 24/8** (`architecture-review.md` §10.38): hai job đóng gói image nay
> chạy ở cả PR, nhưng chưa nằm trong danh sách bắt buộc thì chúng chỉ *hiện* lỗi chứ không *chặn*
> merge — mà chính lỗ ấy đã để PR #10 merge vào rồi mới làm `dev` đỏ.
>
> ⚠ Tên context của một job **ma trận** bao gồm cả giá trị ma trận trong ngoặc, đúng từng ký tự.
> Chép sai một dấu phẩy thì GitHub coi đó là một check **không bao giờ xuất hiện** và PR treo mãi ở
> "Expected". Tra tên thật bằng: `gh run view <run-id> --json jobs --jq '.jobs[].name'`.

### 4.1. `dev` — nơi mọi thứ được kiểm

```bash
gh api -X PUT repos/team-dev-qnt/songnhue/branches/dev/protection --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Vùng nào thay đổi", "Backend — build, lint, test", "Frontend — lint", "Đóng gói image", "Đóng gói image frontend (admin-app, deploy/docker/admin-app.Dockerfile)", "Đóng gói image frontend (public-web, deploy/docker/public-web.Dockerfile)"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
```

### 4.2. `staging` và `production` — chỉ xác minh

```bash
for branch in staging production; do
  gh api -X PUT "repos/team-dev-qnt/songnhue/branches/$branch/protection" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["Promotion guard"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
done
```

### 4.3. Thêm cho `production`: môi trường có người duyệt

Branch protection không chặn được *thời điểm* deploy. Tạo một GitHub Environment tên `production`
với **required reviewer**, rồi `deploy-prod.yml` (WS-11/T11.8) khai `environment: production` —
mọi lượt deploy dừng lại chờ người bấm duyệt.

```bash
gh api -X PUT repos/team-dev-qnt/songnhue/environments/production --input - <<'JSON'
{ "reviewers": [{ "type": "User", "id": <USER_ID> }], "deployment_branch_policy": null }
JSON
```

Lấy `<USER_ID>`: `gh api user --jq .id`.

✅ **Đã tạo 15/8/2026** — reviewer `Toclac18`, `prevent_self_review: false`, `can_admins_bypass: true`.
Hai giá trị sau là **cố ý** khi đội có một người: người ra lệnh deploy cũng là người duy nhất duyệt
được, cấm tự duyệt thì không deploy được lần nào. Nằm trong cụm bật lại ở §3.1.

Environment `staging` (dùng bởi `deploy-staging.yml`) **chưa cần tạo tay** — GitHub tự tạo ở lượt
chạy đầu vì nó không có luật bảo vệ nào.

## 5. Vì sao chọn từng mục

| Cấu hình | Lý do |
|---|---|
| `strict: true` **chỉ ở `dev`** | PR phải cập nhật với base mới merge được. Không có nó thì hai PR đều xanh riêng lẻ vẫn hợp lại thành nhánh đỏ — kiểu hỏng chỉ lộ ra sau khi đã merge. ⚠ Ở staging/production thì ngược lại: nó tự khoá chặng đề bạt, xem §2.4 |
| `contexts` | Đúng **tên job** trong workflow, khớp từng ký tự (kể cả dấu `—`). ⚠ Đổi tên job mà quên sửa ở đây thì rơi vào bẫy §2.1, hoặc tệ hơn: không còn check bắt buộc nào và mọi PR merge được ngay |
| `Vùng nào thay đổi` trong `contexts` | Job này quyết định hai job nặng có chạy hay không. Nó hỏng mà không bắt buộc phải xanh thì cả hai bị skip và được **tính là đạt** — §2.5 |
| `required_approving_review_count: 1` (cả 3 nhánh) | Đúng `conventions.md` §1.5. Chạy được thật vì repo có **hai** collaborator admin — PR #1 merge 18/8 không cần bypass. Luật kèm theo: **số lượt duyệt bắt buộc không được vượt số người duyệt được**, nếu không thì đường merge duy nhất là bypass, mà bypass bỏ qua cả CI — §2.6 |
| `dismiss_stale_reviews` / `require_last_push_approval` | Đẩy thêm commit sau khi được duyệt thì phải duyệt lại, và người đẩy commit cuối không tự tính là người duyệt. Cả hai chỉ có nghĩa khi số người duyệt ≥ 1 — tắt cùng cụm §3.1 |
| `required_linear_history` (chỉ `dev`) | Lịch sử thẳng để `git bisect` dùng được. Với hệ có nhiều loại lỗi âm thầm (xem sổ nợ), khả năng tìm ra commit gây lỗi là thứ đáng giữ |
| `allow_force_pushes: false` | Force push vào nhánh chính xoá lịch sử của người khác |
| `required_conversation_resolution` | Nhận xét trong review phải được xử lý, không trôi qua khi merge |
| `enforce_admins: false` | Có chủ đích. Hai tài khoản admin nhưng **cùng một người**; khoá cả admin là tự nhốt mình ngoài cửa khi cần xử lý sự cố gấp. **Bật cho `production` khi có người thứ hai thật** |

## 6. Kiểm chứng — kết quả thật ngày 15/8/2026

Chạy lại bất cứ lúc nào:

```bash
for b in dev staging production; do
  echo "— $b"
  gh api "repos/team-dev-qnt/songnhue/branches/$b/protection" --jq \
    '{strict: .required_status_checks.strict,
      contexts: .required_status_checks.contexts,
      reviews: .required_pull_request_reviews.required_approving_review_count,
      linear: .required_linear_history.enabled,
      force_push: .allow_force_pushes.enabled}'
done
gh api repos/team-dev-qnt/songnhue/branches/master/protection   # phải 404 — cố ý
gh api repos/team-dev-qnt/songnhue/environments --jq '.environments[].name'
gh api repos/team-dev-qnt/songnhue/rulesets                     # phải [] — không có luật kiểu mới chồng lên
```

### 6.1. Đã đúng

| Mục | Kết quả |
|---|---|
| Ba nhánh có bảo vệ | ✅ `dev`, `staging`, `production` đều trả JSON |
| `contexts` khớp tên job từng ký tự | ✅ kể cả dấu `—` trong `Backend — build, lint, test` |
| `required_linear_history` | ✅ bật ở `dev`, tắt ở hai nhánh sau — đúng §2.3 |
| Force push / xoá nhánh | ✅ cấm ở cả ba |
| `required_conversation_resolution` | ✅ cả ba |
| `master` không bảo vệ | ✅ 404 — đúng ý định |
| Environment `production` có người duyệt | ✅ reviewer `Toclac18` |
| Nhánh mặc định của repo | ✅ `dev` |
| Không có ruleset kiểu mới chồng lên | ✅ `[]` |
| Actions đang bật | ✅ |

### 6.2. Còn phải chỉnh — lệnh sửa

Hai mục dưới đây do **lỗi bản đầu của tài liệu**, không phải do chạy sai. *(Bản 15/8 ghi ba mục;
mục thứ ba — hạ số người duyệt xuống 0 — đã bỏ ngày 18/8 vì tiền đề "đội một người" sai, xem §2.6.)*

```bash
# 1) Thêm context còn thiếu ở dev (§2.5)
gh api -X PATCH repos/team-dev-qnt/songnhue/branches/dev/protection/required_status_checks \
  -f strict=true \
  -f 'contexts[]=Vùng nào thay đổi' \
  -f 'contexts[]=Backend — build, lint, test' \
  -f 'contexts[]=Frontend — lint'

# 2) Tắt strict ở hai chặng đề bạt (§2.4)
for b in staging production; do
  gh api -X PATCH "repos/team-dev-qnt/songnhue/branches/$b/protection/required_status_checks" \
    -F strict=false -f 'contexts[]=Promotion guard'
done

# 3) Hạ số người duyệt xuống 0 khi đội còn 1 người (§2.6)
for b in dev staging production; do
  gh api -X PATCH "repos/team-dev-qnt/songnhue/branches/$b/protection/required_pull_request_reviews" \
    -F required_approving_review_count=0 \
    -F dismiss_stale_reviews=false \
    -F require_last_push_approval=false
done
```

> ⛔ **Đã bỏ — mục 3 của bản 15/8**: `required_approving_review_count=0`. Repo có hai collaborator
> admin nên `1` chạy được thật; PR #1 (18/8) merge không cần bypass. Đừng chạy lại đoạn đó.

### 6.3. ~~⚠ Mã nguồn chưa có trên `dev`~~ — ĐÃ XONG 18/8

> ✅ **Đóng 18/8/2026** — PR #1 (`common → dev`) đã merge bằng **Squash**, `dev` ở `f5c5ac4`.
> Kiểm chứng: `git diff origin/dev origin/common` **rỗng** — 443 tệp, có `.github/`. Giữ lại mục
> này vì phần "hai điều bắt buộc" bên dưới còn áp cho mọi PR feature → `dev` về sau.

Kiểm chứng 15/8 lộ ra một chuyện nằm ngoài cấu hình: **`dev` đang trống**. Toàn bộ Phase 0 — 22
commit, 431 tệp — nằm ở nhánh `common`, còn `dev`, `staging`, `production` cùng đứng ở `3c29f0c`
("Build the phase plan"), tức là chỉ có tài liệu.

Nghĩa là bảo vệ nhánh đang canh một nhánh rỗng, `.github/workflows/` chưa tồn tại trên `dev`, và số
lượt chạy workflow của repo tới giờ là **0**.

Đưa mã lên đúng luồng — PR `common → dev`:

```bash
gh pr create --base dev --head common \
  --title "feat: Phase 0 WS-1 → WS-6, WS-10" \
  --body "Toàn bộ nền tảng Phase 0. Chi tiết: .claude/phase0-tracking.md"
```

Hai điều bắt buộc ở PR này:

- **Merge bằng Squash hoặc Rebase, không dùng merge commit** — `dev` bật `required_linear_history`.
  (Ngược hẳn với PR đề bạt ở §3.2, nơi *bắt buộc* phải là merge commit.)
- Đây là lượt CI chạy thật đầu tiên. Với sự kiện `pull_request`, GitHub lấy workflow từ bản hợp nhất
  head vào base, nên `ci.yml` từ `common` vẫn chạy dù `dev` chưa có tệp đó — hai check bắt buộc sẽ
  xuất hiện bình thường.

### 6.4. Kiểm chứng còn lại — chỉ làm được sau khi có mã trên `dev`

```bash
git push origin dev            # phải bị từ chối
# Mở PR từ một nhánh feature thẳng vào production → Promotion guard phải đỏ
```

Ngoài ra ở lượt chạy đầu cần nhìn hai chỗ — **kết quả thật 18/8, lượt push vào `dev` sau khi merge
PR #1** (`gh run view 32145220919`):

| Job | Kết quả | Ghi chú |
|---|---|---|
| `Vùng nào thay đổi` | ✅ 6s | |
| `Backend — build, lint, test` | ✅ 1'49" | |
| `Frontend — lint` | ✅ 43s | Không còn skip — mã FE đã có từ WS-8/WS-9 |
| `Đóng gói image` | ✅ 1'48" | **Đẩy được lên GHCR** dù repo đặt `default_workflow_permissions: read` — `permissions: packages: write` khai ở job ghi đè được, đúng như dự đoán |
| `Soi phụ thuộc PR thêm vào` | ⏭ skipped | Đúng thiết kế: chỉ chạy ở `pull_request` |

## 7. Việc còn phải làm khớp với luồng này

- Toàn bộ luồng CI/CD và lý do từng quyết định: **`docs/cicd.md`**.
- `ci.yml` cố ý chỉ trigger ở `dev`. Đổi điều đó là phá luồng "kiểm một lần".
- `delete_branch_on_merge` để **false** — có chủ đích: PR đề bạt dùng nhánh nguồn sống lâu (`dev`,
  `staging`), không phải nhánh feature dùng xong bỏ.
