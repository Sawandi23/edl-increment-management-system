package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracks an employee's salary type over time.
 *
 * One row per "period" under a given salary type.
 * Current active period has effectiveTo == null.
 *
 * On promotion:
 *   1. Close current row  (set effectiveTo = promotionDate - 1)
 *   2. Insert new row     (effectiveFrom = promotionDate, effectiveTo = null)
 *   3. Set hasPromotion = true, fill promotionRemark
 *   4. System flags this employee profile with a ⚠ warning
 */
@Entity
@Table(name = "employee_salary_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeSalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salary_type_id", nullable = false)
    private SalaryType salaryType;

    /** Step number within the current salary type (0–15) */
    @Column(nullable = false)
    private Integer currentStep;

    /** Actual salary amount at this step */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentSalary;

    /** When this salary type period began */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** When this period ended (null = still active) */
    private LocalDate effectiveTo;

    // ── Promotion tracking ────────────────────────────────────────────────────

    /** True if this row was created by a mid-career salary type change */
    @Builder.Default
    @Column(nullable = false)
    private Boolean hasPromotion = false;

    /** Free-text remark required when salary type changes mid-career */
    @Column(columnDefinition = "TEXT")
    private String promotionRemark;

    // ── Audit ─────────────────────────────────────────────────────────────────

    /** Login username of whoever made this change */
    @Column(length = 100)
    private String changedBy;

    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}
