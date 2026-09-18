package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.importer.KetQuaNhap;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.operations.application.ConstructionFilter;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.importer.ConstructionImportService;
import com.songnhue.operations.domain.Construction;

/**
 * Nhập danh mục công trình từ tệp — T17.9, và là đường seed dữ liệu thật khi <b>G8</b> về.
 *
 * <h2>Điều bài kiểm này thật sự canh</h2>
 *
 * Không phải "đọc được CSV". Mà là <b>lượt chạy khô nói đúng những gì lượt chạy thật sẽ làm</b>, và
 * <b>một dòng lỗi thì không dòng nào được ghi</b>. Hai điều đó mới là lý do có bước chạy khô: tệp
 * danh mục do Công ty lập có hàng trăm dòng, và nhập một nửa rồi dừng là trạng thái không gỡ được —
 * người dùng không biết đã vào tới đâu, sửa tệp rồi nhập lại thì phần đầu vào hai lần.
 */
class ConstructionImportTest extends IntegrationTestBase {

    private static final String TIEU_DE = "ma_cong_trinh,ten_cong_trinh,loai_cong_trinh,ma_don_vi,"
            + "vi_do,kinh_do,tuyen_song,ly_trinh,nam_xay_dung,tong_von_vnd";

    @Autowired
    private ConstructionImportService importer;

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    @Test
    @DisplayName("⭐ Chạy khô KHÔNG ghi một dòng nào, kể cả khi tệp hoàn toàn hợp lệ")
    void previewNeverWrites() {
        byte[] tep = csv(TIEU_DE, "T17I-001,Trạm bơm Một,Trạm bơm,CTY,20.98,105.78,Nhuệ,K0+390,1998,1500000000");

        KetQuaNhap baoCao = importer.preview(tep);

        assertThat(baoCao.applied()).isFalse();
        assertThat(baoCao.toCreate()).isEqualTo(1);
        assertThat(baoCao.errors()).isEmpty();
        assertThat(demCongTrinh())
                .as("chạy khô mà ghi dữ liệu thì nó không còn là chạy khô")
                .isZero();
    }

    @Test
    @DisplayName("Nhập thật ghi đúng số dòng chạy khô đã hứa")
    void applyMatchesThePreview() {
        byte[] tep = csv(
                TIEU_DE,
                "T17I-001,Trạm bơm Một,Trạm bơm,CTY,20.98,105.78,Nhuệ,K0+390,1998,1500000000",
                "T17I-002,Cống Hai,Cống,CTY,,,Đáy,K2+100,2005,");

        KetQuaNhap truoc = importer.preview(tep);
        KetQuaNhap sau = importer.apply(tep);

        assertThat(sau.applied()).isTrue();
        assertThat(sau.toCreate()).isEqualTo(truoc.toCreate()).isEqualTo(2);
        assertThat(demCongTrinh()).isEqualTo(2);
    }

    @Test
    @DisplayName("Nhập lại cùng tệp → cập nhật, không nhân đôi bản ghi")
    void secondRunUpdatesInsteadOfDuplicating() {
        byte[] tep = csv(TIEU_DE, "T17I-001,Trạm bơm Một,Trạm bơm,CTY,20.98,105.78,Nhuệ,K0+390,1998,1500000000");
        importer.apply(tep);

        byte[] doiTen = csv(TIEU_DE, "T17I-001,Trạm bơm Một (sửa tên),Trạm bơm,CTY,20.98,105.78,Nhuệ,K0+390,1998,");
        KetQuaNhap lan2 = importer.apply(doiTen);

        assertThat(lan2.toUpdate()).isEqualTo(1);
        assertThat(lan2.toCreate()).isZero();
        assertThat(demCongTrinh()).isEqualTo(1);
        assertThat(tenCua("T17I-001")).isEqualTo("Trạm bơm Một (sửa tên)");

        // ⛔⛔ T47.14 — KHẲNG ĐỊNH NÀY LẼ RA PHẢI CÓ TỪ ĐẦU. Dữ liệu của chính bài này đã dựng sẵn
        //    ca hỏng: tệp lần 1 mang `tong_von_vnd=1500000000`, tệp lần 2 để TRỐNG ô ấy. Bài cũ
        //    khẳng định đúng bốn thứ — số dòng cập nhật, số dòng tạo, tổng bản ghi, và cái TÊN —
        //    ⛔ không thứ nào là số tiền. ⇒ **1,5 tỷ đồng bị xoá ngay trong dữ liệu của bài kiểm và
        //    bài kiểm XANH.** Một bộ dữ liệu dựng đúng ca hỏng mà thiếu khẳng định về nó thì tệ hơn
        //    ⛔ không có bài kiểm: nó đọc như một bảo đảm.
        assertThat((BigDecimal) oCua("T17I-001", "total_investment"))
                .as("⛔ Ô để trống trong bảng tính ⛔ KHÔNG phải lệnh xoá — xem `capNhatTuTepNhap`")
                .isEqualByComparingTo(new BigDecimal("1500000000"));
    }

