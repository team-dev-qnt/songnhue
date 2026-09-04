package com.songnhue.content.infra;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.content.application.PublicArticleRow;
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
     *
     * <p>⚠⚠ {@code CAST(:tuKhoa AS String)} <b>không phải trang trí</b>. Hàm {@code sn_khong_dau} do
     * dự án tự khai, nên Hibernate không biết kiểu tham số của nó; truyền {@code null} thì nó gửi
     * xuống dưới dạng {@code bytea} và PostgreSQL trả *"function sn_khong_dau(bytea) does not
     * exist"*. Tức là <b>mọi lượt tìm kiếm để trống ô từ khoá đều hỏng</b> — lỗi có từ WS-13, chỉ
     * chưa bài kiểm nào gọi search với từ khoá rỗng nên nó nằm im tới khi cổng công khai đi qua.
     */
    @Query(
            """
            SELECT DISTINCT a FROM Article a
            LEFT JOIN a.categories c
            WHERE a.deletedAt IS NULL
              AND (CAST(:tuKhoa AS String) IS NULL
                   OR sn_khong_dau(a.title) LIKE sn_khong_dau(CAST(:tuKhoa AS String)))
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
     * Danh sách bài của <b>cổng công khai</b> — T16.1.
     *
     * <p>⛔ Ba điều kiện đi liền nhau, thiếu một là lộ nội dung chưa được phép hiện:
     *
     * <ol>
     *   <li>{@code status = 'XUAT_BAN'} — Nháp, Chờ duyệt, Gỡ bài, Lưu trữ đều không nằm trong danh
     *       sách.
     *   <li>{@code publishedVersionId IS NOT NULL} — và câu lệnh <b>JOIN</b> vào bản đó, nên nội dung
     *       trả ra là bản đã duyệt chứ không phải bản biên tập viên đang gõ dở.
     *   <li>{@code publishedAt <= :now} — bài hẹn giờ chưa tới hạn thì chưa hiện. Đây là lý do không
     *       cần một trạng thái "Đã lên lịch" thứ bảy.
     * </ol>
     *
     * <p>Lọc danh mục bằng {@code EXISTS} chứ không {@code JOIN}: bài thuộc nhiều danh mục, mà join
     * thì nó xuất hiện nhiều lần và {@code Page.totalElements} đếm sai.
     */
    @Query(
            """
            SELECT new com.songnhue.content.application.PublicArticleRow(
                       a.slug, v.title, v.summary, v.coverAttachmentPublicId, a.publishedAt, a.viewCount,
                       v.docNumber, v.docIssuedDate)
            FROM Article a JOIN ArticleVersion v ON v.id = a.publishedVersionId
            WHERE a.deletedAt IS NULL
              AND a.status = 'XUAT_BAN'
              AND a.publishedAt <= :now
              AND (CAST(:tuKhoa AS String) IS NULL
                   OR sn_khong_dau(v.title) LIKE sn_khong_dau(CAST(:tuKhoa AS String)))
              AND (:danhMucId IS NULL
                   OR EXISTS (SELECT 1 FROM Article a2 JOIN a2.categories c
                               WHERE a2.id = a.id AND c.id = :danhMucId))
            ORDER BY a.publishedAt DESC
            """)
    Page<PublicArticleRow> findPublic(
            @Param("tuKhoa") String tuKhoa,
            @Param("danhMucId") Long danhMucId,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Nhãn chuyên mục của một loạt bài — <b>một lượt hỏi cho cả trang</b>.
     *
     * <p>CR-12 cần tag "Tin thủy lợi / Tin Công ty" cạnh mỗi bài ở khối Tin tức – Sự kiện. Danh
     * sách chuyên mục không dựng được trong biểu thức khởi tạo của {@link #findPublic} (JPQL không
     * dựng collection), nên nó đến từ đây rồi được ghép vào ở tầng application.
     *
     * <p>⚠ Vì sao không đọc {@code article.getCategories()} trong vòng lặp: đó là quan hệ LAZY, nên
     * mỗi bài là một truy vấn — 12 bài trên trang chủ thành 13 lượt hỏi CSDL. Ở đây một lượt là đủ,
     * và số lượt không đổi khi trang dài ra.
     *
     * <p>⛔ Lọc {@code c.visible} ngay trong câu lệnh: một chuyên mục đang ẩn thì không được xuất
     * hiện dưới dạng nhãn trên cổng — ẩn danh mục mà nhãn của nó vẫn hiện là để lộ đúng thứ người
     * quản trị vừa quyết định giấu đi.
     */
    @Query(
            """
            SELECT a.slug, c.slug, c.name FROM Article a JOIN a.categories c
            WHERE a.slug IN :slugs AND c.visible = TRUE AND c.deletedAt IS NULL
            ORDER BY c.path, c.sortOrder
            """)
    List<Object[]> findCategoryLabels(@Param("slugs") Collection<String> slugs);

    /**
     * Một bài trên cổng, tra theo slug.
     *
     * <p>Khác danh sách ở đúng một điểm: <b>nhận cả {@code LUU_TRU}</b>. Bài lưu trữ rút khỏi luồng
     * tin nhưng địa chỉ cũ vẫn phải sống — người ta đã chia sẻ liên kết đó, và trả 404 cho một bài
     * còn nguyên dữ liệu là tự làm hỏng liên kết của chính mình (T16.7).
     *
     * <p>{@code GO_BAI} thì <b>không</b> nằm ở đây: gỡ bài là quyết định rút nội dung khỏi công khai.
     */
    @Query(
            """
            SELECT a, v FROM Article a JOIN ArticleVersion v ON v.id = a.publishedVersionId
            WHERE a.deletedAt IS NULL
              AND a.slug = :slug
              AND a.status IN ('XUAT_BAN', 'LUU_TRU')
              AND a.publishedAt <= :now
            """)
    List<Object[]> findPublicBySlug(@Param("slug") String slug, @Param("now") Instant now);

    /** Id nội bộ của một bài công khai — để cộng lượt xem mà không phải tải cả entity. */
    @Query("SELECT a.id FROM Article a WHERE a.slug = :slug AND a.deletedAt IS NULL")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

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
     * Tệp này có nằm trong bản chụp <b>đang được xuất bản</b> của một bài đang thật sự công khai
     * không — chốt chặn duy nhất của {@code GET /api/v1/public/article-documents/&#123;id&#125;}
     * (WS-40).
     *
     * <h2>⛔⛔ Bốn vế, MỘT câu truy vấn — và đó là cả thiết kế</h2>
     *
     * <ol>
     *   <li>{@code a.deletedAt IS NULL} — bài đã xoá thì tệp của nó đi theo;
     *   <li>{@code a.status IN ('XUAT_BAN','LUU_TRU')} — <b>Nháp, Chờ duyệt, Gỡ bài đều 404</b>.
     *       {@code LUU_TRU} có mặt vì bài lưu trữ vẫn vào được bằng địa chỉ trực tiếp (T16.7), nên
     *       tệp của nó cũng phải sống; {@code GO_BAI} thì không — gỡ bài là quyết định rút nội dung
     *       khỏi công khai, và tệp đính kèm là một phần của nội dung ấy;
     *   <li>{@code a.publishedAt <= :now} — bài hẹn giờ chưa tới hạn thì tệp cũng chưa;
     *   <li><b>JOIN vào {@code v.documents} của chính bản {@code publishedVersionId}</b> — không
     *       phải danh sách đang biên tập. Đây là vế biến "đổi tài liệu phải qua duyệt" thành một
     *       điều <i>không biểu diễn được</i>, chứ không phải một lời dặn.
     * </ol>
     *
     * <p>⛔ Bốn vế ở <b>một chỗ dữ liệu đi qua</b>, không rải thành bốn phép kiểm trong service
     * (quy tắc 12) — và mỗi vế có một bài kiểm chứng ngược riêng ở {@code ArticleAttachmentTest}:
     * bốn vế mà chỉ kiểm một là ba vế chưa ai đi qua (quy tắc 7).
     *
     * <p>⚠ Trả {@code boolean}, không trả bài viết: nơi gọi <b>không được</b> biết bài nào — và
     * cũng không cần. Câu trả lời duy nhất cho cả bốn vế hỏng là 404 trần.
     */
    @Query(
            """
            SELECT COUNT(d) > 0 FROM Article a
            JOIN ArticleVersion v ON v.id = a.publishedVersionId
            JOIN v.documents d
            WHERE a.deletedAt IS NULL
              AND a.status IN ('XUAT_BAN', 'LUU_TRU')
              AND a.publishedAt <= :now
              AND d.attachmentPublicId = :maTep
            """)
    boolean daCongBoTaiLieu(@Param("maTep") UUID attachmentPublicId, @Param("now") Instant now);

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
     *
     * <h2>⭐ 04/09 (WS-40) — thêm HAI vế, và vế thứ hai mới là vế nguy hiểm</h2>
     *
     * Tài liệu đính kèm là tham chiếu <b>có khoá</b>, không phải chuỗi trong HTML, nên bốn vế cũ
     * không thấy nó: gắn một PDF bằng khối "Tài liệu đính kèm" mà không nhắc tới trong nội dung thì
     * dò chuỗi trả rỗng và tệp xoá được. Hai vế mới:
     *
     * <ul>
     *   <li>{@code article_attachments} — bản đang biên tập;
     *   <li>⛔ {@code article_version_attachments} — <b>bản chụp đang phục vụ công khai</b>. Thiếu vế
     *       này là xoá được một tài liệu <i>đang nằm trong bài đã xuất bản</i>, và triệu chứng chỉ
     *       hiện ra ở phía người đọc cổng.
     * </ul>
     *
     * <p>⚠ Vế thứ hai chỉ soi <b>bản đang xuất bản</b> ({@code published_version_id}), không soi mọi
     * phiên bản cũ — đúng bằng phạm vi của vế {@code v.content} đã có từ trước. Nới ra mọi phiên bản
     * nghe an toàn hơn nhưng biến kho tài liệu thành thứ <i>không bao giờ dọn được</i>: một tệp từng
     * đính vào bản nháp năm ngoái sẽ chặn mãi mãi. Đây là <b>lưới cảnh báo, không phải ràng buộc
     * toàn vẹn</b> — và xoá vẫn là xoá mềm.
     *
     * <p>⚠ Thêm vào <b>chính câu này</b>, không dựng một phép kiểm thứ hai ở tầng service: đường xoá
     * đi qua {@code MediaService.deleteFile} và màn hình quản trị gọi {@code /usages} <i>trước</i>
     * khi hỏi người dùng — hai nơi ấy phải trả lời giống hệt nhau, mà cách chắc chắn nhất là chúng
     * đọc cùng một câu (quy tắc 12).
     *
     * <p>⚠⚠ {@code EXISTS} chứ không {@code LEFT JOIN} thêm hai bảng: join thì một tệp nằm trong
     * năm bản chụp của cùng một bài sẽ nhân dòng lên, và {@code DISTINCT} che mất chuyện đó cho tới
     * khi ai đó bỏ nó đi.
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
                           OR v.content LIKE CONCAT('%', :maTep, '%')
                           OR EXISTS (SELECT 1 FROM article_attachments aa
                                       WHERE aa.article_id = a.id
                                         AND aa.attachment_public_id = CAST(:maTep AS uuid))
                           OR EXISTS (SELECT 1 FROM article_version_attachments va
                                       WHERE va.article_version_id = a.published_version_id
                                         AND va.attachment_public_id = CAST(:maTep AS uuid)))
                    """,
            nativeQuery = true)
    List<String> findTitlesReferencing(@Param("maTep") String attachmentPublicId);
}
