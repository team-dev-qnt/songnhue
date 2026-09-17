package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.songnhue.hr.application.DemNgayCongService;

/**
 * Nghỉ phép — <b>CN-04.9</b>, đo qua HTTP.
 *
 * <h2>⛔⛔ Vì sao QUA HTTP và ⛔ không gọi service (luật 5)</h2>
 *
 * <p>Bảo đảm lớn nhất của chức năng này ⛔ <b>không</b> nằm trong service, và một bài gọi thẳng
 * service về nguyên tắc ⛔ không thể thấy nó:
 *
 * <ul>
 *   <li><b>Phạm vi đơn vị</b> — đặc tả nói <i>"<b>Quản lý đơn vị</b> duyệt"</i>, một <b>quan hệ</b>.
 *       {@code workflow_transitions.required_permission} chỉ diễn đạt được một <b>mã quyền</b>, và
 *       {@code hr:leave:approve} một mình cho trưởng Xí nghiệp 3 duyệt đơn của Xí nghiệp 5. Vế còn
 *       lại là bộ lọc {@code @Filter} do {@code ScopeFilterAspect} bật <b>quanh</b>
 *       {@code @Transactional} — tầng ấy chỉ tồn tại trên đường chạy thật.
 *       Bài {@link #khongDuyetDuocDonNgoaiDonVi()} canh đúng vế ấy.
 *   <li><b>{@code users.employee_id}</b> — người nộp suy từ <b>token</b>, ⛔ không từ thân yêu cầu
 *       (T51.8). Gọi service thì {@code AuthContext} do chính bài kiểm dựng, tức nó khẳng định
 *       chính giả định của mình.
 *   <li><b>Cổng quyền tầng 2</b> — {@code hr:leave:approve} / {@code hr:leave:view-all} /
 *       {@code hr:contract:manage} có <b>0 endpoint</b> kể từ 13/08/2026; lượt này là đầu nhận đầu
 *       tiên của cả ba, nên phải chứng minh chúng thật sự chặn.
 * </ul>
 *
 * <h2>⛔ Dữ liệu của lớp này ⛔ KHÔNG phải seed</h2>
 *
 * <p>G6-a vẫn là ô trống và CLAUDE.md cấm seed hồ sơ CBNV. Mọi hàng sống đúng bằng thời gian lớp
 * chạy và bị xoá <b>cứng</b> ở {@code @AfterAll}, có khẳng định ngay tại chỗ dọn.
 *
 * <p>⚠ Ngày lễ thì <b>khác</b>: 8 hàng seed sẵn ({@code V202608131008}) là các ngày lễ có <b>ngày
 * dương lịch cố định</b> do Điều 112 BLLĐ 2019 ấn định — <b>luật</b>, ⛔ không phải <i>"seed cho đẹp
 * demo"</i>. Lớp này ⛔ không đụng vào chúng; nó chỉ thêm/xoá hàng mang tiền tố {@link #TIEN_TO}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NghiPhepHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T579-";

    private static final String VAI_TRO_QT = "KIEMTRA_T579_QUANTRI";
    private static final String VAI_TRO_DUYET = "KIEMTRA_T579_DUYET";
    private static final String VAI_TRO_NV = "KIEMTRA_T579_NHANVIEN";

    /** Quản trị: dựng hồ sơ, liên kết tài khoản, nộp hộ, sửa danh mục ngày lễ. */
    private static final List<String> QUYEN_QT = List.of(
            "adm:user:view",
            "adm:user:update",
            "hr:employee:view",
            "hr:employee:create",
            "hr:employee:update",
            "hr:leave:request",
            "hr:leave:view-all",
            "hr:contract:manage");

    private static final List<String> QUYEN_DUYET = List.of("hr:leave:approve", "hr:leave:request");

    /** ⛔ Cố ý ĐÚNG MỘT quyền — vế phân biệt của mọi bài "⛔ không đủ quyền". */
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
    private PhienHttp phienDuyet;
    private PhienHttp phienNv;
    private PhienHttp phienTroi;

    private PhienHttp.Phien quanTri;
    private PhienHttp.Phien nguoiDuyet;
    private PhienHttp.Phien nhanVien;
    private PhienHttp.Phien taiKhoanTroi;

    private UUID idNhanVien;
    private UUID idNguoiDuyet;
    private UUID donViGocPublic;
    private long donViGocId;
    private String pathGoc;
    private UUID xnAPublic;
    private UUID xnBPublic;
    private long xnAId;
    private long xnBId;

    @BeforeAll
    void dungNenVaDangNhap() {
        don();

        taoVaiTro(VAI_TRO_QT, QUYEN_QT);
        taoVaiTro(VAI_TRO_DUYET, QUYEN_DUYET);
        taoVaiTro(VAI_TRO_NV, QUYEN_NV);

        donViGocId = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        donViGocPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);

        xnAId = themDonVi(TIEN_TO + "XN-A");
        xnBId = themDonVi(TIEN_TO + "XN-B");
        xnAPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnAId);
        xnBPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnBId);

        phienQt = new PhienHttp(http);
        phienDuyet = new PhienHttp(http);
        phienNv = new PhienHttp(http);
        phienTroi = new PhienHttp(http);

        String tenQt = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t579_qt", VAI_TRO_QT);
        String tenDuyet = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t579_duyet", VAI_TRO_DUYET);
        String tenNv = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t579_nv", VAI_TRO_NV);
        // ⛔⛔ Tài khoản này CÓ `hr:leave:request` nhưng CỐ Ý ⛔ không liên kết hồ sơ CBNV nào — nó
        //    là vế chứng minh rằng quyền một mình ⛔ không đủ để nộp đơn (T51.8 là điều kiện cần).
        String tenTroi = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t579_troi", VAI_TRO_NV);

        quanTri = phienQt.dangNhap(tenQt);
        nguoiDuyet = phienDuyet.dangNhap(tenDuyet);
        nhanVien = phienNv.dangNhap(tenNv);
        taiKhoanTroi = phienTroi.dangNhap(tenTroi);

        idNhanVien = publicIdCua(tenNv);
        idNguoiDuyet = publicIdCua(tenDuyet);
    }

    @AfterEach
    void traVeTrangThaiDau() {
        jdbc.update("DELETE FROM holidays WHERE name LIKE ?", TIEN_TO + "%");
        datThamSo(KHOA_SO_CAP, "1");
        datThamSo(KHOA_NGUONG_CAP_2, "0");
        jdbc.update(
                "UPDATE users SET org_unit_id = ?, employee_id = NULL WHERE username LIKE 'kiemtra_t579%'", donViGocId);
        authorities.invalidateAll();
    }

    @AfterAll
    void donSach() {
        don();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV — bảng phải RỖNG lại")
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM holidays WHERE deleted_at IS NULL", Integer.class))
                .as("⛔ Chống tập rỗng theo chiều ngược: 8 ngày lễ pháp định (Điều 112 BLLĐ 2019) là "
                        + "LUẬT, ⛔ không phải rác kiểm thử — lớp này ⛔ không được xoá mất chúng")
                .isGreaterThanOrEqualTo(8);
    }

    // =========================================================================
    // Điều kiện CẦN: chưa liên kết hồ sơ thì quy trình ⛔ không biết ai xin nghỉ
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Có hr:leave:request mà chưa liên kết hồ sơ CBNV thì ⛔ KHÔNG nộp được — SYS-0004")
    void chuaLienKetHoSoThiKhongNopDuocDon() {
        String than = donJson(null, "PHEP_NAM", thuHai(2), thuHai(2).plusDays(2));

        ResponseEntity<String> nop = phienTroi.goi(taiKhoanTroi, HttpMethod.POST, "/api/v1/hr/nghi-phep", than);
        assertThat(nop.getStatusCode())
                .as(
                        "⛔⛔ Đây chính là lý do T51.8 phải trả TRƯỚC CN-04.9: một quy trình duyệt ⛔ không "
                                + "biết ai đang xin nghỉ là một quy trình ⛔ không duyệt được gì. %s",
                        nop.getBody())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(nop.getBody()).contains("SYS-0004");

        assertThat(phienTroi.get(taiKhoanTroi, "/api/v1/hr/nghi-phep/so-du").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // ⛔ Đối chứng PHẢI-THÀNH-CÔNG: thiếu vế này thì một endpoint hỏng hoàn toàn cũng xanh.
        lienKet(idNhanVien, taoHoSo("LK-1", donViGocPublic));
        assertThat(phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep", than)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // Đếm ngày công
    // =========================================================================

    @Test
    @DisplayName("⛔ Đơn rơi trọn vào cuối tuần = 0 ngày công ⇒ HR-2004, kèm đối chứng Hai→Sáu chạy được")
    void donToanCuoiTuanBiChan() {
        lienKet(idNhanVien, taoHoSo("CT-1", donViGocPublic));
        LocalDate thuBay = thuHai(2).plusDays(5);

        ResponseEntity<String> ra = phienNv.goi(
                nhanVien,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep",
                donJson(null, "PHEP_NAM", thuBay, thuBay.plusDays(1)));
        assertThat(ra.getStatusCode())
                .as(
                        "⛔ Một đơn 0 ngày công đi lọt là một đơn nghỉ ⛔ không nghỉ ngày nào, vẫn chiếm "
                                + "một dòng trong hộp duyệt: %s",
                        ra.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ra.getBody()).contains("HR-2004");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM leave_requests WHERE from_date = ?", Integer.class, thuBay))
                .as("⛔ Bị từ chối thì ⛔ không được ghi gì cả")
                .isZero();

        // Vế phân biệt (luật 9): cùng đường, cùng người, chỉ khác NGÀY ⇒ phải thành công.
        assertThat(phienNv.goi(
                                nhanVien,
                                HttpMethod.POST,
                                "/api/v1/hr/nghi-phep",
                                donJson(null, "PHEP_NAM", thuHai(3), thuHai(3).plusDays(4)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐ Số ngày công = ngày làm việc trừ lễ — đo bằng SQL ĐỘC LẬP, ⛔ không chép lại mã Java")
    void soNgayCongTruDungSoNgayLe() {
        lienKet(idNhanVien, taoHoSo("NC-1", donViGocPublic));
        LocalDate tu = thuHai(4);
        LocalDate den = tu.plusDays(4); // Hai → Sáu

        // ⛔ Phép đo ĐỘC LẬP: SQL đếm ngày lễ rơi vào ngày THƯỜNG trong khoảng. Chép lại vòng lặp
        //   của `DateTimeUtils.countWorkingDays` ở đây thì bài kiểm chỉ canh chính nó (luật 29).
        Integer leNgayThuong = jdbc.queryForObject(
                "SELECT count(*) FROM holidays WHERE deleted_at IS NULL AND holiday_date BETWEEN ? AND ? "
                        + "AND EXTRACT(ISODOW FROM holiday_date) < 6",
                Integer.class,
                tu,
                den);

        ResponseEntity<String> xem = phienNv.goi(
                nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep/xem-truoc", donJson(null, "PHEP_NAM", tu, den));
        assertThat(xem.getStatusCode()).as("%s", xem.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(so(xem.getBody(), "soNgayCong"))
                .as("⛔ Năm ngày trong tuần trừ %d ngày lễ rơi vào ngày thường", leNgayThuong)
                .isEqualByComparingTo(BigDecimal.valueOf(5L - leNgayThuong));

        // ⛔ Đường xem trước ⛔ KHÔNG được ghi gì — nó là một phép tính, ⛔ không phải một lượt nộp.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests WHERE from_date = ?", Integer.class, tu))
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Thiếu ngày lễ thì hệ nói THẲNG số đã khai X/11 — ⛔ không phải một cờ xanh giấu mất con số")
    void thieuNgayLeThiNoiThangSoDaKhai() {
        lienKet(idNhanVien, taoHoSo("NL-1", donViGocPublic));

        // ⛔⛔ Một năm hệ CHƯA BIẾT gì — chọn năm xa để ⛔ không đụng 8 hàng seed pháp định.
        int nam = LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN)
                        .getYear()
                + 5;
        LocalDate tu = LocalDate.of(nam, 6, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String than = donJson(null, "PHEP_NAM", tu, tu.plusDays(2));

        int nguong = DemNgayCongService.SO_NGAY_LE_THEO_LUAT;

        // ── Biên dưới: khai THIẾU đúng MỘT ngày ─────────────────────────────────────────────
        themNgayLeTho(nam, nguong - 1);
        String thieu = phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep/xem-truoc", than)
                .getBody();
        assertThat(chuoi(thieu, "soNgayLeDaKhai")).isEqualTo(String.valueOf(nguong - 1));
        assertThat(chuoi(thieu, "duNgayLeTheoLuat"))
                .as(
                        "⛔⛔ Seed đặt sẵn 4 ngày dương lịch cố định mỗi năm, nên một cờ *đã có ngày lễ "
                                + "nào chưa* sẽ nói CÓ trong khi **Tết vẫn thiếu** — đúng ca nguy hiểm nhất "
                                + "(luật 9). Vì thế cờ này phải so với %d, ⛔ không so với 0",
                        nguong)
                .isEqualTo("false");

        // ── Biên trên: thêm ĐÚNG một ngày nữa ⇒ cờ phải lật ────────────────────────────────
        themNgayLeTho(nam, nguong);
        String du = phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep/xem-truoc", than)
                .getBody();
        assertThat(chuoi(du, "soNgayLeDaKhai")).isEqualTo(String.valueOf(nguong));
        assertThat(chuoi(du, "duNgayLeTheoLuat"))
                .as("⛔ Một cờ ⛔ không lật được ở đúng biên của nó là một hằng số, ⛔ không phải một "
                        + "phép tính (luật 9)")
                .isEqualTo("true");
    }

    // =========================================================================
    // Ba chốt chặn của đường nộp
    // =========================================================================

    @Test
    @DisplayName("⛔ Đơn chồng ngày lên đơn còn hiệu lực bị chặn — HR-2005, và đơn CŨ phải còn nguyên")
    void donChongNgayBiChan() {
        lienKet(idNhanVien, taoHoSo("CN-1", donViGocPublic));
        LocalDate tu = thuHai(5);
        nopThanhCong(donJson(null, "PHEP_NAM", tu, tu.plusDays(2)));

        ResponseEntity<String> hai = phienNv.goi(
                nhanVien,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep",
                donJson(null, "PHEP_NAM", tu.plusDays(2), tu.plusDays(4)));
        assertThat(hai.getStatusCode())
                .as("⛔ Hai đơn chồng ngày là đếm HAI LẦN cùng những ngày ấy vào số dư: %s", hai.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(hai.getBody()).contains("HR-2005");

        // Chống tập rỗng: một hệ xoá sạch rồi báo lỗi cũng qua được vế trên.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leave_requests WHERE from_date = ?", Integer.class, tu))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ Vượt số dư phép năm bị chặn — HR-2006, kèm đối chứng đơn vừa đủ vẫn qua")
    void vuotSoDuBiChan() {
        lienKet(idNhanVien, taoHoSo("SD-1", donViGocPublic));
        LocalDate tu = thuHai(6);

        ResponseEntity<String> vuot = phienNv.goi(
                nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep", donJson(null, "PHEP_NAM", tu, tu.plusWeeks(7)));
        assertThat(vuot.getStatusCode()).as("%s", vuot.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(vuot.getBody())
                .as("⛔⛔ `HR-2001`, ⛔ KHÔNG phải một mã mới — nó nằm trong danh mục từ 13/08/2026 "
                        + "kèm một dòng miễn trừ *chưa dựng* trong `MaLoiCoNoiNemTest`. Lượt này nối "
                        + "nó và gỡ dòng ấy; đúc thêm `HR-2006` trùng nghĩa là để mã cũ mồ côi thêm "
                        + "một phase nữa")
                .contains("HR-2001");

        // ⭐ Vế phân biệt: cùng loại, cùng người, chỉ khác ĐỘ DÀI ⇒ phải qua. Thiếu nó thì một hệ
        //   chặn MỌI đơn phép năm cũng xanh (luật 9).
        assertThat(phienNv.goi(
                                nhanVien,
                                HttpMethod.POST,
                                "/api/v1/hr/nghi-phep",
                                donJson(null, "PHEP_NAM", tu, tu.plusDays(2)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // ⭐ Và loại ĐẶC BIỆT ⛔ không đụng số dư phép năm: 180 ngày thai sản ⛔ không được "vượt quỹ".
        assertThat(phienNv.goi(
                                nhanVien,
                                HttpMethod.POST,
                                "/api/v1/hr/nghi-phep",
                                donJson(null, "THAI_SAN", tu.plusWeeks(10), tu.plusWeeks(17)))
                        .getStatusCode())
                .as("⛔⛔ Gộp phép đặc biệt vào số dư phép năm là làm người nghỉ thai sản mất sạch phép năm")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐⭐ Số dư trừ CẢ đơn đang chờ duyệt — ⛔ không thì mười đơn cùng lúc đều *hợp lệ*")
    void soDuTruCaDonDangChoDuyet() {
        lienKet(idNhanVien, taoHoSo("SD-2", donViGocPublic));

        String truoc = phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/so-du").getBody();
        BigDecimal conLaiTruoc = so(truoc, "conLai");
        assertThat(conLaiTruoc)
                .as("⛔ Tiền đề: hồ sơ mới phải CÓ quỹ phép. Số 0 ở đây làm mọi khẳng định dưới vô nghĩa")
                .isGreaterThan(BigDecimal.ZERO);
        assertThat(chuoi(truoc, "namTruocCoDuLieu"))
                .as("⛔⛔ Năm đầu vận hành: `chuyenTuNamTruoc = 0` phải mang nghĩa *CHƯA BIẾT*, ⛔ không "
                        + "phải *đã dùng hết* — hai trạng thái khác nhau (quy tắc 16)")
                .isEqualTo("false");

        LocalDate tu = thuHai(7);
        String don = nopThanhCong(donJson(null, "PHEP_NAM", tu, tu.plusDays(2)));
        BigDecimal soNgay = so(don, "workingDays");
        assertThat(chuoi(don, "state")).isEqualTo("CHO_DUYET");

        String sau = phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/so-du").getBody();
        assertThat(so(sau, "dangChoDuyet")).isEqualByComparingTo(soNgay);
        assertThat(so(sau, "daDung"))
                .as("⛔ Đơn CHƯA duyệt ⛔ không được tính vào *đã nghỉ* — hai ô ấy trả lời hai câu khác nhau")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(so(sau, "conLai"))
                .as("⛔⛔ Đặc tả: *Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt*. Bỏ vế đang chờ là "
                        + "cho nộp mười đơn cùng lúc, mỗi đơn nhìn một số dư chưa trừ chín đơn kia")
                .isEqualByComparingTo(conLaiTruoc.subtract(soNgay));
    }

    @Test
    @DisplayName("⭐⭐ Nộp đơn xong thì NGƯỜI DUYỆT nhận được thông báo — ⛔ không thì hộp chờ ⛔ không ai mở")
    void nopDonThiNguoiDuyetNhanDuocThongBao() {
        lienKet(idNhanVien, taoHoSo("TB-1", donViGocPublic));
        long userDuyet = jdbc.queryForObject("SELECT id FROM users WHERE public_id = ?", Long.class, idNguoiDuyet);
        long truoc = demThongBaoToi(userDuyet);

        LocalDate tu = thuHai(14);
        nopThanhCong(donJson(null, "PHEP_NAM", tu, tu.plusDays(1)));

        assertThat(demThongBaoToi(userDuyet))
                .as("⛔⛔ Ràng buộc `ck_workflow_transitions_notify_target_needs_event` đã bắt được ba "
                        + "bước chuyển của lượt seed đặt `notify_permission` mà quên `notify_event` — "
                        + "tức thông báo sẽ ⛔ KHÔNG BAO GIỜ sinh ra, và người duyệt ⛔ không bao giờ "
                        + "biết có đơn đang chờ. CSDL biến một lỗi seed IM LẶNG thành một lượt deploy "
                        + "đỏ; bài này là vế còn lại — nó đo rằng thông báo thật sự TỚI NƠI")
                .isEqualTo(truoc + 1);
    }

    // =========================================================================
    // ⭐⭐ Vế KHÔNG diễn đạt được bằng một mã quyền: phạm vi đơn vị
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Quản lý XN-B ⛔ KHÔNG duyệt được đơn của XN-A — dù có đủ hr:leave:approve")
    void khongDuyetDuocDonNgoaiDonVi() {
        lienKet(idNhanVien, taoHoSo("PV-1", xnAPublic));
        LocalDate tu = thuHai(8);
        String don = nopThanhCong(donJson(null, "PHEP_NAM", tu, tu.plusDays(2)));
        UUID donId = UUID.fromString(chuoi(don, "publicId"));

        assertThat(jdbc.queryForObject("SELECT org_unit_id FROM leave_requests WHERE public_id = ?", Long.class, donId))
                .as("⛔ Tiền đề: đơn phải mang BẢN SAO đơn vị của nhân viên lúc nộp")
                .isEqualTo(xnAId);

        datDonViChoTaiKhoan(idNguoiDuyet, xnBId);

        assertThat(phienDuyet.get(nguoiDuyet, "/api/v1/hr/nghi-phep/cho-duyet").getBody())
                .as("⛔⛔ `required_permission` là một MÃ QUYỀN; đặc tả nói một QUAN HỆ (*quản lý của "
                        + "đơn vị người nộp*). Vế còn lại là bộ lọc phạm vi — thiếu nó thì trưởng "
                        + "Xí nghiệp 3 duyệt đơn của Xí nghiệp 5")
                .doesNotContain(donId.toString());

        ResponseEntity<String> duyetLen = phienDuyet.goi(
                nguoiDuyet,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/" + donId + "/hanh-dong",
                "{\"action\":\"APPROVE\"}");
        assertThat(duyetLen.getStatusCode())
                .as(
                        "⛔ ⛔ Không nhìn thấy thì ⛔ không có gì để bấm — kể cả khi đoán đúng publicId: %s",
                        duyetLen.getBody())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT state FROM leave_requests WHERE public_id = ?", String.class, donId))
                .isEqualTo("CHO_DUYET");

        // ⭐ Vế phân biệt: CHÍNH tài khoản ấy, CHÍNH đơn ấy, chỉ khác ĐƠN VỊ ⇒ duyệt được.
        datDonViChoTaiKhoan(idNguoiDuyet, xnAId);
        assertThat(phienDuyet.get(nguoiDuyet, "/api/v1/hr/nghi-phep/cho-duyet").getBody())
                .contains(donId.toString());
        ResponseEntity<String> duyetTrong = phienDuyet.goi(
                nguoiDuyet,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/" + donId + "/hanh-dong",
                "{\"action\":\"APPROVE\"}");
        assertThat(duyetTrong.getStatusCode()).as("%s", duyetTrong.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuoi(duyetTrong.getBody(), "state")).isEqualTo("DA_DUYET");
    }

    // =========================================================================
    // ⭐ Chốt C2: ngưỡng cấp duyệt thứ hai đọc từ `settings`
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Ngưỡng cấp 2 đọc từ settings — cùng một đơn, hai cấu hình, HAI trạng thái khác nhau")
    void nguongCapHaiDocTuSettings() {
        lienKet(idNhanVien, taoHoSo("C2-1", donViGocPublic));

        // ── Cấu hình A (seed: 1 cấp, ngưỡng 0) ⇒ APPROVE đi thẳng tới DA_DUYET ──────────────
        LocalDate motCap = thuHai(9);
        UUID donA =
                UUID.fromString(chuoi(nopThanhCong(donJson(null, "PHEP_NAM", motCap, motCap.plusDays(2))), "publicId"));
        assertThat(chuoi(duyet(donA), "state")).isEqualTo("DA_DUYET");

        // ── Cấu hình B: hai cấp, ngưỡng 2 ngày ⇒ CÙNG hành động APPROVE dừng ở CHO_DUYET_2 ──
        datThamSo(KHOA_SO_CAP, "2");
        datThamSo(KHOA_NGUONG_CAP_2, "2");

        LocalDate haiCap = thuHai(10);
        UUID donB =
                UUID.fromString(chuoi(nopThanhCong(donJson(null, "PHEP_NAM", haiCap, haiCap.plusDays(2))), "publicId"));
        assertThat(chuoi(duyet(donB), "state"))
                .as("⛔⛔ Hai khoá `settings` của chốt C2 phải điều khiển QUY TRÌNH, ⛔ không điều khiển "
                        + "TRÌNH DUYỆT: người gọi gửi APPROVE, service mới là chỗ đổi nó thành ESCALATE. "
                        + "Đặt phép đổi ấy ở giao diện là biến hai núm cấu hình thành hai núm trang trí")
                .isEqualTo("CHO_DUYET_2");

        // Cấp 2 duyệt tiếp ⇒ mới tới DA_DUYET. Đây là vế chứng minh CHO_DUYET_2 ⛔ không phải ngõ cụt.
        assertThat(chuoi(duyet(donB), "state")).isEqualTo("DA_DUYET");
    }

    // =========================================================================
    // Chốt C3: nộp hộ
    // =========================================================================

    @Test
    @DisplayName("⭐ Nộp hộ ghi *người tạo hộ* và đòi hr:leave:view-all — ⛔ không phải ai cũng nộp thay người khác")
    void nopHoGhiNguoiTaoHo() {
        UUID hoSo = taoHoSo("NH-1", donViGocPublic);
        LocalDate tu = thuHai(11);
        String than = donJson(hoSo, "PHEP_NAM", tu, tu.plusDays(2));

        // ⛔ Tài khoản chỉ có `hr:leave:request` ⛔ không được nộp hộ người khác.
        lienKet(idNhanVien, taoHoSo("NH-2", donViGocPublic));
        ResponseEntity<String> traiPhep = phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep", than);
        assertThat(traiPhep.getStatusCode())
                .as(
                        "⛔⛔ Thiếu cổng này thì bất kỳ CBNV nào cũng nộp đơn nghỉ ĐỨNG TÊN người khác: %s",
                        traiPhep.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(traiPhep.getBody())
                .as("⛔⛔ Bài này bắt CHÍNH tôi ở lượt chạy đầu: bản đầu của service ném "
                        + "`PermissionDeniedException(AUTH_3001, \"hr:leave:view-all\")` và khẳng định "
                        + "ở đây tìm chuỗi ấy trong thân phản hồi. Đo thật: nó ⛔ KHÔNG có ở đó — "
                        + "`AUTH-3001` là mã 403 dùng chung, thông điệp ⛔ không có chỗ cắm `{n}` nên "
                        + "`MessageFormat` bỏ lặng đối số. ⇒ Khẳng định đúng là MÃ LỖI; vế phân biệt "
                        + "*đúng người thì qua* nằm ở lượt gọi của quản trị ngay dưới (luật 9)")
                .contains("AUTH-3001");

        // ⭐ Quản lý có `hr:leave:view-all` thì nộp hộ được, và đơn PHẢI khai ra rằng nó là nộp hộ.
        ResponseEntity<String> noHo = phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/nghi-phep", than);
        assertThat(noHo.getStatusCode()).as("%s", noHo.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuoi(noHo.getBody(), "noHo"))
                .as("⛔ Một đơn nộp hộ và một đơn tự nộp mang hai mức tin cậy khác nhau khi đối chiếu "
                        + "về sau — gộp chúng là mất luôn khả năng phân biệt (chốt C3)")
                .isEqualTo("true");
        assertThat(chuoi(noHo.getBody(), "employeePublicId")).isEqualTo(hoSo.toString());

        UUID donId = UUID.fromString(chuoi(noHo.getBody(), "publicId"));
        assertThat(jdbc.queryForObject(
                        "SELECT created_for_by FROM leave_requests WHERE public_id = ?", Long.class, donId))
                .as("⛔ `created_for_by` phải CÓ giá trị — nó khai RẰNG lượt bấm ấy là nộp hộ, khác hẳn "
                        + "`created_by` vốn luôn là người bấm nút")
                .isNotNull();

        // Nửa ĐỌC của cặp: đường `cua-nhan-vien` (đầu nhận đầu tiên của `hr:leave:view-all`).
        assertThat(phienQt.get(quanTri, "/api/v1/hr/nghi-phep/cua-nhan-vien/" + hoSo)
                        .getBody())
                .contains(donId.toString());
        assertThat(phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/cua-nhan-vien/" + hoSo)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Huỷ đơn
    // =========================================================================

    @Test
    @DisplayName(
            "⛔ Đơn đã duyệt và ĐÃ BẮT ĐẦU nghỉ thì ⛔ không huỷ được — HR-2007, kèm đối chứng đơn tương lai huỷ được")
    void khongHuyDonDaBatDauNghi() {
        lienKet(idNhanVien, taoHoSo("HU-1", donViGocPublic));

        // Đơn đã bắt đầu: ngày trong QUÁ KHỨ. Lược đồ ⛔ không cấm — người ta vẫn khai bù đơn cũ.
        LocalDate quaKhu = thuHai(-2);
        UUID cu =
                UUID.fromString(chuoi(nopThanhCong(donJson(null, "PHEP_NAM", quaKhu, quaKhu.plusDays(2))), "publicId"));
        assertThat(chuoi(duyet(cu), "state")).isEqualTo("DA_DUYET");

        ResponseEntity<String> huy = phienNv.goi(
                nhanVien,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/" + cu + "/hanh-dong",
                "{\"action\":\"CANCEL\",\"reason\":\"Đổi kế hoạch\"}");
        assertThat(huy.getStatusCode())
                .as(
                        "⛔⛔ Huỷ một kỳ nghỉ ĐÃ diễn ra là trả lại số dư cho những ngày người ta đã thật "
                                + "sự vắng mặt: %s",
                        huy.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(huy.getBody()).contains("HR-2007");
        assertThat(jdbc.queryForObject("SELECT state FROM leave_requests WHERE public_id = ?", String.class, cu))
                .isEqualTo("DA_DUYET");

        // ⭐ Vế phân biệt: CÙNG hành động, CÙNG trạng thái DA_DUYET, chỉ khác NGÀY ⇒ huỷ được.
        LocalDate tuongLai = thuHai(12);
        UUID moi = UUID.fromString(
                chuoi(nopThanhCong(donJson(null, "PHEP_NAM", tuongLai, tuongLai.plusDays(1))), "publicId"));
        assertThat(chuoi(duyet(moi), "state")).isEqualTo("DA_DUYET");
        ResponseEntity<String> huyDuoc = phienNv.goi(
                nhanVien,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/" + moi + "/hanh-dong",
                "{\"action\":\"CANCEL\",\"reason\":\"Đổi kế hoạch\"}");
        assertThat(huyDuoc.getStatusCode()).as("%s", huyDuoc.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuoi(huyDuoc.getBody(), "state")).isEqualTo("DA_HUY");
    }

    @Test
    @DisplayName("⭐ Đơn đã huỷ TRẢ LẠI số dư — vòng khép kín nộp → duyệt → huỷ → số dư về như cũ")
    void huyDonThiTraLaiSoDu() {
        lienKet(idNhanVien, taoHoSo("HU-2", donViGocPublic));
        BigDecimal banDau =
                so(phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/so-du").getBody(), "conLai");

        LocalDate tu = thuHai(13);
        UUID don = UUID.fromString(chuoi(nopThanhCong(donJson(null, "PHEP_NAM", tu, tu.plusDays(2))), "publicId"));
        assertThat(so(phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/so-du").getBody(), "conLai"))
                .as("⛔ Tiền đề: nộp xong thì số dư PHẢI giảm — thiếu vế này thì bài dưới xanh cả khi "
                        + "số dư chưa bao giờ đổi (luật 7)")
                .isLessThan(banDau);

        ResponseEntity<String> huy = phienNv.goi(
                nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep/" + don + "/hanh-dong", "{\"action\":\"CANCEL\"}");
        assertThat(huy.getStatusCode()).as("%s", huy.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(so(phienNv.get(nhanVien, "/api/v1/hr/nghi-phep/so-du").getBody(), "conLai"))
                .as("⛔⛔ Số dư TÍNH LẠI từ đơn, ⛔ không đọc một cột đếm nào — nên một đơn rút về ⛔ "
                        + "không cần ai đi cộng trả lại, và ⛔ không có cột nào để lệch")
                .isEqualByComparingTo(banDau);
    }

    // =========================================================================
    // Danh mục ngày lễ
    // =========================================================================

    @Test
    @DisplayName("⭐ Ngày lễ: ĐỌC mở cho mọi người đăng nhập, GHI đòi hr:contract:manage; trùng ngày ⇒ HR-2008")
    void ngayLeDocMoGhiDong() {
        // Đường đọc: tài khoản chỉ có `hr:leave:request` vẫn xem được lịch lễ.
        assertThat(phienNv.get(nhanVien, "/api/v1/hr/ngay-le").getStatusCode())
                .as("⛔ Một ô *còn 8 ngày công* mà ⛔ không xem được vì sao là một con số ⛔ không ai tin")
                .isEqualTo(HttpStatus.OK);

        LocalDate ngay = LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN)
                .plusYears(6)
                .withDayOfYear(100);
        String than = "{\"holidayDate\":\"%s\",\"name\":\"%sGiỗ Tổ Hùng Vương\"}".formatted(ngay, TIEN_TO);

        // Đường ghi: CÙNG thân yêu cầu, hai tài khoản, hai kết quả (luật 9).
        assertThat(phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/ngay-le", than)
                        .getStatusCode())
                .as("⛔⛔ Thêm nhầm một ngày lễ là CẤP THÊM phép cho toàn Công ty, bớt nhầm một ngày là "
                        + "TRỪ OAN — nên đường ghi ⛔ không được mở như đường đọc")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> tao = phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/ngay-le", than);
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> trung = phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/ngay-le", than);
        assertThat(trung.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(trung.getBody()).contains("HR-2008");

        UUID id = UUID.fromString(chuoi(tao.getBody(), "publicId"));
        assertThat(phienQt.goi(quanTri, HttpMethod.DELETE, "/api/v1/hr/ngay-le/" + id, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // ⭐ Xoá MỀM: hàng còn đó, chỉ mang `deleted_at`. Xoá cứng là làm đơn cũ mất dấu vết lý do
        //   nó đếm ra ngần ấy ngày công.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM holidays WHERE public_id = ? AND deleted_at IS NOT NULL",
                        Integer.class,
                        id))
                .isEqualTo(1);

        // Và chỉ mục duy nhất PHẢI cho phép khai lại đúng ngày ấy sau khi đã xoá mềm.
        assertThat(phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/ngay-le", than)
                        .getStatusCode())
                .as("⛔ `uq_holidays_date` mà ⛔ không có vế `WHERE deleted_at IS NULL` thì một lượt xoá "
                        + "nhầm KHOÁ CHẾT ngày ấy vĩnh viễn")
                .isEqualTo(HttpStatus.CREATED);
    }

    // =========================================================================
    // Trợ giúp
    // =========================================================================

    private String donJson(UUID hoSo, String loai, LocalDate tu, LocalDate den) {
        return """
                {"employeePublicId":%s,"leaveType":"%s","fromDate":"%s","toDate":"%s","reason":"Kiểm thử T57.9"}"""
                .formatted(hoSo == null ? "null" : "\"" + hoSo + "\"", loai, tu, den);
    }

    private String nopThanhCong(String than) {
        ResponseEntity<String> ra = phienNv.goi(nhanVien, HttpMethod.POST, "/api/v1/hr/nghi-phep", than);
        assertThat(ra.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", ra.getBody())
                .isEqualTo(HttpStatus.OK);
        return ra.getBody();
    }

    private String duyet(UUID donId) {
        ResponseEntity<String> ra = phienDuyet.goi(
                nguoiDuyet,
                HttpMethod.POST,
                "/api/v1/hr/nghi-phep/" + donId + "/hanh-dong",
                "{\"action\":\"APPROVE\"}");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        return ra.getBody();
    }

    private UUID taoHoSo(String hau, UUID donVi) {
        String than =
                """
                {"code":"%s","fullName":"Lê Thị Hoa","dateOfBirth":"1990-02-11","gender":"NU",\
                "orgUnitId":"%s","hiredAt":"2015-01-05",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, donVi);
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
     * Đặt đơn vị cho một tài khoản — và <b>xoá đệm quyền</b>.
     *
     * <p>⚠ {@code AuthorityLoader} suy phạm vi từ {@code users.org_unit_id} và giữ kết quả trong
     * một đệm TTL 30 giây <b>ngoài</b> transaction. Thiếu lượt {@code invalidate} thì bài kiểm đo
     * phạm vi CŨ trong khi CSDL đã mang phạm vi MỚI — và nó xanh hay đỏ tuỳ vào việc lượt gọi
     * trước đó có kịp nạp đệm hay chưa.
     */
    private void datDonViChoTaiKhoan(UUID taiKhoan, long donViId) {
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", donViId, taiKhoan);
        authorities.invalidateAll();
    }

    /**
     * Thêm ngày lễ <b>thô</b> cho tới khi năm {@code nam} có đúng {@code soLuong} hàng.
     *
     * <p>⛔ Cố ý đi thẳng CSDL: đây là <b>đồ gá</b>, ⛔ không phải thứ đang kiểm — đường ghi qua API
     * đã có bài riêng ({@link #ngayLeDocMoGhiDong()}), và 11 lượt POST ở đây chỉ tiêu ngân sách hạn
     * mức của một phiên rồi làm một lớp khác đỏ với {@code SYS-0002}.
     */
    private void themNgayLeTho(int nam, int soLuong) {
        for (int i = 0; i < soLuong; i++) {
            LocalDate ngay = LocalDate.of(nam, 3, 1).plusDays(i);
            jdbc.update(
                    "INSERT INTO holidays (holiday_date, name, created_at) VALUES (?, ?, now()) "
                            + "ON CONFLICT DO NOTHING",
                    ngay,
                    TIEN_TO + "Lễ kiểm thử " + i);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM holidays WHERE deleted_at IS NULL AND EXTRACT(YEAR FROM holiday_date) = ?",
                        Integer.class,
                        nam))
                .as("⛔ Chống tập rỗng: năm %d phải có ĐÚNG %d ngày lễ thì hai biên dưới mới có nghĩa", nam, soLuong)
                .isEqualTo(soLuong);
    }

    /** Thứ Hai của tuần cách tuần này {@code soTuan} tuần — âm là quá khứ. */
    private static LocalDate thuHai(int soTuan) {
        return LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(soTuan);
    }

    private void datThamSo(String khoa, String giaTri) {
        jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        settings.invalidate(khoa);
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
                        + "và mọi bài dưới đây đỏ với 403 — triệu chứng chẳng liên quan gì tới thứ đang kiểm")
                .isEqualTo(quyen.size());
    }

    private long themDonVi(String ma) {
        jdbc.update(
                // ⚠ ⛔ Không `ON CONFLICT (code)`: chỉ mục duy nhất của `org_units` là PARTIAL nên
                //   Postgres ⛔ không suy ra được ràng buộc — nó trả `bad SQL grammar`.
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                donViGocId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ? WHERE id = ?", pathGoc + id + "/", id);
        return id;
    }

    /**
     * Số thông báo {@code LEAVE_SUBMITTED} đã tới tay một tài khoản.
     *
     * <p>⚠ {@code COUNT(DISTINCT n.id)} chứ ⛔ không đếm dòng người nhận: {@code
     * notification_recipients} có <b>một dòng mỗi KÊNH</b> (IN_APP + EMAIL), nên một lượt nộp sinh
     * <b>hai</b> dòng cho cùng một người. Bản đầu của bài này đếm dòng và đỏ với {@code 20 ≠ 19} —
     * một con số đúng của một câu hỏi khác.
     */
    private long demThongBaoToi(long userId) {
        return jdbc.queryForObject(
                "SELECT count(DISTINCT n.id) FROM notification_recipients nr "
                        + "JOIN notifications n ON n.id = nr.notification_id "
                        + "WHERE nr.user_id = ? AND n.event_type = 'LEAVE_SUBMITTED'",
                Long.class,
                userId);
    }

    private UUID publicIdCua(String username) {
        return jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, username);
    }

    private void don() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t579%'");
        jdbc.update(
                "DELETE FROM leave_requests WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM holidays WHERE name LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_t579%'");
        // ⛔⛔ Hai bảng NỀN TẢNG trỏ khoá ngoại vào `org_units`, và lượt dọn đầu tiên của lớp này
        //    đỏ vì đúng chúng: `jobs_org_unit_id_fkey`. Đó ⛔ không phải rác — nó là BẰNG CHỨNG
        //    rằng bước `SUBMIT` đã thật sự sinh thông báo cho người duyệt (`notify_event`
        //    `LEAVE_SUBMITTED`). Xem `nopDonThiNguoiDuyetNhanDuocThongBao`.
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

    private static BigDecimal so(String json, String truong) {
        String tho = chuoi(json, truong);
        assertThat(tho).as("⛔ ⛔ Không thấy trường `%s` trong: %s", truong, json).isNotNull();
        return new BigDecimal(tho.trim());
    }
}
