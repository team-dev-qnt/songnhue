package com.songnhue.hydro.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.HydroAlertPort;
import com.songnhue.hydro.infra.AlertEventQueryRepository;

/**
 * ⭐⭐ Cài đặt <b>thật</b> của {@link HydroAlertPort} — <b>T33.9</b>, thay
 * {@code DummyHydroAlertService} (đã xoá trong cùng commit).
 *
 * <h2>Cái gì vừa sống lại</h2>
 *
 * <p>{@code ConstructionStatusService.tinh()} có sáu mắt xích. Mắt xích 3 —
 * <i>"công trình có cảnh báo ngưỡng đang mở không"</i> — trả {@code false} <b>ghi cứng</b> từ Phase
 * 1 tới nay. Trạng thái {@code CANH_BAO} vì thế chưa công trình nào chạm tới được, và không ai
 * thấy: bài kiểm của chuỗi ấy <b>mock cổng này</b>, nên nó luôn trả lời đúng thứ bài kiểm dặn (luật
 * 7 — <i>một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai</i>).
 *
 * <h2>⛔⛔ Bean này phải là DUY NHẤT</h2>
 *
 * <p>Để {@code DummyHydroAlertService} còn sống cạnh nó là
 * {@code NoUniqueBeanDefinitionException} lúc dựng context — ứng dụng ⛔ không khởi động được. Nó đã
 * bị xoá, ⛔ không bị {@code @Primary} đè: một bean chết mà vẫn nằm trong context là một bean sẽ
 * được ai đó tiêm nhầm.
 *
 * <h2>⚠ Vì sao {@code readOnly = true} mà vẫn có {@code @Transactional}</h2>
 *
 * <p>Ranh giới giao dịch thuộc tầng application (ArchUnit canh), và lượt gọi thường đến từ
 * {@code ConstructionStatusService} vốn <b>đã</b> ở trong một giao dịch. {@code REQUIRED} mặc định
 * nghĩa là nó tham gia giao dịch ấy — đúng điều cần: mắt xích 3 phải nhìn thấy cùng một ảnh chụp dữ
 * liệu với bốn mắt xích còn lại. Hai ảnh chụp khác nhau trong một phép tính dẫn xuất là đúng hình
 * dạng luật 13.
 */
@Service
public class HydroAlertAdapter implements HydroAlertPort {

    private final AlertEventQueryRepository events;

    public HydroAlertAdapter(AlertEventQueryRepository events) {
        this.events = events;
    }

    /**
     * ⛔ <b>KHÔNG lọc phạm vi đơn vị</b> — quyết định, không phải sơ suất.
     *
     * <p>Xem javadoc của {@code AlertEventQueryRepository#SQL_CONG_TRINH_DANG_CANH_BAO}: kết quả của
     * chuỗi mắt xích được <b>ghi xuống cột {@code operational_status}</b>, nên trộn một câu có lọc
     * vào bốn câu không lọc là để giá trị sai được ghi cho <i>tất cả mọi người</i> tuỳ theo ai bấm
     * F5 sau cùng (§10.35 lỗi 2 — đã trả giá một lần ở đúng hàm ấy).
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveAlert(Long constructionId) {
        return constructionId != null && events.congTrinhDangCanhBao(constructionId);
    }

    /**
     * ⭐ Nửa còn thiếu của {@code maintenance_logs.alert_event_public_id} — <b>T33.4</b>.
     *
     * <p>Cột ấy có từ {@code V202608211028} (21/08), có setter, có trường trong form, và ⛔ <b>chưa
     * bao giờ được đối chiếu với bất cứ thứ gì</b>: một UUID bất kỳ gõ vào đó lưu thành công. Đây
     * đúng hình dạng luật 27 ở chiều ngược — nửa <i>ghi</i> hoàn chỉnh, không có ai kiểm.
     *
     * <p>⛔ Không dùng khoá ngoại xuyên module để chữa (T33.4 · §10.4): {@code operations} không
     * thấy {@code hydro} ở tầng biên dịch, và một {@code REFERENCES} qua ranh giới module là ràng
     * buộc mà không lượt tái tổ chức nào gỡ ra được.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean alertEventExists(UUID alertEventPublicId) {
        return alertEventPublicId != null && events.suKienTonTai(alertEventPublicId);
    }
}
