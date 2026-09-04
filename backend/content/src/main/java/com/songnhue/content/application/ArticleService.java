package com.songnhue.content.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.ArticleDocument;
import com.songnhue.content.domain.ArticleState;
import com.songnhue.content.domain.ArticleVersion;
import com.songnhue.content.domain.Category;
import com.songnhue.content.domain.KhoTep;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.ArticleVersionRepository;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.HtmlSanitizer;
import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Bài viết — CN-01.1.
 *
 * <h2>⭐ Copy-on-write, và vì sao nó không thể nằm ở tầng khác</h2>
 *
 * Sửa một bài <b>đang xuất bản</b> không được làm bài biến mất khỏi cổng. Cách làm:
 *
 * <ol>
 *   <li>Nội dung mới ghi thẳng lên {@link Article} — đó là bản đang biên tập.
 *   <li>{@code publishedVersionId} <b>không đụng tới</b> — cổng vẫn phục vụ bản chụp cũ.
 *   <li>Bài chuyển sang {@code CHO_DUYET} qua workflow engine.
 *   <li>Duyệt xong mới chụp bản mới và trỏ {@code publishedVersionId} sang đó.
 * </ol>
 *
 * <p>Người có quyền {@code cms:article:publish} thì bước 3–4 gộp làm một: sửa xong là bản mới lên
 * cổng luôn (điểm nghiệp vụ 1). Không phải đặc quyền tuỳ tiện — đội nội dung của Công ty có 1–2
 * người, bắt họ tự gửi cho chính mình duyệt là thêm hai lần bấm chuột không đổi lại điều gì.
 *
 * <p>⛔ <b>Không có chỗ nào trong lớp này gọi {@code applyState}.</b> Mọi chuyển trạng thái đi qua
 * {@link WorkflowPort#execute} — đi tắt là bỏ qua cùng lúc kiểm quyền, bắn thông báo và ghi nhật ký,
 * cả ba đều im lặng (quy tắc 4).
 */
@Service
public class ArticleService {

    private final ArticleRepository articles;
    private final ArticleVersionRepository versions;
    private final CategoryRepository categories;
    private final WorkflowPort workflow;
    private final PortalCache portalCache;
    private final AttachmentPort attachments;

    public ArticleService(
            ArticleRepository articles,
            ArticleVersionRepository versions,
            CategoryRepository categories,
            WorkflowPort workflow,
            PortalCache portalCache,
            AttachmentPort attachments) {
        this.articles = articles;
        this.versions = versions;
        this.categories = categories;
        this.workflow = workflow;
        this.portalCache = portalCache;
        this.attachments = attachments;
    }

    // ---- Đọc -----------------------------------------------------------------

    /**
     * Nạp sẵn quan hệ lười trước khi entity <b>rời khỏi giao dịch</b>.
     *
     * <h2>Lỗi đã xảy ra thật — hai màn hình CMS trả 500</h2>
     *
     * {@code ArticleController} ánh xạ entity sang DTO <i>trong controller</i>, tức là sau khi
     * phương thức {@code @Transactional} ở đây đã kết thúc và {@code Session} đã đóng
     * ({@code spring.jpa.open-in-view: false}). {@code ArticleSummary.of} và {@code ArticleDetail.of}
     * đều đọc {@code getCategories()} — một {@code PersistentSet} chưa nạp — nên ném
     * {@code LazyInitializationException}, và {@code GlobalExceptionHandler} biến nó thành
     * {@code SYS-0001}. Đo thật: {@code GET /api/v1/cms/articles} và
     * {@code GET /api/v1/cms/articles/&#123;id&#125;} trả <b>500 cho mọi lượt gọi</b> — danh sách bài
     * viết và màn hình sửa bài, tức là toàn bộ phần quản trị nội dung, không dùng được.
     *
     * <p>⚠⚠ Vì sao <b>391 bài kiểm xanh</b> vẫn không thấy: {@code ArticleLifecycleTest} gọi thẳng
     * service, nên phép khẳng định chạy <i>bên trong</i> giao dịch — nơi nạp lười vẫn hoạt động
     * bình thường. Đây đúng là nợ #65 ("bài kiểm CMS chưa đi qua HTTP") hiện nguyên hình: nó không
     * phải mục cho đẹp hồ sơ, nó đang che một sự cố toàn phần. Bài kiểm giữ chỗ này là
     * {@code ArticleHttpTest} — đi qua HTTP thật, vì đó là đường duy nhất tái hiện được.
     *
     * <p>Gọi {@code size()} là cách rẻ nhất buộc Hibernate nạp; {@code @BatchSize} trên chính quan
     * hệ đó giữ cho lượt nạp của cả trang gom về một truy vấn.
     */
    private static Article napQuanHe(Article bai) {
        bai.getCategories().size();
        // ⚠ WS-40: `documents` cũng là quan hệ LAZY, và `ArticleDetail.of` đọc nó ở CONTROLLER —
        //   tức là sau khi giao dịch đã đóng. Quên dòng này thì màn hình sửa bài trả 500, đúng lỗi
        //   mà javadoc trên vừa kể lại (391 bài kiểm xanh, mọi màn hình CMS trả 500).
        bai.getDocuments().size();
        return bai;
    }

    private static Page<Article> napQuanHe(Page<Article> trang) {
        trang.getContent().forEach(ArticleService::napQuanHe);
        return trang;
    }

    @Transactional(readOnly = true)
    public Article get(UUID publicId) {
        return napQuanHe(articles.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004)));
    }

    @Transactional(readOnly = true)
    public Page<Article> search(
            String tuKhoa, String trangThai, Long tacGia, UUID danhMuc, Instant tuNgay, Instant denNgay, Pageable p) {

        Long danhMucId = danhMuc == null
                ? null
                : categories
                        .findByPublicIdAndDeletedAtIsNull(danhMuc)
                        .map(Category::getId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        // Bọc `%…%` ở đây chứ không ở repository: nơi gọi truyền chuỗi người dùng gõ, còn cú pháp
        // LIKE là chuyện của tầng truy vấn. Chuẩn hoá luôn để "Đê Điều" khớp "de dieu".
        String mau = tuKhoa == null || tuKhoa.isBlank() ? null : "%" + VietnameseUtils.normalizeForSearch(tuKhoa) + "%";

        return napQuanHe(articles.search(mau, trangThai, tacGia, danhMucId, tuNgay, denNgay, p));
    }

    @Transactional(readOnly = true)
    public List<ArticleVersion> versionsOf(UUID publicId) {
        return versions.findByArticleIdOrderByVersionNoDesc(get(publicId).getId());
    }

    /**
     * Một phiên bản cụ thể của một bài cụ thể.
     *
     * <p>Lọc theo {@code articleId} chứ không tra {@code versionPublicId} trần: nếu không thì biết
     * một mã phiên bản là đọc được nội dung của bài bất kỳ, kể cả bài chưa xuất bản của người khác.
     */
    @Transactional(readOnly = true)
    public ArticleVersion version(UUID publicId, UUID versionPublicId) {
        Long articleId = get(publicId).getId();
        return versions.findByPublicId(versionPublicId)
                .filter(v -> v.getArticleId().equals(articleId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Tài liệu đính kèm của <b>bản đang biên tập</b>, đã ghép tên/định dạng/dung lượng của tệp.
     *
     * <p>⛔ Đây là bản LÀM VIỆC — màn hình quản trị đọc nó, cổng công khai thì <b>không</b>
     * (cổng đọc bản chụp, xem {@link ArticleVersion#getDocuments()}).
     *
     * <p>⚠ Tệp bị xoá mềm <b>biến khỏi danh sách</b>, không thành một dòng báo lỗi:
     * {@code findRef} lọc {@code deleted_at IS NULL}. Người biên tập thấy đúng những gì còn dùng
     * được — và lượt lưu kế tiếp ghi lại danh sách đã sạch.
     */
    @Transactional(readOnly = true)
    public List<TaiLieuDinhKem> taiLieuCua(Article bai) {
        return bai.getDocuments().stream()
                .map(d -> attachments
                        .findRef(d.getAttachmentPublicId())
                        .map(ref -> new TaiLieuDinhKem(
                                ref.publicId(),
                                d.getLabel(),
                                ref.originalName(),
                                ref.contentType(),
                                ref.sizeBytes(),
                                ref.downloadable()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Nút giao diện được phép hiện — FE render từ đây, không tự suy từ trạng thái. */
    @Transactional(readOnly = true)
    public List<AllowedAction> allowedActions(UUID publicId) {
        return workflow.allowedActions(get(publicId));
    }

    // ---- Ghi -----------------------------------------------------------------

    @Transactional
    public Article create(ArticleDraft draft) {
        requireCategories(draft.categoryPublicIds());

        Long author = draft.authorUserId() != null ? draft.authorUserId() : currentUserId();
        Article article = new Article(
                draft.title(),
                requireUniqueSlug(draft.slug(), draft.title(), null),
                HtmlSanitizer.clean(draft.content()),
                author);
        applyEditableFields(article, draft);

        Article saved = articles.saveAndFlush(article);
        saved.getCategories().addAll(resolveCategories(draft.categoryPublicIds()));
        apDungTaiLieu(saved, draft);
        snapshot(saved, "Tạo mới");
        return napQuanHe(saved);
    }

    /**
     * Sửa nội dung bài viết.
     *
     * <p>⚠ Chặn sửa khi đang {@code CHO_DUYET} (CN-01.1 "khóa chỉnh sửa"): người duyệt đang đọc một
     * bản, mà tác giả sửa dưới chân thì người duyệt bấm Duyệt cho một nội dung họ chưa từng thấy.
     *
     * <p>Bài đang xuất bản sửa được bình thường — bản công khai không đổi cho tới khi có người duyệt.
     * Đó chính là copy-on-write, xem javadoc của lớp.
     */
    @Transactional
    public Article update(UUID publicId, ArticleDraft draft) {
        Article article = get(publicId);
        if (ArticleState.CHO_DUYET.equals(article.getStatus())) {
            throw new BusinessRuleException(ErrorCode.CMS_2007);
        }
        requireCategories(draft.categoryPublicIds());

        article.setTitle(draft.title());
        article.setSlug(slugKhiSua(article, draft));
        // ⛔ Khử trùng lúc GHI, không lúc đọc. Ghi sạch một lần thì mọi nơi đọc đều an toàn —
        // cổng công khai, màn hình xem trước của admin-app, bản chụp phiên bản, và cả bản
        // xuất dữ liệu về sau. Khử trùng lúc đọc là phải nhớ làm ở từng nơi đọc.
        article.setContent(HtmlSanitizer.clean(draft.content()));
        applyEditableFields(article, draft);
        if (draft.authorUserId() != null) {
            article.setAuthorUserId(draft.authorUserId());
        }
        article.getCategories().clear();
        article.getCategories().addAll(resolveCategories(draft.categoryPublicIds()));
        apDungTaiLieu(article, draft);

        snapshot(article, "Lưu bản sửa");

        // Người có quyền xuất bản sửa một bài đang chạy → bản mới lên cổng ngay, không vòng qua duyệt.
        if (ArticleState.XUAT_BAN.equals(article.getStatus()) && hasPermission("cms:article:publish")) {
            serveLatestVersion(article);
            // Đường này KHÔNG đi qua execute() nên phải tự bắn — bỏ sót ở đây thì người có quyền
            // xuất bản sửa bài xong, cổng vẫn hiện nội dung cũ tới lượt dựng lại theo chu kỳ.
            portalCache.articleChanged(article.getSlug());
        }
        return article;
    }

    /**
     * Đường dẫn công khai của bài <b>đã từng xuất bản thì đóng băng</b> — lỗ do WS-16 làm lộ ra.
     *
     * <p>Bản trước suy lại slug từ tiêu đề ở mọi lượt sửa. Nghĩa là biên tập viên sửa một lỗi chính
     * tả trong tiêu đề của bài đang chạy trên cổng thì <b>địa chỉ công khai đổi ngay lập tức</b> —
     * trước khi có ai duyệt. Mọi liên kết đã chia sẻ và mọi kết quả tìm kiếm của Google trỏ vào bài
     * đó chết theo, và đó đúng là thứ copy-on-write sinh ra để tránh: sửa bài không được phép thay
     * đổi cái gì ở phía công khai cho tới lúc duyệt.
     *
     * <p>Phase 1 không có bảng chuyển hướng, nên gõ tay một slug mới vẫn làm mất địa chỉ cũ. Khác
     * biệt là ở chỗ đó là <b>một hành động cố ý</b> chứ không phải hệ quả phụ của việc sửa tiêu đề.
     */
    private String slugKhiSua(Article article, ArticleDraft draft) {
        boolean tuSuyRa = draft.slug() == null || draft.slug().isBlank();
        if (tuSuyRa && article.getPublishedVersionId() != null) {
            return article.getSlug();
        }
        return requireUniqueSlug(draft.slug(), draft.title(), article.getId());
    }

    /**
     * Thực hiện một hành động của quy trình duyệt.
     *
     * <p>Hai việc <b>phải nằm cùng một transaction</b> và đúng thứ tự này: engine kiểm quyền + đổi
     * trạng thái trước, rồi mới tới hệ quả về nội dung công khai. Ngược lại thì một lượt duyệt bị từ
     * chối vì thiếu quyền vẫn kịp đẩy bản mới lên cổng.
     */
    @Transactional
    public Article execute(UUID publicId, String action, String reason) {
        Article article = get(publicId);
        // Truyền `reason` xuống engine, không chỉ để ghi vào `reviewNote`: engine là nơi ép buộc
        // "bước này phải nêu lý do" (`workflow_transitions.requires_reason`). Bỏ tham số này là
        // REQUEST_CHANGES ném SYS-0003 — hỏng đóng, đúng ý.
        workflow.execute(article, action, null, reason);

        switch (action) {
            case "APPROVE" -> {
                article.setReviewNote(null);
                if (article.getPublishedAt() == null) {
                    article.setPublishedAt(Instant.now());
                }
                serveLatestVersion(article);
            }
            case "REQUEST_CHANGES" -> article.setReviewNote(reason);
            // GO_BAI / LUU_TRU / REPUBLISH chỉ đổi việc CÓ hiển thị hay không, nên không đụng vào
            // publishedVersionId — nội dung công khai giữ nguyên, đúng như spec: tái xuất bản từ
            // Gỡ bài không cần duyệt lại.
            default -> {}
        }

        // ⭐ Bắn yêu cầu dựng lại cổng cho MỌI hành động, không riêng APPROVE — T16.5.
        //
        // Gỡ bài và lưu trữ cũng phải làm cổng đổi theo: gỡ một bài đăng nhầm mà trang tĩnh vẫn
        // phục vụ nó thêm vài phút là đúng thứ người ta bấm Gỡ để tránh. `SUBMIT` và
        // `REQUEST_CHANGES` không đổi gì ở phía công khai, nhưng một việc thừa trong hàng đợi rẻ
        // hơn nhiều so với một danh sách hành động phải nhớ cập nhật mỗi lần thêm bước chuyển.
        portalCache.articleChanged(article.getSlug());
        return article;
    }

    /**
     * Phục hồi nội dung từ một phiên bản cũ (CN-01.1).
     *
     * <p>Phục hồi là một <b>lần sửa</b>, không phải một bước lùi thời gian: nó ghi thêm một phiên bản
     * mới mang nội dung cũ. Ghi đè ngược lại lịch sử thì bản đã bị thay không còn dấu vết, mà đó lại
     * đúng là bản người ta cần tra khi đi tìm "ai đã bỏ đoạn này đi".
     */
    @Transactional
    public Article restoreVersion(UUID publicId, UUID versionPublicId) {
        Article article = get(publicId);
        if (ArticleState.CHO_DUYET.equals(article.getStatus())) {
            throw new BusinessRuleException(ErrorCode.CMS_2007);
        }
        ArticleVersion version = versions.findByPublicId(versionPublicId)
                .filter(v -> v.getArticleId().equals(article.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        version.restoreInto(article);
        snapshot(article, "Phục hồi từ bản #" + version.getVersionNo());
        return article;
    }

    @Transactional
    public void delete(UUID publicId) {
        get(publicId).markDeleted(Instant.now());
    }

    // -------------------------------------------------------------------------

    /** Chụp bản hiện tại và trỏ cổng công khai sang đó. */
    private void serveLatestVersion(Article article) {
        ArticleVersion published = snapshot(article, "Xuất bản");
        article.servePublicly(published.getId());
    }

    private ArticleVersion snapshot(Article article, String note) {
        int next = versions.maxVersionNo(article.getId()) + 1;
        ArticleVersion version = ArticleVersion.snapshotOf(article, next, note);
        version.setCreatedBy(currentUserId());
        return versions.saveAndFlush(version);
    }

    private void applyEditableFields(Article article, ArticleDraft draft) {
        article.setSummary(draft.summary());
        article.setSource(draft.source());
        article.setCoverAttachmentPublicId(draft.coverAttachmentPublicId());
        article.setMetaTitle(draft.metaTitle());
        article.setMetaDescription(draft.metaDescription());
        article.setMetaKeywords(draft.metaKeywords());
        article.setDocNumber(draft.docNumber());
        article.setDocIssuedDate(draft.docIssuedDate());
        if (draft.publishedAt() != null) {
            article.setPublishedAt(draft.publishedAt());
        }
    }

    /**
     * Ghi danh sách tài liệu đính kèm — <b>xoá sạch rồi ghi lại</b>, y hệt {@code categories}.
     *
     * <h2>⛔⛔ Hai chốt chặn ở đây, và cả hai đều CHẶN CÙNG MỘT TRIỆU CHỨNG</h2>
     *
     * <p>Tệp lọt qua đây mà không đủ điều kiện sẽ thành <b>một dòng có tên trên cổng, bấm vào là
     * 404</b> — đúng hình dạng §10.52 (ảnh cổng chưa từng ra được một byte, và bài kiểm chỉ đi
     * nhánh 404 nên không ai thấy).
     *
     * <ol>
     *   <li><b>Đúng kho.</b> {@code owner_type} phải là {@code TAI_LIEU}. Không kiểm thì một
     *       {@code publicId} của ảnh media gắn được vào đây; đường công khai hẹp
     *       ({@code /public/article-documents}) đòi {@code TAI_LIEU} nên nó trả 404 — trong khi DTO
     *       vẫn liệt kê tên tệp. ⚠ Đây <b>không</b> phải chốt chặn bảo mật (chốt ấy nằm ở
     *       {@code readForPublic}); nó là chốt chặn <i>tính nhất quán</i>, và thiếu nó thì lỗi hoàn
     *       toàn im lặng.
     *   <li><b>Đã quét xong.</b> {@code findRef} lọc {@code deleted_at} nhưng <b>không</b> lọc
     *       trạng thái quét — chỉ {@code readForPublic} mới lọc {@code isDownloadable()}. Bỏ qua thì
     *       một tệp vừa tải lên chưa quét xong vẫn ra DTO trong khi byte trả 404.
     * </ol>
     *
     * <p>⚠ {@code findRef} trả rỗng cho tệp <b>đã xoá mềm</b> ⇒ {@code SYS-0004}. Đó là hành vi
     * đúng: gắn một tệp đã xoá là một lỗi nhập liệu, không phải một danh sách rỗng lặng lẽ.
     *
     * <p>⚠ Trùng {@code publicId} trong cùng một bài bị <b>ràng buộc UNIQUE</b> của CSDL chặn. Lọc
     * trùng ở đây thay vì để CSDL ném: người dùng kéo nhầm hai lần cùng một tệp không đáng nhận một
     * lỗi 409 khó hiểu.
     */
    private void apDungTaiLieu(Article article, ArticleDraft draft) {
        List<ArticleDocument> ketQua = new java.util.ArrayList<>();
        Set<UUID> daThay = new java.util.LinkedHashSet<>();

        for (ArticleDraft.TaiLieu taiLieu : draft.documentsOrEmpty()) {
            if (taiLieu == null || taiLieu.publicId() == null || !daThay.add(taiLieu.publicId())) {
                continue;
            }
            AttachmentRef tep = attachments
                    .findRef(taiLieu.publicId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
            if (!KhoTep.TAI_LIEU.ownerType().equals(tep.ownerType()) || !tep.downloadable()) {
                throw new BusinessRuleException(ErrorCode.CMS_2016, tep.originalName());
            }
            // ⭐ Trần phục vụ ép Ở ĐÂY, không chỉ lúc phục vụ. Lúc này người biên tập còn sửa được
            //   — nén lại, tách nhỏ. Ép mỗi lúc phục vụ thì tệp lên cổng rồi mới hỏng, và người
            //   đính kèm không bao giờ biết vì sao độc giả tải không được (quy tắc 27).
            if (KhoTep.vuotTranPhucVu(tep.sizeBytes())) {
                throw new BusinessRuleException(
                        ErrorCode.CMS_2017,
                        tep.originalName(),
                        tep.sizeBytes() / (1024 * 1024),
                        KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB);
            }
            ketQua.add(new ArticleDocument(taiLieu.publicId(), taiLieu.label(), ketQua.size()));
        }

        article.getDocuments().clear();
        article.getDocuments().addAll(ketQua);
    }

    private static void requireCategories(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.CMS_2006);
        }
    }

    private Set<Category> resolveCategories(Set<UUID> ids) {
        return ids.stream()
                .map(id -> categories
                        .findByPublicIdAndDeletedAtIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String requireUniqueSlug(String slug, String title, Long selfId) {
        String candidate =
                slug == null || slug.isBlank() ? VietnameseUtils.toSlug(title) : VietnameseUtils.toSlug(slug);
        boolean taken = selfId == null
                ? articles.existsBySlugAndDeletedAtIsNull(candidate)
                : articles.existsBySlugAndDeletedAtIsNullAndIdNot(candidate, selfId);
        if (taken) {
            throw new BusinessRuleException(ErrorCode.CMS_2001, candidate);
        }
        return candidate;
    }

    private static boolean hasPermission(String code) {
        return AuthContext.current()
                .map(AuthenticatedUser::permissions)
                .map(p -> p.contains(code))
                .orElse(false);
    }

    private static Long currentUserId() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }
}
