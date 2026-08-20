import Link from 'next/link';

import { getMenu, getSiteConfig } from '@/lib/api';
import { isExternal, menuHref } from '@/lib/routes';
import { SITE } from '@/lib/site';

/**
 * Chân trang — CN-01.5 (T15.3).
 *
 * ⭐ "Liên kết nhanh" chính là **menu vị trí FOOTER**, không phải một tham số riêng ở
 * `settings`. Khai cùng một danh sách ở hai nơi là bảo đảm chúng sẽ lệch nhau.
 */
export async function SiteFooter() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('FOOTER')]);

  const companyInfo = config?.['site.footer.company-info'] ?? '';
  const mapEmbed = config?.['site.footer.map-embed'] ?? '';
  const copyright = config?.['site.footer.copyright'] || `© ${SITE.name}`;
  const social = [
    { label: 'Facebook', url: config?.['site.footer.social.facebook'] },
    { label: 'Zalo', url: config?.['site.footer.social.zalo'] },
    { label: 'YouTube', url: config?.['site.footer.social.youtube'] },
  ].filter((s) => Boolean(s.url));

  return (
    <footer className="mt-12 border-t border-surface-border bg-surface-bgLayout">
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-8 text-sm text-surface-textSecondary sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <p className="font-semibold text-surface-textBase">
            {config?.['site.name'] ?? SITE.name}
          </p>
          {companyInfo ? (
            /*
              Đã qua `HtmlSanitizer.clean()` của backend lúc ghi — xem SiteConfigService.
            */
            <div
              className="mt-2 space-y-1 [&_a]:text-brand-primary"
              // eslint-disable-next-line react/no-danger -- HtmlSanitizer.clean() lúc GHI
              dangerouslySetInnerHTML={{ __html: companyInfo }}
            />
          ) : (
            <p className="mt-2">
              Thông tin liên hệ sẽ do Công ty cung cấp trong phần cấu hình giao diện.
            </p>
          )}
        </div>

        {menu && menu.length > 0 ? (
          <nav aria-label="Liên kết nhanh">
            <p className="font-semibold text-surface-textBase">Liên kết nhanh</p>
            <ul className="mt-2 space-y-1">
              {menu.map((item) => {
                const href = menuHref(item);
                return href ? (
                  <li key={item.label}>
                    <Link
                      href={href}
                      target={item.openNewTab ? '_blank' : undefined}
                      rel={isExternal(item) ? 'noopener noreferrer' : undefined}
                      className="hover:text-brand-primary"
                    >
                      {item.label}
                    </Link>
                  </li>
                ) : null;
              })}
            </ul>
          </nav>
        ) : null}

        {mapEmbed ? (
          <div>
            <p className="font-semibold text-surface-textBase">Bản đồ</p>
            <div
              className="mt-2 overflow-hidden rounded [&_iframe]:h-48 [&_iframe]:w-full"
              // eslint-disable-next-line react/no-danger -- HtmlSanitizer.cleanMapEmbed() lúc GHI
              dangerouslySetInnerHTML={{ __html: mapEmbed }}
            />
          </div>
        ) : null}
      </div>

      {social.length > 0 ? (
        <div className="mx-auto max-w-6xl px-4 pb-4">
          <ul className="flex gap-4 text-sm text-surface-textSecondary">
            {social.map((s) => (
              <li key={s.label}>
                <a
                  href={s.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-brand-primary"
                >
                  {s.label}
                </a>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="border-t border-surface-border">
        <p className="mx-auto max-w-6xl px-4 py-4 text-xs text-surface-textSecondary">
          {copyright}
        </p>
      </div>
    </footer>
  );
}
