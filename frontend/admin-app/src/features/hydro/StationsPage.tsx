import { ApartmentOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Segmented,
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
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import {
  type ApiSource,
  type MeasurementType,
  type Station,
  type StationRequest,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { LienKetCongTrinhModal } from './LienKetCongTrinhModal';
import {
  VAI_TRO_KHONG_CAN_CONG_TRINH,
  VAI_TRO_VI_TRI,
  VAI_TRO_VI_TRI_OPTIONS,
} from './hydroVocabulary';

type BoLoc = 'TAT_CA' | 'CHUA_GAN_DON_VI' | 'THIEU_LIEN_KET';

/**
 * Danh mục điểm đo — CN-03.1 (T28.3, T28.8, T28.9).
 *
 * ⛔ **Mã ánh xạ API không sửa được sau khi tạo.** Nó là khoá nối duy nhất giữa số liệu
 * nguồn trả về và điểm đo; đổi nó là âm thầm gán toàn bộ lịch sử của trạm này sang trạm
 * khác — biểu đồ vẫn vẽ đẹp, chỉ là của nhầm trạm. Ô nhập bị khoá khi sửa, và backend từ
 * chối bằng `HYD-2006` nếu vẫn có ai gửi lên giá trị khác.
 *
 * ⚠ **Tên điểm đo luôn hiện kèm mã.** Có hai công trình khác nhau cùng tên "Yên Nghĩa", và
 * cụm Liên Mạc có cả "Cống Liên Mạc" lẫn "Liên Mạc 2" — trực ban nhìn tên trần sẽ nhầm.
 */
export function StationsPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<StationRequest>();
  const [dangSua, setDangSua] = useState<Station | null>(null);
  const [taoMoiThuCong, setTaoMoiThuCong] = useState(false);
  const [dangLienKet, setDangLienKet] = useState<Station | null>(null);
  const [boLoc, setBoLoc] = useState<BoLoc>('TAT_CA');
  const [searchParams, setSearchParams] = useSearchParams();

  const coQuanLy = hasPermission('hyd:station:manage');

  /**
   * ⭐ Mã API điền sẵn khi tới từ màn hình **Mã lạ từ nguồn** (T31.13).
   *
   * Đây là nửa ĐỌC của cặp mà nút *"Khai thành điểm đo"* mở ra. Thiếu nửa này thì nút ấy chỉ
   * chuyển trang và người dùng phải tự chép lại `F01613` bằng mắt — đúng loại nửa cặp đọc–ghi
   * mà luật 27 gọi tên, và cũng đúng chỗ một mã gõ sai gán số liệu sang trạm khác.
   */
  const maApiDatSan = searchParams.get('apiCode');

  /** Bỏ tham số khỏi URL: F5 sau khi lưu ⛔ không được mở lại biểu mẫu với mã đã dùng. */
  const xoaMaDatSan = () => {
    if (maApiDatSan) setSearchParams({}, { replace: true });
  };

  /**
   * ⭐ Modal mở là trạng thái **SUY RA** từ URL, ⛔ không phải một bản sao trong state.
   *
   * Bản đầu dùng `useEffect` gọi `setTaoMoi(true)` — ESLint chặn đúng
   * (`react-hooks/set-state-in-effect`), và luật ấy đúng ở đây chứ không chỉ đúng về hiệu năng:
   * chép URL vào state là dựng nửa thứ hai của một cặp phải tự đồng bộ, tức đúng hình dạng lỗi
   * luật 27 mô tả. Suy ra thì không có gì để lệch.
   */
  const taoMoi = taoMoiThuCong || (!!maApiDatSan && coQuanLy);

  const dongTaoMoi = () => {
    setTaoMoiThuCong(false);
    xoaMaDatSan();
  };

  const query = useQuery({
    queryKey: ['hyd', 'stations'],
    queryFn: () => api.get<Station[]>('/hyd/stations'),
  });
  const nguonQuery = useQuery({
    queryKey: ['hyd', 'api-sources'],
    queryFn: () => api.get<ApiSource[]>('/hyd/api-sources'),
    // ⚠ Ô "Nguồn dữ liệu" là trường BẮT BUỘC của biểu mẫu điểm đo. Chặn query này sau riêng
    // `hyd:api-source:manage` làm TECHNICIAN — vai trò duy nhất ngoài SA/ADMIN có
    // `hyd:station:manage` — không tạo nổi điểm đo nào (ô chọn rỗng vĩnh viễn). Endpoint đã nhận
    // cả hai quyền ở chế độ HOẶC từ 01/09.
    enabled: hasPermission('hyd:api-source:manage') || coQuanLy,
  });
  const loaiQuery = useQuery({
    queryKey: ['hyd', 'measurement-types'],
    queryFn: () => api.get<MeasurementType[]>('/hyd/measurement-types'),
  });

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['hyd', 'stations'] });

  const createMutation = useMutation({
    mutationFn: (data: StationRequest) => api.post<Station>('/hyd/stations', data),
    onSuccess: () => {
      message.success('Đã thêm điểm đo');
      dongTaoMoi();
      void lamMoi();
    },
    // ⭐ 01/09 (T28.31): mutation này TRƯỚC ĐÂY không có `onError` nào. HYD-1002/2005/2006 và cả
    //    403 đều im lặng tuyệt đối — người dùng bấm Lưu, không có gì xảy ra và không có gì báo.
    //    Mã lỗi đã khớp đủ bốn nơi từ T28.10, nhưng không màn hình nào hiện chúng.
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thêm được điểm đo');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: string; payload: StationRequest }) =>
      api.put<Station>(`/hyd/stations/${data.id}`, data.payload),
    onSuccess: () => {
      message.success('Đã cập nhật điểm đo');
      setDangSua(null);
      void lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không cập nhật được điểm đo',
      );
    },
  });

  // `?? []` tạo mảng mới mỗi lần render — bọc useMemo để useMemo bên dưới không chạy lại vô ích.
  const tatCa = useMemo(() => query.data ?? [], [query.data]);
  const soChuaGan = tatCa.filter((s) => s.chuaGanDonVi).length;
  const soThieuLienKet = tatCa.filter((s) => s.thieuLienKetCongTrinh).length;

  const hienThi = useMemo(() => {
    if (boLoc === 'CHUA_GAN_DON_VI') return tatCa.filter((s) => s.chuaGanDonVi);
    if (boLoc === 'THIEU_LIEN_KET') return tatCa.filter((s) => s.thieuLienKetCongTrinh);
    return tatCa;
  }, [tatCa, boLoc]);

  const moSua = (s: Station) => {
    form.resetFields();
    form.setFieldsValue({
      code: s.code,
      name: s.name,
      apiCode: s.apiCode,
      apiSourceId: s.apiSourceId ?? undefined,
      positionRole: s.positionRole,
      orgUnitId: s.orgUnitId,
      riverName: s.riverName,
      chainage: s.chainage,
      latitude: s.latitude,
      longitude: s.longitude,
      interpolated: s.interpolated,
      active: s.active,
      description: s.description ?? undefined,
      measurementTypeIds: s.measurementTypes.map((t) => t.id),
    });
    setDangSua(s);
  };

  const columns: ColumnsType<Station> = [
    { title: 'Mã nội bộ', dataIndex: 'code', width: 170 },
    {
      title: 'Điểm đo',
      dataIndex: 'name',
      width: 260,
      render: (name: string, r) => (
        <Space direction="vertical" size={0}>
          <span>{name}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            mã API <Typography.Text code>{r.apiCode}</Typography.Text>
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Vai trò',
      dataIndex: 'positionRole',
      width: 140,
      render: (v: Station['positionRole']) => <Tag>{VAI_TRO_VI_TRI[v]}</Tag>,
    },
    {
      title: 'Loại chỉ số',
      width: 160,
      render: (_, r) => r.measurementTypes.map((t) => <Tag key={t.id}>{t.name}</Tag>),
    },
    {
      title: 'Đơn vị phụ trách',
      dataIndex: 'orgUnitName',
      width: 220,
      render: (ten: string | null) =>
        ten ?? (
          <Tooltip title="Chưa gán đơn vị thì cảnh báo của điểm đo này không có người nhận">
            <Tag color="orange">Chưa gán</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Công trình',
      width: 140,
      render: (_, r) =>
        r.constructions.length > 0 ? (
          <Tag color="blue">{r.constructions.length} liên kết</Tag>
        ) : r.thieuLienKetCongTrinh ? (
          <Tag color="orange">Chưa liên kết</Tag>
        ) : (
          <Tooltip title="Trạm thuỷ văn tham chiếu — không cần liên kết công trình">
            <Tag>Không cần</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 110,
      render: (active: boolean) => (active ? <Tag color="green">Đang dùng</Tag> : <Tag>Ngừng</Tag>),
    },
    {
      title: '',
      width: 110,
      align: 'right',
      render: (_, r) =>
        coQuanLy ? (
          <Space size={0}>
            {/* ⭐ T28.19 — nút mở nửa GHI của liên kết điểm đo ↔ công trình. Trước 03/09 bảng
                `station_constructions` chỉ có đường ĐỌC: cột "Công trình" hiện số liên kết và bộ
                lọc "Chưa liên kết" đếm được 15, nhưng ⛔ không màn hình nào cho làm gì với con số
                ấy — một danh sách việc phải làm mà không có chỗ làm. */}
            <Tooltip title="Liên kết công trình">
              <Button type="text" icon={<ApartmentOutlined />} onClick={() => setDangLienKet(r)} />
            </Tooltip>
            <Button type="text" icon={<EditOutlined />} onClick={() => moSua(r)} />
          </Space>
        ) : null,
    },
  ];

  const truongChung = (dangSuaMa: boolean) => (
    <>
      <Form.Item name="code" label="Mã nội bộ" rules={[{ required: true }]}>
        <Input placeholder="DO-LMAC-TL" />
      </Form.Item>
      <Form.Item
        name="name"
        label="Tên điểm đo"
        rules={[{ required: true }]}
        extra="Nên kèm vai trò, VD “Cống Liên Mạc — Thượng lưu”: có hai công trình cùng tên “Yên Nghĩa”."
      >
        <Input />
      </Form.Item>
      <Form.Item
        name="apiCode"
        label="Mã ánh xạ API"
        rules={[
          { required: true },
          { pattern: /^[Ff][0-9]{5}$/, message: 'Dạng F + 5 chữ số, VD F01771' },
        ]}
        extra={
          dangSuaMa
            ? '⛔ Không sửa được: đổi mã này là gán số liệu của trạm này sang trạm khác.'
            : 'Do Công ty cấp. Sau khi lưu sẽ không sửa được.'
        }
      >
        <Input placeholder="F01771" disabled={dangSuaMa} />
      </Form.Item>
      <Form.Item name="apiSourceId" label="Nguồn dữ liệu" rules={[{ required: true }]}>
        <Select
          loading={nguonQuery.isLoading}
          options={(nguonQuery.data ?? []).map((n) => ({
            value: n.id,
            label: `${n.code} — ${n.name}`,
          }))}
        />
      </Form.Item>
      <Form.Item
        name="positionRole"
        label="Vai trò vị trí"
        rules={[{ required: true }]}
        // ⚠ Câu này DỰNG TỪ hằng số chứ không gõ lại danh sách: `VAI_TRO_KHONG_CAN_CONG_TRINH` là
        //   nơi luật ấy sống, và nó vốn được export mà chưa nơi nào import — một hằng số chết mang
        //   đúng tri thức nghiệp vụ mà không ai đọc (luật 15). Gõ lại tên vai trò vào đây là dựng
        //   bản sao thứ hai của cùng một luật, rồi một hôm hai bản nói khác nhau (luật 14).
        extra={`Quyết định điểm đo là thượng lưu / hạ lưu / bể hút / mực nước sông. ${VAI_TRO_KHONG_CAN_CONG_TRINH.map(
          (v) => `“${VAI_TRO_VI_TRI[v]}”`,
        ).join(', ')} là vai trò duy nhất hợp lệ khi điểm đo không thuộc công trình nào.`}
      >
        <Select options={VAI_TRO_VI_TRI_OPTIONS} />
      </Form.Item>
      <Form.Item
        name="measurementTypeIds"
        label="Loại chỉ số đo được"
        // ⚠ Backend là `@NotEmpty` từ 01/09 (bỏ trường ra = XOÁ SẠCH liên kết). Thiếu luật này ở
        //   client thì người dùng đi trọn một vòng máy chủ để nhận 422 cho một điều biểu mẫu biết
        //   trước — và một điểm đo không đo chỉ số nào là bản ghi không sinh ra được số liệu nào.
        rules={[{ required: true, message: 'Chọn ít nhất một loại chỉ số' }]}
      >
        <Select
          mode="multiple"
          loading={loaiQuery.isLoading}
          options={(loaiQuery.data ?? []).map((t) => ({
            value: t.id,
            label: `${t.name} (${t.unit})`,
          }))}
        />
      </Form.Item>
      <Form.Item
        name="orgUnitId"
        label="Đơn vị phụ trách"
        extra="Để trống được, nhưng cảnh báo của điểm đo sẽ chưa có người nhận."
      >
        <OrgUnitTreeSelect />
      </Form.Item>
    </>
  );

  /**
   * Bảy ô còn lại của hồ sơ — hiện ở **cả hai** modal kể từ 02/09/2026 (T28.33 · N2).
   *
   * ⚠⚠ Trước đợt này chúng chỉ có ở modal **Sửa**, vì `StationService.create` chỉ nhận 7 trường.
   * Hệ quả người dùng gặp: muốn nhập toạ độ phải **Tạo → đóng → mở lại → Sửa**, và ⛔ không ô nào
   * nói ra điều đó — người khai điền toạ độ ở lượt tạo rồi tin là đã lưu. Nay backend nhận đủ 14
   * trường ở cả hai đường, nên biểu mẫu không còn lý do gì để giấu chúng đi.
   */
  const truongHoSo = (
    <>
      <Form.Item
        name="riverName"
        label="Tuyến sông"
        extra="Chưa có dữ liệu từ Công ty (G8) — để trống nếu chưa được cấp."
      >
        <Input />
      </Form.Item>
      <Form.Item
        name="chainage"
        label="Lý trình"
        rules={[{ pattern: /^K[0-9]+\+[0-9]{1,3}$/, message: 'Dạng K<km>+<m>, VD K12+300' }]}
        extra="Để trống được — chờ G8."
      >
        <Input placeholder="K12+300" />
      </Form.Item>
      <Form.Item
        name="latitude"
        label="Vĩ độ"
        extra="Toạ độ phải nhập đủ cả cặp — thiếu một nửa thì cả cặp bị từ chối."
      >
        <Input placeholder="20.980000" />
      </Form.Item>
      <Form.Item
        name="longitude"
        label="Kinh độ"
        extra="Toạ độ phải nhập đủ cả cặp — thiếu một nửa thì cả cặp bị từ chối."
      >
        <Input placeholder="105.780000" />
      </Form.Item>
      <Form.Item
        name="interpolated"
        label="Giá trị nội suy"
        valuePropName="checked"
        extra="Nguồn đánh dấu một số điểm là nội suy, không đo trực tiếp — báo cáo phải phân biệt được."
      >
        <Switch />
      </Form.Item>
      <Form.Item name="active" label="Đang dùng" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item name="description" label="Ghi chú">
        <Input.TextArea rows={2} maxLength={500} />
      </Form.Item>
    </>
  );

  return (
    <Card
      title="Danh mục điểm đo"
      extra={
        coQuanLy ? (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields();
              setTaoMoiThuCong(true);
            }}
          >
            Thêm điểm đo
          </Button>
        ) : null
      }
    >
      {maApiDatSan && !coQuanLy && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Không mở được biểu mẫu cho mã ${maApiDatSan.toUpperCase()}`}
          description="Khai điểm đo cần quyền quản lý danh mục điểm đo. Không có nó thì trang này chỉ xem được — nói ra vì một nút bấm không phản ứng đọc như giao diện hỏng."
        />
      )}

      {soChuaGan > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`${soChuaGan} điểm đo chưa gán đơn vị phụ trách`}
          description="Cảnh báo vượt ngưỡng của những điểm đo này chưa có người nhận — hệ thống sẽ không gửi cho ai, và cũng không báo lỗi. Gán đơn vị để đóng lại phần còn thiếu."
          action={
            <Button size="small" onClick={() => setBoLoc('CHUA_GAN_DON_VI')}>
              Xem danh sách
            </Button>
          }
        />
      )}

      <Segmented
        style={{ marginBottom: 16 }}
        value={boLoc}
        onChange={(v) => setBoLoc(v as BoLoc)}
        options={[
          { value: 'TAT_CA', label: `Tất cả (${tatCa.length})` },
          { value: 'CHUA_GAN_DON_VI', label: `Chưa gán đơn vị (${soChuaGan})` },
          { value: 'THIEU_LIEN_KET', label: `Chưa liên kết công trình (${soThieuLienKet})` },
        ]}
      />

      <Table
        rowKey="id"
        loading={query.isLoading}
        dataSource={hienThi}
        columns={columns}
        pagination={false}
        // 170+260+140+160+220+140+110+110 = 1310. Trước 01/09 bảng này KHÔNG khai `scroll` và có
        // 1010px cột cố định cộng một cột không khai bề ngang — cùng lỗi với trang Nguồn dữ liệu,
        // chỉ chưa ai báo vì chưa mở ở màn hẹp. Xem chú thích cột "Địa chỉ" ở `ApiSourcesPage`.
        scroll={{ x: 1310 }}
      />

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Tuyến sông, lý trình và toạ độ đang để trống cho toàn bộ điểm đo — Công ty chưa cung cấp
        (mục G8). Lớp bản đồ điểm đo vì vậy còn rỗng; ⛔ không điền toạ độ phỏng đoán, một chấm sai
        trên bản đồ tệ hơn một bản đồ trống.
      </Typography.Paragraph>

      <Modal
        open={taoMoi}
        title="Thêm điểm đo"
        onCancel={dongTaoMoi}
        onOk={async () => {
          const values = await form.validateFields();
          createMutation.mutate(values);
        }}
        confirmLoading={createMutation.isPending}
        width={640}
        destroyOnClose
      >
        {maApiDatSan && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={`Mã ${maApiDatSan.toUpperCase()} lấy từ danh sách Mã lạ từ nguồn`}
            description="Số đo của mã này đã được giữ lại từ trước. ⛔ Chỉ khai khi đã biết chắc nó là trạm nào — hệ thống không tự suy được, và một mã gán nhầm là toàn bộ lịch sử đi vào biểu đồ của trạm khác."
          />
        )}
        {/* ⚠ `initialValues` chứ không `setFieldsValue` trước khi mở: Form nằm trong Modal có
            `destroyOnClose` nên nó gắn lại mỗi lượt mở, và giá trị đặt lúc chưa gắn là giá trị đặt
            vào một chỗ chưa tồn tại. */}
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            active: true,
            interpolated: false,
            apiCode: maApiDatSan?.toUpperCase(),
          }}
        >
          {truongChung(false)}
          {truongHoSo}
        </Form>
      </Modal>

      <Modal
        open={!!dangSua}
        title={`Sửa điểm đo ${dangSua?.code ?? ''}`}
        onCancel={() => setDangSua(null)}
        onOk={async () => {
          const values = await form.validateFields();
          if (dangSua) updateMutation.mutate({ id: dangSua.id, payload: values });
        }}
        confirmLoading={updateMutation.isPending}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {truongChung(true)}
          {truongHoSo}
        </Form>
      </Modal>
      <LienKetCongTrinhModal
        diemDo={dangLienKet}
        onClose={() => setDangLienKet(null)}
        onDone={() => {
          setDangLienKet(null);
          void lamMoi();
        }}
      />
    </Card>
  );
}

export default StationsPage;
