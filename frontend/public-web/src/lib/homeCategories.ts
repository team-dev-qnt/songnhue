import type { MenuLink } from './api';

/** Một ô chuyên mục ở hàng dưới slider: nhãn lấy từ MENU, bài lấy theo `slug`. */
export interface KhoiChuyenMuc {
  /** Nhãn hiển thị — **nhãn mục menu**, không phải tên bản ghi danh mục. Xem ghi chú dưới. */
  label: string;
  slug: string;
}

/**
 * Chọn các chuyên mục con dựng thành hàng chuyên mục của trang chủ — bố cục 29/08/2026.
 *
 * <h2>Cấu trúc đến từ MENU, không từ cây `categories`</h2>
 *
 * Hai nguồn cùng mô tả "có những chuyên mục nào": bảng `categories` và menu đầu trang. Chúng
 * KHÔNG trùng nhau — `pctt` là con của `tin-tuc` trong `categories` nhưng không có mặt trên
 * menu, tức Công ty đã quyết định nó không nằm trong điều hướng. Đọc `categories` thì trang chủ
 * tự ý bày ra một mục mà thanh menu đã bỏ, và không ai biết vì sao (§2: *"menu chính, footer,
 * các card chuyên mục và cây nội dung phải dùng CHUNG một hệ phân loại"*).
 *
 * <p>Nhãn cũng lấy từ menu vì cùng lý do: đổi tên mục menu mà tiêu đề khối trang chủ vẫn tên cũ
 * là hai cái tên cho một nhánh (luật 14). Cái giá phải trả là nhãn menu có thể khác `name` của
 * bản ghi danh mục — nhưng khi ấy nhãn menu mới là thứ người dùng vừa đọc ở thanh điều hướng.
 *
 * <h2>⛔ Không có mục menu ⇒ không có khối</h2>
 *
 * Trả mảng rỗng khi nhánh cấu hình ở `site.home.news-category` không có mục menu nào trỏ vào.
 * Đó là chính sách đã ghi ở `page.tsx`: <i>bố cục trang chủ LÀ cây nội dung Công ty đã duyệt;
 * muốn bớt một khối thì bỏ mục tương ứng khỏi menu</i>. Rơi về một danh sách mặc định ở đây là
 * dựng lại đúng cái bẫy §10.54 dưới dạng cấu trúc thay vì dữ liệu.
 *
 * @param menuTree cây menu HEADER đã dựng bằng `buildMenuTree`
 * @param slugCha slug nhánh cha — `site.home.news-category`
 */
export function chonKhoiChuyenMuc(
  menuTree: { item: MenuLink; children: MenuLink[] }[],
  slugCha: string,
): KhoiChuyenMuc[] {
  if (!slugCha) {
    return [];
  }
  const cha = menuTree.find(
    (n) => n.item.linkType === 'CATEGORY' && n.item.categorySlug === slugCha,
  );
  if (!cha) {
    return [];
  }
  return cha.children.flatMap((con) =>
    // Mục con kiểu URL / ARTICLE / NONE không phải một chuyên mục — nó không có bài để liệt kê.
    // Lọc theo `categorySlug` chứ không chỉ theo `linkType`: một mục CATEGORY mà slug rỗng là
    // dữ liệu hỏng, và dựng khối cho nó là gọi API với `category=` rỗng ⇒ trả về TOÀN BỘ bài
    // của cổng dưới một cái tên chuyên mục. Sai lặng lẽ và trông rất giống đúng.
    con.linkType === 'CATEGORY' && con.categorySlug
      ? [{ label: con.label, slug: con.categorySlug }]
      : [],
  );
}

/**
 * Nhãn của chính nhánh cha — tiêu đề cột "Tin tức – Sự kiện" cạnh slider.
 *
 * @returns `null` khi menu không có mục nào trỏ vào nhánh ấy; nơi gọi bỏ hẳn khối thay vì
 *   dựng một tiêu đề viết cứng.
 */
export function nhanNhanhTin(
  menuTree: { item: MenuLink; children: MenuLink[] }[],
  slugCha: string,
): string | null {
  if (!slugCha) {
    return null;
  }
  const cha = menuTree.find(
    (n) => n.item.linkType === 'CATEGORY' && n.item.categorySlug === slugCha,
  );
  return cha ? cha.item.label : null;
}
