/**
 * Đếm ký tự SEO và cảnh báo vượt ngưỡng — T20.3.
 *
 * <h3>Vì sao là hàm thuần trong file riêng</h3>
 *
 * Ba con số dưới đây quyết định bài viết hiển thị thế nào trên Google, và chúng **không
 * phải luật của backend** — backend chỉ chặn độ dài tối đa của cột. Phần "quá 60 ký tự thì
 * bị cắt đuôi" là quy ước hiển thị của công cụ tìm kiếm, sống ở FE. Tách ra để kiểm được
 * mà không phải dựng cả biểu mẫu.
 */

/**
 * Ngưỡng hiển thị.
 *
 * ⚠ Hai con số cho mỗi trường, và chúng khác nhau về bản chất:
 * - `khuyenNghi`: quá mức này thì công cụ tìm kiếm **cắt đuôi** — cảnh báo, không chặn.
 * - `toiDa`: giới hạn cột ở CSDL (`@Size` của `SaveRequest`) — vượt là backend từ chối.
 *
 * Gộp làm một con số là hoặc chặn oan người dùng, hoặc để họ gõ thoải mái rồi ăn lỗi lúc lưu.
 */
export const SEO_LIMITS = {
  metaTitle: { khuyenNghi: 60, toiDa: 70 },
  metaDescription: { khuyenNghi: 155, toiDa: 160 },
  summary: { khuyenNghi: 300, toiDa: 500 },
} as const;

export type SeoField = keyof typeof SEO_LIMITS;

export type SeoLevel = 'trong' | 'tot' | 'canhBao' | 'vuot';

export interface SeoStatus {
  length: number;
  level: SeoLevel;
  /** Câu hiện dưới ô nhập. Luôn nói *hậu quả*, không chỉ nói con số. */
  hint: string;
}

/**
 * Đếm **ký tự người dùng nhìn thấy**, không đếm đơn vị lưu trữ.
 *
 * <h3>Ba cách đếm cho ba kết quả khác nhau, và chỉ một cách đúng</h3>
 *
 * Chữ "Đề" dán từ Word thường ở dạng **dấu tổ hợp** (`e` + dấu mũ + dấu huyền):
 * - `String.length` → 4 (đơn vị mã UTF-16)
 * - `Array.from(...).length` → 4 (điểm mã — dấu tổ hợp vẫn là điểm mã riêng)
 * - `Intl.Segmenter` → 2 (cụm hiển thị) ✔
 *
 * Hai cách đầu làm ô đếm báo vượt ngưỡng trong khi mắt thấy chưa vượt — và người soạn sẽ
 * cắt bớt một tiêu đề hoàn toàn hợp lệ. Bài kiểm `seo.test.ts` bắt được đúng chỗ này: bản
 * đầu của hàm dùng `Array.from` và tài liệu của nó khẳng định sai rằng như vậy là đủ.
 *
 * Rơi về `Array.from` khi thiếu `Intl.Segmenter` — sai ở dấu tổ hợp nhưng vẫn đúng với emoji,
 * và vẫn tốt hơn `String.length`.
 */
function demKyTuHienThi(text: string): number {
  if (typeof Intl.Segmenter === 'function') {
    return [...new Intl.Segmenter('vi', { granularity: 'grapheme' }).segment(text)].length;
  }
  return Array.from(text).length;
}

/** Đánh giá một giá trị SEO. */
export function evaluateSeo(field: SeoField, value: string | null | undefined): SeoStatus {
  const text = (value ?? '').trim();
  const length = demKyTuHienThi(text);
  const { khuyenNghi, toiDa } = SEO_LIMITS[field];

  if (length === 0) {
    return { length: 0, level: 'trong', hint: HINT_TRONG[field] };
  }
  if (length > toiDa) {
    return {
      length,
      level: 'vuot',
      hint: `Vượt ${length - toiDa} ký tự so với giới hạn ${toiDa} — máy chủ sẽ từ chối lưu`,
    };
  }
  if (length > khuyenNghi) {
    return {
      length,
      level: 'canhBao',
      hint: `Quá ${khuyenNghi} ký tự — công cụ tìm kiếm sẽ cắt bớt phần đuôi`,
    };
  }
  return { length, level: 'tot', hint: `Còn ${khuyenNghi - length} ký tự trong mức khuyến nghị` };
}

const HINT_TRONG: Record<SeoField, string> = {
  metaTitle: 'Bỏ trống thì công cụ tìm kiếm lấy tiêu đề bài làm tiêu đề hiển thị',
  metaDescription: 'Bỏ trống thì công cụ tìm kiếm tự cắt một đoạn trong bài — thường khó đọc',
  summary: 'Bỏ trống thì danh sách bài trên cổng chỉ hiện tiêu đề',
};

/** Màu của bộ đếm. Ánh xạ sang `type` của `Typography.Text` để không tự khai mã màu. */
export function seoTextType(level: SeoLevel): 'secondary' | 'success' | 'warning' | 'danger' {
  switch (level) {
    case 'trong':
      return 'secondary';
    case 'tot':
      return 'success';
    case 'canhBao':
      return 'warning';
    case 'vuot':
      return 'danger';
    default:
      // Enum thêm giá trị mới mà quên nhánh ở đây thì hỏng to tiếng, không im lặng.
      throw new Error(`Mức SEO chưa được xử lý: ${String(level)}`);
  }
}

/**
 * Sinh slug gợi ý từ tiêu đề — cùng quy tắc với `SlugUtils` của backend.
 *
 * ⚠ Đây chỉ là **gợi ý hiển thị** trong lúc gõ. Slug thật do backend sinh và bảo đảm duy
 * nhất; FE mà tự chốt slug rồi gửi lên thì hai bên sẽ có ngày lệch nhau ở chữ có dấu.
 */
export function suggestSlug(title: string): string {
  return (
    title
      .normalize('NFD')
      // Bỏ dấu thanh và dấu phụ (khối U+0300–U+036F).
      .replace(/[̀-ͯ]/g, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 255)
  );
}
