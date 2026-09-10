package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⛔⛔ <b>Mọi cột khoá ngoại trỏ vào {@code org_units} phải có một câu trả lời cho lượt GIẢI THỂ</b>
 * — CN-04.1.
 *
 * <h2>Vì sao bộ canh này tồn tại</h2>
 *
 * <p>{@code function-spec.md:616} đòi: <i>"giải thể/xóa đơn vị chỉ khi ⛔ <b>không còn nhân
 * viên/công trình liên kết</b>"</i>. Đo 10/09/2026, {@code OrgUnitService.delete} kiểm <b>đơn vị
 * cấp dưới</b> và <b>tài khoản</b> — hai thứ đặc tả ⛔ không nêu — và ⛔ <b>không kiểm</b> hai thứ
 * đặc tả nêu đích danh. Guard ấy viết ở Phase 0 khi {@code employees}/{@code constructions} chưa
 * tồn tại: nó ⛔ không sai lúc viết, nó <b>hết đúng</b> khi kho lớn lên.
 *
 * <p>⇒ Vá một lượt là chưa đủ. Cột thứ 14 sẽ ra đời ở một module nào đó vào một ngày nào đó, và
 * người viết nó ⛔ không có lý do gì để nhớ tới lớp {@code OrgUnitService} ở module khác. Đó đúng
 * hình dạng <b>luật 28</b>: <i>phạm vi phải do bộ canh <b>ĐO</b>, ⛔ không do người viết sổ gõ
 * tay</i>.
 *
 * <h2>Bộ canh này ⛔ KHÔNG khẳng định "đã chặn hết" — nó khẳng định "đã QUYẾT hết"</h2>
 *
 * <p>Một số cột <b>đúng</b> là ⛔ không nên chặn ({@code org_unit_leaders} đi kèm
 * {@code ON DELETE CASCADE}; {@code maintenance_logs.performer_org_unit_id} là quy kết
 * <b>lịch sử</b> — chặn theo nó thì ⛔ không đơn vị nào giải thể được nữa). Nên bất biến ở đây là:
 * <b>mỗi cột hoặc CÓ chốt chặn, hoặc có một dòng miễn trừ mang LÝ DO đọc được</b>. Cột mới rơi vào
 * ⛔ không nhóm nào ⇒ đỏ, và người thêm nó buộc phải quyết.
 */
class SoDonViThamChieuTest {

    /** Cột <b>có</b> chốt chặn — hoặc ở {@code OrgUnitService}, hoặc ở một {@code OrgUnitUsagePort}. */
    private static final Map<String, String> CO_CHOT_CHAN = Map.of(
            "org_units.parent_id", "OrgUnitService.delete — existsByParentIdAndDeletedAtIsNull",
            "users.org_unit_id", "OrgUnitService.delete — existsByOrgUnitIdAndDeletedAtIsNull",
            "employees.org_unit_id", "hr · HoSoThuocDonVi",
            "constructions.org_unit_id", "operations · CongTrinhThuocDonVi",
            "maintenance_logs.org_unit_id", "operations · CongTrinhThuocDonVi",
            "construction_clusters.org_unit_id", "operations · CongTrinhThuocDonVi",
            "stations.org_unit_id", "hydro · DiemDoThuocDonVi",
            "contacts.assigned_org_unit_id", "content · LienHeGiaoChoDonVi");

    /**
     * Cột <b>cố ý ⛔ không</b> chặn — mỗi dòng phải mang một lý do đọc được.
     *
     * <p>⛔ Đây ⛔ không phải chỗ để "cho hết đỏ": mỗi dòng ở đây là một khẳng định rằng <i>một đơn
     * vị giải thể mà cột này còn trỏ vào nó thì ⛔ không có gì hỏng</i>.
     */
    private static final Map<String, String> MIEN_TRU = new LinkedHashMap<>(Map.of(
            "org_unit_leaders.org_unit_id",
                    "ON DELETE CASCADE — bản ghi lãnh đạo đi theo đơn vị theo đúng thiết kế lược đồ",
            "maintenance_logs.performer_org_unit_id",
                    "Quy kết LỊCH SỬ (đơn vị nào đã thực hiện). Chặn theo nó thì ⛔ không đơn vị nào "
                            + "giải thể được nữa sau vài năm vận hành — cái giá lớn hơn hẳn cái được",
            "attachments.org_unit_id",
                    "Cột phạm vi DẪN XUẤT từ chủ sở hữu (bài viết / công trình / hồ sơ). Chủ sở hữu "
                            + "đã có chốt chặn riêng; chặn thêm ở đây là chặn hai lần cùng một thứ",
            "jobs.org_unit_id", "Hàng đợi việc — bản ghi tạm, cột phạm vi chỉ để lọc màn hình quản trị job",
            "construction_operation_status.org_unit_id",
                    "Mỗi bản ghi tình hình vận hành BẮT BUỘC thuộc một công trình, mà công trình đã "
                            + "có chốt chặn ⇒ chặn được công trình là chặn được nó"));

