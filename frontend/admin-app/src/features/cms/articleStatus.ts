import { type StatusVocabulary } from '@/components/business/statusVocabulary';

import { type ArticleStatus } from './types';

/**
 * Từ vựng trạng thái bài viết — nhãn và màu, một nơi duy nhất.
 *
 * ⛔ **Không** suy ra nút bấm từ bảng này. Nút do backend trả về trong `allowedActions`
 * (`conventions.md` §3): bộ chuyển trạng thái nằm ở bảng `workflow_transitions`, khoá theo
 * `(trạng thái, hành động, quyền)`, và khách thêm một bước duyệt là FE lệch ngay. Bảng này
 * chỉ trả lời câu hỏi *"hiện chữ gì, màu gì"*.
 */
export const ARTICLE_STATUS: StatusVocabulary = {
  NHAP: {
    label: 'Nháp',
    color: 'unknown',
    hint: 'Chỉ tác giả và quản trị viên nhìn thấy',
  },
  CHO_DUYET: {
    label: 'Chờ duyệt',
    color: 'warning',
    hint: 'Đang khoá chỉnh sửa, chờ Quản trị nội dung xử lý',
  },
  DA_DUYET: {
    label: 'Đã duyệt',
    color: 'normal',
    hint: 'Đã duyệt nhưng chưa tới giờ đăng — chưa hiện trên cổng',
  },
  XUAT_BAN: { label: 'Xuất bản', color: 'normal' },
  GO_BAI: {
    label: 'Gỡ bài',
    color: 'danger',
    hint: 'Địa chỉ trả 404 nhưng dữ liệu vẫn còn nguyên, đăng lại không cần duyệt lại',
  },
  LUU_TRU: {
    label: 'Lưu trữ',
    color: 'inactive',
    hint: 'Không lên danh sách, vẫn vào được bằng địa chỉ trực tiếp',
  },
};

/** Thứ tự hiện trong ô lọc — theo vòng đời, không theo bảng chữ cái. */
export const ARTICLE_STATUS_ORDER: ArticleStatus[] = [
  'NHAP',
  'CHO_DUYET',
  'DA_DUYET',
  'XUAT_BAN',
  'GO_BAI',
  'LUU_TRU',
];

/**
 * Bài này có đang hiện trên cổng không, **theo cách nói của con người**.
 *
 * ⚠ Nhận `publiclyVisible` do backend tính chứ không tự ghép từ `status` — ba điều kiện
 * (đã duyệt · trạng thái cho phép · đã tới giờ đăng) mà FE tự suy thì màn hình quản trị và
 * cổng công khai sẽ có ngày trả lời khác nhau về cùng một bài.
 */
export function visibilityHint(status: ArticleStatus, publiclyVisible: boolean): string {
  if (publiclyVisible) {
    return 'Đang hiển thị trên cổng';
  }
  if (status === 'DA_DUYET') {
    return 'Đã duyệt, chờ tới giờ đăng';
  }
  if (status === 'LUU_TRU') {
    return 'Chỉ vào được bằng địa chỉ trực tiếp';
  }
  return 'Chưa hiển thị trên cổng';
}
