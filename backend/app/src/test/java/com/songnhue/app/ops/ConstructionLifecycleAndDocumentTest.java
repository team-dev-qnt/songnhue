package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.operations.application.ConstructionClusterService;
import com.songnhue.operations.application.ConstructionDocumentService;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionCluster;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Vòng đời, trạng thái dẫn xuất, cụm công trình và tài liệu — T17.6, T17.7, T17.11.
 *
 * <p>Chạy trên MinIO thật (xem {@code SongnhueMinio}): từ WS-14 trở đi mọi lượt tải tệp trong test đi
 * tới kho lưu trữ thật, vì trước đó địa chỉ kho là {@code minio.invalid} và <b>chưa một lượt tải nào
 * chạm ra ngoài</b> suốt Phase 0.
 */
class ConstructionLifecycleAndDocumentTest extends IntegrationTestBase {

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private ConstructionClusterService clusters;

    @Autowired
    private ConstructionDocumentService documents;

    @Autowired
    private JdbcTemplate jdbc;

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

    // === Trạng thái dẫn xuất (T17.6) =========================================

    @Test
    @DisplayName("⛔ Không có setter công khai nào ghi được operational_status")
    void thereIsNoPublicWritePathForStatus() {
        // Canh CẤU TRÚC chứ không canh văn bản: một setter công khai xuất hiện ở đây là mở đường thứ
        // hai vào cột dẫn xuất, và đường thứ hai sẽ ghi đè đường thứ nhất theo thứ tự ngẫu nhiên.
        boolean coSetter = false;
        for (Method m : Construction.class.getMethods()) {
            if (m.getName().equals("setOperationalStatus")) {
                coSetter = true;
            }
        }
        assertThat(coSetter)
                .as("trạng thái vận hành là giá trị dẫn xuất — chỉ ConstructionStatusService được ghi")
                .isFalse();
    }

    @Test
    @DisplayName("Vòng đời quyết định trạng thái: thanh lý → DA_THANH_LY, mở lại → BINH_THUONG")
    void lifecycleDrivesDerivedStatus() {
        UUID id = constructions.create(hoSo("T17L-001", "Trạm bơm vòng đời")).getPublicId();
        assertThat(constructions.get(id).getOperationalStatus()).isEqualTo(OperationalStatus.BINH_THUONG);

        constructions.changeLifecycle(id, LifecycleState.NGUNG_MUA_VU, "hết vụ chiêm");
        assertThat(constructions.get(id).getOperationalStatus()).isEqualTo(OperationalStatus.NGUNG_MUA_VU);

        constructions.changeLifecycle(id, LifecycleState.DA_THANH_LY, "hư hỏng không sửa được");
        assertThat(constructions.get(id).getOperationalStatus()).isEqualTo(OperationalStatus.DA_THANH_LY);

        constructions.changeLifecycle(id, LifecycleState.DANG_HOAT_DONG, "đã đầu tư lại");
        assertThat(constructions.get(id).getOperationalStatus())
                .as("mở lại phải quay về chuỗi suy ra bình thường, không kẹt ở trạng thái cũ")
                .isEqualTo(OperationalStatus.BINH_THUONG);
    }

    // === Mã gợi ý (T17.3) ====================================================

    @Test
    @DisplayName("Mã gợi ý chạy tiếp số lớn nhất đang có, không bắt đầu lại từ 001")
    void codeSuggestionContinuesFromTheHighest() {
        assertThat(constructions.suggestCode(ConstructionType.TRAM_BOM, donViGoc))
                .endsWith("-001");

        constructions.create(hoSo("TB-CTY-007", "Trạm bơm bảy"));

        assertThat(constructions.suggestCode(ConstructionType.TRAM_BOM, donViGoc))
                .as("gợi ý trùng mã đang dùng thì người nhập chỉ biết khi bấm Lưu và nhận OPS-2008")
                .isEqualTo("TB-CTY-008");
    }

    // === Cụm công trình (T17.11) =============================================

