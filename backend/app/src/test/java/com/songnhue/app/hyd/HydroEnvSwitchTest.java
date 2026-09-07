package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // =========================================================================
    // ⭐⭐ T27.4 — bộ đối chiếu HAI CHIỀU: biến ĐƯỢC ĐỌC ↔ biến ĐƯỢC KHAI
    // =========================================================================

    /**
     * ⭐⭐ Mọi biến {@code HYDRO_*} phải <b>vừa có người đọc, vừa có nơi khai</b>.
     *
     * <h2>Vì sao {@code .contains(tên)} một chiều là chưa đủ — nợ T27.4</h2>
     *
     * <p>Bài {@link #congTacDuocKhaiTrongApplicationYml()} phía trên hỏi <i>"tên này có xuất hiện ở
     * {@code application.yml} không"</i> cho <b>hai</b> biến chép tay. Nó ⛔ không thấy:
     *
     * <ul>
     *   <li>biến thứ <b>ba</b> ({@code HYDRO_API_KEY}) — có thật, được đọc thật, ⛔ chưa từng được
     *       bài nào canh;
     *   <li>chiều <b>ngược lại</b>: một biến khai trong {@code deploy/env/*} mà ⛔ không ai đọc.
     *       Đó là luật 15 ở dạng nguy hiểm nhất của nó — người vận hành đặt giá trị, đọc lại thấy
     *       đúng dòng mình vừa gõ, và hệ thống ⛔ không dùng nó. §10.41 đã trả giá đúng thế với
     *       {@code DB_APP_PASSWORD}: một biến ⛔ không ai đọc <b>che mất</b> một biến thật sự thiếu.
     * </ul>
     *
     * <h2>⛔⛔ Bộ tách phải theo CẤU TRÚC — và bản đầu của lượt đo này đã sai vì không thế</h2>
     *
     * <p>Lượt đo tay 04/09 dùng {@code grep -oE "HYDRO_[A-Z_]+"} và kết luận rằng
     * {@code HYDRO_API_BASE_URL} <i>vẫn còn</i> trong cả ba tệp env — trong khi javadoc của
     * {@code HydroApiProperties} nói nó đã bị gỡ. Cả hai đều "đúng": biến <b>đã</b> bị gỡ, và thứ
     * còn lại là <b>dòng chú thích giải thích rằng nó đã bị gỡ</b>.
     *
     * <p>⇒ Ở đây <b>khai</b> nghĩa là một dòng {@code ^\s*TÊN=} thật sự, và <b>đọc</b> nghĩa là một
     * placeholder {@code ${TÊN...}} thật sự. Văn xuôi ⛔ không tạo ra được cái nào.
     *
     * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
     *
     * <p>Soi {@code application.yml} (nơi Spring giải biến) và {@code deploy/env/*.example} (nơi
     * người vận hành chép ra). ⛔ <b>Không</b> soi {@code compose*.yml} lẫn workflow CI — chúng
     * truyền biến xuống container, và cặp ấy do {@code DeploySecretWiringTest} canh cho nhóm secret.
     * Một biến {@code HYDRO_*} mới mà quên khai ở compose ⛔ sẽ không bị bài này bắt.
     */
    @Test
    @DisplayName("⭐⭐ T27.4 — mọi biến HYDRO_* vừa có người ĐỌC vừa có nơi KHAI (hai chiều)")
    void moiBienHydroDeuCoCaHaiVe() {
        Set<String> duocDoc = bienDuocDoc(doc(timTuGocKho("backend/app/src/main/resources/application.yml")));
        Set<String> duocKhai = new LinkedHashSet<>();
        for (String tep : TEP_MAU_ENV) {
            duocKhai.addAll(bienDuocKhai(doc(timTuGocKho(tep))));
        }

        // ⚠ Vế chống xanh-trên-tập-rỗng (luật 7): bộ tách hỏng ⇒ hai tập rỗng ⇒ mọi phép so xanh.
        assertThat(duocDoc)
                .as("⛔ Bộ tách ⛔ không thấy một placeholder ${HYDRO_*} nào trong application.yml — "
                        + "hoặc mẫu hỏng, hoặc tệp đổi tên. Cả hai đều làm bài này mù, ⛔ không đỏ.")
                .isNotEmpty();
        assertThat(duocKhai)
                .as("⛔ Bộ tách ⛔ không thấy một dòng khai HYDRO_*= nào trong %s", TEP_MAU_ENV)
                .isNotEmpty();

        assertThat(duocKhai)
                .as(
                        """
                        ⛔ Biến được KHAI mà ⛔ KHÔNG ai đọc — luật 15. Người vận hành đặt giá trị, đọc lại \
                        thấy đúng dòng mình vừa gõ, và hệ thống ⛔ không dùng nó. §10.41: một biến không ai \
                        đọc CHE MẤT một biến thật sự thiếu. Đọc: %s""",
                        duocDoc)
                .isSubsetOf(duocDoc);

        assertThat(duocDoc)
                .as(
                        """
                        ⛔ Biến được ĐỌC mà ⛔ KHÔNG tệp mẫu nào khai — người dựng máy chủ ⛔ không có cách \
                        nào biết nó tồn tại, và giá trị sẽ rơi về mặc định trong im lặng. Khai: %s""",
                        duocKhai)
                .isSubsetOf(duocKhai);
    }

    /**
     * ⚠ Vế tự kiểm (luật 29): hai bộ tách phải <b>phân biệt được</b> một dòng khai thật với một
     * dòng chú thích — đúng chỗ lượt đo tay đã sai.
     */
    @Test
    @DisplayName("⚠ Tự kiểm: dòng CHÚ THÍCH nhắc tên biến ⛔ KHÔNG được tính là khai hay đọc")
    void boTachPhanBietDuocChuThich() {
        String chuThich = "# ⛔ HYDRO_API_BASE_URL đã BỎ (31/08/2026): địa chỉ nguồn nằm ở `api_sources.base_url`";

        assertThat(bienDuocKhai(chuThich))
                .as("⛔ Một dòng chú thích nói rằng biến ĐÃ BỊ GỠ mà bị đọc thành 'đã khai' thì bộ canh "
                        + "đòi phải có người đọc một biến ⛔ không còn tồn tại — đỏ trên mã ĐÚNG.")
                .isEmpty();
        assertThat(bienDuocDoc(chuThich)).isEmpty();

        assertThat(bienDuocKhai("HYDRO_API_KEY=abc;"))
                .as("⚠ và vế phân biệt: một dòng khai THẬT phải được thấy")
                .containsExactly("HYDRO_API_KEY");
        assertThat(bienDuocDoc("      key: ${HYDRO_API_KEY:}")).containsExactly("HYDRO_API_KEY");
    }

    /** Tệp <b>mẫu</b> — thứ người dựng máy chủ chép ra. ⛔ Không soi tệp env thật (⛔ không vào kho). */
    private static final List<String> TEP_MAU_ENV =
            List.of("deploy/env/local.env.example", "deploy/env/staging.env.example", "deploy/env/prod.env.example");

    /** {@code ${HYDRO_XXX...}} — placeholder THẬT, ⛔ không phải một lần nhắc tên. */
    private static final Pattern DOC_BIEN = Pattern.compile("\\$\\{(HYDRO_[A-Z0-9_]+)[:}]");

    /** {@code ^ TÊN=} — dòng khai THẬT; dòng bắt đầu bằng {@code #} ⛔ không khớp. */
    private static final Pattern KHAI_BIEN = Pattern.compile("(?m)^\\s*(HYDRO_[A-Z0-9_]+)\\s*=");

    private static Set<String> bienDuocDoc(String noiDung) {
        return DOC_BIEN.matcher(noiDung)
                .results()
                .map(r -> r.group(1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> bienDuocKhai(String noiDung) {
        return KHAI_BIEN
                .matcher(noiDung)
                .results()
                .map(r -> r.group(1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
