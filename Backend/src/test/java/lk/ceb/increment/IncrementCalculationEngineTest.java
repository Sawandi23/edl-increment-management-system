package lk.ceb.increment;

import lk.ceb.increment.entity.Employee;
import lk.ceb.increment.entity.EmployeeSalaryHistory;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.enums.IncrementType;
import lk.ceb.increment.service.IncrementCalculationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IncrementCalculationEngine.
 * Tests real CEB salary scale data (EL1 type).
 * No Spring context needed — pure logic tests.
 */
class IncrementCalculationEngineTest {

    private IncrementCalculationEngine engine;
    private SalaryType el1Type;

    @BeforeEach
    void setUp() {
        engine = new IncrementCalculationEngine();

        // EL1 salary type — real values from SALARY_CODE file
        el1Type = SalaryType.builder()
                .code("EL1")
                .startSalary(BigDecimal.valueOf(134870))
                .band1Increment(BigDecimal.valueOf(2400))
                .band1Years(5)
                .band2Increment(BigDecimal.valueOf(2520))
                .band2Years(10)
                .maxSalary(BigDecimal.valueOf(172070))
                .effectiveFrom(LocalDate.of(2024, 8, 1))
                .build();
    }

    // ── Anniversary date tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Anniversary date: same day/month in target year")
    void anniversaryDate_normalCase() {
        LocalDate incrementDate = LocalDate.of(2000, 8, 15);
        LocalDate result   = engine.anniversaryDateForYear(incrementDate, 2025);
        assertEquals(LocalDate.of(2025, 8, 15), result);
    }

    @Test
    @DisplayName("Anniversary date: Feb 29 join date in non-leap year → Feb 28")
    void anniversaryDate_feb29NonLeapYear() {
        LocalDate incrementDate = LocalDate.of(2000, 2, 29); // 2000 is a leap year
        LocalDate result   = engine.anniversaryDateForYear(incrementDate, 2025); // 2025 not leap
        assertEquals(LocalDate.of(2025, 2, 28), result);
    }

    @Test
    @DisplayName("Anniversary date: Feb 29 join date in leap year → Feb 29")
    void anniversaryDate_feb29LeapYear() {
        LocalDate incrementDate = LocalDate.of(2000, 2, 29);
        LocalDate result   = engine.anniversaryDateForYear(incrementDate, 2024); // 2024 is leap
        assertEquals(LocalDate.of(2024, 2, 29), result);
    }

    // ── Service years tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Completed service years: anniversary already passed this year")
    void completedServiceYears_afterAnniversary() {
        LocalDate incrementDate = LocalDate.of(2000, 8, 15);
        LocalDate asOf     = LocalDate.of(2025, 9, 1); // after Aug 15
        assertEquals(25, engine.completedServiceYears(incrementDate, asOf));
    }

    @Test
    @DisplayName("Completed service years: anniversary not yet reached this year")
    void completedServiceYears_beforeAnniversary() {
        LocalDate incrementDate = LocalDate.of(2000, 8, 15);
        LocalDate asOf     = LocalDate.of(2025, 7, 1); // before Aug 15
        assertEquals(24, engine.completedServiceYears(incrementDate, asOf));
    }

    // ── Eligibility-window tests ────────────────────────────────────────────

    @Test
    @DisplayName("Anniversary window: December anniversary is included across year boundary")
    void anniversaryWindow_decemberCrossesBoundary() {
        LocalDate incrementDate = LocalDate.of(2023, 12, 15);
        LocalDate from     = LocalDate.of(2025, 12, 1);
        LocalDate to       = LocalDate.of(2026, 3, 1);

        assertTrue(engine.anniversaryFallsWithinWindow(incrementDate, from, to));
    }

    @Test
    @DisplayName("Anniversary window: January/February anniversary is included without crossing")
    void anniversaryWindow_januaryFebruaryWithinWindow() {
        LocalDate incrementDate = LocalDate.of(2023, 1, 10);
        LocalDate from     = LocalDate.of(2026, 1, 1);
        LocalDate to       = LocalDate.of(2026, 2, 28);

        assertTrue(engine.anniversaryFallsWithinWindow(incrementDate, from, to));
    }

