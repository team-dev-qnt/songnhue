package com.songnhue.hr.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.spi.SecurityEventPort;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmployeeSensitive;
import com.songnhue.hr.infra.EmployeeSensitiveRepository;

/**
 * Trường 🔒 của hồ sơ CBNV — CN-04.2, quy tắc 10 và 13, NĐ 13/2023/NĐ-CP.
 *
 * <h2>Bốn bảo đảm, và chỗ đặt từng cái</h2>
 *
 * <ol>
 *   <li><b>Phạm vi đơn vị</b> — mọi đường vào bắt đầu từ {@link EmployeeService#get(UUID)}, tức đã
 *       đi qua {@code ScopeGuard}. Lớp này ⛔ <b>không</b> có đường tra riêng theo {@code publicId}
 *       của bảng 🔒: có nó là mở một cửa đi vòng qua tầng 3 ở đúng bảng nhạy cảm nhất.
 *   <li><b>Mã hoá</b> — {@link CryptoService} tại đây, ⛔ không ở setter của entity (Hibernate nạp
 *       lại từ CSDL sẽ đi qua setter và mã hoá <b>lần thứ hai</b> một chuỗi vốn đã là bản mã).
 *   <li><b>CCCD unique</b> — cột vân tay, xem {@link #vanTay}.
 *   <li><b>Ghi nhật ký lượt ĐỌC</b> — {@link SecurityEventPort#hrSensitiveFieldsRead(String)}.
 *       {@code audit_logs} chỉ sinh dòng khi có <i>thay đổi</i>, nên nếu ⛔ không có dòng này thì
 *       một người mở lần lượt toàn bộ hồ sơ để chép số tài khoản ⛔ không để lại dấu vết nào.
 * </ol>
 *
 * <h2>⛔⛔ Nửa còn thiếu của CN-04.7, khai ra chứ ⛔ không giấu</h2>
 *
 * <p>Đặc tả nói trường 🔒 chỉ dành cho <i>Admin HR <b>và chính nhân viên đó</b></i>. Vế đầu có:
 * quyền {@code hr:employee:view-sensitive} chỉ gán cho SUPER_ADMIN và ADMIN_HR — ⛔ <b>kể cả ADMIN
 * cũng bị loại trừ tường minh</b> ({@code V202608131007:169}). Vế sau ⛔ <b>chưa có</b>, và ⛔
 * không phải vì quên: {@code AuthenticatedUser} ⛔ không mang {@code employeeId}, mà quan trọng hơn
 * — đo 10/09/2026 — cột {@code users.employee_id} có <b>0 đường ghi</b> trong toàn kho. Dựng vế đọc
 * trước khi có vế ghi là dựng đúng một nửa cặp đọc–ghi, hình dạng đã trả giá sáu lần (luật 27). Nợ
 * ghi ở {@code master-tracking.md} T51.9 kèm ba chỗ phải sửa cùng lượt.
 */
