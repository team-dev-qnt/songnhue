import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  ColorPicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Slider,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  Upload,
  theme,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type GisLayerRequest, type GisLayerView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

const GOC = '/ops/gis-layers';

const NHAN_HINH_HOC: Record<GisLayerView['geometryType'], string> = {
  POINT: 'Điểm',
  LINE: 'Đường',
  POLYGON: 'Vùng',
  HON_HOP: 'Hỗn hợp',
};

/**
 * Quản lý lớp bản đồ GIS — CN-02.4 / M2.9 (WS-59).
 *
 * <h2>⛔⛔ Chỉ nhận GeoJSON — KML/KMZ bị TỪ CHỐI ở cổng nhận, kèm lý do</h2>
 *
 * Đặc tả viết *"GeoJSON/KMZ"*. Kho ⛔ **không có** bộ đọc KML/KMZ (KMZ là ZIP chứa KML ⇒ cần một bộ
 * phân tích XML theo lược đồ OGC). ⛔ Nhận rồi lưu là phương án **tệ nhất**: người dùng thấy “nạp
 * thành công”, lớp hiện trong danh sách, và bản đồ ⛔ không vẽ gì — họ sẽ đi báo hỏng *bản đồ* chứ
 * ⛔ không báo hỏng *lượt nạp*. ⭐ Từ 24/09 (T59.14) KML/KMZ ĐỔI được sang GeoJSON ở
 * backend nên nó ⛔ còn bị từ chối; tệp thật sự hỏng trả `OPS-2033` kèm lý do đo được.
 *
 * <h2>⛔ Độ mờ là phần trăm NGUYÊN 0–100</h2>
 *
 * ⛔ Không phải 0.0–1.0. Một phép đổi đơn vị giữa giao diện và CSDL là một chỗ để `0.8` và `80` lẫn
 * vào nhau, và triệu chứng là một lớp trong suốt hoàn toàn hoặc đục hoàn toàn.
 *
 * <h2>⛔ Thứ tự chồng lớp gửi TOÀN BỘ danh sách đã sắp</h2>
 *
 * ⛔ Không gửi “lên một bậc”: hai nơi cùng phải biết thứ tự hiện tại là hai nơi sẽ lệch. Cùng quyết
 * định với cây đơn vị (T25.x).
 */
