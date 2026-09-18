package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hr.api.HrDtos;

/**
 * Bất biến của {@code HrDtos}: ⛔ KHÔNG DTO nào của màn hình danh sách / chi tiết mang một trường
 * 🔒 — CN-04.2, quy tắc 10, NĐ 13/2023.
 *
 * <h2>Vì sao là bài kiểm CẤU TRÚC, ⛔ không phải bài kiểm hành vi</h2>
 *
 * <p>Một bài gọi API rồi soi thân JSON chỉ chứng minh <b>hồ sơ vừa dựng</b> ⛔ không lộ gì — mà hồ
 * sơ ấy do chính bài kiểm tạo ra, thường ⛔ không có trường 🔒 nào để lộ. Đọc
 * {@code getRecordComponents()} thì hỏi thẳng <i>hợp đồng của kiểu</i>: thêm một trường
 * {@code baseSalary} vào {@link HrDtos.EmployeeDetail} là bài này đỏ ngay, ⛔ không cần một hàng dữ
 * liệu nào tồn tại. Khuôn lấy từ {@code PublicOrgDirectoryServiceTest:215-251}.
 *
 * <p>⚠ Cố ý <b>⛔ KHÔNG</b> {@code extends IntegrationTestBase}: JUnit trần, ⛔ không CSDL, ⛔ không
 * container. Một bất biến về kiểu ⛔ không cần cái nào trong số đó, và bắt nó chờ Docker là cách
 * chắc chắn nhất để một ngày nào đó nó bị bỏ qua.
 *
 * <h2>Ba lớp phòng thân, vì một phép so tên rất dễ xanh vì lý do sai</h2>
 *
 * <ol>
 *   <li><b>Chống-tập-rỗng</b> — {@code getRecordComponents()} của một record RỖNG trả mảng rỗng, và
 *       "⛔ không thành phần nào nằm trong tập cấm" khi ấy đúng một cách vô nghĩa. Nên phải khẳng
 *       định số thành phần tối thiểu (luật 7).
 *   <li><b>Đối chứng phải-tìm-thấy</b> — {@link HrDtos.SensitiveView} PHẢI mang đủ 8 tên ấy. Thiếu
 *       vế này thì một lỗi chính tả trong tập cấm (VD {@code "baseSalaryy"}) cho ra một bộ canh ⛔
 *       không bao giờ bắt được gì, và cái xanh của nó đọc như một lời bảo đảm (luật 28).
 *   <li><b>Tên tập cấm khai một lần</b> — hai bản sao lệch nhau là chuyện đã trả giá nhiều lần.
 * </ol>
 */
class HoSoNhanSuKhongLoTruongKinTest {

    /**
     * Chín tên trường 🔒 — <b>đúng chín cột giá trị</b> của bảng {@code employee_sensitive}.
     *
     * <p>{@code nationalIdFingerprint} có trong tập cấm mà ⛔ KHÔNG có trong {@link
     * HrDtos.SensitiveView}, và đó là chủ đích: vân tay HMAC ⛔ không giải ngược được nhưng nó vẫn
     * là một <i>oracle</i> — ai cầm nó có thể thử từng số CCCD để xác nhận một người có mặt trong
     * hệ thống. Nó ⛔ không được ra khỏi CSDL qua bất kỳ DTO nào.
     */
    private static final Set<String> TRUONG_KHOA = Set.of(
            "nationalId",
            "nationalIdIssuedOn",
            "nationalIdIssuedPlace",
            "baseSalary",
            "salaryCoefficient",
            "bankAccount",
            "taxCode",
            "socialInsuranceNo",
            "nationalIdFingerprint");

