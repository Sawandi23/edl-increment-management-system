package lk.ceb.increment.repository;

import lk.ceb.increment.entity.SalaryScaleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryScaleVersionRepository extends JpaRepository<SalaryScaleVersion, Long> {

    List<SalaryScaleVersion> findBySalaryTypeCodeOrderByEffectiveFromDesc(String salaryTypeCode);

    Optional<SalaryScaleVersion> findTopBySalaryTypeCodeAndEffectiveToIsNullOrderByEffectiveFromDesc(String salaryTypeCode);

    boolean existsBySalaryTypeCodeAndEffectiveFrom(String salaryTypeCode, LocalDate effectiveFrom);

    @Query(value = """
            SELECT *
            FROM salary_scale_versions
            WHERE salary_type_code = :salaryTypeCode
              AND effective_from <= :onDate
              AND (effective_to IS NULL OR effective_to > :onDate)
            ORDER BY effective_from DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<SalaryScaleVersion> findEffectiveVersion(@Param("salaryTypeCode") String salaryTypeCode,
                                                      @Param("onDate") LocalDate onDate);
}