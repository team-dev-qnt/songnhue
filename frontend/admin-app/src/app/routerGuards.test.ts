import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Một trang chứa nhiều việc thì phải khai đủ quyền của các việc ấy.**
 *
 * <h2>Sự cố ngày 31/08/2026 — đếm sai đơn vị</h2>
 *
 * Tuyến `/van-hanh/cong-trinh/:publicId` chứa **ba** tab thuộc **ba** quyền khác nhau: sửa hồ sơ
 * (`ops:construction:update`), tài liệu đính kèm (`ops:document:*`), lịch sử sửa chữa
 * (`ops:maintenance:*`). Nhưng cả tuyến bị canh bằng riêng `ops:construction:update`.
 *
 * <p>Đo được trên ma trận RBAC seed:
 *
 * | Vai trò | Quyền vận hành được cấp | Có `ops:construction:update`? | Mở được trang? |
 * |---|---|---|---|
 * | `XN_MANAGER` | **8** | không | **không — 403** |
 * | `XN_OPERATOR` | **3** | không | **không — 403** |
 *
 * <p>Nghĩa là 11 quyền được cấp đúng, endpoint có đủ, màn hình có thật — mà **chưa từng dùng được
 * bởi đúng vai trò sở hữu chúng**. Hệ quả chạm thẳng ra cổng công khai: hai cột "Quy trình vận
 * hành" và "Phương án bảo vệ" (CR-28) chỉ nạp được tệp bởi ADMIN/SUPER_ADMIN/TECHNICIAN, trong khi
 * cán bộ Xí nghiệp mới là người giữ tệp gốc — và ô chọn tệp còn bảo họ *"tải lên ở tab Tài liệu
 * đính kèm trước"*, đúng tab họ bị 403.
 *
 * <h2>Hình dạng lỗi — khác §10.36 và khác §10.62</h2>
 *
 * §10.36 là *"màn hình đòi một quyền mà vai trò sở hữu nó không có"* (ô chọn đơn vị đứng sau
 * `adm:org-unit:view`). §10.62 là *"cột không có nửa còn lại của cặp đọc–ghi"*. Lần này **quyền cấp
 * đúng, đường ghi đủ, màn hình có thật** — chỉ là màn hình bị chôn sau một quyền khác.
 *
 * <p>📌 Đơn vị đếm là chỗ sai: đếm *màn hình đã dựng* thì xanh; đếm *vai trò × việc họ phải làm
 * được* thì hai vai trò ra số không. Cùng bài học §10.62 — <i>một nửa vòng chạy hoàn hảo vẫn cho ra
 * số không</i>.
 *
 * <p>Nguyên tắc đúng đã nằm sẵn trong kho, ở chú thích nút "Nhập nhanh" của `ConstructionsPage`:
 * *"Quyền phải khớp với endpoint mà nút này gọi, không phải quyền sửa hồ sơ công trình."*
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * Bài này đọc **văn bản `router.tsx`** và chỉ canh **một** tuyến — tuyến đã gây ra sự cố. Nó
 * <b>không</b> suy ra được "trang X chứa việc thuộc quyền Y" cho tuyến khác: điều đó nằm trong cây
 * component, không nằm trong bảng tuyến. Đừng đọc bài xanh này thành *"mọi tuyến đã canh đúng
 * quyền"*.
 */
const ROUTER = join(process.cwd(), 'src', 'app', 'router.tsx');

/** Ba quyền của ba tab trên trang hồ sơ công trình. */
const QUYEN_TRANG_CONG_TRINH = [
  'ops:construction:update',
  'ops:document:view',
  'ops:maintenance:view',
] as const;

/**
 * Lấy đối số quyền của một `adminRoute('<đường dẫn>', <quyền>, …)`.
 *
 * @returns danh sách mã quyền tuyến ấy chấp nhận (một phần tử nếu khai chuỗi đơn)
 */
export function quyenCuaTuyen(ma: string, duongDan: string): string[] {
  const viTri = ma.indexOf(`'${duongDan}'`);
  if (viTri < 0) {
    return [];
  }
  // Đối số thứ hai nằm giữa dấu phẩy sau đường dẫn và dấu phẩy trước `lazyPage(`.
  const sau = ma.slice(viTri + duongDan.length + 2);
  const ketThuc = sau.indexOf('lazyPage(');
  const doiSo = ketThuc < 0 ? sau : sau.slice(0, ketThuc);
  return [...doiSo.matchAll(/'([a-z-]+:[a-z-]+:[a-z-]+)'/g)].map((m) => m[1]);
}

