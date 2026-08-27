import type { Metadata } from 'next';

import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import type { OrgChartNode } from '@/lib/api';
import { getOrgChart } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

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
 * cấu hệ thống đang thực sự dùng, không phải một bản vẽ chép tay đã lỗi thời.
 *
 * <p>⛔ Bảng {@code org_units} cố ý <b>không seed</b> ({@code V202608131008}): nó là dữ liệu
 * chịu tải, đoán sai rồi sửa là phải di chuyển mọi thứ đã bám vào id của nó. Nên trang này
 * rỗng cho tới lượt nhập liệu, và nó nói thẳng điều đó.
 */
export default async function CoCauToChucPage() {
  const chart = await getOrgChart();
  const nodes = chart ?? [];

  return (
    <PageShell
      title="Cơ cấu tổ chức"
      description="Sơ đồ tổ chức bộ máy quản lý của Công ty."
      breadcrumb={[{ label: 'Giới thiệu' }, { label: 'Cơ cấu tổ chức' }]}
    >
      {nodes.length === 0 ? (
        <EmptyBlock>
          Sơ đồ tổ chức chưa được nhập. Cây đơn vị được quản lý ở màn hình Sơ đồ tổ chức của trang
          quản trị; cổng đọc thẳng từ đó nên không có bản vẽ riêng nào để cập nhật.
        </EmptyBlock>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-surface-border bg-white p-5 shadow-xs">
          <ul className="min-w-[520px] space-y-1">
            {nodes.map((node) => (
              <NhanhToChuc key={node.code} node={node} cap={0} />
            ))}
          </ul>
        </div>
      )}
    </PageShell>
  );
}

/** Nhãn hiển thị của từng loại đơn vị — `OrgUnitType` phía backend là enum trong mã, không phải danh mục CRUD. */
const NHAN_LOAI: Record<string, string> = {
  CONG_TY: 'Công ty',
  PHONG_BAN: 'Phòng ban',
  XI_NGHIEP: 'Xí nghiệp',
  TO_DOI: 'Tổ, đội',
  KHAC: 'Khác',
};

/**
 * Một nhánh của cây, vẽ bằng thụt lề + đường nối.
 *
 * ⚠ Đệ quy theo `children` chứ không theo `path`: backend cố ý không trả materialized path ra
 * đường công khai (nó là chuỗi id chạy số). Cấu trúc lồng đã đủ để vẽ.
 */
function NhanhToChuc({ node, cap }: { node: OrgChartNode; cap: number }) {
  return (
    <li>
      <div
        className="flex items-center gap-2.5 rounded-lg border border-surface-border/80 bg-surface-bgLayout/50 px-3 py-2"
        style={{ marginLeft: `${cap * 1.5}rem` }}
      >
        <span
          aria-hidden="true"
          className={`h-2 w-2 shrink-0 rounded-full ${cap === 0 ? 'bg-brand-primary' : 'bg-surface-border'}`}
        />
        <span
          className={`text-sm ${cap === 0 ? 'font-bold text-brand-primary' : 'font-semibold text-surface-textBase'}`}
        >
          {node.name}
        </span>
        <span className="rounded bg-white px-1.5 py-0.5 text-[11px] font-medium text-surface-textSecondary">
          {NHAN_LOAI[node.unitType] ?? node.unitType}
        </span>
      </div>
      {node.children.length > 0 ? (
        <ul className="mt-1 space-y-1">
          {node.children.map((con) => (
            <NhanhToChuc key={con.code} node={con} cap={cap + 1} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