    @Test
    @DisplayName("⛔⛔ Mọi cột FK trỏ vào org_units đều đã được QUYẾT — chặn, hoặc miễn trừ có lý do")
    void moiCotThamChieuDeuDaDuocQuyet() throws IOException {
        List<String> cot = quetLuocDo();

        // ⛔⛔ CHỐNG-TẬP-RỖNG (luật 7). Đổi bố cục thư mục migration mà quên bài này thì phép so
        // dưới chạy trên danh sách RỖNG và xanh trọn vẹn — đúng thứ §11.19 đã trả giá.
        assertThat(cot)
                .as("⛔ Phép quét lược đồ trả về RỖNG ⇒ bộ canh đang canh ⛔ không gì cả. Đo 10/09/2026: "
                        + "13 cột trên 5 module")
                .hasSizeGreaterThanOrEqualTo(13);

        List<String> chuaQuyet = cot.stream()
                .filter(c -> !CO_CHOT_CHAN.containsKey(c) && !MIEN_TRU.containsKey(c))
                .toList();
        assertThat(chuaQuyet)
                .as("⛔⛔ Những cột này trỏ vào `org_units` mà ⛔ CHƯA có câu trả lời cho lượt giải "
                        + "thể. Thêm một bean `OrgUnitUsagePort` ở module sở hữu, HOẶC thêm một dòng "
                        + "`MIEN_TRU` kèm lý do đọc được. ⛔ Đừng thêm vào MIEN_TRU chỉ để hết đỏ — "
                        + "mỗi dòng ở đó là một khẳng định rằng giải thể đơn vị ⛔ không làm hỏng gì.")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Danh sách khai ⛔ KHÔNG được chứa cột đã biến mất khỏi lược đồ")
    void danhSachKhaiKhongConThuaCot() throws IOException {
        List<String> cot = quetLuocDo();
        List<String> thua = Stream.concat(CO_CHOT_CHAN.keySet().stream(), MIEN_TRU.keySet().stream())
                .filter(k -> !cot.contains(k))
                .toList();
        // ⛔ Một dòng khai trỏ vào cột ⛔ không còn tồn tại là một dòng ⛔ không canh gì — và nó làm
        //   cả danh sách đọc như bảo đảm rộng hơn thực tế (luật 28).
        assertThat(thua)
                .as("Những khoá này ⛔ không còn cột nào tương ứng trong lược đồ — gõ sai tên, hoặc "
                        + "cột đã bị gỡ và dòng khai bị bỏ quên")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Mỗi dòng miễn trừ phải mang LÝ DO thật, ⛔ không phải một chỗ trống")
    void miemTruPhaiCoLyDo() {
        // ⛔ 40 ký tự: cùng ngưỡng mà `MaLoiCoNoiNemTest` dùng, và nó đã bắt được một dòng miễn trừ
        //   33 ký tự của chính người viết ra nó (§10.69).
        MIEN_TRU.forEach((cot, ly) -> assertThat(ly)
                .as("Lý do miễn trừ cho `%s` quá ngắn để nói được điều gì", cot)
                .hasSizeGreaterThanOrEqualTo(40));
        assertThat(MIEN_TRU).hasSizeGreaterThanOrEqualTo(5);
    }

    // -------------------------------------------------------------------------

    /**
     * Quét <b>mọi</b> tệp migration, ⛔ không liệt kê thư mục bằng tay.
     *
     * <p>⚠ {@code MigrationNamingTest} đã trả giá đúng chỗ này (T49.1): nó liệt kê tay 5 thư mục
     * {@code db/migration/<module>} và mù trước {@code db/seed/portal} — <b>một vị trí Flyway
     * thật</b> — suốt 16 ngày, trong khi bên trong có sẵn một nạn nhân của chính thứ nó canh.
     */
    private static List<String> quetLuocDo() throws IOException {
        Path goc = Path.of("..").toAbsolutePath().normalize();
        Pattern taoBang = Pattern.compile("^(?:CREATE TABLE(?: IF NOT EXISTS)?|ALTER TABLE)\\s+([a-z_]+)");
        Pattern cotFk = Pattern.compile("^(?:ADD COLUMN\\s+)?([a-z_]+)\\s+BIGINT");

        List<String> ket = new ArrayList<>();
        try (Stream<Path> tep = Files.walk(goc)) {
            List<Path> sql = tep.filter(p -> p.toString().contains("/src/main/resources/db/"))
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
            for (Path p : sql) {
                String bang = null;
                for (String dong : Files.readAllLines(p)) {
                    String t = dong.strip();
                    Matcher mb = taoBang.matcher(t);
                    if (mb.find()) {
                        bang = mb.group(1);
                    }
                    if (t.contains("REFERENCES org_units")) {
                        Matcher mc = cotFk.matcher(t);
                        if (mc.find() && bang != null) {
                            ket.add(bang + "." + mc.group(1));
                        }
                    }
                }
            }
        }
        return ket;
    }
}