    @Test
    @DisplayName("⛔⛔ T47.14 — nhập lại tệp CHỈ có toạ độ ⛔ KHÔNG được xoá phần còn lại của hồ sơ")
    void nhapLaiChiVoiToaDoKhongXoaPhanConLai() {
        // ⛔⛔ Bài này mô phỏng ĐÚNG luồng đã hứa với Công ty ở T42.20: *"ngày có bảng toạ độ thì
        //    tải tệp mẫu, điền, upload"*. Trước T47.14, người nhập điền mỗi mã + toạ độ rồi tải lên
        //    sẽ XOÁ TRẮNG tuyến sông, lý trình, tổng vốn, năm xây dựng và toàn bộ thông số kỹ
        //    thuật — ⛔ không một dòng log, màn hình báo "nhập thành công 1 dòng".
        //
        // ⚠ Và tuyến sông/lý trình của 11 hồ sơ thật đến từ bản chụp G8 (V202609091073) mà nguồn
        //   ⛔ KHÔNG có API lịch sử: mất là mất hẳn.
        importer.apply(csv(TIEU_DE, "T47I-001,Cống Một,Cống,CTY,20.98,105.78,Sông Nhuệ,K0+390,1998,1500000000"));

        // Thông số kỹ thuật ⛔ không biểu diễn được trong CSV (0/26 ô) — đặt thẳng qua CSDL, đúng
        // như chúng đến từ màn hình hồ sơ công trình trong đời thật.
        Long id = jdbc.queryForObject("SELECT id FROM constructions WHERE code = 'T47I-001'", Long.class);
        jdbc.update(
                """
                INSERT INTO sluice_specs (construction_id, sluice_type, bay_count, bay_width_m)
                VALUES (?, 'VAN_PHANG', 3, 2.50)
                """,
                id);

        // ⚠⚠ VẾ CHỐNG TẬP RỖNG (luật 7 · §11.19) — vế chịu lực nhất của bài này.
        //    Nếu ⛔ không khẳng định dữ liệu ĐANG CÓ trước lượt nhập, thì một lượt hỏng khiến bản
        //    ghi ⛔ không được tạo sẽ cho mọi phép so bên dưới đọc `null == null` và bài kiểm XANH
        //    trong đúng tình huống nó sinh ra để bắt.
        assertThat(jdbc.queryForObject(
                        "SELECT bay_count FROM sluice_specs WHERE construction_id = ?", Integer.class, id))
                .as("⛔ thông số phải ĐANG CÓ trước khi đo phép nhập lại")
                .isEqualTo(3);
        assertThat(oCua("T47I-001", "total_investment")).isNotNull();
        assertThat(oCua("T47I-001", "river_name")).isEqualTo("Sông Nhuệ");

        // Tệp thứ hai: ĐÚNG những gì người nhập điền khi chỉ có bảng toạ độ trong tay.
        KetQuaNhap lan2 = importer.apply(csv(TIEU_DE, "T47I-001,Cống Một,Cống,CTY,21.048201,105.782500,,,,"));

        assertThat(lan2.toUpdate()).isEqualTo(1);

        // (a) Thứ tệp MANG được thì phải ĐỔI — nếu không thì "vá" bằng cách bỏ qua cả lượt nhập.
        assertThat((BigDecimal) oCua("T47I-001", "latitude"))
                .as("⛔ toạ độ mới PHẢI được ghi — đây là cả mục đích của lượt nhập")
                .isEqualByComparingTo(new BigDecimal("21.048201"));

        // (b) Thứ tệp ⛔ KHÔNG mang được, hoặc mang mà để trống, thì phải GIỮ NGUYÊN.
        assertThat(oCua("T47I-001", "river_name"))
                .as("⛔⛔ Tuyến sông đến từ bản chụp G8 và nguồn ⛔ KHÔNG có API lịch sử")
                .isEqualTo("Sông Nhuệ");
        assertThat(oCua("T47I-001", "chainage")).isEqualTo("K0+390");
        assertThat(oCua("T47I-001", "built_year")).isEqualTo(1998);
        // ⚠ `isEqualByComparingTo`, ⛔ KHÔNG `isEqualTo`: `BigDecimal.equals` so cả THANG ĐO, nên
        //   `1500000000` ≠ `1500000000.00` — cùng cái bẫy T46.2 đã trả giá với `compareTo`.
        assertThat((BigDecimal) oCua("T47I-001", "total_investment"))
                .as("⛔ Một ô để trống trong bảng tính ⛔ KHÔNG phải một lệnh xoá 1,5 tỷ đồng")
                .isEqualByComparingTo(new BigDecimal("1500000000"));
        assertThat(jdbc.queryForObject(
                        "SELECT bay_count FROM sluice_specs WHERE construction_id = ?", Integer.class, id))
                .as("⛔⛔ Tệp mẫu có 0/26 ô thông số ⇒ nó ⛔ KHÔNG có quyền nói gì về chúng")
                .isEqualTo(3);
    }

