package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.application.workflow.WorkflowEngine;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Canh chính cái nền mà mọi luật kiến trúc đứng lên.
 *
 * <p>Một luật ArchUnit chạy trên tập lớp <b>rỗng</b> thì xanh — và xanh mãi mãi. Tập lớp có thể rỗng
 * đi vì những lý do rất tầm thường: đổi tên gói gốc, thêm nhầm {@code DoNotIncludeJars} ({@code core}
 * tới {@code app} dưới dạng jar phụ thuộc), hay bỏ một module ra khỏi phần phụ thuộc của {@code app}.
 * Không cái nào trong số đó làm build đỏ.
 *
 * <p>Đây là bài kiểm rẻ nhất trong cả gói và cũng là bài duy nhất phát hiện được chuyện "hàng rào vẫn
 * đứng đó nhưng không bao quanh gì cả".
 */
class ImportedScopeTest {

    /**
     * Ba lớp đại diện cho ba tầng khác nhau của {@code core} — module <b>duy nhất</b> có mã thật ở
     * Phase 0, và cũng là module tới đây dưới dạng jar. Bốn module còn lại hiện chỉ có
     * {@code package-info}; ngày chúng có lớp đầu tiên thì {@code ProductionClasses.ALL} tự phủ tới,
     * không phải sửa gì ở đây.
     */
    private static final List<Class<?>> WITNESSES =
            List.of(ScopedEntity.class, WorkflowEngine.class, com.songnhue.core.api.org.OrgUnitController.class);

    @Test
    @DisplayName("Lớp của module core (đến từ jar) thật sự nằm trong tập đem soi")
    void coreClassesAreImported() {
        for (Class<?> witness : WITNESSES) {
            assertThat(ProductionClasses.ALL)
                    .as(
                            "không nạp được %s — mọi luật kiến trúc đang chạy trên tập rỗng hoặc thiếu jar `core`",
                            witness.getName())
                    .anyMatch(javaClass -> javaClass.getName().equals(witness.getName()));
        }
    }

    @Test
    @DisplayName("Số lớp nạp được không sụt bất thường")
    void importIsNotSuspiciouslySmall() {
        // Ngưỡng đặt thấp có chủ ý: đây là bẫy chống "tập rỗng", không phải chỉ số đo quy mô mã nguồn.
        // Đặt sát con số hiện tại thì mỗi lần thêm lớp lại phải sửa test — kiểu bảo trì làm người ta
        // ghét test rồi cuối cùng xoá nó đi.
        assertThat(ProductionClasses.ALL.size())
                .as(
                        "chỉ nạp được %d lớp — nhiều khả năng phần phụ thuộc hoặc ImportOption đã đổi",
                        ProductionClasses.ALL.size())
                .isGreaterThan(100);
    }
}
