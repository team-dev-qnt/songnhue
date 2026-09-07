package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ContactInboxService;
import com.songnhue.content.application.ContactService;
import com.songnhue.content.application.FeedbackService;
import com.songnhue.content.application.InboundSubmissionGate;

/**
 * <b>⛔⛔ MỌI đường ghi công khai phải đi qua {@link InboundSubmissionGate}.</b> T36.9.
 *
 * <h2>Vì sao bài này tồn tại — và vì sao nó ĐẾM thay vì liệt kê</h2>
 *
 * <p>Luật 12 có một hoá đơn cụ thể: XSS lưu trữ lọt qua <b>2/3</b> đường ghi {@code settings} vì
 * bảo đảm được đặt ở hai trong ba <i>nơi gọi</i> thay vì ở <i>chỗ dữ liệu đi qua</i>. Và T27.7 lặp
 * lại đúng nó theo chiều thời gian: ba điểm ghi được nối, điểm ghi <b>thứ tư ra đời cùng đợt</b>
 * mang lại đúng lỗi cũ.
 *
 * <p>⇒ Một bài kiểm liệt kê "ContactService và FeedbackService đều dùng cổng" ⛔ <b>không</b> bắt
 * được điểm thứ ba. Bài này vì thế <b>đo</b> tập đường ghi công khai từ chính bảng định tuyến của
 * Spring, rồi đòi mỗi đường phải được <b>phân loại</b> ở {@link #PHAN_LOAI}. Một endpoint công khai
 * mới mà ⛔ không ai phân loại làm bài này <b>ĐỎ</b> — nó ép người viết trả lời câu hỏi, chứ ⛔
 * không im lặng cho qua.
 *
 * <h2>⚠ Bất biến ⛔ KHÔNG phải "mọi đường ghi công khai đều qua cổng"</h2>
 *
 * <p>Đo được: {@code POST /api/v1/public/articles/{slug}/views} cũng là một đường ghi công khai,
 * và nó ⛔ <b>không</b> nhận một ký tự nào do người dùng gõ — thân rỗng, tham số là một slug đã có
 * trong CSDL. Bắt nó đi qua cổng là dựng một nghi thức ⛔ không bảo vệ gì.
 *
 * <p>Bất biến thật: <b>đường ghi công khai nào nhận CHỮ TỰ DO thì phải qua cổng</b>; đường ⛔ không
 * nhận chữ tự do thì phải nói ra điều đó. Hai nhãn ở {@link Loai} là đúng hai câu trả lời ấy.
 *
 * <h2>⭐ Kiểm bằng KIỂU của field, ⛔ không bằng chuỗi trong mã nguồn</h2>
 *
 * <p>Luật 2: canh cấu trúc, đừng canh văn bản. Một {@code grep "InboundSubmissionGate"} vẫn xanh
 * khi lớp còn giữ field mà ⛔ không gọi nó nữa, và cũng xanh khi tên lớp chỉ nằm trong một dòng
 * javadoc. Phản chiếu {@code getDeclaredFields()} thì ⛔ không có chỗ cho một chú thích nói dối.
 */
class InboundWritePathTest extends IntegrationTestBase {

    private static final String TIEN_TO_CONG_KHAI = "/api/v1/public";

    private static final Set<String> PHUONG_THUC_GHI = Set.of("POST", "PUT", "PATCH", "DELETE");

    private enum Loai {
        /** Nhận chữ tự do từ người lạ ⇒ <b>bắt buộc</b> có {@link InboundSubmissionGate}. */
        QUA_CONG,
        /** ⛔ Không nhận một ký tự tự do nào — phải kèm lý do ở {@link #PHAN_LOAI}. */
        KHONG_NHAN_CHU,
    }

    private record Khai(Loai loai, Class<?> service, String lyDo) {}

    /**
     * ⚠⚠ <b>Thêm một endpoint ghi công khai thì phải thêm một dòng ở đây.</b>
     *
     * <p>⛔ Đừng thêm dòng "cho hết đỏ": mỗi dòng là một câu trả lời cho <i>"chữ do người lạ gõ đi
     * vào bằng đường nào, và ai gác nó"</i>. Nếu câu trả lời là {@code KHONG_NHAN_CHU} thì lý do
     * phải kiểm chứng được bằng cách đọc chữ ký của phương thức.
     */
    private static final Map<String, Khai> PHAN_LOAI = new LinkedHashMap<>();

    static {
        PHAN_LOAI.put(
                "POST " + TIEN_TO_CONG_KHAI + "/contacts",
                new Khai(Loai.QUA_CONG, ContactService.class, "Biểu mẫu liên hệ — CN-01.4"));
        PHAN_LOAI.put(
                "POST " + TIEN_TO_CONG_KHAI + "/feedbacks",
                new Khai(Loai.QUA_CONG, FeedbackService.class, "Biểu mẫu góp ý — CN-01.6"));
        PHAN_LOAI.put(
                "POST " + TIEN_TO_CONG_KHAI + "/articles/{slug}/views",
                new Khai(
                        Loai.KHONG_NHAN_CHU,
                        null,
                        "Thân RỖNG; tham số duy nhất là `slug` — một khoá đã có trong CSDL, ⛔ không "
                                + "phải chữ người dùng gõ. Nó chỉ tăng một bộ đếm."));
    }

