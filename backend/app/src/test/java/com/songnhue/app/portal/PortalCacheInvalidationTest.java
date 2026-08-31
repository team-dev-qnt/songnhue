package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.PortalCache;
import com.songnhue.core.application.org.OrgUnitService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitType;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.operations.api.dto.OperationStatusBatchCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusBatchItemRequest;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionOperationStatusService;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.ManagementLevel;

/**
 * <b>Bộ đệm cổng có thật sự bị xoá khi dữ liệu ngoài module {@code content} đổi không</b> — T27.8.
 *
 * <h2>Bài kiểm này phải ĐO, không được KHẲNG ĐỊNH</h2>
 *
 * <p>T25.22 nằm treo vì một lý do đã đo được (§10.62): {@code PortalCache} sống ở {@code content},
 * còn {@code org_units} ở {@code core} và {@code constructions} ở {@code operations} — quy tắc 6 cấm
 * gọi chéo. Hệ quả với người dùng thật: <b>màn hình báo "lưu thành công", cổng không đổi gì</b> cho
 * tới hết chu kỳ ISR 5 phút, nên người nhập tưởng mình lưu hỏng và nhập lại.
 *
 * <p>{@link PortalCachePort} đảo chiều phụ thuộc để chữa việc đó. Nhưng một cổng <i>tồn tại</i> chưa
 * phải là một cổng <i>được gọi</i>: đây đúng hình dạng luật 27 (nửa cặp đọc–ghi) và luật 15 (cơ chế
 * chưa ai đi qua). Vì vậy mọi khẳng định dưới đây là một <b>con số đếm được</b> — số dòng
 * {@code jobs} mang {@code job_type = 'CMS_PORTAL_REVALIDATE'} trước và sau một lượt sửa — chứ không
 * phải một mock ghi nhận đã có người gọi. Mock chỉ chứng minh dây được nối trong bài kiểm; hàng đợi
 * việc là thứ production thật sự đọc.
 *
 * <h2>⚠ Vì sao phải dọn hàng đợi trước mỗi phép đo</h2>
 *
 * <p>{@code PortalCache.datViec} truyền {@code dedupKey}, và hàng đợi gộp hai việc cùng khoá khi
 * việc cũ <b>chưa kết thúc</b>. Không dọn thì lượt sửa thứ hai đúng ra phải đặt việc lại đo được 0
 * dòng mới — và ta sẽ đi kết luận rằng lời gọi bị thiếu, tức là bài kiểm nói dối theo hướng ngược
 * lại.
 */
class PortalCacheInvalidationTest extends IntegrationTestBase {

    @Autowired
    private OrgUnitService orgUnits;

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private ConstructionOperationStatusService operationStatuses;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * ⚠ Tiêm theo <b>kiểu SPI</b>, không theo lớp cài đặt.
     *
     * <p>Nếu Spring không tìm được bean nào cài {@link PortalCachePort} thì cả lớp này đổ vỡ lúc dựng
     * context — đó là điều mong muốn. Một SPI khai ra mà không có cài đặt là một cơ chế chết đúng
     * kiểu §10.62, và nó phải nổ ở đây chứ không phải im lặng ở production.
     */
    @Autowired
    private PortalCachePort portalCache;

    private UUID donViGoc;

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        donDep();
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // === Cài đặt SPI có thật và đúng nhà ======================================

    @Test
    @DisplayName("SPI PortalCachePort được cài bởi PortalCache của module content")
    void theSpiIsImplementedByTheModuleThatOwnsTheCache() {
        assertThat(portalCache)
                .as(
                        """
                        Hợp đồng ở core.spi, cài đặt ở module sở hữu cơ chế — đúng khuôn HydroAlertPort. \
                        Cài đặt nằm ở nơi khác nghĩa là bộ đệm cổng có hai chủ.""")
                .isInstanceOf(PortalCache.class);
    }

    // === ⭐ Phép đo: sửa dữ liệu ngoài `content` thì hàng đợi phải có việc mới ==

    @Test
    @DisplayName("⭐ Thêm đơn vị → ĐẾM ĐƯỢC việc dựng lại cổng mang nhãn `to-chuc`")
    void creatingAnOrgUnitEnqueuesARevalidateJob() {
        donDepHangDoi();
        int truoc = soViecDungLaiCong();

        orgUnits.create("T278-XN-01", "Xí nghiệp T27.8", OrgUnitType.XI_NGHIEP, donViGoc, null, null, null, null);

        int sau = soViecDungLaiCong();
        assertThat(sau)
                .as(
                        """
                        ⛔ Đây là con số, không phải lời hứa. Bằng nhau nghĩa là OrgUnitService không gọi \
                        portalCache — sơ đồ tổ chức trên cổng đứng yên tới 5 phút sau khi người dùng bấm \
                        Lưu (§10.62). Trước: %d, sau: %d.""",
                        truoc, sau)
                .isGreaterThan(truoc);
        assertThat(nhungViecDungLaiCong())
                .as("nhãn `to-chuc` lo ba trang /gioi-thieu/*; thiếu nó thì xoá nhầm chỗ cũng như không xoá")
                .anyMatch(payload -> payload.contains(PortalCache.TAG_TO_CHUC));
        assertThat(nhungViecDungLaiCong())
                .as(
                        """
                        ⚠ Và phải có CẢ đường dẫn trang chủ: §10.17 đo được rằng một lượt fetch hỏng thì \
                        không mục cache nào mang nhãn được tạo ra, nên revalidateTag không có gì để lần \
                        ngược — trang chủ là trang duy nhất từng ra đời rỗng sau một lượt triển khai.""")
                .anyMatch(payload -> payload.contains("\"path\""));
    }