    // ── Band increment tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("Band 1: steps 1–5 get band1Increment (2400 for EL1)")
    void getIncrement_band1() {
        for (int step = 1; step <= 5; step++) {
            BigDecimal inc = engine.getIncrementForStep(el1Type, step);
            assertEquals(BigDecimal.valueOf(2400), inc,
                    "Step " + step + " should be band1Increment");
        }
    }

    @Test
    @DisplayName("Band 2: steps 6–15 get band2Increment (2520 for EL1)")
    void getIncrement_band2() {
        for (int step = 6; step <= 15; step++) {
            BigDecimal inc = engine.getIncrementForStep(el1Type, step);
            assertEquals(BigDecimal.valueOf(2520), inc,
                    "Step " + step + " should be band2Increment");
        }
    }

    // ── Full calculation tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Step 0→1: start salary + band1 increment")
    void calculate_firstIncrement() {
        EmployeeSalaryHistory history = buildHistory(0, BigDecimal.valueOf(134870),
                LocalDate.of(2024, 8, 15));

        IncrementCalculationEngine.CalculationResult result = engine.calculate(history, 2025);
  
  
        assertTrue(result.isCalculatedSuccessfully());
        assertFalse(result.isSurplus());
        assertEquals(BigDecimal.valueOf(2400), result.getIncrementAmount());
        assertEquals(BigDecimal.valueOf(137270), result.getNewSalary());
        assertEquals(1, result.getNewStep());
        assertEquals(IncrementType.NORMAL, result.getIncrementType());
        assertEquals(LocalDate.of(2025, 8, 15), result.getIncrementDate());
    }

    @Test
    @DisplayName("Step 5→6: band transition — increment changes from 2400 to 2520")
    void calculate_bandTransition() {
        // At step 5: salary = 134870 + (5 × 2400) = 146870
        EmployeeSalaryHistory history = buildHistory(5, BigDecimal.valueOf(146870),
                LocalDate.of(2019, 8, 15));

        IncrementCalculationEngine.CalculationResult result = engine.calculate(history, 2025);

        assertEquals(BigDecimal.valueOf(2520), result.getIncrementAmount());
        assertEquals(BigDecimal.valueOf(149390), result.getNewSalary());
        assertEquals(6, result.getNewStep());
    }

    @Test
    @DisplayName("Step 14→15: last normal increment before surplus")
    void calculate_lastNormalStep() {
        // At step 14: salary = 172070 - 2520 = 169550
        EmployeeSalaryHistory history = buildHistory(14, BigDecimal.valueOf(169550),
                LocalDate.of(2010, 8, 15));

        IncrementCalculationEngine.CalculationResult result = engine.calculate(history, 2025);

        assertFalse(result.isSurplus());
        assertEquals(BigDecimal.valueOf(2520), result.getIncrementAmount());
        assertEquals(BigDecimal.valueOf(172070), result.getNewSalary()); // = maxSalary
        assertEquals(15, result.getNewStep());
    }

    @Test
    @DisplayName("Step 15+: surplus stage — isSurplus=true, isCertificateYear=false by default")
    void calculate_surplusStage() {
        EmployeeSalaryHistory history = buildHistory(15, BigDecimal.valueOf(172070),
                LocalDate.of(2000, 8, 15));

        IncrementCalculationEngine.CalculationResult result = engine.calculate(history, 2025);

        assertTrue(result.isSurplus());
        assertFalse(result.isCertificateYear()); // HR sets this manually
        assertEquals(IncrementType.SURPLUS, result.getIncrementType());
        assertEquals(BigDecimal.valueOf(2520), result.getIncrementAmount()); // continues at band2 rate
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private EmployeeSalaryHistory buildHistory(int step, BigDecimal salary, LocalDate incrementDate) {
        Employee employee = Employee.builder()
                .epfNo("99999")
                .nameEnglish("Test Employee")
                .nameSinhala("පරීක්ෂණ සේවකයා")
                .designationEnglish("Test Designation")
                .designationSinhala("පරීක්ෂණ")
                .incrementDate(incrementDate)
                .build();

        return EmployeeSalaryHistory.builder()
                .employee(employee)
                .salaryType(el1Type)
                .currentStep(step)
                .currentSalary(salary)
                .effectiveFrom(incrementDate)
                .build();
    }
}

