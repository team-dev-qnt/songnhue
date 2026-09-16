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
    private static final Map<String, String> DA_CO_BAI_KIEM = new LinkedHashMap<>(Map.of(
            "ConstructionController.SaveRequest",
                    "frontend/admin-app/src/features/operations/hoSoCongTrinhVongKhuHoi.test.tsx",
            "ArticleController.SaveRequest", "frontend/admin-app/src/features/cms/soanBaiVongKhuHoi.test.tsx",
            "MaintenanceLogController.SaveRequest",
                    "frontend/admin-app/src/features/operations/suaBanGhiSuaChuaVongKhuHoi.test.tsx",
            "MenuController.MenuRequest", "frontend/admin-app/src/features/cms/menuVongKhuHoi.test.tsx",
            "StationController.StationRequest", "frontend/admin-app/src/features/hydro/diemDoVongKhuHoi.test.tsx",
            "EmployeeSensitiveController.SensitiveRequest",
                    "frontend/admin-app/src/features/hr/truongBaoMatVongKhuHoi.test.tsx"));

    /**
     * Endpoint CHƯA có bài kiểm — <b>mỗi dòng một lý do ĐO ĐƯỢC, tối thiểu 40 ký tự</b>.
     *
     * <p>⚠ Ràng buộc độ dài ⛔ phải hình thức: nó chặn kiểu miễn trừ <i>"chưa làm"</i> — một câu đúng
     * với MỌI khoảng trống và vì thế ⛔ phân biệt được <i>có thứ tự ưu tiên</i> với <i>bị bỏ quên</i>.
     * Cùng ràng buộc đã bắt một dòng miễn trừ 33 ký tự của chính người viết ở {@code MaLoiCoNoiNemTest}.
     */
    private static final Map<String, String> CHUA_CO_BAI_KIEM = new TreeMap<>(Map.ofEntries(
            Map.entry(
                    "EmployeeController.EmployeeRequest",
                    "24 trường hồ sơ CBNV. NẶNG: đánh rơi một trường là sửa hồ sơ nhân sự sai mà ⛔ ai "
                            + "thấy. Cần mock `HoSoNhanSuPage` + ngăn kéo con; xếp ngay sau nhóm 🔒."),
            Map.entry(
                    "LyLichController.LyLichRequest",
                    "9 trường lý lịch & chuyên môn (CN-04.3). Cùng ngăn kéo với hồ sơ CBNV nên dựng "
                            + "chung một lượt với `EmployeeController` sẽ rẻ hơn dựng rời."),
            Map.entry(
                    "ApiSourceController.ApiSourceRequest",
                    "8 trường. Bốn tham số nhịp (`cron`/`frameMinutes`/`timeoutSeconds`/`maxRetry`) có "
                            + "giá trị `null` MANG NGHĨA *dùng tham số chung* ⇒ đánh rơi là nguồn lặng lẽ đổi hành vi."),
            Map.entry(
                    "BannerController.BannerRequest",
                    "7 trường. FE gom `startAt`/`endAt` thành MỘT ô `RangePicker` rồi tách lại lúc "
                            + "`onOk` ⇒ phép ánh xạ ⛔ 1-1, đúng chỗ một trường rơi được mà ⛔ ai thấy."),
            Map.entry(
                    "MeasurementTypeController.MeasurementTypeRequest",
                    "7 trường. `valueScale == null ⇒ 3` và `sortOrder == null ⇒ 0` là hai mặc định LẶNG "
                            + "— đánh rơi ⛔ sinh lỗi nào, chỉ đổi số chữ số hiển thị và thứ tự danh mục."),
            Map.entry(
                    "AlertRuleController.AlertRuleUpdateRequest",
                    "6 trường, có vế AN TOÀN: `active == null ⇒ true` ⇒ đánh rơi `active` là BẬT LẠI một "
                            + "ngưỡng cảnh báo vừa cố ý tắt; `delayMinutes == null ⇒ 0` ⇒ bắn ngay mỗi lượt vượt."),
            Map.entry(
                    "AlertLevelController.AlertLevelRequest",
                    "6 trường danh mục mức ngưỡng. Cùng màn hình họ hàng với AlertRule nên gộp một lượt; "
                            + "ảnh hưởng màu và thứ tự hiển thị cảnh báo trên dashboard trực ban."),
            Map.entry(
                    "OrgUnitController.UpdateRequest",
                    "6 trường. `address`/`phone`/`email` là BA CỘT DUY NHẤT nuôi bảng *Xí nghiệp trực "
                            + "thuộc* của CR-26 trên cổng công khai — mất là bảng công khai trống lần nữa."),
            Map.entry(
                    "OrgUnitLeaderController.LeaderRequest",
                    "5 trường lãnh đạo đơn vị. Hiển thị trên cổng công khai; `org_unit_leaders` từng có "
                            + "ĐƯỜNG ĐỌC mà ⛔ đường ghi suốt một đợt (§10.62) nên nhóm này có tiền sử."),
            Map.entry(
                    "TimelineController.SuKienRequest",
                    "6 trường mốc sự kiện nhân sự (CN-04.4). Dữ liệu điều động/bổ nhiệm là căn cứ của "
                            + "báo cáo BCNS — sai một mốc là sai cả biểu đồ biến động 12 tháng."),
            Map.entry(
                    "PositionController.PositionRequest",
                    "6 trường danh mục chức vụ. Danh mục do khách vận hành (quy tắc 16) nên nó bị sửa "
                            + "thường xuyên hơn hẳn các bảng khác — tần suất sửa là tần suất rơi."),
            Map.entry(
                    "GisLayerController.LayerRequest",
                    "6 trường lớp bản đồ GIS. Đánh rơi cấu hình lớp là bản đồ điều hành hiển thị sai "
                            + "nền hoặc mất lớp, mà lớp bản đồ là thứ TRANG TRÍ nên ⛔ ai đi đối chiếu."),
            Map.entry(
                    "ConstructionClusterController.ClusterRequest",
                    "5 trường cụm công trình. Cụm là khoá gom của báo cáo MOD-02; đổi sai một trường "
                            + "làm số liệu tổng hợp lệch mà từng dòng chi tiết vẫn đúng."),
            Map.entry(
                    "ContactCategoryController.CategoryForm",
                    "4 trường phân loại liên hệ. Vừa có một khuyết tật THẬT ở đúng biểu mẫu này (T61.47: "
                            + "`@NotBlank` trên trường chỉ-đọc-lúc-tạo làm MỌI lượt sửa trả 400)."),
            Map.entry(
                    "OperationStatusCodeController.OperationStatusCodeUpdateRequest",
                    "6 trường, và là DTO dạng `class` chứ ⛔ `record` — bộ đọc phải nhận cả hai dạng. "
                            + "Mã tình hình vận hành quyết định TRẠNG THÁI DẪN XUẤT của công trình (quy tắc 4).")));

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
        List<String> hoiHot = CHUA_CO_BAI_KIEM.entrySet().stream()
                .filter(e -> e.getValue().length() < 40)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        assertThat(hoiHot).as("Lý do ngắn hơn 40 ký tự: %s", hoiHot).isEmpty();
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
