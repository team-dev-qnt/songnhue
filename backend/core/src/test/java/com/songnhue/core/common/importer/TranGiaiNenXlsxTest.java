package com.songnhue.core.common.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.exception.ValidationException;

/**
 * <b>Trần GIẢI NÉN của bộ đọc xlsx — T61.40</b> (ASVS 5.5.2, "zip bomb").
 *
 * <h2>Vì sao trần tải lên ⛔ đủ</h2>
 *
 * <p>Trần tải lên chặn phần <b>NÉN</b>. XML lặp lại nén được trên <b>1000:1</b>, nên một tệp 2 MB đi
 * qua mọi cửa rồi nở ra hàng GB ngay trong heap của worker nhập liệu — {@code readAllBytes()} đọc tới
 * hết mục, ⛔ hỏi gì thêm. Worker chết kéo theo mọi việc nền khác (quét virus, gửi thư, sao lưu).
 *
 * <p>⚠ Bài này dựng một tệp nén THẬT bằng {@code ZipOutputStream} — ⛔ mock, vì thứ cần đo là hành vi
 * của chính bộ đọc trên một luồng nén (luật 4).
 */
class TranGiaiNenXlsxTest {

    /** Một mục xlsx mang {@code so} byte giống nhau — nén xuống vài KB, nở ra đúng {@code so} byte. */
    private static byte[] xlsxVoiSheetCo(long so) throws Exception {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(ra)) {
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            // ⚠ XML phải ĐÚNG CÚ PHÁP: bản đầu của bài này ghi các thẻ <row> trần, và bộ đọc đỏ ở
            //   khâu phân tích XML chứ ⛔ ở trần giải nén — xanh/đỏ vì một lý do khác thứ đang đo.
            zip.write("<worksheet><sheetData>".getBytes(StandardCharsets.UTF_8));
            // ⛔⛔ MỘT dòng duy nhất, ô dài vô tận — ⛔ nhiều dòng nhỏ. Bản đầu ghi ~1,4 triệu dòng, nên
            //   thứ ném ra là trần DÒNG (`SYS-0012`) chứ ⛔ trần giải nén: bài kiểm XANH cả khi đã gỡ chốt
            //   (đo được 16/09 — luật 1 + luật 9). Với một dòng thì chỉ còn đúng một chốt có thể bắn.
            zip.write("<row><c t=\"inlineStr\"><is><t>".getBytes(StandardCharsets.UTF_8));
            byte[] khoi = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
            long daGhi = 0;
            while (daGhi < so) {
                zip.write(khoi);
                daGhi += khoi.length;
            }
            zip.write("</t></is></c></row>".getBytes(StandardCharsets.UTF_8));
            zip.write("</sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return ra.toByteArray();
    }

    @Test
    @DisplayName("⛔⛔ Mục nở quá trần ⇒ ném SYS-0012 — ⛔ để nó nuốt hết heap rồi mới chết")
    void noQuaTranThiNem() throws Exception {
        byte[] bom = xlsxVoiSheetCo(70L * 1024 * 1024);
        assertThat(bom.length)
                .as("chống tập rỗng: tệp NÉN phải nhỏ — đó chính là hình dạng của một zip bomb")
                .isLessThan(5 * 1024 * 1024);

        assertThatThrownBy(() -> SpreadsheetReader.read(bom))
                .isInstanceOf(ValidationException.class)
                .as("⛔ SYS-0012 (trần DÒNG): hai trạng thái phải phân biệt được")
                .hasMessageContaining("SYS-0014");
    }

    @Test
    @DisplayName("⭐ Đối chứng: tệp bình thường vẫn đọc được — trần ⛔ chạm người dùng thật")
    void tepBinhThuongVanDoc() throws Exception {
        byte[] nho = xlsxVoiSheetCo(64 * 1024);

        assertThatCode(() -> SpreadsheetReader.read(nho))
                .as("một bảng 5.000 dòng × 30 cột dạng XML thô chưa tới 20 MB")
                .doesNotThrowAnyException();
    }
}
