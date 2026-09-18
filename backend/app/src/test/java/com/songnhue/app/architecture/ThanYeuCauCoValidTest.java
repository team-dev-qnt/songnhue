package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi {@code @RequestBody} phải có {@code @Valid} — T61.40</b> (ASVS 5.1.3 · 13.2.2).
 *
 * <h2>⚠⚠ Và {@code @Valid} MỘT MÌNH ⛔ đủ — đó là chỗ lượt đo đầu tiên đọc sai</h2>
 *
 * <p>Đánh giá ASVS ghi *"13 thân yêu cầu thiếu {@code @Valid}"*. Đo lại 16/09: các DTO ấy có
 * <b>0 annotation ràng buộc nào</b>, nên thêm {@code @Valid} một mình <b>⛔ đổi gì cả</b> — thứ
 * thiếu là <b>ràng buộc</b>. Cặp ấy phải đi cùng nhau, và T61.37 đã trả giá đúng chuyện này: một tên
 * 300 ký tự đi vào {@code varchar(255)} ra <b>500</b>, ⛔ phải 422.
 *
 * <p>⚠ Bài này canh vế cấu trúc (mọi thân yêu cầu đều được hợp lệ hoá). Vế *"DTO có ràng buộc"* ⛔
 * tự động hoá được một cách trung thực — một {@code record} toàn {@code UUID} và {@code enum} thì
 * kiểu đã là ràng buộc; nên phần ấy nằm ở lượt rà tay và ở các bài HTTP từng chức năng.
 *
 * <p>⚠ Phạm vi tự khai (luật 28): quét <b>mã nguồn</b> của mọi module dưới {@code backend/}, ⛔ đọc
 * bytecode. Một {@code @RequestBody} viết bằng annotation khác tên (alias) sẽ ⛔ bị thấy.
 */
class ThanYeuCauCoValidTest {

    @Test
    @DisplayName("⛔⛔ 0 thân yêu cầu nào được nhận mà ⛔ qua hợp lệ hoá")
    void moiThanYeuCauDeuCoValid() throws IOException {
        List<String> viPham = new ArrayList<>();
        int tong = 0;

        for (Path tep : tepJavaCuaApi()) {
            String ma = Files.readString(tep);
            // ⚠ Một tham số có thể xuống dòng giữa `@Valid` và `@RequestBody` (Spotless ngắt dòng ở
            //   ~120 ký tự) ⇒ ⛔ quét theo DÒNG như bản nháp đầu, mà chuẩn hoá khoảng trắng trước.
            String gon = ma.replaceAll("\\s+", " ");
            int i = 0;
            while ((i = gon.indexOf("@RequestBody", i)) >= 0) {
                tong++;
                String truoc = gon.substring(Math.max(0, i - 40), i);
                if (!truoc.contains("@Valid")) {
                    viPham.add(tep.getFileName() + " — " + gon.substring(i, Math.min(gon.length(), i + 60)));
                }
                i += "@RequestBody".length();
            }
        }

        assertThat(tong)
                .as("chống tập rỗng: phải tìm thấy hàng chục @RequestBody — 0 nghĩa là phép quét hỏng")
                .isGreaterThan(30);
        assertThat(viPham)
                .as("thiếu @Valid ⇒ mọi ràng buộc trên DTO là trang trí")
                .isEmpty();
    }

    private static List<Path> tepJavaCuaApi() throws IOException {
        Path goc = timTuGocKho("backend");
        try (Stream<Path> tep = Files.walk(goc)) {
            return tep.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList();
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new AssertionError("⛔ tìm thấy " + duongDanTuongDoi);
    }
}
