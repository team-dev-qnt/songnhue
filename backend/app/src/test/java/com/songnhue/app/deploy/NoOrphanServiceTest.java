package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi service trong {@code compose.prod.yml} phải có một đường thật sự khởi động nó.</b>
 *
 * <h2>Lỗi này đã xảy ra, và nó im lặng hoàn toàn</h2>
 *
 * {@code minio-init} tạo ba bucket và tài khoản dịch vụ hạn chế cho ứng dụng. Nó
 * {@code depends_on: minio} — nhưng <b>không service nào depends_on nó</b>, và workflow triển khai
 * chỉ gọi:
 *
 * <pre>
 *   docker compose … up -d app admin-app public-web nginx
 * </pre>
 *
 * {@code postgres} và {@code minio} lên theo {@code app}. {@code minio-init} thì <b>không bao giờ
 * chạy</b>. Hệ quả trên staging: MinIO không có bucket nào, và ứng dụng không có tài khoản dịch vụ.
 * Mọi lượt tải tệp lên, mọi bản kết xuất báo cáo, mọi lượt kết xuất audit đều hỏng.
 *
 * <p>Không bước nào báo sai. Smoke test chỉ hỏi {@code /api/v1/public/site-config}. Lỗi lộ ra lần
 * đầu qua một dòng của {@code mc} khi nạp nội dung seed:
 *
 * <pre>
 *   mc: &lt;ERROR&gt; Failed to copy … Bucket `songnhue-media` does not exist.
 * </pre>
 *
 * <h2>Vì sao canh bằng đồ thị chứ không bằng danh sách</h2>
 *
 * Cách chữa hời hợt là thêm {@code minio-init} vào dòng {@code up -d}. Nó vá đúng một service, và
 * service thứ mười một thêm vào sau này lại mồ côi y như thế. Bài kiểm này hỏi câu tổng quát: <i>có
 * service nào không nằm trên đường nào không?</i>
 *
 * <p>Một service được coi là <b>có đường chạy</b> khi rơi vào một trong ba trường hợp:
 *
 * <ol>
 *   <li>nằm trong danh sách {@code up -d} của workflow triển khai, hoặc tới được từ đó qua
 *       {@code depends_on};</li>
 *   <li>được gọi thẳng bằng {@code run --rm <tên>} (đúng cách {@code migrator} chạy);</li>
 *   <li>có khai {@code profiles:} — tức cố ý đứng ngoài lượt {@code up} mặc định (đúng cách
 *       {@code certbot} chạy).</li>
 * </ol>
 */
class NoOrphanServiceTest {

    private static final Pattern TEN_SERVICE = Pattern.compile("^  ([a-z][a-z0-9-]*):\\s*$");
    private static final Pattern PHU_THUOC = Pattern.compile("^      ([a-z][a-z0-9-]*):\\s*$");
    private static final Pattern PHU_THUOC_DANG_DS = Pattern.compile("^      - ([a-z][a-z0-9-]*)\\s*$");
    private static final Pattern GOI_RUN = Pattern.compile("run --rm ([a-z][a-z0-9-]*)");

    /**
     * ⚠ TỰ TÌM, không viết cứng. Bản trước liệt kê {@code deploy-staging.yml} và
     * {@code deploy-prod.yml}; khi hai tệp ấy gộp lại thành một thân chung
     * ({@code deploy.yml}), danh sách cũ trỏ vào hai tệp không còn lệnh compose nào và bài kiểm báo
     * <b>toàn bộ 8 service đều mồ côi</b> — may là đỏ ầm ĩ, nhưng lần sau có thể là kiểu ngược lại:
     * một tệp đổi tên thì tập trở nên rỗng và bài xanh mà chưa kiểm gì (luật 7).
     */
    private static final String LENH_UP = "up -d";

