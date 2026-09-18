import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';

import { NopDonModal } from './NopDonModal';
import { SoDuPhepCard } from './SoDuPhepCard';
import { LOAI_NGHI_LABEL, TRANG_THAI_DON_META, type DonNghiView } from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';
import { ngayVn } from './nghiPhepFormat';

const CO_TRANG = 20;

/**
 * Đơn nghỉ của tôi + số dư phép năm — CN-04.9 (WS-57).
 *
 * <h2>⛔⛔ Tuyến này ⛔ KHÔNG gác bằng một mã quyền phạm vi</h2>
 *
 * Nó gác bằng **liên kết hồ sơ** (`requiresEmployeeLink`), đúng như `/nhan-su/ho-so-cua-toi`.
 * Backend ⛔ không nhận một định danh nào ở `/cua-toi` và `/so-du` — nó suy hồ sơ từ **token**
 * (T51.8), nên IDOR là trạng thái ⛔ không biểu diễn được.
 *
 * <p>⚠ Quyền `hr:leave:request` **có** gác endpoint, nhưng chốt C3 cấp nó cho gần như mọi vai trò
 * (gồm VIEWER): *"cấp tài khoản cho toàn bộ CBNV"*. Gác thêm ở menu bằng `hr:employee:view` sẽ
 * chặn đúng người mục này phục vụ.
 *
 * <h2>⛔ Nút Rút/Huỷ ⛔ không suy từ trạng thái</h2>
 *
 * Nó đọc `GET /{id}/hanh-dong` — danh sách backend đã lọc theo `workflow_transitions` **và** theo
 * quyền người đang đăng nhập. Dựng một bảng `if` ở đây là để giao diện lệch khỏi quy trình ngay
 * lần đầu Công ty thêm một bước duyệt (quy tắc 4).
 */
export function NghiPhepCuaToiPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [trang, setTrang] = useState(0);
  const [moNop, setMoNop] = useState(false);

  const soDu = useQuery({
    queryKey: ['hr', 'nghi-phep', 'so-du'],
    queryFn: () => nghiPhepApi.soDu(),
  });

  const donCuaToi = useQuery({
    queryKey: ['hr', 'nghi-phep', 'cua-toi', trang],
    queryFn: () => nghiPhepApi.cuaToi(trang, CO_TRANG),
  });

  const lamMoi = () => {
    void queryClient.invalidateQueries({ queryKey: ['hr', 'nghi-phep'] });
  };

  const rutMutation = useMutation({
    mutationFn: (don: DonNghiView) =>
      nghiPhepApi.thucHien(don.publicId, 'CANCEL', 'Người nộp rút đơn'),
    onSuccess: () => {
      message.success('Đã rút đơn');
      lamMoi();
    },
    // ⚠ `HR-2007` (đơn đã bắt đầu nghỉ) tới đây và nó là **câu trả lời nghiệp vụ**, ⛔ không phải
    //   một trục trặc. Thiếu nhánh này thì người dùng bấm Rút, ⛔ không có gì xảy ra và ⛔ không có
    //   gì báo — đúng lớp lỗi `moiLuotGhiPhaiBaoLoi.test.ts` sinh ra để đóng.
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không rút được đơn'),
  });

  const columns: ColumnsType<DonNghiView> = [
    {
      title: 'Loại nghỉ',
      dataIndex: 'leaveType',
      width: 150,
      render: (loai: DonNghiView['leaveType']) => LOAI_NGHI_LABEL[loai],
    },
    {
      title: 'Từ ngày',
      dataIndex: 'fromDate',
      width: 120,
      render: (d: string) => ngayVn(d),
    },
    {
      title: 'Đến ngày',
      dataIndex: 'toDate',
      width: 120,
      render: (d: string) => ngayVn(d),
    },
    {
      title: 'Ngày công',
      dataIndex: 'workingDays',
      width: 110,
      align: 'right',
      // ⛔ Chuỗi, ⛔ không phải số — `NUMERIC(5,1)` phía CSDL. ⛔ Đừng `Number()` ở đây (quy tắc 2).
      render: (s: string) => <b>{s}</b>,
    },
    { title: 'Lý do', dataIndex: 'reason', width: 260, ellipsis: true },
    {
      title: 'Trạng thái',
      dataIndex: 'state',
      width: 150,
      render: (tt: DonNghiView['state'], don) => (
        <Space size={4} wrap>
          <Tag color={TRANG_THAI_DON_META[tt].color}>{TRANG_THAI_DON_META[tt].label}</Tag>
          {/* Một đơn nộp hộ và một đơn tự nộp mang hai mức tin cậy khác nhau khi đối chiếu về sau
              — chốt C3 đòi lưu, nên giao diện phải hiện. */}
          {don.noHo ? <Tag>Nộp hộ</Tag> : null}
        </Space>
      ),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 110,
      align: 'right',
      render: (_, don) =>
        don.state === 'CHO_DUYET' || don.state === 'CHO_DUYET_2' || don.state === 'DA_DUYET' ? (
          <Popconfirm
            title="Rút đơn này?"
            description="Đơn đã bắt đầu nghỉ thì không rút được."
            okText="Rút đơn"
            cancelText="Thôi"
            onConfirm={() => rutMutation.mutate(don)}
          >
            <Button type="link" size="small">
              Rút đơn
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <SoDuPhepCard soDu={soDu.data} dangTai={soDu.isLoading} />

      <Card
        title="Đơn nghỉ của tôi"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setMoNop(true)}>
            Nộp đơn
          </Button>
        }
      >
        <Table
          rowKey="publicId"
          loading={donCuaToi.isLoading}
          dataSource={donCuaToi.data?.muc ?? []}
          columns={columns}
          locale={{ emptyText: 'Bạn chưa nộp đơn nghỉ nào' }}
          // 150+120+120+110+260+150+110 = 1020. Thiếu `scroll` thì `rc-table` chọn
          // `tableLayout:'auto'` và `<col width>` chỉ còn là gợi ý (`bangCuonNgang.test.ts`).
          scroll={{ x: 1020 }}
          pagination={{
            current: trang + 1,
            pageSize: donCuaToi.data?.co ?? CO_TRANG,
            // ⛔ `tong` từ API, ⛔ KHÔNG phải `muc.length` — đó chỉ là một trang.
            total: donCuaToi.data?.tong ?? 0,
            showSizeChanger: false,
            onChange: (p) => setTrang(p - 1),
          }}
        />

        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Số ngày công của mỗi đơn được <b>đóng băng lúc nộp</b>. Công ty thêm một ngày lễ vào tháng
          sau thì đơn đã duyệt tháng trước ⛔ không đổi số — người lao động đã nghỉ đúng ngần ấy
          ngày.
        </Typography.Paragraph>
      </Card>

      <NopDonModal mo={moNop} onDong={() => setMoNop(false)} onXong={lamMoi} />
    </Space>
  );
}
