import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { CAU_HINH_MAC_DINH } from './ContactForm';

/**
 * **Biểu mẫu liên hệ đọc cấu hình trường, chứ không ghi cứng.** CN-01.4 — T36.7.
 *
 * ## Nửa cặp đọc–ghi mà bài này canh
 *
 * Ba khoá `site.contact.field.*` được seed ở `V202609061067` và có nơi ghi (màn hình Cấu hình giao
 * diện) lẫn nơi đọc ở backend (`ContactFormPolicy`, gác lượt `POST`). Nửa còn thiếu rất dễ xảy ra
 * và **im lặng hoàn toàn**: giao diện vẫn hiện ô Số điện thoại sau khi Công ty đã tắt nó, người dân
 * điền vào, backend nhận bình thường — ⛔ không có gì đỏ ở bất kỳ đâu. Đúng hình dạng luật 27, và
 * đúng thứ lượt 28/8 tìm ra sáu lần trong một buổi.
 *
 * ## Vì sao soi mã nguồn thay vì dựng component
 *
 * Cùng lý do với `siteContactConfig.test.ts`: một bản giả trả đủ giá trị sẽ **xanh y hệt** dù
 * component ghi cứng, vì chuỗi cứng và giá trị cấu hình trông giống nhau khi render. Thứ cần khẳng
 * định là *nguồn* của quyết định, nên phải nhìn vào mã.
 *
 * ## ⚠ Phạm vi tự khai (luật 28)
 *
 * Soi **một** nơi gọi: `app/lien-he/page.tsx`. Đó là nơi gọi DUY NHẤT hôm nay — trang chủ đã bỏ
 * biểu mẫu từ 29/08 và `HomeContactBlock.tsx` bị xoá hẳn. Nơi gọi thứ hai ra đời thì phải thêm tên
 * vào {@link NOI_GOI}, nếu không thì cái xanh của bài này ⛔ không nói gì về nó.
 */
const CONTACT_FORM = readFileSync(join(process.cwd(), 'src/components/ContactForm.tsx'), 'utf8');

/** Mọi nơi dựng `ContactForm`. Thêm nơi thứ hai thì thêm vào đây. */
const NOI_GOI: { ten: string; nguon: string }[] = [
  {
    ten: 'app/lien-he/page.tsx',
    nguon: readFileSync(join(process.cwd(), 'src/app/lien-he/page.tsx'), 'utf8'),
  },
];

/** Ba khoá phải khớp từng chữ với migration `V202609061067` và với `ContactFormPolicy`. */
const KHOA = [
  'site.contact.field.phone.enabled',
  'site.contact.field.email.required',
  'site.contact.field.phone.required',
];

describe('Biểu mẫu liên hệ — trường hiện/bắt buộc do cấu hình quyết định', () => {
  it('⚠ vế chống tập rỗng (luật 7): có ít nhất một nơi gọi để soi', () => {
    expect(NOI_GOI.length).toBeGreaterThanOrEqual(1);
  });

  it('⭐⭐ Nơi gọi đọc ĐỦ ba khoá `site.contact.field.*` — thiếu một là một trường không ai điều khiển', () => {
    for (const { ten, nguon } of NOI_GOI) {
      for (const khoa of KHOA) {
        expect(
          nguon.includes(khoa),
          `⛔ \`${ten}\` không đọc \`${khoa}\`. Công ty bật/tắt trên màn hình, biểu mẫu không đổi ` +
            `gì, và không có gì đỏ ở bất kỳ đâu — nửa cặp đọc–ghi (luật 27).`,
        ).toBe(true);
      }
    }
  });

  it('⭐ `ContactForm` nhận cấu hình qua PROPS — ⛔ không tự gọi API bên trong', () => {
    // ⛔ Một client component tự `fetch` cấu hình là thêm một vòng khứ hồi cho mỗi lượt mở trang,
    //    và nó ⛔ không dùng được bộ đệm ISR mà trang server đã có sẵn.
    expect(CONTACT_FORM).toContain('cauHinh');
    expect(
      CONTACT_FORM.includes('getSiteConfig'),
      '⛔ `ContactForm` tự đọc cấu hình — trang server đã có `config`, truyền xuống là đủ',
    ).toBe(false);
  });

  it('⭐⭐ Vế suy ra "tắt điện thoại ⇒ email bắt buộc" có mặt ở nơi gọi', () => {
    // Bất biến này do BACKEND ép (`ContactFormPolicy.emailBatBuoc()`); nếu giao diện ⛔ không dựng
    // lại nó thì người dân điền xong biểu mẫu rồi nhận 400 mà ⛔ không có ô nào để sửa cho hợp lệ.
    for (const { ten, nguon } of NOI_GOI) {
      expect(
        /!\s*hienDienThoai/.test(nguon),
        `⛔ \`${ten}\` không suy ra "tắt ô điện thoại ⇒ email bắt buộc". Backend CHẶN lượt gửi ` +
          `ấy (ck_contacts_lien_lac đòi ít nhất một cách liên hệ ngược), nên giao diện phải nói ` +
          `trước — nếu không thì biểu mẫu rơi vào trạng thái không điền đúng được.`,
      ).toBe(true);
    }
  });

  it('⭐ Mặc định của giao diện khớp giá trị SEED của migration', () => {
    // ⚠ Luật 14: cùng một mặc định nằm ở hai nơi (migration và mã FE). Bài này là nơi nhớ hộ.
    //   `V202609061067` seed: phone.enabled = true, email.required = false, phone.required = false.
    expect(CAU_HINH_MAC_DINH).toEqual({
      hienDienThoai: true,
      emailBatBuoc: false,
      dienThoaiBatBuoc: false,
    });
  });

  it('⛔ Câu hướng dẫn ⛔ KHÔNG ghi cứng "email hoặc số điện thoại"', () => {
    // §10.69 — một dòng chữ NÓI DỐI khó thấy hơn hẳn một dòng chữ không ai đọc. Khi ô điện thoại
    // đã tắt, câu ấy chỉ vào một ô ⛔ không còn tồn tại.
    const cauCung = /Cần ít nhất <b>một<\/b> cách liên hệ lại: email hoặc số điện thoại/;
    expect(
      cauCung.test(CONTACT_FORM),
      '⛔ Câu hướng dẫn phải đổi theo cấu hình, ⛔ không phải một chuỗi cố định',
    ).toBe(false);
    // Vế đối chứng phải-tìm-thấy: bản có điều kiện vẫn nhắc tới cả hai khả năng.
    expect(CONTACT_FORM).toContain('hienDienThoai');
  });
});
