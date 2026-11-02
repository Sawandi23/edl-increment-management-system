package lk.ceb.increment.service;

import lk.ceb.increment.entity.EmployeeSalaryHistory;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.enums.IncrementType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Core increment calculation engine.
 *
 * PURE LOGIC — no database calls inside here.
 * Takes inputs, returns a result record.
 * Fully unit-testable without Spring context.
 *
 * Rules encoded:
 *   1. Anniversary date = increment date shifted to current year
 *   2. Steps 0–15: normal two-band increment (band1 for steps 1–5, band2 for steps 6–15)
 *   3. Step 15+: surplus stage — AP form every year, salary change only on certificate years
 *   4. Manual override: bypasses calculation entirely, HR inputs all values
 */
@Component
public class IncrementCalculationEngine {

    /**
     * Calculate the next increment for an employee.
     *
     * @param history   The employee's current active salary history record
     * @param forYear   The calendar year to calculate for (e.g. 2025)
     * @return          CalculationResult with all values needed to create an IncrementCycle
     */
    public CalculationResult calculate(EmployeeSalaryHistory history, int forYear) {

        SalaryType type       = history.getSalaryType();
        int        currentStep = history.getCurrentStep();
        BigDecimal currentSalary = history.getCurrentSalary();
        LocalDate  incrementDateSource = history.getEmployee().getIncrementDate();

        // ── Step 1: Anniversary increment date for this year ──────────────────
        LocalDate incrementDate = anniversaryDateForYear(incrementDateSource, forYear);

        // ── Step 2: Check if post step-15 (surplus) ──────────────────────────
        boolean isSurplus = currentStep >= 15;

        if (isSurplus) {
            // Surplus stage: calculate what the next increment WOULD be
            // but flag as surplus — HR will decide whether this is a
            // certificate year or AP-only year and fill surplus label manually.
            BigDecimal surplusIncrement = type.getBand2Increment(); // same increment continues
            BigDecimal newSalary = currentSalary.add(surplusIncrement);

            return CalculationResult.builder()
                    .incrementDate(incrementDate)
                    .previousSalary(currentSalary)
                    .previousStep(currentStep)
                    .incrementAmount(surplusIncrement)
                    .newSalary(newSalary)
                    .newStep(currentStep + 1) // conceptual step counter only
                    .isSurplus(true)
                    .isCertificateYear(false) // default — HR changes this
                    .incrementType(IncrementType.SURPLUS)
                    .calculatedSuccessfully(true)
                    .build();
        }

        // ── Step 3: Normal band calculation (steps 1–15) ─────────────────────
        int    newStep      = currentStep + 1;
        BigDecimal increment = getIncrementForStep(type, newStep);
        BigDecimal newSalary = currentSalary.add(increment);

        // Safety cap at max salary (shouldn't be needed if data is correct)
        if (newSalary.compareTo(type.getMaxSalary()) > 0) {
            newSalary = type.getMaxSalary();
            increment = newSalary.subtract(currentSalary);
        }

        return CalculationResult.builder()
                .incrementDate(incrementDate)
                .previousSalary(currentSalary)
                .previousStep(currentStep)
                .incrementAmount(increment)
                .newSalary(newSalary)
                .newStep(newStep)
                .isSurplus(false)
                .isCertificateYear(true)
                .incrementType(IncrementType.NORMAL)
                .calculatedSuccessfully(true)
                .build();
    }

    /**
     * Returns the increment amount for a given step on a salary type.
     *
     * Step 1–band1Years      → band1Increment
     * Step band1Years+1 → 15 → band2Increment
     */
    public BigDecimal getIncrementForStep(SalaryType type, int step) {
        if (step <= 0) return BigDecimal.ZERO;
        if (step <= type.getBand1Years()) {
            return type.getBand1Increment();
        }
        return type.getBand2Increment();
    }

    /**
     * Given an increment date and target year, returns the anniversary date in that year.
     *
     * e.g. incrementDate=2000-08-15, forYear=2025 → 2025-08-15
     *
     * Edge case: Feb 29 increment date in a non-leap year → Feb 28
     */
    public LocalDate anniversaryDateForYear(LocalDate incrementDate, int forYear) {
        int month = incrementDate.getMonthValue();
        int day   = incrementDate.getDayOfMonth();

        if (month == 2 && day == 29 && !LocalDate.of(forYear, 1, 1).isLeapYear()) {
            day = 28;
        }

        return LocalDate.of(forYear, month, day);
    }

    /**
     * Calculate how many complete service years an employee has
     * as of a given reference date.
     */
    public int completedServiceYears(LocalDate incrementDate, LocalDate asOf) {
        int years = asOf.getYear() - incrementDate.getYear();
        // Check if anniversary hasn't happened yet this year
        LocalDate thisYearAnniversary = anniversaryDateForYear(incrementDate, asOf.getYear());
        if (asOf.isBefore(thisYearAnniversary)) {
            years--;
        }
        return Math.max(0, years);
    }

    /**
     * Returns true when the employee's anniversary in either boundary year
     * falls within the requested window.
     *
     * This keeps year-boundary windows correct, e.g. a December anniversary
     * can still be eligible when the filter spans into January/February of the
     * next year.
     */
    public boolean anniversaryFallsWithinWindow(LocalDate incrementDate, LocalDate from, LocalDate to) {
        if (incrementDate == null || from == null || to == null) {
            return false;
        }

        for (int year = from.getYear(); year <= to.getYear(); year++) {
            LocalDate anniversary = anniversaryDateForYear(incrementDate, year);
            if (!anniversary.isBefore(from) && !anniversary.isAfter(to)) {
                return true;
            }
        }
        return false;
    }

    // ── Result record ─────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class CalculationResult {
        private final LocalDate  incrementDate;
        private final BigDecimal previousSalary;
        private final int        previousStep;
        private final BigDecimal incrementAmount;
        private final BigDecimal newSalary;
        private final int        newStep;
        private final boolean    isSurplus;
        private final boolean    isCertificateYear;
        private final IncrementType incrementType;
        private final boolean    calculatedSuccessfully;
        private final String     errorMessage; // set if calculatedSuccessfully = false
    }
}
