package com.songnhue.core.application.system;

import static com.songnhue.core.spi.MucCauHinh.MucDo.CANH_BAO;
import static com.songnhue.core.spi.MucCauHinh.MucDo.CHAN;
import static com.songnhue.core.spi.MucCauHinh.MucDo.THONG_TIN;
import static com.songnhue.core.spi.MucCauHinh.TrangThai.DAT;
import static com.songnhue.core.spi.MucCauHinh.TrangThai.KHONG_AP_DUNG;
import static com.songnhue.core.spi.MucCauHinh.TrangThai.NGOAI_TAM_NHIN;
import static com.songnhue.core.spi.MucCauHinh.TrangThai.SAI;
import static com.songnhue.core.spi.MucCauHinh.TrangThai.THIEU;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.common.config.CryptoProperties;
import com.songnhue.core.common.filter.DauVetDocChiSoFilter;
import com.songnhue.core.spi.MucCauHinh;
import com.songnhue.core.spi.NguonTinhTrangCauHinh;

/**
 * Cấu hình của {@code core} + hạ tầng giám sát — T61.41. Phân loại từng biến: architecture-review.md §12.1.
 *
 * <p>⛔ Mọi phép kiểm ở đây đọc <b>giá trị ĐÃ GIẢI</b> của Spring {@link Environment} (luật 3), ⛔ đọc tệp
 * {@code .env}: tệp và tiến trình đang chạy nói hai điều khác nhau cho tới lượt tạo lại container kế tiếp.
 */
@Component
class TinhTrangCauHinhCore implements NguonTinhTrangCauHinh {

    /** Prometheus đọc 30 giây/lần; 5 phút ⛔ lượt nào là chuỗi đã đứt. */
    static final Duration HAN_DOC_CHI_SO = Duration.ofMinutes(5);

    private static final String ENV_VPS2 = ".env VPS-2";
    private static final String ENV_HAI_MAY = ".env cả hai máy";

    private final Environment env;
    private final BackupProperties backup;
    private final CryptoProperties crypto;
    private final DauVetDocChiSoFilter dauVetChiSo;

    /** Màn hình tự làm mới; ⛔ mở một kết nối clamd mỗi lượt xem. */
    private final Cache<String, String> demMayQuet =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build();

    TinhTrangCauHinhCore(
            Environment env, BackupProperties backup, CryptoProperties crypto, DauVetDocChiSoFilter dauVetChiSo) {
        this.env = env;
        this.backup = backup;
        this.crypto = crypto;
        this.dauVetChiSo = dauVetChiSo;
    }

    @Override
    public List<MucCauHinh> mucCauHinh() {
        String moiTruong =
                env.getProperty("management.metrics.tags.environment", "local").strip();
        boolean trienKhai = "staging".equalsIgnoreCase(moiTruong) || "production".equalsIgnoreCase(moiTruong);
        List<MucCauHinh> ra = new ArrayList<>();

        ra.add(new MucCauHinh(
                "MOI_TRUONG",
                "Môi trường",
                "Tên môi trường (" + moiTruong + ")",
                trienKhai ? DAT : SAI,
                THONG_TIN,
                "Ứng dụng",
                ".env — APP_ENVIRONMENT",
                trienKhai
                        ? "Quyết định gác chuyển hướng thư và nhãn chỉ số."
                        : "⚠ Khác staging/production ⇒ gác chuyển hướng thư ⛔ chạy. Đúng với máy lập trình viên."));

        mucThuDienTu(ra, moiTruong);
        mucTepTaiLen(ra);
        mucMaHoaPhien(ra, trienKhai);
        mucSaoLuu(ra);
        mucCongCongKhai(ra);
        mucGiamSat(ra, trienKhai);
        return ra;
    }

    private void mucThuDienTu(List<MucCauHinh> ra, String moiTruong) {
        boolean coSmtp = coGiaTri("spring.mail.host");
        ra.add(
                new MucCauHinh(
                        "SMTP",
                        "Thư điện tử",
                        "Máy chủ gửi thư (SMTP)",
                        coSmtp ? DAT : THIEU,
                        CANH_BAO,
                        "Ứng dụng · Alertmanager (VPS-2)",
                        ".env — SMTP_HOST · SMTP_PORT · SMTP_USERNAME · SMTP_PASSWORD · SMTP_FROM",
                        "⛔ Giữ ở .env (§12.1): đổi máy chủ thư trên giao diện = rút thư mang dữ liệu cá nhân + mở kết nối tới "
                                + "máy tuỳ ý. Thiếu ⇒ mọi thư thông báo bị đánh dấu SKIPPED, chỉ còn thông báo trong hệ thống."));
        boolean coChuyenHuong = coGiaTri("app.notification.redirect-to");
        MucCauHinh.TrangThai chuyenHuong;
        String ghiChuChuyenHuong;
        if ("production".equalsIgnoreCase(moiTruong)) {
            chuyenHuong = KHONG_AP_DUNG;
            ghiChuChuyenHuong = "⛔ Production ⛔ được khai — ứng dụng dừng khởi động nếu có.";
        } else if (!coSmtp) {
            chuyenHuong = KHONG_AP_DUNG;
            ghiChuChuyenHuong = "Chưa có SMTP ⇒ ⛔ thư nào đi.";
        } else {
            chuyenHuong = coChuyenHuong ? DAT : THIEU;
            ghiChuChuyenHuong = "CSDL staging nhân bản từ production ⇒ ⛔ chuyển hướng là thư thử tới hộp thư THẬT của "
                    + "cán bộ. ⛔ Giữ ở .env: nếu nằm trong CSDL, mỗi lượt nhân bản xoá mất nó.";
        }
        ra.add(new MucCauHinh(
                "MAIL_REDIRECT_TO",
                "Thư điện tử",
                "Chuyển hướng thư của staging",
                chuyenHuong,
                CHAN,
                "Ứng dụng",
                ENV_VPS2 + " — MAIL_REDIRECT_TO",
                ghiChuChuyenHuong));
    }

