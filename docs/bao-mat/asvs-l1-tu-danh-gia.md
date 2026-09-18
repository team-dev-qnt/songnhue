# Tự đánh giá OWASP ASVS 4.0.3 — Mức 1

| | |
|---|---|
| **Ngày đánh giá** | 15/09/2026 |
| **Commit đánh giá** | `7421719` (nhánh `chore/ws-60-dong-bo-tai-lieu-phase3`). Lượt đọc bắt đầu ở `3832cb6`; hai commit chen giữa (`175f3ef`, `7421719`) đổi `RateLimitPolicy`, `HanMucNguoiDungFilter`, `VirusScanHandler`, `EmailSender`, `Attachment` — các dòng trích từ những tệp ấy đã đo lại trên `7421719` |
| **Task** | T61.28 — NFR-05 (QuanTran chốt: tự kiểm, ⛔ không thuê ngoài) |
| **Người đánh giá** | Phía phát triển (đọc mã + cấu hình trong kho) |
| **Phạm vi** | `backend/` (core · content · operations · hydro · hr · app) · `frontend/admin-app` · `frontend/public-web` · `deploy/nginx` · `deploy/docker` · `deploy/compose.*.yml` · `.github/workflows` |
| **Ngoài phạm vi** | Máy chủ đang chạy (VPS-1/VPS-2), DNS, cấu hình hệ điều hành, MinIO/PostgreSQL ở mức vận hành, hệ nguồn thuỷ văn bên thứ ba |

> ⛔⛔ **Đây là TỰ ĐÁNH GIÁ của phía phát triển, KHÔNG phải kiểm thử xâm nhập (pentest) độc lập.**
> Người viết mã và người đánh giá cùng một phía, mang cùng giả định (luật 29 của dự án). Tài liệu này
> đọc mã và cấu hình trong kho; nó ⛔ không chứng minh được máy chủ đang chạy đúng như kho. Phép đo
> trên môi trường thật đi kèm là kịch bản ZAP baseline ở `tools/zap/` — ⛔ chưa chạy tại ngày viết.

## 0. Cách đọc

**Luật chấm** (áp cứng cho mọi dòng):

- **Đạt** — tìm được dòng mã / cấu hình **thực thi** hoặc bài kiểm tự động, trích ở cột *Bằng chứng*.
  Một chú thích hay javadoc khẳng định điều gì đó ⛔ **không** được tính là bằng chứng.
- **Một phần** — có cơ chế, nhưng có ngược chứng đo được hoặc phủ không hết đường vào.
- **Không đạt** — cơ chế vắng, hoặc có đường vượt qua đã đọc được trong mã.
- **N/A** — hệ ⛔ có tính năng mà yêu cầu nói tới; lý do ghi ở cột *Ghi chú*.
- **Chưa đo** — không tìm được bằng chứng theo cả hai chiều, hoặc chỉ đo được trên hệ đang chạy.

Đường dẫn tính từ gốc kho. `tệp:dòng` là số dòng **tại commit `7421719`** — số đo có hạn dùng.
Tên bài kiểm ghi dạng `Lớp#phươngThức` và **đã đối chiếu là có thật** trong kho; ⚠ tài liệu này
⛔ không chạy lại bộ kiểm (ranh giới của lượt đánh giá), nên *"có bài kiểm"* ≠ *"bài kiểm đang xanh"*.

**Chương V1 (Kiến trúc)**: ASVS 4.0.3 ⛔ không có yêu cầu nào ở mức 1 (mọi mục V1 bắt đầu từ L2) ⇒
không có dòng nào, ⛔ không tính vào bảng đếm.

## 1. Tổng hợp

| Chương | Số dòng | Đạt | Một phần | Không đạt | N/A | Chưa đo |
|---|---:|---:|---:|---:|---:|---:|
| V2 Xác thực | 27 | 9 | 5 | 7 | 6 | 0 |
| V3 Phiên | 12 | 9 | 2 | 1 | 0 | 0 |
| V4 Kiểm soát truy cập | 8 | 4 | 3 | 1 | 0 | 0 |
| V5 Kiểm tra đầu vào / mã hoá đầu ra | 26 | 13 | 6 | 1 | 4 | 2 |
| V6 Mật mã | 1 | 1 | 0 | 0 | 0 | 0 |
| V7 Lỗi & nhật ký | 3 | 2 | 1 | 0 | 0 | 0 |
| V8 Bảo vệ dữ liệu | 7 | 1 | 3 | 3 | 0 | 0 |
| V9 Truyền thông | 3 | 2 | 1 | 0 | 0 | 0 |
| V10 Mã độc | 3 | 1 | 0 | 0 | 1 | 1 |
| V11 Logic nghiệp vụ | 5 | 2 | 2 | 1 | 0 | 0 |
| V12 Tệp & tài nguyên | 11 | 5 | 5 | 1 | 0 | 0 |
| V13 API | 6 | 3 | 1 | 0 | 1 | 1 |
| V14 Cấu hình | 15 | 8 | 5 | 1 | 1 | 0 |
| **Tổng** | **127** | **60** | **34** | **16** | **13** | **4** |

⛔ **Không đọc "60/127 Đạt" thành một điểm số.** Một dòng *Không đạt* ở V4.3.1 (vượt 2FA của tài
khoản quản trị) nặng hơn mười dòng *Đạt* về header. Thứ tự ưu tiên ở **§16**.

---

## V2 — Xác thực

