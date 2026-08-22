'use client';

import { useEffect } from 'react';

import { API_BASE_URL } from '@/lib/site';

/**
 * Ghi nhận một lượt xem — T13.10 (nợ #64).
 *
 * <h3>Vì sao phải là Client Component</h3>
 *
 * Trang chi tiết được dựng **tĩnh** (ISR): máy chủ dựng HTML một lần rồi phục vụ lại cho
 * mọi người xem. Đếm ở phía máy chủ nghĩa là đếm số lần *dựng trang*, không phải số người
 * đọc — một bài có 5.000 lượt xem sẽ hiện con số 3.
 *
 * <h3>Vì sao `keepalive`</h3>
 *
 * Người dùng bấm sang trang khác ngay sau khi mở là chuyện thường; không có `keepalive` thì
 * trình duyệt huỷ lượt gọi đang dở và lượt xem đó mất.
 *
 * <p>Lỗi thì im lặng: người đọc không cần biết bộ đếm có chạy hay không, và một thông báo
 * lỗi vì việc này là làm phiền vì chuyện không liên quan tới họ.
 */
export function ViewTracker({ slug }: { slug: string }) {
  useEffect(() => {
    const url = `${API_BASE_URL}/public/articles/${encodeURIComponent(slug)}/views`;
    void fetch(url, { method: 'POST', keepalive: true }).catch(() => {
      /* Bộ đếm là số xấp xỉ — hỏng một lượt không đáng để làm phiền người đọc. */
    });
  }, [slug]);

  return null;
}
