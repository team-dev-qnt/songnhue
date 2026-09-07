package com.songnhue.content.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Dấu vết đọc chỉ ghi MỘT lần — và ⛔ KHÔNG đụng tới trạng thái.</b> CN-01.4.
 *
 * <p>Màn hình quản trị gọi lượt đánh dấu mỗi khi người dùng mở một dòng — mở đi mở lại là chuyện
 * bình thường. Nếu mỗi lượt mở đều ghi đè {@code read_by}/{@code read_at} thì câu hỏi <i>"ai là
 * người đầu tiên thấy phản ánh này, lúc mấy giờ"</i> không còn trả lời được, mà đó chính là câu
 * hỏi duy nhất hai cột ấy sinh ra để trả lời.
 *
 * <h2>⭐⭐ Đổi từ WS-36: {@code MOI → DA_DOC} nay đi qua Workflow engine</h2>
 *
 * <p>Trước đây {@code danhDauDaDoc()} làm <b>hai</b> việc: ghi dấu vết, và gán thẳng
 * {@code this.status}. Việc thứ hai là một đường ghi trạng thái mà luật ArchUnit
 * {@code chi_workflow_engine_duoc_goi_applyState} ⛔ <b>không</b> thấy — luật soi lời gọi
 * {@code applyState()}, còn đó là một phép gán field bên trong entity.
 *
 * <p>⇒ Nay còn đúng một việc: {@link Contact#ghiDauVetDoc}. Trạng thái là chuyện của engine, và
 * bài này khẳng định entity ⛔ <b>không</b> tự đổi nó — vế ấy là vế mới, và là vế chịu lực.
 */
class ContactTest {

    private static Contact mau() {
        return new Contact("Nguyễn Văn A", "a@example.invalid", null, "Chủ đề", "Nội dung");
    }

    @Test
    @DisplayName("⭐ Bản ghi mới ở trạng thái MOI và chưa có dấu đọc")
    void moiTaoThiChuaDoc() {
        Contact c = mau();
        assertThat(c.getStatus()).isEqualTo(ContactStatus.MOI);
        assertThat(c.getReadAt()).isNull();
        assertThat(c.getReadBy()).isNull();
    }

    @Test
    @DisplayName("⭐⭐ Lượt đọc đầu ghi người + mốc, và ⛔ KHÔNG đụng trạng thái")
    void luotDauGhiDau() {
        Contact c = mau();
        Instant luc = Instant.parse("2026-08-29T02:00:00Z");

        c.ghiDauVetDoc(7L, luc);

        assertThat(c.getStatus())
                .as("⛔⛔ Ghi dấu vết ⛔ KHÔNG được đổi trạng thái — đó là việc của Workflow engine "
                        + "(quy tắc 4). Một entity tự đổi status là một đường ghi thứ hai mà ⛔ không "
                        + "cơ chế canh gác nào của dự án nhìn thấy.")
                .isEqualTo(ContactStatus.MOI);
        assertThat(c.getReadBy()).isEqualTo(7L);
        assertThat(c.getReadAt()).isEqualTo(luc);
    }

    @Test
    @DisplayName("⛔ Lượt đọc thứ hai KHÔNG ghi đè người và mốc của lượt đầu")
    void luotSauKhongGhiDe() {
        Contact c = mau();
        Instant dau = Instant.parse("2026-08-29T02:00:00Z");
        c.ghiDauVetDoc(7L, dau);

        c.ghiDauVetDoc(99L, Instant.parse("2026-08-29T09:00:00Z"));

        assertThat(c.getReadBy())
                .as("người đọc đầu tiên là dữ liệu, không phải người mở gần nhất")
                .isEqualTo(7L);
        assertThat(c.getReadAt()).isEqualTo(dau);
    }

    @Test
    @DisplayName("⭐ `applyState` là đường DUY NHẤT đổi trạng thái — và nó ném khi chuỗi lạ")
    void applyStateLaDuongDuyNhat() {
        Contact c = mau();

        c.applyState("DANG_XU_LY");
        assertThat(c.getStatus()).isEqualTo(ContactStatus.DANG_XU_LY);
        assertThat(c.currentState()).isEqualTo("DANG_XU_LY");
        assertThat(c.workflowEntityType()).isEqualTo(Contact.ENTITY_TYPE);

        // ⛔ Một `to_state` gõ sai trong migration phải hỏng NGAY ở lượt bấm đầu tiên, ⛔ không lặng
        //    lẽ giữ nguyên trạng thái cũ rồi để người dùng bấm lại lần thứ ba.
        assertThatThrownBy(() -> c.applyState("KHONG_CO_TRANG_THAI_NAY")).isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getStatus())
                .as("⛔ Một bước chuyển bị từ chối ⛔ không được để lại nửa kết quả")
                .isEqualTo(ContactStatus.DANG_XU_LY);
    }

    @Test
    @DisplayName("⭐ Lý do của bước gần nhất ghi đè lý do cũ — kể cả bằng `null`")
    void lyDoLuonLaCuaBuocGanNhat() {
        Contact c = mau();

        c.applyWorkflowReason("REPLY", "Đã gọi điện trả lời");
        assertThat(c.getResolutionNote()).isEqualTo("Đã gọi điện trả lời");

        c.applyWorkflowReason("CLOSE", null);
        assertThat(c.getResolutionNote())
                .as("⭐ Giữ lại là để một câu giải thích của tháng trước đứng cạnh một trạng thái "
                        + "của hôm nay — người đọc sẽ hiểu nó là lý do của bước vừa rồi")
                .isNull();
    }

    @Test
    @DisplayName("⭐ Giữ nguyên nội dung người dân gửi — không cắt, không sửa")
    void giuNguyenNoiDung() {
        Contact c = new Contact("Trần Thị B", null, "0243354xxxx", "Kênh N4", "Đoạn qua xã bị bồi lắng.");
        assertThat(c.getFullName()).isEqualTo("Trần Thị B");
        assertThat(c.getEmail()).isNull();
        assertThat(c.getPhone()).isEqualTo("0243354xxxx");
        assertThat(c.getSubject()).isEqualTo("Kênh N4");
        assertThat(c.getContent()).isEqualTo("Đoạn qua xã bị bồi lắng.");
    }
}