| Mã | Yêu cầu (tóm tắt) | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 2.1.1 | Mật khẩu người dùng ≥ 12 ký tự | **Không đạt** | `backend/core/src/main/java/com/songnhue/core/application/settings/SettingKeys.java:45` `DEFAULT_PASSWORD_MIN_LENGTH = 10` · seed `backend/core/src/main/resources/db/migration/core/V202608131009__core_seed_settings.sql:41-42` giá trị `'10'`, ràng buộc `'min=8;max=64'` · kiểm ở `PasswordPolicyService.java:107` | Mặc định 10, và UI cho hạ xuống 8 |
| 2.1.2 | Cho phép ≥ 64 ký tự, từ chối > 128 | **Một phần** | `backend/core/src/main/java/com/songnhue/core/api/auth/AuthDtos.java:30,43` `@Size(max = 200)` · `UserAdminController.java:204` `@NotBlank String temporaryPassword` (⛔ không có trần) · BCrypt `PasswordPolicyService.java:38` | Trần 200 > 128. ⚠ `spring-security-crypto 7.1.1` **ném** `IllegalArgumentException("password cannot be more than 72 bytes")` (đọc bằng `javap` trên jar trong `~/.m2`) ⇒ mật khẩu > 72 **byte** rơi vào `GlobalExceptionHandler.java:176-183` ⇒ **500 SYS-0001**. Cụm 64 ký tự tiếng Việt có dấu (2–3 byte/ký tự) chạm trần này |
| 2.1.3 | Không cắt cụt mật khẩu | **Một phần** | như 2.1.2 | Lúc **đặt**: ⛔ không cắt âm thầm (ném lỗi). Lúc **kiểm**: BCrypt chỉ dùng 72 byte đầu theo thuật toán — chưa đo bằng bài kiểm |
| 2.1.4 | Cho mọi ký tự Unicode in được | **Đạt** | `PasswordPolicyService.java:101-130` chỉ kiểm độ dài, chữ+số, không chứa username — ⛔ không có danh sách ký tự cấm | Giới hạn thực tế là 72 byte (2.1.2) |
| 2.1.5 | Người dùng tự đổi được mật khẩu | **Đạt** | `AuthController.java:228-244` `POST /api/v1/auth/change-password` · `ChangePasswordHttpTest#doiMatKhauThuHoiPhienHienTai` | |
| 2.1.6 | Đổi mật khẩu cần mật khẩu hiện tại | **Đạt** | `PasswordChangeService.java:64-74` `if (!passwords.matches(currentPassword, …)) throw AUTH_0001` | |
| 2.1.7 | Kiểm mật khẩu với danh sách đã lộ | **Không đạt** | grep `pwned\|breach\|common-password` trong `backend/*/src/main` = 0 kết quả liên quan | |
| 2.1.8 | Có thước đo độ mạnh mật khẩu | **Không đạt** | `frontend/admin-app/src/features/auth/ChangePasswordPage.tsx:108,125` chỉ `Input.Password`; grep `strength\|zxcvbn` = 0 | |
| 2.1.9 | Không có luật tổ hợp ký tự | **Không đạt** | `PasswordPolicyService.java:112-115` `requireLetterAndDigit` · seed `…core_seed_settings.sql:43` `'true'` | Bắt buộc có cả chữ và số |
| 2.1.10 | Không bắt đổi định kỳ / lịch sử | **Đạt** | grep `PASSWORD_MAX_AGE_DAYS` toàn kho: chỉ khai ở `SettingKeys.java:18`, **0 nơi đọc** | ⚠ Seed `…core_seed_settings.sql:45` bày khoá `security.password.max-age-days = 90` lên UI mà ⛔ không mã nào thi hành — một tham số **nói dối** (luật 15). Hành vi đạt ASVS, bảng cài đặt thì sai |
| 2.1.11 | Cho dán, cho trình quản lý mật khẩu | **Đạt** | `LoginPage.tsx:101` `autoComplete="current-password"` · `ChangePasswordPage.tsx:108` `autoComplete="new-password"`; grep `onPaste` trong `features/auth` = 0 | |
| 2.1.12 | Xem được mật khẩu đang gõ | **Đạt** | `LoginPage.tsx:101`, `ChangePasswordPage.tsx:100-125` dùng `Input.Password` của AntD, ⛔ không tắt `visibilityToggle` | Nút hiện/ẩn là mặc định của AntD |
| 2.2.1 | Chống dò tự động (≤ 100 lần sai/giờ mỗi tài khoản) | **Một phần** | Khoá tài khoản: `LoginAttemptService.java:100-114` (5 lần/15' → khoá 15', seed `…core_seed_settings.sql:47-52`) · xô LOGIN 30/15'/IP `RateLimitPolicy.java:44,124-126` · nginx `deploy/nginx/templates/default.conf.template:59,274-275` 20 lượt/phút · `AuthServiceLoginTest#thresholdReachedSwitchesErrorCode`, `HaiTangHanMucTest` | ⛔ **Mã TOTP không có bộ đếm sai theo tài khoản**: `TotpService.java:224-227` chỉ ghi sự kiện. ⛔ **Cổng công khai chuyển tiếp MỌI `/api/v1/**`** (`frontend/public-web/src/app/api/v1/[...path]/route.ts:55,105-125`) ⇒ `/auth/2fa/verify` gọi qua tên miền công khai **né** vùng `api_auth` của nginx (chỉ khai ở khối admin) và rơi vào xô API 100/phút/IP |
| 2.2.2 | Xác thực yếu (SMS/email) chỉ làm bước phụ | **N/A** | grep `sms\|otp.*email` = 0 | Hệ ⛔ có OTP qua SMS/email |
| 2.2.3 | Báo cho người dùng khi đổi thông tin xác thực | **Không đạt** | `PasswordChangeService.java:91-96` và `TotpService.java:160` chỉ gọi `SecurityEventService.record` — `SecurityEventService.java:54-68` ghi bảng + bộ đếm, ⛔ không gửi thông báo; grep `PASSWORD_CHANGED\|TWO_FACTOR_ENROLLED` ngoài hai chỗ trên = 0 | Chủ tài khoản ⛔ biết mật khẩu/2FA của mình vừa đổi |
| 2.3.1 | Mật khẩu ban đầu do hệ sinh ngẫu nhiên, hết hạn | **Một phần** | `UserAdminService.java:116-138` Admin tự gõ mật khẩu tạm, `setMustChangePassword(true)` (dòng 135) · `PermissionInterceptor.java:87-89` chặn mọi thứ trừ đổi mật khẩu | Buộc đổi ở lần đầu = đạt; ⛔ không do hệ sinh, ⛔ không hết hạn |
| 2.5.1 | Mã kích hoạt/khôi phục ban đầu không gửi dạng rõ | **N/A** | `UserAdminService.java:116-138` ⛔ không gọi kênh gửi nào | Hệ ⛔ gửi mật khẩu tạm; Admin trao tay ngoài hệ |
| 2.5.2 | Không có gợi ý mật khẩu / câu hỏi bí mật | **Đạt** | grep `hint\|security_question\|cau_hoi_bi_mat` trong `backend/*/src/main` và migration = 0 | |
| 2.5.3 | Khôi phục không làm lộ mật khẩu hiện tại | **Đạt** | `PasswordPolicyService.java:38,45-47` chỉ lưu BCrypt cost 12 · `PasswordPolicyServiceTest#usesBcryptCost12`, `#errorDetailNeverLeaksThePassword` | |
| 2.5.4 | Không có tài khoản mặc định/dùng chung | **Một phần** | `AdminBootstrapRunner.java:40` `BOOTSTRAP_USERNAME = "superadmin"`; dòng 70 chỉ kích hoạt khi `PENDING_ACTIVATION`, dòng 85 buộc đổi mật khẩu | Tên đăng nhập quản trị cao nhất là tên cố định, đoán trước được |
| 2.5.5 | Báo khi yếu tố xác thực bị thay | **Không đạt** | `TotpService.java:115-122` xoá TOTP cũ + mã khôi phục, ⛔ không thông báo (xem 2.2.3) | Kết hợp 2.5.6 ⇒ kẻ thay 2FA không để lại dấu hiệu nào chủ tài khoản nhìn thấy |
| 2.5.6 | Khôi phục dùng cơ chế an toàn | **Không đạt** | `AuthController.java:133-140` `POST /auth/2fa/enroll` là `@PublicEndpoint`, chỉ cần vé challenge · `AuthService.java:136-139` `totp.enroll(userFromChallenge(…))` **⛔ không kiểm `isEnrolled`** · `TotpService.java:120-121` xoá bản TOTP **đã xác nhận** · `CsrfFilter.java:47-48` miễn CSRF · grep `2fa\|enroll` trong `backend/*/src/test` = **0 bài kiểm** | ⛔⛔ Vé challenge phát ra ngay sau bước **mật khẩu** (`AuthService.java:106-108`). Có mật khẩu ⇒ gọi `enroll` ⇒ secret mới ⇒ `confirm` ⇒ token. **"Khôi phục 2FA" = chỉ cần mật khẩu.** Không có luồng quên mật khẩu nào khác |
| 2.7.1 | Không mặc định dùng SMS/PSTN | **N/A** | xem 2.2.2 | |
| 2.7.2 | Mã ngoài băng hết hạn sau 10 phút | **N/A** | xem 2.2.2 | |
| 2.7.3 | Mã ngoài băng dùng một lần | **N/A** | xem 2.2.2 | |
| 2.7.4 | Kênh ngoài băng độc lập, an toàn | **N/A** | xem 2.2.2 | |
| 2.8.1 | OTP theo thời gian có vòng đời xác định | **Đạt** | `TotpGenerator.java:28,30` 30 giây · 6 chữ số · `TotpService.java:57` lệch ±1 bước · chống dùng lại `TotpService.java:230-241` · `TotpGeneratorTest` | Chống dùng lại ⛔ có bài kiểm riêng |

