package com.songnhue.hr.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.tree.TreeBuilder;
import com.songnhue.core.spi.OrgUnitLeaderRef;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitTreeRef;
import com.songnhue.hr.infra.QuanSoRepository;

/**
 * Sơ đồ tổ chức — CN-04.1 (SRS UC4.1).
 *
 * <h2>⛔⛔ HAI con số quân số trên mỗi nút, ⛔ không phải một</h2>
 *
 * <p>Đặc tả nói *"nút hiện tên đơn vị + người đứng đầu + <b>số lượng nhân sự</b>"* — một con số. Đo
 * lại thì <b>một</b> con số ⛔ không đủ, và cái thiếu hỏng theo chiều im lặng:
 *
 * <ul>
 *   <li>Chỉ <b>trực tiếp</b> ⇒ một Xí nghiệp có 4 Tổ đội hiện <b>0 người</b> khi thu gọn nhánh.
 *       Người xem kết luận đơn vị ấy trống.
 *   <li>Chỉ <b>cả nhánh</b> ⇒ tổng của các nút con ⛔ không bằng nút cha, và ⛔ không có chỗ nào
 *       nói ra vì sao. Người xem kết luận số liệu sai.
 * </ul>
 *
 * <p>⇒ Trả cả hai và <b>gọi tên</b> chúng. Quy tắc 3: cộng dồn tính ở BE — để giao diện tự cộng
 * đệ quy là dựng bản sao thứ hai của phép cộng, và bản ấy sẽ lệch vào ngày ai đó lọc bớt một nhánh
 * trước khi vẽ.
 *
 * <h2>⛔ Sơ đồ ⛔ KHÔNG cắt theo phạm vi đơn vị</h2>
 *
 * <p>Một sơ đồ tổ chức chỉ hiện nhánh của mình ⛔ không phải một sơ đồ tổ chức. Cùng quyết định với
 * CN-04.6 (danh bạ), và quân số lấy qua {@link QuanSoRepository} — JDBC thuần, ngoài tầm
 * {@code @Filter}. Xem javadoc lớp ấy để biết vì sao đó là quyết định về <b>phạm vi</b> chứ ⛔ không
 * phải một đường tắt.
 *
 * <h2>⛔ Đơn vị đã TẮT vẫn hiện, kèm nhãn</h2>
 *
 * <p>Bỏ lặng lẽ là làm một đơn vị biến khỏi sơ đồ trong khi hồ sơ vẫn trỏ vào nó — đúng triệu
 * chứng mà WS-56 phải dựng chốt chặn {@code ADM-2004} để ngăn.
 */
@Service
public class SoDoToChucService {

    private final OrgUnitPort orgUnits;
    private final QuanSoRepository quanSo;

    public SoDoToChucService(OrgUnitPort orgUnits, QuanSoRepository quanSo) {
        this.orgUnits = orgUnits;
        this.quanSo = quanSo;
    }

    /**
     * @param soNhanSuTrucTiep người có {@code org_unit_id} khớp ĐÚNG nút này
     * @param soNhanSuCaNhanh cộng dồn cả cây con — <b>bằng</b> trực tiếp khi nút ⛔ không có con
     * @param dangDung {@code false} = đơn vị đã tắt; giao diện phải hiện nhãn, ⛔ không được ẩn
     */
    public record Nut(
            UUID publicId,
            String code,
            String name,
            String shortName,
            String unitType,
            int depth,
            boolean dangDung,
            List<LanhDao> lanhDao,
            long soNhanSuTrucTiep,
            long soNhanSuCaNhanh,
            List<Nut> con) {}

    /** ⚠ Cây lãnh đạo trong một nút là <b>PHẲNG</b> — T50.6: {@code title} là ô tự do, ⛔ không phải thang bậc. */
    public record LanhDao(String hoTen, String chucDanh) {}

    /**
     * @param soNhanSuNgoaiSoDo bình thường là <b>0</b>. Khác 0 ⇒ có hồ sơ trỏ vào một đơn vị ⛔
     *     không còn trên sơ đồ — xem {@link QuanSoRepository#demNgoaiSoDo(List)}
     */
    public record SoDo(List<Nut> goc, long tongNhanSu, long soNhanSuNgoaiSoDo, int soDonVi) {}

    @Transactional(readOnly = true)
    public SoDo dung() {
        List<OrgUnitTreeRef> phang = orgUnits.cayPhang();
        if (phang.isEmpty()) {
            // ⛔ Rỗng là một trạng thái HỢP LỆ và phải nói ra đúng như vậy (quy tắc 16) — ⛔ không
            //   bịa một nút gốc để sơ đồ "trông có gì".
            return new SoDo(List.of(), 0, quanSo.demNgoaiSoDo(List.of()), 0);
        }

        Map<Long, Long> trucTiep = quanSo.demTheoDonVi();

        List<Nut> goc = TreeBuilder.build(
                phang,
                r -> r.donVi().id(),
                OrgUnitTreeRef::parentId,
                Comparator.comparingInt(OrgUnitTreeRef::sortOrder)
                        .thenComparing(r -> r.donVi().name()),
                (r, con) -> toNut(r, con, trucTiep.getOrDefault(r.donVi().id(), 0L)));

        List<Long> idTrenSoDo = phang.stream().map(r -> r.donVi().id()).toList();
        long tong = goc.stream().mapToLong(Nut::soNhanSuCaNhanh).sum();
        return new SoDo(goc, tong, quanSo.demNgoaiSoDo(idTrenSoDo), phang.size());
    }

    private static Nut toNut(OrgUnitTreeRef r, List<Nut> con, long trucTiep) {
        long caNhanh = trucTiep + con.stream().mapToLong(Nut::soNhanSuCaNhanh).sum();
        List<LanhDao> lanhDao = new ArrayList<>();
        for (OrgUnitLeaderRef l : r.lanhDao()) {
            lanhDao.add(new LanhDao(l.fullName(), l.title()));
        }
        return new Nut(
                r.donVi().publicId(),
                r.donVi().code(),
                r.donVi().name(),
                r.donVi().shortName(),
                r.donVi().unitType(),
                r.donVi().depth(),
                r.active(),
                List.copyOf(lanhDao),
                trucTiep,
                caNhanh,
                con);
    }
}
