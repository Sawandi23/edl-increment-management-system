package lk.ceb.increment.controller;

import jakarta.validation.Valid;
import lk.ceb.increment.dto.request.DashboardFilterRequest;
import lk.ceb.increment.dto.request.StatusChangeRequest;
import lk.ceb.increment.dto.response.DashboardStatsResponse;
import lk.ceb.increment.dto.response.IncrementCycleResponse;
import lk.ceb.increment.exception.InvalidStatusTransitionException;
import lk.ceb.increment.service.IncrementCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/increment-cycles")
@RequiredArgsConstructor
public class IncrementCycleController {

    private final IncrementCycleService cycleService;

    /** POST /api/increment-cycles/filter — main filter dashboard query */
    @PostMapping("/filter")
    public ResponseEntity<List<IncrementCycleResponse>> filterDashboard(
            @Valid @RequestBody DashboardFilterRequest request) {
        return ResponseEntity.ok(cycleService.filterDashboard(request));
    }

    /** GET /api/increment-cycles/stats?from=2025-01-01&to=2025-12-31 */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(cycleService.getDashboardStats(from, to));
    }

    /** GET /api/increment-cycles/employee/{employeeId} */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<IncrementCycleResponse>> getByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(cycleService.getByEmployee(employeeId));
    }

    /** GET /api/increment-cycles/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<IncrementCycleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cycleService.getById(id));
    }

    /** POST /api/increment-cycles/employee/{employeeId}/create-initial */
    @PostMapping("/employee/{employeeId}/create-initial")
    public ResponseEntity<IncrementCycleResponse> createInitialCycle(@PathVariable Long employeeId) {
        return ResponseEntity.ok(cycleService.createInitialCycle(employeeId));
    }

    /** PATCH /api/increment-cycles/{id}/status — workflow transitions */
    @PatchMapping("/{id}/status")
    public ResponseEntity<IncrementCycleResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusChangeRequest request) {
        return ResponseEntity.ok(cycleService.changeStatus(id, request));
    }

    /** PATCH /api/increment-cycles/{id}/override — manual HR override */
    @PatchMapping("/{id}/override")
    public ResponseEntity<IncrementCycleResponse> manualOverride(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        BigDecimal newSalary = new BigDecimal(body.get("newSalary").toString());
        Integer newStep = Integer.valueOf(body.get("newStep").toString());
        String notes = (String) body.get("notes");
        String changedBy = (String) body.get("changedBy");
        return ResponseEntity.ok(cycleService.applyManualOverride(id, newSalary, newStep, notes, changedBy));
    }

    /**
     * POST /api/increment-cycles/{id}/next-cycle — manually triggered by HR
     * ("Generate Next Cycle") once a cycle has reached PAID. Creates the
     * following year's IncrementCycle row for the same employee.
     *
     * Returns 409 Conflict (with a message) if the source cycle isn't PAID
     * yet, or if a cycle for next year already exists for this employee —
     * handled explicitly here rather than assumed to be caught by a global
     * exception handler, since I haven't seen one in this codebase.
     */
    @PostMapping("/{id}/next-cycle")
    public ResponseEntity<?> generateNextCycle(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(cycleService.generateNextCycle(id));
        } catch (InvalidStatusTransitionException | IllegalStateException e) {
            log.warn("Next-cycle generation rejected for cycle {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}