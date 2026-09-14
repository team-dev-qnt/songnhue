package com.songnhue.hr.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.export.BangCsv;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmployeeEventType;
import com.songnhue.hr.domain.MaBaoCaoNhanSu;
import com.songnhue.hr.infra.EmployeeEventRepository;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Thống kê và báo cáo nhân sự — CN-04.8 (SRS M4.14–M4.17, UC4.6).
 *
 * <h2>⛔⛔ Màn hình này CẮT theo phạm vi đơn vị — ngược với danh bạ và sơ đồ tổ chức</h2>
 *
 * <p>Bốn chức năng đọc cùng bảng {@code employees} với <b>hai luật ngược nhau</b>, và biết mình
 * đang ở vế nào là điều kiện để ⛔ không hỏng:
 *
 * <table>
 *   <tr><th>Chức năng</th><th>Phạm vi</th><th>Vì sao</th></tr>
 *   <tr><td>CN-04.6 danh bạ</td><td>toàn Công ty</td><td>một cuốn danh bạ chỉ thấy đơn vị mình là vô dụng</td></tr>
 *   <tr><td>CN-04.1 sơ đồ</td><td>toàn Công ty</td><td>một sơ đồ tổ chức chỉ hiện nhánh mình ⛔ không phải sơ đồ</td></tr>
 *   <tr><td>CN-04.7 hồ sơ</td><td><b>cắt</b></td><td>M4.13: *"Quản lý cấp XN chỉ xem hồ sơ NV thuộc đơn vị mình"*</td></tr>
 *   <tr><td><b>CN-04.8 báo cáo</b></td><td><b>cắt</b></td><td>báo cáo là <i>đầu ra của hồ sơ</i> — rộng hơn nguồn là một đường vòng qua M4.13</td></tr>
 * </table>
 *
 * <p>⇒ Lớp này đọc qua <b>JPA</b> ({@code EmployeeRepository}), tức đi qua {@code @Filter} phạm vi.
 * ⛔ Cố ý <b>không</b> dùng {@code QuanSoRepository} (JDBC thuần) — lớp ấy sinh ra cho sơ đồ tổ chức
 * và dùng lại ở đây là mở một đường đọc toàn Công ty cho một màn hình phải bị cắt.
 *
 * <p>⚠ Hệ quả phải KHAI RA trên giao diện: hai người ở hai đơn vị mở cùng màn hình thấy hai bộ số
 * khác nhau, và cả hai đều <b>đúng</b>. Im lặng ở đây là để họ so số rồi kết luận hệ thống sai.
 *
 * <h2>⛔ CSV, ⛔ không phải XLSX — quyết định T34.8 đã chốt</h2>
 *
 * <p>Apache POI kéo theo ~12 MB phụ thuộc và một bề mặt CVE mới, trên một VPS 2 nhân. Excel mở CSV
 * được, và {@link BangCsv} đã mang sẵn ba quy ước để bản tiếng Việt đọc đúng (BOM · dấu tách
 * {@code ;} · dấu thập phân {@code ,}).
 *
 * <p>⬜ <b>PDF thì vẫn CHƯA</b> — nợ T42.14, và nó ⛔ không phải chuyện chọn thư viện: bố cục bản
 * in của các báo cáo này chờ <b>G10</b>. Chọn thư viện trước khi có tệp mẫu là chọn trước khi biết
 * khổ giấy, cách gộp ô và phông tiếng Việt.
 */
@Service
public class BaoCaoNhanSuService {

    /** Mốc chia nhóm tuổi — ⛔ không phải một thang bậc nghiệp vụ, chỉ để biểu đồ đọc được. */
    private static final int[] MOC_TUOI = {30, 40, 50};

    private final EmployeeRepository employees;
    private final EmployeeEventRepository suKien;
    private final OrgUnitPort orgUnits;
    private final CanhBaoHetHanService canhBao;

    public BaoCaoNhanSuService(
            EmployeeRepository employees,
            EmployeeEventRepository suKien,
            OrgUnitPort orgUnits,
            CanhBaoHetHanService canhBao) {
        this.employees = employees;
        this.suKien = suKien;
        this.orgUnits = orgUnits;
        this.canhBao = canhBao;
    }

    // =========================================================================
    // Tổng quan: KPI + dữ liệu ba biểu đồ
    // =========================================================================

