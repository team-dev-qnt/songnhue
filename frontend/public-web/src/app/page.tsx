import { AffiliatedUnitsLinks } from '@/components/home/AffiliatedUnitsLinks';
import { CategoryServicesGrid } from '@/components/home/CategoryServicesGrid';
import { GroupLabel } from '@/components/home/GroupLabel';
import { HomeBannerSlider } from '@/components/home/HomeBannerSlider';
import { HomeCategoryNews } from '@/components/home/HomeCategoryNews';
import { HomeMediaGallery } from '@/components/home/HomeMediaGallery';
import { HomeNewsColumn } from '@/components/home/HomeNewsColumn';
import { OperationsBlock } from '@/components/home/OperationsBlock';
import { PublishedDocumentsSection } from '@/components/home/PublishedDocumentsSection';
import { WaterLevelBlock } from '@/components/home/WaterLevelBlock';
import {
  getArticles,
  getBanners,
  getPhotos,
  getMenu,
  getOperationStatuses,
  getServerTime,
  getSiteConfig,
  getSubsidiaries,
} from '@/lib/api';
import { chonKhoiChuyenMuc, nhanNhanhTin } from '@/lib/homeCategories';
import { fileUrl, buildMenuTree } from '@/lib/routes';
import { docBool, docSo } from '@/lib/settings';

/**
 * Trang chủ Cổng TTĐT Thủy lợi Sông Nhuệ — bố cục dựng lại 29/08/2026.
 *
 * <h2>Năm nhóm, không còn mười một khối rời</h2>
 *
 * <pre>
 *   Nhóm 1  Tin tức &amp; sự kiện      slider 8/12 │ danh sách tin có ảnh 4/12
 *                                     + hàng chuyên mục con (3 cột bằng nhau)
 *   Nhóm 2  Điều hành &amp; số liệu    Mực nước (kín bề rộng) → Vận hành (kín bề rộng) → bản đồ
 *   Nhóm 3  Văn bản                 Công bố thông tin · biểu mẫu gửi ý kiến (kín bề rộng)
 *   Nhóm 4  Tổ chức &amp; đơn vị       Xí nghiệp trực thuộc · liên kết
 *   Nhóm 5  Truyền thông            Video · ảnh · chuyên mục &amp; lĩnh vực
 * </pre>
 *
 * <h2>⭐ Vì sao Mực nước và Vận hành KHÔNG nằm cạnh nhau</h2>
 *
 * Hai khối ấy là bảng: mực nước là biểu tổng hợp theo tuyến sông (CN-03.4, tám cột), vận hành là
 * tình hình từng cống (CN-02.11, sáu cột). Nhét một bảng tám cột vào nửa bề rộng thì hoặc chữ bé
 * lại hoặc cột bị cắt — cả hai đều làm hỏng đúng thứ khối ấy sinh ra để làm. Xếp chồng, mỗi khối
 * kín bề rộng khung.
 *
 * <h2>⭐ Hai cột cạnh nhau phải cao bằng nhau — bằng CẤU TRÚC</h2>
 *
 * Lưới CSS đã kéo giãn các ô con theo hàng ({@code items-stretch} là mặc định), nhưng cái giãn
 * là Ô, không phải cái THẺ bên trong nó. Nên thẻ phải nhận {@code h-full}, và bên trong lại phải
 * là {@code flex-col} với phần co giãn mang {@code flex-1 min-h-0}. Thiếu một mắt trong chuỗi
 * ấy là bên cao bên thấp — lỗi đo được ở bản duyệt ngày 29/08.
 *
 * <p>⛔ Đừng canh bằng một chiều cao chốt sẵn ({@code max-h-[510px]} kiểu cổng tham chiếu): con
 * số ấy đúng với đúng bộ nội dung hôm nay và sai ngay khi một tiêu đề dài thêm một dòng.
 *
 * <h2>⭐ 29/08 — ba thay đổi Công ty yêu cầu sau khi duyệt bản vẽ</h2>
 *
 * <ol>
 *   <li><b>Trụ sở, đầu mối liên hệ VÀ biểu mẫu gửi ý kiến đều rời khỏi trang chủ</b>, dồn hết về
 *       {@code /lien-he}. Lượt đầu 29/08 còn giữ lại biểu mẫu; lượt hai bỏ nốt, vì hai bản của
 *       cùng một biểu mẫu là hai nơi phải nhớ sửa khi CN-01.4 làm tiếp (T26.24).
 *   <li><b>Cơ cấu tổ chức KHÔNG lên trang chủ.</b> Bản vẽ có một ô "Sơ đồ tổ chức Công ty" +
 *       "Lãnh đạo Công ty" ở Nhóm 4; cả hai đã là trang riêng dưới nhánh Giới thiệu
 *       ({@code /gioi-thieu/co-cau-to-chuc}, {@code /gioi-thieu/lanh-dao}) và đọc cùng
 *       {@code org_units}. Dựng thêm một bản rút gọn ở trang chủ là nơi thứ hai hiển thị cùng
 *       một cây — thứ sẽ lệch ngay lần đầu ai đó sửa một bên (luật 14).
 *   <li><b>Hàng chuyên mục con</b> dưới slider — xem {@link HomeCategoryNews}.
 * </ol>
 *
 * <h2>⛔ `site.home.blocks` đã thôi được dùng làm công tắc bật/tắt khối — và luật ấy vẫn đứng</h2>
 *
 * Bố cục trang chủ <b>là</b> cây nội dung Công ty đã duyệt; muốn bớt một khối thì bỏ mục tương
 * ứng khỏi menu, và card ở khối Chuyên mục biến mất theo — cùng một nguồn. Hàng chuyên mục đi
 * đúng luật ấy: nó dựng từ các mục con của nhánh menu {@code site.home.news-category}.
 * {@code SiteLayoutTest} và {@code PortalSettingsReadTest} vẫn canh cho khoá cũ không sống lại.
 *
 * <h2>⚠ 01/09/2026 — MỘT ngoại lệ có tên: `site.home.show-dieu-hanh`</h2>
 *
 * Luật trên chỉ áp được cho khối <b>có một mục menu để gỡ</b>. Nhóm 2 không có: "Mực nước, lượng
 * mưa" và "Vận hành công trình" trên trang chủ là hai khối <i>tóm tắt</i>, còn mục menu tương ứng
 * trỏ sang hai TRANG riêng dưới nhánh {@code /quan-ly-van-hanh}. Gỡ mục menu ấy là gỡ mất cả hai
 * trang chi tiết — tức cái nút duy nhất có sẵn làm nhiều hơn hẳn thứ QuanTran yêu cầu (01/09:
 * <i>"tôi muốn admin có thêm tính năng bật / tắt hiển thị ở trên public-web"</i>).
 *
 * <p>Nên đây là một công tắc <b>trình bày</b>, cùng họ với {@code site.slider.*}: nó chỉnh cách
 * một khối hiện ra, không chỉnh việc khối ấy có thuộc cây nội dung hay không. Ranh giới ấy là thứ
 * giữ cho ngoại lệ này không lớn dần trở lại thành {@code site.home.blocks}.
 *
 * <p>⛔ MỘT công tắc cho cả nhóm, không phải hai. Tách riêng Mực nước / Vận hành là dựng thêm một
 * cột không ai yêu cầu — quy tắc 15 tính đó là một lỗi, không phải một tính năng để dành.
 */
