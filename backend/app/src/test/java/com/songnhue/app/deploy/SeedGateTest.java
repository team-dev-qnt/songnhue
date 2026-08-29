package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Bộ seed nội dung cổng: cổng chặn production, và hai vế không được lệch nhau.</b>
 *
 * <h2>Vì sao lớp này tồn tại</h2>
 *
 * Bộ seed 5 bài viết chuyển từ một script bấm tay ({@code deploy/seed/seed.sh} + workflow
 * {@code Nạp nội dung Staging}) thành <b>một migration Flyway</b>. Đổi lấy sự tự động ấy, ta nhận
 * về hai rủi ro mới, và cả hai đều <b>hỏng câm</b>:
 *
 * <ol>
 *   <li><b>Migration chạy ở mọi môi trường, một chiều, không hỏi ai.</b> Migration này mở đầu bằng
 *       một lệnh <b>xoá bài</b>. Chạy nhầm trên production là xoá nội dung thật của Công ty rồi
 *       đăng 5 bài chép lại của báo ngoài — không có lượt bấm xác nhận nào chặn được.
 *       <br>Cổng chặn: tệp seed nằm ở {@code classpath:db/seed/portal}, <b>ngoài</b>
 *       {@code spring.flyway.locations} mặc định, chỉ được giải khi {@code SEED_LOCATION} trỏ vào.
 *   <li><b>Hàng trong CSDL và byte trong MinIO là hai hệ thống khác nhau.</b> Migration ghi hàng
 *       {@code attachments} kèm {@code storage_key}; byte thì đi đường khác — {@code minio-init}
 *       đẩy cả thư mục {@code deploy/seed/media/} lên bucket. Lệch một ký tự thì CSDL vẫn nói tệp
 *       tồn tại còn {@code GET /api/v1/public/files/<id>} trả 404.
 * </ol>
 *
 * Đây đúng khuôn <i>"chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ"</i>
 * (CLAUDE.md luật 14). Lớp này là phép kiểm ấy.
 *
 * <p>⚠ Nó canh <b>giá trị đã giải</b> chứ không canh lời hứa: {@code sha256} và {@code size_bytes}
 * trong SQL được đối chiếu với <b>byte thật của tệp trên đĩa</b>, không phải với {@code images.json}
 * — đối chiếu hai tệp sinh ra từ cùng một nguồn thì cả hai cùng sai vẫn xanh.
 */
class SeedGateTest {

    /**
     * Tệp seed ĐẦU TIÊN — chỉ dùng cho hai bài nói về NỘI DUNG RIÊNG của nó (khối xoá bài theo vị
     * từ menu, và bài kiểm chứng ngược lấy nó làm mẫu).
     *
     * <p>⛔ KHÔNG dùng nó cho hai bài canh byte. Bản trước dùng, và hậu quả lộ ra ngày 27/08/2026
     * khi có tệp seed THỨ HAI: bộ canh đọc đúng một tệp ghi cứng rồi khẳng định {@code hasSize(4)},
     * nên 30 tệp byte mới thành "mồ côi" trong khi hàng của chúng nằm ngay ở tệp seed kia. Đúng
     * hình dạng {@code CLAUDE.md} luật 28 — <i>một cơ chế canh gác hẹp hơn nơi nó phải chặn</i>.
     */
    private static final String SEED_SQL =
            "backend/content/src/main/resources/db/seed/portal/V202608251100__seed_portal_content.sql";

    /** Thư mục chứa MỌI migration seed — bộ canh byte phải đọc hết, không đọc một tệp. */
    private static final String THU_MUC_SEED = "backend/content/src/main/resources/db/seed";

    private static final String THU_MUC_BYTE = "deploy/seed/media";
    /** Tên biến đã bị bỏ ở T11.26 — canh để nó không quay lại. */
    private static final String BIEN_DA_BO = "SEED_MEDIA" + "_DIR";

    private static final String LOCATION_MAC_DINH = "backend/content/src/main/resources/db/seed/none";

    /** {@code 'seed/portal/x.jpeg', 'image/jpeg', 113720, '05f8…'} — khoá · kiểu · cỡ · băm. */
    private static final Pattern HANG_DINH_KEM =
            Pattern.compile("'(seed/[^']+)',\\s*'([^']+)',\\s*(\\d+),\\s*'([0-9a-f]{64})'", Pattern.DOTALL);

