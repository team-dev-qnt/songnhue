package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mọi ô DẪN XUẤT của Báo cáo nhanh — lớp THUẦN (⛔ Spring, ⛔ CSDL), kiểm bằng JUnit trần.
 *
 * <h2>Chuỗi tính MỘT CHIỀU (spec §4)</h2>
 *
 * <pre>
 *   Bảng 2 (nhập) ──► Bảng 1 dòng Sông Nhuệ ──► Mục 1 (COPY, ⛔ tính lại)
 *        └──────────► Ghi chú Yên Nghĩa
 *   Bảng 5 (nhập) ──► dòng III (Sông Nhuệ) ──► Mục 3 (COPY)
 * </pre>
 *
 * <h2>⛔ {@code null} ≠ 0 — quy tắc 16</h2>
 *
 * <p>Ô chưa nhập là {@code null}. Một tổng mà MỌI thành phần đều chưa nhập là {@code null} (ô trống
 * trong văn bản), ⛔ phải 0 — "0 máy chạy" là một câu khẳng định gửi UBND.
 *
 * <h2>⛔ Ba công ty kia và dòng "Tổng cộng" để TRỐNG</h2>
 *
 * <p>Hệ chỉ có số của Sông Nhuệ (OI-BC1). Điền "Tổng cộng" bằng số Sông Nhuệ là khẳng định tổng toàn
 * Thành phố = một công ty. ⇒ Chỉ dòng Sông Nhuệ có số.
 */
public final class TinhBaoCaoNhanh {

    private static final BigDecimal GIAY_MOI_GIO = BigDecimal.valueOf(3600);

    private TinhBaoCaoNhanh() {}

    // ==== Đầu vào ===========================================================

    /** Một dòng Bảng 2: nhóm máy (thiết kế + Q — danh mục sống hoặc ảnh chụp) + số máy đang chạy. */
    public record DongVanHanh(DongNhomMay nhom, Integer soMayVanHanh) {}

    /** Một xã của Bảng 5 — bốn ô nhập, {@code null} = chưa nhập. */
    public record DongNgapUng(
            Long xaId,
            UUID xaPublicId,
            String ten,
            int thuTu,
            BigDecimal ngapTrangLua,
            BigDecimal ngapTrangRau,
            BigDecimal sauNuocLua,
            BigDecimal sauNuocRau) {}

    // ==== Đầu ra ============================================================

    /** Một dòng Bảng 1. {@code theoCo[i]} theo thứ tự cột của mẫu (0 = "43"). */
    public record DongBang1(int tongTram, int tongMay, int[] theoCo, BigDecimal tongLuuLuongM3h) {}

    /** Mục 1 thân báo cáo — dòng Sông Nhuệ, COPY từ Bảng 1. */
    public record Muc1(Integer tongTram, Integer tongMay, BigDecimal tongLuuLuongM3h) {}

    public enum TrangThaiYenNghia {
        /** Chưa gắn công trình vào vị trí "Yên Nghĩa" (màn hình cấu hình Báo cáo nhanh). */
        CHUA_GAN_TRAM,
        /** Đã gắn trạm, nhưng danh mục máy bơm ⛔ có nhóm máy nào của trạm ấy — ⛔ được khẳng định gì. */
        CHUA_CO_TRONG_DANH_MUC,
        /** Có trong danh mục, chưa ai nhập số máy chạy. */
        CHUA_NHAP,
        KHONG_VAN_HANH,
        VAN_HANH
    }

    /**
     * Ghi chú Yên Nghĩa.
     *
     * @param cau câu in ra văn bản; {@code null} ở ba trạng thái CHƯA — bản Word giữ nguyên dấu "…"
     *     của mẫu, ⛔ bịa một câu
     * @param luuLuongM3s ⚠ m³/<b>s</b> — đơn vị KHÁC mọi bảng còn lại (spec §3.4, OI-BC5)
     */
    public record GhiChuYenNghia(TrangThaiYenNghia trangThai, String cau, Integer soMay, BigDecimal luuLuongM3s) {}

