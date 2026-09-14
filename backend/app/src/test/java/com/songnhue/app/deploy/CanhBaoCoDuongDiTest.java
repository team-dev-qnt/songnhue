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
 * <p>Năm mắt xích, mỗi mắt một bài: cấu hình ⛔ mang {@code ${} · mọi chỗ cắm được điền và thiếu là dừng
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
        luat.forEach((ten, r) -> assertThat(((Map<String, String>) r.get("labels")).get("severity"))
                .as("%s: severity phải là critical|warning — Alertmanager định tuyến bằng nhãn này", ten)
                .isIn("critical", "warning"));
        for (String ten : List.of("SaoLuuQuaHan", "NguonDuLieuImLang", "ChiSoProductionVangMat")) {
            assertThat(luat).containsKey(ten);
            assertThat((String) luat.get(ten).get("expr"))
                    .as("%s thiếu `absent(…environment=\"production\"…)` ⇒ im VĨNH VIỄN khi ⛔ có chỉ số", ten)
                    .containsPattern("absent\\([^)]*environment=\"production\"");
        }
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
        assertThat(khoi.group(1))
                .contains("allow ${METRICS_ALLOW_IP};", "deny all;", "if ($metrics_token_ok = 0)", "return 403;");
        assertThat(tpl).contains("\"Bearer ${METRICS_BEARER_TOKEN}\" 1;");
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
