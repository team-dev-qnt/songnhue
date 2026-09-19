package com.songnhue.core.common.ratelimit;

import java.time.Duration;

/**
 * Ba nhóm hạn mức theo conventions.md §4.5.
 *
 * <p>Giá trị để ở đây (hằng số trong mã) chứ không ở bảng {@code settings} — khác với đa số tham số
 * khác của hệ thống. Lý do: đây là <b>chốt chặn bảo mật</b>, không phải tham số nghiệp vụ. Nếu để
 * Admin sửa được qua UI thì tài khoản Admin bị chiếm sẽ tự nới hạn mức đăng nhập trước khi dò mật
 * khẩu. Muốn đổi thì phải qua review mã nguồn và deploy.
 *
 * <p>Riêng chính sách khoá tài khoản (số lần sai, thời gian khoá — M5.15) <i>là</i> tham số cấu
 * hình và nằm ở bảng {@code settings}. Hai thứ khác nhau: rate limit chặn ở tầng hạ tầng theo IP,
 * lockout chặn ở tầng nghiệp vụ theo tài khoản.
 *
 * <p>⚠ <b>Ngoại lệ DUY NHẤT — {@link #EXPORT} (T61.27, QuanTran chốt 15/09/2026)</b>: số lượt kết xuất
 * mỗi giờ là câu hỏi <i>nghiệp vụ</i> (người lập 8 báo cáo BCNS + báo cáo vận hành trong một buổi) nên
 * nằm ở {@code settings} ({@link #KHOA_KET_XUAT}). Lý lẽ "Admin bị chiếm tự nới hạn mức" vẫn đúng, nên
 * giá trị bị <b>kẹp trong mã</b> ở {@link #TRAN_KET_XUAT} — sửa trên giao diện ⛔ bao giờ mở được đường rút
 * dữ liệu hàng loạt. {@code LOGIN}/{@code API}/{@code PUBLIC} vẫn là hằng số.
 */
public enum RateLimitPolicy {

    /**
     * Đăng nhập: 30 lượt / 15 phút <b>trên mỗi IP</b>.
     *
     * <p>⚠ Con số này <b>phải rộng hơn hẳn</b> ngưỡng khoá tài khoản ở {@code settings} (mặc định 5
     * lần). Hai lý do, cùng phát hiện khi chạy thử thật:
     *
     * <ul>
     *   <li><b>Đặt bằng nhau thì khoá tài khoản không bao giờ kích hoạt.</b> Rate limit nằm ở filter
     *       (trước controller) nên luôn chặn trước; người dùng nhận {@code SYS-0002} thay vì
     *       {@code AUTH-0003}, và tham số "số lần sai bị khoá" (M5.15) mà Admin chỉnh trên UI trở
     *       thành vô nghĩa — đúng thứ nằm trong hạng mục nghiệm thu.
     *   <li><b>Cả Công ty đi ra Internet qua một IP NAT.</b> Với 200 người dùng nội bộ, hạn mức 5
     *       lượt/15 phút cho <i>toàn bộ</i> văn phòng nghĩa là vài người gõ nhầm mật khẩu buổi sáng
     *       là cả cơ quan không ai đăng nhập được nữa.
     * </ul>
     *
     * <p>Vai trò của lớp này là <b>chặn khối lượng</b> (một máy dò hàng nghìn lượt), còn việc bảo vệ
     * từng tài khoản là của lockout theo tài khoản — nó đếm đúng người, không đếm nhầm hàng xóm.
     *
     * <p>⛔⛔ <b>T61.17 (WS-72) — 30 là trần lượt ⛔ ĐÚNG mật khẩu, ⛔ phải trần mọi lượt.</b> Bộ lọc vẫn tính mọi
     * lượt trước khi cho đi; lượt đúng mật khẩu được trả lại ở {@code LoginAttemptService#hoanLuotDangNhapDung}.
     * Bản trước tính cả lượt đúng, nên với lý do NAT nêu ngay trên, người thứ 31 đăng nhập ĐÚNG trong 15 phút đầu
     * giờ nhận 429 tới hết cửa sổ. Hai vế đo qua HTTP ở {@code DangNhapSauNatHttpTest}.
     */
    LOGIN("login", 30, Duration.ofMinutes(15)),

    /**
     * API thường: 100 lượt / phút cho mỗi <b>người dùng đã xác thực trên một IP</b>; lượt gọi chưa
     * xác thực đếm theo IP. Đếm ở {@code HanMucNguoiDungFilter}, SAU bước kiểm token — T61.17.
     */
    API("api", 100, Duration.ofMinutes(1)),

