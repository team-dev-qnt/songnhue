package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;

/**
 * ⭐ Seed 19 điểm đo — DOD2.2, và bốn thứ chỉ CSDL thật mới kiểm chứng được.
 *
 * <h2>Vì sao bài kiểm này khẳng định cùng một chuyện bằng ba cách không liên quan</h2>
 *
 * <p>{@code architecture-review.md} §10.62: hai khẳng định chia sẻ một giả định thì chúng cùng đúng
 * và cùng sai. Ở đây:
 *
 * <ul>
 *   <li><b>Đếm số lượng</b> bắt trường hợp thiếu hoặc thừa dòng.
 *   <li><b>Đối chiếu toàn bộ bảng ánh xạ</b> bắt trường hợp đủ 19 dòng nhưng chép nhầm một mã — mà
 *       phép đếm không thấy gì.
 *   <li><b>Soi riêng {@code F01705}</b> vì đúng mã đó từng bị đoán sai (Cống Phủ Lý, thực tế là Vân
 *       Đình hạ lưu). Một mã đã sai một lần thì đáng có bài kiểm mang tên nó.
 * </ul>
 */
class HydroCatalogueSeedTest extends IntegrationTestBase {

    /**
     * Bảng ánh xạ G8b — chép từ {@code function-spec.md} CN-03.1, <b>không</b> chép từ migration.
     *
     * <p>⚠ Chép từ migration thì bài kiểm chỉ chứng minh migration bằng chính nó. Nguồn sự thật là
     * tài liệu Công ty cấp, nên bảng đối chiếu phải đi từ tài liệu.
     */
    private static final Map<String, String[]> ANH_XA_G8B = Map.ofEntries(
            Map.entry("F01771", new String[] {"DO-LMAC-TL", "THUONG_LUU"}),
            Map.entry("F01672", new String[] {"DO-LMAC-HL", "HA_LUU"}),
            Map.entry("F01965", new String[] {"DO-LMAC2-HL", "HA_LUU"}),
            Map.entry("F01794", new String[] {"DO-HDONG-TL", "THUONG_LUU"}),
            Map.entry("F01905", new String[] {"DO-DQUAN-TL", "THUONG_LUU"}),
            Map.entry("F01527", new String[] {"DO-DQUAN-HL", "HA_LUU"}),
            Map.entry("F02031", new String[] {"DO-NTUU-TL", "THUONG_LUU"}),
            Map.entry("F02030", new String[] {"DO-NTUU-HL", "HA_LUU"}),
            // ⚠ Sửa 09/09/2026 theo bản chụp G8 của Công ty (V202609091073). Bản seed gốc ghi
            //   `DO-LCO-TL` / `THUONG_LUU`; bản chụp ghi Hạ lưu, và 18/19 dòng còn lại khớp tuyệt
            //   đối giữa hai nguồn. QuanTran chốt lấy theo bản chụp.
            Map.entry("F01519", new String[] {"DO-LCO-HL", "HA_LUU"}),
            Map.entry("F01657", new String[] {"DO-VDINH-TL", "THUONG_LUU"}),
            Map.entry("F01705", new String[] {"DO-VDINH-HL", "HA_LUU"}),
            Map.entry("F02039", new String[] {"DO-HMY-HL", "HA_LUU"}),
            Map.entry("F01820", new String[] {"DO-CTTC-YNGHIA-TL", "THUONG_LUU"}),
            Map.entry("F01652", new String[] {"DO-CTTC-YNGHIA-HL", "HA_LUU"}),
            Map.entry("F01707", new String[] {"DO-TB-YNGHIA-BH", "BE_HUT"}),
            Map.entry("F01732", new String[] {"DO-TB-HVAN-MN", "MN_SONG"}),
            Map.entry("F01559", new String[] {"DO-TV-HNOI-MN", "MN_SONG"}),
            Map.entry("F01812", new String[] {"DO-ANCANH-MN", "MN_SONG"}),
            Map.entry("F01532", new String[] {"DO-TV-BATHA-MN", "MN_SONG"}));

