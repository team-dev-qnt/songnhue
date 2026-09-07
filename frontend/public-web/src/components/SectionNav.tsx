import Link from 'next/link';

import { getMenu } from '@/lib/api';
import { buildMenuTree, isExternal, menuHref } from '@/lib/routes';

/**
 * Dải điều hướng **giữa các trang anh em trong cùng một mục cấp 1** — 29/08/2026.
 *
 * <h2>Bảy trang đang là ngõ cụt</h2>
 *
 * Ba trang dưới nhánh Giới thiệu và bốn trang dưới Quản lý, vận hành đều chỉ có một lối vào
 * (menu đầu trang) và không có lối nào sang trang anh em. Người đang đọc "Cơ cấu tổ chức" muốn
 * xem "Lãnh đạo Công ty" phải mở lại menu — trên điện thoại là mở ngăn kéo, cuộn, bấm. Cổng
 * tham chiếu đặt dải này ở mọi trang con vì cùng lý do.
 *
 * <h2>⛔ Nhãn và đường dẫn đều lấy từ MENU, không viết trong tệp này</h2>
 *
 * Cám dỗ ở đây là khai một mảng ba dòng {@code {label, href}}. Làm vậy là dựng hệ phân loại
 * THỨ HAI ngay cạnh menu — đúng thứ CR-09 vừa gỡ khỏi chân trang, và đúng thứ §2 cấm bằng lời:
 * *"menu chính, footer, các card chuyên mục và cây nội dung phải dùng CHUNG một hệ phân loại"*.
 * Công ty đổi tên một mục hay ẩn một trang thì dải này đổi theo, không cần deploy.
 *
 * <p>⚠ Tìm mục cha bằng cách so <b>đường dẫn</b> chứ không so nhãn: nhãn là thứ Công ty sửa
 * được bất cứ lúc nào, còn đường dẫn bị {@code portalRoutes.test.ts} canh cho khớp với
 * {@code menu_items.url} trong migration.
 *
 * @param duongDan đường dẫn của trang đang mở, ví dụ {@code /gioi-thieu/lanh-dao}
 */
export async function SectionNav({ duongDan }: { duongDan: string }) {
  const menu = await getMenu('HEADER');
  const cay = buildMenuTree(menu ?? []);

  const cha = cay.find((n) => n.children.some((c) => menuHref(c) === duongDan));
  // Trang không nằm dưới mục cấp 1 nào (hoặc menu chưa về) ⇒ không vẽ gì. Không dựng một dải
  // rỗng có viền: một khung trống là thứ trông như hỏng.
  if (!cha || cha.children.length < 2) {
    return null;
  }

  return (
    <nav aria-label={`Các trang trong mục ${cha.item.label}`} className="mt-8">
      <p className="text-[11px] font-bold tracking-wide text-surface-textSecondary">
        {cha.item.label}
      </p>
      <ul className="mt-2.5 flex flex-wrap gap-2.5">
        {cha.children.map((con) => {
          const href = menuHref(con);
          if (!href) {
            return null;
          }
          const dangMo = href === duongDan;
          return (
            <li key={con.label}>
              <Link
                href={href}
                aria-current={dangMo ? 'page' : undefined}
                target={con.openNewTab ? '_blank' : undefined}
                rel={isExternal(con) ? 'noopener noreferrer' : undefined}
                className={
                  dangMo
                    ? 'inline-flex min-h-11 items-center rounded-lg border border-brand-primary bg-brand-primaryLight px-3.5 text-[13px] font-bold text-brand-primary'
                    : 'inline-flex min-h-11 items-center rounded-lg border border-surface-border bg-white px-3.5 text-[13px] font-medium text-surface-textSecondary transition-colors hover:border-brand-primary hover:text-brand-primary'
                }
              >
                {con.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
