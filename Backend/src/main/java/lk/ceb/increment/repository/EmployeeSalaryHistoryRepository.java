package lk.ceb.increment.repository;

import lk.ceb.increment.entity.EmployeeSalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeSalaryHistoryRepository extends JpaRepository<EmployeeSalaryHistory, Long> {

    /** The currently active salary history row — effectiveTo IS NULL */
    @Query("""
        SELECT h FROM EmployeeSalaryHistory h
        WHERE h.employee.id = :employeeId AND h.effectiveTo IS NULL
        """)
    Optional<EmployeeSalaryHistory> findActiveByEmployeeId(@Param("employeeId") Long employeeId);

    List<EmployeeSalaryHistory> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
