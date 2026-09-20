import type { SiteConfig } from '@/lib/api';

/**
 * Màu nhận diện do Công ty đặt trên màn hình quản trị → biến CSS của cổng công khai (T75.7).
 *
 * <h2>Vì sao cơ chế này tồn tại, và vì sao nó từng bị GỠ</h2>
 *
 * `site.color.primary` / `site.color.secondary` seed ngày 19/08/2026, bày ra trên *Cấu hình hệ
 * thống* suốt 9 ngày, và **0 nơi đọc**. Quản trị viên đặt màu, hệ báo *lưu thành công*, cổng ⛔ đổi
 * một pixel nào — `V202608281037` gỡ cả hai theo quy tắc 15 (*công tắc chưa ai đọc là một lỗi*).
 *
 * Lượt này dựng lại **kèm đường đọc**, và đường đọc ấy là tệp này. Bộ canh
 * `PortalSettingsReadTest.moiKhoaDeuCoNguoiDoc` ⛔ cho tái diễn: khoá `site.*` nào còn sống mà ⛔ ai
 * đọc thì CI đỏ, gọi đích danh khoá đó.
 *
 * <h2>Vì sao là biến CSS chứ ⛔ phải một lượt build</h2>
 *
 * Tailwind nướng mã hex vào CSS **lúc build**, nên `bg-brand-primary` là một hằng số trong bundle.
 * Nướng màu lúc build còn dựng lại đúng bẫy `SITE_URL` của `T68.12`: hai môi trường dùng chung một
 * ảnh Docker thì chúng buộc phải mang chung giá trị đã nướng. `public-web/tailwind.config.ts` vì
 * thế khai `var(--sn-brand-primary, <token>)`, và tệp này sinh phần `<style>` ghi đè.
 *
 * <h2>Hợp đồng — ba trạng thái, ⛔ phải hai</h2>
 *
 * <ul>
 *   <li><b>Khoá để trống / thiếu</b> ⇒ ⛔ phát biến nào ⇒ trình duyệt rơi về token. Đây là trạng
 *       thái MẶC ĐỊNH và nó đúng — ⛔ phải một lỗi cần che.</li>
 *   <li><b>Khoá có mã màu hợp lệ</b> ⇒ phát biến, ghi đè.</li>
 *   <li><b>Khoá có giá trị RÁC</b> ⇒ bỏ qua đúng khoá ấy. Backend đã chặn bằng `value_type = COLOR`
 *       (`SettingValidator`), nhưng một giá trị rác lọt tới đây thì thà mất một lượt ghi đè còn hơn
 *       **tiêm chuỗi tuỳ ý vào thẻ `<style>`** — đó là một đường XSS, và cổng công khai là nơi ⛔
 *       được phép có. Bộ lọc ở đây là chốt chặn thứ hai, ⛔ phải bản sao của chốt thứ nhất
 *       (quy tắc 12: đặt bảo đảm ở chỗ dữ liệu ĐI QUA).</li>
 * </ul>
 *
 * <h2>⚠ Từ 20/09: hai khoá nền khung cổng, và ở đó giá trị rác hỏng NẶNG hơn</h2>
 *
 * `site.brand.header` · `site.brand.footer` điều khiển nền đầu trang và chân trang. Khác hai khoá
 * trên ở một điểm phải nhớ: chúng chảy vào **chặng gradient** của Tailwind, mà `--tw-gradient-from`
 * là custom property **đã đăng ký** (`@property … syntax: "&lt;color&gt;"; initial-value: #0000`).
 * Với một property đã đăng ký, một giá trị sai kiểu ⛔ rơi về `var()` fallback — nó rơi về
 * **initial-value, tức TRONG SUỐT**. Nên ở hai khoá này, một chuỗi rác lọt qua ⛔ cho ra *"màu
 * lạ"* mà cho ra *"⛔ còn đầu trang"*. Bộ lọc dưới đây vì thế là chốt chặn thật, ⛔ phải lớp sơn.
 */

/**
 * Khoá `settings` ↔ tên biến CSS. Tên biến khớp tên token của `design-tokens` (quy tắc 14).
 *
 * <p>Bốn vai trò, ⛔ phải bốn *màu*: hai cái đầu là bộ nhận diện (`brandColors`), hai cái sau là
 * **nền của một vùng khung cổng** (`portalChrome`). Thêm một dòng vào đây là thêm một núm cho
 * Công ty xoay — nó phải kèm một chỗ đọc thật, nếu ⛔ thì đó lại là `site.color.*` lần hai.
 */
export const ANH_XA_MAU: ReadonlyArray<readonly [khoa: string, bien: string]> = [
  ['site.brand.primary', '--sn-brand-primary'],
  ['site.brand.accent', '--sn-brand-accent'],
  ['site.brand.header', '--sn-brand-header'],
  ['site.brand.footer', '--sn-brand-footer'],
] as const;

/**
 * Chỉ nhận đúng `#` + 6 chữ số hex.
 *
 * ⚠ ⛔ nhận dạng 3 ký tự (`#fff`) dù CSS hiểu: ô nhập ở admin mô tả *"6 chữ số"*, và nhận thêm một
 * dạng nghĩa là hai bài kiểm phải nhớ hai dạng. ⛔ nhận `rgb()`, `hsl()`, tên màu — chúng mở cửa
 * cho dấu `;`, `}` và `/*`, tức mở cửa cho việc thoát khỏi khai báo và viết thêm luật CSS.
 */
const MA_MAU = /^#[0-9a-fA-F]{6}$/;

export function laMaMauHopLe(gia: string | undefined | null): boolean {
  return typeof gia === 'string' && MA_MAU.test(gia.trim());
}

/**
 * Dựng nội dung `<style>` ghi đè màu nhận diện — chuỗi RỖNG khi ⛔ có gì để ghi đè.
 *
 * <p>Chuỗi rỗng là một câu trả lời có nghĩa: nơi gọi ⛔ dựng thẻ `<style>` nào cả, nên trang mặc
 * định ⛔ mang thêm một byte. ⛔ trả `':root{}'` — một thẻ rỗng đọc như *"đã có ghi đè"* với người
 * mở DevTools đi tìm xem màu tới từ đâu.
 */
export function cssMauThuongHieu(config: SiteConfig | null | undefined): string {
  if (!config) return '';

  const khaiBao = ANH_XA_MAU.filter(([khoa]) => laMaMauHopLe(config[khoa])).map(
    ([khoa, bien]) => `${bien}:${config[khoa].trim().toLowerCase()}`,
  );

  return khaiBao.length === 0 ? '' : `:root{${khaiBao.join(';')}}`;
}
