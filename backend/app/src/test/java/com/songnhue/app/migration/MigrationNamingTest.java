package com.songnhue.app.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Số hiệu migration — nửa còn lại của <b>T27.2</b>.
 *
 * <h2>⚠⚠ Vì sao một phép kiểm HÌNH DẠNG ở đây là vô dụng</h2>
 *
 * <p>Quy ước là {@code V<yyyyMMdd><nnnn>} với {@code nnnn} là <b>số thứ tự chạy tiếp toàn kho</b>.
 * Lỗi đã trả giá (§10.66) là viết {@code nnnn} thành <b>giờ-phút</b>: {@code V202608272320}. Và
 * chuỗi ấy <b>khớp hoàn hảo</b> mẫu "8 chữ số rồi 4 chữ số" — một regex hình dạng cho nó đi qua,
 * rồi hai lượt CD đỏ liên tiếp.
 *
 * <p>Bất biến THẬT nằm ở chỗ khác: sắp mọi migration theo <b>số hiệu đầy đủ</b> (đúng thứ tự Flyway
 * áp) thì phần {@code nnnn} cũng phải <b>tăng dần</b>. Giờ-phút phá đúng điều đó và không phá gì
 * khác — {@code 202608272320} đứng TRƯỚC {@code 202608281046}, nên {@code nnnn} tụt {@code 2320 →
 * 1046}. Đó là chữ ký duy nhất phân biệt được hai cách viết, nên nó là thứ phải đo (luật 9: một
 * khẳng định không phân biệt được hai trạng thái thì không khẳng định gì).
 *
 * <p>⚠ Bài này bổ trợ, <b>không thay thế</b>, {@code backend/tools/kiem-thu-tu-migration.sh}: script
 * so với <i>nhánh nền</i> để bắt tệp mới rơi xuống dưới bản đã áp trên staging — thứ mà một bộ test
 * chạy trên CSDL rỗng về nguyên tắc không thể thấy (luật 30). Bài này chỉ soi tính nhất quán nội
 * bộ của cây hiện tại, và nó nói ra giới hạn đó (luật 28).
 */
class MigrationNamingTest {

    /** {@code V} + ngày 8 chữ số + số thứ tự 4 chữ số + {@code __} + prefix module. */
    private static final Pattern SO_HIEU =
            Pattern.compile("^V(\\d{8})(\\d{4})__(core|cms|ops|hyd|hr)_[a-z0-9_]+\\.sql$");

    private static final List<String> MODULE =
            List.of("core/core", "content/cms", "operations/ops", "hydro/hyd", "hr/hr");

    /**
     * ⚠⚠ <b>Hai nạn nhân của chính §10.66, đã merge và đã áp — không đổi tên được nữa.</b>
     *
     * <p>{@code 1255} và {@code 1256} là <b>giờ-phút</b> (12:55, 12:56), lạc hẳn khỏi dãy
     * {@code 1001…1049} của cả kho. Chúng vào {@code dev} ở commit {@code c4a49ef} và đã áp trên
     * staging: đổi tên bây giờ là đổi số hiệu một migration Flyway đã ghi vào
     * {@code flyway_schema_history} ⇒ lượt khởi động kế tiếp báo thiếu bản đã áp. Cấm sửa migration
     * đã merge (conventions.md §1.2) không có ngoại lệ nào cho "sửa cho đẹp".
     *
     * <p>⛔ Danh sách này là <b>đường ranh, không phải chỗ để dọn</b>. Nó tồn tại để luật bên dưới
     * chặn được mọi tệp MỚI đánh số bằng giờ-phút, thay vì phải tắt hẳn luật vì hai tệp lịch sử —
     * đúng thứ mà "bộ canh phải nói ra phạm vi của chính nó" (luật 28) đòi hỏi. Thêm tên vào đây là
     * một quyết định phải giải trình, không phải một thao tác cho hết đỏ.
     */
    private static final List<String> NGOAI_LE_LICH_SU = List.of(
            "V202608241255__core_update_company_contact.sql", "V202608241256__core_workflow_requires_reason.sql");

    private record Migration(String ten, long soHieu, int thuTu) {}

