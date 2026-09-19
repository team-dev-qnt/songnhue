package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một nhóm máy kèm thông tin trạm — dòng đọc của Bảng 2, đầu vào của {@link TinhBaoCaoNhanh}.
 *
 * @param nguonTuoiHuongTieu {@code constructions.basin_note} — chốt F3: trường VĂN BẢN tự do, và nó
 *     đúng là cột *"Nguồn tưới, hướng tiêu"* của mẫu. ⛔ Thêm cột thứ hai cho cùng một sự thật.
 * @param thuTu thứ tự dòng trong tệp nhập — giữ đúng thứ tự Công ty lập danh mục
 */
public record DongNhomMay(
        Long nhomId,
        UUID nhomPublicId,
        Long constructionId,
        UUID constructionPublicId,
        String maCongTrinh,
        String tenCongTrinh,
        String nguonTuoiHuongTieu,
        Long orgUnitId,
        int soMay,
        BigDecimal qM3h,
        int thuTu) {}
