package com.songnhue.core.application.audit;

import java.time.Instant;

/**
 * Một mắt xích gãy trong chuỗi hash của nhật ký kiểm toán (T6.12).
 *
 * <p>Khai ở tầng application chứ không ở repository: kết quả kiểm tra chuỗi là thứ đi thẳng ra màn
 * hình quản trị, nên để record này nằm trong {@code infra} là bắt tầng {@code api} import
 * {@code infra} chỉ để đọc ba trường — đúng vi phạm mà luật ArchUnit
 * {@code api_khong_duoc_goi_thang_repository} bắt được ngay lần chạy đầu.
 *
 * @param seq số thứ tự bản ghi trong chuỗi — chỗ chuỗi đứt
 * @param id khoá chính của dòng {@code audit_logs} tương ứng
 * @param occurredAt thời điểm bản ghi đó được tạo
 * @param reason vì sao coi là gãy: hash không khớp, hay {@code prev_hash} không nối được
 */
public record ChainBreak(long seq, long id, Instant occurredAt, String reason) {}
