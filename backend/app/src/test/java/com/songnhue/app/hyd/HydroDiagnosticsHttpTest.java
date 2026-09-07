package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;

/**
 * ⭐⭐ Hai màn hình chẩn đoán đi qua HTTP thật — T31.13.
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * <p>Từ 01/09 tới 02/09, {@code sync_logs} và {@code hydro_unmapped_readings} có <b>đường ghi hoàn
 * chỉnh và không đường đọc nào</b>. Bài này là lượt đi trọn vòng đầu tiên của nửa còn lại: quyền →
 * bộ lọc → phân trang → tuần tự hoá ra JSON. Luật 5: <i>bài kiểm gọi thẳng service không đi cùng
 * đường với production</i>.
 *
 * <h2>⚠ Không lớp nào ở đây được giả định CSDL rỗng</h2>
 *
 * <p>Bộ kiểm thử dùng chung một CSDL, và {@code TelemetryProbeHttpTest} <b>có</b> ghi
 * {@code sync_logs} thật. Nên mọi khẳng định về danh sách đều lọc theo nguồn của riêng lớp này, còn
 * khẳng định về dải tóm tắt (vốn toàn cục) bắt vào <b>quan hệ</b> giữa các con số chứ không vào giá
 * trị tuyệt đối. Một bài kiểm chỉ đúng khi nó chạy một mình là một bài kiểm sẽ đỏ vào ngày xấu
 * nhất.
 *
 * <h2>⚠ Vai trò: TECHNICIAN, ⛔ không phải một vai trò tạm</h2>
 *
 * <p>Chính {@code TECHNICIAN} là khẳng định cần chứng minh: nó là vai trò duy nhất ngoài quản trị
 * có {@code hyd:station:manage} — tức đúng người sẽ đi khai một mã lạ — và nó <b>không</b> có
 * {@code hyd:api-source:manage}. Dựng một vai trò tạm mang sẵn quyền cần thiết là kiểm cái mình vừa
 * cấp cho mình, không kiểm ma trận thật (T27.20 · §10.70).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydroDiagnosticsHttpTest extends IntegrationTestBase {

    private static final String MA_NGUON = "T13-CD";

    /** ⚠ Mã dùng riêng cho lớp này, dạng {@code F9xxxx} để không đụng dải seed thật (19 mã). */
    private static final String MA_CHUA_KHAI = "F97001";

    private static final String SQL_CHEN_LUOT =
            """
            INSERT INTO sync_logs (
                api_source_id, started_at, finished_at, duration_ms, frame_start,
                status, failure_kind, failure_detail,
                received_count, written_count, skipped_count, unmapped_count, raw_log_id)
            SELECT s.id, now() - ?::interval, now() - ?::interval,
                   ?, date_trunc('hour', now()), ?, ?, ?, ?, ?, ?, ?, ?
              FROM api_sources s WHERE s.code = ?
            """;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;
    private PhienHttp.Phien khongQuyen;

    /** Mã API của một điểm đo đã seed — dùng cho nhánh "đã khai" của màn hình mã lạ. */
    private String maDaKhai;

    private String maDiemDoDaKhai;

    @BeforeAll
    void dungDuLieu() {
        donSach();

        jdbc.update(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES (?, 'Nguồn kiểm thử chẩn đoán', 'MOCK', 'http://chan-doan.invalid/', 'HOAT_DONG', now())
                """,
                MA_NGUON);

        // 8 lượt: đủ BỐN kết cục và đủ NĂM lý do hỏng, mỗi lượt một mốc riêng để thứ tự xác định.
        chen(80, SyncStatus.SUCCESS, null, 28, 19, 0, 9, 501L);
        chen(70, SyncStatus.PARTIAL, null, 8, 8, 0, 0, 502L);
        chen(60, SyncStatus.SKIPPED_UP_TO_DATE, null, 0, 0, 0, 0, null);
        chen(50, SyncStatus.FAILED, SyncFailureKind.THIEU_MA_SO, 0, 0, 0, 0, null);
        chen(40, SyncStatus.FAILED, SyncFailureKind.NOT_WORKING, 0, 0, 0, 0, 503L);
        chen(30, SyncStatus.FAILED, SyncFailureKind.TIMEOUT, 0, 0, 0, 0, null);
        chen(20, SyncStatus.FAILED, SyncFailureKind.HTTP_ERROR, 0, 0, 0, 0, 504L);
        chen(10, SyncStatus.FAILED, SyncFailureKind.EMPTY_BODY, 0, 0, 0, 0, 505L);

        maDaKhai = jdbc.queryForObject(
                "SELECT api_code FROM stations WHERE deleted_at IS NULL ORDER BY api_code LIMIT 1", String.class);
        maDiemDoDaKhai = jdbc.queryForObject(
                "SELECT code FROM stations WHERE api_code = ? AND deleted_at IS NULL", String.class, maDaKhai);
        assertThat(maDaKhai)
                .as("⚠ Vế chống tập rỗng: không có điểm đo seed nào thì nhánh 'đã khai' của màn hình mã "
                        + "lạ chạy qua tập rỗng và xanh mà chẳng chứng minh gì (luật 7)")
                .isNotNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stations WHERE api_code = ? AND deleted_at IS NULL",
                        Integer.class,
                        MA_CHUA_KHAI))
                .as("⛔ Và vế đối: mã 'chưa khai' phải THẬT SỰ chưa khai, nếu không hai nhánh của cùng "
                        + "một khẳng định trở thành một")
                .isZero();

        // Ba bản ghi cho mã CHƯA khai — mốc tăng dần để kiểm "giá trị GẦN NHẤT" chứ không phải bất kỳ.
        chenMaLa(MA_CHUA_KHAI, 30, "150.000");
        chenMaLa(MA_CHUA_KHAI, 20, "160.000");
        chenMaLa(MA_CHUA_KHAI, 10, "213.000");
        // Một bản ghi cho mã ĐÃ khai — lịch sử tích trước lúc Công ty khai báo, vẫn nằm lại.
        chenMaLa(maDaKhai, 40, "493.000");

        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "cd_kythuat", "TECHNICIAN"));
        khongQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "cd_trongtron"));
    }

    private void chen(
            int phutTruoc,
            SyncStatus trangThai,
            SyncFailureKind loi,
            int nhan,
            int ghi,
            int boQua,
            int maLa,
            Long rawLogId) {
        String lui = phutTruoc + " minutes";
        jdbc.update(
                SQL_CHEN_LUOT,
                lui,
                lui,
                1234,
                trangThai.name(),
                loi == null ? null : loi.name(),
                loi == null ? null : "chi tiết kiểm thử " + loi,
                nhan,
                ghi,
                boQua,
                maLa,
                rawLogId,
                MA_NGUON);
    }

    private void chenMaLa(String apiCode, int phutTruoc, String giaTri) {
        jdbc.update(
                """
                INSERT INTO hydro_unmapped_readings (api_code, api_source_id, measured_at, raw_value, raw_unit)
                SELECT ?, s.id, now() - ?::interval, ?::numeric, 'cm'
                  FROM api_sources s WHERE s.code = ?
                """,
                apiCode,
                phutTruoc + " minutes",
                giaTri,
                MA_NGUON);
    }

    private void donSach() {
        jdbc.update(
                "DELETE FROM sync_logs WHERE api_source_id IN (SELECT id FROM api_sources WHERE code = ?)", MA_NGUON);
        jdbc.update(
                "DELETE FROM hydro_unmapped_readings WHERE api_source_id IN (SELECT id FROM api_sources WHERE code = ?)",
                MA_NGUON);
        jdbc.update("DELETE FROM api_sources WHERE code = ?", MA_NGUON);
    }

    /**
     * ⚠ Khẳng định NGAY TẠI CHỖ DỌN.
     *
     * <p>Bài học 02/09: một lớp kiểm thử để sót 10 điểm đo trong CSDL dùng chung làm
     * {@code HydroCatalogueSeedTest} đỏ với triệu chứng <i>"phải seed đúng 19 điểm đo, đang có 29"</i>
     * — một triệu chứng chẳng chỉ về phía nguyên nhân nào cả.
     */
    @AfterAll
    void donDuLieu() {
        donSach();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_sources WHERE code = ?", Integer.class, MA_NGUON))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_unmapped_readings WHERE api_code = ?", Integer.class, MA_CHUA_KHAI))
                .as("⛔ Sót một dòng ở đây là màn hình mã lạ của lớp kiểm thử KHÁC hiện thêm một mã ma")
                .isZero();
    }

    private JsonNode doc(ResponseEntity<String> phanHoi) {
        try {
            return json.readTree(phanHoi.getBody());
        } catch (Exception e) {
            throw new AssertionError("Không đọc được thân JSON: " + phanHoi.getBody(), e);
        }
    }

    private ResponseEntity<String> lay(PhienHttp.Phien phien, String duong) {
        return phienHttp.goi(phien, HttpMethod.GET, duong, null);
    }

    /** Danh sách của riêng nguồn kiểm thử — ⛔ không bao giờ đọc toàn bảng. */
    private JsonNode nhatKyCuaToi(String themThamSo) {
        ResponseEntity<String> kq = lay(kyThuat, "/api/v1/hyd/sync-logs?size=100&nguonId=" + nguonId() + themThamSo);
        assertThat(kq.getStatusCode()).isEqualTo(HttpStatus.OK);
        return doc(kq);
    }

    private String nguonId() {
        return jdbc.queryForObject("SELECT public_id FROM api_sources WHERE code = ?", String.class, MA_NGUON);
    }

    private static List<String> chuoiCua(JsonNode mang, String truong) {
        List<String> ra = new ArrayList<>();
        mang.forEach(n -> ra.add(n.hasNonNull(truong) ? n.get(truong).asText() : null));
        return ra;
    }

    @Test
    @DisplayName("⭐⭐ Đủ NĂM lý do hỏng phân biệt được trên dây — đây là toàn bộ lý do cột ấy tồn tại")
    void namLyDoHongDeuPhanBietDuoc() {
        JsonNode than = nhatKyCuaToi("").get("data");

        assertThat(than).hasSize(8);
        assertThat(chuoiCua(than, "loi"))
                .as("§10.68-B: bản cũ của bước SSH cho CÙNG MỘT VÂN TAY cho ba nguyên nhân cần ba cách "
                        + "xử lý ngược nhau. Năm giá trị này phải ra tới giao diện thành năm giá trị")
                .containsAll(
                        Arrays.stream(SyncFailureKind.values()).map(Enum::name).toList());
        assertThat(chuoiCua(than, "trangThai"))
                .containsAll(Arrays.stream(SyncStatus.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("⭐ Mới nhất trước — và bốn bộ đếm ra dây RIÊNG, ⛔ không gộp")
    void moiNhatTruocVaBonBoDemRieng() {
        JsonNode than = nhatKyCuaToi("").get("data");

        List<Instant> moc =
                chuoiCua(than, "batDau").stream().map(Instant::parse).toList();
        assertThat(moc)
                .as("⚠ So mốc ĐÃ PHÂN TÍCH: Jackson lược số 0 ở phần giây lẻ nên so chuỗi lexicographic "
                        + "có thể sai ở đúng những dòng khó gặp nhất")
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());

        JsonNode dauTien = than.get(0);
        assertThat(dauTien.get("loi").asText())
                .as("lượt gần nhất là EMPTY_BODY (chèn cách đây 10 phút)")
                .isEqualTo("EMPTY_BODY");

        JsonNode luotThanhCong = than.get(than.size() - 1);
        assertThat(luotThanhCong.get("trangThai").asText()).isEqualTo("SUCCESS");
        assertThat(luotThanhCong.get("soNhan").asInt()).isEqualTo(28);
        assertThat(luotThanhCong.get("soGhiMoi").asInt()).isEqualTo(19);
        assertThat(luotThanhCong.get("soMaLa").asInt())
                .as("⭐ 28 mã nguồn trả − 19 mã đã khai = 9. Con số này phải hiện RIÊNG: gộp vào 'ghi "
                        + "mới' là xoá mất chính thứ màn hình mã lạ tồn tại để hiện")
                .isEqualTo(9);
        assertThat(luotThanhCong.hasNonNull("loi"))
                .as("SUCCESS ⇒ không lý do hỏng — envelope bỏ hẳn trường null khỏi JSON")
                .isFalse();
    }

    @Test
    @DisplayName("⚠ rawLogId RỖNG nghĩa là CHƯA HỀ MỞ KẾT NỐI — ⛔ không phải 'ghi hỏng', và ⛔ không phải 0")
    void rawLogIdRongNghiaLaChuaGoi() {
        JsonNode than = nhatKyCuaToi("&loi=THIEU_MA_SO").get("data");

        assertThat(than).hasSize(1);
        assertThat(than.get(0).hasNonNull("rawLogId"))
                .as("⛔ Bẫy đã tránh trong bộ đọc: getLong()+wasNull() trả lời hộ cột đọc GẦN NHẤT, nên "
                        + "mọi NULL sẽ thành 0 — mà 'chưa mở kết nối' và 'đã ghi raw #0' là hai chuyện")
                .isFalse();

        JsonNode coRaw = nhatKyCuaToi("&loi=HTTP_ERROR").get("data");
        assertThat(coRaw.get(0).get("rawLogId").asLong())
                .as("⚠ Vế PHÂN BIỆT: thiếu vế này thì khẳng định trên xanh cả khi bộ đọc trả null cho MỌI dòng")
                .isEqualTo(504L);
    }

    @Test
    @DisplayName("Bộ lọc kết cục, lý do và 'chỉ lượt có vấn đề' — ba con số phải KHÁC nhau")
    void baBoLocChoBaConSoKhacNhau() {
        assertThat(nhatKyCuaToi("&trangThai=FAILED").get("data")).hasSize(5);
        assertThat(nhatKyCuaToi("&loi=TIMEOUT").get("data")).hasSize(1);
        assertThat(nhatKyCuaToi("&chiHong=true").get("data"))
                .as("'có vấn đề' = FAILED (5) + PARTIAL (1); ⛔ SKIPPED_UP_TO_DATE và SUCCESS không "
                        + "thuộc nhóm này — trộn chúng vào là dạy người vận hành bỏ qua màu đỏ")
                .hasSize(6);
    }

    @Test
    @DisplayName("⭐ Phân trang: tổng ĐẾM và trang LẤY soi cùng một tập dòng")
    void phanTrangDemVaLayKhopNhau() {
        ResponseEntity<String> kq = lay(kyThuat, "/api/v1/hyd/sync-logs?size=3&page=1&nguonId=" + nguonId());
        JsonNode than = doc(kq);

        assertThat(than.get("data")).hasSize(3);
        assertThat(than.get("meta").get("totalElements").asInt())
                .as("câu đếm và câu lấy trang ghép cùng một mệnh đề WHERE — lệch nhau thì thanh phân "
                        + "trang nói một đằng, bảng hiện một nẻo, và chỉ lộ ra ở trang cuối")
                .isEqualTo(8);
        assertThat(than.get("meta").get("totalPages").asInt()).isEqualTo(3);

        JsonNode trangCuoi = doc(lay(kyThuat, "/api/v1/hyd/sync-logs?size=3&page=3&nguonId=" + nguonId()));
        assertThat(trangCuoi.get("data")).hasSize(2);
        assertThat(trangCuoi.get("data").get(0).get("trangThai").asText())
                .as("trang cuối phải là hai lượt CŨ nhất — thứ tự giữ nguyên qua các trang")
                .isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("⛔ Không có tham số sort — gửi lên cũng ⛔ không làm màn hình trả 422 (hình dạng A1)")
    void thamSoSortBiBoQuaChuKhongLamDo() {
        ResponseEntity<String> kq = lay(kyThuat, "/api/v1/hyd/sync-logs?nguonId=" + nguonId() + "&sort=updatedAt,desc");

        assertThat(kq.getStatusCode())
                .as("A1: `ConstructionsPage` gửi sort mặc định ngoài whitelist ⇒ 422 ngay lượt tải đầu, "
                        + "và triệu chứng ('bảng rỗng') trùng khít trạng thái đúng nên không ai báo. "
                        + "Endpoint này KHÔNG khai tham số sort nên lớp lỗi ấy không tồn tại")
                .isEqualTo(HttpStatus.OK);
        assertThat(doc(kq).get("data")).hasSize(8);
    }

    @Test
    @DisplayName("⭐⭐ Dải tóm tắt mang ĐỦ 4 + 5 khoá kể cả khoá bằng 0 — “0 lượt NOT_WORKING” là một khẳng định")
    void daiTomTatMangDuMoiKhoa() {
        JsonNode than = doc(lay(kyThuat, "/api/v1/hyd/sync-logs/tong-hop")).get("data");

        JsonNode theoTrangThai = than.get("theoTrangThai");
        JsonNode theoLoi = than.get("theoLoi");
        assertThat(theoTrangThai.size()).isEqualTo(SyncStatus.values().length);
        assertThat(theoLoi.size()).isEqualTo(SyncFailureKind.values().length);
        for (SyncStatus s : SyncStatus.values()) {
            assertThat(theoTrangThai.hasNonNull(s.name()))
                    .as("thiếu khoá %s", s)
                    .isTrue();
        }
        for (SyncFailureKind k : SyncFailureKind.values()) {
            assertThat(theoLoi.hasNonNull(k.name())).as("thiếu khoá %s", k).isTrue();
            assertThat(theoLoi.get(k.name()).asLong()).isGreaterThanOrEqualTo(1L);
        }

        long tongLoi = 0;
        long chuaGoi = 0;
        for (SyncFailureKind k : SyncFailureKind.values()) {
            tongLoi += theoLoi.get(k.name()).asLong();
            if (!k.duocGhiVaoRawLog()) {
                chuaGoi += theoLoi.get(k.name()).asLong();
            }
        }
        assertThat(than.get("soLuotGoiHong").asLong())
                .as("⭐ Khẳng định QUAN HỆ chứ không giá trị tuyệt đối — CSDL dùng chung nên lớp khác "
                        + "cũng ghi sync_logs. Quan hệ này vẫn đúng dù có bao nhiêu dòng của ai")
                .isEqualTo(tongLoi - chuaGoi);
        assertThat(chuaGoi).as("vế chống tập rỗng cho phép trừ ở trên").isGreaterThanOrEqualTo(1L);
        assertThat(than.get("soGio").asInt()).isEqualTo(24);
        assertThat(than.hasNonNull("mocGanNhat")).isTrue();
    }

    @Test
    @DisplayName("⚠ soGio bị KẸP thì nhãn phải nói con số ĐÃ KẸP — ⛔ không in lại con số người dùng gửi")
    void soGioBiKepThiNoiRa() {
        JsonNode than =
                doc(lay(kyThuat, "/api/v1/hyd/sync-logs/tong-hop?soGio=99999")).get("data");

        assertThat(than.get("soGio").asInt())
                .as("hình dạng A3 ở dạng nhỏ nhất: modal xin size=1000, PageUtils kẹp về 100 trong im "
                        + "lặng, và không một dòng chữ nào nói")
                .isEqualTo(24 * 30);
    }

    @Test
    @DisplayName("⭐ Từ vựng đến từ BACKEND — và 'lý do chưa gọi' suy ra từ vị ngữ, ⛔ không chép tay")
    void tuVungDenTuBackend() {
        JsonNode than = doc(lay(kyThuat, "/api/v1/hyd/sync-logs/tu-vung")).get("data");

        assertThat(than.get("trangThai")).hasSize(SyncStatus.values().length);
        assertThat(than.get("lyDoHong")).hasSize(SyncFailureKind.values().length);
        assertThat(than.get("loiChuaGoi").size())
                .as("⭐ Đúng MỘT giá trị xảy ra trước khi mở kết nối. Đây là chênh lệch 5↔4 giữa "
                        + "ck_sync_logs_failure_kind và ck_hydro_raw_logs_failure_kind, hiện ra tới giao diện")
                .isEqualTo(1);
        assertThat(than.get("loiChuaGoi").get(0).asText()).isEqualTo("THIEU_MA_SO");
    }

    @Test
    @DisplayName("⭐⭐ Mã lạ: gộp đúng, giá trị là bản GẦN NHẤT, và ⛔ là CHUỖI chứ không phải số")
    void manHinhMaLaGopDung() {
        JsonNode than = doc(lay(kyThuat, "/api/v1/hyd/ma-la")).get("data");

        JsonNode chuaKhai = timTheoMa(than, MA_CHUA_KHAI);
        assertThat(chuaKhai.get("soBanGhi").asLong()).isEqualTo(3);
        assertThat(chuaKhai.get("daKhaiThanhDiemDo").asBoolean()).isFalse();
        assertThat(chuaKhai.hasNonNull("maDiemDo")).isFalse();
        assertThat(chuaKhai.get("giaTriGanNhat").isTextual())
                .as("⛔ T28.27: 2.30 tuần tự hoá thành SỐ sẽ về 2.3 và mất chữ số cuối. Kiểu khai ở "
                        + "api-types.ts là một LỜI KHẲNG ĐỊNH; @JsonFormat(STRING) là thứ làm nó đúng")
                .isTrue();
        assertThat(chuaKhai.get("giaTriGanNhat").asText())
                .as("⭐ GẦN NHẤT, ⛔ không phải bất kỳ: ba bản ghi 150 → 160 → 213, mốc tăng dần")
                .startsWith("213");
        assertThat(chuaKhai.get("donViNguon").asText())
                .as("⚠⚠ Đơn vị NGUỒN (cm), ⛔ không phải đơn vị hệ thống (m). Thiếu nó thì 213 được "
                        + "đọc thành 213 mét — sai 100 lần")
                .isEqualTo("cm");

        JsonNode daKhai = timTheoMa(than, maDaKhai);
        assertThat(daKhai.get("daKhaiThanhDiemDo").asBoolean())
                .as("⭐ Vế đối của khẳng định trên — thiếu nó thì cột trạng thái xanh cả khi nó luôn false")
                .isTrue();
        assertThat(daKhai.get("maDiemDo").asText()).isEqualTo(maDiemDoDaKhai);
        assertThat(daKhai.get("soBanGhi").asLong())
                .as("⚠ 'Đã khai' ⛔ KHÔNG có nghĩa là xong: lịch sử tích trước lúc khai vẫn nằm lại và "
                        + "phải hiện ra, nếu không thì biểu đồ trạm ấy đứt một đoạn mà không ai biết vì "
                        + "sao. ⚠ CSDL dùng chung nên bắt vào QUAN HỆ (≥ 1), ⛔ không giá trị tuyệt đối")
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("⛔ Không phản hồi nào mang mã số hay thân nguyên văn của nguồn")
    void khongPhanHoiNaoLoMaSo() {
        List<String> than = List.of(
                lay(kyThuat, "/api/v1/hyd/sync-logs?size=100&nguonId=" + nguonId())
                        .getBody(),
                lay(kyThuat, "/api/v1/hyd/sync-logs/tong-hop").getBody(),
                lay(kyThuat, "/api/v1/hyd/ma-la").getBody());

        assertThat(than).allSatisfy(t -> assertThat(t)
                .doesNotContain("credential")
                .doesNotContain("maSo")
                .doesNotContain("__VIEWSTATE")
                .doesNotContain("<br>"));
        assertThat(than.get(0))
                .as("⚠ Vế PHÂN BIỆT: chắc chắn thân có nội dung, nếu không mọi doesNotContain ở trên "
                        + "xanh vì thân rỗng (luật 7)")
                .contains("soMaLa")
                .contains("EMPTY_BODY");
    }

    @Test
    @DisplayName("⭐⭐ TECHNICIAN — người sẽ đi khai mã lạ — MỞ ĐƯỢC cả ba endpoint; không quyền thì 403")
    void quyenKhopVoiNguoiThatSuDung() {
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM role_permissions rp
                          JOIN roles r ON r.id = rp.role_id
                          JOIN permissions p ON p.id = rp.permission_id
                         WHERE r.code = 'TECHNICIAN' AND p.code = 'hyd:api-source:manage'
                        """,
                        Integer.class))
                .as("⛔ Khẳng định NỀN của cả bài: TECHNICIAN KHÔNG có quyền cấu hình nguồn. Ngày nào "
                        + "ma trận đổi thì bài dưới không còn chứng minh điều nó nói là chứng minh")
                .isZero();

        for (String duong : List.of(
                "/api/v1/hyd/sync-logs",
                "/api/v1/hyd/sync-logs/tong-hop",
                "/api/v1/hyd/sync-logs/tu-vung",
                "/api/v1/hyd/ma-la")) {
            assertThat(lay(kyThuat, duong).getStatusCode())
                    .as(
                            "T27.20 lần thứ ba: gác bằng hyd:api-source:manage thì đúng người cần đọc lại "
                                    + "không đọc được — %s",
                            duong)
                    .isEqualTo(HttpStatus.OK);
            assertThat(lay(khongQuyen, duong).getStatusCode())
                    .as("và tầng 2 vẫn là chốt chặn thật — %s", duong)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("⭐ Hợp đồng JSON khớp từng tên trường với api-types.ts — đổi tên ở đây là màn hình trắng")
    void hopDongJsonKhopVoiKieuFe() {
        JsonNode dong = nhatKyCuaToi("&loi=NOT_WORKING").get("data").get(0);

        assertThat(tenTruong(dong))
                .as("SyncLogRow ở admin-app/src/shared/api-types.ts. Envelope bỏ trường null nên chỉ "
                        + "khẳng định các trường CÓ MẶT ở một dòng lỗi đầy đủ")
                .contains(
                        "id",
                        "nguonId",
                        "nguonCode",
                        "nguonName",
                        "batDau",
                        "ketThuc",
                        "durationMs",
                        "khungNhamToi",
                        "trangThai",
                        "loi",
                        "lyDo",
                        "soNhan",
                        "soGhiMoi",
                        "soTrungBoQua",
                        "soMaLa",
                        "rawLogId");

        JsonNode tongHop = doc(lay(kyThuat, "/api/v1/hyd/sync-logs/tong-hop")).get("data");
        assertThat(tenTruong(tongHop))
                .containsExactlyInAnyOrder(
                        "tuMoc", "soGio", "soLuot", "theoTrangThai", "theoLoi", "soLuotGoiHong", "mocGanNhat");

        JsonNode maLa = timTheoMa(doc(lay(kyThuat, "/api/v1/hyd/ma-la")).get("data"), MA_CHUA_KHAI);
        assertThat(tenTruong(maLa))
                .as("⛔ maDiemDo vắng mặt ở dòng chưa khai vì envelope bỏ null — đó là hành vi ĐÚNG và "
                        + "api-types.ts khai `string | null`")
                .contains(
                        "apiCode",
                        "nguonId",
                        "nguonCode",
                        "soBanGhi",
                        "lanDau",
                        "lanGanNhat",
                        "giaTriGanNhat",
                        "donViNguon",
                        "daKhaiThanhDiemDo");
    }

    private static List<String> tenTruong(JsonNode node) {
        List<String> ten = new ArrayList<>();
        node.fieldNames().forEachRemaining(ten::add);
        return ten;
    }

    private static JsonNode timTheoMa(JsonNode mang, String apiCode) {
        for (JsonNode n : mang) {
            if (apiCode.equals(n.path("apiCode").asText())) {
                return n;
            }
        }
        throw new AssertionError("Không thấy mã " + apiCode + " trong " + mang);
    }
}