## V3 — Quản lý phiên

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 3.1.1 | Không để token phiên trên URL | **Đạt** | `AuthFilter.java:86-93` chỉ đọc `Authorization: Bearer` · refresh token chỉ trong cookie `AuthController.java:171,280` · `frontend/admin-app/src/shared/apiClient.ts:32` token trong bộ nhớ; grep `token=` trong `frontend/*/src` = 0 | |
| 3.2.1 | Token mới khi xác thực | **Đạt** | `RefreshTokenService.java:78-80` `familyId = UUID.randomUUID()` mỗi lần đăng nhập · `AuthService.java:193-198` | |
| 3.2.2 | Token ≥ 64 bit entropy | **Đạt** | `RefreshTokenService.java:43,150` 32 byte · `HashUtils.java:27,49-52` `SecureRandom` · `RefreshTokenServiceTest#storesOnlyHash` | |
| 3.2.3 | Lưu token trên trình duyệt an toàn | **Đạt** | access token: biến bộ nhớ `apiClient.ts:32`; refresh: cookie HttpOnly `CsrfTokens.java:56-60,88-90`; grep `localStorage\|sessionStorage` trong `admin-app/src` (bỏ chú thích) = 0 | |
| 3.3.1 | Đăng xuất/hết hạn làm token vô hiệu ngay | **Đạt** | `AuthService.java:176-180` `revokeFamily` · `AuthFilter.java:79` kiểm `isAccessTokenStillValid` **mỗi request** (`UserAuthorityRepository.java:80-96`, ⛔ cache) · `ChangePasswordHttpTest#doiMatKhauThuHoiPhienHienTai` · `RefreshTokenServiceTest#revokeAllSessions` | ⚠ ⛔ có bài HTTP riêng cho `/auth/logout`; bài đổi mật khẩu đi qua cùng cơ chế |
| 3.3.2 | Bắt xác thực lại định kỳ (L1: 30 ngày) | **Một phần** | `RefreshTokenService.java:157` `expiresAt = now + refreshTokenTtl` tính lại **mỗi lượt xoay** · `application.yml:266` `refresh-token-ttl: 7d`; grep `absolute\|family_started` = 0 | Bỏ không 7 ngày ⇒ hết phiên (đạt vế *idle*). ⛔ Dùng liên tục thì phiên **trượt vô hạn** — không có trần tuyệt đối |
| 3.4.1 | Cookie có `Secure` | **Đạt** | `CsrfTokens.java:91-94` · `AuthController.java:80` · `backend/app/src/main/resources/application.yml:273` `${SECURE_COOKIE:true}`; `SECURE_COOKIE=false` chỉ có ở `deploy/env/local.env*` | Chưa đo trên máy chủ (ZAP quy tắc 10011) |
| 3.4.2 | Cookie phiên có `HttpOnly` | **Đạt** | `CsrfTokens.java:58-59` refresh `httpOnly=true` | `XSRF-TOKEN` cố ý đọc được bằng JS (double-submit, `CsrfTokens.java:47`) — ⛔ phải cookie phiên |
| 3.4.3 | Cookie có `SameSite` | **Đạt** | `CsrfTokens.java:87` `SameSite=Strict` | |
| 3.4.4 | Dùng tiền tố `__Host-` | **Không đạt** | `CsrfTokens.java:32,34` tên `XSRF-TOKEN`, `refresh_token` | Refresh cookie có `Path=/api/v1/auth` nên ⛔ dùng được `__Host-` (đòi `Path=/`); `__Secure-` thì dùng được |
| 3.4.5 | Đặt `Path` hợp lý | **Đạt** | `CsrfTokens.java:37` `REFRESH_COOKIE_PATH = "/api/v1/auth"` | |
| 3.7.1 | Xác thực lại trước thao tác nhạy cảm | **Một phần** | đổi mật khẩu cần mật khẩu cũ `PasswordChangeService.java:64` · khôi phục CSDL cần mã TOTP `BackupController.java:142` | ⛔ Gán vai trò / đặt lại quyền (`UserAdminController.java:111,152`) và liên kết hồ sơ 🔒 ⛔ đòi xác thực lại |

## V4 — Kiểm soát truy cập

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 4.1.1 | Kiểm quyền ở tầng máy chủ tin cậy | **Đạt** | `PermissionInterceptor.java:64-114` · `PermissionInterceptorTest#deniesWhenPermissionMissing` · `MaTranPhanQuyenHttpTest` | |
| 4.1.2 | Thuộc tính dùng để phân quyền không sửa được từ phía người dùng | **Đạt** | JWT ⛔ mang quyền: `TokenServiceTest#tokenCarriesNoPermissions` · quyền nạp từ CSDL `AuthorityLoader` · chữ ký: `TokenServiceTest#tamperedPayload`, `#algorithmConfusionAttack`, `#noneAlgorithmAttack` · IP do nginx ghi đè `deploy/nginx/snippets/proxy-common.conf:3` + `IpThatTrongNhatKyHttpTest` | |
| 4.1.3 | Quyền tối thiểu | **Một phần** | `UserAdminService.java:396-431` `replacePermissionsOfRole`: chỉ chặn vai trò hệ thống (dòng 400-402) và tự gỡ quyền của chính mình (dòng 409-416); **⛔ không chặn cấp quyền mà người sửa không có** · `RbacMatrixTest#readOnlyRolesHoldNoWritePermission` | ADMIN (có `adm:role:manage`, vai trò `is_system = FALSE`) tự thêm `hr:employee:view-sensitive` cho vai trò của mình — đã ghi ở T54.4, bản vá chờ quyết định chính sách |
| 4.1.5 | Kiểm quyền thất bại an toàn (kể cả khi lỗi) | **Đạt** | `PermissionInterceptor.java:95-104` endpoint quên khai quyền ⇒ **403** · `PermissionInterceptorTest#unannotatedEndpointIsDenied` · `DenyByDefaultTest#everyEndpointDeclaresItsGuard` | |
| 4.2.1 | Chống IDOR cho dữ liệu nhạy cảm | **Một phần** | lọc phạm vi đơn vị `ScopeFilterEndToEndTest#outOfScopeLookupIsForbiddenNotMissing` · `HoSoNhanSuPhamViTest#traHoSoNgoaiPhamViQuaHttpLaTuChoi` · tệp theo chủ `AttachmentService.java:274` `readForOwner` · phiên của chính mình `SessionService.java:73-74` | Có ở các module có phạm vi; ⛔ chưa có phép kiểm phủ **mọi** endpoint nhận `publicId` (vd. CMS, thuỷ văn — dữ liệu dùng chung nên có thể ⛔ cần) |
| 4.2.2 | Chống CSRF | **Đạt** | `CsrfFilter.java:70-99` double-submit header ↔ cookie · `CsrfTokens.java:87` SameSite=Strict · `CsrfFilterTest#blocksRequestWithCookieButNoHeader`, `#blocksMismatchedToken`, `#doesNotSkipRefresh` | |
| 4.3.1 | Giao diện quản trị dùng MFA phù hợp | **Không đạt** | Có bắt buộc: `AuthenticatedUser.java:51` `SUPER_ADMIN, ADMIN, ADMIN_HR` · `TotpService.java:96-103` · `AuthServiceLoginTest#adminMustPassTwoFactor`. **Vượt được**: xem 2.5.6 (`AuthService.java:136-139`) | ⛔⛔ Lớp thứ hai bị gỡ bằng chính lớp thứ nhất. Thêm: API quản trị gọi được qua **tên miền công khai** (`route.ts:55` chuyển tiếp mọi đường dẫn) |
| 4.3.2 | Tắt duyệt thư mục, không lộ `.git`/metadata | **Một phần** | admin: image chỉ chép `dist` `deploy/docker/admin-app.Dockerfile:224`, chặn `.map` dòng 200-202; grep `autoindex` trong `deploy/` = 0 | `public-web` ⛔ đo; ⛔ có luật nginx chung chặn `/\.` (xem 12.5.1) |

