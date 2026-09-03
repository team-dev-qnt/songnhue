import {
  type AlertConditionType,
  type AlertEventStatus,
  type PositionRole,
  type ReadingQuality,
  type ReadingSource,
  type SyncFailureKind,
  type SyncStatus,
} from '@/shared/api-types';

/**
 * Nhãn tiếng Việt của vai trò vị trí điểm đo — <b>một chỗ duy nhất</b>.
 *
 * Bảng, biểu mẫu, bộ lọc và cột xuất báo cáo đều lấy chữ từ đây. Chép chữ vào từng màn
 * hình thì tới lúc Công ty đổi cách gọi (chuyện đã xảy ra với "tình hình vận hành") sẽ
 * còn sót một chỗ, và chỗ sót đó không có triệu chứng nào ngoài việc đọc thấy lạ.
 */
export const VAI_TRO_VI_TRI: Record<PositionRole, string> = {
  THUONG_LUU: 'Thượng lưu',
  HA_LUU: 'Hạ lưu',
  BE_HUT: 'Bể hút',
  MN_SONG: 'Mực nước sông',
  MUA: 'Đo mưa',
};

export const VAI_TRO_VI_TRI_OPTIONS = (Object.keys(VAI_TRO_VI_TRI) as PositionRole[]).map(
  (value) => ({ value, label: VAI_TRO_VI_TRI[value] }),
);

/**
 * ⛔ Vai trò được phép KHÔNG liên kết công trình nào.
 *
 * 4/19 điểm đo là trạm thuỷ văn tham chiếu (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân).
 * Với chúng, "chưa liên kết" là dữ liệu ĐỦ — hiện cảnh báo thiếu dữ liệu ở đây là dạy
 * người dùng bỏ qua cảnh báo.
 */
export const VAI_TRO_KHONG_CAN_CONG_TRINH: readonly PositionRole[] = ['MN_SONG'];

// =============================================================================
// Chẩn đoán đường ingest — WS-31 / T31.13
// =============================================================================

/**
 * Bốn kết cục một lượt polling, kèm màu và **câu giải thích**.
 *
 * ⛔⛔ `SKIPPED_UP_TO_DATE` **không được vẽ màu đỏ**. Nó là kết cục *bình thường và mong muốn*
 * của **4/5 lượt chạy**: poller gọi 2 phút một lần trên một nguồn cập nhật 10 phút một lần,
 * nên phần lớn lượt gọi gặp đúng dữ liệu của lượt trước và cố ý không mở kết nối. Trộn nó vào
 * nhóm màu đỏ là dạy người vận hành bỏ qua màu đỏ — và ngày có sự cố thật thì màu đỏ ấy không
 * còn nghĩa gì (§10.42).
 */
export const KET_CUC_DONG_BO: Record<
  SyncStatus,
  { label: string; color: string; giaiThich: string }
> = {
  SUCCESS: {
    label: 'Thành công',
    color: 'green',
    giaiThich:
      'Gọi được nguồn và bóc đủ số đo. ⚠ Ghi mới 0 dòng vẫn là thành công — dữ liệu trùng.',
  },
  PARTIAL: {
    label: 'Thiếu dữ liệu',
    color: 'gold',
    giaiThich:
      'Nguồn trả lời nhưng thiếu quá nửa số điểm đo đang hoạt động. Xem lượt kế tiếp có bù không.',
  },
  FAILED: {
    label: 'Hỏng',
    color: 'red',
    giaiThich: 'Không lấy được số đo nào. Cột “Lý do” nói việc phải làm.',
  },
  SKIPPED_UP_TO_DATE: {
    label: 'Bỏ qua — đã đủ',
    color: 'default',
    giaiThich:
      'Cố ý KHÔNG gọi vì toàn bộ điểm đo đã có bản ghi của khung hiện tại. Đây là kết cục của 4/5 lượt chạy và là điều đúng.',
  },
};