    @Test
    @DisplayName("Mọi tệp V*.sql khớp mẫu V<yyyyMMdd><nnnn>__<prefix>_<mô_tả>.sql")
    void everyMigrationFileMatchesThePattern() {
        List<String> sai = docTatCa().stream()
                .map(Migration::ten)
                .filter(ten -> !SO_HIEU.matcher(ten).matches())
                .toList();

        assertThat(sai)
                .as("mẫu: V + ngày(8) + số thứ tự(4) + __ + prefix module + mô tả chữ thường")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠⚠ Sắp theo số hiệu thì phần <nnnn> cũng phải TĂNG DẦN — chữ ký chống đánh số bằng giờ-phút")
    void theSequenceNumberIncreasesInStepWithTheVersion() {
        List<Migration> theoThuTu = docTatCa().stream()
                .filter(m -> !NGOAI_LE_LICH_SU.contains(m.ten()))
                .sorted(Comparator.comparingLong(Migration::soHieu))
                .toList();

        List<String> tut = new ArrayList<>();
        for (int i = 1; i < theoThuTu.size(); i++) {
            Migration truoc = theoThuTu.get(i - 1);
            Migration sau = theoThuTu.get(i);
            if (sau.thuTu() <= truoc.thuTu()) {
                tut.add("%s (nnnn=%04d) đứng sau %s (nnnn=%04d)"
                        .formatted(sau.ten(), sau.thuTu(), truoc.ten(), truoc.thuTu()));
            }
        }

        assertThat(tut)
                .as(
                        """
                        ⛔ `nnnn` là SỐ THỨ TỰ TOÀN KHO, không phải giờ-phút. Một tệp đánh số bằng giờ-phút \
                        vẫn khớp mọi mẫu hình dạng, nhưng nó làm dãy `nnnn` TỤT khi sắp theo số hiệu — đó \
                        là thứ đo được duy nhất phân biệt hai cách viết. Đúng lỗi làm hai lượt CD đỏ liên \
                        tiếp ngày 27/08/2026 (§10.66).""")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Bài trên thật sự bắt được một số hiệu đánh bằng giờ-phút")
    void theRuleActuallyCatchesATimestampStyleVersion() {
        // Kiểm chứng ngược trên dữ liệu dựng tay: dãy thật đang hợp lệ nên bài trên xanh vì mã đúng,
        // mà "xanh vì mã đúng" và "xanh vì luật mù" nhìn giống hệt nhau (luật 7).
        List<Migration> giaLap = List.of(
                new Migration("V202608271046__ops_a.sql", 202608271046L, 1046),
                // Đúng hình dạng, đúng ngày hợp lệ — và là GIỜ-PHÚT. Đây là tệp đã gây sự cố thật.
                new Migration("V202608272320__ops_b.sql", 202608272320L, 2320),
                new Migration("V202608281047__ops_c.sql", 202608281047L, 1047));

        assertThat(giaLap.stream().allMatch(m -> SO_HIEU.matcher(m.ten()).matches()))
                .as("⚠ cả ba đều khớp mẫu hình dạng — đó chính là lý do mẫu hình dạng không đủ")
                .isTrue();

        List<String> tut = new ArrayList<>();
        List<Migration> theoThuTu = giaLap.stream()
                .sorted(Comparator.comparingLong(Migration::soHieu))
                .toList();
        for (int i = 1; i < theoThuTu.size(); i++) {
            if (theoThuTu.get(i).thuTu() <= theoThuTu.get(i - 1).thuTu()) {
                tut.add(theoThuTu.get(i).ten());
            }
        }

        assertThat(tut)
                .as("luật phải chỉ đích danh tệp đứng SAU kẻ đánh số bằng giờ-phút")
                .containsExactly("V202608281047__ops_c.sql");
    }

    @Test
    @DisplayName("⛔ Danh sách ngoại lệ lịch sử KHÔNG được dài thêm")
    void theLegacyExemptionListDoesNotGrow() {
        List<String> tenTep = docTatCa().stream().map(Migration::ten).toList();

        assertThat(NGOAI_LE_LICH_SU)
                .as(
                        """
                        Hai tệp này là nạn nhân của §10.66, đã merge và đã áp nên không đổi tên được.                         Danh sách dài thêm nghĩa là có người vừa đánh số bằng giờ-phút LẦN NỮA rồi cho                         nó vào danh sách miễn trừ cho hết đỏ — tức là gỡ đúng cái chuông báo cháy.""")
                .hasSize(2);
        assertThat(tenTep)
                .as("ngoại lệ trỏ vào tệp không còn tồn tại thì nó đang miễn trừ cho một cái bóng")
                .containsAll(NGOAI_LE_LICH_SU);
    }

    @Test
    @DisplayName("⚠ Không chạy qua tập rỗng — phải đọc được toàn bộ migration của 5 module")
    void theScanActuallyFindsTheMigrations() {
        List<Migration> tatCa = docTatCa();

        assertThat(tatCa)
                .as(
                        """
                        Đường dẫn sai thì mọi khẳng định ở trên xanh trên một danh sách RỖNG (luật 7). \
                        Ngưỡng đặt thấp hơn số thật để bài kiểm không phải sửa mỗi lần thêm migration, \
                        nhưng đủ cao để một lượt quét hỏng lộ ra ngay.""")
                .hasSizeGreaterThanOrEqualTo(40);
    }

    private static List<Migration> docTatCa() {
        List<Migration> ket = new ArrayList<>();
        for (String m : MODULE) {
            String[] phan = m.split("/");
            Path thuMuc = Path.of("..", phan[0], "src/main/resources/db/migration", phan[1]);
            if (!Files.isDirectory(thuMuc)) {
                continue;
            }
            try (Stream<Path> tep = Files.list(thuMuc)) {
                tep.map(p -> p.getFileName().toString())
                        .filter(ten -> ten.startsWith("V") && ten.endsWith(".sql"))
                        .forEach(ten -> {
                            Matcher khop = SO_HIEU.matcher(ten);
                            if (khop.matches()) {
                                ket.add(new Migration(
                                        ten,
                                        Long.parseLong(khop.group(1) + khop.group(2)),
                                        Integer.parseInt(khop.group(2))));
                            } else {
                                ket.add(new Migration(ten, 0L, 0));
                            }
                        });
            } catch (IOException e) {
                throw new IllegalStateException("Không đọc được thư mục migration " + thuMuc, e);
            }
        }
        return ket;
    }
}
