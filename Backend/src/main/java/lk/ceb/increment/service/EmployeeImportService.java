package lk.ceb.increment.service;

import lk.ceb.increment.dto.request.EmployeeImportRequest;
import lk.ceb.increment.dto.request.IncrementCycleImportRequest;
import lk.ceb.increment.dto.response.EmployeeResponse;
import lk.ceb.increment.dto.response.IncrementCycleResponse;
import lk.ceb.increment.entity.*;
import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.enums.IncrementType;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles importing employee data and increment cycles from the HR Excel sheet.
 *
 * Strategy:
 *   - Employee: upsert by EPF no (create if not exists, update fields if exists)
 *   - SalaryHistory: create initial row if none exists for this employee
 *   - IncrementCycle: upsert by (employee_id, cycle_year) — one row per year per employee
 *
 * This means the import is idempotent — running it twice won't create duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeImportService {

    private final EmployeeRepository             employeeRepo;
    private final EmployeeSalaryHistoryRepository historyRepo;
    private final SalaryTypeRepository           salaryTypeRepo;
    private final SalaryScaleVersionService      salaryScaleVersionService;
    private final IncrementCycleRepository       cycleRepo;
    private final IncrementStatusLogRepository   statusLogRepo;
    private final IncrementCalculationEngine     calcEngine;
    private final EmployeeService                employeeService;

    // ── Employee upsert ───────────────────────────────────────────────────────

    /**
     * Create or update an employee by EPF no.
     * If the employee already exists, updates all non-null fields.
     * Returns the saved employee response.
     */
    @Transactional
    public EmployeeResponse importEmployee(EmployeeImportRequest req) {
        if (req.getEpfNo() == null || req.getEpfNo().isBlank()) {
            throw new IllegalArgumentException("EPF number is required for import");
        }

        Optional<Employee> existing = employeeRepo.findByEpfNo(req.getEpfNo().trim());

        Employee employee;
        boolean isNew = existing.isEmpty();

        if (isNew) {
            employee = Employee.builder()
                    .epfNo(req.getEpfNo().trim())
                    .nameEnglish(nullSafe(req.getNameEnglish(), "Unknown"))
                    .nameSinhala(nullSafe(req.getNameSinhala(), ""))
                    .designationEnglish(nullSafe(req.getDesignationEnglish(), ""))
                    .designationSinhala(nullSafe(req.getDesignationSinhala(), ""))
                    .incrementDate(req.getIncrementDate())
                    .permanentDate(req.getPermanentDate())
                    .unit(nullSafe(req.getUnit(), ""))
                    .phoneNumber(req.getPhoneNumber())
                    .isActive(Boolean.TRUE.equals(req.getIsActive()))
                    .build();
            log.debug("Creating new employee EPF {}", req.getEpfNo());
        } else {
            employee = existing.get();
            // Update fields — only overwrite if incoming value is non-null/non-blank
            if (isNotBlank(req.getNameEnglish()))       employee.setNameEnglish(req.getNameEnglish());
            if (isNotBlank(req.getNameSinhala()))       employee.setNameSinhala(req.getNameSinhala());
            if (isNotBlank(req.getDesignationEnglish())) employee.setDesignationEnglish(req.getDesignationEnglish());
            if (isNotBlank(req.getDesignationSinhala())) employee.setDesignationSinhala(req.getDesignationSinhala());
            if (req.getIncrementDate() != null)         employee.setIncrementDate(req.getIncrementDate());
            if (req.getPermanentDate() != null)         employee.setPermanentDate(req.getPermanentDate());
            if (isNotBlank(req.getUnit()))              employee.setUnit(req.getUnit());
            if (isNotBlank(req.getPhoneNumber()))       employee.setPhoneNumber(req.getPhoneNumber());
            log.debug("Updating existing employee EPF {}", req.getEpfNo());
        }

        employee = employeeRepo.save(employee);

        log.info("{} employee EPF {} — {}", isNew ? "Created" : "Updated",
                employee.getEpfNo(), employee.getNameEnglish());

        return employeeService.findByEpfNo(employee.getEpfNo());
    }

    // ── Increment cycle upsert ─────────────────────────────────────────────────

    /**
     * Seed one increment cycle row from the Excel sheet.
     * Upserts by (employee_id, cycle_year) — if a cycle for this year already
     * exists, it updates only if status is still ELIGIBLE (i.e. not yet processed).
     */
    @Transactional
    public IncrementCycleResponse importCycle(Long employeeId, IncrementCycleImportRequest req) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Resolve salary type
        SalaryType salaryType = null;
        if (isNotBlank(req.getSalaryTypeCode())) {
            salaryType = salaryTypeRepo.findByCode(req.getSalaryTypeCode().trim())
                    .orElse(null);
            if (salaryType == null) {
                log.warn("Salary type '{}' not found for EPF {} — cycle will have no type",
                        req.getSalaryTypeCode(), employee.getEpfNo());
            }
        }

        // ── Seed salary history if none exists ────────────────────────────────
        Optional<EmployeeSalaryHistory> activeHistory =
                historyRepo.findActiveByEmployeeId(employeeId);

        if (activeHistory.isEmpty() && salaryType != null && req.getPreviousSalary() != null) {
            BigDecimal prevSalary = req.getPreviousSalary();
            int prevStep = req.getPreviousStep() != null ? req.getPreviousStep() : 0;

            EmployeeSalaryHistory history = EmployeeSalaryHistory.builder()
                    .employee(employee)
                    .salaryType(salaryType)
                    .currentStep(prevStep)
                    .currentSalary(prevSalary)
                        .effectiveFrom(employee.getIncrementDate() != null
                            ? employee.getIncrementDate()
                            : LocalDate.of(2000, 1, 1))
                    .hasPromotion(false)
                    .changedBy("IMPORT")
                    .build();

            historyRepo.save(history);
            log.debug("Created salary history for EPF {} — type {} step {}",
                    employee.getEpfNo(), req.getSalaryTypeCode(), prevStep);
        }

        // ── Upsert increment cycle ────────────────────────────────────────────
       int year = Math.max(LocalDate.now().getYear(), employee.getIncrementDate().getYear());

        Optional<IncrementCycle> existingCycle =
                cycleRepo.findByEmployeeIdAndCycleYear(employeeId, year);

        IncrementCycle cycle;
        if (existingCycle.isPresent()) {
            cycle = existingCycle.get();
            // Only update if not yet processed — don't overwrite real workflow data
            if (cycle.getStatus() != IncrementStatus.ELIGIBLE) {
                log.debug("Skipping cycle update for EPF {} year {} — status is {}",
                        employee.getEpfNo(), year, cycle.getStatus());
                return buildCycleResponse(cycle);
            }
            updateCycleFromImport(cycle, req, salaryType);
            log.debug("Updated cycle for EPF {} year {}", employee.getEpfNo(), year);
        } else {
            cycle = buildNewCycle(employee, salaryType, req, year);
            log.debug("Created cycle for EPF {} year {}", employee.getEpfNo(), year);
        }

        cycle = cycleRepo.save(cycle);

        // Seed status log entry for audit trail
        IncrementStatusLog logEntry = IncrementStatusLog.builder()
                .incrementCycle(cycle)
                .oldStatus(null)
                .newStatus(IncrementStatus.ELIGIBLE)
                .changedBy("IMPORT")
                .notes("Seeded from HR Excel import")
                .build();
        statusLogRepo.save(logEntry);

        return buildCycleResponse(cycle);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IncrementCycle buildNewCycle(Employee employee, SalaryType salaryType,
                                          IncrementCycleImportRequest req, int year) {
        return IncrementCycle.builder()
                .employee(employee)
                .salaryType(salaryType)
            .salaryScale(resolveSalaryScale(req.getSalaryTypeCode(), req.getIncrementDate()))
                .cycleYear(year)
                .incrementDate(calcEngine.anniversaryDateForYear(req.getIncrementDate(), year))
                .previousSalary(nullSafe(req.getPreviousSalary(), BigDecimal.ZERO))
                .previousStep(nullSafe(req.getPreviousStep(), 0))
                .incrementAmount(nullSafe(req.getIncrementAmount(), BigDecimal.ZERO))
                .newSalary(nullSafe(req.getNewSalary(), BigDecimal.ZERO))
                .newStep(nullSafe(req.getNewStep(), 0))
                .isSurplus(Boolean.TRUE.equals(req.getIsSurplus()))
                .surplusLabel(req.getSurplusLabel())
                .isCertificateYear(Boolean.TRUE.equals(req.getIsCertificateYear()))
                .incrementType(req.getIncrementType() != null ? req.getIncrementType() : IncrementType.NORMAL)
                .evaluationScore(req.getEvaluationScore())
                .suspended(Boolean.TRUE.equals(req.getSuspended()))
                .hasPraise(Boolean.TRUE.equals(req.getHasPraise()))
                .hasPunishment(Boolean.TRUE.equals(req.getHasPunishment()))
                .periodFrom(req.getPeriodFrom())
                .periodTo(req.getPeriodTo())
                .status(IncrementStatus.ELIGIBLE)
                .clerkName(req.getClerkName())
                .referenceNumber(req.getReferenceNumber())
                .isManualOverride(false)
                .createdBy("IMPORT")
                .build();
    }

    private void updateCycleFromImport(IncrementCycle cycle, IncrementCycleImportRequest req,
                                        SalaryType salaryType) {
        if (salaryType != null)             cycle.setSalaryType(salaryType);
        if (req.getIncrementDate() != null) cycle.setIncrementDate(req.getIncrementDate());
        if (req.getPreviousSalary() != null) cycle.setPreviousSalary(req.getPreviousSalary());
        if (req.getPreviousStep() != null)  cycle.setPreviousStep(req.getPreviousStep());
        if (req.getIncrementAmount() != null) cycle.setIncrementAmount(req.getIncrementAmount());
        if (req.getNewSalary() != null)     cycle.setNewSalary(req.getNewSalary());
        if (req.getNewStep() != null)       cycle.setNewStep(req.getNewStep());
        if (req.getIsSurplus() != null)     cycle.setIsSurplus(req.getIsSurplus());
        if (isNotBlank(req.getSurplusLabel())) cycle.setSurplusLabel(req.getSurplusLabel());
        if (req.getIncrementType() != null) cycle.setIncrementType(req.getIncrementType());
        if (isNotBlank(req.getEvaluationScore())) cycle.setEvaluationScore(req.getEvaluationScore());
        if (req.getSuspended() != null)     cycle.setSuspended(req.getSuspended());
        if (req.getHasPraise() != null)     cycle.setHasPraise(req.getHasPraise());
        if (req.getHasPunishment() != null) cycle.setHasPunishment(req.getHasPunishment());
        if (isNotBlank(req.getClerkName())) cycle.setClerkName(req.getClerkName());
        if (isNotBlank(req.getReferenceNumber())) cycle.setReferenceNumber(req.getReferenceNumber());

        String salaryTypeCode = salaryType != null
                ? salaryType.getCode()
                : cycle.getSalaryType() != null ? cycle.getSalaryType().getCode() : null;
        LocalDate effectiveDate = req.getIncrementDate() != null ? req.getIncrementDate() : cycle.getIncrementDate();
        if (isNotBlank(salaryTypeCode) && effectiveDate != null) {
            cycle.setSalaryScale(resolveSalaryScale(salaryTypeCode, effectiveDate));
        }
    }

    private IncrementCycleResponse buildCycleResponse(IncrementCycle c) {
        return IncrementCycleResponse.builder()
                .id(c.getId())
                .employeeId(c.getEmployee().getId())
                .epfNo(c.getEmployee().getEpfNo())
                .employeeNameEnglish(c.getEmployee().getNameEnglish())
                .employeeNameSinhala(c.getEmployee().getNameSinhala())
                .designationEnglish(c.getEmployee().getDesignationEnglish())
                .unit(c.getEmployee().getUnit())
                .salaryTypeCode(c.getSalaryType() != null ? c.getSalaryType().getCode() : null)
                .salaryScale(c.getSalaryScale())
                .cycleYear(c.getCycleYear())
                .incrementDate(c.getIncrementDate())
                .previousSalary(c.getPreviousSalary() != null ? c.getPreviousSalary().doubleValue() : null)
                .previousStep(c.getPreviousStep())
                .incrementAmount(c.getIncrementAmount() != null ? c.getIncrementAmount().doubleValue() : null)
                .newSalary(c.getNewSalary() != null ? c.getNewSalary().doubleValue() : null)
                .newStep(c.getNewStep())
                .isSurplus(c.getIsSurplus())
                .surplusLabel(c.getSurplusLabel())
                .isCertificateYear(c.getIsCertificateYear())
                .incrementType(c.getIncrementType())
                .evaluationScore(c.getEvaluationScore())
                .suspended(c.getSuspended())
                .hasPraise(c.getHasPraise())
                .hasPunishment(c.getHasPunishment())
                .periodFrom(c.getPeriodFrom())
                .periodTo(c.getPeriodTo())
                .status(c.getStatus())
                .referenceNumber(c.getReferenceNumber())
                .isManualOverride(c.getIsManualOverride())
                .build();
    }

    // ── Null-safe utilities ───────────────────────────────────────────────────

    private <T> T nullSafe(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String resolveSalaryScale(String salaryTypeCode, LocalDate onDate) {
        if (!isNotBlank(salaryTypeCode) || onDate == null) {
            return null;
        }
        return salaryScaleVersionService.resolveScaleString(salaryTypeCode.trim(), onDate);
    }
}
