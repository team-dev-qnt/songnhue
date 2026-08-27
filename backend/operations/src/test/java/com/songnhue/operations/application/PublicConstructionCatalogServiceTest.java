package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Danh mục công trình công bố ra cổng — CR-27, CR-28.
 *
 * <h2>⛔ Vì sao lớp này cần bài kiểm hơn hẳn một service thường</h2>
 *
 * Nó nằm sau {@code /api/v1/public/constructions}: <b>không có tầng phân quyền nào phía sau</b>,
 * và bộ lọc phạm vi đơn vị <i>không bật</i> vì không có ai đăng nhập. Nghĩa là mọi phép lọc "được
 * công bố cái gì" chỉ tồn tại ở đúng lớp này — quên một phép lọc là công bố thẳng ra Internet, và
 * không có gì báo.
 */
@ExtendWith(MockitoExtension.class)
class PublicConstructionCatalogServiceTest {

    @Mock
    private ConstructionRepository constructions;

    @Mock
    private OrgUnitPort orgUnits;

    @InjectMocks
    private PublicConstructionCatalogService service;

    private static Construction congTrinh(String ma, String ten, long donVi, LifecycleState vongDoi) {
        Construction c = new Construction();
        c.setCode(ma);
        c.setName(ten);
        c.setConstructionType(ConstructionType.TRAM_BOM);
        c.setOrgUnitId(donVi);
        c.setLifecycleState(vongDoi);
        return c;
    }

    private static OrgUnitRef xiNghiep(long id, String ma, String ten) {
        return new OrgUnitRef(id, UUID.randomUUID(), ma, ten, ten, "XI_NGHIEP", "/1/" + id + "/", 1);
    }

