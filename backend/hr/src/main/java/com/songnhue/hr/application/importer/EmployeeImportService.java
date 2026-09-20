package com.songnhue.hr.application.importer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.importer.BieuMauCsv;
import com.songnhue.core.common.importer.CotMau;
import com.songnhue.core.common.importer.KetQuaNhap;
import com.songnhue.core.common.importer.KetQuaNhap.LoiDong;
import com.songnhue.core.common.importer.SpreadsheetReader;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.hr.application.EmployeeForm;
import com.songnhue.hr.application.EmployeeService;
import com.songnhue.hr.domain.ContractType;
import com.songnhue.hr.domain.EducationLevel;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;
import com.songnhue.hr.domain.Position;
import com.songnhue.hr.infra.EmployeeRepository;
import com.songnhue.hr.infra.PositionRepository;

/**
 * <b>Nhập danh sách CBNV từ tệp — T68.23 (G6-a).</b>
 *
 * <h2>Vì sao bộ nhập này tồn tại</h2>
 *
 * <p>Ba bộ nhập khác của kho ({@code operations} × 2, {@code hydro}) đã dùng chung
 * {@code core/common/importer}; {@code backend/hr} có <b>0</b>. Hệ quả đo được: tệp mẫu
 * {@code docs/mau/mau-danh-sach-cbnv.csv} là một tệp TĨNH và đã lệch lược đồ — nó ghi loại hợp đồng
 * <i>"Thời vụ"</i> (ràng buộc CSDL ⛔ nhận) và trình độ <i>"Sau đại học"</i> (enum tách Tiến sĩ/Thạc sĩ).
 * Từ lượt này tệp mẫu do chính {@link #COT_MAU} sinh ra, nên nó ⛔ lệch được nữa (luật 14).
 *
 * <h2>⛔⛔ Ô TRỐNG ⛔ phải lệnh xoá — T47.16</h2>
 *
 * <p>Hồ sơ CBNV có <b>24 trường</b>, tệp mẫu có 16 cột. Một lượt nhập <i>thay toàn phần</i> sẽ xoá
 * trắng những cột ⛔ có trong tệp (quê quán, địa chỉ, liên hệ khẩn cấp…) và mọi ô để trống — lặng lẽ.
 * Nên với hồ sơ ĐÃ CÓ, bộ nhập dựng biểu mẫu từ <b>hồ sơ hiện tại</b> rồi chỉ ghi đè ô nào tệp có giá
 * trị ({@link #hopNhat}). Trường 🔒 (CCCD, số tài khoản, lương) ⛔ có trong tệp <b>và ⛔ bao giờ có</b>:
 * chúng đi qua hộp thoại riêng, ⛔ qua một tệp nằm trong thư mục Tải về của ai đó.
 *
 * <h2>Đi qua {@link EmployeeService}, ⛔ ghi thẳng repository</h2>
 *
 * <p>Ở đó có: chuẩn hoá mã, ràng buộc trạng thái ↔ ngày nghỉ việc, <b>phạm vi ghi</b> (T74.8) và nhật ký
 * kiểm toán. Một bộ nhập ghi thẳng entity là bản sao thứ hai của mọi luật ấy — và bản sao sẽ lệch.
 */
