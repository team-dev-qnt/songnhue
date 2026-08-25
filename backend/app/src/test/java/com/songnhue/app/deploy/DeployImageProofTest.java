package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lượt triển khai phải <b>chứng minh</b> container đang chạy đúng image nó vừa triển khai — WS-11.
 *
 * <h2>Vì sao bài kiểm này tồn tại</h2>
 *
 * Ngày 25/8, lượt triển khai staging làm đủ mọi việc đúng: giải tag SHA ra digest, {@code pull}
 * digest ấy về máy chủ, in {@code ✓ ghcr.io/…/app:71207e8d… → sha256:9c9f18e9…}, rồi tổng kết
 * "Image (theo <b>digest</b>, không theo tag)". Ba dòng xanh.
 *
 * <p>Nhưng {@code docker compose up -d} in {@code Container songnhue-app Running} — không phải
 * {@code Recreated} — và giữ nguyên container cũ. Mã cũ chạy tiếp. Bản vá nằm trong image, image
 * nằm trên đĩa, và <b>không có một dòng nào nối hai thứ đó lại</b>.
 *
 * <p>Cả chuỗi ấy chỉ chứng minh digest đã <i>tới đĩa máy chủ</i>. Câu mà không ai hỏi là câu duy
 * nhất quan trọng: <i>container đang chạy cái gì</i>. Đây là luật 9 ở dạng đắt nhất — một khẳng
 * định không phân biệt được hai trạng thái thì không khẳng định gì (§10.53).
 *
 * <p>⚠ Bài kiểm này canh <b>cấu trúc</b> của workflow, không canh hành vi lúc chạy. Phần hành vi —
 * vòng lặp có thật sự bắt được container lệch image không — được kiểm bằng cách chạy nó với một
 * {@code docker} giả ở hai kịch bản; kết quả ghi ở §10.53.
 */
class DeployImageProofTest {

    private static final String WORKFLOW = ".github/workflows/deploy.yml";

    /** Ba biến image mà workflow export trước khi gọi compose. */
    private static final List<String> BIEN_IMAGE = List.of("APP_IMAGE", "ADMIN_IMAGE", "PUBLIC_IMAGE");

    @Test
    @DisplayName("⭐⭐ Sau `up -d` phải có phép so image ĐANG CHẠY với image vừa triển khai")
    void phaiCoPhepSoSauKhiUp() {
        String than = thanKhongChuThich();

        int viTriUp = than.indexOf("up -d");
        assertThat(viTriUp)
                .as("không thấy lệnh `up -d` nào trong %s — bài kiểm đang soi nhầm tệp", WORKFLOW)
                .isGreaterThan(-1);

        int viTriSo = than.indexOf("docker inspect --format '{{.Image}}'");
        assertThat(viTriSo)
                .as(
                        """
                        %s không đọc lại image của container sau khi triển khai.

                        Thiếu bước này thì "đã triển khai digest X" là một câu KHÔNG kiểm được: bước tra
                        giải đúng digest, `pull` in "Pulled", compose in "Running" — ba dòng xanh, và
                        không dòng nào nói container đang chạy cái gì.

                        Đã trả giá 25/8: bản vá ClassCastException nằm trong image mới, image mới nằm
                        trên đĩa máy chủ, container vẫn là bản cũ, và lượt triển khai báo thành công.""",
                        WORKFLOW)
                .isGreaterThan(-1);

        assertThat(viTriSo)
                .as("phép so phải nằm SAU `up -d` — đo trước khi thay container thì đo bản cũ")
                .isGreaterThan(viTriUp);
    }

    @Test
    @DisplayName("⭐ Phép so dùng ID ẢNH, không so chuỗi tag")
    void phaiSoBangIdAnh() {
        String than = thanKhongChuThich();

        assertThat(than)
                .as(
                        """
                        Phép so phải giải ref ra ID ảnh bằng `docker image inspect`.

                        So bằng chuỗi tag không phân biệt được hai trạng thái: `.Config.Image` giữ nguyên
                        văn ref lúc tạo container, nên hai ref khác nhau vẫn có thể là MỘT ảnh, và một
                        ref như `:dev` có thể đã trỏ sang ảnh khác từ lúc nào. ID ảnh là nội dung — nó
                        chỉ có một nghĩa (luật 2: canh cấu trúc, đừng canh văn bản).""")
                .contains("docker image inspect --format '{{.Id}}'");
    }

