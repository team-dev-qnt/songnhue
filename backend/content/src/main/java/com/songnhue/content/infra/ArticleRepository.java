package com.songnhue.content.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.content.domain.Article;

/** Truy vấn bài viết — CN-01.1, CN-01.8. */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    /** Kiểm trùng slug khi SỬA — bỏ qua chính bài đang sửa, nếu không thì tự nó trùng với nó. */
    boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, Long id);

    /**
     * Tìm kiếm quản trị (T13.8) — bỏ dấu, không phân biệt hoa thường.
     *
     * <p>Mọi tham số lọc đều nhận {@code null} = không lọc theo tiêu chí đó. Viết một câu chịu được
     * mọi tổ hợp thay vì dựng câu động: câu động là nơi lỗ SQL injection hay chui vào, và cũng là nơi
     * bộ lọc bị bỏ sót mà không ai thấy.
     *
     * <p>⚠ Sắp xếp đi qua {@code Pageable}, mà cột sắp xếp phải lọc bằng {@code PageUtils} ở tầng
     * service — cho người dùng truyền tên cột tự do là mở đường đọc dữ liệu ngoài ý muốn.
     */
    @Query(
            """
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.categories c
            WHERE a.deletedAt IS NULL
              AND (:tuKhoa IS NULL OR sn_khong_dau(a.title) LIKE sn_khong_dau(:tuKhoa))
              AND (:trangThai IS NULL OR a.status = :trangThai)
              AND (:tacGia IS NULL OR a.authorUserId = :tacGia)
              AND (:danhMucId IS NULL OR c.id = :danhMucId)
              AND (CAST(:tuNgay AS timestamp) IS NULL OR a.createdAt >= :tuNgay)
              AND (CAST(:denNgay AS timestamp) IS NULL OR a.createdAt <= :denNgay)
            """)
    Page<Article> search(
            @Param("tuKhoa") String tuKhoa,
            @Param("trangThai") String trangThai,
            @Param("tacGia") Long tacGia,
            @Param("danhMucId") Long danhMucId,
            @Param("tuNgay") Instant tuNgay,
            @Param("denNgay") Instant denNgay,
            Pageable pageable);

    /**
     * Bài đã duyệt vừa tới giờ đăng — nguồn cho job bắn revalidate ISR (T13.7).
     *
     * <p>Cửa sổ hai đầu chứ không phải {@code published_at <= now()}: nếu không thì mỗi lượt chạy sẽ
     * quét lại toàn bộ bài đã đăng từ trước tới nay, và bắn revalidate cho tất cả.
     */
    @Query(
            """
            SELECT a FROM Article a
            WHERE a.deletedAt IS NULL
              AND a.status = 'XUAT_BAN'
              AND a.publishedVersionId IS NOT NULL
              AND a.publishedAt > :tu AND a.publishedAt <= :den
            """)
    List<Article> findJustDue(@Param("tu") Instant tu, @Param("den") Instant den);

    /**
     * Cộng dồn lượt xem theo lô (T13.10).
     *
     * <p>⚠ {@code @Modifying} <b>không đi qua bộ ghi nhật ký kiểm toán</b> — cố ý ở đây: lượt xem là
     * số máy đếm, ghi vào nhật ký nghiệp vụ chỉ làm loãng thứ người ta cần tra. Mọi thao tác khác cần
     * dấu vết thì phải đi qua entity.
     *
     * <p>Cộng dồn tại chỗ ({@code view_count + :them}) chứ không đọc-rồi-ghi: nhiều lượt đẩy song
     * song mà đọc-rồi-ghi thì mất số của nhau.
     */
    @Modifying
    @Query("UPDATE Article a SET a.viewCount = a.viewCount + :them WHERE a.id = :id")
    int addViews(@Param("id") Long id, @Param("them") long them);

    /**
     * Bài viết đang dùng một tệp media — nguồn cho cảnh báo trước khi xoá (T14.5).
     *
     * <p>Hai chỗ một tệp có thể bị tham chiếu, và <b>phải xét cả hai</b>:
     *
     * <ol>
     *   <li>Ảnh đại diện — cột {@code cover_attachment_public_id}.
     *   <li>Ảnh chèn giữa bài — nằm trong chuỗi HTML của {@code content}, dưới dạng đường dẫn có mã
     *       tệp. Không có cách nào ngoài dò chuỗi: nội dung là RichText do người dùng soạn, không
     *       phải quan hệ có khoá ngoại.
     * </ol>
     *
     * <p>⚠ Dò chuỗi thì có thể sót — ai đó sao chép đường dẫn theo dạng khác thì không bắt được. Đây
     * là <b>lưới cảnh báo, không phải ràng buộc toàn vẹn</b>; nó ngăn phần lớn tai nạn thường gặp
     * (xoá nhầm ảnh đang chạy trên cổng) chứ không bảo đảm tuyệt đối. Xoá là xoá mềm nên vẫn khôi
     * phục được, và đó là lý do mức bảo đảm này chấp nhận được.
     *
     * <p>Cũng xét cả bản chụp đang phục vụ công khai: bài đã xuất bản dùng ảnh trong
     * {@code article_versions}, mà cột nội dung của {@code articles} có thể đã đổi.
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT a.title
                    FROM articles a
                             LEFT JOIN article_versions v ON v.id = a.published_version_id
                    WHERE a.deleted_at IS NULL
                      AND (a.cover_attachment_public_id = CAST(:maTep AS uuid)
                           OR v.cover_attachment_public_id = CAST(:maTep AS uuid)
                           OR a.content LIKE CONCAT('%', :maTep, '%')
                           OR v.content LIKE CONCAT('%', :maTep, '%'))
                    """,
            nativeQuery = true)
    List<String> findTitlesReferencing(@Param("maTep") String attachmentPublicId);
}
