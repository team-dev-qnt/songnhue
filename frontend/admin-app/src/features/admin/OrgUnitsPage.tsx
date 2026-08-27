import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Tag,
  Tree,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';

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

  const tree = useQuery({
    queryKey: ['org-units', 'tree'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/tree'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['org-units'] });

  const remove = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`/org-units/${publicId}`),
    onSuccess: async () => {
      message.success('Đã xóa đơn vị');
      setSelected(null);
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
          <Tree
            treeData={treeData}
            defaultExpandAll
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
                  <Popconfirm
                    title="Xóa đơn vị này?"
                    description="Chỉ xóa được khi không còn đơn vị cấp dưới và không còn người dùng."
                    okText="Xóa"
                    cancelText="Hủy"
                    onConfirm={() => remove.mutate(selected.publicId)}
                  >
                    <Button danger>Xóa</Button>
                  </Popconfirm>
                </Space>
              )}

              {/* Danh bạ lãnh đạo — CR-25 (bảng Lãnh đạo Công ty) · CR-26 (cột Giám đốc XN). */}
              <OrgUnitLeadersPanel orgUnitPublicId={selected.publicId} />
            </Space>
          )}
        </Card>
      </Col>

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
      if (caught instanceof ApiClientError && caught.details.length > 0) {
        form.setFields(caught.fieldErrors<keyof CreateOrgUnitRequest & string>());
        return;
      }
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
      if (caught instanceof ApiClientError && caught.details.length > 0) {
        form.setFields(caught.fieldErrors<keyof UpdateOrgUnitRequest & string>());
        return;
      }
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
      destroyOnClose
    >
      <Form<UpdateOrgUnitRequest>
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={{
          name: unit.name,
          shortName: unit.shortName ?? undefined,
          unitType: unit.unitType,
          address: unit.address ?? undefined,
          phone: unit.phone ?? undefined,
          email: unit.email ?? undefined,
        }}
        onFinish={(values) => update.mutate(values)}
      >
        {/* Mã đơn vị KHÔNG sửa được: nó là khoá nghiệp vụ, đã in trên văn bản và dùng làm mã tra
            cứu ở tệp nhập công trình. Đổi mã là đổi danh tính, không phải sửa một lỗi gõ. */}
        <Form.Item label="Mã đơn vị">
          <Input value={unit.code} disabled />
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
