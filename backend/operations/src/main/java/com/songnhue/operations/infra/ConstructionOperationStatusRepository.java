package com.songnhue.operations.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.ConstructionOperationStatus;

/**
 * Tình hình vận hành công trình — CN-02.11.
 *
 * <h2>Hai loại câu, cố ý khác nhau, và khác biệt đó là phần dễ hỏng nhất của lớp này</h2>
 *
 * <p>{@link ConstructionOperationStatus} là {@code ScopedEntity}, nên câu JPQL/derived <b>bị</b> bộ
 * lọc phạm vi áp vào còn câu native thì <b>không</b>. Ở đây cả hai loại đều cần:
 *
 * <ul>
 *   <li>{@link #lichSu} — <b>có lọc</b>. Trả lời "cho tôi xem những dòng tôi được xem".
 *   <li>{@link #banGhiMoiNhat}, {@link #findConstructionIdsWithLatestCode} — <b>không lọc</b>. Chúng
 *       nuôi phép tính trạng thái công trình, thứ phải có một giá trị duy nhất bất kể ai đang gọi.
 * </ul>
 *
 * <p>Nhầm chiều nào cũng hỏng, và cả hai kiểu hỏng đều không có triệu chứng: lọc nhầm vào phép tính
 * thì trạng thái âm thầm rơi về "Bình thường" ngay sau một lượt bàn giao công trình; bỏ lọc ở lượt
 * đọc thì Xí nghiệp này đọc được nhật ký của Xí nghiệp kia.
 */
@Repository
public interface ConstructionOperationStatusRepository extends JpaRepository<ConstructionOperationStatus, Long> {

    boolean existsByOperationCodeId(Long operationCodeId);

    /**
     * Lịch sử cho người dùng đọc — <b>có</b> lọc phạm vi (câu JPQL).
     *
     * <p>⚠ {@code JOIN FETCH} không phải để tối ưu. {@code operationCode} là {@code @ManyToOne} LAZY,
     * còn việc dựng DTO xảy ra ở controller — tức là <b>ngoài giao dịch</b>. Không nạp sẵn thì mỗi
     * dòng ném {@code LazyInitializationException}, và người dùng nhận {@code SYS-0001} chứ không
     * phải một danh sách rỗng: lỗi có hiện ra, nhưng hiện dưới dạng "lỗi hệ thống" nên không ai đoán
     * được nguyên nhân từ giao diện.
     *
     * <p>{@code countQuery} phải khai riêng: Spring Data không tự suy được câu đếm từ một câu có
     * {@code JOIN FETCH}.
     */
    @Query(
            value =
                    """
                    SELECT s FROM ConstructionOperationStatus s
                    JOIN FETCH s.operationCode
                    WHERE s.deletedAt IS NULL AND s.constructionId = :congTrinhId
                    """,
            countQuery =
                    """
                    SELECT count(s) FROM ConstructionOperationStatus s
                    WHERE s.deletedAt IS NULL AND s.constructionId = :congTrinhId
                    """)
    Page<ConstructionOperationStatus> lichSu(@Param("congTrinhId") Long congTrinhId, Pageable pageable);

    /**
     * Bản ghi mới nhất — nguồn của mắt xích 4 trong chuỗi suy ra trạng thái.
     *
     * <p>⚠ <b>Câu native, và đó là chủ ý</b> — cùng lý do với
     * {@code MaintenanceLogRepository.demBanGhiDangMo}. Bản trước là câu derived
     * ({@code findFirstByConstructionIdOrderByEffectiveAtDesc}) nên bộ lọc phạm vi áp vào: một người
     * ở đơn vị khác mở màn hình công trình vừa bàn giao sẽ tra ra rỗng, mắt xích 4 bị bỏ qua, và
     * trạng thái được ghi đè thành "Bình thường" trong khi cống vẫn đang đóng kín.
     *
     * <p>Kèm {@code deleted_at IS NULL}: một dòng đã xoá mềm không được tiếp tục quyết định trạng
     * thái hiện tại của công trình.
     */
    @Query(
            value =
                    """
                    SELECT * FROM construction_operation_status
                    WHERE construction_id = :congTrinhId AND deleted_at IS NULL
                    ORDER BY effective_at DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<ConstructionOperationStatus> banGhiMoiNhat(@Param("congTrinhId") Long congTrinhId);

    /**
     * Công trình nào đang lấy mã này làm bản ghi mới nhất — dùng khi sửa ánh xạ của mã.
     *
     * <p>{@code deleted_at IS NULL} phải xuất hiện ở <b>cả hai</b> vế: câu con chọn "dòng mới nhất"
     * và câu ngoài lọc theo mã. Thiếu ở câu con thì một dòng đã xoá vẫn được coi là mới nhất, và tập
     * công trình cần tính lại lệch khỏi tập mà {@link #banGhiMoiNhat} thực sự đọc.
     */
    @Query(
            value =
                    """
                    SELECT cos.construction_id FROM construction_operation_status cos
                    WHERE cos.operation_code_id = :codeId AND cos.deleted_at IS NULL AND cos.id = (
                      SELECT cos2.id FROM construction_operation_status cos2
                      WHERE cos2.construction_id = cos.construction_id AND cos2.deleted_at IS NULL
                      ORDER BY cos2.effective_at DESC LIMIT 1
                    )
                    """,
            nativeQuery = true)
    List<Long> findConstructionIdsWithLatestCode(@Param("codeId") Long codeId);

    /**
     * Công trình đã từng ghi nhận tình hình vận hành nhưng lâu rồi không cập nhật — nguồn của cảnh
     * báo mềm {@code ops.operation-status.stale-days} (CN-02.11, chốt G4).
     *
     * <p>⚠ <b>Chỉ đếm công trình ĐÃ TỪNG có bản ghi.</b> Công trình chưa có dòng nào không phải
     * "quá hạn cập nhật" mà là "chưa bắt đầu dùng chức năng" — gộp hai thứ đó lại thì ở ngày đầu
     * chạy, cảnh báo sẽ liệt kê <i>toàn bộ</i> danh mục công trình và không ai đọc nó nữa. Khi Công
     * ty chốt G8 và danh mục công trình ổn định, đây là chỗ để xem lại lựa chọn này.
     *
     * <p>Chỉ xét công trình {@code DANG_HOAT_DONG}: cống đã thanh lý hoặc đang ngừng mùa vụ không có
     * gì để trực ban ghi nhận hằng ngày.
     */
    @Query(
            value =
                    """
                    SELECT c.id FROM constructions c
                    WHERE c.deleted_at IS NULL AND c.lifecycle_state = 'DANG_HOAT_DONG'
                      AND EXISTS (
                        SELECT 1 FROM construction_operation_status s
                        WHERE s.construction_id = c.id AND s.deleted_at IS NULL)
                      AND NOT EXISTS (
                        SELECT 1 FROM construction_operation_status s
                        WHERE s.construction_id = c.id AND s.deleted_at IS NULL
                          AND s.effective_at >= :moc)
                    """,
            nativeQuery = true)
    List<Long> congTrinhQuaHanCapNhat(@Param("moc") OffsetDateTime moc);
}
