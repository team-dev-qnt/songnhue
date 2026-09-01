package com.songnhue.content.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.PortalCachePort;

/**
 * Đặt việc "dựng lại trang công khai" vào hàng đợi — T16.5.
 *
 * <p>Một lớp mỏng, nhưng nó tồn tại để giữ <b>một chỗ duy nhất</b> biết cấu trúc payload và tên nhãn
 * cache. Rải {@code jobs.enqueue("CMS_PORTAL_REVALIDATE", "{\"path\":…}")} ở bốn nơi thì lần đổi cấu
 * trúc đường dẫn đầu tiên sẽ sót một chỗ, và triệu chứng là "có một loại thay đổi không lên cổng" —
 * rất khó truy.
 *
 * <p>⚠ Tên nhãn ở đây phải khớp với nhãn mà trang Next gắn vào {@code fetch}. Hai nơi phải nhớ cùng
 * một chuỗi, nên chuỗi đó nằm trong hằng số có tài liệu ở cả hai phía.
 */
@Component
public class PortalCache implements PortalCachePort {

    private static final Logger log = LoggerFactory.getLogger(PortalCache.class);

    /** Danh sách bài, trang chủ, trang danh mục — mọi chỗ liệt kê bài viết. */
    public static final String TAG_ARTICLES = "bai-viet";

    /** Menu, banner, cấu hình nhận diện — thứ nằm trên mọi trang. */
    public static final String TAG_LAYOUT = "giao-dien";

    private final JobPort jobs;

    public PortalCache(JobPort jobs) {
        this.jobs = jobs;
    }

    /**
     * Một bài vừa đổi trạng thái công khai.
     *
     * <p>Dựng lại <b>cả</b> trang chi tiết lẫn nhãn danh sách: xuất bản một bài không chỉ tạo ra một
     * trang mới, nó còn làm đổi trang chủ và trang danh mục. Quên vế thứ hai thì bài mới có địa chỉ
     * riêng nhưng không ai tìm thấy đường vào nó.
     */
    public void articleChanged(String slug) {
        datViec("{\"path\":\"/bai-viet/%s\"}".formatted(slug), "bai:" + slug);
        datViec("{\"tag\":\"%s\"}".formatted(TAG_ARTICLES), "tag:" + TAG_ARTICLES);
    }

    /**
     * Sơ đồ tổ chức / lãnh đạo / Xí nghiệp — nhãn {@code to-chuc} của {@code lib/api.ts}.
     *
     * <p>⚠ Hai nơi phải nhớ cùng một chuỗi, nên chuỗi nằm trong hằng số có tài liệu ở cả hai phía —
     * y như {@link #TAG_ARTICLES}. Đổi nhãn ở một phía là một loại thay đổi <b>không lên cổng</b>,
     * và không có gì báo.
     */
    public static final String TAG_TO_CHUC = "to-chuc";

    /** Danh mục công trình và tình hình vận hành — nhãn {@code cong-trinh} của {@code lib/api.ts}. */
    public static final String TAG_CONG_TRINH = "cong-trinh";

    /**
     * {@inheritDoc}
     *
     * <p>Gửi <b>cả nhãn lẫn đường dẫn trang chủ</b>. Nhãn lo ba trang {@code /gioi-thieu/*}; đường
     * dẫn lo trang chủ, vì §10.17 đã đo được: một lượt {@code fetch} hỏng thì <b>không mục cache nào
     * mang nhãn được tạo ra</b>, nên {@code revalidateTag} không có gì để lần ngược. Trang chủ là
     * trang duy nhất trong nhóm này từng ra đời rỗng sau một lượt triển khai.
     */
    @Override
    public void orgUnitsChanged() {
        datViec("{\"tag\":\"%s\"}".formatted(TAG_TO_CHUC), "tag:" + TAG_TO_CHUC);
        datViec("{\"path\":\"/\"}", "duong-dan:/");
    }

    /** {@inheritDoc} */
    @Override
    public void constructionsChanged() {
        datViec("{\"tag\":\"%s\"}".formatted(TAG_CONG_TRINH), "tag:" + TAG_CONG_TRINH);
        datViec("{\"path\":\"/\"}", "duong-dan:/");
    }

