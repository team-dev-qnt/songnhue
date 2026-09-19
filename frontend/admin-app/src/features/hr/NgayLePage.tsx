import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Space,
  Table,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';

import { bayGio } from '@/shared/format';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { SO_NGAY_LE_THEO_LUAT, type NgayLeView } from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';
import { ngayVn } from './nghiPhepFormat';

interface GiaTriBieuMau {
  holidayDate: Dayjs;
  name: string;
  note?: string;
}

/**
 * Danh mục ngày nghỉ lễ — CN-04.9 (WS-57).
 *
 * <h2>⛔⛔ Bảng này GIAO ĐI với 8 hàng, và 8 hàng ấy là LUẬT — ⛔ không phải "seed cho đẹp demo"</h2>
 *
 * `V202608131008` dựng đúng những ngày lễ có **ngày dương lịch cố định** do Điều 112 BLLĐ 2019 ấn
 * định: 1/1, 30/4, 1/5, 2/9 cho hai năm. Tết Nguyên đán và Giỗ Tổ Hùng Vương theo **âm lịch**, đổi
 * ngày dương mỗi năm, nên hệ thống ⛔ **không suy ra được** — chúng phải do Công ty khai.
 *
 * <h2>⛔ Vì sao màn hình hiện SỐ ĐÃ KHAI thay vì một cờ xanh/đỏ</h2>
 *
 * Một cờ *"đã cấu hình ngày lễ chưa"* sẽ nói **CÓ** cho mọi năm đã seed — trong khi **Tết, kỳ nghỉ
 * dài nhất năm, vẫn thiếu**. Cờ ấy nói dối đúng ở ca nguy hiểm nhất (luật 9). ⇒ Hiện `X/11` kèm
 * căn cứ pháp lý, để người vận hành thấy khoảng trống thay vì một dấu tích.
 *
 * <h2>⚠ Xoá là xoá MỀM, và đơn đã nộp ⛔ không đổi số ngày</h2>
 *
 * `leave_requests.working_days` đóng băng lúc nộp. Gỡ một ngày lễ ⛔ không làm đơn cũ đổi số — người
 * lao động đã nghỉ đúng ngần ấy ngày.
 */
