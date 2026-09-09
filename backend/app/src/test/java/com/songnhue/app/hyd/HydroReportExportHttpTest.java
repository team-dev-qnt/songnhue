package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.job.JobWorker;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * ⭐⭐ Kết xuất báo cáo <b>đi trọn vòng</b> — WS-34/T34.7 · T34.8.
 *
 * <h2>Vì sao bài này phải chạy CẢ việc nền, ⛔ không gọi thẳng handler</h2>
 *
 * <p>Đợt này nối <b>hai nửa cặp đọc–ghi</b> hở từ Phase 0, và cả hai chỉ chứng minh được khi đi qua
 * {@link JobWorker} thật:
 *
 * <ol>
 *   <li>{@code jobs.result} — ba nơi đọc, ⛔ không nơi ghi. Đường ghi mới ({@code JobContext.result})
 *       chạy trong một giao dịch riêng, rồi {@code JobWorker.succeed()} <b>đọc lại</b> giá trị ấy từ
 *       CSDL. Gọi thẳng handler thì bước đọc lại ⛔ không xảy ra, và một lời gọi đặt sai chỗ vẫn
 *       xanh. ⚠ Đây đúng luật 5: bài kiểm gọi thẳng service ⛔ không đi cùng đường với production.
 *   <li>{@code MINIO_BUCKET_REPORT} — cấp phát từ 13/8, {@code minio-init} tạo, {@code push-offsite.sh}
 *       sao lưu, {@code @NotBlank} chặn khởi động, và ⛔ không dòng mã nào đọc. Ở đây nó nhận byte
 *       thật từ MinIO thật ({@code SongnhueMinio}) — ⛔ không mock.
 * </ol>
 *
 * <p>⛔ Mock {@code ReportFilePort} ở bài này thì ⛔ <b>không kiểm được gì cả</b> — luật 4:
 * {@code BackupServiceTest} mock {@code PostgresToolRunner} và {@code pg_dump} chưa từng chạy suốt
 * bốn ngày.
 */
