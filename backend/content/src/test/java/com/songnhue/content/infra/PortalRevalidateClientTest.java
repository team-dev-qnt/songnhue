package com.songnhue.content.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.songnhue.core.common.config.PortalProperties;

/**
 * ISR revalidate — DOD1.5, và là <b>lần đầu cơ chế này có một phép kiểm</b>.
 *
 * <h2>Vì sao phải dựng một máy chủ HTTP thật thay vì mock</h2>
 *
 * <p>Javadoc của {@link PortalRevalidateClient} ghi hai chi tiết mà mock <b>không thể</b> kiểm được,
 * và cả hai đều đã trả giá thật ở WS-16:
 *
 * <ul>
 *   <li><b>Ép HTTP/1.1.</b> {@code HttpClient} của JDK mặc định HTTP/2 và gửi kèm
 *       {@code Connection: Upgrade, HTTP2-Settings}; máy chủ Node của Next đóng kết nối, và lỗi hiện
 *       ra là *"header parser received no bytes"* — một câu không nhắc gì tới HTTP/2. Mock lớp
 *       {@code HttpClient} là bỏ qua đúng chỗ đã hỏng.
 *   <li><b>Header bí mật.</b> Gửi thiếu hoặc sai tên header thì cổng trả 401, và triệu chứng duy
 *       nhất là trang công khai đứng yên với nội dung cũ.
 * </ul>
 *
 * <p>{@code com.sun.net.httpserver.HttpServer} có sẵn trong JDK — không kéo thêm phụ thuộc nào, và
 * nó ghi lại được đúng những gì đi qua dây.
 *
 * <p>⚠ Phép kiểm này <b>không</b> chứng minh Next.js dựng lại trang thật. Nó chứng minh phía phát
 * ra gửi đúng thứ, và phân biệt được thành công với thất bại. Vế còn lại chỉ đo được trên môi
 * trường có cổng chạy — ghi ở mục nghiệm thu của {@code docs/deploy-guideline.md}.
 */
class PortalRevalidateClientTest {

    private HttpServer server;
    private final List<GhiNhan> daNhan = new ArrayList<>();
    private final AtomicInteger maTraVe = new AtomicInteger(200);

    /** Một lượt gọi đã đi qua dây — giữ đủ để khẳng định, không giữ hơn. */
    private record GhiNhan(
            String method, String path, String secret, String contentType, String body, String moiHeader) {}

