package com.songnhue.hr.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.core.spi.UserDirectoryPort;
import com.songnhue.hr.domain.UyQuyenDuyetPhep;
import com.songnhue.hr.infra.UyQuyenDuyetPhepRepository;

/**
 * Uỷ quyền duyệt nghỉ phép — <b>chốt B3</b>, CN-04.9 (T80.5).
 *
 * <p>Đặc tả nguyên văn ({@code function-spec.md:716}): <i>"có chức năng <b>uỷ quyền duyệt có thời
 * hạn</b> (từ–đến, người được uỷ quyền <b>cùng đơn vị hoặc cấp trên</b>); audit ghi <b>'duyệt theo
 * uỷ quyền của X'</b>"</i>. Ba vế ấy là ba chốt chặn ở lớp này, và cả ba đều có bài kiểm riêng.
 *
 * <h2>⛔⛔ Ba điều kiện, ba mã lỗi — ⛔ gộp</h2>
 *
 * <ul>
 *   <li>{@code HR-2016} người <b>giao</b> ⛔ phải trưởng/phó của đơn vị hay cấp trên
 *   <li>{@code HR-2014} người <b>nhận</b> chưa có {@code hr:leave:approve}
 *   <li>{@code HR-2015} người <b>nhận</b> ⛔ thuộc cùng đơn vị hay cấp trên
 * </ul>
 *
 * <p>Gộp cả ba vào một câu <i>"⛔ hợp lệ"</i> thì người quản trị ⛔ biết phải đi sửa ở đâu — gán vai
 * trò, đổi đơn vị, hay nhờ người khác giao. Cùng lý lẽ ba trạng thái <i>"báo cáo này ⛔ tải
 * được"</i> (T59.0).
 */
@Service
public class UyQuyenDuyetPhepService {

    private static final Logger log = LoggerFactory.getLogger(UyQuyenDuyetPhepService.class);

    private final UyQuyenDuyetPhepRepository uyQuyen;
    private final OrgUnitPort orgUnits;
    private final UserDirectoryPort taiKhoan;
    private final ThamQuyenDuyetPhep thamQuyen;
    private final ScopeGuard scopeGuard;

    public UyQuyenDuyetPhepService(
            UyQuyenDuyetPhepRepository uyQuyen,
            OrgUnitPort orgUnits,
            UserDirectoryPort taiKhoan,
            ThamQuyenDuyetPhep thamQuyen,
            ScopeGuard scopeGuard) {
        this.uyQuyen = uyQuyen;
        this.orgUnits = orgUnits;
        this.taiKhoan = taiKhoan;
        this.thamQuyen = thamQuyen;
        this.scopeGuard = scopeGuard;
    }

    /** Quyền duyệt mà người nhận phải <b>sẵn có</b> — quyết định QuanTran 20/09/2026. */
    public static final String QUYEN_DUYET = "hr:leave:approve";

    @Transactional(readOnly = true)
    public List<UyQuyenDuyetPhep> cuaDonVi(UUID donViPublicId) {
        return uyQuyen.cuaDonVi(donVi(donViPublicId).id());
    }

