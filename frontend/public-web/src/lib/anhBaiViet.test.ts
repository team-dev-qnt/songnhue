import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { ANH_BAI_VIET_MAC_DINH } from './anhMacDinh';
import { boChuThich } from './boChuThich';

/**
 * Ảnh bài viết trên cổng — hai luật, ngược chiều nhau.
 *
 * <h2>1. Trang CHI TIẾT không vẽ ảnh bìa</h2>
 *
 * Ảnh đại diện là ảnh cho **danh sách** (thẻ tin) và cho **chia sẻ mạng xã hội**. Biên tập viên
 * gần như luôn chọn nó từ chính ảnh đầu trong thân bài, nên trang chi tiết hiện cùng một tấm
 * ảnh hai lần cách nhau vài dòng. Bỏ đi ngày 01/09/2026.
 *
 * ⛔ Nhưng `og:image` thì PHẢI GIỮ — đó là thứ khác hẳn. Bỏ nhầm nó là mọi lượt chia sẻ bài
 * viết lên Facebook/Zalo mất ảnh, và không ai trong đội thấy vì nó không hiện trên trang.
 * Bài kiểm giữ **cả hai vế**, vì một luật chỉ có vế cấm thì lần dọn dẹp sau sẽ dọn luôn vế kia.
 *
 * <h2>2. Thẻ trong DANH SÁCH luôn có ảnh mặc định</h2>
 *
 * Bài chưa gắn ảnh bìa thì thẻ hiện ô xám kèm biểu tượng — trông như ảnh hỏng. Bốn nơi hiển
 * thị thẻ tin phải truyền `anhMacDinh`; thiếu một nơi là lưới tin so le mà không ai thấy ngay.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * Bài này đọc **văn bản nguồn**, không dựng component. Nó bắt được "quên truyền prop" và
 * "vẽ lại ảnh bìa", nhưng **không** chứng minh ảnh thật sự hiện ra đúng kích thước — phần đó
 * thuộc bộ đo bố cục bằng trình duyệt (`e2e/`).
 */

const GOC = join(process.cwd(), 'src');

function doc(duongDan: string): string {
  return readFileSync(join(GOC, duongDan), 'utf8');
}

/** Bốn nơi hiển thị thẻ tin — con số này là một khẳng định, không phải danh sách để dài thêm. */
const NOI_HIEN_THE_TIN = [
  'components/ArticleCard.tsx',
  'components/home/HomeNewsColumn.tsx',
  'components/home/HomeCategoryNews.tsx',
];

describe('ảnh bài viết trên cổng', () => {
  it('⭐ trang chi tiết KHÔNG vẽ ảnh bìa nữa', () => {
    const nguon = doc('app/bai-viet/[slug]/page.tsx');

    // Canh cấu trúc, không canh chữ (luật 2): tìm THẺ `<PortalImage` thật sự được vẽ ra.
    // Chuỗi "PortalImage" vẫn còn trong chú thích giải thích vì sao bỏ — nếu khớp chuỗi trần
    // thì bài này đỏ vì đúng đoạn văn giải thích nó, đúng kiểu §10.62.
    expect(
      nguon.includes('<PortalImage'),
      'Trang chi tiết vẽ lại ảnh bìa — nó trùng với ảnh đầu trong thân bài. ' +
        'Ảnh đại diện chỉ dùng cho thẻ danh sách và og:image.',
    ).toBe(false);
  });

  it('⛔ nhưng og:image PHẢI còn — bỏ là mọi lượt chia sẻ mất ảnh', () => {
    const nguon = doc('app/bai-viet/[slug]/page.tsx');

    expect(
      /images:\s*cover\s*\?/.test(nguon),
      'generateMetadata phải còn dựng `images` từ ảnh bìa. Đây là vế NGƯỢC của luật trên: ' +
        'ảnh bìa biến mất khỏi thân trang, nhưng vẫn là ảnh khi chia sẻ lên mạng xã hội.',
    ).toBe(true);

    expect(
      nguon.includes('coverAttachmentPublicId'),
      'generateMetadata không còn đọc ảnh bìa — og:image sẽ rỗng ở mọi bài.',
    ).toBe(true);
  });

  it('⭐ cả BA nơi hiển thị thẻ tin đều truyền ảnh mặc định', () => {
    // Vế chống xanh-trên-tập-rỗng (luật 7): danh sách rỗng thì vòng lặp dưới không khẳng định gì.
    expect(NOI_HIEN_THE_TIN).toHaveLength(3);

    const thieu = NOI_HIEN_THE_TIN.filter(
      (p) => !doc(p).includes('anhMacDinh={ANH_BAI_VIET_MAC_DINH}'),
    );

    expect(
      thieu,
      'Nơi hiển thị thẻ tin không truyền `anhMacDinh` sẽ hiện ô xám kèm biểu tượng cho bài ' +
        'chưa gắn ảnh — trông như ảnh hỏng chứ không như "bài này chưa có ảnh".',
    ).toEqual([]);
  });

  it('⛔ ảnh mặc định là đường dẫn tĩnh cục bộ, không hotlink', () => {
    expect(ANH_BAI_VIET_MAC_DINH.startsWith('/')).toBe(true);
    expect(
      /^https?:/.test(ANH_BAI_VIET_MAC_DINH),
      'Hotlink ảnh từ tên miền ngoài — cổng mất ảnh ngay khi bên kia đổi đường dẫn (§10.54).',
    ).toBe(false);
  });

  it('⛔ PortalImage vẫn chỉ có ĐÚNG MỘT thẻ <img> — đếm sau khi BỎ CHÚ THÍCH', () => {
    // `responsiveImages.test.ts` giữ danh sách ngoại lệ ĐẾM CHÍNH XÁC và cấp cho tệp này đúng 1.
    // Nhánh ảnh mặc định cố ý dùng chung thẻ ấy thay vì thêm thẻ thứ hai.
    //
    // ⚠⚠ Bản đầu của chính bài kiểm này đếm trên nguyên văn nguồn và ĐỎ NGAY — vì tệp
    // `PortalImage.tsx` nhắc tới thẻ ấy **bốn lần trong chú thích** (javadoc giải thích vì sao
    // không dùng `next/image`, và chú thích mới giải thích vì sao chỉ một thẻ). Đúng cái bẫy
    // §10.62 mà đoạn văn ngay trên đang cảnh báo: khớp chuỗi trần thì lời giải thích về một
    // luật lại làm đỏ chính luật ấy. Bỏ chú thích trước khi khớp — như `noFabricatedContent`
    // và bộ canh `quality` vẫn làm.
    const nguon = boChuThich(doc('components/PortalImage.tsx'));
    const soThe = (nguon.match(/<img\b/g) ?? []).length;

    expect(
      soThe,
      'Thêm một thẻ <img> nữa là làm đỏ đúng bộ canh đang bảo vệ luật "ảnh phải qua PortalImage".',
    ).toBe(1);
  });
});
