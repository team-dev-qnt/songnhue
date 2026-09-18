package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi endpoint thay-toàn-phần phải được XẾP LOẠI: đã có bài kiểm giữ-nguyên, hay chưa</b> —
 * T63.10, biến nợ {@code T47.17} từ một dấu ba chấm thành một danh sách đếm được.
 *
 * <h2>⛔⛔ Vì sao dòng nợ cũ ⛔ dùng được</h2>
 *
 * {@code T47.17} khai *"18 endpoint"* rồi *"còn 14"*, nhưng nó liệt **7 cái có tên** và bỏ lửng bằng
 * dấu <b>…</b>. Bảy cái ẩn sau dấu ấy <b>⛔ có tên ở bất kỳ đâu trong kho</b> — tức một nửa nợ ⛔ truy
 * được, ⛔ giao được cho ai, và ⛔ biết bao giờ hết. Đo lại 17/09: mẫu số ⛔ phải 17 mà là <b>21</b>,
 * và số còn thiếu ⛔ phải 5 mà là <b>16</b>.
 *
 * <p>⇒ Lớp này <b>ĐO</b> vế trái (danh sách endpoint) từ chính mã nguồn, nên một endpoint
 * thay-toàn-phần mới ra đời là một lượt CI đỏ bắt người viết chọn: dựng bài kiểm, hoặc khai nợ kèm
 * lý do. ⛔ Còn chỗ cho dấu ba chấm nữa.
 *
 * <h2>Khuyết tật mà nhóm bài kiểm ấy canh</h2>
 *
 * Mỗi endpoint dưới đây nhận một DTO nhiều trường rồi <b>ghi đè hết</b>. Một ô biểu mẫu quên nối là
 * một lượt Lưu <b>xoá trường ấy</b> — ⛔ một dòng lỗi, và chỉ có triệu chứng kể từ ngày dữ liệu tồn
 * tại (§11.19: {@code PUT} điểm đo xoá trắng tuyến sông/lý trình, vô hình suốt từ WS-29 vì mọi ô vốn
 * đã NULL).
 *
 * <p>⚠ Và bài kiểm <b>phải là bài render biểu mẫu</b>: phép kiểm *"PUT nguyên văn thân GET"* ở tầng
 * HTTP <b>xanh ở CẢ HAI trạng thái</b>, vì tầng ấy round-trip hoàn hảo — thứ đánh rơi trường là biểu
 * mẫu.
 *
 * <h2>Ngưỡng 4 trường</h2>
 *
 * DTO 1–3 trường (đổi vai trò, đổi một giá trị, đổi thứ tự) ⛔ có chỗ cho một trường lặng lẽ biến
 * mất — người dùng thấy ngay. Ngưỡng ở đây là <b>⛔ 4 trường trở lên</b>, đo trên số thành phần của
 * record (hoặc số trường của class DTO).
 */
class VongKhuHoiDuPhamViTest {

