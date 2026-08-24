package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.MediaFolder;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.MediaFolderRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.tree.MaterializedPath;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;

/**
 * Thư viện media — CN-01.3.
 *
 * <p>⭐ <b>Không có bảng tệp riêng</b> (điểm nghiệp vụ 8): tệp đi qua {@link AttachmentPort} của
 * Core, thư mục là {@link MediaFolder}. Nhờ vậy kiểm định dạng bằng magic bytes, bóc EXIF, đánh số
 * phiên bản, hạn mức dung lượng và xoá mềm đều dùng lại nguyên bộ của pattern P3 — không có cơ hội
 * để một đường tải lên thứ hai quên mất một trong số đó.
 */
@Service
public class MediaService {

    /** Giá trị giữ chỗ cho lượt INSERT đầu — {@code path} chứa chính id do CSDL sinh. */
    private static final String PATH_TAM = "/";

    /**
     * Định dạng nhận được ở thư viện media.
     *
     * <p>⛔ <b>Không có {@code image/svg+xml}</b> — điểm nghiệp vụ 7. SVG chạy được JavaScript, nên
     * nó chỉ vào hệ thống qua màn hình cấu hình giao diện (người tải là Quản trị viên) và phải qua
     * {@code SvgSanitizer}. Ảnh trong bài viết thì không nhận SVG, chấm hết.
     */
    private static final List<String> DINH_DANG_ANH = List.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private static final List<String> DINH_DANG_VIDEO = List.of("video/mp4", "video/webm");

    private static final List<String> DINH_DANG_TAI_LIEU = List.of(
            "application/pdf",
            "application/zip",
            "application/msword",
            "application/vnd.ms-excel",
            "application/x-ole-storage",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    /** Mọi định dạng thư viện media chấp nhận — hợp của ba nhóm trên. */
    private static final List<String> TAT_CA = java.util.stream.Stream.of(
                    DINH_DANG_ANH, DINH_DANG_VIDEO, DINH_DANG_TAI_LIEU)
            .flatMap(List::stream)
            .toList();

    private final MediaFolderRepository folders;
    private final ArticleRepository articles;
    private final AttachmentPort attachments;

    public MediaService(MediaFolderRepository folders, ArticleRepository articles, AttachmentPort attachments) {
        this.folders = folders;
        this.articles = articles;
        this.attachments = attachments;
    }

    // ---- Thư mục -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MediaFolder> tree() {
        return folders.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();
    }

    @Transactional(readOnly = true)
    public MediaFolder folder(UUID publicId) {
        return folders.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional
    public MediaFolder createFolder(String name, UUID parentPublicId) {
        MediaFolder parent = parentPublicId == null ? null : folder(parentPublicId);

        MediaFolder folder = new MediaFolder(name, parent == null ? null : parent.getId());
        // Cùng ba bước với CategoryService: path chứa chính id, mà cột là NOT NULL.
        folder.placeAt(folder.getParentId(), PATH_TAM, (short) 0);
        MediaFolder saved = folders.saveAndFlush(folder);

        String path = parent == null
                ? MaterializedPath.rootPath(saved.getId())
                : MaterializedPath.childPath(parent.getPath(), saved.getId());
        short depth = (short) MaterializedPath.depthOf(path);
        if (depth > MediaFolder.MAX_DEPTH) {
            throw new BusinessRuleException(ErrorCode.CMS_2005);
        }
        saved.placeAt(parent == null ? null : parent.getId(), path, depth);
        return folders.saveAndFlush(saved);
    }

    @Transactional
    public MediaFolder renameFolder(UUID publicId, String name) {
        MediaFolder folder = folder(publicId);
        folder.setName(name);
        return folder;
    }

    /**
     * Xoá thư mục — <b>chỉ khi rỗng</b> (CN-01.3).
     *
     * <p>Rỗng nghĩa là không còn tệp <i>và</i> không còn thư mục con. Xoá đệ quy nghe tiện hơn nhưng
     * một lần bấm nhầm sẽ cuốn đi cả nhánh tài liệu, mà người dùng không thấy trước mình đang xoá
     * bao nhiêu thứ.
     */
    @Transactional
    public void deleteFolder(UUID publicId) {
        MediaFolder folder = folder(publicId);

        if (folders.countByParentIdAndDeletedAtIsNull(folder.getId()) > 0) {
            throw new BusinessRuleException(ErrorCode.CMS_2004);
        }
        if (!attachments.refsOf(MediaFolder.OWNER_TYPE, folder.getId()).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.CMS_2008);
        }
        folder.markDeleted(Instant.now());
    }

