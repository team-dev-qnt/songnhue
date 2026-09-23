package com.songnhue.operations.application.importer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Truy vấn tìm bản ghi trùng mã chạy qua bộ lọc tầng 3, nên người của Xí nghiệp A <b>không thấy</b>
 * bản ghi của Xí nghiệp B. <b>⚠ Đoạn dưới đây đã ĐỔI ngày 23/09/2026 (T81.4)</b> — bản trước khai
 * rằng để kế hoạch nói <i>"thêm mới"</i> rồi cho chỉ mục duy nhất chặn bằng {@code OPS-2008} là
 * <i>chủ ý</i>, vì <i>"nói rõ hơn là tiết lộ dữ liệu ngoài phạm vi"</i>.
 *
 * <p>Lý do ấy đo ra là <b>đúng một nửa</b>: {@code OPS-2008} (<i>"Mã công trình đã tồn tại"</i>)
 * <b>đã</b> tiết lộ chính sự tồn tại ấy. Thứ khác nhau thật là <b>thời điểm</b> và <b>chất lượng</b>
 * của thông báo — lỗi cũ nổ <b>giữa lượt ghi</b>, giao dịch cuộn lại, người nhập mất cả tệp và ⛔
 * biết dòng nào hỏng. Nay {@link #traMa} hỏi toàn Công ty ngay ở bước lập kế hoạch và trả một
 * <b>dòng lỗi chỉ đúng chỗ sai</b>, theo tiền lệ {@code EmployeeImportService} (WS-74c) — tiết lộ
 * sự tồn tại, ⛔ tiết lộ đơn vị nào.
 */
@Service
public class ConstructionImportService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionImportService.class);

    private static final String COT_MA = "ma_cong_trinh";
    private static final String COT_TEN = "ten_cong_trinh";
    private static final String COT_LOAI = "loai_cong_trinh";
    private static final String COT_DON_VI = "ma_don_vi";

    /**
     * ⭐ Danh mục cột của tệp nhập — <b>một nguồn</b>, và nó sinh ra chính tệp mẫu.
     *
     * <p>Lý lẽ đầy đủ ở {@link CotMau}. Ở đây chỉ giữ phần riêng của danh mục công trình:
     * ⚠ <b>thứ tự khai là thứ tự cột trong tệp mẫu</b>, và 4 cột bắt buộc đứng trước để người lập
     * tệp thấy ngay phần ⛔ không được bỏ trống.
     */
    public static final List<CotMau> COT_MAU = List.of(
            new CotMau(COT_MA, true, "BẮT BUỘC · mã duy nhất, tự động viết HOA"),
            new CotMau(COT_TEN, true, "BẮT BUỘC · tên đầy đủ của công trình"),
            new CotMau(COT_LOAI, true, "BẮT BUỘC · Trạm bơm | Cống | Kênh mương | Đê điều | Khác"),
            new CotMau(COT_DON_VI, true, "BẮT BUỘC · mã đơn vị quản lý, ví dụ CTY"),
            new CotMau("nhiem_vu", false, "Tưới | Tiêu | Hỗn hợp"),
            new CotMau("cap_quan_ly", false, "Công ty | Xí nghiệp | Cụm — bỏ trống thì mặc định Xí nghiệp"),
            new CotMau("ma_cum", false, "Mã cụm công trình, phải có sẵn trong danh mục cụm"),
            new CotMau("dia_chi", false, "Địa chỉ hành chính"),
            new CotMau("vi_do", false, "Vĩ độ WGS-84, dấu chấm thập phân — ví dụ 21.023456"),
            new CotMau("kinh_do", false, "Kinh độ WGS-84 — phải có ĐỦ CẢ HAI hoặc bỏ trống cả hai"),
            new CotMau("tuyen_song", false, "Ví dụ: Sông Nhuệ"),
            new CotMau("ly_trinh", false, "Dạng K<km>+<m>, ví dụ K43+750"),
            // ⚠ T75.2 — giữ NGUYÊN khoá `luu_vuc`: Công ty có thể đang giữ tệp đã điền theo tên cũ,
            //    và đổi khoá là đẩy mọi tệp ấy vào nhánh "cột lạ". Thứ đổi là phần MÔ TẢ — nó in ra
            //    tệp mẫu, và đây là chỗ DUY NHẤT tệp mẫu nói được rằng ô này chính là cột
            //    "Nguồn tưới, hướng tiêu" của mẫu Báo cáo nhanh (cùng một `constructions.basin_note`).
            new CotMau("luu_vuc", false, "Nguồn tưới, hướng tiêu / lưu vực — ví dụ: Sông Đáy"),
            new CotMau("nam_xay_dung", false, "Số nguyên trong khoảng 1900–2200"),
            new CotMau("nam_su_dung", false, "Số nguyên trong khoảng 1900–2200"),
            new CotMau("don_vi_thiet_ke", false, "Tên đơn vị thiết kế"),
            new CotMau("don_vi_thi_cong", false, "Tên đơn vị thi công"),
            new CotMau("tong_von_vnd", false, "VNĐ — chấp nhận 1.500.000 hoặc 1500000"),
            new CotMau("mo_ta", false, "Ghi chú tự do"));

    /**
     * Cột bắt buộc — <b>suy từ {@link #COT_MAU}</b>, ⛔ không khai lại.
     *
     * <p>Trước 09/09/2026 đây là một {@code List.of(...)} riêng. Hai danh sách nói cùng một điều là
     * đúng chỗ luật 14 canh: thêm một cột bắt buộc mà quên sửa danh sách kia thì tệp mẫu và bộ đọc
     * lệch nhau, và ⛔ không có gì nói ra.
     */
    private static final List<String> COT_BAT_BUOC = CotMau.tenBatBuoc(COT_MAU);

    /** Tệp mẫu CSV — tiêu đề + một dòng mô tả. Xem {@link BieuMauCsv} về vì sao ⛔ không dùng dòng ví dụ. */
    public static byte[] bieuMau() {
        return BieuMauCsv.dung(COT_MAU);
    }

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
    private final ScopeGuard scopeGuard;

    public ConstructionImportService(
            ConstructionService constructions,
            ConstructionRepository repository,
            ConstructionClusterRepository clusters,
            OrgUnitPort orgUnits,
            ScopeGuard scopeGuard) {
        this.constructions = constructions;
        this.repository = repository;
        this.clusters = clusters;
        this.orgUnits = orgUnits;
        this.scopeGuard = scopeGuard;
    }

    /** Xem trước — <b>không ghi một dòng nào</b>, kể cả khi tệp hoàn toàn hợp lệ. */
    @Transactional(readOnly = true)
    public KetQuaNhap preview(byte[] content) {
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
    public KetQuaNhap apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }
        for (DongKeHoach dong : keHoach.dong) {
            if (dong.publicIdHienCo == null) {
                constructions.create(dong.form);
            } else {
                // ⛔⛔ T47.14 — ⛔ KHÔNG gọi `update()`: nó THAY TOÀN PHẦN, mà tệp mẫu chỉ mang
                //    19/24 trường cấp 1 và 0/26 ô thông số ⇒ mỗi lượt nhập lại xoá trắng phần còn
                //    lại, ⛔ không một dòng log. Xem javadoc `capNhatTuTepNhap`.
                constructions.capNhatTuTepNhap(dong.publicIdHienCo, dong.form);
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
        private final List<LoiDong> loi = new ArrayList<>();
        private int tongDong;

        private int soThem() {
            return (int) dong.stream().filter(d -> d.publicIdHienCo == null).count();
        }

        private int soSua() {
            return (int) dong.stream().filter(d -> d.publicIdHienCo != null).count();
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

        // Trùng mã NGAY TRONG tệp: hai dòng cùng mã thì dòng sau ghi đè dòng trước và người nhập
        // không bao giờ biết mình vừa mất một hồ sơ.
        Set<String> maDaGap = new HashSet<>();

        for (SpreadsheetReader.Row row : rows) {
            List<LoiDong> loiDong = new ArrayList<>();
            ConstructionForm form = doc(row, loiDong, maDaGap);
            if (loiDong.isEmpty() && form != null) {
                TraMa tra = traMa(form.code());
                if (tra.ngoaiPhamVi()) {
                    keHoach.loi.add(new LoiDong(
                            row.rowNumber(),
                            COT_MA,
                            "Mã '%s' đã thuộc một công trình ngoài phạm vi đơn vị của bạn".formatted(form.code())));
                } else {
                    keHoach.dong.add(new DongKeHoach(row.rowNumber(), form, tra.publicIdHienCo()));
                }
            } else {
                keHoach.loi.addAll(loiDong);
            }
        }
        return keHoach;
    }

    /** Tra một mã: bản ghi trong phạm vi (nếu có), và mã ấy có đang thuộc đơn vị khác ⛔. */
    private record TraMa(UUID publicIdHienCo, boolean ngoaiPhamVi) {}

    /**
     * Bản ghi đang có mang đúng mã này, <b>trong phạm vi đơn vị của người nhập</b> — và nếu ⛔ có,
     * hỏi tiếp <b>toàn Công ty</b> xem mã đã bị chiếm chưa.
     *
     * <h2>⛔⛔ Vì sao vế thứ hai phải có (T81.4 — 23/09/2026)</h2>
     *
     * <p>Bản trước chỉ tra trong phạm vi, và ghi chú ở đầu lớp khai đó là <b>chủ ý</b>: mã thuộc
     * đơn vị khác ⇒ kế hoạch nói <i>"thêm mới"</i> ⇒ {@code ConstructionService.create} đâm vào chỉ
     * mục duy nhất và trả {@code OPS-2008}, vì <i>"nói rõ hơn là tiết lộ dữ liệu ngoài phạm vi"</i>.
     *
     * <p>Lập luận ấy <b>tự bác một nửa</b>: {@code OPS-2008} — <i>"Mã công trình đã tồn tại"</i> —
     * <b>đã</b> tiết lộ đúng cái sự tồn tại ấy rồi. Hai đường chỉ khác nhau ở <b>lúc nào</b> và
     * <b>rõ tới đâu</b>, ⛔ khác nhau về bản chất. Cái giá của việc biết muộn thì đo được: lỗi nổ
     * <b>giữa lượt ghi</b>, {@code @Transactional} cuộn lại, người nhập mất <b>cả tệp</b> và ⛔
     * biết dòng nào hỏng.
     *
     * <p>⚠ Và kho đã có một quyết định <b>NGƯỢC LẠI, viết sau</b>: {@code EmployeeImportService}
     * (WS-74c, 20/09) hỏi toàn Công ty rồi báo <i>"Mã đã thuộc một hồ sơ ngoài phạm vi đơn vị của
     * bạn"</i> — trên dữ liệu <b>nhạy cảm hơn</b> (hồ sơ CBNV, NĐ 13/2023). Hai bộ nhập trả lời
     * ngược nhau cho cùng một câu hỏi riêng tư, cả hai đều tự khai là chủ ý — đúng hình dạng T47.19
     * (<i>hai tiền lệ của kho mâu thuẫn nhau thì phải đo mới phân xử được</i>).
     *
     * <p>⇒ Theo tiền lệ MỚI hơn và chặt hơn: tiết lộ <b>sự tồn tại</b>, ⛔ tiết lộ <b>đơn vị nào</b>.
     * ⬜ QuanTran muốn giữ cách cũ thì đảo lại ở đây — xem {@code T81.4} trong sổ.
     */
    private TraMa traMa(String code) {
        String ma = code.trim().toUpperCase(Locale.ROOT);
        UUID trongPhamVi = repository
                .findByCodeAndDeletedAtIsNull(ma)
                .map(c -> c.getPublicId())
                .orElse(null);
        if (trongPhamVi != null) {
            return new TraMa(trongPhamVi, false);
        }
        return new TraMa(null, scopeGuard.toanCongTy(() -> repository.existsByCodeAndDeletedAtIsNull(ma)));
    }

    private ConstructionForm doc(SpreadsheetReader.Row row, List<LoiDong> loi, Set<String> maDaGap) {
        int soDong = row.rowNumber();
        String ma = row.get(COT_MA);
        String ten = row.get(COT_TEN);

        if (ma == null) {
            loi.add(new LoiDong(soDong, COT_MA, "Thiếu mã công trình"));
        } else if (!maDaGap.add(ma.toUpperCase(Locale.ROOT))) {
            loi.add(new LoiDong(soDong, COT_MA, "Mã '%s' xuất hiện nhiều lần trong tệp".formatted(ma)));
        }
        if (ten == null) {
            loi.add(new LoiDong(soDong, COT_TEN, "Thiếu tên công trình"));
        }

        ConstructionType loai = nhan(NHAN_LOAI, row.get(COT_LOAI));
        if (loai == null) {
            loi.add(new LoiDong(soDong, COT_LOAI, "Loại công trình không nhận ra: '%s'".formatted(row.get(COT_LOAI))));
        }

        OrgUnitRef donVi = orgUnits.findRefByCode(row.get(COT_DON_VI)).orElse(null);
        if (donVi == null) {
            loi.add(new LoiDong(soDong, COT_DON_VI, "Không có đơn vị mã '%s'".formatted(row.get(COT_DON_VI))));
        }

        BigDecimal viDo = so(row.get("vi_do"), soDong, "vi_do", loi);
        BigDecimal kinhDo = so(row.get("kinh_do"), soDong, "kinh_do", loi);
        if ((viDo == null) != (kinhDo == null)) {
            loi.add(new LoiDong(soDong, "vi_do", "Toạ độ phải đủ cả vĩ độ và kinh độ"));
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
                // ⛔ T47.14 — BỎ `.orElse(XI_NGHIEP)`: nó biến một ô TRỐNG thành một lệnh GHI.
                //   11 hồ sơ đã có mang `CONG_TY` (V202609091075), nên mặc định ở đây sẽ lặng lẽ
                //   hạ cấp quản lý của chúng ở lượt nhập kế tiếp. Đường TẠO MỚI vẫn có mặc định —
                //   `apDung()` áp `XI_NGHIEP` khi trường này null, nên hành vi tạo ⛔ không đổi.
                nhan(NHAN_CAP, row.get("cap_quan_ly")),
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
                // ⛔ Hai cột tài liệu công bố (Quy trình vận hành · Phương án bảo vệ) KHÔNG nhận
                //    từ tệp nhập, và đó là chủ ý: giá trị của chúng là `attachments.public_id` —
                //    một khoá ngoại tới tệp đã tải lên. Một ô trong Excel không tải tệp nào lên,
                //    nên nhận nó ở đây chỉ có hai kết cục: người nhập gõ một UUID không tồn tại
                //    (khoá ngoại chặn, báo lỗi khó hiểu), hoặc gõ đúng UUID của tệp người khác.
                //    Hai tài liệu này gắn ở màn hình hồ sơ công trình, sau khi tệp đã có.
                null,
                null,
                row.get("mo_ta"),
                null,
                null,
                null);
    }

    private UUID maCum(String maCum, int soDong, List<LoiDong> loi) {
        if (maCum == null) {
            return null;
        }
        return clusters.findByCodeAndDeletedAtIsNull(maCum.toUpperCase(Locale.ROOT))
                .map(c -> c.getPublicId())
                .orElseGet(() -> {
                    loi.add(new LoiDong(soDong, "ma_cum", "Không có cụm mã '%s'".formatted(maCum)));
                    return null;
                });
    }

    private static <T> T nhan(Map<String, T> bang, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return bang.get(VietnameseUtils.removeDiacritics(value.trim()).toLowerCase(Locale.ROOT));
    }

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
    private static BigDecimal so(String value, int soDong, String cot, List<LoiDong> loi) {
        try {
            return com.songnhue.core.common.util.NumericUtils.docSoNhapTay(value);
        } catch (NumberFormatException e) {
            loi.add(new LoiDong(soDong, cot, "Không phải số: '%s'".formatted(value)));
            return null;
        }
    }

    private static Short nam(String value, int soDong, String cot, List<LoiDong> loi) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Excel hay trả số nguyên dưới dạng "1998.0" — cắt phần thập phân trước khi đọc.
            int nam = new BigDecimal(value.trim()).intValue();
            if (nam < 1900 || nam > 2200) {
                loi.add(new LoiDong(soDong, cot, "Năm ngoài khoảng hợp lệ: '%s'".formatted(value)));
                return null;
            }
            return (short) nam;
        } catch (NumberFormatException e) {
            loi.add(new LoiDong(soDong, cot, "Không phải năm: '%s'".formatted(value)));
            return null;
        }
    }
}
