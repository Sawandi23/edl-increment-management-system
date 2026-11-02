package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeCreateRequest {

    @NotBlank
    @Pattern(regexp = "^[0-9]{1,20}$", message = "EPF number must contain digits only")
    private String epfNo;

    @NotBlank
    @Size(max = 150)
    private String nameEnglish;

    @NotBlank
    @Size(max = 150)
    private String nameSinhala;

    @NotBlank
    @Size(max = 150)
    private String designationEnglish;

    @NotBlank
    @Size(max = 150)
    private String designationSinhala;

    @NotNull
    private LocalDate incrementDate;

    @NotNull
    private Integer currentStep;

    private LocalDate permanentDate;

    @NotBlank
    @Size(max = 20)
    private String salaryTypeCode;

    @NotBlank
    @Size(max = 100)
    private String unit;

    @Pattern(regexp = "^(?:[0-9]{9}|[0-9]{10})$", message = "Phone number must be 9 or 10 digits")
    private String phoneNumber;

    private Boolean isActive = true;
}