    /** Một ô bất kỳ của hồ sơ — tra theo mã, ⛔ không dựng lại phép ánh xạ cột ở chỗ khác. */
    private Object oCua(String ma, String cot) {
        return jdbc.queryForObject("SELECT %s FROM constructions WHERE code = ?".formatted(cot), Object.class, ma);
    }

    @Test
    @DisplayName("⛔ Một dòng lỗi thì KHÔNG dòng nào được ghi — kể cả những dòng đúng đứng trước nó")
    void oneBadRowBlocksTheWholeFile() {
        byte[] tep = csv(
                TIEU_DE,
                "T17I-001,Trạm bơm Một,Trạm bơm,CTY,20.98,105.78,Nhuệ,K0+390,1998,",
                "T17I-002,Cống Hai,Loại không có thật,CTY,,,Đáy,K2+100,2005,",
                "T17I-003,Kênh Ba,Kênh mương,CTY,,,,,2010,");

        KetQuaNhap khoSau = importer.preview(tep);
        assertThat(khoSau.errors()).hasSize(1);
        assertThat(khoSau.errors().get(0).rowNumber())
                .as("số dòng như người dùng thấy trong Excel")
                .isEqualTo(3);

        assertThatThrownBy(() -> importer.apply(tep)).isInstanceOf(BusinessRuleException.class);
        assertThat(demCongTrinh())
                .as("nhập một nửa rồi dừng là trạng thái tệ nhất — người dùng không biết đã vào tới đâu")
                .isZero();
    }

    @Test
    @DisplayName("⛔ Trùng mã ngay trong tệp bị bắt — nếu không, dòng sau ghi đè dòng trước lặng lẽ")
    void duplicateCodeInsideTheFileIsCaught() {
        byte[] tep = csv(
                TIEU_DE,
                "T17I-001,Trạm bơm Một,Trạm bơm,CTY,,,,,,",
                "T17I-001,Trạm bơm Một bản khác,Trạm bơm,CTY,,,,,,");

        KetQuaNhap baoCao = importer.preview(tep);

        assertThat(baoCao.errors()).hasSize(1);
        assertThat(baoCao.errors().get(0).message()).contains("nhiều lần trong tệp");
    }

    @Test
    @DisplayName("Thiếu cột bắt buộc → từ chối cả tệp, không đọc dòng nào")
    void missingRequiredColumnRejectsTheFile() {
        byte[] tep = csv("ma_cong_trinh,ten_cong_trinh", "T17I-001,Trạm bơm Một");

        KetQuaNhap baoCao = importer.preview(tep);

        assertThat(baoCao.errors()).hasSize(1);
        assertThat(baoCao.errors().get(0).message()).contains("thiếu cột bắt buộc");
    }

