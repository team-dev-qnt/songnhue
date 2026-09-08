package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.app.architecture.fixture.ThuTuCayFixtures;

/**
 * <b>Ai canh người canh gác</b> — bốn luật của {@link ThuTuCayRuleTest} chạy lên mã cố ý sai.
 *
 * <h2>Vì sao bài này bắt buộc phải có</h2>
 *
 * Vì bốn luật kia hiện chạy trên một kho <b>đã sạch</b>: chúng xanh, và cái xanh ấy không phân biệt
 * được <i>"luật bắt được vi phạm và không có vi phạm nào"</i> với <i>"luật hỏng nên chẳng bắt được
 * gì"</i> (quy tắc 9). Dự án đã có <b>năm</b> cơ chế canh gác xanh mà không chạy, trong đó chính bộ
 * máy ArchUnit từng báo {@code Tests run: 0} suốt Phase 0 — xem javadoc {@link ProductionClasses}.
 *
 * <p>⚠ {@link ThuTuCayFixtures#DungChuanRepo} nằm cùng gói fixture và <b>phải không bị báo vi
 * phạm</b>. Thiếu vế ấy thì bốn bài dưới đây vẫn xanh với một luật viết kiểu "từ chối tất cả" —
 * đúng lỗi §10.62 đã trả giá.
 */
class ThuTuCayRuleSelfCheckTest {

    /** Nạp riêng gói fixture: luật thật chạy trên {@code ProductionClasses.ALL} đã loại {@code src/test}. */
    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages(ThuTuCayFixtures.class.getPackageName());

    @Test
    @DisplayName("Bắt được cái tên hứa 'anh em đúng thứ tự'")
    void batDuocTenHuaHao() {
        assertThatThrownBy(() -> ThuTuCayRuleTest.tenHuaHao().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc");
    }

    @Test
    @DisplayName("⭐⭐ Bắt được cửa hiển thị RỖNG RUỘT — ca mà phép canh văn bản mù hoàn toàn")
    void batDuocCuaRongRuot() {
        assertThatThrownBy(() -> ThuTuCayRuleTest.cuaHienThiPhaiGoiPhepSap().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ThuTuCayFixtures.CuaRongRuotRepo.class.getSimpleName());
    }

    @Test
    @DisplayName("Bắt được lời gọi vượt mặt từ tầng service")
    void batDuocGoiVuotMat() {
        assertThatThrownBy(() -> ThuTuCayRuleTest.cauThoKhongDuocGoiVuotMat().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ThuTuCayFixtures.GoiVuotMatService.class.getSimpleName());
    }

    @Test
    @DisplayName("Bắt được cây khai câu thô mà thiếu hẳn cửa hiển thị")
    void batDuocThieuCua() {
        assertThatThrownBy(
                        () -> ThuTuCayRuleTest.khaiCauThoThiPhaiKhaiCuaHienThi().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ThuTuCayFixtures.ThieuCuaRepo.class.getSimpleName());
    }

    @Test
    @DisplayName("⛔ Không báo nhầm bản làm ĐÚNG — nếu không, bốn bài trên xanh vì lý do sai")
    void khongBaoNhamBanDung() {
        String banDung = ThuTuCayFixtures.DungChuanRepo.class.getName();

        for (EvaluationResult ketQua : java.util.List.of(
                ThuTuCayRuleTest.tenHuaHao().evaluate(FIXTURES),
                ThuTuCayRuleTest.cuaHienThiPhaiGoiPhepSap().evaluate(FIXTURES),
                ThuTuCayRuleTest.cauThoKhongDuocGoiVuotMat().evaluate(FIXTURES),
                ThuTuCayRuleTest.khaiCauThoThiPhaiKhaiCuaHienThi().evaluate(FIXTURES))) {

            assertThat(ketQua.getFailureReport().getDetails())
                    .as("bản khai đúng chuẩn không được bị báo vi phạm bởi bất kỳ luật nào")
                    .noneMatch(dong -> dong.contains(banDung));
        }
    }
}
