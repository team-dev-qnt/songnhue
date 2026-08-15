# Bảo vệ nhánh — T10.7

> **Trạng thái**: chưa áp dụng. Cần người có quyền **admin** trên repo
> `team-dev-qnt/songnhue` chạy lệnh ở mục 2. Ghi nhận trong `.claude/phase0-tracking.md`.

## 1. Vì sao đây là mục duy nhất của WS-10 không nằm trong mã nguồn

Mọi cổng chất lượng khác — định dạng, quy ước, luật kiến trúc, ma trận phân quyền, chuỗi hash,
bao phủ mã — đều là tệp trong repo: ai clone về cũng có, ai sửa cũng để lại vết trong lịch sử
commit. Bảo vệ nhánh thì không: nó là **cấu hình phía GitHub**, sống ngoài repo, không có phiên
bản, và tắt đi thì không sinh ra bất kỳ dấu vết nào trong mã nguồn.

Hệ quả thực tế: CI có thể xanh rực rỡ trong khi vẫn có người push thẳng vào `dev`. Tài liệu này
tồn tại để trạng thái đó **được nhìn thấy** thay vì được giả định.

## 2. Lệnh áp dụng

Yêu cầu: `gh` đã đăng nhập bằng tài khoản có quyền admin trên repo.

```bash
gh api -X PUT repos/team-dev-qnt/songnhue/branches/dev/protection \
  --input - <<'JSON'
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

Làm y hệt cho nhánh `master` (đổi `branches/dev` thành `branches/master`).

## 3. Vì sao chọn từng mục

| Cấu hình | Lý do |
|---|---|
| `strict: true` | PR phải cập nhật với `dev` mới merge được. Không có nó thì hai PR đều xanh riêng lẻ vẫn hợp lại thành nhánh đỏ — kiểu hỏng chỉ lộ ra sau khi đã merge |
| `contexts` | Đúng **tên job** trong `ci.yml`. ⚠ Đổi tên job mà quên sửa ở đây thì GitHub chờ một check không bao giờ tới, hoặc tệ hơn: không còn check bắt buộc nào và mọi PR merge được ngay |
| `required_approving_review_count: 1` | `conventions.md` §1.5 |
| `dismiss_stale_reviews` | Đẩy thêm commit sau khi được duyệt thì phải duyệt lại — nếu không, "đã duyệt" nói về một đoạn mã không còn tồn tại |
| `require_last_push_approval` | Người tự đẩy commit cuối không được tự tính là người duyệt |
| `required_linear_history` | Lịch sử thẳng để `git bisect` dùng được. Với hệ có loại lỗi âm thầm (xem sổ nợ), khả năng tìm ra commit gây lỗi là thứ đáng giữ |
| `allow_force_pushes: false` | Force push vào nhánh chính xoá lịch sử của người khác |
| `required_conversation_resolution` | Nhận xét trong review phải được xử lý, không trôi qua khi merge |
| `enforce_admins: false` | Có chủ đích. Đội hiện có một người; khoá cả admin là tự nhốt mình ngoài cửa khi cần xử lý sự cố gấp. **Bật lên khi đội ≥ 2 người** |

## 4. Kiểm chứng sau khi áp dụng

```bash
# Phải trả về JSON cấu hình, không phải 404
gh api repos/team-dev-qnt/songnhue/branches/dev/protection | jq '.required_status_checks.contexts'

# Phải bị từ chối
git push origin dev
```
