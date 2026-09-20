import { describe, expect, it } from 'vitest';

import { MENU, type MenuNode } from '@/app/menu';

import { neoChoDuongDan } from './neoHuongDan';

/**
 * **Nút `?` phải tra ra mục ĐÚNG cho mọi màn hình** — và ⛔ bao giờ dẫn bừa.
 *
 * <h2>⛔⛔ Vì sao vế "⛔ dẫn bừa" quan trọng hơn vế "có dẫn"</h2>
 *
 * Một nút ⛔ hiện ra thì người dùng đi tìm cách khác. Một nút dẫn tới **giữa tài liệu một cách
 * ngẫu nhiên** thì họ tin rằng mình vừa được đưa tới đúng chỗ, rồi làm theo hướng dẫn của **một
 * màn hình khác** — sai mà trông như đang chạy (T23.8).
 */

function moiDuongDan(nodes: readonly MenuNode[]): string[] {
  return nodes.flatMap((n) => [
    ...(n.path ? [n.path] : []),
    ...(n.children ? moiDuongDan(n.children) : []),
  ]);
}

describe('nút ? tra ra mục hướng dẫn', () => {
  it('⚠ đọc được MENU — chống xanh trên tập rỗng (luật 7)', () => {
    expect(moiDuongDan(MENU).length).toBeGreaterThan(40);
  });

  it('⭐⭐ mọi màn hình trong MENU đều tra ra một neo', () => {
    const hong = moiDuongDan(MENU).filter((d) => !neoChoDuongDan(d));
    expect(hong, `⛔ ⛔ tra ra mục hướng dẫn cho:\n${hong.join('\n')}`).toHaveLength(0);
  });

  it('⭐⭐ trả về MỤC CON, ⛔ phải cả phần lớn — người dùng cần đúng chỗ họ đang đứng', () => {
    // §9.6 chứ ⛔ phải §9 *Quản trị hệ thống*: đưa họ về đầu phần 9 là bắt tự dò tiếp 8 mục.
    expect(neoChoDuongDan('/quan-tri/sao-luu')).toBe('96-sao-lưu--khôi-phục');
    expect(neoChoDuongDan('/thuy-van/ma-la')).toBe('65-mã-lạ-từ-nguồn');
  });

  it('⭐ trang chi tiết rơi về mục của trang danh sách', () => {
    expect(neoChoDuongDan('/van-hanh/cong-trinh/9f1c-abc')).toBe(
      neoChoDuongDan('/van-hanh/cong-trinh'),
    );
  });

  it('⭐⭐ đường dẫn lạ trả `undefined` — vế phân biệt, và là vế CHỊU LỰC', () => {
    expect(neoChoDuongDan('/khong-co-that')).toBeUndefined();
    // ⚠ Tiền tố phải theo PHÂN ĐOẠN: `startsWith` trần sẽ khớp bừa chỗ này và dẫn người dùng
    //   tới hướng dẫn của một màn hình KHÁC.
    expect(neoChoDuongDan('/van-hanh/cong-trinh-gia-mao')).toBeUndefined();
  });
});
