import { describe, expect, it } from 'vitest';

import {
  BE_RONG_TOI_THIEU_MUC_NUOC,
  COT_MUC_NUOC,
  COT_VAN_HANH,
  LUOI_MUC_NUOC,
} from './homeDataColumns';

/**
 * **Hai bảng số liệu của trang chủ phải giữ đúng số cột đặc tả.**
 *
 * Bài này khẳng định **số lượng**, không khẳng định từng chuỗi. Đó là chủ ý: khẳng định lại
 * đúng mảng đang có là chép lại chính nó (luật 29 — bài kiểm chứng ngược sai theo đúng cách
 * thứ nó kiểm đang sai). Số 9 và số 6 đến từ `function-spec.md` CN-03.4 / CN-02.11 (cột thứ 9 chốt 08/09/2026 —
 * DOD2.3), tức từ
 * một nguồn KHÁC với tệp đang được kiểm.
 */
describe('Cột của hai bảng số liệu trang chủ', () => {
  it('⭐ CN-03.4 — biểu tổng hợp theo tuyến sông có ĐỦ 9 cột', () => {
    // 8 tới 08/09/2026; cột thứ 9 "Mực nước sông (m)" thêm ở DOD2.3.
    expect(COT_MUC_NUOC).toHaveLength(9);
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

  /**
   * ⛔⛔ Số ô của lưới CSS phải bằng số cột — nếu không, hàng tiêu đề trượt khỏi hàng dữ liệu.
   *
   * Đây là bộ canh cho một lỗ hổng luật 14 đóng ngày 08/09/2026: hai chuỗi `grid-cols-[…]` và
   * `min-w-[…]` từng được ghi lặp ở **bốn** chỗ, ngay cạnh một chú thích tự cảnh báo *"phải TRÙNG
   * … nếu không cột lệch"*. Một lời dặn ⛔ không phải một cổng kiểm.
   *
   * ⚠ Bài này canh **cấu trúc** (đếm ô), ⛔ không canh **văn bản** (so chuỗi) — luật 2. Đổi
   * `0.95fr` thành `1fr` là chuyện thẩm mỹ và ⛔ không được làm nó đỏ; thêm một cột mà quên lưới
   * thì phải đỏ.
   */
  it('⭐ Lưới CSS có ĐÚNG số ô bằng số cột — bộ canh cho lỗ hổng luật 14 vừa đóng', () => {
    const trongNgoac = /^grid-cols-\[(.+)\]$/.exec(LUOI_MUC_NUOC);
    expect(trongNgoac, `LUOI_MUC_NUOC sai hình dạng: ${LUOI_MUC_NUOC}`).not.toBeNull();
    expect(trongNgoac![1].split('_')).toHaveLength(COT_MUC_NUOC.length);
  });

  it('⛔ Bề rộng tối thiểu phải nới theo số cột — 9 cột ⛔ không vừa khung của 8', () => {
    // ⚠ Khẳng định một SÀN, ⛔ không một con số chính xác: tinh chỉnh px là việc thường xuyên,
    //   còn "bảng bóp chữ thay vì cuộn ngang" là lỗi. Hai chuyện khác nhau (luật 9).
    const px = /^min-w-\[(\d+)px\]$/.exec(BE_RONG_TOI_THIEU_MUC_NUOC);
    expect(px, `BE_RONG_TOI_THIEU_MUC_NUOC sai hình dạng: ${BE_RONG_TOI_THIEU_MUC_NUOC}`).not.toBeNull();
    expect(Number(px![1])).toBeGreaterThanOrEqual(110 * COT_MUC_NUOC.length);
  });

  it('⭐ Cột "Mực nước sông (m)" có mặt — DOD2.3, và ⛔ KHÔNG trùng nghĩa với hai cột cống', () => {
    // Trước 08/09 mực nước của 4 trạm thuỷ văn sông lên cổng dưới tiêu đề "Mực nước hạ lưu (m)".
    expect(COT_MUC_NUOC).toContain('Mực nước sông (m)');
    expect(COT_MUC_NUOC).toContain('Mực nước thượng lưu (m)');
    expect(COT_MUC_NUOC).toContain('Mực nước hạ lưu (m)');
  });

  it('⛔ không tên cột nào rỗng hoặc thừa khoảng trắng', () => {
    for (const ten of [...COT_MUC_NUOC, ...COT_VAN_HANH]) {
      expect(ten.trim()).toBe(ten);
      expect(ten.length).toBeGreaterThan(0);
    }
  });
});
