package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import com.songnhue.app.architecture.fixture.AuditRedactionFixtures;
import com.songnhue.core.common.audit.Audited;

/**
 * ⭐⭐ Bài <b>tự-kiểm-chứng</b> cho {@link AuditRedactionRuleTest} — {@code conventions.md} §1.5,
 * luật 1: <i>mỗi cơ chế canh gác phải có bài chứng minh nó BẮT ĐƯỢC vi phạm</i>.
 *
 * <p>⚠ Luật 29: <i>một bài kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm đang sai</i> —
 * người viết cả hai là cùng một người. Nên bài này ⛔ không chỉ hỏi <i>"có bắt được vi phạm
 * không"</i>: <b>ba trong bốn</b> fixture là <b>ĐỐI CHỨNG phải-được-tha</b>. Thiếu chúng thì một
 * luật viết kiểu <i>"từ chối tất cả"</i> cũng làm bài này xanh — và nó sẽ làm mọi entity thật đỏ,
 * nên người ta tắt nó đi.
 */
class AuditRedactionRuleSelfCheckTest {

    /** Nạp riêng gói fixture: luật thật chạy trên {@code ProductionClasses.ALL} đã loại {@code src/test}. */
    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages(AuditRedactionFixtures.class.getPackageName());

    private static JavaClass lop(Class<?> c) {
        return FIXTURES.get(c);
    }

    @Test
    @DisplayName("⛔ BẮT được entity giữ bí mật mà ⛔ không loại trừ (luật 1)")
    void batDuocBiMatKhongDuocLoaiTru() {
        JavaClass roRi = lop(AuditRedactionFixtures.RoBiMat.class);

        assertThat(AuditRedactionRuleTest.truongBiMat(roRi))
                .as("⛔ Bộ nhận diện phải thấy `credential`")
                .extracting(f -> f.getName())
                .containsExactly("credential");
        assertThat(roRi.getAnnotationOfType(Audited.class).excludeFields())
                .as("⛔ Tập loại trừ phải RỖNG — nếu ⛔ không, luật 1 sẽ tha mọi vi phạm")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ BẮT được `excludeFields` TREO — ca đổi tên trường trong im lặng (luật 2)")
    void batDuocLoaiTruTreo() {
        JavaClass treo = lop(AuditRedactionFixtures.LoaiTruTreo.class);
        Set<String> tenTruong =
                treo.getFields().stream().map(f -> f.getName()).collect(java.util.stream.Collectors.toSet());

        assertThat(treo.getAnnotationOfType(Audited.class).excludeFields()).containsExactly("tenCu");
        assertThat(tenTruong)
                .as("⛔⛔ Đây là ca đắt nhất: entity biên dịch sạch, cả nghìn bài kiểm xanh, và bí mật "
                        + "chảy vào nhật ký vì phép che là một phép so CHUỖI")
                .doesNotContain("tenCu")
                .contains("secretMoi");
    }

    @Test
    @DisplayName("⭐ ĐỐI CHỨNG — entity khai ĐÚNG phải được THA")
    void thaEntityKhaiDung() {
        JavaClass dung = lop(AuditRedactionFixtures.KhaiDung.class);
        List<String> biMat = AuditRedactionRuleTest.truongBiMat(dung).stream()
                .map(f -> f.getName())
                .toList();
        Set<String> loaiTru = Set.of(dung.getAnnotationOfType(Audited.class).excludeFields());

        assertThat(biMat).containsExactly("passwordHash");
        assertThat(loaiTru)
                .as("⛔ Nếu vế này đỏ thì luật đang 'từ chối tất cả' — nó sẽ làm mọi entity thật đỏ "
                        + "và bị tắt đi, tức tệ hơn ⛔ không có bộ canh")
                .containsAll(biMat);
    }

    @Test
    @DisplayName("⭐⭐ ĐỐI CHỨNG — bộ lọc KIỂU tha một mốc thời gian, dù tên khớp mẫu")
    void boLocKieuThaMocThoiGian() {
        JavaClass moc = lop(AuditRedactionFixtures.ChiLaMocThoiGian.class);

        assertThat(moc.getFields())
                .as("⛔ vế chống tập rỗng: fixture phải THẬT SỰ có trường ấy")
                .extracting(f -> f.getName())
                .contains("passwordChangedAt");
        assertThat(AuditRedactionRuleTest.truongBiMat(moc))
                .as(
                        """
                        ⛔⛔ `passwordChangedAt` khớp mẫu tên nhưng là `Instant`. Bộ lọc KIỂU phải \
                        loại nó bằng CẤU TRÚC — nếu ⛔ không thì dự án phải nuôi một dòng `NGOAI_LE` \
                        cho nó, và một dòng ngoại lệ là một thứ con người phải nhớ. Đây cũng là lý do \
                        `NGOAI_LE` RỖNG hôm nay.""")
                .isEmpty();
    }
}
