package com.songnhue.core.common.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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

    /**
     * Thứ tự hiển thị — T26.25. Đây là chỗ {@code sort_order} lần đầu có tác dụng.
     *
     * <p>Bài kiểm dùng một record tối giản thay vì entity JPA: phép sắp là hàm thuần, và kiểm nó
     * qua entity thì mỗi lỗi đỏ lại phải hỏi "lỗi ở phép sắp hay ở entity".
     */
    @Nested
    @DisplayName("Thứ tự hiển thị")
    class DisplayOrder {

        private record Nut(String path, Integer sortOrder, String ten) {}

        private List<String> sap(List<Nut> dau) {
            return MaterializedPath.sortForDisplay(dau, Nut::path, Nut::sortOrder).stream()
                    .map(Nut::ten)
                    .toList();
        }

        @Test
        @DisplayName("Anh em ruột theo sort_order, KHÔNG theo id")
        void siblingsFollowSortOrder() {
            List<Nut> dau = List.of(new Nut("/1/", 30, "A"), new Nut("/2/", 20, "B"), new Nut("/3/", 10, "C"));

            assertThat(sap(dau)).containsExactly("C", "B", "A");
        }

        @Test
        @DisplayName("⭐⭐ id 9 và 10: ORDER BY path so theo CHUỖI nên /10/ đứng trước /9/")
        void beatsLexicographicIdOrder() {
            // Đây là ca mà bài kiểm tích hợp không dựng được — id do CSDL cấp. Nó cũng là lý do câu
            // "thứ tự thật là thứ tự tạo bản ghi" trong sổ nợ là một mô tả THIẾU: thứ tự thật là
            // thứ tự id SO THEO CHUỖI, nên một mục thêm sau có thể rơi vào GIỮA menu.
            List<Nut> dau = List.of(new Nut("/9/", 20, "chin"), new Nut("/10/", 10, "muoi"));

            assertThat(MaterializedPath.sortForDisplay(dau, Nut::path, Nut::sortOrder).stream()
                            .map(Nut::ten)
                            .toList())
                    .containsExactly("muoi", "chin");

            // Đối chứng: phép so path trần — thứ hệ đã chạy suốt tới T26.25 — cho ra thứ tự NGƯỢC
            // với sort_order. Không có khẳng định này thì bài trên xanh cả khi phép sắp chỉ tình cờ
            // trùng thứ tự đầu vào.
            assertThat(dau.stream().map(Nut::path).sorted().toList()).containsExactly("/10/", "/9/");
        }

        @Test
        @DisplayName("⛔ Cha LUÔN đứng trước con, kể cả khi sort_order của cha lớn hơn")
        void parentAlwaysBeforeChild() {
            // buildMenuTree ở public-web duyệt MỘT lượt: gặp con trước cha là nhánh vỡ, không lỗi,
            // không log. Một phép sắp phẳng theo sort_order sẽ phá đúng bất biến này.
            List<Nut> dau = List.of(new Nut("/1/", 900, "cha"), new Nut("/1/2/", 0, "con"));

            assertThat(sap(dau)).containsExactly("cha", "con");
        }

        @Test
        @DisplayName("Cây hai cấp: mỗi nhánh trọn vẹn rồi mới sang nhánh sau")
        void depthFirstWholeBranch() {
            List<Nut> dau = List.of(
                    new Nut("/1/", 20, "B"),
                    new Nut("/1/5/", 10, "B.2"),
                    new Nut("/1/4/", 5, "B.1"),
                    new Nut("/2/", 10, "A"),
                    new Nut("/2/9/", 0, "A.1"));

            assertThat(sap(dau)).containsExactly("A", "A.1", "B", "B.1", "B.2");
        }

        @Test
        @DisplayName("sort_order trùng nhau thì id quyết định — thứ tự phải TOÀN PHẦN, không nhập nhằng")
        void tiesBrokenByIdSoOrderIsTotal() {
            List<Nut> dau =
                    List.of(new Nut("/7/", 10, "bay"), new Nut("/3/", 10, "ba"), new Nut("/11/", 10, "muoi-mot"));

            assertThat(sap(dau)).containsExactly("ba", "bay", "muoi-mot");
        }

        @Test
        @DisplayName("sort_order âm vẫn đúng — biểu mẫu quản trị cho nhập số âm")
        void negativeSortOrder() {
            List<Nut> dau = List.of(new Nut("/1/", 0, "khong"), new Nut("/2/", -5, "am"));

            assertThat(sap(dau)).containsExactly("am", "khong");
        }

        @Test
        @DisplayName("sort_order null xếp sau mọi giá trị có thật, và KHÔNG ném")
        void nullSortOrderGoesLast() {
            List<Nut> dau = List.of(
                    new Nut("/1/", null, "trong"), new Nut("/2/", 2147483647, "lon-nhat"), new Nut("/3/", 0, "khong"));

            assertThat(sap(dau)).containsExactly("khong", "lon-nhat", "trong");
        }

        @Test
        @DisplayName("Nút có cha ngoài danh sách vẫn sắp được — người dùng chỉ thấy một cây con")
        void orphanStillSorts() {
            // Cha /1/ không nằm trong danh sách (bị lọc quyền, hoặc đang tắt).
            List<Nut> dau = List.of(new Nut("/1/9/", 20, "sau"), new Nut("/1/4/", 10, "truoc"));

            assertThat(sap(dau)).containsExactly("truoc", "sau");
        }

        @Test
        @DisplayName("⛔ Path giữ chỗ '/' của lượt INSERT đầu xếp CUỐI, không làm cả phép sắp ném")
        void placeholderPathSortsLastInsteadOfThrowing() {
            // MenuService/CategoryService/MediaService lưu HAI bước: INSERT lần đầu với path "/" để
            // lấy id do CSDL sinh (path chứa chính id ấy), rồi mới ghi path thật. Giữa hai bước là
            // một trạng thái hợp lệ THEO THIẾT KẾ mà lượt đọc cây trong cùng giao dịch nhìn thấy.
            //
            // Bản đầu của sortForDisplay gọi thẳng ids() và ném ở đúng đây — MenuLogoAndMapImageTest
            // đỏ 2 bài. Một phép sắp HIỂN THỊ không được là thứ làm gãy đường GHI.
            List<Nut> dau =
                    List.of(new Nut("/", 0, "vua-insert"), new Nut("/5/", 99, "that"), new Nut("/2/", 10, "that-2"));

            assertThat(sap(dau)).containsExactly("that-2", "that", "vua-insert");
        }

        @Test
        @DisplayName("Danh sách rỗng và một phần tử — hai ca biên rẻ nhất")
        void edgeCases() {
            assertThat(MaterializedPath.sortForDisplay(List.<Nut>of(), Nut::path, Nut::sortOrder))
                    .isEmpty();
            assertThat(sap(List.of(new Nut("/1/", 0, "mot")))).containsExactly("mot");
        }
    }
}