@Service
public class EmployeeSensitiveService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSensitiveService.class);

    private final EmployeeSensitiveRepository sensitive;
    private final EmployeeService employees;
    private final CryptoService crypto;
    private final SecurityEventPort securityEvents;

    public EmployeeSensitiveService(
            EmployeeSensitiveRepository sensitive,
            EmployeeService employees,
            CryptoService crypto,
            SecurityEventPort securityEvents) {
        this.sensitive = sensitive;
        this.employees = employees;
        this.crypto = crypto;
        this.securityEvents = securityEvents;
    }

    /**
     * Đọc trường 🔒 đã giải mã — <b>để lại một dòng {@code security_events}</b>.
     *
     * <p>⚠ ⛔ Không {@code readOnly = true} một cách máy móc: lượt đọc này <b>ghi</b> một dòng nhật
     * ký bảo mật, nên nó cần một giao dịch ghi thật. Đánh dấu chỉ-đọc ở đây là để dòng nhật ký ấy
     * hoặc bị vứt, hoặc nổ ở tầng driver — cả hai đều làm bảo đảm thứ tư biến mất trong im lặng.
     */
    @Transactional
    public EmployeeSensitiveForm doc(UUID employeePublicId) {
        Employee hoSo = employees.get(employeePublicId);
        securityEvents.hrSensitiveFieldsRead(hoSo.getCode());

        return sensitive
                .findByEmployeeIdAndDeletedAtIsNull(hoSo.getId())
                .map(this::giaiMa)
                .orElseGet(EmployeeSensitiveService::rong);
    }

    /**
     * Ô nào đã có dữ liệu — ⛔ KHÔNG kèm giá trị. Cùng khuôn {@code ApiSourceView.credentialDaCauHinh}.
     *
     * <p>⛔⛔ Tham số là một {@link Employee} <b>đã lấy được</b>, ⛔ không phải một {@code UUID}. Đó
     * là một ràng buộc <b>cấu trúc</b>, ⛔ không phải một lời dặn: một {@code Employee} chỉ ra đời
     * từ {@link EmployeeService#get(UUID)}, tức đã qua {@code ScopeGuard}. Nhận {@code UUID} ở đây
     * là mở một đường tra thứ hai vào bảng 🔒 mà người viết sau có thể quên bọc phạm vi.
     */
    @Transactional(readOnly = true)
    public EmployeeSensitiveStatus tinhTrang(Employee hoSo) {
        return sensitive
                .findByEmployeeIdAndDeletedAtIsNull(hoSo.getId())
                .map(EmployeeSensitiveService::tinhTrangCua)
                .orElseGet(EmployeeSensitiveStatus::trong);
    }

    @Transactional
    public void luu(UUID employeePublicId, EmployeeSensitiveForm form) {
        Employee hoSo = employees.get(employeePublicId);
        EmployeeSensitive ban = sensitive
                .findByEmployeeIdAndDeletedAtIsNull(hoSo.getId())
                .orElseGet(() -> {
                    EmployeeSensitive moi = new EmployeeSensitive();
                    moi.setEmployeeId(hoSo.getId());
                    return moi;
                });

        String cccd = rutGon(form.nationalId());
        String vanTay = vanTay(cccd);
        kiemTrung(vanTay, ban.getId());

        ban.setNationalId(maHoa(cccd));
        ban.setNationalIdFingerprint(vanTay);
        ban.setNationalIdIssuedOn(maHoa(rutGon(form.nationalIdIssuedOn())));
        ban.setNationalIdIssuedPlace(maHoa(rutGon(form.nationalIdIssuedPlace())));
        ban.setBaseSalary(maHoa(rutGon(form.baseSalary())));
        ban.setSalaryCoefficient(maHoa(rutGon(form.salaryCoefficient())));
        ban.setBankAccount(maHoa(rutGon(form.bankAccount())));
        ban.setTaxCode(maHoa(rutGon(form.taxCode())));
        ban.setSocialInsuranceNo(maHoa(rutGon(form.socialInsuranceNo())));

        sensitive.save(ban);
        // ⛔ Log ghi MÃ nhân viên, ⛔ không bao giờ ghi thứ vừa lưu.
        log.info("Cập nhật trường nhạy cảm của hồ sơ {}", hoSo.getCode());
    }

    /**
     * Mọi {@code key_id} đang có ở cột vân tay — một khẳng định thường trực, ⛔ không phải tính năng.
     *
     * <p>⛔⛔ Vân tay <b>phụ thuộc khoá</b>. Sau một lượt xoay khoá mà job (⛔ chưa tồn tại) ⛔ không
     * tính lại cột này, cùng một số CCCD cho hai vân tay khác nhau ⇒ phép chống trùng câm lặng, ⛔
     * không một dòng lỗi. Tập này có nhiều hơn một phần tử chính là chữ ký của chuyện đó.
     */
    @Transactional(readOnly = true)
    public List<String> khoaDangDungOVanTay() {
        return sensitive.cacKhoaDangDungOVanTay();
    }

    /**
     * Vân tay xác định của CCCD — thứ ép được {@code UNIQUE} trên một cột đã mã hoá.
     *
     * <p>⛔⛔ ⛔ Không thể đặt {@code UNIQUE} thẳng lên {@code national_id}: GCM sinh IV ngẫu nhiên
     * nên cùng một số CCCD mã hoá hai lần cho ra hai chuỗi <b>khác nhau</b>. Chỉ mục ấy sẽ tồn tại,
     * đọc như một bảo đảm, và ⛔ <b>không bao giờ</b> bắt được bản trùng nào (luật 7).
     */
    private String vanTay(String cccd) {
        return cccd == null ? null : crypto.fingerprint(cccd);
    }

    private void kiemTrung(String vanTay, Long idHienTai) {
        if (vanTay == null) {
            return;
        }
        Optional<EmployeeSensitive> trung = sensitive.findByNationalIdFingerprintAndDeletedAtIsNull(vanTay);
        if (trung.isPresent() && !trung.get().getId().equals(idHienTai)) {
            // ⛔ Thông điệp lỗi ⛔ KHÔNG nói hồ sơ nào đang giữ số ấy — đó là một phép rò rỉ dữ liệu
            // cá nhân qua đúng cái cửa dựng ra để bảo vệ nó.
            throw new ConflictException(ErrorCode.HR_1003);
        }
    }

    private String maHoa(String thoRaw) {
        return thoRaw == null ? null : crypto.encrypt(thoRaw);
    }

    private EmployeeSensitiveForm giaiMa(EmployeeSensitive ban) {
        return new EmployeeSensitiveForm(
                crypto.decrypt(ban.getNationalId()),
                crypto.decrypt(ban.getNationalIdIssuedOn()),
                crypto.decrypt(ban.getNationalIdIssuedPlace()),
                crypto.decrypt(ban.getBaseSalary()),
                crypto.decrypt(ban.getSalaryCoefficient()),
                crypto.decrypt(ban.getBankAccount()),
                crypto.decrypt(ban.getTaxCode()),
                crypto.decrypt(ban.getSocialInsuranceNo()));
    }

    private static EmployeeSensitiveStatus tinhTrangCua(EmployeeSensitive ban) {
        return new EmployeeSensitiveStatus(
                coDuLieu(ban.getNationalId()),
                coDuLieu(ban.getBaseSalary()) || coDuLieu(ban.getSalaryCoefficient()),
                coDuLieu(ban.getBankAccount()),
                coDuLieu(ban.getTaxCode()),
                coDuLieu(ban.getSocialInsuranceNo()));
    }

    private static boolean coDuLieu(String banMa) {
        return banMa != null && !banMa.isBlank();
    }

    private static EmployeeSensitiveForm rong() {
        return new EmployeeSensitiveForm(null, null, null, null, null, null, null, null);
    }

    private static String rutGon(String giaTri) {
        return giaTri == null || giaTri.isBlank() ? null : giaTri.trim();
    }
}
