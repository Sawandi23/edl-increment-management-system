package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/**
 * Used when HR changes an employee's salary type mid-career (promotion).
 * Closes the current EmployeeSalaryHistory row and opens a new one.
 * Remark is mandatory — this is a deliberate design decision so every
 * promotion is traceable.
 */
@Getter @Setter
public class SalaryTypeChangeRequest {

    @NotBlank
    private String newSalaryTypeCode;

    @NotNull
    private Integer newStep;

    @NotNull
    private LocalDate effectiveFrom;

    @NotBlank
    private String remark;

    @NotBlank
    private String changedBy;
}