    @BeforeEach
    void dungMayChuGia() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/revalidate", this::ghiNhanRoiTraLoi);
        server.start();
    }

    @AfterEach
    void dungLai() {
        server.stop(0);
    }

    private void ghiNhanRoiTraLoi(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        daNhan.add(new GhiNhan(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("x-revalidate-secret"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body,
                exchange.getRequestHeaders().entrySet().toString().toLowerCase(java.util.Locale.ROOT)));

        byte[] phanHoi = "{\"revalidated\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(maTraVe.get(), phanHoi.length);
        exchange.getResponseBody().write(phanHoi);
        exchange.close();
    }

    @Test
    @DisplayName("⭐⭐ Gọi thật tới cổng: đúng đường dẫn, đúng header bí mật, đúng thân JSON")
    void guiDungThuTaiCong() {
        client("bi-mat-kiem-thu").revalidatePath("/tin-tuc/bai-moi");

        assertThat(daNhan).hasSize(1);
        GhiNhan goi = daNhan.get(0);
        assertThat(goi.method()).isEqualTo("POST");
        assertThat(goi.path()).isEqualTo("/api/revalidate");
        assertThat(goi.secret())
                .as("thiếu hoặc sai tên header thì cổng trả 401, và triệu chứng duy nhất là trang "
                        + "công khai đứng yên với nội dung cũ")
                .isEqualTo("bi-mat-kiem-thu");
        assertThat(goi.contentType()).startsWith("application/json");
        assertThat(goi.body()).isEqualTo("{\"path\":\"/tin-tuc/bai-moi\"}");
    }

    @Test
    @DisplayName("⭐⭐ KHÔNG xin nâng cấp HTTP/2 — chính hai header này làm máy chủ Node đóng kết nối")
    void khongXinNangCapHttp2() {
        client("x").revalidateTag("menu");

        // ⚠⚠ Bản đầu của bài kiểm này khẳng định `exchange.getProtocol()` là "HTTP/1.1" — và nó
        //    XANH cả khi đã gỡ `.version(HTTP_1_1)`. Lý do: `com.sun.net.httpserver` chỉ nói
        //    HTTP/1.1, nên client bỏ qua lượt nâng cấp và tự hạ xuống; giao thức quan sát được
        //    giống hệt nhau ở cả hai cấu hình. Một khẳng định không phân biệt được hai trạng thái
        //    thì không khẳng định gì.
        //
        //    Thứ THẬT SỰ khác — đã đo bằng cách chạy cả hai cấu hình lên cùng một máy chủ:
        //      HTTP_2   → upgrade=true  http2-settings=true
        //      HTTP_1_1 → upgrade=false http2-settings=false
        //    Và đúng hai header đó là thứ máy chủ Node của Next không trả lời.
        assertThat(daNhan.get(0).moiHeader())
                .as("⛔ Đây là chỗ WS-16 đã trả giá: cùng một yêu cầu, curl đi qua bình thường trong "
                        + "khi job thử lại hỏng liên tiếp với 'header parser received no bytes' — một "
                        + "câu không hề nhắc tới HTTP/2. Gỡ .version(HTTP_1_1) là bài kiểm này đỏ.")
                .doesNotContain("http2-settings")
                .doesNotContain("upgrade");
    }

    @Test
    @DisplayName("Dựng lại theo nhãn gửi khoá `tag`, không phải `path`")
    void nhanCacheGuiDungKhoa() {
        client("x").revalidateTag("site-config");
        assertThat(daNhan.get(0).body()).isEqualTo("{\"tag\":\"site-config\"}");
    }

    @Test
    @DisplayName("⛔ Cổng trả lỗi → NÉM, để hàng đợi thử lại thay vì ghi SUCCEEDED cho việc chưa xong")
    void congTraLoiThiNem() {
        maTraVe.set(401);

        assertThatThrownBy(() -> client("sai-bi-mat").revalidatePath("/tin-tuc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("⛔ Thông báo lỗi KHÔNG mang nội dung phản hồi — nó đi vào bảng jobs và vào bản sao lưu")
    void thongBaoLoiKhongMangNoiDungPhanHoi() {
        maTraVe.set(500);

        assertThatThrownBy(() -> client("x").revalidatePath("/tin-tuc"))
                .hasMessageNotContaining("revalidated")
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Chưa cấu hình cổng thì bỏ qua lặng lẽ — môi trường dev là một lựa chọn hợp lệ")
    void chuaCauHinhThiBoQua() {
        PortalProperties tat = new PortalProperties();
        tat.setBaseUrl("");
        tat.setRevalidateSecret("");

        new PortalRevalidateClient(tat).revalidatePath("/tin-tuc");

        assertThat(daNhan).as("không cấu hình thì không được gọi đi đâu cả").isEmpty();
    }

    @Test
    @DisplayName("Ký tự đặc biệt trong nhãn được thoát đúng — thân gửi lên vẫn là JSON hợp lệ")
    void thoatKyTuDacBiet() {
        client("x").revalidateTag("nhan \"kep\" và \\ ngược");

        assertThat(daNhan.get(0).body()).isEqualTo("{\"tag\":\"nhan \\\"kep\\\" và \\\\ ngược\"}");
    }

    private PortalRevalidateClient client(String biMat) {
        PortalProperties portal = new PortalProperties();
        portal.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        portal.setRevalidateSecret(biMat);
        portal.setTimeoutSeconds(5);
        return new PortalRevalidateClient(portal);
    }
}
