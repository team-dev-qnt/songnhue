package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.notification.RecipientResolver;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.hydro.application.NguongAlertService;
import com.songnhue.hydro.domain.ReadingQuality;

/**
 * <b>Hai bất biến của máy cảnh báo mà ⛔ CHƯA AI ĐI QUA</b> — DOD2.15 và DOD2.16.
 *
 * <h2>Vì sao "đúng theo cấu trúc" ⛔ không đủ</h2>
 *
 * Cả hai đều <i>đúng</i> khi đọc mã. Nhưng luật 7 nói thẳng: <b>một cơ chế chưa ai đi qua thì chưa
 * biết nó đúng hay sai</b> — ArchUnit suốt Phase 0, tầng 3 phân quyền, ISR revalidate đều "đúng theo
 * cấu trúc" cho tới lượt đầu tiên có người chạy chúng.
 *
 * <h2>DOD2.15 — điểm đo chưa cấu hình ngưỡng ⛔ KHÔNG phát cảnh báo</h2>
 *
 * ⚠⚠ Đây là chỗ <b>đọc theo TÊN bài kiểm sẽ tick nhầm</b>. Nửa <i>nhãn/danh sách</i> đã có thật
 * ({@code AlertEngineHttpTest} + {@code GET /hyd/alert-rules/chua-cau-hinh}); nửa <b>"⛔ không phát
 * cảnh báo"</b> thì ⛔ chưa ai đo. Hai nửa nghe giống nhau và nói hai chuyện khác nhau.
 *
 * <p>19 điểm đo seed hiện ⛔ CHƯA điểm nào có ngưỡng (G9-a chưa về), nên nếu vế này sai thì <b>mọi</b>
 * số đo của <b>mọi</b> trạm sinh cảnh báo — và ⛔ không ai phát hiện cho tới lúc hộp thư nổ tung.
 *
 * <h2>DOD2.16 — hysteresis sống sót qua restart</h2>
 *
 * Hysteresis là một <b>chỉ mục CSDL</b>, ⛔ không phải trạng thái trong bộ nhớ:
 * {@code ux_alert_events_mot_cai_dang_mo ON alert_events (rule_id) WHERE status = 'DANG_XAY_RA'}.
 *
 * <p>⭐ Phép chứng minh mạnh nhất mà ⛔ không cần khởi động lại thật: chèn <b>thẳng bằng SQL</b> một
 * cảnh báo thứ hai đang mở cho cùng quy tắc, và đòi <b>Postgres</b> từ chối. Nếu ràng buộc do
 * Postgres ép thì nó sống sót qua restart <i>theo định nghĩa</i> — còn một biến trong bộ nhớ thì ⛔
 * không cách nào chặn được một lượt {@code INSERT} đi vòng qua service.
 */
class NguongBatBienTest extends IntegrationTestBase {

    private static final String MA_DIEM_DO = "DOD215-DD";
    private static final String MA_API = "F97218";
    private static final String MA_MUC = "DOD216-MUC";

    /** ⚠ Tên có tiền tố riêng để `donDep()` xoá đúng hàng của lớp này, ⛔ không đụng seed. */
    private static final String TAI_KHOAN_BDH = "dod23_bdh";

    @Autowired
    private NguongAlertService nguongAlert;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private SettingService thamSo;

    private long idDiemDo;
    private long idLoaiChiSo;

    @BeforeEach
    void chuanBi() {
        donDep();
        idDiemDo = themDiemDo();
        idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
    }

    @AfterEach
    void donSau() {
        donDep();
    }

    // ---- DOD2.15 ------------------------------------------------------------

    @Test
    @DisplayName("⭐⭐ DOD2.15 — điểm đo CHƯA cấu hình ngưỡng: một số đo cao ⛔ KHÔNG sinh cảnh báo nào")
    void chuaCauHinhNguongThiKhongPhatCanhBao() {
        assertThat(soQuyTac())
                .as("tiền đề: điểm đo này ⛔ chưa có quy tắc ngưỡng nào")
                .isZero();

        // `danhGia` là `Propagation.MANDATORY` — nó ⛔ không tự mở giao dịch, có chủ đích: nó phải
        // chạy trong CÙNG giao dịch với lượt ghi số đo. Nên bài kiểm phải tự mở một cái.
        tx.executeWithoutResult(t -> nguongAlert.danhGia(
                idDiemDo, idLoaiChiSo, Instant.now(), new BigDecimal("999.000"), ReadingQuality.HOP_LE));

        assertThat(soCanhBao())
                .as(
                        """
                        ⛔⛔ 19 điểm đo seed hiện CHƯA điểm nào có ngưỡng (G9-a chưa về). Vế này sai nghĩa \
                        là MỌI số đo của MỌI trạm sinh một cảnh báo — 28 trạm × 720 lượt/ngày — và ⛔ không \
                        ai phát hiện cho tới lúc hộp thư nổ tung.""")
                .isZero();
    }

