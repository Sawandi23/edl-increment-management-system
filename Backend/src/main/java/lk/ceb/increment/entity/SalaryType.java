package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Represents one of the 30 CEB salary types (e.g. ELspl, EL1, EN4 ...).
 *
 * Band structure:
 *   Steps 0 (start) → band1Years steps with band1Increment each
 *   Steps band1Years+1 → band2Years steps with band2Increment each
 *   Total steps = band1Years + band2Years (usually 5 + 10 = 15)
 *
 * For flat-rate types (e.g. EN4, EN5, SN1–SN5, N6):
 *   band2Increment == band1Increment  (same amount for all 15 steps)
 */
@Entity
@Table(name = "salary_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "ELspl", "EL1", "EN4", "N6" */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** Human-readable description */
    @Column(length = 100)
    private String description;

    /** Salary at step 0 (starting salary) */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal startSalary;

    /** Increment amount for steps 1–band1Years */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal band1Increment;

    /** Number of steps in band 1 (usually 5) */
    @Column(nullable = false)
    private Integer band1Years;

    /** Increment amount for steps band1Years+1 to band1Years+band2Years */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal band2Increment;

    /** Number of steps in band 2 (usually 10) */
    @Column(nullable = false)
    private Integer band2Years;

    /** Maximum salary (at step 15) */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal maxSalary;

    /** Scale effective from date (2024-08-01 for current scale) */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** Pre-computed step table (1 row per step 0–15) */
    @OneToMany(mappedBy = "salaryType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SalaryStep> steps;

    /** Full salary scale string for forms e.g. "134,870-5x2,400-10x2,520-172,070/-(EL1)" */
    public String getScaleString() {
        return String.format("%,.0f-5x%,.0f-10x%,.0f-%,.0f/-(%s)",
                startSalary.doubleValue(),
                band1Increment.doubleValue(),
                band2Increment.doubleValue(),
                maxSalary.doubleValue(),
                code);
    }
}
