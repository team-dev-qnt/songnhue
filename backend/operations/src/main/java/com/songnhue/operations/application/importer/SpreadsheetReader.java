package com.songnhue.operations.application.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.VietnameseUtils;

/**
 * Đọc một bảng phẳng từ tệp CSV hoặc XLSX — nền của T17.9.
 *
 * <h2>⛔ Vì sao KHÔNG thêm Apache POI</h2>
 *
 * Tệp nhập danh mục công trình là một <b>bảng phẳng toàn chữ và số</b>: mã, tên, mã đơn vị, toạ độ,
 * năm xây dựng. Không công thức, không ô ngày tháng, không định dạng. Đọc được đúng chừng đó thì
 * XLSX chỉ là một tệp ZIP chứa XML, và JDK đã có sẵn cả hai ({@code java.util.zip} + StAX).
 *
 * <p>POI kéo theo {@code xmlbeans}, {@code commons-compress}, {@code commons-io} và là một trong
 * những nguồn CVE Java thường xuyên nhất — mà dự án này đã tự đặt luật "mỗi thành phần phải tự chứng
 * minh nó đáng nuôi" và đã <i>trả lại</i> một phiên bản MinIO cùng một module Testcontainers vì
 * đúng lý do đó. Khi nào cần <b>xuất</b> Excel có định dạng (CN-02.10, Phase 3) thì POI mới đáng.
 *
 * <h2>⚠ Giới hạn đã biết, ghi ra để không ai tưởng nó là bộ đọc Excel đầy đủ</h2>
 *
 * <ul>
 *   <li>Chỉ đọc <b>sheet đầu tiên</b>.
 *   <li>Đọc <b>giá trị đã lưu</b> của ô công thức, không tính lại công thức. Excel luôn lưu kèm giá
 *       trị nên việc này đúng với tệp do Excel/LibreOffice ghi ra; tệp do máy sinh mà không có
 *       {@code <v>} thì ô đó đọc ra rỗng — và lượt chạy khô sẽ báo thiếu, không nuốt lặng lẽ.
 *   <li><b>Không</b> đổi số sê-ri ngày của Excel sang ngày tháng. Bảng nhập công trình không có cột
 *       ngày nào (năm xây dựng là số nguyên), nên giới hạn này chưa chạm tới ai. Cột ngày đầu tiên
 *       xuất hiện thì <b>phải xử lý ở đây</b>, đừng để nơi gọi tự đoán.
 * </ul>
 */
public final class SpreadsheetReader {

    /** Chữ ký ZIP — mọi tệp XLSX bắt đầu bằng đúng bốn byte này. */
    private static final byte[] CHU_KY_ZIP = {0x50, 0x4B, 0x03, 0x04};

    /** Trần số dòng: tệp danh mục công trình đếm bằng trăm, không phải bằng triệu. */
    public static final int MAX_ROWS = 5000;

    private SpreadsheetReader() {}

    /**
     * Một dòng dữ liệu.
     *
     * @param rowNumber số dòng <b>như người dùng thấy trong Excel</b> (dòng tiêu đề là 1) — báo lỗi
     *     theo số dòng nội bộ thì người sửa tệp phải tự cộng trừ, và họ sẽ sửa nhầm dòng
     */
    public record Row(int rowNumber, Map<String, String> cells) {

        public String get(String column) {
            String value = cells.get(column);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** Nhận diện bằng <b>magic bytes</b>, không tin đuôi tệp — cùng luật với {@code FileValidator}. */
    public static List<Row> read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException(ErrorCode.OPS_2015);
        }
        return laXlsx(content) ? docXlsx(content) : docCsv(content);
    }

    private static boolean laXlsx(byte[] content) {
        if (content.length < CHU_KY_ZIP.length) {
            return false;
        }
        for (int i = 0; i < CHU_KY_ZIP.length; i++) {
            if (content[i] != CHU_KY_ZIP[i]) {
                return false;
            }
        }
        return true;
    }

    // === CSV =================================================================

