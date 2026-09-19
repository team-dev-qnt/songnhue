package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.ratelimit.RateLimitPolicy;

/**
 * <b>Mọi endpoint trả TỆP phải được xếp xô hạn mức có chủ ý — DOD3.6 · T47.11.</b>
 *
 * <h2>Chuyện đã xảy ra — 14/9/2026</h2>
 *
 * {@code RateLimitFilter} nhận diện lượt kết xuất bằng {@code path.contains("/export")} — một chuỗi
 * <b>tiếng Anh</b> trong một kho đặt tên <b>tiếng Việt</b>. Đo lúc đối chiếu DoD Phase 3: <b>6</b>
 * endpoint kết xuất, marker ấy bắt được <b>2</b>. Bốn cái còn lại rơi xuống {@link
 * RateLimitPolicy#API} — <b>100 lượt/phút thay vì 10 lượt/giờ, rộng gấp 600 lần</b>. Và <b>ba trong
 * bốn</b> do chính đợt Phase 3 dựng ra, tức khoảng trống đang <i>rộng ra</i> chứ không đứng yên.
 *
 * <h2>Vì sao một danh sách gõ tay là chưa đủ</h2>
 *
 * Cái bẫy thật ⛔ không phải "quên một endpoint hôm nay" mà là "endpoint kết xuất <b>thứ bảy</b> ra
 * đời tháng sau". Nên bài này có <b>hai</b> vế:
 *
 * <ol>
 *   <li>{@link #moiEndpointTraTepDuocXepDungXo()} — bảng khai xếp đúng như đã khai;
 *   <li>{@link #banKhaiPhaiPhuHETendpointTraTep()} — <b>quét mã nguồn</b> tìm mọi phương thức
 *       controller trả tệp; cái nào ⛔ không có trong bảng thì đỏ. Phạm vi do bộ canh <b>ĐO</b>,
 *       ⛔ không do người viết gõ (CLAUDE.md luật 28).
 * </ol>
 *
 * <h2>⛔ Và ⛔ KHÔNG phải cứ trả tệp là {@code EXPORT}</h2>
 *
 * Ba đường cố ý đứng ngoài, mỗi đường một lý do đo được — xem cột lý do của {@link #BAN_KHAI}.
 * Xếp nhầm {@code /gis-layers/{id}/noi-dung} vào 10 lượt/giờ là làm trắng màn hình trực ban.
 */
class HanMucKetXuatTest {

    /**
     * Đường dẫn đầy đủ → (xô phải nhận, lý do). Lý do là phần bắt buộc: một dòng miễn trừ ⛔ không
     * kèm lý do thì lượt rà sau ⛔ không biết nó còn đúng hay đã mục.
     */
    private static final Map<String, String[]> BAN_KHAI = new LinkedHashMap<>();

    static {
        BAN_KHAI.put("/api/v1/cms/contacts/export", new String[] {"EXPORT", "Kết xuất danh sách liên hệ — quét bảng"});
        BAN_KHAI.put("/api/v1/hr/bao-cao/xuat/BCNS-01", new String[] {"EXPORT", "Báo cáo nhân sự — CN-04.8"});
        BAN_KHAI.put("/api/v1/ops/bao-cao/xuat/BC-06", new String[] {"EXPORT", "Báo cáo vận hành — C3"});
        BAN_KHAI.put(
                "/api/v1/ops/bao-cao-nhanh/9a8b/xuat",
                new String[] {"EXPORT", "Báo cáo nhanh — dựng .docx từ mẫu Công ty (Bảng 2 ~230 dòng)"});
        BAN_KHAI.put("/api/v1/hyd/bao-cao/xuat", new String[] {"EXPORT", "Đặt lệnh kết xuất thuỷ văn — dựng báo cáo"});
        BAN_KHAI.put("/api/v1/hyd/bao-cao/tai/2f1c/", new String[] {"EXPORT", "Tải tệp báo cáo thuỷ văn đã dựng xong"});
        BAN_KHAI.put("/api/v1/hr/employees/7/tai-lieu/zip", new String[] {
            "EXPORT",
            "NẶNG NHẤT: đọc toàn bộ tài liệu một hồ sơ CBNV khỏi MinIO. Ở 100/phút thì vừa là "
                    + "đường tự đánh sập mình, vừa là đường rút dữ liệu cá nhân hàng loạt (NĐ 13/2023)"
        });
        BAN_KHAI.put("/api/v1/ops/gis-layers/3c2a/noi-dung", new String[] {
            "API",
            "⛔ CỐ Ý ⛔ không phải EXPORT — bản đồ VẼ bằng chính lượt gọi này; 10 lượt/giờ là "
                    + "màn hình trực ban trắng"
        });
        BAN_KHAI.put(
                "/api/v1/hyd/stations/mau-nhap",
                new String[] {"API", "Tệp mẫu nhập liệu, vài KB — ⛔ không phải lượt quét bảng"});
        BAN_KHAI.put(
                "/api/v1/ops/may-bom/nhom-may/mau-nhap",
                new String[] {"API", "Tệp mẫu nhập nhóm máy bơm, vài trăm byte — ⛔ phải lượt quét bảng"});
        BAN_KHAI.put(
                "/api/v1/ops/constructions/mau-nhap",
                new String[] {"API", "Tệp mẫu nhập liệu, vài KB — ⛔ không phải lượt quét bảng"});
        BAN_KHAI.put(
                "/api/v1/public/files/9a8b",
                new String[] {"PUBLIC", "Ảnh NHÚNG trong bài trên cổng — mỗi lượt xem trang gọi nhiều lần"});
        BAN_KHAI.put(
                "/api/v1/public/article-documents/9a8b",
                new String[] {"PUBLIC", "Văn bản đính kèm trên cổng công khai"});
        BAN_KHAI.put(
                "/api/v1/public/constructions/documents/9a8b",
                new String[] {"PUBLIC", "Tài liệu công trình trên cổng công khai"});
    }

