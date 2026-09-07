package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Article;
import com.songnhue.content.infra.ArticleRepository;

/**
 * Bài hẹn giờ vừa tới hạn → yêu cầu cổng dựng lại trang — T13.7, trả <b>nợ #63</b>.
 *
 * <h2>⚠ Việc này KHÔNG đổi trạng thái bài nào</h2>
 *
 * Đây là điểm dễ hiểu nhầm nhất. Bài hẹn giờ đã ở trạng thái {@code XUAT_BAN} ngay từ lúc được
 * duyệt; thứ quyết định nó có hiện hay không là {@code published_at <= now()}, mà truy vấn công khai
 * đã lọc sẵn. Nghĩa là <b>bài tự hiện đúng giờ kể cả khi việc này không bao giờ chạy</b>.
 *
 * <p>Vậy nó để làm gì? Cổng công khai dựng trang tĩnh (ISR). Trang chủ dựng lúc 8h không tự biết là
 * 9h có một bài mới tới hạn — nó chỉ dựng lại khi hết chu kỳ hoặc khi có ai bảo. Việc này là người
 * bảo.
 *
 * <p>Hệ quả của cách đặt vấn đề đó: việc này chạy hỏng thì <b>bài vẫn đăng</b>, chỉ chậm hiện trên
 * trang tĩnh tối đa một chu kỳ ISR. Đó là lý do nó không cần khoá chống chạy trùng và không cần
 * ShedLock.
 *
 * <h2>Cửa sổ quét, và vì sao phải nhớ mốc lần trước</h2>
 *
 * Quét {@code published_at <= now()} thì mỗi lượt chạy sẽ bắn lại cho <i>toàn bộ</i> bài đã đăng từ
 * trước tới nay — mỗi 5 phút một lần, mãi mãi. Phải là một <b>khoảng</b>: từ mốc lần quét trước tới
 * bây giờ.
 *
 * <p>Mốc giữ trong bộ nhớ nên khởi động lại là mất. Chấp nhận có ý thức: bài tới hạn đúng lúc máy
 * chủ khởi động lại sẽ không được bắn, và hậu quả tối đa là nó hiện chậm một chu kỳ ISR. Lưu mốc
 * xuống CSDL để đổi lấy chừng đó là không đáng.
 */
@Component
public class ScheduledPublishScanner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPublishScanner.class);

    /** 5 phút — khớp đúng chu kỳ ghi trong T13.7. */
    private static final long CHU_KY_MS = 5 * 60 * 1000L;

    private final ArticleRepository articles;
    private final PortalCache portalCache;

    /** Mốc cuối đã quét. Khởi tạo bằng thời điểm bean được dựng, không phải EPOCH. */
    private final AtomicReference<Instant> mocTruoc = new AtomicReference<>(Instant.now());

    public ScheduledPublishScanner(ArticleRepository articles, PortalCache portalCache) {
        this.articles = articles;
        this.portalCache = portalCache;
    }

    @Scheduled(fixedDelay = CHU_KY_MS, initialDelay = CHU_KY_MS)
    @Transactional(readOnly = true)
    public void quetBaiToiHan() {
        Instant den = Instant.now();
        Instant tu = mocTruoc.getAndSet(den);

        List<Article> toiHan = articles.findJustDue(tu, den);
        if (toiHan.isEmpty()) {
            return;
        }
        log.info("{} bài hẹn giờ vừa tới hạn — yêu cầu cổng dựng lại", toiHan.size());
        for (Article article : toiHan) {
            portalCache.articleChanged(article.getSlug());
        }
    }
}
