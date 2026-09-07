import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '../lib/boChuThich';
import { DAI_TOI_DA_NOI_DUNG } from './FeedbackForm';

/**
 * **Trang Góp ý là nửa "ĐỌC" của vòng kiểm duyệt.** CN-01.6 — T36.8.
 *
 * ## Nửa cặp đọc–ghi mà bài này canh
 *
 * Hai khoá `site.feedback.*` được seed ở `V202609071068` và có nơi ghi (màn hình Cấu hình hệ
 * thống). Nửa còn thiếu rất dễ xảy ra và **im lặng hoàn toàn**: Công ty tắt công bố, cổng vẫn
 * hiện danh sách — ⛔ không có gì đỏ ở bất kỳ đâu. Đúng hình dạng luật 27, và đúng thứ lượt 28/8
 * tìm ra sáu lần trong một buổi.
 *
 * ⚠ `PortalSettingsReadTest` phía backend canh vế *"khoá được seed thì phải có người đọc"* — nó
 * chỉ hỏi **chuỗi khoá có xuất hiện trong `public-web/src` hay không**. Nó ⛔ không biết trang có
 * dùng giá trị ấy để **quyết định** điều gì. Bài này canh vế đó.
 *
 * ## Vì sao soi mã nguồn thay vì dựng trang
 *
 * Cùng lý do với `bieuMauLienHeCauHinh.test.ts`: một bản giả trả đủ giá trị sẽ **xanh y hệt** dù
 * trang ghi cứng. Thứ cần khẳng định là *nguồn* của quyết định, nên phải nhìn vào mã.
 *
 * ## ⚠ Phạm vi tự khai (luật 28)
 *
 * Soi **một** trang: `app/gop-y/page.tsx` — nơi duy nhất hiển thị góp ý hôm nay. Nơi thứ hai ra
 * đời (một khối trên trang chủ chẳng hạn) thì phải thêm tên vào {@link NOI_DOC}; nếu không thì
 * cái xanh của bài này ⛔ không nói gì về nó.
 */
const NOI_DOC: { ten: string; nguon: string }[] = [
  {
    ten: 'app/gop-y/page.tsx',
    nguon: readFileSync(join(process.cwd(), 'src/app/gop-y/page.tsx'), 'utf8'),
  },
];

const FEEDBACK_FORM = readFileSync(join(process.cwd(), 'src/components/FeedbackForm.tsx'), 'utf8');

/** Hai khoá phải khớp từng chữ với migration `V202609071068` và với `FeedbackService`. */
const KHOA = ['site.feedback.enabled', 'site.feedback.public-list.enabled'];

