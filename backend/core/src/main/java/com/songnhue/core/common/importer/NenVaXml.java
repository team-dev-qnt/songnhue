package com.songnhue.core.common.importer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Hai biện pháp bảo vệ dùng chung cho mọi bộ đọc <b>ZIP chứa XML</b> của kho — <b>T59.14</b>.
 *
 * <h2>⛔ Vì sao bóc ra thay vì để mỗi bộ đọc một bản</h2>
 *
 * <p>Kho có <b>hai</b> họ tệp cùng hình dạng ấy: {@code .xlsx} ({@link SpreadsheetReader}) và
 * {@code .kmz} ({@link DocKmlSangGeoJson}) — cộng {@code .docx} ở đường kết xuất. Cả ba cần
 * <b>đúng hai</b> thứ giống nhau: tắt thực thể ngoài của XML, và trần giải nén.
 *
 * <p>Đây là <b>biện pháp bảo mật</b>, và luật 14 nói chỗ nào con người phải nhớ hai nơi thì chỗ ấy
 * cần một phép kiểm nhớ hộ. Hai bản sao của một biện pháp bảo mật ⛔ hỏng cùng lúc — chúng
 * <b>trôi khỏi nhau</b>: ai đó nâng trần ở một bên, hoặc thêm một thuộc tính an toàn mới ở một
 * bên, và bên kia nằm lại — ⛔ một dòng đỏ nào. ⇒ Một bản, một chỗ sửa.
 *
 * <h2>⚠ Phạm vi — thứ lớp này ⛔ lo</h2>
 *
 * <p><b>Zip slip</b> (mục tên {@code ../../etc/passwd}) ⛔ nằm ở đây, và đó là chủ ý: cả hai bộ đọc
 * hiện tại đều giữ nội dung <b>trong bộ nhớ</b> và ⛔ bao giờ ghi ra đĩa theo tên mục. Ngày có bộ
 * đọc nào ghi ra tệp thì nó phải tự lo, và lời này ở đây để lượt ấy ⛔ đọc nhầm sự im lặng của lớp
 * này thành một bảo đảm (luật 28).
 */
final class NenVaXml {

    /**
     * 64 MB: một bảng 5.000 dòng × 30 cột dạng XML thô chưa tới 20 MB, và một tệp KML phủ toàn bộ
     * hệ thống kênh của Công ty còn nhỏ hơn thế ⇒ trần này ⛔ chạm người dùng thật.
     */
    static final long TRAN_GIAI_NEN = 64L * 1024 * 1024;

    private NenVaXml() {}

    /**
     * Bộ đọc XML đã <b>tắt thực thể ngoài</b> — chống XXE (ASVS 5.5.2).
     *
     * <p>⛔ Cả hai thuộc tính đều cần: {@code SUPPORT_DTD=false} chặn khai báo DTD, còn
     * {@code IS_SUPPORTING_EXTERNAL_ENTITIES=false} là vế thứ hai của cùng một bức tường. Tệp đi
     * qua đây là tệp <b>người ngoài gửi lên</b> — một thực thể ngoài trỏ vào {@code file:///} hay
     * một URL nội bộ biến bộ đọc tệp thành một đường SSRF/đọc tệp tuỳ ý.
     */
    static XMLStreamReader xmlAnToan(InputStream input) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(input);
    }

    /**
     * Đọc trọn một mục ZIP với <b>trần giải nén</b> — chống "zip bomb" (T61.40, ASVS 5.5.2).
     *
     * <p>{@code readAllBytes()} trần đọc tới hết mục, mà tỉ lệ nén của XML lặp lại có thể trên
     * <b>1000:1</b>: một tệp 2 MB đi qua trần tải lên rồi nở ra hàng GB trong heap ⇒ worker chết,
     * kéo theo mọi việc nền khác. Trần tải lên chỉ chặn được phần <b>NÉN</b>.
     *
     * <p>⚠ Đếm <b>trong lúc đọc</b>, ⛔ hỏi {@code ZipEntry.getSize()}: con số ấy do <b>chính tệp
     * tải lên</b> khai ra, nên nó là lời của kẻ gửi chứ ⛔ phải một phép đo.
     *
     * <p>⚠ Ném {@code SYS-0014} (⛔ phải {@code SYS-0012} như javadoc bản cũ ở
     * {@code SpreadsheetReader} còn ghi — hai mã tách nhau ở WS-62 và chú thích nằm lại):
     * {@code SYS-0012} là <i>vượt trần DÒNG</i>, mã này là <i>vượt trần GIẢI NÉN</i>. Hai câu khác
     * nhau cho hai việc khác nhau.
     */
    static byte[] docCoTran(ZipInputStream zip, String ten) throws IOException {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        byte[] dem = new byte[8192];
        long tong = 0;
        int n;
        while ((n = zip.read(dem)) > 0) {
            tong += n;
            if (tong > TRAN_GIAI_NEN) {
                throw new ValidationException(ErrorCode.SYS_0014, TRAN_GIAI_NEN / (1024 * 1024), ten);
            }
            ra.write(dem, 0, n);
        }
        return ra.toByteArray();
    }
}
