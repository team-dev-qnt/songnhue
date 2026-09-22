import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { dungCsp, sinhNonce } from './csp';

/**
 * Cổng công khai phải gửi **Content-Security-Policy**, và từng chỉ thị phải nói đúng điều nó hứa.
 *
 * <h2>Lỗi đã đo được (27/08/2026)</h2>
 *
 * Trước bản vá WS-24 cổng **⛔ có CSP nào**. Hai tệp trỏ vào nhau: `next.config.ts` ghi *"CSP đầy đủ
 * đặt ở nginx"*, còn `deploy/nginx/snippets/edge-headers.conf` ghi *"cố ý KHÔNG đặt lại CSP — hai
 * image FE đã đặt đủ"*. Đọc tệp nào cũng thấy yên tâm, và ⛔ tệp nào đặt. `NginxSecurityHeadersTest`
 * phía backend có canh CSP — nhưng nó chỉ soi `admin-app.Dockerfile`, nên public-web nằm ngoài tầm
 * với suốt thời gian đó (§10.61, luật 28).
 *
 * <h2>⭐⭐ Đổi 20/09/2026 — T73.7: `script-src` thôi `'unsafe-inline'`</h2>
 *
 * <p>Lý do cũ ghi ngay trong `next.config.ts`: *"nonce phải khác nhau mỗi request ⇒ mọi trang thành
 * động ⇒ ISR tắt hẳn, mà NFR-02 dựa vào ISR"*. **Đo lại bằng `next build` thì lý do ấy đã ĐỔ**:
 * 18/19 route vốn đã là `ƒ (Dynamic)` và bảng ⛔ có một dòng `●` (ISR) nào — vì `apiGetWithMeta` gọi
 * `await connection()` trước mọi lượt fetch. Cái giá ấy **đã trả từ lâu**, nonce cộng thêm **0**.
 * Dựng lại sau khi thêm middleware: bảng route **y hệt**, `/robots.txt` vẫn `○`.
 *
 * <h2>Vì sao ĐỌC HÀM, ⛔ grep tệp</h2>
 *
 * Luật 3: *canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH*. Grep một chuỗi trong `next.config.ts`
 * sẽ xanh kể cả khi hằng số được khai mà ⛔ ai gắn vào `headers()`.
 *
 * <p>⚠⚠ **Và lớp này một mình ⛔ đủ.** Nó khẳng định *chuỗi CSP dựng ra đúng*; nó ⛔ nói được rằng
 * **thẻ `<script>` của Next có mang đúng nonce ấy ⛔** — mà thiếu vế đó thì cổng **trắng trang** ở
 * trình duyệt người dùng trong khi mọi bài ở đây vẫn xanh (luật 5 · luật 7). Vế ấy đo bằng một máy
 * chủ THẬT, số đo ghi ở `middleware.ts` và trong sổ (`T73.7`): 11/11 thẻ `<script>` mang đúng nonce
 * của header, **0** thẻ thiếu, và nonce khác nhau ở cả ba lượt gọi.
 */
const CAU_HINH = join(process.cwd(), 'next.config.ts');
const MIDDLEWARE = join(process.cwd(), 'src/middleware.ts');

const NONCE_MAU = 'r4nd0mNonceBase64==';

