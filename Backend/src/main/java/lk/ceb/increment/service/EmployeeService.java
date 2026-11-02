package lk.ceb.increment.service;

import lk.ceb.increment.dto.request.EmployeeCreateRequest;
import lk.ceb.increment.dto.request.EmployeePatchRequest;
import lk.ceb.increment.dto.request.EmployeeUpdateRequest;
import lk.ceb.increment.dto.request.SalaryTypeChangeRequest;
import lk.ceb.increment.dto.response.EmployeeResponse;
import lk.ceb.increment.dto.response.SalaryHistoryResponse;
import lk.ceb.increment.entity.Employee;
import lk.ceb.increment.entity.EmployeeSalaryHistory;
import lk.ceb.increment.entity.IncrementCycle;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.exception.DuplicateResourceException;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.EmployeeRepository;
import lk.ceb.increment.repository.EmployeeSalaryHistoryRepository;
import lk.ceb.increment.repository.IncrementCycleRepository;
import lk.ceb.increment.repository.SalaryTypeRepository;
import lk.ceb.increment.service.IncrementCalculationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;
    private final IncrementCycleRepository cycleRepository;
    private final SalaryTypeRepository salaryTypeRepository;
    private final IncrementCalculationEngine calcEngine;
    private final IncrementCycleService incrementCycleService;

        @Transactional(readOnly = true)
        public EmployeeResponse getById(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        return toResponse(employee);
        }

        @Transactional
        public EmployeeResponse createEmployee(EmployeeCreateRequest req) {
        String epfNo = normalize(req.getEpfNo());
        if (employeeRepository.existsByEpfNo(epfNo)) {
            throw new DuplicateResourceException("Employee with EPF No " + epfNo + " already exists");
        }

        SalaryType salaryType = salaryTypeRepository.findByCode(normalize(req.getSalaryTypeCode()))
            .orElseThrow(() -> new ResourceNotFoundException("Salary type not found: " + req.getSalaryTypeCode()));

        Employee employee = Employee.builder()
            .epfNo(epfNo)
            .nameEnglish(normalize(req.getNameEnglish()))
            .nameSinhala(normalize(req.getNameSinhala()))
            .designationEnglish(normalize(req.getDesignationEnglish()))
            .designationSinhala(normalize(req.getDesignationSinhala()))
            .incrementDate(req.getIncrementDate())
            .permanentDate(req.getPermanentDate())
            .unit(normalize(req.getUnit()))
            .phoneNumber(emptyToNull(req.getPhoneNumber()))
            .isActive(req.getIsActive() == null || req.getIsActive())
            .build();

        employee = employeeRepository.save(employee);

        int currentStep = req.getCurrentStep() != null ? req.getCurrentStep() : 0;
        EmployeeSalaryHistory openingHistory = EmployeeSalaryHistory.builder()
            .employee(employee)
            .salaryType(salaryType)
            .currentStep(currentStep)
            .currentSalary(calculateSalary(salaryType, currentStep))
            .effectiveFrom(req.getIncrementDate())
            .hasPromotion(false)
            .changedBy("system")
            .build();
        openingHistory = salaryHistoryRepository.save(openingHistory);

        incrementCycleService.createInitialCycle(employee.getId());

        log.info("Created employee {} ({})", employee.getEpfNo(), employee.getNameEnglish());
        return toResponse(employee);
        }

    // ── Search ────────────────────────────────────────────────────────────────

    public EmployeeResponse findByEpfNo(String epfNo) {
        Employee employee = employeeRepository.findByEpfNo(epfNo)
                .orElseThrow(() -> new ResourceNotFoundException("No employee found with EPF No: " + epfNo));
        return toResponse(employee);
    }

    public List<EmployeeResponse> searchByName(String name) {
        return employeeRepository.searchByName(name).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Update basic details ─────────────────────────────────────────────────

    @Transactional
    public EmployeeResponse updateEmployee(Long employeeId, EmployeeUpdateRequest req) {
        return patchEmployee(employeeId, toPatchRequest(req));
    }

    @Transactional
    public EmployeeResponse patchEmployee(Long employeeId, EmployeePatchRequest req) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        LocalDate previousIncrementDate = employee.getIncrementDate();

        if (req.getNameEnglish() != null) employee.setNameEnglish(normalize(req.getNameEnglish()));
        if (req.getNameSinhala() != null) employee.setNameSinhala(normalize(req.getNameSinhala()));
        if (req.getDesignationEnglish() != null) employee.setDesignationEnglish(normalize(req.getDesignationEnglish()));
        if (req.getDesignationSinhala() != null) employee.setDesignationSinhala(normalize(req.getDesignationSinhala()));
        if (req.getIncrementDate() != null) employee.setIncrementDate(req.getIncrementDate());
        if (req.getPermanentDate() != null) employee.setPermanentDate(req.getPermanentDate());
        if (req.getUnit() != null) employee.setUnit(emptyToNull(req.getUnit()));
        if (req.getPhoneNumber() != null) employee.setPhoneNumber(emptyToNull(req.getPhoneNumber()));
        if (req.getIsActive() != null) employee.setIsActive(req.getIsActive());

        employee = employeeRepository.save(employee);

        if (req.getIncrementDate() != null && !req.getIncrementDate().equals(previousIncrementDate)) {
            incrementCycleService.syncIncrementCycleDates(employee.getId(), req.getIncrementDate());
        }

        log.info("Updated employee {} ({})", employee.getEpfNo(), employee.getNameEnglish());
        return toResponse(employee);
    }

    @Transactional
    public void deleteEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        employeeRepository.delete(employee);
        log.info("Deleted employee {} ({})", employee.getEpfNo(), employee.getNameEnglish());
    }

    // ── Salary type change (promotion) ───────────────────────────────────────

    /**
     * Mid-career salary type change.
     * Closes the current active EmployeeSalaryHistory row and opens a new one.
     * Remark is mandatory — every promotion must be traceable.
     */
    @Transactional
    public SalaryHistoryResponse changeSalaryType(Long employeeId, SalaryTypeChangeRequest req) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        SalaryType newType = salaryTypeRepository.findByCode(req.getNewSalaryTypeCode())
                .orElseThrow(() -> new ResourceNotFoundException("Salary type not found: " + req.getNewSalaryTypeCode()));

        // Close current active history row
        Optional<EmployeeSalaryHistory> current = salaryHistoryRepository.findActiveByEmployeeId(employeeId);
        current.ifPresent(h -> {
            h.setEffectiveTo(req.getEffectiveFrom().minusDays(1));
            salaryHistoryRepository.save(h);
        });

        // Calculate salary at the new step on the new type
        double newSalary = calcEngine.getIncrementForStep(newType, 0).doubleValue(); // start
        // Walk up to the requested step
        java.math.BigDecimal salary = newType.getStartSalary();
        for (int step = 1; step <= req.getNewStep(); step++) {
            salary = salary.add(calcEngine.getIncrementForStep(newType, step));
        }

        EmployeeSalaryHistory newHistory = EmployeeSalaryHistory.builder()
                .employee(employee)
                .salaryType(newType)
                .currentStep(req.getNewStep())
                .currentSalary(salary)
                .effectiveFrom(req.getEffectiveFrom())
                .hasPromotion(true)
                .promotionRemark(req.getRemark())
                .changedBy(req.getChangedBy())
                .build();

        newHistory = salaryHistoryRepository.save(newHistory);
        return toHistoryResponse(newHistory);
    }

    public List<SalaryHistoryResponse> getSalaryHistory(Long employeeId) {
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private EmployeeResponse toResponse(Employee e) {
        Optional<EmployeeSalaryHistory> active = salaryHistoryRepository.findActiveByEmployeeId(e.getId());
        Integer totalServiceYears = e.getPermanentDate() != null && e.getIncrementDate() != null
                ? calcEngine.completedServiceYears(e.getPermanentDate(), e.getIncrementDate())
                : null;

        EmployeeResponse.EmployeeResponseBuilder builder = EmployeeResponse.builder()
                .id(e.getId())
                .epfNo(e.getEpfNo())
                .nameEnglish(e.getNameEnglish())
                .nameSinhala(e.getNameSinhala())
                .designationEnglish(e.getDesignationEnglish())
                .designationSinhala(e.getDesignationSinhala())
            .incrementDate(e.getIncrementDate())
                .permanentDate(e.getPermanentDate())
                .unit(e.getUnit())
                .phoneNumber(e.getPhoneNumber())
                .isActive(e.getIsActive())
                .totalServiceYears(totalServiceYears);

        active.ifPresent(h -> builder
                .currentSalaryTypeCode(h.getSalaryType().getCode())
            .currentSalaryScale(h.getSalaryType().getScaleString())
                .currentStep(h.getCurrentStep())
                .currentSalary(h.getCurrentSalary().doubleValue())
                .hasPromotionFlag(h.getHasPromotion()));

        return builder.build();
    }

    private EmployeePatchRequest toPatchRequest(EmployeeUpdateRequest req) {
        EmployeePatchRequest patch = new EmployeePatchRequest();
        patch.setNameEnglish(req.getNameEnglish());
        patch.setNameSinhala(req.getNameSinhala());
        patch.setDesignationEnglish(req.getDesignationEnglish());
        patch.setDesignationSinhala(req.getDesignationSinhala());
        patch.setIncrementDate(req.getIncrementDate());
        patch.setPermanentDate(req.getPermanentDate());
        patch.setUnit(req.getUnit());
        patch.setPhoneNumber(req.getPhoneNumber());
        patch.setIsActive(req.getIsActive());
        return patch;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SalaryHistoryResponse toHistoryResponse(EmployeeSalaryHistory h) {
        return SalaryHistoryResponse.builder()
                .id(h.getId())
                .salaryTypeCode(h.getSalaryType().getCode())
                .salaryTypeScaleString(h.getSalaryType().getScaleString())
                .currentStep(h.getCurrentStep())
                .currentSalary(h.getCurrentSalary().doubleValue())
                .effectiveFrom(h.getEffectiveFrom())
                .effectiveTo(h.getEffectiveTo())
                .hasPromotion(h.getHasPromotion())
                .promotionRemark(h.getPromotionRemark())
                .changedBy(h.getChangedBy())
                .changedAt(h.getChangedAt())
                .build();
    }

    private java.math.BigDecimal calculateSalary(SalaryType salaryType, int step) {
        java.math.BigDecimal salary = salaryType.getStartSalary();
        for (int currentStep = 1; currentStep <= step; currentStep++) {
            salary = salary.add(calcEngine.getIncrementForStep(salaryType, currentStep));
        }
        if (salary.compareTo(salaryType.getMaxSalary()) > 0) {
            return salaryType.getMaxSalary();
        }
        return salary;
    }
}