describe('Guard của tuyến quản trị — sự cố 31/08', () => {
  const ma = readFileSync(ROUTER, 'utf8');

  it('đọc được tuyến cần soi — chống xanh trên tập rỗng', () => {
    // ⛔ Đổi tên tuyến mà quên bài này thì khẳng định dưới chạy trên mảng rỗng và xanh trọn vẹn.
    expect(quyenCuaTuyen(ma, '/van-hanh/cong-trinh/:publicId').length).toBeGreaterThanOrEqual(1);
  });

  it('trang hồ sơ công trình chấp nhận đủ BA quyền của ba tab', () => {
    expect(quyenCuaTuyen(ma, '/van-hanh/cong-trinh/:publicId')).toEqual([
      ...QUYEN_TRANG_CONG_TRINH,
    ]);
  });

  /**
   * ⭐ Hai tuyến chẩn đoán của T31.13 phải khai ĐÚNG cặp quyền endpoint của chúng nhận (chế độ
   * HOẶC). Đây là cùng một bài học ở dạng phòng ngừa: quyền canh tuyến phải là quyền mà API màn
   * hình gọi, ⛔ không phải quyền "nghe có vẻ thuộc nhóm ấy".
   */
  it('hai màn hình chẩn đoán thuỷ văn nhận cả quyền xem số liệu lẫn quyền cấu hình nguồn', () => {
    const CAP_QUYEN = ['hyd:measurement:view', 'hyd:api-source:manage'];

    expect(quyenCuaTuyen(ma, '/thuy-van/nhat-ky-dong-bo')).toEqual(CAP_QUYEN);
    expect(quyenCuaTuyen(ma, '/thuy-van/ma-la')).toEqual(CAP_QUYEN);
  });

  /**
   * ⭐⭐ WS-32: trang *Dữ liệu nghi ngờ* chứa **ba** việc thuộc **ba** quyền —
   * `hyd:measurement:view` (xem hàng chờ) · `:review` (Duyệt / Loại bỏ) · `:create` (nhập tay).
   *
   * Tuyến phải khai quyền **rộng nhất**; hai nút tự ẩn theo quyền hẹp hơn ở trong trang. Gác tuyến
   * bằng `:review` là chôn cả trang sau quyền hẹp nhất — đúng lỗi 31/08 với tuyến hồ sơ công trình,
   * nơi XN_MANAGER có 8 quyền vận hành mà vẫn nhận 403.
   *
   * ⚠ Đây cũng là chỗ đo được rằng người ĐANG TRỰC vào được: DUTY_OFFICER có `:view` và `:create`
   * mà ⛔ không có `:review`.
   */
  it('trang Dữ liệu nghi ngờ khai quyền RỘNG NHẤT, ⛔ không phải quyền của nút hẹp nhất', () => {
    expect(quyenCuaTuyen(ma, '/thuy-van/du-lieu-nghi-ngo')).toEqual([
      'hyd:measurement:view',
      'hyd:measurement:review',
    ]);
  });

  it('⛔ và tuyến Nguồn dữ liệu vẫn chỉ MỘT quyền — vế phân biệt', () => {
    // Thiếu vế này thì bài trên xanh cả khi ai đó nới toàn bộ nhóm thuỷ văn về cùng một cặp quyền.
    expect(quyenCuaTuyen(ma, '/thuy-van/nguon-du-lieu')).toEqual(['hyd:api-source:manage']);
  });

  describe('kiểm chứng ngược — vị từ phải BẮT ĐƯỢC bản đã gây ra sự cố', () => {
    /** Nguyên văn bản trước bản vá. */
    const BAN_HONG = `
          adminRoute(
            '/van-hanh/cong-trinh/:publicId',
            'ops:construction:update',
            lazyPage(() => import('@/features/operations/ConstructionFormPage')),
          ),`;

    it('bản hỏng chỉ khai MỘT quyền → không khớp ba quyền', () => {
      expect(quyenCuaTuyen(BAN_HONG, '/van-hanh/cong-trinh/:publicId')).toEqual([
        'ops:construction:update',
      ]);
    });

    it('vị từ không phải "luôn rỗng" — nó đọc ra đúng mã ở bản hỏng', () => {
      // Nếu bộ tách trả rỗng cho mọi đầu vào thì bài trên xanh mà chẳng canh gì.
      expect(quyenCuaTuyen(BAN_HONG, '/van-hanh/cong-trinh/:publicId').length).toBe(1);
    });

    it('đường dẫn không tồn tại → rỗng, không ném', () => {
      expect(quyenCuaTuyen(ma, '/khong-ton-tai')).toEqual([]);
    });
  });
});