describe('Trang Góp ý — CN-01.6, chốt D1', () => {
  it('⚠ vế chống tập rỗng (luật 7): có ít nhất một nơi đọc để soi', () => {
    expect(NOI_DOC.length).toBeGreaterThanOrEqual(1);
  });

  it('⭐⭐ Nơi đọc dùng ĐỦ hai khoá `site.feedback.*` — thiếu một là một công tắc không ai đọc', () => {
    for (const { ten, nguon } of NOI_DOC) {
      for (const khoa of KHOA) {
        expect(
          nguon.includes(khoa),
          `⛔ \`${ten}\` không đọc \`${khoa}\`. Công ty bật/tắt trên màn hình, cổng không đổi ` +
            `gì, và không có gì đỏ ở bất kỳ đâu — nửa cặp đọc–ghi (luật 27, quy tắc 15).`,
        ).toBe(true);
      }
    }
  });

  it('⭐⭐ Mỗi khoá phải QUYẾT ĐỊNH một nhánh — ⛔ không chỉ được đọc rồi bỏ đấy', () => {
    // ⚠ Đây là vế mà `PortalSettingsReadTest` KHÔNG thể canh: nó chỉ hỏi chuỗi khoá có mặt hay
    //   không. Một trang gán `const congBo = docBool(...)` rồi ⛔ không dùng `congBo` ở đâu vẫn
    //   qua được bộ canh backend — và đó chính là một công tắc chưa ai đọc, chỉ khó thấy hơn.
    for (const { ten, nguon } of NOI_DOC) {
      expect(
        /docBool\(\s*config\?\.\['site\.feedback\.enabled'\]/.test(nguon),
        `⛔ \`${ten}\` không ép kiểu \`site.feedback.enabled\` qua \`docBool\` — chuỗi 'false' ` +
          `là truthy trong JavaScript, nên đọc thô là công tắc TẮT trở thành BẬT.`,
      ).toBe(true);
      expect(
        /docBool\(\s*config\?\.\['site\.feedback\.public-list\.enabled'\]/.test(nguon),
        `⛔ \`${ten}\` không ép kiểu \`site.feedback.public-list.enabled\` qua \`docBool\`.`,
      ).toBe(true);
    }
  });

  it('⭐⭐ BA trạng thái rỗng được phân biệt — ⛔ không gộp thành một câu "chưa có góp ý"', () => {
    // ⛔ `null` (backend im lặng) · `[]` + tắt công bố · `[]` + bật công bố là BA sự thật khác
    //    nhau. Gộp lại là bịa ra một sự thật ở hai trong ba trường hợp (luật 9 + quy tắc 16).
    for (const { ten, nguon } of NOI_DOC) {
      expect(
        /feedbacks === null/.test(nguon),
        `⛔ \`${ten}\` không phân biệt "backend không trả lời" với "chưa có góp ý nào". ` +
          `\`apiGet\` trả \`null\` khi hỏng — và một sự cố backend hiện ra thành "chưa ai góp ý" ` +
          `là cổng nói dối về chính nó.`,
      ).toBe(true);
      expect(/length === 0/.test(nguon), `⛔ \`${ten}\` không có nhánh cho danh sách RỖNG.`).toBe(
        true,
      );
    }
  });

  it('⛔⛔ Nội dung dựng bằng TEXT — ⛔ KHÔNG `dangerouslySetInnerHTML` ở bất kỳ đâu', () => {
    // Chữ ở đây do người lạ trên Internet gõ và sau khi duyệt nó hiện cho MỌI người đọc cổng —
    // xa hơn hẳn `contacts`, nơi nạn nhân của XSS lưu trữ chỉ là người quản trị.
    //
    // ⚠⚠ ĐO ĐƯỢC ở lượt chạy đầu: bản trước của bài này ĐỎ trên chính trang nó canh — vì javadoc
    //    của trang *nói ra điều cấm* bằng cách gọi tên nó. Đúng luật 2 (canh cấu trúc, đừng canh
    //    văn bản) và cùng hình dạng với `SeedGateTest` từng khớp trúng một `DELETE FROM articles`
    //    nằm trong lời giải thích. ⇒ bỏ chú thích TRƯỚC khi soi.
    //
    // ⛔ Hệ quả nếu ⛔ không bỏ: người sau xoá dòng ghi chú chứ ⛔ không xoá vi phạm.
    for (const { ten, nguon } of NOI_DOC) {
      const ma = boChuThich(nguon);
      expect(
        ma.includes('getFeedbacks'),
        `⚠ vế đối chứng phải-tìm-thấy: sau khi bỏ chú thích, \`${ten}\` phải còn MÃ. ⛔ Không có ` +
          `vế này thì một hàm \`boChuThich\` hỏng (trả chuỗi rỗng) làm bài này xanh trọn vẹn.`,
      ).toBe(true);
      expect(
        ma.includes('dangerouslySetInnerHTML'),
        `⛔⛔ \`${ten}\` dựng nội dung người dùng thành HTML — XSS lưu trữ nhắm vào mọi khách ` +
          `của cổng.`,
      ).toBe(false);
    }
    const maForm = boChuThich(FEEDBACK_FORM);
    expect(maForm.includes('JSON.stringify')).toBe(true);
    expect(maForm.includes('dangerouslySetInnerHTML')).toBe(false);
  });

  it('⭐ Biểu mẫu nói ra việc KIỂM DUYỆT sau khi gửi — chốt D1 làm mục gửi lên ⛔ không hiện ngay', () => {
    // §10.69: một dòng chữ NÓI DỐI khó thấy hơn hẳn một dòng chữ không ai đọc. "Cảm ơn bạn đã
    // góp ý" trơn để người gửi đi tìm bài của mình bên dưới và kết luận là trang hỏng.
    expect(FEEDBACK_FORM).toMatch(/kiểm duyệt|xem xét trước khi/i);
  });

  it('⭐ Chưa chấm sao thì gửi `null`, ⛔ KHÔNG gửi 0 — quy tắc 16', () => {
    // "0 sao" là một khẳng định về sự hài lòng; "chưa chấm" thì không nói gì. Backend phân biệt
    // hai thứ ấy ở MẪU SỐ của điểm trung bình, nên một số 0 gửi lên làm hỏng số liệu vĩnh viễn.
    expect(FEEDBACK_FORM).toContain('rating: sao');
    expect(
      /rating:\s*sao\s*\?\?\s*0/.test(FEEDBACK_FORM),
      '⛔ `?? 0` biến "chưa chấm" thành "rất không hài lòng"',
    ).toBe(false);
  });

  it('⭐ Trần độ dài của biểu mẫu khớp trần của backend — luật 14, một con số hai nơi nhớ', () => {
    // ⚠ Lệch một chiều nào cũng tệ: trần FE lớn hơn ⇒ người dùng gõ xong mới nhận 400; trần FE
    //   nhỏ hơn ⇒ ô nhập cắt chữ mà ⛔ không nói vì sao.
    expect(DAI_TOI_DA_NOI_DUNG).toBe(2000);
    expect(FEEDBACK_FORM).toContain('maxLength={DAI_TOI_DA_NOI_DUNG}');
  });
});
