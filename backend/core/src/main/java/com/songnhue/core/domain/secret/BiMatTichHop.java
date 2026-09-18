package com.songnhue.core.domain.secret;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.spi.LoaiBiMat;

/**
 * Một bí mật tích hợp đã mã hoá — T61.44. Bảng {@code integration_secrets}.
 *
 * <p>⛔ {@code ciphertext} loại khỏi nhật ký kiểm toán: bản mã theo bản sao lưu ra khỏi phòng máy, và nhật ký lưu 5
 * năm (AuditRedactionRuleTest luật 3 ép điều này từ CHECK dạng bản mã của migration).
 */
@Entity
@Table(name = "integration_secrets")
@Audited(
        module = "adm",
        entityType = "Bí mật tích hợp",
        excludeFields = {"ciphertext"})
public class BiMatTichHop extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "secret_code", nullable = false, updatable = false, length = 80)
    private LoaiBiMat loai;

    @Column(name = "ciphertext", nullable = false)
    private String ciphertext;

    protected BiMatTichHop() {}

    public BiMatTichHop(LoaiBiMat loai, String ciphertext) {
        this.loai = loai;
        this.ciphertext = ciphertext;
    }

    public LoaiBiMat getLoai() {
        return loai;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void doiBanMa(String ciphertext) {
        this.ciphertext = ciphertext;
    }
}
