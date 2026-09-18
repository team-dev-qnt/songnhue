import { describe, expect, it } from 'vitest';

import { COT_TRANG_CHU_MUC_NUOC, COT_VAN_HANH } from './homeDataColumns';

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
    // 8 tới 08/09/2026; cột thứ 9 "Mực nước sông" thêm ở DOD2.3.
    // ⭐ WS-44 đổi HÌNH DẠNG bảng (một dòng một CÔNG TRÌNH, TL/HL cạnh nhau) nhưng ⛔ KHÔNG đổi
    //    số cột — và chính bài này bắt được bản đầu của WS-44 làm rơi cột "MN sông / Bể hút",
    //    thứ khiến 5/14 công trình hiện dòng trống trơn.
    expect(COT_TRANG_CHU_MUC_NUOC).toHaveLength(9);
  });

  it('⭐ CN-02.11 — tình hình vận hành có ĐỦ 6 cột', () => {
    expect(COT_VAN_HANH).toHaveLength(6);
  });

  it('⛔ cột "Lượng mưa" KHÔNG được bỏ dù v1 chắc chắn hiển thị `-` (G3)', () => {
    // Đây là khoảng trống Công ty cần nhìn thấy. Bỏ cột đi thì bảng trông đầy đủ trong khi
    // một nguồn dữ liệu vẫn đang thiếu — cùng họ với §10.54 ở chiều ngược lại.
    expect(COT_TRANG_CHU_MUC_NUOC.some((c: string) => c.includes('Lượng mưa'))).toBe(true);
  });

  it('⛔ không cột nào trùng tên — bảng hai cột giống nhau là bảng đọc sai', () => {
    for (const cot of [COT_TRANG_CHU_MUC_NUOC, COT_VAN_HANH]) {
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
  /**
   * ⭐ WS-44 — hai bài về **lưới CSS** ở đây đã bị xoá cùng `LUOI_MUC_NUOC` /
   * `BE_RONG_TOI_THIEU_MUC_NUOC`: bảng nay là `<table>` thật (§6.1.2 đòi gộp ô, thứ `div` ⛔ không
   * làm được), nên bề rộng cột do trình duyệt chia và ⛔ không còn một chuỗi `grid-cols-[…]` nào
   * để đếm ô.
   *
   * <p>⛔ Bất biến chúng canh (*"thêm một cột mà quên lưới thì phải đỏ"*) ⛔ không mất — nó chuyển
   * sang `bangLuoiMucNuoc.test.ts`, nơi khẳng định bảng dùng `rowSpan`/`colSpan` thật và cuộn
   * ngang trong vùng của chính nó.
   */
  it('⭐ Ba chỉ tiêu mực nước đều có cột riêng — DOD2.3, và ⛔ KHÔNG trùng nghĩa nhau', () => {
    // Trước 08/09 mực nước của 4 trạm thuỷ văn sông lên cổng dưới tiêu đề "Mực nước hạ lưu".
    // ⚠ Cột thứ ba này là thứ bản đầu của WS-44 làm rơi, khiến 5/14 công trình trống trơn.
    expect(COT_TRANG_CHU_MUC_NUOC).toContain('Thượng lưu');
    expect(COT_TRANG_CHU_MUC_NUOC).toContain('Hạ lưu');
    expect(COT_TRANG_CHU_MUC_NUOC).toContain('MN sông / Bể hút');
  });

  it('⭐ Cột "Chênh lệch" có mặt — §6.1.2, và nó là dòng/cột TỰ TÍNH ở BE', () => {
    expect(COT_TRANG_CHU_MUC_NUOC).toContain('Chênh lệch');
  });

  it('⛔ không tên cột nào rỗng hoặc thừa khoảng trắng', () => {
    for (const ten of [...COT_TRANG_CHU_MUC_NUOC, ...COT_VAN_HANH]) {
      expect(ten.trim()).toBe(ten);
      expect(ten.length).toBeGreaterThan(0);
    }
  });
});
