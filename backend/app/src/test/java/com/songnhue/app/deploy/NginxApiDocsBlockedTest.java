package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tài liệu API không được ra ngoài — <b>T11.6-a</b>, nợ nhận từ WS-4.
 *
 * <h2>Trạng thái đo được trước bản vá (staging, 3/9/2026)</h2>
 *
 * <pre>
 *   https://staging.songnhue.com/v3/api-docs            → 404  (trang 404 của Next)
 *   https://staging.songnhue.com/swagger-ui.html        → 404
 *   https://admin-staging.songnhue.com/v3/api-docs      → 200, 2138 byte = index.html của SPA
 *   https://admin-staging.songnhue.com/swagger-ui.html  → 200, 2138 byte = index.html của SPA
 * </pre>
 *
 * Tài liệu API <b>không</b> ra ngoài. Nhưng lý do là một <b>tai nạn của định tuyến</b>: backend chỉ
 * với tới được qua tiền tố {@code /api/}, mà {@code /v3/api-docs} và {@code /swagger-ui.html} nằm
 * ngoài tiền tố ấy nên rơi vào {@code location /} và được trả lời bằng nội dung tĩnh.
 *
 * <p>Cùng lúc đó {@code application.yml} khẳng định từ WS-4 rằng <i>"Trên production nginx chặn 2
 * đường dẫn này"</i> trong khi cấu hình nginx của kho <b>không có một dòng nào</b> về swagger. Một
 * cam kết ghi trong chú thích mà không cơ chế nào thi hành — hình dạng dự án đã trả giá nhiều lần
 * ({@code architecture-review.md} §10.61 CSP, §10.69 trần multipart).
 *
 * <h2>⚠ Bài này canh CẤU TRÚC, không canh sự có mặt của một chuỗi (luật 2)</h2>
 *
 * Khẳng định không phải "tệp cấu hình có chữ swagger" — nó là: <b>mọi khối {@code server} chuyển
 * tiếp tới một ứng dụng đều phải {@code include} tệp chặn</b>. Nhờ vậy một khối {@code server} mới
 * thêm sau này (tên miền thứ tư) cũng bị bắt, chứ không phải chỉ hai khối biết tên hôm nay.
 *
 * <h2>⚠ Phạm vi của chính bộ canh này (luật 28)</h2>
 *
 * <ul>
 *   <li>Nó canh <b>nginx biên</b> — chỗ duy nhất mọi lượt gọi từ Internet đi qua. Nó KHÔNG canh
 *       nginx trong image {@code admin-app}: khối ấy nằm sau nginx biên nên đã được che.
 *   <li>Nó chứng minh <b>cấu hình khai đúng</b>, KHÔNG chứng minh <b>máy chủ đã nạp</b>. Phép đo
 *       thật là bốn lượt {@code curl} ở trên, chạy lại sau lượt deploy kế tiếp.
 *   <li>Khối tên miền kho tệp ({@code $files_upstream} → MinIO) cố ý đứng ngoài: MinIO không phục
 *       vụ tài liệu API, và thêm một {@code location} regex vào đó là thêm một thứ có thể va vào
 *       đường ký SigV4 mà không đổi lại được gì.
 * </ul>
 */
class NginxApiDocsBlockedTest {

    private static final String TEMPLATE = "deploy/nginx/templates/default.conf.template";
    private static final String SNIPPET = "deploy/nginx/snippets/chan-tai-lieu-api.conf";
    private static final String DUONG_INCLUDE = "/etc/nginx/snippets/chan-tai-lieu-api.conf";

    /** Upstream cố ý KHÔNG phải chặn — xem phần "Phạm vi" ở javadoc lớp. */
    private static final String UPSTREAM_MIEN_TRU = "files_upstream";

