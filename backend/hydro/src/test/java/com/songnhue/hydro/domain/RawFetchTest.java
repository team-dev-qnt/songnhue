package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link RawFetch} — bản ghi một lượt gọi HTTP, thứ được ghi <b>trước khi</b> parse. */
class RawFetchTest {

    private static final Instant LUC = Instant.parse("2026-09-02T03:20:45Z");

    @Test
    @DisplayName("⭐ soByte đếm theo UTF-8, và 'không nhận được gì' cho ra 0 chứ không ném")
    void demByteTheoUtf8() {
        // ⚠ Tiếng Việt có dấu chiếm nhiều hơn một byte mỗi ký tự. Đếm bằng `length()` thì cột
        //   `body_bytes` nói dối về kích thước thật — và đó là con số dùng để ước lượng ngân sách
        //   lưu trữ của VPS (hosting_recommendations.md §8).
        RawFetch coDau = new RawFetch(1L, LUC, null, 200, 10, "Sông Nhuệ", null, null);
        assertThat(coDau.soByte()).isGreaterThan("Sông Nhuệ".length());

        RawFetch khongCoGi = new RawFetch(1L, LUC, null, null, null, null, SyncFailureKind.TIMEOUT, "hết giờ chờ");
        assertThat(khongCoGi.soByte())
                .as("timeout thì không nhận được byte nào — 0 là câu trả lời đúng, ⛔ không phải ngoại lệ")
                .isZero();
    }

    @Test
    @DisplayName("⭐ thanhCong() đọc theo quy ước NULL = không lỗi")
    void thanhCongTheoNull() {
        assertThat(new RawFetch(1L, LUC, null, 200, 10, "ok", null, null).thanhCong())
                .isTrue();
        assertThat(new RawFetch(1L, LUC, null, 200, 10, "not.working", SyncFailureKind.NOT_WORKING, null).thanhCong())
                .as("HTTP 200 vẫn có thể là một lượt hỏng — nguồn trả 200 kèm chuỗi not.working")
                .isFalse();
    }

    @Test
    @DisplayName("⛔ Thiếu mốc gọi bị chặn — nó là khoá PHÂN MẢNH, thiếu là INSERT hỏng giữa lượt ingest")
    void thieuMocGoiBiChan() {
        assertThatThrownBy(() -> new RawFetch(1L, null, null, 200, 10, "ok", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fetchedAt");
    }

    @Test
    @DisplayName("⚠ Lý do quá dài bị cắt, ⛔ thân phản hồi thì KHÔNG BAO GIỜ bị cắt")
    void catLyDoNhungKhongCatThan() {
        String than = "F01771;01/09/2026;10:20;value=493;<br>".repeat(500);
        RawFetch f = new RawFetch(1L, LUC, null, 200, 10, than, SyncFailureKind.EMPTY_BODY, "y".repeat(4000));

        assertThat(f.failureDetail()).hasSize(1000);
        assertThat(f.body())
                .as(
                        """
                        ⛔ Nguồn KHÔNG có API lịch sử — `body` là bản sao duy nhất của response ấy. Cắt \
                        bớt để "cho gọn" là vứt đi đúng phần bằng chứng sẽ cần vào ngày nguồn đổi định \
                        dạng. Cột là TEXT, không có trần.""")
                .isEqualTo(than);
    }
}
