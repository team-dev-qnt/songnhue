package com.songnhue.operations.application.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.songnhue.core.common.util.NumericUtils;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.DanhMucMayBomService;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.NhomMayBom;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.NhomMayBomRepository;

/**
 * Nhập danh mục trạm bơm — <b>MỘT tệp dựng cả TRẠM lẫn NHÓM MÁY</b> (T75.6).
 *
 * <h2>Vì sao bộ nhập cũ ⛔ đủ, dù nó ⛔ sai dòng nào</h2>
 *
 * <p>Bản trước ({@code NhomMayBomImportService}) nhận đúng ba cột — {@code ma_cong_trinh},
 * {@code so_may}, {@code q_mot_may_m3h} — và đòi trạm đã có sẵn trong danh mục. Đặt cạnh thứ Công
 * ty thật sự gửi thì nó bỏ người vận hành lại giữa đường. Đo trên sheet {@code TB Tiêu (KH)} của
 * {@code Danh mục TB Cty SN 2026.xlsx}:
 *
 * <ul>
 *   <li><b>179 trạm · 227 dòng nhóm máy</b> — nhóm thứ hai của một trạm là một dòng <b>⛔ tên</b>
 *       nối ngay dưới, nên một sheet phải tách thành hai tệp khác cấu trúc.
 *   <li><b>0 cột mã công trình.</b> Định danh Công ty dùng là TÊN. Bộ nhập cũ đòi mã ⇒ người vận
 *       hành phải tự nghĩ ra <b>179 mã</b> trước khi nhập được dòng đầu tiên.
 *   <li><b>Xí nghiệp nằm ở dòng TIÊU ĐỀ</b> ({@code I | XNTL THANH TRÌ | 9 trạm}), ⛔ phải một cột.
 *   <li>Cột <i>"Nguồn tưới, hướng tiêu"</i> — thứ in ra cột cuối Bảng 2 của Báo cáo nhanh —
 *       <b>⛔ có chỗ nhận</b>: nó thuộc {@code constructions.basin_note}, mà bộ nhập cũ ⛔ chạm tới
 *       bảng {@code constructions}.
 * </ul>
 *
 * <p>⇒ Lớp này nhận <b>một tệp phẳng, một dòng một nhóm máy</b>, và tự lo cả hai bảng.
 *
 * <h2>Dòng NỐI TIẾP: ⛔ mã, ⛔ tên ⇒ thuộc trạm của dòng ngay trên</h2>
 *
 * <p>Đúng hình dạng sheet gốc. Dòng đầu tiên của tệp vì thế <b>phải</b> xác định được một trạm —
 * ⛔ thì ⛔ có "dòng trên" nào để thuộc về, và bộ đọc báo lỗi ngay dòng ấy thay vì gán bừa.
 *
 * <h2>Mã công trình: SINH khi thiếu, và sửa được sau</h2>
 *
 * <p>QuanTran chốt 20/09/2026. Bỏ trống {@code ma_cong_trinh} ⇒ tra theo <b>(đơn vị, tên)</b>; ⛔
 * thấy thì sinh mã theo đúng quy ước sẵn có của sản phẩm ({@code ConstructionService.suggestCode}
 * ⇒ {@code TB-<mã đơn vị>-001}). Mã sinh ra <b>sửa được</b> trên màn hình Hồ sơ công trình, nên nó
 * là một <i>giá trị khởi đầu</i> chứ ⛔ phải một định danh bị khoá — đúng điều javadoc của
 * {@code suggestCode} đã khai: <i>"gợi ý, ⛔ phải áp đặt"</i>.
 *
 * <p>⚠ Điền {@code ma_cong_trinh} thì mã ấy THẮNG, và lượt nhập sau vẫn khớp đúng hồ sơ cũ kể cả
 * khi Công ty đổi tên trạm. Đó là lý do cột ấy ở lại trong tệp mẫu dù ⛔ bắt buộc.
 *
 * <h2>⛔ Những cột của sheet gốc CỐ Ý ⛔ nhận</h2>
 *
 * <p>Cột {@code I}/{@code J} của sheet là <b>Diện tích Tưới/Tiêu (ha)</b>. Chốt <b>B5</b> (12/8/2026)
 * đã CẮT trường ấy khỏi phạm vi. Nhận nó vào đây là đảo một chốt nghiệp vụ bằng một lượt nhập tệp —
 * ⛔ ai duyệt, ⛔ ai thấy. Cột {@code H} <i>"Phân loại"</i> (lớn/vừa/nhỏ) cũng ⛔ có chỗ trong lược
 * đồ và ⛔ được tự chế ra một chỗ.
 *
 * <h2>⛔ Nhóm VẮNG khỏi tệp ⛔ bị xoá — và trạm cũng vậy</h2>
 *
 * <p>Tệp thường lập từng phần (một Xí nghiệp một tệp). Hiểu <i>"vắng"</i> thành <i>"xoá"</i> là để
 * lượt nhập thứ hai xoá sạch Xí nghiệp nhập trước, im lặng (T42.20 · T47.16). Xoá là nút riêng trên
 * màn hình, nơi thao tác ấy <b>nhìn thấy được</b> và chỉ chạm một bản ghi.
 *
 * <h2>⭐ Thứ tự dòng của tệp = thứ tự dòng của Bảng 2</h2>
 *
 * <p>Số TT trong mẫu Word có lỗi đánh số (Phú Xuyên nhảy 62→82; Ứng Hoà lặp 25) ⇒ ⛔ nhập cột TT;
 * thứ tự lấy theo vị trí dòng, và hệ tự đánh lại số khi in.
 */