    /**
     * Cổng công khai: 300 lượt / phút trên mỗi IP — <b>bucket riêng, không dùng chung với {@link
     * #API}</b>.
     *
     * <p>Đây không phải chuyện nới tay cho khách vãng lai. Nếu <b>cả Công ty đi ra Internet qua một IP
     * NAT</b> (chưa đo — T61.17) thì gộp chung với khách lạ theo IP nghĩa là một con bọ tìm kiếm quét cổng thông
     * tin sẽ tiêu hết hạn mức, và người đang soạn bài trong màn hình quản trị nhận {@code SYS-0002}
     * — một sự cố ở phần công khai lan sang phần nội bộ, không dấu vết nào chỉ ra vì sao.
     *
     * <p>Hạn mức cao hơn vì một lượt xem trang gọi nhiều endpoint (cấu hình, menu, banner, danh
     * sách bài), và Next dựng trang phía máy chủ nên các lượt gọi đó dồn vào <i>một</i> IP: chính
     * máy chủ cổng. Đặt bằng {@link #API} là tự khoá trang chủ của mình lúc có vài chục người xem.
     */
    PUBLIC("public", 300, Duration.ofMinutes(1)),

    /**
     * Kết xuất báo cáo: {@code limits.rate.export-per-hour} lượt / giờ (mặc định 30) cho mỗi người dùng
     * đã xác thực trên một IP — mỗi lượt tốn nhiều tài nguyên. Trước T61.17 khoá theo IP ⇒ sau một NAT
     * là 10 lượt/giờ cho CẢ Công ty. Con số trong enum là <b>dự phòng</b> khi thiếu khoá — trùng mặc định
     * seed, nên bài kiểm núm cấu hình ⛔ được dùng 30 làm giá trị thử (T48.7).
     */
    EXPORT("export", 30, Duration.ofHours(1)),

    /**
     * <b>Gửi biểu mẫu công khai</b> (liên hệ · góp ý): 10 lượt / giờ cho mỗi IP — <b>T61.37</b>.
     *
     * <h2>Vì sao {@link #PUBLIC} ⛔ đủ</h2>
     *
     * <p>300 lượt/phút là hạn mức của một người ĐỌC cổng (một lượt xem trang gọi nhiều endpoint).
     * Áp cùng con số cho đường GHI nghĩa là một người lạ gửi được <b>18.000 lượt/giờ</b>, và mỗi
     * lượt sinh: một thư xác nhận tới địa chỉ kẻ gửi tự khai, cộng một thông báo + một thư cho
     * <b>từng cán bộ</b> giữ {@code cms:contact:manage}. Với 10 cán bộ là hệ số nhân <b>11×</b> —
     * máy chủ thư của Công ty thành máy phát tán, và tên miền vào danh sách đen.
     *
     * <p>⚠ Con số 10/giờ chọn theo <b>hành vi thật</b>: một người dân gửi một lượt, hoạ hoằn gửi
     * lại vì gõ nhầm. ⚠⚠ Nó ⛔ phủ được kẻ tấn công có nhiều IP — vế ấy thuộc về reCAPTCHA (chờ
     * khoá G13) và về việc thư xác nhận nay ⛔ gửi khi reCAPTCHA chưa thật sự bật.
     */
    BIEU_MAU_CONG_KHAI("form", 10, Duration.ofHours(1));

    /**
     * Hai đường GHI công khai duy nhất của cổng.
     *
     * <p>⛔ ⛔ Liệt kê TƯỜNG MINH chứ ⛔ bắt mọi {@code POST} dưới {@code /api/v1/public}:
     * {@code POST /articles/{slug}/views} là <b>bộ đếm lượt xem</b>, một lượt mỗi lần mở bài — hạ
     * nó xuống 10/giờ là khoá đếm lượt xem của cả cổng sau mười người đọc.
     */
    private static final java.util.List<String> DUONG_GUI_BIEU_MAU =
            java.util.List.of("/api/v1/public/contacts", "/api/v1/public/feedbacks");

    /** Khoá {@code settings} của {@link #EXPORT} — T61.27. */
    public static final String KHOA_KET_XUAT = "limits.rate.export-per-hour";

    /**
     * Trần cứng của {@link #EXPORT}, ⛔ sửa được từ giao diện. Phải bằng {@code max=} ở cột
     * {@code validation} của khoá (bài kiểm đối chiếu hai nơi — luật 14).
     */
    public static final int TRAN_KET_XUAT = 100;

    /** Kẹp giá trị đọc từ {@code settings} vào {@code [1; TRAN_KET_XUAT]} — 0 hay số âm ⛔ được khoá chết kết xuất. */
    public static int kepKetXuat(int caiDat) {
        return Math.max(1, Math.min(TRAN_KET_XUAT, caiDat));
    }

    /** Đường dẫn đăng nhập — xét trước mọi thứ khác. */
    private static final String DUONG_DANG_NHAP = "/api/v1/auth/login";

