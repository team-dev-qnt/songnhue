package com.songnhue.app.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.domain.Article;
import com.songnhue.core.application.identity.UserAdminService;
import com.songnhue.core.application.notification.RecipientResolver;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;

/**
 * ⛔⛔⛔ <b>Nhóm "Ban điều hành" đang được CỘNG vào những lá thư chỉ dành cho MỘT người</b> — T74.7.
 *
 * <h2>Khuyết tật, đo ngày 20/09/2026</h2>
 *
 * <p>{@code RecipientResolver.resolve(...)} suy chính sách người nhận từ <b>hình dạng của dữ liệu</b>
 * chứ ⛔ không từ một lời khai: ⛔ có {@code targetPermission} thì nó rơi thẳng về
 * {@code executiveBoard()}. Nhánh ấy đúng cho <i>cảnh báo vận hành</i> (luật G11 — hệ thống ĐOÁN ai
 * nên biết), nhưng <b>ba</b> nơi gọi khác cũng rơi vào đó trong khi chúng biết chính xác người nhận
 * là ai:
 *
 * <ul>
 *   <li>{@code WorkflowEngine} — <b>17 hàng</b> {@code workflow_transitions} mang
 *       {@code notify_owner = TRUE} và {@code notify_permission IS NULL} (đếm 20/09: ARTICLE 5 ·
 *       LEAVE_REQUEST 4 · MAINTENANCE_LOG 4 · MAINTENANCE_INCIDENT 4). Thư <i>"bài của bạn đã được
 *       duyệt"</i>, <i>"đơn nghỉ của bạn đã được duyệt"</i> cộng thêm cả Ban điều hành.
 *   <li>{@code UserAdminService.notifyStatusChange} — {@code ACCOUNT_DISABLED} ·
 *       {@code ACCOUNT_ENABLED}.
 *   <li>{@code CanhBaoTaiKhoanService.bao} — {@code PASSWORD_CHANGED} ·
 *       {@code PASSWORD_RESET_BY_ADMIN} · {@code TWO_FACTOR_RESET_BY_ADMIN} ·
 *       {@code TWO_FACTOR_ENROLLED}.
 * </ul>
 *
 * <p>Sáu mã sự kiện cuối là <b>thông báo an ninh của một cá nhân</b>. Cộng cả ban lãnh đạo vào đó
 * ⛔ chỉ là tiếng ồn — nó còn công bố <i>ai vừa bị khoá tài khoản</i>, <i>ai vừa được quản trị viên
 * đặt lại mật khẩu</i> cho một nhóm ⛔ hề cần biết.
 *
 * <h2>⚠⚠ Vì sao hôm nay ⛔ ai thấy, và vì sao bài kiểm này phải có TRƯỚC màn hình chọn nhóm</h2>
 *
 * <p>Khoá {@code notification.alert-group.executive-board} seed {@code '[]'} và tới 20/09/2026 chưa
 * ai điền được — đường ghi duy nhất là ô JSON thô, mà giá trị phải là mảng {@code publicId} tài
 * khoản trong khi màn hình Tài khoản ⛔ hiện {@code publicId} ở đâu cả (T76.3). Tức là
 * <b>khuyết tật đang ngủ vì một khuyết tật khác</b>: nhóm rỗng ⇒ phép hợp ⛔ thêm ai ⇒ mọi lượt gửi
 * hôm nay tình cờ đúng.
 *
 * <p>⇒ Ngày nào Công ty điền được nhóm ấy là ngày lỗi thức dậy trên <b>toàn bộ</b> sáu mã sự kiện và
 * 17 hàng quy trình cùng lúc. Vì vậy bài kiểm này — và bản vá của nó — phải đi <b>trước</b> widget
 * chọn tài khoản, ⛔ đi sau.
 *
 * <h2>Vì sao ba bài, ⛔ phải một</h2>
 *
 * <p>Hai bài đầu canh hai <i>họ</i> nơi gọi khác nhau (quy trình duyệt · thông báo an ninh cá nhân)
 * và chúng ⛔ thay thế nhau được: một bản vá chỉ đụng {@code WorkflowEngine} sẽ làm bài đầu xanh mà
 * để nguyên thư <i>"tài khoản của bạn đã bị khoá"</i>.
 *
 * <p>Bài thứ ba là <b>vế phân biệt</b> (luật 9). ⛔ Có nó thì cách vá rẻ nhất — gỡ hẳn
 * {@code executiveBoard()} — cũng làm hai bài đầu xanh, và như thế là <b>tháo</b> đúng cơ chế G11
 * mà chốt của khách đòi phải có. Nó khẳng định {@code NotifyRequest.alert(...)} <b>vẫn</b> tới Ban
 * điều hành, kể cả khi ⛔ có đơn vị nào được nêu (4/19 điểm đo {@code MN_SONG} ⛔ thuộc công trình
 * nào theo thiết kế — T33.8 — nên nhóm ấy là người nhận DUY NHẤT của chúng).
 */