    private void mucTepTaiLen(List<MucCauHinh> ra) {
        String mayQuet = env.getProperty("app.clamav.host", "").strip();
        String ketQuaQuet = mayQuet.isEmpty() ? "" : demMayQuet.get(mayQuet, h -> pingClamd(h));
        ra.add(new MucCauHinh(
                "CLAMAV",
                "Tệp tải lên",
                "Máy quét virus (ClamAV)",
                mayQuet.isEmpty() ? THIEU : ("PONG".equals(ketQuaQuet) ? DAT : SAI),
                CANH_BAO,
                "Ứng dụng → container clamav",
                "compose.prod.yml — APP_CLAMAV_HOST",
                mayQuet.isEmpty()
                        ? "Mọi tệp tải lên (gồm hồ sơ CBNV) là SKIPPED — tự quét lại khi máy quét lên (T61.24)."
                        : ("PONG".equals(ketQuaQuet)
                                ? "clamd trả lời PING."
                                : "⛔ clamd ⛔ trả lời (" + ketQuaQuet + ") — tệp mới xếp hàng chờ quét.")));
    }

    private void mucMaHoaPhien(List<MucCauHinh> ra, boolean trienKhai) {
        ra.add(new MucCauHinh(
                "AES_KEY",
                "Mã hoá & phiên",
                "Khoá AES-256-GCM (khoá hiện hành " + crypto.activeKeyId() + ", "
                        + crypto.keyIds().size() + " khoá đã nạp)",
                DAT,
                THONG_TIN,
                "Ứng dụng",
                ".env — AES_KEY_ID · AES_KEY_V*",
                "⛔ ⛔ Bao giờ lên giao diện: khoá mở dữ liệu trong CSDL ⛔ được nằm trong chính CSDL ấy. Thiếu ⇒ ứng dụng "
                        + "⛔ khởi động."));
        boolean cookieAnToan = env.getProperty("app.security.secure-cookie", Boolean.class, true);
        ra.add(new MucCauHinh(
                "SECURE_COOKIE",
                "Mã hoá & phiên",
                "Cookie phiên chỉ gửi qua HTTPS",
                cookieAnToan ? DAT : SAI,
                trienKhai ? CHAN : THONG_TIN,
                "Ứng dụng",
                ".env — SECURE_COOKIE (bỏ trống = bật)",
                "Tắt ⇒ refresh token đi được qua HTTP thường."));
        boolean conMatKhauKhoiTao = coGiaTri("app.bootstrap.admin-password");
        ra.add(new MucCauHinh(
                "BOOTSTRAP_ADMIN_PASSWORD",
                "Mã hoá & phiên",
                "Mật khẩu khởi tạo tài khoản quản trị đầu tiên",
                conMatKhauKhoiTao ? SAI : DAT,
                CANH_BAO,
                "Ứng dụng (một lần)",
                ".env — BOOTSTRAP_ADMIN_PASSWORD",
                conMatKhauKhoiTao
                        ? "Vẫn còn trong .env sau lần khởi tạo — gỡ đi (chỉ dùng MỘT lần)."
                        : "Đã gỡ khỏi .env."));
    }

    private void mucSaoLuu(List<MucCauHinh> ra) {
        ra.add(new MucCauHinh(
                "BACKUP_DUMP",
                "Sao lưu",
                "Tài khoản CSDL chỉ-đọc cho sao lưu",
                backup.isDumpConfigured() ? DAT : THIEU,
                CHAN,
                "Ứng dụng · pre-deploy-dump.sh",
                ".env — DB_READONLY_PASSWORD",
                "Thiếu ⇒ job sao lưu hằng đêm hỏng — lưới an toàn DUY NHẤT của dữ liệu."));
        ra.add(new MucCauHinh(
                "BACKUP_RESTORE",
                "Sao lưu",
                "Tài khoản CSDL cho khôi phục trên giao diện",
                backup.isRestoreConfigured() ? DAT : THIEU,
                CANH_BAO,
                "Ứng dụng",
                ".env — DB_RESTORE_PASSWORD",
                "Thiếu ⇒ nút Khôi phục báo ADM-2010; runbook khôi phục qua container vẫn dùng được."));
        ra.add(new MucCauHinh(
                "AUDIT_ARCHIVER",
                "Sao lưu",
                "Tài khoản CSDL kết xuất nhật ký quá hạn",
                coGiaTri("app.audit.archiver.password") ? DAT : THIEU,
                CANH_BAO,
                "Ứng dụng",
                ".env — DB_ARCHIVER_PASSWORD",
                "Thiếu ⇒ nhật ký kiểm toán quá hạn ⛔ được kết xuất lưu trữ."));
    }

