package com.songnhue.core.spi;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tình hình vận hành <b>hiện hành</b> của một công trình — bản rút gọn cho module khác đọc (T34.4).
 *
 * <h2>⛔ Vì sao có {@link #mauTinhHinh}</h2>
 *
 * <p>Mã tình hình vận hành là <b>danh mục có CRUD</b> do Công ty tự quản (chốt G4, quy tắc 16 của
 * {@code CLAUDE.md}): thêm một mã mới ⛔ không được đòi deploy. Nếu cổng này chỉ trả mã thì mọi nơi
 * hiển thị phải giữ một bảng ánh xạ mã → màu thứ hai, và ngày Công ty thêm mã thì bảng ấy im lặng
 * rơi về màu mặc định. Trả màu ra là cách ⛔ không có bảng ánh xạ thứ hai nào tồn tại.
 *
 * @param maTinhHinh mã do Công ty đặt trong danh mục
 * @param thamSo giá trị tham số đi kèm ({@code null} khi mã ấy ⛔ không có tham số). ⚠ Đây là
 *     {@code BigDecimal}, và nơi nào phát nó ra dây thì phải kèm
 *     {@code @JsonFormat(shape = STRING)} — {@code JSON.parse("2.30")} ở trình duyệt cho ra
 *     {@code 2.3}, mất đúng thang đo mà quy tắc 2 sinh ra để giữ.
 * @param hieuLucTu mốc bản ghi <b>đang có hiệu lực</b>, ⛔ không phải mốc ghi vào CSDL — người trực
 *     được phép ghi lùi ngày cho một quan sát buổi sáng
 */
public record TinhHinhVanHanhRef(
        String maTinhHinh,
        String tenTinhHinh,
        String mauTinhHinh,
        BigDecimal thamSo,
        String donViThamSo,
        Instant hieuLucTu) {}
