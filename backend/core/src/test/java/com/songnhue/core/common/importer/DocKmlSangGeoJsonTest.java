package com.songnhue.core.common.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.stream.XMLInputFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.common.exception.ValidationException;

/**
 * Bộ đọc KML/KMZ — <b>T59.14</b>.
 *
 * <h2>⚠ Vì sao đọc kết quả bằng bộ phân tích JSON THẬT chứ ⛔ bằng {@code contains}</h2>
 *
 * <p>{@code contains("105.78")} xanh cả khi con số ấy nằm ở <b>ô vĩ độ</b> — tức nó ⛔ phân biệt
 * được hai trạng thái mà bài này sinh ra để phân biệt (luật 9, và T46.8 đã trả giá đúng chuyện
 * này: {@code contains("value":1)} khớp cả {@code "value":12}). Mỗi khẳng định dưới đây đi qua
 * {@link ObjectMapper} rồi hỏi <b>đúng chỉ số</b>.
 */
class DocKmlSangGeoJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Cống Liên Mạc, đo thật từ bản đồ: kinh độ ~105.78, vĩ độ ~21.08. Hai số ⛔ thể lẫn nhau. */
    private static final String KINH_DO = "105.7825";

    private static final String VI_DO = "21.0842";

    private static String kml(String than) {
        return """
               <?xml version="1.0" encoding="UTF-8"?>
               <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
               """
                + than
                + "</Document></kml>";
    }

    private static JsonNode doi(String vanBan) {
        try {
            return JSON.readTree(DocKmlSangGeoJson.doi("thu.kml", vanBan.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("⛔ đọc lại được GeoJSON vừa sinh", e);
        }
    }

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Thứ tự toạ độ GIỮ NGUYÊN kinh-độ-trước — đảo nhầm là đưa Hà Nội xuống Ấn Độ Dương")
    void longitudeStaysFirstExactlyAsKmlAndGeoJsonBothRequire() {
        JsonNode ra = doi(kml(
                "<Placemark><Point><coordinates>" + KINH_DO + "," + VI_DO + ",0</coordinates></Point></Placemark>"));

        JsonNode toaDo = ra.at("/features/0/geometry/coordinates");

        assertThat(toaDo.get(0).asDouble())
                .as("⛔⛔ Ô THỨ NHẤT phải là KINH ĐỘ. KML và GeoJSON đều ghi lon trước (RFC 7946 "
                        + "§3.1.1), còn Leaflet nhận lat trước — nên người sửa mã rất dễ 'sửa cho đúng' "
                        + "rồi đảo nhầm, và triệu chứng là bản đồ vẽ đúng hình dạng ở SAI bán cầu")
                .isEqualTo(105.7825);
        assertThat(toaDo.get(1).asDouble()).isEqualTo(21.0842);

        assertThat(toaDo)
                .as("Cao độ bị BỎ — bản đồ của hệ là 2 chiều, giữ chiều thứ ba là kéo dài mọi toạ độ " + "mà ⛔ ai đọc")
                .hasSize(2);
    }

    @Test
    @DisplayName("⭐ Ba kiểu hình học đọc được, và MultiGeometry tách thành nhiều đặc trưng")
    void pointLineAndPolygonAllConvert() {
        JsonNode ra = doi(
                kml(
                        """
                <Placemark><name>Điểm</name><Point><coordinates>105.1,21.1</coordinates></Point></Placemark>
                <Placemark><name>Tuyến</name><LineString><coordinates>
                   105.1,21.1 105.2,21.2
                </coordinates></LineString></Placemark>
                <Placemark><name>Vùng</name><Polygon>
                  <outerBoundaryIs><LinearRing><coordinates>
                    105.0,21.0 105.1,21.0 105.1,21.1 105.0,21.0
                  </coordinates></LinearRing></outerBoundaryIs>
                  <innerBoundaryIs><LinearRing><coordinates>
                    105.02,21.02 105.04,21.02 105.04,21.04 105.02,21.02
                  </coordinates></LinearRing></innerBoundaryIs>
                </Polygon></Placemark>
                <Placemark><name>Gộp</name><MultiGeometry>
                  <Point><coordinates>105.3,21.3</coordinates></Point>
                  <Point><coordinates>105.4,21.4</coordinates></Point>
                </MultiGeometry></Placemark>
                """));

        assertThat(ra.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(ra.get("features"))
                .as("3 Placemark đơn + 2 hình trong MultiGeometry = 5 đặc trưng")
                .hasSize(5);

        assertThat(ra.at("/features/1/geometry/type").asText()).isEqualTo("LineString");
        assertThat(ra.at("/features/1/geometry/coordinates")).hasSize(2);

        assertThat(ra.at("/features/2/geometry/type").asText()).isEqualTo("Polygon");
        assertThat(ra.at("/features/2/geometry/coordinates"))
                .as("⛔ Vòng NGOÀI rồi mới tới LỖ — KML ghi theo thứ tự ấy và RFC 7946 §3.1.6 đòi "
                        + "đúng thứ tự ấy, nên ⛔ cần theo dõi thẻ innerBoundaryIs")
                .hasSize(2);
        assertThat(ra.at("/features/2/geometry/coordinates/0/0/0").asDouble()).isEqualTo(105.0);
        assertThat(ra.at("/features/2/geometry/coordinates/1/0/0").asDouble()).isEqualTo(105.02);

        assertThat(ra.at("/features/3/properties/name").asText()).isEqualTo("Gộp");
        assertThat(ra.at("/features/4/properties/name").asText()).isEqualTo("Gộp");
    }

    @Test
    @DisplayName("⭐ `description` mang HTML và dấu nháy vẫn cho ra JSON ĐỌC LẠI ĐƯỢC")
    void descriptionWithQuotesAndHtmlStaysParseable() {
        String hiem = "<b class=\"x\">Cống \"Liên Mạc\"</b> \\ xuống\ndòng";
        JsonNode ra = doi(kml("<Placemark><name>A</name><description><![CDATA[" + hiem
                + "]]></description><Point><coordinates>105.1,21.1</coordinates></Point></Placemark>"));

        assertThat(ra.at("/features/0/properties/description").asText())
                .as("⛔ Ghép chuỗi bằng tay ở bộ sinh là một lỗ chèn JSON, và nó lộ ra tận giao diện "
                        + "dưới dạng 'bản đồ ⛔ vẽ được'")
                .contains("Liên Mạc");
    }

    @Test
    @DisplayName("⭐ Placemark ⛔ có hình học bị BỎ — một đặc trưng ⛔ geometry là chấm ⛔ vẽ được ở đâu")
    void placemarkWithoutGeometryIsDropped() {
        JsonNode ra = doi(
                kml(
                        """
                <Folder><name>Thư mục</name>
                  <Placemark><name>Chỉ có nhãn</name></Placemark>
                  <Placemark><name>Có hình</name><Point><coordinates>105.1,21.1</coordinates></Point></Placemark>
                </Folder>
                """));

        assertThat(ra.get("features"))
                .as("⚠ Vế chống tập rỗng đi kèm: phải còn ĐÚNG một đặc trưng, ⛔ phải không còn cái nào")
                .hasSize(1);
        assertThat(ra.at("/features/0/properties/name").asText())
                .as("Folder lồng nhau được TRẢI PHẲNG — `name` của thư mục ⛔ được trèo vào Placemark")
                .isEqualTo("Có hình");
    }

    @Test
    @DisplayName("⭐ KMZ: ưu tiên `doc.kml`, ⛔ lấy bừa tệp .kml đầu tiên")
    void kmzPrefersDocKml() throws Exception {
        byte[] kmz = kmz(
                "khac.kml", kml("<Placemark><Point><coordinates>1,1</coordinates></Point></Placemark>"),
                "doc.kml",
                        kml("<Placemark><name>Chính</name><Point><coordinates>" + KINH_DO + "," + VI_DO
                                + "</coordinates></Point></Placemark>"));

        JsonNode ra = JSON.readTree(DocKmlSangGeoJson.doi("goi.kmz", kmz));

        assertThat(ra.at("/features/0/properties/name").asText()).isEqualTo("Chính");
        assertThat(ra.at("/features/0/geometry/coordinates/0").asDouble()).isEqualTo(105.7825);
    }

    @Test
    @DisplayName("⭐ Nhận ra ZIP bằng BYTE, ⛔ bằng đuôi tệp — người dùng đổi tên vẫn phải đọc được")
    void zipIsDetectedByMagicBytesNotByExtension() throws Exception {
        byte[] kmz = kmz("doc.kml", kml("<Placemark><Point><coordinates>105.1,21.1</coordinates></Point></Placemark>"));

        // Tên khai `.kml` nhưng nội dung là ZIP — đuôi là LỜI KHAI, byte là PHÉP ĐO.
        JsonNode ra = JSON.readTree(DocKmlSangGeoJson.doi("that-ra-la-kmz.kml", kmz));
        assertThat(ra.get("features")).hasSize(1);
    }

    @Test
    @DisplayName("⛔ KMZ ⛔ chứa tệp .kml nào ⇒ OPS-2033 kèm lý do ĐO ĐƯỢC, ⛔ một câu chung chung")
    void kmzWithoutAnyKmlEntryIsRejectedWithAReadableReason() throws Exception {
        byte[] kmz = kmz("anh/bieu-tuong.png", "⛔ phải kml");

        assertThatThrownBy(() -> DocKmlSangGeoJson.doi("goi.kmz", kmz))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OPS-2033");
    }

    @Test
    @DisplayName("⛔ XML hỏng ⇒ OPS-2033 — KHÁC hẳn OPS-2026 (đọc được mà rỗng hình học)")
    void malformedXmlIsRejected() {
        assertThatThrownBy(() ->
                        DocKmlSangGeoJson.doi("thu.kml", "<kml><Placemark><Point>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OPS-2033");
    }

    @Test
    @DisplayName("⭐⭐ XXE: thực thể ngoài ⛔ được giải — và ĐỐI CHỨNG chứng minh bức tường là thứ chặn")
    void externalEntitiesAreRefused() throws Exception {
        String doc =
                """
                <?xml version="1.0"?>
                <!DOCTYPE kml [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
                <kml><Placemark><name>&xxe;</name>
                <Point><coordinates>105.1,21.1</coordinates></Point></Placemark></kml>
                """;
        byte[] than = doc.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> DocKmlSangGeoJson.doi("doc-ac.kml", than))
                .as("Tệp đi qua đây là tệp NGƯỜI NGOÀI gửi lên; một thực thể trỏ `file:///` biến bộ "
                        + "đọc bản đồ thành một đường đọc tệp tuỳ ý")
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OPS-2033");

        // ⭐ ĐỐI CHỨNG — ⛔ có vế này thì cái đỏ ở trên có thể chỉ vì XML hỏng, và bài kiểm ⛔ chứng
        //    minh được rằng chính BỨC TƯỜNG là thứ chặn (luật 9 · luật 29).
        XMLInputFactory macDinh = XMLInputFactory.newFactory();
        assertThatCode(() -> macDinh.createXMLStreamReader(new ByteArrayInputStream(than))
                        .next())
                .as("Bộ đọc MẶC ĐỊNH nhận cùng bộ byte ấy ⇒ khác biệt đến từ SUPPORT_DTD=false, ⛔ từ "
                        + "việc tệp sai cú pháp")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⭐ Bộ toạ độ hỏng bị BỎ QUA chứ ⛔ làm hỏng cả tệp")
    void brokenCoordinateTupleIsSkippedNotFatal() {
        JsonNode ra = doi(kml("<Placemark><LineString><coordinates>"
                + "105.1,21.1 rac 105.2 105.3,21.3"
                + "</coordinates></LineString></Placemark>"));

        assertThat(ra.at("/features/0/geometry/coordinates"))
                .as("Hai bộ hỏng ('rac' ⛔ dấu phẩy, '105.2' thiếu vế) rơi ra; hai bộ đúng ở lại — một "
                        + "dòng rác ⛔ được làm mất cả tuyến kênh")
                .hasSize(2);
    }

    // =========================================================================

    /** Dựng một KMZ THẬT — ⛔ mock, vì thứ cần đo là hành vi trên một luồng nén (luật 4). */
    private static byte[] kmz(String... tenVaThan) throws Exception {
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(ra)) {
            for (int i = 0; i < tenVaThan.length; i += 2) {
                zip.putNextEntry(new ZipEntry(tenVaThan[i]));
                zip.write(tenVaThan[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return ra.toByteArray();
    }
}
