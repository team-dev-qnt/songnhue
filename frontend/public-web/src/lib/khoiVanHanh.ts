import type { MenuLink, SiteConfig } from '@/lib/api';
import { menuHref, ROUTES } from '@/lib/routes';
import { docBool } from '@/lib/settings';

/**
 * Công tắc khối "Điều hành & số liệu công trình" — **nguồn duy nhất** của cả sáu bề mặt.
 *
 * <h2>Vì sao tệp này tồn tại</h2>
 *
 * Công tắc dựng ngày 01/09 (WS-39/T39.13) chỉ có **đúng một** nơi đọc: khối Nhóm 2 của trang chủ.
 * Tắt ở màn hình quản trị thì trang chủ sạch, nhưng mục menu vẫn nằm nguyên và bấm vào vẫn mở
 * được một trang rỗng. Đó là quy tắc 27 ở dạng nhìn thấy được — nửa vòng đọc–ghi.
 *
 * QuanTran yêu cầu 04/09: đồng bộ cả **mục menu** lẫn **chính trang**. Sáu bề mặt phải cùng đổi:
 *
 * <ol>
 *   <li>khối Nhóm 2 trang chủ;</li>
 *   <li>thanh điều hướng ({@code SiteHeader} → {@code PortalNav});</li>
 *   <li>chân trang ({@code SiteFooter});</li>
 *   <li>dải điều hướng trong mục ({@code SectionNav}, dùng ở cả bốn trang Quản lý vận hành);</li>
 *   <li>sidebar bài viết / danh mục / tìm kiếm ({@code PortalSidebar} — liên kết viết cứng);</li>
 *   <li>chính hai trang, phải trả 404.</li>
 * </ol>
 *
 * <h2>⭐ Bề mặt 2·3·4 được lọc ở CHỖ DỮ LIỆU ĐI QUA, không ở từng nơi gọi</h2>
 *
 * Cả ba đều tiêu thụ một hàm duy nhất: {@code getMenu()}. Nên bộ lọc nằm **bên trong** hàm ấy
 * (`lib/api.ts`), và mọi nơi gọi tự nhận cây đã lọc — kể cả nơi gọi ra đời sau (quy tắc 12).
 *
 * Đây không phải lo xa: T27.7 đã trả nợ xoá đệm cổng ở **ba** điểm ghi, rồi điểm ghi **thứ tư**
 * ra đời cùng đợt mang lại đúng lỗi cũ. Đếm tay các nơi gọi là việc sẽ sai ở lượt sau.
 *
 * <h2>⚠⚠ `config === null` ⇒ coi như BẬT — một fail-open CÓ TÊN</h2>
 *
 * {@code apiGet} nuốt lỗi và trả `null` (`api.ts:116-133`). Nghĩa là khi backend hỏng, công tắc
 * đọc ra mặc định `true`: menu **không** bị lọc *và* trang **không** 404. Hai lớp phòng thủ ở đây
 * hỏng **cùng lúc, cùng chiều, từ cùng một lượt gọi hỏng** — chúng là một lớp viết hai lần, không
 * phải hai lớp độc lập.
 *
 * <p>Đó vẫn là hành vi mong muốn (một lượt backend hắt hơi không được làm 404 hai trang thật), và
 * chính vì thế nó phải được **viết ra**: một mặc định không ai nói ra là một mặc định sẽ bị đọc
 * nhầm thành một bảo đảm (quy tắc 28).
 *
 * <h2>⚠ Một ngoại lệ đã có sẵn trong mã</h2>
 *
 * Giả định *"mọi menu đều đi qua `getMenu()`"* không đúng tuyệt đối: {@code SiteHeader} rơi về
 * {@code MENU_TOI_THIEU} khi API trả rỗng, và nhánh ấy **không** đi qua bộ lọc. Hôm nay vô hại
 * (danh sách dự phòng chỉ có "Trang chủ"), nhưng đó đúng là chỗ giả định này sẽ mục nếu ai đó
 * thêm mục vào danh sách dự phòng.
 */

/**
 * ⛔ Tên khoá giữ nguyên chữ {@code home} — **cố ý**, không phải sót.
 *
 * <p>Từ 04/09 công tắc này khoá cả hai trang **ngoài** trang chủ, nên cái tên hẹp hơn phạm vi.
 * Đổi tên vẫn là lựa chọn tệ hơn, vì hai lý do đo được:
 *
 * <ol>
 *   <li>{@code PortalSettingsReadTest} quét mọi khoá xuất hiện trong migration và đòi nó có nơi
 *       đọc ở cổng. Khoá cũ nằm trong khối {@code VALUES} của {@code V202609011051} — mà tệp
 *       migration đã merge thì **cấm sửa** (Flyway băm cả tệp, §10.65). Nên một lượt
 *       {@code UPDATE} tên khoá làm bài kiểm ấy đỏ vì một lý do sai;</li>
 *   <li>khoá này {@code exportable = TRUE}. Một bản xuất cấu hình lấy **trước** đợt này, nhập lại
 *       **sau**, sẽ rơi khoá cũ vào {@code skippedKeys} — {@code SettingService.importConfiguration}
 *       bỏ qua khoá lạ **trong im lặng**. Công tắc âm thầm về mặc định {@code true} và khối hiện
 *       lại, không một triệu chứng nào.</li>
 * </ol>
 *
 * <p>Thay vào đó {@code V202609041057} sửa {@code label} + {@code description} để mô tả nói đúng
 * phạm vi mới. ⛔ Đừng "dọn dẹp" cái tên này ở lượt sau mà không đọc lại hai điều trên.
 */
