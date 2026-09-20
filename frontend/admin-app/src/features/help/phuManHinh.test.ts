import { describe, expect, it } from 'vitest';

import { docTaiLieu } from './markdown';
import { MAN_HINH, catMuc, hopTuKhoa, hopVaiTro, mucChoDuongDan } from './mucTaiLieu';
import nguon from './huong-dan-su-dung.md?raw';

/**
 * **Mọi màn hình của hệ thống phải được hướng dẫn nhắc tới** — luật 28 · luật 27.
 *
 * <h2>⛔⛔ Hình dạng lỗi</h2>
 *
 * Thêm một màn hình vào `MENU` mà quên viết hướng dẫn thì ⛔ có gì báo: menu mọc thêm một mục,
 * người dùng mở ra và tự xoay xở. Tệ hơn — kể từ khi trang Hướng dẫn **lọc theo vai trò**, một
 * màn hình ⛔ khai thẻ `man-hinh` sẽ ⛔ bao giờ xuất hiện trong bộ lọc của bất kỳ ai, nên nó vừa
 * ⛔ được viết vừa ⛔ được ai tìm thấy.
 *
 * ⇒ Vế trái **ĐO từ `MENU`**, ⛔ phải một danh sách gõ tay (cùng khuôn `VongKhuHoiDuPhamViTest`).
 * Màn hình mới ra đời là một lượt CI đỏ gọi đích danh đường dẫn còn thiếu.
 */

const { nhom } = catMuc(docTaiLieu(nguon));
const DA_KHAI = new Set(nhom.flatMap((n) => [n.muc, ...n.con]).flatMap((m) => m.duongDan));

describe('tài liệu phủ hết màn hình — đo từ MENU', () => {
  it('⚠ đọc được cả hai vế — chống xanh trên tập rỗng (luật 7)', () => {
    expect(MAN_HINH.size).toBeGreaterThan(40);
    expect(DA_KHAI.size).toBeGreaterThan(30);
    expect(nhom.length).toBeGreaterThan(10);
  });

  it('⭐⭐ mọi màn hình trong MENU đều có một mục hướng dẫn', () => {
    const thieu = [...MAN_HINH.entries()]
      .filter(([d]) => !DA_KHAI.has(d))
      .map(([d, v]) => `  ${d}  (${v.nhan})`);

    expect(
      thieu,
      '⛔ Màn hình có trong MENU mà ⛔ mục hướng dẫn nào khai. Thêm một thẻ ' +
        '`<!-- man-hinh: … -->` ngay dưới tiêu đề mục tương ứng trong `huong-dan-su-dung.md`.\n' +
        'Một màn hình ⛔ khai thẻ sẽ ⛔ bao giờ hiện ra trong bộ lọc theo vai trò của bất kỳ ai:\n' +
        thieu.join('\n'),
    ).toHaveLength(0);
  });

  it('⭐⭐ mọi đường dẫn tài liệu khai đều là màn hình CÓ THẬT', () => {
    const ma = [...DA_KHAI].filter((d) => !MAN_HINH.has(d)).map((d) => `  ${d}`);
    expect(
      ma,
      '⛔ Tài liệu khai một đường dẫn ⛔ có trong MENU — gõ sai, hoặc màn hình đã bị gỡ mà hướng ' +
        'dẫn còn trỏ vào. Nút `?` sẽ ⛔ bao giờ tra ra mục này.\n' +
        ma.join('\n'),
    ).toHaveLength(0);
  });

  it('⭐ nút `?` tra ra mục đúng cho mọi màn hình, kể cả trang chi tiết', () => {
    for (const d of MAN_HINH.keys()) {
      expect(mucChoDuongDan(nhom, d), `⛔ ⛔ tra ra mục hướng dẫn cho ${d}`).toBeDefined();
    }
    // Trang chi tiết phải rơi về mục của trang danh sách.
    expect(mucChoDuongDan(nhom, '/van-hanh/cong-trinh/abc-123')?.id).toBe(
      mucChoDuongDan(nhom, '/van-hanh/cong-trinh')?.id,
    );
    // ⚠ Vế phân biệt: tiền tố phải theo PHÂN ĐOẠN. `startsWith` trần sẽ khớp bừa chỗ này và đưa
    //   người dùng tới mục hướng dẫn của một màn hình KHÁC — sai mà trông như đang chạy.
    expect(mucChoDuongDan(nhom, '/van-hanh/cong-trinh-gia-mao')).toBeUndefined();
  });
});