export function NgayLePage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<GiaTriBieuMau>();
  const [modalMo, setModalMo] = useState(false);
  const [dangSua, setDangSua] = useState<NgayLeView | null>(null);

  const coGhi = hasPermission('hr:contract:manage');

  const query = useQuery({
    queryKey: ['hr', 'ngay-le'],
    queryFn: () => nghiPhepApi.ngayLe(),
  });

  const lamMoi = () => {
    void queryClient.invalidateQueries({ queryKey: ['hr', 'ngay-le'] });
    // Số ngày công của MỌI đơn chưa nộp đổi theo danh mục này — ⛔ đừng để màn hình nộp đơn còn
    // giữ một phép xem trước đã hết đúng.
    void queryClient.invalidateQueries({ queryKey: ['hr', 'nghi-phep'] });
  };

  const luuMutation = useMutation({
    mutationFn: (v: GiaTriBieuMau) => {
      const body = {
        holidayDate: v.holidayDate.format('YYYY-MM-DD'),
        name: v.name,
        note: v.note,
      };
      return dangSua ? nghiPhepApi.suaNgayLe(dangSua.publicId, body) : nghiPhepApi.taoNgayLe(body);
    },
    onSuccess: () => {
      message.success(dangSua ? 'Đã cập nhật ngày lễ' : 'Đã thêm ngày lễ');
      setModalMo(false);
      lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được ngày lễ');
    },
  });

  const xoaMutation = useMutation({
    mutationFn: (publicId: string) => nghiPhepApi.xoaNgayLe(publicId),
    onSuccess: () => {
      message.success('Đã xoá ngày lễ');
      lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được ngày lễ'),
  });

  const moTaoMoi = () => {
    setDangSua(null);
    form.resetFields();
    setModalMo(true);
  };

  const moSua = (record: NgayLeView) => {
    setDangSua(record);
    // ⛔⛔ Đặt giá trị TƯỜNG MINH sau `resetFields`. `Form.useForm()` sống ở component NGOÀI hộp
    //    thoại nên nó ⛔ không unmount theo `destroyOnHidden` — đúng cơ chế đã làm hai hộp thoại
    //    HRM trộn dữ liệu giữa hai con người (T51.12 · T53.7).
    form.resetFields();
    form.setFieldsValue({
      holidayDate: dayjs(record.holidayDate),
      name: record.name,
      note: record.note ?? undefined,
    });
    setModalMo(true);
  };

  const luu = async () => {
    try {
      luuMutation.mutate(await form.validateFields());
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường; ⛔ đừng để lời hứa rơi ra ngoài (`Modal.onOk` ⛔ không chờ).
    }
  };

  const columns: ColumnsType<NgayLeView> = [
    {
      title: 'Ngày',
      dataIndex: 'holidayDate',
      width: 140,
      render: (d: string) => ngayVn(d),
    },
    { title: 'Tên ngày lễ', dataIndex: 'name', width: 280, ellipsis: true },
    { title: 'Ghi chú', dataIndex: 'note', width: 320, ellipsis: true },
    {
      title: '',
      key: 'thao-tac',
      width: 110,
      align: 'right',
      render: (_, record) =>
        coGhi ? (
          <Space size={0}>
            <Button
              type="text"
              icon={<EditOutlined />}
              aria-label={`Sửa ngày lễ ${record.name}`}
              onClick={() => moSua(record)}
            />
            <Popconfirm
              title="Xoá ngày lễ này?"
              description="Đơn nghỉ đã nộp giữ nguyên số ngày công đã tính."
              okText="Xoá"
              cancelText="Huỷ"
              onConfirm={() => xoaMutation.mutate(record.publicId)}
            >
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label={`Xoá ngày lễ ${record.name}`}
              />
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  // Quanh giao thừa, `dayjs().year()` trên máy lệch múi giờ đếm ngày lễ của năm KIA (T63.18).
  const namNay = bayGio().year();
  const soNamNay = (query.data ?? []).filter((n) => dayjs(n.holidayDate).year() === namNay).length;

  return (
    <Card
      title="Danh mục ngày nghỉ lễ"
      extra={
        coGhi ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moTaoMoi}>
            Thêm ngày lễ
          </Button>
        ) : null
      }
    >
      {/* ⛔⛔ CON SỐ, ⛔ không phải một dấu tích. Xem javadoc lớp. */}
      <Alert
        type={soNamNay >= SO_NGAY_LE_THEO_LUAT ? 'success' : 'warning'}
        showIcon
        style={{ marginBottom: 16 }}
        title={`Năm ${namNay} đã khai ${soNamNay}/${SO_NGAY_LE_THEO_LUAT} ngày nghỉ lễ`}
        description={
          <Space orientation="vertical" size={4} style={{ width: '100%' }}>
            <Progress
              percent={Math.round((soNamNay * 100) / SO_NGAY_LE_THEO_LUAT)}
              status={soNamNay >= SO_NGAY_LE_THEO_LUAT ? 'success' : 'active'}
              showInfo={false}
            />
            <Typography.Text>
              Điều 112 Bộ luật Lao động 2019 quy định <b>{SO_NGAY_LE_THEO_LUAT}</b> ngày nghỉ lễ mỗi
              năm. Hệ thống chỉ khai sẵn những ngày có <b>ngày dương lịch cố định</b> (1/1, 30/4,
              1/5, 2/9). Tết Nguyên đán và Giỗ Tổ Hùng Vương theo âm lịch — đổi ngày dương mỗi năm
              nên ⛔ không suy ra được, phải nhập tay.
            </Typography.Text>
            <Typography.Text type="secondary">
              Chừng nào còn thiếu, số ngày công của đơn nghỉ có thể <b>cao hơn thực tế</b>.
            </Typography.Text>
          </Space>
        }
      />

      <Table
        rowKey="publicId"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        locale={{ emptyText: 'Chưa có ngày lễ nào — bấm “Thêm ngày lễ” để khai danh mục' }}
        // 140+280+320+110 = 850.
        scroll={{ x: 850 }}
      />

      <Modal
        open={modalMo}
        title={dangSua ? 'Sửa ngày nghỉ lễ' : 'Thêm ngày nghỉ lễ'}
        onOk={() => void luu()}
        onCancel={() => setModalMo(false)}
        confirmLoading={luuMutation.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="holidayDate"
            label="Ngày"
            rules={[{ required: true, message: 'Chọn ngày' }]}
            extra="Mỗi năm khai riêng — lễ âm lịch rơi vào ngày dương khác nhau."
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="name"
            label="Tên ngày lễ"
            rules={[{ required: true, message: 'Nhập tên ngày lễ' }, { max: 255 }]}
          >
            <Input placeholder="Mùng 1 Tết Nguyên đán" />
          </Form.Item>
          <Form.Item name="note" label="Ghi chú" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} placeholder="Căn cứ, quyết định nghỉ bù…" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
