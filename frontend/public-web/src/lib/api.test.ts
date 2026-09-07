import { afterEach, describe, expect, it, vi } from 'vitest';

import { getArticle, getArticles } from '@/lib/api';

/**
 * `apiGetWithMeta` mở đầu bằng `await connection()` — cái chốt chặn `next build` dựng sẵn nội
 * dung khi backend chưa tồn tại (§10.54, xem `noBuildTimePrerender.test.ts`). Hàm ấy ném khi
 * gọi ngoài phạm vi một request, mà vitest thì chạy trần.
 *
 * ⚠ Giả lập ở đây là ranh giới FRAMEWORK, không phải chỗ mã chạm ra ngoài: thứ những bài dưới
 *   đây khẳng định là việc bóc envelope và quy đổi phân trang, và `fetch` — chỗ thật sự chạm
 *   ra ngoài — vẫn được thay bằng bản giả có kiểm soát ở từng bài. Việc `connection()` CÓ nằm
 *   trên đường chạy được canh bằng một bài riêng ở tầng cấu trúc, không canh bằng bài này
 *   (luật 4).
 */
vi.mock('next/server', () => ({ connection: () => Promise.resolve() }));

/**
 * Bóc envelope và quy đổi phân trang.
 *
 * <h3>Vì sao bài kiểm này tồn tại</h3>
 *
 * Hợp đồng phân trang của hệ này có **hai quy ước ngược nhau trên cùng một khái niệm**:
 * tham số `page` gửi lên đếm từ 0, còn `meta.page` trả về đếm từ 1 (conventions.md §1.3).
 * Đoán nhầm không gây lỗi biên dịch — TypeScript tin kiểu ta khai — nên nó chết lúc chạy,
 * và ở lượt chạy thật đầu tiên của WS-16 nó chết đúng như vậy.
 */
function traLoi(body: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as Response);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('getArticles — ghép data với meta', () => {
  it('⭐ quy đổi meta.page (đếm từ 1) về chỉ số trang (đếm từ 0)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        traLoi({
          success: true,
          data: [
            {
              slug: 'a',
              title: 'Bài A',
              summary: null,
              coverAttachmentPublicId: null,
              publishedAt: null,
              viewCount: 0,
            },
          ],
          meta: { page: 3, size: 12, totalElements: 30, totalPages: 3 },
        }),
      ),
    );

    const result = await getArticles({ page: 2 });

    expect(result?.number).toBe(2);
    expect(result?.totalPages).toBe(3);
    expect(result?.content).toHaveLength(1);
  });

  it('⛔ phần tử nằm ở `data`, KHÔNG ở `data.content` — đoán nhầm là trang chết lúc chạy', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        traLoi({
          success: true,
          data: [],
          meta: { page: 1, size: 12, totalElements: 0, totalPages: 0 },
        }),
      ),
    );

    const result = await getArticles({});

    expect(result?.content).toEqual([]);
    expect(result?.totalElements).toBe(0);
  });

  it('thiếu meta thì vẫn ra một trang hợp lệ, không phải undefined', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => traLoi({ success: true, data: [] })),
    );

    const result = await getArticles({});

    expect(result).toEqual({ content: [], totalElements: 0, totalPages: 0, number: 0 });
  });
});

describe('lỗi backend không được làm trắng trang', () => {
  it('404 trả null, không ném', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => traLoi({ success: false }, 404)),
    );

    await expect(getArticle('khong-co')).resolves.toBeNull();
  });

  it('backend chết (fetch nổ) cũng trả null', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('ECONNREFUSED'))),
    );

    await expect(getArticles({})).resolves.toBeNull();
  });

  it('envelope success=false trả null dù HTTP 200', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => traLoi({ success: false, error: { code: 'SYS-0001', message: 'x' } })),
    );

    await expect(getArticle('x')).resolves.toBeNull();
  });
});
