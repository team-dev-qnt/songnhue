package com.songnhue.hydro.infra;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Vân tay <b>mã đang thật sự chạy trong tiến trình này</b> — T31.11.
 *
 * <h2>⭐ Vì sao một con số, không phải một chuỗi phiên bản</h2>
 *
 * <p>Dự án đã trả giá <b>ba lần</b> cho cùng một câu hỏi, và cả ba lần câu trả lời đều là "cái đang
 * chạy không phải cái vừa build":
 *
 * <ul>
 *   <li>§10.53 — {@code compose} in {@code Running} và giữ nguyên container cũ; bản vá không bao giờ
 *       được nạp;
 *   <li>§10.56 — cluster giữ collation cũ, vá tệp compose không đổi được gì;
 *   <li>§10.67 — bản vá sống trên đĩa mà tiến trình MCP vẫn chạy mã cũ; đọc-ngược-sau-khi-ghi
 *       <b>không</b> bắt được.
 * </ul>
 *
 * <p>Poller là một daemon chạy nhiều ngày, nên nó là chỗ dễ mắc nhất: nó không có ai bấm F5. Một
 * chuỗi phiên bản ({@code 1.0.0-SNAPSHOT}) trả lời sai câu hỏi — nó nói <i>build này tên gì</i>, còn
 * câu cần hỏi là <i>byte của lớp này có đúng byte tôi vừa dịch không</i>. Băm thẳng nội dung tệp
 * {@code .class} trả lời đúng câu ấy, và ⛔ không cần thêm một dòng cấu hình build nào — thứ mà nếu
 * quên thì vân tay lại thành một chuỗi rỗng nói dối.
 *
 * <p>Cách đối chiếu: chạy lại cùng phép băm trên tệp {@code .class} trong {@code target/classes}
 * (hoặc trong image) và so với dòng log lúc khởi động. Khác nhau ⇒ tiến trình đang chạy mã khác.
 */
public final class VanTayLop {

    /** Đủ để phân biệt hai bản build; dài hơn chỉ làm dòng log khó đọc. */
    static final int SO_KY_TU = 16;

    /** Trả về khi không đọc được byte của lớp — ⛔ nói ra, không im lặng trả chuỗi rỗng. */
    static final String KHONG_DOC_DUOC = "khong-doc-duoc";

    private VanTayLop() {}

    /**
     * Băm nội dung tệp {@code .class} của một lớp.
     *
     * @return 16 ký tự hex đầu của SHA-256, hoặc {@link #KHONG_DOC_DUOC}. ⛔ Không ném: một lượt khởi
     *     động ⛔ không được hỏng vì không in nổi một dòng chẩn đoán
     */
    public static String cua(Class<?> lop) {
        ClassLoader nap = lop.getClassLoader();
        if (nap == null) {
            // ⚠ Lớp nguyên thuỷ và lớp của JDK không có classloader — `getClassLoader()` trả null.
            //   Không phải trường hợp ta dùng, nhưng một NPE ở @PostConstruct là một ứng dụng KHÔNG
            //   KHỞI ĐỘNG ĐƯỢC vì một dòng log chẩn đoán. Bắt được nhờ bài kiểm với `int.class`.
            return KHONG_DOC_DUOC;
        }
        String duongDan = lop.getName().replace('.', '/') + ".class";
        try (InputStream in = nap.getResourceAsStream(duongDan)) {
            if (in == null) {
                return KHONG_DOC_DUOC;
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(in.readAllBytes())).substring(0, SO_KY_TU);
        } catch (IOException | NoSuchAlgorithmException e) {
            return KHONG_DOC_DUOC;
        }
    }

    /** Một dòng {@code Tên=vân tay} cho nhiều lớp — dạng dán thẳng vào log lúc khởi động. */
    public static String cua(List<Class<?>> cacLop) {
        return cacLop.stream()
                .map(l -> l.getSimpleName() + "=" + cua(l))
                .reduce((a, b) -> a + " · " + b)
                .orElse("");
    }
}
