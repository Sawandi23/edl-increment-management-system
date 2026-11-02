package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.enums.IncrementType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One row = one annual increment cycle for one employee.
 *
 * This is the core table of the system.
 * Every action (form generation, status change, SMS, override) is
 * tracked against a specific IncrementCycle row.
 *
 * Salary source of truth:
 *   The certificate print event (CERTIFICATE_ISSUED) is what actually
 *   changes an employee's salary — not the AP form.
 *   For surplus-stage employees, the AP form is issued every year but
 *   the certificate (and salary change) only happens every 2nd year.
 *
 * Manual override:
 *   Any calculated field can be overridden by HR admin.
 *   isManualOverride = true and overrideNotes is required when this happens.
 *   All overrides are logged in IncrementStatusLog.
 */
@Entity
@Table(name = "increment_cycles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "cycle_year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IncrementCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relations ─────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salary_type_id", nullable = false)
    private SalaryType salaryType;

    @Column(name = "salary_scale", columnDefinition = "TEXT")
    private String salaryScale;

    // ── Cycle identity ────────────────────────────────────────────────────────

    /** Calendar year this cycle belongs to (e.g. 2025) */
    @Column(nullable = false)
    private Integer cycleYear;

    /** Actual anniversary date of the increment (e.g. 2025-08-15) */
    @Column(nullable = false)
    private LocalDate incrementDate;

    // ── Salary calculation ────────────────────────────────────────────────────

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal previousSalary;

    @Column(nullable = false)
    private Integer previousStep;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal incrementAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal newSalary;

    @Column(nullable = false)
    private Integer newStep;

    // ── Surplus stage ─────────────────────────────────────────────────────────

    /** True when employee is past step 15 (surplus stage) */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isSurplus = false;

    /**
     * Surplus label for this cycle — manually entered by HR.
     * e.g. "I වේතනාධිකය", "II වේතනාධිකය"
     * Only populated when isSurplus = true.
     */
    @Column(length = 50)
    private String surplusLabel;

    /**
     * For surplus employees: is this cycle a certificate year (true)
     * or an AP-form-only year (false)?
     * AP-only year = form is generated but salary does NOT change.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isCertificateYear = true;

    // ── Increment type ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncrementType incrementType;

    // ── Appraisal info ────────────────────────────────────────────────────────

    /** Performance evaluation score (entered by HR on AP form) */
    @Column(length = 20)
    private String evaluationScore;

    /** Whether increment was suspended */
    @Builder.Default
    private Boolean suspended = false;

    /** Whether employee has praise/commendation on record */
    @Builder.Default
    private Boolean hasPraise = false;

    /** Whether employee has disciplinary record */
    @Builder.Default
    private Boolean hasPunishment = false;

    // ── AP period ─────────────────────────────────────────────────────────────
    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    // ── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private IncrementStatus status = IncrementStatus.ELIGIBLE;

    // ── Form field values (saved so HR edits persist) ─────────────────────────

    /** Auto-generated reference number for the AP form */
    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 150)
    private String clerkName;

    @Column(length = 150)
    private String hrOfficerName;

    @Column(length = 150)
    private String unitHeadName;

    @Column(length = 150)
    private String dgmName;

    // ── Timestamps ────────────────────────────────────────────────────────────

    private LocalDateTime apFormGeneratedAt;
    private LocalDateTime apFormSentAt;
    private LocalDateTime certificateGeneratedAt;
    private LocalDateTime paidAt;

    // ── Manual override ───────────────────────────────────────────────────────

    @Builder.Default
    @Column(nullable = false)
    private Boolean isManualOverride = false;

    @Column(columnDefinition = "TEXT")
    private String overrideNotes;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String createdBy;

    // ── Relations ─────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "incrementCycle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("changedAt DESC")
    private List<IncrementStatusLog> statusLog;

    @OneToMany(mappedBy = "incrementCycle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SmsLog> smsLogs;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
