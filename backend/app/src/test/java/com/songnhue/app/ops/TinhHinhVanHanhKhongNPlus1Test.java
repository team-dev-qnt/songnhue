package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.DemTruyVan;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;

/**
 * <b>Số truy vấn của bảng Tình hình vận hành ⛔ được tăng theo số công trình</b> — T68.36.
 *
 * <h2>Khuyết tật, và vì sao nó tồn tại một cách CÓ LÝ</h2>
 *
 * {@code PublicOperationStatusService.hienHanh()} gọi {@code banGhiMoiNhat(id)} <b>trong một vòng
 * lặp</b> — một truy vấn mỗi công trình. Javadoc của chính lớp ấy khai ra lựa chọn này kèm lý do
 * đúng: cái phải giữ là <i>MỘT định nghĩa "hiện hành"</i> dùng chung với mắt xích 4 của
 * {@code ConstructionStatusService}, vì hai định nghĩa lệch nhau thì cổng nói cống mở treo trong khi
 * dashboard nội bộ nói đóng kín, và <b>⛔ gì báo sai</b>. Nó còn hẹn sẵn: <i>"khi danh mục vượt
 * ~200 công trình thì đổi sang {@code DISTINCT ON (construction_id)} — và lúc ấy phải đổi CẢ
 * {@code banGhiMoiNhat} để hai nơi vẫn nói một điều"</i>.
 *
 * <p>⇒ Việc phải làm ⛔ phải <i>"gộp truy vấn"</i> mà là <b>gộp truy vấn mà ⛔ đẻ định nghĩa thứ
 * hai</b>. Đó là lý do lớp này có <b>hai</b> bài, ⛔ phải một, và bài thứ hai mới là bài khó.
 *
 * <h2>Vì sao bài 1 so HAI CỠ dữ liệu thay vì khẳng định một ngưỡng</h2>
 *
 * Xem javadoc {@link DemTruyVan}. Tóm lại: một ngưỡng tuyệt đối đỏ vì thay đổi vô can, và cách sửa
 * rẻ nhất khi nó đỏ là <b>nâng con số</b> — tự tay tháo bộ canh (§11.17, T58.6). Bất biến thật của
 * N+1 là <b>độ dốc</b>, và độ dốc thì ⛔ nới được.
 *
 * <h2>⭐ Phạm vi: bài này canh CẢ HAI nơi gọi</h2>
 *
 * {@code hienHanh()} có đúng hai nơi gọi (đo 24/09): {@code PublicConstructionController} — khối
 * Vận hành công trình trên <b>trang chủ</b>, tức đường của NFR-02 và DOD1.17 — và
 * {@code ConstructionLookupAdapter:139}, thứ nuôi <b>BC-11</b> của {@code HydroReportService}. Vá ở
 * {@code hienHanh()} nên phủ cả hai; bài đo qua đường công khai vì đó là đường có ràng buộc thời
 * gian.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinhHinhVanHanhKhongNPlus1Test extends IntegrationTestBase {

    private static final String TIEN_TO = "T6836-";

    /** Mã tình hình vận hành của riêng bài này — ⛔ dùng mã seed, để lượt dọn ⛔ chạm dữ liệu thật. */
    private static final String MA_TT = "T6836TT";

    /** Cỡ nhỏ và cỡ lớn. Chênh lệch phải đủ to để một N+1 lộ ra rõ hơn mọi nhiễu. */
    private static final int CO_NHO = 3;

    private static final int CO_LON = 30;

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private ConstructionOperationStatusRepository banGhiRepo;

    private DemTruyVan demTruyVan;
    private Long donViId;
    private Long maTtId;

    @BeforeAll
    void dungNen() {
        demTruyVan = new DemTruyVan(emf);
        donViId = jdbc.queryForObject("SELECT id FROM org_units ORDER BY id LIMIT 1", Long.class);
        jdbc.update(
                """
                INSERT INTO operation_status_codes (code, name, has_parameter, color_hex, sort_order, active)
                VALUES (?, 'Mã kiểm thử T68.36', FALSE, '#1a7f37', 900, TRUE)
                ON CONFLICT DO NOTHING
                """,
                MA_TT);
        maTtId = jdbc.queryForObject("SELECT id FROM operation_status_codes WHERE code = ?", Long.class, MA_TT);
    }

    @AfterAll
    void donDep() {
        // `ON DELETE CASCADE` dọn `construction_operation_status` theo công trình; hàng của bài
        // trùng-mốc gắn vào chính những công trình ấy nên ⛔ sót.
        jdbc.update("DELETE FROM constructions WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM operation_status_codes WHERE code = ?", MA_TT);
    }

    @Test
    @DisplayName("⭐⭐ Gấp 10 lần số công trình ⛔ được làm số truy vấn của bảng vận hành tăng theo")
    void soTruyVanKhongTangTheoSoCongTrinh() {
        taoCongTrinh(0, CO_NHO);
        long vetNho = demTruyVan.dem(this::moBangVanHanh);

        taoCongTrinh(CO_NHO, CO_LON);
        long vetLon = demTruyVan.dem(this::moBangVanHanh);

        // Tiền đề phải là một KHẲNG ĐỊNH: nếu lượt dựng dữ liệu hỏng thì cả hai lượt đo chạy trên
        // cùng một tập và bài này xanh mà ⛔ so gì (luật 7).
        Integer soCongTrinh = jdbc.queryForObject(
                "SELECT count(*) FROM constructions WHERE code LIKE ?", Integer.class, TIEN_TO + "%");
        assertThat(soCongTrinh)
                .as("dữ liệu nền phải thật sự có %d công trình", CO_LON)
                .isEqualTo(CO_LON);

        long chenhLech = vetLon - vetNho;
        assertThat(chenhLech)
                .as(
                        """
                        Số truy vấn TĂNG THEO số công trình — đây là hình dạng N+1.

                          %d công trình ⇒ %d câu lệnh
                          %d công trình ⇒ %d câu lệnh   (chênh %d)

                        Thêm %d công trình mà sinh thêm %d câu lệnh nghĩa là `hienHanh()` còn gọi \
                        repository trong vòng lặp. Đường này dựng khối Vận hành công trình trên \
                        TRANG CHỦ (NFR-02 · DOD1.17) và nuôi BC-11 — trên VPS 2 nhân của Công ty nó \
                        là một trang *"hơi chậm"*, thứ ⛔ ai đi đo, và càng nhiều công trình càng chậm.

                        ⛔ ĐỪNG sửa bài này bằng cách nới con số: ngưỡng ở đây ⛔ phải một ngân sách \
                        truy vấn, nó là vế phân biệt *hằng số* với *tuyến tính*.""",
                        CO_NHO, vetNho, CO_LON, vetLon, chenhLech, CO_LON - CO_NHO, chenhLech)
                .isLessThan(CO_LON - CO_NHO) // ⇐ độ dốc phải NHỎ HƠN 1 câu lệnh mỗi công trình
                .isLessThan(10);
    }

    @Test
    @DisplayName("⛔⛔ MỌI câu chọn \"bản ghi mới nhất\" phải mang CÙNG vế phân xử khi TRÙNG mốc")
    void moiCauChonBanGhiMoiNhatMangCungVePhanXu() {
        // ⛔⛔ Vì sao vế phân biệt ở đây là CẤU TRÚC chứ ⛔ phải hành vi.
        //
        // Gộp truy vấn nghĩa là kho có thêm một câu SQL nói cùng một điều — đúng thứ javadoc
        // `PublicOperationStatusService` cảnh báo. Chỗ chúng CÓ THỂ lệch nhau ⛔ phải dữ liệu bình
        // thường mà là dữ liệu TRÙNG `effective_at` (trực ban nhập tay theo ngày ⇒ ⛔ phải ca lạ).
        //
        // Nhưng một bài HÀNH VI trên dữ liệu trùng mốc ⛔ đỏ tin cậy được: thiếu vế phân xử thì
        // Postgres trả hàng nào là tuỳ kế hoạch truy vấn và thứ tự heap, nên bản HỎNG vẫn xanh phần
        // lớn các lượt chạy. Một bài đỏ-ngẫu-nhiên vừa ⛔ chứng minh được gì khi xanh, vừa dạy người
        // đọc rằng cứ chạy lại là hết (luật 9).
        //
        // ⛔⛔ VÀ BẢN ĐẦU CỦA BÀI NÀY HỤT PHẠM VI — luật 28, do chính người vừa viết nó mắc.
        //   Nó gõ tay HAI cái tên (`banGhiMoiNhat`, `banGhiMoiNhatTheoLo`) rồi xanh, trong khi
        //   repository có câu THỨ BA chọn cùng một thứ: câu con của `findConstructionIdsWithLatestCode`
        //   — và javadoc của chính nó đòi tập nó trả về phải khớp với `banGhiMoiNhat`. Một bộ canh
        //   hẹp hơn nơi nó phải chặn thì cái xanh của nó đọc như một lời bảo đảm.
        // ⇒ Phạm vi do bộ canh ĐO: mọi `@Query` của repository mà SQL có `effective_at DESC` đều
        //   đang chọn "bản ghi mới nhất", nên đều phải mang vế phân xử. Câu thứ tư ra đời là một
        //   lượt CI đỏ, ⛔ phải một dòng người ta quên thêm vào danh sách.
        Map<String, String> cauCanCanh = new java.util.LinkedHashMap<>();
        for (Method m : ConstructionOperationStatusRepository.class.getDeclaredMethods()) {
            Query q = m.getAnnotation(Query.class);
            if (q == null) {
                continue;
            }
            String sql = gonKhoangTrang(q.value());
            if (sql.contains("effective_at DESC")) {
                cauCanCanh.put(m.getName(), sql);
            }
        }

        // Tiền đề (luật 7): một bài cấu trúc chạy trên tập RỖNG thì xanh mà ⛔ đọc gì. Con số 3 là
        // số đo 24/09 — nó chỉ được TĂNG. Tụt xuống nghĩa là phản xạ đã hỏng hoặc một câu vừa bị gỡ.
        assertThat(cauCanCanh)
                .as("⛔ đọc được câu `@Query` nào có `effective_at DESC` — phản xạ đã hỏng, bài này khẳng định RỖNG")
                .hasSizeGreaterThanOrEqualTo(3);

        cauCanCanh.forEach((ten, sql) -> assertThat(sql)
                .as(
                        """
                        `%s` sắp theo `effective_at DESC` mà ⛔ có vế phân xử khi hai hàng BẰNG NHAU.

                        Postgres ⛔ bảo đảm thứ tự giữa các hàng bằng nhau, nên câu này trả hàng nào là \
                        tuỳ kế hoạch truy vấn. Nhiều câu cùng mô tả "bản ghi mới nhất" mà thiếu vế ấy thì \
                        chúng có thể trả HAI hàng khác nhau trên cùng một dữ liệu — cổng công khai và \
                        dashboard nội bộ nói hai điều về cùng một cái cống, và ⛔ màn hình nào báo sai.

                        ⇒ Mọi câu phải mang `effective_at DESC, <tiền tố>id DESC` (hàng ghi sau thắng).

                        SQL đang đọc được:
                        %s""",
                        ten, sql)
                .containsPattern("effective_at DESC, (\\w+\\.)?id DESC"));

        // Vế HÀNH VI đi kèm — ⛔ phải bộ canh, mà là phép đối chứng rằng vế cấu trúc trên nói về một
        // thứ CÓ THẬT: trên dữ liệu trùng mốc, hai câu trả cùng một hàng, và đó là hàng ghi sau.
        Long ctId = taoMotCongTrinh(TIEN_TO + "TRUNG");
        Long hangCu = themBanGhi(ctId, "2026-09-20T03:00:00Z");
        Long hangMoi = themBanGhi(ctId, "2026-09-20T03:00:00Z");
        assertThat(hangMoi).as("tiền đề: hai hàng phải là hai hàng khác nhau").isNotEqualTo(hangCu);

        Long moiNhatDon = banGhiRepo
                .banGhiMoiNhat(ctId)
                .orElseThrow(() -> new AssertionError("tiền đề hỏng: ⛔ tra ra bản ghi nào"))
                .getId();
        List<Long> moiNhatLo = banGhiRepo.banGhiMoiNhatTheoLo(List.of(ctId)).stream()
                .map(s -> s.getId())
                .toList();

        assertThat(moiNhatLo)
                .as("câu gộp phải trả ĐÚNG MỘT hàng cho một công trình — `DISTINCT ON` sai là trả nhiều")
                .hasSize(1);
        assertThat(moiNhatLo.get(0))
                .as(
                        "hai câu trả hai hàng khác nhau trên dữ liệu trùng mốc: đơn=%d · lô=%d",
                        moiNhatDon, moiNhatLo.get(0))
                .isEqualTo(moiNhatDon);
        assertThat(moiNhatDon)
                .as("giữa hai hàng trùng mốc, hàng được ghi SAU (id lớn hơn) phải thắng")
                .isEqualTo(hangMoi);
    }

    private static String gonKhoangTrang(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private void moBangVanHanh() {
        ResponseEntity<String> tl = http.getForEntity("/api/v1/public/constructions/operation-statuses", String.class);
        assertThat(tl.getStatusCode())
                .as("lượt mở bảng phải thành công, ⛔ thì phép đếm đo một đường lỗi: %s", tl.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    /** Dựng công trình + một bản ghi vận hành bằng SQL — ⛔ qua API, vì lượt dựng ⛔ phải thứ đang đo. */
    private void taoCongTrinh(int tu, int den) {
        for (int i = tu; i < den; i++) {
            Long id = taoMotCongTrinh(TIEN_TO + i);
            themBanGhi(id, "2026-09-%02dT03:00:00Z".formatted(1 + (i % 28)));
        }
    }

    private Long taoMotCongTrinh(String ma) {
        return jdbc.queryForObject(
                """
                INSERT INTO constructions (code, name, construction_type, org_unit_id, lifecycle_state)
                VALUES (?, ?, 'CONG', ?, 'DANG_HOAT_DONG')
                RETURNING id
                """,
                Long.class,
                ma,
                "Cống kiểm thử " + ma,
                donViId);
    }

    private Long themBanGhi(Long congTrinhId, String mocIso) {
        return jdbc.queryForObject(
                """
                INSERT INTO construction_operation_status
                       (construction_id, org_unit_id, operation_code_id, effective_at)
                VALUES (?, ?, ?, CAST(? AS timestamptz))
                RETURNING id
                """,
                Long.class,
                congTrinhId,
                donViId,
                maTtId,
                mocIso);
    }
}