@Service
public class TramBomImportService {

    private static final Logger log = LoggerFactory.getLogger(TramBomImportService.class);

    private static final String COT_MA = "ma_cong_trinh";
    private static final String COT_TEN = "ten_cong_trinh";
    private static final String COT_DON_VI = "ma_don_vi";
    private static final String COT_NGUON = "nguon_tuoi_huong_tieu";
    private static final String COT_DIA_DIEM = "dia_diem";
    private static final String COT_LY_TRINH = "ly_trinh";
    private static final String COT_SO_MAY = "so_may";
    private static final String COT_Q = "q_mot_may_m3h";

    /**
     * ⭐ Một nguồn — sinh ra chính tệp mẫu (luật 14).
     *
     * <p>⚠ Chỉ {@code so_may} và {@code q_mot_may_m3h} là bắt buộc ở tầng TIÊU ĐỀ. Ba cột định danh
     * trạm ràng buộc nhau theo dòng ({@link #kiemTieuDe}, {@link #docTram}) — khai chúng "bắt buộc"
     * ở đây sẽ chặn cả tệp chỉ bổ sung nhóm máy cho trạm đã có, tức là bỏ mất chính ca dùng mà bản
     * trước phục vụ.
     */
    public static final List<CotMau> COT_MAU = List.of(
            new CotMau(COT_MA, false, "Bỏ trống thì hệ SINH mã (TB-<mã đơn vị>-001) và sửa được sau"),
            new CotMau(COT_TEN, false, "Tên trạm bơm · bỏ trống CẢ cột này lẫn mã ⇒ là nhóm máy của dòng trên"),
            new CotMau(COT_DON_VI, false, "Mã Xí nghiệp phụ trách — BẮT BUỘC khi trạm chưa có trong danh mục"),
            new CotMau(COT_NGUON, false, "Nguồn tưới, hướng tiêu — in vào cột cuối Bảng 2, ví dụ: Sông Đáy"),
            new CotMau(COT_DIA_DIEM, false, "Địa điểm (xã, phường)"),
            new CotMau(COT_LY_TRINH, false, "Dạng K<km>+<m>, ví dụ K83+547"),
            new CotMau(COT_SO_MAY, true, "BẮT BUỘC · số máy của nhóm, số nguyên > 0"),
            new CotMau(COT_Q, true, "BẮT BUỘC · lưu lượng MỘT máy, m³/h — ví dụ 1.100 hoặc 43200"));

    private static final List<String> COT_BAT_BUOC = CotMau.tenBatBuoc(COT_MAU);

    public static byte[] bieuMau() {
        return BieuMauCsv.dung(COT_MAU);
    }

    private final ConstructionRepository constructions;
    private final ConstructionService constructionService;
    private final NhomMayBomRepository nhomMay;
    private final DanhMucMayBomService danhMuc;
    private final OrgUnitPort orgUnits;
    private final ScopeGuard scopeGuard;

