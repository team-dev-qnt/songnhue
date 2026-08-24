import { revalidatePath, revalidateTag } from 'next/cache';
import { NextResponse, type NextRequest } from 'next/server';

/**
 * Dựng lại trang tĩnh theo yêu cầu (T9.4) — chỗ Phase 1 cắm luồng duyệt bài của CMS vào.
 *
 * <h3>Vì sao cần, khi đã có `revalidate` theo chu kỳ</h3>
 *
 * Chu kỳ 5 phút là đủ cho tin thường, nhưng **không** đủ cho thứ phải lên ngay: thông báo
 * xả lũ, cảnh báo mực nước, đính chính một bài đã đăng sai. Khi biên tập viên bấm duyệt,
 * backend gọi endpoint này và trang được dựng lại trong vài giây.
 *
 * <h3>Bí mật</h3>
 *
 * `REVALIDATE_SECRET` **không** có tiền tố `NEXT_PUBLIC_` — cố ý. Biến có tiền tố đó bị
 * nhúng vào bundle gửi xuống trình duyệt, tức là ai cũng đọc được, và khi đó endpoint này
 * thành một nút bất kỳ ai cũng bấm được để ép máy chủ dựng lại trang liên tục.
 *
 * So sánh chuỗi bằng vòng lặp **thời gian không đổi**: so sánh `===` của JavaScript dừng
 * ngay ở ký tự khác nhau đầu tiên, để lộ độ dài tiền tố đúng qua thời gian phản hồi.
 *
 * Chưa cấu hình bí mật thì endpoint **đóng** (503), không phải mở — mặc định an toàn.
 */
export const dynamic = 'force-dynamic';

interface RevalidateBody {
  /** Đường dẫn cần dựng lại, VD `/tin-tuc/thong-bao-xa-lu`. */
  path?: string;
  /** Nhãn cache, VD `bai-viet` — dựng lại mọi trang có gắn nhãn đó. */
  tag?: string;
}

export async function POST(request: NextRequest) {
  const secret = process.env.REVALIDATE_SECRET;

  if (!secret) {
    return NextResponse.json(
      { error: 'Chức năng dựng lại trang chưa được cấu hình trên môi trường này' },
      { status: 503 },
    );
  }

  const provided = request.headers.get('x-revalidate-secret') ?? '';
  if (!timingSafeEqual(provided, secret)) {
    // Không nói rõ "thiếu" hay "sai" — cùng một câu trả lời cho cả hai.
    return NextResponse.json({ error: 'Không được phép' }, { status: 401 });
  }

  let body: RevalidateBody;
  try {
    body = (await request.json()) as RevalidateBody;
  } catch {
    return NextResponse.json({ error: 'Nội dung không phải JSON hợp lệ' }, { status: 400 });
  }

  if (!body.path && !body.tag) {
    return NextResponse.json({ error: 'Cần có `path` hoặc `tag`' }, { status: 400 });
  }

  if (body.path) {
    revalidatePath(body.path);
  }
  if (body.tag) {
    // ⚠ Next 16 bắt buộc tham số thứ hai (hồ sơ `cacheLife`). `'max'` = xoá sạch mọi bản
    // cache mang nhãn này, bất kể tuổi — đúng ý muốn ở đây: biên tập viên vừa duyệt bài
    // thì bản cũ phải biến mất, không phải "hết hạn sớm hơn một chút".
    revalidateTag(body.tag, 'max');
  }

  return NextResponse.json({ revalidated: true, path: body.path, tag: body.tag });
}

/** So sánh không rò rỉ thông tin qua thời gian chạy. */
function timingSafeEqual(a: string, b: string): boolean {
  // Chênh lệch độ dài vẫn lộ ra ở đây, nhưng độ dài của một bí mật ngẫu nhiên không giúp
  // đoán được nội dung — khác hẳn việc lộ tiền tố đúng từng ký tự.
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