class NhomCanhBaoKhongLanSangThuCaNhanTest extends IntegrationTestBase {

    private static final String TIEN_TO = "t82-";

    /** Mã sự kiện riêng của bài đối chứng — ⛔ mượn mã thật để lượt dọn ⛔ chạm dữ liệu khác. */
    private static final String SU_KIEN_DOI_CHUNG = "T82_CANH_BAO_G11";

    private static final String QUYEN_DANH_MUC = "cms:category:manage";
    private static final String QUYEN_TAO = "cms:article:create";
    private static final String QUYEN_GUI = "cms:article:submit";
    private static final String QUYEN_DUYET = "cms:article:approve";

    @Autowired
    private ArticleService baiViet;

    @Autowired
    private CategoryService danhMucService;

    @Autowired
    private UserAdminService quanTriTaiKhoan;

    @Autowired
    private NotificationPort thongBao;

    @Autowired
    private SettingService thamSo;

    @Autowired
    private JdbcTemplate jdbc;

    /** "X" — thành viên Ban điều hành. ⛔ Được nhận bất cứ thư nào của hai bài đầu. */
    private long idBanDieuHanh;

    private long idTacGia;
    private long idNguoiDuyet;

    @BeforeEach
    void dung() {
        don();

        idBanDieuHanh = themNguoi("ban-dieu-hanh");
        idTacGia = themNguoi("tac-gia");
        idNguoiDuyet = themNguoi("nguoi-duyet");

        datNhomBanDieuHanh(publicIdCua(idBanDieuHanh));

        assertThat(thamSo.getString(RecipientResolver.KEY_EXECUTIVE_BOARD))
                .as("⚠ TIỀN ĐỀ: nhóm Ban điều hành phải ĐANG CÓ người — nhóm rỗng thì cả ba bài "
                        + "xanh vì lý do sai (luật 7: phép hợp với tập rỗng ⛔ thêm ai)")
                .hasValueSatisfying(giaTri ->
                        assertThat(giaTri).contains(publicIdCua(idBanDieuHanh).toString()));
    }

