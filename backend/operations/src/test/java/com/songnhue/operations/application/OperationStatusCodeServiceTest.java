package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.operations.api.dto.OperationStatusCodeCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusCodeUpdateRequest;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

@ExtendWith(MockitoExtension.class)
class OperationStatusCodeServiceTest {

    private static final UUID PUBLIC_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private OperationStatusCodeRepository repository;

    @Mock
    private ConstructionOperationStatusRepository statusRepository;

    @Mock
    private ConstructionStatusService constructionStatusService;

    /**
     * ⚠ Thiếu {@code @Mock} này thì {@code @InjectMocks} tiêm {@code null} — và bài kiểm sẽ đỏ vì
     * {@code NullPointerException}, không vì luật nghiệp vụ nào. Đó là cách rẻ nhất để biết đường
     * ghi đã thật sự chạm cổng chứ không chỉ khai một trường.
     */
    @Mock
    private PortalCachePort portalCache;

    @InjectMocks
    private OperationStatusCodeService service;

    @Test
    void createTrungMaNenNemOPS2005() {
        OperationStatusCodeCreateRequest request = new OperationStatusCodeCreateRequest();
        request.setCode("MT");

        when(repository.existsByCodeAndDeletedAtIsNull("MT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPS_2005);
    }

    @Test
    void deleteDaDuocSuDungNenNemOPS2007() {
        OperationStatusCode entity = ma("MT", null, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));
        when(statusRepository.existsByOperationCodeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(PUBLIC_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPS_2007);
    }

    @Test
    void deleteChuaSuDungChiAnDi() {
        OperationStatusCode entity = ma("MT", null, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));
        when(statusRepository.existsByOperationCodeId(1L)).thenReturn(false);

        service.delete(PUBLIC_ID);

        assertThat(entity.getDeletedAt()).isNotNull();
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("⛔ Mã đã xoá mềm không tra lại được — thiếu điều kiện này thì nó sống dậy khi bị sửa")
    void aSoftDeletedCodeCannotBeResurrected() {
        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(PUBLIC_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("⭐ Đổi ánh xạ mã → tính lại trạng thái nhúm công trình đang mang mã đó")
    void changingTheMappingRecomputesAffectedConstructions() {
        OperationStatusCode entity = ma("ĐK", OperationalStatus.NGUNG_MUA_VU, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));
        when(statusRepository.findConstructionIdsWithLatestCode(1L)).thenReturn(List.of(7L, 9L));

        service.update(PUBLIC_ID, yeuCauSua("ĐK", OperationalStatus.SU_CO, true));

        verify(constructionStatusService).recomputeFor(7L);
        verify(constructionStatusService).recomputeFor(9L);
    }

    @Test
    @DisplayName("⭐⭐ So sánh ánh xạ phải xảy ra TRƯỚC khi ghi đè — đọc sau thì hai vế luôn bằng nhau")
    void theComparisonHappensBeforeTheFieldsAreOverwritten() {
        OperationStatusCode entity = ma("ĐK", OperationalStatus.NGUNG_MUA_VU, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));

        // Không đổi gì cả → không được tính lại. Nếu phép so sánh đọc entity SAU khi đã set thì
        // nhánh này cũng không chạy, nên bài kiểm trên mới là bài phân biệt được hai cách viết.
        service.update(PUBLIC_ID, yeuCauSua("ĐK", OperationalStatus.NGUNG_MUA_VU, true));

        verify(statusRepository, never()).findConstructionIdsWithLatestCode(1L);
        verify(constructionStatusService, never()).recomputeFor(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("⭐ Ẩn một mã cũng phải tính lại — 'ẩn' đổi cách công trình được hiển thị")
    void hidingACodeAlsoRecomputes() {
        OperationStatusCode entity = ma("ĐK", OperationalStatus.NGUNG_MUA_VU, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));
        when(statusRepository.findConstructionIdsWithLatestCode(1L)).thenReturn(List.of(3L));

        service.update(PUBLIC_ID, yeuCauSua("ĐK", OperationalStatus.NGUNG_MUA_VU, false));

        verify(constructionStatusService).recomputeFor(3L);
    }

    @Test
    @DisplayName("⭐⭐ V1 — chỉ đổi TÊN (ánh xạ và cờ ẩn giữ nguyên) vẫn phải bảo cổng dựng lại")
    void renamingACodeStillInvalidatesThePortalCache() {
        OperationStatusCode entity = ma("ĐK", OperationalStatus.NGUNG_MUA_VU, true);

        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));

        OperationStatusCodeUpdateRequest doiTen = yeuCauSua("ĐK", OperationalStatus.NGUNG_MUA_VU, true);
        doiTen.setName("Đóng kín hoàn toàn");

        service.update(PUBLIC_ID, doiTen);

        // ⭐ Đây là bài PHÂN BIỆT ĐƯỢC HAI CÁCH VIẾT (luật 9): lượt sửa này để cả `anhXaDoi` lẫn
        //   `anAnDoi` ở `false`, nên một lời gọi gói trong nhánh ấy sẽ KHÔNG chạy — và nó là lượt
        //   sửa thường gặp nhất. `verify(never()).recomputeFor` giữ vế còn lại: xoá đệm cổng ⛔
        //   không được kéo theo một vòng tính lại trạng thái mà không ai cần.
        verify(portalCache).constructionsChanged();
        verify(constructionStatusService, never()).recomputeFor(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("⛔ Thêm và xoá mã KHÔNG bảo cổng dựng lại — cổng chỉ thấy mã đang có người dùng")
    void creatingAndDeletingDoNotTouchThePortal() {
        OperationStatusCodeCreateRequest them = new OperationStatusCodeCreateRequest();
        them.setCode("XX");
        them.setName("Mã mới");
        them.setColorHex("#123456");
        them.setSortOrder(10);
        them.setActive(true);
        when(repository.existsByCodeAndDeletedAtIsNull("XX")).thenReturn(false);

        service.create(them);

        OperationStatusCode entity = ma("YY", null, true);
        when(repository.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(Optional.of(entity));
        when(statusRepository.existsByOperationCodeId(1L)).thenReturn(false);

        service.delete(PUBLIC_ID);

        // ⛔ Khẳng định NGƯỢC, có chủ đích — nó khoá lập luận trong javadoc lớp lại thành một phép
        //   kiểm: mã vừa tạo chưa ai mang, mã xoá được là mã không ai mang (OPS-2007 chặn phần còn
        //   lại), nên cả hai đường ⛔ không đổi được một ô nào trên cổng. Ngày nào cổng công bố cả
        //   danh mục thì bài này đỏ — và đỏ đúng chỗ cần đọc lại.
        verify(portalCache, never()).constructionsChanged();
    }

    @Test
    @DisplayName("⛔ Danh sách quản trị KHÔNG được gọi findAll() trần — câu đó gồm cả mã đã xoá mềm")
    void theAdminListExcludesSoftDeletedRows() {
        when(repository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()).thenReturn(List.of());

        service.findAll();

        verify(repository).findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();
        verify(repository, never()).findAll();
    }

    // -------------------------------------------------------------------------

    private static OperationStatusCode ma(String code, OperationalStatus anhXa, boolean dangDung) {
        OperationStatusCode entity = new OperationStatusCode();
        ReflectionTestUtils.setField(entity, "id", 1L);
        entity.setPublicId(PUBLIC_ID);
        entity.setCode(code);
        entity.setName("Tên " + code);
        entity.setColorHex("#123456");
        entity.setMappedStatus(anhXa);
        entity.setActive(dangDung);
        return entity;
    }

    private static OperationStatusCodeUpdateRequest yeuCauSua(String code, OperationalStatus anhXa, boolean dangDung) {
        OperationStatusCodeUpdateRequest request = new OperationStatusCodeUpdateRequest();
        request.setCode(code);
        request.setName("Tên " + code);
        request.setColorHex("#123456");
        request.setMappedStatus(anhXa);
        request.setSortOrder(10);
        request.setActive(dangDung);
        return request;
    }
}
