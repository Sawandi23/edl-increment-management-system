package lk.ceb.increment.dto.response;

import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.enums.IncrementType;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class IncrementCycleResponse {
    private Long id;

    // Employee summary fields (flattened, no nested entity)
    private Long employeeId;
    private String epfNo;
    private String employeeNameEnglish;
    private String employeeNameSinhala;
    private String designationEnglish;
    private String unit;

    private String salaryTypeCode;
    private String salaryScale;
    private Integer cycleYear;
    private LocalDate incrementDate;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    private Double previousSalary;
    private Integer previousStep;
    private Double incrementAmount;
    private Double newSalary;
    private Integer newStep;

    private Boolean isSurplus;
    private String surplusLabel;
    private Boolean isCertificateYear;
    private IncrementType incrementType;

    private String evaluationScore;
    private Boolean suspended;
    private Boolean hasPraise;
    private Boolean hasPunishment;

    private String serviceTotal;
    private String servicePost;
    private String serviceGrade;

    private IncrementStatus status;
    private String referenceNumber;

    private LocalDateTime apFormGeneratedAt;
    private LocalDateTime apFormSentAt;
    private LocalDateTime certificateGeneratedAt;
    private LocalDateTime paidAt;

    private Boolean isManualOverride;
    private String overrideNotes;
}
