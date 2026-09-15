package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.attachment.QuetLaiTepService;
import com.songnhue.core.application.attachment.VirusScanHandler;
import com.songnhue.core.application.job.JobService;
import com.songnhue.core.common.config.StorageProperties;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.infra.attachment.AttachmentRepository;
import com.songnhue.core.infra.storage.ObjectStorage;

import tools.jackson.databind.ObjectMapper;

/**
 * <b>Tệp tải lên TRƯỚC khi có ClamAV phải được quét lại — T61.24.</b>
 *
 * <p>CSDL thật + MinIO thật + một <b>clamd giả</b> nói đúng giao thức INSTREAM (⛔ mock
 * {@link VirusScanHandler}: luật 4 — mock đặt đúng chỗ mã chạm ra ngoài là chưa kiểm gì). clamd giả trả
 * {@code FOUND} khi nội dung chứa {@code EICAR-GIA}, {@code ERROR} khi chứa {@code LOI-GIA}, còn lại
 * {@code OK}; chế độ "chết" đóng kết nối ⛔ trả lời.
 *
 * <p>⚠ Mọi đợt chạy từ con trỏ = id lớn nhất TRƯỚC khi bài dựng tệp ⇒ ⛔ đụng tệp {@code SKIPPED} của
 * lớp kiểm khác ({@code MediaLibraryTest} khẳng định {@code SKIPPED} — surefire xếp lớp khác nhau giữa
 * macOS và Linux, §11.19).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuetLaiTepTest extends IntegrationTestBase {

    @Autowired
    private AttachmentRepository repository;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobService jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Bean thật của ngữ cảnh kiểm thử — ClamAV ⛔ cấu hình. */
    @Autowired
    private QuetLaiTepService quetLaiChuaCoMayQuet;

    private ServerSocket clamdGia;
    private final AtomicBoolean chet = new AtomicBoolean();
    private QuetLaiTepService quetLai;
    private VirusScanHandler mayQuet;
    private long moc;

    @BeforeAll
    void dungClamdGia() throws IOException {
        clamdGia = new ServerSocket(0);
        Thread t = new Thread(this::phucVu, "clamd-gia");
        t.setDaemon(true);
        t.start();
        mayQuet = new VirusScanHandler(repository, storage, objectMapper, "127.0.0.1", clamdGia.getLocalPort());
        quetLai = new QuetLaiTepService(repository, mayQuet, jobs, objectMapper, transactionManager);
    }

    @AfterAll
    void tatClamdGia() throws IOException {
        clamdGia.close();
    }

    @BeforeEach
    void datMoc() {
        chet.set(false);
        moc = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM attachments", Long.class);
    }

    /** Khôi phục ở {@code @AfterEach} — ⛔ cuối phương thức (T48.8). */
    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM jobs WHERE job_type = 'VIRUS_RESCAN'");
        jdbc.update("UPDATE attachments SET deleted_at = now() WHERE id > ? AND deleted_at IS NULL", moc);
    }

    @Test
    @DisplayName("⭐⭐ SKIPPED/ERROR ⇒ sạch thành CLEAN, nhiễm thành QUARANTINED; tệp CLEAN và tệp đã xoá ⛔ bị đụng")
    void quetLaiDungKetCuc() {
        long sachCu = tep("tai lieu sach", "SKIPPED");
        long nhiemCu = tep("mo dau EICAR-GIA ket thuc", "SKIPPED");
        long loiCu = tep("tai lieu tung loi quet", "ERROR");
        long daSach = tep("da quet roi", "CLEAN");
        long daXoa = tep("EICAR-GIA nhung da xoa", "SKIPPED");
        jdbc.update("UPDATE attachments SET deleted_at = now() WHERE id = ?", daXoa);

        QuetLaiTepService.KetQua kq = quetLai.chayDot(moc, Duration.ofMinutes(5), p -> {});

        assertThat(kq.sach()).isEqualTo(2);
        assertThat(kq.nhiem()).isEqualTo(1);
        assertThat(kq.conTiep()).isFalse();
        assertThat(trangThai(sachCu)).isEqualTo("CLEAN/READY");
        assertThat(trangThai(nhiemCu))
                .as("⛔⛔ tệp CŨ nhiễm mã độc đang tải xuống được phải bị cách ly")
                .isEqualTo("INFECTED/QUARANTINED");
        assertThat(trangThai(loiCu)).isEqualTo("CLEAN/READY");
        assertThat(trangThai(daSach)).isEqualTo("CLEAN/READY");
        assertThat(trangThai(daXoa)).as("tệp đã xoá ⛔ quét").isEqualTo("SKIPPED/READY");
    }

    @Test
    @DisplayName("⛔ Máy quét ⛔ kết luận được ⇒ tệp cũ GIỮ NGUYÊN SKIPPED/READY — ⛔ khoá tài liệu vì máy quét trục trặc")
    void loiQuetGiuNguyen() {
        long id = tep("LOI-GIA qua lon", "SKIPPED");

        QuetLaiTepService.KetQua kq = quetLai.chayDot(moc, Duration.ofMinutes(5), p -> {});

        assertThat(kq.loi()).isEqualTo(1);
        assertThat(trangThai(id)).isEqualTo("SKIPPED/READY");
    }

    @Test
    @DisplayName("⛔ Máy quét chết ⇒ DỪNG chuỗi sau 3 tệp, ⛔ đi hết kho với 30 s chờ mỗi tệp")
    void mayQuetChetDungChuoi() {
        for (int i = 0; i < 5; i++) {
            tep("tep " + i, "SKIPPED");
        }
        chet.set(true);

        QuetLaiTepService.KetQua kq = quetLai.chayDot(moc, Duration.ofMinutes(5), p -> {});

        assertThat(kq.mayQuetChet()).isTrue();
        assertThat(kq.loi())
                .as("dừng ở tệp lỗi thứ 3 liên tiếp — tệp 4, 5 ⛔ chờ 30 s mỗi tệp")
                .isEqualTo(3);
        assertThat(kq.conTiep()).as("dừng hẳn, ⛔ đặt đợt kế").isFalse();
    }

    @Test
    @DisplayName("⛔ Hết ngân sách giờ ⇒ conTiep + con trỏ ⛔ vượt tệp chưa quét (đợt kế nhặt lại)")
    void hetNganSachDatDotKe() {
        tep("tai lieu", "SKIPPED");

        QuetLaiTepService.KetQua kq = quetLai.chayDot(moc, Duration.ZERO, p -> {});

        assertThat(kq.conTiep()).isTrue();
        assertThat(kq.idCuoi())
                .as("con trỏ đứng yên ở mốc ⇒ đợt kế bắt đầu đúng tệp này")
                .isEqualTo(moc);
    }

    @Test
    @DisplayName("⭐ Khởi động: có máy quét + còn tệp ⇒ đặt ĐÚNG MỘT job; ⛔ máy quét ⇒ ⛔ đặt")
    void khoiDongDatMotJob() {
        tep("tai lieu", "SKIPPED");

        assertThat(quetLaiChuaCoMayQuet.kichHoatNeuCan())
                .as("ngữ cảnh kiểm thử ⛔ có ClamAV ⇒ ⛔ đặt job (đặt thì mọi đợt đều lỗi)")
                .isEmpty();
        assertThat(quetLai.kichHoatNeuCan()).isPresent();
        assertThat(quetLai.kichHoatNeuCan())
                .as("lượt khởi động thứ hai ⛔ đặt thêm")
                .isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM jobs WHERE job_type = 'VIRUS_RESCAN' AND status IN ('PENDING','RUNNING')",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Hết lượt thử VIRUS_SCAN ⇒ tệp PENDING thành ERROR/FAILED; tệp đã có kết luận ⛔ bị ghi đè")
    void hetLuotThuGhiError() {
        long dangCho = tep("dang cho", "PENDING");
        long daSach = tep("da sach", "CLEAN");

        mayQuet.khiHetLuotThu("{\"attachmentId\":%d}".formatted(dangCho), "IllegalStateException: INSTREAM ERROR");
        mayQuet.khiHetLuotThu("{\"attachmentId\":%d}".formatted(daSach), "IllegalStateException: muộn");

        assertThat(trangThai(dangCho))
                .as("⛔ trước T61.24 tệp kẹt UPLOADING vĩnh viễn, ScanStatus.ERROR 0 nơi ghi")
                .isEqualTo("ERROR/FAILED");
        assertThat(trangThai(daSach)).isEqualTo("CLEAN/READY");
    }

    // -------------------------------------------------------------------------

    private long tep(String noiDung, String scan) {
        String khoa = "t6124/" + UUID.randomUUID();
        storage.put(storageProperties.getBucketMedia(), khoa, noiDung.getBytes(StandardCharsets.UTF_8), "text/plain");
        Attachment a = new Attachment("ARTICLE", 1L, "t6124.txt", storageProperties.getBucketMedia(), khoa);
        a.setContentType("text/plain");
        a.setSizeBytes(noiDung.length());
        if ("SKIPPED".equals(scan)) {
            a.markScanSkipped("Chưa cấu hình ClamAV");
        } else if ("ERROR".equals(scan)) {
            a.markScanError("lỗi cũ");
        } else if ("CLEAN".equals(scan)) {
            a.markClean();
        } // PENDING — trạng thái khởi tạo
        return repository.saveAndFlush(a).getId();
    }

    private String trangThai(long id) {
        return jdbc.queryForObject(
                "SELECT scan_status || '/' || status FROM attachments WHERE id = ?", String.class, id);
    }

    private void phucVu() {
        while (!clamdGia.isClosed()) {
            try (Socket s = clamdGia.accept()) {
                if (chet.get()) {
                    continue; // đóng ⛔ trả lời ⇒ phản hồi rỗng ⇒ LOI
                }
                DataInputStream in = new DataInputStream(s.getInputStream());
                in.readNBytes("zINSTREAM\0".length());
                StringBuilder nd = new StringBuilder();
                for (int len = in.readInt(); len > 0; len = in.readInt()) {
                    nd.append(new String(in.readNBytes(len), StandardCharsets.UTF_8));
                }
                String tra = nd.indexOf("EICAR-GIA") >= 0
                        ? "stream: Eicar-Test-Signature FOUND"
                        : nd.indexOf("LOI-GIA") >= 0 ? "INSTREAM size limit exceeded. ERROR" : "stream: OK";
                OutputStream out = s.getOutputStream();
                out.write((tra + "\0").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                if (clamdGia.isClosed()) {
                    return;
                }
            }
        }
    }
}
