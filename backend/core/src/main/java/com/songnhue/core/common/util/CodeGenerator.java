package com.songnhue.core.common.util;

import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sinh mã nghiệp vụ chạy số theo năm: {@code BT-2026-0001}, {@code NV-2026-001}…
 *
 * <p><b>Cấm dùng {@code MAX(mã) + 1}.</b> Hai request cùng lúc sẽ đọc ra cùng một số cũ và sinh
 * trùng mã — lỗi hiếm gặp lúc thử nghiệm nhưng chắc chắn xảy ra khi nhiều người cùng nhập liệu, và
 * mã trùng thì đối chiếu hồ sơ giấy là hỏng.
 *
 * <p>Ở đây dùng {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING}: PostgreSQL khoá đúng một dòng
 * và trả về giá trị mới trong cùng một câu lệnh, nên không có khe hở giữa đọc và ghi.
 *
 * <p>Giao dịch <b>riêng</b> là cố ý: bộ đếm phải nhích ngay cả khi giao dịch nghiệp vụ bao ngoài bị
 * rollback. Đổi lại có thể "nhảy số" (BT-2026-0007 rồi tới 0009) — chấp nhận được, vì mã trùng mới
 * là thứ không chấp nhận được. Số nhảy không có nghĩa là mất dữ liệu.
 *
 * <p>⚠⚠ Mở giao dịch riêng bằng {@link TransactionTemplate} chứ <b>không</b> bằng
 * {@code @Transactional(propagation = REQUIRES_NEW)}. Bản đầu dùng chú thích, và nạp chồng
 * {@link #next(String, int)} gọi sang bản ba tham số bằng {@code this} — không qua proxy Spring, nên
 * giao dịch riêng <b>không</b> được mở và bộ đếm rơi vào chính giao dịch nghiệp vụ mà nó phải đứng
 * ngoài. Triệu chứng đúng bằng thứ cả lớp này sinh ra để chống: lượt ghi hỏng thì bộ đếm lùi theo, và
 * bản ghi kế tiếp mang <b>lại đúng mã đó</b>. Mở bằng tay thì hai cửa vào cùng một hành vi, bất kể ai
 * gọi từ đâu.
 */
@Service
public class CodeGenerator {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate giaoDichRieng;

    public CodeGenerator(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        this.giaoDichRieng = new TransactionTemplate(transactionManager);
        this.giaoDichRieng.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param seqType loại mã, VD {@code BT}, {@code NV} — cũng chính là tiền tố
     * @param year năm đưa vào mã
     * @param padding số chữ số của phần chạy số, VD 4 → {@code 0001}
     */
    public String generate(String seqType, int year, int padding) {
        Long value = giaoDichRieng.execute(status -> jdbcClient
                .sql(
                        """
                        INSERT INTO code_sequences (seq_type, seq_year, current_value, updated_at)
                        VALUES (:type, :year, 1, now())
                        ON CONFLICT (seq_type, seq_year)
                        DO UPDATE SET current_value = code_sequences.current_value + 1,
                                      updated_at = now()
                        RETURNING current_value
                        """)
                .param("type", seqType)
                .param("year", year)
                .query(Long.class)
                .single());

        return "%s-%d-%s".formatted(seqType, year, padLeft(value, padding));
    }

    /** Sinh mã cho năm hiện tại theo lịch Việt Nam. */
    public String next(String seqType, int padding) {
        return generate(seqType, LocalDate.now(DateTimeUtils.ZONE_VN).getYear(), padding);
    }

    /**
     * Số vượt quá số chữ số dự kiến thì KHÔNG cắt bớt — mã dài hơn còn hơn mã trùng. VD bản ghi thứ
     * 10.000 của năm ra {@code BT-2026-10000}, vẫn duy nhất.
     */
    private static String padLeft(long value, int padding) {
        String text = Long.toString(value);
        return text.length() >= padding ? text : "0".repeat(padding - text.length()) + text;
    }
}
