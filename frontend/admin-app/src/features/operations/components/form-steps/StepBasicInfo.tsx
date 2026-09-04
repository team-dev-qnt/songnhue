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
          <Form.Item
            name="purpose"
            label="Mục đích sử dụng"
            extra="Bỏ trống được — hồ sơ chưa xác định nhiệm vụ tưới hay tiêu"
          >
            {/* ⚠⚠ Vá 01/09/2026 — hai trong bốn lựa chọn cũ là giá trị KHÔNG TỒN TẠI.
                Bản cũ chào `TUOI_TIEU_KET_HOP` và `KHAC`; enum Java `ConstructionPurpose` chỉ có
                `TUOI` · `TIEU` · `HON_HOP`, và `ck_constructions_purpose` cũng đúng ba giá trị ấy.
                Chọn một trong hai giá trị ma ⇒ Jackson không giải được ⇒ **400 hỏng CẢ lượt lưu**,
                không riêng ô này. Ngược lại, giá trị hợp lệ `HON_HOP` KHÔNG ô nào tạo ra được —
                nó chỉ vào hệ thống qua bộ nhập Excel (`ConstructionImportService.NHAN_NHIEM_VU`).
                ⛔ Bỏ `KHAC` không mất gì: `purpose` nullable và ô có `allowClear`, nên "chưa xác
                định" vẫn biểu diễn được — còn `KHAC` thì chưa bao giờ lưu nổi. */}
            <Select
              allowClear
              options={[
                { value: 'TUOI', label: 'Tưới' },
                { value: 'TIEU', label: 'Tiêu' },
                { value: 'HON_HOP', label: 'Tưới tiêu kết hợp' },
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