    /**
     * ⚠ Phải khai {@code @Qualifier}: ngữ cảnh có <b>hai</b> bean cùng kiểu —
     * {@code requestMappingHandlerMapping} (của MVC) và {@code controllerEndpointHandlerMapping}
     * (của Actuator). Thiếu nó thì lớp này ⛔ không dựng được, và một bài kiểm ⛔ không dựng được
     * đọc như một bài kiểm ⛔ không tồn tại (luật 31: sự vắng mặt nguy hiểm hơn màu đỏ).
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping duongDan;

    @Test
    @DisplayName("⛔⛔ MỌI đường ghi công khai đều được phân loại — endpoint mới ⛔ không đi lọt")
    void moiDuongGhiCongKhaiDeuDuocPhanLoai() {
        Set<String> doDuoc = duongGhiCongKhai();

        assertThat(doDuoc)
                .as("⚠ vế chống tập rỗng (luật 7): ⛔ không tìm thấy đường ghi công khai nào thì "
                        + "bài này ⛔ không kiểm gì cả — và nó sẽ xanh trọn vẹn")
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(doDuoc)
                .as("⛔⛔ Có đường ghi công khai ⛔ CHƯA ai phân loại. Đây chính là hình dạng T27.7: "
                        + "ba điểm ghi được nối, điểm ghi THỨ TƯ ra đời cùng đợt mang lại đúng lỗi "
                        + "cũ. Thêm một dòng vào `PHAN_LOAI` và trả lời: chữ do người lạ gõ có đi "
                        + "qua đường này ⛔ không?")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(PHAN_LOAI.keySet()));
    }

    @Test
    @DisplayName("⛔⛔ Mọi service nhận chữ tự do đều GIỮ một `InboundSubmissionGate` — kiểm bằng KIỂU")
    void serviceNhanChuTuDoDeuQuaCong() {
        Set<Class<?>> qua = new LinkedHashSet<>();
        PHAN_LOAI.values().stream().filter(k -> k.loai() == Loai.QUA_CONG).forEach(k -> qua.add(k.service()));

        assertThat(qua)
                .as("⚠ vế chống tập rỗng: ⛔ không service nào được khai `QUA_CONG` thì vòng lặp "
                        + "dưới đây chạy 0 lần và bài này xanh mà ⛔ không khẳng định gì (luật 7)")
                .hasSizeGreaterThanOrEqualTo(2);

        for (Class<?> service : qua) {
            assertThat(coFieldKieu(service, InboundSubmissionGate.class))
                    .as(
                            "⛔⛔ `%s` nhận chữ do người lạ trên Internet gõ mà ⛔ KHÔNG giữ "
                                    + "`InboundSubmissionGate`. Chép bốn bảo đảm vào đây là dựng bản sao "
                                    + "thứ hai — và bản sao ấy sẽ lệch ở lượt sửa đầu tiên (luật 12).",
                            service.getSimpleName())
                    .isTrue();
        }
    }

    /**
     * ⭐ Đường <b>cán bộ</b> cũng đi qua cổng — ⛔ không phải vì captcha, mà vì ba bảo đảm kia.
     *
     * <p>{@code ContactInboxService.themGhiChu} nhận chữ do một con người gõ. Ký tự điều khiển làm
     * hỏng bản xuất CSV và chèn được dòng giả vào nhật ký <b>bất kể ai gõ ra chúng</b> — một cán bộ
     * dán nội dung từ email vào ô ghi chú là chuyện xảy ra hằng ngày.
     *
     * <p>⚠ Bài này ⛔ không đòi mọi service quản trị phải có cổng; nó chỉ khoá <b>đúng một</b>
     * đường đã được nối, để một lượt "dọn dẹp phụ thuộc" về sau ⛔ không gỡ nó ra trong im lặng.
     */
    @Test
    @DisplayName("⭐ Ghi chú nội bộ (đường cán bộ) cũng qua cổng — ký tự điều khiển ⛔ không phân biệt ai gõ")
    void duongCanBoCungQuaCong() {
        assertThat(coFieldKieu(ContactInboxService.class, InboundSubmissionGate.class))
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Đọc thẳng bảng định tuyến của Spring — ⛔ không phải một danh sách viết tay. */
    private Set<String> duongGhiCongKhai() {
        Set<String> ket = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e :
                duongDan.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            Set<String> mau = info.getPatternValues();
            for (String duong : mau) {
                if (!duong.startsWith(TIEN_TO_CONG_KHAI)) {
                    continue;
                }
                info.getMethodsCondition().getMethods().stream()
                        .map(Enum::name)
                        .filter(PHUONG_THUC_GHI::contains)
                        .forEach(pt -> ket.add(pt + " " + duong));
            }
        }
        return ket;
    }

    private static boolean coFieldKieu(Class<?> lop, Class<?> kieu) {
        return Arrays.stream(lop.getDeclaredFields()).map(Field::getType).anyMatch(kieu::equals);
    }
}
