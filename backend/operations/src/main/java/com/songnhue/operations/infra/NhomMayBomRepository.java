package com.songnhue.operations.infra;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.operations.domain.NhomMayBom;

/**
 * Nhóm máy bơm — dòng Bảng 2 Báo cáo nhanh.
 *
 * <p>⛔ {@link NhomMayBom} ⛔ là {@code ScopedEntity}: Báo cáo nhanh là văn bản cấp Công ty (xem
 * javadoc entity).
 */
public interface NhomMayBomRepository extends JpaRepository<NhomMayBom, Long> {

    Optional<NhomMayBom> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<NhomMayBom> findByDeletedAtIsNull();

    /**
     * ⚠ So Q bằng {@code BigDecimal} — {@code 1100} và {@code 1100.00} là MỘT khoá (CSDL lưu scale
     * 2), nên nơi gọi phải chuẩn hoá scale trước; xem {@code TramBomImportService}.
     *
     * <p>⚠ JPQL tường minh: tên phương thức dẫn xuất ⛔ giải được trường {@code qMotMayM3h} (tiền tố
     * một chữ cái viết thường ⇒ Spring Data đọc thành {@code QMotMayM3h}) — lượt chạy HTTP đầu đỏ ở đây.
     */
    @Query("SELECT n FROM NhomMayBom n WHERE n.constructionId = :ct AND n.qMotMayM3h = :q AND n.deletedAt IS NULL")
    Optional<NhomMayBom> timTheoKhoa(@Param("ct") Long constructionId, @Param("q") BigDecimal q);
}
