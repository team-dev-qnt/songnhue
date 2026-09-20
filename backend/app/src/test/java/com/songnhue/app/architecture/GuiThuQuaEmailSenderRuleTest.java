package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

/**
 * <b>Mọi thư đi qua {@code EmailSender} — T61.23 (WS-73).</b>
 *
 * <p>Chuyển hướng thư của staging ({@code MAIL_REDIRECT_TO}) nằm ở {@code EmailSender}, đúng chỗ dữ liệu đi qua
 * (luật 12). Một lớp mới tiêm {@code JavaMailSender} rồi gọi {@code send} là vượt mặt nó: staging mang dữ liệu nhân
 * bản từ production và dùng chung SMTP ⇒ thư đi tới hộp thư THẬT của cán bộ, ⛔ một dòng lỗi. Đo 19/09/2026: chỉ
 * {@code EmailSender} và {@code MailConfig} phụ thuộc API gửi thư; luật này giữ nguyên trạng thái ấy.
 *
 * <p>⚠ Phạm vi (luật 28): cấm phụ thuộc mọi kiểu gán được vào {@code org.springframework.mail.MailSender} (gồm
 * {@code JavaMailSender}) và {@code jakarta.mail.Transport}. {@code MailException} được phép — bắt lỗi gửi thư ⛔ gửi
 * được thư nào. Một thư viện gửi thư KHÁC (HTTP API của nhà cung cấp) ⛔ nằm trong tầm soi.
 */
class GuiThuQuaEmailSenderRuleTest {

    static final Set<String> DUOC_GUI = Set.of(
            "com.songnhue.core.application.notification.EmailSender",
            "com.songnhue.core.application.notification.MailConfig");

    static ArchRule chiEmailSenderGuiThu() {
        return noClasses()
                .that(new DescribedPredicate<JavaClass>("⛔ phải EmailSender/MailConfig") {
                    @Override
                    public boolean test(JavaClass lop) {
                        return !DUOC_GUI.contains(lop.getName());
                    }
                })
                .should()
                .dependOnClassesThat()
                .areAssignableTo("org.springframework.mail.MailSender")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.mail.Transport")
                .because("chuyển hướng thư của staging nằm ở EmailSender (T61.23) — gửi thẳng là thư tới hộp thư THẬT");
    }

    @Test
    @DisplayName("⛔ Chỉ EmailSender/MailConfig chạm API gửi thư — ⛔ đường nào vượt mặt chuyển hướng thư staging")
    void maThatChiEmailSenderGuiThu() {
        chiEmailSenderGuiThu().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Vế chống tập rỗng — EmailSender THẬT SỰ phụ thuộc JavaMailSender (tên lớp đúng, luật ⛔ rỗng)")
    void emailSenderThatSuGuiThu() {
        Set<String> lopGuiThu = ProductionClasses.ALL.stream()
                .filter(lop -> lop.getDirectDependenciesFromSelf().stream()
                        .map(Dependency::getTargetClass)
                        .anyMatch(dich -> dich.isAssignableTo("org.springframework.mail.MailSender")))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
        assertThat(lopGuiThu)
                .as("đổi tên/gói EmailSender mà quên DUOC_GUI thì luật chính đỏ; còn tập này rỗng là luật hết nghĩa")
                .containsAll(DUOC_GUI)
                .hasSize(DUOC_GUI.size());
    }
}