    /**
     * @param tyLeNghiViec phần trăm, <b>chuỗi</b> — {@code NUMERIC}, cấm float (quy tắc 2)
     * @param soThangCoDuLieu ⚠ {@code 0} ⇒ mọi tỉ lệ ở trên là <b>chưa biết</b>, ⛔ không phải 0%
     */
    public record Kpi(
            long tongDangLamViec,
            long tuyenMoiThangNay,
            long nghiViecThangNay,
            String tyLeNghiViec,
            int nguongNgayHopDong,
            long hopDongSapHetHan,
            int nguongNgayChungChi,
            long chungChiSapHetHan,
            int soThangCoDuLieu) {}

    /** Một cột/lát của biểu đồ. {@code khoa} là mã máy đọc, {@code nhan} là chữ cho người. */
    public record MucDem(String khoa, String nhan, long soLuong) {}

    /**
     * @param thang dạng {@code yyyy-MM}
     * @param dieuDong gồm cả điều động và bổ nhiệm/miễn nhiệm — hai loại sự kiện, một câu hỏi
     */
    public record BienDongThang(String thang, long tuyenMoi, long nghiViec, long dieuDong) {}

    public record TongQuan(
            Kpi kpi,
            List<MucDem> theoDonVi,
            List<MucDem> theoGioiTinh,
            List<MucDem> theoHocVan,
            List<MucDem> theoNhomTuoi,
            List<BienDongThang> bienDong) {}

    @Transactional(readOnly = true)
    public TongQuan tongQuan() {
        List<Employee> tatCa = employees.findByDeletedAtIsNull();
        List<Employee> dangLam = tatCa.stream()
                .filter(e -> e.getStatus() == null || !e.getStatus().daNghi())
                .toList();

        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
        YearMonth thangNay = YearMonth.from(homNay);

        long tuyenMoi = tatCa.stream()
                .filter(e ->
                        e.getHiredAt() != null && YearMonth.from(e.getHiredAt()).equals(thangNay))
                .count();
        long nghiViec = tatCa.stream()
                .filter(e -> e.getTerminatedAt() != null
                        && YearMonth.from(e.getTerminatedAt()).equals(thangNay))
                .count();

        CanhBaoHetHanService.KetQua canh = canhBao.canhBao();

        Kpi kpi = new Kpi(
                dangLam.size(),
                tuyenMoi,
                nghiViec,
                tyLe(nghiViec, dangLam.size()),
                canh.nguongNgayHopDong(),
                canh.hopDong().size(),
                canh.nguongNgayChungChi(),
                canh.chungChi().size(),
                // ⛔⛔ Số tháng hệ THẬT SỰ có dữ liệu. `0` ⇒ mọi tỉ lệ ở trên là **chưa biết**, ⛔
                //    không phải 0% — quy tắc 16, và đây là năm đầu vận hành nên nó sẽ nhỏ.
                soThangCoDuLieu(tatCa));

        return new TongQuan(
                kpi,
                theoDonVi(dangLam),
                dem(dangLam, e -> e.getGender() == null ? null : e.getGender().name()),
                dem(
                        dangLam,
                        e -> e.getEducationLevel() == null
                                ? null
                                : e.getEducationLevel().name()),
                theoNhomTuoi(dangLam, homNay),
                bienDong12Thang(tatCa, thangNay));
    }

    // =========================================================================
    // Kết xuất CSV
    // =========================================================================

    public record TepXuat(String tenTep, byte[] noiDung) {}

    /**
     * @throws IllegalStateException khi mã báo cáo <b>chưa dựng được</b> — kèm nguyên văn lý do của
     *     {@link MaBaoCaoNhanSu#lyDoChuaCo()}, vì đó là câu người vận hành cần đọc
     */
    @Transactional(readOnly = true)
    public TepXuat xuat(MaBaoCaoNhanSu ma) {
        if (!ma.khaDung()) {
            throw new IllegalStateException(ma.lyDoChuaCo());
        }
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
        BangCsv bang =
                switch (ma) {
                    case BCNS_01 -> trichNgang();
                    case BCNS_02 -> quanSoTheoDonVi();
                    case BCNS_03 -> bienDongCsv(YearMonth.from(homNay));
                    case BCNS_04 -> hopDongSapHetHan();
                    case BCNS_05 -> coCau(homNay);
                    case BCNS_06 -> chungChiSapHetHan();
                    case BCNS_08 -> tongHopNam(homNay.getYear());
                    default ->
                        throw new IllegalStateException(
                                "⛔ Mã " + ma.ma() + " khai `khaDung = true` mà ⛔ không có nhánh dựng bảng");
                };
        return new TepXuat("%s-%s.csv".formatted(ma.ma(), homNay), bang.byteUtf8Bom());
    }

