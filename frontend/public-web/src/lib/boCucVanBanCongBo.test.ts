import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { boCucVanBanCongBo } from './boCucVanBanCongBo';

/**
 * "Hệ thống văn bản điều hành" tắt được từ màn hình Cấu hình — T79.1.
 *
 * <p>⚠ Phạm vi (luật 28): bài này canh **quyết định bố cục**. Vế *"khoá `settings` có thật sự xoá
 * được ⛔"* nằm ở `SiteLayoutTest.heThongVanBanTatDuocTuManHinh` (backend, đo giá trị ĐÃ GIẢI) —
 * ⛔ bài jsdom nào chạm tới `default_value` được.
 */
describe('boCucVanBanCongBo — thẻ ẩn thì bảng văn bản phải giãn kín', () => {
  it('⛔ có địa chỉ ⇒ ẩn thẻ VÀ bảng văn bản chiếm trọn 12 cột', () => {
    for (const rong of [undefined, null, '', '   ']) {
      const b = boCucVanBanCongBo(rong);
      expect(b.hienTheHeThong, `địa chỉ ${JSON.stringify(rong)}`).toBe(false);
      expect(b.cotBangVanBan).toBe('lg:col-span-12');
    }
  });

  it('có địa chỉ hợp lệ ⇒ hiện thẻ VÀ bảng lui về 8 cột', () => {
    const b = boCucVanBanCongBo('https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi');
    expect(b.hienTheHeThong).toBe(true);
    expect(b.cotBangVanBan).toBe('lg:col-span-8');
  });

  it('⭐ địa chỉ RÁC đi cùng đường với rỗng — ⛔ dựng một thẻ trỏ vào hư không', () => {
    // `lienKetAnToan` từ chối `javascript:`, đường tương đối, `//host`… Thẻ mà vẫn hiện thì nút
    // "Truy cập hệ thống" thành một nút ⛔ đi đâu cả, và bảng văn bản vẫn bị bóp còn 8 cột.
    for (const rac of ['javascript:alert(1)', 'abc/xyz', '//ke-la.example']) {
      const b = boCucVanBanCongBo(rac);
      expect(b.hienTheHeThong, `địa chỉ ${rac}`).toBe(false);
      expect(b.cotBangVanBan).toBe('lg:col-span-12');
    }
  });

  it('⭐⭐ HAI quyết định ⛔ thể trôi khỏi nhau — ẩn thẻ ⟺ 12 cột, ở MỌI đầu vào', () => {
    // Đây là bất biến T79.1 sinh ra để giữ. Bản trước tách hai quyết định: thẻ hỏi
    // `lienKetAnToan`, bề rộng ghim `lg:col-span-8` vô điều kiện ⇒ ẩn thẻ để lại 4/12 trống.
    const dauVao = [
      undefined,
      null,
      '',
      '  ',
      'https://a.example',
      'http://b.example/x?y=1',
      'javascript:alert(1)',
      '//ke-la.example',
      'abc/xyz',
      '/noi-bo',
      '#neo',
    ];
    for (const v of dauVao) {
      const b = boCucVanBanCongBo(v);
      expect(b.cotBangVanBan, `địa chỉ ${JSON.stringify(v)}`).toBe(
        b.hienTheHeThong ? 'lg:col-span-8' : 'lg:col-span-12',
      );
    }
    // Vế chống-tập-rỗng: phải có CẢ HAI trạng thái trong tập thử, ⛔ thì khẳng định trên rỗng
    // nghĩa (luật 7 — một bài chạy qua một nhánh duy nhất ⛔ phân biệt được gì).
    const trangThai = new Set(dauVao.map((v) => boCucVanBanCongBo(v).hienTheHeThong));
    expect(trangThai).toEqual(new Set([true, false]));
  });

  it('⛔⛔ component ⛔ được tự quyết bề rộng — hai tên lớp chỉ sống ở MỘT nơi', () => {
    // Nếu ai đó viết lại `lg:col-span-8` thẳng vào JSX thì bất biến trên thành trang trí: hàm
    // vẫn đúng, còn trang chủ lại hụt 4 cột. Quét trên mã ĐÃ BỎ CHÚ THÍCH — chú thích giải
    // thích khuyết tật phải được phép gọi tên nó (T46.7 · T54.8).
    const ma = boChuThich(
      readFileSync(
        join(
          dirname(fileURLToPath(import.meta.url)),
          '../components/home/PublishedDocumentsSection.tsx',
        ),
        'utf8',
      ),
    );
    expect(ma).not.toMatch(/lg:col-span-(8|12)/);
    expect(ma, 'tự kiểm: ⛔ quét nhầm một tệp rỗng').toContain('boCucVanBanCongBo');
  });
});