export function GisLayersPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  // ⛔ Màu mặc định lấy từ `theme.useToken()` — nó LÀ màu chính của AntD, ⛔ không phải màu thương
  //   hiệu, nên nó ⛔ không thuộc về `design-tokens` (bậc thang `noHardcodedColors` CHỈ ĐƯỢC GIẢM).
  const { token } = theme.useToken();
  const qc = useQueryClient();
  const [form] = Form.useForm<GisLayerRequest>();
  const [dangSua, setDangSua] = useState<GisLayerView | null>(null);
  const [moModal, setMoModal] = useState(false);

  const coSua = hasPermission('ops:gis-layer:manage');

  const query = useQuery({
    queryKey: ['ops', 'gis-layers', 'tat-ca'],
    queryFn: () => api.get<GisLayerView[]>(GOC, { chiDangBat: false }),
  });

  const lamMoi = () => qc.invalidateQueries({ queryKey: ['ops', 'gis-layers'] });

  const luuMutation = useMutation({
    mutationFn: (v: GisLayerRequest) =>
      dangSua
        ? api.put<GisLayerView>(`${GOC}/${dangSua.publicId}`, v)
        : api.post<GisLayerView>(GOC, v),
    onSuccess: () => {
      message.success(dangSua ? 'Đã cập nhật lớp' : 'Đã tạo lớp — hãy nạp tệp GeoJSON');
      setMoModal(false);
      void lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được lớp');
    },
  });

  const xoaMutation = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`${GOC}/${publicId}`),
    onSuccess: () => {
      message.success('Đã xoá lớp');
      void lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được lớp'),
  });

  const thuTuMutation = useMutation({
    mutationFn: (theoThuTu: string[]) => api.patch<void>(`${GOC}/thu-tu`, { theoThuTu }),
    onSuccess: () => void lamMoi(),
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không sắp được thứ tự'),
  });

  const ds = query.data ?? [];

  const dichChuyen = (viTri: number, buoc: number) => {
    const moi = [...ds];
    const [bi] = moi.splice(viTri, 1);
    moi.splice(viTri + buoc, 0, bi);
    thuTuMutation.mutate(moi.map((l) => l.publicId));
  };

  const moTaoMoi = () => {
    setDangSua(null);
    form.resetFields();
    form.setFieldsValue({ color: token.colorPrimary, opacity: 70, active: true });
    setMoModal(true);
  };

  const moSua = (l: GisLayerView) => {
    setDangSua(l);
    // ⛔ Đặt giá trị TƯỜNG MINH sau `resetFields` — `Form.useForm()` sống ở component NGOÀI hộp
    //   thoại nên nó ⛔ không unmount theo `destroyOnHidden` (T51.12 · T53.7).
    form.resetFields();
    form.setFieldsValue({
      name: l.name,
      description: l.description ?? undefined,
      color: l.color,
      opacity: l.opacity,
      active: l.active,
    });
    setMoModal(true);
  };

  const columns: ColumnsType<GisLayerView> = [
    {
      title: 'Thứ tự',
      key: 'thu-tu',
      width: 110,
      render: (_, __, i) =>
        coSua ? (
          <Space size={0}>
            <Button
              type="text"
              size="small"
              icon={<ArrowUpOutlined />}
              aria-label="Đưa lớp lên một bậc"
              disabled={i === 0 || thuTuMutation.isPending}
              onClick={() => dichChuyen(i, -1)}
            />
            <Button
              type="text"
              size="small"
              icon={<ArrowDownOutlined />}
              aria-label="Đưa lớp xuống một bậc"
              disabled={i >= ds.length - 1 || thuTuMutation.isPending}
              onClick={() => dichChuyen(i, 1)}
            />
          </Space>
        ) : null,
    },
    { title: 'Tên lớp', dataIndex: 'name', width: 220, ellipsis: true },
    { title: 'Mô tả', dataIndex: 'description', width: 260, ellipsis: true },
    {
      title: 'Hình học',
      dataIndex: 'geometryType',
      width: 120,
      render: (t: GisLayerView['geometryType']) => NHAN_HINH_HOC[t],
    },
    {
      title: 'Màu',
      dataIndex: 'color',
      width: 90,
      render: (c: string) => (
        <Space size={4}>
          <span
            style={{
              display: 'inline-block',
              width: 14,
              height: 14,
              background: c,
              borderRadius: 3,
            }}
          />
          {c}
        </Space>
      ),
    },
    { title: 'Độ mờ', dataIndex: 'opacity', width: 90, render: (o: number) => `${o}%` },
    {
      title: 'Dữ liệu',
      key: 'du-lieu',
      width: 170,
      // ⛔⛔ "Chưa nạp tệp" và "0 đối tượng" là HAI trạng thái khác nhau — và cái sau ⛔ không tồn
      //    tại (backend từ chối tệp rỗng hình học bằng OPS-2026). Hiện một ô trống cho cả hai là
      //    xoá mất khác biệt ấy.
      render: (_, l) =>
        l.coTep ? (
          <Tag color="green">{l.soDoiTuong} đối tượng</Tag>
        ) : (
          <Tag color="orange">Chưa nạp tệp</Tag>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 110,
      render: (a: boolean) => (a ? <Tag color="blue">Đang bật</Tag> : <Tag>Đã tắt</Tag>),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 150,
      align: 'right',
      render: (_, l) =>
        coSua ? (
          <Space size={0}>
            <Upload
              accept=".geojson,.json,.kml,.kmz"
              showUploadList={false}
              customRequest={({ file, onSuccess, onError }) => {
                const fd = new FormData();
                fd.append('file', file as Blob);
                api
                  .upload(`${GOC}/${l.publicId}/tep`, fd)
                  .then((r) => {
                    message.success('Đã nạp tệp GeoJSON');
                    void lamMoi();
                    onSuccess?.(r);
                  })
                  .catch((e: unknown) => {
                    message.error(e instanceof ApiClientError ? e.message : 'Không nạp được tệp');
                    onError?.(e as Error);
                  });
              }}
            >
              <Button
                type="text"
                size="small"
                icon={<UploadOutlined />}
                aria-label={`Nạp tệp GeoJSON cho lớp ${l.name}`}
              />
            </Upload>
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              aria-label={`Sửa lớp ${l.name}`}
              onClick={() => moSua(l)}
            />
            <Popconfirm
              title="Xoá lớp này?"
              description="Lớp bị gỡ khỏi bản đồ; tệp GeoJSON vẫn giữ trong kho."
              okText="Xoá"
              cancelText="Huỷ"
              onConfirm={() => xoaMutation.mutate(l.publicId)}
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={`Xoá lớp ${l.name}`}
              />
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      title="Lớp bản đồ GIS"
      extra={
        coSua ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moTaoMoi}>
            Thêm lớp
          </Button>
        ) : null
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="Chỉ nhận tệp GeoJSON (≤ 20MB)"
        description={
          <>
            Tệp <b>KML/KMZ</b> hiện chưa đọc được — hệ thống <b>từ chối ngay</b> thay vì nhận rồi để
            bản đồ trống. Hãy chuyển sang GeoJSON (QGIS:{' '}
            <i>Xuất → Lưu đối tượng thành… → GeoJSON</i>) rồi nạp lại. Lớp nằm <b>dưới</b> trong
            bảng này được vẽ <b>trước</b>, tức nằm dưới trên bản đồ.
          </>
        }
      />

      <Table
        rowKey="publicId"
        loading={query.isLoading}
        dataSource={ds}
        columns={columns}
        pagination={false}
        locale={{ emptyText: 'Chưa có lớp bản đồ nào — bấm “Thêm lớp” để khai' }}
        // 110+220+260+120+90+90+170+110+150 = 1320.
        scroll={{ x: 1320 }}
      />

      <Modal
        open={moModal}
        title={dangSua ? `Sửa lớp “${dangSua.name}”` : 'Thêm lớp bản đồ'}
        onOk={() => void luu()}
        onCancel={() => setMoModal(false)}
        confirmLoading={luuMutation.isPending}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Tên lớp"
            rules={[{ required: true, message: 'Nhập tên lớp' }, { max: 255 }]}
          >
            <Input placeholder="Kênh mương cấp 1" />
          </Form.Item>
          <Form.Item name="description" label="Mô tả" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} placeholder="Nguồn số liệu, năm số hoá…" />
          </Form.Item>
          <Form.Item
            name="color"
            label="Màu"
            getValueFromEvent={(c: { toHexString: () => string }) => c.toHexString()}
          >
            <ColorPicker format="hex" disabledAlpha />
          </Form.Item>
          <Form.Item name="opacity" label="Độ mờ (%)" extra="0 = trong suốt, 100 = đặc">
            <Slider min={0} max={100} />
          </Form.Item>
          <Form.Item name="active" label="Đang bật" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Số đối tượng được đếm <b>lúc nạp tệp</b> và ghi lại — nó không đếm lại mỗi lần mở trang, vì
        làm thế nghĩa là tải cả tệp về chỉ để hiện một con số.
      </Typography.Paragraph>
    </Card>
  );

  async function luu() {
    try {
      luuMutation.mutate(await form.validateFields());
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường; ⛔ đừng để lời hứa rơi ra ngoài (`Modal.onOk` ⛔ không chờ).
    }
  }
}
