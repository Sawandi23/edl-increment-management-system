package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryScaleVersionCreateRequest {
    @NotBlank
    private String salaryTypeCode;

    @NotBlank
    private String scaleString;

    @NotNull
    private LocalDate effectiveFrom;

    @NotBlank
    private String createdBy;
}