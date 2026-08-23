package com.songnhue.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link Article#isPubliclyVisible(Instant)} — ba điều kiện, thiếu một là không hiện.
 *
 * <h2>Vì sao kiểm ở tầng domain chứ không chỉ qua HTTP</h2>
 *
 * <p>Hàm này là <b>chỗ duy nhất</b> trả lời câu "bài có đang hiện ngoài cổng không", và nó được cả
 * truy vấn công khai lẫn màn hình quản trị dùng chung — chính vì thế mà hai bên không trả lời khác
 * nhau về cùng một bài. Một hàm như vậy cần bài kiểm đi hết <i>từng</i> điều kiện, mà bài HTTP thì
 * chỉ đi được vài tổ hợp thực tế dựng nổi.
 *
 * <p>⚠ Đây cũng là lần đầu <b>cổng bao phủ tầng domain của module {@code content} thực sự chạy</b>.
 * Trước đó module không có bài kiểm nào, nên JaCoCo báo *"Skipping … due to missing execution data
 * file"* và luật bị bỏ qua trong im lặng — đúng khuôn luật 7: một cơ chế chưa ai đi qua thì chưa
 * biết nó đúng hay sai.
 */
class ArticleVisibilityTest {

    private static final Instant BAY_GIO = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    @DisplayName("Đủ ba điều kiện thì hiện")
    void duBaDieuKienThiHien() {
        assertThat(bai(ArticleState.XUAT_BAN, 7L, BAY_GIO.minus(1, ChronoUnit.HOURS))
                        .isPubliclyVisible(BAY_GIO))
                .isTrue();
    }

    @Test
    @DisplayName("⛔ Chưa có bản được duyệt → không hiện, dù trạng thái đã là Xuất bản")
    void chuaCoBanDuyetThiKhongHien() {
        assertThat(bai(ArticleState.XUAT_BAN, null, BAY_GIO.minus(1, ChronoUnit.HOURS))
                        .isPubliclyVisible(BAY_GIO))
                .as("thiếu `publishedVersionId` mà vẫn hiện là hiện một bài KHÔNG có nội dung đã duyệt")
                .isFalse();
    }

    @Test
    @DisplayName("⛔ Chưa tới giờ đăng → không hiện; tới giờ là tự hiện, không cần ai bấm lại")
    void henGioDangDuocTonTrong() {
        Article henGio = bai(ArticleState.XUAT_BAN, 7L, BAY_GIO.plus(1, ChronoUnit.HOURS));

        assertThat(henGio.isPubliclyVisible(BAY_GIO)).isFalse();
        assertThat(henGio.isPubliclyVisible(BAY_GIO.plus(1, ChronoUnit.HOURS)))
                .as("đúng giây hẹn là hiện — biên phải là 'không SAU', không phải 'TRƯỚC'")
                .isTrue();
    }

    @Test
    @DisplayName("⛔ Gỡ bài → tắt ngay, kể cả khi bản duyệt và giờ đăng vẫn còn nguyên")
    void goBaiThiTatNgay() {
        assertThat(bai(ArticleState.GO_BAI, 7L, BAY_GIO.minus(1, ChronoUnit.DAYS))
                        .isPubliclyVisible(BAY_GIO))
                .as("gỡ bài là thao tác khẩn — nếu nó phải chờ xoá `published_at` mới có tác dụng thì "
                        + "một nội dung sai vẫn nằm ngoài cổng thêm một nhịp nữa")
                .isFalse();
    }

    @Test
    @DisplayName("⛔ Bản nháp không bao giờ hiện, kể cả khi dữ liệu còn sót lại từ lần đăng trước")
    void banNhapKhongHien() {
        assertThat(bai(ArticleState.NHAP, 7L, BAY_GIO.minus(1, ChronoUnit.DAYS)).isPubliclyVisible(BAY_GIO))
                .isFalse();
    }

    @Test
    @DisplayName("⚠ Trạng thái trung gian không lọt — nhưng LUU_TRU thì vẫn hiện, ghi lại đúng hành vi")
    void trangThaiTrungGianKhongLot() {
        assertThat(bai(ArticleState.CHO_DUYET, null, null).isPubliclyVisible(BAY_GIO))
                .isFalse();
        assertThat(bai(ArticleState.YEU_CAU_CHINH_SUA, null, null).isPubliclyVisible(BAY_GIO))
                .isFalse();
        assertThat(bai(ArticleState.LUU_TRU, 7L, BAY_GIO.minus(1, ChronoUnit.DAYS))
                        .isPubliclyVisible(BAY_GIO))
                .as("⚠ Lưu trữ KHÔNG nằm trong danh sách loại trừ của isPubliclyVisible — bài đã lưu "
                        + "trữ mà còn bản duyệt và đã tới giờ đăng thì VẪN hiện. Ghi lại hành vi thật "
                        + "ở đây thay vì giả định; nếu đó là ý muốn khác thì sửa hàm, không sửa bài kiểm")
                .isTrue();
    }

    @Test
    @DisplayName("Tác giả của chiều phản hồi là `authorUserId`, KHÔNG phải người bấm nút Tạo mới")
    void nguoiNhanPhanHoiLaTacGiaHienTai() {
        Article bai = new Article("Tiêu đề", "tieu-de", "<p>nội dung</p>", 11L);
        ReflectionTestUtils.setField(bai, "createdBy", 99L);

        assertThat(bai.ownerUserId())
                .as("biểu mẫu cho đổi tác giả; thư báo bài bị trả về phải tới tác giả hiện tại chứ "
                        + "không tới người đã tạo bài từ tháng trước")
                .isEqualTo(11L);

        bai.setAuthorUserId(22L);
        assertThat(bai.ownerUserId()).isEqualTo(22L);
    }

    @Test
    @DisplayName("Bài mới dựng mang trạng thái Nháp và chưa hiện ở đâu")
    void baiMoiDungLaNhap() {
        Article moi = new Article("Tiêu đề", "tieu-de", "<p>nội dung</p>", 1L);

        assertThat(moi.currentState()).isEqualTo(ArticleState.NHAP);
        assertThat(moi.workflowEntityType()).isEqualTo(Article.ENTITY_TYPE);
        assertThat(moi.isPubliclyVisible(BAY_GIO)).isFalse();
    }

    // -------------------------------------------------------------------------

    private static Article bai(String trangThai, Long banDuyet, Instant gioDang) {
        Article bai = new Article("Tiêu đề", "tieu-de", "<p>nội dung</p>", 1L);
        bai.applyState(trangThai);
        ReflectionTestUtils.setField(bai, "publishedVersionId", banDuyet);
        ReflectionTestUtils.setField(bai, "publishedAt", gioDang);
        return bai;
    }
}
