package com.songnhue.hr.application;

import java.util.List;
import java.util.UUID;

import com.songnhue.hr.domain.Gender;

/**
 * Bộ lọc của danh bạ nội bộ — CN-04.6.
 *
 * <p>Ba ô lọc đều <b>multi-select</b> theo đặc tả. Danh sách <b>rỗng</b> nghĩa là <i>⛔ không lọc
 * theo tiêu chí này</i>, ⛔ không phải <i>⛔ không khớp gì cả</i> — hai nghĩa ấy mà lẫn nhau thì
 * người dùng bỏ hết dấu tick rồi thấy danh bạ **trống rỗng** thay vì thấy tất cả.
 *
 * @param tuKhoa tìm theo họ tên · mã NV · chức danh, <b>bỏ dấu hai chiều</b> ({@code sn_khong_dau})
 * @param donViIds {@code publicId} của đơn vị — khớp <b>cả nhánh con</b>, xem {@code DanhBaRepository}
 * @param chucVuIds {@code publicId} của chức vụ
 * @param gioiTinh lọc theo giới tính
 */
public record DanhBaLoc(String tuKhoa, List<UUID> donViIds, List<UUID> chucVuIds, List<Gender> gioiTinh) {

    public DanhBaLoc {
        donViIds = donViIds == null ? List.of() : List.copyOf(donViIds);
        chucVuIds = chucVuIds == null ? List.of() : List.copyOf(chucVuIds);
        gioiTinh = gioiTinh == null ? List.of() : List.copyOf(gioiTinh);
        tuKhoa = tuKhoa == null || tuKhoa.isBlank() ? null : tuKhoa.trim();
    }

    public static DanhBaLoc rong() {
        return new DanhBaLoc(null, List.of(), List.of(), List.of());
    }
}