    @Test
    @DisplayName("⭐⭐ Không service nào trong compose.prod.yml bị mồ côi")
    void khongServiceNaoMoCoi() {
        Map<String, DichVu> bang = docServices();

        assertThat(bang)
                .as("không đọc được service nào — bài này sẽ xanh trên tập rỗng")
                .hasSizeGreaterThan(5);

        Set<String> coDuong = new LinkedHashSet<>();

        // (3) có `profiles:` — cố ý đứng ngoài lượt `up` mặc định
        bang.forEach((ten, dv) -> {
            if (dv.coProfile) {
                coDuong.add(ten);
            }
        });

        // (2) được gọi thẳng bằng `run --rm <tên>`
        // (1) nằm trong danh sách `up -d`, rồi lan theo depends_on
        Deque<String> hangDoi = new ArrayDeque<>();
        List<Path> workflow = workflowChuaLenhUp();
        assertThat(workflow)
                .as(
                        "không workflow nào chứa `%s` — bài này sẽ coi MỌI service là mồ côi, hoặc tệ "
                                + "hơn là xanh trên tập rỗng nếu compose cũng trống",
                        LENH_UP)
                .isNotEmpty();
        for (Path duongDan : workflow) {
            String noiDung = doc(duongDan);
            GOI_RUN.matcher(noiDung).results().map(r -> r.group(1)).forEach(coDuong::add);
            hangDoi.addAll(serviceTrongLenhUp(noiDung));
        }

        while (!hangDoi.isEmpty()) {
            String ten = hangDoi.poll();
            if (!coDuong.add(ten)) {
                continue;
            }
            DichVu dv = bang.get(ten);
            if (dv != null) {
                hangDoi.addAll(dv.phuThuoc);
            }
        }

        Set<String> moCoi = new TreeSet<>(bang.keySet());
        moCoi.removeAll(coDuong);

        assertThat(moCoi)
                .as(
                        """
                        Có service trong `compose.prod.yml` mà KHÔNG đường nào khởi động.

                        Nó không nằm trong `up -d` của workflow triển khai, không service nào \
                        `depends_on` nó, không ai `run --rm` nó, và nó cũng không khai `profiles:`. \
                        Nghĩa là nó tồn tại trong tệp mà chưa từng chạy — và điều đó KHÔNG có gì báo.

                        Đã xảy ra với `minio-init`: ba bucket và tài khoản dịch vụ không bao giờ được \
                        tạo, nên mọi lượt tải tệp lên staging đều hỏng, im lặng, cho tới khi một dòng \
                        của `mc` nói ra (§10.49).

                        Cách chữa ĐÚNG là cho thứ cần nó `depends_on` nó — đặt bảo đảm ở chỗ dữ liệu \
                        đi qua, đừng thêm tên vào một dòng `up -d` mà lần sau lại quên.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐⭐ `app` phải đợi `minio-init` hoàn tất mới được lên")
    void appPhaiDoiMinioInit() {
        DichVu app = docServices().get("app");
        assertThat(app).as("không tìm thấy service `app`").isNotNull();

        assertThat(app.phuThuoc)
                .as(
                        """
                        `app` không `depends_on` `minio-init`.

                        Ứng dụng không làm gì được khi bucket chưa tồn tại: mọi lượt tải tệp lên, kết \
                        xuất báo cáo và kết xuất audit đều đi qua MinIO. Đây chính là chỗ ràng buộc \
                        phải nằm — không phải ở trí nhớ của người viết dòng `up -d`.""")
                .contains("minio-init");

        assertThat(app.dieuKien.get("minio-init"))
                .as(
                        """
                        `app` đợi `minio-init` nhưng KHÔNG dùng `service_completed_successfully`.

                        `minio-init` chạy xong rồi thoát. Với `service_started` thì compose chỉ chờ nó \
                        BẮT ĐẦU — app vẫn có thể lên trước khi bucket kịp tạo, và lỗi sẽ thành lúc có \
                        lúc không, tuỳ máy chậm nhanh. Đó là kiểu lỗi đắt nhất để tìm.""")
                .isEqualTo("service_completed_successfully");
    }

    private record DichVu(boolean coProfile, List<String> phuThuoc, Map<String, String> dieuKien) {}

    private static Map<String, DichVu> docServices() {
        List<String> dong = doc(timTuGocKho("deploy/compose.prod.yml")).lines().toList();
        Map<String, DichVu> ket = new LinkedHashMap<>();

        boolean trongServices = false;
        String ten = null;
        boolean coProfile = false;
        boolean trongDependsOn = false;
        List<String> phuThuoc = new ArrayList<>();
        Map<String, String> dieuKien = new LinkedHashMap<>();
        String phuThuocCuoi = null;

        for (String d : dong) {
            if (d.startsWith("services:")) {
                trongServices = true;
                continue;
            }
            if (!trongServices) {
                continue;
            }
            // khoá cấp cao nhất khác (volumes:, networks:) → hết khối services
            if (!d.isBlank() && !Character.isWhitespace(d.charAt(0))) {
                break;
            }

            Matcher mTen = TEN_SERVICE.matcher(d);
            if (mTen.matches()) {
                if (ten != null) {
                    ket.put(ten, new DichVu(coProfile, phuThuoc, dieuKien));
                }
                ten = mTen.group(1);
                coProfile = false;
                trongDependsOn = false;
                phuThuoc = new ArrayList<>();
                dieuKien = new LinkedHashMap<>();
                continue;
            }
            if (ten == null) {
                continue;
            }
            if (d.startsWith("    profiles:")) {
                coProfile = true;
            }
            if (d.startsWith("    depends_on:")) {
                trongDependsOn = true;
                continue;
            }
            // rời khối depends_on khi gặp khoá cấp 4 khác
            if (trongDependsOn && d.startsWith("    ") && !d.startsWith("     ")) {
                trongDependsOn = false;
            }
            if (!trongDependsOn) {
                continue;
            }
            Matcher mPt = PHU_THUOC.matcher(d);
            if (mPt.matches()) {
                phuThuocCuoi = mPt.group(1);
                phuThuoc.add(phuThuocCuoi);
                continue;
            }
            Matcher mDs = PHU_THUOC_DANG_DS.matcher(d);
            if (mDs.matches()) {
                phuThuoc.add(mDs.group(1));
                continue;
            }
            if (phuThuocCuoi != null && d.stripLeading().startsWith("condition:")) {
                dieuKien.put(phuThuocCuoi, d.substring(d.indexOf(':') + 1).trim());
            }
        }
        if (ten != null) {
            ket.put(ten, new DichVu(coProfile, phuThuoc, dieuKien));
        }
        return ket;
    }

    /** Mọi workflow có lệnh `up -d` — tìm động để bài kiểm theo được khi workflow đổi tên/gộp lại. */
    private static List<Path> workflowChuaLenhUp() {
        try (Stream<Path> duyet = Files.list(timTuGocKho(".github/workflows"))) {
            return duyet.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .filter(p -> doc(p).lines().anyMatch(d -> !d.stripLeading().startsWith("#") && d.contains(LENH_UP)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bóc tên service khỏi `docker compose … up -d \` và dòng nối tiếp của nó. */
    private static List<String> serviceTrongLenhUp(String workflow) {
        List<String> ket = new ArrayList<>();
        List<String> dong = workflow.lines().toList();
        for (int i = 0; i < dong.size(); i++) {
            String d = dong.get(i);
            if (d.stripLeading().startsWith("#") || !d.contains("up -d")) {
                continue;
            }
            String phanSau = d.substring(d.indexOf("up -d") + "up -d".length());
            StringBuilder gom = new StringBuilder(phanSau);
            int j = i;
            while (dong.get(j).stripTrailing().endsWith("\\") && j + 1 < dong.size()) {
                gom.append(' ').append(dong.get(++j));
            }
            for (String tu : gom.toString().replace("\\", " ").trim().split("\\s+")) {
                if (tu.matches("[a-z][a-z0-9-]*")) {
                    ket.add(tu);
                }
            }
        }
        return ket;
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
