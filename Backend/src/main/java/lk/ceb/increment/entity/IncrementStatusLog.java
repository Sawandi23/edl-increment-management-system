package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lk.ceb.increment.enums.IncrementStatus;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Immutable audit log — one row per status change on an IncrementCycle.
 * Never updated, only inserted.
 */
@Entity
@Table(name = "increment_status_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IncrementStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "increment_cycle_id", nullable = false)
    private IncrementCycle incrementCycle;

    @Enumerated(EnumType.STRING)
    private IncrementStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncrementStatus newStatus;

    /** Who made the change (login username) */
    @Column(nullable = false, length = 100)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    /** Optional note — required for MANUAL_OVERRIDE entries */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}
