package com.songnhue.app.ditru;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⛔⛔ Flyway phải được <b>tự cấu hình</b>, ⛔ không chỉ nằm trên classpath — T11.69.
 *
 * <h2>Khuyết tật bài kiểm này canh</h2>
 *
 * <p>Boot 3.5 để {@code FlywayAutoConfiguration} trong {@code spring-boot-autoconfigure} — artifact
 * luôn có mặt — nên khai {@code org.flywaydb:flyway-core} trần là đủ để migration chạy. Boot 4.1.1
 * <b>dời nó sang artifact riêng</b> {@code spring-boot-flyway}, và đường kéo vào là
 * {@code spring-boot-starter-flyway}.
 *
 * <p>Quay về {@code flyway-core} trần thì: biên dịch <b>sạch</b>, và trên một CSDL <b>đã có dữ
 * liệu</b> ứng dụng khởi động <b>bình thường</b> trong khi <b>⛔ không migration nào chạy</b> — bản
 * vá lược đồ mới âm thầm ⛔ không được áp. Đúng hình dạng §11.19 và luật 30.
 *
 * <h2>⛔ Vì sao bài này là JUnit TRẦN, ⛔ không kế thừa {@code IntegrationTestBase}</h2>
 *
 * <p>Bản đầu có kế thừa, và hỏi bean {@code Flyway} có tồn tại ⛔ không. Lượt kiểm chứng ngược (hạ
 * pom về {@code flyway-core} trần) cho thấy nó <b>đỏ vì lý do khác</b>: bộ kiểm chạy trên CSDL
 * <b>rỗng</b>, nên thiếu Flyway là ⛔ không có bảng nào và context chết ngay — thông điệp chẩn đoán
 * ⛔ không bao giờ in ra, người đọc log thấy một đống stack trace ⛔ không trỏ vào nguyên nhân.
 * Một bộ canh đỏ mà ⛔ không nói được vì sao thì gần như một bộ canh ⛔ không có (luật 9).
 *
 * <p>⇒ Hỏi ở tầng <b>tĩnh</b>: lớp auto-config có trên classpath ⛔ không. Đó chính là thứ đổi khi ai
 * đó sửa {@code app/pom.xml}, nó ⛔ không cần CSDL, chạy trong mili-giây, và khi đỏ thì câu trả lời
 * nằm ngay trong thông điệp. Một tên gói ghi trong chú thích ⛔ không phải một cổng kiểm (§11.19).
 */
class FlywayAutoConfigCoMatTest {

    /** ⛔ Đừng rút gọn thành import — chính <b>đường dẫn gói</b> là thứ Boot 4 đã đổi. */
    private static final String LOP_AUTO_CONFIG =
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration";

    /** Gói Boot 3.5, phải VẮNG MẶT — đối chứng để bài kiểm ⛔ không xanh nhờ một classpath bừa bộn. */
    private static final String LOP_BOOT_3 = "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";

    @Test
    @DisplayName("⭐ `FlywayAutoConfiguration` của Boot 4 có trên classpath — mất nó là migration im lặng không chạy")
    void lopAutoConfigCoMat() {
        assertThatCode(() -> Class.forName(LOP_AUTO_CONFIG))
                .as(
                        """
                        ⛔⛔ ⛔ Không nạp được `%s`.

                        Nguyên nhân gần như chắc chắn: `app/pom.xml` khai `org.flywaydb:flyway-core` trần \
                        thay vì `spring-boot-starter-flyway`. Hậu quả trên môi trường THẬT (CSDL đã có dữ \
                        liệu): ứng dụng lên xanh, health xanh, và migration mới KHÔNG được áp.

                        Khả năng còn lại: một lượt nâng Boot về sau lại dời gói này lần nữa — cũng là thứ \
                        phải biết NGAY, ⛔ không phải sau một lượt deploy.""",
                        LOP_AUTO_CONFIG)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Gói Boot 3.5 đã biến mất — chứng minh phép dò trên thật sự phân biệt hai phiên bản")
    void goiCuaBoot3DaBienMat() {
        // ⛔ Đối chứng PHẢI-KHÔNG-TÌM-THẤY (luật 9): thiếu nó thì bài trên vẫn xanh trong một thế
        //   giới nơi cả hai gói cùng tồn tại — tức là nó ⛔ không phân biệt được Boot 3 với Boot 4,
        //   và cái xanh của nó ⛔ không khẳng định điều gì.
        assertThat(thuNap(LOP_BOOT_3))
                .as("`%s` vẫn nạp được ⇒ cây phụ thuộc còn lẫn Boot 3.5, bài kiểm trên mất ý nghĩa", LOP_BOOT_3)
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static Exception thuNap(String ten) {
        try {
            Class.forName(ten);
            return null;
        } catch (ClassNotFoundException | LinkageError e) {
            return e instanceof Exception ex ? ex : new ClassNotFoundException(ten, e);
        }
    }
}