    /** Cổng công khai. Xét TRƯỚC kết xuất: ảnh nhúng trang chủ không bao giờ được rơi vào 10/giờ. */
    private static final String TIEN_TO_CONG_KHAI = "/api/v1/public";

    /**
     * Đoạn đường dẫn đánh dấu một lượt <b>kết xuất</b> — T47.11 · DOD3.6.
     *
     * <p>⛔⛔ Bản cũ chỉ có {@code "/export"}, một chuỗi <b>tiếng Anh</b> trong một kho đặt tên
     * <b>tiếng Việt</b>. Đo ngày 14/9/2026: 6 endpoint kết xuất, marker ấy bắt được <b>2</b>. Bốn
     * cái còn lại rơi xuống {@link #API} — <b>100 lượt/phút thay vì 10 lượt/giờ, rộng gấp 600
     * lần</b>. Ba trong bốn cái ấy do chính đợt Phase 3 dựng ra.
     *
     * <p>Nặng nhất là {@code /tai-lieu/zip}: mỗi lượt đọc toàn bộ tài liệu của một hồ sơ CBNV ra
     * khỏi MinIO rồi dựng một tệp tạm. Ở 100 lượt/phút trên một VPS 2 vCPU thì đó vừa là đường tự
     * đánh sập mình, vừa là đường rút dữ liệu cá nhân hàng loạt (NĐ 13/2023).
     *
     * <p>⚠ Mỗi đoạn bắt đầu bằng {@code /} nên nó khớp <b>ranh giới đoạn</b>: {@code /de-xuat}
     * ⛔ không chứa {@code /xuat}. Còn những endpoint trả tệp mà <b>cố ý KHÔNG</b> nằm đây:
     * {@code /gis-layers/{id}/noi-dung} (bản đồ VẼ bằng nó — 10/giờ là màn hình trực ban trắng) và
     * hai đường tải tệp mẫu nhập liệu (vài KB).
     */
    private static final java.util.List<String> MOC_KET_XUAT =
            java.util.List.of("/export", "/xuat", "/zip", "/bao-cao/tai/");

    /**
     * Chính sách cho một đường dẫn.
     *
     * <p>Tách khỏi filter để bài kiểm hỏi thẳng được, ⛔ không phải dựng một {@code
     * HttpServletRequest} giả — và để chỉ có <b>một</b> bản luật, thay vì một bản trong filter và
     * một bản chép lại trong bài kiểm (một bài kiểm chép hằng số của phía bên kia thì nó canh chính
     * nó — T51.15).
     */
    public static RateLimitPolicy choDuongDan(String duongDan) {
        return choYeuCau(duongDan, "GET");
    }

    /**
     * Chính sách cho một lượt gọi — <b>phương thức cũng quyết định</b> (T61.37).
     *
     * <p>Cùng một tiền tố {@code /api/v1/public} mang hai loại lưu lượng khác hẳn nhau: ĐỌC (cả
     * nghìn lượt/phút là bình thường) và GHI (một lượt mỗi người, mỗi tuần). Một con số cho cả hai
     * thì hoặc khoá người đọc, hoặc mở cửa cho máy gửi thư rác.
     */
    public static RateLimitPolicy choYeuCau(String duongDan, String phuongThuc) {
        if (duongDan.startsWith(DUONG_DANG_NHAP)) {
            return LOGIN;
        }
        if (!"GET".equalsIgnoreCase(phuongThuc) && !"HEAD".equalsIgnoreCase(phuongThuc)) {
            for (String d : DUONG_GUI_BIEU_MAU) {
                if (duongDan.equals(d) || duongDan.startsWith(d + "/")) {
                    return BIEU_MAU_CONG_KHAI;
                }
            }
        }
        // ⚠ Công khai xét TRƯỚC kết xuất. Ngược lại thì một đường dẫn công khai lỡ mang chữ `/xuat`
        // sẽ bị hạ xuống trần kết xuất theo GIỜ cho TOÀN BỘ khách của cổng — hỏng theo chiều không ai ngờ.
        if (duongDan.startsWith(TIEN_TO_CONG_KHAI)) {
            return PUBLIC;
        }
        for (String moc : MOC_KET_XUAT) {
            if (duongDan.contains(moc)) {
                return EXPORT;
            }
        }
        return API;
    }

    private final String prefix;
    private final int limit;
    private final Duration window;

    RateLimitPolicy(String prefix, int limit, Duration window) {
        this.prefix = prefix;
        this.limit = limit;
        this.window = window;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }

    /** Khoá đếm: gồm cả tên bucket để ba nhóm không đè lên nhau. */
    public String key(String identity) {
        return prefix + ":" + identity;
    }
}
