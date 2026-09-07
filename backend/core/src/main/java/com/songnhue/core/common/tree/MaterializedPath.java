package com.songnhue.core.common.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Phép toán trên materialized path — pattern P2 (implement.md §2).
 *
 * <p>Path có dạng {@code /1/4/9/}: dãy id từ gốc tới chính nó, có dấu {@code /} ở cả hai đầu. Dùng
 * chung cho cây đơn vị (MOD-02/MOD-04), danh mục bài viết, thư mục media, danh mục công trình và
 * menu — cùng một bài toán, viết một lần.
 *
 * <p><b>Vì sao dấu {@code /} ở hai đầu là bắt buộc.</b> Thiếu nó thì {@code LIKE '/1/4%'} khớp nhầm
 * {@code /1/40/}, {@code /1/41/}… — đơn vị 40 bỗng nằm trong cây con của đơn vị 4. Với bộ lọc phạm
 * vi tầng 3 ({@link com.songnhue.core.common.persistence.ScopedEntity}) thì đó là <b>rò rỉ dữ liệu
 * giữa các Xí nghiệp</b>, mà không có lỗi nào báo ra. Mọi hàm ở đây giữ bất biến đó.
 *
 * <p>Lớp thuần hàm, không đụng DB — nhờ vậy kiểm thử được đầy đủ các ca biên.
 */
public final class MaterializedPath {

    public static final String SEPARATOR = "/";

    private MaterializedPath() {}

    /** Path của nút gốc (không có cha): {@code /7/}. */
    public static String rootPath(long id) {
        return SEPARATOR + id + SEPARATOR;
    }

    /**
     * Path của nút con, nối tiếp path của cha.
     *
     * @param parentPath path của cha, phải kết thúc bằng {@code /}
     */
    public static String childPath(String parentPath, long id) {
        requireWellFormed(parentPath);
        return parentPath + id + SEPARATOR;
    }

    /**
     * Độ sâu: nút gốc là 0.
     *
     * <p>{@code /1/} → 0 · {@code /1/4/} → 1 · {@code /1/4/9/} → 2.
     */
    public static int depthOf(String path) {
        return ids(path).size() - 1;
    }

    /** Dãy id từ gốc tới chính nó. {@code /1/4/9/} → {@code [1, 4, 9]}. */
    public static List<Long> ids(String path) {
        requireWellFormed(path);
        List<Long> result = new ArrayList<>();
        int from = 1;
        while (from < path.length()) {
            int to = path.indexOf(SEPARATOR, from);
            result.add(Long.parseLong(path.substring(from, to)));
            from = to + 1;
        }
        return result;
    }

    /** Id của chính nút đó — phần tử cuối của path. */
    public static long selfId(String path) {
        List<Long> ids = ids(path);
        return ids.get(ids.size() - 1);
    }

    /** Id của cha, rỗng nếu là nút gốc. */
    public static Long parentId(String path) {
        List<Long> ids = ids(path);
        return ids.size() < 2 ? null : ids.get(ids.size() - 2);
    }

    /**
     * {@code path} có nằm trong cây con của {@code ancestorPath} không — <b>tính cả chính nó</b>.
     *
     * <p>Cùng ngữ nghĩa với điều kiện SQL của bộ lọc phạm vi, để mã Java và SQL không lệch nhau.
     */
    public static boolean isSelfOrDescendant(String path, String ancestorPath) {
        requireWellFormed(path);
        requireWellFormed(ancestorPath);
        return path.startsWith(ancestorPath);
    }

    /**
     * Path mới sau khi chuyển một cây con sang chỗ khác.
     *
     * <p>⚠ <b>Cấm dùng {@code replace()}</b> cho việc này. Path hoàn toàn có thể chứa lặp lại chuỗi
     * tiền tố — {@code /1/4/1/4/9/} với tiền tố cũ {@code /1/4/} — và {@code replace} sẽ thay <i>cả
     * hai</i> chỗ, đẩy nút sang một nhánh không liên quan. Chỉ được cắt đúng phần đầu.
     *
     * @param path path hiện tại của một nút trong cây con đang chuyển
     * @param oldPrefix path cũ của nút gốc cây con
     * @param newPrefix path mới của nút gốc cây con
     */
    public static String reparent(String path, String oldPrefix, String newPrefix) {
        if (!isSelfOrDescendant(path, oldPrefix)) {
            throw new IllegalArgumentException("Path '" + path + "' không nằm trong cây con '" + oldPrefix + "'");
        }
        requireWellFormed(newPrefix);
        return newPrefix + path.substring(oldPrefix.length());
    }

    /**
     * Chuyển vào chính cây con của mình có tạo thành vòng không.
     *
     * <p>Đây là ca hỏng nặng nhất của thao tác move: nút bị cắt khỏi cây, mọi truy vấn theo path
     * không còn tìm thấy nó, mà dữ liệu vẫn nằm nguyên trong bảng.
     */
    public static boolean wouldCreateCycle(String movingPath, String newParentPath) {
        return isSelfOrDescendant(newParentPath, movingPath);
    }

    private static void requireWellFormed(String path) {
        if (path == null || !path.startsWith(SEPARATOR) || !path.endsWith(SEPARATOR) || path.length() < 3) {
            throw new IllegalArgumentException("Materialized path phải có dạng '/1/4/', đang là: " + path);
        }
    }
}
