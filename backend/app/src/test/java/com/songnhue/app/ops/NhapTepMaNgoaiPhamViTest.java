package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.importer.KetQuaNhap;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.hydro.application.importer.StationLocationImportService;
import com.songnhue.operations.application.importer.ConstructionImportService;
import com.songnhue.operations.application.importer.TramBomImportService;

/**
 * ⭐⭐ <b>T81.4</b> — ba bộ nhập tệp tra mã <b>qua bộ lọc phạm vi</b>, trong khi mã là duy nhất
 * <b>toàn Công ty</b>.
 *
 * <h2>Vì sao ba lớp này đi chung một bài</h2>
 *
 * <p>Chúng hỏng theo <b>cùng một cơ chế</b> và được tìm ra bằng <b>cùng một phép đo</b>: luật
 * bytecode W2 ({@code GhiPhamViRuleTest}) sau khi vị từ của nó thôi chỉ nhận {@code existsBy…Code…}
 * mà nhận cả {@code findBy}/{@code countBy}. Trước 23/09/2026 cả ba vô hình với luật, và cái xanh
 * của W2 <b>đọc như một lời bảo đảm</b> cho một vùng nó ⛔ soi (luật 28).
 *
 * <h2>Ba triệu chứng khác nhau, cùng một gốc</h2>
 *
 * <ul>
 *   <li><b>Danh mục công trình</b> — kế hoạch nói <i>"thêm mới"</i>, rồi chỉ mục duy nhất ném
 *       {@code OPS-2008} <b>giữa lượt ghi</b>: giao dịch cuộn lại, người nhập mất <b>cả tệp</b> và
 *       ⛔ biết dòng nào hỏng.
 *   <li><b>Trạm bơm</b> — cùng đường ấy, hoặc tệ hơn: báo <i>"Không có công trình mã X"</i> về một
 *       mã <b>có thật</b>.
 *   <li><b>Vị trí điểm đo</b> — nặng nhất: nó nói <i>"Không có điểm đo mang mã F#####"</i> rồi dặn
 *       thêm rằng đường này ⛔ tạo mới được, nên người dùng đi tìm đường tạo và đâm vào
 *       {@code ux_stations_api_code}. Và đây là đường nhập <b>toạ độ G8</b> — quy tắc 18.
 * </ul>
 *
 * <h2>⛔ Khẳng định phải phân biệt được HAI trạng thái (luật 9)</h2>
 *
 * <p>Mỗi bài dưới đây đòi <b>cả hai</b>: (a) mã ngoài phạm vi ⇒ một dòng lỗi <b>chỉ đúng dòng ấy</b>
 * và nói ra <i>"ngoài phạm vi đơn vị của bạn"</i>; (b) một mã <b>⛔ tồn tại ở đâu cả</b> ⇒ <b>⛔</b>
 * mang câu ấy. Thiếu vế (b) thì một bản vá gắn câu ấy cho <i>mọi</i> mã ⛔ tra ra cũng xanh — và
 * khi đó hệ vừa nói dối vừa tiết lộ, cùng lúc.
 *
 * <p>⚠ Bài này ⛔ hỏi <i>"đơn vị nào đang giữ mã"</i>, vì câu trả lời ấy <b>cố ý</b> ⛔ được lộ ra
 * (quy tắc 5): thông báo khai <b>sự tồn tại</b>, ⛔ khai <b>danh tính</b>.
 */
class NhapTepMaNgoaiPhamViTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T81.4-";

    @Autowired
    private ConstructionImportService nhapCongTrinh;

    @Autowired
    private TramBomImportService nhapTramBom;

    @Autowired
    private StationLocationImportService nhapViTri;

    @Autowired
    private JdbcTemplate jdbc;

    private long xnAId;
    private long xnBId;
    private String pathA;

    @BeforeEach
    void setUp() {
        donDep();
        String pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        long rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        xnAId = themDonVi(TIEN_TO + "XN-A", rootId, pathRoot);
        xnBId = themDonVi(TIEN_TO + "XN-B", rootId, pathRoot);
        pathA = pathRoot + xnAId + "/";
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        donDep();
    }

    @Test
    @DisplayName("⛔⛔ Nhập danh mục công trình: mã của Xí nghiệp khác ⇒ MỘT dòng lỗi, ⛔ phải OPS-2008 giữa lượt ghi")
    void nhapCongTrinhBaoDongMaNgoaiPhamVi() {
        themCongTrinh(TIEN_TO + "CT-B", xnBId);
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        KetQuaNhap ngoaiPhamVi = nhapCongTrinh.preview(tepCongTrinh(TIEN_TO + "CT-B"));

        assertThat(ngoaiPhamVi.errors())
                .as("mã đang thuộc Xí nghiệp B phải sinh ĐÚNG một dòng lỗi ở bước lập kế hoạch")
                .hasSize(1);
        assertThat(ngoaiPhamVi.errors().get(0).message())
                .as("thông báo phải nói ra rằng mã ĐÃ BỊ CHIẾM, ⛔ để người nhập tưởng là thêm mới")
                .contains("ngoài phạm vi");
        assertThat(ngoaiPhamVi.toCreate())
                .as("⛔ được xếp nó vào nhóm 'thêm mới' — đó chính là đường đâm vào chỉ mục duy nhất")
                .isZero();

        // ⛔ Vế phân biệt (luật 9): một mã CHƯA AI dùng phải đi tiếp bình thường. Thiếu vế này thì
        //    một bản vá gắn câu 'ngoài phạm vi' cho MỌI mã ⛔ tra ra cũng xanh.
        KetQuaNhap maMoi = nhapCongTrinh.preview(tepCongTrinh(TIEN_TO + "CT-MOI"));
        assertThat(maMoi.errors())
                .as("mã hoàn toàn mới ⛔ được coi là ngoài phạm vi")
                .isEmpty();
        assertThat(maMoi.toCreate()).isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Nhập trạm bơm: mã của Xí nghiệp khác ⇒ nói *ngoài phạm vi*, ⛔ nói *⛔ có công trình mã X*")
    void nhapTramBomBaoDongMaNgoaiPhamVi() {
        themCongTrinh(TIEN_TO + "TB-B", xnBId);
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        KetQuaNhap ngoaiPhamVi = nhapTramBom.preview(tepTramBom(TIEN_TO + "TB-B"));

        assertThat(ngoaiPhamVi.errors()).hasSize(1);
        assertThat(ngoaiPhamVi.errors().get(0).message())
                .as("⛔ được khai *Không có công trình mã X* về một mã CÓ THẬT — đó là một lời nói dối "
                        + "dẫn người vận hành đi tạo trùng")
                .contains("ngoài phạm vi")
                .doesNotContain("Không có công trình");

        KetQuaNhap maMoi = nhapTramBom.preview(tepTramBom(TIEN_TO + "TB-MOI"));
        assertThat(maMoi.errors())
                .as("mã mới + đủ tên + đủ đơn vị ⇒ dựng trạm mới, ⛔ dính câu 'ngoài phạm vi'")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔⛔ Nhập vị trí điểm đo (G8): mã của Xí nghiệp khác ⇒ *ngoài phạm vi*, ⛔ phải *⛔ có điểm đo*")
    void nhapViTriBaoDungTrangThai() {
        themDiemDo(TIEN_TO + "DO-B", "F88002", xnBId);
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        KetQuaNhap ngoaiPhamVi = nhapViTri.preview(tepViTri("F88002"));

        assertThat(ngoaiPhamVi.errors()).hasSize(1);
        assertThat(ngoaiPhamVi.errors().get(0).message())
                .as("điểm đo CÓ THẬT mà báo *Không có điểm đo* là sai, và câu sai ấy còn dặn người dùng "
                        + "đi tìm đường tạo mới — đường ⛔ tồn tại")
                .contains("ngoài phạm vi")
                .doesNotContain("Không có điểm đo");

        // ⛔ Vế phân biệt: mã ⛔ tồn tại ở BẤT KỲ đơn vị nào vẫn phải nhận đúng câu cũ.
        KetQuaNhap khongTonTai = nhapViTri.preview(tepViTri("F88999"));
        assertThat(khongTonTai.errors()).hasSize(1);
        assertThat(khongTonTai.errors().get(0).message())
                .as("mã ⛔ có ở đâu cả thì ⛔ được nói *ngoài phạm vi* — nói thế là tiết lộ một thứ ⛔ tồn tại")
                .contains("Không có điểm đo")
                .doesNotContain("ngoài phạm vi");
    }

    // =========================================================================

    private byte[] tepCongTrinh(String ma) {
        return ("ma_cong_trinh,ten_cong_trinh,loai_cong_trinh,ma_don_vi\n" + ma + ",Công trình kiểm thử T81.4,Cống,"
                        + TIEN_TO + "XN-A\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] tepTramBom(String ma) {
        return ("ma_cong_trinh,ten_cong_trinh,ma_don_vi,so_may,q_mot_may_m3h\n" + ma + ",Trạm bơm kiểm thử T81.4,"
                        + TIEN_TO + "XN-A,2,1000\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] tepViTri(String maApi) {
        return ("ma_api,tuyen_song\n" + maApi + ",Sông Nhuệ\n").getBytes(StandardCharsets.UTF_8);
    }

    private void themCongTrinh(String ma, long donVi) {
        jdbc.update(
                "INSERT INTO constructions (public_id, code, name, construction_type, management_level, "
                        + "org_unit_id, lifecycle_state, operational_status, created_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'CONG', 'XI_NGHIEP', ?, 'DANG_HOAT_DONG', "
                        + "'BINH_THUONG', now())",
                ma,
                ma,
                donVi);
    }

    private void themDiemDo(String ma, String maApi, long donVi) {
        Long nguonId = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE code = 'BHH40' AND deleted_at IS NULL", Long.class);
        jdbc.update(
                "INSERT INTO stations (code, name, api_code, api_source_id, position_role, org_unit_id, created_at) "
                        + "VALUES (?, ?, ?, ?, 'MN_SONG', ?, now())",
                ma,
                ma,
                maApi,
                nguonId,
                donVi);
    }

    private long themDonVi(String ma, Long chaId, String chaPath) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                ma,
                chaId);
        String path = chaPath + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                996L,
                UUID.randomUUID(),
                "t81.4-probe",
                "Người kiểm thử T81.4",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null);
    }

    private void donDep() {
        jdbc.update("DELETE FROM nhom_may_bom WHERE construction_id IN "
                + "(SELECT id FROM constructions WHERE code LIKE '" + TIEN_TO + "%')");
        jdbc.update("DELETE FROM constructions WHERE code LIKE '" + TIEN_TO + "%'");
        jdbc.update("DELETE FROM stations WHERE code LIKE '" + TIEN_TO + "%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE '" + TIEN_TO + "%'");
    }
}
