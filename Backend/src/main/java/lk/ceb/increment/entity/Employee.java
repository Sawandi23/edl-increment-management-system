package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Master employee record.
 * Imported from the HR Excel sheet provided by HR division.
 *
 * NOTE: salary type is NOT stored here as a flat field —
 * it lives in EmployeeSalaryHistory so mid-career type changes
 * (promotions) are tracked with effective dates and remarks.
 */
@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Column(nullable = false, unique = true, length = 20)
    private String epfNo;

    @Column(nullable = false, length = 150)
    private String nameEnglish;

    @Column(nullable = false, length = 150)
    private String nameSinhala;

    // ── Designation ───────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    private String designationEnglish;

    @Column(nullable = false, length = 150)
    private String designationSinhala;

    // ── Dates ─────────────────────────────────────────────────────────────────

    /** Increment anniversary date used for annual calculations */
    @Column(name = "increment_date", nullable = false)
    private LocalDate incrementDate;

    /** Date of confirmation/permanency */
    private LocalDate permanentDate;

    // ── Location ──────────────────────────────────────────────────────────────

    @Column(length = 100)
    private String unit;

    // ── Contact ───────────────────────────────────────────────────────────────

    @Column(length = 20)
    private String phoneNumber;

    // ── Status ────────────────────────────────────────────────────────────────

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt;

    private LocalDate updatedAt;

    // ── Relations ─────────────────────────────────────────────────────────────

    /** Full time-bound salary type history (current = latest effectiveTo IS NULL) */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("effectiveFrom DESC")
    private List<EmployeeSalaryHistory> salaryHistory;

    /** All increment cycles across all years */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("incrementDate DESC")
    private List<IncrementCycle> incrementCycles;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDate.now();
        this.updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDate.now();
    }
}


