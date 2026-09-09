package com.songnhue.content.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.core.spi.AttachmentUsagePort;

/**
 * Bài viết nào đang dẫn tới một tệp — nửa {@code content} của {@link AttachmentUsagePort} (T40.26).
 *
 * <p>Dùng lại đúng truy vấn mà {@code MediaService.articlesUsing} vẫn dùng: sáu vế {@code OR} chạm
 * năm bảng (ảnh bìa của bài và của bản đã xuất bản, chuỗi HTML của cả hai, và hai bảng đính kèm).
 * ⛔ Không viết truy vấn thứ hai — hai bản dò tham chiếu là hai chỗ phải nhớ, và bản nào lạc hậu
 * thì nó lạc hậu <b>âm thầm</b>.
 *
 * <p>⚠ Phép dò là <b>lưới cảnh báo, ⛔ không phải ràng buộc toàn vẹn</b>: ảnh chèn giữa bài nằm
 * trong chuỗi HTML nên chỉ dò được bằng so khớp chuỗi. Nó bắt phần lớn tai nạn thường gặp; thứ lọt
 * qua vẫn cứu được vì xoá ở đây là xoá <b>mềm</b>.
 */
@Component
public class ArticleAttachmentUsage implements AttachmentUsagePort {

    /**
     * Trần số dòng kể ra.
     *
     * <p>Một ảnh dùng chung bị 400 bài dẫn thì câu lỗi ⛔ không cần kể hết — nó cần nói đủ để người
     * vận hành hiểu chuyện gì đang xảy ra. Con số tổng vẫn đúng vì nó đếm trước khi cắt.
     */
    private static final int TRAN_KE = 5;

    private final ArticleRepository articles;

    public ArticleAttachmentUsage(ArticleRepository articles) {
        this.articles = articles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangDuocDanBoi(UUID attachmentPublicId) {
        return articles.findTitlesReferencing(attachmentPublicId.toString()).stream()
                .limit(TRAN_KE)
                .map(tieuDe -> "bài viết \"%s\"".formatted(tieuDe))
                .toList();
    }
}
