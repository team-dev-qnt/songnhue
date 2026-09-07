package com.songnhue.core.common.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Dựng cây lồng nhau từ danh sách phẳng — pattern P2 (implement.md §2).
 *
 * <p>Dùng chung cho sơ đồ tổ chức, danh mục bài viết, thư mục media, menu. Mỗi chỗ tự khai báo DTO
 * của mình; lớp này chỉ lo phần ghép cha–con và thứ tự.
 *
 * <p><b>Một lượt qua danh sách, không truy vấn đệ quy.</b> Cây đơn vị chỉ vài chục nút nên tải hết
 * rồi ghép trong bộ nhớ là rẻ nhất; gọi DB theo từng cấp là bài toán N+1 kinh điển.
 */
public final class TreeBuilder {

    private TreeBuilder() {}

    /**
     * @param rows danh sách phẳng, <b>phải chứa đủ cả cây</b> — nút nào có cha không nằm trong danh
     *     sách sẽ được coi là nút gốc, xem {@link #build} để hiểu vì sao đó là lựa chọn đúng
     * @param idOf lấy id của một hàng
     * @param parentIdOf lấy id cha, {@code null} nếu là gốc
     * @param sortKeyOf khoá sắp xếp giữa các anh em ruột
     * @param toNode dựng DTO từ (hàng, danh sách con đã dựng xong)
     */
    public static <R, N> List<N> build(
            List<R> rows,
            Function<R, Long> idOf,
            Function<R, Long> parentIdOf,
            Comparator<R> sortKeyOf,
            BiFunction<R, List<N>, N> toNode) {

        List<R> sorted = new ArrayList<>(rows);
        sorted.sort(sortKeyOf);

        Map<Long, List<R>> childrenByParent = new LinkedHashMap<>();
        Map<Long, R> byId = new LinkedHashMap<>();
        for (R row : sorted) {
            byId.put(idOf.apply(row), row);
        }
        List<R> roots = new ArrayList<>();
        for (R row : sorted) {
            Long parentId = parentIdOf.apply(row);
            // Cha không có trong danh sách → coi là gốc. Đây KHÔNG phải cách xử lý qua loa: khi người
            // dùng chỉ được xem cây con của đơn vị mình, nút trên cùng của phần họ thấy luôn có cha
            // nằm ngoài tầm nhìn. Bỏ qua những nút này thì cây trả về rỗng.
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(row);
            } else {
                childrenByParent
                        .computeIfAbsent(parentId, k -> new ArrayList<>())
                        .add(row);
            }
        }

        return roots.stream()
                .map(root -> toNodeRecursive(root, childrenByParent, idOf, toNode))
                .toList();
    }

    private static <R, N> N toNodeRecursive(
            R row, Map<Long, List<R>> childrenByParent, Function<R, Long> idOf, BiFunction<R, List<N>, N> toNode) {

        List<R> children = childrenByParent.getOrDefault(idOf.apply(row), List.of());
        List<N> childNodes = children.stream()
                .map(child -> toNodeRecursive(child, childrenByParent, idOf, toNode))
                .toList();
        return toNode.apply(row, childNodes);
    }
}