    @Test
    @DisplayName("⭐⭐ Mọi khối server chuyển tiếp tới ứng dụng đều include tệp chặn tài liệu API")
    void moiKhoiServerUngDungDeuChanTaiLieuApi() throws IOException {
        Map<String, String> khoi = khoiServerCua(docTemplate());

        List<String> phaiChan = new ArrayList<>();
        List<String> thieu = new ArrayList<>();
        for (Map.Entry<String, String> e : khoi.entrySet()) {
            if (!laVhostUngDung(e.getKey(), e.getValue())) {
                continue;
            }
            phaiChan.add(e.getKey());
            if (!e.getValue().contains(DUONG_INCLUDE)) {
                thieu.add(e.getKey());
            }
        }

        // ⛔ Chống xanh-trên-tập-rỗng (luật 7). Đổi tên biến upstream hay đổi cách viết
        //    `proxy_pass` là tập này về 0 và bài xanh trọn vẹn mà chưa kiểm gì.
        assertThat(phaiChan)
                .as(
                        "không tách được khối `server` nào có `proxy_pass` ngoài %s — bài kiểm đang "
                                + "soi nhầm tệp hoặc bộ tách khối đã hỏng",
                        UPSTREAM_MIEN_TRU)
                .hasSizeGreaterThanOrEqualTo(2);

        assertThat(thieu)
                .as(
                        "Khối server này chuyển tiếp tới một ứng dụng nhưng KHÔNG include %s — "
                                + "`/v3/api-docs` và `/swagger-ui.html` sẽ đi tới đâu là chuyện của "
                                + "định tuyến, không phải chuyện ai đó quyết. Đã kiểm %d khối phải chặn.",
                        DUONG_INCLUDE, phaiChan.size())
                .isEmpty();
    }

    @Test
    @DisplayName("Tệp chặn dùng `location ~` (regex) — tiền tố sẽ THUA `location /api/`")
    void dungRegexChuKhongDungTienTo() throws IOException {
        String snippet = docSnippet();

        String[] khai = khaiLocationCua(snippet);

        assertThat(khai[0])
                .as("nginx xét regex TRƯỚC mọi khối tiền tố (trừ `^~`). Viết tiền tố thì hôm nay "
                        + "vẫn đúng, nhưng ngày springdoc chuyển đường dẫn vào dưới `/api/` nó "
                        + "sẽ thua `location /api/` và bản vá im lặng ngừng có tác dụng")
                .isEqualTo("~");

        assertThat(khai[1]).contains("swagger-ui").contains("v3/api-docs");

        assertThat(snippet)
                .as("404 chứ không 403: 403 vẫn xác nhận đường dẫn tồn tại")
                .containsPattern("return\\s+404");
    }

    /**
     * ⭐ Đối chiếu <b>hai chiều</b> với {@code application.yml}: mọi đường dẫn springdoc khai ở đó
     * đều phải khớp mẫu regex trong tệp chặn.
     *
     * <p>Đây là vế "hai nơi con người phải nhớ" (luật 14). Đổi {@code springdoc.api-docs.path} mà
     * quên tệp nginx là mở lại đúng cánh cửa vừa đóng, và không lượt rà nào kêu — hai tệp nằm ở hai
     * kho khác nhau, không trình biên dịch nào nối chúng.
     */
    @Test
    @DisplayName("⭐ Mọi đường dẫn springdoc khai trong application.yml đều khớp mẫu chặn")
    void duongDanSpringdocDeuBiMauChanBat() throws IOException {
        Pattern mauChan = mauChanTaiLieuApi();
        List<String> duong = duongDanSpringdoc();

        assertThat(duong)
                .as("không đọc được đường dẫn springdoc nào từ application.yml — bài kiểm soi nhầm khoá")
                .hasSizeGreaterThanOrEqualTo(2);

        List<String> lot =
                duong.stream().filter(d -> !mauChan.matcher(d).find()).toList();

        assertThat(lot)
                .as(
                        "springdoc phục vụ những đường dẫn này nhưng mẫu chặn của nginx KHÔNG bắt được "
                                + "chúng — sửa `%s` cho khớp, đừng sửa bài kiểm",
                        SNIPPET)
                .isEmpty();
    }