## V5 — Kiểm tra đầu vào, làm sạch, mã hoá đầu ra

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 5.1.1 | Chống HTTP parameter pollution | **Chưa đo** | grep `getParameterValues\|getParameterMap` = 0; tham số trùng lặp để Spring xử lý mặc định | ⛔ có bài kiểm; ⛔ khẳng định được theo chiều nào |
| 5.1.2 | Chống mass assignment | **Đạt** | Đối chiếu các kiểu `@RequestBody` (91 chỗ) với mọi lớp `@Entity`: 0 trùng · ví dụ `backend/hydro/src/main/java/com/songnhue/hydro/api/HydroCatalogDtos.java:55` record riêng · vai trò/trạng thái có endpoint riêng `UserAdminController.java:95,111` | |
| 5.1.3 | Kiểm đầu vào theo danh sách cho phép | **Một phần** | `@Valid` + Bean Validation, xử lý ở `GlobalExceptionHandler.java:91-97` | ⛔ **13/91** `@RequestBody` thiếu `@Valid`, vd `backend/content/src/main/java/com/songnhue/content/api/PublicPortalController.java:315` (record dòng 289-290 ⛔ ràng buộc). Email biểu mẫu liên hệ ⛔ kiểm định dạng: `ContactService.java:118-163` |
| 5.1.4 | Dữ liệu có cấu trúc được định kiểu mạnh | **Một phần** | `@PathVariable UUID` (vd `PublicPortalController.java:413`), enum, record | như 5.1.3 |
| 5.1.5 | Chuyển hướng chỉ tới đích cho phép | **Đạt** | grep `sendRedirect\|"redirect:\|HttpHeaders.LOCATION` trong backend = 0 · `LoginPage.tsx:40` đích lấy từ state router, ⛔ query · `route.ts:80` `redirect: 'manual'` | |
| 5.2.1 | Làm sạch HTML từ trình soạn thảo | **Đạt** | `HtmlSanitizer.java:116` jsoup + allowlist · đường ghi bài viết `ArticleService.java:227` · đường ghi cài đặt `SettingService.java:198-199` · `HtmlSanitizerTest#goThescript` · `SettingHtmlSanitizeTest#duongCauHinhHeThongKhuTrung` · `ArticleContentRongTest` | |
| 5.2.2 | Làm sạch văn bản tự do | **Một phần** | `InboundSubmissionGate.java:91` bỏ ký tự điều khiển trừ `\n\t` · `ContactService.java:164` giới hạn độ dài nội dung | Họ tên/chủ đề ⛔ giới hạn độ dài ở tầng DTO |
| 5.2.3 | Chống chèn vào hệ thư | **Một phần** | tiêu đề là hằng `ContactAckMailHandler.java:48` · gửi bằng `SimpleMailMessage` `EmailSender.java:66-75` | ⛔ Người nhận là email người dân gõ, ⛔ kiểm định dạng (`ContactService.java:122`); thư xác nhận **bật mặc định** (`ContactAckMailHandler.java:78` `true`) và reCAPTCHA **tắt mặc định** (`InboundSubmissionGate.java:133`) ⇒ bất kỳ ai cũng khiến máy chủ thư của Công ty gửi thư mang họ tên/chủ đề tuỳ ý (`ContactAckMailHandler.java:95-111`) tới địa chỉ tuỳ ý. Chặn CRLF phụ thuộc JavaMail — ⛔ bài kiểm |
| 5.2.4 | Không dùng `eval`/thực thi mã động | **Đạt** | grep `ScriptEngine\|SpelExpressionParser\|parseExpression` (BE) và `eval(\|new Function(` (FE) = 0 · `SpelCompilerTatTest` | |
| 5.2.5 | Chống template injection | **N/A** | grep `thymeleaf\|freemarker\|velocity\|mustache\|pebble` trong mã và `pom.xml` = 0 | Hệ ⛔ dùng template engine; thư dựng bằng `formatted()` |
| 5.2.6 | Chống SSRF | **Một phần** | `backend/hydro/src/main/java/com/songnhue/hydro/domain/DiaChiNguon.java:92-103` chặn scheme/userinfo/IP nội bộ · `Bhh40Adapter.java:97` ⛔ theo redirect · `application.yml:253` `allow-internal-host` mặc định `false` · `DiaChiNguonTest#daiNoiBoBiChan` | ⛔ `DiaChiNguon.java:225` chỉ chặn IP **viết dạng số chấm**: tên miền trỏ về IP nội bộ, IPv6 `::ffff:127.0.0.1`, IP dạng thập phân — ⛔ phân giải DNS nên lọt (suy từ regex, ⛔ bài kiểm). Người nhập URL là quản trị viên thuỷ văn |
| 5.2.7 | Làm sạch SVG | **Không đạt** | `SvgSanitizer.java:52` regex `<\s*script\b.*?(</\s*script\s*>\|$)` chạy **một lượt** (dòng 90-92) · nằm trên đường tải lên thật `AttachmentService.java:158-159`, nhận SVG ở `SiteConfigService.java:131` | ⛔⛔ **Đo 15/09** (chép nguyên 8 mẫu vào một chương trình Java tạm, chạy `java`): `<svg><scr<script></script>ipt>alert(1)</script></svg>` ⇒ `<svg><script>alert(1)</script></svg>`. `SvgSanitizerTest#catTheScript` ⛔ có ca lồng. `<style>` đi qua nguyên vẹn |
| 5.2.8 | Làm sạch Markdown/CSS/BBCode | **N/A** | grep `markdown\|commonmark\|flexmark\|remark` = 0 · CSS người dùng chỉ ở `style` của iframe bản đồ `HtmlSanitizer.java:103` | |
| 5.3.1 | Mã hoá đầu ra đúng ngữ cảnh | **Đạt** | React tự escape; `dangerouslySetInnerHTML` chỉ có ở 4 chỗ, 3 chỗ trên cổng nhận HTML đã làm sạch lúc ghi (`public-web/src/app/bai-viet/[slug]/page.tsx:147`, `SiteFooter.tsx:141,303`) · header: `HttpHeaderText.java:35` | Chỗ thứ tư: `admin-app/src/components/business/RichTextEditor.tsx:797` xem trước giá trị đang soạn (tự-XSS; CSP admin `script-src 'self'`) |
| 5.3.2 | Giữ bộ ký tự người dùng chọn | **Chưa đo** | | ⛔ có bài kiểm ký tự ngoài BMP qua HTTP |
| 5.3.3 | Chống XSS phản chiếu/lưu trữ/DOM | **Một phần** | CSP `frontend/public-web/next.config.ts:41-57` · `csp.test.ts` · `NginxSecurityHeadersTest#cspChatOChoDangKe` · liên kết nguồn bài viết chặn scheme `nguonBaiViet.ts:55` | ⛔ Liên kết menu `routes.ts:146-147` `return item.url` và kênh MXH `SiteFooter.tsx:267` `href={kenh.url}` — backend chỉ kiểm rỗng (`MenuService.java:233`) ⇒ `javascript:` lưu được. Cổng dùng `react 18.3.1` (⛔ chặn `javascript:`) và CSP `script-src 'unsafe-inline'` (`next.config.ts:43`). **Chưa đo trên trình duyệt.** Cộng 5.2.7 |
| 5.3.4 | Truy vấn CSDL tham số hoá | **Đạt** | grep `createNativeQuery` = 0; `@Query(nativeQuery=true)` dùng `:tham_so` (vd `UserAuthorityRepository.java:80-96`) · JdbcTemplate dùng `?` (`SyncLogQueryRepository.java:157-158`) · sắp xếp theo whitelist `UtilsTest#rejectsSortFieldOutsideWhitelist` | Chỗ nối chuỗi chỉ ghép **hằng số** tên bảng: `MaHoaLaiJdbc.java:88,101,161`, `CmsAttachmentRefCleaner.java:80` |
| 5.3.5 | Nơi không tham số hoá thì mã hoá đúng ngữ cảnh | **Đạt** | như 5.3.4 | |
| 5.3.6 | Chống JSON injection | **Đạt** | JSON dựng tay chỉ chèn số/UUID: `AttachmentService.java:173`; chuỗi có escape `PortalRevalidateClient.java:73` | |
| 5.3.7 | Chống LDAP injection | **N/A** | | ⛔ LDAP |
| 5.3.8 | Chống OS command injection | **Đạt** | `PostgresToolRunner.java:68` `new ProcessBuilder(command)` dạng mảng, ⛔ qua shell · tham số từ cấu hình + tên tệp máy sinh `BackupService.java:159-162` · grep `Runtime.exec` = 0 | |
| 5.3.9 | Chống LFI/RFI | **Đạt** | `Paths.get` chỉ nhận đường dẫn từ cấu hình/CSDL (`BackupService.java:240,329`, `RestoreService.java:127`) · tên lưu kho là UUID `FileValidator.java:185` | |
| 5.3.10 | Chống XPath/XML injection | **N/A** | | ⛔ dựng XML từ đầu vào |
| 5.5.2 | Trình đọc XML chặn XXE | **Đạt** | `backend/core/src/main/java/com/songnhue/core/common/importer/SpreadsheetReader.java:319-320` `SUPPORT_DTD=false`, `IS_SUPPORTING_EXTERNAL_ENTITIES=false`; grep `DocumentBuilderFactory\|SAXParserFactory\|TransformerFactory` = 0 | ⛔ bài kiểm XXE. ⚠ `SpreadsheetReader.java:188-191` `readAllBytes()` ⛔ trần giải nén (zip bomb) |
| 5.5.3 | Không giải tuần tự dữ liệu không tin cậy | **Đạt** | grep `ObjectInputStream\|activateDefaultTyping\|@JsonTypeInfo` = 0 | |
| 5.5.4 | Phía trình duyệt dùng `JSON.parse` | **Đạt** | `admin-app/src/features/admin/SettingsPage.tsx:253`, `AuditLogPage.tsx:272`; grep `eval(` = 0 | |

## V6 — Mật mã lưu trữ

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 6.2.1 | Mô-đun mật mã thất bại an toàn, không padding oracle | **Đạt** | `CryptoService.java:38` `AES/GCM/NoPadding` · `CryptoService.java:109-114` sai tag ⇒ ném, ⛔ trả bản rõ · `CryptoServiceTest#detectsTampering`, `#missingOldKeyFailsLoudly` · lỗi ra ngoài qua `GlobalExceptionHandler.java:176-183` thành câu chung | |