    /**
     * Đọc CSV theo RFC 4180: dấu phẩy ngăn cột, ngoặc kép bọc ô, {@code ""} là một dấu ngoặc kép.
     *
     * <p>⚠ Bỏ BOM ở đầu tệp. Excel bản Windows luôn ghi BOM khi lưu CSV UTF-8, và nếu không bỏ thì
     * <b>tên cột đầu tiên</b> mang thêm một ký tự vô hình — hệ quả là "thiếu cột bắt buộc" ở một tệp
     * nhìn bằng mắt thì hoàn toàn đúng.
     */
    private static List<Row> docCsv(byte[] content) {
        // ⚠ Chặn tệp nhị phân sớm. Không có bước này thì một tệp .doc hoặc .pdf tải nhầm vẫn được
        // "đọc" thành một dòng ký tự rác, và người dùng nhận thông báo "tệp thiếu cột bắt buộc" —
        // câu đó dẫn họ đi sửa tiêu đề của một tệp không hề là bảng tính. Byte 0x00 là dấu hiệu đủ
        // chắc: văn bản UTF-8 hợp lệ không bao giờ chứa nó.
        for (byte b : content) {
            if (b == 0) {
                throw new ValidationException(ErrorCode.OPS_2015);
            }
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<List<String>> grid = tachCsv(text);
        if (grid.isEmpty()) {
            throw new ValidationException(ErrorCode.OPS_2015);
        }
        return dungRows(grid);
    }

    private static List<List<String>> tachCsv(String text) {
        List<List<String>> grid = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder o = new StringBuilder();
        boolean trongNgoac = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (trongNgoac) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        o.append('"');
                        i++;
                    } else {
                        trongNgoac = false;
                    }
                } else {
                    o.append(c);
                }
            } else if (c == '"') {
                trongNgoac = true;
            } else if (c == ',' || c == ';') {
                row.add(o.toString());
                o.setLength(0);
            } else if (c == '\n') {
                row.add(o.toString());
                o.setLength(0);
                grid.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                o.append(c);
            }
        }
        if (o.length() > 0 || !row.isEmpty()) {
            row.add(o.toString());
            grid.add(row);
        }
        return grid;
    }

    // === XLSX ================================================================

    private static List<Row> docXlsx(byte[] content) {
        try {
            List<String> chuoiDungChung = new ArrayList<>();
            byte[] sheet = null;

            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String ten = entry.getName();
                    if ("xl/sharedStrings.xml".equals(ten)) {
                        chuoiDungChung = docSharedStrings(zip.readAllBytes());
                    } else if ("xl/worksheets/sheet1.xml".equals(ten)) {
                        sheet = zip.readAllBytes();
                    }
                }
            }
            if (sheet == null) {
                throw new ValidationException(ErrorCode.OPS_2015);
            }
            return dungRows(docSheet(sheet, chuoiDungChung));
        } catch (IOException | XMLStreamException e) {
            throw new ValidationException(ErrorCode.OPS_2015, e.getMessage());
        }
    }

    /**
     * Bảng chuỗi dùng chung.
     *
     * <p>Excel không lưu chữ ngay trong ô mà lưu một chỉ số trỏ vào bảng này. Đọc thiếu nó thì mọi ô
     * chữ ra thành con số — và con số đó trông đủ hợp lệ để không ai nghi ngờ ngay.
     *
     * <p>Một mục {@code <si>} có thể vỡ thành nhiều đoạn {@code <t>} khi ô có nhiều kiểu chữ, nên
     * phải nối lại theo mục chứ không đếm từng {@code <t>}.
     */
    private static List<String> docSharedStrings(byte[] xml) throws XMLStreamException {
        List<String> ket = new ArrayList<>();
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(xml));
        StringBuilder dangGhep = null;
        while (reader.hasNext()) {
            int su = reader.next();
            if (su == XMLStreamConstants.START_ELEMENT) {
                String ten = reader.getLocalName();
                if ("si".equals(ten)) {
                    dangGhep = new StringBuilder();
                } else if ("t".equals(ten) && dangGhep != null) {
                    dangGhep.append(reader.getElementText());
                }
            } else if (su == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
                ket.add(dangGhep == null ? "" : dangGhep.toString());
                dangGhep = null;
            }
        }
        return ket;
    }

    private static List<List<String>> docSheet(byte[] xml, List<String> chuoiDungChung) throws XMLStreamException {
        List<List<String>> grid = new ArrayList<>();
        XMLStreamReader reader = xmlReader(new ByteArrayInputStream(xml));

        Map<Integer, String> dong = new HashMap<>();
        int cotLonNhat = -1;
        int cotHienTai = -1;
        boolean laChuoiNoiTuyen = false;
        boolean laChiSoChuoi = false;

        while (reader.hasNext()) {
            int su = reader.next();
            if (su == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "row" -> {
                        dong = new HashMap<>();
                        cotLonNhat = -1;
                    }
                    case "c" -> {
                        cotHienTai = cotTuThamChieu(reader.getAttributeValue(null, "r"));
                        String kieu = reader.getAttributeValue(null, "t");
                        laChiSoChuoi = "s".equals(kieu);
                        laChuoiNoiTuyen = "inlineStr".equals(kieu);
                        cotLonNhat = Math.max(cotLonNhat, cotHienTai);
                    }
                    case "v" -> {
                        String raw = reader.getElementText();
                        String value = laChiSoChuoi ? traChuoi(chuoiDungChung, raw) : raw;
                        dong.put(cotHienTai, value);
                    }
                    case "t" -> {
                        if (laChuoiNoiTuyen) {
                            dong.put(cotHienTai, reader.getElementText());
                        }
                    }
                    default -> {
                        // phần tử định dạng — không liên quan tới dữ liệu
                    }
                }
            } else if (su == XMLStreamConstants.END_ELEMENT && "row".equals(reader.getLocalName())) {
                List<String> phang = new ArrayList<>();
                for (int i = 0; i <= cotLonNhat; i++) {
                    phang.add(dong.getOrDefault(i, ""));
                }
                grid.add(phang);
            }
        }
        return grid;
    }

    private static String traChuoi(List<String> bang, String chiSo) {
        try {
            int i = Integer.parseInt(chiSo.trim());
            return i >= 0 && i < bang.size() ? bang.get(i) : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** {@code "C7"} → cột 2. Chữ cái là hệ 26, phần số là số dòng nên bỏ qua. */
    private static int cotTuThamChieu(String reference) {
        if (reference == null || reference.isEmpty()) {
            return 0;
        }
        int cot = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = Character.toUpperCase(reference.charAt(i));
            if (c < 'A' || c > 'Z') {
                break;
            }
            cot = cot * 26 + (c - 'A' + 1);
        }
        return cot - 1;
    }

    /**
     * XML từ tệp người dùng tải lên là dữ liệu <b>không tin được</b>.
     *
     * <p>Tắt DTD và thực thể ngoài: một XLSX là ZIP chứa XML, nên nó là đường vào XXE hoàn chỉnh —
     * kẻ gửi tệp có thể đọc trộm tệp trên máy chủ hoặc bắt máy chủ gọi ra ngoài. Đây không phải rủi
     * ro lý thuyết ở đây: màn hình nhập danh mục nhận tệp từ người dùng đã đăng nhập, mà tài khoản
     * đăng nhập được không có nghĩa là được đọc {@code /opt/songnhue/keys/}.
     */
    private static XMLStreamReader xmlReader(InputStream input) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(input);
    }

    // === Chung ===============================================================

    /**
     * Dòng đầu là tiêu đề. Tên cột chuẩn hoá về <b>không dấu, chữ thường, gạch dưới</b> để tệp ghi
     * "Mã công trình", "ma_cong_trinh" hay "MÃ CÔNG TRÌNH" đều vào đúng một chỗ — người lập tệp
     * không nên phải đoán cách viết mà máy chấp nhận.
     */
    private static List<Row> dungRows(List<List<String>> grid) {
        if (grid.isEmpty()) {
            throw new ValidationException(ErrorCode.OPS_2015);
        }
        List<String> tieuDe =
                grid.get(0).stream().map(SpreadsheetReader::chuanHoaCot).toList();

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < grid.size() && rows.size() < MAX_ROWS; i++) {
            List<String> raw = grid.get(i);
            if (raw.stream().allMatch(o -> o == null || o.isBlank())) {
                continue; // dòng trống giữa bảng là chuyện thường trong tệp Excel người dùng gửi
            }
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < tieuDe.size(); c++) {
                cells.put(tieuDe.get(c), c < raw.size() ? raw.get(c) : "");
            }
            rows.add(new Row(i + 1, cells));
        }
        return rows;
    }

    private static String chuanHoaCot(String ten) {
        if (ten == null) {
            return "";
        }
        return VietnameseUtils.removeDiacritics(ten.trim())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
    }
}
