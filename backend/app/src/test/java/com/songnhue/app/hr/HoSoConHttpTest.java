package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
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
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hr.domain.HoSoThuMuc;

/**
 * Ba lớp hồ sơ con của một CBNV, đo <b>qua HTTP</b> — WS-53 · CN-04.3, CN-04.4, CN-04.5.
 *
 * <h2>Vì sao qua HTTP (luật 5)</h2>
 *
 * <p>Ba bảo đảm của đợt này ⛔ không nằm trong service, và ⛔ không lượt gọi service nào chạm tới:
 *
 * <ul>
 *   <li><b>Vòng khứ hồi {@code GET} → {@code PUT}</b> của mục lý lịch — §11.19: một {@code PUT}
 *       thiếu trường xoá trắng dữ liệu và <b>chỉ có triệu chứng kể từ ngày dữ liệu tồn tại</b>, tức
 *       sau khi Công ty nhập thật (G6-a).
 *   <li><b>Trần dung lượng theo THƯ MỤC</b> — nó chạy trên thân multipart thật, và
 *       {@code UploadSizeCeilingTest} đã trả giá cho đúng chỗ này: một tầng chỉ chạy khi có thân
 *       multipart để phân tích sống sót qua 723 bài kiểm.
 *   <li><b>Ngưỡng cảnh báo đọc từ {@code settings}</b> — hai khoá mồ côi 28 ngày. Một bài gọi
 *       service ⛔ không chứng minh được rằng đổi giá trị trong CSDL thì hành vi đổi theo.
 * </ul>
 *
 * <h2>⛔ Dữ liệu của lớp này KHÔNG phải seed</h2>
 *
 * <p>Mọi hàng sống đúng bằng thời gian lớp chạy và bị xoá <b>cứng</b> ở {@code @AfterAll}, có
 * khẳng định ngay tại chỗ dọn. G6-a vẫn là ô trống và các bảng vẫn rỗng sau lượt chạy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoSoConHttpTest extends IntegrationTestBase {

    private static final String DUONG_HO_SO = "/api/v1/hr/employees";
    private static final String DUONG_CHUC_VU = "/api/v1/hr/positions";
    private static final String TIEN_TO = "WS53HTTP-";
    private static final String VAI_TRO_TAM = "KIEMTRA_HR_HOSO_CON";

    private static final List<String> QUYEN_CAN =
            List.of("hr:employee:view", "hr:employee:create", "hr:employee:update", "hr:employee:delete");

    /** Một PDF tối thiểu — Tika nhận diện bằng chữ ký {@code %PDF-} ở đầu tệp. */
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * ⛔⛔ {@link SettingService} có bộ nhớ đệm <b>Caffeine trong tiến trình</b> — một câu
     * {@code UPDATE settings} thẳng vào CSDL <b>⛔ không tới được ứng dụng</b>.
     *
     * <p>⚠ Bản đầu của lớp này quên điều đó và <b>ba</b> bài đỏ với cùng một triệu chứng: ngưỡng trả
     * ra vẫn là giá trị cũ. Đó là hình dạng §10.67 (*bản vá sống trên đĩa mà tiến trình vẫn chạy giá
     * trị cũ*) — và nó nguy hiểm theo chiều ngược lại: một bài kiểm <b>quên</b> dọn đệm sẽ khẳng
     * định *"đổi tham số ⛔ không có tác dụng"* trong khi mã hoàn toàn đúng, rồi ai đó đi "sửa" mã.
     */
    @Autowired
    private SettingService settingService;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;
    private UUID donViGoc;

    @BeforeAll
    void dungVaiTroVaDangNhap() {
        donDuLieuThu();
        donSachVaiTroTam();

        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử hồ sơ con', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO_TAM);
        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code IN (?, ?, ?, ?)",
                VAI_TRO_TAM,
                QUYEN_CAN.get(0),
                QUYEN_CAN.get(1),
                QUYEN_CAN.get(2),
                QUYEN_CAN.get(3));
        assertThat(soQuyen)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM LẶNG "
                        + "và mọi bài dưới đây đỏ với 403 — triệu chứng chẳng liên quan gì tới thứ đang kiểm")
                .isEqualTo(4);

        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        assertThat(donViGoc).isNotNull();

        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "hr_hosocon", VAI_TRO_TAM));
    }

    @AfterAll
    void don() {
        donDuLieuThu();
        donSachVaiTroTam();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV — bảng phải RỖNG lại")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employee_qualifications q JOIN employees e ON e.id = q.employee_id "
                                + "WHERE e.code LIKE ?",
                        Integer.class,
                        TIEN_TO + "%"))
                .isZero();
    }

    // =========================================================================
    // CN-04.3 — Lý lịch & chuyên môn
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Lý lịch: gửi NGUYÊN VĂN thân GET làm thân PUT ⇒ ⛔ không rơi một trường nào (§11.19)")
    void guiLaiThanGetLamPutKhongRoiTruong() {
        UUID hoSo = taoHoSo("LL-001");
        UUID muc = taoMucLyLichDayDu(hoSo);

        String truoc = phienHttp.get(quanTri, duongLyLich(hoSo)).getBody();
        assertThat(truoc).isNotNull();

        // ⛔ Lấy đúng object của mục vừa tạo trong mảng, rồi gửi lại NGUYÊN VĂN các trường của nó.
        String than = thanPutTuView(truoc, muc);
        ResponseEntity<String> put = phienHttp.goi(quanTri, HttpMethod.PUT, duongLyLich(hoSo) + "/" + muc, than);
        assertThat(put.getStatusCode()).as("%s", put.getBody()).isEqualTo(HttpStatus.OK);

        // Đối chiếu TỪNG trường một, ⛔ không so cả chuỗi: so chuỗi xanh cả khi hai bên cùng rỗng.
        List<String> truongPhaiCon = List.of(
                "kind", "name", "grade", "major", "institution", "certificateNo", "issuedOn", "expiresOn", "note");
        int soKhac0 = 0;
        for (String truong : truongPhaiCon) {
            String cu = chuoi(truoc, truong);
            String moi = chuoi(put.getBody(), truong);
            assertThat(moi)
                    .as(
                            "⛔ Trường `%s` rơi mất sau lượt PUT nguyên văn — đây là §11.19, và nó chỉ có "
                                    + "triệu chứng kể từ ngày Công ty nhập dữ liệu thật",
                            truong)
                    .isEqualTo(cu);
            if (cu != null && !cu.isBlank()) {
                soKhac0++;
            }
        }
        assertThat(soKhac0)
                .as("⛔⛔ Chống tập rỗng (luật 7): nếu mọi ô đều rỗng thì chín khẳng định trên xanh "
                        + "trọn vẹn mà ⛔ không chứng minh gì. Bản mốc PHẢI đang có dữ liệu")
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("⛔ Mục lý lịch của hồ sơ A ⛔ không với tới được qua đường dẫn của hồ sơ B — IDOR")
    void mucCuaHoSoKhacThiKhongVoiToi() {
        UUID hoSoA = taoHoSo("LL-A");
        UUID hoSoB = taoHoSo("LL-B");
        UUID mucCuaA = taoMucLyLichDayDu(hoSoA);

        ResponseEntity<String> qua =
                phienHttp.goi(quanTri, HttpMethod.PUT, duongLyLich(hoSoB) + "/" + mucCuaA, thanLyLich("CHUNG_CHI"));
        assertThat(qua.getStatusCode())
                .as("⛔ Kiểm quyền ở tham số thứ nhất mà ⛔ không ràng buộc tham số thứ hai là một lỗ "
                        + "IDOR trông y hệt mã đúng")
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Đối chứng PHẢI-THÀNH-CÔNG: thiếu nó thì một hệ từ chối TẤT CẢ cũng xanh (luật 9).
        assertThat(phienHttp
                        .goi(quanTri, HttpMethod.PUT, duongLyLich(hoSoA) + "/" + mucCuaA, thanLyLich("CHUNG_CHI"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // CN-04.4 — Timeline
    // =========================================================================

    @Test
    @DisplayName("⭐ Timeline sắp theo ngày HIỆU LỰC giảm dần — ⛔ không theo ngày ký, ⛔ không theo ngày nhập")
    void timelineSapTheoNgayHieuLuc() {
        UUID hoSo = taoHoSo("TL-001");

        // ⛔ Nhập theo thứ tự XÁO TRỘN, và ngày KÝ cố ý ngược chiều ngày hiệu lực: nếu ai đó sắp
        //    theo `decisionDate` hay `createdAt` thì thứ tự ra sẽ khác hẳn — đó là vế phân biệt.
        taoSuKien(hoSo, "DIEU_DONG", "2021-06-01", "2021-05-20", "Điều động về Xí nghiệp 2");
        taoSuKien(hoSo, "TUYEN_DUNG", "2019-01-15", "2024-01-01", "Tuyển dụng lần đầu");
        taoSuKien(hoSo, "NANG_LUONG", "2023-09-01", "2019-01-02", "Nâng bậc lương");

        String body = phienHttp.get(quanTri, duongTimeline(hoSo)).getBody();
        List<String> ngay = moiGiaTri(body, "effectiveOn");

        assertThat(ngay).as("⛔ Chống tập rỗng — ba sự kiện vừa tạo phải có mặt").hasSize(3);
        assertThat(ngay)
                .as("⛔ Thứ tự phải là ngày HIỆU LỰC giảm dần. Sắp theo `decisionDate` cho ra "
                        + "[2024-01-01, 2021-05-20, 2019-01-02] — một thứ tự KHÁC HẲN")
                .containsExactly("2023-09-01", "2021-06-01", "2019-01-15");

        List<String> loc = moiGiaTri(
                phienHttp.get(quanTri, duongTimeline(hoSo) + "?loai=NANG_LUONG").getBody(), "effectiveOn");
        assertThat(loc).containsExactly("2023-09-01");
    }

    // =========================================================================
    // CN-04.5 — Tài liệu
    // =========================================================================

    @Test
    @DisplayName("⭐ Tải lại CÙNG thư mục ⇒ phiên bản KẾ TIẾP, bản cũ giữ nguyên (đặc tả: ⛔ không ghi đè)")
    void taiLaiCungThuMucThiLenPhienBanMoi() {
        UUID hoSo = taoHoSo("TL-DOC");

        ResponseEntity<String> lan1 = tai(hoSo, HoSoThuMuc.HOP_DONG, "hd-2025.pdf");
        assertThat(lan1.getStatusCode()).as("%s", lan1.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(chuoi(lan1.getBody(), "phienBan")).isEqualTo("1");

        ResponseEntity<String> lan2 = tai(hoSo, HoSoThuMuc.HOP_DONG, "hd-2026.pdf");
        assertThat(chuoi(lan2.getBody(), "phienBan"))
                .as("⛔ Versioning là hành vi SẴN CÓ của AttachmentService.nextVersion(owner, purpose) "
                        + "— nếu nó về 1 nghĩa là `purpose` ⛔ không được truyền đúng")
                .isEqualTo("2");

        List<String> ten = moiGiaTri(phienHttp.get(quanTri, duongTaiLieu(hoSo)).getBody(), "tenGoc");
        assertThat(ten)
                .as("⛔ Bản CŨ phải còn — 'không ghi đè' nghĩa là hai hàng, ⛔ không phải một hàng bị sửa")
                .contains("hd-2025.pdf", "hd-2026.pdf");
    }

    @Test
    @DisplayName("⛔⛔ Trần dung lượng RIÊNG của thư mục có hiệu lực — và nó ĐỌC TỪ settings, ⛔ không ghi cứng")
    void tranDungLuongTheoThuMucDocTuSettings() {
        UUID hoSo = taoHoSo("TL-LIMIT");

        // Thư mục ANH: seed 5MB. Dựng một "ảnh" 2MB — dưới trần, phải qua.
        // ⚠ Dùng PDF cho đơn giản về magic bytes; trần theo thư mục ⛔ không phụ thuộc định dạng.
        byte[] haiMb = tepCoKichThuoc(2 * 1024 * 1024);
        assertThat(tai(hoSo, HoSoThuMuc.ANH, "anh.pdf", haiMb).getStatusCode())
                .as("⛔ Đối chứng PHẢI-THÀNH-CÔNG — thiếu nó thì một hệ từ chối MỌI tệp cũng xanh")
                .isEqualTo(HttpStatus.CREATED);

        // ⭐ Hạ trần thư mục ANH xuống 1MB trong CSDL rồi thử lại CHÍNH tệp ấy.
        //   ⛔ Đây là phép đo DUY NHẤT phân biệt "đọc từ settings" với "ghi cứng 5": cùng một tệp,
        //     chỉ khác giá trị trong bảng (T48.7/T48.9).
        int cu = jdbc.queryForObject(
                "SELECT setting_value::int FROM settings WHERE setting_key = 'hr.document.max-mb.ANH'", Integer.class);
        assertThat(cu)
                .as("⛔ Khoá phải TỒN TẠI — nếu ⛔ không, bài dưới xanh vì rơi về dự phòng")
                .isEqualTo(5);
        try {
            datThamSo("hr.document.max-mb.ANH", "1");
            ResponseEntity<String> qua = tai(hoSo, HoSoThuMuc.ANH, "anh2.pdf", haiMb);
            assertThat(qua.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(qua.getBody())
                    .as("⛔ HR-2003 chứ ⛔ không SYS-0010: mã kia là hạn mức CẢ HỒ SƠ, mã này là trần "
                            + "MỖI TỆP theo thư mục. Gộp hai mã là để người vận hành đi sửa nhầm tham số")
                    .contains("HR-2003");
        } finally {
            // ⛔ Khôi phục trong `finally`, ⛔ không ở cuối phương thức (T48.8/T51.17): một khẳng
            //    định đỏ phía trên là kẹt giá trị 1MB, rò sang mọi lớp chạy sau.
            datThamSo("hr.document.max-mb.ANH", String.valueOf(cu));
        }
    }

    @Test
    @DisplayName("⛔ Bảy thư mục của enum khớp ĐÚNG bảy khoá settings — hai nơi con người phải nhớ (luật 14)")
    void bayThuMucKhopBayKhoaSettings() {
        // ⚠⚠ ⛔ KHÔNG sắp ở SQL rồi so thứ tự với `sorted()` của Java: CSDL của dự án chạy
        //    collation ICU `vi-VN`, và nó xếp `HO_SO_Y_TE` TRƯỚC `HOP_DONG` trong khi Java (thứ tự
        //    mã ASCII, 'P' < '_') xếp ngược lại. Hai bộ sắp khác nhau cho ra một bài kiểm đỏ về một
        //    khác biệt ⛔ không liên quan gì tới thứ nó canh. Ở đây thứ tự ⛔ không phải điều cần
        //    khẳng định — TẬP mới là.
        List<String> khoa = jdbc.queryForList(
                "SELECT setting_key FROM settings WHERE setting_key LIKE 'hr.document.max-mb.%'", String.class);
        List<String> tuEnum = java.util.Arrays.stream(HoSoThuMuc.values())
                .map(HoSoThuMuc::khoaHanMuc)
                .toList();

        assertThat(khoa).as("⛔ Chống tập rỗng").hasSize(7);
        assertThat(khoa)
                .as("⛔ Enum và khối seed phải khai CÙNG một tập bảy. Thêm một thư mục mà quên seed "
                        + "khoá ⇒ nó rơi về dự phòng 10MB trong im lặng, và ô cấu hình ⛔ không tồn tại")
                .containsExactlyInAnyOrderElementsOf(tuEnum);
    }

    /**
     * ⛔⛔ Bài này bắt được một khuyết tật trong <b>thiết kế của chính tôi</b>, và bản vá là ở
     * <b>migration</b> chứ ⛔ không ở đây.
     *
     * <p>Bản đầu seed {@code hr.document.required-folders = 'GIAY_TO_TUY_THAN,HOP_DONG'} như một
     * *"đề xuất tối thiểu"*. Hai điều sai cùng lúc:
     *
     * <ol>
     *   <li><b>Nghiệp vụ</b> — in % dựa trên một luật nhân sự ⛔ KHÔNG AI duyệt, lên màn hình Ban
     *       giám đốc.
     *   <li><b>Kỹ thuật</b> — {@code Setting.effectiveValue()} rơi về {@code default_value} khi giá
     *       trị rỗng, và {@code changeValue()} quy chuỗi rỗng về {@code null} (cố ý: *"xoá ô"* và
     *       *"khôi phục mặc định"* phải cùng nghĩa). ⇒ Một tham số có mặc định khác rỗng thì trạng
     *       thái *"chưa cấu hình"* <b>⛔ KHÔNG BIỂU DIỄN ĐƯỢC</b> — luật 3 (*"rỗng" khác "chưa
     *       đặt"*), lần thứ tư trong dự án.
     * </ol>
     *
     * <p>⇒ Mặc định nay là <b>rỗng</b>, và bài này khẳng định <b>cả hai chiều</b>: chưa cấu hình ⇒
     * {@code null}; cấu hình rồi ⇒ một con số. Thiếu chiều thứ hai thì một hệ luôn trả {@code null}
     * cũng xanh (luật 9).
     */
    @Test
    @DisplayName("⭐⭐ % hoàn thiện: CHƯA cấu hình ⇒ null (⛔ không phải 0%) · cấu hình rồi ⇒ có số")
    void phanTramHoanThienPhanBietChuaCauHinhVoiKhongDat() {
        UUID hoSo = taoHoSo("TL-PCT");

        String macDinh =
                phienHttp.get(quanTri, duongTaiLieu(hoSo) + "/tinh-trang").getBody();
        assertThat(macDinh)
                .as("⛔⛔ Mặc định là RỖNG ⇒ `phanTram` phải NULL. Quy tắc 16: số 0 là một câu khẳng "
                        + "định, và ở đây nó khẳng định 'hồ sơ này thiếu tài liệu' đúng vào lúc ⛔ chưa "
                        + "ai định nghĩa thế nào là đủ")
                .contains("\"daCauHinh\":false");
        assertThat(macDinh)
                .as("⚠ Jackson của dự án BỎ HẲN trường null khỏi thân JSON — nên `phanTram` tới giao "
                        + "diện dưới dạng một trường VẮNG MẶT, ⛔ không phải `null`. Đó là lý do có cờ "
                        + "`daCauHinh`: một khác biệt quan trọng ⛔ không được phụ thuộc vào cấu hình "
                        + "bộ tuần tự hoá")
                .doesNotContain("\"phanTram\"");

        String cu = jdbc.queryForObject(
                "SELECT coalesce(setting_value, '') FROM settings WHERE setting_key = 'hr.document.required-folders'",
                String.class);
        try {
            datThamSo("hr.document.required-folders", "GIAY_TO_TUY_THAN,HOP_DONG");

            String chuaCoTep =
                    phienHttp.get(quanTri, duongTaiLieu(hoSo) + "/tinh-trang").getBody();
            assertThat(chuaCoTep).contains("\"daCauHinh\":true");
            assertThat(chuoi(chuaCoTep, "phanTram"))
                    .as("⭐ Chiều thứ hai — thiếu nó thì một hệ LUÔN trả null cũng xanh (luật 9)")
                    .isEqualTo("0");
            assertThat(chuaCoTep).contains("\"conThieu\":[\"GIAY_TO_TUY_THAN\",\"HOP_DONG\"]");

            // Nạp một tệp vào MỘT trong hai thư mục bắt buộc ⇒ 50%.
            assertThat(tai(hoSo, HoSoThuMuc.HOP_DONG, "hd.pdf").getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String mot =
                    phienHttp.get(quanTri, duongTaiLieu(hoSo) + "/tinh-trang").getBody();
            assertThat(chuoi(mot, "phanTram"))
                    .as("⛔ 1/2 thư mục bắt buộc đã có tệp ⇒ 50%%. Một hằng số ghi cứng sẽ ⛔ không đổi ở đây")
                    .isEqualTo("50");
            assertThat(mot).contains("\"conThieu\":[\"GIAY_TO_TUY_THAN\"]");
        } finally {
            datThamSo("hr.document.required-folders", cu);
        }
    }

    // =========================================================================
    // M4.9 — Cảnh báo hết hạn: VẾ ĐỌC của hai khoá mồ côi 28 ngày
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Ngưỡng cảnh báo ĐỌC TỪ settings — đổi giá trị thì ranh giới DỊCH theo")
    void nguongCanhBaoDocTuSettings() {
        UUID hoSo = taoHoSo("CB-001");
        LocalDate homNay = LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN);

        // Chứng chỉ hết hiệu lực sau 95 ngày — NGOÀI ngưỡng mặc định 90.
        taoChungChi(hoSo, "Chứng chỉ an toàn lao động", homNay.plusDays(95));

        String mac = phienHttp.get(quanTri, "/api/v1/hr/canh-bao-het-han").getBody();
        assertThat(chuoi(mac, "nguongNgayChungChi"))
                .as("⛔ Ngưỡng trả ra API để màn hình ⛔ không phải ghi cứng lại con số ấy (luật 14)")
                .isEqualTo("90");
        assertThat(mac)
                .as("95 ngày nằm NGOÀI ngưỡng 90 ⇒ ⛔ chưa được cảnh báo")
                .doesNotContain("Chứng chỉ an toàn lao động");

        String cu = jdbc.queryForObject(
                "SELECT setting_value FROM settings WHERE setting_key = 'hr.certificate.expiry-warning-days'",
                String.class);
        try {
            datThamSo("hr.certificate.expiry-warning-days", "120");
            String moi = phienHttp.get(quanTri, "/api/v1/hr/canh-bao-het-han").getBody();
            assertThat(moi)
                    .as("⛔⛔ ĐÂY là phép đo chứng minh khoá `hr.certificate.expiry-warning-days` THẬT SỰ "
                            + "có người đọc. Nó nằm trong settings từ 13/08 với 0 nơi đọc suốt 28 ngày: "
                            + "người vận hành sửa ô ấy và ⛔ không có gì đổi (luật 15)")
                    .contains("Chứng chỉ an toàn lao động");
        } finally {
            datThamSo("hr.certificate.expiry-warning-days", cu);
        }
    }

    @Test
    @DisplayName("⛔⛔ 'Sắp hết hạn' BAO GỒM 'đã hết hạn' — và soNgayCon ÂM để giao diện tô đỏ")
    void daHetHanVanNamTrongDanhSach() {
        UUID hoSo = taoHoSo("CB-QUA");
        LocalDate homNay = LocalDate.now(com.songnhue.core.common.util.DateTimeUtils.ZONE_VN);
        taoChungChi(hoSo, "Chứng chỉ ĐÃ QUÁ HẠN", homNay.minusDays(40));

        String body = phienHttp.get(quanTri, "/api/v1/hr/canh-bao-het-han").getBody();
        assertThat(body)
                .as("⛔ Lấy khoảng [hôm nay, hôm nay+N] là để rơi mất đúng những mục NGUY HIỂM NHẤT, "
                        + "và triệu chứng là một danh sách ngắn dần theo thời gian — trông y hệt "
                        + "'mọi thứ đang ổn'")
                .contains("Chứng chỉ ĐÃ QUÁ HẠN");
        assertThat(body)
                .as("⛔ soNgayCon phải ÂM. Kẹp về 0 là mất đúng thông tin 'quá hạn bao lâu rồi', thứ "
                        + "quyết định việc nào làm trước")
                .contains("\"soNgayCon\":-40");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private String duongLyLich(UUID hoSo) {
        return DUONG_HO_SO + "/" + hoSo + "/ly-lich";
    }

    private String duongTimeline(UUID hoSo) {
        return DUONG_HO_SO + "/" + hoSo + "/timeline";
    }

    private String duongTaiLieu(UUID hoSo) {
        return DUONG_HO_SO + "/" + hoSo + "/tai-lieu";
    }

    private ResponseEntity<String> tai(UUID hoSo, HoSoThuMuc thuMuc, String ten) {
        return tai(hoSo, thuMuc, ten, PDF);
    }

    private ResponseEntity<String> tai(UUID hoSo, HoSoThuMuc thuMuc, String ten, byte[] noiDung) {
        return phienHttp.dangTep(quanTri, duongTaiLieu(hoSo) + "?thuMuc=" + thuMuc.name(), noiDung, ten);
    }

    /** PDF hợp lệ được đệm tới đúng {@code soByte} — magic bytes vẫn ở đầu tệp. */
    private static byte[] tepCoKichThuoc(int soByte) {
        byte[] ra = new byte[soByte];
        System.arraycopy(PDF, 0, ra, 0, PDF.length);
        java.util.Arrays.fill(ra, PDF.length, soByte, (byte) ' ');
        return ra;
    }

    private UUID taoHoSo(String hau) {
        UUID chucVu = taoChucVu(hau);
        String than =
                """
                {"code":"%s","fullName":"Lê Thị Hồng","dateOfBirth":"1990-02-11","gender":"NU",\
                "orgUnitId":"%s","positionId":"%s","hiredAt":"2016-03-01",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, donViGoc, chucVu);
        ResponseEntity<String> tao = phienHttp.goi(quanTri, HttpMethod.POST, DUONG_HO_SO, than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoChucVu(String hau) {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                DUONG_CHUC_VU,
                """
                {"code":"%s","name":"Chức vụ kiểm thử %s","positionGroup":"WS-53","sortOrder":10,"active":true}"""
                        .formatted(TIEN_TO + hau, hau));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoMucLyLichDayDu(UUID hoSo) {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                duongLyLich(hoSo),
                """
                {"kind":"BANG_CAP","name":"Kỹ sư Thuỷ lợi","grade":"Giỏi","major":"Kỹ thuật tài nguyên nước",\
                "institution":"Trường Đại học Thuỷ lợi","certificateNo":"DHTL-2013-04517",\
                "issuedOn":"2013-06-20","expiresOn":"2030-06-20","note":"Bản gốc lưu tại phòng TCHC"}""");
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private void taoChungChi(UUID hoSo, String ten, LocalDate hetHan) {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                duongLyLich(hoSo),
                """
                {"kind":"CHUNG_CHI","name":"%s","issuedOn":"2020-01-01","expiresOn":"%s"}"""
                        .formatted(ten, hetHan));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    private void taoSuKien(UUID hoSo, String loai, String hieuLuc, String ngayKy, String tieuDe) {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                duongTimeline(hoSo),
                """
                {"eventType":"%s","effectiveOn":"%s","decisionNo":"QĐ-01","decisionDate":"%s","title":"%s"}"""
                        .formatted(loai, hieuLuc, ngayKy, tieuDe));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
    }

    private static String thanLyLich(String kind) {
        return """
                {"kind":"%s","name":"Chứng chỉ đổi tên"}""".formatted(kind);
    }

    /** Bóc đúng object của {@code muc} trong mảng rồi dựng lại thân PUT nguyên văn. */
    private static String thanPutTuView(String mangJson, UUID muc) {
        int i = mangJson.indexOf(muc.toString());
        assertThat(i)
                .as("⛔ ⛔ không thấy mục vừa tạo trong danh sách — bài dưới sẽ vô nghĩa")
                .isNotNegative();
        int mo = mangJson.lastIndexOf('{', i);
        int dong = mangJson.indexOf('}', i);
        return mangJson.substring(mo, dong + 1);
    }

    private static String chuoi(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*(null|\"([^\"]*)\"|[^,}\\]]+)")
                .matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2) : m.group(1);
    }

    private static List<String> moiGiaTri(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*\"([^\"]*)\"")
                .matcher(json == null ? "" : json);
        List<String> ra = new java.util.ArrayList<>();
        while (m.find()) {
            ra.add(m.group(1));
        }
        return ra;
    }

    /** Đặt một tham số và <b>dọn đệm</b> — hai bước, ⛔ không tách rời được. */
    private void datThamSo(String khoa, String giaTri) {
        jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        settingService.invalidate(khoa);
    }

    private void donSachVaiTroTam() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_TAM);
    }

    /** ⚠ Xoá con TRƯỚC cha — khoá ngoại. Attachments đi trước vì chúng trỏ vào employee_id. */
    private void donDuLieuThu() {
        jdbc.update(
                "DELETE FROM attachments WHERE owner_type = 'EMPLOYEE' AND owner_id IN "
                        + "(SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM employee_qualifications WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM employee_events WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM positions WHERE code LIKE ?", TIEN_TO + "%");
    }
}
