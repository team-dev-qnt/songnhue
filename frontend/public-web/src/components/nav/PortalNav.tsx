'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useId, useRef, useState } from 'react';

import type { MenuLink } from '@/lib/api';
import { menuCap1KeTiep, type SuKienMenuCap1 } from '@/lib/menuCap1';
import { isExternal, menuHref, ROUTES } from '@/lib/routes';
import { vuaThanhNgang } from '@/lib/vuaThanhNgang';

/**
 * Kiểu chữ và đệm của mục cấp 1 — khai **một lần**, dùng ở cả thanh thật lẫn thước đo.
 *
 * ⛔ Đây không phải gom code cho gọn. Thước đo quyết định thanh ngang có hiện hay không; nếu nó
 *    vẽ bằng cỡ chữ khác thanh thật thì phép đo trả lời cho một thanh KHÔNG TỒN TẠI, và sai số
 *    ấy im lặng — thanh vẫn hiện, chỉ là tràn. Đúng hình dạng luật 14 (hai nơi phải nhớ giống
 *    nhau), nên chặn bằng cấu trúc chứ không bằng lời dặn.
 */
const LOP_CHU_CAP1 = 'text-[12px] font-semibold';

/**
 * Đệm, khoảng cách **và chữ hoa** của MỘT mục cấp 1 — cùng lý do trên, cộng một lý do nữa.
 *
 * ⛔⛔ {@code uppercase} phải nằm ở ĐÂY, tuyệt đối không đặt lên {@code <ul>} rồi trông chờ kế thừa.
 *
 * Mục {@code linkType='NONE'} vẽ ra {@code <button>} chứ không phải {@code <a>} — chúng chỉ để mở
 * menu con. UA stylesheet của trình duyệt khai thẳng {@code text-transform: none} trên
 * {@code button}, và <b>một khai báo trên chính phần tử luôn thắng giá trị kế thừa</b>, kể cả khai
 * báo của trình duyệt. Preflight của Tailwind v4 có reset {@code font}, {@code letter-spacing},
 * {@code color} cho form control đúng vì lý do này, nhưng <b>không</b> reset
 * {@code text-transform}.
 *
 * <p>Hậu quả đo được ngày 28/08: sáu mục viết hoa, còn <i>"Giới thiệu"</i> và
 * <i>"Quản lý, vận hành"</i> thì không — <b>đúng bằng tập hai mục {@code NONE}</b>, không mục nào
 * khác. Trông như lỗi dữ liệu, thật ra là một luật CSS.
 */
const LOP_MUC_CAP1 = 'flex items-center gap-1 whitespace-nowrap rounded-md px-2 py-3 uppercase';

export interface NhanhMenu {
  item: MenuLink;
  children: MenuLink[];
}

interface PortalNavProps {
  /** Cây menu HEADER đã dựng ở phía máy chủ — cùng một nguồn với card chuyên mục và chân trang. */
  tree: NhanhMenu[];
}

