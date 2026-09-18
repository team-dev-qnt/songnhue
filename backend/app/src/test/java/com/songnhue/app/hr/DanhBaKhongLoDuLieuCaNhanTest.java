package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.hr.api.HoSoConDtos;
import com.songnhue.hr.api.HrDtos;
import com.songnhue.hr.application.DanhBaMuc;

/**
 * Bất biến của danh bạ nội bộ: nó ⛔ <b>KHÔNG</b> mang dữ liệu cá nhân — CN-04.6, NĐ 13/2023.
 *
 * <h2>⛔⛔ Vì sao tập cấm ở đây RỘNG HƠN tập của {@code HoSoNhanSuKhongLoTruongKinTest}</h2>
 *
 * <p>Bài kia cấm **9 trường 🔒** trên hồ sơ CBNV. Ở đây tập cấm còn thêm **8 trường thường** —
 * ngày sinh, quê quán, địa chỉ nhà, email cá nhân, dân tộc, hôn nhân, và hai ô liên hệ khẩn cấp.
 * Chúng hoàn toàn hợp lệ trên {@link HrDtos.EmployeeDetail}, vì màn hình ấy gác bằng
 * {@code hr:employee:view} và cắt theo phạm vi đơn vị.
 *
 * <p>Danh bạ thì ⛔ không có cả hai lớp ấy: quyền {@code hr:directory:view} cấp cho <b>11/12</b>
 * vai trò, và câu truy vấn cố ý ⛔ <b>không</b> đi qua bộ lọc phạm vi. ⇒ Một trường lọt vào đây là
 * <b>công bố cho toàn Công ty</b> — địa chỉ nhà và số điện thoại người thân của 200 con người.
 * Đó là hai mức rủi ro khác hẳn nhau trên cùng một bảng dữ liệu, nên cần hai bộ canh khác nhau.
 *
 * <p>⚠ Cùng khuôn bài kia: JUnit trần, ⛔ không CSDL, ⛔ không container. Một bất biến về <b>kiểu</b>
 * ⛔ không cần cái nào trong số đó, và bắt nó chờ Docker là cách chắc nhất để một ngày nó bị bỏ qua.
 */
class DanhBaKhongLoDuLieuCaNhanTest {

    /** Chín trường 🔒 — chép ý nghĩa từ {@code employee_sensitive}, ⛔ không chép giá trị. */
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

    /**
     * Tám trường <b>⛔ không phải 🔒</b> nhưng vẫn là dữ liệu cá nhân — cấm trên danh bạ.
     *
     * <p>⛔ {@code phone} và {@code workEmail} <b>⛔ không</b> nằm ở đây: đặc tả CN-04.6 vẽ chúng
     * đích danh trên thẻ danh bạ. Ranh giới là <i>"liên hệ công vụ"</i>, ⛔ không phải <i>"mọi thứ
     * thuộc về một con người"</i>.
     */
    private static final Set<String> TRUONG_CA_NHAN = Set.of(
            "dateOfBirth",
            "hometown",
            "address",
            "personalEmail",
            "ethnicity",
            "maritalStatus",
            "emergencyContactName",
            "emergencyContactPhone");

    @Test
    @DisplayName("⛔⛔ DanhBaView ⛔ KHÔNG mang một trường 🔒 hay một trường cá nhân nào")
    void danhBaKhongMangDuLieuCaNhan() {
        List<String> truong = tenThanhPhan(HoSoConDtos.DanhBaView.class);

        // ⛔⛔ CHỐNG-TẬP-RỖNG (luật 7): một record rỗng, hoặc một lượt đổi tên lớp làm phép so trỏ
        // vào chỗ khác, khiến hai khẳng định dưới xanh mà ⛔ không canh gì cả.
        assertThat(truong)
                .as("Thẻ danh bạ theo đặc tả: tên, mã, chức vụ/chức danh, đơn vị, SĐT, email nội bộ")
                .hasSizeGreaterThanOrEqualTo(8);

        assertThat(truong).doesNotContainAnyElementsOf(TRUONG_KHOA);
        assertThat(truong)
                .as("⛔⛔ Danh bạ ⛔ không qua bộ lọc phạm vi và mở cho 11/12 vai trò — một trường ở "
                        + "đây là CÔNG BỐ nó cho toàn Công ty, ⛔ không phải 'hiện thêm một cột'")
                .doesNotContainAnyElementsOf(TRUONG_CA_NHAN);
    }

    @Test
    @DisplayName("⛔ Record NGUỒN của tầng application cũng sạch — chặn từ chỗ dữ liệu ra khỏi CSDL")
    void recordNguonCungKhongMangDuLieuCaNhan() {
        // ⚠ `DanhBaView` chỉ là lớp vỏ API. Nếu chỉ canh nó thì một trường cá nhân vẫn đi được từ
        //   CSDL tới tầng application rồi rơi vào một log, một báo cáo, hay một DTO thứ hai viết
        //   sau. Canh cả `DanhBaMuc` là canh ở chỗ dữ liệu ĐI QUA (luật 12).
        List<String> truong = tenThanhPhan(DanhBaMuc.class);
        assertThat(truong).hasSizeGreaterThanOrEqualTo(8);
        assertThat(truong).doesNotContainAnyElementsOf(TRUONG_KHOA);
        assertThat(truong).doesNotContainAnyElementsOf(TRUONG_CA_NHAN);
    }

    @Test
    @DisplayName("⛔ Đối chứng PHẢI-TÌM-THẤY: hai tập cấm ⛔ không được là hai tập gõ sai chính tả")
    void tapCamPhaiTrungVoiTenTruongCoThAT() {
        // ⛔⛔ Thiếu vế này thì một lỗi chính tả (`"baseSalaryy"`, `"dateOfBirht"`) cho ra một bộ
        //    canh ⛔ không bao giờ bắt được gì — và cái xanh của nó đọc như một lời bảo đảm
        //    (luật 28). Hai record dưới đây PHẢI mang đủ những tên ấy.
        assertThat(tenThanhPhan(HrDtos.SensitiveView.class))
                .as("SensitiveView là nơi 8 trường 🔒 hợp lệ tồn tại")
                .containsAll(TRUONG_KHOA.stream()
                        .filter(t -> !t.equals("nationalIdFingerprint"))
                        .toList());

        assertThat(tenThanhPhan(HrDtos.EmployeeDetail.class))
                .as("EmployeeDetail là nơi 8 trường cá nhân hợp lệ tồn tại — gác bằng "
                        + "hr:employee:view và cắt theo phạm vi đơn vị")
                .containsAll(TRUONG_CA_NHAN);
    }

    private static List<String> tenThanhPhan(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
