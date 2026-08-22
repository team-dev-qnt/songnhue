import { Col, Form, Input, InputNumber, Row } from 'antd';
import { LocationPickerMap } from '../LocationPickerMap';

export function StepLocation() {
  const form = Form.useFormInstance();
  const lat = Form.useWatch('latitude', form);
  const lng = Form.useWatch('longitude', form);

  return (
    <>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="address" label="Địa chỉ/Vị trí">
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="riverName" label="Tuyến sông">
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="chainage" label="Lý trình (K)">
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item name="basinNote" label="Ghi chú lưu vực">
        <Input.TextArea rows={2} />
      </Form.Item>

      <Row gutter={16} align="middle" style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Form.Item name="latitude" label="Vĩ độ (Latitude)">
            <InputNumber style={{ width: '100%' }} precision={6} step={0.000001} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="longitude" label="Kinh độ (Longitude)">
            <InputNumber style={{ width: '100%' }} precision={6} step={0.000001} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <span style={{ color: '#595959', fontSize: 13 }}>
            * Nhấp vào bản đồ để chọn toạ độ tự động
          </span>
        </Col>
      </Row>

      <div style={{ border: '1px solid #d9d9d9', borderRadius: 6, padding: 4 }}>
        <LocationPickerMap
          latitude={lat}
          longitude={lng}
          onChange={(newLat, newLng) => {
            form.setFieldsValue({
              latitude: Number(newLat.toFixed(6)),
              longitude: Number(newLng.toFixed(6)),
            });
          }}
        />
      </div>
    </>
  );
}
