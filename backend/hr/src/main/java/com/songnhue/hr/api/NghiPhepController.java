package com.songnhue.hr.api;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.hr.application.DonNghiPhepForm;
import com.songnhue.hr.application.DonNghiPhepService;
import com.songnhue.hr.application.EmployeeService;
import com.songnhue.hr.application.SoDuPhepService;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.LeaveRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Nghỉ phép — {@code /api/v1/hr/nghi-phep} (CN-04.9, SRS M4.10).
 *
 * <h2>⛔⛔ Hai nhóm quyền, hai câu hỏi khác nhau</h2>
 *
 * <ul>
 *   <li>{@code hr:leave:request} — <b>5/12</b> vai trò, gồm VIEWER. Chốt C3 đòi *"cấp tài khoản cho
 *       toàn bộ CBNV"*, nên ai cũng phải nộp và rút được đơn <b>của chính mình</b>.
 *   <li>{@code hr:leave:approve} / {@code hr:leave:view-all} — nhóm quản lý. ⚠ Chúng <b>⛔ không đủ
 *       một mình</b>: bảo đảm *"quản lý ĐƠN VỊ MÌNH duyệt"* là một <b>quan hệ</b>, và vế còn lại là
 *       bộ lọc phạm vi tầng 3 trên {@code leave_requests.org_unit_id} — xem
 *       {@link DonNghiPhepService}.
 * </ul>
 *
 * <p>⭐ Ba mã quyền này có <b>0 endpoint</b> kể từ 13/08/2026 — đo lại 14/09. Lượt này là đầu nhận
 * đầu tiên của cả ba.
 */
@RestController
@RequestMapping("/api/v1/hr/nghi-phep")
@Tag(name = "04-hr · Nghỉ phép", description = "Đơn nghỉ phép, số dư, hộp chờ duyệt — CN-04.9")
public class NghiPhepController {

    private final DonNghiPhepService donNghi;
    private final SoDuPhepService soDu;
    private final EmployeeService employees;

    public NghiPhepController(DonNghiPhepService donNghi, SoDuPhepService soDu, EmployeeService employees) {
        this.donNghi = donNghi;
        this.soDu = soDu;
        this.employees = employees;
    }

    /**
     * Xem trước <b>trước khi nộp</b> — đặc tả: *"tự tính số ngày + hiển thị số dư + cảnh báo vượt
     * phép"*.
     *
     * <p>⛔ Đường này ⛔ <b>không ghi gì</b>, và ba con số nó trả đều do <b>backend</b> tính
     * (quy tắc 3). Để giao diện tự đếm ngày là dựng một bản sao thứ hai của luật đếm — và bản ở
     * trình duyệt sẽ ⛔ không biết ngày lễ.
     */
    @PostMapping("/xem-truoc")
    @Operation(summary = "Tính thử số ngày công + số dư + cảnh báo — KHÔNG ghi gì")
    @RequirePermission("hr:leave:request")
    public NghiPhepDtos.XemTruocView xemTruoc(@Valid @RequestBody NghiPhepDtos.DonRequest request) {
        DonNghiPhepService.XemTruoc xt = donNghi.xemTruoc(toForm(request));
        return new NghiPhepDtos.XemTruocView(
                xt.soNgayCong(),
                xt.soNgayLeTru(),
                xt.soNgayLeDaKhai(),
                xt.duNgayLeTheoLuat(),
                NghiPhepDtos.SoDuView.of(xt.soDu()),
                xt.vuotPhep(),
                xt.canCapHaiDuyet(),
                xt.soNguoiNghiCungLuc(),
                xt.canhBaoTrungLich().orElse(null));
    }

    @PostMapping
    @Operation(summary = "Nộp đơn — HR-2004/2005/2006 chặn ba ca hỏng đã biết")
    @RequirePermission("hr:leave:request")
    public NghiPhepDtos.DonView nop(@Valid @RequestBody NghiPhepDtos.DonRequest request) {
        return toView(donNghi.nop(toForm(request)));
    }

    /** Đơn của <b>chính mình</b> — ⛔ không nhận id nào, suy từ token (T51.8). */
    @GetMapping("/cua-toi")
    @Operation(summary = "Đơn nghỉ của chính người đang đăng nhập")
    @RequirePermission("hr:leave:request")
    public NghiPhepDtos.DonTrangView cuaToi(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return trang(donNghi.cuaHoSo(null, PageUtils.toPageable(page, size, null, List.of())));
    }

    /** Số dư phép năm của <b>chính mình</b>. */
    @GetMapping("/so-du")
    @Operation(summary = "Số dư phép năm — TÍNH LẠI từ đơn, không đọc một cột đếm nào")
    @RequirePermission("hr:leave:request")
    public NghiPhepDtos.SoDuView soDuCuaToi(@RequestParam(required = false) Integer nam) {
        Employee hoSo = donNghi.hoSoCuaToi();
        return NghiPhepDtos.SoDuView.of(soDu.tinh(hoSo, nam == null ? soDu.namHienTai() : nam));
    }

