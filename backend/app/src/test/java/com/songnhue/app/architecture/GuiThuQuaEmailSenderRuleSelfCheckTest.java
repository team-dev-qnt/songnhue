package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.app.architecture.fixture.GuiThuFixtures;

/** <b>Ai canh người canh gác</b> — {@link GuiThuQuaEmailSenderRuleTest} chạy trên mã cố ý sai (luật 1). */
class GuiThuQuaEmailSenderRuleSelfCheckTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importClasses(GuiThuFixtures.GuiVuotMat.class, GuiThuFixtures.BatLoiGuiThu.class);

    @Test
    @DisplayName("⛔ Bắt được lớp gửi thẳng JavaMailSender; ⛔ báo nhầm lớp chỉ bắt MailException")
    void batDuocGuiVuotMat() {
        EvaluationResult kq =
                GuiThuQuaEmailSenderRuleTest.chiEmailSenderGuiThu().evaluate(FIXTURES);

        assertThat(kq.getFailureReport().getDetails())
                .as("vi phạm phải thuộc GuiVuotMat — và CHỈ nó")
                .isNotEmpty()
                .allMatch(d -> d.contains(GuiThuFixtures.GuiVuotMat.class.getName()));
    }
}
