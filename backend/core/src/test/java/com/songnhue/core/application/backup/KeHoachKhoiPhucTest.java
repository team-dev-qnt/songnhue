package com.songnhue.core.application.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Các bước thuần của lượt khôi phục (T68.3) — cùng ba vế với {@code khoi-phuc-qua-container.sh}.
 *
 * <p>Dữ liệu mẫu là dòng mục lục ĐÚNG ĐỊNH DẠNG {@code pg_restore --list} (luật 25): dòng EXTENSION ⛔
 * mang chủ sở hữu nên trường cuối của nó là TÊN extension — đúng ca mà bộ lọc hai vế cũ để lọt ngày
 * 08/09/2026, và bài kiểm tích hợp {@code KhoiPhucVaoCsdlTrangTest} chạy nó trên bản dump thật.
 */
class KeHoachKhoiPhucTest {

    private static final List<String> MUC_DAC_BIET = List.of(
            ";",
            "; Archive created at 2026-09-19 02:00:03 UTC",
            ";     dbname: songnhue",
            ";",
            "; Selected TOC Entries:",
            ";",
            "4; 3079 16389 EXTENSION - postgis ",
            "6021; 0 0 COMMENT - EXTENSION postgis ",
            "2; 3079 16420 EXTENSION - pg_trgm ",
            "6022; 0 0 COMMENT - EXTENSION pg_trgm ",
            "3; 3079 16501 EXTENSION - unaccent ",
            "6023; 0 0 COMMENT - EXTENSION unaccent ",
            "220; 1259 17010 TABLE public spatial_ref_sys postgres",
            "5801; 0 17010 TABLE DATA public spatial_ref_sys postgres",
            "",
            "255; 1259 16600 TABLE public users songnhue_owner",
            "5990; 0 0 ACL public TABLE users songnhue_owner");

    @Test
    @DisplayName("⭐ Lọc đủ BA vế: COMMENT - EXTENSION · chính dòng EXTENSION · mục của postgres")
    void locDuBaVe() {
        KeHoachKhoiPhuc.MucLucDaLoc daLoc = KeHoachKhoiPhuc.locMucLuc(mucLuc(0));

        assertThat(daLoc.dong())
                .as("dòng EXTENSION có trường cuối là TÊN extension (⛔ `postgres`) — vế giữa phải bắt nó")
                .noneMatch(d -> d.contains(" EXTENSION - "))
                .noneMatch(d -> d.contains("COMMENT - EXTENSION"))
                .noneMatch(d -> d.endsWith(" postgres"));
        assertThat(daLoc.dong())
                .as("mục của ứng dụng và dòng chú thích phải GIỮ NGUYÊN")
                .contains("255; 1259 16600 TABLE public users songnhue_owner")
                .contains("5990; 0 0 ACL public TABLE users songnhue_owner")
                .contains("; Selected TOC Entries:");
        assertThat(daLoc.boDi())
                .as("3 EXTENSION + 3 COMMENT + 2 mục của postgres")
                .isEqualTo(8);
        assertThat(daLoc.soDongExtension()).isZero();
    }

    @Test
    @DisplayName("⛔ Vế giữa là vế QUYẾT ĐỊNH: bộ lọc hai vế cũ (§10.58) để lọt dòng EXTENSION — phân biệt được")
    void boLocHaiVeCuDeLotExtension() {
        // Dựng lại bộ lọc CŨ đúng như trước 08/09: chỉ `grep -v COMMENT - EXTENSION` + `awk '$NF != "postgres"'`.
        List<String> haiVe = MUC_DAC_BIET.stream()
                .filter(d -> !d.contains("COMMENT - EXTENSION"))
                .filter(d -> !d.strip().endsWith(" postgres"))
                .toList();
        assertThat(haiVe)
                .as("tiền đề của bài: bộ lọc cũ ĐỂ LỌT dòng EXTENSION — nếu ⛔ thì bài này ⛔ chứng minh gì")
                .anyMatch(d -> d.contains(" EXTENSION - postgis"));
        assertThat(KeHoachKhoiPhuc.locMucLuc(MUC_DAC_BIET).dong()).noneMatch(d -> d.contains(" EXTENSION - "));
    }

    @Test
    @DisplayName("⛔ Chốt bộ lọc: ≤ 100 mục còn lại ⇒ DỪNG (khôi phục ra CSDL rỗng trông như thành công)")
    void chotSanSoMuc() {
        // ⚠ Dòng chú thích `;` cũng được đếm (cùng nghĩa `grep -c .` của script) — 8 dòng giữ lại của
        //   MUC_DAC_BIET + 80 bảng = 88 ≤ 100. Bản nháp dùng `- 5` ra 103 và bài đỏ vì lý do SAI.
        KeHoachKhoiPhuc.MucLucDaLoc it = KeHoachKhoiPhuc.locMucLuc(mucLuc(KeHoachKhoiPhuc.SO_MUC_TOI_THIEU - 20));
        assertThat(it.con())
                .as("tiền đề: số mục còn lại phải ≤ sàn")
                .isLessThanOrEqualTo(KeHoachKhoiPhuc.SO_MUC_TOI_THIEU);
        assertThatThrownBy(() -> KeHoachKhoiPhuc.kiemMucLuc(it))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ăn quá tay");

        KeHoachKhoiPhuc.MucLucDaLoc du = KeHoachKhoiPhuc.locMucLuc(mucLuc(KeHoachKhoiPhuc.SO_MUC_TOI_THIEU + 20));
        KeHoachKhoiPhuc.kiemMucLuc(du); // ⛔ ném
    }

