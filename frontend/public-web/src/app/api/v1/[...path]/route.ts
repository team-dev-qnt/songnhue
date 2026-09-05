import { API_INTERNAL_BASE_URL } from '@/lib/site';

/**
 * Chuyển tiếp `/api/v1/*` sang backend — để **trình duyệt luôn gọi cùng origin**.
 *
 * <h3>Vì sao phải có</h3>
 *
 * Backend **không cấu hình CORS**, và đó là lựa chọn đúng: ở production nginx đứng trước cả
 * hệ (T11.5) nên cổng công khai và API vốn cùng origin. Nhưng bản đầu build bundle với
 * `NEXT_PUBLIC_API_BASE_URL=http://localhost:18080/api/v1`, tức là trình duyệt gọi sang một
 * cổng khác → khác origin → lượt kiểm trước (preflight) nhận `403 Invalid CORS request`.
 *
 * Hệ quả cụ thể: **bộ đếm lượt xem chưa từng chạy được từ trình duyệt thật**. Lượt kiểm ở
 * WS-16 gọi bằng `curl` nên đi lọt — curl không phải trình duyệt, không có chính sách cùng
 * nguồn, không làm preflight. Ảnh trong bài thì vẫn hiện vì thẻ `<img>` không chịu ràng buộc
 * CORS, nên lỗi càng khó thấy.
 *
 * <h3>Vì sao là Route Handler chứ không phải `rewrites()`</h3>
 *
 * Đã thử `rewrites()` trước và **hỏng**: với `output: 'standalone'`, Next gọi `rewrites()`
 * **lúc build** rồi ghi kết quả đã giải sẵn vào `.next/required-server-files.json`. Giá trị
 * `API_INTERNAL_BASE_URL` lúc build trong Docker chưa tồn tại nên nó rơi về `localhost:8080`
 * và bị nướng cứng vào image — container có đúng biến môi trường mà vẫn
 * `ECONNREFUSED 127.0.0.1:8080`.
 *
 * Route Handler chạy ở **mỗi request**, nên đọc env lúc chạy và một image dùng được cho mọi
 * môi trường — đúng nguyên tắc *"đóng gói một lần, đề bạt cùng image"* của `docs/cicd.md`.
 *
 * ⚠ Chỉ nhận tiền tố `/api/v1/`; hai route của chính Next (`/api/health`, `/api/revalidate`)
 * nằm ngoài nên không bị nuốt.
 */

/** Không đệm: đây là đường ống, mọi quyết định về cache thuộc về backend. */
export const dynamic = 'force-dynamic';

/**
 * Header **không** được chép sang lượt gọi ngược dòng.
 *
 * `host` phải để `fetch` tự đặt theo địa chỉ đích, giữ nguyên là backend nhận tên máy của
 * cổng công khai. Nhóm `content-length`/`transfer-encoding`/`connection` do tầng vận chuyển
 * quản, chép tay sang là sinh ra phản hồi méo.
 */
const KHONG_CHEP_LEN = new Set(['host', 'connection', 'content-length', 'transfer-encoding']);

/** Header của phản hồi cũng vậy — để Node tự tính lại theo luồng thật sự gửi đi. */
const KHONG_CHEP_VE = new Set([
  'content-encoding',
  'content-length',
  'transfer-encoding',
  'connection',
]);

async function chuyenTiep(request: Request, path: string[]): Promise<Response> {
  const nguon = new URL(request.url);
  const dich = `${API_INTERNAL_BASE_URL}/${path.map(encodeURIComponent).join('/')}${nguon.search}`;

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    if (!KHONG_CHEP_LEN.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });

  // Backend đọc IP thật từ đây để ghi sự kiện bảo mật và tính hạn mức theo IP. Thiếu thì mọi
  // lượt gọi trông như đến từ chính máy chủ Next, và một người có thể làm cạn hạn mức chung.
  const ipCu = request.headers.get('x-forwarded-for');
  const ip = nguon.hostname;
  headers.set('x-forwarded-for', ipCu ? `${ipCu}, ${ip}` : ip);

  let phanHoi: Response;
  try {
    phanHoi = await fetch(dich, {
      method: request.method,
      headers,
      // GET/HEAD không có thân; truyền `body` cho chúng là `fetch` ném lỗi ngay.
      body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
      // Bắt buộc khi truyền một luồng làm `body` — thiếu thì Node từ chối với
      // "RequestInit: duplex option is required when sending a body".
      ...(request.method === 'GET' || request.method === 'HEAD' ? {} : { duplex: 'half' }),
      redirect: 'manual',
      cache: 'no-store',
    } as RequestInit);
  } catch (loi) {
    // Nói rõ đây là lỗi của đường ống, không phải lỗi nghiệp vụ — nếu không thì người đọc log
    // sẽ đi tìm nguyên nhân trong mã backend.
    console.error(`[cổng] không chuyển tiếp được ${request.method} ${dich}`, loi);
    return Response.json(
      { success: false, error: { code: 'SYS-0001', message: 'Không kết nối được máy chủ' } },
      { status: 502 },
    );
  }

  const headersVe = new Headers();
  phanHoi.headers.forEach((value, key) => {
    if (!KHONG_CHEP_VE.has(key.toLowerCase())) {
      headersVe.append(key, value);
    }
  });

  return new Response(phanHoi.body, { status: phanHoi.status, headers: headersVe });
}

type Ngucanh = { params: Promise<{ path: string[] }> };

export async function GET(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}

export async function POST(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}

export async function PUT(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}

export async function PATCH(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}

export async function DELETE(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}

export async function HEAD(request: Request, { params }: Ngucanh) {
  return chuyenTiep(request, (await params).path);
}
