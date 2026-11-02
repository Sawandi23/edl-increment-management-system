package lk.ceb.increment.repository;

import lk.ceb.increment.entity.IncrementCycle;
import lk.ceb.increment.enums.IncrementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncrementCycleRepository extends JpaRepository<IncrementCycle, Long> {

    // ── Dashboard filter query ─────────────────────────────────────────────────

    @Query("""
        SELECT ic FROM IncrementCycle ic
        JOIN FETCH ic.employee e
        JOIN FETCH ic.salaryType st
        ORDER BY ic.incrementDate ASC
        """)
    List<IncrementCycle> findAllWithEmployeeAndSalaryType();

    /**
     * Core filter dashboard query.
     * Returns all cycles whose incrementDate falls within [from, to].
     * Optional filters: status, salary type code.
     */
    @Query("""
        SELECT ic FROM IncrementCycle ic
        JOIN FETCH ic.employee e
        JOIN FETCH ic.salaryType st
        WHERE ic.incrementDate BETWEEN :from AND :to
        AND (:status IS NULL OR ic.status = :status)
        AND (:salaryTypeCode IS NULL OR st.code = :salaryTypeCode)
        ORDER BY ic.incrementDate ASC
        """)
    List<IncrementCycle> findByDateRangeAndFilters(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") IncrementStatus status,
            @Param("salaryTypeCode") String salaryTypeCode
    );

    // ── Per-employee queries ──────────────────────────────────────────────────

    List<IncrementCycle> findByEmployeeIdOrderByIncrementDateDesc(Long employeeId);

    Optional<IncrementCycle> findByEmployeeIdAndCycleYear(Long employeeId, Integer cycleYear);

    // ── Stats for dashboard counters ──────────────────────────────────────────

    long countByStatus(IncrementStatus status);

    @Query("SELECT COUNT(ic) FROM IncrementCycle ic WHERE ic.incrementDate BETWEEN :from AND :to")
    long countDueInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
