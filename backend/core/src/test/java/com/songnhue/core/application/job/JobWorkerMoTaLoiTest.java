package com.songnhue.core.application.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.error.ErrorMessageResolver;
import com.songnhue.core.common.exception.BusinessRuleException;

/** {@code jobs.last_error} phải mang LÝ DO, ⛔ chỉ mã lỗi — T61.13. */
class JobWorkerMoTaLoiTest {

    private final ErrorMessageResolver messages = new ErrorMessageResolver(nguon());

    @Test
    @DisplayName("⭐ Lỗi nghiệp vụ: last_error = mã + câu ĐÃ điền đối số (bản cũ: 'BusinessRuleException: ADM-2001')")
    void loiNghiepVuMangLyDo() {
        String moTa = JobWorker.moTaLoi(
                new BusinessRuleException(ErrorCode.ADM_2001, "chưa cấu hình DB_ARCHIVER_PASSWORD"), messages);

        assertThat(moTa)
                .isEqualTo("ADM-2001: Kết xuất lưu trữ nhật ký thất bại (chưa cấu hình DB_ARCHIVER_PASSWORD)")
                .doesNotContain("BusinessRuleException");
    }

    @Test
    @DisplayName("Lỗi ngoài dự kiến giữ nguyên dạng cũ: tên lớp + thông điệp")
    void loiKhacGiuNguyen() {
        assertThat(JobWorker.moTaLoi(new IllegalStateException("hỏng"), messages))
                .isEqualTo("IllegalStateException: hỏng");
    }

    private static StaticMessageSource nguon() {
        StaticMessageSource s = new StaticMessageSource();
        s.addMessage("ADM-2001", Locale.of("vi"), "Kết xuất lưu trữ nhật ký thất bại ({0})");
        return s;
    }
}