    @Test
    @DisplayName("⛔⛔ EmployeeRow và EmployeeDetail ⛔ KHÔNG mang một trường 🔒 nào")
    void danhSachVaChiTietKhongMangTruongKin() {
        List<String> truongDong = tenThanhPhan(HrDtos.EmployeeRow.class);
        List<String> truongChiTiet = tenThanhPhan(HrDtos.EmployeeDetail.class);

        // ⛔⛔ CHỐNG-TẬP-RỖNG. Một record rỗng (hoặc một lượt đổi tên lớp làm phép so trỏ vào chỗ
        // khác) khiến hai khẳng định `doesNotContainAnyElementsOf` bên dưới xanh mà ⛔ không canh
        // gì cả — luật 7, đúng hình dạng đã trả giá ở §11.19.
        assertThat(truongChiTiet)
                .as("EmployeeDetail là hồ sơ đầy đủ (23 trường nghiệp vụ + tên đơn vị/chức vụ + cờ 🔒)")
                .hasSizeGreaterThanOrEqualTo(20);
        assertThat(truongDong)
                .as("EmployeeRow là một dòng danh sách — gọn, nhưng ⛔ không thể gọn tới mức rỗng")
                .hasSizeGreaterThanOrEqualTo(7);

        assertThat(truongDong)
                .as("một dòng danh sách CBNV lộ lương hay CCCD là lộ cho MỌI người có "
                        + "hr:employee:view — quyền rộng nhất của module")
                .doesNotContainAnyElementsOf(TRUONG_KHOA);
        assertThat(truongChiTiet)
                .as("quyền xem trường 🔒 là hr:employee:view-sensitive, gác một endpoint KHÁC — "
                        + "gộp vào đây là biến nó thành một nhánh if mà chỉ cần sai một lần là lộ hết")
                .doesNotContainAnyElementsOf(TRUONG_KHOA);
    }

    @Test
    @DisplayName("Đối chứng phải-tìm-thấy: SensitiveView CÓ đủ 8 tên 🔒 — phép so tên đang hoạt động")
    void phepSoTenThucSuBatDuocTruongKin() {
        List<String> truongKin = tenThanhPhan(HrDtos.SensitiveView.class);

        // Thiếu bài này thì một lỗi chính tả trong TRUONG_KHOA (hoặc một lượt đổi tên trường ở DTO)
        // biến bộ canh trên thành một phép so LUÔN TRẢ RỖNG: nó xanh mãi mãi, kể cả ngày CCCD thật
        // sự chảy ra màn hình danh sách. Đây là nửa "phải tìm thấy" của cùng một phép đo.
        assertThat(truongKin)
                .as("SensitiveView là DTO DUY NHẤT được phép mang giá trị 🔒 — nếu tám tên này ⛔ "
                        + "không khớp ở đây thì tập cấm ở trên đang canh những cái tên ⛔ không tồn tại")
                .containsExactlyInAnyOrder(
                        "nationalId",
                        "nationalIdIssuedOn",
                        "nationalIdIssuedPlace",
                        "baseSalary",
                        "salaryCoefficient",
                        "bankAccount",
                        "taxCode",
                        "socialInsuranceNo");

        assertThat(truongKin)
                .as("⛔ vân tay HMAC là một oracle dò tên người — nó ⛔ không ra khỏi CSDL qua DTO nào")
                .doesNotContain("nationalIdFingerprint");
    }

    @Test
    @DisplayName("EmployeeDetail PHẢI có cờ `sensitive` — \"chưa nhập\" và \"⛔ không được xem\" phải khác nhau")
    void chiTietPhaiMangCoDaCoDuLieu() {
        assertThat(tenThanhPhan(HrDtos.EmployeeDetail.class))
                .as("hai trạng thái ấy mà trông giống nhau (cùng một ô trống) thì sẽ có người nhập "
                        + "đè lên dữ liệu đang có — T50.1 là một sự cố đúng hình dạng ấy")
                .contains("sensitive");
    }

    private static List<String> tenThanhPhan(Class<?> record) {
        RecordComponent[] thanhPhan = record.getRecordComponents();
        assertThat(thanhPhan)
                .as("%s phải là một record — getRecordComponents() trả null nếu ⛔ không", record.getSimpleName())
                .isNotNull();
        return Arrays.stream(thanhPhan).map(RecordComponent::getName).toList();
    }
}
