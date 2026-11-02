package lk.ceb.increment.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
public class EmployeeUpdateRequest {
    private String nameEnglish;
    private String nameSinhala;
    private String designationEnglish;
    private String designationSinhala;
    private LocalDate incrementDate;
    private LocalDate permanentDate;
    private String unit;
    private String phoneNumber;
    private Boolean isActive;
}
