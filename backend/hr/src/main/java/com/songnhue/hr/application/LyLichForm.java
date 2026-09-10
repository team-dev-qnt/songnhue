package com.songnhue.hr.application;

import java.time.LocalDate;

import com.songnhue.hr.domain.QualificationKind;

/**
 * Dữ liệu tạo/sửa một mục lý lịch & chuyên môn — CN-04.3.
 *
 * <p>⚠ Biểu mẫu <b>thay toàn phần</b>, cùng hợp đồng với {@link EmployeeForm}: trường {@code null}
 * ghi thành {@code null}. Vòng khứ hồi <i>GET → PUT nguyên văn</i> được canh ở
 * {@code LyLichHttpTest} — §11.19 nói một {@code PUT} thiếu trường chỉ có triệu chứng kể từ ngày dữ
 * liệu tồn tại, tức <b>sau</b> khi Công ty nhập thật.
 *
 * @param expiresOn {@code null} = ⛔ không hết hiệu lực (bằng đại học). ⛔ Đừng điền một ngày xa để
 *     né {@code null} — chuông M4.9 sẽ kêu vào năm 2099 (quy tắc 3)
 */
public record LyLichForm(
        QualificationKind kind,
        String name,
        String grade,
        String major,
        String institution,
        String certificateNo,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String note) {}
