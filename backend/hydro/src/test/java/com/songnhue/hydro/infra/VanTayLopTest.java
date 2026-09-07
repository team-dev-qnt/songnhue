package com.songnhue.hydro.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vân tay mã đang chạy — T31.11.
 *
 * <p>⚠ Bài kiểm này ⛔ không khẳng định một giá trị băm cụ thể: giá trị ấy đổi mỗi lần ai đó sửa
 * một dòng trong lớp được băm, và một khẳng định như vậy sẽ bị gỡ ngay lần thứ hai nó đỏ. Thứ đáng
 * canh là <b>ba tính chất</b> của phép băm — ổn định, phân biệt được, và không ném.
 */
class VanTayLopTest {

    @Test
    @DisplayName("Cùng một lớp cho cùng một vân tay — nếu không thì nó chẳng đối chiếu được gì")
    void onDinhGiuaHaiLuotGoi() {
        assertThat(VanTayLop.cua(VanTayLopTest.class)).isEqualTo(VanTayLop.cua(VanTayLopTest.class));
    }

    @Test
    @DisplayName("⭐ Hai lớp khác nhau cho hai vân tay khác nhau — luật 9: phân biệt được hai trạng thái")
    void haiLopKhacNhauChoHaiVanTay() {
        String a = VanTayLop.cua(VanTayLop.class);
        String b = VanTayLop.cua(VanTayLopTest.class);

        assertThat(a).hasSize(VanTayLop.SO_KY_TU).isNotEqualTo(VanTayLop.KHONG_DOC_DUOC);
        assertThat(b).hasSize(VanTayLop.SO_KY_TU);
        // Không phân biệt được hai lớp thì cũng không phân biệt được hai bản build của một lớp —
        // và khi ấy dòng log lúc khởi động là một lời bảo đảm rỗng.
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Dạng nhiều lớp in đủ từng tên — dán thẳng vào log là đọc được")
    void dangNhieuLopInDuTen() {
        String dong = VanTayLop.cua(List.of(VanTayLop.class, VanTayLopTest.class));

        assertThat(dong).contains("VanTayLop=").contains("VanTayLopTest=").contains(" · ");
        assertThat(dong.split(" · ")).hasSize(2);
    }

    @Test
    @DisplayName("⛔ Lớp không đọc được byte KHÔNG ném — một lượt khởi động không được hỏng vì một dòng log")
    void khongNemKhiKhongDocDuoc() {
        // int.class không có tệp .class nào để đọc trên classpath.
        assertThat(VanTayLop.cua(int.class)).isEqualTo(VanTayLop.KHONG_DOC_DUOC);
    }

    @Test
    @DisplayName("Danh sách rỗng cho chuỗi rỗng, ⛔ không ném")
    void danhSachRong() {
        assertThat(VanTayLop.cua(List.of())).isEmpty();
    }
}
