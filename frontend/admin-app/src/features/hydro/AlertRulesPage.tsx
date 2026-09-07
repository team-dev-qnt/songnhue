import { DeleteOutlined, EditOutlined, PlusOutlined, WarningOutlined } from '@ant-design/icons';
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
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import {
  type AlertConditionType,
  type AlertLevelRow,
  type AlertRuleRequest,
  type AlertRuleRow,
  type MeasurementType,
  type Station,
  type StationWithoutThresholdRow,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { LOAI_DIEU_KIEN_NGUONG } from './hydroVocabulary';

/**
 * Cấu hình **ngưỡng cảnh báo** — T33.2 / T33.11, hạng mục nghiệm thu **G9**.
 *
 * ⭐ Khối *"Điểm đo chưa cấu hình ngưỡng"* ở đầu trang là nửa **đọc** của `HYD-2003`. Không có
 * nó thì "chưa cấu hình ngưỡng" là một trạng thái đúng mà ⛔ không ai nhìn thấy — và ngày Công
 * ty đưa bộ mức thật, không ai biết còn thiếu điểm nào cho tới khi một trận lũ đi qua trong im
 * lặng.
 *
 * ⚠ Trang này có **ba** ô chọn phụ trợ (điểm đo · loại chỉ số · mức cảnh báo) và cả ba đọc bằng
 * quyền khác nhau. Hình dạng T27.20 / T28.25 đã tái phát **hai lần**: một biểu mẫu mà ô bắt
 * buộc đứng sau một quyền mà chính vai trò sở hữu biểu mẫu ⛔ không có. TECHNICIAN — vai trò
 * thật sẽ cấu hình ngưỡng — mang cả `hyd:station:view` lẫn `hyd:threshold:manage`, nên ba ô này
 * đều nạp được.
 */
export function AlertRulesPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AlertRuleRequest>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loaiDieuKien, setLoaiDieuKien] = useState<AlertConditionType>('GT');

  const coQuanLy = hasPermission('hyd:threshold:manage');

  const rulesQuery = useQuery({
    queryKey: ['hyd', 'alert-rules'],
    queryFn: () => api.get<AlertRuleRow[]>('/hyd/alert-rules'),
  });

  const thieuQuery = useQuery({
    queryKey: ['hyd', 'alert-rules', 'chua-cau-hinh'],
    queryFn: () => api.get<StationWithoutThresholdRow[]>('/hyd/alert-rules/chua-cau-hinh'),
  });

  const levelsQuery = useQuery({
    queryKey: ['hyd', 'alert-levels'],
    queryFn: () => api.get<AlertLevelRow[]>('/hyd/alert-levels'),
  });

  const stationsQuery = useQuery({
    queryKey: ['hyd', 'stations', 'chon-nguong'],
    queryFn: () => api.get<Station[]>('/hyd/stations'),
    enabled: modalVisible && !editingId,
  });

  const typesQuery = useQuery({
    queryKey: ['hyd', 'measurement-types'],
    queryFn: () => api.get<MeasurementType[]>('/hyd/measurement-types'),
    enabled: modalVisible && !editingId,
  });

  const lamMoi = () => {
    void queryClient.invalidateQueries({ queryKey: ['hyd', 'alert-rules'] });
  };

  const bao = (caught: unknown, macDinh: string) => {
    if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);
  };

  const createMutation = useMutation({
    mutationFn: (data: AlertRuleRequest) => api.post<AlertRuleRow>('/hyd/alert-rules', data),
    onSuccess: () => {
      message.success('Đã thêm ngưỡng');
      setModalVisible(false);
      lamMoi();
    },
    onError: (caught: unknown) => bao(caught, 'Không thêm được ngưỡng'),
  });

  const updateMutation = useMutation({
    mutationFn: (data: AlertRuleRequest) =>
      api.put<AlertRuleRow>(`/hyd/alert-rules/${editingId}`, {
        conditionType: data.conditionType,
        thresholdValue: data.thresholdValue,
        thresholdValueHigh: data.thresholdValueHigh,
        delayMinutes: data.delayMinutes,
        active: data.active,
        note: data.note,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật ngưỡng');
      setModalVisible(false);
      lamMoi();
    },
    onError: (caught: unknown) => bao(caught, 'Không cập nhật được ngưỡng'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/hyd/alert-rules/${id}`),
    onSuccess: () => {
      message.success('Đã xoá ngưỡng');
      lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được ngưỡng'),
  });

  const moThem = () => {
    setEditingId(null);
    setLoaiDieuKien('GT');
    form.resetFields();
    setModalVisible(true);
  };

  const moSua = (row: AlertRuleRow) => {
    setEditingId(row.id);
    setLoaiDieuKien(row.conditionType);
    form.setFieldsValue({
      stationId: row.stationId,
      measurementTypeCode: row.measurementTypeCode,
      alertLevelId: row.alertLevelId,
      conditionType: row.conditionType,
      thresholdValue: row.thresholdValue,
      thresholdValueHigh: row.thresholdValueHigh,
      delayMinutes: row.delayMinutes,
      active: row.active,
      note: row.note,
    });
    setModalVisible(true);
  };

  const columns: ColumnsType<AlertRuleRow> = [
    {
      title: 'Điểm đo',
      width: 240,
      ellipsis: true,
      render: (_, r) => `${r.stationCode} — ${r.stationName}`,
    },
    { title: 'Chỉ số', dataIndex: 'measurementTypeName', width: 140, ellipsis: true },
    {
      title: 'Mức',
      width: 160,
      ellipsis: true,
      render: (_, r) => <Tag>{r.alertLevelName}</Tag>,
    },
    {
      title: 'Điều kiện',
      width: 170,
      render: (_, r) => LOAI_DIEU_KIEN_NGUONG[r.conditionType].label,
    },
    {
      title: 'Ngưỡng',
      width: 170,
      // ⚠ Giá trị ra dây là CHUỖI (@JsonFormat STRING) — hiện nguyên văn, ⛔ không Number().
      render: (_, r) =>
        r.conditionType === 'OUT_OF_RANGE'
          ? `${r.thresholdValue} … ${r.thresholdValueHigh ?? '—'} ${r.unit}`
          : `${r.thresholdValue} ${r.unit}${r.conditionType === 'RATE_OF_CHANGE' ? '/giờ' : ''}`,
    },
    {
      title: 'Chờ xác nhận',
      dataIndex: 'delayMinutes',
      width: 130,
      render: (v: number) =>
        v === 0 ? (
          <Typography.Text type="secondary">Báo ngay</Typography.Text>
        ) : (
          <Tooltip title="Điều kiện phải giữ được bấy nhiêu phút mới báo động — chống nhiễu cảm biến">
            {v} phút
          </Tooltip>
        ),
    },
    {
      title: 'Đang dùng',
      dataIndex: 'active',
      width: 110,
      render: (v: boolean) => (v ? <Tag color="blue">Đang dùng</Tag> : <Tag>Đã tắt</Tag>),
    },
    {
      title: '',
      width: 110,
      align: 'right',
      render: (_, row) =>
        coQuanLy ? (
          <Space>
            <Button type="text" icon={<EditOutlined />} onClick={() => moSua(row)} />
            <Popconfirm title="Xoá ngưỡng này?" onConfirm={() => deleteMutation.mutate(row.id)}>
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  const chuaCauHinh = thieuQuery.data ?? [];
  const khongCoMuc = (levelsQuery.data ?? []).length === 0;

  return (
    <Card
      title="Ngưỡng cảnh báo"
      extra={
        coQuanLy ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moThem} disabled={khongCoMuc}>
            Thêm ngưỡng
          </Button>
        ) : null
      }
    >
      {khongCoMuc && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Chưa khai mức cảnh báo nào — chưa cấu hình được ngưỡng"
          description="Mỗi ngưỡng phải thuộc về một mức cảnh báo. Vào Dữ liệu thuỷ văn → Mức cảnh báo để khai bộ mức Công ty đã duyệt (G9-a)."
        />
      )}

      {chuaCauHinh.length > 0 && (
        <Alert
          type="info"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16 }}
          message={`${chuaCauHinh.length} điểm đo đang hoạt động chưa cấu hình ngưỡng nào`}
          description={
            <>
              Những điểm này ⛔ không phát cảnh báo nào, dù số liệu vẫn về đầy đủ. Đây là trạng thái
              hợp lệ khi Công ty chưa đưa bộ ngưỡng — nêu ra để không ai nhầm im lặng với bình
              thường.
              <br />
              <Typography.Text type="secondary">
                {chuaCauHinh
                  .slice(0, 12)
                  .map((s) => s.code)
                  .join(' · ')}
                {chuaCauHinh.length > 12 ? ` … và ${chuaCauHinh.length - 12} điểm nữa` : ''}
              </Typography.Text>
            </>
          }
        />
      )}

      <Table
        rowKey="id"
        size="small"
        loading={rulesQuery.isLoading}
        dataSource={rulesQuery.data ?? []}
        columns={columns}
        pagination={false}
        scroll={{ x: 1330 }}
        locale={{ emptyText: 'Chưa cấu hình ngưỡng nào' }}
      />

      <Modal
        open={modalVisible}
        title={editingId ? 'Sửa ngưỡng' : 'Thêm ngưỡng'}
        width={640}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ active: true, delayMinutes: 0, conditionType: 'GT' }}
          onFinish={(v) => (editingId ? updateMutation.mutate(v) : createMutation.mutate(v))}
        >
          <Form.Item
            name="stationId"
            label="Điểm đo"
            rules={[{ required: true, message: 'Chọn điểm đo' }]}
            extra={
              editingId
                ? '⛔ Không sửa được: đổi điểm đo là gán lịch sử cảnh báo của ngưỡng này sang một điểm đo khác.'
                : 'Gõ để tìm theo mã hoặc tên.'
            }
          >
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!!editingId}
              loading={stationsQuery.isLoading}
              placeholder="Chọn điểm đo"
              options={(stationsQuery.data ?? []).map((s) => ({
                value: s.id,
                label: `${s.code} — ${s.name}`,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="measurementTypeCode"
            label="Loại chỉ số"
            rules={[{ required: true, message: 'Chọn loại chỉ số' }]}
          >
            <Select
              disabled={!!editingId}
              loading={typesQuery.isLoading}
              placeholder="Chọn loại chỉ số"
              options={(typesQuery.data ?? []).map((t) => ({
                value: t.code,
                label: `${t.name} (${t.unit})`,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="alertLevelId"
            label="Mức cảnh báo"
            rules={[{ required: true, message: 'Chọn mức cảnh báo' }]}
            extra="Mỗi điểm đo chỉ có một ngưỡng cho mỗi cặp (loại chỉ số × mức)."
          >
            <Select
              disabled={!!editingId}
              loading={levelsQuery.isLoading}
              placeholder="Chọn mức"
              options={(levelsQuery.data ?? []).map((l) => ({
                value: l.id,
                label: `${l.name} (hạng ${l.severityRank})`,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="conditionType"
            label="Loại điều kiện"
            rules={[{ required: true, message: 'Chọn loại điều kiện' }]}
            extra={LOAI_DIEU_KIEN_NGUONG[loaiDieuKien].moTaThamSo}
          >
            <Select
              onChange={(v: AlertConditionType) => setLoaiDieuKien(v)}
              options={(Object.keys(LOAI_DIEU_KIEN_NGUONG) as AlertConditionType[]).map((k) => ({
                value: k,
                label: LOAI_DIEU_KIEN_NGUONG[k].label,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="thresholdValue"
            label={LOAI_DIEU_KIEN_NGUONG[loaiDieuKien].canCanTren ? 'Cận dưới' : 'Giá trị ngưỡng'}
            rules={[{ required: true, message: 'Nhập giá trị ngưỡng' }]}
            // ⚠ `Input` chữ, ⛔ KHÔNG `InputNumber`: giá trị ra/vào dây là CHUỖI để giữ nguyên
            //   thang đo (2.30 ⛔ không thành 2.3). Cùng lý do với `parameterValue` ở MOD-02.
            extra="Nhập số thập phân dùng dấu chấm, ví dụ 2.500"
          >
            <Input placeholder="2.500" />
          </Form.Item>

          {LOAI_DIEU_KIEN_NGUONG[loaiDieuKien].canCanTren && (
            <Form.Item
              name="thresholdValueHigh"
              label="Cận trên"
              rules={[{ required: true, message: 'Ra ngoài khoảng phải nhập đủ cả hai cận' }]}
              extra="Phải lớn hơn cận dưới."
            >
              <Input placeholder="4.000" />
            </Form.Item>
          )}

          <Form.Item
            name="delayMinutes"
            label="Chờ bao lâu mới báo động (phút)"
            extra="0 = báo ngay. Đặt lớn hơn 0 để một cú nhiễu cảm biến thoáng qua ⛔ không đánh thức Ban điều hành — điều kiện phải còn vượt ở một lượt đo sau nữa mới thành cảnh báo thật."
          >
            <InputNumber min={0} max={1440} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="active"
            label="Đang dùng"
            valuePropName="checked"
            extra="Tắt để tạm ngừng ngưỡng này mà ⛔ không mất lịch sử cảnh báo đã ghi."
          >
            <Switch />
          </Form.Item>

          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

export default AlertRulesPage;
