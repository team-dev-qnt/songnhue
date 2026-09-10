package com.songnhue.core.domain.security;

/**
 * Danh mục sự kiện bảo mật + mức nghiêm trọng đi kèm.
 *
 * <p>Mức nghiêm trọng gắn ngay vào loại sự kiện chứ không để nơi gọi tự chọn: cùng một loại sự kiện
 * mà chỗ này ghi WARNING chỗ kia ghi INFO thì luật cảnh báo bên Grafana (WS-7) không viết được.
 *
 * <p>⚠ Thêm loại mới thì phải xem lại luật cảnh báo ở {@code docs/runbook/} — sự kiện DANGER/CRITICAL
 * mà không ai nhận thông báo thì ghi cũng như không.
 */
public enum SecurityEventType {

    // --- Đăng nhập ------------------------------------------------------------
    LOGIN_SUCCESS(Severity.INFO),
    LOGIN_FAILED(Severity.WARNING),
    /** Vượt ngưỡng số lần sai → khoá tạm (M5.16 — cảnh báo Admin gần thời gian thực). */
    LOGIN_LOCKED(Severity.DANGER),
    /** Đăng nhập ngoài khung giờ hành chính đọc từ `settings` (M5.16, chốt F5). */
    LOGIN_OUTSIDE_OFFICE_HOURS(Severity.WARNING),
    LOGIN_DISABLED_ACCOUNT(Severity.WARNING),

    // --- Token & phiên --------------------------------------------------------
    /** ⚠ Nghiêm trọng nhất nhóm này: gần như chắc chắn refresh token đã bị đánh cắp (§4.1). */
    REFRESH_REUSE_DETECTED(Severity.CRITICAL),
    SESSION_REVOKED(Severity.INFO),
    LOGOUT(Severity.INFO),

    // --- Mật khẩu & 2FA -------------------------------------------------------
    PASSWORD_CHANGED(Severity.INFO),
    TWO_FACTOR_ENROLLED(Severity.INFO),
    TWO_FACTOR_FAILED(Severity.WARNING),
    TWO_FACTOR_RECOVERY_USED(Severity.DANGER),

    // --- Phân quyền -----------------------------------------------------------
    /** Thiếu permission — tầng 2 chặn (AUTH-3001). */
    ACCESS_DENIED_PERMISSION(Severity.WARNING),
    /** Truy cập dữ liệu ngoài đơn vị — tầng 3 chặn (AUTH-3002). Đáng ngờ hơn hẳn thiếu quyền. */
    ACCESS_DENIED_SCOPE(Severity.DANGER),
    CSRF_REJECTED(Severity.DANGER),

    // --- Quản trị -------------------------------------------------------------
    /** Kích hoạt tài khoản quản trị tối cao bằng lệnh bootstrap (T5.7). */
    ADMIN_BOOTSTRAP(Severity.DANGER),

    // --- Sao lưu & khôi phục (WS-7) -------------------------------------------
    /** Bật/tắt chế độ bảo trì — cả hệ thống ngừng nhận ghi (T7.6). */
    MAINTENANCE_MODE_CHANGED(Severity.DANGER),
    BACKUP_CREATED(Severity.INFO),
    /**
     * ⚠ Sao lưu hỏng là sự kiện <b>nghiêm trọng</b>, không phải cảnh báo thường. Không có PITR
     * (architecture-review.md §6.5) nên bản dump đêm là đường phục hồi duy nhất — mất nó là hệ thống
     * đang chạy không lưới an toàn, dù mọi thứ khác vẫn xanh.
     */
    BACKUP_FAILED(Severity.CRITICAL),
    /** Bắt đầu ghi đè toàn bộ CSDL bằng một bản dump (M5.11). */
    DATABASE_RESTORE_STARTED(Severity.CRITICAL),
    DATABASE_RESTORE_FINISHED(Severity.CRITICAL),
    DATABASE_RESTORE_FAILED(Severity.CRITICAL),

    // --- Credential bên thứ ba (WS-28, conventions.md §4.7) --------------------
    /**
     * Mã số truy cập một nguồn dữ liệu bên ngoài vừa được ĐẶT LẦN ĐẦU, THAY, hoặc XOÁ.
     *
     * <p>{@code detail} ghi mã nguồn và hành động — ⛔ không bao giờ ghi giá trị mã số.
     *
     * <p>⚠ <b>Cố ý KHÔNG có sự kiện "đã dùng mã số".</b> §4.7 đòi ghi nhận cả lượt sử dụng, nhưng
     * poller gọi nguồn <b>2 phút/lần</b> — 720 dòng mỗi ngày cho một nguồn. Nhật ký bảo mật là chỗ
     * người ta tìm bốn năm dòng đáng ngờ giữa hàng nghìn dòng bình thường; đổ 720 dòng đều đặn vào
     * đó là chôn sống chính những dòng cần thấy, và bảng lưu 5 năm. Vế "dùng" được ghi ở
     * {@code sync_logs} (mỗi lượt polling một dòng, có kết quả) — đúng chỗ để tra, và
     * {@link #EXTERNAL_CREDENTIAL_DECRYPT_FAILED} giữ lại phần thật sự thuộc về bảo mật.
     */
    EXTERNAL_CREDENTIAL_CHANGED(Severity.DANGER),

