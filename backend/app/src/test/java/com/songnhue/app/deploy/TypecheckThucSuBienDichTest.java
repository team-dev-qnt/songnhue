package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Lệnh {@code typecheck} phải khớp HÌNH DẠNG của {@code tsconfig.json}</b> — nợ
 * <b>T28.42 / T39.12</b>.
 *
 * <h2>⛔⛔ Khuyết tật: {@code tsc --noEmit} trên một tệp SOLUTION biên dịch ĐÚNG 0 TỆP</h2>
 *
 * <p>{@code admin-app/tsconfig.json} là một tệp <b>solution</b>: {@code "files": []} cộng một danh
 * sách {@code "references"}. Chạy {@code tsc --noEmit -p admin-app/tsconfig.json} lên nó thì
 * TypeScript đọc đúng tệp, phân giải đúng cấu hình, <b>thoát 0</b> — và ⛔ không mở một tệp nguồn
 * nào. Chỉ {@code tsc -b} mới đi theo {@code references}.
 *
 * <p>⚠ Đo được 01/09/2026 (WS-39): lệnh ấy báo <b>xanh</b> trên một tệp <b>thiếu hẳn một trường bắt
 * buộc</b>. ⇒ ⛔ Không phải một lỗi cấu hình — nó là một <b>cổng kiểm không chạy</b>, và §10.72 đã
 * chốt: <i>một cổng kiểm KHÔNG CHẠY ⛔ không đọc như một cổng kiểm ĐỎ, và ⛔ không có gì đứng ra báo
 * sự vắng mặt</i>.
 *
 * <h2>✅ Cổng CI hiện LÀNH — nợ còn lại là cái bẫy TAY</h2>
 *
 * <p>{@code npm run typecheck} của {@code admin-app} chạy {@code tsc -b}, nên CI đúng. Thứ nguy
 * hiểm là người kế tiếp (hoặc một trợ lý) gõ {@code npx tsc --noEmit -p ...} để "kiểm nhanh" và
 * nhận một màu xanh ⛔ không có nghĩa gì — đúng chuyện đã xảy ra.
 *
 * <p>⇒ Bài này ⛔ không kiểm CI (CI tự kiểm nó bằng cách chạy). Nó khoá <b>bất biến</b>: hình dạng
 * tsconfig và lệnh typecheck phải khớp nhau, ở <b>cả hai</b> workspace, và lệch nhau thì đỏ.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <p>Soi <b>hai</b> workspace có tên. Một workspace thứ ba ⛔ sẽ không được bài này thấy — nhưng nó
 * cũng ⛔ không tồn tại, và ngày nó ra đời thì {@link #WORKSPACE} là chỗ phải sửa.
 */
class TypecheckThucSuBienDichTest {

    private static final List<String> WORKSPACE = List.of("frontend/admin-app", "frontend/public-web");

    @Test
    @DisplayName("⭐⭐ T28.42 — tsconfig kiểu SOLUTION thì lệnh typecheck phải là `tsc -b`")
    void lenhTypecheckKhopHinhDangTsconfig() {
        assertThat(WORKSPACE).as("⚠ vế chống tập rỗng (luật 7)").hasSizeGreaterThanOrEqualTo(2);

        int soSolution = 0;
        int soCauHinhThat = 0;

        for (String ws : WORKSPACE) {
            String tsconfig = doc(timTuGocKho(ws + "/tsconfig.json"));
            String pkg = doc(timTuGocKho(ws + "/package.json"));
            String lenh = lenhTypecheck(pkg, ws);

            // ⚠ Hình dạng đọc theo CẤU TRÚC: một tệp solution ⛔ không có `compilerOptions` và CÓ
            //   `references`. ⛔ Không đoán theo tên workspace.
            boolean laSolution = tsconfig.contains("\"references\"") && !tsconfig.contains("\"compilerOptions\"");

            if (laSolution) {
                soSolution++;
                assertThat(lenh)
                        .as(
                                """
                                ⛔ `%s/tsconfig.json` là tệp SOLUTION ("files": [] + references), mà lệnh \
                                typecheck là `%s`. `tsc --noEmit` trên một tệp solution biên dịch ĐÚNG 0 TỆP: \
                                đọc đúng tệp, phân giải đúng cấu hình, THOÁT 0, và ⛔ không mở một tệp nguồn \
                                nào. Đo được 01/09: nó báo xanh trên một tệp thiếu hẳn một trường bắt buộc. \
                                Chỉ `tsc -b` mới đi theo `references`.""",
                                ws, lenh)
                        .contains("tsc -b")
                        .doesNotContain("--noEmit");
            } else {
                soCauHinhThat++;
                assertThat(tsconfig)
                        .as("⚠ `%s/tsconfig.json` ⛔ không phải solution ⇒ phải là cấu hình THẬT", ws)
                        .contains("\"compilerOptions\"");
            }
        }

        // ⭐ Vế phân biệt (luật 9): bài này phải thấy được CẢ HAI hình dạng. Nếu bộ nhận diện luôn
        //    trả về cùng một nhánh thì khẳng định phía trên chỉ chứng minh một nửa, và nửa kia là
        //    nửa chưa ai đi qua (luật 7).
        assertThat(soSolution)
                .as("⛔ ⛔ Bộ nhận diện ⛔ không thấy tệp solution nào — nhánh chịu lực chưa từng chạy")
                .isGreaterThanOrEqualTo(1);
        assertThat(soCauHinhThat)
                .as("⛔ Bộ nhận diện ⛔ không thấy cấu hình thật nào — nó đang trả cùng một nhánh cho "
                        + "mọi đầu vào, tức là ⛔ không phân biệt được hai trạng thái")
                .isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------

    /** ⚠ Đọc giá trị của khoá {@code "typecheck"} trong {@code scripts} — ⛔ không grep cả tệp. */
    private static String lenhTypecheck(String packageJson, String ws) {
        var m = java.util.regex.Pattern.compile("\"typecheck\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(packageJson);
        if (!m.find()) {
            return fail("⛔ `%s/package.json` ⛔ không khai script `typecheck` — cổng kiểm kiểu này ".formatted(ws)
                    + "⛔ không tồn tại thì cũng ⛔ không có gì chạy ở CI");
        }
        return m.group(1);
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
