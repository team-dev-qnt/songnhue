import type { Page } from '@playwright/test';

/**
 * Bộ đo dùng chung — T61.29. Mỗi hàm trả SỐ LIỆU để khẳng định, ⛔ tự khẳng định (bài tự-kiểm
 * `tuKiem.spec.ts` chứng minh từng hàm phân biệt được trang hỏng với trang lành — luật 1, luật 9).
 */

export type NhatKyLoi = { loiJs: string[]; taiNguyenHong: string[] };

/**
 * Gắn TRƯỚC `goto`. Ghi lỗi JS chưa bắt (`pageerror`) và tài nguyên CÙNG NGUỒN hỏng (JS/CSS/tài liệu
 * ≥ 400 hoặc đứt kết nối). ⛔ Đếm `console.error`: thư viện bên thứ ba in cảnh báo vô hại, và một bộ
 * đo kêu thường trực là bộ đo bị tắt.
 */
export function ghiLoi(page: Page, goc: string): NhatKyLoi {
  const nk: NhatKyLoi = { loiJs: [], taiNguyenHong: [] };
  const nguon = new URL(goc).origin;
  const quanTrong = new Set(['document', 'script', 'stylesheet']);
  page.on('pageerror', (e) => nk.loiJs.push(`${e.name}: ${e.message}`));
  page.on('requestfailed', (r) => {
    if (r.url().startsWith(nguon) && quanTrong.has(r.resourceType())) {
      nk.taiNguyenHong.push(`${r.resourceType()} ${r.url()} — ${r.failure()?.errorText ?? '?'}`);
    }
  });
  page.on('response', (r) => {
    const req = r.request();
    if (r.url().startsWith(nguon) && quanTrong.has(req.resourceType()) && r.status() >= 400) {
      nk.taiNguyenHong.push(`${req.resourceType()} ${r.url()} — HTTP ${r.status()}`);
    }
  });
  return nk;
}

/**
 * Tràn NGANG của trang (px) kèm tối đa 5 phần tử thò ra ngoài khung nhìn — thứ người dùng thấy là
 * thanh cuộn ngang hoặc nội dung bị cắt. Phần tử nằm trong tổ tiên `overflow-x` ≠ `visible` bị bỏ qua:
 * bảng cuộn ngang TRONG khung của nó là thiết kế đúng (`docs/ui-styles.md`).
 */
export async function doTranNgang(page: Page): Promise<{ px: number; thoRa: string[] }> {
  return page.evaluate(() => {
    const khung = document.documentElement.clientWidth;
    const px = Math.max(0, document.documentElement.scrollWidth - khung);
    const thoRa: string[] = [];
    if (px > 1) {
      const biCat = (el: Element): boolean => {
        for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
          const o = getComputedStyle(p).overflowX;
          if (o !== 'visible') return true;
        }
        return false;
      };
      for (const el of Array.from(document.body.querySelectorAll('*'))) {
        const r = el.getBoundingClientRect();
        if (r.width > 0 && r.right > khung + 1 && !biCat(el)) {
          const ten = el.tagName.toLowerCase() + (el.id ? `#${el.id}` : '');
          const lop =
            typeof el.className === 'string' ? el.className.split(/\s+/).slice(0, 3).join('.') : '';
          thoRa.push(`${ten}${lop ? '.' + lop : ''} right=${Math.round(r.right)}`);
          if (thoRa.length >= 5) break;
        }
      }
    }
    return { px, thoRa };
  });
}

/** Độ dài chữ HIỆN RA trong `main` (hoặc `body`) — trang trắng / khung rỗng cho số rất nhỏ. */
export async function doChuHienRa(page: Page): Promise<number> {
  return page.evaluate(() => {
    const goc = document.querySelector('main') ?? document.body;
    return (goc as HTMLElement).innerText.replace(/\s+/g, ' ').trim().length;
  });
}

export function batBuocBien(ten: string): string {
  const v = process.env[ten]?.trim();
  if (!v) {
    // ⛔ Mặc định: đo nhầm môi trường (production, hay localhost trống) cho ra số ⛔ ai kiểm được.
    throw new Error(`Thiếu biến ${ten} — xem playwright.tuong-thich.config.ts`);
  }
  return v.replace(/\/+$/, '');
}