    /**
     * Không giải mã được credential của một nguồn.
     *
     * <p>Nghĩa là bản mã và khoá AES hiện tại không khớp — hoặc khoá vừa xoay mà chưa mã hoá lại,
     * hoặc CSDL được khôi phục từ một bản sao lưu cũ hơn lần xoay khoá. Cả hai đều là sự cố hạ tầng
     * im lặng: nguồn ngừng lấy dữ liệu, và nếu chỉ nhìn log ứng dụng thì triệu chứng giống hệt
     * "nguồn không phản hồi".
     */
    EXTERNAL_CREDENTIAL_DECRYPT_FAILED(Severity.CRITICAL),

    // --- Dữ liệu cá nhân nhạy cảm (WS-51, MOD-04, NĐ 13/2023/NĐ-CP) -----------
    /**
     * Ai đó vừa <b>ĐỌC</b> trường 🔒 của một hồ sơ nhân sự — CCCD, lương, số tài khoản, MST, BHXH.
     *
     * <p><b>Vì sao ghi lượt ĐỌC, trong khi mọi nhóm sự kiện khác chỉ ghi lượt GHI:</b> NĐ 13/2023
     * xếp các trường này vào dữ liệu cá nhân <i>nhạy cảm</i>, và nhật ký kiểm toán
     * ({@code audit_logs}) chỉ sinh dòng khi có <b>thay đổi</b>. Một người có quyền
     * {@code hr:employee:view-sensitive} mở lần lượt toàn bộ hồ sơ để chép số tài khoản sẽ ⛔ không
     * để lại <b>một dòng nào</b> ở bất kỳ bảng nào — đúng thứ nguyên tắc tối thiểu (CN-04.7) cần
     * nhìn thấy.
     *
     * <p>⚠ Đối chiếu có ý thức với {@link #EXTERNAL_CREDENTIAL_CHANGED}, nơi lượt "đã dùng" bị
     * <b>cố ý bỏ</b> vì poller gọi 720 lần/ngày. Ở đây khối lượng ngược hẳn: một người mở hồ sơ vài
     * lần một ngày, nên mỗi dòng vẫn là một dòng người đọc được. Số lượng, ⛔ không phải nguyên tắc,
     * là thứ quyết định khác nhau giữa hai chỗ.
     *
     * <p>⛔ {@code detail} chỉ ghi <b>mã nhân viên</b> — ⛔ không bao giờ ghi giá trị vừa đọc.
     */
    HR_SENSITIVE_FIELDS_READ(Severity.WARNING),

    // --- Liên kết tài khoản ↔ hồ sơ CBNV (T51.8, CN-05.1) ----------------------
    /**
     * Một tài khoản vừa được <b>gán</b> hoặc <b>gỡ</b> liên kết tới một hồ sơ CBNV.
     *
     * <p><b>Vì sao DANGER, trong khi mọi lượt sửa tài khoản khác chỉ vào {@code audit_logs}:</b>
     * cột {@code users.employee_id} ⛔ không phải một trường hồ sơ — nó là <b>một quyền</b>. Vế thứ
     * hai của CN-04.7 suy quyền tự đọc CCCD/lương/số tài khoản <i>thẳng từ nó</i>, nên trỏ tài khoản
     * của mình sang hồ sơ người khác là tự cấp cho mình quyền đọc dữ liệu cá nhân nhạy cảm của
     * người ấy. Một thao tác cấp quyền phải nằm ở nhật ký <b>bảo mật</b>, ⛔ không chỉ ở nhật ký
     * thay đổi dữ liệu.
     *
     * <p>⚠ Đây là <b>nửa còn lại</b> của {@link #HR_SENSITIVE_FIELDS_READ}. Dòng "ai đọc" một mình
     * ⛔ không trả lời được câu quan trọng nhất — <i>người ấy có quyền đọc từ bao giờ, và ai cho</i>.
     * Hai loại sự kiện ghép lại mới dựng được dòng thời gian: <b>gán liên kết</b> → <b>lượt đọc</b>.
     *
     * <p>⛔ {@code detail} chỉ ghi tên tài khoản và <b>mã</b> nhân viên — ⛔ không ghi họ tên, ⛔
     * không ghi bất kỳ trường 🔒 nào.
     */
    ACCOUNT_EMPLOYEE_LINK_CHANGED(Severity.DANGER);

    private final Severity severity;

    SecurityEventType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    /** Khớp CHECK {@code ck_security_events_severity}. */
    public enum Severity {
        INFO,
        WARNING,
        DANGER,
        CRITICAL
    }
}
