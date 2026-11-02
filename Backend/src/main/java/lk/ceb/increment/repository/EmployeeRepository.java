package lk.ceb.increment.repository;

import lk.ceb.increment.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEpfNo(String epfNo);

    boolean existsByEpfNo(String epfNo);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.nameEnglish) LIKE LOWER(CONCAT('%', :name, '%'))")
    java.util.List<Employee> searchByName(@Param("name") String name);
}
