package lk.ceb.increment.repository;

import lk.ceb.increment.entity.SalaryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SalaryTypeRepository extends JpaRepository<SalaryType, Long> {
    Optional<SalaryType> findByCode(String code);
    boolean existsByCode(String code);
}