export const revalidate = 300;

/** Bài lấy về cho khối tin tức. Dư ra so với số hiển thị để đổi `site.home.news-count` không cần deploy. */
const SO_BAI_TIN_TUC = 24;

export default async function HomePage() {
  const [
    config,
    banners,
    photos,
    headerMenu,
    portalLinks,
    subsidiaries,
    tinhHinhVanHanh,
    serverTime,
  ] = await Promise.all([
    getSiteConfig(),
    getBanners(),
    getPhotos(),
    getMenu('HEADER'),
    getMenu('LIEN_KET'),
    getSubsidiaries(),
    getOperationStatuses(),
    getServerTime(),
  ]);

  const hotline = config?.['company.hotline'] ?? '';
  const menuTree = buildMenuTree(headerMenu ?? []);

  // ⚠ Mọi tham số dưới đây đọc từ `settings`, không có số nào viết trong tệp này. Mặc định thứ hai
  //   là lưới an toàn khi CSDL chưa có khoá, không phải nơi chốt giá trị.
  const soAnhSlider = docSo(config?.['site.slider.max-items'], 20);
  const nhipSlider = docSo(config?.['site.slider.interval-seconds'], 5);
  const soBaiTinTuc = docSo(config?.['site.home.news-count'], 5);
  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);
  const soVanBan = docSo(config?.['site.home.documents-count'], 6);
  const danhMucVanBan = config?.['site.home.documents-category'] ?? 'cong-bo-thong-tin';
  const danhMucTin = config?.['site.home.news-category'] ?? '';
  const soBaiChuyenMuc = docSo(config?.['site.home.category-news-count'], 4);
  // Công tắc Nhóm 2 — xem javadoc đầu tệp để biết vì sao khối này được có một công tắc trong khi
  // chính sách chung là "bố cục trang chủ LÀ cây menu". Mặc định BẬT: một bản vá thêm công tắc
  // không được đổi thứ người dùng đang nhìn thấy.
  const hienDieuHanh = docBool(config?.['site.home.show-dieu-hanh'], true);
  const hienNhanLienKet = docBool(config?.['site.home.lien-ket.show-label'], true);

  // Nhãn và danh sách chuyên mục con đều lấy từ MENU — không có chuỗi 'tin-tuc' hay
  // "Tin tức – Sự kiện" nào viết trong tệp này nữa.
  const tieuDeTin = nhanNhanhTin(menuTree, danhMucTin);
  const chuyenMuc = chonKhoiChuyenMuc(menuTree, danhMucTin);
  // Cùng một hàm, cùng một luật: nhánh con của mục menu trỏ vào `site.home.documents-category`.
  const nhomVanBan = chonKhoiChuyenMuc(menuTree, danhMucVanBan);

  // Hai lượt gọi dưới đây phụ thuộc `config` ở lượt trên nên phải chờ tới đây; chúng chạy song
  // song với nhau. Danh mục lạ ⇒ backend trả rỗng, khối nói thẳng là chưa có bài — không nổ.
  const [latest, documents, baiTheoChuyenMuc] = await Promise.all([
    // ⚠ LỌC theo `site.home.news-category`, không lấy toàn bộ bài. Bản trước gọi
    //   `getArticles({ size })` trần, nên cột "Tin tức – Sự kiện" liệt kê cả bài của nhánh
    //   Giới thiệu — đo được trên stack 29/08: dòng mới nhất là trang tĩnh **"Tổng quan"**,
    //   không có ảnh bìa. Một danh sách mà nút "Xem tất cả" dẫn tới nơi KHÔNG chứa những mục
    //   vừa liệt kê là một danh sách sai, không phải một danh sách rộng hơn.
    getArticles({ category: danhMucTin, size: SO_BAI_TIN_TUC }),
    getArticles({ category: danhMucVanBan, size: soVanBan }),
    Promise.all(
      chuyenMuc.map(async (cm) => ({
        ...cm,
        articles: (await getArticles({ category: cm.slug, size: soBaiChuyenMuc }))?.content ?? [],
      })),
    ),
  ]);

  const allArticles = latest?.content ?? [];

  return (
    <div className="mx-auto max-w-[1232px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ═════════ NHÓM 1 · TIN TỨC & SỰ KIỆN ═════════ */}
      {/* ⚠ Nhãn nhóm này TỪNG thiếu: bốn nhóm dưới có, nhóm đầu không — nên dải mảnh đầu tiên
          người đọc gặp là "Điều hành & số liệu" ở giữa trang, và nó trông như mốc bắt đầu của
          một cấp bậc chứ không phải mốc thứ hai của một dãy. Một cấp phân nhóm chỉ có nghĩa
          khi nó xuất hiện ở MỌI nhóm. */}
      <GroupLabel dauTien>Tin tức &amp; sự kiện</GroupLabel>
      {/* ⭐⭐ 01/09 — CỘT SLIDER ĐỊNH CHIỀU CAO HÀNG; CỘT TIN TRUNG TÍNH VỀ CHIỀU CAO.

          <h3>Bản 01/09 sáng đã sai ở đâu — và vì sao phải xoá hẳn chứ không giữ làm lịch sử</h3>

          Bản ấy đặt `lg:max-h-[calc(100svh-17rem)]` lên **khung lưới** rồi tin rằng hàng sẽ co
          theo. Không. `grid-auto-rows: auto` = `minmax(auto, max-content)`; thuật toán định cỡ
          track **không có bước nào** ép một track `auto` co lại cho vừa `max-height` của khung
          bao (chia phần thừa chỉ tồn tại với track `fr`). Trần kẹp cái HỘP, hàng vẫn 540px và
          tràn ra ngoài (`overflow: visible`) — vẽ đè lên `HomeCategoryNews` ngay bên dưới. Đó
          đúng là ảnh chụp màn hình QuanTran gửi.

          ⚠ Chú thích cũ còn khẳng định `min-h-0` là "mắt xích không được quên". Sai nốt:
            `min-height:0` chỉ tác động lên **hàm MIN** của track, còn hàm MAX ở đây là
            `max-content` và vẫn nở. Hai lớp `lg:min-h-0` ấy là cơ chế tồn tại mà **không có
            hiệu lực** — luật 15. Xoá cả hai.

          <h3>Cách sửa — giữ nguyên nguyên tắc "cao bằng nhau bằng CẤU TRÚC" ở javadoc trên</h3>

          Cột tin bọc thêm một lớp `lg:relative` với thẻ con `lg:absolute lg:inset-0`. Hệ quả
          CSS, cả hai vế đều cần:

            • con `absolute` bị loại khỏi phép tính kích thước nội tại của tổ tiên ⇒ ô lưới ấy
              đóng góp **0** vào chiều cao hàng. Hàng vì thế do CỘT SLIDER định, mà slider nay
              là `aspect-[16/9]` — một tỉ lệ, không phải một con số chốt sẵn.
            • `items-stretch` (mặc định) vẫn kéo ô ấy cao bằng hàng SAU khi hàng đã tính xong,
              rồi `inset-0` cho thẻ tin một chiều cao XÁC ĐỊNH. Đó chính là thứ chuỗi
              `h-full → flex-1 min-h-0 → overflow-y-auto` trong `HomeNewsColumn` vẫn thiếu:
              nó đúng từ đầu, chỉ chưa bao giờ có một cha bị chặn để cuộn.

          ⛔ KHÔNG chép `max-h-[510px]` của cổng tham chiếu, dù đó là cách họ làm — javadoc đầu
             tệp (29/08) đã cấm, và lý do vẫn đúng nguyên: con số ấy khớp đúng bộ nội dung hôm
             nay và sai ngay khi một tiêu đề dài thêm một dòng. Ta lấy **hình dạng** của họ
             (8/4, ảnh 16:9, cột phải cuộn trong lòng) mà không lấy hằng số của họ.

          ⚠ Dưới `lg` mọi lớp trên đều tắt ⇒ hai khối xếp chồng dọc, chiều cao tự nhiên, y như cũ.
          ⚠ `PortalNav` là `sticky z-40` nên vẫn phủ lên cột tin đang cuộn — đã kiểm, không cần
            thêm z-index nào. */}
      <section aria-label="Ảnh hoạt động và tin tức" className="mt-5">
        <div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9">
          {/* Không có mục menu cho nhánh tin ⇒ không có cột tin ⇒ slider chiếm cả 12 cột.
              Đây là chính sách "bố cục trang chủ LÀ cây menu" ở dạng bố cục, không phải một
              nhánh dự phòng: bỏ mục menu là cố ý bỏ khối. */}
          <div className={tieuDeTin ? 'lg:col-span-8' : 'lg:col-span-12'}>
            <HomeBannerSlider
              banners={(banners ?? []).slice(0, soAnhSlider)}
              intervalSeconds={nhipSlider}
              autoplay={docBool(config?.['site.slider.autoplay'], true)}
              showArrows={docBool(config?.['site.slider.show-arrows'], true)}
              showDots={docBool(config?.['site.slider.show-dots'], true)}
            />
          </div>
          {tieuDeTin ? (
            <div className="lg:relative lg:col-span-4">
              {/* Khối này THAY cả `HomeHotNews` lẫn `HomeLatestNewsFeed`: cùng một nguồn bài mà chia
                  hai chỗ, hai kiểu trình bày, người đọc phải quét hai lần. */}
              <div className="lg:absolute lg:inset-0">
                <HomeNewsColumn
                  articles={allArticles}
                  soBai={soBaiTinTuc}
                  tieuDe={tieuDeTin}
                  categorySlug={danhMucTin}
                />
              </div>
            </div>
          ) : null}
        </div>
      </section>

      <HomeCategoryNews blocks={baiTheoChuyenMuc} />

      {/* ═════════ NHÓM 2 · ĐIỀU HÀNH & SỐ LIỆU ═════════ */}
      {/* ⚠ Công tắc bọc CẢ nhãn nhóm lẫn hai khối. Tắt mà còn trơ một dải nhãn "Điều hành & số
          liệu công trình" không có gì bên dưới là nửa vòng đọc–ghi ở dạng nhìn thấy được: quản
          trị viên tắt khối, cổng vẫn tuyên bố có khối ấy. */}
      {hienDieuHanh ? (
        <>
          <GroupLabel>Điều hành &amp; số liệu công trình</GroupLabel>
          <div className="mt-5 space-y-6">
            <WaterLevelBlock hotline={hotline} refreshSeconds={nhipLamMoi} updatedAt={serverTime} />
            <OperationsBlock
              refreshSeconds={nhipLamMoi}
              updatedAt={serverTime}
              rows={tinhHinhVanHanh ?? []}
            />
          </div>
        </>
      ) : null}
      {/* ⛔ BẢN ĐỒ HỆ THỐNG CÔNG TRÌNH ĐÃ RỜI TRANG CHỦ (yêu cầu QuanTran 01/09).

          Nó nay nằm ở `/quan-ly-van-hanh/danh-muc-cong-trinh`, cùng trang với danh sách công
          trình mà nó vẽ điểm — và cùng chỗ với mục "Bản đồ hệ thống" (CR-29) vốn đang nói
          *chưa được đăng* trong khi trang chủ hiện đúng bản đồ ấy. Hai chỗ trả lời khác nhau về
          cùng một câu hỏi là thứ quy tắc 14 cấm.

          ⭐ `getConstructionCatalog()` GỠ LUÔN khỏi `Promise.all` ở trên: bản đồ là nơi đọc nó
             duy nhất trên trang này. Lượt viết đầu của chú thích này khẳng định "`OperationsBlock`
             đọc nó" — SAI, và `tsc` bắt được ngay vì biến thành ra không ai dùng. Giữ một lượt
             gọi API không ai đọc là quy tắc 15 ở dạng đắt nhất của trang chủ: nó nằm trong
             `Promise.all`, nên nó kéo dài TTFB của mọi lượt tải (NFR-02, DOD1.17). */}

      {/* ═════════ NHÓM 3 · VĂN BẢN & TIẾP NHẬN Ý KIẾN ═════════ */}
      <GroupLabel>Văn bản &amp; tiếp nhận ý kiến</GroupLabel>
      <PublishedDocumentsSection
        documents={documents?.content ?? []}
        categorySlug={danhMucVanBan}
        docSystemUrl={config?.['site.external.doc-system-url'] ?? ''}
        nhomCon={nhomVanBan}
      />
      {/* ⛔ BIỂU MẪU GỬI Ý KIẾN ĐÃ RỜI KHỎI TRANG CHỦ (yêu cầu Công ty 29/08, lượt hai).

          Trang `/lien-he` đã có đúng biểu mẫu ấy, cùng một `POST /public/contacts`, cùng một bộ
          validate. Giữ hai bản là hai nơi phải nhớ sửa khi CN-01.4 làm tiếp (reCAPTCHA v3, email
          xác nhận, phân loại ý kiến — nợ T26.24), và người sửa một bên sẽ không biết bên kia tồn
          tại (luật 14). Lối vào vẫn có ở đúng chỗ người đọc tìm: mục "Liên hệ" trên thanh điều
          hướng, dải "Gửi phản ánh kiến nghị" ở chân trang, và nút của khối Văn bản ngay trên đây.

          ⚠ `HomeContactBlock.tsx` XOÁ HẲN, không để lại làm component mồ côi: trang chủ là nơi
            gọi DUY NHẤT của nó (trang `/lien-he` dựng thẳng từ `ContactForm`). Một component
            không ai gọi là thứ lượt rà sau sẽ đọc như "còn dùng ở đâu đó" — quy tắc 15. Biểu
            mẫu thì không mất đi đâu: `ContactForm` vẫn là một, vẫn dùng ở `/lien-he`. */}

      {/* ═════════ NHÓM 4 · TỔ CHỨC & ĐƠN VỊ ═════════ */}
      <GroupLabel>Tổ chức &amp; đơn vị trực thuộc</GroupLabel>
      <AffiliatedUnitsLinks
        subsidiaries={subsidiaries ?? []}
        portalLinks={portalLinks ?? []}
        hienNhan={hienNhanLienKet}
      />

      {/* ═════════ NHÓM 5 · TRUYỀN THÔNG ═════════ */}
      <GroupLabel>Truyền thông &amp; chuyên mục</GroupLabel>
      {/* ⚠ Ba props của khối này TRƯỚC ĐÂY không nơi gọi nào truyền — `<HomeMediaGallery />` trần,
          nên `videoId` luôn `undefined` và khối rỗng vĩnh viễn ở mọi môi trường (quy tắc 15 ở dạng
          React). Khoá rỗng ⇒ mảng rỗng ⇒ khối nói thẳng là chưa có — không có bộ ảnh dự phòng. */}
      <HomeMediaGallery
        videoId={config?.['site.home.video-id'] || undefined}
        videoTitle={config?.['site.home.video-title'] || undefined}
        photos={(photos ?? []).map((a) => ({
          id: a.publicId,
          title: a.title,
          imageUrl: fileUrl(a.publicId) ?? '',
        }))}
        intervalSeconds={nhipSlider}
        autoplay={docBool(config?.['site.slider.autoplay'], true)}
        showArrows={docBool(config?.['site.slider.show-arrows'], true)}
        showDots={docBool(config?.['site.slider.show-dots'], true)}
      />
      <CategoryServicesGrid menuTree={menuTree} />
    </div>
  );
}