    @Test
    @DisplayName("⚠ Vế phân biệt: CÓ ngưỡng thì số đo vượt PHẢI sinh cảnh báo (luật 9)")
    void coNguongThiVanPhatCanhBao() {
        // Thiếu vế này thì một `danhGia` hỏng theo kiểu "return ngay đầu hàm" cũng làm bài trên xanh,
        // và ta đổi một lỗ ồn ào lấy một lỗ im lặng — máy cảnh báo ⛔ không bao giờ kêu.
        taoQuyTac();

        tx.executeWithoutResult(t -> nguongAlert.danhGia(
                idDiemDo, idLoaiChiSo, Instant.now(), new BigDecimal("999.000"), ReadingQuality.HOP_LE));

        assertThat(soCanhBao()).isEqualTo(1);
    }

    @Test
    @DisplayName("Số đo NGHI NGỜ ⛔ không được đánh giá ngưỡng — quy tắc 14 của CLAUDE.md")
    void soDoNghiNgoKhongDanhGia() {
        taoQuyTac();

        tx.executeWithoutResult(t -> nguongAlert.danhGia(
                idDiemDo, idLoaiChiSo, Instant.now(), new BigDecimal("999.000"), ReadingQuality.NGHI_NGO));

        assertThat(soCanhBao())
                .as("Dữ liệu `NGHI_NGO` nằm trong bảng chính; mọi đường alert/báo cáo phải lọc `HOP_LE`")
                .isZero();
    }

    // ---- DOD2.16 ------------------------------------------------------------