    /**
     * Menu, banner hoặc cấu hình nhận diện vừa đổi — chúng nằm trên mọi trang.
     *
     * <h2>⛔⛔ 01/09/2026 — phương thức này từng có ĐÚNG MỘT lần xuất hiện trong toàn kho</h2>
     *
     * Chính định nghĩa của nó. <b>Không một nơi gọi nào</b> — không ở {@code main}, không ở
     * {@code test}, không ở tài liệu. Nhãn {@code giao-dien} thì vẫn được {@code lib/api.ts} gắn
     * vào {@code getSiteConfig}, {@code getMenu} và {@code getBanners}, tức đầu nhận có sẵn còn
     * đầu phát chưa từng bấm. Đó là nợ T25.22/T27.7 ở dạng thuần khiết nhất và là đúng hình dạng
     * quy tắc 27: <i>một nửa vòng đọc–ghi chạy hoàn hảo vẫn cho ra số không</i>. Triệu chứng của
     * nó im như mọi lần trước — màn hình quản trị báo <i>"Đã lưu"</i>, cổng không đổi gì trong
     * tối đa 300 giây (chu kỳ ISR), rồi tự đúng lại; không lỗi nào, không dấu vết nào.
     *
     * <p>Nay {@link SiteConfigService#onSettingChanged} gọi nó, và
     * {@code CongTacTrangChuTest} đếm hàng bảng {@code jobs} để chứng minh lời gọi ấy còn sống.
     *
     * <h2>⭐ Gửi CẢ nhãn lẫn đường dẫn trang chủ — cùng lý do với hai phương thức anh em</h2>
     *
     * §10.17 đã đo được: một lượt {@code fetch} hỏng thì <b>không mục cache nào mang nhãn được
     * tạo ra</b>, nên {@code revalidateTag} không có gì để lần ngược. Trang chủ là trang duy nhất
     * trong nhóm này từng ra đời rỗng sau một lượt triển khai — và nó cũng chính là trang mà công
     * tắc {@code site.home.show-dieu-hanh} bật/tắt. Chỉ gửi nhãn là chấp nhận một xác suất im
     * lặng ngay tại chỗ đắt nhất.
     */
    public void layoutChanged() {
        datViec("{\"tag\":\"%s\"}".formatted(TAG_LAYOUT), "tag:" + TAG_LAYOUT);
        datViec("{\"path\":\"/\"}", "duong-dan:/");
    }

    /**
     * Hâm nóng cổng sau khi backend khởi động — đóng "cửa sổ trang trắng" sau mỗi lần deploy.
     *
     * <h2>⚠⚠ Vì sao phải là ĐƯỜNG DẪN, không phải nhãn</h2>
     *
     * Ảnh Docker của cổng được dựng ở CI, lúc đó <b>không có backend nào chạy</b>. Next vẫn dựng
     * sẵn trang chủ thành HTML tĩnh — với nội dung rỗng, vì mọi lượt gọi API đều hỏng.
     *
     * <p>Và đây là phần không hiển nhiên: lượt gọi hỏng thì <b>không có mục cache nào mang nhãn
     * được tạo ra</b>, nên {@code revalidateTag} về sau không có gì để lần ngược tới trang đó. Bản
     * HTML rỗng nằm nguyên cho tới khi hết chu kỳ ISR (5 phút). Chỉ {@code revalidatePath} mới trỏ
     * thẳng vào bộ nhớ đệm của tuyến đường và xoá được nó. Đo thật ở WS-16: gửi nhãn hai lần →
     * trang vẫn rỗng; gửi đường dẫn → trang có nội dung ngay.
     *
     * <h2>Vì sao đi qua hàng đợi chứ không gọi thẳng</h2>
     *
     * Lúc backend sẵn sàng thì cổng thường <i>chưa</i> — compose để {@code public-web} chờ
     * {@code app} khoẻ. Gọi thẳng là hỏng chắc chắn; qua hàng đợi thì lượt thử lại lo phần đó, và
     * đó đúng là việc hàng đợi sinh ra để làm.
     */
    public void warmUp() {
        // ⚠ Gồm cả `/sitemap.xml`: nó cũng là trang dựng sẵn lúc build, nên cũng ra đời rỗng —
        // và một sitemap chỉ có mỗi trang chủ là công cụ tìm kiếm không thấy bài nào của cổng.
        for (String duongDan : List.of("/", "/sitemap.xml")) {
            // 10 lần thử: cổng khởi động sau backend, và worker lấy việc mỗi 5 giây.
            jobs.enqueue(new JobRequest(
                    CmsJobTypes.PORTAL_REVALIDATE,
                    "{\"path\":\"%s\"}".formatted(duongDan),
                    "warmup:" + duongDan,
                    (short) 10));
        }
        log.info("Đặt việc hâm nóng cổng công khai sau khi khởi động");
    }

    /**
     * @param dedupKey nhiều lượt sửa liên tiếp trong lúc việc cũ còn chờ thì gộp làm một — dựng lại
     *     cùng một trang năm lần liên tiếp không cho kết quả khác lần thứ nhất
     */
    private void datViec(String payload, String dedupKey) {
        jobs.enqueue(new JobRequest(CmsJobTypes.PORTAL_REVALIDATE, payload, dedupKey, (short) 5));
        log.debug("Đặt việc dựng lại cổng: {}", payload);
    }
}
