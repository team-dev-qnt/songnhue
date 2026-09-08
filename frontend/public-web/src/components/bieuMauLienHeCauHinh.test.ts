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

/**
 * Migration seed/đổi các khoá `site.contact.field.*`, theo THỨ TỰ ÁP DỤNG.
 *
 * ⚠ Danh sách chứ ⛔ không một tệp: `V202609061067` seed ba khoá, `V202609081071` thêm hai khoá và
 * **đổi mặc định** của `email.required`. Đọc một tệp là đúng lỗ hổng phạm vi §10.62 —
 * `PortalSettingsReadTest` soi mỗi một migration nên mọi khoá seed sau đó đi lọt (luật 28).
 */
const MIGRATION_LIEN_HE = [
  '../../backend/content/src/main/resources/db/migration/cms/V202609061067__cms_contact_form_va_recaptcha.sql',
  '../../backend/content/src/main/resources/db/migration/cms/V202609081071__cms_contact_truong_tat_duoc.sql',
];

/** Giá trị mặc định CUỐI CÙNG của mỗi khoá sau khi áp lần lượt mọi migration. */
function macDinhTrongMigration(): Record<string, boolean> {
  const ket: Record<string, boolean> = {};

  for (const tuongDoi of MIGRATION_LIEN_HE) {
    const sql = readFileSync(join(process.cwd(), tuongDoi), 'utf8');

    // Dạng 1 — hàng seed: ('site.contact.field.x', 'true', 'BOOLEAN',
    for (const m of sql.matchAll(/\('(site\.contact\.field\.[a-z-]+\.[a-z-]+)',\s*'(true|false)',\s*'BOOLEAN'/g)) {
      ket[m[1]] = m[2] === 'true';
    }

    // Dạng 2 — đổi mặc định: UPDATE … SET … default_value = 'true' … WHERE setting_key = '…'
    for (const khoi of sql.split(/\bUPDATE\s+settings\b/).slice(1)) {
      const giaTri = /default_value\s*=\s*'(true|false)'/.exec(khoi);
      const khoa = /setting_key\s*=\s*'(site\.contact\.field\.[a-z-]+\.[a-z-]+)'/.exec(khoi);
      if (giaTri && khoa) {
        ket[khoa[1]] = giaTri[1] === 'true';
      }
    }
  }
  return ket;
}

/** Mọi nơi dựng `ContactForm`. Thêm nơi thứ hai thì thêm vào đây. */
const NOI_GOI: { ten: string; nguon: string }[] = [
  {
    ten: 'app/lien-he/page.tsx',
    nguon: readFileSync(join(process.cwd(), 'src/app/lien-he/page.tsx'), 'utf8'),
  },
];

/**
 * Năm khoá phải khớp từng chữ với migration (`V202609061067` + `V202609081071`) và với
 * `ContactFormPolicy`.
 *
 * ⭐ Hai khoá cuối thêm 08/09/2026 (T28.49). ⚠ Danh sách này là chỗ luật 27 dễ hở nhất: thêm một
 * khoá vào migration mà quên thêm vào đây thì bộ canh vẫn XANH cho một trường ⛔ không ai điều
 * khiển — đúng thứ nó sinh ra để chặn.
 */
const KHOA = [
  'site.contact.field.phone.enabled',
  'site.contact.field.email.required',
  'site.contact.field.phone.required',
  'site.contact.field.full-name.enabled',
  'site.contact.field.subject.enabled',
];

describe('Biểu mẫu liên hệ — trường hiện/bắt buộc do cấu hình quyết định', () => {
  it('⚠ vế chống tập rỗng (luật 7): có ít nhất một nơi gọi để soi', () => {
    expect(NOI_GOI.length).toBeGreaterThanOrEqual(1);
  });

  it('⭐⭐ Nơi gọi đọc ĐỦ năm khoá `site.contact.field.*` — thiếu một là một trường không ai điều khiển', () => {
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

  /**
   * ⛔⛔ Bài này ĐỌC migration, ⛔ KHÔNG chép lại giá trị của nó.
   *
   * Bản trước khẳng định `toEqual({ emailBatBuoc: false, … })` kèm một chú thích *"`V202609061067`
   * seed: email.required = false"*. Đó ⛔ không phải một phép đối chiếu — đó là **bản sao thứ hai**
   * của cùng một giá trị, và một bản sao thì lệch trong im lặng. Nó đúng cho tới 08/09/2026, khi
   * `V202609081071` đổi mặc định thành `true` (T28.49); lúc ấy bài đỏ và **⛔ không nói được nguồn
   * sự thật nằm ở đâu** — người sửa dễ nhất là đổi con số trong bài kiểm cho hết đỏ.
   *
   * ⚠ Đây là luật 14 làm nửa vời: nó nhớ hộ *một con số*, thay vì nhớ hộ *quan hệ giữa hai nguồn*.
   * Bản này trích thẳng từ SQL, nên thêm một migration đổi mặc định là bài tự cập nhật theo — và
   * quên đổi mã FE thì đỏ, kèm tên khoá cụ thể.
   */
  it('⭐⭐ Mặc định của giao diện khớp giá trị SEED ĐỌC TỪ migration', () => {
    const seed = macDinhTrongMigration();

    // Vế chống tập rỗng (luật 7 + 29) đứng TRƯỚC: regex khớp hụt trả map rỗng, và mọi so sánh
    // dưới đây sẽ `undefined === undefined` — xanh trọn vẹn mà ⛔ không canh gì.
    expect(Object.keys(seed).sort()).toEqual([...KHOA].sort());

    expect(CAU_HINH_MAC_DINH.hienDienThoai).toBe(seed['site.contact.field.phone.enabled']);
    expect(CAU_HINH_MAC_DINH.emailBatBuoc).toBe(seed['site.contact.field.email.required']);
    expect(CAU_HINH_MAC_DINH.dienThoaiBatBuoc).toBe(seed['site.contact.field.phone.required']);
    expect(CAU_HINH_MAC_DINH.hienHoTen).toBe(seed['site.contact.field.full-name.enabled']);
    expect(CAU_HINH_MAC_DINH.hienTieuDe).toBe(seed['site.contact.field.subject.enabled']);
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
