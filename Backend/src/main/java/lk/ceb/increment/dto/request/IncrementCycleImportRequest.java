package lk.ceb.increment.dto.request;

import lk.ceb.increment.enums.IncrementType;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Used by the Excel import script to seed one increment cycle row per employee.
 * Maps to one data row from the "ALL this one ok" sheet.
 */
@Getter @Setter
public class IncrementCycleImportRequest {

    private Long employeeId;

    /** Salary type code e.g. "EL1", "EN4" — matched against salary_types table */
    private String salaryTypeCode;

    private Integer cycleYear;

    /** Actual anniversary increment date */
    private LocalDate incrementDate;

    private BigDecimal previousSalary;
    private Integer previousStep;
    private BigDecimal incrementAmount;
    private BigDecimal newSalary;
    private Integer newStep;

    private Boolean isSurplus = false;

    /** e.g. "I වේතනාධිකය", "Serpuls I" */
    private String surplusLabel;

    private Boolean isCertificateYear = true;

    private IncrementType incrementType;

    private String evaluationScore;

    private Boolean suspended = false;
    private Boolean hasPraise = false;
    private Boolean hasPunishment = false;

    private LocalDate periodFrom;
    private LocalDate periodTo;

    /** Clerk name from the sheet (e.g. "අනුරංජන") */
    private String clerkName;

    /** Reference number string from column 30 (e.g. "2025-08") */
    private String referenceNumber;
}
