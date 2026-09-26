import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

/**
 * ⛔⛔ **ASVS 13.1.1 — vế proxy Next: thứ tới Spring còn là thứ trình duyệt gửi ⛔** (T85.14).
 *
 * <h2>Vì sao câu này phải hỏi</h2>
 *
 * Route Handler ⛔ chuyển tiếp nguyên văn: nó nhận `[...path]` **đã giải mã** từ Next rồi
 * **dựng lại** đường dẫn bằng `path.map(encodeURIComponent).join('/')`. Proxy này là đường **DUY
 * NHẤT** trình duyệt gọi API ở cổng công khai (dựng ở T11.5 để tránh CORS), nên mọi phép kiểm đường
 * dẫn của Spring chỉ đúng nếu proxy ⛔ làm méo đường dẫn.
 *
 * Đo 24/09 trên Tomcat nhúng: `a%2Fb` ⇒ **400** · `a/b` ⇒ **404** ⇒ `%2F` bị từ chối TRƯỚC định
 * tuyến (`DauVaoMaHoaHieuXacDinhHttpTest`). Nhưng nếu Next tách `a%2Fb` thành **hai** đoạn thì proxy
 * ghép lại thành `a/b`, và **bức tường 400 ấy ⛔ bao giờ được chạm tới**.
 *
 * <h2>⭐⭐ Số đo THẬT, 26/09/2026 — ⛔ phải suy luận</h2>
 *
 * Dòng nợ kê một bài vitest tự truyền `params.path`. Bài như thế khẳng định **giả định của người
 * viết** về cách Next tách đoạn, ⛔ phải hành vi thật — đúng thứ luật 29 gọi tên (*chép lại lỗi thay
 * vì bắt nó*). Nên câu (a) được đo bằng một lượt chạy **đầu-tới-cuối**:
 *
 * <pre>
 *   node public-web/server.js            ← ĐÚNG lệnh container chạy (`public-web.Dockerfile:62`).
 *                                          ⚠ `next start` ⛔ dùng được: Next tự cảnh báo
 *                                          *"does not work with output: standalone"* ⇒ đo ở đó là
 *                                          đo một đường sản phẩm ⛔ đi.
 *   + một máy chủ giả đứng chỗ Spring, ghi lại NGUYÊN VĂN `req.url`.
 *
 *   trình duyệt gửi          →  Spring nhận            phán quyết
 *   /api/v1/a%2Fb            →  /api/v1/a%2Fb          GIỮ  ⇒ tường 400 chạm tới được
 *   /api/v1/a/b              →  /api/v1/a/b            GIỮ
 *   /api/v1/..%2Fx           →  /api/v1/..%2Fx         GIỮ
 *   /api/v1/a%252Fb          →  /api/v1/a%252Fb        GIỮ  (mã hoá hai lần)
 *   /api/v1/tr%C3%A0m        →  /api/v1/tr%C3%A0m      GIỮ  (unicode)
 *   /api/v1/a%00b            →  /api/v1/a%00b          GIỮ  (byte NUL — Spring quyết, ⛔ phải proxy)
 *   /api/v1/a%2Fb?q=1%2F2    →  /api/v1/a%2Fb?q=1%2F2  GIỮ  (cả chuỗi truy vấn)
 *   /api/v1/%2E%2E/x         →  (⛔ tới nơi)            Next tự trả 404
 *   /api/v1/x/../y           →  /api/v1/y              GỘP trước proxy (chuẩn hoá URL của Node)
 * </pre>
 *
 * ⇒ **Parity GIỮ**: Next giải mã đúng MỘT lượt, `encodeURIComponent` mã lại đúng một lượt, nên vòng
 * khứ hồi khít từng byte. Hai dòng cuối ⛔ phải khe hở — `..` bị gộp/chặn **trước** khi tới backend,
 * tức ⛔ lối đi xuyên thư mục nào chạm được Spring.
 *
 * <h2>⚠ Phạm vi bài này, khai ra theo luật 28</h2>
 *
 * Bài dưới pin **nửa của TA**: với mỗi mảng đoạn, URL đích phải là gì. Nó **⛔ thể** thấy Next đổi
 * cách tách đoạn ở một lượt nâng phiên bản — chỉ một lượt chạy đầu-tới-cuối như trên thấy được, và
 * bảng số đo ở trên là bản ghi của lượt ấy kèm ngày. ⛔ đọc cái xanh của bài này thành *"parity còn
 * nguyên sau khi nâng Next"*.
 */

const GOC = 'http://127.0.0.1:19099/api/v1';

