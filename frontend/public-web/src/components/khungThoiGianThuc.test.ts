import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Sự cố backend ⛔ không được trông giống "chưa có dữ liệu"** — T28.46 / cam kết T35.10.
 *
 * ## Đường dây đang canh
 *
 * `apiGet` **cố ý gộp** 404 và lỗi mạng thành `null` (javadoc `lib/api.ts`), rồi mỗi khối quyết định
 * nói câu gì:
 *
 * ```
 *   apiGet → null → rows === null → unavailable={true} → khung amber + lý do
 *                 → []            → unavailable={false} → "Chưa điểm đo nào đang hoạt động"
 * ```
 *
 * Hai câu ấy nói hai chuyện khác nhau, và javadoc của `WaterLevelBlock` nói thẳng vì sao chúng phải
 * khác: *"gộp lại là để một sự cố backend trông y hệt một hệ thống chưa có dữ liệu"*.
 *
 * ## ⛔ Đo được: đường ấy CHƯA có bộ canh nào
 *
 * `grep 'unavailable|RealtimeFrame' --include='*.test.*'` trên toàn kho trả về **0** kết quả cho
 * `RealtimeFrame`. Nghĩa là đổi `unavailable={rows === null}` thành `{false}`, hoặc đổi
 * `rows={mucNuoc}` thành `rows={mucNuoc ?? []}`, thì **toàn bộ bộ test FE vẫn xanh** — và một sự cố
 * backend im lặng biến thành một dòng chữ hiền lành.
 *
 * ## ⚠ Vì sao đọc MÃ NGUỒN chứ ⛔ không render
 *
 * `public-web` **cố ý ⛔ không có môi trường DOM**: `vitest.config.mts` khai
 * `include: ['src/**&#47;*.test.ts']` + `environment: 'node'`, và javadoc của chính tệp cấu hình ấy
 * giải thích lý do (*"dựng một tầng mock nửa vời chỉ tạo ra thứ xanh mà ⛔ không chứng minh gì"*).
 * Thêm jsdom vào đây là đảo một quyết định đã ghi thành văn, để đổi lấy một bài kiểm yếu hơn.
 *
 * <p>Nên bài này theo đúng khuôn `bangMucNuoc.test.ts` và `noFabricatedContent.test.ts`: đọc mã
 * nguồn dạng văn bản và canh **bất biến của đường dây**, ⛔ không canh pixel.
 */

const GOC = join(process.cwd(), 'src');

function doc(tuongDoi: string): string {
  return readFileSync(join(GOC, tuongDoi), 'utf8');
}

/** Mọi khối dùng `RealtimeFrame`, kèm biểu thức `unavailable` mà nó truyền vào. */
const NOI_DUNG = {
  'components/home/WaterLevelBlock.tsx': doc('components/home/WaterLevelBlock.tsx'),
  'components/home/OperationsBlock.tsx': doc('components/home/OperationsBlock.tsx'),
  'app/page.tsx': doc('app/page.tsx'),
  'app/quan-ly-van-hanh/muc-nuoc-luong-mua/page.tsx': doc(
    'app/quan-ly-van-hanh/muc-nuoc-luong-mua/page.tsx',
  ),
  'components/realtime/RealtimeFrame.tsx': doc('components/realtime/RealtimeFrame.tsx'),
};

describe('Khung thời gian thực — sự cố phải nhìn thấy được', () => {
  it('⚠ đọc được cả năm tệp — bài chạy qua tập rỗng thì xanh mà ⛔ không canh gì (luật 7)', () => {
    for (const [ten, ma] of Object.entries(NOI_DUNG)) {
      expect(ma.length, ten).toBeGreaterThan(500);
    }
  });

  it('⭐⭐ `RealtimeFrame` thật sự CÓ nhánh `unavailable` — nếu không, mọi bài dưới vô nghĩa', () => {
    const ma = NOI_DUNG['components/realtime/RealtimeFrame.tsx'];
    expect(ma).toContain('unavailable');
    expect(ma, 'Phải có câu chữ CR-36 chứ ⛔ không chỉ có một prop không ai đọc').toContain(
      'Dữ liệu tạm thời chưa khả dụng',
    );
    expect(
      ma,
      '⛔ `children` KHÔNG được render khi `unavailable` — nếu vẫn render thì khung amber chỉ là ' +
        'trang trí, và bảng rỗng bên dưới vẫn nói "chưa có dữ liệu".',
    ).toMatch(/unavailable\s*\?/);
  });

  it('⭐⭐ `WaterLevelBlock` phân biệt `null` với `[]` — đây đúng là chỗ dễ mất nhất', () => {
    const ma = NOI_DUNG['components/home/WaterLevelBlock.tsx'];
    expect(
      ma,
      'Đổi thành `unavailable={false}` là một sự cố backend trông y hệt "chưa có dữ liệu", và ⛔ ' +
        'không bộ canh nào khác thấy.',
    ).toContain('unavailable={rows === null}');
    // Và vế `[]` phải nói một câu KHÁC — hai trạng thái, hai câu (luật 9).
    expect(ma).toContain('Chưa điểm đo nào đang hoạt động');
    expect(ma).toMatch(/unavailableReason=/);
  });

  it('⭐⭐ nơi GỌI ⛔ không được nuốt `null` bằng `?? []`', () => {
    // Đây là nửa thứ hai của đường dây, và nó ở một tệp KHÁC. Bộ canh chỉ soi component thì mù trước
    // chuyện này: `WaterLevelBlock` vẫn đúng từng dòng, mà `null` ⛔ không bao giờ tới được nó nữa.
    for (const ten of ['app/page.tsx', 'app/quan-ly-van-hanh/muc-nuoc-luong-mua/page.tsx']) {
      const ma = NOI_DUNG[ten as keyof typeof NOI_DUNG];
      expect(ma, ten).toContain('rows={mucNuoc}');
      expect(ma, `${ten}: \`mucNuoc ?? []\` xoá mất trạng thái "gọi API hỏng"`).not.toContain(
        'rows={mucNuoc ?? []}',
      );
    }
  });

  it('⚠ khối Tình hình vận hành CỐ Ý gộp `null` thành `[]` — ghi ra để ⛔ không ai "sửa" nhầm', () => {
    // Hai khối cạnh nhau, hai cách xử lý khác nhau. Đó là chủ đích: `OperationsBlock` đo `coDuLieu`
    // chứ ⛔ không đo `null`, vì nguồn của nó là bảng mã tình hình vận hành nhập tay — rỗng ở đó là
    // trạng thái bình thường, ⛔ không phải một sự cố.
    expect(NOI_DUNG['app/page.tsx']).toContain('rows={tinhHinhVanHanh ?? []}');
    expect(NOI_DUNG['components/home/OperationsBlock.tsx']).toContain('unavailable={!coDuLieu}');
  });

  it('⭐ vế phân biệt: phép so trên bắt được khi biểu thức đổi (luật 9)', () => {
    // Nếu `toContain` khớp cả chuỗi đã hỏng thì bốn bài trên ⛔ không khẳng định gì.
    const ma = NOI_DUNG['components/home/WaterLevelBlock.tsx'];
    expect(ma).not.toContain('unavailable={false}');
    expect(ma).not.toContain('rows={rows ?? []}');
  });
});
