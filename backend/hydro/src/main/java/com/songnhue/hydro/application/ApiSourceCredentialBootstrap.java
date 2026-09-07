package com.songnhue.hydro.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.spi.SecurityEventPort;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.infra.ApiSourceRepository;
import com.songnhue.hydro.infra.HydroApiProperties;

/**
 * Mồi mã số nguồn {@code BHH40} từ biến môi trường vào CSDL — <b>một chiều, một lần</b>.
 *
 * <h2>Vì sao cần một bước mồi thay vì bắt người vận hành nhập tay</h2>
 *
 * <p>Nhà của mã số là {@code api_sources.credential} (§4.7). Nhưng migration <b>không mã hoá
 * được</b>: khoá AES nằm ngoài CSDL, đúng như thiết kế. Nếu không có bước này thì mọi lần dựng môi
 * trường mới — staging, rehearse, và cả container test — đều phải có người mở giao diện dán mã số
 * trước khi poller chạy được lượt đầu tiên.
 *
 * <h2>⚠ Ba ràng buộc làm nó không thành nguồn sự thật thứ hai</h2>
 *
 * <ol>
 *   <li><b>Chỉ ghi khi cột đang rỗng.</b> Đặt mã số trên UI rồi thì biến môi trường có hay không
 *       cũng không đổi gì — đúng như một giá trị mồi phải thế. Không có nhánh nào ghi đè.
 *   <li><b>Bỏ qua giá trị chỗ điền.</b> {@code deploy/env/local.env} có
 *       {@code HYDRO_API_KEY=REPLACE_ME_MASO;}. Mồi giá trị đó vào thì cột khác NULL ⇒ hệ thống báo
 *       "đã cấu hình", poller gọi bằng một mã sai và nhận {@code not.working} — mất hẳn trạng thái
 *       "chưa cấu hình", vốn là thứ duy nhất chỉ đúng chỗ cần làm.
 *   <li><b>Không có mã số thì nói to, không đổ.</b> Một dòng WARN nêu đúng màn hình cần vào.
 *       Fail-fast của MOD-03 nằm ở lượt polling (từ chối chạy, ghi {@code sync_logs}), không phải ở
 *       lúc khởi động — thiếu mã số của một tính năng Phase 2 không được kéo cả hệ thống đã nghiệm
 *       thu Phase 1 xuống.
 * </ol>
 *
 * <p>⛔ Không dòng log nào ở đây in giá trị mã số, kể cả một phần của nó.
 */
@Component
public class ApiSourceCredentialBootstrap {

    /** Mã nguồn được mồi. Trùng {@code V202608311049} — nguồn duy nhất có biến môi trường đi kèm. */
    static final String MA_NGUON = "BHH40";

    private static final Logger log = LoggerFactory.getLogger(ApiSourceCredentialBootstrap.class);

    private final ApiSourceRepository sources;
    private final CryptoService crypto;
    private final HydroApiProperties properties;
    private final SecurityEventPort securityEvents;

    public ApiSourceCredentialBootstrap(
            ApiSourceRepository sources,
            CryptoService crypto,
            HydroApiProperties properties,
            SecurityEventPort securityEvents) {
        this.sources = sources;
        this.crypto = crypto;
        this.properties = properties;
        this.securityEvents = securityEvents;
    }

    /**
     * Chạy sau khi context lên hẳn.
     *
     * <p>{@link ApplicationReadyEvent} chứ không phải {@code @PostConstruct}: cần Flyway đã áp xong
     * migration seed, và cần {@code CryptoService} đã sẵn sàng.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void moiMaSo() {
        ApiSource nguon = sources.findByCodeAndDeletedAtIsNull(MA_NGUON).orElse(null);
        if (nguon == null) {
            // Không phải lỗi: một môi trường có thể đã xoá mềm nguồn mặc định.
            log.info("Không có nguồn {} — bỏ qua bước mồi mã số thuỷ văn", MA_NGUON);
            return;
        }
        if (nguon.isCredentialDaCauHinh()) {
            return;
        }
        if (!properties.coMaSoDeMoi()) {
            log.warn(
                    "Nguồn {} CHƯA CÓ MÃ SỐ — lượt polling thuỷ văn sẽ không chạy. "
                            + "Đặt mã số ở màn hình Quản trị › Nguồn dữ liệu, hoặc mồi bằng biến HYDRO_API_KEY. "
                            + "⚠ Nhớ giữ nguyên dấu ';' ở cuối mã số.",
                    MA_NGUON);
            return;
        }

        nguon.datCredential(crypto.encrypt(properties.getKey()));
        sources.save(nguon);
        securityEvents.externalCredentialChanged(MA_NGUON, "DAT_LAN_DAU");
        log.info("Đã mồi mã số cho nguồn {} từ biến môi trường (lần đầu)", MA_NGUON);
    }
}
