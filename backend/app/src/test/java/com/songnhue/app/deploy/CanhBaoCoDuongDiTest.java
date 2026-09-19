package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * <b>Chuông cảnh báo phải có ĐƯỜNG ĐI từ chỉ số tới người nhận — T61.5.</b>
 *
 * <h2>Chuyện đã xảy ra — đo 14/09/2026</h2>
 *
 * Chạy Prometheus v3.1.0 với đúng {@code deploy/observability/prometheus.yml} của kho: target
 * {@code ${PROD_APP_TARGET}} được dùng <b>nguyên văn</b> (Prometheus ⛔ thay biến môi trường trong tệp
 * cấu hình) ⇒ {@code invalid URL escape "%7B"} ⇒ 0 chỉ số ứng dụng. {@code NguonDuLieuImLang} và
 * {@code SaoLuuQuaHan} nằm {@code inactive} <b>vĩnh viễn</b> vì ⛔ có chuỗi nào để so, còn
 * {@code UngDungKhongPhanHoi} kêu thường trực. Và ⛔ có Alertmanager nào để gửi đi.
 *
 * <p>Năm mắt xích, mỗi mắt một bài: cấu hình ⛔ mang <code>${</code> · mọi chỗ cắm được điền và thiếu là dừng
 * · luật cho production có vế <i>vắng mặt</i> · tuyến gửi đúng như đã chốt · cửa nginx có hai lớp khoá.
 *
 * <p>⚠ Giới hạn (luật 28): bài này đọc TỆP. Tuyến gửi và cửa nginx đã được chạy thật ở máy ngày
 * 14/09/2026 (Alertmanager v0.34.0 + máy chủ giả Slack/Telegram + mailpit; nginx 1.30 với template thật
 * — 5 ca), ghi ở {@code master-tracking.md} T61.5. Bài này canh để các tệp ấy ⛔ trôi khỏi trạng thái đã đo.
 */
class CanhBaoCoDuongDiTest {

    private static final Pattern CHO_CAM = Pattern.compile("__([A-Z][A-Z0-9_]*)__");

    @Test
    @DisplayName("⛔⛔ prometheus.yml và alertmanager.yml ⛔ mang `${…}` — hai phần mềm ấy ⛔ thay biến")
    void cauHinhKhongMangBienMoiTruong() {
        for (String tep : List.of("deploy/observability/prometheus.yml", "deploy/observability/alertmanager.yml")) {
            assertThat(coBienMoiTruong(doc(tep)))
                    .as("%s có `${…}` ngoài chú thích ⇒ sẽ được dùng NGUYÊN VĂN (target `%%7B`, 0 chỉ số)", tep)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("⛔⛔ Mọi chỗ cắm `__TÊN__` đều được điền lúc khởi động, và biến ấy thiếu là DỪNG (`:?`)")
    void moiChoCamDuocDienVaThieuLaDung() {
        String compose = doc("deploy/compose.observability.yml");
        Set<String> choCam = new TreeSet<>();
        for (String tep : List.of("deploy/observability/prometheus.yml", "deploy/observability/alertmanager.yml")) {
            Matcher m = CHO_CAM.matcher(boChuThich(doc(tep)));
            while (m.find()) {
                choCam.add(m.group(1));
            }
        }
        assertThat(choCam).as("chống tập rỗng").contains("PROD_METRICS_HOST", "TELEGRAM_CHAT_ID", "ALERT_EMAIL_TO");

        String lenhDien =
                compose.lines().filter(l -> l.contains("dien-cau-hinh.sh")).collect(Collectors.joining("\n")) + "\n"
                        + compose.lines()
                                .filter(l -> l.matches("\\s+(SMTP|ALERT|TELEGRAM|PROD)[A-Z_ ]+"))
                                .collect(Collectors.joining("\n"));
        List<String> loi = new ArrayList<>();
        for (String ten : choCam) {
            if (!Pattern.compile("\\b" + ten + "\\b").matcher(lenhDien).find()) {
                loi.add(ten + ": ⛔ lệnh `dien-cau-hinh.sh` nào điền");
            }
            if (!compose.contains(ten + ": ${" + ten + ":?")) {
                loi.add(ten + ": ⛔ khai `" + ten + ": ${" + ten + ":?…}` — thiếu biến sẽ ⛔ dừng");
            }
        }
        assertThat(loi).as("chỗ cắm ⛔ có đường điền").isEmpty();

        Set<String> daKhai = doc("deploy/env/staging.env.example")
                .lines()
                .map(l -> l.replaceFirst("^\\s*([A-Z][A-Z0-9_]*)=.*$", "$1"))
                .collect(Collectors.toSet());
        Matcher bien = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):\\?").matcher(compose);
        List<String> thieuMau = new ArrayList<>();
        while (bien.find()) {
            if (!daKhai.contains(bien.group(1))) {
                thieuMau.add(bien.group(1));
            }
        }
        assertThat(thieuMau)
                .as("biến bắt buộc của giám sát ⛔ có trong staging.env.example")
                .isEmpty();
    }

    @Test
    @DisplayName("⭐⭐ Tuyến gửi đúng như đã chốt 14/09 — prod critical 3 kênh · warning 2 · staging chỉ Slack")
    @SuppressWarnings("unchecked")
    void tuyenGuiDungChot() {
        Map<String, Object> am = new Yaml().load(doc("deploy/observability/alertmanager.yml"));
        Map<String, Object> route = (Map<String, Object>) am.get("route");
        Map<String, Set<String>> kenh = ((List<Map<String, Object>>) am.get("receivers"))
                .stream().collect(Collectors.toMap(r -> (String) r.get("name"), r -> r.keySet().stream()
                        .filter(k -> k.endsWith("_configs"))
                        .collect(Collectors.toCollection(TreeSet::new))));

        assertThat(kenh.get((String) route.get("receiver")))
                .as("tuyến MẶC ĐỊNH phải là tuyến ỒN NHẤT — cảnh báo thiếu nhãn môi trường ⛔ được rơi vào im lặng")
                .containsExactly("email_configs", "slack_configs", "telegram_configs");

        List<Map<String, Object>> con = (List<Map<String, Object>>) route.get("routes");
        assertThat(kenh.get(nhanCho(con, "environment=\"staging\""))).containsExactly("slack_configs");
        assertThat(kenh.get(nhanCho(con, "severity=\"warning\"")))
                .as("production warning: Slack + Telegram, ⛔ email")
                .containsExactly("slack_configs", "telegram_configs");
    }

    @Test
    @DisplayName("⛔⛔ Luật sao lưu và poller có vế VẮNG MẶT cho production — ⛔ có chuỗi thì vẫn phải kêu")
    @SuppressWarnings("unchecked")
    void luatProductionCoVeVangMat() {
        Map<String, Object> goc = new Yaml().load(doc("deploy/observability/alerts.yml"));
        Map<String, Map<String, Object>> luat = ((List<Map<String, Object>>) goc.get("groups"))
                .stream()
                        .flatMap(g -> ((List<Map<String, Object>>) g.get("rules")).stream())
                        .collect(Collectors.toMap(r -> (String) r.get("alert"), r -> r));

        assertThat(luat).as("chống tập rỗng").hasSizeGreaterThanOrEqualTo(10);
        luat.entrySet().stream().filter(e -> !e.getKey().equals("Watchdog")).forEach(e -> assertThat(
                        ((Map<String, String>) e.getValue().get("labels")).get("severity"))
                .as("%s: severity phải là critical|warning — Alertmanager định tuyến bằng nhãn này", e.getKey())
                .isIn("critical", "warning"));
        for (String ten : List.of("SaoLuuQuaHan", "NguonDuLieuImLang", "ChiSoProductionVangMat")) {
            assertThat(luat).containsKey(ten);
            assertThat((String) luat.get(ten).get("expr"))
                    .as("%s thiếu `absent(…environment=\"production\"…)` ⇒ im VĨNH VIỄN khi ⛔ có chỉ số", ten)
                    .containsPattern("absent\\([^)]*environment=\"production\"");
        }
    }

    @Test
    @DisplayName("⛔ Chuông sao lưu CHỈ canh production — staging ⛔ có lịch sao lưu nên chuỗi của nó luôn -1 (T61.26)")
    @SuppressWarnings("unchecked")
    void chuongSaoLuuChiProduction() {
        Map<String, Object> luat = luatTheoTen().get("SaoLuuQuaHan");
        String expr = (String) luat.get("expr");
        long soLanDoc = Pattern.compile("songnhue_backup_age_seconds")
                .matcher(expr)
                .results()
                .count();
        long soLanCoNhan = Pattern.compile("songnhue_backup_age_seconds\\{environment=\"production\"}")
                .matcher(expr)
                .results()
                .count();
        assertThat(soLanDoc).as("chống tập rỗng").isGreaterThanOrEqualTo(3);
        assertThat(soLanCoNhan)
                .as(
                        "mọi lần đọc chỉ số sao lưu phải mang {environment=\"production\"} — một vế trần là staging kêu Slack mỗi 4h")
                .isEqualTo(soLanDoc);
        assertThat(((Map<String, String>) luat.get("labels")).get("environment"))
                .isEqualTo("production");
    }

    @Test
    @DisplayName("⛔⛔ Chuông canh: Watchdog luôn kêu → tuyến ĐẦU → chỉ webhook url_file, ⛔ gửi tin resolved (T61.25)")
    @SuppressWarnings("unchecked")
    void chuongCanhDiDungDuong() {
        Map<String, Object> wd = luatTheoTen().get("Watchdog");
        assertThat(wd).as("⛔ có luật Watchdog").isNotNull();
        assertThat(((String) wd.get("expr")).trim()).isEqualTo("vector(1)");
        assertThat(wd)
                .as("`for` làm chuông canh im lúc Prometheus vừa khởi động")
                .doesNotContainKey("for");

        Map<String, Object> am = new Yaml().load(doc("deploy/observability/alertmanager.yml"));
        Map<String, Object> route = (Map<String, Object>) am.get("route");
        List<Map<String, Object>> con = (List<Map<String, Object>>) route.get("routes");
        assertThat((List<String>) con.get(0).get("matchers"))
                .as("tuyến Watchdog phải ĐẦU danh sách — đứng sau thì rơi vào tuyến mặc định (email + Telegram)")
                .containsExactly("alertname=\"Watchdog\"");
        assertThat(con.get(0).get("repeat_interval"))
                .as("healthchecks.io đặt Period 5 phút ⇒ lặp phải ngắn hơn hẳn")
                .isIn("1m", "2m");

        Map<String, Object> nhan = ((List<Map<String, Object>>) am.get("receivers"))
                .stream()
                        .filter(r -> r.get("name").equals(con.get(0).get("receiver")))
                        .findFirst()
                        .orElseGet(() -> fail("⛔ có receiver cho tuyến Watchdog"));
        assertThat(nhan.keySet()).containsExactlyInAnyOrder("name", "webhook_configs");
        Map<String, Object> webhook = ((List<Map<String, Object>>) nhan.get("webhook_configs")).get(0);
        assertThat(webhook)
                .as("URL ping mang mã bí mật ⇒ `url_file`, ⛔ `url`")
                .containsKey("url_file")
                .doesNotContainKey("url");
        assertThat(webhook.get("send_resolved"))
                .as("tin resolved cũng là một lượt POST ⇒ healthchecks.io đếm là CÒN SỐNG")
                .isEqualTo(false);

        assertThat(doc("deploy/observability/alertmanager.yml"))
                .as("luật nén theo UngDungKhongPhanHoi ⛔ được nuốt Watchdog")
                .contains("'alertname!=\"Watchdog\"'");
        String compose = doc("deploy/compose.observability.yml");
        assertThat(compose)
                .contains("HEALTHCHECKS_PING_URL: ${HEALTHCHECKS_PING_URL:?")
                .contains("bi-mat HEALTHCHECKS_PING_URL " + webhook.get("url_file"));
    }

    /**
     * Bóc phần KHOÁ của một dòng trong khối {@code map} của nginx.
     *
     * <p>Cú pháp là {@code <khoá> <giá trị>;}. Khoá có thể được đặt trong nháy — và khi ấy nó
     * chứa khoảng trắng ({@code "Bearer <token>"}), nên tách theo khoảng trắng là sai.
     */
    private static String khoaCuaDong(String dong) {
        String t = dong.trim();
        if (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        if (t.startsWith("\"") || t.startsWith("'")) {
            char nhay = t.charAt(0);
            int dong2 = t.indexOf(nhay, 1);
            return dong2 < 0 ? t : t.substring(0, dong2 + 1);
        }
        int cach = t.indexOf(' ');
        return cach < 0 ? t : t.substring(0, cach);
    }

    @Test
    @DisplayName("⛔⛔⛔ T63.19 — KHOÁ của một `map` nginx ⛔ được chứa chỗ cắm `${...}`")
    void khoaMapKhongDuocMangChoCam() {
        String tpl = doc("deploy/nginx/templates/default.conf.template");

        // Bóc từng khối `map <nguồn> <đích> { ... }` rồi soi các dòng KHOÁ bên trong.
        Matcher m = Pattern.compile("^map\\s+\\S+\\s+\\S+\\s*\\{(.*?)^}", Pattern.DOTALL | Pattern.MULTILINE)
                .matcher(tpl);
        List<String> viPham = new ArrayList<>();
        int soKhoi = 0;
        while (m.find()) {
            soKhoi++;
            for (String dong : m.group(1).split("\n")) {
                String t = dong.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                // ⚠⚠ Chỉ soi KHOÁ, ⛔ soi cả dòng. `map_hash_bucket_size` giới hạn **khoá**;
                //    giá trị ⛔ vào bảng băm. Bản đầu của bộ canh này quét cả dòng nên nó
                //    đỏ ngay lượt chạy đầu ở `map $host $robots_tag { default "${ROBOTS_TAG}"; }`
                //    — một **dương tính giả**, vì chỗ cắm ấy nằm ở giá trị và hoàn toàn an toàn.
                //    Một bộ canh ⛔ phân biệt được khoá với giá trị sẽ phạt đúng dòng ⛔ có tội.
                String khoa = khoaCuaDong(t);
                if (khoa.contains("${")) {
                    viPham.add(t);
                }
            }
        }

        // ⛔ Chống tập rỗng (luật 7): regex hỏng thì bài này xanh mà ⛔ canh gì. Kho có
        //   sẵn hai `map` ⛔ tranh cãi (`$http_upgrade`, `$host`) làm mỏ neo.
        assertThat(soKhoi)
                .as("⛔ bóc được khối `map` nào — regex hỏng, hay template đã đổi cấu trúc?")
                .isGreaterThanOrEqualTo(2);

        assertThat(viPham)
                .as(
                        """
                        Khoá của `map` nginx đi vào một BẢNG BĂM có trần `map_hash_bucket_size`                         (mặc định 64 byte). Một chỗ cắm `${...}` mang giá trị **⛔ biết trước độ                         dài**, nên nó biến trần ấy thành một quả mìn hẹn giờ:

                          nginx: [emerg] could not build map_hash,                         you should increase map_hash_bucket_size: 64

                        ⇒ nginx ⛔ KHỞI ĐỘNG NỔI, và `restart: unless-stopped` biến đó thành vòng                         quay vô tận. Ngày 17/09/2026 chuyện này hạ **toàn bộ staging**: mọi                         container `healthy`, riêng nginx `Restarting (1)`, ⛔ gì lắng nghe ở                         80/443 — và lượt CD vẫn in `Container songnhue-nginx Started` (T63.19).

                        ⛔ Nâng `map_hash_bucket_size` chỉ đẩy trần đi xa hơn; một giá trị dài hơn                         nữa lại hạ site lần nữa. Hãy SO TRỰC TIẾP trong `location`:

                          if ($http_authorization != "Bearer ${MOT_BIEN}") { return 403; }

                        thì độ dài của giá trị thôi ⛔ còn là một tham số của việc nginx có sống                         hay ⛔ (luật 12).""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ nginx mở /actuator/prometheus với HAI lớp khoá: IP và token, cả hai bắt buộc")
    void cuaNginxHaiLopKhoa() {
        String tpl = doc("deploy/nginx/templates/default.conf.template");
        Matcher khoi = Pattern.compile("location = /actuator/prometheus \\{(.*?)\\n    }", Pattern.DOTALL)
                .matcher(tpl);
        assertThat(khoi.find())
                .as("⛔ tìm thấy `location = /actuator/prometheus`")
                .isTrue();
        // ⚠ Canh BẤT BIẾN (có lớp IP + lớp token + đường từ chối), ⛔ canh nguyên văn một
        //   biểu thức: bản cũ ghim chuỗi `if ($metrics_token_ok = 0)` nên nó đỏ khi T63.19
        //   đổi CÁCH so token, dù cam kết "hai lớp khoá" ⛔ hề đổi (luật 2 · §11.16).
        assertThat(khoi.group(1)).contains("allow ${METRICS_ALLOW_IP};", "deny all;", "return 403;");
        assertThat(khoi.group(1))
                .as("lớp token phải so NGUYÊN chuỗi header Authorization với token đã giải")
                .containsPattern("\\$http_authorization\\s*!=\\s*\"Bearer \\$\\{METRICS_BEARER_TOKEN}\"");
        assertThat(Pattern.compile("location[^{]*actuator")
                        .matcher(tpl)
                        .results()
                        .count())
                .as("chỉ ĐÚNG MỘT location chạm actuator — mở rộng là mở chỉ số (và health) ra Internet")
                .isEqualTo(1);

        String compose = doc("deploy/compose.prod.yml");
        assertThat(compose)
                .as("thiếu/rỗng phải DỪNG: envsubst để nguyên chỗ cắm nếu biến vắng ⇒ gửi đúng chuỗi chỗ cắm là lọt")
                .contains("METRICS_BEARER_TOKEN: ${METRICS_BEARER_TOKEN:?", "METRICS_ALLOW_IP: ${METRICS_ALLOW_IP:?");
    }

    @Test
    @DisplayName("⭐ Tự kiểm: `${` trong chú thích ⛔ tính, trong giá trị thì tính")
    void tuKiem() {
        assertThat(coBienMoiTruong("# bản trước viết ${PROD_APP_TARGET}\nx: 1\n"))
                .isFalse();
        assertThat(coBienMoiTruong("      - targets: ['${PROD_APP_TARGET}']\n")).isTrue();
        assertThat(boChuThich("a: '__X__' # __Y__\n")).contains("__X__").doesNotContain("__Y__");
    }

    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> luatTheoTen() {
        Map<String, Object> goc = new Yaml().load(doc("deploy/observability/alerts.yml"));
        return ((List<Map<String, Object>>) goc.get("groups"))
                .stream()
                        .flatMap(g -> ((List<Map<String, Object>>) g.get("rules")).stream())
                        .collect(Collectors.toMap(r -> (String) r.get("alert"), r -> r));
    }

    @SuppressWarnings("unchecked")
    private static String nhanCho(List<Map<String, Object>> routes, String boChon) {
        return routes.stream()
                .filter(r -> ((List<String>) r.get("matchers")).contains(boChon))
                .map(r -> (String) r.get("receiver"))
                .findFirst()
                .orElseGet(() -> fail("⛔ có tuyến con nào khớp %s", boChon));
    }

    static boolean coBienMoiTruong(String yml) {
        return boChuThich(yml).contains("${");
    }

    /** Bỏ phần sau {@code #} ở đầu dòng hoặc sau khoảng trắng — đủ cho YAML cấu hình ở đây (⛔ `#` trong chuỗi). */
    static String boChuThich(String yml) {
        return yml.lines().map(l -> l.replaceFirst("(^|\\s)#.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String doc(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return fail("Không đọc được %s", ungVien);
                }
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s", duongDanTuongDoi);
    }
}