/**
 * Thanh điều hướng chính của cổng — bản dựng lại 28/08/2026.
 *
 * <h2>⚠⚠ Vì sao phải dựng lại: thanh cũ TRÀN khung trên mọi màn hình</h2>
 *
 * Cây nội dung §3 có <b>tám</b> mục cấp 1, nhãn tiếng Việt dài ("Hoạt động Đảng, đoàn thể" 24 ký
 * tự). Bản cũ vẽ chúng bằng {@code text-[13px] font-bold uppercase tracking-wider} với
 * {@code px-3.5}. Đo ra:
 *
 * <pre>
 *   menu 1344px + nút Tìm kiếm 110px = 1454px
 *   khung chứa                        = 1232 − 48 = 1184px      → tràn 22%
 * </pre>
 *
 * {@code flex-wrap} nên nó không vỡ bố cục — nó <b>xuống dòng</b>, và thanh điều hướng cao gấp
 * đôi ở <i>mọi</i> bề rộng, kể cả desktop rộng nhất. Trên điện thoại thì tám mục viết hoa xếp
 * thành một mảng chữ chiếm gần hết màn hình đầu tiên. §10 của văn bản nghiệm thu có đúng một
 * dòng cho việc này: <i>"Giao diện hiển thị đúng trên máy tính, máy tính bảng và điện thoại"</i>.
 *
 * <h2>Ba thay đổi, và mỗi thay đổi đổi lấy cái gì</h2>
 *
 * <ol>
 *   <li><b>Bỏ {@code uppercase} + {@code tracking-wider} ở cấp 1</b> — thu 1344px → ~1082px.
 *       Không chỉ để vừa: chữ hoa tiếng Việt chồng dấu ("ĐOÀN THỂ", "HOẠT ĐỘNG") làm dấu thanh
 *       dính vào nhau và khó đọc hơn hẳn chữ thường. Menu con vẫn giữ chữ thường như cũ.
 *       <b>⚠ Vế chữ hoa đã ĐẢO NGƯỢC ngày 28/08 theo yêu cầu Công ty — xem mục kế tiếp; vế
 *       {@code tracking-wider} thì KHÔNG, và lý do là ngân sách bề rộng ở dưới.</b>
 *   <li><b>Không vừa thì chuyển sang ngăn kéo</b> — một nút, và toàn bộ cây mở ra theo chiều
 *       dọc, nơi nhãn dài không phải cạnh tranh bề ngang với nhau. (Bản đầu chốt ở ngưỡng
 *       {@code lg}; từ 28/08 nó ĐO — xem mục "Ngưỡng là phép đo" bên dưới.)
 *   <li><b>Menu con mở được bằng CHẠM và bàn phím</b>, không chỉ bằng rê chuột — xem dưới.
 * </ol>
 *
 * <h2>⭐ 28/08: chữ hoa trở lại ở cấp 1 — và bề rộng phải mua lại bằng cái khác</h2>
 *
 * Công ty yêu cầu <b>toàn bộ mục cấp 1 viết hoa</b>. Thêm {@code uppercase} vào bản đang chạy mà
 * không đổi gì khác thì <b>tràn khung trở lại</b> — đúng lỗi §10.62 vừa sửa xong. Đo bằng chính
 * font đang dùng (Noto Sans 600, {@code @fontsource}), trên tám nhãn thật lấy từ API menu:
 *
 * <pre>
 *   khung chứa (max-w-1232 − px-6×2)                        = 1184px
 *
 *   thường 13px px-3   (bản 28/08 sáng)   1173,0px   dư  19,0
 *   HOA    13px px-3   (thêm mỗi uppercase) 1297,5px  TRÀN 105,5   ← §10.62 tái phát
 *   HOA    13px px-2                      1225,5px   TRÀN  33,5
 *   HOA    12px px-2.5                    1186,6px   dư   5,4     ← sát mép, không nhận
 *   HOA    12px px-2                      1150,6px   dư  41,4     ← ĐANG DÙNG
 * </pre>
 *
 * Chữ hoa tiếng Việt rộng hơn chữ thường <b>15,7%</b> (đo được, không ước lượng). Nên cỡ chữ
 * xuống 12px và đệm ngang {@code px-3 → px-2}. Kết quả còn <b>nhiều headroom hơn</b> bản chữ
 * thường trước đó (41,4px so với 19,0px).
 *
 * <p>⛔ <b>KHÔNG thêm lại {@code tracking-wider}</b> dù nó vốn đi cùng chữ hoa: nó ngốn thêm
 * ~38px và đẩy thanh về sát mép. Cần thoáng hơn thì nới {@code py}, đừng nới {@code tracking}.
 *
 * <p>⭐⭐ <b>29/08: menu con CŨNG viết hoa</b> — Công ty yêu cầu, và nó đảo lại lựa chọn ghi ở
 * đoạn trên (giữ nguyên đoạn ấy để đọc được vì sao từng chọn khác).
 *
 * <p>⛔ Vì sao đặt ở CSS chứ không viết hoa vào nhãn trong CSDL: nhãn menu do Công ty nhập từ màn
 * hình quản trị. Đặt ở dữ liệu thì hôm nay 12 mục con đúng, còn mục thứ 13 ai đó thêm vào tuần
 * sau sẽ chữ thường — một quy ước phụ thuộc trí nhớ con người, và <b>không cổng kiểm nào bắt
 * được</b> vì nó nằm trong CSDL. Đặt ở đây thì mọi mục mới tự có, kể cả mục chưa ai nhập.
 *
 * <p>Đây cũng là lý do bản vá ngày 29/08 <b>gỡ</b> migration đổi nhãn thành chữ hoa: hai cơ chế
 * cùng sinh ra chữ hoa (CSS cho cấp 1, dữ liệu cho một mục cấp 2) chính là thứ tạo ra cảnh
 * <i>một mục hoa, mười một mục thường</i> mà QuanTran chỉ ra.
 *
 * <p>⚠ Bề rộng đã đo, không ước lượng: menu con là danh sách dọc {@code min-w-64} (256px). Nhãn
 * dài nhất "HOẠT ĐỘNG ĐẢNG, ĐOÀN THỂ" ở 13px chiếm ~204px + đệm 32px = 236px, còn dư 20px. Và
 * {@code min-w} chứ không phải bề rộng cố định, nên nhãn dài hơn làm khối rộng ra chứ không tràn.
 *
 * <h2>⭐⭐ Ngưỡng là một PHÉP ĐO, không phải một con số chốt sẵn</h2>
 *
 * Bảng trên là ngân sách của <b>bộ nhãn hôm nay</b>. Nhãn menu nằm trong CSDL và Công ty sửa được
 * từ màn hình quản trị, nên mọi con số cân sẵn đều có hạn dùng: thêm mục cấp 1 thứ chín, hay đổi
 * một nhãn dài hơn "Hoạt động Đảng, đoàn thể", là tràn lại — và <b>không cổng kiểm nào đỏ được</b>,
 * vì một bài kiểm tĩnh không đọc được CSDL.
 *
 * <p>Nên từ 28/08 ngưỡng không còn là {@code lg}: một {@code ResizeObserver} đo <b>thước đo</b>
 * (bản vô hình của tám mục, vẽ bằng đúng {@code LOP_CHU_CAP1} và {@code LOP_MUC_CAP1} của thanh
 * thật) rồi so với chỗ trống còn lại sau nút Tìm kiếm. Không vừa ⇒ rơi về ngăn kéo. Ràng buộc tự
 * đúng với mọi bộ nhãn, kể cả bộ chưa ai nhập.
 *
 * <p>⚠ Trước lượt đo đầu tiên — SSR, khung hình đầu, hoặc trình duyệt không chạy JS —
 * {@code vuaKhung} là {@code null} và component giữ nguyên ngưỡng {@code lg} cũ làm <b>đường
 * lui</b>. Không có JS thì cổng vẫn dùng được đúng như trước, chỉ mất phần tự rơi về ngăn kéo.
 *
 * <p>⚠ Giới hạn, nói ra thay vì để người đọc tự suy (luật 28): phép đo trả lời <i>"tám nhãn này có
 * vừa không"</i>, <b>không</b> trả lời <i>"thanh có đẹp không"</i>. Vừa sát mép vẫn là vừa. Và nó
 * không biết gì về menu con — menu con bung ra bên dưới nên bề rộng của chúng chưa bao giờ là
 * ràng buộc của thanh.
 *
 * ⛔ Hệ màu, kiểu khối và cách trình bày <b>giữ nguyên</b> (§2 của văn bản nghiệm thu):
 * cùng dải navy, cùng chiều cao, cùng vàng kim khi rê chuột. Bảy mã màu ghi cứng nay đọc từ
 * {@code design-tokens} ({@code chrome.navy*}) — cùng giá trị đang hiện, chỉ khác nơi khai.
 *
 * <h2>Vì sao là client component, trong khi phần còn lại của đầu trang không phải</h2>
 *
 * Ba việc dưới đây <b>không</b> làm được ở phía máy chủ, và cả ba đều là thứ người dùng thật
 * chạm vào: trạng thái đóng/mở của ngăn kéo, mục nào đang là trang hiện tại
 * ({@code usePathname}), và menu con mở bằng chạm. Dữ liệu menu vẫn lấy ở
 * {@code SiteHeader} phía máy chủ rồi truyền xuống — không có lượt gọi API nào từ trình duyệt.
 *
 * <h2>Menu con trên desktop: rê chuột KHÔNG đủ, và đây là chỗ đã suýt sai</h2>
 *
 * Bản cũ mở menu con thuần bằng {@code group-hover}. Trên máy tính bảng không có con trỏ:
 * chạm vào "Giới thiệu" (mục {@code NONE}, không đường dẫn riêng) là chạm vào một
 * {@code <button>} <b>không làm gì cả</b> — bốn mục con của nó không có cách nào mở ra. Máy
 * tính bảng nằm ngay trong câu §10 vừa dẫn.
 *
 * <p>Bản 28/08 giữ {@code group-hover} (chuột giữ nguyên trải nghiệm cũ) và <i>thêm</i> trạng thái
 * mở điều khiển được bằng bấm và bằng bàn phím. Vế sau đúng; vế "giữ lại" là thứ sinh ra sự cố
 * ngay dưới đây.
 *
 * <h2>⭐⭐ 01/09 — MỘT NGUỒN SỰ THẬT: ba cơ chế mở, không cơ chế nào biết hai cơ chế kia</h2>
 *
 * QuanTran báo: <i>"click 1 item trên thanh navigation, dropdown list không disappear, hover vào
 * item khác thì 2 dropdown cùng hiển thị chồng lên nhau"</i>. Tái hiện được, và nguyên nhân không
 * nằm ở chỗ nào trong logic React — nó nằm ở <b>một lớp CSS</b>.
 *
 * <p>Menu con của bản trước hiện lên nếu <b>bất kỳ</b> điều nào sau đây đúng:
 *
 * <pre>
 *   1. group-hover:visible          ← con trỏ đang ở trong mục
 *   2. group-focus-within:visible   ← có phần tử NÀO ĐÓ trong mục đang giữ focus
 *   3. dangMo (state React)         ← moCap1 === nhãn của mục
 * </pre>
 *
 * Bấm một {@code <button>} thì <b>trình duyệt để lại focus trên nó</b> — không có sự kiện nào thu
 * focus về, và không có dòng mã nào của ta chạy. Vế (2) vì thế <b>bật vĩnh viễn</b> cho mục vừa
 * bấm, độc lập hoàn toàn với chuột và với state. Rê sang mục kế: vế (1) bật cho mục thứ hai. Hai
 * menu con cùng hiện, chồng lên nhau. {@code dongHet()} và {@code datMoCap1(null)} chạy đúng như
 * viết — chúng chỉ tắt được vế (3), và vế (3) không phải thứ đang giữ menu mở.
 *
 * <p>⛔ Đây là <b>quy tắc 14 ở dạng CSS ↔ state</b>: hai nơi cùng quyết định một điều, và không nơi
 * nào đọc được nơi kia. Không thể sửa bằng cách thêm mã đồng bộ — CSS không có chỗ để hỏi
 * {@code moCap1}. Cách duy nhất đóng hẳn lớp lỗi là <b>bỏ hai vế CSS đi</b> và để {@code moCap1}
 * — một biến, một giá trị — làm nguồn duy nhất. Hai menu con cùng mở khi ấy không phải "khó xảy
 * ra" mà là <b>không biểu diễn được</b>: {@code string | null} chỉ giữ được một nhãn.
 *
 * <p>Đổi lại, ba hành vi mà CSS đang lo hộ phải viết ra tay — và mỗi cái có một cái bẫy riêng,
 * ghi ngay tại chỗ trong {@link MucCap1}: rê chuột phải lọc {@code pointerType} (chạm cũng bắn
 * {@code pointerenter}), focus phải lọc {@code :focus-visible} (bấm chuột cũng bắn {@code focus}),
 * và {@code dong} phải so nhãn trước khi xoá (rời mục A không được tắt menu của mục B).
 *
 * <p>⚠ {@code aria-expanded} nay đặt ở <b>cả hai</b> nhánh — mục có đường dẫn cũng có menu con,
 * mà bản trước chỉ khai ở nhánh {@code NONE}. Trình đọc màn hình vì thế im lặng về một nửa số
 * menu con của thanh.
 *
 * <h2>⭐ 01/09 — ô tìm kiếm quay lại thanh này, dưới dạng một biểu tượng</h2>
 *
 * Đường đi: 28/08 nút Tìm kiếm ở thanh này → 29/08 dời lên dải nhận diện để trả ~110px cho ngân
 * sách bề rộng → 01/09 quay lại, nhưng <b>thu về một biểu tượng 44px</b> và ô nhập bung ra ở
 * <b>một hàng riêng</b> bên dưới. Ngân sách vì thế chỉ mất 44px thay vì 110px, còn ô nhập thì
 * không phải cạnh tranh bề ngang với tám nhãn cấp 1 ở bất kỳ bề rộng nào.
 *
 * <p>Đây cũng là lý do dải nhận diện ({@link SiteHeader}) nay canh giữa được: nó không còn phải
 * chừa chỗ cho một ô nhập rộng 288px ở bên phải.
 */
