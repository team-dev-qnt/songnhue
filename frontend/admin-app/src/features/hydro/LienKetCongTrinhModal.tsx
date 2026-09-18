import { DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Form, Modal, Popconfirm, Select, Switch, Table, Tag } from 'antd';
import type { FormInstance } from 'antd';
import { useLayoutEffect } from 'react';

import { type ConstructionRow, type Station, type StationLinkRequest } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  VAI_TRO_KHONG_CAN_CONG_TRINH,
  VAI_TRO_VI_TRI,
  VAI_TRO_VI_TRI_OPTIONS,
} from './hydroVocabulary';

/**
 * Khai liên kết **điểm đo ↔ công trình** — T28.19.
 *
 * ⭐⭐ Đây là chỗ **hai luồng dữ liệu của hệ thống gặp nhau**: tình hình vận hành *công trình* do
 * người trực nhập (mục A) và mực nước do API lấy về (mục B, chỉ mang một mã `F#####`). Chúng đứng
 * cạnh nhau trên màn hình **vì cùng trỏ về một công trình**, ⛔ không phải vì được trộn vào một bảng.
 *
 * Bảng `station_constructions` đã có đủ lược đồ, entity, repository, 4 chỉ mục và một mã lỗi riêng
 * từ 31/08 — mà ⛔ **không một dòng mã nào tạo được một hàng**. Màn hình này là nửa ghi còn thiếu.
 *
 * ⛔ Chọn công trình bằng ô **tìm theo tên/mã**, ⛔ không bắt gõ UUID — T27.24 vừa gỡ đúng lỗi ấy ở
 * một màn hình khác.
 */
