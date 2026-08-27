'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useId, useRef, useState } from 'react';

import type { MenuLink } from '@/lib/api';
import { isExternal, menuHref, ROUTES } from '@/lib/routes';

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
 *   khung chứa                        = 1240 − 48 = 1192px      → tràn 22%
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
 *   <li><b>Dưới {@code lg} chuyển sang ngăn kéo</b> — một nút, và toàn bộ cây mở ra theo chiều
 *       dọc, nơi nhãn dài không phải cạnh tranh bề ngang với nhau.
 *   <li><b>Menu con mở được bằng CHẠM và bàn phím</b>, không chỉ bằng rê chuột — xem dưới.
 * </ol>
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
      className="sticky top-0 z-40 w-full border-b border-black/20 bg-gradient-to-r from-chrome-navy800 via-chrome-navy500 to-chrome-navy800 text-white shadow-md backdrop-blur-md"
    >
      <div className="mx-auto flex max-w-[1240px] items-center justify-between gap-2 px-4 sm:px-6">
        {/* ───── Nút ngăn kéo — chỉ dưới lg ───── */}
        <button
          type="button"
          onClick={() => datMoNganKeo((cu) => !cu)}
          aria-expanded={moNganKeo}
          aria-controls={idNganKeo}
          className="flex items-center gap-2 py-3 pr-2 text-sm font-semibold text-white lg:hidden"
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
        <ul className="hidden flex-1 items-center gap-0.5 text-[13px] font-semibold lg:flex">
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

        {/* ───── Tìm kiếm ───── */}
        <Link
          href={ROUTES.search}
          className="flex items-center gap-1.5 rounded-md px-2.5 py-2 text-[13px] font-semibold text-white transition-colors duration-200 ease-smooth hover:bg-white/15 hover:text-brand-gold"
          aria-label="Tìm kiếm"
        >
          <BieuTuongKinhLup />
          <span className="hidden xl:inline">Tìm kiếm</span>
        </Link>
      </div>

      {/* ───── Ngăn kéo — dưới lg ───── */}
      <div
        id={idNganKeo}
        hidden={!moNganKeo}
        className="max-h-[calc(100vh-3.5rem)] overflow-y-auto border-t border-white/10 bg-chrome-navy800 lg:hidden"
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

  const lop = [
    'flex items-center gap-1 whitespace-nowrap rounded-md px-3 py-3 transition-colors duration-200 ease-smooth',
    dangO ? 'text-brand-gold' : 'text-white',
    'hover:bg-white/10 hover:text-brand-gold',
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
                  className="block px-4 py-2.5 text-[13px] font-medium text-surface-textBase transition-all duration-150 ease-smooth hover:bg-brand-primaryLight hover:pl-5 hover:text-brand-primary"
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

  const lopNhan = `flex-1 rounded-md px-3 py-3 text-left text-sm font-semibold ${
    dangO ? 'text-brand-gold' : 'text-white'
  }`;

  return (
    <li className="border-b border-white/10 last:border-0">
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
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-white/80 hover:bg-white/10 hover:text-white"
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
                  className="block rounded-md px-3 py-2.5 text-[13px] font-medium text-white/85 hover:bg-white/10 hover:text-white"
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

function BieuTuongKinhLup() {
  return (
    <svg
      className="h-4 w-4"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
      />
    </svg>
  );
}