    @Test
    @DisplayName("⚠⚠ Dấu chấm: 20.98 là toạ độ thập phân, 1.500.000.000 là hàng nghìn")
    void decimalPointIsNotAThousandSeparator() {
        byte[] tep =
                csv(TIEU_DE, "T17I-001,Trạm bơm Một,Trạm bơm,CTY,20.980000,105.780000,Nhuệ,K0+390,1998,1.500.000.000");

        importer.apply(tep);
        Construction ct = timTheoMa("T17I-001");

        // Quy tắc "bỏ hết dấu chấm" biến vĩ độ 20,98 thành 2098000 — một điểm giữa đại dương, và
        // CHECK của CSDL chỉ chặn khi vượt [-90, 90]. Sai số nhỏ hơn thì không gì bắt được.
        assertThat(ct.getLatitude()).isEqualByComparingTo(new BigDecimal("20.980000"));
        assertThat(ct.getTotalInvestment()).isEqualByComparingTo(new BigDecimal("1500000000"));
    }

    @Test
    @DisplayName("Lý trình trong tệp được quy ra mét — cột sinh của CSDL, để sắp xếp dọc tuyến sông")
    void chainageIsConvertedToMetres() {
        importer.apply(csv(TIEU_DE, "T17I-001,Trạm bơm Một,Trạm bơm,CTY,,,Nhuệ,K18+100,,"));

        assertThat(timTheoMa("T17I-001").getChainageM()).isEqualTo(18100);
    }

    @Test
    @DisplayName("⭐ Đọc được XLSX — chuỗi dùng chung, chuỗi nội tuyến và ô số")
    void readsXlsx() {
        byte[] tep = xlsx();

        KetQuaNhap baoCao = importer.preview(tep);

        assertThat(baoCao.errors()).as("lỗi: %s", baoCao.errors()).isEmpty();
        assertThat(baoCao.toCreate()).isEqualTo(1);
    }

    @Test
    @DisplayName("Tệp rác → OPS-2015, không phải 500")
    void garbageFileIsARequestError() {
        assertThatThrownBy(() -> importer.preview(new byte[] {0x00, 0x01, 0x02}))
                .hasMessageContaining("OPS-2015");
    }

    // -------------------------------------------------------------------------

    private static byte[] csv(String... dong) {
        // Thêm BOM đúng như Excel bản Windows ghi ra — thiếu bước bỏ BOM thì tên cột đầu tiên mang
        // một ký tự vô hình và cả tệp bị báo "thiếu cột bắt buộc".
        return ('﻿' + String.join("\n", dong)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Dựng một tệp XLSX tối thiểu đúng cấu trúc OOXML.
     *
     * <p>⚠ Dòng dữ liệu cố ý trộn ba kiểu ô mà Excel thật sinh ra: chỉ số vào bảng chuỗi dùng chung
     * ({@code t="s"}), chuỗi nội tuyến ({@code t="inlineStr"}) và ô số trần. Chỉ kiểm một kiểu thì
     * bộ đọc có thể đúng với tệp của bài kiểm mà sai với tệp của Công ty.
     */
    private static byte[] xlsx() {
        String sharedStrings =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
                  <si><t>ma_cong_trinh</t></si>
                  <si><t>ten_cong_trinh</t></si>
                  <si><t>loai_cong_trinh</t></si>
                  <si><t>ma_don_vi</t></si>
                  <si><t>T17I-009</t></si>
                  <si><t>Trạm bơm từ Excel</t></si>
                </sst>
                """;
        String sheet =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="inlineStr"><is><t>nam_xay_dung</t></is></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>4</v></c>
                      <c r="B2" t="s"><v>5</v></c>
                      <c r="C2" t="inlineStr"><is><t>Trạm bơm</t></is></c>
                      <c r="D2" t="inlineStr"><is><t>CTY</t></is></c>
                      <c r="E2"><v>1998</v></c>
                    </row>
                  </sheetData>
                </worksheet>
                """;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(sharedStrings.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long demCongTrinh() {
        return constructions.search(ConstructionFilter.rong(), PageRequest.of(0, 100)).stream()
                .filter(c -> c.getCode().startsWith("T17I-"))
                .count();
    }

    private Construction timTheoMa(String ma) {
        return constructions.search(ConstructionFilter.rong(), PageRequest.of(0, 100)).stream()
                .filter(c -> c.getCode().equals(ma))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không thấy công trình " + ma));
    }

    private String tenCua(String ma) {
        return timTheoMa(ma).getName();
    }

    private void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T17I-%'");
    }
}