let GET: (request: Request, ctx: { params: Promise<{ path: string[] }> }) => Promise<Response>;

/**
 * ⚠ Phải khai ĐỦ tham số dù thân ⛔ dùng tới: một `vi.fn(async () => …)` trần cho kiểu tuple `[]`,
 * nên `mock.calls[0][0]` đỏ ở cổng **kiểm kiểu** (`TS2493`) trong khi `vitest` xanh trọn — ba cổng,
 * ba câu hỏi khác nhau (T51.14 · §10.70).
 */
const fetchGia = vi.fn(
  async (_dich: string | URL | Request, _tuyChon?: RequestInit) =>
    new Response('{}', { status: 200 }),
);

beforeAll(async () => {
  process.env.API_INTERNAL_BASE_URL = GOC;
  vi.stubGlobal('fetch', fetchGia);
  ({ GET } = await import('./route'));
});

afterEach(() => {
  fetchGia.mockClear();
});

/** Gọi proxy đúng như Next gọi nó, rồi trả về URL mà proxy đã yêu cầu ngược dòng. */
async function urlDich(doan: string[], truyVan = ''): Promise<string> {
  const yeuCau = new Request(`http://cong-cong-khai.test/api/v1/bo-qua${truyVan}`);
  await GET(yeuCau, { params: Promise.resolve({ path: doan }) });
  expect(fetchGia).toHaveBeenCalledTimes(1);
  return String(fetchGia.mock.calls[0][0]);
}

describe('ASVS 13.1.1 — proxy ⛔ được làm méo đường dẫn (T85.14)', () => {
  /**
   * Mỗi dòng là một cặp ĐO ĐƯỢC ở bảng trên: đoạn Next giao (đã giải mã) → đường dẫn Spring nhận.
   * Giữ chúng thành một bảng để lượt nâng Next chỉ phải đối chiếu lại một chỗ.
   */
  it.each([
    ['một đoạn chứa dấu chéo đã mã hoá', ['a/b'], `${GOC}/a%2Fb`],
    ['hai đoạn thường', ['a', 'b'], `${GOC}/a/b`],
    ['dấu chấm kép + chéo đã mã hoá', ['../x'], `${GOC}/..%2Fx`],
    ['mã hoá hai lần', ['a%2Fb'], `${GOC}/a%252Fb`],
    ['unicode', ['tràm'], `${GOC}/tr%C3%A0m`],
    // ⛔⛔ Byte NUL dựng bằng `String.fromCharCode`, ⛔ bằng escape `\u0000` — Prettier đổi
    //   escape ấy thành KÝ TỰ THÔ, và một byte NUL trong mã nguồn làm **git coi cả tệp là NHỊ
    //   PHÂN** (đo được: `Bin 0 -> 6575 bytes`, `--numstat` ra `- -`) ⇒ PR ⛔ hiện một dòng
    //   diff nào, và mọi lượt sửa tệp này về sau đều VÔ HÌNH. Bài vẫn 8/8 xanh ở trạng thái ấy,
    //   nên thứ bắt được ⛔ phải màu của lượt chạy mà là một phép đo BYTE.
    ['byte NUL', [`a${String.fromCharCode(0)}b`], `${GOC}/a%00b`],
  ])('%s ⇒ Spring nhận ĐÚNG thứ trình duyệt gửi', async (_ten, doan, mongDoi) => {
    expect(await urlDich(doan as string[])).toBe(mongDoi);
  });

  it('chuỗi truy vấn đi kèm nguyên văn — ⛔ giải rồi mã lại', async () => {
    expect(await urlDich(['a/b'], '?q=1%2F2')).toBe(`${GOC}/a%2Fb?q=1%2F2`);
  });

  /**
   * ⛔⛔ **Vế phân biệt (luật 9).** ⛔ có nó thì một bản vá bỏ hẳn `encodeURIComponent` vẫn làm ba
   * bài trên xanh — `['a','b']`, `['tràm']` đúng ra giống nhau ở cả hai cách. Bài này khẳng định
   * **hai trạng thái ấy đọc KHÁC nhau**: ghép thô cho `a/b`, mã lại cho `a%2Fb`.
   */
  it('⚠ VẾ PHÂN BIỆT — ghép thô và mã-lại phải cho hai kết quả KHÁC nhau', async () => {
    const maLai = await urlDich(['a/b']);
    const ghepTho = `${GOC}/${['a/b'].join('/')}`;

    expect(maLai).not.toBe(ghepTho);
    expect(ghepTho).toBe(`${GOC}/a/b`);
  });
});
