package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
public class DashboardFilterRequest {
    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    /** Optional — null means "all statuses" */
    private String status;

    /** Optional — null means "all salary types" */
    private String salaryTypeCode;
}
