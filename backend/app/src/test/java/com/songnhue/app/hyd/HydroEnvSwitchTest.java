package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⭐⭐ Hai công tắc <b>nới bảo mật</b> của MOD-03 phải TẮT ở mọi môi trường ngoài máy lập trình viên.
 *
 * <h2>Vì sao một công tắc như thế cần một bộ canh riêng</h2>
 *
 * <p>Cả hai đều là <i>cửa mở có chủ đích</i>, và cửa mở có chủ đích là loại nợ tệ nhất: nó được thêm
 * vào với một lý do đúng, có javadoc giải thích, và rồi <b>không ai đọc lại</b>. §10.57 là chuyện một
 * cổng secret bỏ qua trong im lặng làm CD Production xanh trọn vẹn mà không byte nào chạm máy chủ —
 * cùng hình dạng: <i>một cơ chế đúng luật, hẹp hơn hoặc rộng hơn nơi nó phải chặn, mà cái xanh của nó
 * đọc như một lời bảo đảm</i>.
 *
 * <ul>
 *   <li>{@code HYDRO_API_ALLOW_INTERNAL_HOST} — nới bộ chặn SSRF cho {@code 127.0.0.1}/{@code 10.*}.
 *       Bật ở prod nghĩa là một dòng {@code api_sources.base_url} sai (tài khoản bị chiếm, bản khôi
 *       phục cũ, một câu {@code UPDATE} tay lúc xử lý sự cố) biến poller thành công cụ đọc endpoint
 *       metadata của máy ảo.
 *   <li>{@code HYDRO_API_MOCK} — bật nguồn GIẢ. Dữ liệu nó sinh ra đi vào <b>cùng bảng</b> với dữ
 *       liệu thật; bảo vệ ở tầng cấu trúc là mã của nó mang tiền tố {@code Z} nên không khớp
 *       {@code CHECK ^F[0-9]{5}$} của {@code stations.api_code}. Nhưng ⛔ "cấu trúc chặn được" không
 *       phải lý do để nó chạy ở nơi nghiệm thu — §10.54: 19 bài viết, 4 văn bản và 5 trạm thuỷ văn
 *       bịa đã lên staging và không màn hình nào phân biệt được.
 * </ul>
 *
 * <h2>⚠ Vế chống tập rỗng — luật 7</h2>
 *
 * <p>Một bài kiểm quét tệp mà không tìm thấy tệp nào thì <b>xanh trọn vẹn</b> và không nói gì. Ba
 * khẳng định dưới đây vì thế luôn đi kèm một khẳng định <b>về số lượng</b>: đủ tệp được soi, và tệp
 * ấy thật sự có nội dung.
 */
class HydroEnvSwitchTest {

    /** Tệp env của <b>mọi môi trường không phải máy lập trình viên</b>. */
    private static final List<String> TEP_NGOAI_LOCAL = List.of(
            "deploy/env/staging.env",
            "deploy/env/staging.env.example",
            "deploy/env/prod.env.example",
            "deploy/env/rehearse.env");

    private static final List<String> CONG_TAC = List.of("HYDRO_API_ALLOW_INTERNAL_HOST", "HYDRO_API_MOCK");

    /** {@code TÊN=true} / {@code =1} / {@code =yes} — bỏ qua chú thích và khoảng trắng. */
    private static Pattern batCongTac(String ten) {
        return Pattern.compile("^\\s*" + ten + "\\s*=\\s*(true|1|yes|on)\\s*(#.*)?$", Pattern.CASE_INSENSITIVE);
    }

