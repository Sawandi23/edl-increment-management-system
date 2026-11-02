package lk.ceb.increment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ceb.increment.entity.Employee;
import lk.ceb.increment.entity.IncrementCycle;
import lk.ceb.increment.exception.ResourceNotFoundException;
import lk.ceb.increment.repository.IncrementCycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates filled AP forms and certificates by:
 *  1. Building a data map from the DB
 *  2. Writing it to a temp JSON file
 *  3. Calling fill_form.py as a subprocess
 *  4. Reading back the file and returning bytes
 *
 * Two output modes per document, both via the same callScript() plumbing:
 *  - *Docx  methods -> output filename ends in .docx -> fill_form.py skips
 *    PDF conversion entirely and hands back the editable Word file.
 *  - *  (PDF) methods -> output filename ends in .pdf -> fill_form.py fills
 *    the docx AND converts it (Word/docx2pdf, falling back to LibreOffice).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormGenerationService {

    private final IncrementCycleRepository cycleRepository;
    private final SystemSettingsService     settings;
    private final IncrementCalculationEngine calcEngine;
    private final ObjectMapper              objectMapper;

    @Value("${app.python.script}")
    private String pythonScriptPath;

    @Value("${app.templates.es-ap-form}")
    private String esApTemplate;

    @Value("${app.templates.skilled-ap-form}")
    private String skilledApTemplate;

    // One certificate template for every employee — no more ES vs Tech split.
    @Value("${app.templates.tech-certificate:templates/Incerment_Certificate_Technical_Staff.docx}")
    private String certificateTemplate;

    @Value("${app.templates.output-dir}")
    private String outputDir;

    // ES salary type codes (everything else = Skilled/TM)
    private static final Set<String> ES_CODES = Set.of(
        "ELspl", "EL1", "EL2",
        "SLspl", "SL1", "SL2", "SL3",
        "SMspl", "SM1", "SM2", "SM3", "SM4"
    );

    // ── AP Form — Word doc (primary generation action) ─────────────────────────

    public byte[] generateApFormDocx(Long cycleId) throws IOException {
        return generateApFormDocx(cycleId, Map.of());
    }

    public byte[] generateApFormDocx(Long cycleId, Map<String, Object> overrides) throws IOException {
        IncrementCycle cycle = findCycle(cycleId);
        Employee emp = cycle.getEmployee();

        String code    = cycle.getSalaryType() != null ? cycle.getSalaryType().getCode() : "";
        boolean isES   = ES_CODES.contains(code);
        String formType = isES ? "ES_AP" : "SKILLED_AP";
        String template = isES ? esApTemplate : skilledApTemplate;

        Map<String, Object> data = buildApData(cycle, emp, isES, overrides);
        String filename = String.format("AP_%s_%s_%d.docx", formType, emp.getEpfNo(), cycle.getCycleYear());

        return callScript(formType, template, filename, data);
    }

    // ── AP Form — PDF (kept for anywhere a final, non-editable copy is needed) ─

    public byte[] generateApForm(Long cycleId) throws IOException {
        IncrementCycle cycle = findCycle(cycleId);
        Employee emp = cycle.getEmployee();

        String code    = cycle.getSalaryType() != null ? cycle.getSalaryType().getCode() : "";
        boolean isES   = ES_CODES.contains(code);
        String formType = isES ? "ES_AP" : "SKILLED_AP";
        String template = isES ? esApTemplate : skilledApTemplate;

        Map<String, Object> data = buildApData(cycle, emp, isES, Map.of());
        String filename = String.format("AP_%s_%s_%d.pdf", formType, emp.getEpfNo(), cycle.getCycleYear());

        return callScript(formType, template, filename, data);
    }

    // ── Certificate — Word doc ──────────────────────────────────────────────────

    public byte[] generateCertificateDocx(Long cycleId) throws IOException {
        IncrementCycle cycle = findCycle(cycleId);
        Employee emp = cycle.getEmployee();

        String formType = "TECH_CERT";
        Map<String, Object> data = buildCertData(cycle, emp);
        String filename = String.format("CERT_%s_%s_%d.docx", formType, emp.getEpfNo(), cycle.getCycleYear());

        return callScript(formType, certificateTemplate, filename, data);
    }

    // ── Certificate — PDF (one template, one form type, for every employee) ────

    public byte[] generateCertificate(Long cycleId) throws IOException {
        IncrementCycle cycle = findCycle(cycleId);
        Employee emp = cycle.getEmployee();

        String formType = "TECH_CERT";
        Map<String, Object> data = buildCertData(cycle, emp);
        String filename = String.format("CERT_%s_%s_%d.pdf", formType, emp.getEpfNo(), cycle.getCycleYear());

        return callScript(formType, certificateTemplate, filename, data);
    }

    // ── Data builders ─────────────────────────────────────────────────────────

    private Map<String, Object> buildApData(IncrementCycle c, Employee emp, boolean isES, Map<String, Object> overrides) {
        Map<String, Object> d = new LinkedHashMap<>();

        // Reference
        String ref = c.getReferenceNumber() != null
            ? c.getReferenceNumber()
            : buildApRef(emp.getEpfNo(), emp.getUnit(), isES);
        d.put("ref_no",        ref);

        // Period
        // The appraisal period is inclusive: the day after the previous
        // anniversary through the current increment date (e.g. 2025-10-05 to
        // 2026-10-04). Derive it from the increment date so stale saved period
        // values cannot produce incorrect dates in generated forms.
        String fromDate = c.getIncrementDate() != null
            ? c.getIncrementDate().minusYears(1).plusDays(1).toString() : "";
        String toDate = c.getIncrementDate() != null
            ? c.getIncrementDate().toString() : "";
        d.put("period_from",   fromDate);
        d.put("period_to",     toDate);
        d.put("inc_date",      c.getIncrementDate() != null ? c.getIncrementDate().toString() : "");
        d.put("period_year",   String.valueOf(c.getCycleYear()));

        // Employee
        d.put("name_si",       safe(emp.getNameSinhala()));
        d.put("name_en",       safe(emp.getNameEnglish()));
        d.put("epf",           emp.getEpfNo());
        d.put("desig_si",      safe(emp.getDesignationSinhala()));
        d.put("desig_en",      safe(emp.getDesignationEnglish()));
        d.put("perm_date",     emp.getPermanentDate() != null ? emp.getPermanentDate().toString() : "");

        // Service years — stored on cycle (imported from Excel)
        int totalYrs = 0;
        String serviceFallback;
        if (isES) {
            serviceFallback = emp.getPermanentDate() != null && c.getIncrementDate() != null
                ? "අවු " + calcEngine.completedServiceYears(emp.getPermanentDate(), c.getIncrementDate())
                : "";
        } else {
            serviceFallback = "අවු " + totalYrs;
        }
        if (!isES && emp.getIncrementDate() != null && c.getIncrementDate() != null) {
            totalYrs = calcEngine.completedServiceYears(emp.getIncrementDate(), c.getIncrementDate());
            serviceFallback = "අවු " + totalYrs;
        }
        // Try to read from notes/overrideNotes if stored there during import
        d.put("service_total", getServiceStr(c, "SERVICE_TOTAL", serviceFallback));
        d.put("service_post",  getServiceStr(c, "SERVICE_POST",  serviceFallback));
        d.put("service_grade", getServiceStr(c, "SERVICE_GRADE", ""));

        // Salary
        d.put("salary_scale",  c.getSalaryScale() != null ? c.getSalaryScale() : (c.getSalaryType() != null ? c.getSalaryType().getScaleString() : ""));
        d.put("prev_salary",   formatMoney(c.getPreviousSalary()));
        d.put("prev_step",     stepLabel(c.getPreviousStep(), null));
        d.put("inc_amount",    formatMoney(c.getIncrementAmount()));
        d.put("new_salary",    formatMoney(c.getNewSalary()));
        d.put("new_step",      stepLabel(c.getNewStep(), c.getSurplusLabel()));
        d.put("step_padded",   String.format("%02d", c.getNewStep()));

        // Increment type
        d.put("inc_type",      resolveIncType(c));

        // Appraisal
        d.put("suspended",     c.getSuspended() != null && c.getSuspended() ? "ඇත" : "නැත");
        d.put("praise",        c.getHasPraise() != null && c.getHasPraise() ? "ඇත" : "නැත");
        d.put("punishment",    c.getHasPunishment() != null && c.getHasPunishment() ? "ඇත" : "නැත");
        d.put("eval_score",    safe(c.getEvaluationScore()));

        // Officers
        d.put("clerk_name",    safe(c.getClerkName()));
        d.put("hr_officer",    settings.getHrOfficerSi());
        d.put("dgm_name",      settings.getDgmNameSi());

        applyOverrides(d, overrides);

        return d;
    }

    private Map<String, Object> buildCertData(IncrementCycle c, Employee emp) {
        Map<String, Object> d = new LinkedHashMap<>();

        String ref = buildCertRef(emp.getEpfNo(), emp.getUnit());
        d.put("ref_no",        ref);
        d.put("period_year",   String.valueOf(c.getCycleYear()));
        d.put("cert_date",     LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));

        d.put("name_en",       safe(emp.getNameEnglish()));
        d.put("epf",           emp.getEpfNo());
        d.put("desig_en",      safe(emp.getDesignationEnglish()));
        d.put("inc_date",      c.getIncrementDate() != null ? c.getIncrementDate().toString() : "");

        d.put("salary_scale",  c.getSalaryType() != null ? c.getSalaryType().getScaleString() : "");
        d.put("new_salary",    formatMoney(c.getNewSalary()));
        d.put("step_padded",   String.format("%02d", c.getNewStep() != null ? c.getNewStep() : 0));

        d.put("hr_officer",    settings.getHrOfficerEn());
        d.put("dgm_name",      settings.getDgmNameEn());
        d.put("unit",          safe(emp.getUnit()));
        d.put("unit_short",    shortUnit(emp.getUnit()));

        return d;
    }

    // ── Python subprocess ─────────────────────────────────────────────────────

    private byte[] callScript(String formType, String templatePath,
                               String outputFilename, Map<String, Object> data) throws IOException {
        Path outDir  = Paths.get(outputDir);
        Files.createDirectories(outDir);

        Path dataFile   = outDir.resolve("data_" + System.currentTimeMillis() + ".json");
        Path outputFile = outDir.resolve(outputFilename);

        try {
            // Write JSON data file
            objectMapper.writeValue(dataFile.toFile(), data);

            // Build command
            List<String> cmd = List.of(
                "python",   // or "python3" on Linux/Mac
                pythonScriptPath,
                formType,
                templatePath,
                outputFile.toString(),
                dataFile.toString()
            );

            log.info("Generating {} for EPF {} → {}", formType, data.get("epf"), outputFilename);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());

            int exitCode;
            try { exitCode = proc.waitFor(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }

            log.debug("Script output:\n{}", output);

            if (exitCode != 0) {
                log.error("Form script failed (exit {}): {}", exitCode, output);
                throw new IOException("Form generation failed — check server logs.\n" + output);
            }

            if (!Files.exists(outputFile)) {
                throw new IOException("Output file not created at: " + outputFile);
            }

            byte[] fileBytes = Files.readAllBytes(outputFile);
            log.info("Generated {} ({} bytes)", outputFilename, fileBytes.length);
            return fileBytes;

        } finally {
            Files.deleteIfExists(dataFile);
            Files.deleteIfExists(outputFile); // clean up after read
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private IncrementCycle findCycle(Long id) {
        return cycleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Increment cycle not found: " + id));
    }

    private String safe(String s) { return s != null ? s : ""; }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,.0f", amount.doubleValue());
    }

    private String stepLabel(Integer step, String surplusLabel) {
        if (surplusLabel != null && !surplusLabel.isBlank()) return surplusLabel;
        if (step == null) return "(00 පියවර)";
        return String.format("(%02d පියවර)", step);
    }

    private String resolveIncType(IncrementCycle c) {
        if (c.getIncrementType() == null) return "සාමාන්‍ය වැටුප් වැඩිවීමක්";
        return switch (c.getIncrementType()) {
            case NORMAL         -> "සාමාන්‍ය වැටුප් වැඩිවීමක්";
            case EFFICIENCY_BAR -> "කාර්යක‍්ෂමතා කඩඉමකින් යුක්ත";
            case SURPLUS        -> "වේතනාධක වාසි සහිත වැ.වැ";
            case MANUAL         -> "අතිරේක";
        };
    }

    private String buildApRef(String epf, String unit, boolean isES) {
        return "නිසා(කො.න)/මාසනි/ඊ5/" + safe(unit) + "/" + epf;
    }

    private String buildCertRef(String epf, String unit) {
        return "DGM (CC)/HRO/E5/" + safe(unit) + "/" + epf;
    }

    private String shortUnit(String unit) {
        if (unit == null || unit.isBlank()) return "";
        // "Chief Engineer (South)" → "C.E. (South)"
        return unit.replaceAll("(?i)Chief Engineer", "C.E.")
                   .replaceAll("(?i)CE ", "C.E. ");
    }

    private void applyOverrides(Map<String, Object> data, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        Map<String, String> keyMap = Map.ofEntries(
            Map.entry("REF_NO", "ref_no"),
            Map.entry("PERIOD_FROM", "period_from"),
            Map.entry("PERIOD_TO", "period_to"),
            Map.entry("EMP_NAME", "name_si"),
            Map.entry("EPF_NO", "epf"),
            Map.entry("DESIGNATION", "desig_si"),
            Map.entry("SALARY_SCALE", "salary_scale"),
            Map.entry("CURR_SALARY", "prev_salary"),
            Map.entry("NEXT_SALARY", "new_salary"),
            Map.entry("SERVICE_TOTAL", "service_total"),
            Map.entry("SERVICE_POST", "service_post"),
            Map.entry("SERVICE_GRADE", "service_grade"),
            Map.entry("SUSPENDED", "suspended"),
            Map.entry("PRAISE", "praise"),
            Map.entry("PUNISHMENT", "punishment"),
            Map.entry("PREV_SCORE", "eval_score"),
            Map.entry("CLERK_NAME", "clerk_name"),
            Map.entry("HR_OFFICER", "hr_officer"),
            Map.entry("OPS_HEAD", "dgm_name")
        );

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String mappedKey = keyMap.getOrDefault(entry.getKey(), entry.getKey().toLowerCase());
            if (entry.getValue() != null) {
                data.put(mappedKey, entry.getValue());
            }
        }
    }

    /**
     * Service year strings are stored in overrideNotes during import
     * using a simple key:value format.
     */
    private String getServiceStr(IncrementCycle c, String key, String fallback) {
        if (c.getOverrideNotes() != null) {
            for (String part : c.getOverrideNotes().split("\\|")) {
                if (part.trim().startsWith(key + ":")) {
                    return part.trim().substring(key.length() + 1).trim();
                }
            }
        }
        return fallback;
    }
}