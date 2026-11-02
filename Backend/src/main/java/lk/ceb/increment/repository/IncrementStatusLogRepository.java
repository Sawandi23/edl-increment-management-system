package lk.ceb.increment.repository;

import lk.ceb.increment.entity.IncrementStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncrementStatusLogRepository extends JpaRepository<IncrementStatusLog, Long> {
    List<IncrementStatusLog> findByIncrementCycleIdOrderByChangedAtDesc(Long incrementCycleId);
}
