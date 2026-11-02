package lk.ceb.increment.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@Builder
public class EmployeeResponse {
    private Long id;
    private String epfNo;
    private String nameEnglish;
    private String nameSinhala;
    private String designationEnglish;
    private String designationSinhala;
    private LocalDate incrementDate;
    private LocalDate permanentDate;
    private String unit;
    private String phoneNumber;
    private Boolean isActive;

    // Current salary snapshot (from active EmployeeSalaryHistory row)
    private String currentSalaryTypeCode;
    private String currentSalaryScale;
    private Integer currentStep;
    private Double currentSalary;
    private Boolean hasPromotionFlag;

    private Integer totalServiceYears;
}
