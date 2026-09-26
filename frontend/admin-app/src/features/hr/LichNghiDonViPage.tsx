import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Badge,
  Calendar,
  Card,
  DatePicker,
  Empty,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';

import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { bayGio, ngayLich } from '@/shared/format';

import { LOAI_NGHI_LABEL, TRANG_THAI_DON_META, type DonNghiView } from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';
import { ngayVn } from './nghiPhepFormat';

/**
 * Lịch nghỉ của đơn vị — CN-04.9, **T57.18 vế (b)**.
 *
 * ## ⛔⛔ Con số trên mỗi ô là của BACKEND, ⛔ phải phép chia ở đây
 *
 * Quy tắc 3. Và ở đây nó ⛔ phải một quy ước cho đẹp: mẫu số là *quân số còn làm việc*, suy từ
 * `EmploymentStatus.daNghi()` — một luật nhân sự mà trình duyệt ⛔ có cách nào biết (nghỉ thai sản
 * vẫn là người của đơn vị, nghỉ việc thì ⛔); còn ngưỡng nằm trong `settings`, sửa được lúc chạy.
 * Chia lại ở giao diện là dựng **bản sao thứ hai của cả hai**, rồi một ngày chúng lệch nhau và ⛔ ai
 * biết bên nào đúng (quy tắc 13).
 *
 * ## ⚠ Bảng dưới là PHÉP CHIẾU, ⛔ phải một phép đo thứ hai
 *
 * Nó liệt kê đúng mảng `don` mà máy chủ trả về, ⛔ lọc lại theo ngày và ⛔ đếm gì. Thêm một phép
 * lọc theo ngày ở đây là chép vị từ *chồng khoảng* sang phía trình duyệt — đúng thứ luật 14 cấm, và
 * triệu chứng sẽ là một ô lịch ghi `2` trong khi bảng dưới hiện 3 dòng.
 *
 * ## ⚠ `tyLePhanTram === null` là trạng thái THỨ BA
 *
 * `null` = *đơn vị ⛔ có quân số nên ⛔ có mẫu số*, khác hẳn `0` = *⛔ ai nghỉ*. Vẽ `0%` cho cả hai
 * là nói dối ở đúng ca người dùng cần biết sự thật — cùng lý lẽ ba trạng thái của `toiDuyetDuoc`.
 */
export function LichNghiDonViPage() {
  const [donVi, setDonVi] = useState<string | undefined>(undefined);
  // ⚠ `bayGio()` chứ ⛔ `dayjs()` trần: tháng mặc định phải theo UTC+7 (T63.18). Máy trạm trong
  //   đơn vị hay lệch múi giờ sau khi cài lại Windows, và một lịch mở nhầm sang tháng khác ⛔ có
  //   dấu hiệu nào để người dùng nhận ra.
  const [thang, setThang] = useState<Dayjs>(() => bayGio());

  const lich = useQuery({
    queryKey: ['hr', 'nghi-phep', 'lich', donVi, thang.year(), thang.month() + 1],
    queryFn: () => nghiPhepApi.lich(donVi as string, thang.year(), thang.month() + 1),
    enabled: donVi !== undefined,
  });

  const theoNgay = new Map((lich.data?.ngay ?? []).map((o) => [o.ngay, o]));

  const cot: ColumnsType<DonNghiView> = [
    {
      title: 'CBNV',
      dataIndex: 'employeeName',
      key: 'employeeName',
      render: (ten: string | null, d) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text>{ten ?? '—'}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {d.employeeCode ?? '—'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Loại nghỉ',
      dataIndex: 'leaveType',
      key: 'leaveType',
      render: (v: DonNghiView['leaveType']) => LOAI_NGHI_LABEL[v],
    },
    {
      title: 'Từ ngày',
      dataIndex: 'fromDate',
      key: 'fromDate',
      render: (v: string) => ngayVn(v),
    },
    {
      title: 'Đến ngày',
      dataIndex: 'toDate',
      key: 'toDate',
      render: (v: string) => ngayVn(v),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'state',
      key: 'state',
      render: (v: DonNghiView['state']) => {
        const meta = TRANG_THAI_DON_META[v];
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space wrap>
          {/*
            ⛔⛔ `chiTrongPhamVi` bật ở đây vì backend THẬT SỰ chặn: `lichDonVi` gọi
            `ScopeGuard.requireReadableOrgUnit`, cùng vị từ `trongPhamVi` mà bốn biểu mẫu GHI đang
            dùng. ⛔ bật thì ô chọn bày cả cây rồi mỗi lượt chọn ngoài phạm vi trả 403 — người dùng
            ⛔ hiểu vì sao đơn vị ấy có trong danh sách.
          */}
          <OrgUnitTreeSelect
            value={donVi}
            onChange={setDonVi}
            placeholder="Chọn đơn vị"
            chiTrongPhamVi
          />
          <DatePicker
            picker="month"
            value={thang}
            allowClear={false}
            onChange={(v) => {
              if (v) {
                setThang(v);
              }
            }}
          />
        </Space>
      </Card>

      {donVi === undefined ? (
        <Card>
          <Empty description="Chọn một đơn vị để xem lịch nghỉ của tháng" />
        </Card>
      ) : (
        <>
          {lich.data && (
            <Alert
              type={lich.data.quanSo > 0 ? 'info' : 'warning'}
              showIcon
              title={
                lich.data.quanSo > 0
                  ? `${lich.data.tenDonVi} — quân số ${lich.data.quanSo} người; ô đỏ là ngày có từ ${lich.data.nguongPhanTram}% quân số nghỉ trở lên.`
                  : `${lich.data.tenDonVi} — đơn vị chưa có CBNV nào đang làm việc, nên hệ thống chưa tính được tỉ lệ nghỉ.`
              }
            />
          )}
          <Card loading={lich.isLoading}>
            <Calendar
              value={thang}
              onPanelChange={(v) => setThang(v)}
              cellRender={(ngay, info) => {
                if (info.type !== 'date') {
                  return info.originNode;
                }
                const o = theoNgay.get(ngayLich(ngay));
                if (!o || o.soNguoiNghi === 0) {
                  return null;
                }
                return (
                  <Badge
                    status={o.vuotNguong ? 'error' : 'processing'}
                    text={
                      o.tyLePhanTram === null
                        ? `${o.soNguoiNghi} người`
                        : `${o.soNguoiNghi} người · ${o.tyLePhanTram}%`
                    }
                  />
                );
              }}
            />
          </Card>
          <Card title={`Đơn nghỉ trong tháng (${lich.data?.don.length ?? 0})`}>
            <Table
              rowKey="publicId"
              columns={cot}
              dataSource={lich.data?.don ?? []}
              loading={lich.isLoading}
              pagination={false}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: 'Tháng này đơn vị không có đơn nghỉ nào' }}
            />
          </Card>
        </>
      )}
    </Space>
  );
}
