package com.songnhue.core.application.org;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitLeader;
import com.songnhue.core.domain.org.OrgUnitType;
import com.songnhue.core.infra.org.OrgUnitLeaderRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;

/**
 * Ba bộ dữ liệu tổ chức mà cổng thông tin điện tử công bố — CR-19, CR-24, CR-25, CR-26.
 *
 * <h2>Vì sao tách hẳn khỏi {@link OrgUnitService}</h2>
 *
 * <p>{@code OrgUnitService} phục vụ màn hình quản trị: nó trả {@code path} (chứa id chạy số),
 * {@code publicId}, cờ {@code active}, và có cả nhánh ghi. Cổng công khai không được thấy thứ nào
 * trong đó. Dùng lại DTO của quản trị cho một endpoint không đăng nhập là cách rẻ nhất để một cột
 * mới thêm vào bảng lặng lẽ trở thành thông tin công bố — chính là điều
 * {@code LayeringTest.ENTITY_KHONG_NAM_TRONG_CHU_KY_ENDPOINT} sinh ra để chặn, ở một tầng cao hơn.
 *
 * <p>Nên lớp này có <b>DTO riêng, dựng bằng tay, liệt kê từng trường</b>. Thêm một cột vào
 * {@code org_units} không tự động lộ nó ra cổng; phải sửa ở đây, và lượt sửa ấy nhìn thấy được.
 *
 * <h2>Không có tầng phân quyền nào phía sau</h2>
 *
 * <p>Ba phương thức dưới đây chạy khi <b>không có ai đăng nhập</b>, nên bộ lọc phạm vi đơn vị
 * không bật ({@code ScopeFilterAspect} chỉ bật khi có {@code AuthContext}). Điều đó đúng ở đây —
 * §6 của tài liệu chỉnh sửa xếp toàn bộ mục "Giới thiệu" vào nhóm *Tất cả người dùng* — nhưng nó
 * có nghĩa là <b>phép lọc "được công bố cái gì" phải nằm trong chính truy vấn</b>: đơn vị đã tắt
 * và dòng danh bạ đã tắt bị loại ngay ở đây, không phải ở nơi hiển thị.
 */
@Service
public class PublicOrgDirectoryService {

    private final OrgUnitRepository orgUnits;
    private final OrgUnitLeaderRepository leaders;

    public PublicOrgDirectoryService(OrgUnitRepository orgUnits, OrgUnitLeaderRepository leaders) {
        this.orgUnits = orgUnits;
        this.leaders = leaders;
    }

    /**
     * Một nút của sơ đồ tổ chức công bố — CR-24.
     *
     * <p>⛔ Cố ý <b>không</b> có {@code path}: materialized path là chuỗi id chạy số
     * ({@code /1/4/9/}), và đưa nó ra một endpoint không đăng nhập là tặng không bản đồ khoá chính
     * của bảng nền tảng nhất trong hệ (§4.2). Cấu trúc lồng đã đủ để vẽ cây.
     */
    public record OrgChartNode(
            String code, String name, String shortName, String unitType, List<OrgChartNode> children) {}

    /** Một dòng bảng "Lãnh đạo Công ty" — đúng ba cột của CR-25. */
    public record LeaderRow(String fullName, String title, String phone) {}

    /** Một dòng bảng "Xí nghiệp trực thuộc" — đúng sáu cột của CR-26. */
    public record SubsidiaryRow(
            String code,
            String name,
            String shortName,
            String address,
            String phone,
            String email,
            String directorName,
            String directorPhone) {}

    /**
     * Sơ đồ cây cơ cấu tổ chức — CR-24.
     *
     * @return danh sách nút gốc; rỗng khi chưa ai nhập cơ cấu tổ chức. Rỗng là câu trả lời hợp lệ
     *     và nơi hiển thị phải nói thẳng là chưa có — {@code V202608131008} cố ý không seed
     *     {@code org_units} vì đó là dữ liệu chịu tải, đoán sai rồi sửa là phải di chuyển mọi thứ
     *     đã bám vào nó.
     */
    @Transactional(readOnly = true)
    public List<OrgChartNode> orgChart() {
        List<OrgUnit> tatCa = orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc().stream()
                .filter(OrgUnit::isActive)
                .toList();

        Map<Long, List<OrgUnit>> theoCha = tatCa.stream()
                .filter(u -> u.getParentId() != null)
                .collect(Collectors.groupingBy(OrgUnit::getParentId));

        return tatCa.stream()
                .filter(u -> u.getParentId() == null)
                .map(goc -> dungNut(goc, theoCha))
                .toList();
    }