export function LienKetCongTrinhModal({
  diemDo,
  onClose,
  onDone,
}: {
  diemDo: Station | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [form] = Form.useForm<StationLinkRequest>();
  const { message } = App.useApp();

  const congTrinhQuery = useQuery({
    queryKey: ['ops', 'constructions', 'chon-lien-ket'],
    // ⚠ `sort` gửi TƯỜNG MINH một cột nằm trong danh sách cho phép của backend. Bỏ trống thì
    //   `PageUtils.parseSort` nhận mặc định của trang gọi — và đúng chỗ đó đã làm trang Danh mục
    //   công trình trả 422 ngay lượt tải đầu vì mặc định là `updatedAt`, một cột không được phép.
    queryFn: () =>
      api.getPage<ConstructionRow>('/ops/constructions', { size: 100, sort: 'code,asc' }),
    enabled: !!diemDo,
  });

  const them = useMutation({
    mutationFn: (body: StationLinkRequest) =>
      api.post(`/hyd/stations/${diemDo?.id}/lien-ket`, body),
    onSuccess: () => {
      message.success('Đã khai liên kết');
      form.resetFields();
      onDone();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không khai được liên kết');
    },
  });

  const bo = useMutation({
    mutationFn: (lienKetId: string) => api.delete(`/hyd/stations/lien-ket/${lienKetId}`),
    onSuccess: () => {
      message.success('Đã bỏ liên kết');
      onDone();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không bỏ được liên kết'),
  });

  const khongCanCongTrinh = !!diemDo && VAI_TRO_KHONG_CAN_CONG_TRINH.includes(diemDo.positionRole);

  return (
    <Modal
      open={!!diemDo}
      title={`Liên kết công trình — ${diemDo?.code ?? ''}`}
      width={760}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      footer={null}
      destroyOnHidden
    >
      {khongCanCongTrinh && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          title={`Điểm đo vai trò “${VAI_TRO_VI_TRI[diemDo!.positionRole]}” không bắt buộc liên kết công trình`}
          description="Đây là trạm thuỷ văn tham chiếu — “chưa liên kết” là dữ liệu ĐỦ, không phải dữ liệu thiếu. Vẫn khai được nếu Công ty muốn gắn nó vào một công trình cụ thể."
        />
      )}

      <Table
        rowKey="id"
        size="small"
        style={{ marginBottom: 24 }}
        dataSource={diemDo?.constructions ?? []}
        pagination={false}
        locale={{ emptyText: 'Chưa liên kết công trình nào' }}
        scroll={{ x: 640 }}
        columns={[
          {
            title: 'Công trình',
            width: 300,
            render: (_, r) =>
              // ⛔ Không giấu dòng đi khi công trình đã bị xoá: một liên kết trỏ vào chỗ trống là
              //   thứ người vận hành cần THẤY để dọn, không phải thứ nên biến mất lặng lẽ.
              r.constructionCode ? (
                `${r.constructionCode} — ${r.constructionName}`
              ) : (
                <Tag color="red">Công trình đã bị xoá</Tag>
              ),
          },
          {
            title: 'Vai trò',
            width: 150,
            render: (_, r) => <Tag>{VAI_TRO_VI_TRI[r.role]}</Tag>,
          },
          {
            title: 'Liên kết chính',
            width: 130,
            render: (_, r) => (r.primary ? <Tag color="blue">Chính</Tag> : null),
          },
          {
            title: '',
            width: 60,
            align: 'right',
            render: (_, r) => (
              <Popconfirm title="Bỏ liên kết này?" onConfirm={() => bo.mutate(r.id)}>
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label={`Bỏ liên kết với ${r.constructionCode ?? 'công trình đã bị xoá'}`}
                  loading={bo.isPending}
                />
              </Popconfirm>
            ),
          },
        ]}
      />

      <BieuMauLienKet
        // ⛔ `key` ép dựng lại thân biểu mẫu ngay khi đổi điểm đo — xem javadoc của nó.
        key={diemDo?.id ?? 'chua-chon'}
        khoa={diemDo?.id ?? 'chua-chon'}
        form={form}
        diemDo={diemDo}
        dangGui={them.isPending}
        onFinish={(v) => them.mutate(v)}
        congTrinh={(congTrinhQuery.data?.items ?? []).map((c) => ({
          value: c.publicId,
          label: `${c.code} — ${c.name}`,
        }))}
        dangTaiCongTrinh={congTrinhQuery.isLoading}
      />
    </Modal>
  );
}

/**
 * Thân biểu mẫu khai liên kết — tách ra để {@code key} ép dựng lại khi đổi điểm đo.
 *
 * <h2>⛔⛔⛔ Vì sao ⛔ để {@code initialValues} một mình — T51.12 lần thứ NĂM (T63.17)</h2>
 *
 * <p>{@code StationsPage} render {@code <LienKetCongTrinhModal diemDo={dangLienKet} …/>}
 * <b>vô điều kiện</b>, và {@code Form.useForm()} nằm ở component NGOÀI {@code Modal}. Nên kho giá
 * trị sống lâu hơn hộp thoại, trong khi {@code rc-field-form@2.7.1} áp
 * {@code setInitialValues(iv, init)} bằng {@code merge(initialValues, this.store)} — <b>kho THẮNG
 * `initialValues`</b>. Mở điểm đo A rồi B thì ô <i>Vai trò</i> vẫn bày vai trò của A.
 *
 * <p>⚠ {@code onCancel} gọi {@code resetFields()} và điều đó <b>⛔ cứu được</b>:
 * {@code resetFields()} ⛔ xoá trắng, nó đặt {@code store = merge(this.initialValues)} — tức
 * {@code initialValues} của lượt TRƯỚC, nghĩa là đúng vai trò của A một lần nữa.
 *
 * <p>⛔⛔ Hậu quả nghiệp vụ ⛔ dừng ở một ô hiển thị sai: chính giá trị này quyết định điểm đo được
 * xếp cột <b>TL hay HL</b> ở biểu tổng hợp theo tuyến sông (đúng câu ô {@code extra} đang nói), và
 * ràng buộc <i>"liên kết CHÍNH phải trùng vai trò của hồ sơ điểm đo"</i> khiến một vai trò sai vừa
 * đủ hợp lệ để lưu xuống. Trực ban sau đó đọc mực nước thượng lưu ở cột hạ lưu.
 *
 * <p>⇒ {@code useLayoutEffect} đặt giá trị <b>tường minh</b> (chứ ⛔ {@code useEffect} — nó chạy
 * TRƯỚC lượt vẽ nên người dùng ⛔ bao giờ thấy một khung hình mang vai trò của điểm đo khác).
 *
 * <p>⛔⛔ Và ⛔ thêm {@code clearOnDestroy}: lượt dọn của cây con CŨ chạy <b>sau</b>
 * {@code useLayoutEffect} của cây con MỚI ⇒ ô ra RỖNG (T53.7 đã trả giá đủ ba lượt). MỘT cơ chế,
 * tường minh, có bài kiểm — ⛔ ba cơ chế chồng nhau.
 *
 * <h2>⚠ KHAI RA: hai lượt phá ⛔ cho cùng một kết quả</h2>
 *
 * <p>Đo 17/09/2026 trên {@code lienKetCongTrinhVongKhuHoi.test.tsx}:
 * <ul>
 *   <li>gỡ {@code useLayoutEffect} (giữ {@code key}) ⇒ <b>3/4 bài ĐỎ</b>, gồm cả vế chống tập
 *       rỗng — đây là cơ chế <b>chịu lực</b>;
 *   <li>gỡ {@code key} (giữ {@code useLayoutEffect}) ⇒ <b>4/4 vẫn XANH</b>.
 * </ul>
 *
 * <p>Vì {@code khoa} nằm trong deps nên effect chạy lại ngay khi đổi điểm đo, ⛔ cần một lượt
 * remount. ⇒ {@code key} ở đây là <b>đai thứ hai</b>, ⛔ phải thứ giữ bảo đảm; nó còn dọn trạng
 * thái ⛔ điều khiển của {@code Select} (chuỗi đang gõ trong ô tìm công trình) — thứ bài kiểm
 * ⛔ chạm tới. Giữ lại theo tiền lệ {@code OrgUnitLeadersPanel}, nhưng ⛔ được đọc cái xanh của
 * bài kiểm thành <i>"`key` đang che chở"</i>: nó ⛔ hề.
 */
function BieuMauLienKet({
  khoa,
  form,
  diemDo,
  congTrinh,
  dangTaiCongTrinh,
  dangGui,
  onFinish,
}: {
  khoa: string;
  form: FormInstance<StationLinkRequest>;
  diemDo: Station | null;
  congTrinh: { value: string; label: string }[];
  dangTaiCongTrinh: boolean;
  dangGui: boolean;
  onFinish: (v: StationLinkRequest) => void;
}) {
  useLayoutEffect(() => {
    form.resetFields();
    // ⚠ Đặt ĐỦ ba trường của `StationLinkRequest`. Thiếu một trường là để nó mang giá trị của
    //   điểm đo mở TRƯỚC — đúng khuyết tật bài này sinh ra để bắt, chỉ hẹp hơn một ô.
    form.setFieldsValue({
      constructionId: undefined as unknown as string,
      role: diemDo?.positionRole,
      primary: false,
    });
    // `khoa` là danh tính điểm đo; `diemDo` là object dựng lại mỗi lượt render nên ⛔ đưa vào deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [khoa, form]);

  return (
    <Form form={form} layout="vertical" preserve={false} onFinish={onFinish}>
      <Form.Item
        name="constructionId"
        label="Công trình"
        rules={[{ required: true, message: 'Chọn công trình' }]}
        extra="Gõ để tìm theo mã hoặc tên. Danh mục công trình đang chờ dữ liệu của Công ty (G8) — ô này rỗng là vì vậy, không phải vì lỗi."
      >
        <Select
          showSearch={{ optionFilterProp: 'label' }}
          loading={dangTaiCongTrinh}
          placeholder="Chọn công trình"
          options={congTrinh}
        />
      </Form.Item>

      <Form.Item
        name="role"
        label="Vai trò của điểm đo với công trình này"
        rules={[{ required: true, message: 'Chọn vai trò' }]}
        extra={`Một điểm đo có thể là hạ lưu của cống này ĐỒNG THỜI là thượng lưu của cống kế tiếp — vai trò khai theo từng liên kết. Riêng liên kết CHÍNH phải trùng vai trò của hồ sơ điểm đo (“${diemDo ? VAI_TRO_VI_TRI[diemDo.positionRole] : ''}”).`}
      >
        <Select options={VAI_TRO_VI_TRI_OPTIONS} />
      </Form.Item>

      <Form.Item
        name="primary"
        label="Là liên kết chính"
        valuePropName="checked"
        extra="Mỗi điểm đo có tối đa MỘT liên kết chính — bật ở đây thì liên kết chính cũ tự chuyển thành phụ. Đây là liên kết mà biểu tổng hợp theo tuyến sông dùng để xếp cột TL/HL."
      >
        <Switch />
      </Form.Item>

      <Button type="primary" htmlType="submit" loading={dangGui}>
        Khai liên kết
      </Button>
    </Form>
  );
}

export default LienKetCongTrinhModal;