    /**
     * Đơn nghỉ của <b>một CBNV cụ thể</b> — quản lý xem lịch sử trước khi duyệt.
     *
     * <h2>⭐ Vì sao endpoint này tồn tại, và nó trả hai món nợ cùng lúc</h2>
     *
     * <p>Người duyệt cần thấy *"người này đã nghỉ mấy lần quý vừa rồi"* trước khi bấm — một đơn 3
     * ngày đọc rất khác nhau tuỳ vào việc nó là đơn đầu tiên hay đơn thứ tám.
     *
     * <p>⛔⛔ Nhưng lý do nó được viết ở lượt này là <b>hai bộ canh cùng đỏ</b>:
     *
     * <ul>
     *   <li>{@code RbacMatrixTest.everyCatalogPermissionIsDeclared} — {@code hr:leave:view-all}
     *       trước đó chỉ được kiểm <b>bên trong service</b> ({@code DonNghiPhepService.hoSoMucTieu}),
     *       ⛔ không ở một {@code @RequirePermission} nào. Bộ canh quét annotation, nên với nó mã
     *       quyền ấy là một <b>công tắc chết</b> — và nó <b>đúng</b>: một quyền chỉ sống trong một
     *       nhánh {@code if} thì ⛔ không có màn hình nào cho người vận hành thấy nó làm gì.
     *   <li>{@code DonNghiPhepService.cuaHoSo(publicId, …)} có nhánh {@code publicId != null} mà
     *       <b>0 nơi gọi</b> — nửa cặp đọc–ghi, luật 15.
     * </ul>
     */
    @GetMapping("/cua-nhan-vien/{employeePublicId}")
    @Operation(summary = "Đơn nghỉ của một CBNV — quản lý xem lịch sử trước khi duyệt")
    @RequirePermission("hr:leave:view-all")
    public NghiPhepDtos.DonTrangView cuaNhanVien(
            @PathVariable UUID employeePublicId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return trang(donNghi.cuaHoSo(employeePublicId, PageUtils.toPageable(page, size, null, List.of())));
    }

    /** Hộp chờ duyệt — bộ lọc phạm vi tự cắt theo đơn vị của người đang đăng nhập. */
    @GetMapping("/cho-duyet")
    @Operation(summary = "Hộp chờ duyệt của đơn vị — phạm vi cắt ở tầng repository, không ở WHERE tay")
    @RequirePermission("hr:leave:approve")
    public NghiPhepDtos.DonTrangView choDuyet(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Page<LeaveRequest> trangDon = donNghi.hopChoDuyet(PageUtils.toPageable(page, size, null, List.of()));
        // ⭐ T80.7 — MỘT phép đo cho cả trang, ⛔ hỏi `GET /{id}/hanh-dong` từng dòng (N+1).
        return trang(trangDon, donNghi.donToiDuyetDuoc(trangDon.getContent()));
    }

    @GetMapping("/{publicId}/hanh-dong")
    @Operation(summary = "Nút được phép bấm — engine lọc theo quyền, giao diện KHÔNG tự liệt kê")
    @RequirePermission("hr:leave:request")
    public List<AllowedAction> hanhDong(@PathVariable UUID publicId) {
        return donNghi.hanhDongChoPhep(publicId);
    }

    /**
     * <b>Lịch nghỉ của một đơn vị</b> — CN-04.9, T57.18 vế (b).
     *
     * <h2>⚠ Vì sao {@code nam}/{@code thang} là hai số, ⛔ phải một chuỗi {@code yyyy-MM}</h2>
     *
     * <p>Một chuỗi cần một bộ phân tích, và bộ phân tích cần một nhánh hỏng — tức một mã lỗi mới
     * cho đúng một màn hình. Hai {@code Integer} thì Spring tự từ chối thứ ⛔ phải số, và phần còn
     * lại ({@code 1..12}) là một câu {@code if} đọc được.
     *
     * <p>⚠ Bỏ trống = <b>tháng hiện tại theo giờ Việt Nam, do BACKEND quyết</b> (quy tắc 1). Để
     * giao diện gửi tháng của máy nó là mời đúng T63.18: một máy trạm lệch múi giờ mở lịch ra tháng
     * khác, và ⛔ gì báo.
     */
    @GetMapping("/lich")
    @Operation(summary = "Lịch nghỉ của một đơn vị trong một tháng — tỉ lệ và ngưỡng do backend tính")
    @RequirePermission("hr:leave:view-all")
    public NghiPhepDtos.LichView lich(
            @RequestParam UUID donVi,
            @RequestParam(required = false) Integer nam,
            @RequestParam(required = false) Integer thang) {
        YearMonth thangHienTai = YearMonth.now(DateTimeUtils.ZONE_VN);
        int n = nam == null ? thangHienTai.getYear() : nam;
        int t = thang == null ? thangHienTai.getMonthValue() : thang;
        if (t < 1 || t > 12 || n < 1900 || n > 9999) {
            // ⛔⛔ ⛔ truyền câu giải thích làm ĐỐI SỐ của `SYS-0003`: câu ấy dùng chung cho cả kho
            //    và ⛔ có chỗ cắm `{0}` nào ⇒ `MessageFormat` **bỏ lặng** đối số, người dùng ⛔
            //    biết cái gì sai (T57.8 — 44 nơi đã mắc). Thêm chỗ cắm vào câu chung thì mọi nơi
            //    khác in ra nguyên chữ `{0}`. ⇒ Chi tiết đi đường `withDetail`, đúng chỗ FE tô đỏ
            //    được ô nhập. `DoiSoMaLoiKhopChoCamTest` bắt đúng chỗ này ở lượt chạy đầu.
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("thang", "range", t)
                    .withDetail("nam", "range", n);
        }

        DonNghiPhepService.Lich lich = donNghi.lichDonVi(donVi, YearMonth.of(n, t));
        Map<Long, Employee> theoId = employees.theoIds(
                lich.don().stream().map(LeaveRequest::getEmployeeId).toList());
        return new NghiPhepDtos.LichView(
                lich.donVi().publicId(),
                lich.donVi().name(),
                lich.thang().toString(),
                lich.quanSo(),
                lich.nguongPhanTram(),
                // ⚠ `toiDuyetDuoc` để `null` là CỐ Ý: lịch ⛔ trả lời câu ấy (xem `DonView.of` —
                //   `false` ở đó nghĩa là *thấy mà ⛔ bấm được*, một câu khác hẳn).
                lich.don().stream()
                        .map(r -> toView(r, theoId.get(r.getEmployeeId())))
                        .toList(),
                lich.ngay().stream()
                        .map(d -> new NghiPhepDtos.LichNgayView(
                                d.ngay(), d.soNguoiNghi(), d.tyLePhanTram(), d.vuotNguong()))
                        .toList());
    }

