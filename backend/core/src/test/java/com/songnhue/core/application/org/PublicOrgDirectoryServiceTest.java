package com.songnhue.core.application.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitLeader;
import com.songnhue.core.domain.org.OrgUnitType;
import com.songnhue.core.infra.org.OrgUnitLeaderRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;

/**
 * Ba bộ dữ liệu tổ chức công bố ra cổng — CR-19, CR-24, CR-25, CR-26.
 *
 * <h2>⛔ Vì sao lớp này cần bài kiểm hơn hẳn một service thường</h2>
 *
 * Nó nằm sau {@code /api/v1/public/org-units/**}: <b>không có tầng phân quyền nào phía sau</b>, và
 * bộ lọc phạm vi đơn vị không bật vì không có ai đăng nhập. Mọi phép lọc "được công bố cái gì" chỉ
 * tồn tại ở đúng lớp này.
 *
 * <p>Ba bài đầu canh <b>phép lọc</b> (đơn vị tắt · dòng danh bạ tắt · chỉ Xí nghiệp), bài cuối canh
 * <b>bề mặt DTO</b> — thứ dễ trôi nhất khi ai đó thêm một cột vào {@code org_units}.
 */
@ExtendWith(MockitoExtension.class)
class PublicOrgDirectoryServiceTest {

    @Mock
    private OrgUnitRepository orgUnits;

    @Mock
    private OrgUnitLeaderRepository leaders;

    @InjectMocks
    private PublicOrgDirectoryService service;

    /**
     * Dựng một đơn vị cho bài kiểm.
     *
     * <p>⚠ {@code parentId}, {@code path} và {@code depth} <b>không có setter</b> — chúng là giá trị
     * dẫn xuất và chỉ {@link OrgUnitService} được đặt, qua {@code placeAt(path)}. Bài kiểm đi đúng
     * cửa ấy thay vì tìm cách lách: nếu một ngày quy tắc dẫn xuất đổi, bài kiểm đổi theo miễn phí.
     *
     * <p>Riêng {@code id} thì phải đặt bằng phản chiếu — nó do CSDL sinh, và service gom cây con
     * theo id nên bài kiểm bắt buộc phải có. Đây là chỗ DUY NHẤT dùng phản chiếu, và nó nằm trong
     * một hàm dựng của test chứ không rải ra từng bài.
     */
    private static OrgUnit donVi(long id, String ma, String ten, OrgUnitType loai, String path, boolean bat) {
        OrgUnit u = new OrgUnit(ma, ten, loai);
        u.placeAt(path);
        u.setSortOrder(0);
        u.setActive(bat);
        datId(u, id);
        return u;
    }

