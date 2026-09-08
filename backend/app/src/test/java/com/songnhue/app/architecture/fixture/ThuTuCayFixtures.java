package com.songnhue.app.architecture.fixture;

import java.util.List;

import com.songnhue.core.common.tree.MaterializedPath;

/**
 * Mã cố ý sai cho {@code ThuTuCayRuleSelfCheckTest} — bốn cách phá bảo đảm thứ tự hiển thị của
 * T26.25, cộng một bản làm đúng.
 *
 * <p>⛔ <b>Cố ý không kế thừa {@code JpaRepository}.</b> Gói fixture nằm dưới {@code com.songnhue} nên
 * nó ở trong tầm quét của Spring; một interface kế thừa {@code JpaRepository} ở đây sẽ được đăng ký
 * thành bean thật và làm hỏng ngữ cảnh của mọi bài kiểm tích hợp. Bộ luật vì thế neo vào <b>tên
 * phương thức</b> chứ không vào lớp cha — nhờ vậy fixture kiểm chứng được luật mà không phải là một
 * repository thật.
 *
 * <p>⚠ Đây cũng là lý do luật {@code khaiCauThoThiPhaiKhaiCuaHienThi} bắt được cả cây tương lai:
 * cây thứ năm không cần khai báo gì thêm, chỉ cần đặt tên câu truy vấn theo đúng quy ước.
 */
public final class ThuTuCayFixtures {

    private ThuTuCayFixtures() {}

    /** Một nút cây tối giản — đủ để {@link MaterializedPath#sortForDisplay} làm việc. */
    public record NutGia(String path, Integer sortOrder) {}

    /**
     * ⛔ Vi phạm 1: cái tên hứa <i>"anh em đúng thứ tự"</i> mà SQL không làm được.
     *
     * <p>Đây đúng chữ ký đã sống trong kho từ WS-6 tới 08/09/2026.
     */
    public interface TenHuaHaoRepo {
        List<NutGia> findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();
    }

    /**
     * ⛔ Vi phạm 2: cửa hiển thị <b>rỗng ruột</b> — tên đúng, không gọi phép sắp.
     *
     * <p>Ca nguy hiểm nhất trong bốn ca: nó qua được mọi phép canh văn bản, mọi lượt đọc chéo, và
     * tái lập chính xác khuyết tật gốc. Chỉ tầng bytecode phân biệt được nó với bản đúng.
     */
    public interface CuaRongRuotRepo {
        List<NutGia> findAllByDeletedAtIsNullOrderByPathAsc();

        default List<NutGia> findAllForDisplay() {
            return findAllByDeletedAtIsNullOrderByPathAsc();
        }
    }

    /** ⛔ Vi phạm 3: khai câu thô mà không có cửa hiển thị nào — không có đường đọc đúng. */
    public interface ThieuCuaRepo {
        List<NutGia> findAllByDeletedAtIsNullOrderByPathAsc();
    }

    /** ⛔ Vi phạm 4: tầng service gọi vượt mặt câu thô, bỏ qua cửa. */
    public static final class GoiVuotMatService {

        private final CuaRongRuotRepo repo;

        public GoiVuotMatService(CuaRongRuotRepo repo) {
            this.repo = repo;
        }

        public List<NutGia> cay() {
            return repo.findAllByDeletedAtIsNullOrderByPathAsc();
        }
    }

    /**
     * ✅ Bản làm đúng — <b>bắt buộc phải có</b>.
     *
     * <p>Không có nó thì bốn bài kiểm chứng ngược xanh cả khi luật viết sai theo kiểu "từ chối tất
     * cả". Đây là bài học §10.62: một bài kiểm chứng ngược có thể xanh theo đúng cách nó đang sai.
     */
    public interface DungChuanRepo {
        List<NutGia> findAllByDeletedAtIsNullOrderByPathAsc();

        default List<NutGia> findAllForDisplay() {
            return MaterializedPath.sortForDisplay(
                    findAllByDeletedAtIsNullOrderByPathAsc(), NutGia::path, NutGia::sortOrder);
        }
    }
}