## V7 — Xử lý lỗi và nhật ký

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 7.1.1 | Không ghi mật khẩu/token vào log; token phiên chỉ lưu dạng băm | **Đạt** | `RequestLoggingFilter.java:60` chỉ method + URI + status · refresh token lưu SHA-256 `RefreshTokenService.java:91,155` + `RefreshTokenServiceTest#storesOnlyHash` · `User.java:34` `excludeFields = {"passwordHash"}` · `AuditRedactionRuleTest#moiTruongBiMatCuaEntityDuocAuditDeuBiLoaiTru`, `#excludeFieldsPhaiTroToiTruongCoThat` · mã số nguồn thuỷ văn che trong log `Bhh40Adapter.java:177` | ⚠ ⛔ phải log nhưng cùng họ: `GlobalExceptionHandler.java:94` trả `rejectedValue` ⇒ mật khẩu > 200 ký tự bị **vọng lại** trong phản hồi 422 |
| 7.1.2 | Không ghi dữ liệu cá nhân nhạy cảm vào log | **Một phần** | `backend/hr/src/main/java/com/songnhue/hr/domain/EmployeeSensitive.java:41-50` `excludeFields` · `AuditRedactionRuleTest#luat3` | ⛔ `GlobalExceptionHandler.java:158-161` ghi `getMostSpecificCause().getMessage()` — thông điệp PostgreSQL dạng `Key (col)=(value) already exists` mang **giá trị** cột duy nhất (username, email, vân tay CCCD) vào log |
| 7.4.1 | Lỗi bất ngờ trả câu chung + mã tra cứu | **Đạt** | `GlobalExceptionHandler.java:176-183` · `application.yml:200-201` `include-stacktrace: never`, `include-message: never` · `EnvelopeAndErrorHandlingTest#hidesInternalDetails` | |

## V8 — Bảo vệ dữ liệu

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 8.2.1 | Header chống lưu đệm cho dữ liệu nhạy cảm | **Không đạt** | grep `no-store\|Cache-Control\|CacheControl` trong `backend/*/src/main`, `deploy/`: chỉ `maxAge` cho tệp công khai (`PublicPortalController.java:415,459`) và `no-cache` cho SPA (`admin-app.Dockerfile:216`); `location /api/` (`default.conf.template:281-288`) ⛔ đặt | Dự án ⛔ dùng Spring Security nên ⛔ có `no-store` mặc định. Phản hồi JSON hồ sơ CBNV/trường 🔒 ⛔ cấm trình duyệt/proxy lưu đệm |
| 8.2.2 | Không lưu dữ liệu nhạy cảm trong storage trình duyệt | **Đạt** | `apiClient.ts:32` token trong bộ nhớ; grep `localStorage\|sessionStorage\|indexedDB` (bỏ chú thích) trong `admin-app/src`, `public-web/src` = 0 | ⛔ bài kiểm canh |
| 8.2.3 | Xoá dữ liệu phía client khi hết phiên | **Một phần** | `frontend/admin-app/src/app/auth/AuthProvider.tsx:138-151` `clearTokens()` + `setUser(null)` | ⛔ ⛔ gọi `queryClient.clear()` (grep ngoài test = 0) ⇒ dữ liệu đã tải (kể cả hồ sơ nhân sự) còn trong bộ nhớ đệm React Query tới khi tải lại trang |
| 8.3.1 | Dữ liệu nhạy cảm không đi trên query string | **Một phần** | ⛔ `@RequestParam` tên token/key/password · chặn tham số bí mật trong `base_url`: `ApiSourceService.java:284` + `NguonDuLieuMaSoHttpTest#creatingASourceWithACredentialInTheUrlIsRejected` | Ngoại lệ do bên thứ ba ép: `Bhh40Adapter.java:109` `"?key=" + maHoaMaSo(…)` — mã số nằm trong nhật ký truy cập **của hệ nguồn** |
| 8.3.2 | Người dùng xuất/xoá được dữ liệu của mình | **Không đạt** | grep `@(Get\|Post\|Delete)Mapping("/me` = chỉ `AuthController.java:192` (xem) | NĐ 13/2023 quyền của chủ thể dữ liệu |
| 8.3.3 | Thông báo rõ về thu thập dữ liệu cá nhân, xin đồng ý | **Không đạt** | `frontend/public-web/src/app/` ⛔ có trang chính sách quyền riêng tư; grep `consent\|đồng ý\|privacy` trong `ContactForm.tsx`, `FeedbackForm.tsx` = 0 (chỉ chú thích) | Biểu mẫu liên hệ/góp ý thu họ tên, email, điện thoại của người dân |
| 8.3.4 | Nhận diện và có chính sách cho dữ liệu nhạy cảm | **Một phần** | cột mã hoá AES-256-GCM `EmployeeSensitive.java:41-50` + `CryptoService.java:38` · `HoSoNhanSuMaHoaTest` · `AuditRedactionRuleTest#luat3` | ⛔ có tài liệu phân loại dữ liệu (grep `privacy\|phan-loai\|du-lieu-nhay-cam` trong `docs/` = 0) |

## V9 — Truyền thông

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 9.1.1 | TLS cho mọi kết nối client, không lùi về HTTP | **Đạt** | `deploy/nginx/templates/default.conf.template:147-148` `return 308 https://…` · dòng 160-165 `ssl_reject_handshake on` cho host lạ · HSTS `deploy/nginx/snippets/edge-headers.conf:27` | Kết nối nội bộ (app ↔ PostgreSQL/MinIO) chạy HTTP/không TLS trong mạng compose — thuộc 9.2 (L2) |
| 9.1.2 | Chỉ bộ mã mạnh (đo bằng công cụ) | **Một phần** | `default.conf.template:86` chỉ ECDHE + AES-GCM/CHACHA20 · dòng 90 `ssl_session_tickets off` | Cấu hình đúng; ⛔ **chưa chạy** testssl.sh/SSL Labs trên máy thật |
| 9.1.3 | Chỉ TLS 1.2/1.3 | **Đạt** | `default.conf.template:85` `ssl_protocols TLSv1.2 TLSv1.3` | Chưa đo trên máy thật |

## V10 — Mã độc

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 10.3.1 | Tự cập nhật qua kênh an toàn, có chữ ký | **N/A** | | Hệ ⛔ có cơ chế tự cập nhật |
| 10.3.2 | Toàn vẹn mã tải về (SRI), không nạp mã từ nguồn không tin cậy | **Đạt** | grep `<script\|next/script\|googletagmanager\|cdn.\|unpkg\|jsdelivr` trong FE: chỉ `admin-app/index.html:19` script nội bộ · CSP admin `script-src 'self'` (`admin-app.Dockerfile:102`) | Cổng: `script-src 'self' 'unsafe-inline'` — ⛔ nạp nguồn ngoài nhưng nới inline |
| 10.3.3 | Chống chiếm tên miền con | **Chưa đo** | | Cần rà DNS thật; ⚠ `songnhue.com` cũ vẫn trỏ về VPS-1 nhưng ⛔ còn khối `server` (runbook `ten-mien-va-chung-chi.md`) |

## V11 — Logic nghiệp vụ

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 11.1.1 | Luồng đi đúng thứ tự, không nhảy bước | **Đạt** | `WorkflowEngine.java:78-80` chuyển trạng thái không có trong bảng ⇒ `SYS_0008` · `WorkflowInitialStateTest#trangThaiKhongPhaiDuongVao` · `ContactWorkflowHttpTest` · `QualityChiQuaWorkflowTest` | |
| 11.1.2 | Chỉ xử lý ở tốc độ con người | **Một phần** | chỉ có hạn mức lượt gọi (11.1.3) | ⛔ kiểm thời gian tối thiểu giữa các bước (vd. gửi biểu mẫu) |
| 11.1.3 | Hạn mức nghiệp vụ theo người dùng | **Đạt** | `HanMucNguoiDungFilter.java:63-74,85-87` xô API/EXPORT theo `u:<người dùng>@<IP>` · `RateLimitPolicy.java:73,76-87,112-113` EXPORT 30/giờ, sửa được qua `settings` `limits.rate.export-per-hour`, kẹp 1–100 · `HanMucTheoNguoiDungHttpTest#haiNguoiCungIpKhongChungXo` · `HanMucKetXuatTest#moiEndpointTraTepDuocXepDungXo` · hạn mức tải lên `AttachmentQuotaTest` | |
| 11.1.4 | Chống tự động hoá (rút dữ liệu, spam, tải tệp) | **Một phần** | xô PUBLIC 300/phút/IP `RateLimitPolicy.java:65` · reCAPTCHA có chỗ cắm `InboundSubmissionGate.java:121` | ⛔ reCAPTCHA **tắt mặc định** (`InboundSubmissionGate.java:133`), FE ⛔ gửi token ⇒ biểu mẫu liên hệ/góp ý chỉ còn hạn mức IP (kết hợp 5.2.3) |
| 11.1.5 | Có mô hình đe doạ cho logic nghiệp vụ | **Không đạt** | grep `threat\|STRIDE\|mô hình đe doạ` trong `docs/`, `.claude/` = 0 tài liệu | |

