package lk.ceb.increment.controller;

import jakarta.validation.Valid;
import lk.ceb.increment.dto.request.EmployeeCreateRequest;
import lk.ceb.increment.dto.request.EmployeePatchRequest;
import lk.ceb.increment.dto.request.EmployeeUpdateRequest;
import lk.ceb.increment.dto.request.SalaryTypeChangeRequest;
import lk.ceb.increment.dto.response.EmployeeResponse;
import lk.ceb.increment.dto.response.SalaryHistoryResponse;
import lk.ceb.increment.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /** GET /api/employees/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getById(id));
    }

    /** GET /api/employees/search?epfNo=26856 */
    @GetMapping("/search")
    public ResponseEntity<EmployeeResponse> searchByEpf(@RequestParam String epfNo) {
        return ResponseEntity.ok(employeeService.findByEpfNo(epfNo));
    }

    /** GET /api/employees/search-by-name?name=Adikari */
    @GetMapping("/search-by-name")
    public ResponseEntity<List<EmployeeResponse>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(employeeService.searchByName(name));
    }

    /** POST /api/employees */
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(201).body(employeeService.createEmployee(request));
    }

    /** PATCH /api/employees/{id} */
    @PatchMapping("/{id}")
    public ResponseEntity<EmployeeResponse> patchEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeePatchRequest request) {
        return ResponseEntity.ok(employeeService.patchEmployee(id, request));
    }

    /** DELETE /api/employees/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    /** PUT /api/employees/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable Long id,
            @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }

    /** POST /api/employees/{id}/salary-type-change — promotion */
    @PostMapping("/{id}/salary-type-change")
    public ResponseEntity<SalaryHistoryResponse> changeSalaryType(
            @PathVariable Long id,
            @Valid @RequestBody SalaryTypeChangeRequest request) {
        return ResponseEntity.ok(employeeService.changeSalaryType(id, request));
    }

    /** GET /api/employees/{id}/salary-history */
    @GetMapping("/{id}/salary-history")
    public ResponseEntity<List<SalaryHistoryResponse>> getSalaryHistory(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getSalaryHistory(id));
    }
}
