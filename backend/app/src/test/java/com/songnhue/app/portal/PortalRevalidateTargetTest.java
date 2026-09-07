package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.application.PortalCache;

/**
 * <b>Mọi đích {@code revalidate} mà backend gửi phải TỒN TẠI ở cổng</b> — T35.9.
 *
 * <h2>⛔⛔ Vì sao một đích gõ sai ⛔ KHÔNG có triệu chứng nào</h2>
 *
 * <p>{@code revalidatePath("/duong-dan-khong-co-that")} của Next trả về <b>bình thường</b> — nó
 * ⛔ không tra xem tuyến ấy có thật. {@code revalidateTag("nhan-go-sai")} cũng vậy: nó xoá đúng
 * <i>không mục nào</i> và báo thành công. Nên một ký tự thừa trong hằng số ở {@code PortalCache}
 * cho ra <b>chính xác cùng một dòng log</b> như một lượt xoá đệm đúng, và cái sai chỉ lộ ra dưới
 * dạng <i>"có một loại thay đổi không lên cổng"</i> — thứ khó truy nhất trong toàn bộ hệ.
 *
 * <p>⇒ Đúng hình dạng luật 14: <b>hai nơi phải nhớ cùng một chuỗi</b> (hằng số Java ↔ thư mục tuyến
 * của Next, hằng số Java ↔ nhãn của {@code lib/api.ts}), và chỗ nào con người phải nhớ hai nơi thì
 * chỗ đó cần một phép kiểm nhớ hộ.
 *
 * <p>📌 Cùng họ với bốn cặp đã có bộ canh: enum SPI ↔ enum domain · từ vựng trình soạn thảo ↔ danh
 * sách cho phép của bộ lọc · mã lỗi BE ↔ FE · URL tile ↔ CSP.
 */
class PortalRevalidateTargetTest {

    private static final String GOC_CONG = "frontend/public-web/src";

    /**
     * ⭐ Đích <b>đường dẫn</b> phải là một tuyến có thật.
     *
     * <p>App Router của Next ánh xạ {@code /a/b} ↔ {@code src/app/a/b/page.tsx}. Bài này đối chiếu
     * với <b>cây thư mục thật</b>, ⛔ không với một danh sách chép tay — một danh sách chép tay là
     * đúng cái chỗ thứ ba mà con người phải nhớ.
     */
    @Test
    @DisplayName("⭐ Mọi đường dẫn `PortalCache` gửi đi đều là một tuyến CÓ THẬT của cổng")
    void everyRevalidatePathIsARealRoute() {
        List<String> duongDan = List.of(PortalCache.DUONG_DAN_MUC_NUOC);

        assertThat(duongDan)
                .as("⚠ vế chống tập rỗng (luật 7): danh sách rỗng làm vòng lặp dưới đây xanh mà ⛔ không kiểm gì")
                .isNotEmpty();

        for (String dd : duongDan) {
            Path tuyen = timTuGocKho(GOC_CONG + "/app" + dd + "/page.tsx");
            assertThat(Files.exists(tuyen))
                    .as(
                            """
                            ⛔ `%s` ⛔ không có `page.tsx` nào đứng sau. `revalidatePath` với một tuyến \
                            không tồn tại vẫn báo thành công, nên lỗi này ⛔ KHÔNG có triệu chứng — chỉ có \
                            một trang mãi mãi không được dựng lại.""",
                            dd)
                    .isTrue();
        }
    }

    /**
     * ⭐ Đích <b>nhãn</b> phải là nhãn mà cổng thật sự gắn vào {@code fetch}.
     *
     * <p>{@code lib/api.ts} là nơi duy nhất gắn nhãn; nhãn nào ⛔ không xuất hiện ở đó thì
     * {@code revalidateTag} ⛔ không có gì để lần ngược.
     */
    @Test
    @DisplayName("⭐ Mọi nhãn `PortalCache` gửi đi đều được `lib/api.ts` gắn vào một lượt fetch")
    void everyRevalidateTagIsAttachedByTheFrontend() {
        String api = doc(GOC_CONG + "/lib/api.ts");
        List<String> nhan = List.of(
                PortalCache.TAG_ARTICLES,
                PortalCache.TAG_LAYOUT,
                PortalCache.TAG_TO_CHUC,
                PortalCache.TAG_CONG_TRINH,
                PortalCache.TAG_THUY_VAN);

        assertThat(nhan).as("⚠ vế chống tập rỗng").hasSizeGreaterThanOrEqualTo(5);

        for (String n : nhan) {
            assertThat(api)
                    .as(
                            """
                            ⛔ Nhãn `%s` ⛔ không xuất hiện trong `lib/api.ts`. Backend xoá một nhãn mà cổng \
                            chưa từng gắn = xoá đúng KHÔNG MỤC NÀO, và báo thành công. Đầu phát có, đầu nhận \
                            không — nửa cặp đọc–ghi ở dạng khó thấy nhất (luật 27).""",
                            n)
                    .contains("'" + n + "'");
        }
    }

    /**
     * ⚠ Vế kiểm chứng chính bộ canh: nếu {@link #doc} hay {@link #timTuGocKho} đọc nhầm chỗ thì hai
     * bài trên xanh vì lý do sai. Một chuỗi <b>chắc chắn không có</b> phải làm khẳng định đỏ.
     */
    @Test
    @DisplayName("⚠ Tự kiểm: bộ canh này ĐỌC ĐƯỢC tệp thật — một nhãn bịa phải KHÔNG khớp")
    void theGuardItselfReadsRealFiles() {
        String api = doc(GOC_CONG + "/lib/api.ts");

        assertThat(api).as("đọc đúng tệp thì phải thấy hàm dựng URL của cổng").contains("export");
        assertThat(api)
                .as("một nhãn bịa ⛔ không được khớp — nếu nó khớp thì phép so đang so trên chuỗi rỗng")
                .doesNotContain("'nhan-bia-khong-bao-gio-ton-tai'");
    }

    // -------------------------------------------------------------------------

    private static String doc(String duongDanTuongDoi) {
        try {
            return Files.readString(timTuGocKho(duongDanTuongDoi), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
        }
    }

    /**
     * ⚠ Đi ngược lên từ {@code user.dir}: Maven chạy bài này với thư mục làm việc là
     * {@code backend/app}, còn IDE thì thường là gốc kho. Ghi cứng một đường dẫn tương đối là dựng
     * một bài kiểm chỉ chạy được ở một trong hai chỗ.
     *
     * <p>⛔ Trả về đường dẫn kể cả khi ⛔ không tồn tại ở bước cuối — nơi gọi tự quyết định đó là
     * lỗi hay không, vì {@link #everyRevalidatePathIsARealRoute()} cần câu <i>"không tồn tại"</i>
     * làm một khẳng định đọc được chứ ⛔ không phải một ngoại lệ.
     */
    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path gocKho = null;
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.exists(hienTai.resolve(GOC_CONG))) {
                gocKho = hienTai;
                break;
            }
            hienTai = hienTai.getParent();
        }
        if (gocKho == null) {
            return fail("Không tìm thấy %s tính từ %s".formatted(GOC_CONG, System.getProperty("user.dir")));
        }
        return gocKho.resolve(duongDanTuongDoi);
    }
}