## V12 — Tệp và tài nguyên

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 12.1.1 | Không nhận tệp lớn gây đầy đĩa/DoS | **Đạt** | `application.yml:170,173` `max-file-size 120MB`, `max-request-size 130MB` · 413 `GlobalExceptionHandler.java:135-146` · hạn mức nghiệp vụ `AttachmentService.java:144-147` + `FileValidator.java:170` · `UploadTooLargeResponseTest#vuotTranTra413ChuKhongPhai500` · `UploadSizeCeilingTest` · `AttachmentQuotaTest` | nginx `client_max_body_size 0` ở `/api/` (`default.conf.template:208,283,336`) — trần nằm hoàn toàn ở Spring |
| 12.3.1 | Tên tệp người dùng không dùng thẳng với hệ tệp | **Đạt** | khoá lưu kho `FileValidator.java:185` `UUID.randomUUID() + "." + safe` · đuôi theo MIME đã nhận diện `AttachmentService.java:164` · `MediaLibraryTest#tenLuuXuongKhoLaNgauNhien` | |
| 12.3.2 | Tên tệp không dẫn tới tạo/sửa/xoá tệp cục bộ | **Một phần** | như 12.3.1 | ⛔ `backend/hr/src/main/java/com/songnhue/hr/application/HoSoTaiLieuService.java:317,349-357` đặt **tên gốc chưa làm sạch** làm tên mục ZIP ⇒ tên chứa `../` có thể ghi tệp ra ngoài thư mục giải nén trên **máy người tải** (zip-slip). Chưa đo `getOriginalFilename()` có giữ `/` hay không |
| 12.3.3 | Tên tệp không dẫn tới RFI/SSRF | **Đạt** | tên gốc chỉ lưu cột `original_name` (`Attachment.java:44`), ⛔ dùng dựng URL | |
| 12.3.4 | Chống Reflective File Download | **Một phần** | presigned `ObjectStorage.java:153-154` `attachment; filename*=UTF-8''…` + `PresignedTenTepTest` · phát trực tiếp `HttpHeaderText.java:35` thay `\r\n"\\` | Đường phát trực tiếp ⛔ có `filename*`; ký tự ngoài ASCII đi thẳng vào header |
| 12.3.5 | Metadata tệp không vào lệnh hệ điều hành | **Đạt** | xem 5.3.8 | |
| 12.4.1 | Lưu ngoài web root, quyền hạn chế | **Đạt** | `AttachmentService.java:167` `storage.put(bucket, …)` vào MinIO; nginx biên ⛔ có `root` ngoài `/var/www/certbot` (`default.conf.template:105`) | |
| 12.4.2 | Quét virus tệp tải lên | **Một phần** | `VirusScanHandler.java:176-190` ClamAV INSTREAM · lỗi quét ⇒ ném, ⛔ đánh dấu sạch (dòng 96-100) · `deploy/compose.prod.yml:233-234,334` (staging `include` tệp này) · `VirusScanPhanLoaiTest#baKetCuc` · `ClamavTranLuongTest` · `MediaLibraryTest#tepChiDungDuocSauKhiQuet` | ⛔ `APP_CLAMAV_HOST` rỗng ⇒ `VirusScanHandler.java:86-91` + `Attachment.java:123-126` cho tệp sang **READY** (mở cửa, chỉ ghi `SKIPPED`). Dịch vụ `clamav` mới vào kho 14/09; T61.24 (`QuetLaiTepService.java`) quét lại tệp `SKIPPED` khi khởi động có ClamAV — **chưa đo** cả hai đã chạy trên VPS-1/VPS-2 |
| 12.5.1 | Tầng web chỉ phục vụ đuôi tệp cho phép | **Một phần** | nginx biên thuần proxy; admin chỉ chép `dist` (`admin-app.Dockerfile:224`), chặn `.map` (dòng 200-202) | ⛔ luật chung chặn `/\.(git\|env)`, `*.bak`, `*.swp` |
| 12.5.2 | Tệp tải lên không bao giờ chạy như HTML/JS | **Không đạt** | allowlist MIME theo magic bytes `FileValidator.java:86` · tài liệu `attachment` (`PublicPortalController.java:464`) | ⛔ `PublicPortalController.java:413-422` phát tệp `SITE_CONFIG` **`inline`** với `Content-Type` gốc — gồm `image/svg+xml` (`SiteConfigService.java:131`) — trên origin cổng có CSP `script-src 'unsafe-inline'`. Cộng lỗ 5.2.7 ⇒ SVG chạy script khi mở trực tiếp. Tên miền files (MinIO) ⛔ đặt nosniff/CSP ở biên (`edge-headers.conf:27,49` chỉ HSTS + X-Robots-Tag) |
| 12.6.1 | Máy chủ chỉ gọi ra các đích cho phép | **Một phần** | reCAPTCHA URI cố định `RecaptchaClient.java:57` · revalidate từ cấu hình `PortalRevalidateClient.java:89` · nguồn thuỷ văn: danh sách **cấm** `DiaChiNguon.java:214-240` | ⛔ allowlist tên miền; ⛔ giới hạn egress ở mức mạng compose |

## V13 — API và dịch vụ web

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 13.1.1 | Mọi thành phần dùng cùng mã hoá/bộ phân tích | **Chưa đo** | | Next proxy (`route.ts`) ↔ Spring: ⛔ có phép kiểm so cách hai bên hiểu đường dẫn mã hoá (`%2F`, `..`) |
| 13.1.3 | URL API không mang thông tin nhạy cảm | **Đạt** | xem 3.1.1 và 8.3.1 | |
| 13.2.1 | Chỉ bật phương thức HTTP hợp lệ cho từng hành động | **Đạt** | mỗi handler khai quyền riêng `PermissionInterceptor.java:95-113` · `DenyByDefaultTest#everyEndpointDeclaresItsGuard` · `RbacMatrixTest#readOnlyRolesHoldNoWritePermission` | |
| 13.2.2 | Kiểm lược đồ JSON trước khi nhận | **Một phần** | Bean Validation trên record (5.1.3) | ⛔ JSON Schema; 13 thân yêu cầu thiếu `@Valid` |
| 13.2.3 | API dùng cookie được chống CSRF | **Đạt** | xem 4.2.2 | |
| 13.3.1 | Kiểm XSD cho SOAP | **N/A** | | ⛔ SOAP |

## V14 — Cấu hình