    @Test
    @DisplayName("⭐⭐ DOD3.6 — mọi endpoint trả tệp rơi đúng xô đã khai")
    void moiEndpointTraTepDuocXepDungXo() {
        List<String> sai = new ArrayList<>();
        BAN_KHAI.forEach((duongDan, khai) -> {
            RateLimitPolicy thuc = RateLimitPolicy.choDuongDan(duongDan);
            if (!thuc.name().equals(khai[0])) {
                sai.add("  · %s%n      khai %s, thực tế %s%n      lý do: %s"
                        .formatted(duongDan, khai[0], thuc, khai[1]));
            }
        });
        assertThat(sai)
                .as(
                        """
                        %d endpoint rơi sai xô hạn mức:

                        %s
                        ⛔ EXPORT tính theo GIỜ (settings, ≤ 100), API là 100 lượt/phút — chênh nhau hàng trăm lần. Một \
                        endpoint kết xuất rơi xuống API ⛔ không có triệu chứng nào cho tới ngày có \
                        người gọi nó hàng nghìn lượt.""",
                        sai.size(), String.join("\n", sai))
                .isEmpty();
    }

    @Test
    @DisplayName("⭐⭐ Bản khai phải phủ HẾT endpoint trả tệp — endpoint mới buộc phải ra quyết định")
    void banKhaiPhaiPhuHETendpointTraTep() {
        Set<String> quetDuoc = quetEndpointTraTep();

        assertThat(quetDuoc)
                .as("Quét ra tập rỗng nghĩa là biểu thức đã mù, ⛔ không phải kho ⛔ không có endpoint nào")
                .hasSizeGreaterThanOrEqualTo(8);

        List<String> thieu = quetDuoc.stream()
                .filter(mau -> BAN_KHAI.keySet().stream().noneMatch(khai -> khopMau(mau, khai)))
                .toList();

        assertThat(thieu)
                .as(
                        """
                        %d endpoint TRẢ TỆP ⛔ không có trong bản khai của bài kiểm này:

                        %s

                        ⇒ Thêm nó vào `BAN_KHAI` kèm xô và LÝ DO. Mặc định im lặng là `API` \
                        (100 lượt/phút) — đúng thứ đã để 4/6 đường kết xuất rộng gấp 600 lần suốt \
                        từ WS-43 tới lúc đối chiếu DoD Phase 3.""",
                        thieu.size(),
                        String.join("\n", thieu.stream().map(t -> "  · " + t).toList()))
                .isEmpty();
    }

