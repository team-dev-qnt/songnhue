import { PlusOutlined } from '@ant-design/icons';
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
import { type CreateOrgUnitRequest, type OrgUnitNode, type OrgUnitType } from '@/shared/api-types';
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

              {canManage && (
                <Space wrap style={{ marginTop: 12 }}>
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
            </Space>
          )}
        </Card>
      </Col>

      <CreateOrgUnitModal open={creating} onClose={() => setCreating(false)} onDone={invalidate} />

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
