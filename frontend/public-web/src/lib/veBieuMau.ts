/**
 * **Vé biểu mẫu công khai — T73.9 (ASVS 11.1.2).**
 *
 * Backend (`VeBieuMauService`) đòi mỗi lượt gửi liên hệ/góp ý mang một vé do máy chủ phát và ký,
 * đủ N giây tuổi (`security.form.min-fill-seconds`, mặc định 3). Một người điền biểu mẫu ⛔ gửi được
 * trong chưa tới ba giây, một máy gửi thẳng vào API thì có.
 *
 * ## Vì sao giao diện tự CHỜ thay vì để người dùng nhận lỗi
 *
 * Người điền nhanh (hoặc trình duyệt tự điền) có thể bấm Gửi trước khi vé đủ tuổi. Trả lỗi cho họ
 * là bắt người dân đọc một câu về thứ họ ⛔ làm sai. Nên `lay()` **chờ nốt phần còn thiếu** —
 * nút đứng ở "Đang gửi…" thêm một nhịp — và máy chủ trả kèm vé đúng số giây phải chờ, để hai phía
 * ⛔ phải cùng nhớ một con số (luật 14).
 *
 * ## Vì sao xin vé lúc người dùng BẮT ĐẦU điền, ⛔ lúc trang tải
 *
 * Phần lớn lượt xem trang Liên hệ là để tra số điện thoại; xin vé lúc tải trang là thêm một lượt
 * gọi API cho mỗi lượt xem mà ⛔ ai dùng. `batDau` gắn vào `onFocus` của biểu mẫu.
 *
 * ## ⚠ Phạm vi — nói ra (luật 28)
 *
 * Vé chặn lớp máy **gửi thẳng vào API** hoặc gửi ngay khi tải trang. Một trình duyệt tự động chạy
 * chính mã này thì cũng chờ đủ tuổi như người — lớp ấy thuộc reCAPTCHA (chờ khoá G13) và hạn mức.
 */

/** Đường xin vé — `PublicPortalController.veBieuMau`. */
export const DUONG_XIN_VE = '/api/v1/public/bieu-mau/ve';

/** Mã lỗi khi vé thiếu · giả · chưa đủ tuổi · quá hạn — khớp `ErrorCode.CMS_2025` (luật 14). */
export const MA_LOI_VE = 'CMS-2025';

/**
 * Câu cho người dùng khi máy chủ vẫn từ chối vé — thường là vé giữ quá 24 giờ (để trang mở qua
 * đêm). Lượt bấm kế tiếp tự xin vé mới và tự chờ, nên câu này chỉ cần nói "bấm lại".
 */
export const THONG_DIEP_VE =
  'Biểu mẫu đã quá hạn hoặc chưa sẵn sàng. Vui lòng bấm Gửi lại — nội dung bạn đã nhập vẫn còn nguyên. Nếu vẫn lỗi, hãy tải lại trang.';

/** Khoảng đệm cộng vào thời gian chờ: độ trễ mạng giữa lúc máy chủ phát vé và lúc trình duyệt nhận. */
export const DEM_CHO_MS = 300;

export interface VeDaNhan {
  ve: string;
  tuoiToiThieuMs: number;
}

type Goi = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * Xin một vé. Trả `null` khi hỏng — ⛔ ném: biểu mẫu vẫn gửi (thiếu vé), máy chủ trả `CMS-2025`
 * và người dùng nhận {@link THONG_DIEP_VE} thay vì một biểu mẫu treo.
 *
 * ⛔ Thiếu `tuoiToiThieuGiay` cũng là hỏng: đoán một con số là để giao diện chờ sai.
 */
export async function xinVe(goi: Goi = (i, init) => fetch(i, init)): Promise<VeDaNhan | null> {
  try {
    const res = await goi(DUONG_XIN_VE, { cache: 'no-store' });
    if (!res.ok) return null;
    const than = (await res.json()) as { data?: { ve?: unknown; tuoiToiThieuGiay?: unknown } };
    const ve = than?.data?.ve;
    const giay = than?.data?.tuoiToiThieuGiay;
    if (typeof ve !== 'string' || ve === '') return null;
    if (typeof giay !== 'number' || !Number.isFinite(giay) || giay < 0) return null;
    return { ve, tuoiToiThieuMs: giay * 1000 };
  } catch {
    return null;
  }
}

/** Phản hồi lỗi có phải "vé ⛔ hợp lệ" không — đọc mã trong envelope, ⛔ đoán theo mã HTTP. */
export async function laLoiVe(res: Response): Promise<boolean> {
  if (res.status !== 422) return false;
  try {
    const than = (await res.json()) as { error?: { code?: unknown } };
    return than?.error?.code === MA_LOI_VE;
  } catch {
    return false;
  }
}

export interface NguoiGiuVe {
  /** Xin vé nếu chưa có và chưa đang xin — gắn vào `onFocus` của biểu mẫu; gọi lặp vô hại. */
  batDau: () => void;
  /** Vé để gắn vào thân POST: xin nếu chưa có, rồi CHỜ tới khi đủ tuổi. `null` ⇔ xin ⛔ được. */
  lay: () => Promise<string | null>;
  /** Máy chủ từ chối vé ⇒ bỏ nó; lượt `lay()` kế tiếp xin vé mới. */
  bo: () => void;
}

export function taoNguoiGiuVe({
  goi = (i, init) => fetch(i, init),
  cho = (ms) => new Promise<void>((xong) => setTimeout(xong, ms)),
  dongHo = () => performance.now(),
}: {
  goi?: Goi;
  cho?: (ms: number) => Promise<void>;
  /** Đồng hồ ĐƠN ĐIỆU của trình duyệt — ⛔ `Date.now()`: đổi giờ máy giữa chừng ⛔ được làm lệch thời gian chờ. */
  dongHo?: () => number;
} = {}): NguoiGiuVe {
  let dangGiu: { ve: string; duTuoiLuc: number } | null = null;
  let dangXin: Promise<void> | null = null;

  function batDau(): void {
    if (dangGiu !== null || dangXin !== null) return;
    dangXin = xinVe(goi)
      .then((v) => {
        if (v !== null) dangGiu = { ve: v.ve, duTuoiLuc: dongHo() + v.tuoiToiThieuMs + DEM_CHO_MS };
      })
      .finally(() => {
        dangXin = null;
      });
  }

  async function lay(): Promise<string | null> {
    batDau();
    if (dangXin !== null) await dangXin;
    const giu = dangGiu;
    if (giu === null) return null;
    const conLai = giu.duTuoiLuc - dongHo();
    if (conLai > 0) await cho(conLai);
    return giu.ve;
  }

  function bo(): void {
    dangGiu = null;
  }

  return { batDau, lay, bo };
}
