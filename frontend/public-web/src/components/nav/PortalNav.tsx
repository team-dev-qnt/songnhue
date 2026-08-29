'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useId, useRef, useState } from 'react';

import type { MenuLink } from '@/lib/api';
import { isExternal, menuHref } from '@/lib/routes';
import { vuaThanhNgang } from '@/lib/vuaThanhNgang';

/**
 * Kiểu chữ và đệm của mục cấp 1 — khai **một lần**, dùng ở cả thanh thật lẫn thước đo.
 *
 * ⛔ Đây không phải gom code cho gọn. Thước đo quyết định thanh ngang có hiện hay không; nếu nó
 *    vẽ bằng cỡ chữ khác thanh thật thì phép đo trả lời cho một thanh KHÔNG TỒN TẠI, và sai số
 *    ấy im lặng — thanh vẫn hiện, chỉ là tràn. Đúng hình dạng luật 14 (hai nơi phải nhớ giống
 *    nhau), nên chặn bằng cấu trúc chứ không bằng lời dặn.
 */
const LOP_CHU_CAP1 = 'text-[12px] font-semibold';

/**
 * Đệm, khoảng cách **và chữ hoa** của MỘT mục cấp 1 — cùng lý do trên, cộng một lý do nữa.
 *
 * ⛔⛔ {@code uppercase} phải nằm ở ĐÂY, tuyệt đối không đặt lên {@code <ul>} rồi trông chờ kế thừa.
 *
 * Mục {@code linkType='NONE'} vẽ ra {@code <button>} chứ không phải {@code <a>} — chúng chỉ để mở
 * menu con. UA stylesheet của trình duyệt khai thẳng {@code text-transform: none} trên
 * {@code button}, và <b>một khai báo trên chính phần tử luôn thắng giá trị kế thừa</b>, kể cả khai
 * báo của trình duyệt. Preflight của Tailwind v4 có reset {@code font}, {@code letter-spacing},
 * {@code color} cho form control đúng vì lý do này, nhưng <b>không</b> reset
 * {@code text-transform}.
 *
 * <p>Hậu quả đo được ngày 28/08: sáu mục viết hoa, còn <i>"Giới thiệu"</i> và
 * <i>"Quản lý, vận hành"</i> thì không — <b>đúng bằng tập hai mục {@code NONE}</b>, không mục nào
 * khác. Trông như lỗi dữ liệu, thật ra là một luật CSS.
 */
const LOP_MUC_CAP1 = 'flex items-center gap-1 whitespace-nowrap rounded-md px-2 py-3 uppercase';

export interface NhanhMenu {
  item: MenuLink;
  children: MenuLink[];
}

interface PortalNavProps {
  /** Cây menu HEADER đã dựng ở phía máy chủ — cùng một nguồn với card chuyên mục và chân trang. */
  tree: NhanhMenu[];
}

