package com.songnhue.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Tập lớp production mà mọi luật kiến trúc soi vào — nạp đúng một lần cho cả gói.
 *
 * <p>Đặt ở module {@code app} vì đây là nơi <b>duy nhất</b> cả 5 module nghiệp vụ cùng nằm trên
 * classpath. ArchUnit đọc bytecode từ classpath, nên {@code core} tuy là một jar phụ thuộc vẫn được
 * quét bình thường.
 *
 * <p>⚠ <b>Vì sao gọi luật bằng {@code @Test} thường chứ không dùng {@code @ArchTest}.</b> Bản đầu
 * tiên của bộ luật này viết theo lối chính thống của ArchUnit: lớp gắn {@code @AnalyzeClasses}, các
 * hằng {@code @ArchTest}, để bộ máy JUnit Platform riêng của ArchUnit chạy. Kết quả: Surefire báo
 * <b>{@code Tests run: 0}</b> cho cả bốn lớp luật và <b>build vẫn xanh</b>. Thử đặt một luật chắc
 * chắn sai (mọi {@code @Entity} phải nằm trong gói {@code api}) — vẫn xanh. Bộ máy {@code archunit}
 * có mặt đủ trên classpath nhưng không tìm ra bài kiểm nào, và không ai báo lỗi cả.
 *
 * <p>Tức là toàn bộ hàng rào kiến trúc đã ở trạng thái trang trí, đúng loại "yên tâm giả" mà WS-10
 * sinh ra để chống. Nên cách gọi ở đây là cách thẳng nhất: một {@code @Test} bình thường gọi
 * {@code rule.check(ALL)}. Không có bộ máy trung gian nào để hỏng âm thầm, số lượng bài kiểm hiện
 * đúng trong log CI, và tên luật hiện ra khi nó gãy.
 */
final class ProductionClasses {

    /**
     * Toàn bộ lớp production dưới {@code com.songnhue} (đã loại {@code src/test}).
     *
     * <p>Nạp một lần rồi dùng chung: quét bytecode mất khoảng một giây, nhân với số lớp luật thì
     * thành thời gian chờ vô ích ở mỗi lần chạy CI.
     *
     * <p>⚠ Cố ý <b>không</b> đặt {@code DoNotIncludeJars}: {@code core} và 4 module nghiệp vụ tới đây
     * dưới dạng jar phụ thuộc, loại jar ra là bộ luật chỉ còn soi vài lớp của {@code app} — vẫn xanh,
     * vẫn vô dụng. {@link ImportedScopeTest} canh đúng điều đó.
     */
    static final JavaClasses ALL = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .withImportOption(ProductionClasses::khongPhaiTestJar)
            .importPackages("com.songnhue");

    /**
     * ⛔⛔ T73.5 — loại {@code *-tests.jar}. {@code app} phụ thuộc test-jar của {@code core} (đồ dùng chung như
     * {@code RsaKeyPairFixture}); {@link ImportOption.DoNotIncludeTests} chỉ nhận ra thư mục
     * {@code /test-classes/}. Ở lượt chạy nhắm mục tiêu, reactor trỏ vào thư mục ấy ⇒ bị loại đúng; ở lượt
     * {@code verify} đầy đủ, nó là một JAR ⇒ <b>lọt</b>, và mọi luật ở gói này soi cả lớp KIỂM của {@code core}.
     * Lộ ra khi luật gửi thư đỏ vì {@code MailConfigTest} chỉ ở lượt chạy toàn bộ (T55.9); câu "đã loại
     * {@code src/test}" ở trên sai từ ngày có test-jar. {@code ImportedScopeTest} canh vế này.
     */
    static boolean khongPhaiTestJar(com.tngtech.archunit.core.importer.Location viTri) {
        return !viTri.contains("-tests.jar");
    }

    private ProductionClasses() {}
}
