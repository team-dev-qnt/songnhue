package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.spi.AllowedAction;

/**
 * {@link AllowedAction} (Java) ↔ {@code AllowedActionView} (TypeScript) phải cùng một hình dạng.
 *
 * <h2>Lỗi đang canh — thao tác trả bài về sửa đã hỏng hẳn vì nó</h2>
 *
 * Kiểu phía giao diện từng khai ba cờ <b>{@code primary} / {@code danger} / {@code requiresReason}</b>
 * mà record Java không hề có, và không nơi nào điền. Cả ba đều {@code optional} nên TypeScript im
 * lặng. Hậu quả đo được: {@code ApprovalActions} mở hộp thoại nhập lý do khi
 * {@code action.requiresReason} bật — mà cờ đó luôn {@code undefined} — nên người duyệt bấm
 * <i>"Yêu cầu chỉnh sửa"</i> thì không có ô nào để nhập, trong khi backend bắt buộc phải có lý do
 * và trả {@code SYS-0003}. <b>Không có đường nào đi tiếp.</b>
 *
 * <h2>Vì sao bài này nằm ở bộ BE</h2>
 *
 * Nguồn sự thật là record Java. Bộ lọc CI cho job {@code backend} chạy khi {@code backend/} đổi,
 * nên một thay đổi ở record — chính là thứ làm hai bên lệch — luôn đi qua bài này. Đặt ở bộ FE thì
 * ngược lại: sửa record Java, job {@code frontend} bị bỏ qua, và {@code skipped} được GitHub tính
 * là <b>ĐẠT</b>.
 *
 * <h2>Nó đọc CẤU TRÚC, không so câu chữ</h2>
 *
 * Vế Java lấy từ {@link Class#getRecordComponents()} — phản chiếu trên lớp đã biên dịch, không
 * phải văn bản. Vế TypeScript buộc phải phân tích tệp (không có bộ biên dịch TS ở đây), nhưng nó
 * bóc <b>danh sách tên trường</b> chứ không khớp một chuỗi cố định, nên đổi chú thích hay xuống
 * dòng không làm bài đỏ, còn thêm/bớt một trường thì có.
 */
class AllowedActionParityTest {

    private static final Path API_TYPES = repoRoot().resolve("frontend/admin-app/src/shared/api-types.ts");

    /** Bóc thân `interface AllowedActionView { … }` rồi lấy tên từng trường. */
    private static final Pattern KHOI =
            Pattern.compile("export\\s+interface\\s+AllowedActionView\\s*\\{(.*?)\\n\\}", Pattern.DOTALL);

    private static final Pattern TRUONG = Pattern.compile("^\\s*(\\w+)\\??\\s*:", Pattern.MULTILINE);

    @Test
    @DisplayName("⭐⭐ Record Java và interface TypeScript có ĐÚNG cùng bộ trường")
    void cungMotBoTruong() throws IOException {
        Set<String> java = java();
        Set<String> ts = typescript();

        assertThat(java)
                .as(
                        """
                        Hai bên mô tả cùng một payload nên phải cùng bộ trường.
                          Java (nguồn sự thật): %s
                          TypeScript          : %s
                        Thừa ở TS = giao diện đọc một trường KHÔNG AI GỬI (luôn undefined).
                        Thiếu ở TS = giao diện bỏ qua dữ liệu backend đã gửi.""",
                        java, ts)
                .isEqualTo(ts);
    }

    @Test
    @DisplayName("⛔ `requiresReason` phải có mặt ở CẢ HAI — đây là trường đã gây ra lỗi chặn")
    void requiresReasonCoOCaHaiBen() throws IOException {
        assertThat(java())
                .as("gỡ khỏi record là giao diện mất căn cứ mở ô nhập lý do")
                .contains("requiresReason");
        assertThat(typescript())
                .as("gỡ khỏi TS là hộp thoại nhập lý do không bao giờ mở, và REQUEST_CHANGES hỏng lại")
                .contains("requiresReason");
    }

    @Test
    @DisplayName("Bộ đọc tệp TS thật sự bóc được trường — tập rỗng làm hai bài trên vô nghĩa")
    void boDocKhongTraVeTapRong() throws IOException {
        // Luật 7: một khẳng định chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ai đó đổi tên interface
        // hay đổi cách khai, `KHOI` không khớp và `typescript()` trả rỗng — lúc đó bài đầu sẽ đỏ
        // vì lệch với Java, nhưng bài này nói thẳng nguyên nhân thay vì để người đọc tự suy.
        assertThat(typescript())
                .as(
                        "không bóc được trường nào từ %s — nhiều khả năng interface đã đổi tên hoặc đổi cách khai",
                        API_TYPES)
                .isNotEmpty();
    }

    // -------------------------------------------------------------------------

    private static Set<String> java() {
        return java.util.Arrays.stream(AllowedAction.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> typescript() throws IOException {
        String noiDung = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        Matcher khoi = KHOI.matcher(noiDung);
        if (!khoi.find()) {
            return Set.of();
        }
        Set<String> truong = new LinkedHashSet<>();
        Matcher m = TRUONG.matcher(khoi.group(1));
        while (m.find()) {
            truong.add(m.group(1));
        }
        return truong;
    }

    /** Đi ngược lên tới thư mục chứa `.claude` — chạy được cả từ module lẫn từ gốc repo. */
    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Không tìm thấy gốc repo (thư mục chứa .claude)");
        }
        return p;
    }
}
