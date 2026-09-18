package com.songnhue.operations.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.w3c.dom.Element;

import com.songnhue.core.common.export.DocxFiller;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.HydroSnapshotPort;
import com.songnhue.operations.domain.SoVanBan;
import com.songnhue.operations.domain.TinhBaoCaoNhanh;

/**
 * Điền một kỳ Báo cáo nhanh vào mẫu Word của Công ty — {@code ops/bao-cao-nhanh/mau-bao-cao-nhanh.docx}.
 *
 * <h2>⛔ Toạ độ ô dưới đây GẮN với đúng một bản mẫu</h2>
 *
 * <p>Bộ canh {@code MauBaoCaoNhanhTest} ghim vân tay SHA-256 + hình học (9 bảng, số dòng từng bảng). Ai
 * thay tệp mẫu thì bài ấy đỏ và bắt người thay đối chiếu lại từng hằng số ở đây.
 *
 * <pre>
 * tbl0 tiêu đề (ngày giờ thay ở mức đoạn) · tbl1 Mục 1 · tbl2 Mục 3 · tbl3 khối ký (⛔ đụng)
 * tbl4 Bảng 1 · tbl5 Bảng 2 (CHÈN dòng) · tbl6 Bảng 3 · tbl7 Bảng 4 · tbl8 Bảng 5
 * </pre>
 *
 * <h2>Chỉ dòng Sông Nhuệ có số (OI-BC1)</h2>
 *
 * <p>Ba công ty kia và dòng "Tổng cộng" để TRỐNG. ⚠ Mẫu in sẵn số 0 ở các dòng ấy (Mục 1, Mục 3, Bảng 5)
 * — xoá đi, vì "0 ha ngập" của một công ty hệ ⛔ có số là một câu SAI gửi UBND.
 */
public final class BaoCaoNhanhDocx {

    public static final String MAU = "ops/bao-cao-nhanh/mau-bao-cao-nhanh.docx";

    static final int BANG_MUC1 = 1;
    static final int BANG_MUC3 = 2;
    static final int BANG_1 = 4;
    static final int BANG_2 = 5;
    static final int BANG_3 = 6;
    static final int BANG_4 = 7;
    static final int BANG_5 = 8;

    /** Dòng "Tổng cộng" và dòng "Sông Nhuệ" của Mục 1 / Mục 3 / Bảng 1. */
    static final int DONG_TONG = 2;

    static final int DONG_SONG_NHUE_MUC = 4;

    /** Bảng 2: dòng tiêu đề khối Xí nghiệp và dòng dữ liệu đầu tiên — khuôn nhân bản. */
    static final int B2_KHUON_KHOI = 2;

    static final int B2_KHUON_DONG = 3;

    /** Bảng 3: dòng TL của cống đầu tiên (Liên Mạc) trong mục 11; mỗi cống 2 dòng. */
    static final int B3_DONG_DAU = 25;

    static final int B3_O_GIA_TRI = 3;

    /** Bảng 5: dòng các công ty (I–IV) và dòng xã đầu tiên của Sông Nhuệ (TT 47). */
    static final int[] B5_DONG_CONG_TY = {3, 25, 51, 72};

    static final int B5_DONG_SONG_NHUE = 51;
    static final int B5_TT_XA_DAU = 47;