| Mã | Yêu cầu | Kết luận | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| 14.2.1 | Thành phần cập nhật, có quét phụ thuộc khi build | **Một phần** | `.github/workflows/security-scan.yml:33` cron hằng ngày, `:209` `dependency-check:aggregate`, `:311` `npm audit --audit-level=moderate` · `backend/pom.xml:502` `failBuildOnCVSS 7` · `ci.yml:696-698` `dependency-review-action` `fail-on-severity: high` · `PhuQuetCveTest`, `CanhBaoQuetCveTest` | ⛔ `.github/dependabot.yml` ⛔ tồn tại. Số CVE ≥ 7 trên commit này **chưa đo** (chỉ có ở artifact CI). Suppression `backend/dependency-check-suppressions.xml:180` hạn 15/10 ghi về Spring 6.2.x trong khi stack là Boot 4.1.1 — cần rà |
| 14.2.2 | Gỡ tính năng/tài liệu/mẫu thừa | **Một phần** | nginx chặn `deploy/nginx/snippets/chan-tai-lieu-api.conf:44-46` + `NginxApiDocsBlockedTest#moiKhoiServerUngDungDeuChanTaiLieuApi` · actuator `application.yml:357` `health,info,prometheus` · seed `SeedGateTest#seedKhongNamTrongLocationMacDinh` | ⛔ springdoc vẫn **bật trong ứng dụng** (`application.yml:346-350` chỉ đặt path, ⛔ `enabled: false`) — chặn chỉ ở một lớp |
| 14.2.3 | Tài nguyên CDN có SRI | **N/A** | xem 10.3.2 | ⛔ dùng CDN |
| 14.3.2 | Tắt chế độ debug ở production | **Đạt** | `application.yml:200-201` · `deploy/docker/public-web.Dockerfile:38` `NODE_ENV=production` · grep `devtools` trong `pom.xml` = 0 | |
| 14.3.3 | Header không lộ phiên bản | **Đạt** | `default.conf.template:67` `server_tokens off` · `admin-app.Dockerfile:115` `server_tokens off` · `next.config.ts:70` `poweredByHeader: false` | Chưa đo trên máy thật (ZAP 10036/10037) |
| 14.4.1 | Content-Type kèm bộ ký tự an toàn | **Một phần** | CSV `ContactController.java:170` `text/csv; charset=utf-8` | JSON trả `application/json` ⛔ `charset`; chưa đo HTML trên máy thật |
| 14.4.2 | API trả `Content-Disposition: attachment` | **Không đạt** | chỉ tệp tải về có (`StationController.java:300`, `ConstructionDocumentController.java:148`); phản hồi JSON ⛔ | Rủi ro thấp khi đã có nosniff |
| 14.4.3 | Có Content-Security-Policy | **Một phần** | admin `admin-app.Dockerfile:102` (`script-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`) + `NginxSecurityHeadersTest#cspChatOChoDangKe` · cổng `next.config.ts:41-57` + `csp.test.ts` | Cổng: `script-src 'self' 'unsafe-inline'` (`next.config.ts:43`) — chính chỉ thị làm 5.2.7/5.3.3 chạy được. Tên miền files ⛔ CSP |
| 14.4.4 | `X-Content-Type-Options: nosniff` | **Đạt** | `admin-app.Dockerfile:98` · `next.config.ts:98` | Tên miền files ⛔ đặt ở biên (12.5.2) |
| 14.4.5 | HSTS cho mọi phản hồi + tên miền con | **Đạt** | `edge-headers.conf:27` `max-age=31536000; includeSubDomains` include ở từng `location` (`default.conf.template:211,217,277,286,292,344`) | `location = /healthz` và `/actuator/prometheus` ⛔ include; ⛔ bài kiểm cho template biên (`NginxSecurityHeadersTest` chỉ đọc `admin-app.Dockerfile`) |
| 14.4.6 | Referrer-Policy phù hợp | **Đạt** | `admin-app.Dockerfile:100` · `next.config.ts:100` `strict-origin-when-cross-origin` | |
| 14.4.7 | Chống nhúng khung | **Đạt** | `admin-app.Dockerfile:99` `X-Frame-Options DENY` + `frame-ancestors 'none'` · `next.config.ts:53,99` | |
| 14.5.1 | Chỉ nhận phương thức HTTP đang dùng | **Một phần** | Spring chỉ ánh xạ phương thức khai báo; `GlobalExceptionHandler.java:107-117` gộp `HttpRequestMethodNotSupportedException` về **400** | ⛔ `limit_except` ở nginx; `CsrfFilter.java:44` coi `TRACE` là an toàn; ⛔ ghi cảnh báo khi gặp phương thức lạ |
| 14.5.2 | Không dùng `Origin` cho xác thực/phân quyền | **Đạt** | `CsrfFilter.java:85` so header ↔ cookie; grep `getHeader("Origin"\|"Referer")` trong `src/main` = 0 · `CsrfFilterTest` | |
| 14.5.3 | CORS allowlist chặt, không nhận `null` | **Đạt** | grep `cors\|allowedOrigin\|Access-Control` trong `backend/*/src/main` = 0 ⇒ ⛔ bật CORS; kiến trúc cùng origin qua proxy · `FrontendSameOriginTest` | |

---

## 16. Khoảng trống cần xử lý

Xếp theo mức nghiêm trọng. Mỗi dòng: **mã ASVS · mất gì**. ⛔ Lượt đánh giá này **không sửa mã** —
mỗi dòng cần một task trong `.claude/master-tracking.md` trước khi làm.

### 16.1 Nghiêm trọng — chặn go-live

1. **4.3.1 · 2.5.6 · 2.5.5 — Vượt 2FA của tài khoản quản trị bằng mật khẩu.** Ai có mật khẩu của một
   SUPER_ADMIN/ADMIN/ADMIN_HR thì gọi `POST /auth/2fa/enroll` với vé challenge, thay secret TOTP, đăng
   nhập và nắm toàn quyền hệ thống (gồm khôi phục CSDL và trường 🔒 nhân sự); chủ tài khoản ⛔ nhận
   thông báo và chỉ biết khi 2FA của họ ngừng chạy. `AuthService.java:136-139` · `TotpService.java:115-122`
   · 0 bài kiểm cho luồng 2FA.
2. **4.1.3 — ADMIN tự cấp quyền đọc dữ liệu 🔒.** Một ADMIN tự thêm `hr:employee:view-sensitive` vào vai
   trò của mình (hoặc tự gán `ADMIN_HR`) và đọc CCCD, số tài khoản, mã số thuế của toàn bộ CBNV — đúng
   thứ đặc tả loại trừ. `UserAdminService.java:396-431` (T54.4).
3. **5.2.7 · 12.5.2 · 14.4.3 — SVG lưu trữ chạy script trên tên miền cổng.** Người có quyền cấu hình cổng
   (hoặc tài khoản của họ bị chiếm) tải lên một SVG vượt bộ lọc; ai mở tệp đó trên tên miền Công ty
   chạy mã của kẻ tấn công (giả mạo trang, lừa đảo mang thương hiệu Công ty). `SvgSanitizer.java:52,90-92`
   (đã đo vượt được) · `PublicPortalController.java:413-422` · `next.config.ts:43`.

### 16.2 Cao

4. **5.3.3 — Liên kết `javascript:` trong menu và kênh mạng xã hội.** Người sửa menu/cài đặt chèn được
   mã chạy khi khách bấm liên kết trên cổng. `MenuService.java:233` · `routes.ts:146-147` ·
   `SiteFooter.tsx:267`. Chưa đo trên trình duyệt.
5. **8.2.1 — Phản hồi API ⛔ `Cache-Control: no-store`.** Hồ sơ CBNV và trường 🔒 có thể nằm lại trong
   bộ đệm trình duyệt/proxy của một máy dùng chung sau khi người dùng đã đăng xuất.
6. **2.2.3 — ⛔ báo khi mật khẩu/2FA đổi.** Người dùng ⛔ phát hiện được tài khoản bị chiếm cho tới khi họ
   bị đẩy ra. `SecurityEventService.java:54-68`.
7. **5.2.3 · 11.1.4 — Biểu mẫu liên hệ biến máy chủ thư Công ty thành công cụ gửi thư rác.** Email ⛔
   kiểm định dạng, thư xác nhận bật mặc định, reCAPTCHA tắt mặc định ⇒ kẻ gian gửi thư mang nội dung
   tuỳ ý tới địa chỉ tuỳ ý dưới tên Công ty; tên miền thư bị đưa vào danh sách đen.
   `ContactService.java:118-163` · `ContactAckMailHandler.java:78,95-116` · `InboundSubmissionGate.java:133`.
8. **2.2.1 — Mã TOTP ⛔ khoá theo tài khoản; cổng công khai chuyển tiếp cả API xác thực.** Dò mã 2FA
   chỉ vướng hạn mức IP; qua tên miền công khai còn né vùng `api_auth` 20 lượt/phút của nginx.
   `TotpService.java:224-227` · `route.ts:55,105-125`.
9. **5.2.6 · 12.6.1 — Chống SSRF chỉ nhìn IP dạng chữ.** Một URL nguồn thuỷ văn trỏ tên miền phân giải
   về địa chỉ nội bộ khiến máy chủ gọi vào mạng compose (MinIO, PostgreSQL, actuator). `DiaChiNguon.java:225`.
10. **8.3.2 · 8.3.3 — Thiếu thông báo quyền riêng tư, đồng ý, và quyền xuất/xoá dữ liệu.** Công ty thu dữ
    liệu cá nhân của người dân qua biểu mẫu mà ⛔ đáp ứng nghĩa vụ thông báo/đồng ý của NĐ 13/2023.

### 16.3 Trung bình

11. **3.3.2 — Phiên ⛔ có trần tuyệt đối.** Một refresh token bị đánh cắp mà được dùng ít nhất một lần mỗi
    7 ngày sống mãi. `RefreshTokenService.java:157`.
12. **8.2.3 — Đăng xuất ⛔ xoá bộ đệm React Query.** Người dùng kế tiếp trên cùng tab (trước khi tải lại)
    có thể thấy dữ liệu của người trước. `AuthProvider.tsx:138-151`.
13. **12.4.2 — Quét virus mở cửa khi thiếu cấu hình, và chưa đo trên máy chủ.** Thiếu `APP_CLAMAV_HOST`
    thì mọi tệp (kể cả hồ sơ CBNV) phát ra ngoài ⛔ quét. `VirusScanHandler.java:86-91` · `Attachment.java:123-126`.
