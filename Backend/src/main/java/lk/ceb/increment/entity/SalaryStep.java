package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Pre-computed salary for each step of a salary type.
 * step 0  = starting salary
 * step 15 = max salary (end of band 2)
 *
 * Generated once when salary types are seeded.
 * Avoids recalculating on every form generation or calc engine call.
 */
@Entity
@Table(name = "salary_steps",
       uniqueConstraints = @UniqueConstraint(columnNames = {"salary_type_id", "step_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salary_type_id", nullable = false)
    private SalaryType salaryType;

    /** 0 = start, 1–5 = band 1, 6–15 = band 2 */
    @Column(nullable = false)
    private Integer stepNumber;

    /** Salary amount at this step */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salaryAmount;

    /** Increment that was applied to reach this step (0 for step 0) */
    @Column(precision = 10, scale = 2)
    private BigDecimal incrementApplied;
}
