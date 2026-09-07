package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Ngày hết hỗ trợ là dữ liệu, không phải trí nhớ.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Ngày 2/9/2026 lượt quét CVE đỏ với 4 mã ≥ 7, và {@code spring-core 6.2.20} trả <b>HTTP 404</b>
 * trên Central. Suốt hai ngày cái 404 ấy được đọc là <i>"bản vá chưa kịp ra"</i>. Nó không phải:
 * Spring Boot 3.5 và Spring Framework 6.2 <b>hết hỗ trợ OSS ngày 30/6/2026</b>, và 6.2.19 là bản vá
 * miễn phí <b>cuối cùng</b>. Ghi chú suppression viết 1/9 chốt <i>"6.2.20 nhiều khả năng ra trong
 * vài tuần"</i> — một điều kiện xem lại <b>sẽ không bao giờ kích hoạt</b>.
 *
 * <p>404 trên Central không phải lỗi mạng — <b>nó là ngày hết hỗ trợ nói bằng HTTP</b>.
 *
 * <p>Và đây không phải trường hợp lẻ: cùng lượt rà tìm ra {@code nginx:1.27-alpine} đã hết hỗ trợ
 * từ <b>24/6/2025</b> — <b>14 tháng</b> — mà không dòng nào trong kho nói ra điều đó.
 *
 * <h2>Cơ chế</h2>
 *
 * {@code deploy/vong-doi-phien-ban.tsv} giữ ngày hết hỗ trợ của từng thành phần nền. Bài này:
 *
 * <ol>
 *   <li>đối chiếu <b>hai chiều</b> giữa bảng và mọi nơi ghim thật trong kho — bảng nói 22 mà
 *       Dockerfile ghi 20 thì ĐỎ, và ngược lại;
 *   <li><b>tự đỏ trước 90 ngày</b> tới hạn, để hạng mục đổi dòng được mở khi còn kịp;
 *   <li>cấm tag trôi ({@code :latest}, {@code ubuntu-latest}).
 * </ol>
 *
 * <p>Cùng cơ chế {@code until} của {@code dependency-check-suppressions.xml}: <b>hạn tự làm CI
 * đỏ</b>, không phụ thuộc ai nhớ.
 *
 * <h2>⛔ Giới hạn (luật 28)</h2>
 *
 * Bài này canh <b>ngày ghi trong bảng</b>, không canh <b>ngày thật của thế giới</b>. Nếu nhà phát
 * hành rút ngắn vòng đời thì bảng sai mà bài vẫn xanh. Cột {@code nguon} có để lượt sau đo lại
 * được; không có cơ chế nào tự đo hộ.
 */
class VongDoiPhienBanTest {

    private static final Path BANG = Paths.get("deploy/vong-doi-phien-ban.tsv");

    /** Ngưỡng cảnh báo sớm: còn dưới ngần này ngày là phải mở hạng mục đổi dòng. */
    private static final int NGAY_BAO_TRUOC = 90;

