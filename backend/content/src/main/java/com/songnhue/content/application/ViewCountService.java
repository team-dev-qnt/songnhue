package com.songnhue.content.application;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.content.infra.ArticleRepository;

/**
 * Đếm lượt xem bài viết theo lô — T13.10, trả <b>nợ #64</b>.
 *
 * <h2>Vì sao gom lô thay vì UPDATE mỗi lượt xem</h2>
 *
 * Một lượt xem là một {@code UPDATE} vào đúng một dòng. Trang chủ có vài bài nóng, nên mọi lượt truy
 * cập dồn vào cùng vài dòng đó: khoá dòng nối đuôi nhau, mỗi lượt xem sinh một bản ghi WAL, và bảng
 * {@code articles} phình vì Postgres ghi phiên bản mới cho mỗi lần cập nhật. Cái giá đó trả cho một
 * con số mà không ai dùng để ra quyết định.
 *
 * <h2>Cái được và cái mất, nói rõ</h2>
 *
 * <ul>
 *   <li><b>Được</b>: một lượt xem là một phép cộng trong bộ nhớ; xuống CSDL mỗi phút một lần, gộp
 *       mọi bài thành vài dòng {@code UPDATE}.
 *   <li><b>Mất</b>: khởi động lại đột ngột thì mất tối đa một phút đếm. Chấp nhận có ý thức — cột
 *       {@code view_count} <b>là số xấp xỉ</b>, ghi rõ ngay trong comment của migration, và không
 *       được dùng cho kiểm toán hay báo cáo cần chính xác.
 * </ul>
 *
 * <p>{@code @PreDestroy} đẩy nốt phần còn lại khi tắt máy có trật tự, nên chỉ lần chết đột ngột mới
 * mất.
 *
 * <h2>⚠ Vì sao KHÔNG dùng bảng {@code settings} cho chu kỳ đẩy</h2>
 *
 * {@code @Scheduled} chốt chu kỳ lúc dựng bean, nên một tham số sửa được trên giao diện mà đổi xong
 * không có tác dụng gì cho tới lần khởi động lại là <b>đúng loại công tắc chết</b> đã trả giá ở
 * WS-12. Chu kỳ đẩy cũng không phải tham số nghiệp vụ — không có nghiệp vụ nào phụ thuộc vào nó.
 */
@Service
public class ViewCountService {

    private static final Logger log = LoggerFactory.getLogger(ViewCountService.class);

    private static final long CHU_KY_DAY_MS = 60_000L;

    private final ArticleRepository articles;

    /**
     * ⚠⚠ {@link TransactionTemplate} chứ <b>không</b> phải {@code @Transactional} — xem tài liệu của
     * {@link #day()}.
     */
    private final TransactionTemplate transactions;

    /** Khoá là id nội bộ của bài. {@link LongAdder} chịu được nhiều luồng cộng cùng lúc. */
    private final Map<Long, LongAdder> dem = new ConcurrentHashMap<>();

    public ViewCountService(ArticleRepository articles, TransactionTemplate transactions) {
        this.articles = articles;
        this.transactions = transactions;
    }

    /**
     * Ghi nhận một lượt xem.
     *
     * <p>Không kiểm trùng theo phiên hay theo IP: lượt xem ở đây là <i>lượt tải trang</i>, đúng như
     * cột {@code view_count} tự nhận là số xấp xỉ. Chống trùng cho tử tế cần lưu trạng thái theo
     * người xem, mà cổng công khai thì không có người xem nào đăng nhập.
     *
     * @param slug bài đang xem; slug lạ thì bỏ qua, không ném — người xem không cần biết
     */
    public void record(String slug) {
        articles.findIdBySlug(slug)
                .ifPresent(id -> dem.computeIfAbsent(id, k -> new LongAdder()).increment());
    }

    /** Số đang chờ đẩy của một bài — chỉ dùng cho bài kiểm và cho việc chẩn đoán. */
    public long dangCho(Long articleId) {
        LongAdder adder = dem.get(articleId);
        return adder == null ? 0 : adder.sum();
    }

    @Scheduled(fixedDelay = CHU_KY_DAY_MS, initialDelay = CHU_KY_DAY_MS)
    public void dayXuongDinhKy() {
        day();
    }

    @PreDestroy
    public void dayKhiTat() {
        day();
    }

    /**
     * Đẩy phần đang gom xuống CSDL.
     *
     * <p>⚠ Lấy số ra bằng {@code sumThenReset()} rồi mới ghi. Đọc-rồi-xoá-riêng thì lượt xem rơi vào
     * khoảng giữa hai thao tác biến mất; {@code sumThenReset} là một thao tác nguyên tử của
     * {@link LongAdder}.
     *
     * <p>Nếu lượt ghi hỏng thì số đó mất — cố ý không cộng ngược lại vào bộ đếm. Cộng ngược là mở
     * đường cho một lỗi CSDL kéo dài làm bộ nhớ phình lên mãi, để đổi lấy độ chính xác của một con
     * số vốn đã là xấp xỉ.
     *
     * <h2>⚠⚠ Vì sao {@link TransactionTemplate} chứ không phải {@code @Transactional}</h2>
     *
     * Bản đầu đặt {@code @Transactional} ngay trên hàm này và <b>chưa từng ghi được một lượt xem
     * nào</b>. {@link #dayXuongDinhKy()} gọi {@code day()} bằng {@code this}, tức là gọi thẳng vào
     * đối tượng gốc chứ không qua proxy của Spring, nên chú thích giao dịch không có tác dụng và
     * {@code @Modifying} ném {@code TransactionRequiredException} mỗi phút một lần — trong log của
     * bộ hẹn giờ, không phải trong log của một request nào.
     *
     * <p>Bài kiểm cũ xanh vì nó gọi thẳng {@code day()} trên bean đã được bọc proxy, tức là đi một
     * <i>đường khác</i> với đường mà production đi. Nay bài kiểm gọi {@link #dayXuongDinhKy()} —
     * đúng cửa mà bộ hẹn giờ gọi.
     *
     * <p>Đây là lần thứ hai của dự án ({@code BackupService}, WS-7). Cách chữa cũng như lần trước:
     * mở giao dịch bằng tay, để việc "có giao dịch hay không" không phụ thuộc vào ai gọi từ đâu.
     */
    public void day() {
        if (dem.isEmpty()) {
            return;
        }
        Map<Long, Long> loDay = new HashMap<>();
        for (Map.Entry<Long, LongAdder> entry : dem.entrySet()) {
            long value = entry.getValue().sumThenReset();
            if (value > 0) {
                loDay.put(entry.getKey(), value);
            }
        }
        if (loDay.isEmpty()) {
            return;
        }
        int soDong = transactions.execute(status -> {
            int dong = 0;
            for (Map.Entry<Long, Long> entry : loDay.entrySet()) {
                dong += articles.addViews(entry.getKey(), entry.getValue());
            }
            return dong;
        });
        log.debug("Đẩy lượt xem: {} bài, {} dòng cập nhật", loDay.size(), soDong);
    }
}
