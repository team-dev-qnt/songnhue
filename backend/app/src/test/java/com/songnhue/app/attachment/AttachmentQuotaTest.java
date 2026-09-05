package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentUploadCommand;

/**
 * Hạn mức dung lượng tệp đính kèm — WS-12/T12.6, phục vụ CN-02.3 (500MB mỗi công trình).
 *
 * <p><b>Bài kiểm này hỏi một câu khác câu thường hỏi.</b> Không phải "mã có đọc được tham số không"
 * — mã nào chẳng đọc được một con số. Câu hỏi là: <b>đổi tham số thì hành vi có đổi theo không.</b>
 *
 * <p>Phân biệt ấy không phải chuyện chữ nghĩa. Chính task này lộ ra rằng {@code AttachmentService}
 * từ WS-6 đọc khoá {@code limit.upload.max-file-mb} — một khoá <b>chưa từng được seed</b> — nên mọi
 * lượt tải rơi về 20MB cứng trong mã, trong khi màn hình cấu hình bày ra ba tham số
 * {@code limits.upload.max-mb.*} mà không dòng mã nào đọc. Một bài kiểm chỉ gọi {@code getInt} rồi so
 * với giá trị dự phòng sẽ <b>xanh trọn vẹn</b> suốt thời gian đó.
 *
 * <p>⚠ <b>Giới hạn đã biết của bài kiểm này, nói thẳng để không ai hiểu nhầm phạm vi.</b> MinIO trong
 * môi trường kiểm thử là địa chỉ giả ({@code IntegrationTestBase}), nên <b>không có lượt tải nào đi
 * tới kho thật</b>. Bài kiểm phủ đúng phần logic thêm mới: cả hai chốt chặn (dung lượng mỗi tệp và
 * hạn mức mỗi bản ghi) chạy <i>trước</i> khi mở kết nối kho, nên vẫn kiểm chứng được trọn vẹn — còn
 * lượt tải đi hết đường là việc của Definition of Done mục 7, cần MinIO thật (nợ #20).
 */
class AttachmentQuotaTest extends IntegrationTestBase {

    private static final String OWNER_TYPE = "TEST_QUOTA_OWNER";
    private static final String KHOA_HAN_MUC = "limits.attachment.quota-mb." + OWNER_TYPE;
    private static final String KHOA_CO_TEP = "limits.upload.max-mb.image";

    private static final int MOT_MB = 1024 * 1024;

    /** Quá dung lượng cho phép mỗi tệp — {@code FileValidator} ném {@code ValidationException}. */
    private static final String MA_QUA_DUNG_LUONG = "SYS-0003";

    private static final String MA_VUOT_HAN_MUC = "SYS-0010";

    @Autowired
    private AttachmentPort attachments;