    // =========================================================================
    // 1. Cổng chặn production
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Migration seed KHÔNG nằm trong location mặc định — production không giải được nó")
    void seedKhongNamTrongLocationMacDinh() {
        Path goc = gocKho();
        List<Path> lacCho = new ArrayList<>();
        try (Stream<Path> duyet = Files.walk(goc.resolve("backend"))) {
            duyet.filter(p -> p.toString().replace('\\', '/').contains("/src/main/resources/db/migration/"))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains("seed_portal"))
                    .forEach(lacCho::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(lacCho)
                .as(
                        """
                    Có tệp seed nội dung nằm trong `db/migration/**` — tức là nằm trong \
                    `spring.flyway.locations` MẶC ĐỊNH, tức là production sẽ chạy nó.

                    Migration seed mở đầu bằng lệnh XOÁ BÀI. Nó phải nằm ở `db/seed/portal` và chỉ \
                    được giải qua biến `SEED_LOCATION`.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐ `application.yml` khai công tắc, và mặc định là một thư mục CÓ THẬT mà rỗng")
    void macDinhLaThuMucRongCoThat() {
        String yml = doc(tuGocKho("backend/app/src/main/resources/application.yml"));

        assertThat(yml)
                .as(
                        """
                    `spring.flyway.locations` phải có đúng dòng `${SEED_LOCATION:classpath:db/seed/none}`.

                    Không có dòng này thì `SEED_LOCATION` là một biến KHÔNG AI ĐỌC (luật 15): tệp env \
                    của staging đặt nó, người vận hành tin là đã bật, và không có bài seed nào chạy.""")
                .contains("${SEED_LOCATION:classpath:db/seed/none}");

        Path none = tuGocKho(LOCATION_MAC_DINH);
        assertThat(Files.isDirectory(none))
                .as(
                        "`%s` phải TỒN TẠI. Flyway trỏ vào một location không có thật là hành vi tuỳ "
                                + "phiên bản — cảnh báo hay dừng hẳn — và một cổng chặn chỉ đúng 'tuỳ phiên "
                                + "bản' thì không phải cổng chặn.",
                        LOCATION_MAC_DINH)
                .isTrue();

        assertThat(tepMigration(none))
                .as("`%s` cố ý KHÔNG được có migration nào — đó là toàn bộ ý nghĩa của nó.", LOCATION_MAC_DINH)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Tệp mẫu production để TRỐNG công tắc seed")
    void productionKhongBatSeed() {
        assertThat(giaTri(doc(tuGocKho("deploy/env/prod.env.example")), "SEED_LOCATION"))
                .as("`prod.env.example` đặt SEED_LOCATION khác rỗng — tức là bày sẵn đường cho một "
                        + "lượt deploy production xoá nội dung thật của Công ty.")
                .isEmpty();
    }

    // =========================================================================
    // 2. Hai vế của công tắc phải bật cùng nhau
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Bộ seed chỉ có MỘT công tắc — vế thứ hai không thể quay lại")
    void motCongTacDuyNhat() {
        // Trước T11.26 có hai biến: một cho hàng CSDL, một cho byte MinIO. Biến thứ hai không
        // mang thông tin gì — đường dẫn trong container đã bị bind mount ghim ở `/seed-media` —
        // nên nó chỉ tồn tại để có thể LỆCH với biến thứ nhất, và lệch là hỏng câm:
        //   • có hàng, không có byte → CSDL nói tệp tồn tại, `GET .../files/<id>` trả 404
        //   • có byte, không có hàng → 4 tệp nằm trong bucket không ai đọc tới
        //
        // Bài canh cũ (`haiVeKhongDuocLech`) soi hai TỆP MẪU TRONG REPO, tức canh được bản mẫu
        // chứ không canh được `/opt/songnhue/.env` đang chạy trên máy chủ — đúng chỗ luật 12 nói
        // là đặt bảo đảm sai vị trí. Nay `minio-init` đọc thẳng biến của `migrator`, nên trạng
        // thái lệch **không biểu diễn được** và không cần ai canh nữa.
        //
        // Dòng dưới canh việc đó KHÔNG bị hoàn tác: chừng nào tên biến cũ chưa xuất hiện lại ở
        // `deploy/` thì hai vế không thể tách ra lần nữa.
        List<Path> tep = tepTrongDeploy();
        assertThat(tep)
                .as("không quét được tệp nào trong deploy/ — bài này sẽ xanh trên tập rỗng")
                .isNotEmpty();

        List<String> pham = tep.stream()
                .filter(t -> doc(t).contains(BIEN_DA_BO))
                .map(t -> t.getFileName().toString())
                .toList();
        assertThat(pham)
                .as(
                        """
                        `%s` xuất hiện trở lại ở: %s.

                        Bộ seed có ĐÚNG một công tắc. Thêm biến thứ hai là dựng lại đúng trạng thái \
                        lệch mà T11.26 vừa xoá bỏ — và lần này sẽ không có bài kiểm nào canh, vì bài \
                        canh cặp đã bị gỡ cùng lúc.""",
                        BIEN_DA_BO, pham)
                .isEmpty();

        // Và vế byte phải đang đọc ĐÚNG biến của vế hàng — nếu không thì luật trên chỉ là một
        // khẳng định phủ định, xanh trọn vẹn kể cả khi `minio-init` không còn nạp byte nữa.
        assertThat(khoiService(doc(tuGocKho("deploy/compose.prod.yml")), "minio-init"))
                .as("`minio-init` phải nhận `SEED_LOCATION` — cùng biến `migrator` đọc.")
                .contains("SEED_LOCATION: ${SEED_LOCATION:-}")
                .contains("if [ -n \"$$SEED_LOCATION\" ]; then");

        // Chặn xanh-trên-tập-rỗng: staging PHẢI đang bật.
        assertThat(giaTri(doc(tuGocKho("deploy/env/staging.env.example")), "SEED_LOCATION"))
                .isEqualTo("classpath:db/seed/portal");
    }

    @Test
    @DisplayName("⭐⭐ `minio-init` ĐO tài khoản dịch vụ, không chỉ khai báo nó")
    void minioInitDoTaiKhoanDichVu() {
        String khoi = khoiService(doc(tuGocKho("deploy/compose.prod.yml")), "minio-init");

        // `mc admin user add` hỏng khi tài khoản đã có và `policy attach` hỏng khi policy đã gắn,
        // nên `|| true` sau chúng là hợp lý cho một lượt deploy lặp. Vấn đề là nó nuốt luôn mọi
        // lỗi THẬT: đo ngày 25/8 với một secret sai, bản cũ in `✓ Bucket và tài khoản dịch vụ sẵn
        // sàng` rồi thoát 0, và ứng dụng chết ở lượt tải tệp đầu tiên (T11.25).
        //
        // Cách chữa không phải bỏ `|| true` mà là ĐO trạng thái cuối bằng CHÍNH cặp khoá ứng
        // dụng sẽ dùng — thứ duy nhất phân biệt "đã gọi hai lệnh" với "quyền có hiệu lực"
        // (luật 9).
        assertThat(khoi)
                .as("`minio-init` phải tạo alias bằng cặp khoá của ứng dụng để tự kiểm chứng.")
                .contains("mc alias set svc http://minio:9000 \"$$MINIO_ACCESS_KEY\" \"$$MINIO_SECRET_KEY\"");
        assertThat(khoi)
                .as("phép đo phải GHI, ĐỌC và XOÁ — chỉ ghi được chưa chứng minh ứng dụng đọc lại được.")
                .contains("mc pipe \"svc/")
                .contains("mc cat \"svc/")
                .contains("mc rm \"svc/");

        // ⛔ `mc admin user add` KHÔNG được đứng sau `|| true` nữa: một lỗi thật ở đó (secret sai
        //    độ dài, MinIO từ chối) phải dừng lượt triển khai ngay, chứ không đợi phép đo bên dưới.
        assertThat(khoi)
                .as("`mc admin user add ... || true` đã quay lại — lỗi thật sẽ bị nuốt lần nữa.")
                .doesNotContain("mc admin user add local \"$$MINIO_ACCESS_KEY\" \"$$MINIO_SECRET_KEY\" || true");
    }

    @Test
    @DisplayName("⭐ compose: byte lên MinIO TRƯỚC khi migration ghi hàng khẳng định chúng tồn tại")
    void composeRangBuocThuTu() {
        String compose = doc(tuGocKho("deploy/compose.prod.yml"));

        assertThat(khoiService(compose, "minio-init"))
                .as("`minio-init` phải gắn thư mục byte seed vào. Bố cục thư mục chính là khoá đối "
                        + "tượng, nên không có tiền tố nào được viết cứng trong lệnh `mc`.")
                .contains("./seed/media:/seed-media:ro")
                .contains("SEED_LOCATION");

        assertThat(khoiService(compose, "migrator"))
                .as(
                        """
                    `migrator` phải `depends_on: minio-init` với `service_completed_successfully`.

                    Migration seed ghi hàng `attachments` KHẲNG ĐỊNH byte đã có trong MinIO. Ràng buộc \
                    ấy thuộc về nơi lời khẳng định được ghi ra (luật 12), không thuộc về thứ tự dòng \
                    lệnh trong workflow triển khai — đặt ở workflow thì lượt gõ tay lúc chữa cháy sẽ \
                    không có nó.

                    `service_started` KHÔNG đủ: `minio-init` chạy xong rồi thoát.""")
                .contains("minio-init:")
                .contains("condition: service_completed_successfully");
    }

    @Test
    @DisplayName("⭐ compose LOCAL cũng ràng buộc đúng thứ tự ấy — máy dev đo được thứ staging đo")
    void composeLocalCungRangBuocThuTu() {
        // ⚠⚠ LUẬT 28 — bài trên chỉ soi `compose.prod.yml`, và cho tới 29/08 đó là toàn bộ phạm
        //    vi của bộ canh này. Hệ quả đo được: stack local KHÔNG có vế byte nào và KHÔNG đọc
        //    `SEED_LOCATION`, nên `/banners` và `/photos` trả 0 trên mọi máy dev. Slider và thư
        //    viện ảnh vì thế chỉ từng được nghiệm thu ở nhánh RỖNG — nhánh có dữ liệu thì duy
        //    nhất staging đi qua, tức lỗi ở đó chỉ lộ ra sau khi đã deploy.
        //
        //    Cái xanh của bài trên đọc như một lời bảo đảm về "bộ seed", trong khi nó chỉ bảo
        //    đảm cho một trong hai môi trường chạy bộ seed ấy.
        String infra = doc(tuGocKho("deploy/compose.infra.yml"));
        String local = doc(tuGocKho("deploy/compose.local.yml"));

        assertThat(khoiService(infra, "minio-init"))
                .as("`minio-init` của stack local phải gắn thư mục byte seed và đọc CÙNG biến "
                        + "`SEED_LOCATION` mà `migrator` đọc — hai biến là hai thứ sẽ lệch.")
                .contains("./seed/media:/seed-media:ro")
                .contains("SEED_LOCATION: ${SEED_LOCATION:-}")
                .contains("if [ -n \"$$SEED_LOCATION\" ]; then");

        assertThat(khoiService(local, "migrator"))
                .as("`migrator` của stack local phải chờ `minio-init` xong. Thiếu ràng buộc này "
                        + "thì hàng `attachments` có thể vào CSDL trước byte — CSDL nói tệp tồn "
                        + "tại, `GET /api/v1/public/files/<id>` trả 404.")
                .contains("minio-init:")
                .contains("condition: service_completed_successfully");

        // Chặn xanh-trên-tập-rỗng: hai khẳng định trên chỉ chứng minh DÂY ĐÃ NỐI, không chứng
        // minh có ai bật công tắc. Phải soi cả tệp env.
        //
        // ⚠⚠ SOI TỆP MẪU, KHÔNG SOI `local.env`. Bản đầu của bài này (29/08) khẳng định trên
        //    `deploy/env/local.env` — tệp ấy nằm trong `.gitignore` (nó giữ mật khẩu), nên nó
        //    có ở máy tôi và KHÔNG BAO GIỜ có trên runner. Bài xanh ở máy, đỏ ở CI, và làm đỏ
        //    luôn `Promotion guard` của lượt đề bạt kế tiếp. Đúng ghi chú "xanh ở máy không
        //    phải bằng chứng" ở CLAUDE.md, chỉ khác chiều: ở đây cái CÓ ở máy mới là thứ đánh lừa.
        //
        //    Và lượt sửa ấy lộ ra một lỗi thật, không chỉ lỗi đường dẫn: `local.env.example`
        //    KHÔNG hề có `SEED_LOCATION`. Tức mọi bản clone mới `cp local.env.example local.env`
        //    đều nhận bộ seed TẮT — đúng tình trạng cả đợt 29/08 vừa gỡ bỏ. Bản vá chỉ sống
        //    trong tệp không được commit của một máy (quy tắc 27: một nửa cặp đọc–ghi).
        assertThat(giaTri(doc(tuGocKho("deploy/env/local.env.example")), "SEED_LOCATION"))
                .as("`local.env.example` phải bật bộ seed. Đây là tệp mọi bản clone chép ra, nên "
                        + "tắt ở đây nghĩa là máy dev tiếp theo lại đo nhánh rỗng.")
                .isEqualTo("classpath:db/seed/portal");

        // Tệp env THẬT của máy đang chạy — chỉ soi khi nó tồn tại, và nói rõ phạm vi ấy (luật 28).
        // Trên runner không có tệp này; ở máy dev thì nó là thứ quyết định, không phải tệp mẫu.
        Path envThat = tuGocKho("deploy/env/local.env.example").resolveSibling("local.env");
        if (Files.exists(envThat)) {
            assertThat(giaTri(doc(envThat), "SEED_LOCATION"))
                    .as("`local.env` của máy này đang TẮT bộ seed — stack local sẽ đo nhánh rỗng "
                            + "trong khi staging đo nhánh có dữ liệu.")
                    .isEqualTo("classpath:db/seed/portal");
        }
    }

    // =========================================================================
    // 3. Hàng trong CSDL ↔ byte trên đĩa
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Mỗi hàng `attachments` phải có BYTE THẬT khớp cả kích thước lẫn băm")
    void moiHangDeuCoByteThat() {
        List<String> lech = soLech(moiSeedSql());

        assertThat(lech)
                .as(
                        """
                    Hàng trong migration không khớp byte trên đĩa. Đây là kiểu hỏng CÂM tệ nhất của \
                    bộ seed: không lệnh nào báo sai, CSDL vẫn nói tệp tồn tại, và người dùng thấy \
                    một ô ảnh trống.

                    Sinh lại bằng `python3 deploy/seed/generate.py` — đừng sửa tay tệp SQL.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Không có byte mồ côi — mọi tệp trong `deploy/seed/media` đều có hàng")
    void khongCoByteMoCoi() {
        Path goc = tuGocKho(THU_MUC_BYTE);
        Set<String> coHang = new TreeSet<>();
        Matcher khop = HANG_DINH_KEM.matcher(moiSeedSql());
        while (khop.find()) {
            coHang.add(khop.group(1));
        }

        Set<String> trenDia = new TreeSet<>();
        try (Stream<Path> duyet = Files.walk(goc)) {
            duyet.filter(Files::isRegularFile)
                    .forEach(p -> trenDia.add(goc.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Set<String> moCoi = new TreeSet<>(trenDia);
        moCoi.removeAll(coHang);
        assertThat(moCoi)
                .as(
                        """
                    Tệp nằm trong `deploy/seed/media` mà không hàng nào trỏ tới. `minio-init` đẩy CẢ \
                    THƯ MỤC lên bucket, nên tệp thừa ở đây là tệp thừa trên môi trường thật — và \
                    không ai biết nó từ đâu ra.""")
                .isEmpty();

        // Chiều ngược lại: hàng trỏ tới một tệp KHÔNG có trên đĩa cũng là hỏng — `minio-init` sẽ
        // không đẩy gì lên, và `/api/v1/public/files/<id>` trả 404 trong khi CSDL nói tệp tồn tại.
        Set<String> thieuByte = new TreeSet<>(coHang);
        thieuByte.removeAll(trenDia);
        assertThat(thieuByte)
                .as("Hàng `attachments` trỏ tới byte không có trong `deploy/seed/media` — hỏng câm.")
                .isEmpty();

        assertThat(coHang)
                .as("chặn xanh-trên-tập-rỗng: regex hỏng hoặc đổi đường dẫn thì bài này sẽ so hai "
                        + "tập rỗng với nhau và xanh trọn vẹn. Ngưỡng là SÀN, không phải con số "
                        + "chết — thêm ảnh seed không được làm bài này đỏ.")
                .hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("⛔ Khối xoá bài phải canh theo QUAN HỆ MENU, không phải `DELETE FROM articles` trần")
    void khoiXoaPhaiChuaViTuMenu() {
        // ⚠ Phải bỏ chú thích TRƯỚC khi tìm lệnh. Bản đầu của bài kiểm này quét thẳng văn bản và
        //   đỏ oan: khối chú thích ở trên lệnh có nhắc cụm `DELETE FROM articles` để giải thích vì
        //   sao KHÔNG được viết thế — regex khớp trúng lời giải thích thay vì lệnh (luật 2: canh
        //   cấu trúc, đừng canh văn bản).
        String sql = boChuThich(doc(tuGocKho(SEED_SQL)));
        Matcher khop = Pattern.compile("DELETE\\s+FROM\\s+articles\\b[^;]*;", Pattern.CASE_INSENSITIVE)
                .matcher(sql);

        assertThat(khop.find())
                .as("migration seed phải có đúng một lệnh xoá bài cũ")
                .isTrue();
        String lenh = khop.group();

        assertThat(lenh.replaceAll("\\s+", " "))
                .as(
                        """
                    Lệnh xoá bài phải loại trừ những bài có mục menu trỏ tới.

                    `menu_items.article_id` tham chiếu `articles(id)` mà KHÔNG khai `ON DELETE` — tức \
                    RESTRICT. `DELETE FROM articles` trần sẽ dừng giữa chừng vì lỗi khoá ngoại, SAU \
                    khi đã xoá được một phần; 4 trang tĩnh của `V202608191021` và cả cây menu đi theo.

                    Canh theo quan hệ, đừng canh theo danh sách slug: trang tĩnh thứ năm thêm vào sau \
                    này sẽ không ai nhớ cập nhật danh sách.""")
                .contains("NOT EXISTS")
                .contains("menu_items");

        assertThat(khop.find())
                .as("chỉ được có MỘT lệnh xoá — lệnh thứ hai là một đường không ai đọc")
                .isFalse();
    }

    // =========================================================================
    // 4. Kiểm chứng ngược — chứng minh bài kiểm bắt được vi phạm
    // =========================================================================

    @Test
    @DisplayName("⛔ Và phép so phải THẬT SỰ bắt được lệch — kiểm chứng ngược")
    void batDuocKhiLech() {
        String that = doc(tuGocKho(SEED_SQL));
        assertThat(soLech(that))
                .as("bản thật phải sạch, nếu không thì ba kịch bản dưới vô nghĩa")
                .isEmpty();

        // (a) sai băm — đúng kiểu "ai đó thay ảnh mà quên sinh lại SQL"
        String saiBam = that.replaceFirst(
                "'05f88ed5980fe1915fec0d16c702b4a86e3a32870db944d84e1d034558deab2d'", "'" + "0".repeat(64) + "'");
        assertThat(saiBam).isNotEqualTo(that);
        assertThat(soLech(saiBam))
                .as("đổi băm mà bài kiểm vẫn xanh thì nó chưa từng đọc byte nào")
                .hasSize(1)
                .allSatisfy(d -> assertThat(d).contains("băm"));

        // (b) sai khoá — đúng kiểu "đổi tên tệp một bên"
        String saiKhoa = that.replace("seed/portal/15509c57", "seed/portal/khong-co-that-15509c57");
        assertThat(soLech(saiKhoa))
                .as("khoá trỏ vào tệp không tồn tại phải bị nêu ra")
                .isNotEmpty()
                .allSatisfy(d -> assertThat(d).contains("không có tệp"));

        // (c) sai kích thước
        String saiCo = that.replaceFirst("113720", "999999");
        assertThat(soLech(saiCo)).hasSize(1).allSatisfy(d -> assertThat(d).contains("kích thước"));
    }

    // -------------------------------------------------------------------------

    /** Trả về danh sách điểm lệch giữa hàng SQL và byte trên đĩa; rỗng nghĩa là khớp hết. */
    private static List<String> soLech(String sql) {
        List<String> lech = new ArrayList<>();
        Set<String> daGap = new LinkedHashSet<>();
        Matcher khop = HANG_DINH_KEM.matcher(sql);

        while (khop.find()) {
            String khoa = khop.group(1);
            long co = Long.parseLong(khop.group(3));
            String bam = khop.group(4);
            daGap.add(khoa);

            Path tep = gocKho().resolve(THU_MUC_BYTE).resolve(khoa);
            if (!Files.isRegularFile(tep)) {
                lech.add("%s — không có tệp %s".formatted(khoa, tep));
                continue;
            }
            try {
                long that = Files.size(tep);
                if (that != co) {
                    lech.add("%s — lệch kích thước: SQL ghi %d, tệp thật %d".formatted(khoa, co, that));
                }
                String bamThat = bamSha256(Files.readAllBytes(tep));
                if (!bamThat.equals(bam)) {
                    lech.add("%s — lệch băm: SQL ghi %s, tệp thật %s".formatted(khoa, bam, bamThat));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        if (daGap.isEmpty()) {
            lech.add("không đọc được hàng đính kèm nào từ migration — regex hỏng hoặc tệp đã đổi tên");
        }
        return lech;
    }

    /**
     * Bỏ dòng chú thích SQL. Chỉ bỏ dòng BẮT ĐẦU bằng {@code --} — không cắt {@code --} giữa dòng,
     * vì thân bài viết là HTML và có thể chứa hai gạch nối; cắt giữa dòng sẽ băm nát chuỗi ký tự.
     */
    private static String boChuThich(String sql) {
        return sql.lines()
                .filter(dong -> !dong.stripLeading().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String bamSha256(byte[] du) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(du));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Giá trị của một khoá trong tệp env mẫu — bỏ chú thích cuối dòng theo đúng luật Compose. */
    private static String giaTri(String noiDung, String ten) {
        Matcher khop =
                Pattern.compile("^[ \\t]*" + ten + "=(.*)$", Pattern.MULTILINE).matcher(noiDung);
        if (!khop.find()) {
            return fail(
                    "Tệp env mẫu không khai `%s=` — biến này phải có mặt kể cả khi để trống, "
                            + "vì tệp mẫu là danh sách đầy đủ mà người điền `.env` dựa vào.",
                    ten);
        }
        String con = khop.group(1);
        // Compose chỉ cắt chú thích khi có KHOẢNG TRẮNG trước `#`.
        Matcher cat = Pattern.compile("\\s#").matcher(con);
        return (cat.find() ? con.substring(0, cat.start()) : con).trim();
    }

    /** Cắt lấy khối YAML của một service — từ dòng `  <tên>:` tới service kế tiếp cùng mức. */
    private static String khoiService(String compose, String ten) {
        Matcher khop = Pattern.compile(
                        "^  " + ten + ":$(.*?)(?=^  [a-z][a-z0-9-]*:$)", Pattern.MULTILINE | Pattern.DOTALL)
                .matcher(compose);
        return khop.find() ? khop.group(1) : fail("compose.prod.yml không có service `%s`", ten);
    }

    /** Mọi tệp văn bản dưới `deploy/` — bộ canh phải soi cả cây, không soi một danh sách viết tay. */
    private static List<Path> tepTrongDeploy() {
        try (Stream<Path> duyet = Files.walk(tuGocKho("deploy"))) {
            return duyet.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(".*\\.(yml|yaml|sh|md|example|conf|template)"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> tepMigration(Path thuMuc) {
        try (Stream<Path> duyet = Files.list(thuMuc)) {
            return duyet.filter(p -> p.getFileName().toString().matches("[VR].*\\.sql"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nội dung của MỌI tệp migration seed, nối lại — bộ canh byte soi toàn bộ, không soi một tệp. */
    private static String moiSeedSql() {
        Path goc = tuGocKho(THU_MUC_SEED);
        try (Stream<Path> duyet = Files.walk(goc)) {
            List<Path> tep = duyet.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
            // Không tệp nào ⇒ đường dẫn đã đổi ⇒ mọi bài dùng hàm này sẽ xanh trên tập rỗng.
            assertThat(tep)
                    .as("Không thấy tệp seed nào trong `%s`", THU_MUC_SEED)
                    .isNotEmpty();
            StringBuilder sb = new StringBuilder();
            for (Path f : tep) {
                sb.append(doc(f)).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path tuGocKho(String duongDanTuongDoi) {
        Path ungVien = gocKho().resolve(duongDanTuongDoi);
        return Files.exists(ungVien) ? ungVien : fail("Không tìm thấy %s tính từ %s", duongDanTuongDoi, gocKho());
    }

    /** Đi ngược lên từ thư mục đang chạy để tìm gốc kho mã — cùng cách các lớp khác trong gói này dùng. */
    private static Path gocKho() {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.exists(hienTai.resolve("deploy/compose.prod.yml"))) {
                return hienTai;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy gốc kho tính từ %s", System.getProperty("user.dir"));
    }
}