    // ---- Tệp -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AttachmentRef> filesIn(UUID folderPublicId) {
        return attachments.refsOf(MediaFolder.OWNER_TYPE, folder(folderPublicId).getId());
    }

    /**
     * Tệp trong thư mục, lọc theo nhóm định dạng và khoảng ngày (CN-01.3).
     *
     * <p>⚠ <b>Lọc trong bộ nhớ, có chủ đích.</b> {@code AttachmentPort} cố ý mỏng — nó không có
     * phương thức truy vấn theo định dạng, và thêm vào chỉ để phục vụ một màn hình là bắt mọi module
     * khác gánh một hợp đồng rộng hơn mức cần. Một thư mục media đếm bằng chục tới vài trăm tệp, nên
     * lọc ở đây không đáng kể.
     *
     * <p>Mốc đổi cách làm: khi một thư mục vượt <b>vài nghìn</b> tệp, hoặc khi cần lọc trên toàn bộ
     * thư viện thay vì trong một thư mục. Lúc đó việc phải làm là mở rộng SPI, không phải phân trang
     * cái danh sách đã nằm sẵn trong bộ nhớ.
     *
     * @param nhom {@code "image"}, {@code "video"}, {@code "document"}; {@code null} = mọi loại
     */
    @Transactional(readOnly = true)
    public List<AttachmentRef> filesIn(UUID folderPublicId, String nhom, Instant tuNgay, Instant denNgay) {
        return filesIn(folderPublicId).stream()
                .filter(ref -> nhom == null || nhom.isBlank() || nhom.equals(nhomCua(ref.contentType())))
                .filter(ref -> tuNgay == null || !ref.createdAt().isBefore(tuNgay))
                .filter(ref -> denNgay == null || !ref.createdAt().isAfter(denNgay))
                .toList();
    }

    /** Nhóm hiển thị trên giao diện — khác với nhóm tính hạn mức dung lượng, dù trùng tên phần lớn. */
    private static String nhomCua(String contentType) {
        if (contentType == null) {
            return "document";
        }
        if (contentType.startsWith("image/")) {
            return "image";
        }
        if (contentType.startsWith("video/")) {
            return "video";
        }
        return "document";
    }

    /**
     * Tải một tệp lên thư mục.
     *
     * <p>Định dạng kiểm bằng <b>magic bytes</b> trong {@link AttachmentPort}, không tin đuôi tệp và
     * không tin {@code Content-Type} trình duyệt gửi. Giới hạn dung lượng đọc từ {@code settings}
     * theo nhóm định dạng — cùng cơ chế đã sửa ở WS-12/T12.6.
     */
    @Transactional
    public AttachmentRef upload(UUID folderPublicId, String originalName, byte[] content) {
        MediaFolder folder = folder(folderPublicId);
        return attachments.upload(new AttachmentUploadCommand(
                MediaFolder.OWNER_TYPE, folder.getId(), "MEDIA", originalName, content, TAT_CA));
    }

    /** Đường dẫn tải về — presigned URL, hạn ngắn. */
    @Transactional(readOnly = true)
    public String downloadUrl(UUID attachmentPublicId) {
        return attachments.downloadUrl(attachmentPublicId);
    }

    /**
     * Bài viết đang dùng tệp này — gọi trước khi xoá để hiện cảnh báo (T14.5).
     *
     * <p>Tách thành một hàm đọc riêng chứ không nhét vào {@link #deleteFile}: giao diện cần <b>hỏi
     * trước</b> để hiện danh sách "3 bài đang dùng ảnh này", chứ không phải bấm Xoá rồi mới biết.
     */
    @Transactional(readOnly = true)
    public List<String> articlesUsing(UUID attachmentPublicId) {
        return articles.findTitlesReferencing(attachmentPublicId.toString());
    }

    /**
     * Xoá tệp — chặn khi còn bài viết tham chiếu.
     *
     * <p>⚠ Phép dò tham chiếu là <b>lưới cảnh báo, không phải ràng buộc toàn vẹn</b>: ảnh chèn giữa
     * bài nằm trong chuỗi HTML nên chỉ dò được bằng so khớp chuỗi. Nó bắt phần lớn tai nạn thường
     * gặp; thứ lọt qua vẫn cứu được vì xoá ở đây là xoá mềm.
     */
    @Transactional
    public void deleteFile(UUID attachmentPublicId) {
        List<String> dangDung = articlesUsing(attachmentPublicId);
        if (!dangDung.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.CMS_2009, dangDung.size(), String.join(", ", dangDung));
        }
        attachments.delete(attachmentPublicId);
    }
}
