package lk.ceb.increment.controller;

import lk.ceb.increment.dto.request.StatusChangeRequest;
import lk.ceb.increment.dto.response.IncrementCycleResponse;
import lk.ceb.increment.enums.IncrementStatus;
import lk.ceb.increment.service.FormGenerationService;
import lk.ceb.increment.service.IncrementCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for form generation and the AP-cycle status workflow.
 *
 * Frontend flow:
 *   1. "Generate Appraisal Report" → POST /api/forms/ap/{cycleId}/docx
 *                                     downloads an EDITABLE Word doc, AND
 *                                     auto-advances ELIGIBLE → AP_DRAFT → AP_SENT
 *                                     (this is now the primary action, replacing
 *                                     the old PDF-download button)
 *   2. "Mark Returned from Unit"     → PATCH /api/increment-cycles/{id}/status
 *                                     body: {"newStatus":"CERTIFICATE_READY", ...}
 *                                     (already exists in IncrementCycleController)
 *   3. "Print Certificate"           → POST /api/forms/certificate/{cycleId}/docx
 *                                     (only enabled in the UI once status is
 *                                     CERTIFICATE_READY) downloads an EDITABLE
 *                                     Word doc AND auto-advances
 *                                     CERTIFICATE_READY → PAID (generating the
 *                                     certificate is the final step of the
 *                                     cycle — there is no separate
 *                                     CERTIFICATE_ISSUED holding status anymore)
 *
 * The plain PDF AP-form and certificate endpoints are kept available (e.g.
 * if a final, non-editable copy is ever needed once PDF conversion is
 * reliable on all machines) but do not drive the workflow status — only the
 * docx "Generate Appraisal Report" and "Print Certificate" actions do.
 *
 * changedBy is hardcoded to "system" for now until real admin auth exists.
 */
@Slf4j
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private static final String CHANGED_BY = "system";

    private final FormGenerationService formService;
    private final IncrementCycleService cycleService;

    /**
     * PRIMARY ACTION — "Generate Appraisal Report".
     * Downloads the AP form as an editable Word document (no PDF
     * conversion at all — fill_form.py skips it entirely for a .docx
     * output, so this is fast and needs no LibreOffice/Word on this call).
     * On success, auto-advances the cycle's status from ELIGIBLE all the
     * way to AP_SENT (two allowed transitions applied back-to-back) so no
     * separate "send" click is needed.
     */
    @PostMapping("/ap/{cycleId}/docx")
    public ResponseEntity<byte[]> generateApFormDocx(@PathVariable Long cycleId,
                                                     @RequestBody(required = false) Map<String, Object> overrides) {
        log.info("Appraisal report request — cycle {}", cycleId);
        try {
            byte[] docx = formService.generateApFormDocx(cycleId, overrides == null ? Map.of() : overrides);

            IncrementCycleResponse cycle = cycleService.getById(cycleId);
            if (cycle.getStatus() == IncrementStatus.ELIGIBLE) {
                advanceStatus(cycleId, IncrementStatus.AP_DRAFT, "Appraisal report generated");
                advanceStatus(cycleId, IncrementStatus.AP_SENT, "Sent to unit");
            } else if (cycle.getStatus() == IncrementStatus.AP_DRAFT) {
                advanceStatus(cycleId, IncrementStatus.AP_SENT, "Sent to unit");
            }
            // else: already AP_SENT or further along — this is just a
            // re-download, no status change needed.

            return docxResponse(docx, String.format("AP_Form_%s_%d.docx", cycle.getEpfNo(), cycle.getCycleYear()));
        } catch (Exception e) {
            log.error("Appraisal report generation failed for cycle {}: {}", cycleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PDF version of the AP form. Kept available but no longer drives the
     * workflow status — use the docx endpoint above for the real "generate"
     * step. This is here for cases where a final, non-editable copy of the
     * AP form itself is needed without going through Word conversion twice.
     */
    @PostMapping("/ap/{cycleId}")
    public ResponseEntity<byte[]> generateApForm(@PathVariable Long cycleId) {
        log.info("AP form (PDF) request — cycle {}", cycleId);
        try {
            byte[] pdf = formService.generateApForm(cycleId);
            return pdfResponse(pdf, "AP_Form_" + cycleId + ".pdf");
        } catch (Exception e) {
            log.error("AP form PDF generation failed for cycle {}: {}", cycleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PDF version of the certificate. Kept available but currently unused
     * by the app — PDF conversion (Word/docx2pdf and LibreOffice) has
     * proven unreliable across dev machines, so the Word version below is
     * the real "print certificate" action for now. Does NOT advance status.
     */
    @PostMapping("/certificate/{cycleId}")
    public ResponseEntity<byte[]> generateCertificate(@PathVariable Long cycleId) {
        log.info("Certificate (PDF) request — cycle {}", cycleId);

        IncrementCycleResponse cycle = cycleService.getById(cycleId);
        if (cycle.getStatus() != IncrementStatus.CERTIFICATE_READY) {
            log.warn("Certificate request rejected for cycle {} — status is {}, not CERTIFICATE_READY",
                    cycleId, cycle.getStatus());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        try {
            byte[] pdf = formService.generateCertificate(cycleId);
            return pdfResponse(pdf, String.format("Certificate_%s_%d.pdf", cycle.getEpfNo(), cycle.getCycleYear()));
        } catch (Exception e) {
            log.error("Certificate PDF generation failed for cycle {}: {}", cycleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PRIMARY certificate action — "Print Certificate" downloads the
     * editable Word document (no PDF conversion, so none of the
     * Word/LibreOffice conversion issues apply). Guarded: only allowed
     * once the cycle has come back from the unit (status ==
     * CERTIFICATE_READY) — otherwise returns 409 Conflict. On success,
     * auto-advances CERTIFICATE_READY → PAID — generating the certificate
     * is treated as the final step of the cycle.
     */
    @PostMapping("/certificate/{cycleId}/docx")
    public ResponseEntity<byte[]> generateCertificateDocx(@PathVariable Long cycleId) {
        log.info("Certificate (docx) request — cycle {}", cycleId);

        IncrementCycleResponse cycle = cycleService.getById(cycleId);
        if (cycle.getStatus() != IncrementStatus.CERTIFICATE_READY) {
            log.warn("Certificate request rejected for cycle {} — status is {}, not CERTIFICATE_READY",
                    cycleId, cycle.getStatus());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        try {
            byte[] docx = formService.generateCertificateDocx(cycleId);
            advanceStatus(cycleId, IncrementStatus.CERTIFICATE_ISSUED, "Certificate generated");
            advanceStatus(cycleId, IncrementStatus.PAID, "Certificate generated");
            return docxResponse(docx, String.format("Certificate_%s_%d.docx", cycle.getEpfNo(), cycle.getCycleYear()));
        } catch (Exception e) {
            log.error("Certificate docx generation failed for cycle {}: {}", cycleId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void advanceStatus(Long cycleId, IncrementStatus newStatus, String note) {
        StatusChangeRequest req = new StatusChangeRequest();
        req.setNewStatus(newStatus.name());
        req.setChangedBy(CHANGED_BY);
        req.setNotes(note);
        cycleService.changeStatus(cycleId, req);
    }

    private ResponseEntity<byte[]> docxResponse(byte[] docx, String filename) {
        MediaType wordType = MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return ResponseEntity.ok()
                .contentType(wordType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(docx.length))
                .body(docx);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
                .body(pdf);
    }
}