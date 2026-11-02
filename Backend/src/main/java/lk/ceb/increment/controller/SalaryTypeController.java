package lk.ceb.increment.controller;

import lk.ceb.increment.dto.response.SalaryTypeResponse;
import lk.ceb.increment.service.SalaryTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salary-types")
@RequiredArgsConstructor
public class SalaryTypeController {

    private final SalaryTypeService salaryTypeService;

    /** GET /api/salary-types — full list, for dropdowns */
    @GetMapping
    public ResponseEntity<List<SalaryTypeResponse>> getAll() {
        return ResponseEntity.ok(salaryTypeService.getAll());
    }

    /** GET /api/salary-types/{code} */
    @GetMapping("/{code}")
    public ResponseEntity<SalaryTypeResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(salaryTypeService.getByCode(code));
    }
}
