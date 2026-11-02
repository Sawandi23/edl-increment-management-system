package lk.ceb.increment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_scale_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"salary_type_code", "effective_from"}),
        indexes = {
                @Index(name = "idx_salary_scale_versions_code_from", columnList = "salary_type_code,effective_from"),
                @Index(name = "idx_salary_scale_versions_code_to", columnList = "salary_type_code,effective_to")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryScaleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salary_type_code", nullable = false, length = 20)
    private String salaryTypeCode;

    @Column(name = "scale_string", nullable = false, columnDefinition = "TEXT")
    private String scaleString;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}