    private void mucCongCongKhai(List<MucCauHinh> ra) {
        boolean coRevalidate = coGiaTri("app.portal.base-url") && coGiaTri("app.portal.revalidate-secret");
        ra.add(
                new MucCauHinh(
                        "REVALIDATE_SECRET",
                        "Cổng công khai",
                        "Xoá đệm cổng khi sửa nội dung",
                        coRevalidate ? DAT : THIEU,
                        CANH_BAO,
                        "Ứng dụng · public-web",
                        ".env — REVALIDATE_SECRET (compose đặt PORTAL_BASE_URL)",
                        "⛔ Giữ ở .env: public-web cũng đọc — hai nguồn sẽ lệch. Thiếu ⇒ cổng hiện nội dung cũ tới hết chu kỳ."));
    }

    private void mucGiamSat(List<MucCauHinh> ra, boolean trienKhai) {
        Instant docCuoi = dauVetChiSo.lanDocCuoi().orElse(null);
        boolean dangDoc = docCuoi != null && docCuoi.isAfter(Instant.now().minus(HAN_DOC_CHI_SO));
        ra.add(new MucCauHinh(
                "PROMETHEUS",
                "Giám sát & cảnh báo",
                "Prometheus đọc được chỉ số của ứng dụng",
                trienKhai ? (dangDoc ? DAT : THIEU) : KHONG_AP_DUNG,
                CHAN,
                "nginx (METRICS_ALLOW_IP · METRICS_BEARER_TOKEN) → Prometheus (VPS-2)",
                ENV_HAI_MAY + " — METRICS_* · PROD_METRICS_*",
                dangDoc
                        ? "Lượt đọc gần nhất: " + docCuoi + "."
                        : "⛔ 5 phút qua ⛔ lượt đọc nào ⇒ mọi chuông cảnh báo chạy trên số liệu rỗng. Kiểm: "
                                + "curl 127.0.0.1:19090/api/v1/targets trên VPS-2."));
        ra.add(new MucCauHinh(
                "ALERTMANAGER",
                "Giám sát & cảnh báo",
                "Kênh cảnh báo Email · Slack · Telegram",
                NGOAI_TAM_NHIN,
                CHAN,
                "Alertmanager (VPS-2)",
                ENV_VPS2 + " — ALERT_EMAIL_TO · SLACK_WEBHOOK_URL · TELEGRAM_BOT_TOKEN · TELEGRAM_CHAT_ID",
                "⛔ Giữ ở .env: chuông phải kêu cả khi ứng dụng/CSDL đã chết, và kẻ chiếm tài khoản quản trị ⛔ được tắt "
                        + "chuông trước khi ra tay. Tự kiểm: docker exec songnhue-alertmanager amtool alert add …"));
        ra.add(new MucCauHinh(
                "HEALTHCHECKS",
                "Giám sát & cảnh báo",
                "Chuông canh chính hệ giám sát (healthchecks.io)",
                NGOAI_TAM_NHIN,
                CHAN,
                "Alertmanager (VPS-2)",
                ENV_VPS2 + " — HEALTHCHECKS_PING_URL",
                "Tự kiểm: trang healthchecks.io của check phải 'up'. Tắt tiếng ⇒ Alertmanager chết mà ⛔ ai biết."));
    }

    private boolean coGiaTri(String thuocTinh) {
        String v = env.getProperty(thuocTinh, "");
        return v != null && !v.isBlank();
    }

    /** {@code PONG} khi clamd trả lời; ngược lại một mô tả ngắn — ⛔ ném. */
    private String pingClamd(String host) {
        int cong = env.getProperty("app.clamav.port", Integer.class, 3310);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, cong), 1500);
            s.setSoTimeout(1500);
            OutputStream ra = s.getOutputStream();
            ra.write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            ra.flush();
            InputStream vao = s.getInputStream();
            // ⚠ Đọc tới NUL chứ ⛔ readNBytes(n): clamd có thể giữ kết nối, và chờ đủ n byte là treo tới hết timeout.
            StringBuilder tlb = new StringBuilder();
            for (int b = vao.read(); b > 0 && tlb.length() < 32; b = vao.read()) {
                tlb.append((char) b);
            }
            String tl = tlb.toString().strip();
            return tl.isEmpty() ? "⛔ phản hồi" : tl;
        } catch (IOException e) {
            return e.getClass().getSimpleName();
        }
    }
}
