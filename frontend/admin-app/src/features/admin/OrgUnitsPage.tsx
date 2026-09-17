import { ArrowDownOutlined, ArrowUpOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  type FormInstance,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Tag,
  Tree,
  Typography,
} from 'antd';
import { useLayoutEffect, useMemo, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { OrgUnitLeadersPanel } from './OrgUnitLeadersPanel';
import {
  type CreateOrgUnitRequest,
  type OrgUnitNode,
  type OrgUnitType,
  type UpdateOrgUnitRequest,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

/**
 * Sơ đồ tổ chức — **một bảng `org_units` dùng chung** cho Xí nghiệp và phòng ban
 * (CLAUDE.md quy tắc 7), nên màn hình này phục vụ cả MOD-02 lẫn MOD-04 HRM.
 *
 * ⚠ Cây này không chỉ để hiển thị: nó là **biên giới phân quyền tầng 3**. Chuyển một đơn
 * vị sang nhánh khác là đổi phạm vi dữ liệu của mọi tài khoản thuộc nhánh đó, nên thao
 * tác chuyển có xác nhận riêng và backend từ chối chuyển đơn vị vào chính cây con của nó
 * (`ADM-2003`) — chuyện đó cắt rời cả nhánh khỏi cây mà dữ liệu vẫn còn.
 */
export function OrgUnitsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('adm:org-unit:manage');

  const [selected, setSelected] = useState<OrgUnitNode | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [moving, setMoving] = useState(false);
  const [xoaDonVi, setXoaDonVi] = useState<OrgUnitNode | null>(null);

  const tree = useQuery({
    queryKey: ['org-units', 'tree'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/tree'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['org-units'] });

  const remove = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`/org-units/${publicId}`),
    onSuccess: async () => {
      message.success('Đã giải thể đơn vị');
      setSelected(null);
      setXoaDonVi(null);
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xóa được đơn vị');
    },
  });

  const move = useMutation({
    mutationFn: (newParentPublicId: string) =>
      api.patch<OrgUnitNode>(`/org-units/${selected?.publicId}/parent`, { newParentPublicId }),
    onSuccess: async () => {
      message.success('Đã chuyển đơn vị');
      setMoving(false);
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không chuyển được đơn vị');
    },
  });

  /**
   * Kéo–thả một nhánh sang đơn vị cha khác — CN-04.1.
   *
   * <p>⛔⛔ AntD `Tree` gọi lại với `dropToGap`: **true** = thả vào khe giữa hai nút (tức muốn đổi
   * **thứ tự** trong cùng cấp), **false** = thả *lên* một nút (tức muốn đổi **cha**). Hai thao tác
   * ấy gọi hai endpoint khác nhau, và gộp chúng làm một là kéo để sắp thứ tự rồi thấy nhánh nhảy
   * sang một đơn vị khác.
   *
   * <p>⚠ Ở đây chỉ xử lý vế **đổi cha**. Thả vào khe thì ⛔ không làm gì và nói ra vì sao — thứ tự
   * cùng cấp đã có hai nút *Lên/Xuống*, và dựng thêm một đường thứ hai cho cùng một việc là dựng
   * chỗ để hai đường lệch nhau.
   */
  const keoThaMutation = useMutation({
    mutationFn: (v: { nguon: OrgUnitNode; dich: OrgUnitNode }) =>
      api.patch<OrgUnitNode>(`/org-units/${v.nguon.publicId}/parent`, {
        newParentPublicId: v.dich.publicId,
      }),
    onSuccess: async (_data, v) => {
      message.success(`Đã chuyển “${v.nguon.name}” vào “${v.dich.name}”`);
      await invalidate();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không chuyển được đơn vị'),
  });

  /**
   * Xử lý một lượt kéo–thả đã được tra sẵn hai đầu.
   *
   * <p>⚠⚠ Hàm này nhận {@code nguon}/{@code dich} làm <b>tham số</b> chứ ⛔ không tự tra
   * {@code index}, và đó ⛔ không phải sở thích: bản đầu bắt (capture) {@code index} — một giá trị
   * đang được {@code useMemo} — rồi đi vào prop {@code onDrop}, và React Compiler <b>bỏ tối ưu cả
   * component</b> với lỗi *"Existing memoization could not be preserved"* ở cổng
   * `Frontend — lint` (`--max-warnings 0`). ⛔ `tsc` và `vitest` đều ⛔ không thấy — **lần thứ TƯ**
   * cùng hình dạng (T55.8 · T57.12).
   */
  const keoTha = (nguon?: OrgUnitNode, dich?: OrgUnitNode, thaVaoKhe = false) => {
    if (!nguon || !dich || nguon.publicId === dich.publicId) {
      return;
    }
    // ⛔⛔ Thả vào KHE giữa hai nút nghĩa là người dùng muốn đổi **thứ tự**, ⛔ không phải đổi
    //    **cha**. Hai thao tác ấy gọi hai endpoint khác nhau; gộp chúng làm một là kéo để sắp thứ
    //    tự rồi thấy nhánh nhảy sang một đơn vị khác.
    // ⚠ Ở đây chỉ xử lý vế đổi cha: thứ tự cùng cấp đã có hai nút *Lên/Xuống*, và dựng đường thứ
    //   hai cho cùng một việc là dựng chỗ để hai đường lệch nhau.
    if (thaVaoKhe) {
      message.info('Thả vào giữa hai đơn vị để đổi thứ tự chưa hỗ trợ — hãy dùng nút Lên/Xuống.');
      return;
    }
    // ⛔ Thả một nút vào chính cây con của nó là một vòng lặp trong cây tổ chức. Backend từ chối,
    //   nhưng nói ra ở đây thì người dùng hiểu NGAY vì sao.
    if (dich.path.startsWith(nguon.path)) {
      message.warning('Không thể chuyển một đơn vị vào chính cấp dưới của nó.');
      return;
    }
    // ⛔⛔ ⛔ KHÔNG tự dịch nút ở phía giao diện rồi gọi API sau: một lượt bị từ chối (`ADM-2004`
    //    — còn hồ sơ/công trình thuộc đơn vị) sẽ để lại một cây **đã đổi** trên màn hình, và
    //    người dùng tin rằng nó đã chuyển. Cây vẽ lại từ câu trả lời của máy chủ.
    keoThaMutation.mutate({ nguon, dich });
  };

  /**
   * Đổi thứ tự một đơn vị so với các đơn vị **cùng cấp** — `PATCH /org-units/order`.
   *
   * <p>Endpoint và `OrgUnitService.reorder()` có từ WS-6, cây đọc theo `sort_order`, và **không
   * lời gọi nào** từ giao diện cho tới 31/08/2026 — nửa cặp đọc–ghi, luật 27. Hệ quả nhìn thấy
   * trên cổng công khai: trang *Cơ cấu tổ chức* và bảng *Xí nghiệp trực thuộc* dựng theo đúng thứ
   * tự ấy, nên thứ tự các Xí nghiệp trên cổng cố định theo **thứ tự tạo bản ghi** — không ai sắp
   * lại được.
   *
   * <p>⚠ Gửi **toàn bộ danh sách anh em đã sắp lại**, không gửi "chuyển lên một bậc": backend gán
   * `sortOrder` chạy từ 0 theo đúng thứ tự nhận được. Gửi một phép dịch chuyển tương đối là hai
   * nơi cùng phải biết thứ tự hiện tại, và hai nơi biết một sự thật là hai nơi sẽ lệch.
   */
  const doiThuTu = useMutation({
    mutationFn: (orderedPublicIds: string[]) =>
      api.patch<void>('/org-units/order', { orderedPublicIds }),
    onSuccess: async () => {
      message.success('Đã đổi thứ tự hiển thị');
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không đổi được thứ tự');
    },
  });

  /** Danh sách anh em cùng cha của đơn vị đang chọn, theo đúng thứ tự cây đang hiển thị. */
  const anhEm = useMemo<OrgUnitNode[]>(() => {
    if (!selected) {
      return [];
    }
    const goc = tree.data ?? [];
    if (selected.depth === 0 || goc.some((node) => node.publicId === selected.publicId)) {
      return goc;
    }
    const tim = (nodes: OrgUnitNode[]): OrgUnitNode[] | null => {
      for (const node of nodes) {
        if (node.children.some((con) => con.publicId === selected.publicId)) {
          return node.children;
        }
        const sau = tim(node.children);
        if (sau) {
          return sau;
        }
      }
      return null;
    };
    return tim(goc) ?? [];
  }, [selected, tree.data]);

  const viTri = anhEm.findIndex((node) => node.publicId === selected?.publicId);

  const dichChuyen = (buoc: -1 | 1) => {
    const moi = [...anhEm];
    const [bi] = moi.splice(viTri, 1);
    moi.splice(viTri + buoc, 0, bi);
    doiThuTu.mutate(moi.map((node) => node.publicId));
  };

  const treeData = useMemo(() => (tree.data ?? []).map(toTreeData), [tree.data]);
  const index = useMemo(() => flatten(tree.data ?? []), [tree.data]);

  return (
    <Row gutter={16}>
      <Col xs={24} lg={12}>
        <Card
          title="Sơ đồ tổ chức"
          loading={tree.isLoading}
          extra={
            canManage && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
                Thêm đơn vị
              </Button>
            )
          }
        >
          {/*
            ⭐ Kéo–thả di chuyển nhánh — CN-04.1. Endpoint `PATCH /{id}/parent` có từ WS-6 và giao
               diện chỉ gọi được nó qua một hộp thoại chọn cây; đặc tả thì nói *"kéo thả"*.

            ⛔⛔ `onDrop` KHÔNG tự đổi cây ở phía giao diện. Nó gọi API rồi `invalidate` — cây vẽ
               lại từ **câu trả lời của máy chủ**. Tự dịch nút trước rồi gọi API sau (optimistic)
               nghĩa là một lượt bị từ chối (`ADM-2004`: còn hồ sơ/công trình thuộc đơn vị) vẫn để
               lại một cây **đã đổi** trên màn hình, và người dùng tin rằng nó đã chuyển.

            ⚠ Chỉ bật khi `canManage`: `draggable` là một ĐƯỜNG GHI, và một cây kéo được cho người
              chỉ có quyền xem là một lời mời bấm vào rồi nhận 403.
          */}
          <Tree
            treeData={treeData}
            defaultExpandAll
            draggable={canManage ? { icon: false } : false}
            blockNode
            onDrop={(info) =>
              keoTha(
                index.get(String(info.dragNode.key)),
                index.get(String(info.node.key)),
                info.dropToGap,
              )
            }
            selectedKeys={selected ? [selected.publicId] : []}
            onSelect={(keys) => setSelected(keys[0] ? (index.get(String(keys[0])) ?? null) : null)}
          />
        </Card>
      </Col>

      <Col xs={24} lg={12}>
        <Card title="Chi tiết đơn vị">
          {!selected ? (
            <Empty description="Chọn một đơn vị trên cây" />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Title level={5} style={{ marginBottom: 0 }}>
                {selected.name}
              </Typography.Title>
              <Space wrap>
                <Tag>{selected.code}</Tag>
                <Tag color="blue">{UNIT_TYPE_LABELS[selected.unitType]}</Tag>
                <Tag>Cấp {selected.depth}</Tag>
                {!selected.active && <Tag color="red">Ngừng hoạt động</Tag>}
              </Space>
              <Typography.Text type="secondary">Đường dẫn cây: {selected.path}</Typography.Text>

              {/*
                ⛔ Ba dòng liên hệ hiện "Chưa nhập" chứ không hiện dấu gạch hay bỏ trống hẳn:
                   đúng ba cột này là bảng 6 cột "Xí nghiệp trực thuộc" trên cổng công khai
                   (CR-26), nên người quản trị cần thấy ngay ô nào đang rỗng và vì sao trang
                   công khai trống. Trước 28/08/2026 ba cột ấy ĐỌC ĐƯỢC MÀ KHÔNG GHI ĐƯỢC —
                   không biểu mẫu nào có ô nhập.
              */}
              <Space direction="vertical" size={2} style={{ marginTop: 8 }}>
                <Typography.Text>
                  <Typography.Text type="secondary">Địa chỉ: </Typography.Text>
                  {selected.address ?? <Typography.Text type="warning">Chưa nhập</Typography.Text>}
                </Typography.Text>
                <Typography.Text>
                  <Typography.Text type="secondary">Điện thoại: </Typography.Text>
                  {selected.phone ?? <Typography.Text type="warning">Chưa nhập</Typography.Text>}
                </Typography.Text>
                <Typography.Text>
                  <Typography.Text type="secondary">Email: </Typography.Text>
                  {selected.email ?? <Typography.Text type="warning">Chưa nhập</Typography.Text>}
                </Typography.Text>
              </Space>

              {canManage && (
                <Space wrap style={{ marginTop: 12 }}>
                  <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                    Sửa thông tin
                  </Button>
                  <Button onClick={() => setMoving(true)}>Chuyển sang đơn vị cha khác</Button>
                  {/* ⚠ Thứ tự này đi thẳng ra cổng công khai — trang Cơ cấu tổ chức và bảng Xí
                      nghiệp trực thuộc dựng theo `sort_order`. Vô hiệu ở hai đầu danh sách thay vì
                      ẩn nút: nút biến mất làm người dùng tưởng chức năng chỉ có ở vài đơn vị. */}
                  <Button
                    icon={<ArrowUpOutlined />}
                    disabled={viTri <= 0 || doiThuTu.isPending}
                    onClick={() => dichChuyen(-1)}
                  >
                    Lên
                  </Button>
                  <Button
                    icon={<ArrowDownOutlined />}
                    disabled={viTri < 0 || viTri >= anhEm.length - 1 || doiThuTu.isPending}
                    onClick={() => dichChuyen(1)}
                  >
                    Xuống
                  </Button>
                  {/*
                    ⛔⛔ XÁC NHẬN HAI BƯỚC (SRS UC4.1), ⛔ không phải một `Popconfirm`.

                    Giải thể một đơn vị kéo theo hồ sơ CBNV, công trình, điểm đo và đơn nghỉ phép
                    của nó — WS-56 phải dựng `OrgUnitUsagePort` với **bốn** bên cài để chặn. Một
                    hộp "Bạn có chắc?" đặt ngay dưới con trỏ là thứ người ta bấm Đồng ý theo quán
                    tính; gõ lại **mã đơn vị** thì ⛔ không bấm nhầm được, và nó bắt người dùng
                    nhìn xem mình đang xoá cái gì.
                  */}
                  <Button danger onClick={() => setXoaDonVi(selected)}>
                    Giải thể
                  </Button>
                </Space>
              )}

              {/* Danh bạ lãnh đạo — CR-25 (bảng Lãnh đạo Công ty) · CR-26 (cột Giám đốc XN). */}
              <OrgUnitLeadersPanel orgUnitPublicId={selected.publicId} />
            </Space>
          )}
        </Card>
      </Col>

      {xoaDonVi ? (
        <XacNhanGiaiThe
          donVi={xoaDonVi}
          dangXoa={remove.isPending}
          onDong={() => setXoaDonVi(null)}
          onXacNhan={() => remove.mutate(xoaDonVi.publicId)}
        />
      ) : null}

      <CreateOrgUnitModal open={creating} onClose={() => setCreating(false)} onDone={invalidate} />

      <EditOrgUnitModal
        open={editing}
        unit={selected}
        onClose={() => setEditing(false)}
        onDone={invalidate}
      />

      <Modal
        open={moving}
        title={`Chuyển "${selected?.name ?? ''}" sang đơn vị cha khác`}
        okText="Chuyển"
        cancelText="Hủy"
        confirmLoading={move.isPending}
        onCancel={() => setMoving(false)}
        footer={null}
      >
        <Typography.Paragraph type="secondary">
          Chuyển đơn vị sẽ đổi phạm vi dữ liệu của toàn bộ tài khoản thuộc nhánh này.
        </Typography.Paragraph>
        <OrgUnitTreeSelect
          onChange={(value) => value && move.mutate(value)}
          placeholder="Chọn đơn vị cha mới"
        />
      </Modal>
    </Row>
  );
}

const UNIT_TYPE_LABELS: Record<OrgUnitType, string> = {
  CONG_TY: 'Công ty',
  PHONG_BAN: 'Phòng ban',
  XI_NGHIEP: 'Xí nghiệp',
  TO_DOI: 'Tổ / Đội',
};

function CreateOrgUnitModal({
  open,
  onClose,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateOrgUnitRequest>();

  const create = useMutation({
    mutationFn: (values: CreateOrgUnitRequest) => api.post<OrgUnitNode>('/org-units', values),
    onSuccess: async () => {
      message.success('Đã thêm đơn vị');
      form.resetFields();
      onClose();
      await onDone();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thêm được đơn vị');
    },
  });

  return (
    <Modal
      open={open}
      title="Thêm đơn vị"
      okText="Tạo"
      cancelText="Hủy"
      confirmLoading={create.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      destroyOnClose
    >
      <Form<CreateOrgUnitRequest>
        form={form}
        layout="vertical"
        preserve={false}
        onFinish={(values) => create.mutate(values)}
      >
        <Form.Item name="code" label="Mã đơn vị" rules={[{ required: true, message: 'Bắt buộc' }]}>
          <Input />
        </Form.Item>
        <Form.Item name="name" label="Tên đơn vị" rules={[{ required: true, message: 'Bắt buộc' }]}>
          <Input />
        </Form.Item>
        <Form.Item name="shortName" label="Tên viết tắt">
          <Input />
        </Form.Item>
        <Form.Item
          name="unitType"
          label="Loại đơn vị"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Select
            options={Object.entries(UNIT_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
          />
        </Form.Item>
        <Form.Item
          name="parentPublicId"
          label="Thuộc đơn vị"
          extra="Bỏ trống để tạo nút gốc — toàn hệ thống chỉ có một nút gốc"
        >
          <OrgUnitTreeSelect />
        </Form.Item>
        <OTruongLienHe />
      </Form>
    </Modal>
  );
}

/**
 * Ba ô liên hệ dùng chung cho biểu mẫu Tạo và biểu mẫu Sửa.
 *
 * ⚠ Một component, không chép hai bản: hai bản sẽ trôi ra khỏi nhau đúng lúc ai đó thêm ô thứ tư
 * vào một trong hai (quy tắc 14). Ba ô này đổ thẳng vào bảng 6 cột "Xí nghiệp trực thuộc" của cổng
 * công khai — CR-26.
 */
function OTruongLienHe() {
  return (
    <>
      <Form.Item
        name="address"
        label="Địa chỉ"
        extra="Hiện ở cột 2 bảng Xí nghiệp trực thuộc trên cổng công khai"
      >
        <Input />
      </Form.Item>
      <Form.Item name="phone" label="Điện thoại">
        <Input />
      </Form.Item>
      <Form.Item
        name="email"
        label="Email"
        rules={[{ type: 'email', message: 'Email không hợp lệ' }]}
      >
        <Input />
      </Form.Item>
    </>
  );
}

/**
 * Sửa thông tin một đơn vị — {@code PUT /org-units/:publicId}.
 *
 * ⚠⚠ Endpoint ấy có từ WS-6 nhưng **không màn hình nào gọi** cho tới 28/08/2026: màn hình này chỉ
 * có Tạo, Chuyển cha và Xoá. Hệ quả là tên, tên viết tắt và loại đơn vị **chưa bao giờ sửa được**
 * sau khi tạo — gõ sai một chữ trong tên Xí nghiệp thì cách chữa duy nhất là xoá rồi tạo lại, mà
 * xoá thì vướng ràng buộc "còn người dùng thuộc đơn vị".
 *
 * ⛔ `initialValues` nạp ĐỦ sáu trường. Nạp thiếu một trường thì mỗi lượt Lưu ghi đè giá trị đang
 * có bằng rỗng — biểu mẫu trông đúng, dữ liệu mất, không thông báo nào (xem `OrgUnitNode`).
 */
function EditOrgUnitModal({
  open,
  unit,
  onClose,
  onDone,
}: {
  open: boolean;
  unit: OrgUnitNode | null;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<UpdateOrgUnitRequest>();

  const update = useMutation({
    mutationFn: (values: UpdateOrgUnitRequest) =>
      api.put<OrgUnitNode>(`/org-units/${unit?.publicId}`, values),
    onSuccess: async () => {
      message.success('Đã cập nhật đơn vị');
      onClose();
      await onDone();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không cập nhật được');
    },
  });

  if (!unit) return null;

  return (
    <Modal
      open={open}
      title={`Sửa "${unit.name}"`}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={update.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      destroyOnHidden
    >
      {/* ⛔⛔ `key` ⛔ KHÔNG thừa — xem javadoc của `BieuMauSuaDonVi` ngay dưới. Nó tạo ra lượt
          unmount mà phép đặt giá trị tường minh cần, khi người dùng đổi đơn vị mà ⛔ đóng trang. */}
      <BieuMauSuaDonVi
        key={unit.publicId}
        khoa={unit.publicId}
        form={form}
        donVi={unit}
        onFinish={(values) => update.mutate(values)}
      />
    </Modal>
  );
}

/**
 * Thân biểu mẫu sửa đơn vị — tách ra để {@code key} ép dựng lại khi đổi bản ghi.
 *
 * <h2>⛔⛔⛔ Vì sao ⛔ để {@code initialValues} một mình — T51.12 lần thứ TƯ</h2>
 *
 * <p>Nơi gọi render {@code <EditOrgUnitModal open={editing} unit={selected} …/>} <b>vô điều
 * kiện</b>, và {@code Form.useForm()} nằm ở component NGOÀI {@code Modal} (AntD đòi vậy để
 * {@code Modal.onOk} gọi được {@code submit}). Nên kho giá trị sống lâu hơn hộp thoại, còn
 * {@code rc-field-form} chỉ áp {@code initialValues} khi {@code init}.
 *
 * <p>⚠ {@code destroyOnHidden} + {@code preserve={false}} là <b>hai</b> biện pháp phòng và chúng
 * <b>⛔ cộng lại thành an toàn</b> (T53.7). Đo được trước lượt vá này: mở <i>XN Hà Đông</i> → Huỷ →
 * chọn <i>XN Thanh Trì</i> → Sửa ⇒ ô <i>Địa chỉ</i> vẫn hiện <i>"Số 1 Quang Trung, Hà Đông"</i>, và
 * một lượt Lưu ghi địa chỉ/điện thoại/email của đơn vị A đè lên B kèm thông báo
 * <i>"Đã cập nhật đơn vị"</i>. Ba cột ấy đổ thẳng ra bảng <i>Xí nghiệp trực thuộc</i> của cổng công
 * khai (CR-26).
 *
 * <p>⇒ {@code useLayoutEffect} đặt giá trị <b>tường minh</b>: {@code resetFields()} gỡ cờ *đã chạm*
 * và lỗi hợp lệ hoá của lượt trước, rồi {@code setFieldsValue} ghi đè bằng đơn vị hiện tại.
 * {@code useLayoutEffect} chứ ⛔ {@code useEffect} — nó chạy TRƯỚC lượt vẽ nên người dùng ⛔ bao giờ
 * thấy một khung hình mang dữ liệu của đơn vị khác.
 *
 * <p>⛔⛔ Và ⛔ thêm {@code clearOnDestroy}: lượt dọn của cây con CŨ chạy <b>sau</b>
 * {@code useLayoutEffect} của cây con MỚI ⇒ ô ra RỖNG (T53.7 đã trả giá đủ ba lượt). MỘT cơ chế,
 * tường minh, có bài kiểm — ⛔ ba cơ chế chồng nhau.
 */
function BieuMauSuaDonVi({
  khoa,
  form,
  donVi,
  onFinish,
}: {
  khoa: string;
  form: FormInstance<UpdateOrgUnitRequest>;
  donVi: OrgUnitNode;
  onFinish: (values: UpdateOrgUnitRequest) => void;
}) {
  useLayoutEffect(() => {
    form.resetFields();
    form.setFieldsValue({
      name: donVi.name,
      shortName: donVi.shortName ?? undefined,
      unitType: donVi.unitType,
      address: donVi.address ?? undefined,
      phone: donVi.phone ?? undefined,
      email: donVi.email ?? undefined,
    });
    // `khoa` là danh tính bản ghi; `donVi` là object dựng lại mỗi lượt render nên ⛔ đưa vào deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [khoa, form]);

  return (
    <Form<UpdateOrgUnitRequest> form={form} layout="vertical" preserve={false} onFinish={onFinish}>
      {/* Mã đơn vị KHÔNG sửa được: nó là khoá nghiệp vụ, đã in trên văn bản và dùng làm mã tra
          cứu ở tệp nhập công trình. Đổi mã là đổi danh tính, không phải sửa một lỗi gõ. */}
      <Form.Item label="Mã đơn vị">
        <Input value={donVi.code} disabled />
      </Form.Item>
      <Form.Item name="name" label="Tên đơn vị" rules={[{ required: true, message: 'Bắt buộc' }]}>
        <Input />
      </Form.Item>
      <Form.Item name="shortName" label="Tên viết tắt">
        <Input />
      </Form.Item>
      <Form.Item
        name="unitType"
        label="Loại đơn vị"
        rules={[{ required: true, message: 'Bắt buộc' }]}
      >
        <Select
          options={Object.entries(UNIT_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
        />
      </Form.Item>
      <OTruongLienHe />
    </Form>
  );
}

/**
 * Xác nhận giải thể đơn vị — **hai bước** (SRS UC4.1).
 *
 * <h2>⛔⛔ Vì sao ⛔ không phải một `Popconfirm`</h2>
 *
 * <p>Giải thể một đơn vị chạm tới hồ sơ CBNV, công trình, nhật ký bảo trì, điểm đo, phiếu liên hệ
 * và đơn nghỉ phép của nó — WS-56 phải dựng `OrgUnitUsagePort` với **bốn** bên cài để chặn đúng
 * chuyện đó. Một hộp *"Bạn có chắc?"* hiện ngay dưới con trỏ là thứ người ta bấm Đồng ý theo quán
 * tính.
 *
 * <p>⇒ Bước 2 là **gõ lại mã đơn vị**. Nó ⛔ không thể bấm nhầm, và nó bắt người dùng đọc xem mình
 * đang xoá cái gì. ⚠ So sánh sau khi `trim()` nhưng **phân biệt hoa thường**: mã đơn vị là khoá
 * nối, và nới phép so ở đây là dạy người dùng rằng mã ⛔ không phân biệt hoa thường.
 *
 * <p>⚠⚠ Hộp thoại chỉ **TỒN TẠI** khi đang mở (`donVi ? … : null` ở nơi gọi) — cùng cơ chế đã dùng
 * cho `NopDonModal`: `Form`/`useState` ở component ngoài ⛔ không unmount theo `destroyOnHidden`,
 * và ba biện pháp phòng chồng nhau đo được là ⛔ **không** cộng lại thành an toàn (T53.7).
 */
function XacNhanGiaiThe({
  donVi,
  dangXoa,
  onDong,
  onXacNhan,
}: {
  donVi: OrgUnitNode;
  dangXoa: boolean;
  onDong: () => void;
  onXacNhan: () => void;
}) {
  const [go, setGo] = useState('');
  const khop = go.trim() === donVi.code;

  return (
    <Modal
      open
      title={`Giải thể đơn vị “${donVi.name}”?`}
      okText="Giải thể"
      okButtonProps={{ danger: true, disabled: !khop }}
      cancelText="Hủy"
      confirmLoading={dangXoa}
      onOk={onXacNhan}
      onCancel={onDong}
      destroyOnHidden
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="warning"
          showIcon
          message="Thao tác này bị từ chối nếu đơn vị còn dữ liệu"
          description={
            <>
              Hệ thống sẽ kiểm hồ sơ cán bộ, công trình, nhật ký bảo trì, điểm đo, phiếu liên hệ và
              đơn nghỉ phép đang thuộc đơn vị này. Còn bất kỳ thứ nào thì lượt giải thể <b>không</b>
              được thực hiện, và thông báo sẽ nói rõ phải chuyển cái gì đi trước.
            </>
          }
        />
        <Typography.Text>
          Gõ lại mã đơn vị <Typography.Text code>{donVi.code}</Typography.Text> để xác nhận:
        </Typography.Text>
        <Input
          value={go}
          onChange={(e) => setGo(e.target.value)}
          placeholder={donVi.code}
          autoFocus
          status={go.length > 0 && !khop ? 'error' : undefined}
        />
      </Space>
    </Modal>
  );
}

interface AntTreeNode {
  key: string;
  title: string;
  children?: AntTreeNode[];
}

function toTreeData(node: OrgUnitNode): AntTreeNode {
  return {
    key: node.publicId,
    title: `${node.name}${node.shortName ? ` (${node.shortName})` : ''}`,
    children: node.children.length > 0 ? node.children.map(toTreeData) : undefined,
  };
}

/** Bảng tra `publicId → nút`, để chọn trên cây là có ngay dữ liệu chi tiết, không phải gọi lại API. */
function flatten(nodes: readonly OrgUnitNode[], into = new Map<string, OrgUnitNode>()) {
  for (const node of nodes) {
    into.set(node.publicId, node);
    flatten(node.children, into);
  }
  return into;
}