    /**
     * ⭐ Tự kiểm: chứng minh bộ canh phân biệt được hai trạng thái (luật 1 · luật 9).
     *
     * <p>Ba biến thể hỏng, ba lý do hỏng khác nhau. Bài trước đó khẳng định về <b>số lượng</b> khối
     * phải chặn — một khẳng định không chia sẻ giả định nào với mẫu regex (luật 29).
     */
    @Test
    @DisplayName("⭐ Tự kiểm: bộ canh bắt được khối server thiếu include, và bắt được tiền tố thay regex")
    void tuKiemBatDuocViPham() throws IOException {
        String that = docTemplate();

        // (a) Gỡ include khỏi MỘT khối server → phải còn đúng một vhost thiếu.
        //     ⚠ Gọi CHÍNH `laVhostUngDung` chứ không chép lại điều kiện: một bộ canh mà bài tự kiểm
        //       của nó dùng điều kiện riêng thì hai bên trôi khỏi nhau, và bài tự kiểm sẽ chứng minh
        //       cho một cơ chế không còn tồn tại (đúng bẫy đã trả giá ở `VongDoiPhienBanTest`).
        String daGo = that.replaceFirst("(?m)^[ \\t]*include\\s+" + Pattern.quote(DUONG_INCLUDE) + ";[ \\t]*\\R", "");
        assertThat(daGo)
                .as("bản hỏng phải KHÁC bản thật, nếu không bài tự kiểm chỉ chạy lại bản thật (luật 10)")
                .isNotEqualTo(that);

        long dayDu = soVhostDaChan(that);
        long conLai = soVhostDaChan(daGo);
        assertThat(dayDu)
                .as("bản THẬT phải có ít nhất 2 vhost đã chặn — dưới mức ấy thì phép trừ dưới đây vô nghĩa")
                .isGreaterThanOrEqualTo(2);
        assertThat(conLai)
                .as("gỡ một dòng include mà số vhost đã chặn không giảm ⇒ bộ tách khối server đang hỏng")
                .isEqualTo(dayDu - 1);

        // (b) Mẫu chặn viết thành TIỀN TỐ → phép khẳng định regex phải đỏ.
        //     ⚠ Bản đầu của bài này dùng mẫu `location\s+(\S+)\s+([^{]+)\{` — đòi hai tiếng trước
        //       `{`, tức nó KHÔNG ĐỌC NỔI một khối tiền tố (`location /swagger-ui {` chỉ có một
        //       tiếng). Hậu quả: bản hỏng làm bài đỏ ở câu "tệp chặn không khai khối location nào"
        //       thay vì ở câu về tiền tố — một bộ canh đỏ SAI LÝ DO đẩy người đọc đi dò nhầm chỗ
        //       (§10.68-B). `khaiLocationCua` đọc được cả hai dạng, nên nay nó đỏ đúng chỗ.
        String tienTo = docSnippet().replace("location ~ ^/(swagger-ui|v3/api-docs)", "location /swagger-ui");
        assertThat(tienTo).as("bản hỏng phải KHÁC bản thật (luật 10)").isNotEqualTo(docSnippet());
        assertThat(khaiLocationCua(tienTo)[0])
                .as("bản hỏng phải không còn là `~`")
                .isNotEqualTo("~");

        // (c) Mẫu chặn bỏ sót một đường dẫn springdoc → phép đối chiếu hai chiều phải bắt.
        Pattern mauThieu = Pattern.compile("^/(swagger-ui)");
        assertThat(duongDanSpringdoc().stream()
                        .filter(d -> !mauThieu.matcher(d).find())
                        .toList())
                .as("bỏ `v3/api-docs` khỏi mẫu mà phép đối chiếu vẫn rỗng ⇒ nó chưa đọc được gì")
                .isNotEmpty();
    }

    // -------------------------------------------------------------------------

    /**
     * Một khối {@code server} có phải <b>vhost ứng dụng</b> không — tức có phải chỗ mà một người
     * ngoài Internet gõ tên miền vào rồi được một ứng dụng trả lời.
     *
     * <p>Bốn điều kiện, và ba điều kiện đầu là để LOẠI đúng ba khối cố ý đứng ngoài:
     *
     * <ul>
     *   <li>{@code listen 443 ssl} — loại khối cổng 80 (chỉ chuyển hướng + ACME + hai đích
     *       healthcheck). ⚠ Khối ấy CÓ {@code proxy_pass}: {@code /healthz/upstream} chuyển tiếp để
     *       đo được cả lỗi phân giải DNS. Một điều kiện chỉ dựa vào {@code proxy_pass} sẽ đòi chặn
     *       swagger ở đó — vô nghĩa, và tệ hơn là dạy người đọc rằng bộ canh này hay báo oan.
     *   <li>{@code server_name} khác {@code _} — loại khối {@code default_server} đóng cửa.
     *   <li>không phải {@link #UPSTREAM_MIEN_TRU} — loại tên miền kho tệp (MinIO).
     *   <li>có {@code proxy_pass} — nếu không thì nó chẳng dẫn tới ứng dụng nào.
     * </ul>
     */
    /** Đếm số vhost ứng dụng ĐÃ include tệp chặn, trên một nội dung cấu hình bất kỳ. */
    private static long soVhostDaChan(String cauHinh) {
        return khoiServerCua(cauHinh).entrySet().stream()
                .filter(e -> laVhostUngDung(e.getKey(), e.getValue()))
                .filter(e -> e.getValue().contains(DUONG_INCLUDE))
                .count();
    }

    private static boolean laVhostUngDung(String tenMien, String than) {
        return than.contains("proxy_pass")
                && than.contains("listen 443 ssl")
                && !"_".equals(tenMien)
                && !than.contains(UPSTREAM_MIEN_TRU);
    }

