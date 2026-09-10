import type { Metadata } from 'next';

import { SoDoToChuc } from '@/components/gioi-thieu/SoDoToChuc';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import { getCompanyLeaders, getOrgChart } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { tuCoCauToChuc } from '@/lib/soDoToChuc';

/** Xem ghi chú ở `danh-muc/[slug]/page.tsx`: Next đòi literal, `revalidate-config.test.ts` canh. */
export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Cơ cấu tổ chức - Thủy lợi Sông Nhuệ',
  description:
    'Sơ đồ tổ chức bộ máy quản lý của Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ.',
  alternates: { canonical: ROUTES.gioiThieu.coCauToChuc },
};

/**
 * Giới thiệu &gt; **Cơ cấu tổ chức** — CR-24.
 *
 * <p>Trước đợt này, "Cơ cấu tổ chức" là một bài viết nằm trong khối "Chỉ đạo điều hành" của
 * trang chủ (CR-16 đã bỏ khối ấy). Nay nó là một trang riêng đọc thẳng {@code org_units} —
 * cùng bảng mà phân quyền tầng 3 và hồ sơ công trình neo vào, nên sơ đồ hiển thị luôn là cơ
 * cấu hệ thống đang thực sự dùng, ⛔ không phải một bản vẽ chép tay đã lỗi thời.
 *
 * <h2>⭐ 10/09/2026 — từ danh sách thụt lề sang SƠ ĐỒ HÌNH CÂY</h2>
 *
 * <p>Bản trước vẽ cây bằng thụt lề. QuanTran yêu cầu đúng nghĩa *"sơ đồ hình cây"*, nên trang nay
 * dùng {@link SoDoToChuc}: từ 768px là hộp nối nhau bằng đường kẻ, dưới ngưỡng ấy vẫn là danh
 * sách thụt lề (chín Xí nghiệp trên một hàng ngang là ~1.7 ngàn pixel — ⛔ không đọc nổi trên
 * điện thoại).
 *
 * <p>⭐ Ban lãnh đạo nay hiện <b>trong hộp Công ty</b>, thay vì chỉ nằm ở một trang khác: đó là
 * cách một sơ đồ tổ chức thật đọc được — ai đứng đầu, rồi tới những đơn vị nào.
 *
 * <p>⛔ Bảng {@code org_units} cố ý <b>không seed</b> ({@code V202608131008}): nó là dữ liệu
 * chịu tải, đoán sai rồi sửa là phải di chuyển mọi thứ đã bám vào id của nó. Nên trang này
 * rỗng cho tới lượt nhập liệu, và nó nói thẳng điều đó.
 */
export default async function CoCauToChucPage() {
  const [chart, lanhDao] = await Promise.all([getOrgChart(), getCompanyLeaders()]);
  const cay = tuCoCauToChuc(chart ?? [], lanhDao ?? []);

  return (
    <PageShell
      title="Cơ cấu tổ chức"
      description="Sơ đồ tổ chức bộ máy quản lý của Công ty."
      breadcrumb={[{ label: 'Giới thiệu' }, { label: 'Cơ cấu tổ chức' }]}
    >
      {cay.length === 0 ? (
        <EmptyBlock>
          Sơ đồ tổ chức chưa được nhập. Cây đơn vị được quản lý ở màn hình Sơ đồ tổ chức của trang
          quản trị; cổng đọc thẳng từ đó nên không có bản vẽ riêng nào để cập nhật.
        </EmptyBlock>
      ) : (
        <SoDoToChuc nut={cay} nhan="Sơ đồ cơ cấu tổ chức Công ty" />
      )}
      <SectionNav duongDan={ROUTES.gioiThieu.coCauToChuc} />
    </PageShell>
  );
}