/**
 * Năm lý do hỏng — và **năm việc phải làm khác nhau**.
 *
 * ⭐ Đây là toàn bộ lý do cột này tồn tại (§10.68-B): bản cũ của bước SSH trong CD cho *cùng
 * một vân tay* cho ba nguyên nhân cần ba cách xử lý ngược nhau, nên người trực nhìn log mà
 * không biết phải làm gì. Nhãn suông thì lặp lại đúng lỗi ấy — nên mỗi giá trị phải nói ra
 * **việc phải làm**.
 */
export const LY_DO_HONG: Record<SyncFailureKind, { label: string; viecPhaiLam: string }> = {
  THIEU_MA_SO: {
    label: 'Chưa có mã số',
    viecPhaiLam: 'Chưa hề gọi lần nào. Vào Nguồn dữ liệu đặt mã số truy cập.',
  },
  NOT_WORKING: {
    label: 'Nguồn từ chối mã số',
    viecPhaiLam:
      'Mã số sai, hoặc thiếu dấu “;” ở cuối. Thử lại không bao giờ hết — phải đặt lại mã số.',
  },
  TIMEOUT: {
    label: 'Hết thời gian chờ',
    viecPhaiLam:
      'Mạng hoặc nguồn quá tải. Một lượt lẻ là bình thường; nhiều lượt liên tiếp mới là sự cố.',
  },
  HTTP_ERROR: {
    label: 'Nguồn trả mã lỗi',
    viecPhaiLam: 'Nguồn còn sống nhưng từ chối phục vụ. Liên hệ đơn vị cấp dữ liệu.',
  },
  EMPTY_BODY: {
    label: 'Trả rỗng',
    viecPhaiLam:
      '⚠ Nguy hiểm nhất: lượt gọi trông như thành công. Thường là nguồn đổi định dạng — đối chiếu nguyên văn ở hydro_raw_logs.',
  },
};

// =============================================================================
// Chất lượng số đo — WS-32
// =============================================================================

/**
 * Ba trạng thái của một bản ghi số đo, kèm màu và **việc phải làm**.
 *
 * ⛔⛔ `HOP_LE` **không được vẽ màu**: nó là kết cục của gần như mọi bản ghi, và tô màu cho
 * trạng thái bình thường là làm hai trạng thái còn lại chìm đi. Cùng bài học §10.42 đã áp cho
 * `SKIPPED_UP_TO_DATE` ở bảng trên.
 *
 * ⚠ `XOA` là **bia mộ**, ⛔ không phải "mức chất lượng thứ ba". Nó nằm ngoài thang HOP_LE ↔
 * NGHI_NGO, và bộ lọc `quality = 'HOP_LE'` của quy tắc 14 loại nó ra miễn phí.
 */
export const CHAT_LUONG_SO_DO: Record<
  ReadingQuality,
  { label: string; color: string; giaiThich: string }
> = {
  HOP_LE: {
    label: 'Hợp lệ',
    color: 'default',
    giaiThich: 'Qua bộ quy tắc chuẩn hoá — được dùng cho báo cáo, biểu đồ và cảnh báo ngưỡng.',
  },
  NGHI_NGO: {
    label: 'Nghi ngờ',
    color: 'orange',
    giaiThich:
      'Vượt khoảng vật lý hoặc nhảy quá nhanh. ⚠ Bản ghi VẪN nằm trong CSDL — nó chỉ bị loại khỏi báo cáo, biểu đồ và cảnh báo cho tới khi có người duyệt.',
  },
  XOA: {
    label: 'Đã loại bỏ',
    color: 'red',
    giaiThich:
      'Người duyệt kết luận không dùng được, kèm lý do. ⛔ Dòng vẫn nằm nguyên và giá trị gốc không bị sửa — nguyên văn response cũng còn ở nhật ký thô. ⛔ Không có đường quay lại.',
  },
};

/**
 * Bản ghi này do đâu mà có.
 *
 * ⭐ Phân biệt được hai nguồn là điều kiện để trả lời một câu hỏi vận hành có thật: *"khoảng
 * trống hôm qua là do poller chết hay do nguồn không phát?"* — nếu ai đó đã nhập tay bù vào
 * thì hai tình huống ấy trông giống hệt nhau trên biểu đồ.
 */
