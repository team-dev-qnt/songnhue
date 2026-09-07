import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { alertLevelColorTokens, laKhoaMauHopLe, mauMucCanhBao, neutralColors } from 'design-tokens';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type AlertLevelRequest, type AlertLevelRow } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

/**
 * Danh mục **mức cảnh báo ngưỡng** — T33.1, hạng mục nghiệm thu **G9**.
 *
 * ⛔⛔ Danh sách này **cố ý rỗng** khi hệ thống mới dựng. Bộ mức thật là **G9-a**, Công ty chưa
 * chốt, và migration ⛔ không seed dòng nào. Rỗng là trạng thái ĐÚNG, ⛔ không phải lỗi cấu
 * hình — nó nghĩa là *chưa có ngưỡng nào, nên chưa có cảnh báo nào*.
 *
 * ⛔ **Màu mức là một KHOÁ trong `design-tokens`, ⛔ không phải mã hex.** Dự án đang mang nợ T25.23
 * vì 29 mã màu ghi cứng lọt vào admin-app, và cách rẻ nhất để không sinh thêm là làm cho giá trị
 * sai **không lưu được**.
 *
 * ⭐⭐ **T35.14 (04/09/2026) — nửa cặp đọc–ghi vừa được nối.** Trước đó ô này là `<Input>` gõ tay và
 * cột trên bảng hiện khoá dưới dạng **chữ trần**: `colorToken = 'banana'` qua được regex của
 * service, qua được `ck_alert_levels_color_token`, lưu thành công, rồi hiện lên màn hình đúng chữ
 * "banana" — vì ⛔ **không bảng ánh xạ nào trong toàn kho** đổi khoá thành màu (luật 27, luật 15).
 * Nay: ô **chọn** từ bảng màu chung, cột hiện **ô màu thật**, và service từ chối khoá ngoài bảng.
 *
 * ⚠ Thêm một **mức** mới vẫn ⛔ không cần deploy (quy tắc 16) — mức mới chọn lại một slot màu có
 * sẵn. Chỉ thêm một **slot màu** mới mới phải sửa mã, và khi đó phải sửa **cả hai** tệp
 * (`design-tokens` + `AlertLevelService.java`); `alertLevelColors.test.ts` canh đúng điều đó.
 */