14. **7.1.2 — Log vi phạm ràng buộc mang giá trị dữ liệu.** Username, email, vân tay CCCD có thể nằm trong
    log ứng dụng (nhiều người đọc hơn bảng gốc). `GlobalExceptionHandler.java:158-161`.
15. **2.1.1 · 2.1.7 · 2.1.8 · 2.1.9 — Chính sách mật khẩu lệch ASVS.** Tối thiểu 10 (hạ được 8), bắt tổ hợp
    chữ+số, ⛔ đối chiếu danh sách mật khẩu đã lộ, ⛔ thước đo độ mạnh ⇒ người dùng chọn `Songnhue2026`
    và hệ nhận. `SettingKeys.java:45` · `PasswordPolicyService.java:107-115`.
16. **2.1.2 · 2.1.3 — Mật khẩu > 72 byte ra lỗi 500.** Người đặt cụm mật khẩu tiếng Việt dài nhận *"Lỗi hệ
    thống"* thay vì một câu nói rõ giới hạn. `PasswordPolicyService.java:38,46`.
17. **3.7.1 — Gán vai trò/đặt quyền ⛔ đòi xác thực lại.** Một phiên bị chiếm (máy mở không khoá) đủ để nâng
    quyền. `UserAdminController.java:111,152`.
18. **12.3.2 — Tên mục ZIP lấy nguyên tên gốc.** Tệp nén hồ sơ có thể ghi tệp ra ngoài thư mục giải nén
    trên máy người tải. `HoSoTaiLieuService.java:317`.
19. **14.2.1 — ⛔ Dependabot; số CVE trên commit này chưa đo; một suppression có thể đã lỗi thời.**
20. **14.2.2 — Swagger/OpenAPI bật trong ứng dụng, chỉ nginx chặn.** Một khối `server` mới quên include
    snippet là lộ toàn bộ bản đồ API. `application.yml:346-350`.
21. **5.1.3 · 5.1.4 · 13.2.2 — 13 thân yêu cầu thiếu `@Valid`.** Ràng buộc khai trên DTO ⛔ có hiệu lực ở
    các đường ấy. `PublicPortalController.java:315` và 12 chỗ khác.
22. **4.2.1 — Chống IDOR chỉ có phép kiểm ở module có phạm vi.**
23. **11.1.5 — ⛔ có mô hình đe doạ.** Các lỗ ở 16.1 đều là lỗ *logic* mà một lượt STRIDE trên luồng đăng
    nhập đã chỉ ra.

### 16.4 Thấp

24. **2.3.1** mật khẩu tạm do Admin gõ, ⛔ hết hạn · **2.5.4** tên `superadmin` cố định ·
    **3.4.4** ⛔ tiền tố cookie `__Host-`/`__Secure-` · **4.3.2 · 12.5.1** ⛔ luật chặn tệp ẩn ở nginx,
    `public-web` chưa đo · **5.2.2** họ tên/chủ đề ⛔ giới hạn độ dài ở DTO · **5.5.2** ⛔ trần giải nén
    XLSX · **8.3.1** mã số thuỷ văn trên query string của hệ nguồn (bên thứ ba ép) · **8.3.4** ⛔ tài liệu
    phân loại dữ liệu · **9.1.2** chưa đo TLS bằng công cụ · **11.1.2** ⛔ kiểm tốc độ con người ·
    **12.3.4** ⛔ `filename*` ở đường phát trực tiếp · **14.4.1** JSON ⛔ `charset` · **14.4.2** JSON ⛔
    `Content-Disposition` · **14.5.1** 405 bị gộp về 400, ⛔ `limit_except`.

### 16.5 Chưa đo — cần một phép đo trước khi kết luận

- **5.1.1** tham số trùng lặp · **5.3.2** ký tự ngoài BMP qua HTTP · **10.3.3** DNS tên miền con treo ·
  **13.1.1** Next proxy ↔ Spring hiểu đường dẫn mã hoá có giống nhau không.
- Toàn bộ các dòng ghi *"chưa đo trên máy thật"* (3.4.1, 9.1.x, 14.3.3, 14.4.x): chạy
  `tools/zap/zap-baseline.sh` vào staging rồi đối chiếu báo cáo với bảng này.

## 17. Giới hạn của lượt đánh giá

- ⛔ Chạy bộ kiểm tự động: *"có bài kiểm"* ở cột bằng chứng là **đã thấy lớp/phương thức trong kho**,
  ⛔ phải *"đã thấy nó xanh hôm nay"*.
- Một phần bằng chứng V5/V7/V8/V10/V11/V12/V14 được thu bằng lượt đọc song song; các dòng dẫn tới
  kết luận *Không đạt* đều được mở lại tệp và đọc lại dòng trước khi ghi (SVG còn được **chạy** lại).
- Grep = 0 là bằng chứng **vắng mặt trong phạm vi đã grep**, ⛔ phải bằng chứng tuyệt đối.
- Số dòng `tệp:dòng` đúng tại `7421719`; mã đổi thì số dòng trôi.

## 18. Theo dõi sau đánh giá — cập nhật 15/09/2026

Bảng trên giữ nguyên kết luận **tại `7421719`** (bản ghi của lượt đánh giá, ⛔ sửa lùi). Cùng ngày, phía phát
triển **đối chiếu lại trên mã và đo** các khoảng trống nặng nhất trước khi vá — mỗi dòng dưới đây có task,
bài kiểm, và lượt phá-bản-vá chứng minh bài kiểm bắt được lỗ cũ:

| Khoảng trống | Task | Trạng thái | Phép đo trước khi vá |
|---|---|---|---|
| 16.1 #1 — vượt 2FA bằng đăng ký lại | `T61.30` | ✅ vá · `HaiBuocHttpTest` | gỡ bản vá ⇒ máy chủ trả `secret` mới qua HTTP |
| 16.1 #2 — ADMIN tự cấp quyền 🔒 | `T54.4` ⇒ vá ở `T61.43` | ✅ vá (15/09) · `CapQuyenVuotQuyenBiChanTest` | ADMIN có `adm:role:manage`, vai trò `ADMIN` khai `is_system = FALSE` ⇒ ba cú bấm là tự cấp lại đúng quyền đặc tả loại trừ |
| 16.1 #3 — SVG chạy script | `T61.32` | ✅ vá (lọc trên cây XML) · `SvgSanitizerTest` | ⭐ thêm đường vượt thứ hai (tiền tố namespace) — Playwright: chạy ở chromium/firefox/webkit |
| 16.2 #4 — liên kết `javascript:` | `T61.34` + `T63.4` | ✅ vá TRỌN (16/09) · hiển thị `lienKetAnToan.test.ts` · ghi `DiaChiLienKetTest` + `DiaChiLienKetChanLucGhiHttpTest` | React 18.3.1 chỉ CẢNH BÁO chứ ⛔ chặn; gỡ chốt ghi ⇒ `"success":true` kèm nguyên chuỗi `javascript:` đã lưu ở cả menu lẫn settings |
| 16.2 #5 — thiếu `no-store` | `T61.35` | ✅ vá · `KhongLuuDemHttpTest` | 0 nơi đặt header |
| 16.2 #8 — dò mã TOTP | `T61.33` | ✅ vá (khoá theo tài khoản) · `HaiBuocHttpTest#saiMaTotpBiKhoa` | ⭐ nặng hơn bảng ghi: mật khẩu đúng đặt bộ đếm về 0 TRƯỚC bước 2FA |
| 16.2 #6 · #7 · #9 · #10 | `T61.36`→`T61.39` | ✅ cả bốn đã vá (16/09) · `DatLaiMatKhauHttpTest` · `BieuMauCongKhaiChongLamDungTest` · `DiaChiNguonTest` · `QuyenRiengTuHttpTest` | email biểu mẫu công khai ⛔ kiểm định dạng ⇒ `"x"` thành người nhận thư; hạn mức 300/PHÚT của đường ĐỌC dùng cho đường GHI |
| 16.3 · 16.4 · 16.5 | `T61.40` | 🟡 **31 dòng** đo được (sổ cũ ghi 23) — đã vá ở `T61.40`+`T61.47`; còn `T61.48` (7 mục chờ QuanTran quyết) · `T61.49` (5 mục chỉ đo được trên staging) | ⚠ Một dòng của bảng này từng **mồ côi**: ASVS 8.2.3 (đăng xuất ⛔ xoá đệm truy vấn) ⛔ nằm trong T61.48 lẫn T61.49 — vá ở `T63.3` |

⚠ Phép đo trên hệ đang chạy (ZAP baseline vào staging) vẫn **chưa chạy** — việc của QuanTran, xem `tools/zap/README.md`.
