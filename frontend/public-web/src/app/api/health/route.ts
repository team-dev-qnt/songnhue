import { NextResponse } from 'next/server';

/**
 * Health check của tiến trình Next (T9.5).
 *
 * `HEALTHCHECK` trong `deploy/docker/public-web.Dockerfile` gọi đúng đường dẫn này, và
 * Docker dựa vào nó để biết container đã sẵn sàng nhận lưu lượng.
 *
 * <h3>Cố ý chỉ trả lời "tiến trình này còn sống"</h3>
 *
 * **Không** gọi sang API Core để kiểm tra. Nếu làm vậy thì backend hỏng sẽ kéo theo
 * container này bị đánh dấu unhealthy và bị khởi động lại — trong khi phần lớn cổng thông
 * tin là HTML tĩnh, vẫn phục vụ được người dân đọc tin bình thường. Một thành phần hỏng
 * không nên làm hỏng lây thành phần còn chạy được.
 *
 * Tình trạng của backend đã có chỗ riêng: `GET /api/v1/system/health` (M5.12).
 */
export const dynamic = 'force-dynamic';

export function GET() {
  return NextResponse.json(
    { status: 'UP', service: 'public-web', time: new Date().toISOString() },
    { headers: { 'Cache-Control': 'no-store' } },
  );
}
