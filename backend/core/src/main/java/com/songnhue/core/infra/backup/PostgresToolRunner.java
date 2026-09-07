package com.songnhue.core.infra.backup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gọi các công cụ dòng lệnh của PostgreSQL ({@code pg_dump}, {@code pg_restore}, {@code psql}).
 *
 * <p><b>⛔ Mật khẩu đi qua biến môi trường {@code PGPASSWORD}, TUYỆT ĐỐI không qua tham số dòng
 * lệnh.</b> Tham số dòng lệnh của một tiến trình là thứ <i>mọi</i> tiến trình khác trên cùng máy đọc
 * được qua {@code ps} hay {@code /proc/<pid>/cmdline}. Nhét mật khẩu chủ sở hữu CSDL vào đó là dán
 * nó lên bảng tin — và không để lại dấu vết nào trong nhật ký của mình.
 *
 * <p><b>Đầu ra lỗi bị chặn lại có giới hạn.</b> {@code pg_restore} hỏng có thể phun ra hàng chục
 * nghìn dòng; giữ hết vào bộ nhớ để rồi cắt đi là cách làm sập tiến trình đúng lúc nó đang xử lý
 * sự cố. Chỉ giữ {@value #ERROR_TAIL_LINES} dòng cuối — dòng đầu tiên hiếm khi là nguyên nhân,
 * dòng cuối gần như luôn là.
 *
 * <p><b>Luôn phải đọc hết luồng đầu ra.</b> Bộ đệm ống dẫn của hệ điều hành chỉ vài chục KB; không
 * đọc thì tiến trình con <b>chặn lại ở lệnh ghi</b> và nằm im tới hết hạn chờ. Triệu chứng là
 * "pg_dump treo", không phải "log đầy".
 */
@Component
public class PostgresToolRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresToolRunner.class);

    private static final int ERROR_TAIL_LINES = 50;

    /** Chờ luồng đọc dọn nốt bộ đệm. Ngắn — tới đây thì tiến trình con đã chết rồi. */
    private static final long READER_JOIN_MILLIS = 2_000;

    /**
     * Kết quả một lượt chạy.
     *
     * @param exitCode 0 là thành công; {@code pg_dump} dùng mã khác 0 cho mọi loại lỗi
     * @param output vài chục dòng cuối của stdout+stderr, đã gộp
     */
    public record ToolResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /**
     * @param command lệnh và tham số — <b>không được chứa mật khẩu</b>
     * @param password giá trị cho {@code PGPASSWORD}
     * @param workingDirectory thư mục làm việc, hoặc {@code null}
     * @throws IOException không khởi động được tiến trình con (thường là thiếu nhị phân)
     * @throws InterruptedException luồng bị ngắt trong lúc chờ
     */
    public ToolResult run(List<String> command, String password, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        // Gộp stderr vào stdout: pg_dump ghi tiến độ ra stderr, lỗi cũng ra stderr.
        // Đọc hai luồng riêng cần hai luồng thực thi; gộp lại là một vòng lặp.
        builder.redirectErrorStream(true);

        Map<String, String> env = builder.environment();
        env.put("PGPASSWORD", password == null ? "" : password);
        // Thông báo lỗi của công cụ phải đọc được trong nhật ký, không phụ thuộc locale máy chủ
        env.put("LC_ALL", "C");

        log.info("Chạy {}", redact(command));
        Process process = builder.start();

        // ⚠ Việc đọc luồng PHẢI nằm ở luồng thực thi khác. Đọc tới hết ngay tại đây rồi mới gọi
        // waitFor(timeout) thì hạn chờ thành vô nghĩa: tiến trình con treo mà không ghi gì cả sẽ
        // kẹt ở readLine() vĩnh viễn, và cái hạn chờ bên dưới không bao giờ tới lượt chạy.
        OutputTail tail = new OutputTail();
        Thread reader = new Thread(() -> drain(process, tail), "pg-tool-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            // destroyForcibly chứ không destroy: pg_restore đang giữ khoá trên CSDL, một tín hiệu
            // lịch sự mà nó bỏ qua là khoá còn nguyên đó.
            process.destroyForcibly();
            process.waitFor(READER_JOIN_MILLIS, TimeUnit.MILLISECONDS);
        }

        // Tiến trình đã kết thúc nhưng luồng đọc có thể còn vài dòng trong bộ đệm.
        reader.join(READER_JOIN_MILLIS);

        if (!finished) {
            return new ToolResult(-1, "Quá hạn " + timeout + ". " + tail.snapshot());
        }
        return new ToolResult(process.exitValue(), tail.snapshot());
    }

    private static void drain(Process process, OutputTail tail) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.add(line);
            }
        } catch (IOException e) {
            // Luồng bị đóng vì tiến trình đã bị huỷ — bình thường ở nhánh quá hạn, không phải lỗi
            log.debug("Ngừng đọc đầu ra tiến trình con: {}", e.getMessage());
        }
    }

    /**
     * Vòng đệm giữ {@value #ERROR_TAIL_LINES} dòng cuối.
     *
     * <p>Đồng bộ hoá vì hai luồng chạm vào: luồng đọc ghi vào, luồng gọi đọc ra. Ở nhánh quá hạn,
     * {@code join} có thể hết giờ trước khi luồng đọc dừng hẳn, nên hai bên thật sự chạy song song.
     */
    private static final class OutputTail {
        private final Deque<String> lines = new ArrayDeque<>(ERROR_TAIL_LINES + 1);

        synchronized void add(String line) {
            if (lines.size() == ERROR_TAIL_LINES) {
                lines.removeFirst();
            }
            lines.addLast(line);
        }

        synchronized String snapshot() {
            return String.join("\n", lines);
        }
    }

    /** Ghi lệnh vào nhật ký — công cụ Postgres không nhận mật khẩu qua tham số, nhưng vẫn lọc phòng xa. */
    private static String redact(List<String> command) {
        return command.stream()
                .map(arg -> arg.toLowerCase(java.util.Locale.ROOT).contains("password") ? "***" : arg)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