    private BangCsv trichNgang() {
        BangCsv b = new BangCsv();
        b.dong(
                "Mã CBNV",
                "Họ và tên",
                "Ngày sinh",
                "Giới tính",
                "Đơn vị",
                "Chức danh",
                "Ngày vào làm",
                "Loại hợp đồng",
                "Hợp đồng hết hạn",
                "Học vấn",
                "Trạng thái");
        Map<Long, OrgUnitRef> donVi = donViCua(employees.findByDeletedAtIsNull());
        for (Employee e : sapTheoMa(employees.findByDeletedAtIsNull())) {
            b.dong(
                    e.getCode(),
                    e.getFullName(),
                    e.getDateOfBirth(),
                    e.getGender(),
                    tenDonVi(donVi, e.getOrgUnitId()),
                    e.getJobTitle(),
                    e.getHiredAt(),
                    e.getContractType(),
                    e.getContractExpiresAt(),
                    e.getEducationLevel(),
                    e.getStatus());
        }
        return b;
    }

    private BangCsv quanSoTheoDonVi() {
        BangCsv b = new BangCsv();
        b.dong("Mã đơn vị", "Tên đơn vị", "Số người còn làm việc");
        for (MucDem m : theoDonVi(conLamViec())) {
            b.dong(m.khoa(), m.nhan(), m.soLuong());
        }
        return b;
    }

    private BangCsv bienDongCsv(YearMonth den) {
        BangCsv b = new BangCsv();
        b.dong("Tháng", "Tuyển mới", "Nghỉ việc", "Điều động / bổ nhiệm");
        for (BienDongThang t : bienDong12Thang(employees.findByDeletedAtIsNull(), den)) {
            b.dong(t.thang(), t.tuyenMoi(), t.nghiViec(), t.dieuDong());
        }
        return b;
    }

    private BangCsv hopDongSapHetHan() {
        CanhBaoHetHanService.KetQua kq = canhBao.canhBao();
        BangCsv b = new BangCsv();
        b.dong("Mã CBNV", "Họ và tên", "Nội dung", "Ngày hết hạn", "Số ngày còn lại");
        for (CanhBaoHetHanService.Muc m : kq.hopDong()) {
            b.dong(m.maCanBo(), m.hoTen(), m.moTa(), m.hetHan(), m.soNgayCon());
        }
        return b;
    }

    private BangCsv chungChiSapHetHan() {
        CanhBaoHetHanService.KetQua kq = canhBao.canhBao();
        BangCsv b = new BangCsv();
        b.dong("Mã CBNV", "Họ và tên", "Chứng chỉ", "Ngày hết hiệu lực", "Số ngày còn lại");
        for (CanhBaoHetHanService.Muc m : kq.chungChi()) {
            b.dong(m.maCanBo(), m.hoTen(), m.moTa(), m.hetHan(), m.soNgayCon());
        }
        return b;
    }

    private BangCsv coCau(LocalDate homNay) {
        List<Employee> dangLam = conLamViec();
        BangCsv b = new BangCsv();
        b.dong("Tiêu chí", "Nhóm", "Số người");
        for (MucDem m :
                dem(dangLam, e -> e.getGender() == null ? null : e.getGender().name())) {
            b.dong("Giới tính", m.nhan(), m.soLuong());
        }
        for (MucDem m : dem(
                dangLam,
                e -> e.getEducationLevel() == null
                        ? null
                        : e.getEducationLevel().name())) {
            b.dong("Học vấn", m.nhan(), m.soLuong());
        }
        for (MucDem m : theoNhomTuoi(dangLam, homNay)) {
            b.dong("Nhóm tuổi", m.nhan(), m.soLuong());
        }
        return b;
    }

    /**
     * BCNS-08 — mỗi tháng một dòng: <b>đầu kỳ · tăng · giảm · cuối kỳ</b>.
     *
     * <p>⚠ "Đầu kỳ" suy từ chính hai cột {@code hired_at} / {@code terminated_at}, ⛔ không đọc một
     * cột tồn kho nào — hệ ⛔ không có cột ấy, và dựng một cột như thế là mời đúng quy tắc 13 vào.
     */
    private BangCsv tongHopNam(int nam) {
        List<Employee> tatCa = employees.findByDeletedAtIsNull();
        BangCsv b = new BangCsv();
        b.dong("Tháng", "Đầu kỳ", "Tăng", "Giảm", "Cuối kỳ");
        for (int thang = 1; thang <= 12; thang++) {
            YearMonth ym = YearMonth.of(nam, thang);
            LocalDate dau = ym.atDay(1);
            LocalDate cuoi = ym.atEndOfMonth();
            long dauKy =
                    tatCa.stream().filter(e -> dangLamVao(e, dau.minusDays(1))).count();
            long tang = tatCa.stream()
                    .filter(e -> e.getHiredAt() != null
                            && !e.getHiredAt().isBefore(dau)
                            && !e.getHiredAt().isAfter(cuoi))
                    .count();
            long giam = tatCa.stream()
                    .filter(e -> e.getTerminatedAt() != null
                            && !e.getTerminatedAt().isBefore(dau)
                            && !e.getTerminatedAt().isAfter(cuoi))
                    .count();
            b.dong(ym, dauKy, tang, giam, dauKy + tang - giam);
        }
        return b;
    }

