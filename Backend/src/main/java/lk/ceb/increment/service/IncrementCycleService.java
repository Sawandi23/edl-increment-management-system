package lk.ceb.increment.service;

import lk.ceb.increment.dto.request.DashboardFilterRequest;
import lk.ceb.increment.dto.request.StatusChangeRequest;
import lk.ceb.increment.dto.response.DashboardStatsResponse;
import lk.ceb.increment.dto.response.IncrementCycleResponse;
import lk.ceb.increment.entity.Employee;
import lk.ceb.increment.entity.IncrementCycle;
import lk.ceb.increment.entity.IncrementStatusLog;
import lk.ceb.increment.entity.EmployeeSalaryHistory;
import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.exception.InvalidStatusTransitionException;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.EmployeeRepository;
import lk.ceb.increment.repository.EmployeeSalaryHistoryRepository;
import lk.ceb.increment.repository.IncrementCycleRepository;
import lk.ceb.increment.repository.IncrementStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncrementCycleService {

    private final IncrementCycleRepository cycleRepository;
    private final IncrementStatusLogRepository statusLogRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository salaryHistoryRepository;
    private final IncrementCalculationEngine calcEngine;
    private final SalaryScaleVersionService salaryScaleVersionService;
    private final DialogSmsService dialogSmsService;

    // ── Valid status transitions — encodes the workflow rules ────────────────
    // Prevents e.g. jumping straight from ELIGIBLE to PAID
    private static final Map<IncrementStatus, Set<IncrementStatus>> ALLOWED_TRANSITIONS = Map.of(
            IncrementStatus.ELIGIBLE,           EnumSet.of(IncrementStatus.AP_DRAFT),
            IncrementStatus.AP_DRAFT,           EnumSet.of(IncrementStatus.AP_SENT, IncrementStatus.ELIGIBLE),
            IncrementStatus.AP_SENT,            EnumSet.of(IncrementStatus.AP_STOPPED, IncrementStatus.AP_PAUSED,
                                                            IncrementStatus.CERTIFICATE_READY, IncrementStatus.REJECTED),
            IncrementStatus.AP_PAUSED,          EnumSet.of(IncrementStatus.AP_SENT, IncrementStatus.AP_STOPPED),
            IncrementStatus.CERTIFICATE_READY,  EnumSet.of(IncrementStatus.CERTIFICATE_ISSUED),
            IncrementStatus.CERTIFICATE_ISSUED, EnumSet.of(IncrementStatus.PAID),
            IncrementStatus.AP_STOPPED,         EnumSet.of(IncrementStatus.AP_SENT), // can resume after fix
            IncrementStatus.REJECTED,           EnumSet.noneOf(IncrementStatus.class), // terminal, manual follow-up
            IncrementStatus.PAID,               EnumSet.noneOf(IncrementStatus.class)  // terminal
    );

    // ── Dashboard filter ──────────────────────────────────────────────────────

    public List<IncrementCycleResponse> filterDashboard(DashboardFilterRequest req) {
        IncrementStatus status = req.getStatus() != null ? IncrementStatus.valueOf(req.getStatus()) : null;
        List<IncrementCycle> cycles = cycleRepository.findAllWithEmployeeAndSalaryType().stream()
            .filter(c -> matchesEligibilityWindow(c, req.getFromDate(), req.getToDate()))
            .filter(c -> status == null || c.getStatus() == status)
            .filter(c -> req.getSalaryTypeCode() == null || c.getSalaryType() != null
                && req.getSalaryTypeCode().equals(c.getSalaryType().getCode()))
            .sorted(java.util.Comparator.comparing(IncrementCycle::getIncrementDate))
            .toList();
        return cycles.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public DashboardStatsResponse getDashboardStats(LocalDate from, LocalDate to) {
        return DashboardStatsResponse.builder()
            .dueThisPeriod(cycleRepository.findAllWithEmployeeAndSalaryType().stream()
                .filter(c -> matchesEligibilityWindow(c, from, to))
                .count())
                .apFormsPending(cycleRepository.countByStatus(IncrementStatus.AP_DRAFT))
                .sentToUnit(cycleRepository.countByStatus(IncrementStatus.AP_SENT))
                .paidThisYear(cycleRepository.countByStatus(IncrementStatus.PAID))
                .build();
    }

    // ── Per-employee ──────────────────────────────────────────────────────────

    public List<IncrementCycleResponse> getByEmployee(Long employeeId) {
        return cycleRepository.findByEmployeeIdOrderByIncrementDateDesc(employeeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public IncrementCycleResponse getById(Long cycleId) {
        return toResponse(findCycleOrThrow(cycleId));
    }

    // ── Initial cycle ────────────────────────────────────────────────────────

    @Transactional
    public IncrementCycleResponse createInitialCycle(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (employee.getIncrementDate() == null) {
            throw new IllegalArgumentException("Employee increment date is required to create an initial cycle");
        }

        int currentSoftwareYear = LocalDate.now().getYear();
        int cycleYear = Math.max(currentSoftwareYear, employee.getIncrementDate().getYear());

        cycleRepository.findByEmployeeIdAndCycleYear(employeeId, cycleYear)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Initial cycle already exists for employee " + employeeId);
                });

        EmployeeSalaryHistory activeHistory = salaryHistoryRepository.findActiveByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalStateException("No active salary history found for employee " + employeeId));

        IncrementCalculationEngine.CalculationResult result = calcEngine.calculate(activeHistory, cycleYear);
        LocalDate cycleDate = result.getIncrementDate();

        IncrementCycle cycle = IncrementCycle.builder()
                .employee(employee)
                .salaryType(activeHistory.getSalaryType())
                .salaryScale(resolveSalaryScale(activeHistory.getSalaryType().getCode(), cycleDate))
                .cycleYear(cycleYear)
                .incrementDate(cycleDate)
                .periodFrom(cycleDate != null ? cycleDate.minusYears(1).plusDays(1) : null)
                .periodTo(cycleDate)
                .previousSalary(result.getPreviousSalary())
                .previousStep(result.getPreviousStep())
                .incrementAmount(result.getIncrementAmount())
                .newSalary(result.getNewSalary())
                .newStep(result.getNewStep())
                .isSurplus(result.isSurplus())
                .isCertificateYear(result.isCertificateYear())
                .incrementType(result.getIncrementType())
                .status(IncrementStatus.ELIGIBLE)
                .createdBy("system")
                .build();

        cycle = cycleRepository.save(cycle);

        IncrementStatusLog log = IncrementStatusLog.builder()
                .incrementCycle(cycle)
                .oldStatus(null)
                .newStatus(IncrementStatus.ELIGIBLE)
                .changedBy("system")
                .notes("Initial cycle created")
                .build();
        statusLogRepository.save(log);

        return toResponse(cycle);
    }

    @Transactional
    public void syncIncrementCycleDates(Long employeeId, LocalDate newIncrementDate) {
        if (newIncrementDate == null) {
            return;
        }

        List<IncrementCycle> cycles = cycleRepository.findByEmployeeIdOrderByIncrementDateDesc(employeeId);
        for (IncrementCycle cycle : cycles) {
            int year = cycle.getCycleYear() != null ? cycle.getCycleYear() : newIncrementDate.getYear();
            LocalDate syncDate = calcEngine.anniversaryDateForYear(newIncrementDate, year);
            cycle.setIncrementDate(syncDate);
            cycle.setPeriodFrom(syncDate.minusYears(1).plusDays(1));
            cycle.setPeriodTo(syncDate);
            if (cycle.getSalaryType() != null) {
                cycle.setSalaryScale(resolveSalaryScale(cycle.getSalaryType().getCode(), syncDate));
            }
            cycleRepository.save(cycle);
        }
    }

    // ── Status workflow ───────────────────────────────────────────────────────

    /**
     * Change the status of an increment cycle.
     * Validates the transition is allowed, logs it, and stamps the
     * relevant timestamp field (apFormSentAt, paidAt, etc.)
     */
    @Transactional
    public IncrementCycleResponse changeStatus(Long cycleId, StatusChangeRequest req) {
        IncrementCycle cycle = findCycleOrThrow(cycleId);
        IncrementStatus oldStatus = cycle.getStatus();
        IncrementStatus newStatus = IncrementStatus.valueOf(req.getNewStatus());

        Set<IncrementStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(oldStatus, EnumSet.noneOf(IncrementStatus.class));
        if (!allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot move from %s to %s", oldStatus, newStatus));
        }

        // Require notes for these transitions
        boolean notesRequired = newStatus == IncrementStatus.AP_STOPPED
                || newStatus == IncrementStatus.AP_PAUSED
                || newStatus == IncrementStatus.REJECTED;
        if (notesRequired && (req.getNotes() == null || req.getNotes().isBlank())) {
            throw new IllegalArgumentException("Notes are required when setting status to " + newStatus);
        }

        cycle.setStatus(newStatus);
        stampTimestamp(cycle, newStatus);
        cycle = cycleRepository.save(cycle);

        // Audit log
        IncrementStatusLog log = IncrementStatusLog.builder()
                .incrementCycle(cycle)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(req.getChangedBy())
                .notes(req.getNotes())
                .build();
        statusLogRepository.save(log);

        // Send workflow notifications only after a valid status transition.
        // DialogSmsService catches SMS errors so they do not interrupt the workflow.
        dialogSmsService.notifyStatusChange(cycle, oldStatus, newStatus);

        return toResponse(cycle);
    }

    /**
     * Manual override — HR admin directly edits calculated values.
     * Always logged with mandatory notes explaining why.
     */
    @Transactional
    public IncrementCycleResponse applyManualOverride(Long cycleId, java.math.BigDecimal newSalary,
                                                         Integer newStep, String notes, String changedBy) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Override notes are required");
        }
        IncrementCycle cycle = findCycleOrThrow(cycleId);

        cycle.setNewSalary(newSalary);
        cycle.setNewStep(newStep);
        cycle.setIsManualOverride(true);
        cycle.setOverrideNotes(notes);
        cycle = cycleRepository.save(cycle);

        IncrementStatusLog log = IncrementStatusLog.builder()
                .incrementCycle(cycle)
                .oldStatus(cycle.getStatus())
                .newStatus(cycle.getStatus()) // status unchanged, just values
                .changedBy(changedBy)
                .notes("MANUAL OVERRIDE: " + notes)
                .build();
        statusLogRepository.save(log);

        return toResponse(cycle);
    }

    // ── Next-cycle generation ────────────────────────────────────────────────

    /**
     * Manually triggered by HR ("Generate Next Cycle" button) once a cycle
     * has reached PAID. Creates a new IncrementCycle row for (cycleYear + 1)
     * so the employee reappears in next year's eligibility window.
     *
     * Guards:
     *  - source cycle must be PAID (not just any status)
     *  - a cycle for (employeeId, nextYear) must not already exist —
     *    prevents duplicates from a double-click or repeat call
     *
     */
    @Transactional
    public IncrementCycleResponse generateNextCycle(Long cycleId) {
        IncrementCycle source = findCycleOrThrow(cycleId);

        if (source.getStatus() != IncrementStatus.PAID) {
            throw new InvalidStatusTransitionException(
                    "Cannot generate next cycle — source cycle status is "
                            + source.getStatus() + ", must be PAID");
        }

        int nextYear = source.getCycleYear() + 1;
        Long employeeId = source.getEmployee().getId();

        if (cycleRepository.findByEmployeeIdAndCycleYear(employeeId, nextYear).isPresent()) {
            throw new IllegalStateException(
                    "A cycle for " + nextYear + " already exists for this employee");
        }

        LocalDate nextIncrementDate = calcEngine.anniversaryDateForYear(
                source.getEmployee().getIncrementDate(), nextYear);
        int previousStep = source.getNewStep() != null ? source.getNewStep() : 0;
        int nextStep = previousStep + 1;
        BigDecimal previousSalary = source.getNewSalary();
        BigDecimal incrementAmount;
        if (previousStep >= 15 || Boolean.TRUE.equals(source.getIsSurplus())) {
            incrementAmount = source.getSalaryType().getBand2Increment();
        } else {
            incrementAmount = calcEngine.getIncrementForStep(source.getSalaryType(), nextStep);
        }
        BigDecimal nextSalary = previousSalary.add(incrementAmount);
        if (nextSalary.compareTo(source.getSalaryType().getMaxSalary()) > 0) {
            nextSalary = source.getSalaryType().getMaxSalary();
            incrementAmount = nextSalary.subtract(previousSalary);
        }
        boolean nextIsSurplus = previousStep >= 15 || Boolean.TRUE.equals(source.getIsSurplus());

        IncrementCycle next = IncrementCycle.builder()
                .employee(source.getEmployee())
                .salaryType(source.getSalaryType())
                .salaryScale(resolveSalaryScale(source.getSalaryType().getCode(), nextIncrementDate))
                .cycleYear(nextYear)
                .incrementDate(nextIncrementDate)
                .periodFrom(nextIncrementDate.minusYears(1).plusDays(1))
                .periodTo(nextIncrementDate)
                .previousSalary(previousSalary)
                .previousStep(previousStep)
                .incrementAmount(incrementAmount)
                .newSalary(nextSalary)
                .newStep(nextStep)
                .isSurplus(nextIsSurplus)
                .isCertificateYear(!nextIsSurplus)
                .incrementType(nextIsSurplus ? lk.ceb.increment.enums.IncrementType.SURPLUS
                                             : lk.ceb.increment.enums.IncrementType.NORMAL)
                .status(IncrementStatus.ELIGIBLE)
                .createdBy("system")
                .build();

        next = cycleRepository.save(next);

        IncrementStatusLog log = IncrementStatusLog.builder()
                .incrementCycle(next)
                .oldStatus(null)
                .newStatus(IncrementStatus.ELIGIBLE)
                .changedBy("system")
                .notes("Auto-generated from cycle " + cycleId + " (" + source.getCycleYear() + ")")
                .build();
        statusLogRepository.save(log);

        return toResponse(next);
    }

    private void stampTimestamp(IncrementCycle cycle, IncrementStatus status) {
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case AP_DRAFT -> cycle.setApFormGeneratedAt(now);
            case AP_SENT -> cycle.setApFormSentAt(now);
            case CERTIFICATE_ISSUED -> cycle.setCertificateGeneratedAt(now);
            case PAID -> cycle.setPaidAt(now);
            default -> { /* no timestamp for this status */ }
        }
    }

    private IncrementCycle findCycleOrThrow(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Increment cycle not found: " + cycleId));
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private IncrementCycleResponse toResponse(IncrementCycle c) {
        LocalDate incrementDate = c.getIncrementDate();
        LocalDate periodFrom = incrementDate != null
            ? incrementDate.minusYears(1).plusDays(1) : c.getPeriodFrom();
        LocalDate periodTo = incrementDate != null ? incrementDate : c.getPeriodTo();
        String serviceTotal = getServiceStr(c, "SERVICE_TOTAL", serviceTotalFallback(c));

        return IncrementCycleResponse.builder()
                .id(c.getId())
                .employeeId(c.getEmployee().getId())
                .epfNo(c.getEmployee().getEpfNo())
                .employeeNameEnglish(c.getEmployee().getNameEnglish())
                .employeeNameSinhala(c.getEmployee().getNameSinhala())
                .designationEnglish(c.getEmployee().getDesignationEnglish())
                .unit(c.getEmployee().getUnit())
                .salaryTypeCode(c.getSalaryType().getCode())
                .salaryScale(c.getSalaryScale())
                .cycleYear(c.getCycleYear())
            .incrementDate(incrementDate)
            .periodFrom(periodFrom)
            .periodTo(periodTo)
                .previousSalary(c.getPreviousSalary().doubleValue())
                .previousStep(c.getPreviousStep())
                .incrementAmount(c.getIncrementAmount().doubleValue())
                .newSalary(c.getNewSalary().doubleValue())
                .newStep(c.getNewStep())
                .isSurplus(c.getIsSurplus())
                .surplusLabel(c.getSurplusLabel())
                .isCertificateYear(c.getIsCertificateYear())
                .incrementType(c.getIncrementType())
                .evaluationScore(c.getEvaluationScore())
                .suspended(c.getSuspended())
                .hasPraise(c.getHasPraise())
                .hasPunishment(c.getHasPunishment())
                .serviceTotal(serviceTotal)
                .servicePost(getServiceStr(c, "SERVICE_POST", serviceTotal))
                .serviceGrade(getServiceStr(c, "SERVICE_GRADE", ""))
                .status(c.getStatus())
                .referenceNumber(c.getReferenceNumber())
                .apFormGeneratedAt(c.getApFormGeneratedAt())
                .apFormSentAt(c.getApFormSentAt())
                .certificateGeneratedAt(c.getCertificateGeneratedAt())
                .paidAt(c.getPaidAt())
                .isManualOverride(c.getIsManualOverride())
                .overrideNotes(c.getOverrideNotes())
                .build();
    }

    /**
     * A cycle matches the dashboard window when its OWN incrementDate falls
     * within [from, to]. This must check the specific cycle row's stored
     * date, not recompute the employee's general recurring anniversary
     * pattern — recomputing per-window previously caused every historical
     * cycle row for an employee (2024, 2025, 2026...) to match any window
     * whose span happened to cover that employee's month/day, even when
     * the row being shown was an old, already-completed cycle from a
     * different year. Each row's incrementDate was already correctly
     * computed once at creation time (see IncrementCalculationEngine),
     * so comparing it directly is both simpler and correct.
     */
    private boolean matchesEligibilityWindow(IncrementCycle cycle, LocalDate from, LocalDate to) {
        LocalDate d = cycle.getIncrementDate();
        if (d == null || from == null || to == null) {
            return false;
        }
        return !d.isBefore(from) && !d.isAfter(to);
    }

    private String serviceTotalFallback(IncrementCycle cycle) {
        if (cycle.getEmployee() == null || cycle.getEmployee().getIncrementDate() == null || cycle.getIncrementDate() == null) {
            return "";
        }
        int years = calcEngine.completedServiceYears(cycle.getEmployee().getIncrementDate(), cycle.getIncrementDate());
        return String.format("අවු %d", years);
    }

    private String getServiceStr(IncrementCycle cycle, String key, String fallback) {
        if (cycle.getOverrideNotes() == null) {
            return fallback;
        }
        for (String part : cycle.getOverrideNotes().split("\\|")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(key + ":")) {
                return trimmed.substring(key.length() + 1).trim();
            }
        }
        return fallback;
    }

    private String resolveSalaryScale(String salaryTypeCode, LocalDate onDate) {
        if (salaryTypeCode == null || salaryTypeCode.isBlank() || onDate == null) {
            return null;
        }
        return salaryScaleVersionService.resolveScaleString(salaryTypeCode.trim(), onDate);
    }
}