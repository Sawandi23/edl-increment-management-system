package lk.ceb.increment.service;

import lk.ceb.increment.entity.SystemSettings;
import lk.ceb.increment.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository repo;

    public String get(String key, String defaultValue) {
        return repo.findBySettingKey(key)
                .map(SystemSettings::getSettingValue)
                .orElse(defaultValue);
    }

    public List<SystemSettings> getAll() {
        return repo.findAll();
    }

    @Transactional
    public void set(String key, String value) {
        SystemSettings s = repo.findBySettingKey(key)
                .orElse(SystemSettings.builder().settingKey(key).build());
        s.setSettingValue(value);
        repo.save(s);
    }

    @Transactional
    public void setAll(Map<String, String> updates) {
        updates.forEach(this::set);
    }

    // ── Convenience getters used by FormGenerationService ─────────────────────

    public String getDgmNameSi()    { return get("DGM_NAME_SI",   "ආර්.එම්.එස්.කේ.රත්නායක"); }
    public String getDgmNameEn()    { return get("DGM_NAME_EN",   "R.M.S.K.Rathnayake"); }
    public String getHrOfficerSi()  { return get("HR_OFFICER_SI", "ඩබ්.ඩී.අරුණ විතාන"); }
    public String getHrOfficerEn()  { return get("HR_OFFICER_EN", "W.D.Aruna Withana"); }
}
