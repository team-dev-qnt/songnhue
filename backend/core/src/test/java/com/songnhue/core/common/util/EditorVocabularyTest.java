package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Thứ trình soạn thảo tạo ra phải sống sót qua bộ lọc.</b>
 *
 * <h2>Vì sao bài kiểm này phải bắc cầu sang mã nguồn FE</h2>
 *
 * {@link HtmlSanitizer} chạy lúc <b>ghi</b>: thẻ nằm ngoài danh sách cho phép bị gỡ, im lặng, và
 * bài vẫn lưu thành công. Nên một nút trên thanh công cụ tạo ra thẻ ngoài danh sách sẽ cho ra đúng
 * kịch bản này: biên tập viên chèn bảng, bấm Lưu, hệ thống báo <i>"Đã lưu"</i>, mở lại thì bảng biến
 * mất. Không lỗi, không cảnh báo, và người dùng sẽ nghĩ mình thao tác sai.
 *
 * <p>Hai danh sách này nằm ở hai ngôn ngữ và hai kho thư mục, nên không có cách nào để trình biên
 * dịch bắt lệch. Chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ — cùng lý do
 * đã dựng {@code error-map.test.ts} (FE đọc danh mục mã lỗi của BE) và
 * {@code NotificationEnumParityTest}.
 *
 * <p>Chiều đọc ở đây <b>ngược lại</b>: Java đọc mã nguồn TypeScript. Bắt buộc phải vậy, vì logic khử
 * trùng là Java — không chạy được trong môi trường kiểm thử của FE.
 */
class EditorVocabularyTest {

    /**
     * ⚠ Tệp này đã chuyển từ {@code admin-app} sang {@code design-tokens} — nó là hợp đồng của <b>ba</b>
     * bên (soạn thảo · khử trùng · hiển thị), không phải tài sản riêng của ứng dụng quản trị. Lý do đầy
     * đủ nằm ngay trong tệp đó.
     */
    private static final String RELATIVE_PATH = "frontend/design-tokens/src/editor-schema.ts";

    @Test
    @DisplayName("⭐⭐ Mọi thẻ trình soạn thảo sinh ra đều sống sót qua HtmlSanitizer")
    void moiTheCuaTrinhSoanThaoDeuSongSot() {
        String mauHtml = docHangChuoi("EDITOR_SAMPLE_HTML");
        List<String> theMongDoi = docDanhSachChuoi("EDITOR_TAGS");

        assertThat(theMongDoi)
                .as("đọc hụt danh sách thẻ thì bài kiểm này xanh mà chẳng kiểm gì — chặn ngay tại đây")
                .isNotEmpty();

        // ⚠⚠ Hai phép khẳng định, không phải một — và lần chạy đầu tiên đã chứng minh vì sao.
        // "Thẻ không có trong kết quả" có ĐÚNG HAI nguyên nhân, mà chỉ một là lỗi thật:
        //   (a) bộ lọc gỡ nó đi          → lỗi production, người dùng mất nội dung;
        //   (b) mẫu HTML chưa từng có nó → lỗi của chính bài kiểm, không ai mất gì cả.
        // Gộp làm một thông báo thì lượt đỏ đầu tiên chỉ đường sai: tôi suýt đi thêm bốn thẻ vào
        // safelist trong khi bốn thẻ đó chưa bao giờ bị gỡ.
        Document truocKhiLoc = Jsoup.parseBodyFragment(mauHtml);
        List<String> thieuTrongMau = theMongDoi.stream()
                .filter(the -> truocKhiLoc.select(the).isEmpty())
                .toList();
        assertThat(thieuTrongMau)
                .as(
                        """
                        Những thẻ này khai trong EDITOR_TAGS nhưng KHÔNG có trong EDITOR_SAMPLE_HTML, nên bài \
                        kiểm không chứng minh được gì về chúng. Bổ sung vào mẫu ở %s"""
                                .formatted(RELATIVE_PATH))
                .isEmpty();

        Document sauKhiLoc = Jsoup.parseBodyFragment(HtmlSanitizer.clean(mauHtml));
        List<String> biGo = new ArrayList<>();
        for (String the : theMongDoi) {
            if (sauKhiLoc.select(the).isEmpty()) {
                biGo.add(the);
            }
        }

        assertThat(biGo)
                .as(
                        """
                        Những thẻ này có nút trên thanh công cụ của admin-app nhưng bị HtmlSanitizer gỡ. \
                        Người dùng sẽ soạn được, lưu thành công, rồi mở lại thấy nội dung biến mất mà không \
                        có thông báo nào. Sửa MỘT trong hai phía: thêm thẻ vào Safelist NOI_DUNG, hoặc bỏ \
                        nút tương ứng khỏi %s"""
                                .formatted(RELATIVE_PATH))
                .isEmpty();
    }

