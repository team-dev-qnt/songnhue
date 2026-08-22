package com.songnhue.operations.application.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.infra.ConstructionClusterRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Nhập danh mục công trình từ tệp — T17.9, và cũng là đường seed dữ liệu thật khi G8 về.
 *
 * <h2>⭐ Chạy khô và chạy thật đi CÙNG một đường</h2>
 *
 * {@link #preview} và {@link #apply} gọi chung {@link #lapKeHoach}. Đây không phải chuyện gọn gàng
 * mã nguồn mà là điều kiện để bản xem trước có nghĩa: viết hai bộ luật thì bản xem trước sẽ dần khác
 * bản chạy thật, và người dùng nhận "0 lỗi" rồi vẫn hỏng ở lượt nhập — đúng loại mất niềm tin không
 * gỡ lại được.
 *
 * <h2>⛔ Có một dòng lỗi thì không dòng nào được ghi</h2>
 *
 * Nhập một nửa rồi dừng là trạng thái tệ nhất: người dùng không biết đã vào tới đâu, sửa tệp rồi
 * nhập lại thì phần đầu bị nhập hai lần. Toàn bộ lượt nhập nằm trong một giao dịch, và lỗi bất kỳ
 * dòng nào cũng chặn cả lượt ({@code OPS-2016}).
 *
 * <h2>⚠ Phạm vi đơn vị vẫn có hiệu lực</h2>
 *
 * Truy vấn tìm bản ghi trùng mã chạy qua bộ lọc tầng 3. Người của Xí nghiệp A nhập một mã đang thuộc
 * Xí nghiệp B sẽ <b>không thấy</b> bản ghi đó, nên kế hoạch ghi "thêm mới", rồi
 * {@code ConstructionService.create} đâm vào chỉ mục duy nhất và trả {@code OPS-2008}. Kết quả cuối
 * cùng đúng — không ai ghi đè được hồ sơ của đơn vị khác — nhưng thông báo sẽ nói "mã đã tồn tại"
 * chứ không nói "thuộc đơn vị khác", và đó là chủ ý: nói rõ hơn là tiết lộ dữ liệu ngoài phạm vi.
 */
@Service
public class ConstructionImportService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionImportService.class);

    private static final String COT_MA = "ma_cong_trinh";
    private static final String COT_TEN = "ten_cong_trinh";
    private static final String COT_LOAI = "loai_cong_trinh";
    private static final String COT_DON_VI = "ma_don_vi";

    /** Cột bắt buộc phải có trong tiêu đề — thiếu là từ chối cả tệp, không đọc dòng nào. */
    private static final List<String> COT_BAT_BUOC = List.of(COT_MA, COT_TEN, COT_LOAI, COT_DON_VI);

    /**
     * Nhãn tiếng Việt của loại công trình → enum.
     *
     * <p>Nhận cả nhãn lẫn mã enum: tệp do Công ty lập sẽ ghi "Trạm bơm", tệp do hệ thống kết xuất ra
     * ghi "TRAM_BOM", và cả hai đều phải nhập lại được. Khoá đã bỏ dấu và hạ chữ thường.
     */
    private static final Map<String, ConstructionType> NHAN_LOAI = Map.ofEntries(
            Map.entry("tram bom", ConstructionType.TRAM_BOM),
            Map.entry("tram_bom", ConstructionType.TRAM_BOM),
            Map.entry("cong", ConstructionType.CONG),
            Map.entry("cong dieu tiet", ConstructionType.CONG),
            Map.entry("kenh muong", ConstructionType.KENH_MUONG),
            Map.entry("kenh_muong", ConstructionType.KENH_MUONG),
            Map.entry("kenh", ConstructionType.KENH_MUONG),
            Map.entry("de dieu", ConstructionType.DE_DIEU),
            Map.entry("de_dieu", ConstructionType.DE_DIEU),
            Map.entry("de", ConstructionType.DE_DIEU),
            Map.entry("khac", ConstructionType.KHAC));

    private static final Map<String, ConstructionPurpose> NHAN_NHIEM_VU = Map.of(
            "tuoi", ConstructionPurpose.TUOI,
            "tieu", ConstructionPurpose.TIEU,
            "hon hop", ConstructionPurpose.HON_HOP,
            "hon_hop", ConstructionPurpose.HON_HOP);

    private static final Map<String, ManagementLevel> NHAN_CAP = Map.of(
            "cong ty", ManagementLevel.CONG_TY,
            "cong_ty", ManagementLevel.CONG_TY,
            "xi nghiep", ManagementLevel.XI_NGHIEP,
            "xi_nghiep", ManagementLevel.XI_NGHIEP,
            "cum", ManagementLevel.CUM);

    private final ConstructionService constructions;
    private final ConstructionRepository repository;
    private final ConstructionClusterRepository clusters;
    private final OrgUnitPort orgUnits;

    public ConstructionImportService(
            ConstructionService constructions,
            ConstructionRepository repository,
            ConstructionClusterRepository clusters,
            OrgUnitPort orgUnits) {
        this.constructions = constructions;
        this.repository = repository;
        this.clusters = clusters;
        this.orgUnits = orgUnits;
    }

    /** @param column {@code null} khi lỗi thuộc cả dòng chứ không thuộc một ô */
    public record RowError(int rowNumber, String column, String message) {}

    /**
     * @param applied {@code false} = mới chỉ chạy khô, chưa ghi gì
     * @param toCreate số dòng sẽ thêm mới
     * @param toUpdate số dòng sẽ cập nhật lên bản ghi đang có
     */
    public record ImportReport(boolean applied, int totalRows, int toCreate, int toUpdate, List<RowError> errors) {

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /** Xem trước — <b>không ghi một dòng nào</b>, kể cả khi tệp hoàn toàn hợp lệ. */
    @Transactional(readOnly = true)
    public ImportReport preview(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        return keHoach.baoCao(false);
    }

    /**
     * Nhập thật.
     *
     * <p>Chạy lại {@link #lapKeHoach} chứ không nhận kế hoạch từ lượt xem trước: giữa hai lượt có thể
     * có người khác vừa thêm một công trình trùng mã, và tin vào kế hoạch cũ là ghi đè lên thay đổi
     * của họ mà không ai biết.
     */
    @Transactional
    public ImportReport apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }
        for (DongKeHoach dong : keHoach.dong) {
            if (dong.publicIdHienCo == null) {
                constructions.create(dong.form);
            } else {
                constructions.update(dong.publicIdHienCo, dong.form);
            }
        }
        log.info(
                "Nhập danh mục công trình: thêm {} · cập nhật {} (tổng {} dòng)",
                keHoach.soThem(),
                keHoach.soSua(),
                keHoach.dong.size());
        return keHoach.baoCao(true);
    }

    // === Lập kế hoạch ========================================================

    private record DongKeHoach(int rowNumber, ConstructionForm form, UUID publicIdHienCo) {}

    private static final class KeHoach {
        private final List<DongKeHoach> dong = new ArrayList<>();
        private final List<RowError> loi = new ArrayList<>();
        private int tongDong;

        private int soThem() {
            return (int) dong.stream().filter(d -> d.publicIdHienCo == null).count();
        }

        private int soSua() {
            return (int) dong.stream().filter(d -> d.publicIdHienCo != null).count();
        }

        private ImportReport baoCao(boolean applied) {
            return new ImportReport(applied, tongDong, soThem(), soSua(), List.copyOf(loi));
        }
    }

    private KeHoach lapKeHoach(byte[] content) {
        List<SpreadsheetReader.Row> rows = SpreadsheetReader.read(content);
        KeHoach keHoach = new KeHoach();
        keHoach.tongDong = rows.size();

        if (rows.isEmpty()) {
            keHoach.loi.add(new RowError(1, null, "Tệp không có dòng dữ liệu nào"));
            return keHoach;
        }
        Set<String> cotCo = rows.get(0).cells().keySet();
        List<String> thieu =
                COT_BAT_BUOC.stream().filter(c -> !cotCo.contains(c)).toList();
        if (!thieu.isEmpty()) {
            keHoach.loi.add(new RowError(1, String.join(", ", thieu), "Tệp thiếu cột bắt buộc"));
            return keHoach;
        }

        // Trùng mã NGAY TRONG tệp: hai dòng cùng mã thì dòng sau ghi đè dòng trước và người nhập
        // không bao giờ biết mình vừa mất một hồ sơ.
        Set<String> maDaGap = new HashSet<>();

        for (SpreadsheetReader.Row row : rows) {
            List<RowError> loiDong = new ArrayList<>();
            ConstructionForm form = doc(row, loiDong, maDaGap);
            if (loiDong.isEmpty() && form != null) {
                keHoach.dong.add(new DongKeHoach(row.rowNumber(), form, publicIdHienCo(form.code())));
            } else {
                keHoach.loi.addAll(loiDong);
            }
        }
        return keHoach;
    }

    /**
     * Bản ghi đang có mang đúng mã này, <b>trong phạm vi đơn vị của người nhập</b>.
     *
     * <p>{@code null} = sẽ thêm mới. Xem ghi chú ở đầu lớp về trường hợp mã đang thuộc đơn vị khác:
     * kế hoạch nói "thêm mới", rồi chỉ mục duy nhất chặn lại bằng {@code OPS-2008}.
     */
    private UUID publicIdHienCo(String code) {
        return repository
                .findByCodeAndDeletedAtIsNull(code.trim().toUpperCase(Locale.ROOT))
                .map(c -> c.getPublicId())
                .orElse(null);
    }

    private ConstructionForm doc(SpreadsheetReader.Row row, List<RowError> loi, Set<String> maDaGap) {
        int soDong = row.rowNumber();
        String ma = row.get(COT_MA);
        String ten = row.get(COT_TEN);

        if (ma == null) {
            loi.add(new RowError(soDong, COT_MA, "Thiếu mã công trình"));
        } else if (!maDaGap.add(ma.toUpperCase(Locale.ROOT))) {
            loi.add(new RowError(soDong, COT_MA, "Mã '%s' xuất hiện nhiều lần trong tệp".formatted(ma)));
        }
        if (ten == null) {
            loi.add(new RowError(soDong, COT_TEN, "Thiếu tên công trình"));
        }

        ConstructionType loai = nhan(NHAN_LOAI, row.get(COT_LOAI));
        if (loai == null) {
            loi.add(new RowError(soDong, COT_LOAI, "Loại công trình không nhận ra: '%s'".formatted(row.get(COT_LOAI))));
        }

        OrgUnitRef donVi = orgUnits.findRefByCode(row.get(COT_DON_VI)).orElse(null);
        if (donVi == null) {
            loi.add(new RowError(soDong, COT_DON_VI, "Không có đơn vị mã '%s'".formatted(row.get(COT_DON_VI))));
        }

        BigDecimal viDo = so(row.get("vi_do"), soDong, "vi_do", loi);
        BigDecimal kinhDo = so(row.get("kinh_do"), soDong, "kinh_do", loi);
        if ((viDo == null) != (kinhDo == null)) {
            loi.add(new RowError(soDong, "vi_do", "Toạ độ phải đủ cả vĩ độ và kinh độ"));
        }

        UUID cum = maCum(row.get("ma_cum"), soDong, loi);

        if (!loi.isEmpty()) {
            return null;
        }
        return new ConstructionForm(
                ma,
                ten,
                loai,
                nhan(NHAN_NHIEM_VU, row.get("nhiem_vu")),
                donVi.publicId(),
                Optional.ofNullable(nhan(NHAN_CAP, row.get("cap_quan_ly"))).orElse(ManagementLevel.XI_NGHIEP),
                cum,
                row.get("dia_chi"),
                viDo,
                kinhDo,
                row.get("tuyen_song"),
                row.get("ly_trinh"),
                row.get("luu_vuc"),
                nam(row.get("nam_xay_dung"), soDong, "nam_xay_dung", loi),
                nam(row.get("nam_su_dung"), soDong, "nam_su_dung", loi),
                row.get("don_vi_thiet_ke"),
                row.get("don_vi_thi_cong"),
                so(row.get("tong_von_vnd"), soDong, "tong_von_vnd", loi),
                row.get("mo_ta"),
                null,
                null,
                null);
    }

    private UUID maCum(String maCum, int soDong, List<RowError> loi) {
        if (maCum == null) {
            return null;
        }
        return clusters.findByCodeAndDeletedAtIsNull(maCum.toUpperCase(Locale.ROOT))
                .map(c -> c.getPublicId())
                .orElseGet(() -> {
                    loi.add(new RowError(soDong, "ma_cum", "Không có cụm mã '%s'".formatted(maCum)));
                    return null;
                });
    }

    private static <T> T nhan(Map<String, T> bang, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return bang.get(VietnameseUtils.removeDiacritics(value.trim()).toLowerCase(Locale.ROOT));
    }

    /** Nhóm hàng nghìn kiểu Việt Nam: {@code 1.500.000}. Dùng để phân biệt với số thập phân. */
    private static final Pattern NHOM_HANG_NGHIN = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    /**
     * Đọc số từ ô do người dùng gõ.
     *
     * <p>⚠⚠ <b>Dấu chấm là chỗ nguy hiểm nhất của cả lượt nhập.</b> Tiếng Việt dùng "." ngăn hàng
     * nghìn, trong khi toạ độ GPS luôn viết "21.023456" với "." là dấu thập phân. Quy tắc "bỏ hết
     * dấu chấm" biến vĩ độ 21,023456 thành <b>21023456</b> — một điểm ở giữa đại dương. Ràng buộc
     * CHECK ở CSDL bắt được vĩ độ ngoài [-90, 90], nhưng đừng dựa vào đó: nó không bắt được sai số
     * nhỏ hơn, và một công trình đặt lệch vài trăm mét trên bản đồ điều hành thì không ai phát hiện
     * bằng mắt.
     *
     * <p>Phân biệt bằng <i>hình dạng</i> thay vì đoán theo ngôn ngữ:
     *
     * <ul>
     *   <li>Có cả "." và "," → "." là hàng nghìn, "," là thập phân (kiểu Việt Nam đầy đủ).
     *   <li>Chỉ có "." và khớp dạng {@code 1.500.000} → hàng nghìn.
     *   <li>Còn lại → "." hoặc "," là dấu thập phân.
     * </ul>
     */
    private static BigDecimal so(String value, int soDong, String cot, List<RowError> loi) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sach = value.replaceAll("[\\s\\u00a0]", "");
        if (sach.contains(".") && sach.contains(",")) {
            sach = sach.replace(".", "").replace(",", ".");
        } else if (NHOM_HANG_NGHIN.matcher(sach).matches()) {
            sach = sach.replace(".", "");
        } else {
            sach = sach.replace(",", ".");
        }
        try {
            return new BigDecimal(sach);
        } catch (NumberFormatException e) {
            loi.add(new RowError(soDong, cot, "Không phải số: '%s'".formatted(value)));
            return null;
        }
    }

    private static Short nam(String value, int soDong, String cot, List<RowError> loi) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Excel hay trả số nguyên dưới dạng "1998.0" — cắt phần thập phân trước khi đọc.
            int nam = new BigDecimal(value.trim()).intValue();
            if (nam < 1900 || nam > 2200) {
                loi.add(new RowError(soDong, cot, "Năm ngoài khoảng hợp lệ: '%s'".formatted(value)));
                return null;
            }
            return (short) nam;
        } catch (NumberFormatException e) {
            loi.add(new RowError(soDong, cot, "Không phải năm: '%s'".formatted(value)));
            return null;
        }
    }
}
