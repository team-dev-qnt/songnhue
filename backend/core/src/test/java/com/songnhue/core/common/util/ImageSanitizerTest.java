package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.createGraphics().setColor(Color.RED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("⚠ Dữ liệu lạ gắn sau ảnh không sống sót qua lần mã hoá lại")
    void stripsAppendedPayload() throws IOException {
        // Tệp vừa là ảnh hợp lệ vừa mang mã ở phần đuôi (polyglot). Kiểm magic bytes KHÔNG bắt được
        // vì phần đầu đúng là ảnh thật — chỉ giải mã ra điểm ảnh rồi ghi lại mới loại được.
        byte[] original = jpeg(8, 8);
        byte[] payload = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);
        byte[] polyglot = new byte[original.length + payload.length];
        System.arraycopy(original, 0, polyglot, 0, original.length);
        System.arraycopy(payload, 0, polyglot, original.length, payload.length);

        byte[] cleaned = ImageSanitizer.stripMetadata(polyglot, "image/jpeg");

        assertThat(new String(cleaned, StandardCharsets.ISO_8859_1)).doesNotContain("php system");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(cleaned)))
                .as("vẫn phải là ảnh đọc được sau khi làm sạch")
                .isNotNull();
    }

    @Test
    @DisplayName("Ảnh PNG có kênh trong suốt vẫn xử lý được")
    void handlesPngWithAlpha() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        byte[] cleaned = ImageSanitizer.stripMetadata(out.toByteArray(), "image/png");

        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(cleaned))).isNotNull();
    }

    @Test
    @DisplayName("Định dạng không phải ảnh raster giữ nguyên — PDF không mã hoá lại được")
    void leavesNonImagesAlone() {
        byte[] pdf = "%PDF-1.7 nội dung".getBytes(StandardCharsets.UTF_8);
        assertThat(ImageSanitizer.stripMetadata(pdf, "application/pdf")).isSameAs(pdf);
    }

    @Test
    @DisplayName("Khai là ảnh mà giải mã không ra thì giữ nguyên, không ném lỗi")
    void keepsUndecodableContent() {
        byte[] rac = "khong-phai-anh".getBytes(StandardCharsets.UTF_8);
        assertThat(ImageSanitizer.stripMetadata(rac, "image/jpeg")).isSameAs(rac);
    }

    @Test
    @DisplayName("SVG KHÔNG thuộc phạm vi xử lý — nó là XML, cần đường riêng")
    void doesNotClaimToHandleSvg() {
        assertThat(ImageSanitizer.isSupported("image/svg+xml")).isFalse();
    }
}
