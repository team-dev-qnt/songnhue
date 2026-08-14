package com.songnhue.core.common.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Path là thứ bộ lọc phạm vi tầng 3 dựa vào — sai ở đây là rò rỉ dữ liệu giữa các Xí nghiệp, không
 * phải lỗi hiển thị. Vì vậy kiểm cả những ca trông có vẻ vụn vặt.
 */
class MaterializedPathTest {

    @Nested
    @DisplayName("Dựng path")
    class Building {

        @Test
        void rootAndChild() {
            assertThat(MaterializedPath.rootPath(1)).isEqualTo("/1/");
            assertThat(MaterializedPath.childPath("/1/", 4)).isEqualTo("/1/4/");
            assertThat(MaterializedPath.childPath("/1/4/", 9)).isEqualTo("/1/4/9/");
        }

        @Test
        @DisplayName("Nút gốc có depth 0")
        void depth() {
            assertThat(MaterializedPath.depthOf("/1/")).isZero();
            assertThat(MaterializedPath.depthOf("/1/4/")).isEqualTo(1);
            assertThat(MaterializedPath.depthOf("/1/4/9/")).isEqualTo(2);
        }

        @Test
        void idsSelfAndParent() {
            assertThat(MaterializedPath.ids("/1/4/9/")).containsExactly(1L, 4L, 9L);
            assertThat(MaterializedPath.selfId("/1/4/9/")).isEqualTo(9);
            assertThat(MaterializedPath.parentId("/1/4/9/")).isEqualTo(4);
            assertThat(MaterializedPath.parentId("/1/")).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1/4/", "/1/4", "1/4", "/", "//"})
        @DisplayName("Path sai định dạng bị từ chối ngay, không âm thầm dựng path hỏng")
        void rejectsMalformed(String bad) {
            assertThatThrownBy(() -> MaterializedPath.depthOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Quan hệ cha con")
    class Ancestry {

        @Test
        void selfCounts() {
            assertThat(MaterializedPath.isSelfOrDescendant("/1/4/", "/1/4/")).isTrue();
            assertThat(MaterializedPath.isSelfOrDescendant("/1/4/9/", "/1/4/")).isTrue();
            assertThat(MaterializedPath.isSelfOrDescendant("/1/5/", "/1/4/")).isFalse();
        }

        @Test
        @DisplayName("⚠ Đơn vị 40 KHÔNG nằm trong cây con của đơn vị 4")
        void prefixCollision() {
            // Đây là lý do path phải có dấu '/' ở cuối. Thiếu nó thì LIKE '/1/4%' khớp cả '/1/40/',
            // và người quản lý đơn vị 4 lặng lẽ xem được dữ liệu của đơn vị 40.
            assertThat(MaterializedPath.isSelfOrDescendant("/1/40/", "/1/4/")).isFalse();
            assertThat(MaterializedPath.isSelfOrDescendant("/1/4/", "/1/40/")).isFalse();
        }
    }

    @Nested
    @DisplayName("Chuyển cây con")
    class Reparenting {

        @Test
        void movesWholeSubtree() {
            assertThat(MaterializedPath.reparent("/1/4/", "/1/4/", "/1/7/4/")).isEqualTo("/1/7/4/");
            assertThat(MaterializedPath.reparent("/1/4/9/", "/1/4/", "/1/7/4/")).isEqualTo("/1/7/4/9/");
            assertThat(MaterializedPath.reparent("/1/4/9/12/", "/1/4/", "/1/7/4/"))
                    .isEqualTo("/1/7/4/9/12/");
        }

        @Test
        @DisplayName("⚠ Tiền tố lặp lại chỉ được thay ở ĐẦU path")
        void repeatedPrefixIsReplacedOnlyOnce() {
            // Nếu cài bằng replace() thì kết quả sẽ là "/9/9/1/4/9/" — nút bị ném sang một nhánh
            // hoàn toàn khác mà không có lỗi nào. Đây là bug kinh điển của materialized path.
            assertThat(MaterializedPath.reparent("/1/4/1/4/9/", "/1/4/", "/9/")).isEqualTo("/9/1/4/9/");
        }

        @Test
        void rejectsPathOutsideTheSubtree() {
            assertThatThrownBy(() -> MaterializedPath.reparent("/1/5/", "/1/4/", "/1/7/"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Chống tạo vòng")
    class CycleGuard {

        @Test
        @DisplayName("Chuyển vào chính mình hoặc vào cấp dưới của mình đều là vòng")
        void detectsCycle() {
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/4/")).isTrue();
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/4/9/")).isTrue();
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/4/9/12/")).isTrue();
        }

        @Test
        void allowsLegitimateMove() {
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/7/")).isFalse();
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/")).isFalse();
        }

        @Test
        @DisplayName("Đơn vị 40 không phải cấp dưới của 4 — không chặn nhầm thao tác hợp lệ")
        void doesNotBlockOnPrefixCollision() {
            assertThat(MaterializedPath.wouldCreateCycle("/1/4/", "/1/40/")).isFalse();
        }
    }
}