    @Test
    @DisplayName("⭐ Sửa đơn vị → ĐẾM ĐƯỢC việc dựng lại cổng")
    void updatingAnOrgUnitEnqueuesARevalidateJob() {
        OrgUnit donVi = orgUnits.create(
                "T278-XN-02", "Xí nghiệp T27.8 hai", OrgUnitType.XI_NGHIEP, donViGoc, null, null, null, null);

        donDepHangDoi();
        int truoc = soViecDungLaiCong();

        orgUnits.update(donVi.getPublicId(), "Tên đã đổi trên cổng", null, OrgUnitType.XI_NGHIEP, null, null, null);

        assertThat(soViecDungLaiCong())
                .as("đường SỬA cũng phải xoá đệm — thêm thì xoá mà sửa thì không là cái bẫy khó thấy hơn")
                .isGreaterThan(truoc);
    }

    @Test
    @DisplayName("⭐ Thêm công trình → ĐẾM ĐƯỢC việc dựng lại cổng mang nhãn `cong-trinh`")
    void creatingAConstructionEnqueuesARevalidateJob() {
        donDepHangDoi();
        int truoc = soViecDungLaiCong();

        constructions.create(hoSo("T278-CT-01", "Trạm bơm T27.8"));

        assertThat(soViecDungLaiCong())
                .as(
                        """
                        Danh mục công trình và khối Vận hành công trình đều đọc qua đệm cổng. Trước: %d.""",
                        truoc)
                .isGreaterThan(truoc);
        assertThat(nhungViecDungLaiCong()).anyMatch(payload -> payload.contains(PortalCache.TAG_CONG_TRINH));
    }

    @Test
    @DisplayName("⭐⭐ Ghi tình hình vận hành → ĐẾM ĐƯỢC việc dựng lại cổng (dữ liệu này lên cổng từ T27.16)")
    void recordingAnOperationStatusEnqueuesARevalidateJob() {
        UUID congTrinh = constructions.create(hoSo("T278-CT-02", "Cống T27.8")).getPublicId();
        donDepHangDoi();
        int truoc = soViecDungLaiCong();

        operationStatuses.batchCreate(motDong(congTrinh));

        assertThat(soViecDungLaiCong())
                .as(
                        """
                        ⚠⚠ Đây là đường ghi CHẠM CỔNG CÔNG KHAI kể từ T27.16/T27.17 — bản ghi mới nhất \
                        đi thẳng ra khối "Vận hành công trình" trên trang chủ. Trước 01/09 nó KHÔNG gọi \
                        portalCache: trực ban bấm Lưu, cổng vẫn hiện mã cũ tới 5 phút — đúng nguyên văn \
                        triệu chứng §10.62 mà T27.7 vừa đi trả nợ, tái phát ở một đường ghi khác. \
                        Trước: %d.""",
                        truoc)
                .isGreaterThan(truoc);
        assertThat(nhungViecDungLaiCong()).anyMatch(payload -> payload.contains(PortalCache.TAG_CONG_TRINH));
    }

    // === Vế chống xanh-trên-tập-rỗng ==========================================

    @Test
    @DisplayName("⚠ Phép đếm KHÔNG đứng yên: hàng đợi rỗng đo ra 0, và nó tăng đúng khi có lượt gọi")
    void theCounterItselfIsTrustworthy() {
        donDepHangDoi();
        assertThat(soViecDungLaiCong())
                .as(
                        """
                        Nếu hàm đếm luôn trả một số > 0 (đếm nhầm bảng, nhầm job_type) thì mọi khẳng định \
                        isGreaterThan ở trên xanh mà chẳng chứng minh gì — luật 7.""")
                .isZero();

        portalCache.orgUnitsChanged();

        assertThat(soViecDungLaiCong())
                .as("gọi thẳng SPI phải làm con số nhúc nhích — nếu không thì phép đo mù, không phải mã sai")
                .isPositive();
    }

    // -------------------------------------------------------------------------

    private int soViecDungLaiCong() {
        return jdbc.queryForObject("SELECT count(*) FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", Integer.class);
    }

    private List<String> nhungViecDungLaiCong() {
        return jdbc.queryForList(
                "SELECT payload::text FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", String.class);
    }

    /** Xem javadoc lớp: {@code dedup_key} gộp việc chưa kết thúc, nên mỗi phép đo bắt đầu từ hàng đợi sạch. */
    private void donDepHangDoi() {
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
    }

    private void donDep() {
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T278-CT-%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T278-XN-%'");
    }

    /** Một dòng "Đóng kín" — mã seed, không mang tham số. */
    private OperationStatusBatchCreateRequest motDong(UUID congTrinh) {
        OperationStatusBatchItemRequest item = new OperationStatusBatchItemRequest();
        item.setConstructionPublicId(congTrinh);
        item.setOperationCode("ĐK");
        item.setEffectiveAt(OffsetDateTime.parse("2026-08-30T08:00:00+07:00"));
        OperationStatusBatchCreateRequest request = new OperationStatusBatchCreateRequest();
        request.setItems(List.of(item));
        return request;
    }

    private ConstructionForm hoSo(String ma, String ten) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.TRAM_BOM,
                null,
                donViGoc,
                ManagementLevel.XI_NGHIEP,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