export function PortalNav({ tree }: PortalNavProps) {
  const [moNganKeo, datMoNganKeo] = useState(false);
  /**
   * Nhãn của mục cấp 1 đang mở menu con trên desktop. `null` = không mục nào.
   *
   * ⭐⭐ **Đây là nguồn sự thật DUY NHẤT** — một biến, một giá trị, nên *về mặt kiểu dữ liệu*
   * không thể có hai menu con cùng mở. Xem javadoc "Một nguồn sự thật" ở đầu tệp.
   */
  const [moCap1, datMoCap1] = useState<string | null>(null);
  /** Ô tìm kiếm đang bung ra hay đang thu về một biểu tượng — CR "gọn thanh nhận diện", 01/09. */
  const [moTimKiem, datMoTimKiem] = useState(false);
  const duongDan = usePathname();
  const idNganKeo = useId();
  const idTimKiem = useId();
  const vungNavRef = useRef<HTMLElement>(null);
  const khungRef = useRef<HTMLDivElement>(null);
  const thuocDoRef = useRef<HTMLUListElement>(null);
  const timKiemRef = useRef<HTMLButtonElement>(null);
  const oNhapTimRef = useRef<HTMLInputElement>(null);

  /**
   * Thanh ngang có VỪA khung không — `null` nghĩa là chưa đo lần nào.
   *
   * <h2>Vì sao đo, thay vì chọn một ngưỡng `lg`</h2>
   *
   * Ngưỡng theo bề rộng màn hình trả lời sai câu hỏi. Câu hỏi thật là *"tám nhãn này, ở cỡ chữ
   * này, có vừa khung không"* — mà nhãn nằm trong CSDL và Công ty sửa được từ màn hình quản trị.
   * Bản trước chốt `lg` (1024px) rồi cân cỡ chữ cho vừa **đúng bộ nhãn hôm nay**: thêm mục cấp 1
   * thứ chín, hay đổi một nhãn dài hơn, là tràn lại — và **không cổng kiểm nào đỏ**, vì bài kiểm
   * tĩnh không đọc được CSDL.
   *
   * <p>Đo thì ràng buộc tự đúng mãi: không vừa ⇒ rơi về ngăn kéo, nơi nhãn dài xếp dọc và không
   * phải cạnh tranh bề ngang với nhau.
   */
  const [vuaKhung, datVuaKhung] = useState<boolean | null>(null);

  useEffect(() => {
    const khung = khungRef.current;
    const thuoc = thuocDoRef.current;
    if (!khung || !thuoc) return;

    // ⚠ Đặt `setState` trong callback của ResizeObserver, KHÔNG trong thân effect:
    //   `react-hooks/set-state-in-effect` chặn cách kia, và nó chặn đúng — gọi thẳng sinh ra một
    //   lượt vẽ lại nối đuôi. ResizeObserver bắn ngay lần `observe` đầu tiên nên phép đo mở màn
    //   vẫn có, mà không cần gọi tay.
    const doLai = () => {
      // ⚠⚠ Lịch sử của đúng ba dòng này, giữ lại vì nó là một bài học đắt:
      //
      //    28/08 — nút Tìm kiếm nằm ở thanh này, phép đo trừ bề rộng của nó.
      //    29/08 — ô tìm kiếm dời lên dải nhận diện ⇒ `timKiemRef` LUÔN null. Bản trước
      //            `return` khi không tìm thấy nó; giữ nguyên dòng ấy là phép đo im lặng ngừng
      //            chạy, `vuaKhung` kẹt `null` vĩnh viễn, thanh không bao giờ rơi về ngăn kéo
      //            nữa. Không lỗi, không cổng kiểm nào đỏ. Nên nó đổi thành `?? 0`, kèm ghi
      //            chú "chỗ bên phải thanh còn có thể có mục khác về sau".
      //    01/09 — mục ấy về thật: biểu tượng kính lúp. `?? 0` tự nhận lại giá trị đúng, không
      //            phải sửa gì. Đó là phần thưởng của việc viết "không có gì ở đó ⇒ 0" thay vì
      //            "bỏ đo": một phép đo đúng ở cả hai cấu hình, không phải hai bản mã.
      const rongTim = timKiemRef.current?.offsetWidth ?? 0;
      const kieu = getComputedStyle(khung);
      const vua = vuaThanhNgang({
        trong: khung.clientWidth - parseFloat(kieu.paddingLeft) - parseFloat(kieu.paddingRight),
        thuoc: thuoc.scrollWidth,
        tim: rongTim,
      });
      // `null` = chưa kết luận được (khung bề rộng 0: tab chạy nền, lượt vẽ để in). GIỮ NGUYÊN
      // kết luận cũ — xem lý do ở `vuaThanhNgang`.
      if (vua === null) return;
      datVuaKhung((cu) => (cu === vua ? cu : vua));
      // ⚠ Bắt buộc: hiệu ứng khoá cuộn nền bám theo `moNganKeo`. Nếu ngăn kéo đang mở mà thanh
      //   ngang vừa khung trở lại (xoay ngang máy tính bảng), ngăn kéo bị ẩn bằng CSS nhưng
      //   `moNganKeo` vẫn `true` — và trang đứng im, cuộn không được, không dấu vết nào.
      if (vua) datMoNganKeo(false);
    };

    const bd = new ResizeObserver(doLai);
    bd.observe(khung);
    // Quan sát cả thước đo: đổi nhãn menu làm nó rộng ra trong khi khung không đổi kích thước.
    bd.observe(thuoc);
    return () => bd.disconnect();
  }, []);

  // `null` = chưa đo (SSR, lượt vẽ đầu, hoặc không có JS) → giữ nguyên ngưỡng `lg` cũ làm đường
  // lui. Không có JS thì cổng vẫn dùng được đúng như trước, chỉ mất phần tự rơi về ngăn kéo.
  const chuaDo = vuaKhung === null;
  const lopThanhNgang = chuaDo ? 'hidden lg:flex' : vuaKhung ? 'flex' : 'hidden';
  const lopNutNganKeo = chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'flex';
  const lopVungNganKeo = chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'block';

  /**
   * Đóng mọi thứ đang mở — gọi từ **trình xử lý sự kiện** của từng liên kết.
   *
   * ⚠ Bản đầu làm việc này bằng `useEffect` theo `duongDan`. Hai lý do phải đổi, và lý do thứ hai
   * mới là lý do đúng:
   *
   * 1. `react-hooks/set-state-in-effect` chặn ở ESLint — gọi `setState` thẳng trong thân effect
   *    sinh ra một lượt vẽ lại nối đuôi (cùng luật đã bắt `RealtimeFrame` ở đợt trước);
   * 2. bám theo đường dẫn thì bấm một liên kết trỏ về **chính trang đang xem** sẽ không đóng ngăn
   *    kéo — đường dẫn không đổi nên effect không chạy. Người dùng bấm, không thấy gì xảy ra, và
   *    bấm tiếp. Trình xử lý sự kiện không có lỗ ấy: nó chạy vì có người bấm, không vì trạng thái
   *    nào đó tình cờ đổi.
   */
  const dongHet = useCallback(() => {
    datMoNganKeo(false);
    datMoCap1(null);
    datMoTimKiem(false);
  }, []);

  // Esc đóng — lối thoát bắt buộc cho người dùng bàn phím đang kẹt trong menu con.
  useEffect(() => {
    if (!moNganKeo && moCap1 === null && !moTimKiem) return;
    const xuLy = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        datMoNganKeo(false);
        datMoCap1(null);
        datMoTimKiem(false);
      }
    };
    document.addEventListener('keydown', xuLy);
    return () => document.removeEventListener('keydown', xuLy);
  }, [moNganKeo, moCap1, moTimKiem]);

  // Bấm ra ngoài thanh điều hướng thì đóng menu con và ô tìm kiếm đang mở.
  useEffect(() => {
    if (moCap1 === null && !moTimKiem) return;
    const xuLy = (e: PointerEvent) => {
      if (vungNavRef.current?.contains(e.target as Node)) return;
      datMoCap1(null);
      datMoTimKiem(false);
    };
    document.addEventListener('pointerdown', xuLy);
    return () => document.removeEventListener('pointerdown', xuLy);
  }, [moCap1, moTimKiem]);

  /**
   * Bung ô tìm kiếm ⇒ đưa con trỏ nhập vào luôn.
   *
   * ⛔ Không có bước này thì cú bấm biểu tượng mới đi được nửa đường: ô hiện ra, người dùng vẫn
   * phải bấm thêm một lần nữa mới gõ được. Đúng hình dạng "vòng nhập→lưu→hiện hở một nửa" ở
   * quy mô một thao tác (quy tắc 27).
   *
   * ⚠ Đây là `focus()`, KHÔNG phải `setState` — `react-hooks/set-state-in-effect` không chạm tới,
   * và nó phải nằm trong effect vì ô nhập chỉ tồn tại sau lượt vẽ mở ra.
   */
  useEffect(() => {
    if (moTimKiem) oNhapTimRef.current?.focus();
  }, [moTimKiem]);

  // Khoá cuộn nền khi ngăn kéo mở: nếu không, vuốt trong ngăn kéo sẽ cuộn trang phía sau.
  useEffect(() => {
    if (!moNganKeo) return;
    const cu = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = cu;
    };
  }, [moNganKeo]);

  const dangO = useCallback(
    (nhanh: NhanhMenu) => {
      const dich = [menuHref(nhanh.item), ...nhanh.children.map(menuHref)].filter(
        (h): h is string => Boolean(h) && h!.startsWith('/'),
      );
      // Trang chủ phải khớp tuyệt đối; mọi mục khác khớp cả nhánh con để trang bài viết bên
      // trong một chuyên mục vẫn làm sáng mục cha của nó.
      return dich.some((h) => (h === '/' ? duongDan === '/' : duongDan.startsWith(h)));
    },
    [duongDan],
  );

  return (
    <nav
      ref={vungNavRef}
      aria-label="Điều hướng chính"
      className="sticky top-0 z-40 w-full border-b-2 border-brand-primary bg-white text-surface-textBase shadow-sm"
    >
      <div
        ref={khungRef}
        className="mx-auto flex max-w-[1232px] items-center justify-between gap-2 px-4 sm:px-6"
      >
        {/* ───── Nút ngăn kéo — chỉ dưới lg ───── */}
        <button
          type="button"
          onClick={() => datMoNganKeo((cu) => !cu)}
          aria-expanded={moNganKeo}
          aria-controls={idNganKeo}
          className={`items-center gap-2 py-3 pr-2 text-sm font-semibold text-surface-textBase ${lopNutNganKeo}`}
        >
          <span className="relative flex h-5 w-5 items-center justify-center">
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-transform duration-200 ${
                moNganKeo ? 'rotate-45' : '-translate-y-1.5'
              }`}
            />
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-opacity duration-200 ${
                moNganKeo ? 'opacity-0' : 'opacity-100'
              }`}
            />
            <span
              className={`absolute h-0.5 w-5 rounded bg-current transition-transform duration-200 ${
                moNganKeo ? '-rotate-45' : 'translate-y-1.5'
              }`}
            />
          </span>
          <span>{moNganKeo ? 'Đóng' : 'Danh mục'}</span>
        </button>

        {/* ───── Thanh ngang — từ lg trở lên ───── */}
        <ul className={`flex-1 items-center gap-0.5 ${LOP_CHU_CAP1} ${lopThanhNgang}`}>
          {tree.map((nhanh) => (
            <MucCap1
              key={`${nhanh.item.label}-${nhanh.item.depth}`}
              nhanh={nhanh}
              dangO={dangO(nhanh)}
              dangMo={moCap1 === nhanh.item.label}
              // ⭐ Mọi đường đổi trạng thái đi qua ĐÚNG MỘT hàm thuần — `menuCap1KeTiep`. Đó là
              //    chỗ duy nhất kiểm được bằng bài kiểm (kho này không dựng DOM), và là chỗ ba
              //    cái bẫy đã đo được nằm: lọc `pointerType`, so nhãn trước khi xoá, và đảo
              //    trạng thái khi bấm.
              batSuKien={(e) => datMoCap1((cu) => menuCap1KeTiep(cu, e))}
              khiDieuHuong={dongHet}
            />
          ))}
        </ul>

        {/* ───── Thước đo — quyết định thanh ngang có hiện hay không ─────
            Vẽ đúng tám nhãn ấy bằng ĐÚNG hằng số kiểu chữ của thanh thật, ngoài luồng bố cục
            (`absolute`) và không nhìn thấy được, chỉ để đọc `scrollWidth`.

            ⚠ `w-max`: thước phải lấy bề rộng TỰ NHIÊN. Không có nó, bọc `overflow-hidden` bên
              ngoài sẽ ép các mục co lại và thước đo một thanh hẹp hơn thanh thật — tức luôn kết
              luận "vừa", tức bộ đo trở thành trang trí.
            ⚠ `h-0 overflow-hidden` ở lớp bọc: thước rộng hơn màn hình, để tràn tự do sẽ sinh
              thanh cuộn ngang cho cả trang. */}
        <div
          aria-hidden
          className="pointer-events-none invisible absolute left-0 top-0 h-0 overflow-hidden"
        >
          <ul ref={thuocDoRef} className={`flex w-max items-center gap-0.5 ${LOP_CHU_CAP1}`}>
            {tree.map((nhanh) => (
              <li key={`thuoc-${nhanh.item.label}-${nhanh.item.depth}`} className={LOP_MUC_CAP1}>
                <span>{nhanh.item.label}</span>
                {nhanh.children.length > 0 ? <MuiTen /> : null}
              </li>
            ))}
          </ul>
        </div>

        {/* ───── Biểu tượng Tìm kiếm — hiện ở MỌI bề rộng ─────
            Nó đứng ngoài `lopThanhNgang` có chủ đích: khi thanh ngang rơi về ngăn kéo, ô tìm
            kiếm KHÔNG được biến mất theo. Đây là lối tìm kiếm duy nhất của cổng kể từ 01/09
            (dải nhận diện không còn ô nào) — mất nó ở điện thoại là mất hẳn chức năng.

            ⚠ `shrink-0`: nút này là số trừ trong ngân sách bề rộng ở `doLai()`. Để nó co lại
              thì bề rộng đo được nhỏ hơn bề rộng thật, và phép đo kết luận "vừa" cho một thanh
              đang tràn. */}
        <button
          ref={timKiemRef}
          type="button"
          onClick={() => datMoTimKiem((cu) => !cu)}
          aria-expanded={moTimKiem}
          aria-controls={idTimKiem}
          aria-label={moTimKiem ? 'Đóng ô tìm kiếm' : 'Tìm kiếm trên cổng thông tin'}
          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-md transition-colors duration-200 ease-smooth ${
            moTimKiem
              ? 'bg-brand-primaryLight text-brand-primary'
              : 'text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary'
          }`}
        >
          {moTimKiem ? <DauX /> : <KinhLup />}
        </button>
      </div>

      {/* ───── Ô tìm kiếm bung ra — một hàng riêng, kín bề rộng khung ─────
          ⛔ KHÔNG nhét ô nhập vào chính hàng menu. Ở 1232px, tám nhãn cấp 1 đã chiếm 1150,6px
             trong 1184px khả dụng (bảng ngân sách ở javadoc trên) — chèn thêm một ô nhập rộng
             240px vào đó là đẩy thanh vào ngăn kéo ngay trên màn hình desktop rộng nhất. Một
             hàng riêng thì ô nhập không phải cạnh tranh bề ngang với bất cứ thứ gì, và cùng một
             bố cục chạy đúng từ 320px tới 1232px. */}
      {moTimKiem ? (
        <div id={idTimKiem} className="border-t border-surface-border bg-surface-bgLayout">
          <form
            action={ROUTES.search}
            method="get"
            role="search"
            className="mx-auto max-w-[1232px] px-4 py-2.5 sm:px-6"
            onSubmit={() => datMoTimKiem(false)}
          >
            <label htmlFor={`${idTimKiem}-o-nhap`} className="sr-only">
              Tìm kiếm trên cổng thông tin
            </label>
            <div className="flex h-11 items-center gap-2 rounded-full bg-white pl-3.5 pr-1.5 shadow-xs sm:h-10">
              <span className="shrink-0 text-surface-textSecondary">
                <KinhLup />
              </span>
              <input
                ref={oNhapTimRef}
                id={`${idTimKiem}-o-nhap`}
                name="q"
                type="search"
                placeholder="Nhập từ khoá tìm kiếm…"
                className="min-w-0 flex-1 bg-transparent text-[15px] text-surface-textBase outline-none placeholder:text-surface-textSecondary"
              />
              <button
                type="submit"
                className="h-8 shrink-0 rounded-full bg-brand-primary px-3.5 text-xs font-bold text-white transition-colors hover:bg-brand-primaryHover sm:h-7 sm:px-3"
              >
                Tìm
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {/* ───── Ngăn kéo — dưới lg ───── */}
      <div
        id={idNganKeo}
        hidden={!moNganKeo}
        className={`max-h-[calc(100vh-3.5rem)] overflow-y-auto border-t border-surface-border bg-white ${lopVungNganKeo}`}
      >
        <ul className="px-2 py-2">
          {tree.map((nhanh) => (
            <MucNganKeo
              key={`${nhanh.item.label}-${nhanh.item.depth}`}
              nhanh={nhanh}
              dangO={dangO(nhanh)}
              khiDieuHuong={dongHet}
            />
          ))}
        </ul>
      </div>
    </nav>
  );
}

// ───────────────────────── Desktop ─────────────────────────

interface MucCap1Props {
  nhanh: NhanhMenu;
  dangO: boolean;
  dangMo: boolean;
  /** Đẩy một sự kiện vào máy trạng thái dùng chung — xem `@/lib/menuCap1`. */
  batSuKien: (e: SuKienMenuCap1) => void;
  khiDieuHuong: () => void;
}

/**
 * Focus này có phải do BÀN PHÍM không?
 *
 * ⚠ `:focus-visible` là vị từ CSS; trình duyệt không hiểu nó sẽ ném `SyntaxError` ở `matches`.
 * Rơi về `false` — tức *không* mở menu con bằng focus — là hướng hỏng an toàn hơn: mục `NONE`
 * vẫn mở được bằng `click` (Enter/Space đều sinh `click`), và mục có đường dẫn vẫn mở được bằng
 * `ArrowDown`. Rơi về `true` thì ngược lại: máy tính bảng quay về đúng lỗi bấm-không-phản-hồi.
 */
function laFocusBanPhim(el: EventTarget): boolean {
  try {
    return el instanceof Element && el.matches(':focus-visible');
  } catch {
    return false;
  }
}

function MucCap1({ nhanh, dangO, dangMo, batSuKien, khiDieuHuong }: MucCap1Props) {
  const { item, children } = nhanh;
  const href = menuHref(item);
  const coCon = children.length > 0;
  const idMenuCon = useId();

  // ⚠ Gạch chân dùng `border-b-[3px]` ở CẢ hai trạng thái (trong suốt khi không hoạt động):
  //   thêm viền chỉ ở trạng thái hoạt động là mục ấy cao hơn các mục khác 3px và cả hàng nhấp
  //   nháy mỗi lần đổi trang.
  const lop = [
    `${LOP_MUC_CAP1} border-b-[3px] transition-colors duration-200 ease-smooth`,
    dangO ? 'border-brand-primary text-brand-primary' : 'border-transparent text-surface-textBase',
    'hover:border-brand-primary hover:text-brand-primary',
  ].join(' ');

  return (
    <li
      className="relative"
      // ⭐ 01/09: năm trình xử lý dưới đây THAY hai lớp `group-hover:*` và `group-focus-within:*`.
      //    Không cái nào tự quyết định gì — cả năm chỉ MÔ TẢ sự kiện rồi đẩy vào `menuCap1KeTiep`.
      //    Mọi cái bẫy (lọc `pointerType`, so nhãn trước khi xoá, đảo trạng thái khi bấm) nằm
      //    trong hàm ấy, nơi bài kiểm chạm tới được — kho này không dựng DOM.
      onPointerEnter={(e) =>
        batSuKien({ loai: 'contro-vao', nhan: item.label, loaiContro: e.pointerType })
      }
      onPointerLeave={(e) =>
        batSuKien({ loai: 'contro-ra', nhan: item.label, loaiContro: e.pointerType })
      }
      // Chỉ báo sự kiện khi focus đến TỪ BÀN PHÍM. Focus do bấm chuột/chạm cũng bắn `focus`, và
      // coi nó là focus bàn phím là dựng lại đúng cái bẫy máy tính bảng nói ở `menuCap1.ts`.
      onFocus={(e) => {
        if (laFocusBanPhim(e.target)) batSuKien({ loai: 'focus-ban-phim', nhan: item.label });
      }}
      // `focusout` nổi bọt, `relatedTarget` là phần tử sắp nhận focus. Còn trong cùng mục thì
      // giữ nguyên — người dùng bàn phím đang đi trong menu con.
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null))
          batSuKien({ loai: 'roi-focus', nhan: item.label });
      }}
      // ⭐ Mũi tên xuống mở menu con — lối vào bàn phím KHÔNG phụ thuộc `:focus-visible`, nên nó
      //   vẫn chạy trên trình duyệt không hiểu vị từ ấy (xem `laFocusBanPhim`).
      onKeyDown={(e) => {
        if (coCon && e.key === 'ArrowDown') {
          e.preventDefault();
          batSuKien({ loai: 'mui-ten-xuong', nhan: item.label });
        }
      }}
    >
      {href ? (
        <Link
          href={href}
          target={item.openNewTab ? '_blank' : undefined}
          rel={isExternal(item) ? 'noopener noreferrer' : undefined}
          aria-current={dangO ? 'page' : undefined}
          aria-expanded={coCon ? dangMo : undefined}
          aria-controls={coCon ? idMenuCon : undefined}
          onClick={khiDieuHuong}
          className={lop}
        >
          <span>{item.label}</span>
          {coCon ? <MuiTen /> : null}
        </Link>
      ) : (
        // Mục `NONE` chỉ để mở menu con. Bản cũ vẽ nó thành `<button>` không gắn hành vi
        // nào — trên thiết bị cảm ứng nó là một nút bấm không phản hồi (luật 15 ở dạng
        // giao diện: một điều khiển bày ra mà không ai đọc thao tác của nó).
        <button
          type="button"
          onClick={() => batSuKien({ loai: 'bam', nhan: item.label })}
          aria-expanded={dangMo}
          aria-controls={coCon ? idMenuCon : undefined}
          aria-haspopup={coCon ? 'true' : undefined}
          className={lop}
        >
          <span>{item.label}</span>
          {coCon ? <MuiTen /> : null}
        </button>
      )}

      {coCon ? (
        <ul
          id={idMenuCon}
          // ⛔ KHÔNG thêm lại `group-hover:*` / `group-focus-within:*` vào đây. `dangMo` là nguồn
          //    DUY NHẤT quyết định menu con này hiện hay ẩn — xem javadoc "Một nguồn sự thật".
          className={`absolute left-0 top-full z-50 min-w-64 divide-y divide-surface-border/40 rounded-lg border border-surface-border bg-white py-1.5 shadow-lg transition-all duration-200 ease-smooth before:absolute before:-top-2 before:left-0 before:right-0 before:h-2 ${
            dangMo ? 'visible translate-y-0 opacity-100' : 'invisible translate-y-1 opacity-0'
          }`}
        >
          {children.map((con) => {
            const hrefCon = menuHref(con);
            return hrefCon ? (
              <li key={con.label}>
                <Link
                  href={hrefCon}
                  target={con.openNewTab ? '_blank' : undefined}
                  rel={isExternal(con) ? 'noopener noreferrer' : undefined}
                  onClick={khiDieuHuong}
                  className="block px-4 py-2.5 text-[13px] font-medium uppercase text-surface-textBase transition-all duration-150 ease-smooth hover:bg-brand-primaryLight hover:pl-5 hover:text-brand-primary"
                >
                  {con.label}
                </Link>
              </li>
            ) : null;
          })}
        </ul>
      ) : null}
    </li>
  );
}

// ───────────────────────── Ngăn kéo ─────────────────────────

function MucNganKeo({
  nhanh,
  dangO,
  khiDieuHuong,
}: {
  nhanh: NhanhMenu;
  dangO: boolean;
  khiDieuHuong: () => void;
}) {
  const { item, children } = nhanh;
  const href = menuHref(item);
  const coCon = children.length > 0;
  // Mở sẵn nhánh chứa trang đang xem: người dùng thấy ngay mình đang đứng ở đâu trong cây.
  const [mo, datMo] = useState(dangO);
  const idMenuCon = useId();

  const lopNhan = `flex-1 rounded-md px-3 py-3 text-left text-sm font-semibold uppercase ${
    dangO ? 'text-brand-primary' : 'text-surface-textBase'
  }`;

  return (
    <li className="border-b border-surface-border/70 last:border-0">
      <div className="flex items-center">
        {href ? (
          <Link
            href={href}
            target={item.openNewTab ? '_blank' : undefined}
            rel={isExternal(item) ? 'noopener noreferrer' : undefined}
            aria-current={dangO ? 'page' : undefined}
            onClick={khiDieuHuong}
            className={lopNhan}
          >
            {item.label}
          </Link>
        ) : (
          <button type="button" onClick={() => datMo((cu) => !cu)} className={lopNhan}>
            {item.label}
          </button>
        )}
        {coCon ? (
          // Nút bung tách khỏi nhãn: mục cấp 1 có đường dẫn riêng thì bấm nhãn phải ĐI TỚI
          // trang ấy, không phải chỉ bung menu con — nhập nhằng ấy là lý do người dùng di
          // động hay bấm hai lần rồi bỏ cuộc.
          <button
            type="button"
            onClick={() => datMo((cu) => !cu)}
            aria-expanded={mo}
            aria-controls={idMenuCon}
            aria-label={`${mo ? 'Thu gọn' : 'Mở rộng'} ${item.label}`}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary"
          >
            <span className={`transition-transform duration-200 ${mo ? 'rotate-180' : ''}`}>
              <MuiTen />
            </span>
          </button>
        ) : null}
      </div>

      {coCon ? (
        <ul id={idMenuCon} hidden={!mo} className="pb-2 pl-3">
          {children.map((con) => {
            const hrefCon = menuHref(con);
            return hrefCon ? (
              <li key={con.label}>
                <Link
                  href={hrefCon}
                  target={con.openNewTab ? '_blank' : undefined}
                  rel={isExternal(con) ? 'noopener noreferrer' : undefined}
                  onClick={khiDieuHuong}
                  className="block rounded-md px-3 py-2.5 text-[13px] font-medium uppercase text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary"
                >
                  {con.label}
                </Link>
              </li>
            ) : null;
          })}
        </ul>
      ) : null}
    </li>
  );
}

// ───────────────────────── Biểu tượng ─────────────────────────

function MuiTen() {
  return (
    <svg
      className="h-3 w-3 opacity-80"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M19 9l-7 7-7-7" />
    </svg>
  );
}

/** Kính lúp — dùng ở CẢ nút thu gọn lẫn ô nhập đã bung, nên chỉ khai một lần (luật 14). */
function KinhLup() {
  return (
    <svg
      className="h-[18px] w-[18px]"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      aria-hidden="true"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M21 21l-4.35-4.35M11 19a8 8 0 110-16 8 8 0 010 16z"
      />
    </svg>
  );
}

/** Dấu X — trạng thái "đang mở" của cùng một nút, để cú bấm thứ hai đọc ra được là sẽ đóng. */
function DauX() {
  return (
    <svg
      className="h-[18px] w-[18px]"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
    </svg>
  );
}