    // =========================================================================
    // Nội bộ
    // =========================================================================

    private List<Employee> conLamViec() {
        return employees.findByDeletedAtIsNull().stream()
                .filter(e -> e.getStatus() == null || !e.getStatus().daNghi())
                .toList();
    }

    /** Đang làm việc tính tới ngày {@code moc} — dùng cho cột "đầu kỳ" của BCNS-08. */
    private static boolean dangLamVao(Employee e, LocalDate moc) {
        if (e.getHiredAt() == null || e.getHiredAt().isAfter(moc)) {
            return false;
        }
        return e.getTerminatedAt() == null || e.getTerminatedAt().isAfter(moc);
    }

    private List<MucDem> theoDonVi(List<Employee> ds) {
        Map<Long, OrgUnitRef> donVi = donViCua(ds);
        Map<Long, Long> dem = new LinkedHashMap<>();
        for (Employee e : ds) {
            dem.merge(e.getOrgUnitId(), 1L, Long::sum);
        }
        List<MucDem> ket = new ArrayList<>();
        dem.forEach((id, so) -> {
            OrgUnitRef ref = donVi.get(id);
            ket.add(new MucDem(ref == null ? String.valueOf(id) : ref.code(), tenDonVi(donVi, id), so));
        });
        ket.sort(Comparator.comparingLong(MucDem::soLuong).reversed());
        return List.copyOf(ket);
    }

    private Map<Long, OrgUnitRef> donViCua(List<Employee> ds) {
        return orgUnits.findRefsByIds(ds.stream().map(Employee::getOrgUnitId).toList());
    }

    /**
     * ⛔ Đơn vị ⛔ không tra được thì nói <b>"(đơn vị đã xoá)"</b>, ⛔ không trả chuỗi rỗng: một ô
     * trống đọc như *chưa nhập*, trong khi sự thật là *hồ sơ trỏ vào một đơn vị ⛔ không còn* — hai
     * câu dẫn tới hai việc khác hẳn nhau.
     */
    private static String tenDonVi(Map<Long, OrgUnitRef> donVi, Long id) {
        OrgUnitRef ref = id == null ? null : donVi.get(id);
        return ref == null ? "(đơn vị đã xoá)" : ref.name();
    }

    private static List<MucDem> dem(List<Employee> ds, java.util.function.Function<Employee, String> khoaCua) {
        Map<String, Long> dem = new LinkedHashMap<>();
        for (Employee e : ds) {
            String k = khoaCua.apply(e);
            // ⛔ "Chưa nhập" là một NHÓM có thật, ⛔ không bị bỏ đi: tổng các lát phải bằng quân số,
            //   ⛔ không thì biểu đồ tròn nói dối về mẫu số.
            dem.merge(k == null ? "CHUA_NHAP" : k, 1L, Long::sum);
        }
        List<MucDem> ket = new ArrayList<>();
        dem.forEach((k, so) -> ket.add(new MucDem(k, "CHUA_NHAP".equals(k) ? "Chưa nhập" : k, so)));
        ket.sort(Comparator.comparingLong(MucDem::soLuong).reversed());
        return List.copyOf(ket);
    }

