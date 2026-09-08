package com.songnhue.core.common.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /**
     * Khoá của nút chưa có path hợp lệ — xếp sau mọi khoá thật.
     *
     * <p>{@code '~'} (126) lớn hơn mọi chữ số, nên một ký tự là đủ. Nhiều nút cùng trạng thái ấy giữ
     * nguyên thứ tự đầu vào ({@code List.sort} ổn định), tức thứ tự {@code ORDER BY path} của SQL —
     * xác định, không phụ thuộc lượt chạy.
     */
    private static final String KHOA_CHUA_CO_VI_TRI = "~";

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

    /**
     * Sắp một danh sách phẳng theo <b>thứ tự hiển thị</b>: duyệt trước–sâu, anh em ruột xếp theo
     * {@code sortOrder} rồi tới id.
     *
     * <p><b>Vì sao hàm này phải tồn tại — T26.25.</b> Mọi cây trong hệ đọc bằng {@code ORDER BY path
     * ASC, sort_order ASC}, và câu ấy <b>không bao giờ so tới {@code sort_order}</b>: path chứa id
     * của chính nút ({@link #childPath}) nên hai anh em <b>không thể</b> trùng path. Thứ tự thật là
     * thứ tự path <b>so theo chuỗi</b> — tức {@code /10/} đứng trước {@code /9/}, và một mục thêm
     * sau có thể rơi vào <i>giữa</i> menu. Hệ quả: {@code sort_order} là một núm bày ra ở màn hình
     * quản trị mà không điều khiển gì (quy tắc 15), và {@code reorder()} ghi một giá trị vô tác dụng
     * — người dùng kéo thả, API trả 204, cổng không đổi gì (quy tắc 27).
     *
     * <p><b>Bất biến phải giữ:</b> cha luôn đứng trước con. Khoá sắp của một nút là khoá của cha
     * cộng thêm đúng một đoạn, nên khoá cha là <b>tiền tố thực sự</b> của khoá con ⇒ so chuỗi tự bảo
     * đảm điều đó. Đây là thứ {@code buildMenuTree} ở public-web dựa vào: nó duyệt MỘT lượt, gặp con
     * trước cha là nhánh vỡ. Một phép sắp phẳng theo {@code sortOrder} sẽ phá đúng bất biến này.
     *
     * <p>⚠ Nút có tổ tiên <b>không nằm trong danh sách</b> (cha bị lọc, hoặc người dùng chỉ thấy một
     * cây con) vẫn sắp được: đoạn khoá của tổ tiên vắng mặt lấy {@code sortOrder} lớn nhất. Kết quả
     * vẫn là một thứ tự toàn phần và ổn định, không ném.
     *
     * @param pathOf lấy materialized path của một nút
     * @param sortOrderOf lấy {@code sortOrder}; {@code null} xếp sau mọi giá trị có thật
     */
    public static <T> List<T> sortForDisplay(
            List<T> nodes, Function<T, String> pathOf, Function<T, Integer> sortOrderOf) {

        Map<Long, String> doanTheoId = new HashMap<>();
        for (T node : nodes) {
            List<Long> chuoiId = idsAnToan(pathOf.apply(node));
            if (chuoiId.isEmpty()) {
                continue;
            }
            long id = chuoiId.get(chuoiId.size() - 1);
            doanTheoId.put(id, doanKhoa(sortOrderOf.apply(node), id));
        }
        List<T> ket = new ArrayList<>(nodes);
        ket.sort(Comparator.comparing(node -> khoaSap(pathOf.apply(node), doanTheoId)));
        return List.copyOf(ket);
    }

    /**
     * Khoá sắp của một nút: nối đoạn khoá của từng tổ tiên, từ gốc xuống chính nó.
     *
     * <p>⚠ Nút <b>chưa có path hợp lệ</b> xếp cuối thay vì làm cả phép sắp ném. Đây không phải phép
     * bỏ qua lỗi cho tiện: {@code MenuService}/{@code CategoryService}/{@code MediaService} đều lưu
     * <b>hai bước</b> — INSERT lần đầu với path giữ chỗ {@code "/"} để lấy id do CSDL sinh, rồi mới
     * ghi path thật (path chứa chính id ấy). Giữa hai bước đó là một trạng thái <b>hợp lệ theo thiết
     * kế</b>, và một lượt đọc cây trong cùng giao dịch nhìn thấy nó.
     *
     * <p>Đo được 08/09/2026: bản đầu của {@code sortForDisplay} gọi thẳng {@link #ids} và ném
     * {@code "Materialized path phải có dạng '/1/4/', đang là: /"} — {@code MenuLogoAndMapImageTest}
     * đỏ 2 bài. ⛔ Một phép sắp <b>hiển thị</b> không được là thứ làm gãy đường ghi.
     *
     * <p>Đường ghi vẫn giữ nguyên phần nghiêm khắc: {@link #childPath} và {@link #reparent} vẫn ném
     * với path sai dạng, nên một path hỏng thật vẫn bị chặn ở nơi nó sinh ra.
     */
    private static String khoaSap(String path, Map<Long, String> doanTheoId) {
        List<Long> chuoiId = idsAnToan(path);
        if (chuoiId.isEmpty()) {
            return KHOA_CHUA_CO_VI_TRI;
        }
        StringBuilder khoa = new StringBuilder();
        for (Long id : chuoiId) {
            khoa.append(doanTheoId.getOrDefault(id, doanKhoa(null, id)));
        }
        return khoa.toString();
    }

    /**
     * Dãy id, hoặc rỗng nếu path chưa hợp lệ.
     *
     * <p>⛔ Bắt {@code IllegalArgumentException} ở đây là <b>có phạm vi</b>: chỉ đường ĐỌC để hiển
     * thị mới được nới, và nới thành "xếp cuối" chứ không thành "biến mất".
     */
    private static List<Long> idsAnToan(String path) {
        try {
            return ids(path);
        } catch (IllegalArgumentException chuaCoViTriTrongCay) {
            return List.of();
        }
    }

    /**
     * Một đoạn khoá, bề rộng cố định.
     *
     * <p>⚠ Bề rộng cố định là thứ làm phép so <b>chuỗi</b> cho ra đúng thứ tự <b>số</b> — chính là
     * cái bẫy mà {@code ORDER BY path} mắc phải. {@code sortOrder} dịch về không âm để giá trị âm
     * (biểu mẫu quản trị cho nhập số âm) không mất chữ số khi đệm 0.
     */
    private static String doanKhoa(Integer sortOrder, long id) {
        // ⚠ null phải LỚN HƠN HẲN mọi giá trị có thật, không được bằng Integer.MAX_VALUE — nếu bằng
        //   thì một nút khai 2147483647 sẽ hoà với một nút chưa có sort_order và thứ tự giữa hai
        //   trạng thái RẤT khác nhau ấy do id quyết định. Bài kiểm `nullSortOrderGoesLast` bắt được
        //   đúng chỗ này, và nó bắt được vì javadoc đã nói ra một lời hứa kiểm chứng được (quy tắc 9).
        //   Dịch về không âm: khoảng giá trị thành [0, 4294967296], vẫn vừa 10 chữ số.
        long thuTu = sortOrder == null ? (long) Integer.MAX_VALUE + 1 : sortOrder;
        return "%010d:%019d/".formatted(thuTu - Integer.MIN_VALUE, id);
    }

    private static void requireWellFormed(String path) {
        if (path == null || !path.startsWith(SEPARATOR) || !path.endsWith(SEPARATOR) || path.length() < 3) {
            throw new IllegalArgumentException("Materialized path phải có dạng '/1/4/', đang là: " + path);
        }
    }
}