    /**
     * Bộ canh phải <b>bắt được</b> vi phạm (CLAUDE.md luật 1). Vế phân biệt quan trọng nhất là cặp
     * <i>tiếng Anh</i> ↔ <i>tiếng Việt</i>: đó đúng là hình dạng của T47.11.
     */
    @Test
    @DisplayName("⭐ Tự kiểm chứng: cùng một lượt kết xuất, đặt tên Anh hay Việt đều rơi vào EXPORT")
    void tuKiemChung() {
        assertThat(RateLimitPolicy.choDuongDan("/api/v1/x/export")).isEqualTo(RateLimitPolicy.EXPORT);
        assertThat(RateLimitPolicy.choDuongDan("/api/v1/x/xuat")).isEqualTo(RateLimitPolicy.EXPORT);

        assertThat(RateLimitPolicy.choDuongDan("/api/v1/x/de-xuat"))
                .as("`/de-xuat` (đề xuất) ⛔ không phải một lượt kết xuất — khớp theo RANH GIỚI ĐOẠN")
                .isEqualTo(RateLimitPolicy.API);

        assertThat(RateLimitPolicy.choDuongDan("/api/v1/auth/login")).isEqualTo(RateLimitPolicy.LOGIN);

        assertThat(RateLimitPolicy.choDuongDan("/api/v1/public/files/1"))
                .as("Ảnh nhúng cổng phải là PUBLIC, ⛔ không bao giờ EXPORT")
                .isEqualTo(RateLimitPolicy.PUBLIC);
        assertThat(RateLimitPolicy.choDuongDan("/api/v1/public/bao-cao/xuat"))
                .as("Công khai xét TRƯỚC kết xuất — ⛔ không được hạ cả cổng xuống 10 lượt/giờ")
                .isEqualTo(RateLimitPolicy.PUBLIC);

        assertThat(RateLimitPolicy.choDuongDan("/api/v1/hr/employees/7"))
                .as("Đường thường vẫn là API — nếu mọi thứ đều EXPORT thì bài trên xanh vì lý do sai")
                .isEqualTo(RateLimitPolicy.API);
    }

    // -------------------------------------------------------------------------

    /** Khai trong bảng là một đường dẫn CỤ THỂ; quét ra là một MẪU có {@code {bien}}. */
    private static boolean khopMau(String mau, String duongDanKhai) {
        String regex = mau.replaceAll("\\{[^}]+}", "[^/]+");
        return duongDanKhai.matches(regex) || duongDanKhai.startsWith(regex.replace("[^/]+", ""));
    }

    private static final Pattern LOP = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]*)\"");
    private static final Pattern ANH_XA =
            Pattern.compile("@(?:Get|Post|Put|Patch|Delete)Mapping\\(\\s*(?:value\\s*=\\s*)?(?:\"([^\"]*)\")?");

    /** Chữ ký trả TỆP. {@code \\s+} vì Spotless ngắt dòng chữ ký dài (§11.13). */
    private static final Pattern TRA_TEP = Pattern.compile(
            "public\\s+(?:ResponseEntity\\s*<\\s*(?:Resource|byte\\[]|InputStreamResource|StreamingResponseBody)"
                    + "\\s*>|byte\\[]|Resource)\\s+\\w+\\s*\\(");

    private static Set<String> quetEndpointTraTep() {
        Set<String> ket = new TreeSet<>();
        Path goc = timTuGocKho("backend");
        try {
            Files.walkFileTree(goc, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    String ten = d.getFileName().toString();
                    return "target".equals(ten) || "test".equals(ten)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    if (tep.getFileName().toString().endsWith("Controller.java")) {
                        ket.addAll(bocTach(doc(tep)));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path tep, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + goc, e);
        }
        return ket;
    }

    /** Hàm thuần — tách ra để thử được với nội dung tự soạn. */
    static Set<String> bocTach(String noiDung) {
        Set<String> ket = new TreeSet<>();
        Matcher lop = LOP.matcher(noiDung);
        String nen = lop.find() ? lop.group(1) : "";

        List<int[]> moc = new ArrayList<>();
        List<String> duong = new ArrayList<>();
        Matcher m = ANH_XA.matcher(noiDung);
        while (m.find()) {
            moc.add(new int[] {m.start(), m.end()});
            duong.add(m.group(1) == null ? "" : m.group(1));
        }
        for (int i = 0; i < moc.size(); i++) {
            int tu = moc.get(i)[1];
            int den = i + 1 < moc.size() ? moc.get(i + 1)[0] : noiDung.length();
            if (TRA_TEP.matcher(noiDung.substring(tu, den)).find()) {
                ket.add(nen + duong.get(i));
            }
        }
        return ket;
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
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
        return fail("Không tìm thấy %s".formatted(duongDanTuongDoi));
    }
}
