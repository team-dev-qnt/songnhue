import { useQuery } from '@tanstack/react-query';
import { Alert, List, Modal, Skeleton, Tag, Typography } from 'antd';
import { useState } from 'react';

import { MaintenanceFormModal } from '@/features/operations/components/MaintenanceFormModal';
import { type AlertEventRow, type Station } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

import { VAI_TRO_VI_TRI } from './hydroVocabulary';

/**
 * Từ một cảnh báo ngưỡng sang bản ghi khắc phục của MOD-02 — T33.10.
 *
 * <h2>⛔⛔ Vì sao phải có bước CHỌN CÔNG TRÌNH</h2>
 *
 * Cảnh báo gắn với **điểm đo**, còn bản ghi sửa chữa gắn với **công trình**, và một điểm đo có thể
 * thuộc **nhiều** công trình (`station_constructions` — thượng lưu cống này là hạ lưu cống kia).
 * ⇒ ⛔ có phép ánh xạ một-một nào để đoán hộ; đoán là gắn một sự cố vào sai hồ sơ, và hồ sơ ấy là
 * thứ Công ty dùng để quyết toán sửa chữa.
 *
 * <p>⚠ Khi chỉ có **đúng một** liên kết thì bỏ qua bước chọn — bắt bấm thêm một lần để xác nhận
 * thứ ⛔ có lựa chọn nào khác là thêm ma sát ⛔ mua được gì.
 *
 * <h2>Ba trạng thái phải nói ba câu khác nhau (T59.0)</h2>
 *
 * <ul>
 *   <li><b>⛔ liên kết nào</b> — điểm đo chưa gắn công trình. Việc cần làm nằm ở màn hình Điểm đo,
 *       ⛔ phải ở đây, nên nói thẳng ra thay vì hiện một danh sách rỗng (quy tắc 16).
 *   <li><b>Liên kết trỏ vào công trình ĐÃ XOÁ</b> — `constructionCode` về `null`. Bày ra mà ⛔ cho
 *       chọn: giấu đi thì người vận hành ⛔ bao giờ biết có một liên kết cần dọn.
 *   <li><b>Bình thường</b>.
 * </ul>
 *
 * <h2>⚠ ⛔ tự sinh bản ghi</h2>
 *
 * Cảnh báo **⛔ tự sinh** `maintenance_logs` — đó là quyết định của con người. Tự sinh là đổ rác
 * vào sổ gốc của cả MOD-02, và mỗi dòng rác còn kéo theo một lượt tính lại trạng thái công trình.
 * Màn hình này chỉ **điền sẵn**; người trực vẫn bấm Lưu.
 */
export function TaoBanGhiKhacPhucModal({
  canhBao,
  onClose,
  onSaved,
}: {
  canhBao: AlertEventRow;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [daBam, setDaBam] = useState<string | null>(null);

  // ⚠ Tra khi NGƯỜI DÙNG bấm, ⛔ nạp sẵn cho cả bảng: `/hyd/stations` đòi `hyd:station:view`, mà
  //   một người có `hyd:alert:view` chưa chắc có. Nạp sẵn là để một lượt 403 ⛔ liên quan nổ ra
  //   giữa màn hình danh sách; tra ở đây thì nó nằm gọn trong thao tác đã chọn.
  const { data, isLoading, isError } = useQuery({
    queryKey: ['hyd', 'station', canhBao.stationId],
    queryFn: () => api.get<Station>(`/hyd/stations/${canhBao.stationId}`),
  });

  const lienKet = data?.constructions ?? [];
  const chonDuoc = lienKet.filter((l) => l.constructionCode !== null);
  // ⚠⚠ Đi thẳng chỉ khi TOÀN BỘ danh sách là đúng một liên kết dùng được — ⛔ phải "đúng một
  //    liên kết CHỌN ĐƯỢC". Điểm đo có 1 liên kết hỏng + 1 liên kết tốt mà lướt thẳng qua là
  //    **giấu mất** dòng hỏng, đúng thứ javadoc của `StationConstructionView` cấm: *"một liên kết
  //    trỏ vào công trình đã xoá là thứ người vận hành cần thấy để dọn"*.
  const diThang = lienKet.length === 1 && chonDuoc.length === 1;
  const congTrinh = daBam ?? (diThang ? chonDuoc[0].constructionId : null);

  if (congTrinh) {
    return (
      <MaintenanceFormModal
        // ⚠ `key` là cơ chế DUY NHẤT giữ biểu mẫu ⛔ mang dữ liệu của công trình chọn trước đó —
        //   `Form.useForm()` sống bên trong nó và `initialValues` chỉ áp lúc mount (T51.12).
        key={congTrinh}
        constructionPublicId={congTrinh}
        open
        onClose={onClose}
        onSaved={onSaved}
        loaiMacDinh="KHAC_PHUC_SU_CO"
        alertEventId={canhBao.id}
      />
    );
  }

  return (
    <Modal
      title="Tạo bản ghi khắc phục"
      open
      onCancel={onClose}
      footer={null}
      width={620}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        Cảnh báo <b>{canhBao.alertLevelName}</b> tại điểm đo <b>{canhBao.stationName}</b> (
        {canhBao.stationCode}). Bản ghi sửa chữa gắn với <b>công trình</b>, nên hãy chọn công trình
        đã bị ảnh hưởng.
      </Typography.Paragraph>

      {isLoading && <Skeleton active paragraph={{ rows: 3 }} />}

      {isError && (
        <Alert
          type="error"
          showIcon
          title="Không đọc được danh sách công trình của điểm đo"
          description="Có thể tài khoản của bạn chưa có quyền xem danh mục điểm đo. Bản ghi khắc phục vẫn tạo được từ trang chi tiết công trình."
        />
      )}

      {!isLoading && !isError && lienKet.length === 0 && (
        <Alert
          type="warning"
          showIcon
          title="Điểm đo này chưa liên kết công trình nào"
          description="Cảnh báo gắn với điểm đo, còn bản ghi sửa chữa gắn với công trình — chưa có liên kết thì chưa biết ghi vào hồ sơ nào. Khai liên kết ở màn hình Điểm đo trước."
        />
      )}

      {lienKet.length > 0 && (
        <List
          dataSource={lienKet}
          renderItem={(l) => {
            const daXoa = l.constructionCode === null;
            return (
              <List.Item
                actions={
                  daXoa
                    ? [<Tag key="x">Công trình đã xoá</Tag>]
                    : [
                        <Typography.Link key="chon" onClick={() => setDaBam(l.constructionId)}>
                          Chọn
                        </Typography.Link>,
                      ]
                }
              >
                <List.Item.Meta
                  title={
                    daXoa ? (
                      <Typography.Text type="secondary">
                        Liên kết trỏ vào một công trình đã bị xoá — cần dọn ở màn hình Điểm đo
                      </Typography.Text>
                    ) : (
                      `${l.constructionCode} — ${l.constructionName}`
                    )
                  }
                  description={`Vai trò vị trí: ${VAI_TRO_VI_TRI[l.role] ?? l.role}`}
                />
              </List.Item>
            );
          }}
        />
      )}
    </Modal>
  );
}