export const KHOA_HIEN_KHOI_VAN_HANH = 'site.home.show-dieu-hanh';

/**
 * Hai trang bị khoá theo công tắc — <b>đúng</b> hai trang mà khối Nhóm 2 trên trang chủ tóm tắt.
 *
 * <p>⛔ {@code danh-muc-cong-trinh} và {@code tien-do-san-xuat} **không** nằm ở đây: chúng có dữ
 * liệu thật và không thuộc khối Nhóm 2 (QuanTran chốt 04/09).
 *
 * <p>Danh sách này là bất biến được canh hai phía trong {@code khoiVanHanh.test.ts}: mỗi tuyến ở
 * đây phải có một trang gọi {@code notFound()}, và số trang gọi {@code notFound()} dưới
 * {@code app/quan-ly-van-hanh/} phải bằng đúng độ dài của nó.
 */
export const TUYEN_BI_KHOA: readonly string[] = [
  ROUTES.quanLyVanHanh.vanHanhCongTrinh,
  ROUTES.quanLyVanHanh.mucNuocLuongMua,
];

/**
 * Khối Vận hành có đang bật không.
 *
 * @param config cụm `settings` của cổng; {@code null} (backend hỏng) ⇒ **BẬT** — xem javadoc đầu
 *     tệp về fail-open có tên
 */
export function khoiVanHanhBat(config: SiteConfig | null | undefined): boolean {
  return docBool(config?.[KHOA_HIEN_KHOI_VAN_HANH], true);
}

/** Bỏ dấu `/` cuối để `/a/b` và `/a/b/` là cùng một tuyến. */
function chuanHoa(duongDan: string): string {
  return duongDan.length > 1 && duongDan.endsWith('/') ? duongDan.slice(0, -1) : duongDan;
}

const TUYEN_CHUAN = new Set(TUYEN_BI_KHOA.map(chuanHoa));

/** Mục menu này có trỏ vào một tuyến đang bị khoá không. */
function laMucBiKhoa(item: MenuLink): boolean {
  const href = menuHref(item);
  return href !== null && TUYEN_CHUAN.has(chuanHoa(href));
}

/**
 * Lọc danh sách menu PHẲNG theo công tắc.
 *
 * <p>⚠ Hai bước, và bước hai không được bỏ:
 *
 * <ol>
 *   <li>bỏ mục trỏ vào tuyến bị khoá;</li>
 *   <li>bỏ mục cha <b>vốn có con mà nay còn 0 con</b>.</li>
 * </ol>
 *
 * <p>Bước hai cần thiết vì mục cha "Quản lý, vận hành" là kiểu {@code NONE}: {@code PortalNav}
 * vẽ nó thành một {@code <button>} khi nó còn con, và một nút không có menu con là **một điều
 * khiển bày ra mà không ai đọc được thao tác của nó** — đúng lỗi đã trả giá ở WS-25. Hôm nay
 * nhánh ấy còn 2 trong 4 con nên chuyện không xảy ra; đó là an toàn do **biên độ**, không do
 * thiết kế, nên bước hai vẫn phải có và vẫn phải có bài kiểm.
 *
 * <p>⛔ So theo <b>đường dẫn</b> qua {@link menuHref}, không so theo nhãn: nhãn là thứ Công ty sửa
 * được bất cứ lúc nào, còn đường dẫn bị {@code PortalTaxonomyTest} canh cho khớp với
 * {@code menu_items.url} trong migration. Và ⛔ không lọc theo {@code parentLabel} ở bước một:
 * {@code buildMenuTree} **nâng mục con mất cha lên cấp gốc** thay vì bỏ, nên bỏ nhầm mục cha sẽ
 * làm bốn mục con nhảy lên hàng cấp 1 — một cách hỏng trông như một cách hiển thị.
 *
 * @param bat {@code true} ⇒ trả về nguyên danh sách, không sao chép thừa
 */
export function locMenuTheoCongTac(items: MenuLink[], bat: boolean): MenuLink[] {
  if (bat) {
    return items;
  }

  const conLai = items.filter((item) => !laMucBiKhoa(item));

  const nhanCha = (danhSach: MenuLink[]) =>
    new Set(
      danhSach.map((item) => item.parentLabel).filter((nhan): nhan is string => nhan !== null),
    );
  const vonCoCon = nhanCha(items);
  const conCon = nhanCha(conLai);

  return conLai.filter((item) => {
    // Mục con thì bước hai không đụng tới.
    if (item.parentLabel !== null) {
      return true;
    }
    // Mục cấp 1 vốn không có con (một liên kết thường) — giữ nguyên.
    if (!vonCoCon.has(item.label)) {
      return true;
    }
    // Vốn có con: chỉ giữ khi còn ít nhất một con sống sót.
    return conCon.has(item.label);
  });
}
