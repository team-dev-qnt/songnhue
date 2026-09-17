package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>Một gói NỘI BỘ của workspace ⛔ không bao giờ được giải về kho npm công khai.</b>
 *
 * <h2>Khuyết tật đã xảy ra thật — T63.1</h2>
 *
 * {@code frontend/design-tokens} là gói nội bộ của dự án ({@code private: true}, phiên bản
 * {@code 0.1.0}), và {@code admin-app} lẫn {@code public-web} đều khai nó làm phụ thuộc. Trên npm
 * công khai <b>có một gói CÙNG TÊN của người khác</b> — {@code design-tokens}, {@code latest =
 * 1.0.1}, tác giả {@code alex-e-leon}. Dependabot đã mở đúng PR nâng {@code 0.1.0 → 1.0.1} (#145).
 *
 * <p>Nếu PR ấy được gộp thì dải phiên bản {@code 1.0.1} <b>⛔ không còn khớp</b> gói nội bộ
 * {@code 0.1.0} ⇒ npm thôi nối vào workspace và quay sang <b>tải gói của người lạ</b> ⇒ mã bên thứ
 * ba thay chỗ bộ token của dự án trong CẢ HAI ứng dụng, và ⛔ không một dòng nào báo: cài đặt thành
 * công, build thành công, chỉ màu sắc và khoảng cách là của một dự án khác.
 *
 * <h2>Vì sao một dòng `ignore` ⛔ không đủ</h2>
 *
 * {@code .github/dependabot.yml} nay có {@code ignore: design-tokens}, nhưng đó là một <b>lời
 * dặn</b> gửi tới một công cụ — nó ⛔ không chặn một người gõ tay, ⛔ không chặn một lượt
 * {@code npm install design-tokens@latest}, và ⛔ không chặn lượt đổi tên gói nội bộ ngày mai.
 * CLAUDE.md đã trả giá nhiều lượt cho đúng câu này: <i>một lời dặn ⛔ không phải một cổng kiểm</i>
 * (§11.19). Bộ canh này đứng ở chỗ <b>hậu quả hiện ra</b> — bản lockfile đã giải — nên nó bắt được
 * mọi con đường dẫn tới cùng trạng thái ấy, ⛔ không chỉ con đường Dependabot.
 *
 * <p>⚠ Nó ⛔ không thay được cách vá tận gốc là <b>đổi gói nội bộ sang tên có phạm vi</b>
 * ({@code @songnhue/design-tokens}) — một phạm vi ta sở hữu thì ⛔ không ai chiếm tên được, và lỗi
 * sẽ là một lượt cài <b>đỏ</b> thay vì một lượt cài im lặng sai. Lượt đổi tên ấy đụng <b>73 tệp</b>
 * nên nó là một quyết định của QuanTran, ghi ở dòng nợ T63.2.
 */
class PhuThuocNoiBoTest {

    @Test
    @DisplayName(
            "⭐⭐ Mọi gói nội bộ của workspace phải giải về chính workspace (`link: true`), ⛔ không về kho công khai")
    void goiNoiBoPhaiNoiVaoWorkspace() {
        Set<String> noiBo = tenGoiNoiBo();
        String lock = doc(timTuGocKho("frontend/package-lock.json"));

        Set<String> lech = goiKhongNoiVaoWorkspace(lock, noiBo);

        assertThat(lech)
                .as(
                        """
                        Những gói sau là gói NỘI BỘ của `frontend/` (workspace), nhưng \
                        `frontend/package-lock.json` ⛔ KHÔNG giải chúng về workspace:

                        %s

                        Nghĩa là npm đang (hoặc sẽ) tải một gói CÙNG TÊN từ kho công khai về thay chỗ \
                        mã của dự án. Với `design-tokens` thì gói công khai ấy CÓ THẬT (latest 1.0.1, \
                        của một tác giả ngoài dự án), nên đây ⛔ không phải rủi ro lý thuyết.

                        Cách sửa: giữ dải phiên bản trong `package.json` KHỚP phiên bản gói nội bộ \
                        (`frontend/<gói>/package.json`), rồi chạy lại `npm install` ở `frontend/` để \
                        lockfile ghi `"link": true`.""",
                        String.join("\n", lech))
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi mục `npm` trong dependabot.yml phải trỏ vào thư mục CÓ lockfile")
    void mucNpmPhaiTroVaoThuMucCoLockfile() {
        List<String> thuMuc = thuMucNpmCuaDependabot();

        Set<String> thieu = new TreeSet<>();
        for (String tm : thuMuc) {
            String sach = tm.startsWith("/") ? tm.substring(1) : tm;
            Path lock = Paths.get(gocKho().toString(), sach, "package-lock.json");
            if (!Files.exists(lock)) {
                thieu.add("%s  (⛔ không có %s/package-lock.json)".formatted(tm, sach));
            }
        }

        assertThat(thieu)
                .as(
                        """
                        `.github/dependabot.yml` khai những thư mục npm sau, mà ở đó ⛔ KHÔNG có \
                        lockfile nào:

                        %s

                        Dependabot sẽ sửa `package.json` rồi ⛔ không có lockfile để cập nhật ⇒ mọi PR \
                        nó mở đều đỏ ngay bước `npm ci` (*"package.json and package-lock.json are not \
                        in sync"*). Đã xảy ra thật: 7/7 PR frontend của lượt chạy đầu tiên (#141→#147).

                        `frontend/` là npm workspaces ⇒ khai ĐÚNG MỘT mục `directory: /frontend`, \
                        ⛔ không khai từng ứng dụng con.""",
                        String.join("\n", thieu))
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi gói nội bộ phải nằm trong `ignore` của mục npm — Dependabot ⛔ không được đề nghị nâng nó")
    void goiNoiBoPhaiNamTrongIgnore() {
        Set<String> noiBo = goiNoiBoDuocPhuThuoc();
        String yml = doc(timTuGocKho(".github/dependabot.yml"));

        Set<String> thieu = new TreeSet<>();
        for (String ten : noiBo) {
            if (!yml.contains("dependency-name: " + ten)) {
                thieu.add(ten);
            }
        }

        assertThat(thieu)
                .as(
                        """
                        Gói nội bộ ⛔ chưa có trong `ignore` của `.github/dependabot.yml`: %s

                        Dependabot tra tên gói ở kho CÔNG KHAI, ⛔ không biết gói ấy là của chính kho \
                        này. Nếu có ai đã chiếm tên ấy trên npm thì nó sẽ mở PR nâng lên phiên bản \
                        của người lạ — và PR ấy trông y hệt một lượt nâng phụ thuộc bình thường.""",
                        thieu)
                .isEmpty();
    }

    @Test
    @DisplayName("Phải tìm ra ít nhất 1 gói nội bộ và 1 mục npm — chặn xanh-trên-tập-rỗng")
    void quetRaTapKhacRong() {
        // conventions.md §1.5 + CLAUDE.md luật 7. Đổi cách khai workspaces (glob `packages/*`) hay đổi
        // cách viết `directory:` làm hai bộ đọc trên trả tập RỖNG, và cả ba bài trên xanh mà ⛔ không
        // so gì. Đây là vế phân biệt *"⛔ không có vi phạm"* với *"⛔ không nhìn thấy gì"*.
        assertThat(tenGoiNoiBo()).as("gói nội bộ của workspace").isNotEmpty();
        assertThat(goiNoiBoDuocPhuThuoc())
                .as("gói nội bộ BỊ một gói khác phụ thuộc")
                .isNotEmpty();
        assertThat(thuMucNpmCuaDependabot()).as("mục npm trong dependabot.yml").isNotEmpty();
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: một lockfile giải gói nội bộ về kho công khai PHẢI bị bắt")
    void tuKiemChung() {
        // CLAUDE.md luật 1 + luật 29. Bài trên xanh vì lockfile HÔM NAY đúng; nó ⛔ chứng minh phép
        // phân tích bắt được ca hỏng. Ở đây dựng đúng hình dạng npm ghi ra khi gói bị lấy từ registry
        // — có `integrity`, ⛔ không có `link` — rồi đòi phép phân tích gọi tên nó.
        String lockHong =
                """
                {
                  "name": "songnhue-frontend",
                  "lockfileVersion": 3,
                  "packages": {
                    "node_modules/design-tokens": {
                      "version": "1.0.1",
                      "resolved": "https://registry.npmjs.org/design-tokens/-/design-tokens-1.0.1.tgz",
                      "integrity": "sha512-giaDinhKhongPhaiThat"
                    }
                  }
                }
                """;
        assertThat(goiKhongNoiVaoWorkspace(lockHong, Set.of("design-tokens")))
                .as("Lockfile lấy gói nội bộ từ registry công khai mà phép phân tích ⛔ không bắt")
                .containsExactly("design-tokens");

        // Vế đối chứng: đúng hình dạng workspace thì phải SẠCH — nếu ⛔ thì bài trên đỏ với mọi
        // lockfile, tức nó ⛔ phân biệt được hai trạng thái (luật 9).
        String lockDung =
                """
                {
                  "packages": {
                    "node_modules/design-tokens": { "resolved": "design-tokens", "link": true }
                  }
                }
                """;
        assertThat(goiKhongNoiVaoWorkspace(lockDung, Set.of("design-tokens")))
                .as("Lockfile nối đúng vào workspace mà vẫn bị báo vi phạm")
                .isEmpty();

        // Vế thứ ba: gói VẮNG HẲN khỏi lockfile cũng là một ca hỏng — ⛔ phải một ca "sạch".
        assertThat(goiKhongNoiVaoWorkspace("{\"packages\":{}}", Set.of("design-tokens")))
                .as("Gói nội bộ vắng mặt trong lockfile phải bị bắt, ⛔ không được đọc thành sạch")
                .containsExactly("design-tokens");
    }

    // ---------------------------------------------------------------- phép phân tích

    /**
     * Trả về tên những gói nội bộ mà {@code lockJson} <b>⛔ không</b> giải về workspace.
     *
     * <p>Tách riêng khỏi phép đọc tệp để {@link #tuKiemChung()} nạp được một lockfile hỏng dựng tay —
     * đó là vế duy nhất chứng minh bộ canh này bắt được vi phạm.
     */
    private static Set<String> goiKhongNoiVaoWorkspace(String lockJson, Set<String> tenGoiNoiBo) {
        JsonNode goi = new ObjectMapper().readTree(lockJson).path("packages");

        Set<String> lech = new TreeSet<>();
        for (String ten : tenGoiNoiBo) {
            JsonNode muc = goi.path("node_modules/" + ten);
            // `link: true` là cách npm ghi *"đây là symlink tới một workspace trong chính kho này"*.
            // Thiếu nó — dù vì vắng mặt hay vì có `integrity` của registry — đều ⛔ không phải trạng
            // thái ta muốn, nên cả hai rơi vào cùng một nhánh.
            if (!muc.path("link").asBoolean(false)) {
                lech.add(ten);
            }
        }
        return lech;
    }

    /**
     * Gói nội bộ mà <b>một gói khác trong workspace khai làm phụ thuộc</b> — tập hẹp hơn
     * {@link #tenGoiNoiBo()}.
     *
     * <p>⚠ Vế phân biệt này do chính bộ canh bắt ở lượt chạy đầu (luật 28): bản đầu của tôi đòi
     * <i>mọi</i> gói workspace phải nằm trong {@code ignore}, nên nó báo thiếu {@code admin-app} và
     * {@code public-web}. Hai cái ấy là <b>ứng dụng</b>, ⛔ không ai phụ thuộc vào chúng ⇒ Dependabot
     * ⛔ bao giờ đề nghị nâng, và thêm chúng vào {@code ignore} chỉ là tiếng ồn che mất dòng thật sự
     * có ý nghĩa. Thứ cần chặn là gói <b>bị phụ thuộc</b>, vì chỉ nó mới có một dải phiên bản để
     * Dependabot đi nâng.
     */
    private static Set<String> goiNoiBoDuocPhuThuoc() {
        ObjectMapper om = new ObjectMapper();
        Set<String> tatCa = tenGoiNoiBo();
        JsonNode ws = om.readTree(doc(timTuGocKho("frontend/package.json"))).path("workspaces");

        Set<String> duocPhuThuoc = new TreeSet<>();
        for (JsonNode muc : ws) {
            Path pkg = Paths.get(gocKho().toString(), "frontend", muc.asString(), "package.json");
            if (!Files.exists(pkg)) {
                continue;
            }
            JsonNode goc = om.readTree(doc(pkg));
            for (String khoi : List.of("dependencies", "devDependencies", "peerDependencies")) {
                for (String ten : goc.path(khoi).propertyNames()) {
                    if (tatCa.contains(ten)) {
                        duocPhuThuoc.add(ten);
                    }
                }
            }
        }
        return duocPhuThuoc;
    }

    /** Tên mọi gói khai trong {@code frontend/package.json} → {@code workspaces}. */
    private static Set<String> tenGoiNoiBo() {
        ObjectMapper om = new ObjectMapper();
        JsonNode ws = om.readTree(doc(timTuGocKho("frontend/package.json"))).path("workspaces");

        Set<String> ten = new LinkedHashSet<>();
        for (JsonNode muc : ws) {
            Path pkg = Paths.get(gocKho().toString(), "frontend", muc.asString(), "package.json");
            if (Files.exists(pkg)) {
                String t = om.readTree(doc(pkg)).path("name").asString("");
                if (!t.isBlank()) {
                    ten.add(t);
                }
            }
        }
        return ten;
    }

    /**
     * Giá trị {@code directory:} của mọi mục {@code package-ecosystem: npm}.
     *
     * <p>Quét theo dòng có trạng thái thay vì một biểu thức chính quy trên cả tệp: mỗi mục bắt đầu ở
     * {@code - package-ecosystem:}, nên chỉ cần nhớ hệ sinh thái đang mở là gán đúng được
     * {@code directory:} cho nó. ⚠ Nó ⛔ không phải một bộ đọc YAML đầy đủ — YAML nhiều dòng hay
     * {@code directories:} dạng danh sách sẽ ⛔ không thấy; khi đổi sang dạng ấy thì
     * {@link #quetRaTapKhacRong()} là vế báo động.
     */
    private static List<String> thuMucNpmCuaDependabot() {
        List<String> thuMuc = new ArrayList<>();
        String heSinhThaiDangMo = "";

        for (String dong : doc(timTuGocKho(".github/dependabot.yml")).split("\n")) {
            String t = dong.strip();
            if (t.startsWith("- package-ecosystem:")) {
                heSinhThaiDangMo = t.substring(t.indexOf(':') + 1).strip();
            } else if (t.startsWith("directory:") && "npm".equals(heSinhThaiDangMo)) {
                thuMuc.add(t.substring(t.indexOf(':') + 1).strip());
            }
        }
        return thuMuc;
    }

    // ---------------------------------------------------------------- đọc tệp

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fail("Không đọc được %s".formatted(p), e);
        }
    }

    private static Path gocKho() {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.exists(hienTai.resolve(".github"))) {
                return hienTai;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy gốc kho tính từ %s".formatted(System.getProperty("user.dir")));
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path ungVien = gocKho().resolve(duongDanTuongDoi);
        if (!Files.exists(ungVien)) {
            return fail("Không tìm thấy %s".formatted(duongDanTuongDoi));
        }
        return ungVien;
    }
}