    private OrgChartNode dungNut(OrgUnit unit, Map<Long, List<OrgUnit>> theoCha) {
        List<OrgChartNode> con = theoCha.getOrDefault(unit.getId(), List.of()).stream()
                .sorted(Comparator.comparing(OrgUnit::getSortOrder).thenComparing(OrgUnit::getId))
                .map(c -> dungNut(c, theoCha))
                .toList();
        return new OrgChartNode(
                unit.getCode(),
                unit.getName(),
                unit.getShortName(),
                unit.getUnitType().name(),
                con);
    }

    /**
     * Bảng "Lãnh đạo Công ty" — CR-25.
     *
     * <p>Đọc danh bạ của nút gốc ({@link OrgUnitType#CONG_TY}). Chưa có nút gốc, hoặc có mà chưa
     * nhập dòng nào, đều trả danh sách rỗng — hai trạng thái ấy giống nhau với người xem, và tách
     * chúng ra chỉ tạo một nhánh thứ hai mà không ai kiểm (luật 7).
     */
    @Transactional(readOnly = true)
    public List<LeaderRow> companyLeaders() {
        return orgUnits.findFirstByParentIdIsNullAndDeletedAtIsNull()
                .map(goc ->
                        leaders
                                .findByOrgUnitIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(goc.getId())
                                .stream()
                                .map(l -> new LeaderRow(l.getFullName(), l.getTitle(), l.getPhone()))
                                .toList())
                .orElseGet(List::of);
    }

    /**
     * Bảng "Xí nghiệp trực thuộc" — CR-26, và cũng là nguồn cho khối "Đơn vị trực thuộc" ở trang
     * chủ (CR-19).
     *
     * <p>Cột "Giám đốc Xí nghiệp" lấy <b>dòng danh bạ đầu tiên</b> của Xí nghiệp ấy. Đó là quy ước
     * chứ không phải suy đoán: {@code sort_order} là thứ tự Công ty tự sắp trên màn hình quản trị,
     * nên dòng đầu là người họ muốn đứng đầu. Xí nghiệp chưa có dòng nào thì hai cột cuối trả
     * {@code null} và cổng hiện dấu gạch — không mượn tên của ai khác (quy tắc 16).
     */
    @Transactional(readOnly = true)
    public List<SubsidiaryRow> subsidiaries() {
        List<OrgUnit> xiNghiep = orgUnits.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc().stream()
                .filter(OrgUnit::isActive)
                .filter(u -> u.getUnitType() == OrgUnitType.XI_NGHIEP)
                .toList();

        if (xiNghiep.isEmpty()) {
            return List.of();
        }

        // Một lượt hỏi cho mọi Xí nghiệp. Hỏi trong vòng lặp là N+1 truy vấn cho một bảng chỉ có
        // vài chục dòng — không đau ngay, nhưng là hình dạng sẽ đau khi có người sao chép nó.
        Map<Long, List<OrgUnitLeader>> danhBa = leaders
                .findByOrgUnitIdInAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                        xiNghiep.stream().map(OrgUnit::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrgUnitLeader::getOrgUnitId));

        List<SubsidiaryRow> ket = new ArrayList<>(xiNghiep.size());
        for (OrgUnit xn : xiNghiep) {
            List<OrgUnitLeader> cua = danhBa.getOrDefault(xn.getId(), List.of());
            OrgUnitLeader giamDoc = cua.isEmpty() ? null : cua.get(0);
            ket.add(new SubsidiaryRow(
                    xn.getCode(),
                    xn.getName(),
                    xn.getShortName(),
                    xn.getAddress(),
                    xn.getPhone(),
                    xn.getEmail(),
                    giamDoc == null ? null : giamDoc.getFullName(),
                    giamDoc == null ? null : giamDoc.getPhone()));
        }
        return ket;
    }
}
