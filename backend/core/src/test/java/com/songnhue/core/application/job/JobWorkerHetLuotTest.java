package com.songnhue.core.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/** Hook {@link JobHandler#khiHetLuotThu} chạy khi và chỉ khi hết lượt thử — T61.24. */
class JobWorkerHetLuotTest {

    private final List<String> daGoi = new ArrayList<>();

    private final JobHandler ghiLai = new JobHandler() {
        @Override
        public String jobType() {
            return "THU";
        }

        @Override
        public void handle(JobContext context) {}

        @Override
        public void khiHetLuotThu(String payload, String loi) {
            daGoi.add(payload + "|" + loi);
        }
    };

    @Test
    @DisplayName("⛔ Còn lượt thử ⇒ ⛔ gọi hook; hết lượt ⇒ gọi đúng một lần với payload + câu lỗi")
    void chiGoiKhiHetLuot() {
        JobWorker.baoHetLuot(ghiLai, "{\"a\":1}", "loi 1", false);
        assertThat(daGoi)
                .as("còn lượt thử mà đã ghi ERROR là khoá tệp vì một lần chớp mạng")
                .isEmpty();

        JobWorker.baoHetLuot(ghiLai, "{\"a\":1}", "loi 3", true);
        assertThat(daGoi).containsExactly("{\"a\":1}|loi 3");
    }

    @Test
    @DisplayName("⛔ Hook ném ⇒ worker ⛔ ném theo — việc ghi FAILED đã xong, ⛔ được lan ra vòng poll")
    void hookNemKhongLan() {
        JobHandler hong = new JobHandler() {
            @Override
            public String jobType() {
                return "HONG";
            }

            @Override
            public void handle(JobContext context) {}

            @Override
            public void khiHetLuotThu(String payload, String loi) {
                throw new IllegalStateException("hook hỏng");
            }
        };
        assertThatCode(() -> JobWorker.baoHetLuot(hong, "{}", "x", true)).doesNotThrowAnyException();
        assertThatCode(() -> JobWorker.baoHetLuot(null, "{}", "x", true)).doesNotThrowAnyException();
    }
}
