// SystemSettingsRepository.java
package lk.ceb.increment.repository;

import lk.ceb.increment.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, String> {
    Optional<SystemSettings> findBySettingKey(String key);
}
