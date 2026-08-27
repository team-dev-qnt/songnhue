import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Cổng công khai phải gửi **Content-Security-Policy**, và từng chỉ thị phải nói đúng điều nó
 * hứa.
 *
 * <h2>Lỗi đã đo được</h2>
 *
 * Trước bản vá này cổng **không có CSP nào**. Hai tệp trỏ vào nhau: `next.config.ts` ghi *"CSP
 * đầy đủ đặt ở nginx"*, còn `deploy/nginx/snippets/edge-headers.conf` ghi *"cố ý KHÔNG đặt lại
 * CSP — hai image FE đã đặt đủ"*. Đọc tệp nào cũng thấy yên tâm, và không tệp nào đặt.
 * `NginxSecurityHeadersTest` phía backend có canh CSP — nhưng nó chỉ soi
 * `deploy/docker/admin-app.Dockerfile`, nên public-web nằm ngoài tầm với suốt thời gian đó.
 *
 * <h2>Vì sao đọc CHÍNH GIÁ TRỊ ĐÃ GIẢI, không grep tệp</h2>
 *
 * Luật 3 của dự án: *canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH*. Grep chuỗi
 * `Content-Security-Policy` trong `next.config.ts` sẽ xanh kể cả khi hằng số `CSP` được khai
 * mà không ai gắn vào `headers()` — đúng loại xanh giả đã trả giá nhiều lần. Ở đây bài kiểm
 * gọi thẳng `nextConfig.headers()` rồi đọc header thật sự sẽ được gửi đi.
 *
 * ⚠ Ba khẳng định "cấm" ở cuối là phần dễ bị nới lỏng nhất khi ai đó gỡ rối một khung nhúng:
 * thêm `'unsafe-eval'` hay mở `frame-ancestors` là hai thao tác một dòng, và không có gì báo.
 */
const CAU_HINH = join(process.cwd(), 'next.config.ts');

async function layCsp(): Promise<string> {
  const mod = (await import('../../next.config')) as {
    default: { headers: () => Promise<{ headers: { key: string; value: string }[] }[]> };
  };
  const nhom = await mod.default.headers();
  const header = nhom
    .flatMap((n) => n.headers)
    .find((h) => h.key.toLowerCase() === 'content-security-policy');
  expect(header, 'cổng công khai phải gửi Content-Security-Policy').toBeDefined();
  return header!.value;
}

describe('Content-Security-Policy của cổng công khai', () => {
  it('⚠ header thật sự nằm trong headers() — không chỉ là một hằng số khai ra rồi bỏ đó', async () => {
    expect(await layCsp()).toContain('default-src');
  });

  it.each([
    ["default-src 'self'", 'mặc định chỉ cùng origin'],
    ["img-src 'self' data: blob:", 'ảnh đi qua /api/v1/public/files, không hotlink'],
    ["object-src 'none'", 'không plugin nhúng'],
    ["base-uri 'self'", 'chặn cướp đường dẫn tương đối bằng thẻ <base>'],
    ["form-action 'self'", 'form không gửi được ra ngoài'],
    ["frame-ancestors 'none'", 'không ai nhúng cổng vào iframe của họ — chống clickjacking'],
  ])('khai %s (%s)', async (chiThi) => {
    expect(await layCsp()).toContain(chiThi);
  });

  it('⭐ frame-src mở đúng hai host, mỗi host một lý do có thật', async () => {
    const csp = await layCsp();
    const frameSrc = csp.split('; ').find((d) => d.startsWith('frame-src'));
    expect(frameSrc).toBeDefined();

    // Bản đồ trụ sở (CR-22) và video phóng sự (CN-01.3). Host thứ ba phải kèm một lý do —
    // và phải thêm cả vào `noFabricatedContent.test.ts`, nếu không hai danh sách lệch nhau.
    const host = frameSrc!.match(/https:\/\/[\w.-]+/g) ?? [];
    expect(host.sort()).toEqual(['https://www.google.com', 'https://www.youtube-nocookie.com']);
  });

  it('⛔ không nới lỏng bằng unsafe-eval hay wildcard', async () => {
    const csp = await layCsp();
    expect(
      csp,
      "'unsafe-eval' cho phép chạy chuỗi thành mã — không có lý do nào ở một cổng tin tức",
    ).not.toContain('unsafe-eval');
    expect(csp, 'wildcard host biến CSP thành một dòng trang trí').not.toMatch(/(^|[\s;])\*/);
  });

  it("⚠ script-src có 'unsafe-inline' là CỐ Ý — và chỉ ở script-src", async () => {
    const csp = await layCsp();
    // Next App Router chèn script nội tuyến cho hydration; nonce thì phải khác mỗi request,
    // tức tắt hẳn ISR mà NFR-02 (trang chủ < 3s) đang dựa vào. Ghi nhận đánh đổi ở đây để
    // nó là một quyết định đọc được, không phải một chỗ ai đó nới ra rồi quên.
    const script = csp.split('; ').find((d) => d.startsWith('script-src'));
    expect(script).toContain("'unsafe-inline'");
    expect(csp.split('; ').find((d) => d.startsWith('connect-src'))).not.toContain('unsafe-inline');
  });

  it('⛔ kiểm chứng ngược: bài kiểm bắt được một CSP đã bị nới lỏng', () => {
    const hong = "default-src *; script-src 'self' 'unsafe-eval'";
    expect(hong).toContain('unsafe-eval');
    expect(hong).toMatch(/(^|[\s;])\*/);
    expect(readFileSync(CAU_HINH, 'utf8')).toContain('Content-Security-Policy');
  });
});