    @AfterEach
    void donSau() {
        AuthContext.clear();
        don();
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⛔⛔ Duyệt bài — thư 'bài của bạn đã được duyệt' ⛔ được cộng Ban điều hành (17 hàng notify_owner)")
    void duyetBaiKhongGuiChoBanDieuHanh() {
        dangNhap(idTacGia, QUYEN_DANH_MUC);
        UUID danhMuc =
                danhMucService.create("Chuyên mục kiểm thử T82", null, null).getPublicId();

        dangNhap(idTacGia, QUYEN_TAO, QUYEN_GUI);
        Article bai = baiViet.create(banThao(danhMuc));
        baiViet.execute(bai.getPublicId(), "SUBMIT", null);

        dangNhap(idNguoiDuyet, QUYEN_DUYET);
        baiViet.execute(bai.getPublicId(), "APPROVE", null);

        List<Long> nhan = nguoiNhanCua("ARTICLE_APPROVED");

        assertThat(nhan)
                .as("⚠ vế chống tập rỗng (luật 29): thư phải TỚI TÁC GIẢ. ⛔ có vế này thì một bản vá "
                        + "làm tắt hẳn thông báo cũng cho tập rỗng, và tập rỗng ⛔ chứa Ban điều hành.")
                .contains(idTacGia);

        assertThat(nhan)
                .as("⛔⛔ `notify_owner = TRUE` + `notify_permission IS NULL` ⇒ `RecipientResolver` rơi về "
                        + "`executiveBoard()`. Thư chỉ dành cho tác giả đang được gửi cho cả Ban điều hành — "
                        + "17 hàng `workflow_transitions` mang đúng hình dạng này.")
                .doesNotContain(idBanDieuHanh);
    }

    @Test
    @DisplayName("⛔⛔⛔ Khoá tài khoản — 'Tài khoản của bạn đã bị khoá' ⛔ được đi tới người thứ ba")
    void khoaTaiKhoanKhongGuiChoBanDieuHanh() {
        long idBiKhoa = themNguoi("bi-khoa");
        dangNhap(idNguoiDuyet, "adm:user:lock");

        quanTriTaiKhoan.setStatus(publicIdCua(idBiKhoa), UserStatus.LOCKED);

        assertThat(nguoiNhanCua("ACCOUNT_DISABLED"))
                .as("⛔⛔⛔ Đây là thông báo an ninh của MỘT cá nhân. Cộng Ban điều hành vào đây ⛔ chỉ "
                        + "là tiếng ồn — nó công bố *ai vừa bị khoá tài khoản* cho một nhóm ⛔ cần biết. "
                        + "Người nhận đúng là đúng một người: chủ tài khoản.")
                .containsExactly(idBiKhoa);
    }

    @Test
    @DisplayName("⚠ VẾ PHÂN BIỆT (luật 9) — cảnh báo G11 VẪN phải tới Ban điều hành, kể cả khi ⛔ nêu đơn vị nào")
    void canhBaoG11VanToiBanDieuHanh() {
        thongBao.notify(NotifyRequest.alert(
                SU_KIEN_DOI_CHUNG,
                "Cảnh báo vận hành kiểm thử T82",
                "Bài đối chứng: nhánh G11 phải giữ nguyên sau bản vá T74.7.",
                NotifySeverity.WARNING,
                List.of()));

        assertThat(nguoiNhanCua(SU_KIEN_DOI_CHUNG))
                .as("⛔ Gỡ hẳn `executiveBoard()` cũng làm hai bài trên xanh — và đó là THÁO cơ chế G11 "
                        + "mà chốt của khách đòi phải có. Với 4/19 điểm đo `MN_SONG` ⛔ thuộc công trình nào "
                        + "(T33.8), danh sách đơn vị liên quan RỖNG và nhóm này là người nhận DUY NHẤT.")
                .containsExactly(idBanDieuHanh);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    /**
     * ⚠ {@code DISTINCT} là bắt buộc: {@code notification_recipients} có <b>một hàng mỗi KÊNH</b>
     * ({@code IN_APP} + {@code EMAIL}), nên ⛔ khử trùng thì mỗi người nhận xuất hiện hai lần và
     * {@code containsExactly} đỏ vì <i>số kênh</i> chứ ⛔ vì người nhận. Đo được ở lượt chạy đầu của
     * chính lớp này: bài đối chứng trả {@code [9L, 9L]} trong khi nó hoàn toàn đúng.
     */
    private List<Long> nguoiNhanCua(String maSuKien) {
        return jdbc.queryForList(
                """
                SELECT DISTINCT r.user_id FROM notification_recipients r
                  JOIN notifications n ON n.id = r.notification_id
                 WHERE n.event_type = ?
                 ORDER BY r.user_id
                """,
                Long.class,
                maSuKien);
    }

    /**
     * ⚠ {@code authorUserId} để {@code null} là <b>có chủ ý</b>: {@code ArticleService:244} khi đó
     * lấy người đang đăng nhập, tức đúng đường mà người dùng thật đi. Khai tường minh ở đây sẽ dựng
     * được một tác giả ⛔ khớp danh tính đang thao tác — một trạng thái production ⛔ sinh ra.
     */
    private static ArticleDraft banThao(UUID danhMuc) {
        return new ArticleDraft(
                "Bài kiểm người nhận T82",
                null,
                null,
                "Nội dung của bài kiểm T82.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc),
                null);
    }

    /**
     * ⚠ Ghi thẳng vào bảng rồi <b>gỡ đệm</b>: {@code SettingService} đệm bằng Caffeine trong tiến
     * trình, nên một lượt {@code UPDATE} thô mà ⛔ gọi {@link SettingService#invalidate} sẽ để bài
     * kiểm đọc lại giá trị {@code '[]'} cũ — và khi ấy cả ba bài xanh vì lý do sai.
     */
    private void datNhomBanDieuHanh(UUID publicId) {
        jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?",
                "[\"%s\"]".formatted(publicId),
                RecipientResolver.KEY_EXECUTIVE_BOARD);
        thamSo.invalidate(RecipientResolver.KEY_EXECUTIVE_BOARD);
    }

    private UUID publicIdCua(long id) {
        return jdbc.queryForObject("SELECT public_id FROM users WHERE id = ?", UUID.class, id);
    }

    private long themNguoi(String ten) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (public_id, username, full_name, email, password_hash, status,
                                   org_unit_id, must_change_password, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'x', 'ACTIVE',
                        (SELECT id FROM org_units WHERE parent_id IS NULL), FALSE, now())
                RETURNING id
                """,
                Long.class,
                TIEN_TO + ten,
                "Cán bộ kiểm thử " + ten,
                TIEN_TO + ten + "@example.invalid");
    }

    private static void dangNhap(Long userId, String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                userId,
                UUID.randomUUID(),
                "probe-" + userId,
                "Người kiểm thử T82",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of(quyen),
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null));
    }

    private void don() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
        jdbc.update(
                """
                DELETE FROM notification_recipients WHERE notification_id IN (
                    SELECT id FROM notifications WHERE event_type IN (
                        'ARTICLE_APPROVED', 'ARTICLE_SUBMITTED', 'ACCOUNT_DISABLED', ?))
                """,
                SU_KIEN_DOI_CHUNG);
        jdbc.update(
                "DELETE FROM notifications WHERE event_type IN "
                        + "('ARTICLE_APPROVED', 'ARTICLE_SUBMITTED', 'ACCOUNT_DISABLED', ?)",
                SU_KIEN_DOI_CHUNG);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", TIEN_TO + "%");
        jdbc.update(
                "UPDATE settings SET setting_value = '[]' WHERE setting_key = ?",
                RecipientResolver.KEY_EXECUTIVE_BOARD);
        thamSo.invalidate(RecipientResolver.KEY_EXECUTIVE_BOARD);
    }
}
