package com.songnhue.core.common.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreeBuilderTest {

    private record Row(long id, Long parentId, String name, int sortOrder) {}

    private record Node(String name, List<Node> children) {}

    private static List<Node> build(List<Row> rows) {
        return TreeBuilder.build(
                rows,
                Row::id,
                Row::parentId,
                Comparator.comparingInt(Row::sortOrder).thenComparing(Row::name),
                (row, children) -> new Node(row.name(), children));
    }

    @Test
    @DisplayName("Ghép đúng cha con qua nhiều cấp")
    void buildsNestedTree() {
        List<Node> tree = build(List.of(
                new Row(1, null, "Công ty", 0),
                new Row(2, 1L, "Phòng Kỹ thuật", 0),
                new Row(3, 1L, "XN Thượng", 1),
                new Row(4, 3L, "Tổ vận hành", 0)));

        assertThat(tree).hasSize(1);
        Node root = tree.get(0);
        assertThat(root.name()).isEqualTo("Công ty");
        assertThat(root.children()).extracting(Node::name).containsExactly("Phòng Kỹ thuật", "XN Thượng");
        assertThat(root.children().get(1).children()).extracting(Node::name).containsExactly("Tổ vận hành");
    }

    @Test
    @DisplayName("Anh em ruột xếp theo sort_order, không theo thứ tự trong danh sách đầu vào")
    void sortsSiblings() {
        List<Node> tree =
                build(List.of(new Row(1, null, "Công ty", 0), new Row(2, 1L, "Z cuối", 0), new Row(3, 1L, "A đầu", 5)));

        assertThat(tree.get(0).children()).extracting(Node::name).containsExactly("Z cuối", "A đầu");
    }

    @Test
    @DisplayName("Nút có cha nằm ngoài danh sách được coi là gốc — không bị mất khỏi cây")
    void treatsOrphanAsRoot() {
        // Xảy ra thật khi người dùng chỉ được xem cây con của đơn vị mình: nút trên cùng họ thấy
        // luôn có cha nằm ngoài tầm nhìn. Bỏ qua những nút này thì API trả về cây rỗng.
        List<Node> tree = build(List.of(new Row(3, 1L, "XN Thượng", 0), new Row(4, 3L, "Tổ vận hành", 0)));

        assertThat(tree).extracting(Node::name).containsExactly("XN Thượng");
        assertThat(tree.get(0).children()).extracting(Node::name).containsExactly("Tổ vận hành");
    }

    @Test
    void emptyInputGivesEmptyTree() {
        assertThat(build(List.of())).isEmpty();
    }
}