export const NGUON_SO_DO: Record<ReadingSource, { label: string; color: string }> = {
  API: { label: 'Tự động', color: 'blue' },
  MANUAL: { label: 'Nhập tay', color: 'purple' },
};

// =============================================================================
// Máy cảnh báo ngưỡng — WS-33
// =============================================================================

/**
 * Loại điều kiện của một ngưỡng, kèm **cách đọc con số** đi cùng nó.
 *
 * ⭐ `moTaThamSo` là thứ cứu người nhập: bốn loại dùng chung hai ô số, và ý nghĩa của hai ô
 * ấy đổi theo loại. Không nói ra thì `RATE_OF_CHANGE` bị hiểu là *"chênh giữa hai lượt đo"*
 * — sai một bậc thời gian, và sai kiểu ⛔ không bao giờ lộ ra khi thử tay.
 */
export const LOAI_DIEU_KIEN_NGUONG: Record<
  AlertConditionType,
  { label: string; moTaThamSo: string; canCanTren: boolean }
> = {
  GT: {
    label: 'Vượt lên trên',
    moTaThamSo: 'Báo khi giá trị đo LỚN HƠN ngưỡng. ⚠ Bằng đúng ngưỡng thì chưa báo.',
    canCanTren: false,
  },
  LT: {
    label: 'Xuống dưới',
    moTaThamSo: 'Báo khi giá trị đo NHỎ HƠN ngưỡng — dùng cho mực nước bể hút cạn.',
    canCanTren: false,
  },
  OUT_OF_RANGE: {
    label: 'Ra ngoài khoảng',
    moTaThamSo: 'Báo khi giá trị ra khỏi khoảng [cận dưới … cận trên]. Phải nhập đủ cả hai cận.',
    canCanTren: true,
  },
  RATE_OF_CHANGE: {
    label: 'Đổi quá nhanh',
    moTaThamSo:
      'Ngưỡng là ĐỘ LỚN thay đổi trên MỘT GIỜ, ⛔ không phải chênh giữa hai lượt đo. Cần ít nhất một số đo hợp lệ trước đó.',
    canCanTren: false,
  },
};

/**
 * Trạng thái một lần vượt ngưỡng.
 *
 * ⚠⚠ Nhãn của `DANG_XAY_RA` cố ý **không** nói "đang cảnh báo": một dòng chưa `daXacNhan`
 * là điều kiện đang được theo dõi, ⛔ chưa ai nhận thông báo nào. Màn hình phải hiện cả hai
 * thông tin — trạng thái và cờ xác nhận — nếu không thì "đang xảy ra" đọc thành "đã báo động"
 * và người trực tưởng lãnh đạo đã biết.
 *
 * ⛔ `DA_XU_LY` **không** vẽ màu: nó là kết cục bình thường của gần như mọi cảnh báo, và tô
 * màu cho trạng thái bình thường là làm hai trạng thái còn lại chìm đi (§10.42).
 */
export const TRANG_THAI_CANH_BAO: Record<
  AlertEventStatus,
  { label: string; color: string; giaiThich: string }
> = {
  DANG_XAY_RA: {
    label: 'Đang xảy ra',
    color: 'red',
    giaiThich:
      'Giá trị vẫn ngoài ngưỡng. ⚠ Xem cột "Đã báo động": chưa xác nhận nghĩa là điều kiện chưa giữ đủ số phút cấu hình, và CHƯA AI nhận thông báo.',
  },
  DA_XU_LY: {
    label: 'Đã kết thúc',
    color: 'default',
    giaiThich:
      'Giá trị đã về trong ngưỡng, hoặc người trực đã đóng. Cột "Người đóng" phân biệt hai trường hợp — trống nghĩa là hệ thống tự đóng.',
  },
  FALSE_ALARM: {
    label: 'Báo động giả',
    color: 'orange',
    giaiThich:
      'Điều kiện hết trước khi giữ đủ số phút cấu hình (một cú nhiễu cảm biến), hoặc người trực xem lại và bác bỏ.',
  },
};
