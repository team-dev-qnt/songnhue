package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Entity của Báo cáo nhanh — phần có LUẬT (⛔ getter trần): trạng thái chỉ đổi qua {@code applyState},
 * lý do chỉ ghi ở lượt MỞ LẠI, ảnh chụp ghi một lượt.
 */
class BaoCaoNhanhDomainTest {

    @Test
    @DisplayName("⭐ Lý do CHỈ ghi ở MO_LAI; lượt CHỐT kế tiếp ⛔ xoá nó")
    void lyDoChiGhiOMoLai() {
        BaoCaoNhanh bc = new BaoCaoNhanh(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), "NHAP");
        assertThat(bc.workflowEntityType()).isEqualTo("QUICK_REPORT");
        assertThat(bc.currentState()).isEqualTo("NHAP");
        assertThat(bc.daChot()).isFalse();

        bc.applyWorkflowReason("CHOT", null);
        bc.applyState("DA_CHOT");
        assertThat(bc.daChot()).isTrue();
        assertThat(bc.getLyDoMoLai()).isNull();

        bc.applyWorkflowReason("MO_LAI", "UBND yêu cầu cập nhật");
        bc.applyState("NHAP");
        bc.applyWorkflowReason("CHOT", null);
        assertThat(bc.getLyDoMoLai()).isEqualTo("UBND yêu cầu cập nhật");
        assertThat(bc.getTrangThai()).isEqualTo(TrangThaiBaoCaoNhanh.NHAP);
    }

    @Test
    @DisplayName("Khung giờ đổi thành CẶP")
    void datKhung() {
        BaoCaoNhanh bc = new BaoCaoNhanh(Instant.EPOCH, Instant.EPOCH.plusSeconds(1), "NHAP");
        bc.datKhung(Instant.EPOCH.plusSeconds(10), Instant.EPOCH.plusSeconds(20));
        assertThat(bc.getTuThoiDiem()).isEqualTo(Instant.EPOCH.plusSeconds(10));
        assertThat(bc.getDenThoiDiem()).isEqualTo(Instant.EPOCH.plusSeconds(20));
        assertThat(bc.entityId()).isNull();
    }

    @Test
    @DisplayName("Ảnh chụp Bảng 2 + bốn ô Bảng 5 ghi MỘT lượt")
    void ghiMotLuot() {
        BaoCaoNhanhVanHanh v = new BaoCaoNhanhVanHanh(1L, 2L);
        v.ghi((short) 10, new BigDecimal("43200"), null);
        assertThat(v.getBaoCaoId()).isEqualTo(1L);
        assertThat(v.getNhomMayId()).isEqualTo(2L);
        assertThat(v.getSoMayThietKe()).isEqualTo((short) 10);
        assertThat(v.getQMotMayM3h()).isEqualByComparingTo("43200");
        assertThat(v.getSoMayVanHanh()).as("null = CHƯA NHẬP, ⛔ 0").isNull();

        BaoCaoNhanhNgapUng n = new BaoCaoNhanhNgapUng(1L, 55L);
        n.ghi(null, BigDecimal.ONE, BigDecimal.TEN, null);
        assertThat(n.getBaoCaoId()).isEqualTo(1L);
        assertThat(n.getDonViHanhChinhId()).isEqualTo(55L);
        assertThat(n.getNgapTrangLua()).isNull();
        assertThat(n.getNgapTrangRau()).isEqualByComparingTo("1");
        assertThat(n.getSauNuocLua()).isEqualByComparingTo("10");
        assertThat(n.getSauNuocRau()).isNull();
    }

    @Test
    @DisplayName("Nhóm máy: Q là khoá; số máy + thứ tự sửa được. Cỡ máy: biên đi thành cặp")
    void nhomMayVaCoMay() {
        NhomMayBom nhom = new NhomMayBom(7L, (short) 24, new BigDecimal("1100"), 3);
        nhom.setSoMay((short) 20);
        nhom.setSortOrder(4);
        assertThat(nhom.getConstructionId()).isEqualTo(7L);
        assertThat(nhom.getSoMay()).isEqualTo((short) 20);
        assertThat(nhom.getQMotMayM3h()).isEqualByComparingTo("1100");
        assertThat(nhom.getSortOrder()).isEqualTo(4);

        CoMayBom co = new CoMayBom();
        co.datBien(new BigDecimal("1000"), new BigDecimal("1100"));
        BangCoMayBom.Co c = co.toCo();
        assertThat(c.tu()).isEqualByComparingTo("1000");
        assertThat(c.den()).isEqualByComparingTo("1100");
        assertThat(co.getQTuM3h()).isEqualByComparingTo("1000");
        assertThat(co.getQDenM3h()).isEqualByComparingTo("1100");
        assertThat(co.getNhan()).isNull();
        assertThat(co.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("Bảng 3 mục 11: 7 cống, mã vị trí duy nhất — khớp 7 hàng B3_* seed ở V202609181088")
    void bang3SongNhue() {
        assertThat(Bang3SongNhue.DONG).hasSize(7);
        assertThat(Bang3SongNhue.DONG.stream().map(Bang3SongNhue.Dong::maViTri))
                .doesNotHaveDuplicates()
                .allMatch(m -> m.startsWith("B3_"))
                .containsExactly(
                        "B3_LIEN_MAC",
                        "B3_HA_DONG",
                        "B3_DONG_QUAN",
                        "B3_HOA_MY",
                        "B3_VAN_DINH",
                        "B3_NHAT_TUU",
                        "B3_LUONG_CO");
    }

    @Test
    @DisplayName("Số in văn bản: âm, số nguyên, ô trống")
    void soVanBanBien() {
        assertThat(SoVanBan.thapPhan(new BigDecimal("-1234.5"))).isEqualTo("-1.234,5");
        assertThat(SoVanBan.thapPhan(new BigDecimal("1E+3"))).isEqualTo("1.000");
        assertThat(SoVanBan.soNguyen(null)).isEmpty();
        assertThat(SoVanBan.mucNuoc(null)).isEmpty();
        assertThat(SoVanBan.mucNuoc(new BigDecimal("-0.5"))).isEqualTo("-0,50");
    }
}
