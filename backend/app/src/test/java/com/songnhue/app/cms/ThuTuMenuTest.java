package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.MenuService;
import com.songnhue.content.application.PublicPortalService;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * Kéo–thả đổi thứ tự menu ở màn hình quản trị phải đổi được thứ tự trên cổng — T26.25.
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * Vì {@code sort_order} là một <b>cái núm bày ra ở màn hình quản trị mà không điều khiển gì</b>. Cây
 * menu đọc bằng {@code ORDER BY path ASC, sort_order ASC}, mà {@code path} của mọi mục <b>chứa id của
 * chính nó</b> ({@code MaterializedPath.childPath}) ⇒ hai anh em <b>không bao giờ</b> trùng
 * {@code path} ⇒ {@code sort_order} không bao giờ được so tới. Thứ tự thật là thứ tự {@code path}
 * <b>so theo chuỗi</b> — tức {@code /10/} đứng trước {@code /9/}.
 *
 * <p>Triệu chứng là hình dạng đắt nhất của dự án (quy tắc 27): người dùng kéo thả, giao diện vẽ lại
 * đúng thứ tự mới, API trả <b>204 thành công</b>, và cổng công khai <b>không đổi gì</b>. Không lỗi,
 * không log.
 *
 * <p>⛔ Ba lời khẳng định trong kho nói ngược lại sự thật đó, và cả ba đều là <b>văn xuôi</b> — thứ
 * không bộ canh nào bắt được:
 *
 * <ul>
 *   <li>{@code MenuItemRepository} — <i>"anh em đúng thứ tự"</i>
 *   <li>{@code MenuService.tree()} — <i>"Danh sách đã sắp theo path nên cha luôn đứng trước con"</i>
 *       (vế cha–con đúng, vế anh em không được nói ra nên người đọc tự suy là có)
 *   <li>{@code buildMenuTree} ở public-web — <i>"Backend đã sắp… không cần sắp lại"</i>
 * </ul>
 *
 * <p>⚠ Bài này đi qua {@link PublicPortalService#menu} — <b>đúng đường cổng công khai đi</b>, không
 * gọi thẳng repository (quy tắc 5). {@code buildMenuTree} ở FE duyệt một lượt và <b>không sắp lại</b>,
 * nên thứ tự trả ra ở đây chính là thứ tự người dân nhìn thấy.
 */
class ThuTuMenuTest extends IntegrationTestBase {

    @Autowired
    private MenuService menus;

    @Autowired
    private PublicPortalService portal;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void dangNhap() {
        CmsFixtures.donDep(jdbc);
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "thu-tu-menu",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of("cms:layout:manage"),
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null));
    }

    @AfterEach
    void donDep() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
    }

    private MenuService.Target url(String duongDan) {
        return new MenuService.Target(MenuLinkType.URL, null, null, duongDan);
    }

    /** Nhãn của các mục do bài này tạo, theo đúng thứ tự cổng trả ra. */
    private List<String> thuTuTrenCong(String tienTo) {
        return portal.menu(MenuPosition.HEADER).stream()
                .map(MenuService.MenuNode::label)
                .filter(nhan -> nhan.startsWith(tienTo))
                .toList();
    }

    @Test
    @DisplayName("Kéo–thả đổi thứ tự ở quản trị thì cổng công khai đổi theo")
    void keoThaDoiThuTuThiCongDoiTheo() {
        MenuService.MenuNode a = menus.create(MenuPosition.HEADER, null, "TT-A", url("/tt-a"));
        MenuService.MenuNode b = menus.create(MenuPosition.HEADER, null, "TT-B", url("/tt-b"));
        MenuService.MenuNode c = menus.create(MenuPosition.HEADER, null, "TT-C", url("/tt-c"));

        // Thứ tự tạo là A, B, C ⇒ id tăng dần ⇒ path tăng dần. Đảo ngược là phép thử rẻ nhất
        // phân biệt được "sort_order có tác dụng" với "thứ tự là thứ tự id" — hai trạng thái mà
        // mọi khẳng định trước đây không tách được (quy tắc 9).
        menus.reorder(List.of(c.publicId(), b.publicId(), a.publicId()));

        assertThat(thuTuTrenCong("TT-"))
                .as("cổng phải trả theo sort_order vừa đặt, không theo thứ tự tạo bản ghi")
                .containsExactly("TT-C", "TT-B", "TT-A");
    }

    @Test
    @DisplayName("Mục con cũng theo sort_order, không theo thứ tự tạo")
    void mucConCungTheoSortOrder() {
        MenuService.MenuNode cha = menus.create(MenuPosition.HEADER, null, "TT-Cha", url("/tt-cha"));
        MenuService.MenuNode con1 = menus.create(MenuPosition.HEADER, cha.publicId(), "TT-Con1", url("/tt-c1"));
        MenuService.MenuNode con2 = menus.create(MenuPosition.HEADER, cha.publicId(), "TT-Con2", url("/tt-c2"));

        menus.reorder(List.of(con2.publicId(), con1.publicId()));

        assertThat(thuTuTrenCong("TT-"))
                .as("cha đứng đầu nhánh, hai con theo sort_order vừa đặt")
                .containsExactly("TT-Cha", "TT-Con2", "TT-Con1");
    }

    /**
     * Bất biến mà {@code buildMenuTree} ở public-web <b>dựa vào</b>: nó duyệt MỘT lượt, gặp con
     * trước cha thì con bị nâng lên cấp gốc và nhánh vỡ. Bản vá thứ tự anh em ⛔ không được làm
     * hỏng bất biến này — đây là chỗ dễ đánh đổi nhầm nhất.
     */
    @Test
    @DisplayName("Cha vẫn luôn đứng trước con, kể cả khi sort_order của cha lớn hơn")
    void chaVanLuonDungTruocCon() {
        MenuService.MenuNode cha = menus.create(MenuPosition.HEADER, null, "TT-Cha", url("/tt-cha"));
        MenuService.MenuNode con = menus.create(MenuPosition.HEADER, cha.publicId(), "TT-Con", url("/tt-con"));

        // Cha sort_order = 900, con = 0. Một phép sắp phẳng theo sort_order sẽ đảo ngược cặp này.
        jdbc.update("UPDATE menu_items SET sort_order = 900 WHERE public_id = ?", cha.publicId());
        jdbc.update("UPDATE menu_items SET sort_order = 0 WHERE public_id = ?", con.publicId());

        assertThat(thuTuTrenCong("TT-")).containsExactly("TT-Cha", "TT-Con");
    }
}
