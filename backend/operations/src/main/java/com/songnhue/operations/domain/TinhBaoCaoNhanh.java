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
 * <h2>⛔ Ba công ty kia để TRỐNG — nhưng dòng "Tổng cộng" của BẢNG 1 thì CÓ SỐ (sửa 20/09, T78.2)</h2>
 *
 * <p>Hệ chỉ có số của Sông Nhuệ (OI-BC1) ⇒ ba dòng công ty kia để trống, ⛔ ghi 0.
 *
 * <p>⚠ Câu cũ ở đây viết: <i>"Điền 'Tổng cộng' bằng số Sông Nhuệ là khẳng định tổng toàn Thành phố =
 * một công ty ⇒ chỉ dòng Sông Nhuệ có số"</i>. Lập luận ấy <b>đúng về rủi ro mà sai về kết luận</b>,
 * và QuanTran đảo lại 20/09 theo đặc tả (<i>"dòng Tổng cộng = tổng theo cột của cả 4 công ty,
 * formula, ⛔ nhập tay"</i>):
 *
 * <ul>
 *   <li>"Tổng cộng" của Bảng 1 là <b>tổng theo cột của chính bảng ấy</b> — một giá trị hệ TÍNH
 *       ĐƯỢC từ những dòng đang có, ⛔ phải một ô chờ dữ liệu ⛔ ai có. Để trống một ô tính được là
 *       đúng thứ quy tắc 15 gọi là nửa cặp đọc–ghi.</li>
 *   <li>Rủi ro "đọc thành tổng toàn Thành phố" <b>tự nó lộ ra trên bản in</b>: ba dòng công ty kia
 *       TRỐNG, nên người đọc thấy ngay tổng bằng dòng duy nhất có số. Một dòng trống tự khai rằng
 *       nó chưa có gì — đó chính là lý do quy tắc 16 bắt để trống thay vì ghi 0.</li>
 * </ul>
 *
 * <p>⇒ Xem {@link #tongCongBang1}. Dòng "Tổng cộng" của <b>Mục 1</b> và <b>Mục 3</b> thì vẫn để
 * trống: chúng là ô của <i>thân báo cáo</i> toàn Thành phố, ⛔ phải tổng của một bảng.
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
     * @param tenTram tên công trình Công ty đã gắn — {@code null} khi chưa gắn. Đây là thứ đi vào
     *     {@link #cau}, nên câu in ra nói đúng trạm đang được chọn.
     * @param maTram mã công trình — ⚠ CHỈ để màn hình quản trị phân biệt hai trạm TRÙNG TÊN.
     *     ⛔ đi vào bản Word: văn bản gửi UBND dùng câu chữ của mẫu Công ty, chèn mã vào đó là sửa
     *     bố cục mẫu (G10).
     */
    public record GhiChuYenNghia(
            TrangThaiYenNghia trangThai,
            String cau,
            Integer soMay,
            BigDecimal luuLuongM3s,
            String tenTram,
            String maTram) {}

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

    /**
     * Một trạm trong Bảng 2 — các nhóm máy theo thứ tự tệp nhập.
     *
     * @param ma mã công trình. ⚠ CHỈ dùng cho màn hình nhập liệu: danh mục có những trạm TRÙNG TÊN
     *     (hai "Yên Nghĩa"), nên một bảng chỉ có tên là một bảng người nhập ⛔ biết mình đang gõ số
     *     cho trạm nào. ⛔ đi vào bản Word — cột của mẫu là *"Tên trạm bơm"*, thêm mã vào đó là sửa
     *     bố cục mẫu (G10).
     */
    public record TramBang2(
            Long constructionId, String ma, String ten, String nguonTuoiHuongTieu, List<DongVanHanh> nhom) {}

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

    /**
     * Dòng "Tổng cộng" của Bảng 1 — cộng THEO CỘT các dòng công ty (T78.2).
     *
     * <h3>⛔ Đây ⛔ phải "bản sao dòng Sông Nhuệ", dù hôm nay hai thứ bằng nhau</h3>
     *
     * Hệ chỉ có số của Sông Nhuệ (OI-BC1), nên tổng của một danh sách một phần tử đúng bằng phần tử
     * ấy. Viết thẳng {@code tongCong = songNhue} sẽ **chạy đúng hôm nay và sai vĩnh viễn kể từ ngày
     * có công ty thứ hai** — mà ngày ấy ⛔ có gì đỏ: cả hai dòng vẫn ra số, chỉ là tổng thôi ⛔ phải
     * tổng. ⇒ Nhận một DANH SÁCH và cộng thật; thêm một công ty là thêm một phần tử ở nơi gọi.
     *
     * <p>⚠ {@code tongTram} cộng được vì một trạm thuộc ĐÚNG MỘT công ty — ⛔ có trạm nào bị đếm hai
     * lần. (Trong một công ty thì ⛔: {@link #bang1} phải {@code COUNT DISTINCT} vì một trạm có nhiều
     * nhóm máy.)
     *
     * @param dongCongTy các dòng công ty ĐÃ CÓ SỐ; phần tử {@code null} = công ty ⛔ có dữ liệu và
     *     được BỎ QUA, ⛔ đọc thành 0 (quy tắc 16)
     * @return {@code null} khi ⛔ dòng nào có số — ô "Tổng cộng" để TRỐNG, ⛔ in "0 trạm · 0 máy"
     */
    public static DongBang1 tongCongBang1(List<DongBang1> dongCongTy) {
        List<DongBang1> coSo =
                dongCongTy.stream().filter(java.util.Objects::nonNull).toList();
        if (coSo.isEmpty()) {
            return null;
        }
        int soCo = coSo.get(0).theoCo().length;
        int[] theoCo = new int[soCo];
        int tram = 0;
        int may = 0;
        BigDecimal luuLuong = BigDecimal.ZERO;
        for (DongBang1 d : coSo) {
            if (d.theoCo().length != soCo) {
                throw new IllegalArgumentException(
                        "Các dòng công ty phải cùng số cột cỡ máy: %d vs %d".formatted(soCo, d.theoCo().length));
            }
            tram += d.tongTram();
            may += d.tongMay();
            for (int i = 0; i < soCo; i++) {
                theoCo[i] += d.theoCo()[i];
            }
            luuLuong = luuLuong.add(d.tongLuuLuongM3h());
        }
        return new DongBang1(tram, may, theoCo, luuLuong);
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
     * <h3>⛔ Câu in ra ⛔ được ghi cứng một cái TÊN (T78.1)</h3>
     *
     * Bản trước nhận {@code Long tramYenNghia} rồi dựng câu bằng hằng chuỗi *"Trạm bơm Yên Nghĩa
     * vận hành…"*. Vị trí {@code YEN_NGHIA} thì **chọn được** từ 18/09 (màn hình Cấu hình Báo cáo
     * nhanh), nên hai nửa ấy nói hai điều khác nhau: Công ty đổi sang một trạm khác, số liệu đổi
     * theo, còn **cái tên in lên văn bản gửi UBND thì ⛔**. Một ô chọn ⛔ điều khiển thứ nó hứa
     * điều khiển là nửa cặp đọc–ghi (quy tắc 27) — và ở đây nửa sai lại là nửa **người ngoài đọc**.
     *
     * <p>⇒ Nhận nguyên {@link CongTrinhGan}, ⛔ phải cặp (id, tên): hai tham số thì chúng lệch nhau
     * được, một bản ghi thì ⛔ (quy tắc 14). Tên vào câu; mã ⛔ vào câu, xem {@link GhiChuYenNghia}.
     *
     * <p>⚠ Tên lấy từ công trình **lúc đọc**, kể cả với kỳ ĐÃ CHỐT — ảnh chụp ghim *trạm nào*
     * ({@code construction_id}), ⛔ ghim *tên gọi lúc ấy*. Đây là cùng lựa chọn Bảng 2 đã làm
     * ({@code TramBang2.ten} cũng đọc sống): đổi tên một công trình là sửa một cách viết cho cùng
     * một trạm, ⛔ phải thay nó bằng trạm khác.
     *
     * @param tram công trình Công ty gắn vào vị trí {@code YEN_NGHIA}; {@code null} = chưa gắn.
     *     ⛔ tra theo mã hay tên: danh mục có HAI công trình tên "Yên Nghĩa" (trạm bơm và cống tiêu).
     */
    public static GhiChuYenNghia yenNghia(List<DongVanHanh> dong, CongTrinhGan tram) {
        if (tram == null) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_GAN_TRAM, null, null, null, null, null);
        }
        String ten = tram.ten();
        String ma = tram.ma();
        List<DongVanHanh> yn = dong.stream()
                .filter(d -> tram.id().equals(d.nhom().constructionId()))
                .toList();
        if (yn.isEmpty()) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_CO_TRONG_DANH_MUC, null, null, null, ten, ma);
        }
        if (yn.stream().allMatch(d -> d.soMayVanHanh() == null)) {
            return new GhiChuYenNghia(TrangThaiYenNghia.CHUA_NHAP, null, null, null, ten, ma);
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
                    TrangThaiYenNghia.KHONG_VAN_HANH, ten + " không vận hành.", 0, BigDecimal.ZERO, ten, ma);
        }
        BigDecimal y = m3h.divide(GIAY_MOI_GIO, 2, RoundingMode.HALF_UP);
        return new GhiChuYenNghia(
                TrangThaiYenNghia.VAN_HANH,
                // ⚠ ⛔ ghép "Trạm bơm " vào trước: tên trong danh mục ĐÃ mang tiền tố ấy
                //   ("Trạm bơm Yên Nghĩa" — V202609091074), nên ghép nữa là in ra hai lần.
                "%s vận hành %d máy bơm với tổng lưu lượng bơm %s m³/s.".formatted(ten, x, SoVanBan.thapPhan(y)),
                x,
                y,
                ten,
                ma);
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
                ds.add(new TramBang2(
                        dau.constructionId(), dau.maCongTrinh(), dau.tenCongTrinh(), dau.nguonTuoiHuongTieu(), nhom));
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
