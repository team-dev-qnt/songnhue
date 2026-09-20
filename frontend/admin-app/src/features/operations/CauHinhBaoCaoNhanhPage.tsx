import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Card, Select, Space, Table, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { Link } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { type BcnCongTrinhView, type BcnVeView, type BcnViTriView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { boDau } from '@/shared/boDau';
import { EMPTY_MARK } from '@/shared/format';

const GOC = '/ops/bao-cao-nhanh/cau-hinh';
const KHOA = ['ops', 'bao-cao-nhanh', 'cau-hinh'];
const THIEU_QUYEN = 'Thiếu quyền ops:quick-report:manage';

/**
 * Cấu hình Báo cáo nhanh — công trình gắn vào 7 cống của Bảng 3 và trạm của ghi chú Yên Nghĩa.
 *
 * Trước 18/09/2026 các mã ấy nằm trong mã nguồn: thêm điểm đo cho một vế đang trống hay đổi trạm Yên
 * Nghĩa là phải chờ deploy. Nay Công ty tự chọn ở đây; điểm đo thượng/hạ lưu của từng cống SUY RA từ
 * liên kết điểm đo–công trình (màn hình Điểm đo) — ⛔ khai lần thứ hai ở đây.
 *
 * Kỳ ĐÃ CHỐT ⛔ đổi theo cấu hình này — lúc chốt hệ thống đã chụp lại.
 */
export function CauHinhBaoCaoNhanhPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const coSua = hasPermission('ops:quick-report:manage');

  const viTri = useQuery({
    queryKey: KHOA,
    queryFn: () => api.get<BcnViTriView[]>(`${GOC}/vi-tri`),
  });
  const cong = useQuery({
    queryKey: [...KHOA, 'cong-trinh', 'CONG'],
    queryFn: () => api.get<BcnCongTrinhView[]>(`${GOC}/cong-trinh`, { loai: 'CONG' }),
  });
  const tramBom = useQuery({
    queryKey: [...KHOA, 'cong-trinh', 'TRAM_BOM'],
    queryFn: () => api.get<BcnCongTrinhView[]>(`${GOC}/cong-trinh`, { loai: 'TRAM_BOM' }),
  });

  const gan = useMutation({
    mutationFn: (v: { viTri: string; constructionPublicId: string | null }) =>
      api.put<BcnViTriView>(`${GOC}/vi-tri/${v.viTri}`, {
        constructionPublicId: v.constructionPublicId,
      }),
    onSuccess: (moi) => {
      queryClient.setQueryData<BcnViTriView[]>(KHOA, (cu) =>
        cu?.map((v) => (v.publicId === moi.publicId ? moi : v)),
      );
      // Kỳ ĐANG NHẬP đọc cấu hình sống ⇒ Bảng 3 / ghi chú Yên Nghĩa của nó đổi theo.
      void queryClient.invalidateQueries({ queryKey: ['ops', 'bao-cao-nhanh'] });
      message.success('Đã lưu cấu hình');
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được cấu hình'),
  });

  const ve = (v: BcnVeView | null) => {
    if (!v) {
      return EMPTY_MARK;
    }
    return v.apiCode ? (
      <Typography.Text code>{v.apiCode}</Typography.Text>
    ) : (
      <Typography.Text type="secondary">{v.lyDo}</Typography.Text>
    );
  };

  const cot: ColumnsType<BcnViTriView> = [
    { title: 'Vị trí trên mẫu', dataIndex: 'nhan', width: 240 },
    {
      title: 'Công trình',
      key: 'ct',
      width: 300,
      render: (_, v) => {
        const nguon = v.loaiCongTrinh === 'CONG' ? cong.data : tramBom.data;
        return (
          <Select
            aria-label={`Công trình cho ${v.nhan}`}
            style={{ width: '100%' }}
            // ⭐ Gõ để lọc theo TÊN hoặc MÃ, và ⛔ phân biệt dấu (T78.1). Bộ lọc mặc định của antd so
            //   chuỗi NGUYÊN DẤU, nên gõ "yen nghia" ⛔ ra "Yên Nghĩa" — đúng lúc người dùng đang cần
            //   tìm nhanh giữa 178 trạm thì ô tìm im lặng trả về rỗng. `boDau` là bộ lọc Bảng 2 đang
            //   dùng, ⛔ viết bản thứ hai (quy tắc 14).
            showSearch={{
              filterOption: (nhap: string, opt?: { label?: string }) =>
                boDau(opt?.label ?? '').includes(boDau(nhap.trim())),
            }}
            allowClear
            placeholder="Chưa gắn — gõ tên hoặc mã công trình để tìm"
            disabled={!coSua}
            title={coSua ? undefined : THIEU_QUYEN}
            loading={gan.isPending && gan.variables?.viTri === v.publicId}
            value={v.congTrinh?.publicId ?? null}
            options={(nguon ?? []).map((c) => ({ value: c.publicId, label: `${c.ten} (${c.ma})` }))}
            onChange={(giaTri: string | null | undefined) =>
              gan.mutate({ viTri: v.publicId, constructionPublicId: giaTri ?? null })
            }
          />
        );
      },
    },
    { title: 'Điểm đo thượng lưu', key: 'tl', width: 280, render: (_, v) => ve(v.tl) },
    { title: 'Điểm đo hạ lưu', key: 'hl', width: 280, render: (_, v) => ve(v.hl) },
  ];

  return (
    <Card
      title="Cấu hình Báo cáo nhanh"
      extra={<Link to="/van-hanh/bao-cao-nhanh">Về danh sách kỳ báo cáo</Link>}
    >
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="Chọn công trình cho từng vị trí cố định của mẫu Word"
          description={
            <>
              Điểm đo thượng/hạ lưu của mỗi cống lấy từ liên kết điểm đo – công trình ở màn hình{' '}
              <Link to="/thuy-van/diem-do">Điểm đo</Link>. Vế nào chưa có điểm đo thì Bảng 3 để
              trống kèm lý do. Kỳ báo cáo đã chốt giữ nguyên cấu hình của lúc chốt.
            </>
          }
        />
        <Table
          rowKey="publicId"
          size="small"
          columns={cot}
          dataSource={viTri.data ?? []}
          loading={viTri.isLoading}
          pagination={false}
          scroll={{ x: 1100 }}
        />
      </Space>
    </Card>
  );
}

export default CauHinhBaoCaoNhanhPage;
