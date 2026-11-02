package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SalaryHistoryResponse {
    private Long id;
    private String salaryTypeCode;
    private String salaryTypeScaleString;
    private Integer currentStep;
    private Double currentSalary;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean hasPromotion;
    private String promotionRemark;
    private String changedBy;
    private LocalDateTime changedAt;
}
