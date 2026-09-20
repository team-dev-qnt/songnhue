import { Button, Checkbox, Col, Form, Input, Row, Select, Space } from 'antd';
import { ClusterSelect } from '@/components/business/ClusterSelect';
import {
  CONSTRUCTION_STATUS,
  CONSTRUCTION_TYPE,
  MANAGEMENT_LEVEL,
} from '@/components/business/statusVocabulary';

export interface ConstructionFilterValues {
  q?: string;
  type?: string;
  status?: string;
  level?: string;
  river?: string;
  /**
   * `publicId` của cụm công trình.
   *
   * ⚠ Tên trường trùng KHÍT tham số của `GET /ops/constructions` (`clusterId`) vì `ConstructionsPage`
   * rải thẳng bộ lọc này vào query string — đổi tên ở đây là lặng lẽ bỏ rơi bộ lọc, ⛔ một lỗi nào.
   * Cùng lý lẽ đã viết cho `q`/`type`/`status`/`level` ở `ConstructionsPage`.
   *
   * ⭐ Backend nhận tham số này từ WS-17 mà **⛔ nơi nào gọi** cho tới WS-75 — nửa cặp đọc–ghi
   * (quy tắc 27), và là lý do bảng có cột "Cụm công trình" nhưng ⛔ lọc theo nó được.
   */
  clusterId?: string;
  withoutLocation?: boolean;
}

interface Props {
  initialValues?: ConstructionFilterValues;
  onFilter: (values: ConstructionFilterValues) => void;
  rivers: string[];
}

export function ConstructionFilter({ initialValues, onFilter, rivers }: Props) {
  const [form] = Form.useForm<ConstructionFilterValues>();

  const handleSubmit = (values: ConstructionFilterValues) => {
    onFilter(values);
  };

  const handleReset = () => {
    form.resetFields();
    onFilter({});
  };

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onFinish={handleSubmit}
      style={{ marginBottom: 16 }}
    >
      <Row gutter={16}>
        <Col span={6}>
          <Form.Item name="q" label="Tìm kiếm">
            <Input placeholder="Mã, tên công trình..." allowClear />
          </Form.Item>
        </Col>
        <Col span={4}>
          <Form.Item name="type" label="Loại công trình">
            <Select
              allowClear
              placeholder="Tất cả"
              options={Object.entries(CONSTRUCTION_TYPE).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
            />
          </Form.Item>
        </Col>
        <Col span={4}>
          <Form.Item name="status" label="Trạng thái">
            <Select
              allowClear
              placeholder="Tất cả"
              options={Object.entries(CONSTRUCTION_STATUS).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
            />
          </Form.Item>
        </Col>
        <Col span={4}>
          <Form.Item name="level" label="Cấp quản lý">
            <Select
              allowClear
              placeholder="Tất cả"
              options={Object.entries(MANAGEMENT_LEVEL).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
            />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="river" label="Tuyến sông">
            <Select
              allowClear
              showSearch
              placeholder="Tất cả"
              options={rivers.map((r) => ({ value: r, label: r }))}
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={6}>
          <Form.Item name="clusterId" label="Cụm công trình">
            <ClusterSelect placeholder="Tất cả" />
          </Form.Item>
        </Col>
      </Row>
      <Row justify="space-between" align="middle">
        <Col>
          <Form.Item name="withoutLocation" valuePropName="checked" style={{ margin: 0 }}>
            <Checkbox>Chưa số hoá vị trí</Checkbox>
          </Form.Item>
        </Col>
        <Col>
          <Space>
            <Button onClick={handleReset}>Bỏ lọc</Button>
            <Button type="primary" htmlType="submit">
              Lọc
            </Button>
          </Space>
        </Col>
      </Row>
    </Form>
  );
}
