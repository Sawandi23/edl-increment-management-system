
package lk.ceb.increment.seeder;

import lk.ceb.increment.entity.SalaryStep;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.repository.SalaryTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds all 30 CEB salary types (2024-08-01 scale) on first boot.
 * Skips if data already exists.
 *
 * Data source: SALARY_CODE__1_.xlsx provided by HR division.
 * Effective date: 2024-08-01
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SalaryTypeSeeder implements CommandLineRunner {

    private final SalaryTypeRepository salaryTypeRepository;

    private static final LocalDate EFFECTIVE_FROM =
            LocalDate.of(2024, 8, 1);

    @Override
    public void run(String... args) {

        if (salaryTypeRepository.count() > 0) {
            log.info("Salary types already seeded — skipping.");
            return;
        }

        log.info("Seeding 30 salary types (scale effective 2024-08-01)...");

        List<SalaryType> types = buildSalaryTypes();

        for (SalaryType type : types) {
            type.setSteps(buildSteps(type));
            salaryTypeRepository.save(type);
        }

        log.info("✓ {} salary types seeded.", types.size());
    }

    /**
     * Build all 30 salary types.
     *
     * Normal salary structure:
     *   Band 1 = 5 years
     *   Band 2 = 10 years
     *   Total = 15 increments
     *
     * Flat-rate salary structure:
     *   Same increment for all 15 years.
     */
    private List<SalaryType> buildSalaryTypes() {

        List<SalaryType> list = new ArrayList<>();

        // ============================================================
        // EL Series
        // ============================================================

        list.add(build("ELspl", 140490, 2500, 5, 2625, 10, 179240));
        list.add(build("EL1",   126610, 2300, 5, 2440, 10, 162510));
        list.add(build("EL2",   113470, 2060, 5, 2200, 10, 145770));


        // ============================================================
        // SL Series
        // ============================================================

        list.add(build("SLspl", 124325, 2220, 5, 2335, 10, 158775));
        list.add(build("SL1",   112035, 2035, 5, 2170, 10, 143910));
        list.add(build("SL2",   100420, 1825, 5, 1950, 10, 129045));
        list.add(build("SL3",    92910, 1715, 5, 1815, 10, 119635));


        // ============================================================
        // SM Series
        // ============================================================

        list.add(build("SMspl", 104520, 1660, 5, 1815, 10, 130970));
        list.add(build("SM1",    95625, 1440, 5, 1590, 10, 118725));
        list.add(build("SM2",    88810, 1335, 5, 1435, 10, 109835));
        list.add(build("SM3",    79925, 1140, 5, 1250, 10,  98125));
        list.add(build("SM4",    74470,  995, 5, 1020, 10,  89645));


        // ============================================================
        // EN Series
        // ============================================================

        list.add(build("ENspl", 112700, 1795, 5, 1990, 10, 141575));
        list.add(build("EN1",   104985, 1625, 5, 1765, 10, 130760));
        list.add(build("EN2",    96115, 1500, 5, 1615, 10, 119765));
        list.add(build("EN3",    89935, 1265, 5, 1420, 10, 110460));

        // Flat-rate — same increment for all 15 years
        list.add(build("EN4", 81050, 1085, 15, 97325));

        // EN4X has two bands: 5 + 10 years
        list.add(build("EN4X", 84150, 1120, 5, 1150, 10, 101250));

        // Flat-rate — same increment for all 15 years
        list.add(build("EN5", 73770, 920, 15, 87570));


        // ============================================================
        // SN Series
        // ============================================================

        list.add(build("SNspl", 99735, 1590, 5, 1765, 10, 125335));

        // X variants
        list.add(build("SN1X", 92910, 1440, 5, 1560, 10, 115710));
        list.add(build("SN2X", 85060, 1335, 5, 1435, 10, 106085));
        list.add(build("SN3X", 79590, 1120, 5, 1250, 10,  97690));
        list.add(build("SN4X", 74470,  995, 5, 1020, 10,  89645));

        // Standard flat-rate SN types
        list.add(build("SN1", 83345, 1335, 15, 103370));
        list.add(build("SN2", 79245, 1245, 15,  97920));
        list.add(build("SN3", 75825, 1050, 15,  91575));
        list.add(build("SN4", 71720,  960, 15,  86120));
        list.add(build("SN5", 68310,  845, 15,  80985));


        // ============================================================
        // N Series
        // ============================================================

        list.add(build("N6", 59615, 710, 15, 70265));


        // ============================================================
        // Special variant
        // ============================================================

        // list.add(build(
        //         "SN1xSPL",
        //         "Support Grade 1 Special",
        //         88885,
        //         1425,
        //         5,
        //         1525,
        //         10,
        //         116135
        // ));

        return list;
    }


    /**
     * Build a normal two-band salary type.
     *
     * Example:
     * Band 1 = 5 years
     * Band 2 = 10 years
     *
     * Total = 15 salary increments.
     */
    private SalaryType build(
            String code,
            String desc,
            double start,
            double b1Inc,
            int b1Yrs,
            double b2Inc,
            int b2Yrs,
            double max) {

        return SalaryType.builder()
                .code(code)
                .description(desc)
                .startSalary(BigDecimal.valueOf(start))
                .band1Increment(BigDecimal.valueOf(b1Inc))
                .band1Years(b1Yrs)
                .band2Increment(BigDecimal.valueOf(b2Inc))
                .band2Years(b2Yrs)
                .maxSalary(BigDecimal.valueOf(max))
                .effectiveFrom(EFFECTIVE_FROM)
                .build();
    }


    /**
     * Build a normal salary type without an explicit description.
     *
     * Uses the salary code as the description.
     */
    private SalaryType build(
            String code,
            double start,
            double b1Inc,
            int b1Yrs,
            double b2Inc,
            int b2Yrs,
            double max) {

        return build(
                code,
                code,
                start,
                b1Inc,
                b1Yrs,
                b2Inc,
                b2Yrs,
                max
        );
    }


    /**
     * Build a flat-rate salary type.
     *
     * The same increment is applied for all 15 years.
     *
     * Example:
     * Start = 81,050
     * Increment = 1,085
     * Years = 15
     *
     * Step 0 = 81,050
     * Step 1 = 82,135
     * ...
     * Step 15 = 97,325
     */
    private SalaryType build(
            String code,
            double start,
            double increment,
            int years,
            double max) {

        return build(
                code,
                code,
                start,
                increment,
                years,
                increment,
                0,
                max
        );
    }


    /**
     * Pre-compute salary for every step.
     *
     * Step 0  = starting salary
     * Step 1  = first annual increment
     * ...
     * Step 15 = salary after 15 increments
     *
     * For normal types:
     *   Step 1-5  use Band 1 increment
     *   Step 6-15 use Band 2 increment
     *
     * For flat-rate types:
     *   Step 1-15 use the same increment.
     */
    private List<SalaryStep> buildSteps(SalaryType type) {

        List<SalaryStep> steps = new ArrayList<>();

        BigDecimal salary = type.getStartSalary();

        int totalSteps =
                type.getBand1Years() +
                type.getBand2Years();

        for (int step = 0; step <= totalSteps; step++) {

            BigDecimal incrementApplied;

            if (step == 0) {

                incrementApplied = BigDecimal.ZERO;

            } else if (step <= type.getBand1Years()) {

                incrementApplied = type.getBand1Increment();

            } else {

                incrementApplied = type.getBand2Increment();
            }


            // Create salary step
            steps.add(
                    SalaryStep.builder()
                            .salaryType(type)
                            .stepNumber(step)
                            .salaryAmount(salary)
                            .incrementApplied(incrementApplied)
                            .build()
            );


            // Calculate next salary
            if (step < totalSteps) {

                BigDecimal nextIncrement;

                if (step < type.getBand1Years()) {

                    nextIncrement =
                            type.getBand1Increment();

                } else {

                    nextIncrement =
                            type.getBand2Increment();
                }

                salary = salary.add(nextIncrement);
            }
        }

        return steps;
    }
}
