package lk.ceb.increment.dto.request;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/**
 * Used by the Excel import script.
 * Maps directly to the columns in the "ALL this one ok" sheet.
 * All fields nullable — import is best-effort, missing fields are skipped.
 */
@Getter @Setter
public class EmployeeImportRequest {

    // Identity
    private String epfNo;
    private String nameEnglish;
    private String nameSinhala;
    private String designationEnglish;
    private String designationSinhala;

    // Dates
    private LocalDate incrementDate;
    private LocalDate permanentDate;

    // Location
    private String unit;

    // Contact
    private String phoneNumber;

    private Boolean isActive = true;
}
