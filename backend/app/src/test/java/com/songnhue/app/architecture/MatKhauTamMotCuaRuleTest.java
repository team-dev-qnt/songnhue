package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.app.architecture.fixture.MatKhauTamFixtures;

/**
 * <b>Mật khẩu tạm chỉ được phát qua MỘT cửa — T73.8 (ASVS 2.3.1).</b>
 *
 * <p>{@code PasswordPolicyService.ganMatKhauTam} gán cả bốn trường (hash · mốc đổi · cờ buộc đổi · HẠN). Một đường
 * phát mật khẩu tạm thứ ba tự bật {@code setMustChangePassword(true)} là một mật khẩu tạm ⛔ hạn — đúng khuyết tật
 * T73.8 vừa vá (luật 12, luật 14). Luật chỉ cho ba lớp chạm hai setter ấy, mỗi lớp có lý do:
 *
 * <ul>
 *   <li>{@code PasswordPolicyService} — cửa phát mật khẩu tạm;
 *   <li>{@code PasswordChangeService} — người dùng tự đổi: tắt cờ, xoá hạn;
 *   <li>{@code AdminBootstrapRunner} — bootstrap {@code superadmin}: cố ý ⛔ hạn (hết hạn là khoá chết quản trị).
 * </ul>
 *
 * <p>⚠ Phạm vi (luật 28): canh lời gọi SETTER; ⛔ canh một câu {@code UPDATE users SET must_change_password} viết
 * tay (0 câu hôm nay).
 */
class MatKhauTamMotCuaRuleTest {

    static final Set<String> DUOC_CHAM = Set.of(
            "com.songnhue.core.application.auth.PasswordPolicyService",
            "com.songnhue.core.application.auth.PasswordChangeService",
            "com.songnhue.core.application.auth.AdminBootstrapRunner");

    private static final String USER = "com.songnhue.core.domain.identity.User";

    static ArchRule motCuaPhatMatKhauTam() {
        return noClasses()
                .that(new DescribedPredicate<JavaClass>("⛔ phải một trong ba cửa được phép") {
                    @Override
                    public boolean test(JavaClass lop) {
                        return !DUOC_CHAM.contains(lop.getName())
                                && !lop.getName().equals(USER);
                    }
                })
                .should()
                .callMethod(USER, "setMustChangePassword", "boolean")
                .orShould()
                .callMethod(USER, "setTempPasswordExpiresAt", "java.time.Instant")
                .because("mật khẩu tạm phải mang HẠN — đi qua PasswordPolicyService.ganMatKhauTam (T73.8)");
    }

    @Test
    @DisplayName("⛔ Chỉ ba cửa được bật cờ buộc đổi / đặt hạn mật khẩu tạm (T73.8)")
    void maThat() {
        motCuaPhatMatKhauTam().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Tự kiểm: một đường phát mật khẩu tạm thứ ba tự bật cờ ⇒ bị bắt, gọi đích danh")
    void tuKiem() {
        EvaluationResult kq = motCuaPhatMatKhauTam()
                .evaluate(new ClassFileImporter().importClasses(MatKhauTamFixtures.DuongThuBa.class));

        assertThat(kq.getFailureReport().getDetails())
                .isNotEmpty()
                .allMatch(d -> d.contains(MatKhauTamFixtures.DuongThuBa.class.getName()));
    }
}
