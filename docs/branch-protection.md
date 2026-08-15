# Bảo vệ nhánh — T10.7

> **Trạng thái**: chưa áp dụng. Cần người có quyền **admin** trên repo `team-dev-qnt/songnhue`
> chạy các lệnh ở mục 4. Ghi nhận ở `.claude/phase0-tracking.md` (sổ nợ mục 23).
>
> **Chưa có nhánh `staging` và `production`** — repo hiện có `common`, `dev`, `master`. Phải quyết
> định đổi tên `master` hay tạo mới trước khi chạy lệnh.

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

## 3. Ba hồ sơ

| | `dev` | `staging` | `production` |
|---|---|---|---|
| Check bắt buộc | `Backend — build, lint, test`, `Frontend — lint` | `Promotion guard` | `Promotion guard` |
| Nguồn hợp lệ | nhánh feature bất kỳ | chỉ `dev` | chỉ `staging` |
| Số người duyệt | 1 | 1 | 1 + môi trường có approval |
| `strict` (bắt cập nhật với base) | ✅ | ✅ | ✅ |
| `required_linear_history` | ✅ | ❌ — xem §2.3 | ❌ |
| Force push / xoá nhánh | cấm | cấm | cấm |
| `enforce_admins` | false | false | **bật khi đội ≥ 2 người** |

## 4. Lệnh áp dụng

Yêu cầu: `gh` đã đăng nhập bằng tài khoản có quyền admin trên repo.

### 4.1. `dev` — nơi mọi thứ được kiểm

```bash
gh api -X PUT repos/team-dev-qnt/songnhue/branches/dev/protection --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Backend — build, lint, test", "Frontend — lint"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_last_push_approval": true
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
    "strict": true,
    "contexts": ["Promotion guard"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_last_push_approval": true
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

## 5. Vì sao chọn từng mục

| Cấu hình | Lý do |
|---|---|
| `strict: true` | PR phải cập nhật với base mới merge được. Không có nó thì hai PR đều xanh riêng lẻ vẫn hợp lại thành nhánh đỏ — kiểu hỏng chỉ lộ ra sau khi đã merge |
| `contexts` | Đúng **tên job** trong workflow. ⚠ Đổi tên job mà quên sửa ở đây thì rơi vào bẫy §2.1, hoặc tệ hơn: không còn check bắt buộc nào và mọi PR merge được ngay |
| `required_approving_review_count: 1` | `conventions.md` §1.5 |
| `dismiss_stale_reviews` | Đẩy thêm commit sau khi được duyệt thì phải duyệt lại — nếu không, "đã duyệt" nói về một đoạn mã không còn tồn tại |
| `require_last_push_approval` | Người tự đẩy commit cuối không được tự tính là người duyệt |
| `required_linear_history` (chỉ `dev`) | Lịch sử thẳng để `git bisect` dùng được. Với hệ có nhiều loại lỗi âm thầm (xem sổ nợ), khả năng tìm ra commit gây lỗi là thứ đáng giữ |
| `allow_force_pushes: false` | Force push vào nhánh chính xoá lịch sử của người khác |
| `required_conversation_resolution` | Nhận xét trong review phải được xử lý, không trôi qua khi merge |
| `enforce_admins: false` | Có chủ đích. Đội hiện có một người; khoá cả admin là tự nhốt mình ngoài cửa khi cần xử lý sự cố gấp. **Bật cho `production` khi đội ≥ 2 người** |

## 6. Kiểm chứng sau khi áp dụng

```bash
# Cả ba phải trả về JSON, không phải 404
for b in dev staging production; do
  echo "— $b"
  gh api "repos/team-dev-qnt/songnhue/branches/$b/protection" --jq '.required_status_checks.contexts'
done

# Phải bị từ chối
git push origin dev

# Mở thử một PR từ nhánh feature thẳng vào production → Promotion guard phải đỏ
```

## 7. Việc còn phải làm khớp với luồng này

- `.claude/phase0-tracking.md` **T11.8** đang ghi *"deploy-staging.yml tự động khi merge `master`"* —
  viết theo mô hình 2 nhánh cũ. Phải sửa thành: merge vào `staging` → deploy Staging; merge vào
  `production` → deploy Production (có approval).
- `ci.yml` cố ý chỉ trigger ở `dev` (xem ghi chú đầu file). Đổi điều đó là phá luồng "kiểm một lần".