    private static void datId(Object entity, long id) {
        try {
            java.lang.reflect.Field truong =
                    com.songnhue.core.common.persistence.BaseEntity.class.getDeclaredField("id");
            truong.setAccessible(true);
            truong.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "không đặt được id cho " + entity.getClass().getSimpleName(), e);
        }
    }

    private static OrgUnitLeader nguoi(long donViId, String ten, String chucDanh, String dienThoai, boolean bat) {
        OrgUnitLeader l = new OrgUnitLeader();
        l.setOrgUnitId(donViId);
        l.setFullName(ten);
        l.setTitle(chucDanh);
        l.setPhone(dienThoai);
        l.setActive(bat);
        return l;
    }

    // ---- CR-24 · Sơ đồ cơ cấu tổ chức ---------------------------------------

    @Test
    @DisplayName("Dựng cây lồng nhau từ danh sách phẳng, và ĐƠN VỊ ĐÃ TẮT không ra cổng")
    void soDoToChucLocDonViTat() {
        when(orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc())
                .thenReturn(List.of(
                        donVi(1L, "CT", "Công ty", OrgUnitType.CONG_TY, "/1/", true),
                        donVi(2L, "P-KT", "Phòng Kỹ thuật", OrgUnitType.PHONG_BAN, "/1/2/", true),
                        donVi(3L, "XN-A", "Xí nghiệp A", OrgUnitType.XI_NGHIEP, "/1/3/", true),
                        donVi(4L, "XN-CU", "Xí nghiệp đã giải thể", OrgUnitType.XI_NGHIEP, "/1/4/", false)));

        List<PublicOrgDirectoryService.OrgChartNode> cay = service.orgChart();

        assertThat(cay).singleElement().satisfies(goc -> {
            assertThat(goc.name()).isEqualTo("Công ty");
            assertThat(goc.children())
                    .as("đơn vị `active = false` là đơn vị Công ty đã thôi công bố — nó không được lọt ra cổng")
                    .extracting(PublicOrgDirectoryService.OrgChartNode::name)
                    .containsExactly("Phòng Kỹ thuật", "Xí nghiệp A");
        });
    }

    @Test
    @DisplayName("Chưa nhập cơ cấu tổ chức thì trả rỗng — org_units cố ý không seed")
    void soDoToChucRong() {
        when(orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc()).thenReturn(List.of());

        assertThat(service.orgChart()).isEmpty();
    }

    // ---- CR-25 · Bảng Lãnh đạo Công ty --------------------------------------

    @Test
    @DisplayName("Lãnh đạo Công ty lấy từ NÚT GỐC, theo thứ tự Công ty tự sắp")
    void lanhDaoCongTy() {
        OrgUnit goc = donVi(1L, "CT", "Công ty", OrgUnitType.CONG_TY, "/1/", true);
        when(orgUnits.findFirstByParentIdIsNullAndDeletedAtIsNull()).thenReturn(Optional.of(goc));
        when(leaders.findByOrgUnitIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(
                        nguoi(1L, "Nguyễn Văn A", "Chủ tịch Công ty", "(024) 3111 1111", true),
                        nguoi(1L, "Trần Thị B", "Phó Giám đốc", null, true)));

        assertThat(service.companyLeaders())
                .containsExactly(
                        new PublicOrgDirectoryService.LeaderRow("Nguyễn Văn A", "Chủ tịch Công ty", "(024) 3111 1111"),
                        new PublicOrgDirectoryService.LeaderRow("Trần Thị B", "Phó Giám đốc", null));
    }

    @Test
    @DisplayName("⛔ Chưa có nút gốc ⇒ rỗng, KHÔNG nổ — hai trạng thái ấy giống nhau với người xem")
    void lanhDaoKhiChuaCoNutGoc() {
        when(orgUnits.findFirstByParentIdIsNullAndDeletedAtIsNull()).thenReturn(Optional.empty());

        assertThat(service.companyLeaders()).isEmpty();
    }

    // ---- CR-26 · Bảng Xí nghiệp trực thuộc ----------------------------------

    @Test
    @DisplayName("⛔ CHỈ đơn vị loại XI_NGHIEP; Giám đốc là dòng danh bạ ĐẦU TIÊN")
    void xiNghiepTrucThuoc() {
        OrgUnit xnA = donVi(3L, "XN-A", "Xí nghiệp A", OrgUnitType.XI_NGHIEP, "/1/3/", true);
        xnA.setAddress("Phường Hà Đông");
        xnA.setPhone("(024) 3222 2222");
        xnA.setEmail("xna@example.test");
        when(orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc())
                .thenReturn(List.of(
                        donVi(1L, "CT", "Công ty", OrgUnitType.CONG_TY, "/1/", true),
                        donVi(2L, "P-KT", "Phòng Kỹ thuật", OrgUnitType.PHONG_BAN, "/1/2/", true),
                        xnA,
                        donVi(4L, "XN-B", "Xí nghiệp B", OrgUnitType.XI_NGHIEP, "/1/4/", true)));
        when(leaders.findByOrgUnitIdInAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(anyList()))
                .thenReturn(List.of(
                        nguoi(3L, "Lê Văn C", "Giám đốc Xí nghiệp", "(024) 3333 3333", true),
                        nguoi(3L, "Phạm Thị D", "Phó Giám đốc Xí nghiệp", "(024) 3444 4444", true)));

        List<PublicOrgDirectoryService.SubsidiaryRow> bang = service.subsidiaries();

        assertThat(bang)
                .as("Công ty và phòng ban KHÔNG phải Xí nghiệp trực thuộc — bảng CR-26 chỉ có Xí nghiệp")
                .extracting(PublicOrgDirectoryService.SubsidiaryRow::name)
                .containsExactly("Xí nghiệp A", "Xí nghiệp B");

        assertThat(bang.get(0)).satisfies(a -> {
            assertThat(a.address()).isEqualTo("Phường Hà Đông");
            assertThat(a.email()).isEqualTo("xna@example.test");
            assertThat(a.directorName())
                    .as("`sort_order` là thứ tự Công ty tự sắp, nên dòng đầu là người họ muốn đứng đầu")
                    .isEqualTo("Lê Văn C");
            assertThat(a.directorPhone()).isEqualTo("(024) 3333 3333");
        });

        assertThat(bang.get(1)).satisfies(b -> {
            assertThat(b.directorName())
                    .as(
                            """
                            Xí nghiệp chưa có dòng danh bạ nào ⇒ hai cột cuối là null ⇒ cổng hiện dấu gạch. \
                            KHÔNG mượn tên của Xí nghiệp khác, và không để chuỗi rỗng trông như lỗi hiển \
                            thị (quy tắc 16).""")
                    .isNull();
            assertThat(b.directorPhone()).isNull();
        });
    }

    @Test
    @DisplayName("Chưa có Xí nghiệp nào thì trả rỗng và KHÔNG hỏi bảng danh bạ")
    void chuaCoXiNghiepNao() {
        when(orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc())
                .thenReturn(List.of(donVi(1L, "CT", "Công ty", OrgUnitType.CONG_TY, "/1/", true)));

        assertThat(service.subsidiaries()).isEmpty();
    }

    // ---- Bề mặt DTO — thứ dễ trôi nhất --------------------------------------

    @Test
    @DisplayName("⛔ DTO công khai KHÔNG mang materialized path hay khoá nội bộ")
    void dtoKhongLoKhoaNoiBo() {
        /*
          `org_units.path` là chuỗi id chạy số ("/1/4/9/"). Đưa nó ra một endpoint không đăng nhập
          là tặng không bản đồ khoá chính của bảng nền tảng nhất trong hệ (§4.2 chống IDOR), và
          `head_user_id` thì trỏ thẳng tới một tài khoản.

          Bài này canh ở tầng CẤU TRÚC (danh sách trường của record) chứ không canh giá trị: một
          giá trị `null` hôm nay không ngăn ai điền nó vào ngày mai, còn một trường không tồn tại
          thì không điền được. Cùng lý lẽ với ApiSurfaceRuleTest.
        */
        List<String> truongChart = java.util.Arrays.stream(
                        PublicOrgDirectoryService.OrgChartNode.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(truongChart).containsExactly("code", "name", "shortName", "unitType", "children");

        List<String> truongXn = java.util.Arrays.stream(
                        PublicOrgDirectoryService.SubsidiaryRow.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(truongXn)
                .as("đúng sáu cột của CR-26 cộng mã đơn vị — không hơn")
                .containsExactly(
                        "code", "name", "shortName", "address", "phone", "email", "directorName", "directorPhone");

        assertThat(truongChart).doesNotContain("path", "publicId", "headUserId", "deputyUserId", "id");
        assertThat(truongXn).doesNotContain("path", "publicId", "headUserId", "deputyUserId", "id");
    }
}
