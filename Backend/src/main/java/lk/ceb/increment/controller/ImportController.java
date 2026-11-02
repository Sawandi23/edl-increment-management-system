package lk.ceb.increment.controller;

import lk.ceb.increment.dto.request.EmployeeImportRequest;
import lk.ceb.increment.dto.request.IncrementCycleImportRequest;
import lk.ceb.increment.dto.response.EmployeeResponse;
import lk.ceb.increment.dto.response.IncrementCycleResponse;
import lk.ceb.increment.service.EmployeeImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Import endpoints — called by the Python import script.
 *
 * POST /api/employees/import
 *   Upsert one employee record by EPF no.
 *   Returns 201 (created) or 200 (updated).
 *
 * POST /api/employees/{id}/import-cycle
 *   Seed one increment cycle row for an employee.
 *   Upserts by (employee_id, cycle_year).
 *   Returns 201 (created) or 200 (updated).
 */
@Slf4j
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class ImportController {

    private final EmployeeImportService importService;

    /**
     * Upsert employee by EPF no.
     * Called once per row in the Excel sheet.
     */
    @PostMapping("/import")
    public ResponseEntity<EmployeeResponse> importEmployee(
            @RequestBody EmployeeImportRequest request) {

        log.info("Import request — EPF: {}", request.getEpfNo());
        EmployeeResponse response = importService.importEmployee(request);

        // Return 201 if new, 200 if updated — client can tell the difference
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Seed one increment cycle from the Excel row.
     * Called after the employee has been created/found.
     */
    @PostMapping("/{id}/import-cycle")
    public ResponseEntity<IncrementCycleResponse> importCycle(
            @PathVariable Long id,
            @RequestBody IncrementCycleImportRequest request) {

        request.setEmployeeId(id);
        log.info("Import cycle — employee ID: {}, year: {}", id, request.getCycleYear());
        IncrementCycleResponse response = importService.importCycle(id, request);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
