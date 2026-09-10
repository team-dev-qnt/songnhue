import { Col, Form, Input, InputNumber, Row, Typography } from 'antd';

import { DanToaDo } from '@/components/business/DanToaDo';
import { hopLeLyTrinh } from '../../constructionRules';
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
          <Form.Item
            name="chainage"
            label="Lý trình (K)"
            rules={[
              {
                // Cùng luật với OPS-2011 ở backend; ở đây chỉ để người dùng thấy lỗi ngay tại ô.
                validator: (_, value: string) =>
                  hopLeLyTrinh(value)
                    ? Promise.resolve()
                    : Promise.reject(new Error('Lý trình phải theo dạng K<km>+<m>, ví dụ K0+390')),
              },
            ]}
          >
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item name="basinNote" label="Ghi chú lưu vực">
        <Input.TextArea rows={2} />
      </Form.Item>

      {/* ⭐ WS-46 — BA đường vào cho cùng một cặp số, và đó là chủ ý:
            · dán chuỗi từ Google Maps  ← đường người vận hành thật sự dùng
            · trỏ chuột lên bản đồ      ← khi ⛔ không biết toạ độ, chỉ biết chỗ
            · gõ tay hai ô số            ← khi hiệu đính một chữ số
          ⛔ Đừng bỏ ô "Dán": trước bản này nó ⛔ không tồn tại, và dán
             `21.048201, 105.782500` vào ô `InputNumber` cho ra `21` — mất phần
             thập phân, mất luôn kinh độ, ⛔ không một dòng báo lỗi. */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={24}>
          <DanToaDo
            onChange={(viDo, kinhDo) => form.setFieldsValue({ latitude: viDo, longitude: kinhDo })}
          />
        </Col>
      </Row>

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
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            * Hoặc nhấp thẳng lên bản đồ bên dưới
          </Typography.Text>
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