    @Test
    @DisplayName("⭐ Cả BA image đều được đo, không chỉ backend")
    void doDuBaImage() {
        String than = thanKhongChuThich();
        int viTriSo = than.indexOf("docker inspect --format '{{.Image}}'");
        // Chỉ soi phần SAU `up -d`: ba biến này còn xuất hiện ở khối `export` và khối quay lui.
        String khoiDo = than.substring(Math.max(0, viTriSo - 800));

        for (String bien : BIEN_IMAGE) {
            assertThat(khoiDo)
                    .as(
                            """
                            Khối kiểm chứng không nhắc tới $%s.

                            Đo mỗi backend là để lại đúng cái lỗ vừa ngã: lượt 25/8 chỉ có backend đổi
                            image, và nếu lần sau public-web là cái không được thay thì không ai biết.""",
                            bien)
                    .contains(bien);
        }
    }

    @Test
    @DisplayName("⛔ `up -d` phải `--force-recreate` — không để compose tự quyết")
    void upPhaiForceRecreate() {
        String than = thanKhongChuThich();

        assertThat(than)
                .as(
                        """
                        `up -d` của bước triển khai phải mang `--force-recreate`.

                        Triển khai là lúc ĐẶT image đã khai vào container đang chạy — một mệnh lệnh, không
                        phải một gợi ý để compose cân nhắc. Ngày 25/8 compose cân nhắc xong và quyết định
                        giữ nguyên container cũ, dù digest đã khác.

                        ⚠ `--force-recreate` KHÔNG thay được phép so ở bài kiểm trên: nếu ref bị cấp sai
                          (ví dụ `.env` lỡ chứa APP_IMAGE và `--env-file` thắng biến export), force cũng
                          chỉ dựng lại đúng cái image sai. Hai thứ trả lời hai câu khác nhau.""")
                .contains("up -d --force-recreate");
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: bốn phép so trên phải THẬT SỰ bắt được khi khối bị gỡ")
    void batDuocKhiKhoiBiGo() {
        String than = thanKhongChuThich();

        // Mô phỏng đúng ba cách khối này có thể biến mất trong một lượt sửa vô ý.
        String goHanPhepSo = than.replace("docker inspect --format '{{.Image}}'", "docker ps");
        String haThanhSoTag = than.replace("docker image inspect --format '{{.Id}}'", "echo");
        String boForce = than.replace("up -d --force-recreate", "up -d");

        assertThat(goHanPhepSo).doesNotContain("docker inspect --format '{{.Image}}'");
        assertThat(haThanhSoTag).doesNotContain("docker image inspect --format '{{.Id}}'");
        assertThat(boForce).doesNotContain("up -d --force-recreate");

        // Và bản THẬT phải khác cả ba — nếu không thì ba phép khẳng định trên đang so rỗng với rỗng.
        assertThat(than).isNotEqualTo(goHanPhepSo).isNotEqualTo(haThanhSoTag).isNotEqualTo(boForce);
    }

    // ---- Trợ giúp ------------------------------------------------------------

    /**
     * Thân workflow đã bỏ mọi dòng chú thích.
     *
     * <p>⛔ Bắt buộc: khối chú thích của bước triển khai <b>trích dẫn nguyên văn</b> các lệnh đang
     * được canh để giải thích vì sao chúng tồn tại. Tìm trên văn bản thô thì mọi phép khẳng định ở
     * đây đều xanh sau khi lệnh thật đã bị xoá — đúng cách `SeedGateTest` từng khớp trúng một
     * `DELETE FROM articles` nằm trong lời giải thích.
     */
    private static String thanKhongChuThich() {
        return doc(timTuGocKho(WORKFLOW))
                .lines()
                .filter(dong -> !dong.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"));
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
