# Sự kiện bảo mật

> Cảnh báo `PhatHienDungLaiRefreshToken` · `TruyCapNgoaiPhamViDonVi` · `DangNhapSaiDonDap`.
>
> Nguồn: bảng `security_events` (append-only — `songnhue_app` **không có** `UPDATE`/`DELETE`) và
> bộ đếm `songnhue_security_events_total`. Danh mục loại sự kiện + mức nghiêm trọng:
> `SecurityEventType`.

## Tra nhanh

```sql
SELECT occurred_at, event_type, severity, username, ip_address,
       left(coalesce(detail::text, ''), 200) AS chi_tiet, trace_id
  FROM security_events
 WHERE occurred_at > now() - interval '2 hours'
   AND severity IN ('DANGER', 'CRITICAL')
 ORDER BY occurred_at DESC LIMIT 50;
```

`trace_id` nối sự kiện với dòng log của đúng request đó — bắt đầu điều tra từ đây.

---

## 1. `REFRESH_REUSE_DETECTED` — nghiêm trọng nhất

**Nghĩa là**: một refresh token đã dùng rồi lại được dùng lần nữa. Cơ chế xoay vòng chỉ cho dùng một
lần, nên chuyện này gần như luôn có nghĩa là **token đã bị đánh cắp**: kẻ tấn công dùng bản sao, chủ
thật dùng bản gốc (hoặc ngược lại).

Hệ thống **đã tự xử lý** ngay khi phát hiện: thu hồi cả family, access token chết ngay nhờ claim
`fid`, người dùng bị buộc đăng nhập lại. Việc của bạn là điều tra, không phải chặn.

```sql
-- IP nào, phiên nào
SELECT occurred_at, username, ip_address, user_agent, detail
  FROM security_events WHERE event_type = 'REFRESH_REUSE_DETECTED'
 ORDER BY occurred_at DESC LIMIT 20;

-- Tài khoản đó còn phiên nào đang mở
SELECT s.id, s.created_at, s.last_used_at, s.ip_address, s.device_label
  FROM sessions s JOIN users u ON u.id = s.user_id
 WHERE u.username = '<username>' AND s.revoked_at IS NULL;
```

**Làm gì**:
1. Liên hệ chủ tài khoản: có phải họ vừa đăng nhập từ IP đó không.
2. Không phải → thu hồi mọi phiên + bắt đổi mật khẩu:
   ```sql
   UPDATE sessions SET revoked_at = now(), revoke_reason = 'SECURITY_INCIDENT'
    WHERE user_id = (SELECT id FROM users WHERE username = '<username>') AND revoked_at IS NULL;
   UPDATE users SET must_change_password = TRUE WHERE username = '<username>';
   ```
3. Xem người đó đã làm gì trong `audit_logs` trong khoảng nghi vấn.
4. Lặp lại nhiều lần trên nhiều tài khoản → sự cố diện rộng: cân nhắc xoay khoá ký JWT
   ([xoay-khoa.md](xoay-khoa.md) mục B) để dứt điểm mọi token đang sống.

---

## 2. `ACCESS_DENIED_SCOPE` — truy cập ngoài phạm vi đơn vị

Tầng 3 phân quyền chặn (`AUTH-3002`). Vài lần rải rác = người dùng bấm nhầm link cũ. Dồn dập từ một
tài khoản = có người đang dò dữ liệu của Xí nghiệp khác.

```sql
SELECT username, ip_address, count(*), min(occurred_at), max(occurred_at)
  FROM security_events
 WHERE event_type = 'ACCESS_DENIED_SCOPE' AND occurred_at > now() - interval '1 day'
 GROUP BY 1, 2 ORDER BY 3 DESC;
```

> ⚠ Ghi nhớ khi đọc số liệu này: tầng 3 **chưa từng hoạt động** cho tới khi WS-10 sửa
> (`architecture-review.md` §9.8.1). Không có dòng nào trước 15/8/2026 không có nghĩa là không ai
> từng đọc dữ liệu ngoài đơn vị — chỉ có nghĩa là hồi đó không ai bị chặn.

Đúng là có người dò → khoá tài khoản, rà lại vai trò được gán, và kiểm `audit_logs` xem đã đọc được
gì trước khi bị chặn.

---

## 3. `LOGIN_FAILED` dồn dập

Rate limit đăng nhập là **30 lượt/15 phút theo IP**. Con số cao có chủ đích: cả Công ty ra Internet
qua một IP NAT, để 5 thì cả cơ quan không đăng nhập được (`conventions.md` §4.5).

Nghĩa là **đừng vội siết rate limit** khi thấy cảnh báo này — nhiều khả năng là một IP chung.

```sql
SELECT ip_address, count(*), count(DISTINCT username) AS so_tai_khoan_bi_thu
  FROM security_events
 WHERE event_type = 'LOGIN_FAILED' AND occurred_at > now() - interval '1 hour'
 GROUP BY 1 ORDER BY 2 DESC;
```

- **Nhiều lượt, ít tài khoản** → người dùng quên mật khẩu, hoặc một client tự đăng nhập lại vòng
  lặp. Xem có tài khoản nào đang bị khoá (`LOGIN_LOCKED`).
- **Nhiều lượt, NHIỀU tài khoản khác nhau** → dò tài khoản. Đây mới là trường hợp đáng chặn IP ở
  nginx.

---

## 4. `MAINTENANCE_MODE_CHANGED` / `DATABASE_RESTORE_*`

Không phải "lỗi", nhưng là những thao tác cả đội phải biết ngay. Mọi thao tác khôi phục đều có `reason`
trong `detail` — nếu không khớp với việc đã được lên kế hoạch thì xử lý như sự cố an ninh nghiêm
trọng, và đọc [khoi-phuc-du-lieu.md](khoi-phuc-du-lieu.md).

---

## 5. Điều cần nhớ khi số liệu và bảng không khớp

Bộ đếm metric tăng **trước** khi ghi CSDL (xem `SecurityEventService.countForAlerting`). Nên Grafana
có thể hiện sự kiện mà bảng `security_events` không có dòng tương ứng — **đó là cố ý**: khi CSDL
không ghi được, bảng câm lặng đúng lúc cần nhìn nhất, còn bộ đếm trong bộ nhớ vẫn báo. Lệch giữa hai
nguồn là dấu hiệu **CSDL đang có vấn đề**, hãy tìm dòng `ERROR` "Không ghi được sự kiện bảo mật".