    /**
     * Thực hiện một bước chuyển.
     *
     * <p>⚠ Người gọi gửi {@code APPROVE}; việc đổi nó thành {@code ESCALATE} khi đơn đủ dài nằm ở
     * <b>service</b>, ⛔ không ở giao diện — nếu ⛔ không thì hai khoá {@code settings} của chốt C2
     * thành núm điều khiển <b>trình duyệt</b> thay vì điều khiển <b>quy trình</b>.
     */
    @PostMapping("/{publicId}/hanh-dong")
    @Operation(summary = "Duyệt / từ chối / rút đơn — trạng thái đổi DUY NHẤT qua Workflow engine")
    @RequirePermission("hr:leave:request")
    public NghiPhepDtos.DonView thucHien(
            @PathVariable UUID publicId, @Valid @RequestBody NghiPhepDtos.HanhDongRequest request) {
        return toView(donNghi.thucHien(publicId, request.action(), request.reason()));
    }

    // ---- Nội bộ ---------------------------------------------------------------

    private static DonNghiPhepForm toForm(NghiPhepDtos.DonRequest r) {
        return new DonNghiPhepForm(r.employeePublicId(), r.leaveType(), r.fromDate(), r.toDate(), r.reason());
    }

    private NghiPhepDtos.DonTrangView trang(Page<LeaveRequest> p) {
        return trang(p, null);
    }

    /**
     * @param duyetDuoc id các đơn người đang đăng nhập bấm được nút; {@code null} = <b>endpoint này
     *     ⛔ trả lời câu ấy</b> (xem javadoc {@code DonView.of}). ⛔ Truyền tập rỗng thay cho
     *     {@code null}: hai thứ ấy nói hai điều khác nhau.
     */
    private NghiPhepDtos.DonTrangView trang(Page<LeaveRequest> p, Set<Long> duyetDuoc) {
        List<LeaveRequest> noiDung = p.getContent();
        // ⚠ Nạp tên/mã CBNV MỘT LẦN cho cả trang — tra từng dòng là N+1, và số dòng là số đơn của
        //   cả đơn vị trong một kỳ.
        Map<Long, Employee> theoId = employees.theoIds(
                noiDung.stream().map(LeaveRequest::getEmployeeId).toList());
        return new NghiPhepDtos.DonTrangView(
                noiDung.stream()
                        .map(r -> toView(
                                r,
                                theoId.get(r.getEmployeeId()),
                                duyetDuoc == null ? null : duyetDuoc.contains(r.getId())))
                        .toList(),
                p.getTotalElements(),
                p.getNumber(),
                p.getSize());
    }

    private NghiPhepDtos.DonView toView(LeaveRequest r) {
        return toView(r, employees.theoIds(List.of(r.getEmployeeId())).get(r.getEmployeeId()));
    }

    private static NghiPhepDtos.DonView toView(LeaveRequest r, Employee hoSo) {
        return toView(r, hoSo, null);
    }

    private static NghiPhepDtos.DonView toView(LeaveRequest r, Employee hoSo, Boolean toiDuyetDuoc) {
        return NghiPhepDtos.DonView.of(
                r,
                hoSo == null ? null : hoSo.getPublicId(),
                hoSo == null ? null : hoSo.getCode(),
                hoSo == null ? null : hoSo.getFullName(),
                toiDuyetDuoc);
    }
}
