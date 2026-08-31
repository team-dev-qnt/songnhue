package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Tình hình vận hành công bố ra cổng — CN-02.11, đấu nối 31/08/2026.
 *
 * <h2>⛔ Vì sao lớp này cần bài kiểm hơn hẳn một service thường</h2>
 *
 * Nó nằm sau {@code /api/v1/public/constructions/operation-statuses}: <b>không có tầng phân quyền
 * nào phía sau</b>, và bộ lọc phạm vi đơn vị <i>không bật</i> vì không có ai đăng nhập. Bảng
 * {@code construction_operation_status} vốn thuộc phạm vi đơn vị (lọc tầng 3), nên mở nó ra công
 * khai là một <b>quyết định về phạm vi công bố</b> — và mọi ranh giới của quyết định ấy chỉ tồn tại
 * ở đúng lớp này.
 */
@ExtendWith(MockitoExtension.class)
class PublicOperationStatusServiceTest {

    @Mock
    private ConstructionRepository constructions;

    @Mock
    private ConstructionOperationStatusRepository statuses;

    @Mock
    private OrgUnitPort orgUnits;

    @InjectMocks
    private PublicOperationStatusService service;

    /**
     * Đặt trường của {@code BaseEntity} bằng phản chiếu.
     *
     * <p>{@code id} và {@code updatedAt} <b>cố ý không có setter công khai</b>: một cái do CSDL cấp,
     * một cái do {@code @LastModifiedDate} ghi. Bài kiểm cần chúng để phân biệt hai công trình và để
     * khẳng định cột "Cập nhật lần cuối", nên nó vào bằng phản chiếu — chứ không phải bằng cách mở
     * một setter mà mã nghiệp vụ rồi sẽ dùng.
     */
    private static void datTruongCoSo(Object thucThe, String ten, Object giaTri) {
        try {
            java.lang.reflect.Field f = com.songnhue.core.common.persistence.BaseEntity.class.getDeclaredField(ten);
            f.setAccessible(true);
            f.set(thucThe, giaTri);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Không đặt được trường " + ten + " cho bài kiểm", e);
        }
    }

    private static Construction congTrinh(long id, String ma, String ten, long donVi, LifecycleState vongDoi) {
        Construction c = new Construction();
        datTruongCoSo(c, "id", id);
        c.setCode(ma);
        c.setName(ten);
        c.setConstructionType(ConstructionType.CONG);
        c.setOrgUnitId(donVi);
        c.setLifecycleState(vongDoi);
        return c;
    }

    private static OperationStatusCode ma(String code, String ten, boolean coThamSo, String donVi, String mauHex) {
        OperationStatusCode m = new OperationStatusCode();
        m.setCode(code);
        m.setName(ten);
        m.setHasParameter(coThamSo);
        m.setParameterUnit(donVi);
        m.setColorHex(mauHex);
        return m;
    }

    private static ConstructionOperationStatus banGhi(long congTrinhId, OperationStatusCode maTt, BigDecimal thamSo) {
        ConstructionOperationStatus s = new ConstructionOperationStatus();
        s.setConstructionId(congTrinhId);
        s.setOperationCode(maTt);
        s.setParameterValue(thamSo);
        s.setEffectiveAt(OffsetDateTime.parse("2026-08-31T03:00:00Z"));
        datTruongCoSo(s, "updatedAt", Instant.parse("2026-08-31T03:05:00Z"));
        return s;
    }

    private static OrgUnitRef xiNghiep(long id, String maXn, String ten) {
        return new OrgUnitRef(id, UUID.randomUUID(), maXn, ten, ten, "XI_NGHIEP", "/1/" + id + "/", 1);
    }

    @Test
    @DisplayName("⭐ Công bố bản ghi của MỌI Xí nghiệp — đường công khai không có bộ lọc phạm vi")
    void congBoBanGhiCuaMoiXiNghiep() {
        OperationStatusCode mt = ma("MT", "Mở treo", true, "m", "#1a7f37");
        OperationStatusCode dk = ma("ĐK", "Đóng kín", false, null, "#b42318");
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(
                        congTrinh(1L, "C-01", "Cống A", 10L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh(2L, "C-02", "Cống B", 20L, LifecycleState.DANG_HOAT_DONG)));
        when(statuses.banGhiMoiNhat(1L)).thenReturn(Optional.of(banGhi(1L, mt, new BigDecimal("2.35"))));
        when(statuses.banGhiMoiNhat(2L)).thenReturn(Optional.of(banGhi(2L, dk, null)));
        when(orgUnits.findRefsByIds(anyCollection()))
                .thenReturn(
                        Map.of(10L, xiNghiep(10L, "XN-A", "Xí nghiệp A"), 20L, xiNghiep(20L, "XN-B", "Xí nghiệp B")));

        List<PublicOperationStatusService.OperationStatusRow> ket = service.hienHanh();

        assertThat(ket)
                .as(
                        """
                        Hai công trình thuộc HAI Xí nghiệp khác nhau, và cả hai phải ra cổng. Đây là điều \
                        §6 của văn bản nghiệm thu yêu cầu — nhưng nó có nghĩa là mọi ranh giới công bố \
                        phải nằm trong chính lớp này, không ở tầng phân quyền.""")
                .extracting(PublicOperationStatusService.OperationStatusRow::unitName)
                .containsExactly("Xí nghiệp A", "Xí nghiệp B");
    }