    @Test
    @DisplayName("Gom theo Xí nghiệp quản lý, sắp theo tên đơn vị — CR-27")
    void gomTheoXiNghiep() {
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(
                        congTrinh("TB-02", "Trạm bơm B", 2L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("TB-01", "Trạm bơm A", 1L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("TB-03", "Trạm bơm C", 1L, LifecycleState.DANG_HOAT_DONG)));
        when(orgUnits.findRefsByIds(anyCollection()))
                .thenReturn(Map.of(1L, xiNghiep(1L, "XN-A", "Xí nghiệp A"), 2L, xiNghiep(2L, "XN-B", "Xí nghiệp B")));

        List<PublicConstructionCatalogService.UnitCatalog> ket = service.catalogByUnit();

        assertThat(ket)
                .extracting(PublicConstructionCatalogService.UnitCatalog::unitName)
                .containsExactly("Xí nghiệp A", "Xí nghiệp B");
        assertThat(ket.get(0).constructions())
                .as("trong một đơn vị, công trình sắp theo tên")
                .extracting(PublicConstructionCatalogService.CatalogRow::name)
                .containsExactly("Trạm bơm A", "Trạm bơm C");
    }

    @Test
    @DisplayName("⛔ Công trình đã thanh lý KHÔNG ra cổng; ngừng theo mùa vụ thì CÓ")
    void locVongDoi() {
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(
                        congTrinh("TB-01", "Đang hoạt động", 1L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("TB-02", "Ngừng mùa vụ", 1L, LifecycleState.NGUNG_MUA_VU),
                        congTrinh("TB-03", "Đã thanh lý", 1L, LifecycleState.DA_THANH_LY)));
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of(1L, xiNghiep(1L, "XN-A", "Xí nghiệp A")));

        assertThat(service.catalogByUnit())
                .singleElement()
                .extracting(PublicConstructionCatalogService.UnitCatalog::constructions)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(
                        PublicConstructionCatalogService.CatalogRow.class))
                .as(
                        """
                        Danh mục công trình là bảng giới thiệu năng lực, không phải sổ tài sản: công trình \
                        đã thanh lý không còn phục vụ ai. Còn NGUNG_MUA_VU thì vẫn thuộc quản lý và sẽ vận \
                        hành lại — loại nó đi là nói với người dân rằng trạm bơm ấy không tồn tại.""")
                .extracting(PublicConstructionCatalogService.CatalogRow::name)
                .containsExactly("Đang hoạt động", "Ngừng mùa vụ");
    }

    @Test
    @DisplayName("⭐ Sắp tên theo TIẾNG VIỆT — 'Đ' nằm sau 'D', không phải sau 'N'")
    void sapTenTheoTiengViet() {
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(
                        congTrinh("C-04", "Em", 1L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("C-01", "Anh", 1L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("C-03", "Đăng", 1L, LifecycleState.DANG_HOAT_DONG),
                        congTrinh("C-02", "Dung", 1L, LifecycleState.DANG_HOAT_DONG)));
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of(1L, xiNghiep(1L, "XN-A", "Xí nghiệp A")));

        assertThat(service.catalogByUnit().get(0).constructions())
                .as(
                        """
                        Đúng bộ bốn tên mà T11.3-b dùng để đo collation của cluster staging.                         `String.CASE_INSENSITIVE_ORDER` so theo đơn vị mã UTF-16 nên cho                         Anh < Dung < Em < Đăng — 'Đ' (U+0110) rơi xuống tận cuối. Bản đầu của lớp này                         dùng đúng bộ so sánh ấy, và cái sai chỉ lộ ra khi có một tên bắt đầu bằng Đ.""")
                .extracting(PublicConstructionCatalogService.CatalogRow::name)
                .containsExactly("Anh", "Dung", "Đăng", "Em");
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: bộ so sánh cũ THẬT SỰ xếp sai bộ tên đó")
    void kiemChungNguocBoSoSanhCu() {
        List<String> theoUtf16 = java.util.stream.Stream.of("Em", "Anh", "Đăng", "Dung")
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        assertThat(theoUtf16)
                .as("không có khẳng định này thì bài trên chỉ chứng minh 'kết quả đúng', không chứng minh lỗi có thật")
                .containsExactly("Anh", "Dung", "Em", "Đăng");
    }

    @Test
    @DisplayName("⛔ Công trình mất đơn vị quản lý vẫn hiện, dưới nhãn nói rõ — không biến mất im lặng")
    void congTrinhKhongTraDuocDonVi() {
        when(constructions.findByDeletedAtIsNull())
                .thenReturn(List.of(congTrinh("TB-09", "Trạm bơm mồ côi", 99L, LifecycleState.DANG_HOAT_DONG)));
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of());

        assertThat(service.catalogByUnit()).singleElement().satisfies(nhom -> {
            assertThat(nhom.unitName()).isEqualTo("Chưa phân đơn vị quản lý");
            assertThat(nhom.unitCode()).isNull();
            assertThat(nhom.constructions()).hasSize(1);
        });
    }

    @Test
    @DisplayName("Chưa có công trình nào thì trả rỗng — G8 chưa đóng, và rỗng là câu trả lời đúng")
    void chuaCoCongTrinhNao() {
        when(constructions.findByDeletedAtIsNull()).thenReturn(List.of());

        assertThat(service.catalogByUnit()).isEmpty();
    }

    // ---- Cột "Thông tin chủ yếu" — quy tắc 16 --------------------------------

    @Test
    @DisplayName("⭐ 'Số máy × Lưu lượng' chỉ dựng khi CÓ ĐỦ hai vế")
    void thongTinChuYeuDuHaiVe() {
        Construction c = congTrinh("TB-01", "A", 1L, LifecycleState.DANG_HOAT_DONG);
        c.setPumpCount((short) 4);
        c.setFlowPerPumpM3s(new BigDecimal("1.500"));

        assertThat(PublicConstructionCatalogService.thongTinChuYeu(c))
                .as("số thập phân bỏ số 0 thừa — '1.500' đọc như một độ chính xác không có thật")
                .isEqualTo("4 máy × 1.5 m³/s");
    }

    @Test
    @DisplayName("⛔ Thiếu MỘT vế thì trả null, không ghép một nửa và không điền số 0")
    void thongTinChuYeuThieuMotVe() {
        Construction thieuLuuLuong = congTrinh("TB-01", "A", 1L, LifecycleState.DANG_HOAT_DONG);
        thieuLuuLuong.setPumpCount((short) 4);

        Construction thieuSoMay = congTrinh("TB-02", "B", 1L, LifecycleState.DANG_HOAT_DONG);
        thieuSoMay.setFlowPerPumpM3s(new BigDecimal("1.5"));

        Construction rong = congTrinh("TB-03", "C", 1L, LifecycleState.DANG_HOAT_DONG);

        assertThat(PublicConstructionCatalogService.thongTinChuYeu(thieuLuuLuong))
                .as(
                        """
                        Quy tắc 16: số 0 là một câu khẳng định. "4 máy × " vừa sai vừa TRÔNG NHƯ đã có dữ \
                        liệu, còn "0 máy" và "chưa nhập số máy" là hai chuyện khác hẳn nhau trên hồ sơ một \
                        trạm bơm. Ràng buộc ép ở chỗ dữ liệu đi qua, không ở nơi hiển thị (quy tắc 12).""")
                .isNull();
        assertThat(PublicConstructionCatalogService.thongTinChuYeu(thieuSoMay)).isNull();
        assertThat(PublicConstructionCatalogService.thongTinChuYeu(rong)).isNull();
    }

    @Test
    @DisplayName("⛔ Hai tệp Quyết định và toạ độ đi ra nguyên vẹn; chưa có thì là null, không phải chuỗi rỗng")
    void haiTepQuyetDinhVaToaDo() {
        UUID quyTrinh = UUID.randomUUID();
        Construction coTep = congTrinh("TB-01", "A", 1L, LifecycleState.DANG_HOAT_DONG);
        coTep.setOperatingProcedureAttachmentPublicId(quyTrinh);
        coTep.datToaDo(new BigDecimal("20.950000"), new BigDecimal("105.780000"));

        when(constructions.findByDeletedAtIsNull()).thenReturn(List.of(coTep));
        when(orgUnits.findRefsByIds(anyCollection())).thenReturn(Map.of(1L, xiNghiep(1L, "XN-A", "Xí nghiệp A")));

        PublicConstructionCatalogService.CatalogRow dong =
                service.catalogByUnit().get(0).constructions().get(0);

        assertThat(dong.operatingProcedureFileId()).isEqualTo(quyTrinh);
        assertThat(dong.protectionPlanFileId())
                .as("chưa có tệp Phương án bảo vệ ⇒ null ⇒ cổng hiện dấu gạch, KHÔNG dựng liên kết rỗng")
                .isNull();
        assertThat(dong.latitude()).isEqualByComparingTo("20.950000");
        assertThat(dong.longitude()).isEqualByComparingTo("105.780000");
    }
}
