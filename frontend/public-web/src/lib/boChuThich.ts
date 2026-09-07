/**
 * Bỏ chú thích khỏi một tệp nguồn trước khi soi nó bằng biểu thức chính quy.
 *
 * <h2>Vì sao là một module riêng, không nằm trong tệp test</h2>
 *
 * Hai bộ canh cần đúng hàm này: `noFabricatedContent.test.ts` (dữ liệu bịa) và
 * `noHardcodedColors.test.ts` (mã màu ghi cứng). Bản đầu để bộ thứ hai `import` thẳng từ tệp
 * test của bộ thứ nhất — chạy được, nhưng Vitest nạp tệp ấy như một suite nên **toàn bộ 23 bài
 * của nó chạy lại lần thứ hai** trong mỗi lượt (đo được: 28 bài báo cáo cho 5 bài đã viết).
 * Chép hàm ra hai bản thì tệ hơn nữa: hai bộ canh sẽ trôi ra khỏi nhau đúng lúc ai đó sửa một
 * bản (quy tắc 14).
 *
 * <h2>Vì sao cả hai bộ canh đều cần nó</h2>
 *
 * Chú thích *giải thích* một vi phạm phải được phép nhắc tới nó — ghi chú lịch sử của
 * `SiteFooter` cần gọi tên `#061b37`, và ghi chú của §10.54 cần gọi tên bộ dữ liệu bịa đã gỡ.
 * Cấm cả trong chú thích là buộc người sau mô tả lịch sử mà không được gọi tên nó, và hệ quả
 * thực tế là người ta xoá ghi chú chứ không xoá vi phạm.
 */
/**
 * Bỏ chú thích trước khi soi.
 *
 * ⛔ Bắt buộc, và đã suýt trả giá ngay trong lượt viết bản vá này: chú thích giải thích *vì sao*
 *    một hằng số bị xoá thường **trích dẫn lại chính thứ bị cấm**. Bản đầu của lượt vá có một
 *    phép khẳng định đỏ oan vì đúng lý do đó — cùng hình dạng với việc `SeedGateTest` từng khớp
 *    trúng một `DELETE FROM articles` nằm trong lời giải thích (luật 2).
 *
 * ⚠ `//` chỉ được coi là mở chú thích khi nó **không nằm trong chuỗi** — đếm dấu nháy chưa bị
 *   thoát đứng trước nó trên cùng dòng. Nếu không thì `'https://…'` bị cắt mất và bài canh tên
 *   miền bên dưới sẽ xanh trong khi hotlink vẫn còn nguyên.
 */
export function boChuThich(nguon: string): string {
  const khongKhoi = nguon.replace(/\/\*[\s\S]*?\*\//g, '');
  return khongKhoi
    .split('\n')
    .map((dong) => {
      for (let i = 0; i < dong.length - 1; i++) {
        if (dong[i] !== '/' || dong[i + 1] !== '/') continue;
        const truoc = dong.slice(0, i);
        const soNhay = (truoc.match(/(?<!\\)['"`]/g) ?? []).length;
        if (soNhay % 2 === 0) return truoc;
      }
      return dong;
    })
    .join('\n');
}
