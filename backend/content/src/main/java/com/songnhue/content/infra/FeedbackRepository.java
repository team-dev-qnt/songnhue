package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.songnhue.content.domain.Feedback;
import com.songnhue.content.domain.FeedbackStatus;

/** Truy vấn phản hồi từ cổng — CN-01.6. */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Page<Feedback> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Feedback> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(FeedbackStatus status, Pageable pageable);

    long countByStatusAndDeletedAtIsNull(FeedbackStatus status);

    long countByDeletedAtIsNull();

    /**
     * Các mục <b>đã duyệt</b> để hiện lên cổng, mới nhất trước.
     *
     * <p>⛔⛔ {@code DA_DUYET} nằm <b>trong chính câu truy vấn</b>, ⛔ không phải một tham số.
     * Dùng lại {@code findAllByStatus...} cho đường công khai là để ngỏ một ngày nào đó có người
     * truyền {@code CHO_DUYET} xuống và công bố những mục <b>chưa ai đọc</b> — đúng thứ chốt D1
     * dựng ra để chặn. Ở đây điều đó ⛔ không gõ ra được.
     *
     * <p>⚠ Số dòng do <b>nơi gọi</b> truyền qua {@code Pageable} — ⛔ không phải một
     * {@code findTop50By...} chôn con số vào tên phương thức, nơi ⛔ không ai đọc được vì sao lại
     * là 50.
     */
    @Query(
            """
            SELECT f FROM Feedback f
             WHERE f.deletedAt IS NULL
               AND f.status = com.songnhue.content.domain.FeedbackStatus.DA_DUYET
             ORDER BY f.createdAt DESC
            """)
    List<Feedback> daDuyetMoiNhat(Pageable gioiHan);

    /**
     * Số phiếu <b>đã duyệt</b> có chấm điểm — mẫu số của điểm trung bình.
     *
     * <p>⚠ Nó ⛔ <b>không</b> bằng tổng số phiếu đã duyệt: {@code rating} cho phép {@code null}
     * (xem javadoc {@link Feedback}). Trả cả hai con số ra ngoài là cách duy nhất để người đọc
     * thấy phần đã bị lọc — một điểm trung bình đứng một mình ⛔ không phân biệt được "4,2 trên 5
     * phiếu" với "4,2 trên 500" (luật 9).
     */
    long countByRatingIsNotNullAndStatusAndDeletedAtIsNull(FeedbackStatus status);

    /**
     * <b>Tổng</b> điểm của các phiếu đã duyệt có chấm — ⛔ không phải trung bình.
     *
     * <p>⛔ ⛔ Cố ý ⛔ không dùng {@code avg()}: JPQL trả {@code Double} cho {@code avg}, và quy tắc
     * 2 cấm {@code float}/{@code double} rời khỏi tầng hạ tầng ({@code CodingRuleTest} canh). Tổng
     * của một cột {@code SMALLINT} là số nguyên chính xác; phép chia làm bằng {@code BigDecimal}
     * ở service, nơi nhìn thấy được cả tử lẫn mẫu.
     *
     * @return {@code null} khi ⛔ không phiếu nào có điểm — ⛔ đừng {@code coalesce(...,0)}: 0 sao
     *     là một khẳng định về sự hài lòng, "chưa ai chấm" thì ⛔ không (quy tắc 16)
     */
    @Query(
            """
            SELECT sum(f.rating) FROM Feedback f
             WHERE f.deletedAt IS NULL
               AND f.rating IS NOT NULL
               AND f.status = com.songnhue.content.domain.FeedbackStatus.DA_DUYET
            """)
    Long tongDiemDaDuyet();
}
