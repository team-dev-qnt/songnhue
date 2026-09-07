package com.songnhue.core.common.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mã hoá lại ảnh để loại bỏ mọi thứ không phải điểm ảnh — util #11 (conventions.md §4.4).
 *
 * <p><b>Hai thứ bị loại, vì hai lý do khác nhau:</b>
 *
 * <ul>
 *   <li><b>EXIF</b> — ảnh chụp bằng điện thoại mang theo <b>toạ độ GPS</b>, thời điểm chụp, kiểu
 *       máy. Với hệ thống này thì ảnh hiện trường công trình đăng lên Cổng thông tin điện tử là
 *       công khai, và toạ độ trong đó công khai theo. Người đăng không hề biết mình vừa công bố
 *       điều gì.
 *   <li><b>Dữ liệu lạ gắn kèm</b> — tệp vừa là ảnh hợp lệ vừa chứa mã ở phần đuôi (polyglot). Kiểm
 *       magic bytes không bắt được vì phần đầu đúng là ảnh thật. Giải mã ra điểm ảnh rồi ghi lại thì
 *       những gì không phải điểm ảnh <i>không có đường nào sống sót</i> — mạnh hơn mọi bộ lọc theo
 *       mẫu.
 * </ul>
 *
 * <p>⚠ Chỉ áp dụng cho ảnh raster (JPEG/PNG). <b>Không</b> dùng cho SVG: SVG là XML, mã hoá lại
 * không có nghĩa gì, và nó cần đường xử lý riêng (sanitize XML hoặc chỉ cho quản trị viên tải lên).
 */
public final class ImageSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ImageSanitizer.class);

    private ImageSanitizer() {}

    public static boolean isSupported(String mimeType) {
        return "image/jpeg".equals(mimeType) || "image/png".equals(mimeType);
    }

    /**
     * @return nội dung ảnh đã mã hoá lại; trả nguyên bản nếu không đọc được bằng ImageIO
     */
    public static byte[] stripMetadata(byte[] content, String mimeType) {
        if (!isSupported(mimeType)) {
            return content;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                // Khai là ảnh mà giải mã không ra — để nguyên rồi để tầng trên quyết định. Ném ở đây
                // thì mỗi định dạng ảnh lạ mà ImageIO chưa hỗ trợ lại thành một lỗi tải lên khó hiểu.
                log.warn("Không giải mã được ảnh {} — giữ nguyên nội dung gốc", mimeType);
                return content;
            }
            String format = "image/png".equals(mimeType) ? "png" : "jpg";
            BufferedImage flattened = flattenForJpeg(image, format);

            ByteArrayOutputStream out = new ByteArrayOutputStream(content.length);
            ImageIO.write(flattened, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("Mã hoá lại ảnh thất bại — giữ nguyên nội dung gốc", e);
            return content;
        }
    }

    /**
     * JPEG không có kênh alpha.
     *
     * <p>Ghi thẳng ảnh có alpha sang JPEG cho ra ảnh <b>ám hồng</b> hoặc lỗi hẳn, tuỳ phiên bản JDK —
     * một lỗi hiển thị rất khó truy vì nó chỉ xảy ra với ảnh có nền trong suốt.
     */
    private static BufferedImage flattenForJpeg(BufferedImage source, String format) {
        if (!"jpg".equals(format) || !source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage opaque = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = opaque.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return opaque;
    }
}
