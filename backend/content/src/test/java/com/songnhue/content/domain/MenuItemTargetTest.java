package com.songnhue.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MenuItem#pointTo} — đổi đích của một mục menu <b>phải dọn hai cột kia</b>.
 *
 * <p>Cùng hình dạng với bẫy ô ẩn của AntD ở phía giao diện: giá trị cũ không tự biến mất khi người
 * dùng đổi lựa chọn. Ở đây hậu quả nặng hơn một bậc — {@code menu_items} có ràng buộc CSDL cho phép
 * đúng một cột đích có giá trị, nên một mục còn sót {@code categoryId} sau khi đã đổi sang URL sẽ bị
 * CSDL từ chối, và người dùng nhận một lỗi kỹ thuật cho một thao tác hoàn toàn hợp lệ.
 */
class MenuItemTargetTest {

    @Test
    @DisplayName("⭐ Đổi Danh mục → URL: cột categoryId phải được dọn")
    void doiTuDanhMucSangUrlThiDonCotCu() {
        MenuItem muc = new MenuItem();
        muc.pointTo(MenuLinkType.CATEGORY, 42L, null, null);
        assertThat(muc.getCategoryId()).isEqualTo(42L);

        muc.pointTo(MenuLinkType.URL, 42L, null, "https://songnhue.vn/lien-he");

        assertThat(muc.getCategoryId())
                .as("⛔ Còn sót categoryId sau khi đổi đích → ràng buộc CSDL từ chối, và người dùng "
                        + "nhận một lỗi kỹ thuật cho một thao tác hợp lệ")
                .isNull();
        assertThat(muc.getUrl()).isEqualTo("https://songnhue.vn/lien-he");
    }

    @Test
    @DisplayName("⭐ Đổi Bài viết → Danh mục: cột articleId phải được dọn")
    void doiTuBaiVietSangDanhMucThiDonCotCu() {
        MenuItem muc = new MenuItem();
        muc.pointTo(MenuLinkType.ARTICLE, null, 7L, null);
        assertThat(muc.getArticleId()).isEqualTo(7L);

        muc.pointTo(MenuLinkType.CATEGORY, 9L, 7L, null);

        assertThat(muc.getArticleId()).isNull();
        assertThat(muc.getCategoryId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("Mỗi loại đích chỉ giữ ĐÚNG cột của nó, kể cả khi nơi gọi truyền thừa")
    void moiLoaiChiGiuDungCotCuaNo() {
        MenuItem muc = new MenuItem();

        // Nơi gọi truyền cả ba giá trị — lớp domain phải tự lọc, không trông chờ nơi gọi cẩn thận.
        muc.pointTo(MenuLinkType.CATEGORY, 1L, 2L, "https://a.vn");
        assertThat(muc.getCategoryId()).isEqualTo(1L);
        assertThat(muc.getArticleId()).isNull();
        assertThat(muc.getUrl()).isNull();

        muc.pointTo(MenuLinkType.ARTICLE, 1L, 2L, "https://a.vn");
        assertThat(muc.getArticleId()).isEqualTo(2L);
        assertThat(muc.getCategoryId()).isNull();
        assertThat(muc.getUrl()).isNull();
    }

    @Test
    @DisplayName("Liên kết hệ thống văn bản (EXTERNAL_DOC) giữ URL — cùng cột với loại URL thường")
    void lienKetHeThongVanBanGiuUrl() {
        MenuItem muc = new MenuItem();
        muc.pointTo(MenuLinkType.EXTERNAL_DOC, null, null, "/van-ban");

        assertThat(muc.getUrl()).isEqualTo("/van-ban");
        assertThat(muc.getCategoryId()).isNull();
        assertThat(muc.getArticleId()).isNull();
    }

    @Test
    @DisplayName("Vị trí trong cây là giá trị dẫn xuất — placeAt đặt cả ba cột cùng lúc")
    void viTriTrongCayDatCungLuc() {
        MenuItem muc = new MenuItem();
        muc.placeAt(5L, "/5/9/", (short) 2);

        assertThat(muc.getParentId()).isEqualTo(5L);
        assertThat(muc.getPath()).isEqualTo("/5/9/");
        assertThat(muc.getDepth()).isEqualTo((short) 2);
    }
}