    /** Chín ô của một dòng Bảng 5: {ngập trắng, sâu nước, tổng cộng} × {lúa, rau màu, cộng}. */
    public record ChinO(
            BigDecimal ngapTrangLua,
            BigDecimal ngapTrangRau,
            BigDecimal ngapTrangCong,
            BigDecimal sauNuocLua,
            BigDecimal sauNuocRau,
            BigDecimal sauNuocCong,
            BigDecimal tongLua,
            BigDecimal tongRau,
            BigDecimal tongCong) {}

    public record DongBang5(DongNgapUng xa, ChinO o) {}

    /** Một trạm trong Bảng 2 — các nhóm máy theo thứ tự tệp nhập. */
    public record TramBang2(Long constructionId, String ten, String nguonTuoiHuongTieu, List<DongVanHanh> nhom) {}

    /** Một khối Xí nghiệp (I, II, …) của Bảng 2; {@code tongMayThietKe} in ở dòng tiêu đề khối. */
    public record KhoiBang2(Long orgUnitId, int tongMayThietKe, List<TramBang2> tram) {}

    // ==== Bảng 1 / Mục 1 ====================================================

    /**
     * Dòng Sông Nhuệ của Bảng 1.
     *
     * @return {@code null} khi CHƯA dòng nào được nhập — ô trống, ⛔ "0 trạm · 0 máy"
     */
    public static DongBang1 bang1(List<DongVanHanh> dong, BangCoMayBom bang) {
        List<DongVanHanh> daNhap =
                dong.stream().filter(d -> d.soMayVanHanh() != null).toList();
        if (daNhap.isEmpty()) {
            return null;
        }
        int[] theoCo = new int[bang.soCo()];
        int tongMay = 0;
        BigDecimal tongLuuLuong = BigDecimal.ZERO;
        java.util.Set<Long> tramChay = new java.util.HashSet<>();
        for (DongVanHanh d : daNhap) {
            int chay = d.soMayVanHanh();
            if (chay <= 0) {
                continue;
            }
            // ⛔ COUNT DISTINCT — một trạm nhiều nhóm máy cùng chạy vẫn là MỘT trạm.
            tramChay.add(d.nhom().constructionId());
            tongMay += chay;
            theoCo[bang.xep(d.nhom().qM3h())] += chay;
            tongLuuLuong = tongLuuLuong.add(d.nhom().qM3h().multiply(BigDecimal.valueOf(chay)));
        }
        return new DongBang1(tramChay.size(), tongMay, theoCo, tongLuuLuong.setScale(0, RoundingMode.HALF_UP));
    }

    /** ⛔ COPY — ⛔ công thức riêng. Hai nơi cho một câu hỏi là hai câu trả lời (spec §4.3, OI-BC12). */
    public static Muc1 muc1(DongBang1 songNhue) {
        return songNhue == null
                ? new Muc1(null, null, null)
                : new Muc1(songNhue.tongTram(), songNhue.tongMay(), songNhue.tongLuuLuongM3h());
    }

