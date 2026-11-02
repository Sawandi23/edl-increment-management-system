package lk.ceb.increment.seeder;

import lk.ceb.increment.entity.SalaryScaleVersion;
import lk.ceb.increment.entity.SalaryType;
import lk.ceb.increment.repository.SalaryScaleVersionRepository;
import lk.ceb.increment.repository.SalaryTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * One-time migration seed for salary scale history.
 * Copies each existing salary type into the version table so lookups work
 * against historical dates without breaking existing data.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SalaryScaleVersionSeeder implements CommandLineRunner {

    private final SalaryTypeRepository salaryTypeRepository;
    private final SalaryScaleVersionRepository salaryScaleVersionRepository;

    private static final LocalDate INITIAL_EFFECTIVE_FROM = LocalDate.of(2000, 1, 1);

    @Override
    public void run(String... args) {
        if (salaryScaleVersionRepository.count() > 0) {
            log.info("Salary scale versions already seeded — skipping.");
            return;
        }

        List<SalaryType> salaryTypes = salaryTypeRepository.findAll();
        if (salaryTypes.isEmpty()) {
            log.info("No salary types found — skipping salary scale version seed.");
            return;
        }

        log.info("Seeding {} salary scale versions from existing salary types...", salaryTypes.size());

        for (SalaryType salaryType : salaryTypes) {
            salaryScaleVersionRepository.save(SalaryScaleVersion.builder()
                    .salaryTypeCode(salaryType.getCode())
                    .scaleString(salaryType.getScaleString())
                    .effectiveFrom(INITIAL_EFFECTIVE_FROM)
                    .effectiveTo(null)
                    .createdBy("SYSTEM_MIGRATION")
                    .build());
        }

        log.info("✓ Seeded {} salary scale versions.", salaryTypes.size());
    }
}