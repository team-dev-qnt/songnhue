import { Col, Form, Input, InputNumber, Row } from 'antd';
import { type ConstructionType } from '@/shared/api-types';

export function StepTechnical({ type }: { type?: ConstructionType }) {
  if (!type || type === 'KHAC') {
    return <div style={{ color: '#595959', padding: '24px 0' }}>Không có thông số kỹ thuật đặc thù cho loại công trình này.</div>;
  }

  if (type === 'TRAM_BOM') {
    return (
      <div style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['pump', 'totalPowerKw']} label="Tổng công suất (kW)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'pumpCount']} label="Số tổ máy">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'standbyPumpCount']} label="Số máy dự phòng">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['pump', 'flowPerPumpM3s']} label="Lưu lượng/máy (m3/s)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'headM']} label="Cột nước (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'voltageKv']} label="Điện áp (kV)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['pump', 'operatingLevelMinM']} label="Mực nước Hmin (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'operatingLevelMaxM']} label="Mực nước Hmax (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['pump', 'powerSource']} label="Nguồn cấp điện">
              <Input />
            </Form.Item>
          </Col>
        </Row>
      </div>
    );
  }

  if (type === 'CONG') {
    return (
      <div style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['sluice', 'sluiceType']} label="Loại cống">
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'bayCount']} label="Số cửa">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'bayWidthM']} label="Bề rộng cửa (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['sluice', 'sillElevationM']} label="Cao độ ngưỡng (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'crestElevationM']} label="Cao độ đỉnh (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'designFlowM3s']} label="Lưu lượng thiết kế (m3/s)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['sluice', 'upstreamWarningLevelM']} label="MNBD thượng lưu (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'upstreamDangerLevelM']} label="MN nguy hiểm (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['sluice', 'gateOperation']} label="Kiểu vận hành cửa">
              <Input />
            </Form.Item>
          </Col>
        </Row>
      </div>
    );
  }

  // KENH_MUONG hoặc DE_DIEU dùng LinearSpec
  if (type === 'KENH_MUONG' || type === 'DE_DIEU') {
    return (
      <div style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['linear', 'lengthKm']} label="Chiều dài (km)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['linear', 'startChainage']} label="Lý trình đầu">
              <Input />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['linear', 'endChainage']} label="Lý trình cuối">
              <Input />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['linear', 'designFlowM3s']} label="Lưu lượng TK (m3/s)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['linear', 'crestElevationM']} label="Cao độ đỉnh/bờ (m)">
              <InputNumber style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name={['linear', 'technicalGrade']} label="Cấp công trình">
              <Input />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name={['linear', 'crossSection']} label="Mặt cắt ngang">
              <Input />
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item name={['linear', 'specNote']} label="Ghi chú thêm">
              <Input />
            </Form.Item>
          </Col>
        </Row>
      </div>
    );
  }

  return null;
}