    @Test
    @DisplayName("⭐⭐ DOD2.16 — hysteresis là RÀNG BUỘC CSDL: INSERT thẳng cái thứ hai bị Postgres từ chối")
    void hysteresisLaRangBuocCsdlChuKhongPhaiBienTrongBoNho() {
        long idQuyTac = taoQuyTac();
        chenCanhBaoDangMo(idQuyTac);
        assertThat(soCanhBao()).as("tiền đề: đã có ĐÚNG một cảnh báo đang mở").isEqualTo(1);

        // ⭐ Đây là phép chứng minh "sống sót qua restart" mà ⛔ không cần khởi động lại: lượt chèn
        //   này đi VÒNG QUA service, nên một biến trong bộ nhớ ⛔ không có cách nào chặn nó. Chỉ
        //   Postgres chặn được — và thứ Postgres ép thì sống sót qua restart theo ĐỊNH NGHĨA.
        assertThatThrownBy(() -> chenCanhBaoDangMo(idQuyTac))
                .as("Hysteresis nằm trong bộ nhớ thì lượt chèn này đi lọt, và một trận lũ sinh 720 cảnh báo")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(soCanhBao()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Đóng cảnh báo rồi thì cái MỚI mở được — ⛔ không phải một khoá vĩnh viễn")
    void dongRoiThiMoLaiDuoc() {
        // Vế phân biệt (luật 9): một chỉ mục `UNIQUE (rule_id)` KHÔNG có mệnh đề `WHERE` cũng làm
        // bài trên xanh, nhưng khi ấy một quy tắc chỉ cảnh báo được ĐÚNG MỘT LẦN trong đời.
        long idQuyTac = taoQuyTac();
        chenCanhBaoDangMo(idQuyTac);
        jdbc.update(
                "UPDATE alert_events SET status = 'DA_XU_LY', ended_at = started_at + interval '1 minute' "
                        + "WHERE rule_id = ?",
                idQuyTac);

        chenCanhBaoDangMo(idQuyTac);

        // ⚠ Bản đầu của bài này khẳng định `soCanhBao() == 2` và ĐỎ với "expected 2 but was 1" —
        //   lỗi ở BÀI KIỂM, ⛔ không ở mã: `soCanhBao()` chỉ đếm cái ĐANG MỞ, mà cái thứ nhất vừa
        //   được đóng. Hai con số nói hai chuyện, và chỉ một trong hai là thứ đang kiểm.
        assertThat(soCanhBaoMoiTrangThai())
                .as("Đóng rồi mở lại phải để lại HAI bản ghi — lịch sử cảnh báo ⛔ không được mất")
                .isEqualTo(2);
        assertThat(soCanhBao())
                .as("…và đúng MỘT cái đang mở: chỉ mục vẫn giữ hysteresis sau lượt mở lại")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ Chỉ mục ấy CÓ THẬT trong lược đồ, và có ĐÚNG mệnh đề lọc")
    void chiMucCoThatVaCoMenhDeLoc() {
        // conventions.md §1.5 — hai bài trên đo HÀNH VI; bài này đo chính CƠ CHẾ, để lượt đỏ chỉ
        // thẳng vào chỗ phải sửa thay vì bắt người đọc suy ngược từ một `DataIntegrityViolation`.
        String dinhNghia = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_alert_events_mot_cai_dang_mo'", String.class);

        assertThat(dinhNghia)
                .as("Chỉ mục hysteresis ⛔ không còn — DOD2.16 mất chốt chặn duy nhất của nó")
                .isNotNull()
                .contains("UNIQUE")
                .contains("rule_id")
                .contains("DANG_XAY_RA");
    }

    // -------------------------------------------------------------------------

    private int soCanhBao() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM alert_events WHERE station_id = ? AND status = 'DANG_XAY_RA'",
                Integer.class,
                idDiemDo);
        return n == null ? 0 : n;
    }

    /** Mọi trạng thái — dùng cho vế "lịch sử ⛔ không được mất", khác hẳn {@link #soCanhBao()}. */
    private int soCanhBaoMoiTrangThai() {
        Integer n =
                jdbc.queryForObject("SELECT count(*) FROM alert_events WHERE station_id = ?", Integer.class, idDiemDo);
        return n == null ? 0 : n;
    }

    private int soQuyTac() {
        Integer n =
                jdbc.queryForObject("SELECT count(*) FROM alert_rules WHERE station_id = ?", Integer.class, idDiemDo);
        return n == null ? 0 : n;
    }

    // ---- DOD2.3 — vế CẢNH BÁO của điểm MN_SONG ------------------------------

    /**
     * ⭐⭐ <b>DOD2.3, vế thứ ba.</b> Một trạm thuỷ văn sông ⛔ <b>không liên kết công trình nào</b>
     * vẫn phát được cảnh báo, và cảnh báo ấy tới được <b>nhóm Ban điều hành</b>.
     *
     * <h2>⛔ Nhánh này ⛔ chưa ai đi qua, và đó chính là lý do bài kiểm tồn tại (luật 7)</h2>
     *
     * <p>{@code AlertNotifier.ghiNhatKyThieu()} có một nhánh riêng cho {@code MN_SONG} — <i>"đúng
     * thiết kế, chỉ nhóm cố định nhận"</i> — nhưng đo 08/09/2026: <b>0 bài kiểm nào ở module
     * {@code app} đi qua {@code RecipientResolver} trên đường cảnh báo thuỷ văn</b>. Một cơ chế
     * chưa ai đi qua thì chưa biết nó đúng hay sai.
     *
     * <p>⚠ Và nó là nhánh <b>dễ hỏng im lặng nhất</b> của cả máy cảnh báo: 4/19 điểm đo là
     * {@code MN_SONG}. Nếu {@code RecipientResolver} rơi về <b>tập rỗng</b> thay vì nhóm cố định
     * khi danh sách đơn vị trống, thì cảnh báo lũ của bốn trạm thuỷ văn sông <b>⛔ không tới ai
     * cả</b> — và ⛔ không có gì để nhìn thấy: bảng {@code alert_events} vẫn có hàng, màn hình
     * cảnh báo vẫn hiện, chỉ hộp thư là trống.
     *
     * <h2>⚠ Ba khẳng định, và vì sao cần cả ba (luật 9)</h2>
     *
     * <ol>
     *   <li><b>Có sự kiện cảnh báo</b> — nếu ⛔ không, hai vế sau vô nghĩa;
     *   <li><b>Có bản ghi thông báo</b> — sự kiện ⛔ không tự tới ai;
     *   <li><b>Có người nhận thật</b> — đây là vế chịu lực. Một thông báo ⛔ không người nhận đi
     *       qua mọi khẳng định về "đã tạo thông báo" mà ⛔ không tới hộp thư nào.
     * </ol>
     */
    @Test
    @DisplayName("⭐⭐ DOD2.3 — trạm MN_SONG ⛔ không liên kết công trình VẪN phát cảnh báo tới Ban điều hành")
    void mnSongKhongLienKetVanPhatCanhBaoToiBanDieuHanh() {
        // Tiền đề đo được, ⛔ không giả định: đúng vai trò MN_SONG và ⛔ KHÔNG liên kết công trình.
        assertThat(jdbc.queryForObject("SELECT position_role FROM stations WHERE id = ?", String.class, idDiemDo))
                .isEqualTo("MN_SONG");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM station_constructions WHERE station_id = ?", Integer.class, idDiemDo))
                .as("⚠ tiền đề của DOD2.3: điểm đo này ⛔ KHÔNG liên kết công trình nào")
                .isZero();

        long idNguoiNhan = themNguoiVaoBanDieuHanh();
        taoQuyTac();

        tx.executeWithoutResult(t -> nguongAlert.danhGia(
                idDiemDo, idLoaiChiSo, Instant.now(), new BigDecimal("9.999"), ReadingQuality.HOP_LE));

        assertThat(soCanhBao())
                .as("(1) trạm ⛔ không liên kết công trình vẫn phải sinh cảnh báo")
                .isEqualTo(1);

        Integer soThongBao = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_type = ?",
                Integer.class,
                NguongAlertService.SU_KIEN_VUOT_NGUONG);
        assertThat(soThongBao).as("(2) cảnh báo phải sinh một thông báo").isEqualTo(1);

        assertThat(jdbc.queryForList(
                        """
                        SELECT r.user_id FROM notification_recipients r
                          JOIN notifications n ON n.id = r.notification_id
                         WHERE n.event_type = ?
                        """,
                        Long.class,
                        NguongAlertService.SU_KIEN_VUOT_NGUONG))
                .as("(3) ⛔⛔ VẾ CHỊU LỰC — danh sách đơn vị RỖNG phải rơi về nhóm Ban điều hành, "
                        + "⛔ KHÔNG rơi về tập rỗng. Tập rỗng nghĩa là cảnh báo lũ của 4 trạm thuỷ văn "
                        + "sông ⛔ không tới ai, mà bảng alert_events vẫn có hàng và màn hình vẫn hiện.")
                .contains(idNguoiNhan);
    }

