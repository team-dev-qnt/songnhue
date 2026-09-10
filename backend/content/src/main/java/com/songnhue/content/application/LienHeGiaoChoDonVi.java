package com.songnhue.content.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.spi.OrgUnitUsagePort;

/**
 * Phần khai của {@code content} cho {@link OrgUnitUsagePort} — CN-04.1.
 *
 * <p>{@code contacts.assigned_org_unit_id} là bước <b>giao việc</b> của quy trình xử lý liên hệ
 * (WS-36). Đơn vị được giao mà giải thể thì phiếu liên hệ ⛔ không còn ai chịu trách nhiệm, và nó
 * biến mất khỏi mọi bộ lọc theo đơn vị — trong khi người dân đã gửi và <b>đang chờ trả lời</b>.
 *
 * <p>⚠ Đây là bên cài <b>ít hiển nhiên nhất</b> trong bốn bên, và chính vì thế nó ở đây: đặc tả
 * chỉ nêu <i>nhân viên / công trình</i>, nên một guard viết theo đặc tả sẽ bỏ sót đúng chỗ này.
 */
@Component
public class LienHeGiaoChoDonVi implements OrgUnitUsagePort {

    private final ContactRepository contacts;

    public LienHeGiaoChoDonVi(ContactRepository contacts) {
        this.contacts = contacts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangThuocDonVi(Long orgUnitId) {
        long so = contacts.countByAssignedOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        return so == 0 ? List.of() : List.of("%d phiếu liên hệ đang giao cho đơn vị".formatted(so));
    }
}