    @Test
    @DisplayName("⛔ Xoá cụm còn công trình bên trong → OPS-2012, không tự gỡ liên kết")
    void clusterWithConstructionsCannotBeDeleted() {
        ConstructionCluster cum = clusters.create("T17C-01", "Cụm kiểm thử", donViGoc, null, 0);
        constructions.create(hoSoCoCum("T17L-010", "Trạm bơm trong cụm", cum.getPublicId()));

        assertThatThrownBy(() -> clusters.delete(cum.getPublicId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OPS-2012");
    }

    @Test
    @DisplayName("Cụm rỗng thì xoá được")
    void emptyClusterIsDeletable() {
        ConstructionCluster cum = clusters.create("T17C-02", "Cụm rỗng", donViGoc, null, 0);

        clusters.delete(cum.getPublicId());

        assertThat(clusters.list()).noneMatch(c -> c.getCode().equals("T17C-02"));
    }

    // === Tài liệu công trình (T17.7) =========================================

    @Test
    @DisplayName("⭐ Tải tài liệu lên MinIO thật, kèm ngày lập và ngày hết hiệu lực")
    void documentUploadStoresValidityDates() {
        UUID ct = constructions.create(hoSo("T17L-020", "Trạm bơm có hồ sơ")).getPublicId();

        AttachmentRef ref = documents.upload(
                ct,
                "Hồ sơ hoàn công",
                "hoan-cong.png",
                anhPng(),
                LocalDate.of(2018, 5, 20),
                LocalDate.of(2030, 12, 31));

        // ⚠ Hai cột valid_from/valid_until có trong lược đồ từ WS-2 mà CHƯA TỪNG có đường ghi nào —
        // AttachmentUploadCommand không mang chúng, entity có setter nhưng không ai gọi. WS-17 mở
        // đường đó; bài kiểm này là thứ chứng minh nó thật sự ghi xuống.
        assertThat(ref.validFrom()).isEqualTo(LocalDate.of(2018, 5, 20));
        assertThat(ref.validUntil()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThat(documents.list(ct)).hasSize(1);
        assertThat(documents.usedBytes(ct)).isPositive();
    }

    @Test
    @DisplayName("Tải lại cùng loại tài liệu → phiên bản 2, bản cũ vẫn còn")
    void reuploadCreatesANewVersion() {
        UUID ct = constructions.create(hoSo("T17L-021", "Trạm bơm nhiều bản")).getPublicId();

        documents.upload(ct, "Quy trình vận hành", "qt-v1.png", anhPng(), null, null);
        AttachmentRef v2 = documents.upload(ct, "Quy trình vận hành", "qt-v2.png", anhPng(), null, null);

        assertThat(v2.fileVersion()).isEqualTo(2);
        assertThat(documents.list(ct))
                .as("bản cũ phải còn — hồ sơ công trình là tài liệu pháp lý, không ghi đè")
                .hasSize(2);
    }

    @Test
    @DisplayName("⛔ Tệp của công trình khác không tải được qua đường của công trình này (IDOR)")
    void documentOfAnotherConstructionIsNotReachable() {
        UUID ctA = constructions.create(hoSo("T17L-030", "Công trình A")).getPublicId();
        UUID ctB = constructions.create(hoSo("T17L-031", "Công trình B")).getPublicId();

        AttachmentRef cuaB = documents.upload(ctB, "Bản vẽ", "ban-ve.png", anhPng(), null, null);

        // Kiểm quyền ở tham số thứ nhất mà không ràng buộc tham số thứ hai là một lỗ IDOR trông y
        // hệt mã đúng — và `attachments` là bảng DÙNG CHUNG, nên nó mở luôn cả hồ sơ nhân sự.
        assertThatThrownBy(() -> documents.downloadUrl(ctA, cuaB.publicId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> documents.delete(ctA, cuaB.publicId())).isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------

    private ConstructionForm hoSo(String ma, String ten) {
        return hoSoCoCum(ma, ten, null);
    }

    private ConstructionForm hoSoCoCum(String ma, String ten, UUID cum) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.TRAM_BOM,
                null,
                donViGoc,
                ManagementLevel.XI_NGHIEP,
                cum,
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
                // Hai cột tài liệu công bố (CR-28) — bài kiểm này không dùng tới.
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Ảnh PNG thật — magic bytes phải là thật, không phải mấy byte bịa cho qua chuyện. */
    private static byte[] anhPng() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    private void donDep() {
        jdbc.update("DELETE FROM attachments WHERE owner_type = 'CONSTRUCTION' AND owner_id IN "
                + "(SELECT id FROM constructions WHERE code LIKE 'T17L-%' OR code LIKE 'TB-CTY-%')");
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T17L-%' OR code LIKE 'TB-CTY-%'");
        jdbc.update("DELETE FROM construction_clusters WHERE code LIKE 'T17C-%'");
    }
}