/**
 * Thanh điều hướng chính của cổng — bản dựng lại 28/08/2026.
 *
 * <h2>⚠⚠ Vì sao phải dựng lại: thanh cũ TRÀN khung trên mọi màn hình</h2>
 *
 * Cây nội dung §3 có <b>tám</b> mục cấp 1, nhãn tiếng Việt dài ("Hoạt động Đảng, đoàn thể" 24 ký
 * tự). Bản cũ vẽ chúng bằng {@code text-[13px] font-bold uppercase tracking-wider} với
 * {@code px-3.5}. Đo ra:
 *
 * <pre>
 *   menu 1344px + nút Tìm kiếm 110px = 1454px
 *   khung chứa                        = 1232 − 48 = 1184px      → tràn 22%
 * </pre>
 *
 * {@code flex-wrap} nên nó không vỡ bố cục — nó <b>xuống dòng</b>, và thanh điều hướng cao gấp
 * đôi ở <i>mọi</i> bề rộng, kể cả desktop rộng nhất. Trên điện thoại thì tám mục viết hoa xếp
 * thành một mảng chữ chiếm gần hết màn hình đầu tiên. §10 của văn bản nghiệm thu có đúng một
 * dòng cho việc này: <i>"Giao diện hiển thị đúng trên máy tính, máy tính bảng và điện thoại"</i>.
 *
 * <h2>Ba thay đổi, và mỗi thay đổi đổi lấy cái gì</h2>
 *
 * <ol>
 *   <li><b>Bỏ {@code uppercase} + {@code tracking-wider} ở cấp 1</b> — thu 1344px → ~1082px.
 *       Không chỉ để vừa: chữ hoa tiếng Việt chồng dấu ("ĐOÀN THỂ", "HOẠT ĐỘNG") làm dấu thanh
 *       dính vào nhau và khó đọc hơn hẳn chữ thường. Menu con vẫn giữ chữ thường như cũ.
 *       <b>⚠ Vế chữ hoa đã ĐẢO NGƯỢC ngày 28/08 theo yêu cầu Công ty — xem mục kế tiếp; vế
 *       {@code tracking-wider} thì KHÔNG, và lý do là ngân sách bề rộng ở dưới.</b>
 *   <li><b>Không vừa thì chuyển sang ngăn kéo</b> — một nút, và toàn bộ cây mở ra theo chiều
 *       dọc, nơi nhãn dài không phải cạnh tranh bề ngang với nhau. (Bản đầu chốt ở ngưỡng
 *       {@code lg}; từ 28/08 nó ĐO — xem mục "Ngưỡng là phép đo" bên dưới.)
 *   <li><b>Menu con mở được bằng CHẠM và bàn phím</b>, không chỉ bằng rê chuột — xem dưới.
 * </ol>
 *
 * <h2>⭐ 28/08: chữ hoa trở lại ở cấp 1 — và bề rộng phải mua lại bằng cái khác</h2>
 *
 * Công ty yêu cầu <b>toàn bộ mục cấp 1 viết hoa</b>. Thêm {@code uppercase} vào bản đang chạy mà
 * không đổi gì khác thì <b>tràn khung trở lại</b> — đúng lỗi §10.62 vừa sửa xong. Đo bằng chính
 * font đang dùng (Noto Sans 600, {@code @fontsource}), trên tám nhãn thật lấy từ API menu:
 *
 * <pre>
 *   khung chứa (max-w-1232 − px-6×2)                        = 1184px
 *
 *   thường 13px px-3   (bản 28/08 sáng)   1173,0px   dư  19,0
 *   HOA    13px px-3   (thêm mỗi uppercase) 1297,5px  TRÀN 105,5   ← §10.62 tái phát
 *   HOA    13px px-2                      1225,5px   TRÀN  33,5
 *   HOA    12px px-2.5                    1186,6px   dư   5,4     ← sát mép, không nhận
 *   HOA    12px px-2                      1150,6px   dư  41,4     ← ĐANG DÙNG
 * </pre>
 *
 * Chữ hoa tiếng Việt rộng hơn chữ thường <b>15,7%</b> (đo được, không ước lượng). Nên cỡ chữ
 * xuống 12px và đệm ngang {@code px-3 → px-2}. Kết quả còn <b>nhiều headroom hơn</b> bản chữ
 * thường trước đó (41,4px so với 19,0px).
 *
 * <p>⛔ <b>KHÔNG thêm lại {@code tracking-wider}</b> dù nó vốn đi cùng chữ hoa: nó ngốn thêm
 * ~38px và đẩy thanh về sát mép. Cần thoáng hơn thì nới {@code py}, đừng nới {@code tracking}.
 *
 * <p>⭐⭐ <b>29/08: menu con CŨNG viết hoa</b> — Công ty yêu cầu, và nó đảo lại lựa chọn ghi ở
 * đoạn trên (giữ nguyên đoạn ấy để đọc được vì sao từng chọn khác).
 *
 * <p>⛔ Vì sao đặt ở CSS chứ không viết hoa vào nhãn trong CSDL: nhãn menu do Công ty nhập từ màn
 * hình quản trị. Đặt ở dữ liệu thì hôm nay 12 mục con đúng, còn mục thứ 13 ai đó thêm vào tuần
 * sau sẽ chữ thường — một quy ước phụ thuộc trí nhớ con người, và <b>không cổng kiểm nào bắt
 * được</b> vì nó nằm trong CSDL. Đặt ở đây thì mọi mục mới tự có, kể cả mục chưa ai nhập.
 *
 * <p>Đây cũng là lý do bản vá ngày 29/08 <b>gỡ</b> migration đổi nhãn thành chữ hoa: hai cơ chế
 * cùng sinh ra chữ hoa (CSS cho cấp 1, dữ liệu cho một mục cấp 2) chính là thứ tạo ra cảnh
 * <i>một mục hoa, mười một mục thường</i> mà QuanTran chỉ ra.
 *
 * <p>⚠ Bề rộng đã đo, không ước lượng: menu con là danh sách dọc {@code min-w-64} (256px). Nhãn
 * dài nhất "HOẠT ĐỘNG ĐẢNG, ĐOÀN THỂ" ở 13px chiếm ~204px + đệm 32px = 236px, còn dư 20px. Và
 * {@code min-w} chứ không phải bề rộng cố định, nên nhãn dài hơn làm khối rộng ra chứ không tràn.
 *
 * <h2>⭐⭐ Ngưỡng là một PHÉP ĐO, không phải một con số chốt sẵn</h2>
 *
 * Bảng trên là ngân sách của <b>bộ nhãn hôm nay</b>. Nhãn menu nằm trong CSDL và Công ty sửa được
 * từ màn hình quản trị, nên mọi con số cân sẵn đều có hạn dùng: thêm mục cấp 1 thứ chín, hay đổi
 * một nhãn dài hơn "Hoạt động Đảng, đoàn thể", là tràn lại — và <b>không cổng kiểm nào đỏ được</b>,
 * vì một bài kiểm tĩnh không đọc được CSDL.
 *
 * <p>Nên từ 28/08 ngưỡng không còn là {@code lg}: một {@code ResizeObserver} đo <b>thước đo</b>
 * (bản vô hình của tám mục, vẽ bằng đúng {@code LOP_CHU_CAP1} và {@code LOP_MUC_CAP1} của thanh
 * thật) rồi so với chỗ trống còn lại sau nút Tìm kiếm. Không vừa ⇒ rơi về ngăn kéo. Ràng buộc tự
 * đúng với mọi bộ nhãn, kể cả bộ chưa ai nhập.
 *
 * <p>⚠ Trước lượt đo đầu tiên — SSR, khung hình đầu, hoặc trình duyệt không chạy JS —
 * {@code vuaKhung} là {@code null} và component giữ nguyên ngưỡng {@code lg} cũ làm <b>đường
 * lui</b>. Không có JS thì cổng vẫn dùng được đúng như trước, chỉ mất phần tự rơi về ngăn kéo.
 *
 * <p>⚠ Giới hạn, nói ra thay vì để người đọc tự suy (luật 28): phép đo trả lời <i>"tám nhãn này có
 * vừa không"</i>, <b>không</b> trả lời <i>"thanh có đẹp không"</i>. Vừa sát mép vẫn là vừa. Và nó
 * không biết gì về menu con — menu con bung ra bên dưới nên bề rộng của chúng chưa bao giờ là
 * ràng buộc của thanh.
 *
 * ⛔ Hệ màu, kiểu khối và cách trình bày <b>giữ nguyên</b> (§2 của văn bản nghiệm thu):
 * cùng dải navy, cùng chiều cao, cùng vàng kim khi rê chuột. Bảy mã màu ghi cứng nay đọc từ
 * {@code design-tokens} ({@code chrome.navy*}) — cùng giá trị đang hiện, chỉ khác nơi khai.
 *
 * <h2>Vì sao là client component, trong khi phần còn lại của đầu trang không phải</h2>
 *
 * Ba việc dưới đây <b>không</b> làm được ở phía máy chủ, và cả ba đều là thứ người dùng thật
 * chạm vào: trạng thái đóng/mở của ngăn kéo, mục nào đang là trang hiện tại
 * ({@code usePathname}), và menu con mở bằng chạm. Dữ liệu menu vẫn lấy ở
 * {@code SiteHeader} phía máy chủ rồi truyền xuống — không có lượt gọi API nào từ trình duyệt.
 *
 * <h2>Menu con trên desktop: rê chuột KHÔNG đủ, và đây là chỗ đã suýt sai</h2>
 *
 * Bản cũ mở menu con thuần bằng {@code group-hover}. Trên máy tính bảng không có con trỏ:
 * chạm vào "Giới thiệu" (mục {@code NONE}, không đường dẫn riêng) là chạm vào một
 * {@code <button>} <b>không làm gì cả</b> — bốn mục con của nó không có cách nào mở ra. Máy
 * tính bảng nằm ngay trong câu §10 vừa dẫn.
 *
 * <p>Nay {@code group-hover} vẫn còn (chuột giữ nguyên trải nghiệm cũ) nhưng trạng thái mở
 * <i>cũng</i> điều khiển được bằng bấm và bằng bàn phím, và {@code aria-expanded} nói đúng
 * trạng thái ấy cho trình đọc màn hình.
 */