    private static final String MIGRATION = "db/migration/hyd/V202608311049__hyd_danh_muc_diem_do.sql";
    private static final String MIGRATION_G8 = "db/migration/hyd/V202609091073__hyd_g8_tuyen_song_ly_trinh.sql";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("⭐ Đúng 19 điểm đo, 19 mã API duy nhất — DOD2.2")
    void dungMuoiChinDiemDo() {
        Integer soDiem = jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Integer.class);
        Integer soMa = jdbc.queryForObject(
                "SELECT count(DISTINCT api_code) FROM stations WHERE deleted_at IS NULL", Integer.class);

        assertThat(soDiem).as("bảng G8b có đúng 19 mã, không thừa không thiếu").isEqualTo(19);
        assertThat(soMa)
                .as("19 dòng mà chỉ %s mã duy nhất nghĩa là có mã bị chép trùng", soMa)
                .isEqualTo(19);
    }

    @Test
    @DisplayName("⭐ Toàn bộ 19 dòng khớp bảng ánh xạ G8b — mã nội bộ và vai trò")
    void khopBangAnhXaG8b() {
        for (Map.Entry<String, String[]> mong : ANH_XA_G8B.entrySet()) {
            List<Map<String, Object>> dong = jdbc.queryForList(
                    "SELECT code, position_role FROM stations WHERE api_code = ? AND deleted_at IS NULL",
                    mong.getKey());

            assertThat(dong)
                    .as("mã API %s phải có đúng một điểm đo", mong.getKey())
                    .hasSize(1);
            assertThat(dong.get(0).get("code"))
                    .as("mã nội bộ của %s", mong.getKey())
                    .isEqualTo(mong.getValue()[0]);
            assertThat(dong.get(0).get("position_role"))
                    .as("vai trò của %s", mong.getKey())
                    .isEqualTo(mong.getValue()[1]);
        }
    }

    /**
     * ⚠ Mã đã từng bị đoán sai một lần.
     *
     * <p>Bản suy đoán từ biểu tổng hợp gán {@code F01705} cho <i>Cống Phủ Lý</i>; bảng Công ty cấp
     * cho thấy đó là <b>Vân Đình hạ lưu</b>. Sai một mã ánh xạ không có triệu chứng nào: biểu đồ vẫn
     * vẽ, số vẫn hợp lý, chỉ là của nhầm trạm.
     */
    @Test
    @DisplayName("⚠ F01705 là Vân Đình HẠ LƯU, không phải Cống Phủ Lý")
    void f01705LaVanDinhHaLuu() {
        Map<String, Object> dong = jdbc.queryForMap(
                "SELECT name, position_role FROM stations WHERE api_code = 'F01705' AND deleted_at IS NULL");

        assertThat((String) dong.get("name")).startsWith("Vân Đình");
        assertThat(dong.get("position_role")).isEqualTo("HA_LUU");
        assertThat((String) dong.get("name")).doesNotContain("Phủ Lý");
    }

    /**
     * ⭐ Bản chụp G8 ngày 09/09/2026 — tuyến sông và lý trình, chép từ <b>bản chụp</b>.
     *
     * <p>⚠ Chép từ {@code V202609091073} thì bài kiểm chỉ chứng minh migration bằng chính nó. Nguồn
     * là bảng đối chiếu Công ty; bản sao đọc được của nó ở {@code business-open-questions.md} §G8.
     *
     * <p>{@code null} nghĩa là bản chụp ghi <i>"Chưa rõ"</i> — và đó là một khẳng định, ⛔ không phải
     * chỗ trống chờ ai đó điền cho đẹp.
     */
    private static final Map<String, String[]> G8_VI_TRI = Map.ofEntries(
            Map.entry("F01771", new String[] {"Sông Nhuệ", "K0+390"}),
            Map.entry("F01672", new String[] {"Sông Nhuệ", null}),
            Map.entry("F01794", new String[] {"Sông Nhuệ", "K18+100"}),
            Map.entry("F01905", new String[] {"Sông Nhuệ", "K43+750"}),
            Map.entry("F01527", new String[] {"Sông Nhuệ", "K43+750"}),
            Map.entry("F02031", new String[] {"Sông Nhuệ", "K63+405"}),
            Map.entry("F02030", new String[] {"Sông Nhuệ", "K63+405"}),
            Map.entry("F01519", new String[] {"Sông Nhuệ", "K72+506"}),
            // ⚠ Lý trình bản chụp ghi "(K72+000 – sông Đáy)" ⇒ NULL, xem V202609091073.
            Map.entry("F01657", new String[] {"Sông Vân Đình", null}),
            Map.entry("F02039", new String[] {"Sông Vân Đình", "K1+460"}),
            Map.entry("F01705", new String[] {"Sông Đáy", "K72+000"}),
            Map.entry("F01532", new String[] {"Sông Đáy", "K46+500"}),
            Map.entry("F01707", new String[] {"Sông La Khê", null}),
            // 6 mã bản chụp ghi "Chưa rõ" ở CẢ HAI cột.
            Map.entry("F01732", new String[] {null, null}),
            Map.entry("F01559", new String[] {null, null}),
            Map.entry("F01812", new String[] {null, null}),
            Map.entry("F01652", new String[] {null, null}),
            Map.entry("F01820", new String[] {null, null}),
            Map.entry("F01965", new String[] {null, null}));

    /**
     * ⛔⛔ Toạ độ vẫn phải RỖNG cả 19 dòng — phần G8 còn thiếu.
     *
     * <p>Bản chụp 09/09 đóng tuyến sông và lý trình, ⛔ <b>không</b> có cột toạ độ. Bài kiểm này canh
     * đúng điều dễ bị "sửa cho đẹp": ai đó thấy bản đồ trống rồi điền toạ độ phỏng đoán. Một điểm sai
     * trên bản đồ tệ hơn hẳn một bản đồ trống — bản đồ trống thì còn nằm trong danh sách nhắc việc.
     *
     * <p>⚠ Trước 09/09 bài kiểm này khẳng định <i>cả ba</i> nhóm cột đều NULL. Nới nó xuống còn toạ
     * độ mà ⛔ không thay gì vào chỗ hai cột kia là tự tay tháo một bộ canh — nên phần tuyến sông và
     * lý trình chuyển sang {@link #khopBanChupG8} với khẳng định <b>chặt hơn</b>: ghim từng ô, ⛔
     * không chỉ "khác NULL".
     */
    @Test
    @DisplayName("⛔ Toạ độ vẫn NULL cả 19 dòng — bản chụp G8 KHÔNG có cột toạ độ")
    void khongBiaToaDo() {
        Integer coToaDo = jdbc.queryForObject(
                "SELECT count(*) FROM stations WHERE deleted_at IS NULL AND ("
                        + "latitude IS NOT NULL OR longitude IS NOT NULL OR geom IS NOT NULL)",
                Integer.class);

        assertThat(coToaDo)
                .as("G8 chưa cấp toạ độ — điền phỏng đoán là sinh điểm sai trên bản đồ điều hành")
                .isZero();
    }

    /**
     * ⭐ Từng ô tuyến sông / lý trình khớp bản chụp — <b>gồm cả 6 ô "Chưa rõ"</b>.
     *
     * <p>Ghim cả hai chiều là chủ ý. Một khẳng định kiểu <i>"13 dòng có tuyến sông"</i> vẫn xanh khi
     * ai đó xoá tuyến của F01771 rồi điền tuyến cho F01732 — đúng số lượng, sai dòng.
     */
    @Test
    @DisplayName("⭐ 19/19 ô tuyến sông + lý trình khớp bản chụp G8 (09/09), kể cả ô 'Chưa rõ'")
    void khopBanChupG8() {
        for (Map.Entry<String, String[]> mong : G8_VI_TRI.entrySet()) {
            Map<String, Object> dong = jdbc.queryForMap(
                    "SELECT river_name, chainage FROM stations WHERE api_code = ? AND deleted_at IS NULL",
                    mong.getKey());

            assertThat(dong.get("river_name"))
                    .as("tuyến sông của %s", mong.getKey())
                    .isEqualTo(mong.getValue()[0]);
            assertThat(dong.get("chainage"))
                    .as("lý trình của %s", mong.getKey())
                    .isEqualTo(mong.getValue()[1]);
        }
    }

    /**
     * ⚠ {@code chainage_m} là cột GENERATED, và nó là thứ {@code ix_stations_river} sắp theo.
     *
     * <p>Biểu thức sinh hỏng thì {@code chainage} vẫn hiện đúng trên mọi màn hình — chỉ <b>thứ tự</b>
     * các điểm dọc tuyến sai, ở BC-11 và ở lớp GIS. Quá xa chỗ sai để ai đó nối lại được, nên phải
     * đo thẳng con số.
     */
    @Test
    @DisplayName("⚠ chainage_m tính đúng từ lý trình — cột GENERATED mà không màn hình nào hiện")
    void lyTrinhSinhRaSoMet() {
        Map<String, Integer> mong = Map.of(
                "F01771", 390, // K0+390 — km bằng 0, bẫy nhân 1000
                "F02039", 1460, // K1+460
                "F01519", 72506, // K72+506 — lớn nhất trên sông Nhuệ
                "F01532", 46500); // K46+500

        for (Map.Entry<String, Integer> e : mong.entrySet()) {
            Integer m = jdbc.queryForObject(
                    "SELECT chainage_m FROM stations WHERE api_code = ? AND deleted_at IS NULL",
                    Integer.class,
                    e.getKey());
            assertThat(m).as("chainage_m của %s", e.getKey()).isEqualTo(e.getValue());
        }

        Integer coM = jdbc.queryForObject(
                "SELECT count(*) FROM stations WHERE deleted_at IS NULL AND chainage_m IS NOT NULL", Integer.class);
        assertThat(coM)
                .as("10 lý trình đọc được ⇒ đúng 10 giá trị chainage_m; lệch nghĩa là biểu thức "
                        + "sinh từ chối im lặng một dạng lý trình nào đó")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Cả 19 điểm đo đều gắn loại chỉ số Mực nước, và KHÔNG gắn Lượng mưa (G3-a)")
    void moiDiemDoDeuDoMucNuoc() {
        Integer mucNuoc = jdbc.queryForObject(
                "SELECT count(*) FROM station_measurement_types smt "
                        + "JOIN measurement_types mt ON mt.id = smt.measurement_type_id WHERE mt.code = 'MUC_NUOC'",
                Integer.class);
        Integer luongMua = jdbc.queryForObject(
                "SELECT count(*) FROM station_measurement_types smt "
                        + "JOIN measurement_types mt ON mt.id = smt.measurement_type_id WHERE mt.code = 'LUONG_MUA'",
                Integer.class);

        assertThat(mucNuoc).isEqualTo(19);
        assertThat(luongMua)
                .as("nguồn không trả lượng mưa (G3-a) — gắn sẵn thì biểu sinh 19 ô trống vĩnh viễn "
                        + "và không ai phân biệt được 'chưa có nguồn' với 'trạm hỏng'")
                .isZero();
    }

    @Test
    @DisplayName("⛔ Loại chỉ số Lượng mưa VẪN còn trong danh mục dù chưa có nguồn (G3-a)")
    void giuLoaiLuongMuaDuChuaCoNguon() {
        Integer soLoai =
                jdbc.queryForObject("SELECT count(*) FROM measurement_types WHERE deleted_at IS NULL", Integer.class);
        Integer coLuongMua = jdbc.queryForObject(
                "SELECT count(*) FROM measurement_types WHERE code = 'LUONG_MUA' AND deleted_at IS NULL",
                Integer.class);

        assertThat(soLoai).isEqualTo(3);
        assertThat(coLuongMua)
                .as("xoá loại này đi thì cột 'lượng mưa' của biểu §5.2 không còn chỗ nào để nhập tay")
                .isEqualTo(1);
    }

    /**
     * ⛔ Nhánh bắt buộc: điểm đo {@code MN_SONG} không liên kết công trình nào <b>vẫn hợp lệ</b>.
     *
     * <p>4/19 điểm là trạm thuỷ văn tham chiếu (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân). Một
     * inner join hay một {@code NOT NULL} đặt sai chỗ sẽ làm rớt đúng bốn điểm này khỏi mọi màn
     * hình, và triệu chứng là "bản đồ thiếu vài chấm" chứ không phải một lỗi.
     */
    @Test
    @DisplayName("⛔ 4 điểm MN_SONG không có liên kết công trình nào — và vẫn đọc được")
    void diemMnSongKhongLienKetVanDocDuoc() {
        Integer soMnSong = jdbc.queryForObject(
                "SELECT count(*) FROM stations WHERE position_role = 'MN_SONG' AND deleted_at IS NULL", Integer.class);
        // ⛔⛔ ĐỔI HÌNH DẠNG ở WS-46. Bản cũ đếm `count(*) FROM station_constructions` và đòi bằng
        //    0 — nhưng con số ấy mô tả một THIẾU SÓT (`constructions` rỗng, 0 hàng trong toàn chuỗi
        //    migration), ⛔ không phải một bất biến. V202609091075 dựng 11 hồ sơ từ chính dữ liệu G8
        //    và bài kiểm cũ đỏ ngay — đúng việc của nó.
        //
        // ⭐ Bất biến THẬT nằm bên dưới, và nó chặt hơn hẳn bản cũ: ⛔ KHÔNG điểm MN_SONG nào được
        //    nối vào công trình (`ConstructionStatusPort`: *"MN_SONG ⛔ không thuộc công trình nào
        //    theo thiết kế"*). Bản cũ xanh cả khi bảng liên kết rỗng vì lý do SAI — nó ⛔ không phân
        //    biệt được "chưa ai nối" với "đã nối đúng thiết kế" (luật 9).
        Integer soMnSongBiNoi = jdbc.queryForObject(
                """
                SELECT count(*) FROM station_constructions sc JOIN stations s ON s.id = sc.station_id
                 WHERE sc.deleted_at IS NULL AND s.position_role = 'MN_SONG'
                """,
                Integer.class);

        assertThat(soMnSong).isEqualTo(4);
        assertThat(soMnSongBiNoi)
                .as("⛔ Điểm MN_SONG là trạm quan trắc tham chiếu — nối nó vào một công trình là ĐẢO "
                        + "một quyết định thiết kế đã ghi, và đảo trong im lặng")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM station_constructions WHERE deleted_at IS NULL", Integer.class))
                .as("⛔ CHỐNG TẬP RỖNG (luật 7): khẳng định trên xanh trọn vẹn khi bảng liên kết RỖNG. "
                        + "Vế này đòi danh mục công trình đã được dựng — thiếu nó thì bài kiểm ⛔ không "
                        + "khẳng định gì về thiết kế, nó chỉ đang mô tả một bảng trống")
                .isEqualTo(15);

        // Truy vấn kiểu LEFT JOIN vẫn phải trả đủ 19; đổi sang INNER JOIN là rơi về 0.
        Integer docDuoc = jdbc.queryForObject(
                "SELECT count(*) FROM stations s LEFT JOIN station_constructions sc "
                        + "ON sc.station_id = s.id AND sc.deleted_at IS NULL WHERE s.deleted_at IS NULL",
                Integer.class);
        assertThat(docDuoc)
                .as("⛔ inner join ở đây làm rớt cả 19 điểm; bài kiểm phải phân biệt được hai trạng thái")
                .isEqualTo(19);
    }

    @Test
    @DisplayName("Cả 19 điểm đo chưa gán đơn vị — OI-05, và màn hình T28.9 phải thấy đủ")
    void toanBoChuaGanDonVi() {
        Integer chuaGan = jdbc.queryForObject(
                "SELECT count(*) FROM stations WHERE org_unit_id IS NULL AND deleted_at IS NULL", Integer.class);

        assertThat(chuaGan)
                .as("OI-05 chưa chốt 7 hay 8 Xí nghiệp — cho tới khi gán xong, resolver người nhận "
                        + "cảnh báo (G11 tập 2) không tìm được ai để gửi")
                .isEqualTo(19);
    }

    /**
     * ⭐⭐ <b>Tự kiểm khối canh của chính migration</b> — luật dự án: mọi bộ canh phải có bài kiểm
     * chứng minh nó bắt được vi phạm.
     *
     * <p>Khối {@code DO $$} ở cuối {@code V202608311049} là thứ bảo vệ seed khỏi kiểu hỏng câm của
     * §10.66. Nhưng một khối canh chỉ chạy trên dữ liệu <i>đúng</i> thì không ai biết nó có thật sự
     * canh gì không — {@code ON CONFLICT DO NOTHING} cộng một khối canh viết sai vẫn cho ra một
     * migration "xanh" trên CSDL rỗng.
     *
     * <p>Cách kiểm: đọc <b>chính đoạn SQL trong tệp migration</b> (không chép lại — chép lại là kiểm
     * bản sao), chạy trên dữ liệu đúng ⇒ im lặng; xoá mềm một điểm đo ⇒ phải ném, và thông điệp phải
     * nêu số đếm thật. Toàn bộ nằm trong một transaction bị cuộn lại.
     */
    @Test
    @DisplayName("⭐⭐ Khối canh của migration BẮT ĐƯỢC vi phạm — phá rồi kiểm, rồi cuộn lại")
    void khoiCanhCuaMigrationBatDuocViPham() throws IOException {
        String khoiCanh = docKhoiCanh(MIGRATION);
        assertThat(khoiCanh)
                .as("không lấy được khối DO $$ trong %s — bài kiểm đang kiểm một chuỗi rỗng", MIGRATION)
                .contains("RAISE EXCEPTION")
                .contains("19");

        // 1. Dữ liệu đúng → khối canh im lặng.
        jdbc.execute(khoiCanh);

        // 2. Phá đúng một dòng → khối canh phải ném, và nêu con số thật (18).
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            int chamPhai = jdbc.update("UPDATE stations SET deleted_at = now() WHERE api_code = 'F01532'");
            assertThat(chamPhai)
                    .as("câu phá phải chạm đúng 1 hàng, nếu không thì bước sau vô nghĩa")
                    .isEqualTo(1);

            assertThatThrownBy(() -> jdbc.execute(khoiCanh))
                    .as("⛔ khối canh im lặng khi thiếu một điểm đo = một bộ canh không canh gì")
                    .hasMessageContaining("đang có 18");

            status.setRollbackOnly();
        });

        // 3. Đã cuộn lại → dữ liệu về nguyên trạng, khối canh lại im lặng.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Integer.class))
                .as("transaction phải được cuộn lại, nếu không bài kiểm này phá hỏng các bài sau")
                .isEqualTo(19);
        jdbc.execute(khoiCanh);
    }

    /**
     * ⭐⭐ Khối canh của {@code V202609091073} cũng phải BẮT ĐƯỢC vi phạm.
     *
     * <p>Cùng lý do với {@link #khoiCanhCuaMigrationBatDuocViPham}: migration ấy là thứ duy nhất giữ
     * cho bản chụp G8 không bị điền thêm hay xoá bớt, và một khối canh chưa ai đi qua thì chưa biết
     * nó canh gì (luật 7).
     *
     * <p>Phá bằng cách <b>xoá một tuyến sông</b> — nhánh đầu tiên của khối. Chọn F01707 (Sông La Khê,
     * lý trình NULL) để phép phá chạm đúng một khẳng định, ⛔ không kéo theo khẳng định lý trình.
     */
    @Test
    @DisplayName("⭐⭐ Khối canh V202609091073 bắt được vi phạm — xoá 1 tuyến sông rồi cuộn lại")
    void khoiCanhBanChupG8BatDuocViPham() throws IOException {
        String khoiCanh = docKhoiCanh(MIGRATION_G8);
        assertThat(khoiCanh)
                .as("không lấy được khối DO $$ trong %s — bài kiểm đang kiểm một chuỗi rỗng", MIGRATION_G8)
                .contains("RAISE EXCEPTION")
                .contains("13");

        jdbc.execute(khoiCanh);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            int chamPhai = jdbc.update("UPDATE stations SET river_name = NULL WHERE api_code = 'F01707'");
            assertThat(chamPhai)
                    .as("câu phá phải chạm đúng 1 hàng, nếu không thì bước sau vô nghĩa")
                    .isEqualTo(1);

            assertThatThrownBy(() -> jdbc.execute(khoiCanh))
                    .as("⛔ khối canh im lặng khi mất một tuyến sông = một bộ canh không canh gì")
                    .hasMessageContaining("đang có 12");

            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stations WHERE deleted_at IS NULL AND river_name IS NOT NULL",
                        Integer.class))
                .as("transaction phải được cuộn lại, nếu không bài kiểm này phá hỏng các bài sau")
                .isEqualTo(13);
        jdbc.execute(khoiCanh);
    }

    /** Lấy khối {@code DO $$ … $$;} cuối cùng trong tệp migration — đọc bản thật, không chép lại. */
    private static String docKhoiCanh(String migration) throws IOException {
        String sql =
                new String(new ClassPathResource(migration).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int batDau = sql.lastIndexOf("DO $$");
        int ketThuc = sql.lastIndexOf("$$;");
        if (batDau < 0 || ketThuc <= batDau) {
            return "";
        }
        return sql.substring(batDau, ketThuc + 3);
    }
}