describe('lọc theo vai trò', () => {
  const moiMuc = nhom.flatMap((n) => [n.muc, ...n.con]);
  const muc = (id: string) => {
    const m = moiMuc.find((x) => x.id === id);
    expect(m, `⛔ ⛔ tìm thấy mục "${id}" — tài liệu đã đổi tiêu đề?`).toBeDefined();
    return m!;
  };

  it('⭐⭐ mục CHUNG hiện với cả tài khoản ⛔ một quyền nào', () => {
    const rong = new Set<string>();
    // Đăng nhập · vai trò · ô trống · xử lý sự cố: đúng với mọi người, ⛔ được lọc mất.
    expect(hopVaiTro(muc('2-đăng-nhập-và-tài-khoản'), rong)).toBe(true);
    expect(hopVaiTro(muc('4-vai-trò-và-quyền'), rong)).toBe(true);
    expect(hopVaiTro(muc('11-xử-lý-tình-huống-thường-gặp'), rong)).toBe(true);
  });

  it('⭐⭐ mục của màn hình có quyền thì ẩn với người ⛔ có quyền ấy — vế phân biệt', () => {
    const saoLuu = muc('96-sao-lưu--khôi-phục');
    expect(saoLuu.quyen).toContain('adm:backup:view');
    expect(hopVaiTro(saoLuu, new Set())).toBe(false);
    expect(hopVaiTro(saoLuu, new Set(['adm:backup:view']))).toBe(true);
    // ⛔ Một quyền BẤT KỲ ⛔ mở được mục — nếu ⛔ thì bộ lọc ⛔ lọc gì (luật 9).
    expect(hopVaiTro(saoLuu, new Set(['cms:article:view']))).toBe(false);
  });

  it('⭐ phần lớn thừa hưởng màn hình của mục con', () => {
    const thuyVan = nhom.find((n) => n.muc.id.startsWith('6-'))!;
    expect(thuyVan.muc.quyen.length).toBeGreaterThan(0);
    expect(thuyVan.muc.quyen).toEqual(expect.arrayContaining(thuyVan.con.flatMap((c) => c.quyen)));
  });
});

describe('tìm kiếm', () => {
  const moiMuc = catMuc(docTaiLieu(nguon)).nhom.flatMap((n) => [n.muc, ...n.con]);

  it('⭐⭐ gõ ⛔ dấu vẫn ra — `nguong` phải tìm được `ngưỡng`', () => {
    const co = moiMuc.filter((m) => hopTuKhoa(m, 'nguong canh bao'));
    expect(co.length).toBeGreaterThan(0);
    expect(co.map((m) => m.id)).toContain('66-ngưỡng-và-cảnh-báo');
  });

  it('⭐ tìm được cả theo MÃ QUYỀN và theo TÊN MÀN HÌNH trong menu', () => {
    expect(moiMuc.some((m) => hopTuKhoa(m, 'adm:backup:view'))).toBe(true);
    expect(moiMuc.some((m) => hopTuKhoa(m, 'Sao lưu & khôi phục'))).toBe(true);
  });

  it('⭐ tìm được chữ nằm trong BẢNG, ⛔ chỉ trong đoạn văn', () => {
    // "Bảo trì định kỳ" chỉ xuất hiện trong một ô bảng của §5.3.
    expect(moiMuc.some((m) => hopTuKhoa(m, 'Bảo trì định kỳ'))).toBe(true);
  });

  it('⚠ từ khoá rỗng ⇒ hợp tất cả; từ khoá bịa ⇒ hợp 0 — vế phân biệt', () => {
    expect(moiMuc.every((m) => hopTuKhoa(m, '   '))).toBe(true);
    expect(moiMuc.some((m) => hopTuKhoa(m, 'zzzkhongtontai'))).toBe(false);
  });
});