export function PortalNav({ tree }: PortalNavProps) {
  const [moNganKeo, datMoNganKeo] = useState(false);
  // Nhãn của mục cấp 1 đang mở menu con trên desktop. `null` = không mục nào.
  const [moCap1, datMoCap1] = useState<string | null>(null);
  const duongDan = usePathname();
  const idNganKeo = useId();
  const vungNavRef = useRef<HTMLElement>(null);
  const khungRef = useRef<HTMLDivElement>(null);
  const thuocDoRef = useRef<HTMLUListElement>(null);
  const timKiemRef = useRef<HTMLAnchorElement>(null);

  /**
   * Thanh ngang có VỪA khung không — `null` nghĩa là chưa đo lần nào.
   *
   * <h2>Vì sao đo, thay vì chọn một ngưỡng `lg`</h2>
   *
   * Ngưỡng theo bề rộng màn hình trả lời sai câu hỏi. Câu hỏi thật là *"tám nhãn này, ở cỡ chữ
   * này, có vừa khung không"* — mà nhãn nằm trong CSDL và Công ty sửa được từ màn hình quản trị.
   * Bản trước chốt `lg` (1024px) rồi cân cỡ chữ cho vừa **đúng bộ nhãn hôm nay**: thêm mục cấp 1
   * thứ chín, hay đổi một nhãn dài hơn, là tràn lại — và **không cổng kiểm nào đỏ**, vì bài kiểm
   * tĩnh không đọc được CSDL.
   *
   * <p>Đo thì ràng buộc tự đúng mãi: không vừa ⇒ rơi về ngăn kéo, nơi nhãn dài xếp dọc và không
   * phải cạnh tranh bề ngang với nhau.
   */
  const [vuaKhung, datVuaKhung] = useState<boolean | null>(null);

  useEffect(() => {
    const khung = khungRef.current;
    const thuoc = thuocDoRef.current;
    if (!khung || !thuoc) return;

    // ⚠ Đặt `setState` trong callback của ResizeObserver, KHÔNG trong thân effect:
    //   `react-hooks/set-state-in-effect` chặn cách kia, và nó chặn đúng — gọi thẳng sinh ra một
    //   lượt vẽ lại nối đuôi. ResizeObserver bắn ngay lần `observe` đầu tiên nên phép đo mở màn
    //   vẫn có, mà không cần gọi tay.
    const doLai = () => {
      // ⚠⚠ 29/08: ô Tìm kiếm đã chuyển lên dải nhận diện, nên `timKiemRef` nay LUÔN null.
      //    Bản trước `return` khi không tìm thấy nó — giữ nguyên dòng ấy là phép đo im lặng
      //    ngừng chạy, `vuaKhung` kẹt ở `null` vĩnh viễn, và thanh điều hướng không bao giờ
      //    rơi về ngăn kéo nữa. Không lỗi nào, không cổng kiểm nào đỏ. Đúng hình dạng "cơ chế
      //    canh gác tồn tại mà không có hiệu lực".
      //
      //    Ref được giữ lại có chủ đích: chỗ bên phải thanh còn có thể có mục khác về sau, và
      //    lúc ấy phép đo phải tính nó. Không có gì ở đó ⇒ 0, không phải "bỏ đo".
      const rongTim = timKiemRef.current?.offsetWidth ?? 0;
      const kieu = getComputedStyle(khung);
      const vua = vuaThanhNgang({
        trong: khung.clientWidth - parseFloat(kieu.paddingLeft) - parseFloat(kieu.paddingRight),
        thuoc: thuoc.scrollWidth,
        tim: rongTim,
      });
      // `null` = chưa kết luận được (khung bề rộng 0: tab chạy nền, lượt vẽ để in). GIỮ NGUYÊN
      // kết luận cũ — xem lý do ở `vuaThanhNgang`.
      if (vua === null) return;
      datVuaKhung((cu) => (cu === vua ? cu : vua));
      // ⚠ Bắt buộc: hiệu ứng khoá cuộn nền bám theo `moNganKeo`. Nếu ngăn kéo đang mở mà thanh
      //   ngang vừa khung trở lại (xoay ngang máy tính bảng), ngăn kéo bị ẩn bằng CSS nhưng
      //   `moNganKeo` vẫn `true` — và trang đứng im, cuộn không được, không dấu vết nào.
      if (vua) datMoNganKeo(false);
    };

    const bd = new ResizeObserver(doLai);
    bd.observe(khung);
    // Quan sát cả thước đo: đổi nhãn menu làm nó rộng ra trong khi khung không đổi kích thước.
    bd.observe(thuoc);
    return () => bd.disconnect();
  }, []);

  // `null` = chưa đo (SSR, lượt vẽ đầu, hoặc không có JS) → giữ nguyên ngưỡng `lg` cũ làm đường
  // lui. Không có JS thì cổng vẫn dùng được đúng như trước, chỉ mất phần tự rơi về ngăn kéo.
  const chuaDo = vuaKhung === null;
  const lopThanhNgang = chuaDo ? 'hidden lg:flex' : vuaKhung ? 'flex' : 'hidden';
  const lopNutNganKeo = chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'flex';
  const lopVungNganKeo = chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'block';

  /**
   * Đóng mọi thứ đang mở — gọi từ **trình xử lý sự kiện** của từng liên kết.
   *
   * ⚠ Bản đầu làm việc này bằng `useEffect` theo `duongDan`. Hai lý do phải đổi, và lý do thứ hai
   * mới là lý do đúng:
   *
   * 1. `react-hooks/set-state-in-effect` chặn ở ESLint — gọi `setState` thẳng trong thân effect
   *    sinh ra một lượt vẽ lại nối đuôi (cùng luật đã bắt `RealtimeFrame` ở đợt trước);
   * 2. bám theo đường dẫn thì bấm một liên kết trỏ về **chính trang đang xem** sẽ không đóng ngăn
   *    kéo — đường dẫn không đổi nên effect không chạy. Người dùng bấm, không thấy gì xảy ra, và
   *    bấm tiếp. Trình xử lý sự kiện không có lỗ ấy: nó chạy vì có người bấm, không vì trạng thái
   *    nào đó tình cờ đổi.
   */
  const dongHet = useCallback(() => {
    datMoNganKeo(false);
    datMoCap1(null);
  }, []);

  // Esc đóng — lối thoát bắt buộc cho người dùng bàn phím đang kẹt trong menu con.
  useEffect(() => {
    if (!moNganKeo && moCap1 === null) return;
    const xuLy = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        datMoNganKeo(false);
        datMoCap1(null);
      }
    };
    document.addEventListener('keydown', xuLy);
    return () => document.removeEventListener('keydown', xuLy);
  }, [moNganKeo, moCap1]);

  // Bấm ra ngoài thanh điều hướng thì đóng menu con đang mở.
  useEffect(() => {
    if (moCap1 === null) return;
    const xuLy = (e: PointerEvent) => {
      if (!vungNavRef.current?.contains(e.target as Node)) datMoCap1(null);
    };
    document.addEventListener('pointerdown', xuLy);
    return () => document.removeEventListener('pointerdown', xuLy);
  }, [moCap1]);

  // Khoá cuộn nền khi ngăn kéo mở: nếu không, vuốt trong ngăn kéo sẽ cuộn trang phía sau.
  useEffect(() => {
    if (!moNganKeo) return;
    const cu = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = cu;
    };
  }, [moNganKeo]);

  const dangO = useCallback(
    (nhanh: NhanhMenu) => {
      const dich = [menuHref(nhanh.item), ...nhanh.children.map(menuHref)].filter(
        (h): h is string => Boolean(h) && h!.startsWith('/'),
      );
      // Trang chủ phải khớp tuyệt đối; mọi mục khác khớp cả nhánh con để trang bài viết bên
      // trong một chuyên mục vẫn làm sáng mục cha của nó.
      return dich.some((h) => (h === '/' ? duongDan === '/' : duongDan.startsWith(h)));
    },
    [duongDan],
  );

  return (
    <nav
      ref={vungNavRef}
      aria-label="Điều hướng chính"
      className="sticky top-0 z-40 w-full border-b-2 border-brand-primary bg-white text-surface-textBase shadow-sm"
    >
      <div
        ref={khungRef}
        className="mx-auto flex max-w-[1232px] items-center justify-between gap-2 px-4 sm:px-6"
      >
        {/* ───── Nút ngăn kéo — chỉ dưới lg ───── */}
        <button
          type="button"
          onClick={() => datMoNganKeo((cu) => !cu)}
          aria-expanded={moNganKeo}
          aria-controls={idNganKeo}
          className={`items-center gap-2 py-3 pr-2 text-sm font-semibold text-surface-textBase ${lopNutNganKeo}`}
        >
          <span className="relative flex h-5 w-5 items-center justify-center">
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-transform duration-200 ${
                moNganKeo ? 'rotate-45' : '-translate-y-1.5'
              }`}
            />
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-opacity duration-200 ${
                moNganKeo ? 'opacity-0' : 'opacity-100'
              }`}
            />
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-transform duration-200 ${
                moNganKeo ? '-rotate-45' : 'translate-y-1.5'
              }`}
            />
          </span>
          <span>{moNganKeo ? 'Đóng' : 'Danh mục'}</span>
        </button>

        {/* ───── Thanh ngang — từ lg trở lên ───── */}
        <ul className={`flex-1 items-center gap-0.5 ${LOP_CHU_CAP1} ${lopThanhNgang}`}>
          {tree.map((nhanh) => (
            <MucCap1
              key={`${nhanh.item.label}-${nhanh.item.depth}`}
              nhanh={nhanh}
              dangO={dangO(nhanh)}
              dangMo={moCap1 === nhanh.item.label}
              doiMo={() => datMoCap1((cu) => (cu === nhanh.item.label ? null : nhanh.item.label))}
              dong={() => datMoCap1(null)}
              khiDieuHuong={dongHet}
            />
          ))}
        </ul>

        {/* ───── Thước đo — quyết định thanh ngang có hiện hay không ─────
            Vẽ đúng tám nhãn ấy bằng ĐÚNG hằng số kiểu chữ của thanh thật, ngoài luồng bố cục
            (`absolute`) và không nhìn thấy được, chỉ để đọc `scrollWidth`.

            ⚠ `w-max`: thước phải lấy bề rộng TỰ NHIÊN. Không có nó, bọc `overflow-hidden` bên
              ngoài sẽ ép các mục co lại và thước đo một thanh hẹp hơn thanh thật — tức luôn kết
              luận "vừa", tức bộ đo trở thành trang trí.
            ⚠ `h-0 overflow-hidden` ở lớp bọc: thước rộng hơn màn hình, để tràn tự do sẽ sinh
              thanh cuộn ngang cho cả trang. */}
        <div
          aria-hidden
          className="pointer-events-none invisible absolute left-0 top-0 h-0 overflow-hidden"
        >
          <ul ref={thuocDoRef} className={`flex w-max items-center gap-0.5 ${LOP_CHU_CAP1}`}>
            {tree.map((nhanh) => (
              <li key={`thuoc-${nhanh.item.label}-${nhanh.item.depth}`} className={LOP_MUC_CAP1}>
                <span>{nhanh.item.label}</span>
                {nhanh.children.length > 0 ? <MuiTen /> : null}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* ───── Ngăn kéo — dưới lg ───── */}
      <div
        id={idNganKeo}
        hidden={!moNganKeo}
        className={`max-h-[calc(100vh-3.5rem)] overflow-y-auto border-t border-surface-border bg-white ${lopVungNganKeo}`}
      >
        <ul className="px-2 py-2">
          {tree.map((nhanh) => (
            <MucNganKeo
              key={`${nhanh.item.label}-${nhanh.item.depth}`}
              nhanh={nhanh}
              dangO={dangO(nhanh)}
              khiDieuHuong={dongHet}
            />
          ))}
        </ul>
      </div>
    </nav>
  );
}

// ───────────────────────── Desktop ─────────────────────────

interface MucCap1Props {
  nhanh: NhanhMenu;
  dangO: boolean;
  dangMo: boolean;
  doiMo: () => void;
  dong: () => void;
  khiDieuHuong: () => void;
}

function MucCap1({ nhanh, dangO, dangMo, doiMo, dong, khiDieuHuong }: MucCap1Props) {
  const { item, children } = nhanh;
  const href = menuHref(item);
  const coCon = children.length > 0;
  const idMenuCon = useId();

  // ⚠ Gạch chân dùng `border-b-[3px]` ở CẢ hai trạng thái (trong suốt khi không hoạt động):
  //   thêm viền chỉ ở trạng thái hoạt động là mục ấy cao hơn các mục khác 3px và cả hàng nhấp
  //   nháy mỗi lần đổi trang.
  const lop = [
    `${LOP_MUC_CAP1} border-b-[3px] transition-colors duration-200 ease-smooth`,
    dangO ? 'border-brand-primary text-brand-primary' : 'border-transparent text-surface-textBase',
    'hover:border-brand-primary hover:text-brand-primary',
  ].join(' ');

  return (
    <li className="group relative" onMouseLeave={dong}>
      {href ? (
        <Link
          href={href}
          target={item.openNewTab ? '_blank' : undefined}
          rel={isExternal(item) ? 'noopener noreferrer' : undefined}
          aria-current={dangO ? 'page' : undefined}
          onClick={khiDieuHuong}
          className={lop}
        >
          <span>{item.label}</span>
          {coCon ? <MuiTen /> : null}
        </Link>
      ) : (
        // Mục `NONE` chỉ để mở menu con. Bản cũ vẽ nó thành `<button>` không gắn hành vi
        // nào — trên thiết bị cảm ứng nó là một nút bấm không phản hồi (luật 15 ở dạng
        // giao diện: một điều khiển bày ra mà không ai đọc thao tác của nó).
        <button
          type="button"
          onClick={doiMo}
          aria-expanded={dangMo}
          aria-controls={coCon ? idMenuCon : undefined}
          aria-haspopup={coCon ? 'true' : undefined}
          className={lop}
        >
          <span>{item.label}</span>
          {coCon ? <MuiTen /> : null}
        </button>
      )}

      {coCon ? (
        <ul
          id={idMenuCon}
          className={`absolute left-0 top-full z-50 min-w-64 divide-y divide-surface-border/40 rounded-lg border border-surface-border bg-white py-1.5 shadow-lg transition-all duration-200 ease-smooth before:absolute before:-top-2 before:left-0 before:right-0 before:h-2 group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100 ${
            dangMo ? 'visible translate-y-0 opacity-100' : 'invisible translate-y-1 opacity-0'
          }`}
        >
          {children.map((con) => {
            const hrefCon = menuHref(con);
            return hrefCon ? (
              <li key={con.label}>
                <Link
                  href={hrefCon}
                  target={con.openNewTab ? '_blank' : undefined}
                  rel={isExternal(con) ? 'noopener noreferrer' : undefined}
                  onClick={khiDieuHuong}
                  className="block px-4 py-2.5 text-[13px] font-medium uppercase text-surface-textBase transition-all duration-150 ease-smooth hover:bg-brand-primaryLight hover:pl-5 hover:text-brand-primary"
                >
                  {con.label}
                </Link>
              </li>
            ) : null;
          })}
        </ul>
      ) : null}
    </li>
  );
}

// ───────────────────────── Ngăn kéo ─────────────────────────

function MucNganKeo({
  nhanh,
  dangO,
  khiDieuHuong,
}: {
  nhanh: NhanhMenu;
  dangO: boolean;
  khiDieuHuong: () => void;
}) {
  const { item, children } = nhanh;
  const href = menuHref(item);
  const coCon = children.length > 0;
  // Mở sẵn nhánh chứa trang đang xem: người dùng thấy ngay mình đang đứng ở đâu trong cây.
  const [mo, datMo] = useState(dangO);
  const idMenuCon = useId();

  const lopNhan = `flex-1 rounded-md px-3 py-3 text-left text-sm font-semibold uppercase ${
    dangO ? 'text-brand-primary' : 'text-surface-textBase'
  }`;

  return (
    <li className="border-b border-surface-border/70 last:border-0">
      <div className="flex items-center">
        {href ? (
          <Link
            href={href}
            target={item.openNewTab ? '_blank' : undefined}
            rel={isExternal(item) ? 'noopener noreferrer' : undefined}
            aria-current={dangO ? 'page' : undefined}
            onClick={khiDieuHuong}
            className={lopNhan}
          >
            {item.label}
          </Link>
        ) : (
          <button type="button" onClick={() => datMo((cu) => !cu)} className={lopNhan}>
            {item.label}
          </button>
        )}
        {coCon ? (
          // Nút bung tách khỏi nhãn: mục cấp 1 có đường dẫn riêng thì bấm nhãn phải ĐI TỚI
          // trang ấy, không phải chỉ bung menu con — nhập nhằng ấy là lý do người dùng di
          // động hay bấm hai lần rồi bỏ cuộc.
          <button
            type="button"
            onClick={() => datMo((cu) => !cu)}
            aria-expanded={mo}
            aria-controls={idMenuCon}
            aria-label={`${mo ? 'Thu gọn' : 'Mở rộng'} ${item.label}`}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary"
          >
            <span className={`transition-transform duration-200 ${mo ? 'rotate-180' : ''}`}>
              <MuiTen />
            </span>
          </button>
        ) : null}
      </div>

      {coCon ? (
        <ul id={idMenuCon} hidden={!mo} className="pb-2 pl-3">
          {children.map((con) => {
            const hrefCon = menuHref(con);
            return hrefCon ? (
              <li key={con.label}>
                <Link
                  href={hrefCon}
                  target={con.openNewTab ? '_blank' : undefined}
                  rel={isExternal(con) ? 'noopener noreferrer' : undefined}
                  onClick={khiDieuHuong}
                  className="block rounded-md px-3 py-2.5 text-[13px] font-medium uppercase text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary"
                >
                  {con.label}
                </Link>
              </li>
            ) : null;
          })}
        </ul>
      ) : null}
    </li>
  );
}

// ───────────────────────── Biểu tượng ─────────────────────────

function MuiTen() {
  return (
    <svg
      className="h-3 w-3 opacity-80"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M19 9l-7 7-7-7" />
    </svg>
  );
}
