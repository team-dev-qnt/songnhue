package com.songnhue.operations.application.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import com.songnhue.core.common.util.NumericUtils;
import com.songnhue.operations.application.DanhMucMayBomService;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.NhomMayBom;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.NhomMayBomRepository;

/**
 * Nhập nhóm máy bơm từ tệp — đường vào DUY NHẤT của danh mục Bảng 2 (⛔ seed, CLAUDE.md).
 *
 * <h2>Khoá: (mã công trình, Q một máy)</h2>
 *
 * <p>Đo trên sheet {@code TB Tiêu (KH)}: 178 trạm, 0 trạm có hai nhóm cùng Q ⇒ cặp ấy định danh một
 * nhóm. Trùng khoá ⇒ cập nhật số máy; khoá mới ⇒ thêm nhóm.
 *
 * <h2>⛔ Nhóm VẮNG khỏi tệp ⛔ bị xoá</h2>
 *
 * <p>Tệp thường lập từng phần (một Xí nghiệp một tệp). Hiểu *"vắng"* thành *"xoá"* là để lượt nhập
 * thứ hai xoá sạch trạm của Xí nghiệp nhập trước — im lặng (T42.20 · T47.16). Xoá một nhóm là nút
 * riêng trên màn hình.
 *
 * <h2>⭐ Thứ tự dòng của tệp = thứ tự dòng của Bảng 2</h2>
 *
 * <p>Số TT trong mẫu Word có lỗi đánh số (Phú Xuyên nhảy 62→82; Ứng Hoà lặp 25) ⇒ ⛔ nhập cột TT;
 * thứ tự lấy theo vị trí dòng, và hệ tự đánh lại số khi in.
 *
 * <h2>⛔ Q phải xếp được vào một cỡ NGAY LÚC NHẬP</h2>
 *
 * <p>Bắt ở đây thì người nhập thấy đúng dòng sai; để tới lúc lập báo cáo thì {@code OPS-2027} nổ ở
 * một màn hình khác, một ngày khác, trước mặt một người khác.
 */
@Service
public class NhomMayBomImportService {

    private static final Logger log = LoggerFactory.getLogger(NhomMayBomImportService.class);

    private static final String COT_MA = "ma_cong_trinh";
    private static final String COT_SO_MAY = "so_may";
    private static final String COT_Q = "q_mot_may_m3h";

    /** ⭐ Một nguồn — sinh ra chính tệp mẫu (luật 14). */
    public static final List<CotMau> COT_MAU = List.of(
            new CotMau(COT_MA, true, "BẮT BUỘC · mã trạm bơm, phải có sẵn trong danh mục công trình"),
            new CotMau(COT_SO_MAY, true, "BẮT BUỘC · số máy của nhóm, số nguyên > 0"),
            new CotMau(COT_Q, true, "BẮT BUỘC · lưu lượng MỘT máy, m³/h — ví dụ 1.100 hoặc 43200"));

    private static final List<String> COT_BAT_BUOC = CotMau.tenBatBuoc(COT_MAU);

    public static byte[] bieuMau() {
        return BieuMauCsv.dung(COT_MAU);
    }

    private final ConstructionRepository constructions;
    private final NhomMayBomRepository nhomMay;
    private final DanhMucMayBomService danhMuc;

    public NhomMayBomImportService(
            ConstructionRepository constructions, NhomMayBomRepository nhomMay, DanhMucMayBomService danhMuc) {
        this.constructions = constructions;
        this.nhomMay = nhomMay;
        this.danhMuc = danhMuc;
    }

    @Transactional(readOnly = true)
    public KetQuaNhap preview(byte[] content) {
        return lapKeHoach(content).baoCao(false);
    }

    /** Nhập thật — chạy lại {@link #lapKeHoach}; còn một dòng lỗi thì ⛔ dòng nào được ghi ({@code OPS-2016}). */
    @Transactional
    public KetQuaNhap apply(byte[] content) {
        KeHoach keHoach = lapKeHoach(content);
        if (!keHoach.loi.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2016, keHoach.loi.size());
        }
        for (DongKeHoach d : keHoach.dong) {
            if (d.hienCo == null) {
                nhomMay.save(new NhomMayBom(d.constructionId, d.soMay, d.q, d.thuTu));
            } else {
                d.hienCo.setSoMay(d.soMay);
                d.hienCo.setSortOrder(d.thuTu);
                nhomMay.save(d.hienCo);
            }
        }
        log.info("Nhập nhóm máy bơm: thêm {} · cập nhật {}", keHoach.soThem(), keHoach.soSua());
        return keHoach.baoCao(true);
    }

    private record DongKeHoach(Long constructionId, short soMay, BigDecimal q, int thuTu, NhomMayBom hienCo) {}

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

        BangCoMayBom bang = danhMuc.bangCo();
        Set<String> khoaDaGap = new HashSet<>();
        for (SpreadsheetReader.Row row : rows) {
            int soDong = row.rowNumber();
            int soLoiTruoc = keHoach.loi.size();

            String ma = row.get(COT_MA);
            Construction ct = null;
            if (ma == null) {
                keHoach.loi.add(new LoiDong(soDong, COT_MA, "Thiếu mã công trình"));
            } else {
                ct = constructions
                        .findByCodeAndDeletedAtIsNull(ma.trim().toUpperCase(Locale.ROOT))
                        .orElse(null);
                if (ct == null) {
                    keHoach.loi.add(new LoiDong(soDong, COT_MA, "Không có công trình mã '%s'".formatted(ma)));
                } else if (ct.getConstructionType() != ConstructionType.TRAM_BOM) {
                    keHoach.loi.add(new LoiDong(soDong, COT_MA, "Công trình '%s' không phải trạm bơm".formatted(ma)));
                }
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
            if (ct != null && q != null && !khoaDaGap.add(ct.getId() + "|" + q.toPlainString())) {
                keHoach.loi.add(new LoiDong(
                        soDong, COT_Q, "Trạm '%s' có hai dòng cùng Q = %s".formatted(ma, q.toPlainString())));
            }

            if (keHoach.loi.size() == soLoiTruoc) {
                NhomMayBom hienCo = nhomMay.timTheoKhoa(ct.getId(), q)
                        .orElse(null);
                keHoach.dong.add(new DongKeHoach(ct.getId(), soMay, q, soDong, hienCo));
            }
        }
        return keHoach;
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