    @Test
    @DisplayName("⛔ Chốt bộ lọc: còn sót MỘT mục EXTENSION ⇒ DỪNG, ⛔ để pg_restore phát DROP EXTENSION")
    void chotSotExtension() {
        List<String> dong = new ArrayList<>(KeHoachKhoiPhuc.locMucLuc(mucLuc(KeHoachKhoiPhuc.SO_MUC_TOI_THIEU + 20))
                .dong());
        dong.add("4; 3079 16389 EXTENSION - postgis ");
        KeHoachKhoiPhuc.MucLucDaLoc sot = new KeHoachKhoiPhuc.MucLucDaLoc(dong, dong.size(), dong.size());

        assertThatThrownBy(() -> KeHoachKhoiPhuc.kiemMucLuc(sot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXTENSION");
    }

    @Test
    @DisplayName("⛔⛔ Cờ sinh SQL GIỮ ACL (§10.58) và khối nạp là MỘT giao dịch dừng ở lỗi đầu")
    void coGiuAclVaMotGiaoDich() {
        assertThat(KeHoachKhoiPhuc.CO_SINH_SQL)
                .contains("--clean", "--if-exists", "--no-owner")
                .doesNotContain("--no-" + "privileges");
        assertThat(KeHoachKhoiPhuc.CO_NAP).contains("--single-transaction", "--set=ON_ERROR_STOP=1");
        assertThat(KeHoachKhoiPhuc.khoiTruocKhiNap())
                .contains("c.relkind = 'p'")
                .contains("DROP TABLE IF EXISTS public.%I CASCADE");
        assertThat(KeHoachKhoiPhuc.cauDemPhanMoRong()).contains("'postgis','unaccent','pg_trgm'");
    }

    @Test
    @DisplayName(
            "⛔⛔ Lỗi 4 — khối trước-khi-nạp GỠ quyền mặc định của vai trò đang nạp, và ⛔ tự tước quyền của chính nó")
    void khoiTruocKhiNapGoQuyenMacDinh() {
        String khoi = KeHoachKhoiPhuc.khoiTruocKhiNap();

        assertThat(khoi)
                .as(
                        "bảng dựng lại nhận quyền mặc định của ĐÍCH — ⛔ gỡ thì append-only mất REVOKE (72 quyền thừa, đo 19/09)")
                .contains("FROM pg_default_acl d")
                .contains("ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I REVOKE ALL ON %s FROM %s")
                .contains("d.defaclrole = (SELECT oid FROM pg_roles WHERE rolname = current_user)");
        assertThat(khoi)
                .as("mục của chính vai trò (quyền chủ sở hữu) phải bị LOẠI — REVOKE ALL ở đó là tự khoá mình")
                .contains("a.grantee <> d.defaclrole");
        assertThat(khoi)
                .as("chỉ mục cấp schema: JOIN (⛔ LEFT JOIN) pg_namespace loại mục toàn cục (defaclnamespace = 0)")
                .contains("               JOIN pg_namespace ns ON ns.oid = d.defaclnamespace")
                .doesNotContain("LEFT JOIN pg_namespace");
        assertThat(khoi).as("loại đối tượng lạ phải NÉM, ⛔ lặng lẽ bỏ qua").contains("RAISE EXCEPTION");
        assertThat(khoi.indexOf("c.relkind = 'p'"))
                .as("hai khối đều đứng TRƯỚC phần thân — thứ tự giữa chúng ⛔ quan trọng, nhưng cả hai phải có mặt")
                .isNotNegative();
        assertThat(khoi.chars().allMatch(c -> c < 128))
                .as("khối đứng TRƯỚC `SET client_encoding` của phần thân ⇒ chỉ ASCII")
                .isTrue();
    }

    /** Mục lục đầy đủ = các dòng đặc biệt + {@code soBang} dòng bảng của ứng dụng. */
    private static List<String> mucLuc(int soBang) {
        List<String> dong = new ArrayList<>(MUC_DAC_BIET);
        IntStream.range(0, soBang)
                .forEach(i ->
                        dong.add("%d; 1259 %d TABLE public bang_%d songnhue_owner".formatted(300 + i, 18000 + i, i)));
        return dong;
    }
}
