package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Key-value store for system-wide settings.
 * Seeded with defaults on first boot.
 *
 * Keys used by form generation:
 *   DGM_NAME_SI      — DGM name in Sinhala (for AP forms)
 *   DGM_NAME_EN      — DGM name in English (for certificates)
 *   HR_OFFICER_SI    — HR officer name in Sinhala
 *   HR_OFFICER_EN    — HR officer name in English
 */
@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSettings {

    @Id
    @Column(nullable = false, unique = true, length = 60)
    private String settingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Column(length = 200)
    private String description;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
