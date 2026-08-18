package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Kiểm cài đặt TOTP bằng <b>bộ vector chính thức của RFC 6238</b> (Phụ lục B).
 *
 * <p>Đây là lý do tự cài được mà không phải kéo thư viện: tính đúng đắn chứng minh bằng chính bộ số
 * mà tác giả chuẩn công bố, không phải bằng "chạy thử thấy khớp điện thoại tôi".
 *
 * <p>Nếu một trong các trường hợp dưới đây đỏ thì <b>không ai đăng nhập được bằng 2FA</b> — ứng dụng
 * xác thực trên điện thoại sẽ sinh ra mã khác với máy chủ.
 */
class TotpGeneratorTest {

    /** Khoá mẫu của RFC: chuỗi ASCII "12345678901234567890" (20 byte = 160 bit). */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Nested
    @DisplayName("Vector chuẩn RFC 6238 Phụ lục B (HMAC-SHA1)")
    class RfcVectors {

        /**
         * RFC công bố mã 8 chữ số; hệ thống dùng 6 chữ số nên lấy 6 số cuối — đúng theo phép cắt
         * modulo của RFC 4226 §5.3.
         */
        @ParameterizedTest(name = "t={0}s → {1}")
        @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130"
        })
        void matchesOfficialVectors(long epochSeconds, String expectedCode) {
            long step = TotpGenerator.stepAt(epochSeconds);
            assertThat(TotpGenerator.generate(RFC_SECRET, step)).isEqualTo(expectedCode);
        }
    }

    @Test
    @DisplayName("Mã luôn đủ 6 chữ số, kể cả khi giá trị nhỏ (phải đệm số 0)")
    void alwaysSixDigits() {
        // Mã 005924 của vector RFC bắt đầu bằng hai số 0 — đúng chỗ mà cài đặt cẩu thả trả về "5924"
        assertThat(TotpGenerator.generate(RFC_SECRET, TotpGenerator.stepAt(1234567890)))
                .hasSize(6);

        for (long step = 0; step < 500; step++) {
            assertThat(TotpGenerator.generate(RFC_SECRET, step)).hasSize(6).containsOnlyDigits();
        }
    }

    @Test
    @DisplayName("Mã đổi sau mỗi 30 giây")
    void codeChangesEveryStep() {
        assertThat(TotpGenerator.stepAt(0)).isZero();
        assertThat(TotpGenerator.stepAt(29)).isZero();
        assertThat(TotpGenerator.stepAt(30)).isEqualTo(1);
        assertThat(TotpGenerator.stepAt(59)).isEqualTo(1);
        assertThat(TotpGenerator.stepAt(60)).isEqualTo(2);
    }

    @Nested
    @DisplayName("Kiểm mã")
    class Verification {

        @Test
        @DisplayName("Chấp nhận lệch 1 bước hai phía — bù sai lệch đồng hồ điện thoại")
        void acceptsOneStepDrift() {
            long now = TotpGenerator.stepAt(1_700_000_000L);

            for (long step : new long[] {now - 1, now, now + 1}) {
                String code = TotpGenerator.generate(RFC_SECRET, step);
                OptionalLong matched = TotpGenerator.verify(RFC_SECRET, code, now, 1);
                assertThat(matched).hasValue(step);
            }
        }

        @Test
        @DisplayName("Từ chối mã lệch 2 bước — cửa sổ không được nới rộng vô tội vạ")
        void rejectsTooOldCode() {
            long now = TotpGenerator.stepAt(1_700_000_000L);
            String tooOld = TotpGenerator.generate(RFC_SECRET, now - 2);

            assertThat(TotpGenerator.verify(RFC_SECRET, tooOld, now, 1)).isEmpty();
        }

        @Test
        @DisplayName("Từ chối mã sai định dạng thay vì ném lỗi")
        void rejectsMalformedInput() {
            long now = TotpGenerator.stepAt(1_700_000_000L);

            assertThat(TotpGenerator.verify(RFC_SECRET, null, now, 1)).isEmpty();
            assertThat(TotpGenerator.verify(RFC_SECRET, "", now, 1)).isEmpty();
            assertThat(TotpGenerator.verify(RFC_SECRET, "12345", now, 1)).isEmpty();
            assertThat(TotpGenerator.verify(RFC_SECRET, "1234567", now, 1)).isEmpty();
            assertThat(TotpGenerator.verify(RFC_SECRET, "abcdef", now, 1)).isEmpty();
        }

        @Test
        @DisplayName("Secret khác thì mã khác — không có chuyện mã dùng chung được")
        void differentSecretsProduceDifferentCodes() {
            byte[] other = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);
            long step = TotpGenerator.stepAt(1_700_000_000L);

            assertThat(TotpGenerator.generate(RFC_SECRET, step)).isNotEqualTo(TotpGenerator.generate(other, step));
            assertThat(TotpGenerator.verify(other, TotpGenerator.generate(RFC_SECRET, step), step, 1))
                    .isEmpty();
        }
    }
}