    /**
     * Ghi chú Yên Nghĩa — xem {@link GhiChuYenNghia}.
     *
     * @param tramYenNghia công trình Công ty gắn vào vị trí {@code YEN_NGHIA}; {@code null} = chưa gắn.
     *     ⛔ tra theo mã hay tên: danh mục có HAI công trình tên "Yên Nghĩa" (trạm bơm và cống tiêu).
     */
    public static GhiChuYenNghia yenNghia(List<DongVanHanh> dong, Long tramYenNghia) {
        if (tramYenNghia == null) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_GAN_TRAM, null, null, null);
        }
        List<DongVanHanh> yn = dong.stream()
                .filter(d -> tramYenNghia.equals(d.nhom().constructionId()))
                .toList();
        if (yn.isEmpty()) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_CO_TRONG_DANH_MUC, null, null, null);
        }
        if (yn.stream().allMatch(d -> d.soMayVanHanh() == null)) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_NHAP, null, null, null);
        }
        int x = 0;
        BigDecimal m3h = BigDecimal.ZERO;
        for (DongVanHanh d : yn) {
            int chay = d.soMayVanHanh() == null ? 0 : d.soMayVanHanh();
            x += chay;
            m3h = m3h.add(d.nhom().qM3h().multiply(BigDecimal.valueOf(chay)));
        }
        if (x == 0) {
            return new GhiChuYenNghia(
                    TrangThaiYenNghia.KHONG_VAN_HANH, "Trạm bơm Yên Nghĩa không vận hành.", 0, BigDecimal.ZERO);
        }
        BigDecimal y = m3h.divide(GIAY_MOI_GIO, 2, RoundingMode.HALF_UP);
        return new GhiChuYenNghia(
                TrangThaiYenNghia.VAN_HANH,
                "Trạm bơm Yên Nghĩa vận hành %d máy bơm với tổng lưu lượng bơm %s m3/s."
                        .formatted(x, SoVanBan.thapPhan(y)),
                x,
                y);
    }

    // ==== Bảng 2 ============================================================

    /**
     * Gom Bảng 2 theo Xí nghiệp → trạm → nhóm máy.
     *
     * @param thuTuDonVi thứ tự hiển thị của đơn vị (cây tổ chức); đơn vị vắng xếp cuối
     */
    public static List<KhoiBang2> bang2(List<DongVanHanh> dong, Map<Long, Integer> thuTuDonVi) {
        Map<Long, Map<Long, List<DongVanHanh>>> theoDonVi = new LinkedHashMap<>();
        dong.stream()
                .sorted(Comparator.comparingInt((DongVanHanh d) -> d.nhom().thuTu())
                        .thenComparing(d -> d.nhom().nhomId()))
                .forEach(d -> theoDonVi
                        .computeIfAbsent(d.nhom().orgUnitId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(d.nhom().constructionId(), k -> new ArrayList<>())
                        .add(d));
        List<KhoiBang2> khoi = new ArrayList<>();
        theoDonVi.forEach((donVi, tram) -> {
            List<TramBang2> ds = new ArrayList<>();
            int tong = 0;
            for (List<DongVanHanh> nhom : tram.values()) {
                DongNhomMay dau = nhom.get(0).nhom();
                ds.add(new TramBang2(dau.constructionId(), dau.tenCongTrinh(), dau.nguonTuoiHuongTieu(), nhom));
                tong += nhom.stream().mapToInt(d -> d.nhom().soMay()).sum();
            }
            khoi.add(new KhoiBang2(donVi, tong, ds));
        });
        khoi.sort(Comparator.comparingInt(k -> thuTuDonVi.getOrDefault(k.orgUnitId(), Integer.MAX_VALUE)));
        return khoi;
    }

    // ==== Bảng 5 / Mục 3 ====================================================

    public static ChinO chinO(
            BigDecimal ngapTrangLua, BigDecimal ngapTrangRau, BigDecimal sauNuocLua, BigDecimal sauNuocRau) {
        return new ChinO(
                ngapTrangLua,
                ngapTrangRau,
                cong(ngapTrangLua, ngapTrangRau),
                sauNuocLua,
                sauNuocRau,
                cong(sauNuocLua, sauNuocRau),
                cong(ngapTrangLua, sauNuocLua),
                cong(ngapTrangRau, sauNuocRau),
                cong(ngapTrangLua, ngapTrangRau, sauNuocLua, sauNuocRau));
    }

    public static List<DongBang5> bang5(List<DongNgapUng> xa) {
        return xa.stream()
                .sorted(Comparator.comparingInt(DongNgapUng::thuTu))
                .map(x -> new DongBang5(x, chinO(x.ngapTrangLua(), x.ngapTrangRau(), x.sauNuocLua(), x.sauNuocRau())))
                .toList();
    }

    /** Dòng III (Công ty TL Sông Nhuệ) = tổng các xã — và cũng là Mục 3 (COPY). */
    public static ChinO dongCongTy(List<DongBang5> dong) {
        return chinO(
                cong(dong.stream().map(d -> d.xa().ngapTrangLua()).toList()),
                cong(dong.stream().map(d -> d.xa().ngapTrangRau()).toList()),
                cong(dong.stream().map(d -> d.xa().sauNuocLua()).toList()),
                cong(dong.stream().map(d -> d.xa().sauNuocRau()).toList()));
    }

    /** Tổng bỏ qua ô trống; {@code null} khi MỌI ô đều trống (quy tắc 16). */
    static BigDecimal cong(BigDecimal... o) {
        return cong(java.util.Arrays.asList(o));
    }

    static BigDecimal cong(List<BigDecimal> o) {
        List<BigDecimal> co = o.stream().filter(Objects::nonNull).toList();
        return co.isEmpty() ? null : co.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