    /**
     * Đưa một tài khoản vào nhóm "Ban điều hành" — khoá {@code settings} dạng mảng JSON id.
     *
     * <p>⚠ Ghi thẳng CSDL rồi <b>xoá đệm</b>: {@code SettingService} có Caffeine TTL vài phút, nên
     * thiếu lượt {@code invalidate} thì {@code RecipientResolver} đọc danh sách CŨ và bài kiểm đỏ
     * ở một dòng ⛔ không liên quan gì tới thứ nó đang kiểm.
     */
    private long themNguoiVaoBanDieuHanh() {
        Long idDonVi = jdbc.queryForObject(
                "SELECT id FROM org_units WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idDonVi).as("⚠ vế chống tập rỗng: phải có đơn vị seed").isNotNull();

        // ⛔⛔ Khoá nhóm giữ mảng `public_id` (UUID), ⛔ KHÔNG phải `id` (BIGINT). Bản đầu của
        //    fixture này nhét `id` vào và bài kiểm ĐỎ ở đúng vế chịu lực — trong khi mã sản xuất
        //    hoàn toàn đúng: `executiveBoard()` bắt được `IllegalArgumentException`, ghi log lỗi
        //    kèm nguyên văn giá trị, và trả về danh sách rỗng.
        //
        // ⚠ Đây là luật 29 gặp lại trong cùng một ngày: một bài kiểm sai theo đúng cách mà thứ nó
        //   kiểm CÓ THỂ sai — cả hai đều cho ra "⛔ không người nhận nào". Thứ phân biệt được hai
        //   trạng thái ấy ⛔ không phải khẳng định, mà là lượt ĐỌC mã của `executiveBoard()`.
        Object[] nguoi = jdbc.queryForObject(
                """
                INSERT INTO users (public_id, username, full_name, email, password_hash, status,
                                   org_unit_id, must_change_password, created_at)
                VALUES (gen_random_uuid(), ?, 'Cán bộ Ban điều hành DOD2.3', 'dod23@example.invalid',
                        'x', 'ACTIVE', ?, FALSE, now())
                RETURNING id, public_id
                """,
                (rs, n) -> new Object[] {rs.getLong("id"), rs.getObject("public_id")},
                TAI_KHOAN_BDH,
                idDonVi);
        long idNguoi = (Long) nguoi[0];

        int soHang = jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?",
                "[\"" + nguoi[1] + "\"]",
                RecipientResolver.KEY_EXECUTIVE_BOARD);
        assertThat(soHang)
                .as(
                        "khoá `%s` ⛔ không có trong bảng settings — nhóm Ban điều hành là danh mục có CRUD",
                        RecipientResolver.KEY_EXECUTIVE_BOARD)
                .isEqualTo(1);
        thamSo.invalidate(RecipientResolver.KEY_EXECUTIVE_BOARD);
        return idNguoi;
    }

