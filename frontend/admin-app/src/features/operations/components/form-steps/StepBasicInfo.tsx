import { Col, Form, Input, Row, Select, type FormInstance } from 'antd';
import { ClusterSelect } from '@/components/business/ClusterSelect';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { CONSTRUCTION_TYPE, MANAGEMENT_LEVEL } from '@/components/business/statusVocabulary';

export function StepBasicInfo({ form: _form }: { form: FormInstance }) {
  return (
    <>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="code"
            label="Mã công trình"
            rules={[{ required: true, message: 'Bắt buộc' }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={16}>
          <Form.Item
            name="name"
            label="Tên công trình"
            rules={[{ required: true, message: 'Bắt buộc' }]}
          >
            <Input />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="constructionType" label="Loại công trình" rules={[{ required: true }]}>
            <Select
              options={Object.entries(CONSTRUCTION_TYPE).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="purpose" label="Mục đích sử dụng">
            <Select
              allowClear
              options={[
                { value: 'TUOI', label: 'Tưới' },
                { value: 'TIEU', label: 'Tiêu' },
                { value: 'TUOI_TIEU_KET_HOP', label: 'Tưới tiêu kết hợp' },
                { value: 'KHAC', label: 'Khác' },
              ]}
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="managementLevel" label="Cấp quản lý">
            <Select
              allowClear
              options={Object.entries(MANAGEMENT_LEVEL).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="orgUnitId"
            label="Đơn vị quản lý"
            rules={[{ required: true, message: 'Bắt buộc' }]}
          >
            <OrgUnitTreeSelect />
          </Form.Item>
        </Col>
        <Col span={12}>
          {/* ⛔ Trước 31/08 ô này là `<Input placeholder="Nhập ID cụm (tạm thời)">` — trong khi
              backend nhận `clusterId` kiểu **UUID**, tức là bắt người vận hành gõ tay 36 ký tự.
              Bốn endpoint quản lý cụm có đủ từ WS-17 mà không lời gọi nào từ giao diện. */}
          <Form.Item name="clusterId" label="Cụm công trình">
            <ClusterSelect />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item name="description" label="Mô tả chung">
        <Input.TextArea rows={4} />
      </Form.Item>
    </>
  );
}