    @Test
    @DisplayName("⛔ Mã KHÔNG mang tham số: trả null, không quy về 0 và không kèm đơn vị")
    void maKhongThamSoTraNull() {
        OperationStatusCode dk = ma("ĐK", "Đóng kín", false, "m", "#b42318");
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(congTrinh(1L, "C-01", "Cống A", 10L, LifecycleState.DANG_HOAT_DONG)));
        // Giá trị tham số vẫn nằm trong bảng — bản ghi cũ, hoặc người nhập điền rồi đổi mã.
        when(statuses.banGhiMoiNhat(1L)).thenReturn(Optional.of(banGhi(1L, dk, new BigDecimal("9.99"))));
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of(10L, xiNghiep(10L, "XN-A", "Xí nghiệp A")));

        PublicOperationStatusService.OperationStatusRow dong =
                service.hienHanh().get(0);

        assertThat(dong.parameterValue())
                .as(
                        """
                        Quy tắc 16: số 0 là một câu khẳng định, và "9,99 m" cho một mã Đóng kín thì tệ hơn \
                        — nó là một con số CÓ THẬT trong CSDL nhưng vô nghĩa với mã đang hiển thị.""")
                .isNull();
        assertThat(dong.parameterUnit()).isNull();
    }

    @Test
    @DisplayName("⛔ Công trình đã thanh lý và công trình chưa có bản ghi nào đều KHÔNG ra cổng")
    void locVongDoiVaCongTrinhChuaGhiNhan() {
        OperationStatusCode mt = ma("MT", "Mở treo", true, "m", "#1a7f37");
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(
                        congTrinh(1L, "C-01", "Có ghi nhận", 10L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh(2L, "C-02", "Chưa ghi nhận", 10L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh(3L, "C-03", "Đã thanh lý", 10L, LifecycleState.DA_THANH_LY)));
        when(statuses.banGhiMoiNhat(1L)).thenReturn(Optional.of(banGhi(1L, mt, new BigDecimal("1.20"))));
        when(statuses.banGhiMoiNhat(2L)).thenReturn(Optional.empty());
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of(10L, xiNghiep(10L, "XN-A", "Xí nghiệp A")));

        assertThat(service.hienHanh())
                .as(
                        """
                        "Chưa ghi nhận" khác "mã rỗng": một dòng toàn dấu gạch trên cổng trông y hệt một \
                        dòng có dữ liệu bị mất. Và công trình đã thanh lý phải bị loại ở ĐÂY, cùng bộ lọc \
                        với PublicConstructionCatalogService — hai nơi lệch nhau là một công trình có tình \
                        hình vận hành mà không có trong danh mục.""")
                .extracting(PublicOperationStatusService.OperationStatusRow::constructionName)
                .containsExactly("Có ghi nhận");
    }

    @Test
    @DisplayName("Chưa công trình nào: trả rỗng, không gọi tra đơn vị")
    void chuaCoCongTrinhTraRong() {
        when(constructions.findByDeletedAtIsNull()).thenReturn(List.of());

        assertThat(service.hienHanh()).isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ DTO công khai KHÔNG có `note` và KHÔNG có người cập nhật — phạm vi công bố")
    void dtoKhongMangTruongNoiBo() {
        List<String> truong = Arrays.stream(PublicOperationStatusService.OperationStatusRow.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(truong)
                .as(
                        """
                        Bảng construction_operation_status có `note` (ghi chú giữa các ca trực) và người \
                        cập nhật. Hai trường ấy KHÔNG được công bố. Khẳng định ở đây thay vì tin vào lời \
                        dặn: một trường thêm vào record sẽ đi thẳng ra Internet mà không ai duyệt.""")
                .doesNotContain("note", "updatedBy", "createdBy", "updatedByName");

        // ⛔ Khẳng định về SỐ LƯỢNG — chống xanh-trên-tập-rỗng: nếu ai đó đổi tên record thì
        //    `getRecordComponents()` trả mảng rỗng và `doesNotContain` xanh trọn vẹn (§10.62).
        assertThat(truong).hasSize(10);
    }
}