    private long taoQuyTac() {
        Long idMuc = jdbc.queryForObject(
                "INSERT INTO alert_levels (code, name, color_token, severity_rank, active, created_at) "
                        + "VALUES (?, 'Mức kiểm thử DOD2.16', 'alert-level-1', 218, TRUE, now()) RETURNING id",
                Long.class,
                MA_MUC);
        return jdbc.queryForObject(
                """
                INSERT INTO alert_rules (
                    station_id, measurement_type_id, alert_level_id, condition_type,
                    threshold_value, delay_minutes, active, created_at)
                VALUES (?, ?, ?, 'GT', 2.000, 0, TRUE, now())
                RETURNING id
                """,
                Long.class,
                idDiemDo,
                idLoaiChiSo,
                idMuc);
    }

    /** ⚠ Mốc TƯỜNG MINH lùi quá khứ — {@code now()} của Postgres là mốc BẮT ĐẦU giao dịch. */
    private void chenCanhBaoDangMo(long idQuyTac) {
        Long idMuc = jdbc.queryForObject("SELECT alert_level_id FROM alert_rules WHERE id = ?", Long.class, idQuyTac);
        Instant batDau = Instant.now().minusSeconds(600);
        jdbc.update(
                """
                INSERT INTO alert_events (
                    rule_id, station_id, measurement_type_id, alert_level_id,
                    started_at, confirmed_at, status, trigger_value, peak_value, peak_at, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'DANG_XAY_RA', 3.000, 3.000, ?, '3.000 > 2.000', now())
                """,
                idQuyTac,
                idDiemDo,
                idLoaiChiSo,
                idMuc,
                java.sql.Timestamp.from(batDau),
                java.sql.Timestamp.from(batDau),
                java.sql.Timestamp.from(batDau));
    }

    private long themDiemDo() {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon)
                .as("⚠ Vế chống tập rỗng: ⛔ không có nguồn seed thì điểm đo ⛔ không tạo được")
                .isNotNull();
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử DOD2.15/2.16', ?, ?, 'MN_SONG', TRUE, now())
                RETURNING id
                """,
                Long.class,
                MA_DIEM_DO,
                MA_API,
                idNguon);
    }

    /** ⛔ Dọn theo thứ tự khoá ngoại, và dọn TRƯỚC mỗi lượt — lượt trước bị ngắt để lại rác. */
    private void donDep() {
        jdbc.update(
                "DELETE FROM alert_events WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_levels WHERE code = ?", MA_MUC);
        jdbc.update(
                "DELETE FROM hydro_readings WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update(
                "DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA_DIEM_DO);

        // ⛔ DOD2.3 — dọn theo ĐÚNG thứ tự khoá ngoại: người nhận → thông báo → tài khoản.
        //   Sai thứ tự thì lượt xoá đỏ, và bài KẾ TIẾP đỏ vì dọn dẹp chứ ⛔ không vì thứ nó kiểm.
        jdbc.update(
                "DELETE FROM notification_recipients r USING notifications n "
                        + "WHERE n.id = r.notification_id AND n.event_type = ?",
                NguongAlertService.SU_KIEN_VUOT_NGUONG);
        jdbc.update("DELETE FROM notifications WHERE event_type = ?", NguongAlertService.SU_KIEN_VUOT_NGUONG);
        jdbc.update("DELETE FROM users WHERE username = ?", TAI_KHOAN_BDH);

        // ⚠ Trả khoá nhóm Ban điều hành về ĐÚNG `default_value` — ⛔ không về một hằng ghi cứng.
        //   Cùng bài học vừa trả giá ở `ContactFormPolicyHttpTest` cùng ngày: một lượt khôi phục
        //   ghi cứng giá trị mặc định là một BẢN SAO của mặc định, và bản sao lệch trong im lặng.
        jdbc.update(
                "UPDATE settings SET setting_value = default_value WHERE setting_key = ?",
                RecipientResolver.KEY_EXECUTIVE_BOARD);
        thamSo.invalidate(RecipientResolver.KEY_EXECUTIVE_BOARD);
    }
}