describe('Content-Security-Policy của cổng công khai', () => {
  it.each([
    ["default-src 'self'", 'mặc định chỉ cùng origin'],
    ["img-src 'self' data: blob:", 'ảnh đi qua /api/v1/public/files, ⛔ hotlink'],
    ["object-src 'none'", '⛔ plugin nhúng'],
    ["base-uri 'self'", 'chặn cướp đường dẫn tương đối bằng thẻ <base>'],
    ["form-action 'self'", 'form ⛔ gửi được ra ngoài'],
    ["frame-ancestors 'none'", '⛔ ai nhúng cổng vào iframe của họ — chống clickjacking'],
  ])('khai %s (%s)', (chiThi) => {
    expect(dungCsp(NONCE_MAU)).toContain(chiThi);
  });

  it('⭐ frame-src mở đúng BA host, mỗi host một lý do có thật', () => {
    const frameSrc = dungCsp(NONCE_MAU)
      .split('; ')
      .find((d) => d.startsWith('frame-src'));
    expect(frameSrc).toBeDefined();

    // Bản đồ trụ sở (CR-22) · video phóng sự (CN-01.3) · video nhúng trong thân bài (T84.10).
    // Host thứ tư phải kèm một lý do có thật ở `csp.ts`.
    //
    // ⚠ Câu cũ ở đây dặn *"phải thêm cả vào `noFabricatedContent.test.ts`"* — ĐO 22/09 thì sai:
    //   bộ canh ấy quét bằng `timTsx()`, chỉ `.tsx`, nên `csp.ts` nằm ngoài tầm. Thêm một mục vào
    //   danh sách ⛔ ai đọc là luật 15.
    const host = frameSrc!.match(/https:\/\/[\w.-]+/g) ?? [];
    expect(host.sort()).toEqual([
      'https://player.vimeo.com',
      'https://www.google.com',
      'https://www.youtube-nocookie.com',
    ]);
  });

  it('⭐⭐ Vimeo có mặt ở CẢ HAI đầu — bộ lọc HTML và CSP cổng', () => {
    // ⛔⛔ Đây là cặp đã LỆCH NHAU: `HtmlSanitizer.MIEN_NHUNG_VIDEO` cho `player.vimeo.com` đi qua
    //   từ WS-40, `VideoEmbed.toEmbedUrl` dựng đúng URL nhúng — mà CSP cổng thì ⛔ có. Biên tập
    //   viên thấy video chạy ở màn soạn bài; độc giả nhận KHUNG TRẮNG, lỗi chỉ hiện trong console.
    //   Một nửa cặp đọc–ghi ở dạng chỉ trình duyệt của người dùng cuối mới thấy (luật 14 · luật 27).
    // `process.cwd()` là `frontend/public-web` — cùng khuôn `CAU_HINH`/`MIDDLEWARE` ở trên.
    const sanitizer = readFileSync(
      join(
        process.cwd(),
        '../../backend/core/src/main/java/com/songnhue/core/common/util/HtmlSanitizer.java',
      ),
      'utf8',
    );
    expect(sanitizer).toContain('player.vimeo.com');
    expect(dungCsp(NONCE_MAU)).toContain('https://player.vimeo.com');
  });

  it('⭐⭐ `media-src` nhận gốc kho truyền vào — video đi qua 302 sang MinIO (T84.13)', () => {
    const csp = dungCsp(NONCE_MAU, 'https://files.songnhue.com');
    expect(csp).toContain("media-src 'self' https://files.songnhue.com");
  });

  it("⭐ nhánh RỖNG cho đúng `media-src 'self'`, ⛔ dư khoảng trắng", () => {
    // ⛔⛔ `"media-src 'self' "` trông giống hệt nhưng làm mọi phép so chuỗi lệch đi — và nó là
    //    trạng thái MẶC ĐỊNH ở máy dev, nơi `MEDIA_ORIGIN` ⛔ đặt. Một lỗi chỉ hiện ở chỗ khác.
    const csp = dungCsp(NONCE_MAU);
    const chiThi = csp.split('; ').find((d) => d.startsWith('media-src'));
    expect(chiThi).toBe("media-src 'self'");
  });

  it('⭐⭐ middleware ĐỌC biến lúc chạy bằng `||`, ⛔ `??` (luật 3)', () => {
    // Docker gán CHUỖI RỖNG cho một biến ⛔ truyền, và `??` giữ nguyên chuỗi rỗng ấy — đúng chỗ
    // §10.38 đã trả giá. Canh ở đây vì hậu quả (`media-src` dư khoảng trắng) hiện ở tệp này.
    const mw = readFileSync(MIDDLEWARE, 'utf8');
    expect(mw).toContain('process.env.MEDIA_ORIGIN ||');
    expect(mw).not.toContain('process.env.MEDIA_ORIGIN ??');
  });

  it('⛔ ⛔ nới lỏng bằng unsafe-eval hay wildcard', () => {
    const csp = dungCsp(NONCE_MAU);
    expect(
      csp,
      "'unsafe-eval' cho phép chạy chuỗi thành mã — ⛔ lý do nào ở một cổng tin tức",
    ).not.toContain('unsafe-eval');
    expect(csp, 'wildcard host biến CSP thành một dòng trang trí').not.toMatch(/(^|[\s;])\*/);
  });

  it("⛔⛔ script-src mang NONCE và THÔI 'unsafe-inline' (T73.7 · ASVS 14.4.3)", () => {
    const script = dungCsp(NONCE_MAU)
      .split('; ')
      .find((d) => d.startsWith('script-src'));

    expect(script).toContain(`'nonce-${NONCE_MAU}'`);
    expect(
      script,
      '⛔⛔ Đây là chỉ thị làm 5.2.7/5.3.3 CHẠY ĐƯỢC mỗi khi một lớp khác hở (SVG T61.32, ' +
        "`javascript:` T61.34/T73.2). Để lại `'unsafe-inline'` cạnh một nonce còn tệ hơn: trình " +
        'duyệt hiện đại BỎ QUA nó, nên nó chỉ là một dòng trang trí làm lượt rà sau đọc nhầm.',
    ).not.toContain('unsafe-inline');
  });

  it("⚠ style-src GIỮ 'unsafe-inline' — đánh đổi khác hẳn, ghi ra để nó là một quyết định", () => {
    const style = dungCsp(NONCE_MAU)
      .split('; ')
      .find((d) => d.startsWith('style-src'));
    expect(
      style,
      'Next chèn style nội tuyến cho từng đoạn CSS của route. Inline STYLE ⛔ chạy được mã nên rủi ' +
        'ro thấp hơn hẳn inline SCRIPT; siết vế này là một lượt riêng có phép đo.',
    ).toContain("'unsafe-inline'");
  });

  it('⛔⛔ Nonce RỖNG phải NÉM — ⛔ được lặng lẽ rơi về một CSP yếu hơn', () => {
    expect(() => dungCsp('')).toThrow();
  });

  it('⛔⛔ `sinhNonce` phải cho giá trị KHÁC NHAU — nonce lặp là ⛔ có nonce', () => {
    const tap = new Set(Array.from({ length: 50 }, () => sinhNonce()));
    expect(tap.size, 'trùng một lần trong 50 lượt nghĩa là nguồn ngẫu nhiên hỏng').toBe(50);
    expect(sinhNonce()).toMatch(/^[A-Za-z0-9+/]+={0,2}$/);
  });

  it('⛔⛔⛔ CHỈ MỘT nơi đặt CSP — hai nơi là đúng cái bẫy đã làm cổng chạy ⛔ CSP suốt WS-16→27/08', () => {
    expect(
      readFileSync(CAU_HINH, 'utf8'),
      '⛔⛔ `next.config.ts` ⛔ được đặt lại `Content-Security-Policy`: `headers()` giải MỘT LẦN lúc ' +
        'build nên nonce sẽ bị nướng cứng vào image — một nonce cố định còn tệ hơn `unsafe-inline` ' +
        'vì nó TRÔNG như đã siết. Và hai nguồn cho một header là §10.61 lặp lại.',
    ).not.toMatch(/key:\s*'Content-Security-Policy'/);

    const mw = readFileSync(MIDDLEWARE, 'utf8');
    expect(
      mw,
      '⛔⛔ Thiếu header của RESPONSE ⇒ trình duyệt ⛔ thi hành gì — đúng trạng thái cổng đã nằm ' +
        'từ WS-16 tới 27/08 (§10.61). Đây là vế BẮT BUỘC.',
    ).toMatch(/phanHoi\.headers\.set\('Content-Security-Policy'/);

    // ⚠⚠ Hai khẳng định dưới là BÁNH CÓC, ⛔ phải "thiếu là trắng trang" — bản đầu của bài này
    //    viết đúng câu ấy và **lượt đo đã bác**: trên Next 16.3.5, gỡ CSP-của-request (giữ
    //    `x-nonce`) ⇒ 11/11 thẻ <script> VẪN mang đúng nonce; gỡ `x-nonce` (giữ CSP-của-request)
    //    ⇒ cũng 11/11. Mỗi header một mình đã đủ. Giữ cả hai vì tài liệu Next chỉ cam kết MỘT
    //    trong hai, và một lượt nâng bỏ đi đường còn lại sẽ làm cổng trắng trang — lớp lỗi ⛔ cổng
    //    kiểm nào ở kho nhìn thấy.
    expect(
      mw,
      'Next đọc nonce từ header `Content-Security-Policy` của REQUEST (đường tài liệu cam kết)',
    ).toMatch(/headerYeuCau\.set\('Content-Security-Policy'/);
    expect(
      mw,
      'Next cũng đọc được từ `x-nonce`, và đó là đường mã ứng dụng lấy nonce qua `headers()`',
    ).toMatch(/set\('x-nonce'/);
  });

  it('⚠ TỰ KIỂM — bài kiểm bắt được một CSP đã bị nới lỏng', () => {
    const hong = "default-src *; script-src 'self' 'unsafe-eval' 'unsafe-inline'";
    expect(hong).toContain('unsafe-eval');
    expect(hong).toMatch(/(^|[\s;])\*/);
    expect(hong.split('; ').find((d) => d.startsWith('script-src'))).toContain('unsafe-inline');
  });
});
