import { describe, expect, it } from 'vitest';

import { COT_MUC_NUOC, COT_VAN_HANH } from './homeDataColumns';

/**
 * **Hai bảng số liệu của trang chủ phải giữ đúng số cột đặc tả.**
 *
 * Bài này khẳng định **số lượng**, không khẳng định từng chuỗi. Đó là chủ ý: khẳng định lại
 * đúng mảng đang có là chép lại chính nó (luật 29 — bài kiểm chứng ngược sai theo đúng cách
 * thứ nó kiểm đang sai). Số 8 và số 6 đến từ `function-spec.md` CN-03.4 / CN-02.11, tức từ
 * một nguồn KHÁC với tệp đang được kiểm.
 */
describe('Cột của hai bảng số liệu trang chủ', () => {
  it('⭐ CN-03.4 — biểu tổng hợp theo tuyến sông có ĐỦ 8 cột', () => {
    expect(COT_MUC_NUOC).toHaveLength(8);
  });

  it('⭐ CN-02.11 — tình hình vận hành có ĐỦ 6 cột', () => {
    expect(COT_VAN_HANH).toHaveLength(6);
  });

  it('⛔ cột "Lượng mưa" KHÔNG được bỏ dù v1 chắc chắn hiển thị `-` (G3)', () => {
    // Đây là khoảng trống Công ty cần nhìn thấy. Bỏ cột đi thì bảng trông đầy đủ trong khi
    // một nguồn dữ liệu vẫn đang thiếu — cùng họ với §10.54 ở chiều ngược lại.
    expect(COT_MUC_NUOC.some((c) => c.includes('Lượng mưa'))).toBe(true);
  });

  it('⛔ không cột nào trùng tên — bảng hai cột giống nhau là bảng đọc sai', () => {
    for (const cot of [COT_MUC_NUOC, COT_VAN_HANH]) {
      expect(new Set(cot).size).toBe(cot.length);
    }
  });

  it('⛔ không tên cột nào rỗng hoặc thừa khoảng trắng', () => {
    for (const ten of [...COT_MUC_NUOC, ...COT_VAN_HANH]) {
      expect(ten.trim()).toBe(ten);
      expect(ten.length).toBeGreaterThan(0);
    }
  });
});
