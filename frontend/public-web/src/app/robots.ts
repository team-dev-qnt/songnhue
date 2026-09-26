import type { MetadataRoute } from 'next';
import { connection } from 'next/server';

import { SITE_URL } from '@/lib/site';

/**
 * `robots.txt` sinh động theo `SITE_URL` (T9.3, sửa nhà của biến ở T68.12).
 *
 * ⚠ Chặn `/api/` là bắt buộc: dưới đó có `/api/revalidate` (nhận webhook có bí mật) và
 * `/api/health`. Không có gì để lập chỉ mục, mà lại là đường dẫn không nên quảng bá.
 *
 * ⚠ Staging và production **dùng chung mã nguồn này** — và từ khi có CD, dùng chung cả
 * **ảnh Docker**. Nếu staging cũng cho lập chỉ mục thì Google có hai bản của cùng nội dung
 * và có thể xếp bản staging lên trước — nên chặn theo `SITE_URL`, chứ không hard-code.
 *
 * ⛔⛔ `await connection()` là **một nửa của bản vá T68.12, và là nửa dễ quên nhất.**
 *
 * Đo bằng `next build` ngày 24/09: trong 20 route của cổng, **`/robots.txt` là route DUY
 * NHẤT** Next chọn prerender tĩnh (`○`) — 18 route kia và cả `/sitemap.xml` đều là `ƒ`, vì
 * chúng `await` dữ liệu. Hàm này thì không: nó chỉ đọc một biến, nên Next kết luận kết quả
 * bất biến và **nướng tệp vào ảnh lúc build**.
 *
 * Hệ quả: đổi `SITE_URL` sang đọc-lúc-chạy mà bỏ dòng này thì mọi thứ *trông như* đã vá —
 * `sitemap.xml` đúng, canonical đúng — trong khi **đúng tệp mà dòng nợ tố cáo** vẫn khai
 * `Allow: /` + `Host: <production>` trên staging, và ⛔ cổng kiểm nào báo. Đây là luật 3 ở
 * dạng khó thấy nhất: giá trị *đã giải* đúng rồi mà vẫn giải **sai lúc**.
 *
 * ⛔ Và công tắc phải là `connection()`, **⛔ phải `force-dynamic`** — `noBuildTimePrerender.test.ts`
 * cấm `force-dynamic` trên mọi route của cổng, với lý do đúng: nó hạ mặc định fetch xuống
 * `no-store`, tức backend phải trả lời MỌI lượt truy cập thay vì 1 lần / 5 phút. Ở đây hàm ⛔ gọi
 * fetch nào nên cái giá ấy ⛔ phát sinh, nhưng *"một luật ⛔ có trường hợp riêng là một luật ⛔ ai
 * phải nhớ"* — và `connection()` cho đúng kết quả mà ⛔ cần một dòng ngoại lệ.
 *
 * ⚠ Mọi lượt đọc API của cổng đã đi qua `connection()` ở `apiGetWithMeta` (chokepoint, luật 12).
 * Hàm này là ca **ngoài** chokepoint — nó ⛔ đọc API, chỉ đọc một biến — nên nó phải tự gọi. Đó
 * cũng chính là lý do nó là route DUY NHẤT còn `○`.
 *
 * ⇒ Cái giá là mỗi lượt gọi `/robots.txt` chạy một hàm thuần — không truy vấn, không I/O.
 * `robots.test.ts` neo vào cả hai vế (gọi `connection()` + đọc `process.env` lúc gọi).
 */
export default async function robots(): Promise<MetadataRoute.Robots> {
  await connection();
  const isProduction = !/localhost|staging|127\.0\.0\.1/i.test(SITE_URL);

  if (!isProduction) {
    return {
      rules: [{ userAgent: '*', disallow: '/' }],
    };
  }

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/api/'],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
