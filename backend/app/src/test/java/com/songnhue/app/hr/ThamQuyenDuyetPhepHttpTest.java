package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Ai</b> được duyệt một đơn nghỉ phép — T80.1 → T80.6, đo qua HTTP.
 *
 * <h2>⛔⛔ Một mã quyền ⛔ diễn đạt được một QUAN HỆ — và kho đã viết ra điều đó từ 14/09</h2>
 *
 * <p>{@code V202609141079} mục 3 nói thẳng: <i>"{@code required_permission} là một <b>mã quyền</b>,
 * còn đặc tả nói <b>'Quản lý đơn vị duyệt'</b> — một <b>quan hệ</b>"</i>, rồi khai rằng vế còn lại
 * do <b>bộ lọc phạm vi tầng 3</b> đảm nhiệm. Đo lại 20/09/2026 thì bộ lọc ấy trả <b>một nửa</b> của
 * quan hệ: nó cắt đúng <i>đơn vị nào</i>, và ⛔ nói gì về <i>ai</i>. Hậu quả là <b>bốn</b> trạng
 * thái, cả bốn đi qua đúng quy trình, có chữ ký hash chain, ⛔ một dòng lỗi:
 *
 * <ol>
 *   <li><b>T80.1</b> — bất kỳ ai có {@code hr:leave:approve} mà phạm vi phủ là duyệt được. Một Xí
 *       nghiệp có hai tài khoản vai trò quản lý thì <b>cả hai</b> duyệt, kể cả người ⛔ giữ chức vụ
 *       nào. Trước 20/09 hệ ⛔ có chỗ nào ghi <i>ai là trưởng đơn vị</i> — H24 (T76.1) vừa mở.
 *   <li><b>T80.2</b> — <b>tự duyệt đơn của chính mình</b>. {@code requesterUserId} có,
 *       {@code ghiQuyetDinh(nguoiQuyet)} có, và ⛔ dòng nào so hai cái.
 *   <li><b>T80.3</b> — <b>cấp 2 ⛔ khác cấp 1</b>. Hai bước chuyển đòi cùng {@code hr:leave:approve}
 *       trên cùng phạm vi ⇒ người vừa bấm ESCALATE bấm thêm phát nữa là xong. Chốt C2 mua một cấp
 *       duyệt thứ hai mà hệ ⛔ thu được gì.
 *   <li><b>T80.4</b> — <b>đồng nghiệp rút được đơn của người khác</b>. Bốn bước chuyển {@code CANCEL}
 *       đòi {@code hr:leave:request} — quyền chốt C3 cấp cho <b>mọi CBNV</b> — nên một người cùng
 *       đơn vị huỷ được cả một đơn <b>đã duyệt</b> của đồng nghiệp.
 * </ol>
 *
 * <p>Hai bài cuối đo phần <b>dựng mới</b>: <b>T80.5</b> uỷ quyền duyệt có thời hạn (chốt B3) — có
 * hiệu lực và thu hồi <b>tức thì trên phiên đang mở</b>, thứ mà một lượt *cộng quyền vào token* ⛔
 * làm được — và <b>T80.6</b> đường dự phòng cho đơn vị chưa ai được giao chức vụ.
 *
 * <h2>⚠ Bán kính hôm nay bằng 0, và đó ⛔ phải lý do để hoãn</h2>
 *
 * <p>{@code employees} rỗng vì <b>G6-a</b> chặn dữ liệu, nên ⛔ có đơn nghỉ thật nào để tự duyệt.
 * Đây đúng là cửa sổ để trả: ngày Công ty gửi danh sách CBNV, cả bốn trạng thái trên có người thật
 * đi qua. Cùng lý lẽ đã dùng cho T68.18 — thứ khác là lần ấy ⛔ ai nhìn suốt 38 ngày.
 *
 * <h2>Vì sao QUA HTTP (luật 5)</h2>
 *
 * <p>Cả bốn bảo đảm nằm ở tầng <b>đi qua đủ ba cổng</b>: {@code @RequirePermission} (tầng 2) ·
 * {@code WorkflowEngine.hasPermissionFor} ({@code workflow_transitions}) · bộ lọc phạm vi bật quanh
 * {@code @Transactional} (tầng 3). Một bài gọi thẳng service ⛔ chạy qua cái nào trong ba.
 *
 * <p>⛔ <b>Cấm seed hồ sơ CBNV</b> (CLAUDE.md): mọi hàng ở đây mang tiền tố {@link #TIEN_TO} và bị
 * xoá <b>cứng</b> ở {@code @AfterAll}, có khẳng định ngay tại chỗ dọn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThamQuyenDuyetPhepHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T79-";
    private static final String HAU_TO_USER = "t79";

    private static final String VAI_TRO_QT = "KIEMTRA_T79_QUANTRI";
    private static final String VAI_TRO_DUYET = "KIEMTRA_T79_DUYET";
    private static final String VAI_TRO_NV = "KIEMTRA_T79_NHANVIEN";

    private static final List<String> QUYEN_QT =
            List.of("adm:user:view", "adm:user:update", "hr:employee:view", "hr:employee:create", "hr:leave:request");

    /**
     * ⛔ Cố ý ⛔ có {@code hr:leave:view-all}: bài này đo <b>thẩm quyền</b>, ⛔ đo tầm nhìn.
     *
     * <p>⚠ {@code hr:leave:delegate} có mặt ở <b>cả ba</b> tài khoản duyệt — kể cả {@code quanly_a},
     * người ⛔ giữ chức vụ nào. Đó là vế phân biệt của {@code HR-2016}: mã quyền là cổng <i>năng
     * lực</i>, còn <i>giao được cho đơn vị NÀO</i> là câu hỏi quan hệ. Nếu bài đỏ vì 403 ở tầng
     * {@code @RequirePermission} thì nó ⛔ đo được vế quan hệ.
     */
    private static final List<String> QUYEN_DUYET =
            List.of("hr:leave:approve", "hr:leave:request", "hr:leave:delegate");

    private static final List<String> QUYEN_NV = List.of("hr:leave:request");

    private static final String KHOA_SO_CAP = "hr.leave.approval-levels";
    private static final String KHOA_NGUONG_CAP_2 = "hr.leave.second-level-threshold-days";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorityLoader authorities;

    @Autowired
    private SettingService settings;

    private PhienHttp phienQt;
    private PhienHttp phienTruongA;
    private PhienHttp phienQuanLyA;
    private PhienHttp phienTruongCty;
    private PhienHttp phienNv;
    private PhienHttp phienDongNghiep;
    private PhienHttp phienDuyetB;

    private PhienHttp.Phien quanTri;
    private PhienHttp.Phien truongA;
    private PhienHttp.Phien quanLyA;
    private PhienHttp.Phien truongCty;
    private PhienHttp.Phien nhanVien;
    private PhienHttp.Phien dongNghiep;
    private PhienHttp.Phien duyetB;

    private long donViGocId;
    private String pathGoc;
    private long xnAId;
    private UUID xnAPublic;
    private long xnBId;

    private UUID idTruongA;
    private UUID idTruongCty;
    private UUID idQuanLyA;
    private UUID idDongNghiep;
    private UUID idDuyetB;
    private UUID idNhanVien;
    private UUID hoSoNhanVien;

    private String soCapCu;
    private String nguongCu;

    @BeforeAll
    void dungNen() {
        don();

        taoVaiTro(VAI_TRO_QT, QUYEN_QT);
        taoVaiTro(VAI_TRO_DUYET, QUYEN_DUYET);
        taoVaiTro(VAI_TRO_NV, QUYEN_NV);

        donViGocId = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);
        xnAId = themDonVi(TIEN_TO + "XN-A");
        xnBId = themDonVi(TIEN_TO + "XN-B");
        xnAPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnAId);

        phienQt = moPhien();
        phienTruongA = moPhien();
        phienQuanLyA = moPhien();
        phienTruongCty = moPhien();
        phienNv = moPhien();
        phienDongNghiep = moPhien();
        phienDuyetB = moPhien();

        String tenQt = taoNguoiDung("qt", VAI_TRO_QT);
        String tenTruongA = taoNguoiDung("truong_a", VAI_TRO_DUYET);
        String tenQuanLyA = taoNguoiDung("quanly_a", VAI_TRO_DUYET);
        String tenTruongCty = taoNguoiDung("truong_cty", VAI_TRO_DUYET);
        String tenNv = taoNguoiDung("nv", VAI_TRO_NV);
        String tenDongNghiep = taoNguoiDung("dong_nghiep", VAI_TRO_NV);
        String tenDuyetB = taoNguoiDung("duyet_b", VAI_TRO_DUYET);

        idTruongA = publicIdCua(tenTruongA);
        idNhanVien = publicIdCua(tenNv);
        idTruongCty = publicIdCua(tenTruongCty);
        idQuanLyA = publicIdCua(tenQuanLyA);
        idDongNghiep = publicIdCua(tenDongNghiep);
        idDuyetB = publicIdCua(tenDuyetB);

        // ⛔⛔ Ba tài khoản này CÙNG phạm vi (Xí nghiệp A) và CÙNG quyền `hr:leave:approve`. Vế
        //    phân biệt DUY NHẤT giữa `truong_a` và `quanly_a` là hai cột H24 dưới đây — nếu bài
        //    dưới xanh cho cả hai thì bộ lọc phạm vi đang là thứ duy nhất đang canh, đúng nửa
        //    quan hệ mà `V202609141079` khai là đủ.
        datDonVi(idTruongA, xnAId);
        datDonVi(idQuanLyA, xnAId);
        datDonVi(idNhanVien, xnAId);
        datDonVi(idDongNghiep, xnAId);
        datDonVi(idTruongCty, donViGocId);
        // ⛔⛔ Xí nghiệp B là vế phân biệt của `HR-2015`: `duyet_b` có ĐỦ `hr:leave:approve` mà
        //    đơn vị của họ ⛔ phủ Xí nghiệp A, tức ⛔ thuộc *"cùng đơn vị hoặc cấp trên"* (B3).
        datDonVi(idDuyetB, xnBId);

        datTruong(xnAId, idTruongA);
        datTruong(donViGocId, idTruongCty);

        quanTri = phienQt.dangNhap(tenQt);
        truongA = phienTruongA.dangNhap(tenTruongA);
        quanLyA = phienQuanLyA.dangNhap(tenQuanLyA);
        truongCty = phienTruongCty.dangNhap(tenTruongCty);
        nhanVien = phienNv.dangNhap(tenNv);
        dongNghiep = phienDongNghiep.dangNhap(tenDongNghiep);
        duyetB = phienDuyetB.dangNhap(tenDuyetB);

        hoSoNhanVien = taoHoSo("NV-1");
        lienKet(idNhanVien, hoSoNhanVien);

        soCapCu = giaTriThamSo(KHOA_SO_CAP);
        nguongCu = giaTriThamSo(KHOA_NGUONG_CAP_2);
        assertThat(soCapCu)
                .as("⛔ Chống tập rỗng: hai khoá chốt C2 phải CÓ trong `settings` thì bài cấp 2 mới nói được gì")
                .isNotNull();
    }

    /**
     * ⛔⛔ Khôi phục hai khoá chốt C2 sau MỖI bài, ⛔ ở cuối bài đã đổi chúng.
     *
     * <p>Bản đầu của lớp này dọn ở cuối {@code capHaiPhaiLaNguoiKhac} và lượt chạy đầu tiên lộ ra
     * ngay: {@code chiTruongPhoDuyetDuoc} đọc được trạng thái {@code CHO_DUYET_2} trong khi nó ⛔
     * đụng tới cấu hình nào — surefire xếp bài theo một thứ tự ⛔ ai khai, nên rò trạng thái là một
     * lượt đỏ ở bài VÔ CAN. Đúng hình dạng <b>T48.8</b>.
     */
    @AfterEach
    void traLaiThamSo() {
        datThamSo(KHOA_SO_CAP, soCapCu);
        datThamSo(KHOA_NGUONG_CAP_2, nguongCu);
    }

    @AfterAll
    void dep() {
        datThamSo(KHOA_SO_CAP, soCapCu);
        datThamSo(KHOA_NGUONG_CAP_2, nguongCu);
        don();
    }

    // =========================================================================

    @Test
    @DisplayName("⛔⛔ T80.1 — CÙNG quyền, CÙNG phạm vi: chỉ TRƯỞNG đơn vị duyệt được, quản lý kia ⛔")
    void chiTruongPhoDuyetDuoc() {
        UUID don = nopDon(thuHai(12), thuHai(12));

        assertThat(thayTrongHopChoDuyet(phienQuanLyA, quanLyA, don))
                .as("⛔ Tiền đề (luật 7): `quanly_a` PHẢI thấy đơn — bài dưới mới đo được *thẩm quyền*, "
                        + "⛔ phải đo lại *tầm nhìn* mà `khongDuyetDuocDonNgoaiDonVi` đã canh")
                .isTrue();

        ResponseEntity<String> tuChoi = hanhDong(phienQuanLyA, quanLyA, don, "APPROVE", null);
        assertThat(tuChoi.getStatusCode())
                .as(
                        "⛔⛔ `quanly_a` có ĐỦ `hr:leave:approve` và phạm vi PHỦ Xí nghiệp A, mà ⛔ giữ chức vụ nào "
                                + "ở đó ⇒ ⛔ được duyệt. Đỏ ở đây nghĩa là một mã quyền vẫn đang đứng thay cho một "
                                + "quan hệ. Thân: %s",
                        tuChoi.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(nutDuyetHienVoi(phienQuanLyA, quanLyA, don))
                .as("⛔⛔ Máy chủ từ chối mà nút vẫn hiện thì người dùng bấm vào một lời hứa suông — "
                        + "`allowedActions` lọc bằng ĐÚNG luật mà `thucHien` áp dụng")
                .isFalse();

        ResponseEntity<String> ok = hanhDong(phienTruongA, truongA, don, "APPROVE", null);
        assertThat(ok.getStatusCode())
                .as("⛔ Vế phân biệt: TRƯỞNG đơn vị vẫn duyệt được. %s", ok.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(chuoi(ok.getBody(), "state")).isEqualTo("DA_DUYET");
    }

    @Test
    @DisplayName("⛔⛔ T80.2 — trưởng đơn vị ⛔ TỰ DUYỆT đơn của chính mình; cấp trên quyết thay")
    void khongTuDuyetDonCuaChinhMinh() {
        UUID hoSoTruong = taoHoSo("TRUONG-A");
        lienKet(idTruongA, hoSoTruong);
        try {
            UUID don = nopDonBoi(phienTruongA, truongA, thuHai(13), thuHai(13));

            ResponseEntity<String> tuDuyet = hanhDong(phienTruongA, truongA, don, "APPROVE", null);
            assertThat(tuDuyet.getStatusCode())
                    .as(
                            "⛔⛔ Người nộp và người duyệt là CÙNG một tài khoản — `requesterUserId` và `decidedBy` "
                                    + "nằm cạnh nhau trên cùng một hàng, và ⛔ dòng nào so chúng. Thân: %s",
                            tuDuyet.getBody())
                    .isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<String> capTren = hanhDong(phienTruongCty, truongCty, don, "APPROVE", null);
            assertThat(capTren.getStatusCode())
                    .as(
                            "⛔ Vế phân biệt — và là lý do ⛔ chặn cứng: đơn của trưởng đơn vị phải ĐI ĐÂU ĐÓ, "
                                    + "⛔ được kẹt lại. Trưởng đơn vị CHA quyết. %s",
                            capTren.getBody())
                    .isEqualTo(HttpStatus.OK);
            assertThat(chuoi(capTren.getBody(), "state")).isEqualTo("DA_DUYET");
        } finally {
            jdbc.update("UPDATE users SET employee_id = NULL WHERE public_id = ?", idTruongA);
            authorities.invalidateAll();
        }
    }

    @Test
    @DisplayName("⛔⛔ T80.3 — cấp 2 phải là NGƯỜI KHÁC; ⛔ thì chốt C2 mua một cấp duyệt ⛔ tồn tại")
    void capHaiPhaiLaNguoiKhac() {
        datThamSo(KHOA_SO_CAP, "2");
        datThamSo(KHOA_NGUONG_CAP_2, "1");

        UUID don = nopDon(thuHai(14), thuHai(14).plusDays(1));
        ResponseEntity<String> cap1 = hanhDong(phienTruongA, truongA, don, "APPROVE", null);
        assertThat(chuoi(cap1.getBody(), "state"))
                .as("⛔ Tiền đề: ngưỡng 1 ngày ⇒ đơn 2 ngày công phải CHUYỂN CẤP, ⛔ duyệt thẳng. %s", cap1.getBody())
                .isEqualTo("CHO_DUYET_2");

        ResponseEntity<String> lai = hanhDong(phienTruongA, truongA, don, "APPROVE", null);
        assertThat(lai.getStatusCode())
                .as(
                        "⛔⛔ Cùng một người vừa duyệt cấp 1 bấm thêm phát nữa là xong — hai cấp duyệt khai cùng "
                                + "`hr:leave:approve` trên cùng phạm vi thì chúng là MỘT cấp. Thân: %s",
                        lai.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> cap2 = hanhDong(phienTruongCty, truongCty, don, "APPROVE", null);
        assertThat(cap2.getStatusCode())
                .as("⛔ Vế phân biệt: một người KHÁC, vẫn trong chuỗi lãnh đạo, quyết được cấp 2. %s", cap2.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(chuoi(cap2.getBody(), "state")).isEqualTo("DA_DUYET");
    }

    @Test
    @DisplayName("⛔⛔ T80.4 — đồng nghiệp cùng đơn vị ⛔ RÚT được đơn của người khác")
    void chiNguoiNopMoiRutDuocDon() {
        UUID don = nopDon(thuHai(16), thuHai(16));

        ResponseEntity<String> nguoiLa = hanhDong(phienDongNghiep, dongNghiep, don, "CANCEL", null);
        assertThat(nguoiLa.getStatusCode())
                .as(
                        "⛔⛔ Bốn bước chuyển CANCEL đòi `hr:leave:request` — quyền chốt C3 cấp cho MỌI CBNV — nên "
                                + "cổng quyền ⛔ phân biệt được *người nộp* với *người ngồi cùng phòng*. Thân: %s",
                        nguoiLa.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> chinhChu = hanhDong(phienNv, nhanVien, don, "CANCEL", null);
        assertThat(chinhChu.getStatusCode())
                .as("⛔ Vế phân biệt: chính người nộp vẫn rút được. %s", chinhChu.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(chuoi(chinhChu.getBody(), "state")).isEqualTo("DA_HUY");
    }

    @Test
    @DisplayName("⭐⭐ T80.5 — uỷ quyền có hiệu lực NGAY, và thu hồi cũng vậy: CÙNG một phiên đăng nhập")
    void uyQuyenVaThuHoiCoHieuLucNgay() {
        UUID don = nopDon(thuHai(17), thuHai(17));
        assertThat(hanhDong(phienQuanLyA, quanLyA, don, "APPROVE", null).getStatusCode())
                .as("⛔ Tiền đề (luật 7): chưa uỷ quyền thì `quanly_a` ⛔ duyệt được — ⛔ thì bài dưới xanh "
                        + "vì lý do sai")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> giao = phienTruongA.goi(
                truongA,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/uy-quyen",
                """
                {"orgUnitPublicId":"%s","nguoiDuocUyQuyenPublicId":"%s","tuNgay":"%s","denNgay":"%s",\
                "lyDo":"Đi công tác"}"""
                        .formatted(xnAPublic, idQuanLyA, homNay().minusDays(1), homNay().plusDays(7)));
        assertThat(giao.getStatusCode()).as("%s", giao.getBody()).isEqualTo(HttpStatus.OK);
        UUID idUyQuyen = UUID.fromString(chuoi(giao.getBody(), "publicId"));

        // ⛔⛔ ⛔ Đăng nhập lại, ⛔ làm mới token: chính đây là điều mà một lượt *cộng quyền vào
        //    token* ⛔ làm được. Access token sống 30 phút ⇒ nếu uỷ quyền đi qua token thì lượt
        //    gọi này vẫn 403 cho tới khi phiên hết hạn.
        ResponseEntity<String> ok = hanhDong(phienQuanLyA, quanLyA, don, "APPROVE", null);
        assertThat(ok.getStatusCode())
                .as("⛔⛔ Uỷ quyền phải có hiệu lực NGAY trên phiên đang mở. %s", ok.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT uy_quyen_id FROM leave_requests WHERE public_id = ?", Long.class, don))
                .as("⛔⛔ Chốt B3 đòi audit ghi *duyệt theo uỷ quyền của X* — vết phải đi trên CHÍNH lá đơn, "
                        + "⛔ chỉ trong log: tranh chấp phép năm nổ ra hàng tháng sau và thứ người ta mở là cái đơn")
                .isNotNull();

        UUID donSau = nopDon(thuHai(18), thuHai(18));
        ResponseEntity<String> thu =
                phienTruongA.goi(truongA, HttpMethod.DELETE, "/api/v1/hr/nghi-phep/uy-quyen/" + idUyQuyen, null);
        assertThat(thu.getStatusCode()).as("%s", thu.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(hanhDong(phienQuanLyA, quanLyA, donSau, "APPROVE", null).getStatusCode())
                .as("⛔⛔ THU HỒI cũng phải tức thì — một cơ chế bảo mật mà *thu hồi* trễ tới 30 phút thì "
                        + "⛔ phải một cơ chế bảo mật")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⛔⛔ T80.5 — ba điều kiện của chốt B3 cho BA mã lỗi khác nhau, ⛔ gộp về một câu")
    void baDieuKienCuaUyQuyen() {
        String than =
                """
                {"orgUnitPublicId":"%s","nguoiDuocUyQuyenPublicId":"%s","tuNgay":"%s","denNgay":"%s"}""";

        ResponseEntity<String> nguoiGiaoSai = phienQuanLyA.goi(
                quanLyA,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/uy-quyen",
                than.formatted(xnAPublic, idTruongCty, homNay(), homNay().plusDays(3)));
        assertThat(chuoi(nguoiGiaoSai.getBody(), "code"))
                .as(
                        "⛔ `quanly_a` có ĐỦ `hr:leave:delegate` mà ⛔ giữ chức vụ nào ⇒ ⛔ giao được thứ mình "
                                + "⛔ có. Thân: %s",
                        nguoiGiaoSai.getBody())
                .isEqualTo("HR-2016");

        ResponseEntity<String> chuaCoQuyen = phienTruongA.goi(
                truongA,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/uy-quyen",
                than.formatted(xnAPublic, idDongNghiep, homNay(), homNay().plusDays(3)));
        assertThat(chuoi(chuaCoQuyen.getBody(), "code"))
                .as(
                        "⛔⛔ Người nhận phải SẴN có `hr:leave:approve` — ⛔ thì biểu mẫu này là một đường CẤP "
                                + "QUYỀN ẩn nằm ngoài màn hình Vai trò & phân quyền. Thân: %s",
                        chuaCoQuyen.getBody())
                .isEqualTo("HR-2014");

        ResponseEntity<String> ngoaiPhamVi = phienTruongA.goi(
                truongA,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/uy-quyen",
                than.formatted(xnAPublic, idDuyetB, homNay(), homNay().plusDays(3)));
        assertThat(chuoi(ngoaiPhamVi.getBody(), "code"))
                .as(
                        "⛔ `duyet_b` có quyền duyệt mà ở Xí nghiệp B ⇒ ⛔ thuộc *cùng đơn vị hoặc cấp trên* "
                                + "(nguyên văn B3). Thân: %s",
                        ngoaiPhamVi.getBody())
                .isEqualTo("HR-2015");
    }

    @Test
    @DisplayName("⭐ T80.6 — đơn vị chưa có trưởng/phó: người giữ hr:leave:delegate quyết thay, và ĐƯỢC GHI LẠI")
    void donViChuaCoLanhDaoThiCoDuongDuPhong() {
        // ⛔⛔ Gỡ trưởng của CẢ chuỗi — chỉ khi ấy đường dự phòng mới mở. Gỡ mỗi Xí nghiệp A là ⛔
        //    đủ: trưởng đơn vị CHA vẫn có thẩm quyền, và đó chính là điều đúng.
        jdbc.update("UPDATE org_units SET head_user_id = NULL WHERE id IN (?, ?)", xnAId, donViGocId);
        authorities.invalidateAll();
        try {
            UUID don = nopDon(thuHai(19), thuHai(19));
            ResponseEntity<String> ra = hanhDong(phienQuanLyA, quanLyA, don, "APPROVE", null);
            assertThat(ra.getStatusCode())
                    .as(
                            "⛔ Một lá đơn ⛔ được KẸT vì một ô hành chính chưa ai điền — người lao động gánh "
                                    + "hậu quả của việc đó. %s",
                            ra.getBody())
                    .isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject(
                            "SELECT duyet_du_phong FROM leave_requests WHERE public_id = ?", Boolean.class, don))
                    .as("⛔⛔ Đường dự phòng ⛔ được IM LẶNG: một ô hành chính bỏ trống phải để lại dấu trên "
                            + "chính lá đơn, ⛔ thì nó khôi phục hành vi rộng vừa bỏ mà ⛔ ai đếm được (quy tắc 7)")
                    .isTrue();
        } finally {
            datTruong(xnAId, idTruongA);
            datTruong(donViGocId, idTruongCty);
        }
    }

    // =========================================================================
    // Trợ giúp
    // =========================================================================

    private static LocalDate homNay() {
        return LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN);
    }

    private UUID nopDon(LocalDate tu, LocalDate den) {
        return nopDonBoi(phienNv, nhanVien, tu, den);
    }

    private UUID nopDonBoi(PhienHttp phien, PhienHttp.Phien ai, LocalDate tu, LocalDate den) {
        String than =
                """
                {"employeePublicId":null,"leaveType":"PHEP_NAM","fromDate":"%s","toDate":"%s",\
                "reason":"Kiểm thử T79"}"""
                        .formatted(tu, den);
        ResponseEntity<String> ra = phien.goi(ai, HttpMethod.POST, "/api/v1/hr/nghi-phep", than);
        assertThat(ra.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", ra.getBody())
                .isEqualTo(HttpStatus.OK);
        return UUID.fromString(chuoi(ra.getBody(), "publicId"));
    }

    private ResponseEntity<String> hanhDong(PhienHttp phien, PhienHttp.Phien ai, UUID don, String hanh, String lyDo) {
        String than = lyDo == null
                ? "{\"action\":\"%s\"}".formatted(hanh)
                : "{\"action\":\"%s\",\"reason\":\"%s\"}".formatted(hanh, lyDo);
        return phien.goi(ai, HttpMethod.POST, "/api/v1/hr/nghi-phep/" + don + "/hanh-dong", than);
    }

    private boolean thayTrongHopChoDuyet(PhienHttp phien, PhienHttp.Phien ai, UUID don) {
        ResponseEntity<String> ra = phien.get(ai, "/api/v1/hr/nghi-phep/cho-duyet");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        return ra.getBody() != null && ra.getBody().contains(don.toString());
    }

    private boolean nutDuyetHienVoi(PhienHttp phien, PhienHttp.Phien ai, UUID don) {
        ResponseEntity<String> ra = phien.get(ai, "/api/v1/hr/nghi-phep/" + don + "/hanh-dong");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        return ra.getBody() != null && ra.getBody().contains("\"APPROVE\"");
    }

    private UUID taoHoSo(String hau) {
        String than =
                """
                {"code":"%s","fullName":"Trần Văn Kiểm","dateOfBirth":"1988-04-02","gender":"NAM",\
                "orgUnitId":"%s","hiredAt":"2014-03-01",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, xnAPublic);
        ResponseEntity<String> tao = phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private void lienKet(UUID taiKhoan, UUID hoSo) {
        ResponseEntity<String> ra = phienQt.goi(
                quanTri,
                HttpMethod.PUT,
                "/api/v1/admin/users/" + taiKhoan + "/ho-so-nhan-su",
                "{\"employeePublicId\":\"%s\"}".formatted(hoSo));
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Đặt trưởng đơn vị <b>thẳng CSDL</b> — đồ gá.
     *
     * <p>Đường ghi qua API ({@code PUT /org-units/{id}}) là thứ <b>T76.1 dựng</b> và nó đã có bài
     * riêng {@code TruongPhoDonViHttpTest}; gọi lại ở đây chỉ để vai trò kiểm thử phải ôm thêm
     * {@code adm:org-unit:manage} — một quyền ⛔ liên quan gì tới thứ lớp này đo.
     */
    private void datTruong(long donViId, UUID taiKhoan) {
        int doi = jdbc.update(
                "UPDATE org_units SET head_user_id = (SELECT id FROM users WHERE public_id = ?) WHERE id = ?",
                taiKhoan,
                donViId);
        assertThat(doi)
                .as("⛔ Chống tập rỗng: ⛔ đặt được trưởng thì mọi bài dưới đo một hệ ⛔ có lãnh đạo nào")
                .isEqualTo(1);
    }

    private void datDonVi(UUID taiKhoan, long donViId) {
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", donViId, taiKhoan);
        authorities.invalidateAll();
    }

    private PhienHttp moPhien() {
        PhienHttp p = new PhienHttp(http);
        // T60.9 — mỗi phiên là một máy khách, ⛔ thì cả lớp chung một xô hạn mức LOGIN.
        p.doiIp();
        return p;
    }

    private String taoNguoiDung(String hau, String vaiTro) {
        return PhienHttp.taoNguoiDung(users, passwords, jdbc, HAU_TO_USER + "_" + hau, vaiTro);
    }

    private long themDonVi(String ma) {
        jdbc.update(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                donViGocId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ? WHERE id = ?", pathGoc + id + "/", id);
        return id;
    }

    private void taoVaiTro(String ma, List<String> quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, ?, 'Tạm, xoá ở @AfterAll', FALSE, now())",
                ma,
                "Vai trò kiểm thử " + ma);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code IN ("
                        + String.join(",", java.util.Collections.nCopies(quyen.size(), "?")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(ma), quyen.stream())
                        .toArray());
        assertThat(gan)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM LẶNG, "
                        + "và mọi bài dưới đỏ với 403 — triệu chứng ⛔ liên quan gì tới thứ đang kiểm")
                .isEqualTo(quyen.size());
    }

    private String giaTriThamSo(String khoa) {
        return jdbc
                .queryForList("SELECT setting_value FROM settings WHERE setting_key = ?", String.class, khoa)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void datThamSo(String khoa, String giaTri) {
        jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        settings.invalidate(khoa);
    }

    private UUID publicIdCua(String username) {
        return jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, username);
    }

    /** Thứ Hai của tuần cách tuần này {@code soTuan} tuần. */
    private static LocalDate thuHai(int soTuan) {
        return LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(soTuan);
    }

    private void don() {
        // ⛔⛔ THỨ TỰ: `org_units.head_user_id`/`deputy_user_id` là hai khoá ngoại DUY NHẤT trỏ vào
        //    `users` mà ⛔ có `ON DELETE CASCADE` — bỏ bước này thì lượt xoá tài khoản đỏ với
        //    `org_units_head_user_id_fkey`, và nó đỏ ở @AfterAll nên đọc như một bài kiểm hỏng.
        jdbc.update("UPDATE org_units SET head_user_id = NULL, deputy_user_id = NULL "
                + "WHERE head_user_id IN (SELECT id FROM users WHERE username LIKE 'kiemtra_" + HAU_TO_USER + "%') "
                + "   OR deputy_user_id IN (SELECT id FROM users WHERE username LIKE 'kiemtra_" + HAU_TO_USER + "%')");
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_" + HAU_TO_USER + "%'");
        // ⛔⛔ TRƯỚC `leave_requests`: cột `uy_quyen_id` trỏ sang bảng uỷ quyền, và bảng uỷ quyền
        //    trỏ sang `org_units`. Lượt dọn đầu của lớp này đỏ với
        //    `leave_approval_delegations_org_unit_id_fkey` — và đó ⛔ phải rác: nó là BẰNG CHỨNG
        //    rằng `POST /uy-quyen` đã thật sự ghi một hàng (xem `uyQuyenVaThuHoiCoHieuLucNgay`).
        jdbc.update("UPDATE leave_requests SET uy_quyen_id = NULL WHERE uy_quyen_id IN "
                + "(SELECT id FROM leave_approval_delegations WHERE org_unit_id IN "
                + "(SELECT id FROM org_units WHERE code LIKE '" + TIEN_TO + "%'))");
        jdbc.update(
                "DELETE FROM leave_approval_delegations WHERE org_unit_id IN "
                        + "(SELECT id FROM org_units WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM leave_requests WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_" + HAU_TO_USER + "%'");
        for (String bang : List.of("jobs", "attachments")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE org_unit_id IN (SELECT id FROM org_units WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO_QT, VAI_TRO_DUYET, VAI_TRO_NV)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM org_units WHERE code LIKE ? AND deleted_at IS NULL",
                        Integer.class,
                        TIEN_TO + "%"))
                .as("⛔ Lượt dọn phải ĐO được: đơn vị kiểm thử còn sót là bài lần sau chạy trên cây CŨ")
                .isZero();
        authorities.invalidateAll();
    }

    private static String chuoi(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*(null|\"([^\"]*)\"|[^,}\\]]+)")
                .matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2) : m.group(1);
    }
}
