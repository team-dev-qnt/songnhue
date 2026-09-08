package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentUsagePort;

/**
 * <b>Xoá tệp phải HỎI TRƯỚC — ở cả bốn cửa</b>, T40.26.
 *
 * <h2>Cái đã hỏng</h2>
 *
 * Đo 08/09: <b>3 trong 4</b> cửa xoá tệp ⛔ không hỏi ai đang dùng. Chỉ đường CMS
 * ({@code MediaService.deleteFile}) tra tham chiếu rồi ném {@code CMS-2009}; ba cửa còn lại —
 * {@code DELETE /api/v1/attachments/&#123;id&#125;}, tài liệu công trình, đính kèm nhật ký bảo trì —
 * xoá thẳng và chỉ <i>gỡ</i> tham chiếu <b>sau khi đã xoá</b>.
 *
 * <p>Triệu chứng ở phía người dân: bấm "Quyết định phê duyệt" trên cổng và nhận <b>404 trần</b>.
 * Triệu chứng ở phía quản trị: ⛔ không có gì cả — màn hình báo xoá thành công, và cột tài liệu của
 * công trình đã tự về NULL nên trông cũng hợp lý.
 *
 * <h2>Bài này đo BẤT BIẾN, ⛔ không đo từng controller</h2>
 *
 * Chốt chặn nằm ở {@code AttachmentService.delete} — chỗ dữ liệu đi qua (quy tắc 12) — nên gọi thẳng
 * cổng {@link AttachmentPort} là đi đúng con đường mà cả bốn cửa đều phải đi. Ba controller đã có
 * bài riêng cho phần <i>quyền</i> ({@code AttachmentDeleteHttpTest}); ở đây là phần <i>toàn vẹn</i>.
 */
class AttachmentUsageGateTest extends IntegrationTestBase {

    private static final String OWNER_TYPE = "TEST_T4026_OWNER";
    private static final String MA_CONG_TRINH = "T4026-CT-01";
    private static final String MA_LOI = "CMS-2009";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AttachmentPort attachments;

    @Autowired
    private List<AttachmentUsagePort> congTraCuu;

    @BeforeEach
    void setUp() {
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⚠ Danh sách cổng tra cứu KHÁC RỖNG — rỗng thì chốt chặn ⛔ không chặn gì (luật 7)")
    void congTraCuuKhacRong() {
        // Spring gom bean theo kiểu. Đổi tên gói, quên `@Component`, hay một lượt tách module hỏng
        // là danh sách về rỗng — và MỌI bài dưới đây vẫn xanh, vì `dangDung` luôn rỗng thì lượt xoá
        // luôn đi qua. Đây là chỗ duy nhất phát hiện được chuyện đó.
        assertThat(congTraCuu)
                .as("Phải có ít nhất `content` và `operations` cùng khai — đo 08/09 là đúng 2")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("⭐⭐ Công trình đang dẫn tới tệp ⇒ xoá bị CHẶN, kèm tên công trình")
    void congTrinhDangDanThiKhongXoaDuoc() {
        UUID tep = taoTep();
        long congTrinh = taoCongTrinh();
        jdbc.update(
                "UPDATE constructions SET operating_procedure_attachment_public_id = ? WHERE id = ?", tep, congTrinh);

        assertThatThrownBy(() -> attachments.delete(tep))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);

        // ⭐ Và tệp phải còn SỐNG: một lượt xoá bị từ chối ⛔ không được để lại bản ghi nửa vời.
        assertThat(conSong(tep))
                .as("Lượt xoá bị chặn mà tệp vẫn bị đánh dấu xoá")
                .isTrue();
    }

    @Test
    @DisplayName("⭐ Cột 'Phương án bảo vệ' cũng chặn — hai cột, ⛔ không phải một")
    void cotThuHaiCungChan() {
        // Danh sách cột nằm ở HAI nơi (`ConstructionAttachmentUsage` và `ConstructionDocumentRefCleaner`).
        // Thiếu vế này thì cột thứ hai có thể rơi khỏi truy vấn mà bài kiểm ⛔ không thấy.
        UUID tep = taoTep();
        long congTrinh = taoCongTrinh();
        jdbc.update("UPDATE constructions SET protection_plan_attachment_public_id = ? WHERE id = ?", tep, congTrinh);

        assertThatThrownBy(() -> attachments.delete(tep))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);
    }

    @Test
    @DisplayName("⭐⭐ ⛔ KHÔNG ai dẫn ⇒ xoá được — vế phân biệt hai trạng thái (luật 9)")
    void khongAiDanThiXoaDuoc() {
        // Thiếu vế này thì một cổng tra cứu hỏng theo kiểu "luôn trả về một dòng" vẫn làm ba bài
        // trên xanh, trong khi ⛔ không ai xoá được tệp nào nữa.
        UUID tep = taoTep();

        assertThatCode(() -> attachments.delete(tep)).doesNotThrowAnyException();
        assertThat(conSong(tep))
                .as("Xoá thành công thì bản ghi phải được đánh dấu xoá")
                .isFalse();
    }

    @Test
    @DisplayName("⭐ Công trình đã XOÁ MỀM ⛔ không còn chặn — tham chiếu chết ⛔ không được khoá kho")
    void congTrinhDaXoaThiKhongChan() {
        UUID tep = taoTep();
        long congTrinh = taoCongTrinh();
        jdbc.update(
                "UPDATE constructions SET operating_procedure_attachment_public_id = ?, deleted_at = now() WHERE id = ?",
                tep,
                congTrinh);

        // Truy vấn lọc `deleted_at IS NULL`. Thiếu vế lọc ấy thì mọi công trình đã thanh lý vẫn giữ
        // tệp của nó làm con tin, và ⛔ không ai dọn được kho nữa.
        assertThatCode(() -> attachments.delete(tep)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------

    private boolean conSong(UUID tep) {
        Integer con = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE public_id = ? AND deleted_at IS NULL", Integer.class, tep);
        return con != null && con > 0;
    }

    private UUID taoTep() {
        return jdbc.queryForObject(
                """
                INSERT INTO attachments (owner_type, original_name, storage_bucket, storage_key,
                                         content_type, size_bytes, status, scan_status)
                VALUES (?, 'quyet-dinh.pdf', 'kiem-thu', 't4026/' || gen_random_uuid(),
                        'application/pdf', 2048, 'READY', 'CLEAN')
                RETURNING public_id
                """,
                UUID.class,
                OWNER_TYPE);
    }

    private long taoCongTrinh() {
        Long donVi = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        return jdbc.queryForObject(
                """
                INSERT INTO constructions (code, name, construction_type, management_level, org_unit_id,
                                           lifecycle_state, operational_status, created_at)
                VALUES (?, 'Cống kiểm thử T40.26', 'CONG', 'XI_NGHIEP', ?,
                        'DANG_HOAT_DONG', 'BINH_THUONG', now())
                RETURNING id
                """,
                Long.class,
                MA_CONG_TRINH,
                donVi);
    }

    private void donDep() {
        jdbc.update(
                """
                UPDATE constructions
                   SET operating_procedure_attachment_public_id = NULL,
                       protection_plan_attachment_public_id = NULL
                 WHERE code = ?
                """,
                MA_CONG_TRINH);
        jdbc.update("DELETE FROM constructions WHERE code = ?", MA_CONG_TRINH);
        jdbc.update("DELETE FROM attachments WHERE owner_type = ?", OWNER_TYPE);
    }
}
