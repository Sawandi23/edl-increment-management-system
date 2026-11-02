package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SalaryScaleVersionResponse {
    private Long id;
    private String salaryTypeCode;
    private String scaleString;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime createdAt;
    private String createdBy;
}