package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.operations.domain.TinhBaoCaoNhanh.DongNgapUng;
import com.songnhue.operations.domain.TinhBaoCaoNhanh.DongVanHanh;

/**
 * Bất biến của Báo cáo nhanh — mỗi bất biến MỘT bài (plan §4), ⛔ gộp.
 *
 * <p>Dữ liệu gá lấy hình dạng từ mẫu Word: Đông Mỹ 24 máy × 1.100 (chạy 1), Hòa Bình tiêu 14 × 2.500
 * (chạy 2), Đại Áng tiêu hai nhóm 4 × 4.000 + 1 × 1.950 (chạy 3 + 1), Yên Nghĩa 10 × 43.200.
 */
class TinhBaoCaoNhanhTest {

    private static final BangCoMayBom BANG = BangCoMayBomTest.bienDeXuat();

    private static long idNhom = 1;

    /** Công trình Công ty gắn vào vị trí "Yên Nghĩa" — khoá nội bộ, ⛔ mã (danh mục có hai "Yên Nghĩa"). */
    private static final long YEN_NGHIA = 5;

    private static DongVanHanh dong(long ct, String ma, int thietKe, int q, Integer chay) {
        return new DongVanHanh(
                new DongNhomMay(
                        idNhom++,
                        UUID.randomUUID(),
                        ct,
                        UUID.randomUUID(),
                        ma,
                        "Trạm " + ma,
                        "Sông Nhuệ",
                        7L,
                        thietKe,
                        BigDecimal.valueOf(q),
                        (int) idNhom),
                chay);
    }

    private static List<DongVanHanh> mau(Integer yenNghia) {
        return List.of(
                dong(1, "TB-DMY", 24, 1100, 1),
                dong(2, "TB-HBT", 14, 2500, 2),
                dong(3, "TB-DANG", 4, 4000, 3),
                dong(3, "TB-DANG", 1, 1950, 1),
                dong(4, "TB-SQUAN", 5, 2500, null),
                dong(YEN_NGHIA, "TB-YNGHIA", 10, 43200, yenNghia));
    }

    @Test
    @DisplayName("⭐⭐ Bất biến 1 — SUM(9 cột) = Tổng số máy")
    void tongChinCotBangTongSoMay() {
        TinhBaoCaoNhanh.DongBang1 b1 = TinhBaoCaoNhanh.bang1(mau(5), BANG);
        assertThat(Arrays.stream(b1.theoCo()).sum()).isEqualTo(b1.tongMay()).isEqualTo(12);
        assertThat(b1.theoCo()).containsExactly(5, 0, 0, 0, 3, 2, 2, 0, 0);
    }

    @Test
    @DisplayName("⭐⭐ Bất biến 2 — Mục 1 là BẢN SAO của Bảng 1, từng ô")
    void muc1LaBanSaoBang1() {
        TinhBaoCaoNhanh.DongBang1 b1 = TinhBaoCaoNhanh.bang1(mau(5), BANG);
        TinhBaoCaoNhanh.Muc1 m1 = TinhBaoCaoNhanh.muc1(b1);
        assertThat(m1.tongTram()).isEqualTo(b1.tongTram());
        assertThat(m1.tongMay()).isEqualTo(b1.tongMay());
        assertThat(m1.tongLuuLuongM3h()).isEqualByComparingTo(b1.tongLuuLuongM3h());
    }

    @Test
    @DisplayName("⭐⭐ Bất biến 3 — trạm đếm DISTINCT, máy đếm số ĐANG CHẠY (⛔ số thiết kế)")
    void demTramDistinctVaMayDangChay() {
        TinhBaoCaoNhanh.DongBang1 b1 = TinhBaoCaoNhanh.bang1(mau(5), BANG);
        // Đại Áng hai nhóm cùng chạy ⇒ vẫn MỘT trạm; Siêu Quần chưa nhập ⇒ ⛔ tính.
        assertThat(b1.tongTram()).isEqualTo(4);
        assertThat(b1.tongMay()).as("⛔ SUM(so_may) = 58").isEqualTo(12);
        // 1×1100 + 2×2500 + 3×4000 + 1×1950 + 5×43200
        assertThat(b1.tongLuuLuongM3h()).isEqualByComparingTo("236050");
    }

