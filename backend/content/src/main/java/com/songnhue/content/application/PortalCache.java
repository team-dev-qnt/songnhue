package com.songnhue.content.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;

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
public class PortalCache {

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

    /** Menu, banner hoặc cấu hình nhận diện vừa đổi — chúng nằm trên mọi trang. */
    public void layoutChanged() {
        datViec("{\"tag\":\"%s\"}".formatted(TAG_LAYOUT), "tag:" + TAG_LAYOUT);
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
