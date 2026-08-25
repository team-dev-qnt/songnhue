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
 * <b>Bộ seed nội dung không được chạy tự động, và không được chạm tới production.</b>
 *
 * <h2>Vì sao ràng buộc này cần một phép kiểm chứ không phải một lời dặn</h2>
 *
 * 5 bài trong {@code deploy/seed/} là bản <b>sao chép nguyên văn từ báo ngoài</b>, kèm ảnh của họ.
 * Chúng có mặt vì staging cần nội dung dài thật, ảnh thật để đo bố cục và thời gian tải trang chủ
 * (DOD1.17). Trên cổng thông tin của một doanh nghiệp nhà nước thì đó là vấn đề pháp lý.
 *
 * <p>Ràng buộc "phải gõ tay" hiện chỉ sống trong chú thích ở đầu {@code seed.sh} và
 * {@code README.md}. Thêm một dòng {@code ./seed/seed.sh} vào bước triển khai là xoá nó — và không
 * lệnh nào báo sai, vì script chạy đúng, chỉ là ở nơi không được phép chạy.
 *
 * <p>Đúng dạng CLAUDE.md luật 12: <i>khi một bảo đảm phải đúng ở nhiều đường vào, đặt nó ở chỗ dữ
 * liệu đi qua</i> — ở đây không đặt được nên phải có phép kiểm đếm đủ các đường.
 *
 * <h2>Bối cảnh: dữ liệu staging "chưa cập nhật" (25/8)</h2>
 *
 * Lượt CD Staging đầu tiên xanh trọn vẹn — rsync, migrator, {@code up -d}, smoke test đều đạt — mà
 * cổng vẫn không có 5 bài. Vì <b>không bước nào trong CD nạp dữ liệu cả</b>, và điều đó là đúng
 * thiết kế. Cái thiếu là một đường chạy tay, nay là {@code seed-staging.yml} (§10.45).
 */
class SeedNeverAutomaticTest {

    private static final String GOI_SEED = "seed.sh";

    /** Workflow chạy tự động — nơi bộ seed TUYỆT ĐỐI không được xuất hiện. */
    private static final List<String> TU_DONG = List.of(
            ".github/workflows/ci.yml", ".github/workflows/deploy-staging.yml", ".github/workflows/deploy-prod.yml");

    private static final String WORKFLOW_SEED = ".github/workflows/seed-staging.yml";

    @Test
    @DisplayName("⭐⭐ Không workflow tự động nào được gọi seed.sh")
    void khongWorkflowTuDongNaoGoiSeed() {
        for (String duongDan : TU_DONG) {
            assertThat(boChuThich(doc(duongDan)))
                    .as(
                            """
                            `%s` gọi `%s`.

                            5 bài trong bộ seed là bản SAO CHÉP NGUYÊN VĂN từ báo ngoài. Chúng chỉ được \
                            nạp bằng một lượt bấm có chủ ý (`seed-staging.yml`), không bao giờ vì có \
                            người merge một PR. Trên production thì đây là vấn đề pháp lý, không phải \
                            lựa chọn kỹ thuật — xem `deploy/seed/README.md`.""",
                            duongDan, GOI_SEED)
                    .doesNotContain(GOI_SEED);
        }
    }

    @Test
    @DisplayName("⭐⭐ Workflow seed chỉ chạy bằng workflow_dispatch")
    void seedChiChayBangTayDispatch() {
        String noiDung = boChuThich(doc(WORKFLOW_SEED));

        assertThat(noiDung)
                .as("`%s` phải khai `workflow_dispatch`", WORKFLOW_SEED)
                .contains("workflow_dispatch");

        for (String kichHoatTuDong : List.of("\n  push:", "\n  pull_request:", "\n  schedule:")) {
            assertThat(noiDung)
                    .as(
                            """
                            `%s` khai một kích hoạt TỰ ĐỘNG (`%s`).

                            Bộ seed chỉ được nạp khi có người bấm và gõ đúng ô xác nhận. Mọi kích hoạt \
                            tự động đều biến nó thành thứ chạy sau lưng người vận hành.""",
                            WORKFLOW_SEED, kichHoatTuDong.strip())
                    .doesNotContain(kichHoatTuDong);
        }
    }

    @Test
    @DisplayName("⭐⭐ Workflow seed không được biết tới bất kỳ secret production nào")
    void seedKhongChamToiProduction() {
        String noiDung = boChuThich(doc(WORKFLOW_SEED));

        for (String tienTo : List.of("PROD_", "PRODUCTION_")) {
            assertThat(noiDung)
                    .as(
                            """
                            `%s` nhắc tới secret `%s…`.

                            Workflow này cố ý CHỈ biết bộ `STAGING_*`, để không có đường nào — kể cả gõ \
                            nhầm — trỏ nó sang production. Thêm một tham số môi trường vào đây là dựng \
                            lại đúng cú nhấp sai mà thiết kế đang tránh.""",
                            WORKFLOW_SEED, tienTo)
                    .doesNotContain(tienTo);
        }
    }

    @Test
    @DisplayName("⭐ Workflow seed phải có ô xác nhận gõ tay")
    void seedPhaiCoOXacNhan() {
        String noiDung = boChuThich(doc(WORKFLOW_SEED));

        assertThat(noiDung)
                .as(
                        """
                        `%s` không còn ô xác nhận gõ tay.

                        Việc ghi đè dữ liệu của một môi trường phải tốn của người bấm nhiều hơn một cú \
                        nhấp — cùng tinh thần với Restore UI (§7.3).""",
                        WORKFLOW_SEED)
                .contains("nap-noi-dung-staging");
    }

    @Test
    @DisplayName("Bộ seed phải còn tồn tại — chặn xanh-trên-tập-rỗng")
    void boSeedConTonTai() {
        // conventions.md §1.5: bốn bài trên đều là khẳng định PHỦ ĐỊNH. Xoá `deploy/seed/` đi thì
        // cả bốn xanh trọn vẹn mà không canh gì. Dòng này neo chúng vào một thứ có thật.
        assertThat(Files.exists(timTuGocKho("deploy/seed/seed.sh"))).isTrue();
        assertThat(doc("deploy/seed/seed.sh")).contains("ĐÂY KHÔNG PHẢI MIGRATION");
    }

    private static String boChuThich(String noiDung) {
        return noiDung.lines()
                .filter(dong -> !dong.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"));
    }

    private static String doc(String duongDanTuongDoi) {
        try {
            return Files.readString(timTuGocKho(duongDanTuongDoi), StandardCharsets.UTF_8);
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