export function AlertLevelsPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AlertLevelRequest>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  const coQuanLy = hasPermission('hyd:threshold:manage');

  const query = useQuery({
    queryKey: ['hyd', 'alert-levels'],
    queryFn: () => api.get<AlertLevelRow[]>('/hyd/alert-levels'),
  });

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['hyd', 'alert-levels'] });

  const bao = (caught: unknown, macDinh: string) => {
    if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);
  };

  const createMutation = useMutation({
    mutationFn: (data: AlertLevelRequest) => api.post<AlertLevelRow>('/hyd/alert-levels', data),
    onSuccess: () => {
      message.success('Đã thêm mức cảnh báo');
      setModalVisible(false);
      void lamMoi();
    },
    onError: (caught: unknown) => bao(caught, 'Không thêm được mức cảnh báo'),
  });

  const updateMutation = useMutation({
    mutationFn: (data: AlertLevelRequest) =>
      api.put<AlertLevelRow>(`/hyd/alert-levels/${editingId}`, data),
    onSuccess: () => {
      message.success('Đã cập nhật mức cảnh báo');
      setModalVisible(false);
      void lamMoi();
    },
    onError: (caught: unknown) => bao(caught, 'Không cập nhật được mức cảnh báo'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/hyd/alert-levels/${id}`),
    onSuccess: () => {
      message.success('Đã xoá mức cảnh báo');
      void lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được'),
  });

  const moThem = () => {
    setEditingId(null);
    form.resetFields();
    setModalVisible(true);
  };

  const moSua = (row: AlertLevelRow) => {
    setEditingId(row.id);
    form.setFieldsValue(row);
    setModalVisible(true);
  };

  const columns: ColumnsType<AlertLevelRow> = [
    { title: 'Mã', dataIndex: 'code', width: 160, ellipsis: true },
    { title: 'Tên mức', dataIndex: 'name', width: 260, ellipsis: true },
    {
      title: 'Hạng nặng nhẹ',
      dataIndex: 'severityRank',
      width: 140,
      // ⭐ Nói ra ý nghĩa ngay trên bảng: con số này ⛔ không phải thứ tự hiển thị.
      render: (v: number) => <Tag>{v}</Tag>,
    },
    {
      // ⭐⭐ T35.14 — trước đây cột này hiện khoá dưới dạng CHỮ TRẦN, và ⛔ không nơi nào trong kho
      //    đổi khoá thành màu. Nghĩa là `colorToken = 'banana'` lưu thành công rồi hiện lên đúng
      //    chữ "banana": nửa ghi hoàn chỉnh, nửa đọc không tồn tại (luật 27).
      title: 'Màu mức',
      dataIndex: 'colorToken',
      width: 200,
      render: (v: string) => (
        <Space size={8}>
          <span
            aria-hidden
            style={{
              display: 'inline-block',
              width: 14,
              height: 14,
              borderRadius: 3,
              background: mauMucCanhBao(v),
              border: `1px solid ${neutralColors.border}`,
            }}
          />
          {laKhoaMauHopLe(v) ? (
            <Typography.Text>{v}</Typography.Text>
          ) : (
            // ⛔ Khoá ngoài bảng phải NHÌN THẤY ĐƯỢC, ⛔ không im lặng rơi về một màu mặc định:
            //    dữ liệu tạo trước T35.14 có thể mang khoá lạ, và một ô xám không giải thích sẽ bị
            //    đọc thành "chưa cấu hình" thay vì "cấu hình sai".
            <Tooltip title="Khoá này không có trong bảng màu chung nên không vẽ được — sửa lại mức để chọn một khoá hợp lệ.">
              <Typography.Text type="danger">{v} ⚠</Typography.Text>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: 'Đang dùng',
      dataIndex: 'active',
      width: 110,
      render: (v: boolean) => (v ? <Tag color="blue">Đang dùng</Tag> : <Tag>Đã tắt</Tag>),
    },
    { title: 'Ghi chú', dataIndex: 'description', ellipsis: true },
    {
      title: '',
      width: 110,
      align: 'right',
      render: (_, row) =>
        coQuanLy ? (
          <Space>
            <Button type="text" icon={<EditOutlined />} onClick={() => moSua(row)} />
            <Popconfirm
              title="Xoá mức cảnh báo này?"
              description="Không xoá được nếu còn ngưỡng đang trỏ vào nó."
              onConfirm={() => deleteMutation.mutate(row.id)}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      title="Mức cảnh báo ngưỡng"
      extra={
        coQuanLy ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moThem}>
            Thêm mức
          </Button>
        ) : null
      }
    >
      {/* ⛔ Nói thẳng vì sao bảng rỗng. Một bảng rỗng không lý do trông y hệt một hệ thống hỏng. */}
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Bộ mức cảnh báo do Công ty quyết định (mục G9-a)"
        description={
          <>
            Danh sách để trống là đúng — hệ thống ⛔ không tự đặt sẵn mức nào, vì mỗi mức đi kèm
            những con số ngưỡng thật. Chưa có mức nào thì chưa cấu hình được ngưỡng, và ⛔ chưa cảnh
            báo nào được phát.
            <br />
            <Typography.Text type="secondary">
              Hạng nặng nhẹ: số lớn hơn là nặng hơn. Khi một số đo vượt nhiều mức cùng lúc, cảnh báo
              mang mức nặng nhất. Mỗi hạng chỉ thuộc về một mức.
            </Typography.Text>
          </>
        }
      />

      <Table
        rowKey="id"
        size="small"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        // ⚠ `scroll.x` = tổng bề ngang cột thật. Thiếu nó thì `tableLayout` rơi về `auto` và cột
        //   không khai `width` bị bóp xuống `min-content` — đúng lỗi đã đo được ở ApiSourcesPage.
        scroll={{ x: 1060 }}
        locale={{ emptyText: 'Chưa có mức cảnh báo nào — chờ Công ty chốt bộ mức (G9-a)' }}
      />

      <Modal
        open={modalVisible}
        title={editingId ? 'Sửa mức cảnh báo' : 'Thêm mức cảnh báo'}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ active: true }}
          onFinish={(v) => (editingId ? updateMutation.mutate(v) : createMutation.mutate(v))}
        >
          <Form.Item
            name="code"
            label="Mã mức"
            rules={[{ required: true, message: 'Nhập mã mức' }]}
            extra="Mã ngắn dùng trong báo cáo và bản xuất. Sẽ được viết hoa khi lưu."
          >
            <Input placeholder="BD1" />
          </Form.Item>

          <Form.Item
            name="name"
            label="Tên mức"
            rules={[{ required: true, message: 'Nhập tên mức' }]}
          >
            <Input placeholder="Báo động I" />
          </Form.Item>

          <Form.Item
            name="severityRank"
            label="Hạng nặng nhẹ"
            rules={[{ required: true, message: 'Nhập hạng' }]}
            extra="Số lớn hơn là nặng hơn. Vượt nhiều mức cùng lúc thì cảnh báo mang mức nặng nhất — mỗi hạng chỉ một mức được dùng."
          >
            <InputNumber min={1} max={999} style={{ width: '100%' }} placeholder="10" />
          </Form.Item>

          {/* ⭐ T35.14 — ô CHỌN thay ô gõ tay. Bảng màu là một tập slot cố định (`design-tokens`),
              nên gõ tay chỉ mở đường cho một khoá không vẽ được. Thêm một MỨC mới vẫn ⛔ không cần
              deploy (quy tắc 16) — mức mới chọn lại một slot có sẵn. */}
          <Form.Item
            name="colorToken"
            label="Màu mức"
            rules={[{ required: true, message: 'Chọn màu cho mức' }]}
            extra="Màu này dùng chung cho lớp bản đồ, biểu tuyến sông và lịch sử cảnh báo — cùng một mức ⛔ không được hiện hai màu ở hai màn hình."
          >
            <Select
              placeholder="Chọn màu"
              options={alertLevelColorTokens.map((khoa) => ({
                value: khoa,
                label: (
                  <Space size={8}>
                    <span
                      aria-hidden
                      style={{
                        display: 'inline-block',
                        width: 14,
                        height: 14,
                        borderRadius: 3,
                        background: mauMucCanhBao(khoa),
                        border: `1px solid ${neutralColors.border}`,
                      }}
                    />
                    {khoa}
                  </Space>
                ),
              }))}
            />
          </Form.Item>

          <Form.Item
            name="active"
            label="Đang dùng"
            valuePropName="checked"
            extra="Tắt một mức làm im MỌI ngưỡng đang trỏ vào nó — dùng khi Công ty tạm ngừng một cấp báo động, không phải để ẩn khỏi danh sách."
          >
            <Switch />
          </Form.Item>

          <Form.Item name="description" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

export default AlertLevelsPage;