    private static final String[] LA_MA = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};

    private BaoCaoNhanhDocx() {}

    public static byte[] docMau() {
        try (InputStream in = BaoCaoNhanhDocx.class.getClassLoader().getResourceAsStream(MAU)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu tệp mẫu " + MAU);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Dựng tệp .docx của một kỳ. */
    public static byte[] dung(BaoCaoNhanhService.ChiTiet c) {
        DocxFiller f = DocxFiller.mo(docMau());
        ngayGio(f, c);
        muc1VaBang1(f, c);
        bang2(f, c);
        bang3(f, c);
        bang4(f, c);
        muc3VaBang5(f, c);
        return f.ghi();
    }

    // ==== Ngày giờ + ghi chú ================================================

    private static void ngayGio(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        String tu = moc(c.baoCao().getTuThoiDiem());
        String den = moc(c.baoCao().getDenThoiDiem());
        ZonedDateTime ngay = DateTimeUtils.toVietnamTime(c.baoCao().getDenThoiDiem());

        // ⛔ Thứ tự là CỐ Ý: cụm dài trước, "16h ngày …" đứng một mình sau — ngược lại thì cụm ngắn ăn
        //    mất nửa sau của cụm dài. Mỗi lượt khẳng định ĐÚNG số lần: mẫu đổi thì đỏ.
        dung(f, "Từ 6h ngày 24/8/2026 đến 16h ngày 24/8/2026", "Từ " + tu + " đến " + den, 1);
        dung(f, "từ 6h ngày 24/8/2026 đến 16h ngày 24/8/2026", "từ " + tu + " đến " + den, 2);
        dung(f, "16h ngày 24/8/2026", den, 3);
        dung(
                f,
                "ngày  24  tháng 8 năm 2026",
                "ngày %d tháng %d năm %d".formatted(ngay.getDayOfMonth(), ngay.getMonthValue(), ngay.getYear()),
                1);
        dung(f, "từ…h ngày …/.../2026 đến …h ngày …/…/2026", "từ " + tu + " đến " + den, 1);

        String cau = c.ghiChuYenNghia().cau();
        if (cau != null) {
            // Chưa nhập / chưa có trong danh mục ⇒ GIỮ dấu "…" của mẫu, ⛔ bịa câu.
            dung(f, "Trạm bơm Yên Nghĩa vận hành... máy bơm với tổng lưu lượng bơm… m3/s.", cau, 1);
        }
    }

    /** {@code 6h ngày 24/8/2026} · {@code 6h30 ngày 24/8/2026} — đúng cách mẫu viết. */
    static String moc(Instant t) {
        ZonedDateTime z = DateTimeUtils.toVietnamTime(t);
        String gio = z.getMinute() == 0 ? z.getHour() + "h" : "%dh%02d".formatted(z.getHour(), z.getMinute());
        return "%s ngày %d/%d/%d".formatted(gio, z.getDayOfMonth(), z.getMonthValue(), z.getYear());
    }

    private static void dung(DocxFiller f, String cu, String moi, int soLan) {
        int thay = f.thayTrongDoan(cu, moi);
        if (thay != soLan) {
            throw new IllegalStateException(
                    "Mẫu Báo cáo nhanh đã đổi: cụm \"%s\" thay %d lần, mong %d".formatted(cu, thay, soLan));
        }
    }

    // ==== Mục 1 + Bảng 1 ====================================================

    private static void muc1VaBang1(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        for (int o = 2; o <= 4; o++) {
            f.datO(BANG_MUC1, DONG_TONG, o, null);
        }
        TinhBaoCaoNhanh.Muc1 m = c.muc1();
        f.datO(BANG_MUC1, DONG_SONG_NHUE_MUC, 2, SoVanBan.soNguyen(m.tongTram()));
        f.datO(BANG_MUC1, DONG_SONG_NHUE_MUC, 3, SoVanBan.soNguyen(m.tongMay()));
        f.datO(BANG_MUC1, DONG_SONG_NHUE_MUC, 4, SoVanBan.thapPhan(m.tongLuuLuongM3h()));

        TinhBaoCaoNhanh.DongBang1 b1 = c.bang1SongNhue();
        f.datO(BANG_1, DONG_SONG_NHUE_MUC, 2, b1 == null ? null : SoVanBan.soNguyen(b1.tongTram()));
        f.datO(BANG_1, DONG_SONG_NHUE_MUC, 3, b1 == null ? null : SoVanBan.soNguyen(b1.tongMay()));
        for (int i = 0; i < c.bangCo().soCo(); i++) {
            // Mẫu để TRỐNG ô cỡ 0 máy (cột "12" của dòng Sông Nhuệ) — theo đúng cách viết ấy.
            String v = b1 == null || b1.theoCo()[i] == 0 ? null : SoVanBan.soNguyen(b1.theoCo()[i]);
            f.datO(BANG_1, DONG_SONG_NHUE_MUC, 4 + i, v);
        }
        f.datO(
                BANG_1,
                DONG_SONG_NHUE_MUC,
                4 + c.bangCo().soCo(),
                b1 == null ? null : SoVanBan.thapPhan(b1.tongLuuLuongM3h()));
    }

    // ==== Bảng 2 ============================================================

    private static void bang2(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        Element khuonKhoi = f.khuonDong(BANG_2, B2_KHUON_KHOI);
        Element khuonDong = f.khuonDong(BANG_2, B2_KHUON_DONG);
        f.xoaDong(BANG_2, B2_KHUON_KHOI);
        int k = 0;
        for (TinhBaoCaoNhanh.KhoiBang2 khoi : c.bang2()) {
            f.themDong(
                    BANG_2,
                    khuonKhoi,
                    k < LA_MA.length ? LA_MA[k] : String.valueOf(k + 1),
                    c.tenDonVi().getOrDefault(khoi.orgUnitId(), ""),
                    SoVanBan.soNguyen(khoi.tongMayThietKe()));
            k++;
            int tt = 1;
            for (TinhBaoCaoNhanh.TramBang2 tram : khoi.tram()) {
                boolean dau = true;
                for (TinhBaoCaoNhanh.DongVanHanh d : tram.nhom()) {
                    f.themDong(
                            BANG_2,
                            khuonDong,
                            dau ? String.valueOf(tt) : null,
                            dau ? tram.ten() : null,
                            SoVanBan.soNguyen(d.nhom().soMay()),
                            SoVanBan.thapPhan(d.nhom().qM3h()),
                            SoVanBan.soNguyen(d.soMayVanHanh()),
                            dau ? tram.nguonTuoiHuongTieu() : null);
                    dau = false;
                }
                tt++;
            }
        }
    }

    // ==== Bảng 3 + Bảng 4 ===================================================

    private static void bang3(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        List<CauHinhBaoCaoNhanhService.DongBang3> ds = c.bang3();
        for (int i = 0; i < ds.size(); i++) {
            CauHinhBaoCaoNhanhService.DongBang3 d = ds.get(i);
            f.datO(BANG_3, B3_DONG_DAU + 2 * i, B3_O_GIA_TRI, oMucNuoc(d.tl()));
            f.datO(BANG_3, B3_DONG_DAU + 2 * i + 1, B3_O_GIA_TRI, oMucNuoc(d.hl()));
        }
    }

    /** {@code 1,40} — kèm {@code (15h50)} khi số đo ⛔ trùng đúng mốc báo cáo. */
    static String oMucNuoc(CauHinhBaoCaoNhanhService.OBang3 o) {
        HydroSnapshotPort.MucNuoc m = o.mucNuoc();
        if (o.apiCode() == null || m == null || m.giaTriM() == null) {
            return null;
        }
        String so = SoVanBan.mucNuoc(m.giaTriM());
        if (m.dungMoc()) {
            return so;
        }
        ZonedDateTime z = DateTimeUtils.toVietnamTime(m.mocDo());
        return "%s (%dh%02d)".formatted(so, z.getHour(), z.getMinute());
    }

    /**
     * Bảng 4 — xoá số minh hoạ của mẫu ở MỌI dòng, rồi điền lượng mưa đã nhập của 8 điểm Sông Nhuệ.
     *
     * <p>Dòng = STT của mẫu (dòng 0 là tiêu đề). ⛔ ghi trước khi đối chiếu tên: ghi số của điểm này vào
     * dòng của điểm khác là SAI mà ⛔ ai thấy — cùng luật Bảng 5.
     */
    private static void bang4(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        for (int dong = 1; dong < f.soDong(BANG_4); dong++) {
            f.datO(BANG_4, dong, 2, null);
        }
        for (SoLieuNhapTayService.DongLuongMua d : c.bang4()) {
            String tenMau = f.docO(BANG_4, d.thuTu(), 1).trim();
            if (!tenMau.equals(d.ten())) {
                throw new IllegalStateException(
                        "Bảng 4 dòng %d của mẫu là \"%s\", danh mục nói \"%s\"".formatted(d.thuTu(), tenMau, d.ten()));
            }
            f.datO(BANG_4, d.thuTu(), 2, SoVanBan.thapPhan(d.luongMuaMm()));
        }
    }

    // ==== Mục 3 + Bảng 5 ====================================================

    private static void muc3VaBang5(DocxFiller f, BaoCaoNhanhService.ChiTiet c) {
        for (int o = 2; o <= 10; o++) {
            f.datO(BANG_MUC3, DONG_TONG, o, null);
            f.datO(BANG_5, DONG_TONG, o, null);
            for (int dong : B5_DONG_CONG_TY) {
                f.datO(BANG_5, dong, o, null);
            }
        }
        TinhBaoCaoNhanh.ChinO iii = c.bang5CongTy();
        chinO(f, BANG_MUC3, DONG_SONG_NHUE_MUC, iii);
        chinO(f, BANG_5, B5_DONG_SONG_NHUE, iii);

        for (TinhBaoCaoNhanh.DongBang5 d : c.bang5()) {
            int dong = B5_DONG_SONG_NHUE + 1 + (d.xa().thuTu() - B5_TT_XA_DAU);
            String tenMau = f.docO(BANG_5, dong, 1).trim();
            if (!tenMau.equals(d.xa().ten())) {
                // ⛔ Ghi số của xã này vào dòng của xã khác là SAI mà ⛔ ai thấy — dừng ngay.
                throw new IllegalStateException("Bảng 5 dòng %d của mẫu là \"%s\", danh mục nói \"%s\" (TT %d)"
                        .formatted(dong, tenMau, d.xa().ten(), d.xa().thuTu()));
            }
            chinO(f, BANG_5, dong, d.o());
        }
    }

    private static void chinO(DocxFiller f, int bang, int dong, TinhBaoCaoNhanh.ChinO o) {
        BigDecimal[] v = {
            o.ngapTrangLua(), o.ngapTrangRau(), o.ngapTrangCong(),
            o.sauNuocLua(), o.sauNuocRau(), o.sauNuocCong(),
            o.tongLua(), o.tongRau(), o.tongCong()
        };
        for (int i = 0; i < v.length; i++) {
            f.datO(bang, dong, 2 + i, SoVanBan.thapPhan(v[i]));
        }
    }
}
