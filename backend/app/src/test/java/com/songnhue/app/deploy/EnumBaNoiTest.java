package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Một danh sách giá trị hợp lệ sống ở <b>ba nơi</b> — enum Java, union TypeScript, ràng buộc
 * {@code CHECK} của CSDL — và không cơ chế nào bắt chúng lệch nhau.
 *
 * <h2>Lỗi đã có thật, đo được ngày 01/09/2026</h2>
 *
 * {@code StepBasicInfo.tsx} chào bốn lựa chọn "Mục đích sử dụng": {@code TUOI}, {@code TIEU},
 * <b>{@code TUOI_TIEU_KET_HOP}</b>, <b>{@code KHAC}</b>. Enum {@link ConstructionPurpose} có đúng
 * ba giá trị và {@code ck_constructions_purpose} cũng ba. Hậu quả:
 *
 * <ul>
 *   <li>chọn một trong hai giá trị ma ⇒ Jackson không giải được ⇒ <b>400, hỏng CẢ lượt lưu</b>,
 *       không riêng ô đó;
 *   <li>giá trị hợp lệ {@code HON_HOP} <b>không ô nào tạo ra được</b> — nó chỉ vào hệ thống qua
 *       bộ nhập Excel.
 * </ul>
 *
 * <p>{@code tsc} xanh trọn vẹn suốt thời gian ấy, vì {@code api-types.ts} khai đúng bộ bốn giá trị
 * sai. <b>Khai kiểu là một lời khẳng định, không phải một phép đo</b> — cùng bài học T27.22.
 *
 * <h2>Vì sao bài này nằm ở bộ BE</h2>
 *
 * Nguồn sự thật là enum Java. Bộ lọc CI cho job {@code backend} chạy khi {@code backend/} đổi, nên
 * một thay đổi ở enum — chính là thứ làm ba bên lệch — luôn đi qua bài này. Đặt ở bộ FE thì ngược
 * lại: sửa enum Java, job {@code frontend} bị bỏ qua, và {@code skipped} được GitHub tính là
 * <b>ĐẠT</b>. Cùng lý lẽ với {@code AllowedActionParityTest}.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * Bài này soi <b>đúng năm enum của hồ sơ công trình</b> đã liệt kê ở {@link #BO_BA}. Nó
 * <b>không</b> phủ:
 *
 * <ul>
 *   <li>{@code sluice_specs.sluice_type} và {@code gate_operation} — CSDL có {@code CHECK} liệt kê
 *       giá trị, nhưng giao diện là ô {@code <Input>} <b>chữ tự do</b>, không có union TS nào để
 *       đối chiếu. Gõ "Hộp" hay "van phẳng" vẫn cho ra <b>500</b>. Nợ để mở, không im lặng bỏ qua.
 *   <li>enum của các module khác ({@code cms}, {@code hyd}, {@code adm}).
 * </ul>
 */
class EnumBaNoiTest {

    /** Một dòng = một danh sách giá trị phải khớp ở cả ba nơi. */
    private record BoBa(Class<? extends Enum<?>> enumJava, String tenKieuTs, String tenRangBuoc) {}

    private static final List<BoBa> BO_BA = List.of(
            new BoBa(ConstructionType.class, "ConstructionType", "ck_constructions_type"),
            new BoBa(ConstructionPurpose.class, "ConstructionPurpose", "ck_constructions_purpose"),
            new BoBa(ManagementLevel.class, "ManagementLevel", "ck_constructions_management_level"),
            new BoBa(LifecycleState.class, "LifecycleState", "ck_constructions_lifecycle"),
            new BoBa(OperationalStatus.class, "OperationalStatus", "ck_constructions_operational_status"));

    private static final Path API_TYPES = gocKho().resolve("frontend/admin-app/src/shared/api-types.ts");

    private static final Path MIGRATION = gocKho().resolve(
                    "backend/operations/src/main/resources/db/migration/ops/V202608211026__ops_constructions.sql");

    @Test
    @DisplayName("⭐⭐ Enum Java ↔ union TypeScript ↔ CHECK của CSDL — ba nơi cùng một bộ giá trị")
    void baNoiCungMotBoGiaTri() throws IOException {
        String ts = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        for (BoBa bo : BO_BA) {
            Set<String> java = giaTriJava(bo.enumJava());
            Set<String> typescript = giaTriTypeScript(ts, bo.tenKieuTs());
            Set<String> csdl = giaTriCsdl(sql, bo.tenRangBuoc());

            assertThat(typescript)
                    .as(
                            """
                            `%s`: union TypeScript lệch enum Java.
                              Java (nguồn sự thật): %s
                              TypeScript          : %s
                            Thừa ở TS = giao diện chào một giá trị backend KHÔNG GIẢI ĐƯỢC ⇒ 400 hỏng cả lượt lưu.
                            Thiếu ở TS = một giá trị hợp lệ KHÔNG Ô NÀO tạo ra được.""",
                            bo.tenKieuTs(), java, typescript)
                    .isEqualTo(java);

            assertThat(csdl)
                    .as(
                            """
                            `%s`: ràng buộc `%s` của CSDL lệch enum Java.
                              Java: %s
                              CSDL: %s
                            Lệch ở đây không bị `tsc` hay trình biên dịch thấy — nó nổ thành 500 lúc ghi.""",
                            bo.tenKieuTs(), bo.tenRangBuoc(), java, csdl)
                    .isEqualTo(java);
        }
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: bộ đọc thật sự bóc được giá trị ở CẢ BA nguồn")
    void boDocKhongChayQuaTapRong() throws IOException {
        // Luật 7: một khẳng định chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ai đó đổi cách khai union
        // hay đổi tên ràng buộc, hai bộ đọc văn bản trả về rỗng — bài trên sẽ đỏ vì lệch với Java,
        // nhưng bài này nói thẳng nguyên nhân thay vì bắt người đọc tự suy.
        String ts = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(BO_BA)
                .as("bảng đối chiếu rỗng thì bài trên không khẳng định gì")
                .hasSize(5);

        for (BoBa bo : BO_BA) {
            assertThat(giaTriJava(bo.enumJava()))
                    .as("enum %s không có hằng nào", bo.tenKieuTs())
                    .isNotEmpty();
            assertThat(giaTriTypeScript(ts, bo.tenKieuTs()))
                    .as("không bóc được giá trị nào của `%s` từ %s — union đổi cách khai?", bo.tenKieuTs(), API_TYPES)
                    .isNotEmpty();
            assertThat(giaTriCsdl(sql, bo.tenRangBuoc()))
                    .as("không bóc được giá trị nào của `%s` từ %s — ràng buộc đổi tên?", bo.tenRangBuoc(), MIGRATION)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("⛔ `HON_HOP` phải có ở CẢ BA nơi — đây là giá trị đã gây ra lỗi chặn")
    void honHopCoODuBaNoi() throws IOException {
        String ts = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(giaTriJava(ConstructionPurpose.class)).contains("HON_HOP");
        assertThat(giaTriTypeScript(ts, "ConstructionPurpose"))
                .as("gỡ khỏi TS là ô chọn không tạo ra được nhiệm vụ 'Tưới tiêu kết hợp' nữa")
                .contains("HON_HOP");
        assertThat(giaTriCsdl(sql, "ck_constructions_purpose")).contains("HON_HOP");

        assertThat(giaTriTypeScript(ts, "ConstructionPurpose"))
                .as("hai giá trị ma của bản cũ — chào chúng ra là 400 hỏng cả lượt lưu")
                .doesNotContain("TUOI_TIEU_KET_HOP", "KHAC");
    }

    // -------------------------------------------------------------------------

    private static Set<String> giaTriJava(Class<? extends Enum<?>> loai) {
        return Arrays.stream(loai.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Bóc {@code export type <Ten> = 'A' | 'B' | 'C';} — chấp nhận xuống dòng tuỳ ý giữa các giá
     * trị, nên đổi cách trình bày không làm bài đỏ, còn thêm/bớt một giá trị thì có.
     */
    private static Set<String> giaTriTypeScript(String noiDung, String tenKieu) {
        Matcher khai = Pattern.compile("export\\s+type\\s+" + Pattern.quote(tenKieu) + "\\s*=([^;]*);", Pattern.DOTALL)
                .matcher(noiDung);
        if (!khai.find()) {
            return Set.of();
        }
        return bocChuoiNhay(khai.group(1), '\'');
    }

    /**
     * Bóc danh sách trong {@code CONSTRAINT <ten> CHECK ( … IN ('A', 'B') )}.
     *
     * <p>⚠ Phải chịu được dạng {@code purpose IS NULL OR purpose IN (…)} — nên nó tìm cụm
     * {@code IN (…)} bên trong khối ràng buộc chứ không giả định ràng buộc chỉ có một mệnh đề.
     */
    private static Set<String> giaTriCsdl(String sql, String tenRangBuoc) {
        Matcher khoi = Pattern.compile(
                        "CONSTRAINT\\s+" + Pattern.quote(tenRangBuoc) + "\\s+CHECK\\s*\\((.*?)\\n\\s*\\)",
                        Pattern.DOTALL)
                .matcher(sql);
        if (!khoi.find()) {
            return Set.of();
        }
        Matcher danhSach =
                Pattern.compile("IN\\s*\\(([^)]*)\\)", Pattern.DOTALL).matcher(khoi.group(1));
        if (!danhSach.find()) {
            return Set.of();
        }
        return bocChuoiNhay(danhSach.group(1), '\'');
    }

    private static Set<String> bocChuoiNhay(String doan, char nhay) {
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile(nhay + "([A-Z_]+)" + nhay).matcher(doan);
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /** Đi ngược lên tới thư mục chứa {@code .claude} — chạy được cả từ module lẫn từ gốc repo. */
    private static Path gocKho() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Không tìm thấy gốc repo (thư mục chứa .claude)");
        }
        return p;
    }
}
