package lk.ceb.increment.controller;

import lk.ceb.increment.entity.SystemSettings;
import lk.ceb.increment.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET  /api/settings          — list all settings
 * PUT  /api/settings          — update one or many settings
 * GET  /api/settings/{key}    — get single setting value
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<SystemSettings>> getAll() {
        return ResponseEntity.ok(settingsService.getAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String,String>> get(@PathVariable String key) {
        String value = settingsService.get(key, "");
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping
    public ResponseEntity<Void> update(@RequestBody Map<String,String> updates) {
        settingsService.setAll(updates);
        return ResponseEntity.ok().build();
    }
}
