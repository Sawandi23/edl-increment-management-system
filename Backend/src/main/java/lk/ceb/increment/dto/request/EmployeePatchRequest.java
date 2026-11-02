package lk.ceb.increment.dto.request;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeePatchRequest {

    @Size(max = 150)
    private String nameEnglish;

    @Size(max = 150)
    private String nameSinhala;

    @Size(max = 150)
    private String designationEnglish;

    @Size(max = 150)
    private String designationSinhala;

    @PastOrPresent
    private LocalDate incrementDate;

    private LocalDate permanentDate;

    @Size(max = 100)
    private String unit;

    @Pattern(regexp = "^(?:[0-9]{9}|[0-9]{10})$", message = "Phone number must be 9 or 10 digits")
    private String phoneNumber;

    private Boolean isActive;
}