    /**
     * Nơi ghim thật của từng thành phần: khoá là {@code thanh_phan}, giá trị là các tệp phải chứa
     * giá trị {@code dang_dung}.
     *
     * <p>⚠ Hằng chuỗi literal, không dựng bằng biến — {@code CiPathFilterTest} quét mã nguồn test
     * để bảo đảm bộ lọc đường dẫn của CI bao hết những tệp mà bài kiểm ĐỌC. Dùng
     * {@code Path.of("deploy", …)} thì nó không thấy, và job canh sẽ bị bỏ qua đúng lúc những tệp
     * ấy thay đổi (CLAUDE.md luật 24).
     */
    private static Map<String, List<String>> noiGhim() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("java", List.of("backend/pom.xml", ".github/workflows/ci.yml", "deploy/docker/backend.Dockerfile"));
        m.put(
                "node",
                List.of(
                        ".nvmrc",
                        "frontend/package.json",
                        "deploy/docker/public-web.Dockerfile",
                        "deploy/docker/admin-app.Dockerfile"));
        m.put("spring-boot", List.of("backend/pom.xml"));
        m.put(
                "postgres",
                List.of(
                        "deploy/compose.infra.yml",
                        "deploy/compose.prod.yml",
                        "backend/app/src/test/java/com/songnhue/app/testsupport/SongnhuePostgres.java"));
        m.put("nginx", List.of("deploy/compose.prod.yml", "deploy/docker/admin-app.Dockerfile"));
        m.put("next", List.of("frontend/public-web/package.json"));
        return m;
    }

    // ── Nhóm 1: bảng nói đúng thứ kho đang ghim ────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ Mỗi dòng của bảng khớp MỌI nơi ghim thật — bảng và kho không được nói hai điều")
    void bangKhopNoiGhim() {
        List<Dong> bang = docBang();

        // Chặn xanh-trên-tập-rỗng: bộ đọc TSV hỏng thì phải ĐỎ, không phải im lặng đạt (luật 7).
        assertThat(bang)
                .as("Không đọc ra dòng nào từ %s — cấu trúc đã đổi, SỬA bài kiểm chứ đừng xoá", BANG)
                .hasSizeGreaterThanOrEqualTo(6);

        Map<String, List<String>> noiGhim = noiGhim();
        List<String> loi = new ArrayList<>();

        for (Dong d : bang) {
            List<String> tep = noiGhim.get(d.thanhPhan());
            if (tep == null) {
                loi.add("`%s` có trong bảng nhưng bài kiểm không biết nó được ghim ở đâu — thêm vào noiGhim()"
                        .formatted(d.thanhPhan()));
                continue;
            }
            for (String t : tep) {
                String noiDung = doc(timTuGocKho(t));
                if (!noiDung.contains(d.dangDung())) {
                    loi.add("`%s`: bảng nói `%s` nhưng `%s` KHÔNG chứa giá trị ấy"
                            .formatted(d.thanhPhan(), d.dangDung(), t));
                }
            }
        }

        // Chiều ngược lại: mọi thành phần bài kiểm biết đường ghim đều phải có mặt trong bảng.
        for (String tp : noiGhim.keySet()) {
            if (bang.stream().noneMatch(d -> d.thanhPhan().equals(tp))) {
                loi.add("`%s` được ghim trong kho nhưng KHÔNG có dòng nào trong bảng vòng đời".formatted(tp));
            }
        }

        assertThat(loi)
                .as(
                        """
                        %d chỗ bảng vòng đời và kho nói hai điều khác nhau:

                        %s

                        Bảng chỉ có giá trị khi nó nói đúng thứ đang chạy. Một bảng lệch còn tệ hơn \
                        không có bảng — nó biến một phép đo thành một lời khai.""",
                        loi.size(), String.join("\n", loi))
                .isEmpty();
    }

    // ── Nhóm 2: hạn tự làm CI đỏ ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ Quá hạn OSS mà không có đồng hồ di trú → ĐỎ")
    void quaHanThiPhaiCoDongHo() {
        LocalDate homNay = LocalDate.now();
        List<String> loi = new ArrayList<>();

        for (Dong d : docBang()) {
            if (d.hetHoTroOss() == null) {
                continue; // `chua-cong-bo` — không có hạn để mà quá
            }
            if (!homNay.isAfter(d.hetHoTroOss())) {
                continue;
            }
            if (d.hanHoanTat() == null) {
                loi.add(
                        """
                        `%s` %s đã HẾT hỗ trợ OSS từ %s (%d ngày trước) mà không có `han_hoan_tat_di_tru`.

                            Đứng trên một dòng đã đóng nghĩa là mọi CVE mới của nó đều KHÔNG có \
                        đường nâng cấp. Điền hạn hoàn tất di trú + mã nợ, hoặc đổi dòng."""
                                .formatted(
                                        d.thanhPhan(),
                                        d.dong(),
                                        d.hetHoTroOss(),
                                        ChronoUnit.DAYS.between(d.hetHoTroOss(), homNay)));
            } else if (d.no() == null) {
                loi.add("`%s`: có `han_hoan_tat_di_tru` nhưng KHÔNG có mã nợ — đó là gia hạn im lặng"
                        .formatted(d.thanhPhan()));
            } else if (homNay.isAfter(d.hanHoanTat())) {
                loi.add(
                        """
                        `%s`: hạn hoàn tất di trú %s ĐÃ QUA (%s).

                            ⛔ KHÔNG được đẩy tiếp. Lý do "dòng này hết hỗ trợ" không phải một lý do \
                        hợp lệ để hoãn — nó biến một ngày hết hạn ĐÃ TỚI thành một ngày tự đặt."""
                                .formatted(d.thanhPhan(), d.hanHoanTat(), d.no()));
            }
        }

        assertThat(loi).as("%s", String.join("\n\n", loi)).isEmpty();
    }

    @Test
    @DisplayName("⭐ Còn dưới 90 ngày tới hạn OSS → ĐỎ, để hạng mục đổi dòng mở khi còn kịp")
    void conDuoiNgayNguongThiCanhBaoSom() {
        LocalDate homNay = LocalDate.now();
        List<String> loi = new ArrayList<>();

        for (Dong d : docBang()) {
            if (d.hetHoTroOss() == null || homNay.isAfter(d.hetHoTroOss()) || d.hanHoanTat() != null) {
                continue; // đã quá hạn thì bài trên lo; đang di trú thì có đồng hồ riêng
            }
            long con = ChronoUnit.DAYS.between(homNay, d.hetHoTroOss());
            if (con < NGAY_BAO_TRUOC) {
                loi.add("`%s` %s còn %d ngày tới hạn OSS (%s) — mở hạng mục đổi dòng ngay, đừng đợi hết hạn"
                        .formatted(d.thanhPhan(), d.dong(), con, d.hetHoTroOss()));
            }
        }

        assertThat(loi).as("%s", String.join("\n", loi)).isEmpty();
    }

    // ── Nhóm 3: cấm tag trôi ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐ Không tag trôi — `:latest` và `ubuntu-latest` đổi vào một ngày không ai chọn")
    void khongTagTroi() {
        List<String> tep = List.of(
                "deploy/compose.prod.yml",
                "deploy/compose.infra.yml",
                "deploy/docker/admin-app.Dockerfile",
                "deploy/docker/public-web.Dockerfile",
                "deploy/docker/backend.Dockerfile",
                ".github/workflows/ci.yml",
                ".github/workflows/deploy.yml",
                ".github/workflows/deploy-prod.yml",
                ".github/workflows/deploy-staging.yml",
                ".github/workflows/promotion-guard.yml",
                ".github/workflows/security-scan.yml");

        List<String> viPham = new ArrayList<>();
        int soDongDaQuet = 0;
        for (String t : tep) {
            String[] dong = doc(timTuGocKho(t)).split("\n");
            soDongDaQuet += dong.length;
            for (int i = 0; i < dong.length; i++) {
                // ⚠ GỌI `coTagTroi`, không chép lại điều kiện. Bản đầu chép — và bài tự kiểm bắt
                //   ngay: helper nhận chuỗi có `\n` cuối thì `.matches()` trượt (nó đòi khớp TOÀN
                //   chuỗi, và `.` không khớp xuống dòng), trong khi vòng này đã tách dòng nên
                //   không có `\n`. Hai nơi cùng một luật là hai nơi phải nhớ (luật 14), và ở đây
                //   chúng đã kịp lệch nhau trước cả lượt commit đầu tiên.
                if (coTagTroi(dong[i])) {
                    viPham.add("%s:%d  %s".formatted(t, i + 1, dong[i].strip()));
                }
            }
        }

        // Chặn xanh-trên-tập-rỗng: đọc hụt tệp thì phải ĐỎ.
        assertThat(soDongDaQuet)
                .as("Không quét được dòng nào — danh sách tệp đã lỗi thời")
                .isGreaterThan(500);

        assertThat(viPham)
                .as(
                        """
                        %d tag trôi:

                        %s

                        Một tag trôi đổi vào một ngày không ai chọn, và khi nó hỏng thì hỏng trông \
                        như hỏng vì mã. `ubuntu-latest` sẽ tự sang 26.04; `certbot:latest` là tag \
                        `latest` DUY NHẤT còn lại trong kho tính tới 3/9/2026.""",
                        viPham.size(), String.join("\n", viPham))
                .isEmpty();
    }

    // ── Nhóm 4: tự kiểm chứng ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ TỰ KIỂM: bộ dò phải bắt được dòng quá hạn và bảng lệch nơi ghim")
    void tuKiemChung() {
        // (1) Một dòng quá hạn, không đồng hồ → phải bị bắt. Ngày cố định trong quá khứ, không
        //     phụ thuộc hôm nay chạy bài này.
        List<Dong> giaQuaHan = phanTich(
                """
                thanh_phan\tdong\tdang_dung\thet_ho_tro_oss\than_hoan_tat_di_tru\tno\tnguon
                mot-thu\t1.0\t1.0.0\t2020-01-01\t-\t-\tgia lap
                """);
        assertThat(giaQuaHan).hasSize(1);
        Dong d = giaQuaHan.get(0);
        assertThat(d.hetHoTroOss()).as("Bộ dò phải phân tích được ngày").isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(LocalDate.now().isAfter(d.hetHoTroOss()))
                .as("2020-01-01 phải nằm trong quá khứ — nếu không thì cả bài kiểm hạn là vô nghĩa")
                .isTrue();
        assertThat(d.hanHoanTat())
                .as("`-` phải đọc thành KHÔNG CÓ, không phải một chuỗi rỗng vô hại")
                .isNull();
        assertThat(d.no()).isNull();

        // (2) `chua-cong-bo` phải đọc thành "không có hạn", không được ném và không được thành 0.
        List<Dong> giaChuaCongBo = phanTich(
                """
                thanh_phan\tdong\tdang_dung\thet_ho_tro_oss\than_hoan_tat_di_tru\tno\tnguon
                thu-khac\t2.0\t2.0.0\tchua-cong-bo\t-\t-\tgia lap
                """);
        assertThat(giaChuaCongBo.get(0).hetHoTroOss()).isNull();

        // (3) Bảng lệch nơi ghim: giá trị `dang_dung` không có trong tệp → phải phát hiện được.
        String noiDungThat = doc(timTuGocKho(".nvmrc"));
        assertThat(noiDungThat)
                .as("Đối chứng phải-tìm-thấy: .nvmrc PHẢI chứa 22")
                .contains("22");
        assertThat(noiDungThat)
                .as("Và phải KHÔNG chứa một giá trị bịa — nếu chứa thì phép so ở bài trên không phân biệt gì")
                .doesNotContain("99");
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: bộ dò tag trôi bắt được `:latest` giả")
    void tuKiemChungTagTroi() {
        String giaLatest = "    image: certbot/certbot:latest\n";
        String giaFrom = "FROM nginx:latest AS runtime\n";
        String giaChuThich = "    # trước đây là image: certbot/certbot:latest — đã ghim\n";
        String thatSuOk = "    image: certbot/certbot:v5.8.0\n";

        assertThat(coTagTroi(giaLatest)).as("`image: …:latest` phải bị bắt").isTrue();
        assertThat(coTagTroi(giaFrom)).as("`FROM …:latest` phải bị bắt").isTrue();
        assertThat(coTagTroi(giaChuThich))
                .as(
                        """
                        Một dòng CHÚ THÍCH nhắc tới `:latest` KHÔNG phải vi phạm. Bộ dò không bỏ chú \
                        thích thì mọi ghi chú lịch sử đều thành lỗi, và người ta sẽ xoá ghi chú thay \
                        vì sửa lỗi (luật 2 — canh cấu trúc, đừng canh văn bản).""")
                .isFalse();
        assertThat(coTagTroi(thatSuOk)).isFalse();
    }

    // ── Hạ tầng ────────────────────────────────────────────────────────────────────────────────

    /**
     * Một dòng có khai tag trôi không — chú thích KHÔNG tính.
     *
     * <p>{@code strip()} là bắt buộc, không phải cho đẹp: {@code String.matches} đòi khớp <b>toàn
     * chuỗi</b> và {@code .} không khớp {@code \n}, nên một dòng còn ký tự xuống dòng ở cuối sẽ
     * trượt mọi mẫu. Đây là lỗi bài tự kiểm bắt được ở lượt chạy đầu tiên.
     */
    private static boolean coTagTroi(String dong) {
        String khongChuThich = dong.replaceFirst("#.*$", "").strip();
        return khongChuThich.matches(".*\\bimage:\\s*\\S+:latest\\b.*")
                || khongChuThich.matches(".*\\bFROM\\s+\\S+:latest\\b.*")
                || khongChuThich.contains("ubuntu-latest");
    }

    private record Dong(
            String thanhPhan, String dong, String dangDung, LocalDate hetHoTroOss, LocalDate hanHoanTat, String no) {}

    private static List<Dong> docBang() {
        return phanTich(doc(timTuGocKho(BANG.toString())));
    }

    private static List<Dong> phanTich(String noiDung) {
        List<Dong> ket = new ArrayList<>();
        for (String d : noiDung.split("\n")) {
            if (d.isBlank() || d.startsWith("#") || d.startsWith("thanh_phan\t")) {
                continue;
            }
            String[] o = d.split("\t");
            if (o.length < 6) {
                fail("Dòng thiếu cột trong bảng vòng đời: %s".formatted(d));
            }
            ket.add(new Dong(o[0].strip(), o[1].strip(), o[2].strip(), ngay(o[3]), ngay(o[4]), khac(o[5])));
        }
        return ket;
    }

    /** {@code -} và {@code chua-cong-bo} đều đọc thành KHÔNG CÓ — hai trạng thái khác "ngày 0". */
    private static LocalDate ngay(String o) {
        String s = o.strip();
        if (s.isEmpty() || "-".equals(s) || "chua-cong-bo".equals(s)) {
            return null;
        }
        return LocalDate.parse(s);
    }

    private static String khac(String o) {
        String s = o.strip();
        return s.isEmpty() || "-".equals(s) ? null : s;
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