    public TramBomImportService(
            ConstructionRepository constructions,
            ConstructionService constructionService,
            NhomMayBomRepository nhomMay,
            DanhMucMayBomService danhMuc,
            OrgUnitPort orgUnits,
            ScopeGuard scopeGuard) {
        this.constructions = constructions;
        this.constructionService = constructionService;
        this.nhomMay = nhomMay;
        this.danhMuc = danhMuc;
        this.orgUnits = orgUnits;
        this.scopeGuard = scopeGuard;
    }

    /** Xem trước — <b>⛔ ghi một dòng nào</b>, kể cả khi tệp hoàn toàn hợp lệ. */
    @Transactional(readOnly = true)
    public KetQuaNhap preview(byte[] content) {
        return lapKeHoach(content).baoCao(false);
    }

    /**
     * Nhập thật — chạy lại {@link #lapKeHoach}; còn một dòng lỗi thì ⛔ dòng nào được ghi
     * ({@code OPS-2016}).
     *
     * <p>⚠ Trạm ghi TRƯỚC, nhóm máy ghi SAU, và nhóm tra id trạm qua bảng {@code idTheoKhoa} dựng
     * trong chính lượt này — trạm vừa tạo chưa có id lúc lập kế hoạch, nên kế hoạch mang <b>khoá
     * của tệp</b> chứ ⛔ mang id.
     */
    @Transactional
    public KetQuaNhap apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }

        Map<String, Long> idTheoKhoa = new HashMap<>();
        for (DongTram t : keHoach.tram.values()) {
            Construction ct = t.publicIdHienCo == null
                    ? constructionService.create(t.form)
                    : constructionService.capNhatTuTepNhap(t.publicIdHienCo, t.form);
            idTheoKhoa.put(t.khoa, ct.getId());
        }
        for (DongNhom n : keHoach.nhom) {
            Long ctId = idTheoKhoa.get(n.khoaTram);
            NhomMayBom hienCo = nhomMay.timTheoKhoa(ctId, n.q).orElse(null);
            if (hienCo == null) {
                nhomMay.save(new NhomMayBom(ctId, n.soMay, n.q, n.thuTu));
            } else {
                hienCo.setSoMay(n.soMay);
                hienCo.setSortOrder(n.thuTu);
                nhomMay.save(hienCo);
            }
        }
        log.info(
                "Nhập trạm bơm: trạm thêm {} · trạm sửa {} · nhóm máy {} dòng",
                keHoach.soTramThem(),
                keHoach.soTramSua(),
                keHoach.nhom.size());
        return keHoach.baoCao(true);
    }

    // =========================================================================
    // Kế hoạch
    // =========================================================================

    /**
     * Cấp mã cho các trạm MỚI trong <b>một lượt nhập</b>.
     *
     * <h2>⛔⛔ Vì sao ⛔ gọi thẳng {@code suggestCode} cho từng trạm</h2>
     *
     * <p>{@code suggestCode} chỉ nhìn <b>CSDL</b>. Trong một lượt nhập, ⛔ trạm nào đã được ghi lúc
     * lập kế hoạch ⇒ gọi nó hai lần trả về <b>CÙNG một mã</b>. Bản nháp đầu làm đúng như vậy và tệp
     * mẫu hai trạm mới đâm thẳng vào {@code OPS-2008}: <i>"Mã TB-CTY-001 đã tồn tại"</i> — người vận
     * hành đọc câu ấy sẽ đi tìm một trạm trùng mã ⛔ hề tồn tại. Bài {@code motTepDungCaTramVaNhomMay}
     * bắt được ở lượt chạy đầu.
     *
     * <p>⇒ Đếm tiếp <b>trong bộ nhớ</b> từ gợi ý đầu tiên của mỗi đơn vị, và <b>nhảy qua</b> mã đã
     * bị chiếm — bởi CSDL hoặc bởi chính một dòng khác của tệp có điền {@code ma_cong_trinh}.
     */
    private final class CapMaTrongLuot {
        private final Map<Long, String> tienTo = new HashMap<>();
        private final Map<Long, Integer> soKe = new HashMap<>();
        private final Set<String> daCap = new HashSet<>();

        private void giuCho(String ma) {
            daCap.add(ma);
        }

        private String cap(OrgUnitRef donVi) {
            if (!tienTo.containsKey(donVi.id())) {
                String goiY = constructionService.suggestCode(ConstructionType.TRAM_BOM, donVi.publicId());
                int cat = goiY.lastIndexOf('-') + 1;
                tienTo.put(donVi.id(), goiY.substring(0, cat));
                soKe.put(donVi.id(), Integer.parseInt(goiY.substring(cat)));
            }
            String pre = tienTo.get(donVi.id());
            int n = soKe.get(donVi.id());
            String ma;
            do {
                ma = "%s%03d".formatted(pre, n++);
            } while (daCap.contains(ma) || daCoTrongCongTy(ma));
            soKe.put(donVi.id(), n);
            daCap.add(ma);
            return ma;
        }

        /**
         * Mã này đã có trong <b>TOÀN Công ty</b> chưa — <b>⛔ chỉ trong phạm vi người đang nhập</b> (T74.9).
         *
         * <p>{@code constructions.code} là {@code UNIQUE} trên cả bảng, nên câu hỏi <i>"mã còn trống
         * ⛔"</i> là một câu hỏi <b>toàn Công ty</b>. Hỏi nó qua bộ lọc phạm vi thì mã do một Xí nghiệp
         * khác đang giữ là <b>vô hình</b> ⇒ vòng lặp nhận nó là trống ⇒ {@code ConstructionService.create}
         * đâm vào ràng buộc thật và người vận hành nhận {@code OPS-2008} <i>"Mã … đã tồn tại"</i> về một
         * trạm họ <b>⛔ nhìn thấy được</b> — đúng ngõ cụt mà T74.9 đã đo ở ba service khác.
         *
         * <p>⚠ Vế này chỉ trả về <b>có/⛔</b>; ⛔ bản ghi nào của đơn vị khác lọt ra ngoài (quy tắc 5).
         */
        private boolean daCoTrongCongTy(String ma) {
            return scopeGuard.toanCongTy(() -> constructions.existsByCodeAndDeletedAtIsNull(ma));
        }
    }

    private record DongTram(String khoa, ConstructionForm form, UUID publicIdHienCo) {}

    private record DongNhom(String khoaTram, short soMay, BigDecimal q, int thuTu, boolean daCo) {}

    private static final class KeHoach {
        /** Giữ THỨ TỰ xuất hiện trong tệp — thứ tự ấy là thứ tự dòng của Bảng 2. */
        private final Map<String, DongTram> tram = new LinkedHashMap<>();

        private final List<DongNhom> nhom = new ArrayList<>();
        private final List<LoiDong> loi = new ArrayList<>();
        private int tongDong;

        private int soTramThem() {
            return (int)
                    tram.values().stream().filter(t -> t.publicIdHienCo == null).count();
        }

        private int soTramSua() {
            return tram.size() - soTramThem();
        }

        /**
         * {@code toCreate}/{@code toUpdate} đếm theo <b>DÒNG của tệp</b> (mỗi dòng một nhóm máy) và
         * cộng thêm số TRẠM mới — người vận hành cần thấy con số đáng sợ nhất: bao nhiêu hồ sơ công
         * trình sắp ra đời.
         */
        private KetQuaNhap baoCao(boolean applied) {
            int them = soTramThem() + (int) nhom.stream().filter(n -> !n.daCo).count();
            int sua = soTramSua() + (int) nhom.stream().filter(n -> n.daCo).count();
            return new KetQuaNhap(applied, tongDong, them, sua, List.copyOf(loi));
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
        if (!kiemTieuDe(rows.get(0).cells().keySet(), keHoach.loi)) {
            return keHoach;
        }

        BangCoMayBom bang = danhMuc.bangCo();
        CapMaTrongLuot capMa = new CapMaTrongLuot();
        Set<String> khoaNhomDaGap = new HashSet<>();
        String khoaTramHienTai = null;

        for (SpreadsheetReader.Row row : rows) {
            int soDong = row.rowNumber();
            int soLoiTruoc = keHoach.loi.size();

            boolean laDongNoiTiep = trong(row.get(COT_MA)) == null && trong(row.get(COT_TEN)) == null;
            if (laDongNoiTiep) {
                if (khoaTramHienTai == null) {
                    keHoach.loi.add(new LoiDong(
                            soDong,
                            COT_TEN,
                            "Dòng đầu tiên phải xác định được công trình — điền mã hoặc tên trạm bơm"));
                }
            } else {
                khoaTramHienTai = docTram(row, soDong, keHoach, capMa);
            }

            Short soMay = soMay(row.get(COT_SO_MAY), soDong, keHoach.loi);
            BigDecimal q = luuLuong(row.get(COT_Q), soDong, keHoach.loi);
            if (q != null) {
                try {
                    bang.xep(q);
                } catch (BusinessRuleException e) {
                    keHoach.loi.add(new LoiDong(
                            soDong, COT_Q, "Q = %s m³/h không thuộc cỡ máy nào".formatted(q.toPlainString())));
                }
            }
            if (khoaTramHienTai != null && q != null && !khoaNhomDaGap.add(khoaTramHienTai + "|" + q.toPlainString())) {
                keHoach.loi.add(new LoiDong(
                        soDong, COT_Q, "Trạm bơm này có hai dòng cùng Q = %s".formatted(q.toPlainString())));
            }

            if (keHoach.loi.size() == soLoiTruoc) {
                DongTram t = keHoach.tram.get(khoaTramHienTai);
                boolean daCo = t.publicIdHienCo != null
                        && nhomMay.timTheoKhoa(idCua(t.publicIdHienCo), q).isPresent();
                keHoach.nhom.add(new DongNhom(khoaTramHienTai, soMay, q, soDong, daCo));
            }
        }
        return keHoach;
    }

    /**
     * Tiêu đề phải có hai cột số, <b>và</b> ít nhất một cột định danh trạm.
     *
     * <p>Thiếu cả {@code ma_cong_trinh} lẫn {@code ten_cong_trinh} thì MỌI dòng đều là "dòng nối
     * tiếp" và bộ đọc sẽ nhả ra một lỗi cho từng dòng — một màn hình đầy lỗi ⛔ nói được điều thật
     * sự sai là <b>cái tiêu đề</b>. Bắt một lần ở đây thì người dùng đọc đúng một câu.
     */
    private static boolean kiemTieuDe(Set<String> cotCo, List<LoiDong> loi) {
        List<String> thieu =
                COT_BAT_BUOC.stream().filter(c -> !cotCo.contains(c)).toList();
        if (!thieu.isEmpty()) {
            loi.add(new LoiDong(1, String.join(", ", thieu), "Tệp thiếu cột bắt buộc"));
            return false;
        }
        if (!cotCo.contains(COT_MA) && !cotCo.contains(COT_TEN)) {
            loi.add(new LoiDong(
                    1,
                    COT_MA + " / " + COT_TEN,
                    "Tệp phải có ít nhất một cột định danh trạm bơm: '%s' hoặc '%s'".formatted(COT_MA, COT_TEN)));
            return false;
        }
        return true;
    }

    /**
     * Đọc phần TRẠM của một dòng và ghi vào kế hoạch; trả về khoá của trạm trong tệp.
     *
     * <p>Trả {@code null} khi dòng có lỗi — nơi gọi dựa vào đó để ⛔ gắn nhóm máy vào một trạm ⛔
     * dựng được.
     */
    private String docTram(SpreadsheetReader.Row row, int soDong, KeHoach keHoach, CapMaTrongLuot capMa) {
        String ma = trong(row.get(COT_MA));
        String ten = trong(row.get(COT_TEN));
        String maDonVi = trong(row.get(COT_DON_VI));

        Construction hienCo = null;
        if (ma != null) {
            hienCo = constructions
                    .findByCodeAndDeletedAtIsNull(ma.toUpperCase(Locale.ROOT))
                    .orElse(null);
        }

        OrgUnitRef donVi = null;
        if (maDonVi != null) {
            donVi = orgUnits.findRefByCode(maDonVi).orElse(null);
            if (donVi == null) {
                keHoach.loi.add(new LoiDong(soDong, COT_DON_VI, "Không có đơn vị mã '%s'".formatted(maDonVi)));
                return null;
            }
        }

        // ⛔ Có mã mà ⛔ tra ra ⇒ tra tiếp theo (đơn vị, tên): Công ty có thể vừa đổi mã trên tệp.
        if (hienCo == null && ten != null && donVi != null) {
            List<Construction> trung = constructions.timTheoDonViVaTen(donVi.id(), khoaTen(ten));
            if (trung.size() > 1) {
                keHoach.loi.add(new LoiDong(
                        soDong,
                        COT_TEN,
                        "Đơn vị '%s' đang có %d trạm cùng tên '%s' — điền '%s' để chỉ rõ hồ sơ nào"
                                .formatted(maDonVi, trung.size(), ten, COT_MA)));
                return null;
            }
            hienCo = trung.isEmpty() ? null : trung.get(0);
        }

        if (hienCo == null) {
            return tramMoi(ma, ten, maDonVi, donVi, row, soDong, keHoach, capMa);
        }
        return tramDaCo(hienCo, ma, ten, donVi, row, soDong, keHoach);
    }

    private String tramMoi(
            String ma,
            String ten,
            String maDonVi,
            OrgUnitRef donVi,
            SpreadsheetReader.Row row,
            int soDong,
            KeHoach keHoach,
            CapMaTrongLuot capMa) {
        if (ten == null) {
            keHoach.loi.add(new LoiDong(soDong, COT_MA, "Không có công trình mã '%s'".formatted(ma)));
            return null;
        }
        if (donVi == null) {
            keHoach.loi.add(new LoiDong(
                    soDong,
                    COT_DON_VI,
                    "Trạm '%s' chưa có trong danh mục — phải điền '%s' để biết Xí nghiệp nào phụ trách"
                            .formatted(ten, COT_DON_VI)));
            return null;
        }
        String khoa = khoaTram(ma, donVi, ten);
        if (keHoach.tram.containsKey(khoa)) {
            return khoa; // dòng lặp lại cùng một trạm — giữ bản đọc đầu tiên
        }
        String maDung;
        if (ma != null) {
            maDung = ma.toUpperCase(Locale.ROOT);
            capMa.giuCho(maDung); // mã người dùng điền CHIẾM CHỖ — lượt sinh sau phải nhảy qua nó
        } else {
            maDung = capMa.cap(donVi);
        }
        keHoach.tram.put(khoa, new DongTram(khoa, form(maDung, ten, donVi, row, true), null));
        return khoa;
    }

    private String tramDaCo(
            Construction hienCo,
            String ma,
            String ten,
            OrgUnitRef donVi,
            SpreadsheetReader.Row row,
            int soDong,
            KeHoach keHoach) {
        if (hienCo.getConstructionType() != ConstructionType.TRAM_BOM) {
            keHoach.loi.add(new LoiDong(
                    soDong,
                    ma != null ? COT_MA : COT_TEN,
                    "Công trình '%s' không phải trạm bơm".formatted(hienCo.getCode())));
            return null;
        }
        String khoa = "id:" + hienCo.getPublicId();
        if (keHoach.tram.containsKey(khoa)) {
            return khoa;
        }
        // ⚠ Đơn vị lấy từ hồ sơ ĐANG CÓ khi tệp ⛔ nói gì: một lượt nhập bổ sung nhóm máy ⛔ được
        //   lặng lẽ chuyển trạm sang Xí nghiệp khác chỉ vì cột `ma_don_vi` để trống.
        OrgUnitRef donViDung = donVi != null
                ? donVi
                : orgUnits.findRefById(hienCo.getOrgUnitId()).orElse(null);
        if (donViDung == null) {
            keHoach.loi.add(
                    new LoiDong(soDong, COT_DON_VI, "Không đọc được đơn vị của trạm '%s'".formatted(hienCo.getCode())));
            return null;
        }
        String maDung = ma != null ? ma.toUpperCase(Locale.ROOT) : hienCo.getCode();
        String tenDung = ten != null ? ten : hienCo.getName();
        keHoach.tram.put(khoa, new DongTram(khoa, form(maDung, tenDung, donViDung, row, false), hienCo.getPublicId()));
        return khoa;
    }

    /**
     * Biểu mẫu gửi sang {@code ConstructionService}.
     *
     * <p>⚠⚠ Mọi ô ⛔ có trong tệp để {@code null}, và {@code capNhatTuTepNhap} hiểu {@code null} là
     * <b>"⛔ đổi"</b> (T47.14). Đây là chỗ một bản nháp cẩu thả sẽ xoá trắng toạ độ G8: điền đủ
     * {@code new ConstructionForm(...)} bằng giá trị mặc định thay vì {@code null} là biến mỗi lượt
     * nhập thành một lượt xoá.
     *
     * <p>⛔⛔ {@code managementLevel} để {@code null} ở đường CẬP NHẬT và {@code XI_NGHIEP} ở đường
     * TẠO. Bản nháp đầu truyền {@code XI_NGHIEP} cho cả hai — nghĩa là một lượt nhập chỉ định bổ
     * sung nhóm máy sẽ <b>lặng lẽ hạ cấp quản lý</b> của mọi trạm trong tệp, kể cả trạm cấp Công ty.
     * ⛔ Một dòng lỗi, ⛔ một dòng log: đúng hình dạng §11.19, và nó chỉ có triệu chứng ở màn hình
     * danh sách vài ngày sau.
     */
    private ConstructionForm form(String ma, String ten, OrgUnitRef donVi, SpreadsheetReader.Row row, boolean taoMoi) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.TRAM_BOM,
                null,
                donVi.publicId(),
                taoMoi ? ManagementLevel.XI_NGHIEP : null,
                null,
                trong(row.get(COT_DIA_DIEM)),
                null,
                null,
                null,
                trong(row.get(COT_LY_TRINH)),
                trong(row.get(COT_NGUON)),
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
                null);
    }

    /**
     * Id nội bộ của một trạm ĐÃ CÓ — <b>qua {@link ScopeGuard#require}</b> (T73.1).
     *
     * <p>{@code publicId} ở đây luôn là hồ sơ vừa tra ra ở {@code lapKeHoach}, tức đã đi qua bộ lọc
     * phạm vi ⇒ lượt tra này về nguyên tắc luôn thấy. Nhưng {@code .orElse(null)} biến trạng thái
     * <i>"⛔ thuộc phạm vi"</i> thành một {@code null} lặng lẽ: {@code timTheoKhoa(null, q)} đọc nó
     * thành <i>"nhóm máy này chưa có"</i> ⇒ bản xem trước đếm sai, và ⛔ một dòng
     * {@code security_events} nào. {@code require} ném {@code AUTH-3002} kèm nhật ký bảo mật —
     * <b>một trạng thái ⛔ biểu diễn được</b> thay cho một lời dặn.
     */
    private Long idCua(UUID publicId) {
        return scopeGuard
                .require(constructions.findByPublicIdAndDeletedAtIsNull(publicId), Construction.class, publicId)
                .getId();
    }

    /**
     * Khoá của một trạm TRONG TỆP.
     *
     * <p>Trạm đã có dùng {@code publicId}; trạm mới dùng {@code (đơn vị, tên)} — ⛔ dùng mã sinh ra,
     * vì hai dòng cùng tên trong cùng tệp sẽ nhận hai mã sinh khác nhau và thành hai hồ sơ.
     */
    private static String khoaTram(String ma, OrgUnitRef donVi, String ten) {
        return ma != null ? "ma:" + ma.toUpperCase(Locale.ROOT) : "moi:" + donVi.id() + "|" + khoaTen(ten);
    }

    /** ⚠ Phải trùng KHÍT phép chuẩn hoá của {@code ConstructionRepository.timTheoDonViVaTen}. */
    private static String khoaTen(String ten) {
        return ten.trim().toLowerCase(Locale.ROOT);
    }

    private static String trong(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Short soMay(String value, int soDong, List<LoiDong> loi) {
        try {
            BigDecimal v = NumericUtils.docSoNhapTay(value);
            if (v == null) {
                loi.add(new LoiDong(soDong, COT_SO_MAY, "Thiếu số máy"));
                return null;
            }
            if (v.signum() <= 0 || v.stripTrailingZeros().scale() > 0 || v.compareTo(BigDecimal.valueOf(999)) > 0) {
                loi.add(new LoiDong(soDong, COT_SO_MAY, "Số máy phải là số nguyên 1–999: '%s'".formatted(value)));
                return null;
            }
            return v.shortValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            loi.add(new LoiDong(soDong, COT_SO_MAY, "Không phải số: '%s'".formatted(value)));
            return null;
        }
    }

    /** ⚠ Chuẩn hoá scale 2 — {@code 1100} và {@code 1100.00} phải là MỘT khoá khi tra nhóm đang có. */
    private static BigDecimal luuLuong(String value, int soDong, List<LoiDong> loi) {
        try {
            BigDecimal v = NumericUtils.docSoNhapTay(value);
            if (v == null) {
                loi.add(new LoiDong(soDong, COT_Q, "Thiếu lưu lượng một máy"));
                return null;
            }
            if (v.signum() <= 0) {
                loi.add(new LoiDong(soDong, COT_Q, "Lưu lượng phải lớn hơn 0: '%s'".formatted(value)));
                return null;
            }
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            loi.add(new LoiDong(soDong, COT_Q, "Không phải số: '%s'".formatted(value)));
            return null;
        }
    }
}