@Service
public class EmployeeImportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);

    private static final String COT_MA = "ma_cbnv";
    private static final String COT_TEN = "ho_ten";
    private static final String COT_DON_VI = "ma_don_vi";
    private static final String COT_NGAY_SINH = "ngay_sinh";
    private static final String COT_GIOI_TINH = "gioi_tinh";
    private static final String COT_CHUC_VU = "ma_chuc_vu";
    private static final String COT_CHUC_DANH = "chuc_danh";
    private static final String COT_VAO_LAM = "ngay_vao_cong_ty";
    private static final String COT_LOAI_HD = "loai_hop_dong";
    private static final String COT_KY_HD = "ngay_ky_hop_dong";
    private static final String COT_HET_HD = "ngay_het_han_hop_dong";
    private static final String COT_TRINH_DO = "trinh_do";
    private static final String COT_TRANG_THAI = "trang_thai";
    private static final String COT_NGAY_NGHI = "ngay_nghi_viec";
    private static final String COT_EMAIL = "email_cong_viec";
    private static final String COT_DIEN_THOAI = "dien_thoai";

    /** ⭐ Một nguồn — sinh ra chính tệp mẫu (luật 14). Mô tả LÀ tài liệu của tệp. */
    public static final List<CotMau> COT_MAU = List.of(
            new CotMau(COT_MA, true, "BẮT BUỘC · mã nhân viên Công ty đang dùng, duy nhất toàn Công ty"),
            new CotMau(COT_TEN, true, "BẮT BUỘC · họ và tên đầy đủ"),
            new CotMau(COT_DON_VI, true, "BẮT BUỘC · mã đơn vị, phải có sẵn trong danh mục đơn vị (ví dụ CTY)"),
            new CotMau(COT_NGAY_SINH, false, "dd/mm/yyyy — ô ngày của Excel cũng đọc được"),
            new CotMau(COT_GIOI_TINH, false, "Nam | Nữ"),
            new CotMau(COT_CHUC_VU, false, "Mã chức vụ trong danh mục chức vụ"),
            new CotMau(COT_CHUC_DANH, false, "Chức danh ghi tự do, ví dụ Cán bộ kỹ thuật"),
            new CotMau(COT_VAO_LAM, false, "dd/mm/yyyy · ngày vào làm — mốc tính thâm niên phép năm"),
            new CotMau(COT_LOAI_HD, false, "Không xác định thời hạn | Xác định thời hạn | Thử việc"),
            new CotMau(COT_KY_HD, false, "dd/mm/yyyy"),
            new CotMau(COT_HET_HD, false, "dd/mm/yyyy — bỏ trống nếu hợp đồng không xác định thời hạn"),
            new CotMau(
                    COT_TRINH_DO,
                    false,
                    "Tiến sĩ | Thạc sĩ | Đại học | Cao đẳng | Trung cấp | Sơ cấp | THPT | THCS | Khác"),
            new CotMau(
                    COT_TRANG_THAI,
                    false,
                    "Đang làm | Thử việc | Thai sản | Nghỉ không lương | Nghỉ việc | Nghỉ hưu — bỏ trống = Đang làm"),
            new CotMau(COT_NGAY_NGHI, false, "dd/mm/yyyy · BẮT BUỘC khi trạng thái là Nghỉ việc hoặc Nghỉ hưu"),
            new CotMau(COT_EMAIL, false, "Email công việc"),
            new CotMau(COT_DIEN_THOAI, false, "Số điện thoại"));

    private static final List<String> COT_BAT_BUOC = CotMau.tenBatBuoc(COT_MAU);

    /** ⚠ {@code d/M/yyyy} chứ ⛔ {@code dd/MM/yyyy}: người gõ tay hay bỏ số 0 ở đầu (5/1/2015). */
    private static final DateTimeFormatter NGAY = DateTimeFormatter.ofPattern("d/M/yyyy");

    private static final Map<String, Gender> NHAN_GIOI = Map.of("nam", Gender.NAM, "nu", Gender.NU);

    private static final Map<String, ContractType> NHAN_HOP_DONG = Map.of(
            "khong xac dinh thoi han", ContractType.KHONG_XAC_DINH_THOI_HAN,
            "xac dinh thoi han", ContractType.XAC_DINH_THOI_HAN,
            "thu viec", ContractType.THU_VIEC);

    private static final Map<String, EducationLevel> NHAN_TRINH_DO = Map.of(
            "tien si", EducationLevel.TIEN_SI,
            "thac si", EducationLevel.THAC_SI,
            "dai hoc", EducationLevel.DAI_HOC,
            "cao dang", EducationLevel.CAO_DANG,
            "trung cap", EducationLevel.TRUNG_CAP,
            "so cap", EducationLevel.SO_CAP,
            "thpt", EducationLevel.THPT,
            "thcs", EducationLevel.THCS,
            "khac", EducationLevel.KHAC);

    private static final Map<String, EmploymentStatus> NHAN_TRANG_THAI = Map.of(
            "dang lam", EmploymentStatus.DANG_LAM,
            "thu viec", EmploymentStatus.THU_VIEC,
            "thai san", EmploymentStatus.THAI_SAN,
            "nghi khong luong", EmploymentStatus.KHONG_LUONG,
            "nghi viec", EmploymentStatus.NGHI_VIEC,
            "nghi huu", EmploymentStatus.NGHI_HUU);

    public static byte[] bieuMau() {
        return BieuMauCsv.dung(COT_MAU);
    }

    private final EmployeeService employees;
    private final EmployeeRepository hoSoRepo;
    private final PositionRepository chucVuRepo;
    private final OrgUnitPort orgUnits;
    private final ScopeGuard scopeGuard;

    public EmployeeImportService(
            EmployeeService employees,
            EmployeeRepository hoSoRepo,
            PositionRepository chucVuRepo,
            OrgUnitPort orgUnits,
            ScopeGuard scopeGuard) {
        this.employees = employees;
        this.hoSoRepo = hoSoRepo;
        this.chucVuRepo = chucVuRepo;
        this.orgUnits = orgUnits;
        this.scopeGuard = scopeGuard;
    }

    /** Chạy khô — ⛔ ghi một dòng nào. */
    @Transactional(readOnly = true)
    public KetQuaNhap preview(byte[] content) {
        return lapKeHoach(content).baoCao(false);
    }

    /** Nhập thật — còn một dòng lỗi thì ⛔ dòng nào được ghi ({@code OPS-2016}). */
    @Transactional
    public KetQuaNhap apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }
        for (DongKeHoach d : keHoach.dong) {
            if (d.hienCo == null) {
                employees.create(d.form);
            } else {
                employees.update(d.hienCo.getPublicId(), d.form);
            }
        }
        log.info("Nhập hồ sơ CBNV: thêm {} · cập nhật {}", keHoach.soThem(), keHoach.soSua());
        return keHoach.baoCao(true);
    }

    private record DongKeHoach(EmployeeForm form, Employee hienCo) {}

    private static final class KeHoach {
        private final List<DongKeHoach> dong = new ArrayList<>();
        private final List<LoiDong> loi = new ArrayList<>();
        private int tongDong;

        private int soThem() {
            return (int) dong.stream().filter(d -> d.hienCo == null).count();
        }

        private int soSua() {
            return (int) dong.stream().filter(d -> d.hienCo != null).count();
        }

        private KetQuaNhap baoCao(boolean applied) {
            return new KetQuaNhap(applied, tongDong, soThem(), soSua(), List.copyOf(loi));
        }
    }

    private KeHoach lapKeHoach(byte[] content) {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(content);
        KeHoach keHoach = new KeHoach();
        keHoach.tongDong = rows.size();
        if (rows.isEmpty()) {
            keHoach.loi.add(new LoiDong(1, null, "Tệp không có dòng dữ liệu nào"));
            return keHoach;
        }
        Set<String> cotCo = rows.get(0).cells().keySet();
        List<String> thieu =
                COT_BAT_BUOC.stream().filter(c -> !cotCo.contains(c)).toList();
        if (!thieu.isEmpty()) {
            keHoach.loi.add(new LoiDong(1, String.join(", ", thieu), "Tệp thiếu cột bắt buộc"));
            return keHoach;
        }

        boolean duocSua = AuthContext.current()
                .map(u -> u.hasPermission("hr:employee:update"))
                .orElse(true);
        Set<String> maDaGap = new HashSet<>();

        for (SpreadsheetReader.Row row : rows) {
            int soDong = row.rowNumber();
            String ma = row.get(COT_MA) == null ? "" : row.get(COT_MA).trim().toUpperCase(Locale.ROOT);
            if (ma.isBlank()) {
                keHoach.loi.add(new LoiDong(soDong, COT_MA, "Thiếu mã cán bộ"));
                continue;
            }
            if (!maDaGap.add(ma)) {
                keHoach.loi.add(new LoiDong(soDong, COT_MA, "Mã '%s' xuất hiện hai lần trong tệp".formatted(ma)));
                continue;
            }
            if (rong(row.get(COT_TEN))) {
                keHoach.loi.add(new LoiDong(soDong, COT_TEN, "Thiếu họ tên"));
                continue;
            }

            Employee hienCo = hoSoRepo.findByCodeAndDeletedAtIsNull(ma).orElse(null);
            String maCuoi = ma;
            // ⛔⛔ Mã là duy nhất TOÀN Công ty, còn lượt tra trên kia đi qua bộ lọc phạm vi ⇒ hồ sơ của đơn vị khác
            //   là VÔ HÌNH. Thiếu vế này thì dòng ấy được xếp là "thêm mới", và lượt ghi thật ném `HR-1001` GIỮA
            //   CHỪNG — người nhập nhận một mã lỗi thay vì một dòng chỉ đúng chỗ sai (T74.9 cùng hình dạng).
            if (hienCo == null && scopeGuard.toanCongTy(() -> hoSoRepo.existsByCodeAndDeletedAtIsNull(maCuoi))) {
                keHoach.loi.add(new LoiDong(
                        soDong, COT_MA, "Mã '%s' đã thuộc một hồ sơ ngoài phạm vi đơn vị của bạn".formatted(ma)));
                continue;
            }
            if (hienCo != null && !duocSua) {
                keHoach.loi.add(new LoiDong(
                        soDong, COT_MA, "Mã '%s' đã có hồ sơ — sửa hồ sơ cần quyền hr:employee:update".formatted(ma)));
                continue;
            }

            EmployeeForm form = dungForm(row, hienCo, ma, soDong, keHoach.loi);
            if (form != null) {
                keHoach.dong.add(new DongKeHoach(form, hienCo));
            }
        }
        return keHoach;
    }

    /** Biểu mẫu cho một dòng: hồ sơ ĐÃ CÓ thì bắt đầu từ chính nó, ô trống giữ nguyên giá trị cũ (T47.16). */
    private EmployeeForm dungForm(
            SpreadsheetReader.Row row, Employee hienCo, String ma, int soDong, List<LoiDong> loi) {
        int truoc = loi.size();
        EmployeeForm nen = hienCo == null ? trong(ma) : hopNhat(hienCo);

        UUID donVi = nen.orgUnitPublicId();
        String maDonVi = row.get(COT_DON_VI);
        if (!rong(maDonVi)) {
            Optional<OrgUnitRef> ref = orgUnits.findRefByCode(maDonVi.trim());
            if (ref.isEmpty()) {
                loi.add(new LoiDong(soDong, COT_DON_VI, "Không có đơn vị mã '%s'".formatted(maDonVi.trim())));
            } else if (!scopeGuard.trongPhamVi(ref.get().id())) {
                // ⛔ Để lượt ghi thật ném AUTH-3002: nó từ chối CẢ TỆP giữa chừng và ⛔ nói dòng nào sai (T74.8).
                loi.add(new LoiDong(
                        soDong, COT_DON_VI, "Đơn vị '%s' ngoài phạm vi đơn vị của bạn".formatted(maDonVi.trim())));
            } else {
                donVi = ref.get().publicId();
            }
        } else if (donVi == null) {
            loi.add(new LoiDong(soDong, COT_DON_VI, "Thiếu mã đơn vị"));
        }

        UUID chucVu = nen.positionPublicId();
        String maChucVu = row.get(COT_CHUC_VU);
        if (!rong(maChucVu)) {
            Optional<Position> cv = chucVuRepo.findByCodeAndDeletedAtIsNull(maChucVu.trim());
            if (cv.isEmpty()) {
                loi.add(new LoiDong(soDong, COT_CHUC_VU, "Không có chức vụ mã '%s'".formatted(maChucVu.trim())));
            } else {
                chucVu = cv.get().getPublicId();
            }
        }

        EmployeeForm form = new EmployeeForm(
                ma,
                chuoi(row, COT_TEN, nen.fullName()),
                ngay(row, COT_NGAY_SINH, nen.dateOfBirth(), soDong, loi),
                nhan(row, COT_GIOI_TINH, NHAN_GIOI, nen.gender(), soDong, loi),
                nhan(row, COT_TRINH_DO, NHAN_TRINH_DO, nen.educationLevel(), soDong, loi),
                nen.ethnicity(),
                nen.hometown(),
                nen.address(),
                chuoi(row, COT_DIEN_THOAI, nen.phone()),
                chuoi(row, COT_EMAIL, nen.workEmail()),
                nen.personalEmail(),
                nen.maritalStatus(),
                nen.emergencyContactName(),
                nen.emergencyContactPhone(),
                donVi,
                chucVu,
                chuoi(row, COT_CHUC_DANH, nen.jobTitle()),
                ngay(row, COT_VAO_LAM, nen.hiredAt(), soDong, loi),
                nhan(row, COT_LOAI_HD, NHAN_HOP_DONG, nen.contractType(), soDong, loi),
                ngay(row, COT_KY_HD, nen.contractSignedAt(), soDong, loi),
                ngay(row, COT_HET_HD, nen.contractExpiresAt(), soDong, loi),
                nhan(row, COT_TRANG_THAI, NHAN_TRANG_THAI, nen.status(), soDong, loi),
                ngay(row, COT_NGAY_NGHI, nen.terminatedAt(), soDong, loi),
                nen.terminationReason());

        if (form.status() != null && form.status().daNghi() && form.terminatedAt() == null) {
            loi.add(new LoiDong(soDong, COT_NGAY_NGHI, "Trạng thái nghỉ việc/nghỉ hưu phải có ngày nghỉ việc"));
        }
        if (form.status() != null && !form.status().daNghi() && form.terminatedAt() != null) {
            loi.add(new LoiDong(soDong, COT_NGAY_NGHI, "Chỉ điền ngày nghỉ việc khi trạng thái là nghỉ việc/nghỉ hưu"));
        }
        return loi.size() == truoc ? form : null;
    }

    /**
     * Biểu mẫu RỖNG cho hồ sơ mới.
     *
     * <p>⚠ Trạng thái mặc định là <b>Đang làm</b> chứ ⛔ phải {@code THU_VIEC} (mặc định của
     * {@code EmployeeService}): tệp này dùng để đưa danh sách CBNV <b>đang công tác</b> vào hệ lần đầu, và
     * đánh 200 người thành thử việc là một sự thật nhân sự sai. Cột {@code trang_thai} vẫn có để đổi từng người,
     * và dòng mô tả của tệp mẫu nói rõ quy ước này.
     */
    private static EmployeeForm trong(String ma) {
        return new EmployeeForm(
                ma,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EmploymentStatus.DANG_LAM,
                null,
                null);
    }

    /**
     * Biểu mẫu dựng từ hồ sơ ĐANG CÓ — mọi trường, kể cả trường tệp mẫu ⛔ có (T47.16 · T63.10).
     *
     * <p>Công khai để {@code HopNhatDuTruongTest} soi được: nó dựng một hồ sơ có GIÁ TRỊ ở mọi trường rồi đòi biểu
     * mẫu trả về ⛔ trường nào null — thêm một trường vào {@link EmployeeForm} mà quên chép ở đây là xoá trắng
     * trường ấy ở mọi lượt nhập, im lặng.
     */
    public EmployeeForm hopNhat(Employee e) {
        return new EmployeeForm(
                e.getCode(),
                e.getFullName(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getEducationLevel(),
                e.getEthnicity(),
                e.getHometown(),
                e.getAddress(),
                e.getPhone(),
                e.getWorkEmail(),
                e.getPersonalEmail(),
                e.getMaritalStatus(),
                e.getEmergencyContactName(),
                e.getEmergencyContactPhone(),
                orgUnits.findRefById(e.getOrgUnitId()).map(OrgUnitRef::publicId).orElse(null),
                employees.chucVuPublicId(e.getPositionId()),
                e.getJobTitle(),
                e.getHiredAt(),
                e.getContractType(),
                e.getContractSignedAt(),
                e.getContractExpiresAt(),
                e.getStatus(),
                e.getTerminatedAt(),
                e.getTerminationReason());
    }

    // ---- Đọc từng ô ---------------------------------------------------------

    private static boolean rong(String o) {
        return o == null || o.isBlank();
    }

    private static String chuoi(SpreadsheetReader.Row row, String cot, String cu) {
        String o = row.get(cot);
        return rong(o) ? cu : o.trim();
    }

    private static LocalDate ngay(SpreadsheetReader.Row row, String cot, LocalDate cu, int soDong, List<LoiDong> loi) {
        String o = row.get(cot);
        if (rong(o)) {
            return cu;
        }
        try {
            return LocalDate.parse(o.trim(), NGAY);
        } catch (DateTimeParseException e) {
            loi.add(new LoiDong(soDong, cot, "Ngày '%s' không đọc được — cần dạng dd/mm/yyyy".formatted(o.trim())));
            return cu;
        }
    }

    private static <T> T nhan(
            SpreadsheetReader.Row row, String cot, Map<String, T> bang, T cu, int soDong, List<LoiDong> loi) {
        String o = row.get(cot);
        if (rong(o)) {
            return cu;
        }
        T ra = bang.get(VietnameseUtils.removeDiacritics(o.trim()).toLowerCase(Locale.ROOT));
        if (ra == null) {
            loi.add(new LoiDong(
                    soDong, cot, "Giá trị '%s' không nhận ra — xem dòng mô tả của tệp mẫu".formatted(o.trim())));
            return cu;
        }
        return ra;
    }
}