    private static List<MucDem> theoNhomTuoi(List<Employee> ds, LocalDate homNay) {
        Map<String, Long> dem = new LinkedHashMap<>();
        dem.put("< " + MOC_TUOI[0], 0L);
        for (int i = 0; i < MOC_TUOI.length - 1; i++) {
            dem.put(MOC_TUOI[i] + "–" + (MOC_TUOI[i + 1] - 1), 0L);
        }
        dem.put("≥ " + MOC_TUOI[MOC_TUOI.length - 1], 0L);
        dem.put("Chưa nhập", 0L);

        for (Employee e : ds) {
            if (e.getDateOfBirth() == null) {
                dem.merge("Chưa nhập", 1L, Long::sum);
                continue;
            }
            int tuoi = Period.between(e.getDateOfBirth(), homNay).getYears();
            String nhom;
            if (tuoi < MOC_TUOI[0]) {
                nhom = "< " + MOC_TUOI[0];
            } else if (tuoi >= MOC_TUOI[MOC_TUOI.length - 1]) {
                nhom = "≥ " + MOC_TUOI[MOC_TUOI.length - 1];
            } else {
                nhom = null;
                for (int i = 0; i < MOC_TUOI.length - 1; i++) {
                    if (tuoi < MOC_TUOI[i + 1]) {
                        nhom = MOC_TUOI[i] + "–" + (MOC_TUOI[i + 1] - 1);
                        break;
                    }
                }
            }
            dem.merge(nhom, 1L, Long::sum);
        }
        List<MucDem> ket = new ArrayList<>();
        dem.forEach((k, so) -> ket.add(new MucDem(k, k, so)));
        return List.copyOf(ket);
    }

    /**
     * Mười hai tháng gần nhất, <b>kể cả tháng ⛔ không có gì</b>.
     *
     * <p>⛔ Bỏ tháng rỗng làm trục thời gian của biểu đồ dựng <b>từ chính dữ liệu</b> — đúng khuyết
     * tật T46.1: một khoảng lặng ba tháng biến thành hai điểm liền nhau và đường nối chúng nói dối
     * về tốc độ.
     */
    private List<BienDongThang> bienDong12Thang(List<Employee> tatCa, YearMonth den) {
        List<BienDongThang> ket = new ArrayList<>();
        Map<YearMonth, Long> dieuDongTheoThang = dieuDongTheoThang(den.minusMonths(11));
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = den.minusMonths(i);
            long tuyen = tatCa.stream()
                    .filter(e -> e.getHiredAt() != null
                            && YearMonth.from(e.getHiredAt()).equals(ym))
                    .count();
            long nghi = tatCa.stream()
                    .filter(e -> e.getTerminatedAt() != null
                            && YearMonth.from(e.getTerminatedAt()).equals(ym))
                    .count();
            ket.add(new BienDongThang(ym.toString(), tuyen, nghi, dieuDongTheoThang.getOrDefault(ym, 0L)));
        }
        return List.copyOf(ket);
    }

    /**
     * ⛔⛔ MỘT truy vấn, ⛔ không phải một truy vấn mỗi cán bộ. Bản đầu gọi
     * {@code findByEmployeeId…} trong vòng lặp — ~200 lượt cho một lần mở màn hình, trên VPS 2
     * nhân, và triệu chứng duy nhất là *"trang hơi chậm"*.
     */
    private Map<YearMonth, Long> dieuDongTheoThang(YearMonth tu) {
        Map<YearMonth, Long> ket = new LinkedHashMap<>();
        suKien.theoLoaiTuNgay(List.of(EmployeeEventType.DIEU_DONG, EmployeeEventType.BO_NHIEM_MIEN_NHIEM), tu.atDay(1))
                .forEach(s -> ket.merge(YearMonth.from(s.getEffectiveOn()), 1L, Long::sum));
        return ket;
    }

    /**
     * Số tháng hệ có ít nhất một mốc nhân sự — {@code 0} nghĩa là <b>chưa biết gì</b>.
     *
     * <p>⛔ Tỉ lệ nghỉ việc tính trên một tháng duy nhất có dữ liệu là một con số <b>đúng công
     * thức</b> mà ⛔ không nói được điều gì. Giao diện đọc con số này để quyết định có in tỉ lệ ra
     * hay ⛔ không (quy tắc 16 — số 0 là một khẳng định).
     */
    private static int soThangCoDuLieu(List<Employee> tatCa) {
        return (int) tatCa.stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getHiredAt(), e.getTerminatedAt()))
                .filter(java.util.Objects::nonNull)
                .map(YearMonth::from)
                .distinct()
                .count();
    }

    /** Tỉ lệ phần trăm, một chữ số thập phân — <b>chuỗi</b>, cấm float (quy tắc 2). */
    private static String tyLe(long tu, long mau) {
        if (mau <= 0) {
            return "0.0";
        }
        return BigDecimal.valueOf(tu)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(mau), 1, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static List<Employee> sapTheoMa(List<Employee> ds) {
        List<Employee> sap = new ArrayList<>(ds);
        sap.sort(Comparator.comparing(Employee::getCode, Comparator.nullsLast(String::compareTo)));
        return sap;
    }
}