    @Test
    @DisplayName("Class trình bày còn nguyên — nếu không thì căn giữa một ảnh là mất tác dụng lúc lưu")
    void classTrinhBaySongSot() {
        List<String> classTrinhBay = new ArrayList<>(docDanhSachChuoi("ALIGN_CLASSES"));
        classTrinhBay.addAll(docDanhSachChuoi("IMAGE_WIDTH_CLASSES"));
        assertThat(classTrinhBay).hasSizeGreaterThanOrEqualTo(6);

        // Đặt trên `figure` chứ không trên `p`: đây là thẻ thật sự mang cả hai nhóm class, và là thẻ
        // duy nhất mà cả căn lề lẫn bề ngang cùng có nghĩa.
        String html = classTrinhBay.stream()
                .map(c -> "<figure class=\"%s\"><img src=\"/api/v1/public/files/x\" alt=\"\"></figure>".formatted(c))
                .reduce("", String::concat);

        String sach = HtmlSanitizer.clean(html);
        for (String c : classTrinhBay) {
            assertThat(sach)
                    .as("class trình bày `%s` phải đi qua được — `style` bị cấm nên đây là đường duy nhất", c)
                    .contains(c);
        }
    }

    @Test
    @DisplayName("⛔ Và thuộc tính `style` vẫn bị cấm — đó là lý do căn lề phải đi bằng class")
    void styleVanBiCam() {
        String sach = HtmlSanitizer.clean("<p style=\"position:fixed;top:0;left:0;width:100vw\">Phủ kín trang</p>");

        assertThat(sach).doesNotContain("position:fixed").doesNotContain("style=");
        assertThat(sach).as("chữ thì vẫn giữ, chỉ bỏ cách trình bày").contains("Phủ kín trang");
    }

    // -------------------------------------------------------------------------

    /** Nội dung tệp khai báo của FE. Đọc một lần, dùng cho cả ba bài. */
    private static String nguonTs() {
        Path path = timTuGocKho();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + path, e);
        }
    }

    /**
     * Đi ngược lên từ thư mục đang chạy để tìm gốc kho mã.
     *
     * <p>Bài kiểm chạy khi thì từ {@code backend/}, khi thì từ {@code backend/core/} tuỳ lệnh Maven,
     * nên đường dẫn tương đối cố định là thứ hỏng tuỳ chỗ gọi. Cùng cách mà {@code error-map.test.ts}
     * đang dùng ở phía FE.
     */
    private static Path timTuGocKho() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            Path ungVien = current.resolve(RELATIVE_PATH);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            current = current.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(RELATIVE_PATH, System.getProperty("user.dir")));
    }

    /** Đọc một hằng dạng mảng chuỗi: {@code export const TEN = ['a', 'b'] as const;} */
    private static List<String> docDanhSachChuoi(String tenHang) {
        Matcher khoi = Pattern.compile("export const " + tenHang + "\\s*=\\s*\\[(.*?)]\\s*as const", Pattern.DOTALL)
                .matcher(nguonTs());
        if (!khoi.find()) {
            return fail("Không tìm thấy hằng `%s` trong %s".formatted(tenHang, RELATIVE_PATH));
        }
        List<String> ketQua = new ArrayList<>();
        Matcher tung = Pattern.compile("'([^']+)'").matcher(khoi.group(1));
        while (tung.find()) {
            ketQua.add(tung.group(1));
        }
        return ketQua;
    }

    /**
     * Đọc một hằng dạng mảng-nối-chuỗi: {@code export const TEN = ['…', '…'].join('');}
     *
     * <p>Ghép các mảnh lại đúng như {@code .join('')} của TypeScript làm — mẫu HTML được cắt thành
     * nhiều dòng để người đọc thấy từng thẻ, chứ nó là <b>một tài liệu duy nhất</b>. Kiểm từng mảnh
     * rời sẽ bỏ sót trường hợp một thẻ bị gỡ vì thẻ cha của nó bị gỡ.
     */
    private static String docHangChuoi(String tenHang) {
        Matcher khoi = Pattern.compile("export const " + tenHang + "\\s*=\\s*\\[(.*?)]\\s*\\.join", Pattern.DOTALL)
                .matcher(nguonTs());
        if (!khoi.find()) {
            return fail("Không tìm thấy hằng `%s` trong %s".formatted(tenHang, RELATIVE_PATH));
        }
        StringBuilder ghep = new StringBuilder();
        Matcher tung = Pattern.compile("'([^']*)'").matcher(khoi.group(1));
        while (tung.find()) {
            ghep.append(tung.group(1));
        }
        return ghep.toString();
    }
}
