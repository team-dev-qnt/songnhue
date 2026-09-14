package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi endpoint quản trị phải có một lời gọi từ {@code admin-app} — ĐÚNG ĐỘNG TỪ.</b> T61.18 vế (b).
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * {@code PUT /ops/maintenance-logs/{id}} có service, có phân quyền, có bài kiểm HTTP xanh — và 0 nơi gọi
 * (T61.18). Bộ canh có sẵn {@code apiKhongMoCoi.test.ts} ⛔ thấy: nó chỉ soi phương thức của MỘT client
 * tập trung ({@code features/cms/api.ts}), còn mọi feature khác gọi {@code api.put(...)} thẳng trong
 * component. Và một phép đo theo ĐƯỜNG DẪN cũng ⛔ thấy: {@code GET /ops/maintenance-logs/${id}} có người
 * gọi nên chuỗi đường dẫn "có mặt". Thứ phân biệt được là <b>động từ</b> (luật 9).
 *
 * <h2>Đi ngược chiều</h2>
 *
 * Liệt kê endpoint từ controller backend, rồi tìm lời gọi {@code api.<động từ>(…)} trong {@code
 * admin-app} có đường dẫn khớp. Đường dẫn phía giao diện hiếm khi là một chuỗi trần — lượt đo nháp đầu
 * cho <b>64</b> "mồ côi" chỉ riêng CMS vì {@code `${BASE}/articles`} (luật 25). Bộ đọc vì thế giải:
 * hằng {@code const} gần nhất phía trước lời gọi (một tệp có thể khai {@code const duong} ba lần trong
 * ba component) · biểu thức ba ngôi · thuộc tính object truyền qua prop ({@code duongDan={{ xemTruoc:
 * '/…' }}}) · lời gọi xuống dòng {@code api\n.upload(}. Lời gọi nào ⛔ giải được thì <b>đỏ</b>, ⛔ bỏ
 * qua — bỏ qua là để một endpoint trông như mồ côi hoặc trông như có người gọi tuỳ may rủi.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <ul>
 *   <li>⛔ {@code /api/v1/public/**}: người gọi là {@code public-web} — bộ canh anh em
 *       {@code public-web/src/lib/apiKhongMoCoi.test.ts} (T47.10).
 *   <li>Chỉ nhận lời gọi qua đối tượng {@code api} của {@code shared/apiClient}. Gọi bằng {@code fetch}
 *       hay {@code axios} trần sẽ hiện ra ở đây như mồ côi — đúng ý: nó cũng lách luôn bộ chặn CSRF và
 *       làm mới token của client.
 *   <li>Khớp đường dẫn theo SỐ ĐOẠN: {@code {x}} phía giao diện khớp mọi đoạn. Hai endpoint khác nhau
 *       chỉ ở tên một đoạn biến sẽ được tính chung.
 * </ul>
 */
class EndpointCoNoiGoiTest {

    /**
     * ⛔ Endpoint ⛔ có lời gọi nào — đo 14/09/2026. Sổ nợ đọc được bằng máy, khớp CHÍNH XÁC hai chiều:
     * thêm endpoint quên màn hình ⇒ đỏ; dựng màn hình quên xoá dòng ⇒ cũng đỏ.
     */
    private static final Map<String, String> CHUA_CO_NOI_GOI = new LinkedHashMap<>();

    static {
        CHUA_CO_NOI_GOI.put(
                "GET /ops/maintenance-logs/{publicId}/attachments",
                "CN-02.2 'Tài liệu, ảnh kèm — biên bản nghiệm thu, ảnh trước/sau': backend lưu được tệp cho bản ghi sửa chữa, màn hình ⛔ có tab nào xem");
        CHUA_CO_NOI_GOI.put(
                "POST /ops/maintenance-logs/{publicId}/attachments",
                "Cùng CN-02.2: ⛔ có nút tải biên bản nghiệm thu / ảnh trước-sau lên một bản ghi sửa chữa");
        CHUA_CO_NOI_GOI.put(
                "GET /ops/maintenance-logs/{publicId}/attachments/{attachmentId}/download-url",
                "Cùng CN-02.2: ⛔ tải về được tệp của bản ghi sửa chữa — kể cả khi ai đó đã tải lên bằng đường khác");
        CHUA_CO_NOI_GOI.put(
                "DELETE /ops/maintenance-logs/{publicId}/attachments/{attachmentId}",
                "Cùng CN-02.2: tệp tải nhầm lên một bản ghi sửa chữa ⛔ gỡ được từ giao diện");
        CHUA_CO_NOI_GOI.put(
                "DELETE /ops/maintenance-logs/{publicId}",
                "CN-02.2 quy tắc 'sửa/xoá bản ghi đã lưu → audit, soft delete': bản ghi nhập nhầm ⛔ xoá được — và nó vẫn đẩy trạng thái công trình");
        CHUA_CO_NOI_GOI.put(
                "GET /hr/canh-bao-het-han",
                "M4.9 cảnh báo HĐLĐ/chứng chỉ sắp hết hạn: backend tính danh sách theo ngưỡng settings, ⛔ màn hình nào hiện nó ra");
        CHUA_CO_NOI_GOI.put(
                "DELETE /admin/users/{publicId}",
                "Xoá mềm tài khoản ⛔ có nút — màn hình chỉ khoá/mở; tài khoản tạo nhầm nằm mãi trong danh sách");
        CHUA_CO_NOI_GOI.put(
                "DELETE /hyd/stations/{publicId}",
                "Xoá điểm đo ⛔ có nút — điểm đo khai nhầm chỉ tắt được cờ 'Đang dùng', vẫn hiện trong mọi ô chọn");
        CHUA_CO_NOI_GOI.put(
                "DELETE /hyd/api-sources/{publicId}",
                "Xoá nguồn dữ liệu (chặn khi còn điểm đo trỏ vào, HYD-1002) ⛔ có nút — nguồn khai nhầm nằm mãi trong danh mục");
        CHUA_CO_NOI_GOI.put(
                "GET /hyd/api-sources/{publicId}",
                "Chi tiết một nguồn — màn hình dựng hộp thoại sửa từ hàng của danh sách, ⛔ đọc lại; nghi là endpoint thừa, chưa ai quyết gỡ");
        CHUA_CO_NOI_GOI.put(
                "GET /admin/users/{publicId}",
                "Chi tiết một tài khoản — màn hình dựng hộp thoại từ hàng của danh sách, ⛔ đọc lại; nghi là endpoint thừa, chưa ai quyết gỡ");
        CHUA_CO_NOI_GOI.put(
                "GET /org-units",
                "Danh sách phẳng đơn vị 'cho ô chọn' — ô chọn thật dùng /org-units/selectable (cây có phạm vi); nghi là endpoint thừa");
        CHUA_CO_NOI_GOI.put(
                "GET /org-units/{publicId}/subtree",
                "Cây con của một đơn vị — ⛔ màn hình nào dùng (sơ đồ tổ chức đọc /hr/so-do-to-chuc); nghi là endpoint thừa");
        CHUA_CO_NOI_GOI.put(
                "GET /cms/site-config/effective",
                "Giá trị cấu hình đang hiệu lực 'dạng cổng công khai sẽ dùng' — cổng đọc qua /public/site-config; nghi là endpoint thừa");
        CHUA_CO_NOI_GOI.put(
                "DELETE /attachments/{publicId}",
                "Xoá tệp đính kèm qua đường CHUNG — mọi màn hình nay xoá qua đường riêng của chủ sở hữu (T28.47); nghi là endpoint thừa");
    }

    /**
     * Endpoint gọi NGOÀI đối tượng {@code api} — có lý do, và bài {@link #goiNgoaiApiConThat()} đòi chuỗi
     * đường dẫn còn nằm trong đúng tệp khai, để dòng này ⛔ mục thành một miễn trừ chết.
     */
    private static final Map<String, String> GOI_NGOAI_API =
            Map.of("POST /auth/refresh", "frontend/admin-app/src/shared/apiClient.ts");

    private static final Map<String, String> DONG_TU = Map.of(
            "get", "GET", "getPage", "GET", "getTep", "GET", "post", "POST", "upload", "POST", "put", "PUT", "patch",
            "PATCH", "delete", "DELETE");

    @Test
    @DisplayName("⭐⭐ Tập endpoint ⛔ có lời gọi khớp CHÍNH XÁC sổ nợ — cả hai chiều")
    void moCoiKhopSoNo() {
        KetQua kq = doThat();
        Set<String> daKhai = new TreeSet<>(CHUA_CO_NOI_GOI.keySet());
        assertThat(kq.moCoi)
                .as(
                        """
                        Tập "endpoint ⛔ có lời gọi" lệch sổ nợ CHUA_CO_NOI_GOI.

                          Đo được: %s
                          Sổ khai: %s

                        Thừa ở "đo được" = endpoint vừa dựng mà ⛔ màn hình nào gọi — ĐÚNG ĐỘNG TỪ. Backend \
                        có, phân quyền có, bài kiểm HTTP xanh, người dùng ⛔ có nút (T61.18, luật 27).
                        Thừa ở "sổ khai" = đã dựng màn hình mà quên xoá dòng nợ — danh sách phải TEO ĐI.""",
                        kq.moCoi, daKhai)
                .isEqualTo(daKhai);
    }

    @Test
    @DisplayName("⛔⛔ Mọi lời gọi `api.*(…)` đều giải được đường dẫn — ⛔ bỏ qua lời gọi mù")
    void moiLoiGoiGiaiDuoc() {
        assertThat(doThat().khongGiai)
                .as("Lời gọi ⛔ giải được thì endpoint của nó trông như mồ côi hoặc như có người gọi tuỳ may rủi. "
                        + "Viết đường dẫn thành chuỗi / hằng `const` / thuộc tính object mang chuỗi.")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Chống tập rỗng — bộ đọc thấy đủ endpoint và lời gọi")
    void chongTapRong() {
        KetQua kq = doThat();
        assertThat(kq.soEndpoint).as("controller đổi cách khai mapping?").isGreaterThanOrEqualTo(250);
        assertThat(kq.soLoiGoi).as("giao diện đổi cách gọi api?").isGreaterThanOrEqualTo(200);
        assertThat(kq.moCoi.size()).isLessThan(kq.soEndpoint / 5);
    }

    @Test
    @DisplayName("⛔ Endpoint miễn vì gọi ngoài `api` vẫn thật sự được gọi ở tệp đã khai")
    void goiNgoaiApiConThat() {
        GOI_NGOAI_API.forEach((endpoint, tep) -> assertThat(boChuThich(doc(gocKho().resolve(tep))))
                .as("%s khai là gọi ở %s — chuỗi đường dẫn ⛔ còn ở đó (ngoài chú thích)", endpoint, tep)
                .contains(endpoint.substring(endpoint.indexOf(' ') + 1)));
    }

    @Test
    @DisplayName("⛔ Mỗi dòng nợ phải nói ra người dùng MẤT gì")
    void lyDoDuDai() {
        CHUA_CO_NOI_GOI.forEach(
                (k, v) -> assertThat(v.length()).as("lý do của %s quá ngắn", k).isGreaterThanOrEqualTo(60));
    }

    @Test
    @DisplayName("⭐⭐ Tự kiểm: GET có người gọi ⛔ che PUT mồ côi — ca T61.18")
    void tuKiemDongTu() {
        List<Endpoint> be = List.of(new Endpoint("GET", "/ops/x/{id}"), new Endpoint("PUT", "/ops/x/{id}"));
        Set<String> goi = new HashSet<>();
        List<String> mu = new ArrayList<>();
        docLoiGoi("const a = 1;\nconst q = api.get<X>(`/ops/x/${id}`);\n", goi, mu, Map.of());
        assertThat(moCoi(be, goi)).containsExactly("PUT /ops/x/{id}");
    }

    @Test
    @DisplayName("⭐⭐ Tự kiểm: bộ giải đường dẫn — hằng gần nhất · ba ngôi · prop object · xuống dòng · chú thích")
    void tuKiemBoGiai() {
        String ts =
                """
                const duong = `/hr/a/${id}/ly-lich`;
                api.delete(`${duong}/${mucId}`);
                function B() {
                  const duong = `/hr/a/${id}/timeline`;
                  return api.get(loc ? `${duong}?loai=${loc}` : duong);
                }
                api.post(laSuCo ? '/ops/m/incidents' : '/ops/m', body);
                api
                  .upload(`${GOC}/${l.id}/tep`, fd);
                api.post(duongDan.xemTruoc, form);
                // api.put('/khong/phai/loi/goi');
                const u = 'https://x'; api.patch('/sau/url');
                """;
        Set<String> goi = new HashSet<>();
        List<String> mu = new ArrayList<>();
        docLoiGoi(boChuThich(ts), goi, mu, Map.of("xemTruoc", Set.of("/ops/c/import/preview")));
        assertThat(mu).isEmpty();
        assertThat(goi)
                .contains(
                        "DELETE /hr/a/{x}/ly-lich/{x}",
                        "GET /hr/a/{x}/timeline",
                        "POST /ops/m/incidents",
                        "POST /ops/m",
                        "POST /ops/c/import/preview",
                        "PATCH /sau/url");
        assertThat(goi).as("chú thích ⛔ phải lời gọi").noneMatch(g -> g.startsWith("PUT"));
        assertThat(goi).as("GOC ⛔ khai ⇒ đoạn biến").contains("POST {x}/{x}/tep");
    }

    // =========================================================================

    record Endpoint(String dongTu, String duong) {}

    record KetQua(int soEndpoint, int soLoiGoi, Set<String> moCoi, List<String> khongGiai) {}

    private static KetQua ketQua;

    private static synchronized KetQua doThat() {
        if (ketQua == null) {
            Path goc = gocKho();
            List<Endpoint> be = docEndpoint(goc.resolve("backend"));
            Map<Path, String> tep = tepGiaoDien(goc.resolve("frontend/admin-app/src"));
            Map<String, Set<String>> thuocTinh = new HashMap<>();
            Pattern tt = Pattern.compile("\\b([a-zA-Z_]\\w*)\\s*:\\s*'(/[^'\\n]*)'");
            tep.values().forEach(s -> tt.matcher(s).results().forEach(m -> thuocTinh
                    .computeIfAbsent(m.group(1), k -> new HashSet<>())
                    .add(m.group(2))));
            Set<String> goi = new HashSet<>();
            List<String> mu = new ArrayList<>();
            tep.forEach((duong, s) -> {
                List<String> muTep = new ArrayList<>();
                docLoiGoi(s, goi, muTep, thuocTinh);
                muTep.forEach(x -> mu.add(goc.relativize(duong) + ": " + x));
            });
            ketQua = new KetQua(be.size(), goi.size(), moCoi(be, goi), mu);
        }
        return ketQua;
    }

    static Set<String> moCoi(List<Endpoint> be, Set<String> goi) {
        return be.stream()
                .filter(e -> !e.duong().startsWith("/public"))
                .filter(e -> !GOI_NGOAI_API.containsKey(e.dongTu() + " " + e.duong()))
                .filter(e -> goi.stream().noneMatch(g -> khop(e, g)))
                .map(e -> e.dongTu() + " " + e.duong())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean khop(Endpoint e, String loiGoi) {
        int cach = loiGoi.indexOf(' ');
        if (!loiGoi.substring(0, cach).equals(e.dongTu())) {
            return false;
        }
        String[] a = doan(e.duong());
        String[] b = doan(loiGoi.substring(cach + 1));
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].startsWith("{") && !b[i].contains("{x}") && !a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] doan(String duong) {
        return Stream.of(duong.split("/")).filter(x -> !x.isEmpty()).toArray(String[]::new);
    }

    // ---- backend -------------------------------------------------------------

    private static final Pattern MAP_LOP =
            Pattern.compile("@RequestMapping\\(\\s*(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\"");
    private static final Pattern MAP_PT =
            Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping(?:\\(((?:[^()]|\\([^()]*\\))*)\\))?");
    private static final Pattern DUONG_PT = Pattern.compile("^(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\"");

    static List<Endpoint> docEndpoint(Path backend) {
        List<Endpoint> ket = new ArrayList<>();
        try (Stream<Path> s = Files.walk(backend)) {
            s.filter(p -> p.toString().endsWith("Controller.java"))
                    .filter(p -> p.toString().contains("/src/main/"))
                    .forEach(p -> ket.addAll(docController(doc(p))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ket;
    }

    static List<Endpoint> docController(String java) {
        int lop = java.indexOf("\nclass ") >= 0 ? java.indexOf("\nclass ") : java.indexOf(" class ");
        Matcher ml = MAP_LOP.matcher(java);
        String nen = ml.find() && ml.start() < lop ? ml.group(1) : "";
        List<Endpoint> ket = new ArrayList<>();
        Matcher m = MAP_PT.matcher(java);
        while (m.find()) {
            if (m.start() < lop) {
                continue;
            }
            Matcher d = DUONG_PT.matcher(m.group(2) == null ? "" : m.group(2).strip());
            String duong =
                    (nen + (d.find() ? d.group(1) : "")).replaceAll("/$", "").replace("/api/v1", "");
            ket.add(new Endpoint(m.group(1).toUpperCase(), duong));
        }
        return ket;
    }

    // ---- giao diện -----------------------------------------------------------

    private static final Pattern LOI_GOI = Pattern.compile(
            "\\bapi\\s*\\.\\s*(get|getPage|getTep|post|upload|put|patch|delete)\\s*(?:<(?:[^<>]|<[^<>]*>)*>)?\\(");
    private static final Pattern CHUOI = Pattern.compile("'([^'\\n]*)'|\"([^\"\\n]*)\"|`([^`]*)`");
    private static final Pattern DINH_NGHIA = Pattern.compile("\\b(?:const|let)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;\\n]+)");
    private static final Pattern TEN = Pattern.compile("(?<![.\\w])([A-Za-z_]\\w*)(\\.([A-Za-z_]\\w*))?(?![\\w(])");

    record DinhNghia(int viTri, String ten, String bieuThuc) {}

    static void docLoiGoi(String s, Set<String> goi, List<String> mu, Map<String, Set<String>> thuocTinh) {
        List<DinhNghia> dinh = DINH_NGHIA
                .matcher(s)
                .results()
                .map(m -> new DinhNghia(m.start(), m.group(1), m.group(2)))
                .toList();
        Matcher m = LOI_GOI.matcher(s);
        while (m.find()) {
            String doiSo = doiSoDau(s, m.end());
            List<String> uv = ungVien(doiSo, m.start(), 0, dinh, thuocTinh).stream()
                    .filter(u -> u.startsWith("/") || u.startsWith("{x}"))
                    .toList();
            if (uv.isEmpty()) {
                mu.add("api." + m.group(1) + "(" + doiSo.strip() + ")");
            }
            for (String u : uv) {
                String duong = u.split("\\?", 2)[0].replace("/api/v1", "").replaceAll("/$", "");
                goi.add(DONG_TU.get(m.group(1)) + " " + duong);
            }
        }
    }

    private static List<String> ungVien(
            String bt, int viTri, int sau, List<DinhNghia> dinh, Map<String, Set<String>> tt) {
        List<String> ra = new ArrayList<>();
        Matcher c = CHUOI.matcher(bt);
        while (c.find()) {
            String t = c.group(1) != null ? c.group(1) : c.group(2) != null ? c.group(2) : c.group(3);
            if (t.startsWith("/") || t.startsWith("${")) {
                ra.addAll(giaiChuoi(t, viTri, sau, dinh, tt));
            }
        }
        Matcher n = TEN.matcher(CHUOI.matcher(bt).replaceAll(" "));
        while (n.find()) {
            if (n.group(3) != null) {
                ra.addAll(tt.getOrDefault(n.group(3), Set.of()));
            } else {
                ra.addAll(giaiTen(n.group(1), viTri, sau, dinh, tt));
            }
        }
        return ra;
    }

    private static List<String> giaiTen(
            String ten, int viTri, int sau, List<DinhNghia> dinh, Map<String, Set<String>> tt) {
        if (sau >= 4) {
            return List.of();
        }
        DinhNghia gan = null;
        for (DinhNghia d : dinh) {
            if (d.ten().equals(ten) && d.viTri() < viTri) {
                gan = d;
            }
        }
        return gan == null ? List.of() : ungVien(gan.bieuThuc(), gan.viTri(), sau + 1, dinh, tt);
    }

    private static List<String> giaiChuoi(
            String t, int viTri, int sau, List<DinhNghia> dinh, Map<String, Set<String>> tt) {
        List<String> ra = List.of("");
        for (String phan : t.split("(?=\\$\\{)|(?<=\\})")) {
            List<String> them;
            if (phan.startsWith("${") && phan.endsWith("}")) {
                String k = phan.substring(2, phan.length() - 1).strip();
                List<String> v = k.matches("[A-Za-z_]\\w*") ? giaiTen(k, viTri, sau, dinh, tt) : List.of();
                them = v.isEmpty() ? List.of("{x}") : v;
            } else {
                them = List.of(phan);
            }
            List<String> moi = new ArrayList<>();
            for (String a : ra) {
                for (String b : them) {
                    moi.add(a + b);
                }
            }
            ra = moi;
        }
        return ra;
    }

    /** Đối số đầu tiên: quét tới dấu phẩy / đóng ngoặc CÙNG cấp, bỏ qua nội dung chuỗi. */
    static String doiSoDau(String s, int tu) {
        int sau = 0;
        for (int j = tu; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\'' || c == '"' || c == '`') {
                int k = s.indexOf(c, j + 1);
                j = k < 0 ? s.length() : k;
            } else if (c == '(' || c == '[' || c == '{') {
                sau++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (sau == 0) {
                    return s.substring(tu, j);
                }
                sau--;
            } else if (c == ',' && sau == 0) {
                return s.substring(tu, j);
            }
        }
        return s.substring(tu);
    }

    /** Bỏ chú thích, GIỮ chuỗi — {@code 'https://x'} ⛔ phải chú thích (bản anh em ở TS, T49.6). */
    static String boChuThich(String s) {
        StringBuilder ra = new StringBuilder(s.length());
        char tt = 0; // 0 mã · 'k' khối · 'd' dòng · ' " ` chuỗi
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char ke = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (tt == 0) {
                if (c == '/' && ke == '*') {
                    tt = 'k';
                    i++;
                } else if (c == '/' && ke == '/') {
                    tt = 'd';
                    i++;
                } else {
                    if (c == '\'' || c == '"' || c == '`') {
                        tt = c;
                    }
                    ra.append(c);
                }
            } else if (tt == 'k') {
                if (c == '*' && ke == '/') {
                    tt = 0;
                    i++;
                } else {
                    ra.append(c == '\n' ? '\n' : ' ');
                }
            } else if (tt == 'd') {
                if (c == '\n') {
                    tt = 0;
                    ra.append('\n');
                }
            } else {
                if (c == '\\') {
                    ra.append("  ");
                    i++;
                } else {
                    if (c == tt) {
                        tt = 0;
                    }
                    ra.append(c);
                }
            }
        }
        return ra.toString();
    }

    private static Map<Path, String> tepGiaoDien(Path src) {
        try (Stream<Path> s = Files.walk(src)) {
            return s.filter(p -> p.toString().matches(".*\\.tsx?$"))
                    .filter(p -> !p.toString().contains(".test."))
                    .collect(Collectors.toMap(p -> p, p -> boChuThich(doc(p))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path gocKho() {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.isDirectory(hienTai.resolve("frontend/admin-app/src"))) {
                return hienTai;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy gốc kho");
    }
}