    /**
     * Giao thẩm quyền duyệt của một đơn vị cho một người, trong một khoảng ngày.
     *
     * <p>⚠ Ngày bắt đầu <b>được phép ở quá khứ</b>: trưởng đơn vị đi công tác đột xuất hôm qua và
     * hôm nay mới có người ngồi khai hộ là chuyện thật. Thứ ⛔ được là <i>sửa</i> một lượt đã tạo —
     * xem hàm dựng của {@link UyQuyenDuyetPhep}.
     */
    @Transactional
    public UyQuyenDuyetPhep giao(
            UUID donViPublicId, UUID nguoiNhanPublicId, LocalDate tuNgay, LocalDate denNgay, String lyDo) {

        OrgUnitRef donVi = donVi(donViPublicId);
        // ⛔⛔ Vế GHI của phạm vi tầng 3 — `GhiPhamViRuleTest.w1TrenMaThat` bắt bản đầu của lớp này
        //    ở lượt chạy ĐẦU, đúng thứ nó sinh ra để bắt. Bộ lọc phạm vi chỉ canh đường ĐỌC; ⛔ có
        //    dòng này thì một người đoán đúng `publicId` của Xí nghiệp khác **tạo được** uỷ quyền
        //    cho đơn vị ấy — tức mở một lối duyệt vào đơn vị ngoài tầm mình, bằng chính cơ chế
        //    WS-80 dựng để siết lại. Nó ném `AUTH-3002` + ghi `ACCESS_DENIED_SCOPE`.
        scopeGuard.requireWritableOrgUnit(donVi.id(), UyQuyenDuyetPhep.class);
        AuthenticatedUser ai = AuthContext.current().orElse(null);
        if (!thamQuyen.giaoDuoc(donVi.id(), ai)) {
            throw new PermissionDeniedException(ErrorCode.HR_2016);
        }

        Long nguoiNhan = taiKhoan.internalIdOf(nguoiNhanPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        if (Objects.equals(nguoiNhan, ai.userId())) {
            // ⛔ Tự giao cho mình: ràng buộc `ck_leave_deleg_khac_nguoi` cũng chặn, nhưng một lỗi
            //   CSDL trần trả `SYS-0001` — một câu ⛔ nói được người dùng vừa làm gì.
            throw new BusinessRuleException(ErrorCode.HR_2015);
        }
        if (!taiKhoan.dangHoatDongVaCoQuyen(nguoiNhanPublicId, QUYEN_DUYET)) {
            throw new BusinessRuleException(ErrorCode.HR_2014);
        }

        // *"cùng đơn vị hoặc cấp trên"* — nguyên văn B3. Phép so là: đơn vị của người nhận nằm
        // trong CHUỖI ĐI LÊN của đơn vị được uỷ quyền, tức nó phủ đơn vị ấy.
        Set<Long> chuoi = orgUnits.chuoiDonViLen(donVi.id());
        Long donViNguoiNhan = taiKhoan.orgUnitIdCua(nguoiNhanPublicId).orElse(null);
        if (donViNguoiNhan == null || !chuoi.contains(donViNguoiNhan)) {
            throw new BusinessRuleException(ErrorCode.HR_2015);
        }

        UyQuyenDuyetPhep ban = new UyQuyenDuyetPhep(donVi.id(), ai.userId(), nguoiNhan, tuNgay, denNgay, lyDo);
        UyQuyenDuyetPhep daLuu = uyQuyen.save(ban);
        log.info(
                "Uỷ quyền duyệt nghỉ phép đơn vị {} cho tài khoản #{} từ {} đến {}",
                donVi.code(),
                nguoiNhan,
                tuNgay,
                denNgay);
        return daLuu;
    }

    /**
     * Thu hồi — <b>⛔ xoá</b>.
     *
     * <p>Một lượt duyệt tháng trước có thể đang trỏ vào hàng này ({@code leave_requests.uy_quyen_id});
     * xoá nó đi là để câu <i>"duyệt theo uỷ quyền của X"</i> trỏ vào khoảng không.
     */
    @Transactional
    public UyQuyenDuyetPhep thuHoi(UUID publicId) {
        UyQuyenDuyetPhep ban = scopeGuard.require(
                uyQuyen.findByPublicIdAndDeletedAtIsNull(publicId), UyQuyenDuyetPhep.class, publicId);
        AuthenticatedUser ai = AuthContext.current().orElse(null);
        if (!thamQuyen.giaoDuoc(ban.getOrgUnitId(), ai)) {
            throw new PermissionDeniedException(ErrorCode.HR_2016);
        }
        if (ban.getThuHoiLuc() == null) {
            ban.thuHoi(ai.userId(), Instant.now());
        }
        return uyQuyen.save(ban);
    }

    private OrgUnitRef donVi(UUID publicId) {
        return orgUnits.findRef(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
