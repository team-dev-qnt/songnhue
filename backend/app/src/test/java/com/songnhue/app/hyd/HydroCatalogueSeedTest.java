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
            Map.entry("F01519", new String[] {"DO-LCO-TL", "THUONG_LUU"}),
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
     * ⛔ G8 chưa có dữ liệu ⇒ tuyến sông, lý trình và toạ độ phải RỖNG HẾT.
     *
     * <p>Bài kiểm này canh một điều dễ bị "sửa cho đẹp": ai đó thấy bản đồ trống rồi điền toạ độ
     * phỏng đoán. Một điểm sai trên bản đồ tệ hơn hẳn một bản đồ trống — bản đồ trống thì còn nằm
     * trong danh sách nhắc việc.
     */
    @Test
    @DisplayName("⛔ Không bịa dữ liệu G8: tuyến sông / lý trình / toạ độ đều NULL cả 19 dòng")
    void khongBiaDuLieuChuaCo() {
        Integer coDuLieu = jdbc.queryForObject(
                "SELECT count(*) FROM stations WHERE deleted_at IS NULL AND ("
                        + "river_name IS NOT NULL OR chainage IS NOT NULL "
                        + "OR latitude IS NOT NULL OR longitude IS NOT NULL)",
                Integer.class);

        assertThat(coDuLieu)
                .as("G8 chưa cấp tuyến sông/lý trình/toạ độ — điền phỏng đoán là sinh điểm sai trên bản đồ")
                .isZero();
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
        Integer soLienKet = jdbc.queryForObject("SELECT count(*) FROM station_constructions", Integer.class);

        assertThat(soMnSong).isEqualTo(4);
        assertThat(soLienKet)
                .as("G8 chưa có danh mục công trình — chưa liên kết được dòng nào")
                .isZero();

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
        String khoiCanh = docKhoiCanh();
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

    /** Lấy khối {@code DO $$ … $$;} cuối cùng trong tệp migration — đọc bản thật, không chép lại. */
    private static String docKhoiCanh() throws IOException {
        String sql =
                new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int batDau = sql.lastIndexOf("DO $$");
        int ketThuc = sql.lastIndexOf("$$;");
        if (batDau < 0 || ketThuc <= batDau) {
            return "";
        }
        return sql.substring(batDau, ketThuc + 3);
    }
}