/**
 * ⭐⭐ Bật worker việc nền cho <b>riêng</b> lớp này — và đó là lý do nó có context Spring riêng.
 *
 * <p>{@code IntegrationTestBase} tắt worker cho toàn bộ bộ kiểm, và đó là quyết định đúng: một
 * worker chạy ngầm trong mọi lớp kiểm sẽ nhặt việc do lớp khác vừa đặt, và bài kiểm đếm job của
 * chúng đỏ theo thứ tự chạy.
 *
 * <p>⛔ Cái giá là một lượt khởi động context nữa (~13 giây). Chấp nhận có chủ đích: đây là cách
 * <b>duy nhất</b> chứng minh đường ghi {@code jobs.result} đi đúng — nó chạy trong một giao dịch
 * riêng rồi {@code JobWorker.succeed()} <b>đọc lại</b> giá trị ấy từ CSDL, và gọi thẳng handler thì
 * bước đọc lại ⛔ không xảy ra (luật 5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.test.context.TestPropertySource(properties = "app.worker-enabled=true")
class HydroReportExportHttpTest extends IntegrationTestBase {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JobWorker worker;

    private PhienHttp phienHttp;

    /** ⚠ TECHNICIAN có {@code hyd:report:export} theo ma trận seed — xem {@code RbacMatrixTest}. */
    private PhienHttp.Phien kyThuat;

    private LocalDate ngay;

    @BeforeAll
    void dungDuLieu() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t34_xuat", "TECHNICIAN"));
        ngay = LocalDate.now(DateTimeUtils.ZONE_VN).minusDays(1);
    }

    @Test
    @DisplayName("⭐⭐ Vòng khép kín: 202 → việc nền chạy → jobs.result có con trỏ → tải ra byte CSV thật")
    void theWholeExportLoopProducesRealBytes() {
        ResponseEntity<String> dat =
                xuat("{\"loai\":\"BC05\",\"tuNgay\":\"%s\",\"denNgay\":\"%s\"}".formatted(ngay, ngay));

        assertThat(dat.getStatusCode())
                .as("⭐ 202, ⛔ không 200: mã trạng thái phải nói ra rằng CHƯA có gì để tải. Thân: %s", dat.getBody())
                .isEqualTo(HttpStatus.ACCEPTED);

        String jobId = PhienHttp.giaTriJson(dat.getBody(), "publicId");
        assertThat(jobId)
                .as("⚠ Vế chống tập rỗng: không có mã việc thì mọi khẳng định dưới vô nghĩa")
                .isNotBlank();

        // Chưa chạy ⇒ chưa tải được, và câu trả lời phải PHÂN BIỆT ĐƯỢC với "hết hạn" và "không có".
        ResponseEntity<String> som = phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/tai/" + jobId);
        assertThat(som.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(som.getBody()).contains("HYD-2015");

        chayViecNen();

        // ⭐⭐ Nửa cặp đọc–ghi thứ nhất: cột này KHÔNG có đường ghi nào cho tới đợt này.
        String conTro = jdbc.queryForObject("SELECT result FROM jobs WHERE public_id = ?::uuid", String.class, jobId);
        assertThat(conTro)
                .as(
                        """
                        ⛔⛔ `jobs.result` vẫn NULL sau khi việc nền xong. Cột này có BA nơi đọc từ Phase \
                        0 (JobDtos, JobService.findJob, và chính JobWorker.succeed() đọc lại để giữ giá \
                        trị qua lượt ghi trạng thái) và, cho tới WS-34, ⛔ KHÔNG một nơi ghi nào — javadoc \
                        của JobRef.resultRef thì mô tả đích danh tính năng chưa từng tồn tại: "con trỏ \
                        tới kết quả khi đã xong (VD khoá tệp báo cáo trong kho)".""")
                .isNotNull();

        // ⚠ Khẳng định theo CẤU TRÚC, ⛔ không theo văn bản (luật 2). Cột là JSONB: PostgreSQL
        //   chuẩn hoá lại tài liệu — sắp xếp khoá và chèn khoảng trắng sau dấu hai chấm — nên một
        //   khẳng định trên chuỗi thô đang khẳng định về BỘ ĐỊNH DẠNG của PostgreSQL, ⛔ không phải
        //   về thứ mã của ta ghi ra. Đo được ngay lượt chạy đầu: ta ghi `{"khoa":"…","tenTep":…}`,
        //   CSDL trả về `{"khoa": "…", "soByte": …, "soDong": …, "tenTep": "…"}`.
        assertThat(jdbc.queryForObject(
                        "SELECT result ->> 'khoa' FROM jobs WHERE public_id = ?::uuid", String.class, jobId))
                .as("con trỏ phải là KHOÁ đối tượng trong kho báo cáo")
                .startsWith("hyd/")
                .endsWith(".csv");
        assertThat(jdbc.queryForObject(
                        "SELECT (result ->> 'soDong')::int FROM jobs WHERE public_id = ?::uuid", Integer.class, jobId))
                .as("⚠ Vế chống tập rỗng: một tệp CHỈ có dòng tiêu đề trông y hệt một tệp đầy đủ")
                .isGreaterThan(1);

        ResponseEntity<byte[]> tep = taiByte(jobId);
        assertThat(tep.getStatusCode()).isEqualTo(HttpStatus.OK);

        byte[] noiDung = tep.getBody();
        assertThat(noiDung)
                .as("⛔⛔ Bản kết xuất chưa từng ra được một byte nào — đúng hình dạng §10.52 (envelope "
                        + "bọc byte[] làm ảnh cổng im lặng suốt bốn ngày)")
                .isNotNull();
        assertThat(noiDung.length).isGreaterThan(BOM.length);

        assertThat(new byte[] {noiDung[0], noiDung[1], noiDung[2]})
                .as("⭐ BOM UTF-8 — thiếu nó thì Excel đoán bảng mã theo địa phương và 'Cống Liên Mạc' "
                        + "thành 'CÃ´ng LiÃªn Máº¡c'")
                .isEqualTo(BOM);

        String van = new String(noiDung, StandardCharsets.UTF_8);
        assertThat(van)
                .as("⭐ Dấu tách `;` — khớp dấu tách danh sách của Excel vi-VN. Dấu phẩy cho ra MỘT cột.")
                .contains("\"Mã điểm đo\";\"Tên điểm đo\"")
                .contains("Tuyến sông");

        assertThat(tep.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .as("⭐ `attachment`, ⛔ không `inline`: đây là tệp để lưu, ⛔ không phải nội dung để xem")
                .contains("attachment")
                .contains("BC05_" + ngay + "_" + ngay + ".csv");
    }

    @Test
    @DisplayName("⛔⛔ Mã việc của LOẠI KHÁC ⛔ không tải được — 'UUID khó đoán' ⛔ không phải một tầng phân quyền")
    void aJobOfAnotherTypeIsNotDownloadable() {
        UUID idViecKhac = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO jobs (public_id, job_type, payload, status, result, created_at)
                VALUES (?::uuid, 'DB_BACKUP', '{}', 'SUCCEEDED', '{"khoa":"bi-mat.dump"}'::jsonb, now())
                """,
                idViecKhac.toString());

        ResponseEntity<String> ra = phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/tai/" + idViecKhac);

        assertThat(ra.getStatusCode())
                .as(
                        """
                        ⛔⛔ Endpoint này nhận một mã việc rồi trả nội dung việc ấy sinh ra. Bỏ phép kiểm \
                        `jobType` thì bất kỳ ai có `hyd:report:export` cũng đọc được kết quả của MỌI loại \
                        việc nền — gồm DB_BACKUP. Mã việc là UUID nên khó đoán, nhưng "khó đoán" ⛔ không \
                        phải một tầng phân quyền (§4.2).""")
                .isEqualTo(HttpStatus.NOT_FOUND);

        jdbc.update("DELETE FROM jobs WHERE public_id = ?::uuid", idViecKhac.toString());
    }

    @Test
    @DisplayName("⛔ Mã báo cáo lạ bị chặn NGAY ở API — ⛔ không rơi xuống một dòng FAILED không ai đọc")
    void anUnknownReportCodeIsRejectedAtTheApi() {
        ResponseEntity<String> ra =
                xuat("{\"loai\":\"BC99\",\"tuNgay\":\"%s\",\"denNgay\":\"%s\"}".formatted(ngay, ngay));
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("⛔ Đường XUẤT đi qua ĐÚNG trần khoảng ngày của đường XEM — ⛔ không có lối vòng")
    void theExportPathHonoursTheSameDateCaps() {
        // 40 ngày: hợp lệ với BC-05 (trần 366), quá trần với BC-12 (trần 31).
        LocalDate tu = ngay.minusDays(39);

        assertThat(xuat("{\"loai\":\"BC05\",\"tuNgay\":\"%s\",\"denNgay\":\"%s\"}".formatted(tu, ngay))
                        .getStatusCode())
                .as("⚠ Vế phân biệt: cùng khoảng ấy BC-05 PHẢI nhận, nếu không bài này ⛔ không chứng "
                        + "minh được hai trần là hai con số khác nhau")
                .isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> qua = xuat(
                """
                {"loai":"BC12","tuNgay":"%s","denNgay":"%s","stationPublicId":"%s","maLoaiChiSo":"MUC_NUOC"}
                """
                        .formatted(tu, ngay, UUID.randomUUID()));

        assertThat(qua.getStatusCode())
                .as("⛔ Một đường xuất lỏng hơn đường xem là một cách đi vòng qua chính cái trần vừa đặt")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(qua.getBody()).contains("HYD-2012").contains("31");
    }

    // =========================================================================
    // BC-13 và BC-11 — hai bản kết xuất mà ⛔ KHÔNG bài nào đi tới byte thật trước 09/09/2026.
    //
    // ⛔ Các bài phía trên canh phân quyền, mã việc lạ và hạn tải; ĐÚNG MỘT bài
    // (`theWholeExportLoopProducesRealBytes`) chạy hết vòng khép kín, và nó xuất **chỉ BC05**.
    // Vì thế BC-13 xuất nhầm bảng suốt từ T34.7 mà mọi cổng kiểm đều xanh: nội dung tệp của hai mã
    // báo cáo kia ⛔ chưa từng bị ai nhìn. Luật 28 — cái xanh của một bộ canh hẹp đọc như một lời
    // bảo đảm rộng.
    // =========================================================================

    /**
     * ⛔⛔ BC-13 phải mang <b>khối nhật ký đồng bộ</b>.
     *
     * <p>{@code BaoCaoDongBoView} có hai danh sách; bản kết xuất cũ chỉ in danh sách chất lượng, nên
     * {@code dongBo()} — 13 trường, có truy vấn, có DTO, có endpoint — có <b>0 nơi gọi</b> trong toàn
     * kho. Đối chiếu {@code report-templates-proposal.md} §2.2, bảng cũ trùng đúng <b>2/12</b> cột
     * đặc tả.
     *
     * <p>⚠ Bài này khẳng định theo <b>tên cột chỉ có ở khối mới</b>, ⛔ không theo số dòng: hàng nhật
     * ký đồng bộ chỉ có khi poller đã chạy, mà bộ kiểm ⛔ không chạy poller. Một khẳng định trên số
     * dòng sẽ đỏ vì lý do sai. Cột thì luôn phải có — và trước đợt này chúng ⛔ không tồn tại, nên
     * phép đo phân biệt được hai trạng thái (luật 9).
     */
    @Test
    @DisplayName("⛔⛔ BC-13 kết xuất có KHỐI NHẬT KÝ ĐỒNG BỘ — bản cũ chỉ in bảng chất lượng")
    void bc13CarriesTheSyncJournalBlock() {
        String van = xuatRoiDocVan("BC13", ngay, ngay);

        assertThat(van)
                .as("⛔ Thiếu tiêu đề khối A nghĩa là bản kết xuất vẫn chỉ là bảng chất lượng — đúng "
                        + "khuyết tật `dongBo()` 0 nơi gọi")
                .contains("A. NHẬT KÝ ĐỒNG BỘ")
                .contains("B. CHẤT LƯỢNG DỮ LIỆU");

        assertThat(van)
                .as("bốn cột chỉ có ở khối nhật ký đồng bộ — chúng ⛔ không tồn tại ở bảng chất lượng")
                .contains("Bỏ qua (rate-limit)")
                .contains("Số lượt gọi thật")
                .contains("Mã lạ (không khớp điểm đo)")
                .contains("Số trạm không có bản ghi hợp lệ");

        assertThat(van)
                .as("khối chất lượng phải còn nguyên — thay một khối bằng khối kia là đổi một bản "
                        + "kết xuất sai lấy một bản kết xuất sai khác")
                .contains("Khung bỏ sót");
    }

    /**
     * ⭐⭐ BC-11 — báo cáo DUY NHẤT ⛔ không có đường xuất nào trước 09/09/2026.
     *
     * <p>Bài này cũng là phép đối chứng <b>độc lập</b> cho migration {@code V202609091073}: nếu bản
     * chụp G8 ⛔ không vào được CSDL thì mọi điểm đo rơi vào nhóm "Chưa phân tuyến" và chuỗi
     * <i>"Sông Nhuệ"</i> ⛔ không xuất hiện. Hai phép đo ⛔ không chia sẻ giả định nào —
     * {@code HydroCatalogueSeedTest} đọc thẳng bảng, bài này đi qua HTTP → hàng đợi → kho tệp → byte.
     */
    @Test
    @DisplayName("⭐⭐ BC-11 xuất ra byte thật, và tuyến sông từ bản chụp G8 có mặt trong tệp")
    void bc11IsExportableAndCarriesRiverNames() {
        String van = xuatRoiDocVan("BC11", ngay, ngay);

        assertThat(van).contains("Tuyến sông").contains("Lý trình").contains("Trạng thái tín hiệu");

        assertThat(van)
                .as("⭐ 13/19 điểm đo có tuyến sông sau V202609091073 — chuỗi này vắng mặt nghĩa là "
                        + "bản chụp G8 ⛔ không tới được báo cáo, dù migration có chạy")
                .contains("Sông Nhuệ");

        assertThat(van)
                .as("⛔ 6 điểm đo bản chụp ghi 'Chưa rõ' vẫn phải CÓ MẶT — biểu này sinh ra để chỉ "
                        + "chỗ thiếu, ⛔ không phải để ẩn chúng đi")
                .contains("Chưa phân tuyến");
    }

    /** ⛔ BC-11 là ảnh chụp MỘT ngày — một khoảng sẽ cho ra tệp mang tên sai kỳ. */
    @Test
    @DisplayName("⛔ BC-11 nhận một KHOẢNG ngày → 422, không lặng lẽ lấy ngày cuối")
    void bc11RejectsADateRange() {
        ResponseEntity<String> dat =
                xuat("{\"loai\":\"BC11\",\"tuNgay\":\"%s\",\"denNgay\":\"%s\"}".formatted(ngay.minusDays(3), ngay));

        // ⚠ 400, ⛔ không 422: `SYS-0003` khai `HttpStatus.BAD_REQUEST` ở `ErrorCode`. Khẳng định
        //   theo mã lỗi thay vì theo con số HTTP mà mình đoán — đây đúng là chỗ lượt viết đầu sai.
        assertThat(dat.getStatusCode())
                .as(
                        "⛔ 202 ở đây nghĩa là người dùng nhận một tệp tên `BC11_<3 ngày trước>_<hôm qua>` "
                                + "chứa số liệu của đúng một ngày: %s",
                        dat.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dat.getBody()).contains("SYS-0003");
    }

    /** Đặt lượt xuất → chạy hàng đợi → tải về → trả nội dung dạng văn bản UTF-8. */
    private String xuatRoiDocVan(String loai, LocalDate tu, LocalDate den) {
        ResponseEntity<String> dat =
                xuat("{\"loai\":\"%s\",\"tuNgay\":\"%s\",\"denNgay\":\"%s\"}".formatted(loai, tu, den));
        assertThat(dat.getStatusCode())
                .as("đặt lượt xuất %s: %s", loai, dat.getBody())
                .isEqualTo(HttpStatus.ACCEPTED);

        String jobId = PhienHttp.giaTriJson(dat.getBody(), "publicId");
        assertThat(jobId)
                .as("⚠ chống tập rỗng: không có mã việc thì mọi khẳng định sau vô nghĩa")
                .isNotBlank();

        chayViecNen();

        ResponseEntity<byte[]> tep = taiByte(jobId);
        assertThat(tep.getStatusCode())
                .as("⛔ tải %s hỏng — việc nền có thể đã FAILED, xem jobs.last_error", loai)
                .isEqualTo(HttpStatus.OK);

        byte[] noiDung = tep.getBody();
        assertThat(noiDung).as("thân tệp %s", loai).isNotNull();
        assertThat(noiDung.length)
                .as("⚠ chống tập rỗng: một tệp chỉ có BOM trông y hệt một tệp đầy đủ trên dòng job")
                .isGreaterThan(BOM.length + 20);

        return new String(noiDung, StandardCharsets.UTF_8);
    }

    private ResponseEntity<String> xuat(String than) {
        return phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/bao-cao/xuat", than);
    }

    /**
     * Chạy hàng đợi cho tới khi việc kết xuất xong.
     *
     * <p>⚠ Lặp có trần: một vòng {@code while} không cận trong bộ kiểm là một lần treo CI ⛔ không có
     * thông điệp nào.
     */
    private void chayViecNen() {
        for (int i = 0; i < 40; i++) {
            worker.poll();
            try {
                // ⚠ `poll()` chỉ ĐẶT việc vào pool rồi trả về ngay — nó ⛔ không chạy handler đồng
                //   bộ. Một vòng lặp không nghỉ sẽ quay 40 lượt trong vài mili giây và kết luận
                //   "không xong", trong khi việc mới chỉ vừa được nhận.
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            Long conCho = jdbc.queryForObject(
                    "SELECT count(*) FROM jobs WHERE job_type = 'HYDRO_REPORT_EXPORT' "
                            + "AND status IN ('PENDING', 'RUNNING')",
                    Long.class);
            if (conCho != null && conCho == 0) {
                return;
            }
        }
        // ⛔ Thông điệp phải mang SỐ ĐO, ⛔ không chỉ nói "không xong": trạng thái và lý do hỏng là
        //   hai thứ duy nhất chỉ về phía nguyên nhân khi bài này đỏ trên runner.
        throw new IllegalStateException("Việc kết xuất ⛔ không xong sau 40 lượt poll — trạng thái: "
                + jdbc.queryForList(
                        "SELECT status, attempts, last_error FROM jobs WHERE job_type = 'HYDRO_REPORT_EXPORT'"));
    }

    private ResponseEntity<byte[]> taiByte(String jobId) {
        return http.exchange(
                "/api/v1/hyd/bao-cao/tai/" + jobId,
                HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(phienHttp.header(kyThuat)),
                byte[].class);
    }
}