    /** Endpoint ĐÃ có bài kiểm giữ-nguyên → tệp bài kiểm ấy (phải tồn tại trên đĩa). */
    private static final Map<String, String> DA_CO_BAI_KIEM = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(
                    "ConstructionController.SaveRequest",
                    "frontend/admin-app/src/features/operations/hoSoCongTrinhVongKhuHoi.test.tsx"),
            Map.entry(
                    "ArticleController.SaveRequest", "frontend/admin-app/src/features/cms/soanBaiVongKhuHoi.test.tsx"),
            Map.entry(
                    "MaintenanceLogController.SaveRequest",
                    "frontend/admin-app/src/features/operations/suaBanGhiSuaChuaVongKhuHoi.test.tsx"),
            Map.entry("MenuController.MenuRequest", "frontend/admin-app/src/features/cms/menuVongKhuHoi.test.tsx"),
            Map.entry(
                    "StationController.StationRequest",
                    "frontend/admin-app/src/features/hydro/diemDoVongKhuHoi.test.tsx"),
            Map.entry(
                    "EmployeeSensitiveController.SensitiveRequest",
                    "frontend/admin-app/src/features/hr/truongBaoMatVongKhuHoi.test.tsx"),
            Map.entry(
                    "AlertRuleController.AlertRuleUpdateRequest",
                    "frontend/admin-app/src/features/hydro/nguongCanhBaoVongKhuHoi.test.tsx"),
            Map.entry(
                    "BannerController.BannerRequest", "frontend/admin-app/src/features/cms/bannerVongKhuHoi.test.tsx"),
            Map.entry(
                    "OrgUnitController.UpdateRequest",
                    "frontend/admin-app/src/features/admin/donViVongKhuHoi.test.tsx"),
            Map.entry(
                    "ApiSourceController.ApiSourceRequest",
                    "frontend/admin-app/src/features/hydro/nguonDuLieuVongKhuHoi.test.tsx"),
            Map.entry(
                    "MeasurementTypeController.MeasurementTypeRequest",
                    "frontend/admin-app/src/features/hydro/loaiChiSoVongKhuHoi.test.tsx"),
            Map.entry(
                    "EmployeeController.EmployeeRequest",
                    "frontend/admin-app/src/features/hr/hoSoCanBoVongKhuHoi.test.tsx"),
            Map.entry("LyLichController.LyLichRequest", "frontend/admin-app/src/features/hr/lyLichVongKhuHoi.test.tsx"),
            Map.entry(
                    "AlertLevelController.AlertLevelRequest",
                    "frontend/admin-app/src/features/hydro/mucCanhBaoVongKhuHoi.test.tsx"),
            Map.entry(
                    "GisLayerController.LayerRequest",
                    "frontend/admin-app/src/features/operations/lopBanDoVongKhuHoi.test.tsx"),
            Map.entry(
                    "OperationStatusCodeController.OperationStatusCodeUpdateRequest",
                    "frontend/admin-app/src/features/operations/maTinhHinhVanHanhVongKhuHoi.test.tsx"),
            Map.entry(
                    "ConstructionClusterController.ClusterRequest",
                    "frontend/admin-app/src/features/operations/cumCongTrinhVongKhuHoi.test.tsx"),
            Map.entry(
                    "TimelineController.SuKienRequest", "frontend/admin-app/src/features/hr/suKienVongKhuHoi.test.tsx"),
            Map.entry(
                    "PositionController.PositionRequest",
                    "frontend/admin-app/src/features/hr/chucVuVongKhuHoi.test.tsx"),
            Map.entry(
                    "OrgUnitLeaderController.LeaderRequest",
                    "frontend/admin-app/src/features/admin/danhBaLanhDaoVongKhuHoi.test.tsx"),
            Map.entry(
                    "ContactCategoryController.CategoryForm",
                    "frontend/admin-app/src/features/cms/danhMucVongKhuHoi.test.tsx")));

    /**
     * Endpoint CHƯA có bài kiểm — <b>mỗi dòng một lý do ĐO ĐƯỢC, tối thiểu 40 ký tự</b>.
     *
     * <p>⚠ Ràng buộc độ dài ⛔ phải hình thức: nó chặn kiểu miễn trừ <i>"chưa làm"</i> — một câu đúng
     * với MỌI khoảng trống và vì thế ⛔ phân biệt được <i>có thứ tự ưu tiên</i> với <i>bị bỏ quên</i>.
     * Cùng ràng buộc đã bắt một dòng miễn trừ 33 ký tự của chính người viết ở {@code MaLoiCoNoiNemTest}.
     */
    /** Độ dài tối thiểu của một lý do khai nợ — xem javadoc {@link #lyDoMienPhaiDoDuoc()}. */
    private static final int DAI_TOI_THIEU = 40;

    private static final Map<String, String> CHUA_CO_BAI_KIEM = new TreeMap<>(Map.ofEntries());

    /** Ngưỡng: DTO từ ngần này trường trở lên mới đủ chỗ cho một trường lặng lẽ biến mất. */
    private static final int TRUONG_TOI_THIEU = 4;

    private static final Pattern DONG_TU = Pattern.compile("@(?:Put|Patch)Mapping");

    private static final Pattern CHU_KY =
            Pattern.compile("\\bpublic\\s+[\\w<>,\\[\\]\\s.]+?\\s+(\\w+)\\s*\\(([^;{]*)\\)");

    private static final Pattern THAN_YEU_CAU =
            Pattern.compile("@RequestBody\\s+(?:@Valid\\s+)?([\\w.]+)|@Valid\\s+@RequestBody\\s+([\\w.]+)");

    @Test
    @DisplayName("⛔⛔ Mọi endpoint thay-toàn-phần phải được XẾP LOẠI — ⛔ còn chỗ cho dấu ba chấm")
    void moiEndpointDeuDuocXepLoai() {
        Set<String> coThat = endpointThayToanPhan().keySet();
        Set<String> daXepLoai = new TreeSet<>(DA_CO_BAI_KIEM.keySet());
        daXepLoai.addAll(CHUA_CO_BAI_KIEM.keySet());

        List<String> chuaXep =
                coThat.stream().filter(e -> !daXepLoai.contains(e)).sorted().toList();
        assertThat(chuaXep)
                .as(
                        """
                        Endpoint thay-toàn-phần sau ⛔ nằm trong `DA_CO_BAI_KIEM` lẫn `CHUA_CO_BAI_KIEM`: %s

                        Nó nhận một DTO ≥ %d trường rồi GHI ĐÈ HẾT, nên một ô biểu mẫu quên nối là một \
                        lượt Lưu **xoá trường ấy** — ⛔ một dòng lỗi, và chỉ có triệu chứng kể từ ngày \
                        dữ liệu tồn tại (§11.19).

                        Hãy CHỌN: dựng bài kiểm giữ-nguyên (khuôn `menuVongKhuHoi.test.tsx`) rồi khai ở \
                        `DA_CO_BAI_KIEM`, hoặc khai nợ ở `CHUA_CO_BAI_KIEM` kèm lý do ĐO ĐƯỢC ≥ 40 ký \
                        tự. "Chưa làm" ⛔ phải một lý do.""",
                        chuaXep, TRUONG_TOI_THIEU)
                .isEmpty();

        List<String> khongConTonTai =
                daXepLoai.stream().filter(e -> !coThat.contains(e)).sorted().toList();
        assertThat(khongConTonTai)
                .as("Endpoint khai trong danh sách mà ⛔ còn tồn tại (đổi tên? gỡ rồi?): %s", khongConTonTai)
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi bài kiểm khai ở `DA_CO_BAI_KIEM` phải CÓ THẬT trên đĩa")
    void baiKiemDaKhaiPhaiTonTai() {
        // ⛔ Một dòng trỏ vào tệp ⛔ tồn tại là một endpoint ĐƯỢC MIỄN KIỂM trong im lặng — đúng thứ
        //   nguy hiểm hơn cả việc ⛔ khai gì, vì nó ĐỌC NHƯ một lời bảo đảm.
        List<String> thieu = DA_CO_BAI_KIEM.entrySet().stream()
                .filter(e -> !Files.exists(gocKho().resolve(e.getValue())))
                .map(e -> e.getKey() + " → " + e.getValue())
                .sorted()
                .toList();
        assertThat(thieu)
                .as("Bài kiểm khai trong danh sách mà ⛔ có tệp: %s", thieu)
                .isEmpty();
    }

    @Test
    @DisplayName("Lý do miễn phải ĐO ĐƯỢC — *\"chưa làm\"* đúng với mọi khoảng trống nên nó ⛔ phải lý do")
    void lyDoMienPhaiDoDuoc() {
        assertThat(lyDoHoiHot(CHUA_CO_BAI_KIEM))
                .as("Lý do ngắn hơn %d ký tự: %s", DAI_TOI_THIEU, lyDoHoiHot(CHUA_CO_BAI_KIEM))
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ tự-kiểm: ràng buộc ≥ 40 ký tự vẫn BẮT được, kể cả khi danh sách nợ đã RỖNG")
    void rangBuocLyDoVanConHieuLuc() {
        // ⛔⛔ Từ 17/09 `CHUA_CO_BAI_KIEM` **rỗng** — 21/21 endpoint đã có bài kiểm. Một khẳng định
        //    chạy trên tập rỗng thì XANH mà ⛔ nói gì (luật 7), nên nó ⛔ còn chứng minh được rằng
        //    ràng buộc còn sống. Endpoint thay-toàn-phần TIẾP THEO ra đời sẽ lại cần đúng ràng buộc
        //    ấy, và người viết nó phải gặp một bộ canh ĐANG hoạt động chứ ⛔ phải một dòng mã đã mục.
        //    ⇒ Vế này chạy phép kiểm trên dữ liệu GIẢ, ⛔ phụ thuộc danh sách thật.
        assertThat(lyDoHoiHot(Map.of("X.Y", "chưa làm")))
                .as("⛔ Phép kiểm ⛔ bắt được một lý do 8 ký tự ⇒ nó đã chết")
                .containsExactly("X.Y");

        assertThat(lyDoHoiHot(Map.of("X.Y", "Một lý do đo được, dài hơn bốn mươi ký tự để đi lọt.")))
                .as("⛔ Phép kiểm báo vi phạm cho một lý do ĐẠT ⇒ nó ⛔ phân biệt được hai trạng thái")
                .isEmpty();
    }

    private static List<String> lyDoHoiHot(Map<String, String> danhSach) {
        return danhSach.entrySet().stream()
                .filter(e -> e.getValue().length() < DAI_TOI_THIEU)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("⚠ Chống tập rỗng + đối chứng PHẢI-TÌM-THẤY: phép đo còn sống")
    void phepDoConSong() {
        Map<String, Integer> ds = endpointThayToanPhan();

        // ⛔⛔ Ba lượt đo đầu của chính dòng nợ này đều SAI, và mỗi lượt sai một kiểu:
        //    (1) `[^>]*?` cắt ở dấu `>` bên trong generic ⇒ 34 thay vì 46;
        //    (2) tra `record SaveRequest` trên TOÀN KHO ⇒ bốn endpoint cùng ăn một con số 25;
        //    (3) tra theo THƯ MỤC ⇒ `ArticleDtos.SaveRequest` khớp nhầm `SaveRequest` 3 trường của
        //        `CategoryController.java` nằm cùng chỗ, nên Article biến mất khỏi danh sách.
        //    ⇒ Vế dưới là thứ phân biệt *"⛔ có vi phạm"* với *"phép đo đã chết"* (luật 7 + luật 10).
        assertThat(ds)
                .as("⚠ ⛔ đo ra endpoint nào ⇒ mọi khẳng định trên xanh trên tập RỖNG")
                .hasSizeGreaterThanOrEqualTo(15);

        assertThat(ds)
                .as("Ba endpoint này chắc chắn thay-toàn-phần — ⛔ tìm ra nghĩa là PHÉP ĐO hỏng, ⛔ phải mã hỏng")
                .containsKeys(
                        "ConstructionController.SaveRequest",
                        "ArticleController.SaveRequest",
                        "EmployeeSensitiveController.SensitiveRequest");

        // Và một DTO HẸP phải ⛔ lọt vào: `SettingController.UpdateRequest` có 2 trường.
        assertThat(ds)
                .as(
                        "DTO 2 trường lọt vào tập ⇒ ngưỡng %d ⛔ có hiệu lực, và danh sách sẽ phình bằng nhiễu",
                        TRUONG_TOI_THIEU)
                .doesNotContainKey("SettingController.UpdateRequest");
    }

    // ---------------------------------------------------------------- phép đo

    /** Khoá {@code <TênController>.<TênDTO>} → số trường của DTO. */
    private static Map<String, Integer> endpointThayToanPhan() {
        Map<String, String> nguon = maNguonSanXuat();
        Map<String, Integer> ket = new TreeMap<>();

        for (Map.Entry<String, String> tep : nguon.entrySet()) {
            String ma = tep.getValue();
            Matcher dt = DONG_TU.matcher(ma);
            while (dt.find()) {
                String sau = ma.substring(dt.start(), Math.min(ma.length(), dt.start() + 900));
                Matcher ck = CHU_KY.matcher(sau);
                if (!ck.find()) {
                    continue;
                }
                Matcher tyc = THAN_YEU_CAU.matcher(ck.group(2));
                if (!tyc.find()) {
                    continue;
                }
                String kieu = tyc.group(1) != null ? tyc.group(1) : tyc.group(2);
                int soTruong = soTruongCua(nguon, tep.getKey(), kieu);
                if (soTruong >= TRUONG_TOI_THIEU) {
                    String controller =
                            Paths.get(tep.getKey()).getFileName().toString().replace(".java", "");
                    ket.put(controller + "." + kieu.substring(kieu.lastIndexOf('.') + 1), soTruong);
                }
            }
        }
        return ket;
    }

    /**
     * Số trường của một DTO — nhận CẢ {@code record} lẫn {@code class}.
     *
     * <p>⛔⛔ Tra theo <b>tên đủ điều kiện</b>: {@code ArticleDtos.SaveRequest} phải tìm trong
     * {@code ArticleDtos.java}, ⛔ phải trong bất kỳ tệp nào cùng thư mục tình cờ có một
     * {@code record SaveRequest}. Lượt đo thứ ba của tôi mắc đúng lỗi ấy và <b>Article biến mất khỏi
     * danh sách</b> vì nó ăn nhầm con số 3 trường của {@code CategoryController}.
     */
    private static int soTruongCua(Map<String, String> nguon, String tepGoi, String kieu) {
        int cham = kieu.lastIndexOf('.');
        String ten = kieu.substring(cham + 1);
        List<String> ungVien = new ArrayList<>();
        if (cham > 0) {
            ungVien.add(Paths.get(tepGoi)
                    .getParent()
                    .resolve(kieu.substring(0, cham) + ".java")
                    .toString());
        } else {
            ungVien.add(tepGoi);
        }
        // Dự phòng: DTO đứng riêng một tệp (`OperationStatusCodeUpdateRequest.java`).
        nguon.keySet().stream().filter(k -> k.endsWith("/" + ten + ".java")).forEach(ungVien::add);

        for (String uv : ungVien) {
            String ma = nguon.get(uv);
            if (ma == null) {
                continue;
            }
            Matcher r = Pattern.compile("record\\s+" + Pattern.quote(ten) + "\\s*\\(([\\s\\S]*?)\\)\\s*\\{")
                    .matcher(ma);
            if (r.find()) {
                return demThanhPhan(r.group(1));
            }
            Matcher c = Pattern.compile("class\\s+" + Pattern.quote(ten) + "\\b([\\s\\S]*)")
                    .matcher(ma);
            if (c.find()) {
                Matcher truong = Pattern.compile("(?m)^\\s+private\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*;")
                        .matcher(c.group(1));
                int n = 0;
                while (truong.find()) {
                    n++;
                }
                return n;
            }
        }
        return 0;
    }

    private static int demThanhPhan(String than) {
        String sach = than.replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("@[\\w.]+(\\([^()]*(\\([^()]*\\))?[^()]*\\))?", " ")
                .replaceAll("<[^<>]*>", " ");
        int n = 0;
        for (String phan : sach.split(",")) {
            String[] t = phan.trim().split("\\s+");
            if (t.length > 0 && t[t.length - 1].matches("^[a-z]\\w*$")) {
                n++;
            }
        }
        return n;
    }

    private static Map<String, String> maNguonSanXuat() {
        Map<String, String> ket = new LinkedHashMap<>();
        for (String module : List.of("core", "content", "operations", "hydro", "hr", "app")) {
            Path goc = gocKho().resolve("backend/" + module + "/src/main/java");
            if (!Files.isDirectory(goc)) {
                continue;
            }
            try (Stream<Path> cay = Files.walk(goc)) {
                cay.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        ket.put(p.toString(), Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ket;
    }

    private static Path gocKho() {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.isDirectory(hienTai.resolve("backend")) && Files.isDirectory(hienTai.resolve("frontend"))) {
                return hienTai;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ Không tìm thấy gốc kho từ " + System.getProperty("user.dir"));
    }
}
