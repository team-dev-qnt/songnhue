package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Thứ tự hiển thị của mọi cây materialized-path phải đi qua đúng <b>một cửa</b> — T26.25.
 *
 * <h2>Khuyết tật mà bộ luật này sinh ra để chặn</h2>
 *
 * Bốn cây của hệ (menu, danh mục nội dung, thư mục media, đơn vị tổ chức) đọc bằng
 * {@code ORDER BY path ASC, sort_order ASC}. Câu ấy <b>không bao giờ so tới {@code sort_order}</b>:
 * {@code path} chứa id của chính nút nên hai anh em không thể trùng path. Thứ tự thật là thứ tự
 * {@code path} <b>so theo chuỗi</b> — {@code /10/} đứng trước {@code /9/}.
 *
 * <p>Hệ quả đo được ngày 08/09/2026: kéo–thả đổi thứ tự menu ở màn hình quản trị trả <b>204 thành
 * công</b> và cổng công khai <b>không đổi gì</b> ({@code ThuTuMenuTest} khi chưa vá: 2/3 đỏ). Đó là
 * quy tắc 15 (núm không điều khiển gì) chồng lên quy tắc 27 (nửa cặp đọc–ghi), và triệu chứng của
 * cả hai đều <b>im lặng</b>.
 *
 * <h2>Vì sao canh ở tầng bytecode chứ không grep tên tệp</h2>
 *
 * Vì thứ phải giữ là một <b>quan hệ giữa hai phương thức</b> — cửa hiển thị phải thật sự gọi phép
 * sắp — chứ không phải sự có mặt của một chuỗi ký tự (quy tắc 2). Một {@code findAllForDisplay()}
 * viết đúng tên mà quên gọi {@link com.songnhue.core.common.tree.MaterializedPath#sortForDisplay} sẽ
 * qua được mọi phép canh văn bản, và nó tái lập <i>chính xác</i> khuyết tật gốc.
 *
 * <h2>⚠ Phạm vi bộ luật này — nói ra thay vì để người đọc suy (quy tắc 28)</h2>
 *
 * <ul>
 *   <li>Canh được: tên hứa hão, cửa hiển thị rỗng ruột, câu thô bị gọi vượt mặt, cây thiếu cửa.
 *   <li><b>Không</b> canh được: phép sắp có <i>đúng</i> hay không. Đó là việc của
 *       {@code MaterializedPathTest} (9 bài, gồm ca id 9↔10 mà bài tích hợp không dựng nổi) và
 *       {@code ThuTuMenuTest} (đi đúng đường cổng công khai đi).
 *   <li><b>Không</b> canh được cây nào <i>chưa</i> dùng materialized path. Bộ luật neo vào tên câu
 *       truy vấn dẫn xuất; một cây tự viết native query sẽ vô hình với nó.
 * </ul>
 */
class ThuTuCayRuleTest {

    /**
     * Tên câu truy vấn thô — sắp theo {@code path}, tức chỉ bảo đảm được vế cha trước con.
     *
     * <p>⚠ Mẫu cố ý <b>không</b> đòi tiền tố {@code findAll}: {@code MenuItemRepository} lọc thêm
     * theo {@code position} nên tên nó là {@code findByPosition...}.
     */
    private static final String CAU_THO = "find.*OrderByPathAsc";

    /** Tên cửa duy nhất được phép hứa thứ tự hiển thị. */
    private static final String CUA_HIEN_THI = "find.*ForDisplay";

    /** Tên đầy đủ của phép sắp mà mọi cửa hiển thị phải gọi. */
    private static final String LOP_SAP = "com.songnhue.core.common.tree.MaterializedPath";

    private static final String TEN_PHEP_SAP = "sortForDisplay";

    // =========================================================================
    // Luật — khai riêng để bài tự-kiểm-chứng chạy lại đúng chúng, không chép lại
    // =========================================================================

    /**
     * ⛔ Cấm một cái tên hứa điều SQL không làm được.
     *
     * <p>{@code ...OrderByPathAscSortOrderAsc} đọc như <i>"cha trước con, anh em đúng thứ tự"</i> —
     * và javadoc của {@code MenuItemRepository} từng viết đúng câu ấy. Cái tên là nơi lời nói dối
     * bắt đầu; cấm nó là chặn ngay chỗ rẻ nhất.
     */
    static ArchRule tenHuaHao() {
        return noMethods()
                .should()
                .haveNameMatching(".*OrderByPath.*SortOrder.*")
                .because("ORDER BY path,sort_order KHÔNG bao giờ so tới sort_order — path chứa id của "
                        + "chính nút nên hai anh em không thể trùng path (T26.25). Đặt tên như vậy là "
                        + "hứa một bảo đảm không tồn tại; dùng cửa " + CUA_HIEN_THI + " thay thế");
    }

    /** Mọi cửa hiển thị phải THẬT SỰ gọi phép sắp — tên đúng mà rỗng ruột là khuyết tật gốc quay lại. */
    static ArchRule cuaHienThiPhaiGoiPhepSap() {
        return methods()
                .that()
                .haveNameMatching(CUA_HIEN_THI)
                .should(new ArchCondition<>("gọi " + LOP_SAP + "." + TEN_PHEP_SAP) {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        boolean coGoi = method.getMethodCallsFromSelf().stream().anyMatch(ThuTuCayRuleTest::laPhepSap);
                        if (!coGoi) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    "%s hứa thứ tự hiển thị mà không gọi %s.%s — một cửa rỗng ruột tái lập "
                                                    .formatted(method.getFullName(), LOP_SAP, TEN_PHEP_SAP)
                                            + "đúng khuyết tật T26.25 và qua được mọi phép canh văn bản"));
                        }
                    }
                });
    }

    /**
     * Câu thô chỉ được gọi từ <b>chính lớp khai nó</b>.
     *
     * <p>Đây là vế biến bảo đảm thành cấu trúc thay vì lời dặn (quy tắc 12): còn một nơi gọi vượt mặt
     * là còn một đường đọc cho ra thứ tự khác, và hai đường đọc lệch nhau là thứ không ai thấy cho
     * tới lúc Công ty hỏi vì sao menu trên cổng khác menu trong trang quản trị.
     */
    static ArchRule cauThoKhongDuocGoiVuotMat() {
        return classes().should(new ArchCondition<>("chỉ gọi câu truy vấn thô từ chính repository khai nó") {
            @Override
            public void check(JavaClass lop, ConditionEvents events) {
                for (JavaMethodCall goi : lop.getMethodCallsFromSelf()) {
                    if (!goi.getTarget().getName().matches(CAU_THO)) {
                        continue;
                    }
                    if (!lop.equals(goi.getTargetOwner())) {
                        events.add(SimpleConditionEvent.violated(
                                lop,
                                "%s gọi thẳng %s — câu ấy KHÔNG sắp được anh em; đi qua cửa %s"
                                        .formatted(
                                                lop.getName(), goi.getTarget().getFullName(), CUA_HIEN_THI)));
                    }
                }
            }
        });
    }

    /** Lớp nào khai câu thô thì phải khai luôn cửa hiển thị — thiếu cửa là không có đường đọc đúng. */
    static ArchRule khaiCauThoThiPhaiKhaiCuaHienThi() {
        return classes()
                .that(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>("khai câu truy vấn thô") {
                    @Override
                    public boolean test(JavaClass lop) {
                        return coPhuongThucTen(lop, CAU_THO);
                    }
                })
                .should(new ArchCondition<>("khai một cửa " + CUA_HIEN_THI) {
                    @Override
                    public void check(JavaClass lop, ConditionEvents events) {
                        if (!coPhuongThucTen(lop, CUA_HIEN_THI)) {
                            events.add(SimpleConditionEvent.violated(
                                    lop,
                                    lop.getName() + " đọc cây bằng câu thô mà không có cửa nào trả thứ tự "
                                            + "hiển thị — mọi nơi gọi buộc phải dùng thứ tự sai"));
                        }
                    }
                });
    }

    // =========================================================================
    // Chạy luật lên mã production
    // =========================================================================

    @Test
    @DisplayName("⛔ Không tên phương thức nào hứa 'anh em đúng thứ tự' bằng ORDER BY")
    void khongConTenHuaHao() {
        tenHuaHao().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Mọi cửa *ForDisplay đều thật sự gọi MaterializedPath.sortForDisplay")
    void cuaHienThiKhongRongRuot() {
        cuaHienThiPhaiGoiPhepSap().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Câu truy vấn thô không bị gọi vượt mặt từ tầng service")
    void khongAiGoiVuotMat() {
        cauThoKhongDuocGoiVuotMat().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Cây nào đọc bằng câu thô cũng có cửa hiển thị")
    void moiCayDeuCoCuaHienThi() {
        khaiCauThoThiPhaiKhaiCuaHienThi().check(ProductionClasses.ALL);
    }

    /**
     * ⛔ Chống "xanh trên tập rỗng" (quy tắc 7).
     *
     * <p>Bốn bài trên đều xanh trọn vẹn nếu bộ nạp lớp không thấy repository nào — đúng trạng thái
     * ArchUnit đã ở suốt Phase 0. Khẳng định <b>về số lượng</b> là thứ duy nhất phân biệt được
     * <i>"không có vi phạm"</i> với <i>"không có gì để soi"</i>, và nó không dùng chung giả định nào
     * với bốn luật kia (quy tắc 29).
     */
    @Test
    @DisplayName("⛔ Bộ luật phải thật sự soi được 4 cây, không xanh vì tập rỗng")
    void luatKhongChayQuaTapRong() {
        List<String> coCauTho = timLopCoPhuongThuc(ProductionClasses.ALL, CAU_THO);
        List<String> coCuaHienThi = timLopCoPhuongThuc(ProductionClasses.ALL, CUA_HIEN_THI);

        assertThat(coCauTho)
                .as("bốn cây materialized path của hệ: menu · danh mục · thư mục media · đơn vị tổ chức")
                .hasSizeGreaterThanOrEqualTo(4);
        assertThat(coCuaHienThi).as("mỗi cây một cửa hiển thị").hasSizeGreaterThanOrEqualTo(4);
        assertThat(coCuaHienThi)
                .as("cửa hiển thị phải nằm đúng ở những lớp khai câu thô, không ở đâu khác")
                .containsExactlyInAnyOrderElementsOf(coCauTho);
    }

    // =========================================================================

    private static boolean laPhepSap(JavaMethodCall goi) {
        return TEN_PHEP_SAP.equals(goi.getTarget().getName())
                && LOP_SAP.equals(goi.getTargetOwner().getName());
    }

    private static boolean coPhuongThucTen(JavaClass lop, String mau) {
        return lop.getMethods().stream().anyMatch(m -> m.getName().matches(mau));
    }

    private static List<String> timLopCoPhuongThuc(JavaClasses tapLop, String mau) {
        return tapLop.stream()
                .filter(lop -> coPhuongThucTen(lop, mau))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }
}