    @Test
    @DisplayName("⭐⭐ Bất biến 4 — Yên Nghĩa X=0 ra câu KHÁC HẲN; chưa nhập / chưa gắn trạm ⛔ bịa câu")
    void yenNghiaBaTrangThai() {
        TinhBaoCaoNhanh.GhiChuYenNghia chay = TinhBaoCaoNhanh.yenNghia(mau(5), YEN_NGHIA);
        assertThat(chay.cau()).isEqualTo("Trạm bơm Yên Nghĩa vận hành 5 máy bơm với tổng lưu lượng bơm 60 m³/s.");

        TinhBaoCaoNhanh.GhiChuYenNghia dung = TinhBaoCaoNhanh.yenNghia(mau(0), YEN_NGHIA);
        assertThat(dung.cau()).isEqualTo("Trạm bơm Yên Nghĩa không vận hành.").doesNotContain("0 máy");

        TinhBaoCaoNhanh.GhiChuYenNghia chuaNhap = TinhBaoCaoNhanh.yenNghia(mau(null), YEN_NGHIA);
        assertThat(chuaNhap.trangThai()).isEqualTo(TinhBaoCaoNhanh.TrangThaiYenNghia.CHUA_NHAP);
        assertThat(chuaNhap.cau())
                .as("⛔ 'không vận hành' khi CHƯA AI NHẬP là một câu sai")
                .isNull();

        TinhBaoCaoNhanh.GhiChuYenNghia vang =
                TinhBaoCaoNhanh.yenNghia(List.of(dong(1, "TB-DMY", 24, 1100, 1)), YEN_NGHIA);
        assertThat(vang.trangThai()).isEqualTo(TinhBaoCaoNhanh.TrangThaiYenNghia.CHUA_CO_TRONG_DANH_MUC);

        // Chưa gắn trạm ⇒ ⛔ đoán theo mã/tên, dù danh mục CÓ nhóm máy mang mã "TB-YNGHIA".
        TinhBaoCaoNhanh.GhiChuYenNghia chuaGan = TinhBaoCaoNhanh.yenNghia(mau(5), null);
        assertThat(chuaGan.trangThai()).isEqualTo(TinhBaoCaoNhanh.TrangThaiYenNghia.CHUA_GAN_TRAM);
        assertThat(chuaGan.cau()).isNull();

        // Gắn một công trình KHÁC ⇒ ghi chú đọc đúng công trình ấy, ⛔ theo mã "TB-YNGHIA".
        assertThat(TinhBaoCaoNhanh.yenNghia(mau(5), 1L).soMay()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐⭐ Bất biến 5 — Bảng 5: Cộng = lúa + rau; dòng III = tổng các xã; ô trống ≠ 0")
    void bang5CongVaDongCongTy() {
        List<TinhBaoCaoNhanh.DongBang5> b5 = TinhBaoCaoNhanh.bang5(List.of(
                xa(55, null, null, "115", "20"),
                xa(56, null, null, "125", null),
                xa(62, null, null, "81", "25"),
                xa(47, null, null, null, null)));

        TinhBaoCaoNhanh.ChinO thuongPhuc = b5.get(1).o();
        assertThat(thuongPhuc.sauNuocCong()).isEqualByComparingTo("135");
        assertThat(thuongPhuc.ngapTrangCong())
                .as("⛔ 0 — cả hai ô đều chưa nhập")
                .isNull();

        TinhBaoCaoNhanh.ChinO iii = TinhBaoCaoNhanh.dongCongTy(b5);
        assertThat(iii.sauNuocLua()).isEqualByComparingTo("321");
        assertThat(iii.sauNuocRau()).isEqualByComparingTo("45");
        assertThat(iii.sauNuocCong()).isEqualByComparingTo("366");
        assertThat(iii.tongCong()).as("tổng cộng = ngập trắng + sâu nước").isEqualByComparingTo("366");
        assertThat(iii.ngapTrangLua()).isNull();
        assertThat(b5.get(0).xa().thuTu()).as("sắp theo số TT của mẫu").isEqualTo(47);
    }

    @Test
    @DisplayName("⛔ Chưa ô nào của Bảng 2 được nhập ⇒ Bảng 1 TRỐNG, ⛔ '0 trạm · 0 máy'")
    void chuaNhapThiBang1Trong() {
        assertThat(TinhBaoCaoNhanh.bang1(List.of(dong(1, "TB-DMY", 24, 1100, null)), BANG))
                .isNull();
        assertThat(TinhBaoCaoNhanh.muc1(null).tongMay()).isNull();
    }

    @Test
    @DisplayName("Bảng 2 gom theo Xí nghiệp theo thứ tự cây tổ chức; dòng tiêu đề khối = tổng máy thiết kế")
    void bang2GomTheoXiNghiep() {
        DongVanHanh a = new DongVanHanh(
                new DongNhomMay(
                        1L, UUID.randomUUID(), 10L, UUID.randomUUID(), "A", "A", null, 8L, 3, BigDecimal.TEN, 1),
                null);
        DongVanHanh b = new DongVanHanh(
                new DongNhomMay(
                        2L, UUID.randomUUID(), 11L, UUID.randomUUID(), "B", "B", null, 7L, 4, BigDecimal.TEN, 2),
                null);
        DongVanHanh b2 = new DongVanHanh(
                new DongNhomMay(
                        3L, UUID.randomUUID(), 11L, UUID.randomUUID(), "B", "B", null, 7L, 2, BigDecimal.ONE, 3),
                null);
        List<TinhBaoCaoNhanh.KhoiBang2> k = TinhBaoCaoNhanh.bang2(List.of(a, b, b2), Map.of(7L, 0, 8L, 1));
        assertThat(k).extracting(TinhBaoCaoNhanh.KhoiBang2::orgUnitId).containsExactly(7L, 8L);
        assertThat(k.get(0).tongMayThietKe()).isEqualTo(6);
        assertThat(k.get(0).tram()).hasSize(1);
        assertThat(k.get(0).tram().get(0).nhom()).hasSize(2);
    }

    @Test
    @DisplayName("Số in văn bản: 1.100 · 43.200 · 0,92 · 2,00")
    void soVanBan() {
        assertThat(SoVanBan.thapPhan(new BigDecimal("1100.00"))).isEqualTo("1.100");
        assertThat(SoVanBan.thapPhan(new BigDecimal("2554152"))).isEqualTo("2.554.152");
        assertThat(SoVanBan.thapPhan(new BigDecimal("0.92"))).isEqualTo("0,92");
        assertThat(SoVanBan.thapPhan(null)).isEmpty();
        assertThat(SoVanBan.mucNuoc(new BigDecimal("2"))).isEqualTo("2,00");
    }

    private static DongNgapUng xa(int tt, String ntLua, String ntRau, String snLua, String snRau) {
        return new DongNgapUng(
                (long) tt, UUID.randomUUID(), "Xã " + tt, tt, so(ntLua), so(ntRau), so(snLua), so(snRau));
    }

    private static BigDecimal so(String s) {
        return s == null ? null : new BigDecimal(s);
    }
}
