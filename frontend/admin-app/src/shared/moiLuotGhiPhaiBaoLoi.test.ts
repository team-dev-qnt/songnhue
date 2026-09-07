import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

/**
 * **Mỗi lượt ghi phải nói được là nó hỏng.**
 *
 * <h2>Sự cố 01/09/2026 — và vì sao bộ canh này rộng hơn sự cố</h2>
 *
 * QuanTran báo *"error 422 lúc tạo tài khoản không hiển thị lên màn hình"*. Đợt rà theo lời báo
 * ấy đo được: **68 `useMutation` trong `admin-app`, 9 cái không có `onError` nào**. Chín đường
 * ghi ấy hỏng hoàn toàn im lặng — người dùng bấm, không có gì xảy ra, không có gì báo.
 *
 * <p>⛔ Sửa đúng chín chỗ ấy thì lớp lỗi **không đóng lại**: lượt `useMutation` thứ 69 viết tuần
 * sau sẽ lại quên, và không cổng kiểm nào đỏ. Đúng bài học §10.70 — *trả một nợ ở ba điểm ghi
 * không đóng được lớp lỗi; điểm ghi thứ tư ra đời cùng đợt mang lại đúng lỗi cũ*. Nên bất biến
 * phải phát biểu ở mức **mọi lượt ghi**, không ở mức chín tệp.
 *
 * <h2>⚠⚠ Niềm tin sai đã bảo vệ chín chỗ ấy suốt</h2>
 *
 * `StatusBatchUpdateModal` có một chú thích ngay tại chỗ thiếu `onError`:
 *
 * > *"Không nuốt lỗi bằng một câu chung: apiClient đã tra error-map và hiện đúng mã lỗi (OPS-2006
 * > thiếu tham số, OPS-2018 mã đã ẩn, AUTH-3002 ngoài phạm vi đơn vị)."*
 *
 * Vế đầu đúng — `apiClient` **tra** error-map thật. Vế sau **sai**: nó chỉ *phát thông báo* cho ba
 * sự kiện phiên (`sessionLost`, `maintenance`, `mustChangePassword`) qua `onSessionEvent`;
 * `handling: 'toast'` không có một dòng nào hiện gì cả. Ba mã lỗi được gọi đích danh trong chú
 * thích ấy hiện ra cho **không ai**.
 *
 * <p>📌 §10.42 ở dạng đắt nhất: một chú thích tự tin, cụ thể, nêu tên ba mã lỗi, mô tả một cơ chế
 * **không tồn tại** — và chính vì nó tự tin nên không ai đi kiểm. Bài kiểm này là thứ đi kiểm.
 *
 * <h2>Phạm vi (luật 28)</h2>
 *
 * Canh: **có mặt** một nhánh `onError`. KHÔNG canh: nhánh ấy hiện ra thứ gì cho người dùng —
 * `loiTheoTruong.test.tsx` lo phần đó cho các biểu mẫu. Một `onError` rỗng vẫn qua được bài này;
 * đổi lại, bài này chạy trên **toàn cây** và không có danh sách miễn trừ nào.
 */

const GOC = join(process.cwd(), 'src');

function moiTepNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return moiTepNguon(duong);
    if (!/\.tsx?$/.test(ten) || ten.includes('.test.')) return [];
    return [duong];
  });
}

/**
 * Cắt lấy thân của từng lời gọi `useMutation({ … })`.
 *
 * ⚠ Đếm ngoặc nhọn, KHÔNG dò chuỗi `'\n  });'`: lượt viết đầu dò chuỗi và nó phụ thuộc vào mức
 * thụt lề của nơi gọi — một mutation nằm sâu thêm một cấp là cắt hụt, và phần bị cắt hụt thì
 * "không thấy `onError`" trông y hệt "thiếu `onError`". Bộ canh khi ấy đỏ oan, và lượt sửa sẽ là
 * nới bộ canh chứ không phải sửa mã.
 */
function thanCacMutation(ma: string): string[] {
  const than: string[] = [];
  const re = /useMutation\s*\(\s*\{/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(ma)) !== null) {
    let sau = 1;
    let i = m.index + m[0].length;
    while (i < ma.length && sau > 0) {
      if (ma[i] === '{') sau += 1;
      else if (ma[i] === '}') sau -= 1;
      i += 1;
    }
    than.push(ma.slice(m.index, i));
  }
  return than;
}