    /**
     * Khai báo {@code location} của tệp chặn, tách thành {@code [bổ-từ, mẫu]}.
     *
     * <p>Đọc được CẢ hai dạng — {@code location ~ <mẫu> {} và {@code location <tiền-tố> {} — vì bài
     * tự kiểm phải dựng ra dạng thứ hai rồi chứng minh bộ canh từ chối nó. Bổ từ rỗng nghĩa là
     * tiền tố.
     */
    private static String[] khaiLocationCua(String snippet) {
        Matcher m =
                Pattern.compile("(?m)^[ \\t]*location\\s+([^{\\n]+?)\\s*\\{").matcher(snippet);
        assertThat(m.find()).as("tệp chặn không khai khối `location` nào").isTrue();

        String khai = m.group(1).trim();
        for (String boTu : List.of("~*", "~", "^~", "=")) {
            if (khai.startsWith(boTu + " ")) {
                return new String[] {boTu, khai.substring(boTu.length()).trim()};
            }
        }
        return new String[] {"", khai};
    }

    /** Mẫu regex trong tệp chặn, chuyển thành {@link Pattern} thật để đem đi thử đường dẫn. */
    private static Pattern mauChanTaiLieuApi() throws IOException {
        String[] khai = khaiLocationCua(docSnippet());
        assertThat(khai[0]).as("tệp chặn không khai `location ~ <mẫu>`").isEqualTo("~");
        return Pattern.compile(khai[1]);
    }

    /**
     * Đọc đường dẫn springdoc từ {@code application.yml}. Đọc giá trị THẬT chứ không chép lại hằng
     * số — chép lại là dựng thêm một nơi thứ ba phải nhớ.
     *
     * <p>{@code /swagger-ui.html} kéo theo cả cây tài nguyên webjar dưới {@code /swagger-ui/}, nên
     * đường dẫn ấy được kiểm ở cả hai dạng.
     */
    private static List<String> duongDanSpringdoc() throws IOException {
        String yml =
                Files.readString(timTuGocKho("backend/app/src/main/resources/application.yml"), StandardCharsets.UTF_8);

        int bat = yml.indexOf("\nspringdoc:");
        assertThat(bat).as("application.yml không có khối `springdoc:`").isNotNegative();

        List<String> ket = new ArrayList<>();
        for (String dong : yml.substring(bat + 1).lines().toList()) {
            String khongChuThich = dong.replaceFirst("#.*$", "");
            if (!khongChuThich.isBlank() && !khongChuThich.startsWith(" ") && !khongChuThich.startsWith("springdoc:")) {
                break; // sang khối cấp 1 kế tiếp
            }
            Matcher m = Pattern.compile("\\bpath:\\s*(/\\S+)").matcher(khongChuThich);
            if (m.find()) {
                ket.add(m.group(1));
            }
        }
        if (ket.contains("/swagger-ui.html")) {
            ket.add("/swagger-ui/index.html"); // cây webjar đi kèm giao diện
        }
        return ket;
    }

    private static String docTemplate() throws IOException {
        return Files.readString(timTuGocKho(TEMPLATE), StandardCharsets.UTF_8);
    }

    private static String docSnippet() throws IOException {
        return Files.readString(timTuGocKho(SNIPPET), StandardCharsets.UTF_8);
    }

    /**
     * Tách các khối {@code server { … }} bằng cách đếm ngoặc, không bằng regex — khối lồng nhau làm
     * regex sai lặng lẽ. Khoá là {@code server_name} (hoặc số thứ tự khi không có), để thông báo lỗi
     * gọi đích danh khối nào thiếu thay vì in cả nghìn dòng cấu hình.
     */
    private static Map<String, String> khoiServerCua(String cauHinh) {
        Map<String, String> ket = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^server\\s*\\{").matcher(cauHinh);
        int stt = 0;
        while (m.find()) {
            int dau = m.end() - 1;
            int i = dau;
            int sau = 0;
            while (i < cauHinh.length()) {
                char c = cauHinh.charAt(i);
                if (c == '{') {
                    sau++;
                } else if (c == '}') {
                    sau--;
                    if (sau == 0) {
                        break;
                    }
                }
                i++;
            }
            String than = cauHinh.substring(dau, Math.min(i + 1, cauHinh.length()));
            Matcher ten = Pattern.compile("(?m)^\\s*server_name\\s+([^;]+);").matcher(than);
            ket.put(ten.find() ? ten.group(1).trim() : "server#" + (++stt), than);
        }
        assertThat(ket)
                .as("không tách được khối `server` nào — bài kiểm đang soi nhầm tệp")
                .isNotEmpty();
        return ket;
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