    @Test
    @DisplayName("⛔⛔ Không tệp env nào của staging/prod/rehearse BẬT hai công tắc nới bảo mật")
    void khongMoiTruongNaoNgoaiLocalBatCongTac() {
        int soTepDaSoi = 0;
        int soDongDaSoi = 0;

        for (String ten : TEP_NGOAI_LOCAL) {
            Path tep = timTuGocKho(ten);
            if (tep == null) {
                continue; // staging.env / rehearse.env không có ở mọi bản clone
            }
            soTepDaSoi++;
            List<String> dong = doc(tep).lines().toList();
            soDongDaSoi += dong.size();
            for (String cong : CONG_TAC) {
                assertThat(dong)
                        .as(
                                """
                                `%s` BẬT %s.

                                Đây là công tắc nới bảo mật, mặc định TẮT, và nó chỉ hợp lệ trên máy \
                                lập trình viên. Đọc javadoc của HydroApiProperties trước khi gỡ dòng này.""",
                                tep.getFileName(), cong)
                        .noneMatch(d -> batCongTac(cong).matcher(d).matches());
            }
        }

        assertThat(soTepDaSoi)
                .as("⚠ Vế chống tập rỗng: soi 0 tệp cũng xanh trọn vẹn. Ít nhất staging.env.example và "
                        + "prod.env.example phải có mặt trong mọi bản clone.")
                .isGreaterThanOrEqualTo(2);
        assertThat(soDongDaSoi)
                .as("tệp có mặt nhưng RỖNG cũng cho ra 0 vi phạm — đo cả nội dung")
                .isGreaterThan(100);
    }

    @Test
    @DisplayName("⭐ Bộ canh BẮT được vi phạm — kiểm chứng mẫu trên chuỗi dựng tay (luật 1)")
    void boCanhBatDuocViPham() {
        List<String> viPham = List.of(
                "HYDRO_API_MOCK=true",
                "  HYDRO_API_MOCK = TRUE  ",
                "HYDRO_API_ALLOW_INTERNAL_HOST=1",
                "HYDRO_API_ALLOW_INTERNAL_HOST=yes   # tạm bật để thử",
                "HYDRO_API_MOCK=on");
        List<String> khongViPham = List.of(
                "HYDRO_API_MOCK=false",
                "HYDRO_API_MOCK=",
                "# HYDRO_API_MOCK=true",
                "#HYDRO_API_ALLOW_INTERNAL_HOST=true",
                "HYDRO_API_MOCK_NOTES=true");

        long batDuoc = viPham.stream()
                .filter(d ->
                        CONG_TAC.stream().anyMatch(c -> batCongTac(c).matcher(d).matches()))
                .count();
        long batNham = khongViPham.stream()
                .filter(d ->
                        CONG_TAC.stream().anyMatch(c -> batCongTac(c).matcher(d).matches()))
                .count();

        assertThat(batDuoc)
                .as("⛔ Một cơ chế canh gác phải có bài kiểm chứng minh nó BẮT ĐƯỢC vi phạm "
                        + "(conventions.md §1.5) — dự án đã có 5 cơ chế xanh mà không chạy")
                .isEqualTo(viPham.size());
        assertThat(batNham)
                .as("⚠ Vế còn lại: một bộ canh bắt nhầm dòng chú thích sẽ bị người sau nới ra, và "
                        + "lúc nới thì nới quá tay")
                .isZero();
    }

    @Test
    @DisplayName("⚠ Hai công tắc phải có mặt trong application.yml — neo tên biến vào chỗ nó thật sự được đọc")
    void congTacDuocKhaiTrongApplicationYml() {
        String yml = doc(timTuGocKho("backend/app/src/main/resources/application.yml"));

        for (String cong : CONG_TAC) {
            assertThat(yml)
                    .as("⚠ Nếu tên biến ở application.yml đổi mà bài kiểm này không đổi theo, bộ canh "
                            + "trên vẫn XANH trong khi nó đang canh một cái tên không còn ai đọc — "
                            + "đúng hình dạng luật 2: canh cấu trúc, đừng canh văn bản")
                    .contains(cong);
        }
        assertThat(yml)
                .as("⛔ Mặc định trong application.yml phải là false ở CẢ HAI — đây là nơi giá trị "
                        + "được GIẢI khi tệp env không nói gì (§10.29-a)")
                .contains("${HYDRO_API_ALLOW_INTERNAL_HOST:false}")
                .contains("${HYDRO_API_MOCK:false}");
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return {@code null} nếu tệp không có ở bản clone này — người gọi tự quyết định có bỏ qua không */
    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        if (duongDanTuongDoi.endsWith(".example") || duongDanTuongDoi.endsWith(".yml")) {
            return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
        }
        return null;
    }
}
