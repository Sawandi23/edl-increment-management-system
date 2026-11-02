package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lk.ceb.increment.enums.SmsStatus;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sms_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "increment_cycle_id")
    private IncrementCycle incrementCycle;

    @Column(nullable = false, length = 20)
    private String toPhone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsStatus status;

    /** Raw response from SLT Mobitel API */
    @Column(columnDefinition = "TEXT")
    private String providerResponse;

    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = LocalDateTime.now();
    }
}
