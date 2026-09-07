package com.songnhue.operations.domain;

/**
 * Kết quả nghiệm thu công việc — CN-02.2.
 *
 * <p>Tách khỏi {@code status}: trạng thái xử lý nói công việc đã <i>làm xong</i> chưa, kết quả
 * nghiệm thu nói làm xong có <i>đạt</i> không. Gộp làm một thì không diễn đạt được tình huống hay
 * gặp nhất — đã làm xong, đã đóng bản ghi, nhưng nghiệm thu chưa đạt và phải mở lại
 * ({@code REOPEN}).
 */
public enum AcceptanceResult {
    DAT,
    CHUA_DAT,

    /** Đã bàn giao nhưng còn theo dõi một thời gian trước khi kết luận. */
    DANG_THEO_DOI
}