    @Autowired
    private SettingService settings;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * ⚠ Phải dọn cả <b>bộ nhớ đệm</b>, không chỉ dòng trong CSDL.
     *
     * <p>Bản đầu chỉ {@code DELETE} dòng tham số. Kết quả: chạy riêng từng bài thì xanh, chạy cả lớp
     * thì một bài đỏ — giá trị hạn mức của bài trước còn nằm trong Caffeine và bài sau (vốn cố ý
     * không khai hạn mức) lại thấy có hạn mức. Đúng loại đỏ ngắt quãng ngốn nửa giờ để truy, và nó
     * cũng chính là thứ mà {@code SettingService.invalidate} sinh ra để tránh.
     */
    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM attachments WHERE owner_type = ?", OWNER_TYPE);
        jdbc.update("DELETE FROM settings WHERE setting_key = ?", KHOA_HAN_MUC);
        settings.invalidate(KHOA_HAN_MUC);
        datThamSo(KHOA_CO_TEP, "10");
        datThamSo("limits.upload.max-mb.document", "50");
    }

    // ---- Đếm dung lượng đang dùng -------------------------------------------

    @Test
    @DisplayName("Dung lượng đang dùng cộng đúng, và tính riêng từng bản ghi")
    void demDungLuongTheoTungBanGhi() {
        themTepDaCo(1L, 3 * MOT_MB);
        themTepDaCo(1L, 2 * MOT_MB);
        themTepDaCo(2L, 7 * MOT_MB);

        assertThat(attachments.usedBytes(OWNER_TYPE, 1L)).isEqualTo(5L * MOT_MB);
        assertThat(attachments.usedBytes(OWNER_TYPE, 2L)).isEqualTo(7L * MOT_MB);
        assertThat(attachments.usedBytes(OWNER_TYPE, 99L)).isZero();
    }

    @Test
    @DisplayName("Tệp đã xoá mềm không còn tính vào hạn mức")
    void tepDaXoaKhongTinh() {
        long id = themTepDaCo(3L, 4 * MOT_MB);
        jdbc.update("UPDATE attachments SET deleted_at = now() WHERE id = ?", id);

        assertThat(attachments.usedBytes(OWNER_TYPE, 3L))
                .as("xoá tệp là cách người dùng lấy lại chỗ — không trừ ra thì hạn mức thành cái bẫy")
                .isZero();
    }

    // ---- Hạn mức mỗi bản ghi -------------------------------------------------

    @Test
    @DisplayName("⛔ Vượt hạn mức của bản ghi → SYS-0010")
    void vuotHanMucThiChan() {
        themThamSo(KHOA_HAN_MUC, "5");
        themTepDaCo(4L, 4 * MOT_MB);

        assertThat(maLoiKhiTai(anh(4L, "them.png", 2 * MOT_MB))).isEqualTo(MA_VUOT_HAN_MUC);
    }

    @Test
    @DisplayName("Chưa chạm trần thì chốt hạn mức cho đi qua")
    void chuaChamTranThiChoQua() {
        themThamSo(KHOA_HAN_MUC, "5");
        themTepDaCo(5L, 1 * MOT_MB);

        assertThat(maLoiKhiTai(anh(5L, "them.png", 1 * MOT_MB)))
                .as("1MB + 1MB dưới trần 5MB — nếu chặn ở đây thì hạn mức đang tính sai")
                .isNotEqualTo(MA_VUOT_HAN_MUC);
    }

    @Test
    @DisplayName("Loại chủ sở hữu không khai hạn mức → không giới hạn")
    void khongKhaiThiKhongGioiHan() {
        themTepDaCo(6L, 900 * MOT_MB);

        assertThat(maLoiKhiTai(anh(6L, "them.png", 1 * MOT_MB)))
                .as("hạn mức là ngoại lệ cho vài loại hồ sơ nặng, không phải luật chung")
                .isNotEqualTo(MA_VUOT_HAN_MUC);
    }

    // ---- Dung lượng mỗi tệp: đổi tham số thì hành vi đổi theo ----------------

    @Test
    @DisplayName("⭐ Đổi tham số dung lượng mỗi tệp thì hành vi ĐỔI THEO")
    void doiThamSoThiHanhViDoiTheo() {
        datThamSo(KHOA_CO_TEP, "1");
        assertThat(maLoiKhiTai(anh(7L, "to.png", 2 * MOT_MB)))
                .as(
                        """
                        Trần 1MB, tải 2MB → phải bị chặn. Trước bản sửa T12.6 thì lượt này ĐI QUA, vì mã \
                        đọc một khoá không tồn tại và luôn rơi về giá trị dự phòng 20MB cứng.""")
                .isEqualTo(MA_QUA_DUNG_LUONG);

        datThamSo(KHOA_CO_TEP, "5");
        assertThat(maLoiKhiTai(anh(7L, "to.png", 2 * MOT_MB)))
                .as(
                        "nới trần lên 5MB thì đúng tệp đó phải qua được chốt dung lượng — nếu không, tham số là đồ trang trí")
                .isNotEqualTo(MA_QUA_DUNG_LUONG);
    }

    @Test
    @DisplayName("Ảnh và tài liệu tra hai tham số khác nhau")
    void moiNhomDinhDangMotThamSo() {
        datThamSo(KHOA_CO_TEP, "1");
        datThamSo("limits.upload.max-mb.document", "50");

        assertThat(maLoiKhiTai(anh(8L, "anh.png", 2 * MOT_MB)))
                .as("ảnh 2MB vượt trần ảnh 1MB")
                .isEqualTo(MA_QUA_DUNG_LUONG);

        assertThat(maLoiKhiTai(taiLieu(8L, "ho-so.pdf", 2 * MOT_MB)))
                .as("cùng 2MB nhưng là tài liệu, trần 50MB — dùng chung một tham số là sai nghiệp vụ CN-01.3")
                .isNotEqualTo(MA_QUA_DUNG_LUONG);
    }

    // -------------------------------------------------------------------------

    /**
     * Mã lỗi của lượt tải, hoặc {@code "KHONG_LOI"} nếu đi trót lọt.
     *
     * <p>⚠ Vì sao không dùng {@code assertThatThrownBy(...).isInstanceOf(...)}: MinIO ở môi trường
     * kiểm thử là địa chỉ giả, nên một lượt tải <b>hợp lệ</b> vẫn kết thúc bằng lỗi kho
     * ({@code SYS-0006}). Câu hỏi của bài kiểm không phải "có lỗi hay không" mà là "<b>có bị chặn
     * đúng bởi chốt đang xét hay không</b>" — nên phải so đúng mã lỗi, và câu khẳng định phủ định
     * ("không bị chặn bởi chốt này") mới có nghĩa.
     */
    private String maLoiKhiTai(AttachmentUploadCommand lenh) {
        Throwable loi = catchThrowable(() -> attachments.upload(lenh));
        return loi == null ? "KHONG_LOI" : loi.getMessage();
    }

    private static AttachmentUploadCommand anh(Long ownerId, String ten, int soByte) {
        return lenh(ownerId, ten, soByte, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, "image/png");
    }

    private static AttachmentUploadCommand taiLieu(Long ownerId, String ten, int soByte) {
        return lenh(ownerId, ten, soByte, new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '4'}, "application/pdf");
    }

    /** Nội dung độn cho đủ kích thước, nhưng giữ magic bytes thật để qua được bước nhận dạng. */
    private static AttachmentUploadCommand lenh(Long ownerId, String ten, int soByte, byte[] magicBytes, String mime) {
        byte[] noiDung = new byte[soByte];
        System.arraycopy(magicBytes, 0, noiDung, 0, magicBytes.length);
        return new AttachmentUploadCommand(OWNER_TYPE, ownerId, "kiem-thu", ten, noiDung, List.of(mime));
    }

    /** Bản ghi tệp "đã nằm trên kho" — dựng thẳng bằng SQL vì kho thật không có trong môi trường này. */
    private long themTepDaCo(long ownerId, long sizeBytes) {
        return jdbc.queryForObject(
                """
                INSERT INTO attachments (owner_type, owner_id, original_name, storage_bucket, storage_key,
                                         content_type, size_bytes, status, scan_status, created_at)
                VALUES (?, ?, 'da-co.png', 'media', ?, 'image/png', ?, 'READY', 'CLEAN', now())
                RETURNING id
                """,
                Long.class,
                OWNER_TYPE,
                ownerId,
                "key-" + java.util.UUID.randomUUID(),
                sizeBytes);
    }

    private void themThamSo(String key, String value) {
        jdbc.update(
                """
                INSERT INTO settings (setting_key, setting_value, value_type, default_value,
                                      group_code, label, editable, exportable, sort_order)
                VALUES (?, ?, 'INTEGER', ?, 'LIMIT', 'Hạn mức bài kiểm', TRUE, TRUE, 999)
                ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value
                """,
                key,
                value,
                value);
        settings.invalidate(key);
    }

    /**
     * {@code SettingService} có bộ nhớ đệm Caffeine; bài kiểm ghi thẳng vào bảng nên phải tự dọn.
     *
     * <p>Không có bước này thì lần đọc thứ hai lấy giá trị cũ và bài kiểm báo sai — đúng loại "đỏ vì
     * hạ tầng kiểm thử" mà người đọc log sẽ mất nửa giờ để loại trừ.
     */
    private void datThamSo(String key, String value) {
        jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", value, key);
        settings.invalidate(key);
    }
}