describe('Mọi useMutation của admin-app đều có nhánh onError', () => {
  const tep = moiTepNguon(GOC).map((duong) => ({
    duong: duong.slice(GOC.length + 1),
    than: thanCacMutation(boChuThich(readFileSync(duong, 'utf8'))),
  }));

  const tatCa = tep.flatMap((t) => t.than.map((than) => ({ duong: t.duong, than })));

  it('⛔ chống xanh trên tập rỗng — bộ canh phải THẤY một số lượng mutation đáng kể (luật 7)', () => {
    // Không có dòng này thì một `moiTepNguon` hỏng, hay một `thanCacMutation` không khớp gì, sẽ
    // cho bài kiểm xanh trọn vẹn trong khi nó chưa soi một lượt ghi nào. Đo 01/09: 68.
    expect(tatCa.length).toBeGreaterThanOrEqual(60);
  });

  it('⭐ không lượt ghi nào thiếu onError', () => {
    const thieu = tatCa.filter((m) => !/\bonError\s*:/.test(m.than)).map((m) => m.duong);
    expect(
      thieu,
      `lượt ghi hỏng trong im lặng — người dùng bấm, không có gì xảy ra và không có gì báo:\n  ${thieu.join('\n  ')}`,
    ).toHaveLength(0);
  });

  it('kiểm chứng ngược: bộ cắt phân biệt được mutation CÓ và KHÔNG có onError', () => {
    // Luật 1 — mỗi cơ chế canh gác phải có bài chứng minh nó bắt được vi phạm; và luật 29 —
    // bài kiểm chứng ngược phải khác giả định với thứ nó kiểm, nên ở đây ta cho nó ăn chuỗi
    // dựng tay thay vì một tệp thật.
    const coLoi = `const m = useMutation({
      mutationFn: () => api.post('/x'),
      onSuccess: () => { message.success('ok'); },
    });`;
    const khongLoi = `const m = useMutation({
      mutationFn: () => api.post('/x'),
      onSuccess: () => { message.success('ok'); },
      onError: () => { message.error('hỏng'); },
    });`;
    expect(thanCacMutation(coLoi)).toHaveLength(1);
    expect(/\bonError\s*:/.test(thanCacMutation(coLoi)[0])).toBe(false);
    expect(/\bonError\s*:/.test(thanCacMutation(khongLoi)[0])).toBe(true);
  });

  it('kiểm chứng ngược: bộ cắt KHÔNG hụt khi mutation nằm sâu và có ngoặc lồng nhau', () => {
    // ⚠ Đây là cái bẫy của cách cắt theo chuỗi thụt lề. Bộ đếm ngoặc phải ôm trọn cả
    //   `onError` nằm sau một `onSuccess` có thân nhiều cấp.
    const sauNhieuCap = `      const m = useMutation({
        mutationFn: (d: X) => api.put(\`/x/\${d.id}\`, { a: { b: 1 } }),
        onSuccess: () => {
          if (x) { doSomething({ nested: true }); }
        },
        onError: () => { message.error('hỏng'); },
      });`;
    const than = thanCacMutation(sauNhieuCap);
    expect(than).toHaveLength(1);
    expect(/\bonError\s*:/.test(than[0])).toBe(true);
    // …và không ôm quá tay sang mã đứng sau.
    expect(than[0].endsWith('}')).toBe(true);
  });

  it('chú thích KHÔNG được tính là một nhánh onError', () => {
    // Chính là cái bẫy đã bảo vệ `StatusBatchUpdateModal` suốt: một chú thích nói về việc báo
    // lỗi, đặt đúng chỗ nhánh báo lỗi phải nằm. `boChuThich` cắt nó đi trước khi soi.
    const chiCoChuThich = `const m = useMutation({
      mutationFn: () => api.post('/x'),
      // apiClient đã lo phần onError rồi, không cần ở đây
    });`;
    const than = thanCacMutation(boChuThich(chiCoChuThich));
    expect(than).toHaveLength(1);
    expect(/\bonError\s*:/.test(than[0])).toBe(false);
  });
});
