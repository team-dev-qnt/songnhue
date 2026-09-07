package com.songnhue.operations.application;

import java.util.UUID;

import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Bộ lọc danh sách công trình — CN-02.6 / T17.10.
 *
 * <p>⚠ Không có tiêu chí "đơn vị của tôi": phạm vi đơn vị do tầng 3 lo, tự động, cho mọi truy vấn.
 * {@link #donViId} ở đây là <b>lọc thêm</b> bên trong phạm vi mà người dùng vốn đã thấy — cấp Công ty
 * dùng nó để xem riêng một Xí nghiệp, còn người của Xí nghiệp đó có điền hay không cũng không mở
 * rộng được gì.
 */
public record ConstructionFilter(
        String tuKhoa,
        ConstructionType loai,
        OperationalStatus trangThai,
        LifecycleState vongDoi,
        ManagementLevel capQuanLy,
        Long donViId,
        UUID cumPublicId,
        String tuyenSong,
        boolean chuaSoHoa) {

    /** Rỗng = mở màn hình lần đầu, chưa gõ gì. Đây là đường đi phổ biến nhất, nên có bài kiểm riêng. */
    public static ConstructionFilter rong() {
        return new ConstructionFilter(null, null, null, null, null, null, null, null, false);
    }

    /**
     * Từ khoá đã chuẩn hoá cho câu {@code LIKE} bỏ dấu.
     *
     * <p>Trả {@code null} khi người dùng chưa gõ gì — {@code null} là tín hiệu "không lọc", còn
     * {@code "%%"} thì bắt PostgreSQL quét toàn bảng qua hàm bỏ dấu một cách vô ích.
     */
    public String tuKhoaLike() {
        if (tuKhoa == null || tuKhoa.isBlank()) {
            return null;
        }
        return "%" + VietnameseUtils.normalizeForSearch(tuKhoa.trim()) + "%";
    }
}
